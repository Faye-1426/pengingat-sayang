package com.pengingatsayang.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Satu pengingat, sama seperti yang kamu isi di halaman admin. */
final class Reminder {
    String id = "";
    String title = "Pengingat";
    String message = "";
    String time = "";
    String date = "";
    String repeat = "once";
    int[] days = new int[0];
    boolean active = true;

    long nextTrigger(long after) {
        return TimeCalc.next(repeat, time, date, days, active, after);
    }

    String repeatLabel() {
        if ("daily".equals(repeat)) return "Setiap hari";
        if ("weekly".equals(repeat)) {
            String[] names = {"", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"};
            if (days.length == 7) return "Setiap hari";
            StringBuilder sb = new StringBuilder();
            for (int d = 1; d <= 7; d++) {
                for (int x : days) {
                    if (x == d) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(names[d]);
                    }
                }
            }
            return sb.length() == 0 ? "Mingguan" : "Setiap " + sb;
        }
        return "Sekali";
    }

    // ---------- dari format REST Firestore ----------

    static Reminder fromFirestore(JSONObject doc) {
        Reminder r = new Reminder();
        String name = doc.optString("name", "");
        r.id = name.substring(name.lastIndexOf('/') + 1);
        JSONObject f = doc.optJSONObject("fields");
        if (f == null) f = new JSONObject();
        r.title = str(f, "title", "Pengingat");
        if (r.title.trim().isEmpty()) r.title = "Pengingat";
        r.message = str(f, "message", "");
        r.time = str(f, "time", "");
        r.date = str(f, "date", "");
        r.repeat = str(f, "repeat", "once");
        r.active = bool(f, "active", true);

        JSONObject daysObj = f.optJSONObject("days");
        if (daysObj != null) {
            JSONObject arr = daysObj.optJSONObject("arrayValue");
            JSONArray values = arr == null ? null : arr.optJSONArray("values");
            if (values != null) {
                int[] out = new int[values.length()];
                int n = 0;
                for (int i = 0; i < values.length(); i++) {
                    JSONObject v = values.optJSONObject(i);
                    if (v == null) continue;
                    try {
                        if (v.has("integerValue")) out[n++] = Integer.parseInt(v.getString("integerValue"));
                        else if (v.has("doubleValue")) out[n++] = (int) v.getDouble("doubleValue");
                    } catch (Exception ignored) {
                    }
                }
                r.days = new int[n];
                System.arraycopy(out, 0, r.days, 0, n);
            }
        }
        return r;
    }

    private static String str(JSONObject f, String key, String def) {
        JSONObject v = f.optJSONObject(key);
        if (v == null || !v.has("stringValue")) return def;
        return v.optString("stringValue", def);
    }

    private static boolean bool(JSONObject f, String key, boolean def) {
        JSONObject v = f.optJSONObject(key);
        if (v == null || !v.has("booleanValue")) return def;
        return v.optBoolean("booleanValue", def);
    }

    // ---------- simpan lokal (supaya tetap jalan tanpa internet / setelah restart) ----------

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("message", message);
        o.put("time", time);
        o.put("date", date);
        o.put("repeat", repeat);
        o.put("active", active);
        JSONArray a = new JSONArray();
        for (int d : days) a.put(d);
        o.put("days", a);
        return o;
    }

    static Reminder fromJson(JSONObject o) {
        Reminder r = new Reminder();
        r.id = o.optString("id", "");
        r.title = o.optString("title", "Pengingat");
        r.message = o.optString("message", "");
        r.time = o.optString("time", "");
        r.date = o.optString("date", "");
        r.repeat = o.optString("repeat", "once");
        r.active = o.optBoolean("active", true);
        JSONArray a = o.optJSONArray("days");
        if (a != null) {
            r.days = new int[a.length()];
            for (int i = 0; i < a.length(); i++) r.days[i] = a.optInt(i);
        }
        return r;
    }
}
