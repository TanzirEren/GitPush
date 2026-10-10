package com.gitpush.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.gitpush.app.R;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public final class Ui {
    private Ui() {}

    public interface Task<T> { T run() throws Exception; }
    public interface Cb<T> { void on(T v); }

    public static volatile boolean hapticsOn = true;

    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static int color(Context c, int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    public static int themeFor(int accent) {
        switch (accent) {
            case 1: return R.style.Theme_GitPush_Sky;
            case 2: return R.style.Theme_GitPush_Violet;
            case 3: return R.style.Theme_GitPush_Amber;
            case 4: return R.style.Theme_GitPush_Rose;
            default: return R.style.Theme_GitPush_Mint;
        }
    }

    // ---------------------------------------------------------------- builders
    public static LinearLayout vbox(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    public static LinearLayout hbox(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    public static LinearLayout.LayoutParams lp(Context c, int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }
    public static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, WRAP, w); }

    public static TextView text(Context c, CharSequence t, float sp, boolean bold, int colorAttr) {
        TextView v = new TextView(c);
        v.setText(t); v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); v.setTextColor(color(c, colorAttr));
        if (bold) v.setTypeface(v.getTypeface(), Typeface.BOLD);
        return v;
    }
    public static TextView title(Context c, CharSequence t) { return text(c, t, 16, true, R.attr.gpText); }
    public static TextView body(Context c, CharSequence t) { return text(c, t, 13, false, R.attr.gpText); }
    public static TextView muted(Context c, CharSequence t) { return text(c, t, 12, false, R.attr.gpMuted); }
    public static TextView section(Context c, CharSequence t) {
        TextView v = text(c, t, 13, true, R.attr.gpPrimary);
        v.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 6));
        return v;
    }

    public static View space(Context c, int h) { View v = new View(c); v.setLayoutParams(lp(MATCH, dp(c, h))); return v; }

    public static Button button(Context c, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label); b.setAllCaps(false); b.setTextColor(color(c, R.attr.gpOnPrimary)); b.setBackgroundResource(R.drawable.bg_btn_primary);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setPadding(dp(c, 22), dp(c, 12), dp(c, 22), dp(c, 12)); b.setStateListAnimator(null);
        b.setOnClickListener(v -> { haptic(v); l.onClick(v); });
        return b;
    }

    public static Button outlineButton(Context c, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label); b.setAllCaps(false); b.setTextColor(color(c, R.attr.gpPrimary)); b.setBackgroundResource(R.drawable.bg_btn_outline);
        b.setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10)); b.setStateListAnimator(null);
        b.setOnClickListener(v -> { haptic(v); l.onClick(v); });
        return b;
    }

    public static Button textButton(Context c, String label, int colorAttr, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label); b.setAllCaps(false); b.setTextColor(color(c, colorAttr)); b.setBackground(null); b.setStateListAnimator(null);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setOnClickListener(l);
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackgroundResource(R.drawable.bg_glass);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static EditText input(Context c, String hint, String text) {
        EditText e = new EditText(c);
        e.setHint(hint); e.setText(text); e.setBackgroundResource(R.drawable.bg_input);
        e.setTextColor(color(c, R.attr.gpText)); e.setHintTextColor(color(c, R.attr.gpMuted)); e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        return e;
    }

    public static TextView chip(Context c, String label, boolean on, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(label); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_off);
        t.setTextColor(color(c, on ? R.attr.gpOnPrimary : R.attr.gpText));
        t.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        t.setOnClickListener(v -> { haptic(v); l.onClick(v); });
        return t;
    }

    public static TextView pill(Context c, String label, int colorAttr) {
        TextView t = text(c, label, 10, true, colorAttr);
        t.setBackgroundResource(R.drawable.bg_chip_off);
        t.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        return t;
    }

    public static ImageView icon(Context c, int res, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    public static LinearLayout empty(Context c, String emoji, String title, String sub) {
        LinearLayout l = vbox(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(c, 32), dp(c, 28), dp(c, 32), dp(c, 28));
        TextView e = text(c, emoji, 34, false, com.gitpush.app.R.attr.gpText);
        e.setGravity(Gravity.CENTER);
        e.setBackgroundResource(com.gitpush.app.R.drawable.bg_circle);
        l.addView(e, lp(dp(c, 84), dp(c, 84)));
        TextView t = title(c, title);
        t.setGravity(Gravity.CENTER);
        l.addView(t, lp(c, MATCH, WRAP, 0, 14, 0, 0));
        TextView m = muted(c, sub);
        m.setGravity(Gravity.CENTER);
        l.addView(m, lp(c, MATCH, WRAP, 0, 4, 0, 0));
        return l;
    }

    // ---------------------------------------------------------------- helpers
    public static void haptic(View v) { if (hapticsOn && v != null) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }

    public static void toast(Context c, String msg) { Toast.makeText(c.getApplicationContext(), msg == null ? "" : msg, Toast.LENGTH_SHORT).show(); }

    public static void copy(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    public static void openUrl(Context c, String url) {
        try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { toast(c, "No app can open this link"); }
    }

    public static void confirm(Context c, String title, String msg, String posLabel, Runnable onYes) {
        new AlertDialog.Builder(c).setTitle(title).setMessage(msg)
                .setPositiveButton(posLabel, (d, w) -> onYes.run()).setNegativeButton("Cancel", null).show();
    }

    public static void prompt(Context c, String title, String hint, String initial, Cb<String> onOk) {
        final EditText e = input(c, hint, initial);
        LinearLayout box = vbox(c);
        box.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), 0);
        box.addView(e, lp(MATCH, WRAP));
        new AlertDialog.Builder(c).setTitle(title).setView(box)
                .setPositiveButton("OK", (d, w) -> onOk.on(e.getText().toString())).setNegativeButton("Cancel", null).show();
    }

    // ---------------------------------------------------------------- time
    public static Long parseIso(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s).getTime();
        } catch (Exception e) { return null; }
    }

    public static String ago(long ms) {
        long d = (System.currentTimeMillis() - ms) / 1000;
        if (d < 60) return "just now";
        if (d < 3600) return (d / 60) + " min ago";
        if (d < 86400) return (d / 3600) + " h ago";
        if (d < 86400L * 30) return (d / 86400) + " d ago";
        if (d < 86400L * 365) return (d / (86400L * 30)) + " mo ago";
        return (d / (86400L * 365)) + " y ago";
    }

    public static String agoIso(String s) { Long t = s == null ? null : parseIso(s); return t == null ? "" : ago(t); }
}
