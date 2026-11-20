package com.pengingatsayang.app;

import java.util.Calendar;

/** Menghitung kapan sebuah pengingat harus berbunyi berikutnya (pakai zona waktu HP). */
final class TimeCalc {
    private TimeCalc() {}

    /**
     * @param repeat "once" (sekali), "daily" (setiap hari) atau "weekly" (hari tertentu)
     * @param time   "HH:mm"
     * @param date   "yyyy-MM-dd" (hanya untuk "once")
     * @param days   1=Senin ... 7=Minggu (hanya untuk "weekly")
     * @param after  waktu acuan (ms); hasil selalu &gt; after
     * @return waktu berbunyi (ms) atau -1 kalau tidak ada lagi
     */
    static long next(String repeat, String time, String date, int[] days, boolean active, long after) {
        if (!active) return -1;
        int[] hm = parseTime(time);
        if (hm == null) return -1;

        if ("once".equals(repeat)) {
            int[] ymd = parseDate(date);
            if (ymd == null) return -1;
            Calendar c = Calendar.getInstance();
            c.set(ymd[0], ymd[1] - 1, ymd[2], hm[0], hm[1], 0);
            c.set(Calendar.MILLISECOND, 0);
            long t = c.getTimeInMillis();
            return t > after ? t : -1;
        }

        boolean daily = "daily".equals(repeat);
        boolean weekly = "weekly".equals(repeat);
        if (!daily && !weekly) return -1;

        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(after);
        for (int i = 0; i <= 8; i++) {
            Calendar c = (Calendar) base.clone();
            c.add(Calendar.DAY_OF_YEAR, i);
            c.set(Calendar.HOUR_OF_DAY, hm[0]);
            c.set(Calendar.MINUTE, hm[1]);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long t = c.getTimeInMillis();
            if (t <= after) continue;
            if (daily) return t;
            if (contains(days, isoDay(c))) return t;
        }
        return -1;
    }

    /** 1=Senin ... 7=Minggu */
    static int isoDay(Calendar c) {
        int d = c.get(Calendar.DAY_OF_WEEK); // Minggu=1 ... Sabtu=7
        return d == Calendar.SUNDAY ? 7 : d - 1;
    }

    static int[] parseTime(String s) {
        if (s == null) return null;
        String[] p = s.trim().split(":");
        if (p.length < 2) return null;
        try {
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return new int[]{h, m};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int[] parseDate(String s) {
        if (s == null) return null;
        String[] p = s.trim().split("-");
        if (p.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean contains(int[] arr, int v) {
        if (arr == null) return false;
        for (int x : arr) if (x == v) return true;
        return false;
    }
}
