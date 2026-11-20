package com.gitpush.app;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.gitpush.app.ide.IdePrefs;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;

/** Code IDE settings page. */
public class IdeSettingsActivity extends BaseActivity {
    private TextView sample, sizeLabel;
    private LinearLayout tabChips;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildScreen("Editor settings", "Code IDE", true);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);

        LinearLayout font = Ui.card(this);
        font.addView(Ui.label(this, "Font size"));
        sizeLabel = Ui.text(this, "", 22, true, R.attr.gpText);
        font.addView(sizeLabel, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        SeekBar sb = new SeekBar(this);
        sb.setMax(20);
        sb.setProgress(Math.round(IdePrefs.font()) - 8);
        font.addView(sb, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        sample = new TextView(this);
        sample.setTypeface(android.graphics.Typeface.MONOSPACE);
        sample.setTextColor(Ui.color(this, R.attr.gpText));
        sample.setText("public static void main(String[] args) {\n    System.out.println(\"Hello, GitPush\");\n}");
        sample.setBackgroundResource(R.drawable.bg_input);
        sample.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        font.addView(sample, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        font.addView(Ui.muted(this, "Tip: pinch with two fingers inside the editor to zoom."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { IdePrefs.font(8 + p); applySize(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        col.addView(font, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        applySize();

        LinearLayout opts = Ui.card(this);
        opts.addView(Ui.label(this, "Editor"));
        opts.addView(row("Line numbers", "Fixed gutter on the left", IdePrefs.lines(), IdePrefs::lines));
        opts.addView(row("Word wrap", "Wrap long lines instead of scrolling sideways", IdePrefs.wrap(), IdePrefs::wrap));
        opts.addView(row("Color syntax", "Off = clean monochrome highlighting", IdePrefs.color(), IdePrefs::color));
        opts.addView(row("Auto-indent", "Keep indentation after Enter", IdePrefs.indent(), IdePrefs::indent));
        opts.addView(row("Symbols bar", "Quick keys above the keyboard", IdePrefs.symbols(), IdePrefs::symbols));
        opts.addView(row("Error check", "Mark JSON, XML and bracket errors in red", IdePrefs.lint(), IdePrefs::lint));
        col.addView(opts, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout tab = Ui.card(this);
        tab.addView(Ui.label(this, "Tab size"));
        tabChips = Ui.hbox(this);
        tab.addView(tabChips, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(tab);
        renderTabs();
    }

    private void applySize() {
        float s = IdePrefs.font();
        sample.setTextSize(s);
        sizeLabel.setText(Math.round(s) + " sp");
    }

    private void renderTabs() {
        tabChips.removeAllViews();
        for (final int n : new int[]{2, 4, 8}) tabChips.addView(Ui.chip(this, n + " spaces", IdePrefs.tab() == n, v -> { IdePrefs.tab(n); renderTabs(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
    }

    private interface Setter { void set(boolean v); }

    private LinearLayout row(String title, String sub, boolean on, final Setter setter) {
        LinearLayout r = Ui.hbox(this);
        r.setPadding(0, Ui.dp(this, 10), 0, 0);
        LinearLayout t = Ui.vbox(this);
        t.addView(Ui.text(this, title, 14, true, R.attr.gpText));
        t.addView(Ui.muted(this, sub));
        r.addView(t, Ui.weight(1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((x, v) -> setter.set(v));
        r.addView(sw);
        return r;
    }
}
