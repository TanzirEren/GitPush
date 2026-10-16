package com.gitpush.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.R;

/** Animated glass dialog (builder + dialog in one). API mirrors AlertDialog.Builder for easy migration. */
public class GlassDialog {
    public static final int BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE;
    public static final int BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE;
    public static final int BUTTON_NEUTRAL = DialogInterface.BUTTON_NEUTRAL;

    private final Context ctx;
    private Dialog dialog;
    private LinearLayout card;
    private CharSequence title, message;
    private String icon;
    private View customView;
    private CharSequence[] items;
    private DialogInterface.OnClickListener itemListener;
    private final CharSequence[] btnText = new CharSequence[3];
    private final DialogInterface.OnClickListener[] btnListener = new DialogInterface.OnClickListener[3];
    private final Button[] btns = new Button[3];
    private boolean cancelable = true, danger, closing;
    private DialogInterface.OnDismissListener dismissListener;

    public GlassDialog(Context ctx) { this.ctx = ctx; }

    public GlassDialog setTitle(CharSequence t) { title = t; return this; }
    public GlassDialog setMessage(CharSequence m) { message = m; return this; }
    public GlassDialog setIcon(String emoji) { icon = emoji; return this; }
    public GlassDialog setView(View v) { customView = v; return this; }
    public GlassDialog setCancelable(boolean c) { cancelable = c; return this; }
    public GlassDialog setDanger(boolean d) { danger = d; return this; }
    public GlassDialog onDismiss(DialogInterface.OnDismissListener l) { dismissListener = l; return this; }
    public GlassDialog setItems(CharSequence[] i, DialogInterface.OnClickListener l) { items = i; itemListener = l; return this; }
    public GlassDialog setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) { btnText[0] = t; btnListener[0] = l; return this; }
    public GlassDialog setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) { btnText[1] = t; btnListener[1] = l; return this; }
    public GlassDialog setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) { btnText[2] = t; btnListener[2] = l; return this; }

    public Button getButton(int which) { return btns[which == BUTTON_POSITIVE ? 0 : which == BUTTON_NEGATIVE ? 1 : 2]; }
    public boolean isShowing() { return dialog != null && dialog.isShowing(); }

    public GlassDialog create() {
        if (dialog != null) return this;
        dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(cancelable);
        if (dismissListener != null) dialog.setOnDismissListener(dismissListener);
        build();
        dialog.setContentView(card);
        Window w = dialog.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return this;
    }

    private int primaryAttr() { return danger ? R.attr.gpError : R.attr.gpPrimary; }

    private void build() {
        card = Ui.vbox(ctx);
        card.setBackgroundResource(R.drawable.bg_dialog);
        card.setPadding(Ui.dp(ctx, 22), Ui.dp(ctx, 22), Ui.dp(ctx, 22), Ui.dp(ctx, 14));

        if (title != null || icon != null) {
            LinearLayout head = Ui.hbox(ctx);
            if (icon != null) {
                TextView ic = Ui.text(ctx, icon, 22, false, R.attr.gpText);
                ic.setGravity(Gravity.CENTER);
                ic.setBackgroundResource(R.drawable.bg_icon_tile);
                head.addView(ic, Ui.lp(ctx, Ui.dp(ctx, 44), Ui.dp(ctx, 44), 0, 0, 12, 0));
            }
            if (title != null) head.addView(Ui.text(ctx, title, 19, true, R.attr.gpText), Ui.weight(1f));
            card.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));
        }
        if (message != null) {
            TextView m = Ui.text(ctx, message, 14, false, R.attr.gpMuted);
            m.setLineSpacing(0, 1.15f);
            m.setTextIsSelectable(false);
            if (message.length() > 380 || countLines(message) > 12) {
                ScrollView sv = new ScrollView(ctx);
                sv.addView(m);
                card.addView(sv, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 260), 0, 12, 0, 0));
            } else card.addView(m, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        }
        if (customView != null) card.addView(customView, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        if (items != null) {
            LinearLayout list = Ui.vbox(ctx);
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                TextView row = Ui.text(ctx, items[i], 15, false, R.attr.gpText);
                row.setBackgroundResource(R.drawable.bg_ripple_row);
                row.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 13), Ui.dp(ctx, 12), Ui.dp(ctx, 13));
                row.setOnClickListener(v -> { Ui.haptic(v); if (itemListener != null) itemListener.onClick(dialog, idx); dismiss(); });
                list.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            if (items.length > 7) {
                ScrollView sv = new ScrollView(ctx);
                sv.addView(list);
                card.addView(sv, Ui.lp(ctx, Ui.MATCH, Ui.dp(ctx, 330), 0, 10, 0, 0));
            } else card.addView(list, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        }
        boolean any = btnText[0] != null || btnText[1] != null || btnText[2] != null;
        if (any) {
            LinearLayout row = Ui.hbox(ctx);
            row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            if (btnText[2] != null) { btns[2] = mk(2, false); row.addView(btns[2]); row.addView(new View(ctx), Ui.weight(1f)); }
            if (btnText[1] != null) { btns[1] = mk(1, false); row.addView(btns[1]); }
            if (btnText[0] != null) { btns[0] = mk(0, true); row.addView(btns[0], Ui.lp(ctx, Ui.WRAP, Ui.WRAP, 6, 0, 0, 0)); }
            card.addView(row, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 16, 0, 0));
        } else card.setPadding(card.getPaddingLeft(), card.getPaddingTop(), card.getPaddingRight(), Ui.dp(ctx, 16));
    }

    private int countLines(CharSequence s) { int n = 1; for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++; return n; }

    private Button mk(final int i, boolean primary) {
        final int which = i == 0 ? BUTTON_POSITIVE : i == 1 ? BUTTON_NEGATIVE : BUTTON_NEUTRAL;
        Button b = primary ? Ui.button(ctx, String.valueOf(btnText[i]), null)
                : Ui.textButton(ctx, String.valueOf(btnText[i]), i == 2 ? primaryAttr() : R.attr.gpMuted, null);
        if (primary && danger) b.setBackgroundResource(R.drawable.bg_btn_danger);
        b.setPadding(Ui.dp(ctx, primary ? 20 : 12), Ui.dp(ctx, 9), Ui.dp(ctx, primary ? 20 : 12), Ui.dp(ctx, 9));
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setOnClickListener(v -> {
            Ui.haptic(v);
            if (btnListener[i] != null) btnListener[i].onClick(dialog, which);
            dismiss();
        });
        return b;
    }

    public GlassDialog show() {
        create();
        Window w = dialog.getWindow();
        if (w != null) {
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int width = Math.min((int) (dm.widthPixels * 0.92f), Ui.dp(ctx, 440));
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.5f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (Build.VERSION.SDK_INT >= 31) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                w.getAttributes().setBlurBehindRadius(28);
            }
        }
        card.setAlpha(0f); card.setScaleX(0.9f); card.setScaleY(0.9f); card.setTranslationY(Ui.dp(ctx, 26));
        try { dialog.show(); } catch (Exception e) { return this; }
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(280).setInterpolator(new OvershootInterpolator(0.9f)).start();
        return this;
    }

    public void dismiss() {
        if (dialog == null || !dialog.isShowing() || closing) return;
        closing = true;
        card.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).translationY(Ui.dp(ctx, 10)).setDuration(140).withEndAction(() -> {
            try { dialog.dismiss(); } catch (Exception ignored) { }
            closing = false;
        }).start();
    }
}
