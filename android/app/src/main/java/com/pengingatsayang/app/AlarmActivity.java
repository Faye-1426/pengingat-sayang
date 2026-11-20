package com.pengingatsayang.app;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Layar penuh yang muncul saat alarm berbunyi (juga di atas layar kunci). */
public class AlarmActivity extends Activity {
    private BroadcastReceiver changed;
    private ObjectAnimator pulse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFFBE123C);
        getWindow().setNavigationBarColor(0xFF881337);

        changed = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                render();
            }
        };
        IntentFilter f = new IntentFilter(AlarmService.BROADCAST_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(changed, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(changed, f);
        }
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        render();
    }

    @Override
    protected void onDestroy() {
        if (changed != null) {
            try { unregisterReceiver(changed); } catch (Exception ignored) { }
        }
        if (pulse != null) pulse.cancel();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Sengaja tidak menutup layar: harus pilih Matikan atau Tunda.
    }

    private void render() {
        if (!AlarmService.ringing || AlarmService.current.isEmpty()) {
            finish();
            return;
        }
        String[] last = AlarmService.current.get(AlarmService.current.size() - 1);

        LinearLayout root = Ui.vertical(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFBE123C, 0xFFE11D48, 0xFF881337});
        root.setBackground(bg);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 28);
        root.setPadding(pad, Ui.dp(this, 56), pad, Ui.dp(this, 36));

        TextView clock = Ui.text(this, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()),
                64, 0xFFFFFFFF, true);
        clock.setGravity(Gravity.CENTER);
        root.addView(clock);

        String sender = Config.senderName(this);
        TextView from = Ui.text(this, sender.isEmpty() ? "💌 pengingat untukmu" : "💌 dari " + sender,
                16, 0xFFFFE4E6, false);
        from.setGravity(Gravity.CENTER);
        root.addView(from);

        TextView heart = Ui.text(this, "💗", 72, 0xFFFFFFFF, false);
        heart.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(this, 28);
        hl.bottomMargin = Ui.dp(this, 20);
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        heart.setLayoutParams(hl);
        root.addView(heart);
        if (pulse != null) pulse.cancel();
        pulse = ObjectAnimator.ofPropertyValuesHolder(heart,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.18f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.18f));
        pulse.setDuration(650);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();

        TextView title = Ui.text(this, last[1], 30, 0xFFFFFFFF, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        if (!last[2].isEmpty()) {
            TextView msg = Ui.text(this, last[2], 18, 0xFFFFF1F2, false);
            msg.setGravity(Gravity.CENTER);
            msg.setPadding(0, Ui.dp(this, 10), 0, 0);
            root.addView(msg);
        }

        if (AlarmService.current.size() > 1) {
            StringBuilder sb = new StringBuilder("Juga:");
            for (int i = 0; i < AlarmService.current.size() - 1; i++) {
                sb.append("\n• ").append(AlarmService.current.get(i)[1]);
            }
            TextView others = Ui.text(this, sb.toString(), 15, 0xFFFFE4E6, false);
            others.setGravity(Gravity.CENTER);
            others.setPadding(0, Ui.dp(this, 14), 0, 0);
            root.addView(others);
        }

        View grow = new View(this);
        grow.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1f));
        root.addView(grow);

        Button dismiss = Ui.button(this, "Matikan", false);
        dismiss.setTextColor(0xFFBE123C);
        dismiss.setBackground(Ui.rounded(this, 0xFFFFFFFF, 18));
        dismiss.setTextSize(19);
        dismiss.setMinHeight(Ui.dp(this, 64));
        dismiss.setOnClickListener(v -> send(AlarmService.ACTION_DISMISS));
        root.addView(dismiss);

        Button snooze = Ui.button(this, "Tunda 5 menit", false);
        GradientDrawable outline = Ui.rounded(this, 0x00000000, 18);
        outline.setStroke(Ui.dp(this, 2), 0xAAFFFFFF);
        snooze.setBackground(outline);
        snooze.setTextColor(0xFFFFFFFF);
        snooze.setMinHeight(Ui.dp(this, 56));
        snooze.setOnClickListener(v -> send(AlarmService.ACTION_SNOOZE));
        root.addView(snooze);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.MATCH_PARENT));
        setContentView(sv);
    }

    private void send(String action) {
        startService(new Intent(this, AlarmService.class).setAction(action));
        finish();
    }
}
