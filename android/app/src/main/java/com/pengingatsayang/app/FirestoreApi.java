package com.pengingatsayang.app;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Bicara langsung ke Firestore lewat REST API (tanpa library Firebase),
 * jadi aplikasinya kecil dan tidak perlu file google-services.json.
 * Semua method di sini harus dipanggil dari background thread.
 */
final class FirestoreApi {
    private FirestoreApi() {}

    private static String coupleBase(Context c) {
        return "https://firestore.googleapis.com/v1/projects/" + Uri.encode(Config.projectId(c))
                + "/databases/(default)/documents/couples/" + Uri.encode(Config.ownerId(c));
    }

    private static String key(Context c) {
        return "key=" + Uri.encode(Config.apiKey(c));
    }

    /** Ambil semua pengingat. */
    static List<Reminder> fetchReminders(Context c) throws IOException {
        List<Reminder> out = new ArrayList<>();
        String pageToken = null;
        int guard = 0;
        do {
            String url = coupleBase(c) + "/reminders?pageSize=300&" + key(c)
                    + (pageToken != null ? "&pageToken=" + Uri.encode(pageToken) : "");
            String body = request("GET", url, null);
            try {
                JSONObject o = new JSONObject(body);
                JSONArray docs = o.optJSONArray("documents");
                if (docs != null) {
                    for (int i = 0; i < docs.length(); i++) {
                        JSONObject d = docs.optJSONObject(i);
                        if (d != null) out.add(Reminder.fromFirestore(d));
                    }
                }
                pageToken = o.optString("nextPageToken", "");
                if (pageToken.isEmpty()) pageToken = null;
            } catch (org.json.JSONException e) {
                throw new IOException("Balasan server tidak bisa dibaca");
            }
        } while (pageToken != null && ++guard < 10);
        return out;
    }

    /** Kirim status ke halaman admin: dimatikan / ditunda / tidak direspons. Gagal = diabaikan. */
    static void ack(Context c, String reminderId, String action) {
        if (reminderId == null || reminderId.isEmpty() || reminderId.startsWith("tes-")) return;
        try {
            String url = coupleBase(c) + "/reminders/" + Uri.encode(reminderId) + "?" + key(c)
                    + "&updateMask.fieldPaths=lastAckAt&updateMask.fieldPaths=lastAckAction"
                    + "&currentDocument.exists=true";
            JSONObject fields = new JSONObject();
            fields.put("lastAckAt", new JSONObject().put("timestampValue", isoNow()));
            fields.put("lastAckAction", new JSONObject().put("stringValue", action));
            request("PATCH", url, new JSONObject().put("fields", fields).toString());
        } catch (Exception ignored) {
        }
    }

    /** Laporan "HP pacar masih aktif" supaya kamu bisa lihat di halaman admin. Gagal = diabaikan. */
    static void heartbeat(Context c, int reminderCount) {
        try {
            String url = coupleBase(c) + "/devices/main?" + key(c)
                    + "&updateMask.fieldPaths=lastSyncAt&updateMask.fieldPaths=model"
                    + "&updateMask.fieldPaths=androidVersion&updateMask.fieldPaths=appVersion"
                    + "&updateMask.fieldPaths=reminderCount";
            JSONObject f = new JSONObject();
            f.put("lastSyncAt", new JSONObject().put("timestampValue", isoNow()));
            f.put("model", new JSONObject().put("stringValue", Build.MANUFACTURER + " " + Build.MODEL));
            f.put("androidVersion", new JSONObject().put("stringValue", Build.VERSION.RELEASE));
            f.put("appVersion", new JSONObject().put("stringValue", appVersion(c)));
            f.put("reminderCount", new JSONObject().put("integerValue", String.valueOf(reminderCount)));
            request("PATCH", url, new JSONObject().put("fields", f).toString());
        } catch (Exception ignored) {
        }
    }

    // ---------- HTTP ----------

    private static String request(String method, String url, String jsonBody) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(15000);
            con.setReadTimeout(20000);
            try {
                con.setRequestMethod(method);
            } catch (ProtocolException e) {
                // Cadangan kalau PATCH tidak didukung
                con.setRequestMethod("POST");
                con.setRequestProperty("X-HTTP-Method-Override", method);
            }
            if (jsonBody != null) {
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] b = jsonBody.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(b);
                }
            }
            int code = con.getResponseCode();
            InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
            String body = readAll(in);
            if (code >= 400) throw new IOException(friendlyError(code, body));
            return body;
        } finally {
            con.disconnect();
        }
    }

    private static String friendlyError(int code, String body) {
        if (code == 403) return "Ditolak server (403). Cek Rules Firestore di panduan.";
        if (code == 404) return "Database tidak ditemukan (404). Cek kode setup.";
        if (code == 400 && body != null && body.contains("API key")) return "API key salah. Ulangi kode setup.";
        return "Server error " + code;
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }

    static String appVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private static String isoNow() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
}
