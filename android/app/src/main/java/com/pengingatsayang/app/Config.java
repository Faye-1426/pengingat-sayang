package com.pengingatsayang.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Semua data yang disimpan di HP: kode setup, salinan daftar pengingat, status sinkron. */
final class Config {
    private Config() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("pengingat", Context.MODE_PRIVATE);
    }

    static boolean isConfigured(Context c) {
        SharedPreferences p = prefs(c);
        return !p.getString("projectId", "").isEmpty()
                && !p.getString("apiKey", "").isEmpty()
                && !p.getString("ownerId", "").isEmpty();
    }

    static String projectId(Context c) { return prefs(c).getString("projectId", ""); }
    static String apiKey(Context c) { return prefs(c).getString("apiKey", ""); }
    static String ownerId(Context c) { return prefs(c).getString("ownerId", ""); }
    static String senderName(Context c) { return prefs(c).getString("senderName", ""); }
    /** App ID web Firebase (kode setup lama belum punya, jadi pakai pengganti). */
    static String appId(Context c) {
        String a = prefs(c).getString("appId", "");
        return a.isEmpty() ? "1:000000000000:android:0000000000000000" : a;
    }

    /** Kode setup dari halaman admin: "PS1:" + base64(JSON {p, k, u, n}). Return null kalau berhasil. */
    static String applySetupCode(Context c, String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Kode masih kosong.";
        String s = raw.replaceAll("\\s+", "");
        int idx = s.indexOf("PS1:");
        if (idx >= 0) s = s.substring(idx + 4);
        try {
            byte[] bytes = Base64.decode(s, Base64.DEFAULT);
            JSONObject o = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            String p = o.optString("p", "");
            String k = o.optString("k", "");
            String u = o.optString("u", "");
            String n = o.optString("n", "");
            String a = o.optString("a", "");
            if (p.isEmpty() || k.isEmpty() || u.isEmpty()) return "Kode tidak lengkap.";
            prefs(c).edit()
                    .putString("projectId", p)
                    .putString("apiKey", k)
                    .putString("ownerId", u)
                    .putString("senderName", n)
                    .putString("appId", a)
                    .apply();
            return null;
        } catch (Exception e) {
            return "Kode tidak valid. Pastikan disalin lengkap dari halaman admin (diawali PS1:).";
        }
    }

    static void disconnect(Context c) {
        prefs(c).edit().clear().apply();
    }

    // ---------- salinan daftar pengingat ----------

    static void saveReminders(Context c, List<Reminder> list) {
        JSONArray a = new JSONArray();
        for (Reminder r : list) {
            try {
                a.put(r.toJson());
            } catch (Exception ignored) {
            }
        }
        prefs(c).edit().putString("cache", a.toString()).apply();
    }

    static List<Reminder> loadReminders(Context c) {
        List<Reminder> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("cache", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) out.add(Reminder.fromJson(o));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static Reminder findReminder(Context c, String id) {
        if (id == null) return null;
        for (Reminder r : loadReminders(c)) if (id.equals(r.id)) return r;
        return null;
    }

    // ---------- alarm yang sedang terjadwal ----------

    static Set<String> scheduledIds(Context c) {
        return new HashSet<>(prefs(c).getStringSet("scheduled", new HashSet<>()));
    }

    static void setScheduledIds(Context c, Set<String> ids) {
        prefs(c).edit().putStringSet("scheduled", new HashSet<>(ids)).apply();
    }

    // ---------- status sinkron ----------

    static void setSyncResult(Context c, String error) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (error == null) {
            e.putLong("lastSyncOk", System.currentTimeMillis()).remove("lastSyncError");
        } else {
            e.putString("lastSyncError", error);
        }
        e.apply();
    }

    static long lastSyncOk(Context c) { return prefs(c).getLong("lastSyncOk", 0); }
    static String lastSyncError(Context c) { return prefs(c).getString("lastSyncError", null); }
}
