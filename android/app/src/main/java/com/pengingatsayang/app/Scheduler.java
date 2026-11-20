package com.pengingatsayang.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Menjadwalkan alarm di HP dan sinkron berkala dengan halaman admin. */
final class Scheduler {
    private Scheduler() {}

    static final String ACTION_FIRE = "com.pengingatsayang.app.FIRE";
    static final String EXTRA_ID = "id";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_MESSAGE = "message";
    private static final int SYNC_JOB_ID = 1001;
    private static final Object SYNC_LOCK = new Object();
    static final long SNOOZE_MS = 5 * 60 * 1000L;

    // ---------- sinkron ----------

    /** Ambil daftar terbaru dari internet lalu jadwalkan ulang. Panggil dari background thread. */
    static String syncNow(Context c) {
        synchronized (SYNC_LOCK) {
            return syncLocked(c);
        }
    }

    private static String syncLocked(Context c) {
        if (!Config.isConfigured(c)) return "Belum terhubung";
        String error = null;
        try {
            List<Reminder> list = FirestoreApi.fetchReminders(c);
            Config.saveReminders(c, list);
            Config.setSyncResult(c, null);
            scheduleAll(c);
            FirestoreApi.heartbeat(c, list.size());
        } catch (Exception e) {
            error = e.getMessage() != null ? e.getMessage() : "Tidak ada koneksi internet";
            if (error.contains("Unable to resolve host") || error.contains("timeout")
                    || error.contains("failed to connect")) {
                error = "Tidak ada koneksi internet";
            }
            Config.setSyncResult(c, error);
            scheduleAll(c); // tetap pakai salinan terakhir
        }
        return error;
    }

    /** Sinkron otomatis tiap ±15 menit (batas minimum Android), tetap jalan setelah HP restart. */
    static void ensureSyncJob(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        if (js.getPendingJob(SYNC_JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(SYNC_JOB_ID, new ComponentName(c, SyncJobService.class))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();
        try {
            js.schedule(job);
        } catch (Exception ignored) {
        }
    }

    static void cancelSyncJob(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js != null) js.cancel(SYNC_JOB_ID);
    }

    // ---------- alarm ----------

    /** Jadwalkan ulang semua pengingat dari salinan lokal. */
    static synchronized void scheduleAll(Context c) {
        List<Reminder> list = Config.loadReminders(c);
        Set<String> old = Config.scheduledIds(c);
        Set<String> now = new HashSet<>();
        long t0 = System.currentTimeMillis();
        for (Reminder r : list) {
            long at = r.nextTrigger(t0);
            if (at > 0) {
                setAlarm(c, at, firePi(c, "r", r.id, r.title, r.message, PendingIntent.FLAG_UPDATE_CURRENT));
                now.add(r.id);
            }
        }
        for (String id : old) {
            if (!now.contains(id)) cancel(c, "r", id);
        }
        Config.setScheduledIds(c, now);
    }

    /** Setelah satu alarm berbunyi: jadwalkan kemunculan berikutnya (untuk yang berulang). */
    static synchronized void rescheduleOne(Context c, String id) {
        Reminder r = Config.findReminder(c, id);
        Set<String> ids = Config.scheduledIds(c);
        long at = r == null ? -1 : r.nextTrigger(System.currentTimeMillis());
        if (at > 0) {
            setAlarm(c, at, firePi(c, "r", r.id, r.title, r.message, PendingIntent.FLAG_UPDATE_CURRENT));
            ids.add(id);
        } else {
            ids.remove(id);
        }
        Config.setScheduledIds(c, ids);
    }

    static void snooze(Context c, String id, String title, String message) {
        long at = System.currentTimeMillis() + SNOOZE_MS;
        setAlarm(c, at, firePi(c, "snooze", id, title, message, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    static void testAlarm(Context c, long delayMs) {
        long at = System.currentTimeMillis() + delayMs;
        setAlarm(c, at, firePi(c, "tes", "tes-" + at,
                "Tes alarm berhasil! 🎉",
                "Kalau kamu dengar bunyi ini, semuanya sudah siap.",
                PendingIntent.FLAG_UPDATE_CURRENT));
    }

    static void cancelAll(Context c) {
        for (String id : Config.scheduledIds(c)) cancel(c, "r", id);
        Config.setScheduledIds(c, new HashSet<>());
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    private static PendingIntent firePi(Context c, String kind, String id, String title, String message, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(ACTION_FIRE)
                .setData(Uri.parse("pengingat://" + kind + "/" + Uri.encode(id)))
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message);
        return PendingIntent.getBroadcast(c, 0, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setAlarm(Context c, long at, PendingIntent pi) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null || pi == null) return;
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent showPi = PendingIntent.getActivity(c, 7, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (canExact(c)) {
                // setAlarmClock = jenis alarm paling kuat di Android, sama seperti aplikasi Jam.
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showPi), pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static void cancel(Context c, String kind, String id) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = firePi(c, kind, id, "", "", PendingIntent.FLAG_NO_CREATE);
        if (pi != null && am != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }
}
