package com.pengingatsayang.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Inti "berdering seperti alarm": memutar nada alarm berulang-ulang + getar,
 * menampilkan layar penuh walau HP terkunci, sampai ditekan Matikan / Tunda.
 */
public class AlarmService extends Service {
    static final String ACTION_START = "start";
    static final String ACTION_DISMISS = "dismiss";
    static final String ACTION_SNOOZE = "snooze";
    static final String BROADCAST_CHANGED = "com.pengingatsayang.app.ALARM_CHANGED";

    private static final String CH_ALARM = "alarm_v1";
    private static final String CH_FALLBACK = "alarm_fallback_v1";
    private static final int NOTIF_ID = 42;
    private static final int FALLBACK_NOTIF_ID = 43;
    private static final long AUTO_STOP_MS = 10 * 60 * 1000L; // berhenti sendiri setelah 10 menit

    /** Pengingat yang sedang berbunyi: {id, title, message}. Hanya diakses dari main thread. */
    static final List<String[]> current = new ArrayList<>();
    static volatile boolean ringing = false;

    private MediaPlayer player;
    private Ringtone ringtone;
    private Vibrator vibrator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = () -> finishAlarm("missed");

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            String title = intent.getStringExtra(Scheduler.EXTRA_TITLE);
            String message = intent.getStringExtra(Scheduler.EXTRA_MESSAGE);
            current.add(new String[]{
                    intent.getStringExtra(Scheduler.EXTRA_ID),
                    title == null || title.isEmpty() ? "Pengingat" : title,
                    message == null ? "" : message});
            ringing = true;
            startForegroundCompat(buildNotification());
            startSoundAndVibration();
            handler.removeCallbacks(autoStop);
            handler.postDelayed(autoStop, AUTO_STOP_MS);
            notifyChanged();
        } else if (ACTION_DISMISS.equals(action)) {
            finishAlarm("dismissed");
        } else if (ACTION_SNOOZE.equals(action)) {
            for (String[] r : current) Scheduler.snooze(this, r[0], r[1], r[2]);
            finishAlarm("snoozed");
        } else if (!ringing) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(autoStop);
        stopSoundAndVibration();
        super.onDestroy();
    }

    private void finishAlarm(String ackAction) {
        handler.removeCallbacks(autoStop);
        stopSoundAndVibration();
        final List<String> ids = new ArrayList<>();
        for (String[] r : current) ids.add(r[0]);
        current.clear();
        ringing = false;
        final Context app = getApplicationContext();
        new Thread(() -> {
            for (String id : ids) FirestoreApi.ack(app, id, ackAction);
        }).start();
        notifyChanged();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void notifyChanged() {
        sendBroadcast(new Intent(BROADCAST_CHANGED).setPackage(getPackageName()));
    }

    // ---------- suara & getar ----------

    private void startSoundAndVibration() {
        ensureAudible(this);
        if (player == null && ringtone == null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            try {
                player = new MediaPlayer();
                player.setAudioAttributes(attrs);
                player.setDataSource(this, alarmUri());
                player.setLooping(true);
                player.prepare();
                player.start();
            } catch (Exception e) {
                if (player != null) {
                    try { player.release(); } catch (Exception ignored) { }
                    player = null;
                }
                try {
                    ringtone = RingtoneManager.getRingtone(this, Settings.System.DEFAULT_ALARM_ALERT_URI);
                    if (ringtone != null) {
                        ringtone.setAudioAttributes(attrs);
                        if (Build.VERSION.SDK_INT >= 28) ringtone.setLooping(true);
                        ringtone.play();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (vibrator == null) {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 900, 700};
                AudioAttributes va = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM).build();
                try {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), va);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void stopSoundAndVibration() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        if (ringtone != null) {
            try { ringtone.stop(); } catch (Exception ignored) { }
            ringtone = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) { }
            vibrator = null;
        }
    }

    private Uri alarmUri() {
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        return uri;
    }

    /** Kalau volume alarm 0, naikkan ke 60% supaya pengingat tidak "diam-diam". */
    static void ensureAudible(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, max * 6 / 10), 0);
            }
        } catch (Exception ignored) {
        }
    }

    // ---------- notifikasi ----------

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH_ALARM, "Alarm pengingat",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Layar alarm saat pengingat berbunyi");
        ch.setSound(null, null); // suara diputar sendiri oleh aplikasi
        ch.enableVibration(false);
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        NotificationChannel fb = new NotificationChannel(CH_FALLBACK, "Alarm cadangan",
                NotificationManager.IMPORTANCE_HIGH);
        fb.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        fb.enableVibration(true);
        fb.setBypassDnd(true);
        fb.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(fb);
    }

    private Notification buildNotification() {
        ensureChannels(this);
        String[] last = current.get(current.size() - 1);
        String text = last[2].isEmpty() ? "Ketuk untuk membuka" : last[2];
        if (current.size() > 1) text = "+" + (current.size() - 1) + " pengingat lain · " + text;

        Intent full = new Intent(this, AlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(this, 2, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent dismissPi = PendingIntent.getService(this, 3,
                new Intent(this, AlarmService.class).setAction(ACTION_DISMISS),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent snoozePi = PendingIntent.getService(this, 4,
                new Intent(this, AlarmService.class).setAction(ACTION_SNOOZE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CH_ALARM)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFE11D48)
                .setContentTitle(last[1])
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .addAction(new Notification.Action.Builder((Icon) null, "Tunda 5 menit", snoozePi).build())
                .addAction(new Notification.Action.Builder((Icon) null, "Matikan", dismissPi).build());
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    private void startForegroundCompat(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    /** Cadangan kalau service tidak boleh dijalankan: notifikasi yang bunyinya berulang. */
    static void showFallbackNotification(Context c, String title, String message) {
        try {
            ensureChannels(c);
            ensureAudible(c);
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, 5, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CH_FALLBACK)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setColor(0xFFE11D48)
                    .setContentTitle(title == null ? "Pengingat" : title)
                    .setContentText(message == null ? "" : message)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(pi, true)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            n.flags |= Notification.FLAG_INSISTENT; // bunyi terus sampai dibuka
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(FALLBACK_NOTIF_ID, n);
        } catch (Exception ignored) {
        }
    }
}
