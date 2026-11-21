package com.pengingatsayang.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Layar utama: sekali setup, setelah itu cukup dibiarkan. */
public class MainActivity extends Activity {
    private static final Locale ID = Locale.forLanguageTag("id-ID");
    private boolean syncing = false;
    private String setupDraft = "";
    private BroadcastReceiver dataReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        AlarmService.ensureChannels(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        dataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                render(); // perubahan dari admin masuk → tampilan ikut berubah
            }
        };
        IntentFilter f = new IntentFilter(RealtimeService.BROADCAST_DATA);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dataReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(dataReceiver, f);

        render();
        if (Config.isConfigured(this)) {
            Scheduler.ensureSyncJob(this);
            RealtimeService.start(this);
            syncInBackground(false);
        }
    }

    @Override
    protected void onPause() {
        if (dataReceiver != null) {
            try { unregisterReceiver(dataReceiver); } catch (Exception ignored) { }
            dataReceiver = null;
        }
        super.onPause();
    }

    private void syncInBackground(boolean showToast) {
        if (syncing) return;
        syncing = true;
        render();
        new Thread(() -> {
            String err = Scheduler.syncNow(getApplicationContext());
            runOnUiThread(() -> {
                syncing = false;
                if (showToast) {
                    Toast.makeText(this, err == null ? "Sudah yang terbaru 💗" : err, Toast.LENGTH_SHORT).show();
                }
                if (!isFinishing()) render();
            });
        }).start();
    }

    // =====================================================================

    private void render() {
        LinearLayout root = Ui.vertical(this);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, Ui.dp(this, 28), pad, Ui.dp(this, 40));

        root.addView(Ui.text(this, "💗 " + getString(R.string.app_name), 26, Ui.INK, true));
        String sender = Config.senderName(this);
        TextView sub = Ui.text(this, Config.isConfigured(this)
                ? (sender.isEmpty() ? "Semua pengingat diatur dari halaman admin." : "Semua pengingat diatur oleh " + sender + ".")
                : "Satu langkah lagi untuk menghubungkan aplikasi.", 15, Ui.MUTED, false);
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 18));
        root.addView(sub);

        if (!Config.isConfigured(this)) {
            root.addView(setupCard());
        } else {
            root.addView(nextCard());
            root.addView(checklistCard());
            root.addView(listCard());
            root.addView(footer());
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.addView(root);
        setContentView(sv);
    }

    // ---------- setup ----------

    private LinearLayout setupCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "Hubungkan ke halaman admin", 18, Ui.INK, true));
        TextView info = Ui.text(this,
                "Buka halaman admin → panel \"Hubungkan HP pacar\" → salin kode setup (diawali PS1:), lalu tempel di sini.",
                14, Ui.MUTED, false);
        info.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        card.addView(info);

        EditText input = new EditText(this);
        input.setHint("PS1:…");
        input.setText(setupDraft);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setTextSize(13);
        input.setBackground(Ui.rounded(this, Ui.ROSE_SOFT, 12));
        int p = Ui.dp(this, 12);
        input.setPadding(p, p, p, p);
        card.addView(input);

        Button paste = Ui.button(this, "Tempel dari clipboard", false);
        paste.setOnClickListener(v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null) {
                input.setText(clip.getItemAt(0).getText().toString());
            } else {
                Toast.makeText(this, "Clipboard kosong", Toast.LENGTH_SHORT).show();
            }
        });
        card.addView(paste);

        Button connect = Ui.button(this, "Hubungkan", true);
        connect.setOnClickListener(v -> {
            setupDraft = input.getText().toString();
            String err = Config.applySetupCode(this, setupDraft);
            if (err != null) {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                return;
            }
            setupDraft = "";
            Toast.makeText(this, "Terhubung! 💗", Toast.LENGTH_SHORT).show();
            askNotificationPermission();
            Scheduler.ensureSyncJob(this);
            RealtimeService.stop(this); // sambung ulang dengan kode yang baru
            RealtimeService.start(this);
            syncInBackground(false);
        });
        card.addView(connect);
        return card;
    }

    // ---------- pengingat berikutnya ----------

    private LinearLayout nextCard() {
        LinearLayout card = Ui.card(this);
        card.setBackground(Ui.rounded(this, Ui.ROSE, 20));
        card.addView(Ui.text(this, "Pengingat berikutnya", 14, 0xFFFFE4E6, false));

        Reminder best = null;
        long bestAt = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        for (Reminder r : Config.loadReminders(this)) {
            long at = r.nextTrigger(now);
            if (at > 0 && at < bestAt) {
                bestAt = at;
                best = r;
            }
        }
        if (best == null) {
            card.addView(Ui.text(this, "Belum ada", 26, 0xFFFFFFFF, true));
            card.addView(Ui.text(this, "Santai dulu ya 😊", 15, 0xFFFFE4E6, false));
        } else {
            card.addView(Ui.text(this, formatWhen(bestAt), 26, 0xFFFFFFFF, true));
            card.addView(Ui.text(this, best.title, 17, 0xFFFFFFFF, false));
        }
        return card;
    }

    // ---------- pengecekan pengaturan HP ----------

    private LinearLayout checklistCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "Supaya alarm pasti bunyi", 18, Ui.INK, true));
        TextView hint = Ui.text(this, "Pastikan semuanya ✅. Cukup sekali saja.", 14, Ui.MUTED, false);
        hint.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        card.addView(hint);

        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean notifOk = nm != null && nm.areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        addCheck(card, "Notifikasi diizinkan", notifOk, () -> {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && !Config.prefs(this).getBoolean("askedNotif", false)) {
                askNotificationPermission();
            } else {
                open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            }
        });

        addCheck(card, "Alarm tepat waktu", Scheduler.canExact(this), () -> {
            if (Build.VERSION.SDK_INT >= 31) {
                open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri()));
            }
        });

        boolean fullOk = Build.VERSION.SDK_INT < 34 || (nm != null && nm.canUseFullScreenIntent());
        addCheck(card, "Layar alarm saat HP terkunci", fullOk, () -> {
            if (Build.VERSION.SDK_INT >= 34) {
                open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri()));
            }
        });

        PowerManager pm = getSystemService(PowerManager.class);
        boolean batteryOk = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        addCheck(card, "Tidak dibatasi penghemat baterai", batteryOk, () -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri()));
            } catch (Exception e) {
                open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });

        AudioManager am = getSystemService(AudioManager.class);
        boolean volOk = am != null && am.getStreamVolume(AudioManager.STREAM_ALARM) > 0;
        addCheck(card, "Volume alarm tidak nol", volOk, () -> {
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, max * 7 / 10), AudioManager.FLAG_SHOW_UI);
            }
            render();
        });

        String brand = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
        String[] strict = {"xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "oneplus", "huawei",
                "honor", "infinix", "tecno", "itel", "asus", "meizu"};
        boolean isStrict = false;
        for (String s : strict) if (brand.contains(s)) isStrict = true;
        if (isStrict) {
            TextView t = Ui.text(this,
                    "📌 HP " + Build.MANUFACTURER + " biasanya butuh 1 langkah tambahan: buka Info aplikasi → aktifkan \"Mulai otomatis / Autostart\" dan set Baterai ke \"Tidak dibatasi\".",
                    13.5f, Ui.WARN, false);
            t.setPadding(0, Ui.dp(this, 10), 0, 0);
            card.addView(t);
            Button b = Ui.button(this, "Buka Info aplikasi", false);
            b.setOnClickListener(v -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())));
            card.addView(b);
        }

        Button test = Ui.button(this, "🔔 Tes alarm (bunyi 10 detik lagi)", true);
        test.setOnClickListener(v -> {
            askNotificationPermission();
            Scheduler.testAlarm(this, 10_000);
            Toast.makeText(this, "Kunci layar HP sekarang, tunggu 10 detik…", Toast.LENGTH_LONG).show();
        });
        card.addView(test);
        return card;
    }

    private void addCheck(LinearLayout card, String label, boolean ok, Runnable fix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 2));

        TextView t = Ui.text(this, (ok ? "✅  " : "⚠️  ") + label, 15, ok ? Ui.INK : Ui.WARN, !ok);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        if (!ok) {
            Button b = Ui.button(this, "Atur", true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            b.setLayoutParams(lp);
            b.setMinHeight(Ui.dp(this, 40));
            b.setMinWidth(Ui.dp(this, 72));
            b.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
            b.setOnClickListener(v -> fix.run());
            row.addView(b);
        }
        card.addView(row);
    }

    // ---------- daftar ----------

    private LinearLayout listCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "Daftar pengingat", 18, Ui.INK, true));

        long now = System.currentTimeMillis();
        List<Object[]> upcoming = new ArrayList<>();
        for (Reminder r : Config.loadReminders(this)) {
            long at = r.nextTrigger(now);
            if (at > 0) upcoming.add(new Object[]{at, r});
        }
        Collections.sort(upcoming, (a, b) -> Long.compare((Long) a[0], (Long) b[0]));

        if (upcoming.isEmpty()) {
            TextView t = Ui.text(this, "Belum ada pengingat yang aktif.", 14, Ui.MUTED, false);
            t.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(t);
        }
        for (Object[] item : upcoming) {
            Reminder r = (Reminder) item[1];
            LinearLayout row = Ui.vertical(this);
            row.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
            row.addView(Ui.text(this, r.time + "  ·  " + r.repeatLabel(), 13, Ui.ROSE_DARK, true));
            row.addView(Ui.text(this, r.title, 16, Ui.INK, true));
            if (!r.message.isEmpty()) row.addView(Ui.text(this, r.message, 14, Ui.MUTED, false));
            row.addView(Ui.text(this, "Berikutnya: " + formatWhen((Long) item[0]), 12.5f, Ui.MUTED, false));
            card.addView(row);
        }
        return card;
    }

    // ---------- bawah ----------

    private LinearLayout footer() {
        LinearLayout box = Ui.vertical(this);
        String status;
        int color = Ui.MUTED;
        if (RealtimeService.connected) {
            status = "🟢 Tersambung langsung — perubahan dari admin otomatis masuk";
            color = Ui.OK;
        } else if (syncing) {
            status = "⏳ Menyambungkan…";
        } else {
            status = "⏳ Menyambungkan… (cadangan: dicek otomatis tiap ±15 menit)";
            String err = Config.lastSyncError(this);
            if (err != null) status += "\n⚠️ " + err;
        }
        long ok = Config.lastSyncOk(this);
        if (ok > 0) status += "\nTerakhir diperbarui: " + formatWhen(ok);
        TextView t = Ui.text(this, status, 13, color, false);
        t.setGravity(Gravity.CENTER);
        box.addView(t);

        TextView reset = Ui.text(this, "Ganti / putuskan kode setup", 13, Ui.MUTED, false);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, Ui.dp(this, 18), 0, 0);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Putuskan?")
                .setMessage("Semua alarm di HP ini akan dihapus sampai kode setup dimasukkan lagi.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Putuskan", (d, w) -> {
                    Scheduler.cancelAll(this);
                    Scheduler.cancelSyncJob(this);
                    RealtimeService.stop(this);
                    Config.disconnect(this);
                    render();
                })
                .show());
        box.addView(reset);
        return box;
    }

    // ---------- helper ----------

    private String formatWhen(long at) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        Calendar today = Calendar.getInstance();
        String hm = new SimpleDateFormat("HH:mm", ID).format(new Date(at));
        if (sameDay(c, today)) return "Hari ini " + hm;
        today.add(Calendar.DAY_OF_YEAR, 1);
        if (sameDay(c, today)) return "Besok " + hm;
        today.add(Calendar.DAY_OF_YEAR, -2);
        if (sameDay(c, today)) return "Kemarin " + hm;
        return new SimpleDateFormat("EEE, d MMM · HH:mm", ID).format(new Date(at));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private Uri packageUri() {
        return Uri.parse("package:" + getPackageName());
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()));
            } catch (Exception ignored) {
                Toast.makeText(this, "Buka Pengaturan → Aplikasi → " + getString(R.string.app_name), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Config.prefs(this).edit().putBoolean("askedNotif", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        render();
    }
}
