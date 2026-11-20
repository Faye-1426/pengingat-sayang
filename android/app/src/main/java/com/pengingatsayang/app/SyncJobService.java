package com.pengingatsayang.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Dijalankan Android tiap ±15 menit untuk mengambil pengingat terbaru. */
public class SyncJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Scheduler.syncNow(getApplicationContext());
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
