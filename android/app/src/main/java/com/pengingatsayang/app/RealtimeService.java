package com.pengingatsayang.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MemoryCacheSettings;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Sambungan langsung ke database. Setiap kali kamu menyimpan/mengubah/menghapus pengingat
 * di halaman admin, Firestore mengirim perubahannya ke sini dalam hitungan detik,
 * lalu alarm di HP langsung dijadwalkan ulang. Tidak perlu tombol sinkron.
 */
public class RealtimeService extends Service {
    static final String BROADCAST_DATA = "com.pengingatsayang.app.DATA_CHANGED";
    private static final String CH_STATUS = "status_v1";
    private static final int NOTIF_ID = 7;
    private static final String FIREBASE_APP = "pengingat";
    private static final long RETRY_MS = 60 * 1000L;
    private static final long HEARTBEAT_MS = 5 * 60 * 1000L;

    /** true = sedang tersambung langsung ke server. */
    static volatile boolean connected = false;
    static volatile long lastServerEventAt = 0;

    private ListenerRegistration registration;
    private String listeningKey;
    private long lastHeartbeat = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable retry = this::listen;

    /** Aman dipanggil kapan saja; kalau Android tidak mengizinkan, cadangan 15 menit tetap jalan. */
    static void start(Context c) {
        if (!Config.isConfigured(c)) return;
        try {
            c.startForegroundService(new Intent(c, RealtimeService.class));
        } catch (Exception ignored) {
        }
    }

    static void stop(Context c) {
        connected = false;
        c.stopService(new Intent(c, RealtimeService.class));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat(buildNotification(connected ? "Tersambung · perubahan langsung diterima" : "Menyambungkan…"));
        if (!Config.isConfigured(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        listen();
        return START_STICKY; // kalau dimatikan sistem, Android menyalakannya lagi
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(retry);
        if (registration != null) registration.remove();
        registration = null;
        connected = false;
        notifyUi();
        super.onDestroy();
    }

    // ---------- Firestore ----------

    private void listen() {
        handler.removeCallbacks(retry);
        if (!Config.isConfigured(this)) return;
        String key = Config.projectId(this) + "|" + Config.apiKey(this) + "|" + Config.ownerId(this);
        if (registration != null && key.equals(listeningKey)) return;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        listeningKey = key;
        try {
            FirebaseFirestore db = firestore(this);
            registration = db.collection("couples").document(Config.ownerId(this)).collection("reminders")
                    .addSnapshotListener(MetadataChanges.INCLUDE, this::onSnapshot);
        } catch (Exception e) {
            registration = null;
            listeningKey = null;
            setStatus(false, "⚠️ Gagal menyambung, mencoba lagi…");
            handler.postDelayed(retry, RETRY_MS);
        }
    }

    private void onSnapshot(QuerySnapshot snap, FirebaseFirestoreException error) {
        if (error != null) {
            // Listener berhenti setelah error → coba sambung ulang sebentar lagi.
            if (registration != null) registration.remove();
            registration = null;
            listeningKey = null;
            String msg = error.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED
                    ? "⚠️ Ditolak server. Cek Rules Firestore."
                    : "⚠️ Terputus, mencoba lagi…";
            Config.setSyncResult(this, msg.replace("⚠️ ", ""));
            setStatus(false, msg);
            handler.postDelayed(retry, RETRY_MS);
            return;
        }
        if (snap == null) return;
        if (snap.getMetadata().isFromCache()) {
            // Sedang offline. Jangan ubah alarm: yang tersimpan di HP tetap berbunyi.
            setStatus(false, "Menunggu internet · alarm yang tersimpan tetap bunyi");
            return;
        }

        List<Reminder> list = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) list.add(Reminder.fromSnapshot(d));
        Config.saveReminders(this, list);
        Config.setSyncResult(this, null);
        Scheduler.scheduleAll(this);
        lastServerEventAt = System.currentTimeMillis();
        setStatus(true, "Tersambung · " + list.size() + " pengingat · perubahan langsung diterima");

        boolean changed = !snap.getDocumentChanges().isEmpty();
        long now = System.currentTimeMillis();
        if (changed || now - lastHeartbeat > HEARTBEAT_MS) {
            lastHeartbeat = now;
            final Context app = getApplicationContext();
            final int count = list.size();
            new Thread(() -> FirestoreApi.heartbeat(app, count)).start();
        }
    }

    /** Firebase diinisialisasi dari kode setup (tidak perlu file google-services.json). */
    private static synchronized FirebaseFirestore firestore(Context c) {
        FirebaseOptions opts = new FirebaseOptions.Builder()
                .setApiKey(Config.apiKey(c))
                .setApplicationId(Config.appId(c))
                .setProjectId(Config.projectId(c))
                .build();
        FirebaseApp app = null;
        for (FirebaseApp a : FirebaseApp.getApps(c)) {
            if (FIREBASE_APP.equals(a.getName())) app = a;
        }
        if (app != null) {
            FirebaseOptions o = app.getOptions();
            boolean same = opts.getApiKey().equals(o.getApiKey())
                    && opts.getApplicationId().equals(o.getApplicationId())
                    && String.valueOf(opts.getProjectId()).equals(String.valueOf(o.getProjectId()));
            if (!same) {
                app.delete();
                app = null;
            }
        }
        boolean fresh = app == null;
        if (fresh) app = FirebaseApp.initializeApp(c.getApplicationContext(), opts, FIREBASE_APP);
        FirebaseFirestore db = FirebaseFirestore.getInstance(app);
        if (fresh) {
            try {
                // Tanpa cache di disk: salinan pengingat sudah kita simpan sendiri.
                db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                        .build());
            } catch (Exception ignored) {
            }
        }
        return db;
    }

    // ---------- notifikasi status ----------

    private void setStatus(boolean isConnected, String text) {
        connected = isConnected;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
        notifyUi();
    }

    private void notifyUi() {
        sendBroadcast(new Intent(BROADCAST_DATA).setPackage(getPackageName()));
    }

    private Notification buildNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(CH_STATUS, "Status sambungan",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Boleh dimatikan; tidak memengaruhi alarm.");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 8,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CH_STATUS)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFE11D48)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void startForegroundCompat(Notification n) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }
}
