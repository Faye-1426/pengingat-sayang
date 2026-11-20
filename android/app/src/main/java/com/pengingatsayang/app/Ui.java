package com.pengingatsayang.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pembantu kecil untuk membuat tampilan tanpa file XML. */
final class Ui {
    private Ui() {}

    static final int ROSE = 0xFFE11D48;
    static final int ROSE_DARK = 0xFFBE123C;
    static final int ROSE_SOFT = 0xFFFFE4E6;
    static final int BG = 0xFFFFF7F8;
    static final int INK = 0xFF1F1720;
    static final int MUTED = 0xFF7A6B70;
    static final int OK = 0xFF15803D;
    static final int WARN = 0xFFB45309;

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = vertical(c);
        GradientDrawable bg = rounded(c, 0xFFFFFFFF, 20);
        bg.setStroke(dp(c, 1), 0xFFF5D0D6);
        l.setBackground(bg);
        int p = dp(c, 18);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 14);
        l.setLayoutParams(lp);
        return l;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setTextColor(primary ? 0xFFFFFFFF : ROSE_DARK);
        GradientDrawable shape = rounded(c, primary ? ROSE : ROSE_SOFT, 14);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000), shape, null));
        b.setStateListAnimator(null);
        int ph = dp(c, 16);
        b.setPadding(ph, dp(c, 12), ph, dp(c, 12));
        b.setMinHeight(dp(c, 48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    static View space(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return v;
    }
}
