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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.ui.BaseActivity;
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

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        jobId = getIntent().getLongExtra("job", 0);
        api = st.api();
        buildScreen(getIntent().getStringExtra("name"), "Job log", true);
        addAction(R.drawable.i_copy, v -> { Ui.copy(this, "log", full); Ui.toast(this, "Log copied"); });
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
            public View getView(int i, View cv, ViewGroup p) { return row(shown.get(i)); }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
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
