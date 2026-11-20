package com.pengingatsayang.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dipanggil Android tepat pada jam pengingat. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Scheduler.ACTION_FIRE.equals(intent.getAction())) return;
        final Context app = context.getApplicationContext();
        final String id = intent.getStringExtra(Scheduler.EXTRA_ID);
        String title = intent.getStringExtra(Scheduler.EXTRA_TITLE);
        String message = intent.getStringExtra(Scheduler.EXTRA_MESSAGE);

        Intent svc = new Intent(app, AlarmService.class)
                .setAction(AlarmService.ACTION_START)
                .putExtra(Scheduler.EXTRA_ID, id)
                .putExtra(Scheduler.EXTRA_TITLE, title)
                .putExtra(Scheduler.EXTRA_MESSAGE, message);
        try {
            app.startForegroundService(svc);
        } catch (Exception e) {
            // Kalau Android menolak (jarang), tetap tampilkan notifikasi yang berbunyi terus.
            AlarmService.showFallbackNotification(app, title, message);
        }

        // Jadwalkan kemunculan berikutnya untuk pengingat berulang.
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                if (id != null && !id.startsWith("tes-")) Scheduler.rescheduleOne(app, id);
            } finally {
                pending.finish();
            }
        }).start();
    }
}
