package com.gitpush.app.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny dynamic form dialog: text, multi-line text, toggles and single-choice chips. */
public final class Form {
    private Form() {}

    public static final class F {
        final String key, label, type; String init = ""; String[] opts; int idx; boolean on; EditText edit; LinearLayout chips; String hint = "";
        int kind;   // 0 normal text, 1 url, 2 no-suggestions (names, paths, topics), 3 digits
        F(String key, String label, String type) { this.key = key; this.label = label; this.type = type; }
        public F hint(String h) { hint = h; return this; }
        public F url() { kind = 1; return this; }
        public F plain() { kind = 2; return this; }
    }

    public static F text(String key, String label, String init) { F f = new F(key, label, "text"); f.init = init == null ? "" : init; return f; }
    public static F multi(String key, String label, String init) { F f = new F(key, label, "multi"); f.init = init == null ? "" : init; return f; }
    public static F secret(String key, String label) { return new F(key, label, "secret"); }
    public static F number(String key, String label, String init) { F f = new F(key, label, "number"); f.init = init == null ? "" : init; return f; }
    public static F toggle(String key, String label, boolean on) { F f = new F(key, label, "toggle"); f.on = on; return f; }
    public static F choice(String key, String label, String[] opts, int idx) { F f = new F(key, label, "choice"); f.opts = opts; f.idx = Math.max(0, idx); return f; }

    public static void show(final Context c, String title, String icon, String positive, final List<F> fields, final Ui.Cb<Map<String, String>> cb) {
        LinearLayout box = Ui.vbox(c);
        for (final F f : fields) {
            if (f.type.equals("toggle")) {
                final TextView[] holder = new TextView[1];
                holder[0] = Ui.chip(c, (f.on ? "✔ " : "☐ ") + f.label, f.on, v -> { f.on = !f.on; ((TextView) v).setText((f.on ? "✔ " : "☐ ") + f.label); v.setBackgroundResource(f.on ? R.drawable.bg_chip_on : R.drawable.bg_chip_off); ((TextView) v).setTextColor(Ui.color(c, f.on ? R.attr.gpOnPrimary : R.attr.gpText)); });
                box.addView(holder[0], Ui.lp(c, Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
            } else if (f.type.equals("choice")) {
                box.addView(Ui.muted(c, f.label), Ui.lp(c, Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
                f.chips = Ui.hbox(c);
                renderChoice(c, f);
                android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
                hs.setHorizontalScrollBarEnabled(false);
                hs.addView(f.chips);
                box.addView(hs, Ui.lp(c, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
            } else {
                box.addView(Ui.muted(c, f.label), Ui.lp(c, Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
                f.edit = Ui.input(c, f.hint, f.init);
                if (f.type.equals("multi")) { f.edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); f.edit.setMinLines(3); f.edit.setGravity(Gravity.TOP); }
                else f.edit.setSingleLine(true);
                if (f.type.equals("number")) f.edit.setInputType(InputType.TYPE_CLASS_NUMBER);
                if (f.type.equals("secret")) f.edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                box.addView(f.edit, Ui.lp(c, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
            }
        }
        // keyboard: Next moves to the following field, Done on the last one; the dialog scrolls and resizes with the keyboard
        List<F> inputs = new ArrayList<>();
        for (F f : fields) if (f.edit != null) inputs.add(f);
        for (int i = 0; i < inputs.size(); i++) {
            final F f = inputs.get(i);
            final boolean last = i == inputs.size() - 1;
            if (f.type.equals("multi")) continue;
            f.edit.setImeOptions(last ? android.view.inputmethod.EditorInfo.IME_ACTION_DONE : android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
            if (f.type.equals("text")) {
                int base = android.text.InputType.TYPE_CLASS_TEXT;
                if (f.kind == 1) base |= android.text.InputType.TYPE_TEXT_VARIATION_URI;
                else if (f.kind == 2) base |= android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
                else base |= android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
                f.edit.setInputType(base);
            }
            f.edit.setSelectAllOnFocus(false);
        }
        new GlassDialog(c).setTitle(title).setIcon(icon).setView(box).setPositiveButton(positive, (d, w) -> {
            Map<String, String> out = new LinkedHashMap<>();
            for (F f : fields) {
                if (f.type.equals("toggle")) out.put(f.key, String.valueOf(f.on));
                else if (f.type.equals("choice")) out.put(f.key, f.opts[f.idx]);
                else out.put(f.key, f.edit.getText().toString().trim());
            }
            cb.on(out);
        }).setNegativeButton("Cancel", null).show();
    }

    private static void renderChoice(final Context c, final F f) {
        f.chips.removeAllViews();
        for (int i = 0; i < f.opts.length; i++) {
            final int k = i;
            f.chips.addView(Ui.chip(c, f.opts[i], f.idx == i, v -> { f.idx = k; renderChoice(c, f); }), Ui.lp(c, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        }
    }

    public static List<F> list(F... f) { List<F> l = new ArrayList<>(); for (F x : f) l.add(x); return l; }
}
