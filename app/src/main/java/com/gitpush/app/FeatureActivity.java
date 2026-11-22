package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
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
import com.gitpush.app.features.Feature;
import com.gitpush.app.features.Features;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Downloader;
import com.gitpush.app.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Host for every GitHub control feature (issues, pulls, branches, webhooks, …). */
public class FeatureActivity extends BaseActivity {
    public static void open(Context c, String kind, String owner, String repo) {
        c.startActivity(new Intent(c, FeatureActivity.class).putExtra("kind", kind).putExtra("owner", owner == null ? "" : owner).putExtra("repo", repo == null ? "" : repo));
    }

    public String owner, repo;
    public GitHubApi api;
    private Feature f;
    private final List<Feature.Row> all = new ArrayList<>(), shown = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView status;
    private EditText search;
    private LinearLayout chipRow;
    private boolean loading;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        f = Features.create(getIntent().getStringExtra("kind"));
        if (f == null) { finish(); return; }
        f.attach(this);
        buildScreen(f.title(), repo.isEmpty() ? "Your account" : owner + "/" + repo, true);
        View custom = f.view();
        if (custom != null) {
            body.addView(custom);
            if (f.hasMenu()) addAction(R.drawable.i_more, v -> f.menu());
            return;
        }
        addAction(R.drawable.i_refresh, v -> reload());
        if (f.hasMenu()) addAction(R.drawable.i_more, v -> f.menu());
        if (f.canAdd()) addAction(R.drawable.i_plus, v -> f.onAdd());
        LinearLayout page = Ui.vbox(this);
        chipRow = Ui.hbox(this);
        if (f.filters() != null) {
            chipRow.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), 0);
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(chipRow);
            page.addView(hs);
            renderChips();
        }
        search = Ui.input(this, "Search", "");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(Editable e) { applyFilter(); }
        });
        page.addView(search, Ui.lp(this, Ui.MATCH, Ui.WRAP, 16, 10, 16, 4));
        status = Ui.muted(this, "Loading…");
        status.setPadding(Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 24), 0);
        page.addView(status);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 24));
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { View v = row(shown.get(i)); if (cv == null) Ui.fadeIn(v, i); return v; }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        body.addView(page);
        reload();
    }

    private void renderChips() {
        chipRow.removeAllViews();
        String[] fl = f.filters();
        for (int i = 0; i < fl.length; i++) {
            final int k = i;
            chipRow.addView(Ui.chip(this, fl[i], f.filter == i, v -> { f.filter = k; renderChips(); reload(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        }
    }

    /** Reloads the list (custom screens handle their own refresh). */
    public void reload() {
        if (status == null) return;
        loading = true; status.setText("Loading…"); status.setVisibility(View.VISIBLE);
        async(() -> f.load(), rows -> { loading = false; all.clear(); all.addAll(rows); applyFilter(); },
                e -> { loading = false; all.clear(); applyFilter(); status.setVisibility(View.VISIBLE); status.setText("Couldn't load\n" + msg(e)); });
    }

    private void applyFilter() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase();
        shown.clear();
        for (Feature.Row r : all) if (q.isEmpty() || (r.title + " " + r.sub + " " + r.pill).toLowerCase().contains(q)) shown.add(r);
        adapter.notifyDataSetChanged();
        if (!loading) { status.setText(shown.isEmpty() ? f.empty() : ""); status.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE); }
    }

    private View row(final Feature.Row r) {
        LinearLayout wrap = Ui.vbox(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        TextView t = Ui.title(this, r.title);
        t.setTextSize(14);
        t.setMaxLines(2);
        h.addView(t, Ui.weight(1f));
        if (!r.pill.isEmpty()) h.addView(Ui.pill(this, r.pill, R.attr.gpText), Ui.lp(this, Ui.WRAP, Ui.WRAP, 8, 0, 0, 0));
        c.addView(h);
        if (!r.sub.isEmpty()) c.addView(Ui.text(this, r.sub, 12, false, R.attr.gpMuted), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        c.setOnClickListener(v -> f.onRow(r));
        wrap.addView(c);
        return wrap;
    }

    // helpers for features ------------------------------------------------------------
    public <T> void run(Ui.Task<T> task, Ui.Cb<T> ok) { async(task, ok, e -> Ui.toast(this, msg(e))); }
    public void act(Ui.Task<?> task, String done) { async(task, x -> { Ui.toast(this, done); reload(); }, e -> Ui.toast(this, msg(e))); }
    public <T> void async2(Ui.Task<T> task, Ui.Cb<T> ok, Ui.Cb<Exception> err) { async(task, ok, err); }
    public String err(Exception e) { return msg(e); }
    public AppState st() { return st; }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Downloader.onResult(this, req, res, data);
    }
}
