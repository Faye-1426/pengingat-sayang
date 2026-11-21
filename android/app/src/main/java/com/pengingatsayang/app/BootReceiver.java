package com.pengingatsayang.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Setelah HP restart / jam diubah / aplikasi di-update: pasang ulang semua alarm. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        if (!Config.isConfigured(app)) return;
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Scheduler.scheduleAll(app);
                Scheduler.ensureSyncJob(app);
                RealtimeService.start(app);
            } finally {
                pending.finish();
            }
        }).start();
    }
}
