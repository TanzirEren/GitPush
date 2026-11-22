package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Job log viewer: tail of the log, grouped sections, error lines emphasised, search, jump to end, copy. */
public class JobLogActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo, long jobId, String name) {
        c.startActivity(new Intent(c, JobLogActivity.class).putExtra("owner", owner).putExtra("repo", repo).putExtra("job", jobId).putExtra("name", name));
    }

    private static final Pattern TS = Pattern.compile("^\\d{4}-\\d\\d-\\d\\dT[\\d:.]+Z ");
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

    private String owner, repo;
    private long jobId;
    private GitHubApi api;
    private final List<String> lines = new ArrayList<>(), shown = new ArrayList<>();
    private ListView lv;
    private BaseAdapter adapter;
    private TextView status;
    private EditText search;
    private String full = "";
    // v2.1: GitHub-browser style text selection
    private static final int WIN = 2500;
    private boolean selMode;
    private ScrollView selScroll;
    private TextView selTv, selInfo;
    private LinearLayout selBar;
    private int winEnd = -1, rangeStart = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        jobId = getIntent().getLongExtra("job", 0);
        api = st.api();
        buildScreen(getIntent().getStringExtra("name"), "Job log", true);
        addAction(R.drawable.i_square_check, v -> setSelMode(!selMode));
        addAction(R.drawable.i_copy, v -> { Ui.copy(this, "log", full); Ui.toast(this, "Whole log copied"); });
        addAction(R.drawable.i_chevron_down, v -> lv.setSelection(Math.max(0, shown.size() - 1)));
        addAction(R.drawable.i_refresh, v -> load());
        LinearLayout page = Ui.vbox(this);
        search = Ui.input(this, "Search in log", "");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            public void onTextChanged(CharSequence s, int a, int c, int d) { }
            public void afterTextChanged(Editable e) { filter(); }
        });
        page.addView(search, Ui.lp(this, Ui.MATCH, Ui.WRAP, 16, 10, 16, 6));
        status = Ui.muted(this, "Loading log…");
        status.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), 0);
        page.addView(status);
        lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setStackFromBottom(false);
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { View v = row(shown.get(i)); final int pos = i; v.setOnLongClickListener(x -> { rowMenu(pos); return true; }); return v; }
        };
        lv.setAdapter(adapter);
        buildSelBar();
        page.addView(selBar);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(lv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        selScroll = new ScrollView(this);
        selScroll.setVisibility(View.GONE);
        selTv = new TextView(this);
        selTv.setTypeface(Typeface.MONOSPACE);
        selTv.setTextSize(11);
        selTv.setTextColor(Ui.color(this, R.attr.gpText));
        selTv.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 40));
        selTv.setTextIsSelectable(true);
        selScroll.addView(selTv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        stage.addView(selScroll, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        page.addView(stage, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        body.addView(page);
        load();
    }

    private void load() {
        status.setText("Loading log…"); status.setVisibility(View.VISIBLE);
        async(() -> api.jobLogs(owner, repo, jobId, 600_000), text -> {
            full = ANSI.matcher(text).replaceAll("");
            lines.clear();
            for (String l : full.split("\n")) {
                String t = TS.matcher(l.endsWith("\r") ? l.substring(0, l.length() - 1) : l).replaceFirst("");
                if (t.startsWith("##[endgroup]")) continue;
                lines.add(t);
            }
            filter();
            status.setVisibility(View.GONE);
            lv.post(() -> lv.setSelection(Math.max(0, shown.size() - 1)));
        }, e -> status.setText("Couldn't load the log (it may not exist yet)\n" + msg(e)));
    }

    private void filter() {
        String q = search.getText().toString().trim().toLowerCase();
        shown.clear();
        for (String l : lines) if (q.isEmpty() || l.toLowerCase().contains(q)) shown.add(l);
        adapter.notifyDataSetChanged();
        if (selMode) { winEnd = shown.size(); renderSelect(); }
    }

    // ------------------------------------------------------------------ select / copy mode
    private static boolean isErr(String l) { return l.startsWith("##[error]") || l.toLowerCase().contains("error:") || l.contains("FAILURE"); }

    private static String shownText(String l) {
        if (l.startsWith("##[group]")) return "▸ " + l.substring(9);
        if (l.startsWith("##[error]")) return "✘ " + l.substring(9);
        if (isErr(l)) return "✘ " + l;
        if (l.startsWith("##[warning]")) return "! " + l.substring(11);
        return l;
    }

    private void buildSelBar() {
        selBar = Ui.vbox(this);
        selBar.setVisibility(View.GONE);
        selBar.setPadding(Ui.dp(this, 12), Ui.dp(this, 2), Ui.dp(this, 12), Ui.dp(this, 6));
        selInfo = Ui.label(this, "");
        selBar.addView(selInfo);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout acts = Ui.hbox(this);
        String[] labels = {"📋 Copy selected", "☑ Select all", "⬆ Older", "⬇ Newer", "✘ Copy errors", "📋 Copy visible"};
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            acts.addView(Ui.chip(this, labels[i], i == 0, v -> selAction(k)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        }
        hs.addView(acts);
        selBar.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
    }

    private void setSelMode(boolean on) {
        selMode = on;
        selBar.setVisibility(on ? View.VISIBLE : View.GONE);
        selScroll.setVisibility(on ? View.VISIBLE : View.GONE);
        lv.setVisibility(on ? View.GONE : View.VISIBLE);
        if (on) {
            winEnd = shown.size();
            renderSelect();
            Ui.toast(this, "Long-press a word, drag the handles, then tap Copy");
            selScroll.post(() -> selScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void renderSelect() {
        int n = shown.size();
        if (winEnd < 0 || winEnd > n) winEnd = n;
        int start = Math.max(0, winEnd - WIN);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = start; i < winEnd; i++) {
            String l = shown.get(i);
            int a = sb.length();
            sb.append(shownText(l));
            int b = sb.length();
            sb.append('\n');
            if (isErr(l)) sb.setSpan(new ForegroundColorSpan(0xFFE5484D), a, b, 0);
            else if (l.startsWith("##[group]")) sb.setSpan(new StyleSpan(Typeface.BOLD), a, b, 0);
            else if (l.startsWith("##[warning]")) sb.setSpan(new ForegroundColorSpan(Ui.color(this, R.attr.gpMuted)), a, b, 0);
        }
        selTv.setText(sb, TextView.BufferType.SPANNABLE);
        selInfo.setText("SELECT MODE  •  lines " + (n == 0 ? 0 : start + 1) + "–" + winEnd + " of " + n);
    }

    private void selAction(int k) {
        int n = shown.size();
        if (k == 0) {
            int a = Math.min(selTv.getSelectionStart(), selTv.getSelectionEnd()), b = Math.max(selTv.getSelectionStart(), selTv.getSelectionEnd());
            if (a < 0 || a == b) { Ui.toast(this, "Nothing selected – long-press the text, or tap Select all"); return; }
            Ui.copy(this, "log", selTv.getText().subSequence(a, b).toString());
            Ui.toast(this, "Copied " + (b - a) + " characters");
        } else if (k == 1) {
            selTv.requestFocus();
            Selection.setSelection((Spannable) selTv.getText(), 0, selTv.length());
        } else if (k == 2) { winEnd = Math.max(Math.min(WIN, n), winEnd - WIN); renderSelect(); selScroll.post(() -> selScroll.fullScroll(View.FOCUS_DOWN)); }
        else if (k == 3) { winEnd = Math.min(n, winEnd + WIN); renderSelect(); selScroll.post(() -> selScroll.scrollTo(0, 0)); }
        else if (k == 4) {
            StringBuilder sb = new StringBuilder();
            for (String l : lines) if (isErr(l)) sb.append(shownText(l)).append('\n');
            if (sb.length() == 0) { Ui.toast(this, "No error lines in this log"); return; }
            Ui.copy(this, "errors", sb.toString().trim()); Ui.toast(this, "Error lines copied");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String l : shown) sb.append(shownText(l)).append('\n');
            Ui.copy(this, "log", sb.toString().trim()); Ui.toast(this, n + " lines copied");
        }
    }

    /** Long-press on a row in the normal list: copy a line, or copy a range between two lines. */
    private void rowMenu(final int pos) {
        final String line = shown.get(pos);
        final List<String> items = new ArrayList<>();
        items.add("📋  Copy this line");
        items.add("📋  Copy from here to the end");
        items.add("📌  Mark range start here");
        if (rangeStart >= 0) items.add("📋  Copy range " + (rangeStart + 1) + " → " + (pos + 1));
        items.add("☑  Open select mode");
        final String[] arr = items.toArray(new String[0]);
        Ui.pickList(this, "Line " + (pos + 1), arr, i -> {
            String l = arr[i];
            if (l.contains("this line")) { Ui.copy(this, "line", shownText(line)); Ui.toast(this, "Line copied"); }
            else if (l.contains("to the end")) copyRange(pos, shown.size() - 1);
            else if (l.contains("Mark range")) { rangeStart = pos; Ui.toast(this, "Start marked – long-press the last line and choose Copy range"); }
            else if (l.contains("Copy range")) copyRange(Math.min(rangeStart, pos), Math.max(rangeStart, pos));
            else setSelMode(true);
        });
    }

    private void copyRange(int a, int b) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, a); i <= b && i < shown.size(); i++) sb.append(shownText(shown.get(i))).append('\n');
        Ui.copy(this, "log", sb.toString().trim());
        Ui.toast(this, (b - a + 1) + " lines copied");
    }

    private View row(String l) {
        TextView t = new TextView(this);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(11);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 2));
        t.setTextColor(Ui.color(this, R.attr.gpText));
        if (l.startsWith("##[group]")) {
            t.setText("▸ " + l.substring(9));
            t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            t.setBackgroundColor(Ui.color(this, R.attr.gpAuroraA));
            t.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        } else if (l.startsWith("##[error]") || l.toLowerCase().contains("error:") || l.contains("FAILURE")) {
            t.setText("✘ " + (l.startsWith("##[error]") ? l.substring(9) : l));
            t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            t.setTextColor(0xFFE5484D);
        } else if (l.startsWith("##[warning]")) {
            t.setText("! " + l.substring(11));
            t.setTextColor(Ui.color(this, R.attr.gpMuted));
        } else t.setText(l);
        return t;
    }
}
