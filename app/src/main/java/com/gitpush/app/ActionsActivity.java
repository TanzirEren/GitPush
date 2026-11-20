package com.gitpush.app;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Run;
import com.gitpush.app.data.Models.Workflow;
import com.gitpush.app.ui.BarChartView;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Downloader;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.Ui;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** GitHub Actions: filterable runs with live timers, build-time chart, quick re-run, workflows and dispatch. */
public class ActionsActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, ActionsActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    /** Icon for a run's state. */
    public static int iconFor(Run r) {
        if (r.active()) return R.drawable.i_loader;
        if (r.conclusion == null) return R.drawable.i_circle;
        switch (r.conclusion) {
            case "success": return R.drawable.i_check_circle;
            case "cancelled": case "skipped": return R.drawable.i_ban;
            default: return R.drawable.i_x_circle;
        }
    }

    public static int iconFor(String status, String conclusion) {
        Run r = new Run(); r.status = status; r.conclusion = conclusion;
        return iconFor(r);
    }

    public static long durationMs(Run r) {
        long s = Ui.isoMs(r.startedAt);
        if (s == 0) return 0;
        long e = r.active() ? System.currentTimeMillis() : Ui.isoMs(r.updatedAt);
        return Math.max(0, e - s);
    }

    private String owner, repo, defaultBranch = "main", branchFilter = "";
    private GitHubApi api;
    private final List<Run> all = new ArrayList<>(), shown = new ArrayList<>();
    private final List<Object[]> live = new ArrayList<>();
    private int filter = 0;
    private boolean autoOn = true, loading = true, resumed;
    private String error;
    private BaseAdapter adapter;
    private TextView status, summary;
    private LinearLayout chips;
    private BarChartView chart;
    private ImageView reloadIcon;
    private final Runnable auto = this::refresh;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            for (Object[] o : live) ((TextView) o[0]).setText(Ui.dur(durationMs((Run) o[1])));
            if (resumed) app.main.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        buildScreen("Actions", owner + "/" + repo, true);
        addAction(R.drawable.i_zap, v -> workflows());
        reloadIcon = addAction(R.drawable.i_refresh, v -> { spin(reloadIcon); refresh(); });
        com.gitpush.app.data.Models.Repo r = st.findRepo(owner, repo);
        if (r != null) defaultBranch = r.defaultBranch;

        LinearLayout page = Ui.vbox(this);
        LinearLayout top = Ui.vbox(this);
        top.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), 0);
        LinearLayout card = Ui.card(this);
        summary = Ui.label(this, "Loading…");
        card.addView(summary);
        chart = new BarChartView(this);
        card.addView(chart, Ui.lp(this, Ui.MATCH, Ui.dp(this, 90), 0, 4, 0, 0));
        top.addView(card);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chips = Ui.hbox(this);
        hs.addView(chips);
        top.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 6));
        page.addView(top);
        status = Ui.muted(this, "");
        status.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), 0);
        page.addView(status);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 24));
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { View v = row(shown.get(i)); Ui.fadeIn(v, i); return v; }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        body.addView(page);
        renderChips();
        refresh();
    }

    private void spin(View v) { v.animate().rotationBy(360f).setDuration(600).start(); }

    @Override protected void onResume() { super.onResume(); resumed = true; app.main.post(tick); refresh(); }
    @Override protected void onPause() { super.onPause(); resumed = false; app.main.removeCallbacks(auto); app.main.removeCallbacks(tick); }
    @Override protected void onDestroy() { app.main.removeCallbacks(auto); app.main.removeCallbacks(tick); super.onDestroy(); }

    private void refresh() {
        app.main.removeCallbacks(auto);
        async(() -> api.runs(owner, repo, 40), list -> {
            loading = false; error = null; all.clear(); all.addAll(list); render();
            boolean active = false;
            for (Run r : list) if (r.active()) active = true;
            if (active && autoOn && resumed) app.main.postDelayed(auto, 6000);
        }, e -> { loading = false; error = msg(e); render(); });
    }

    private void renderChips() {
        chips.removeAllViews();
        String[] names = {"All", "Running", "Success", "Failed", "Cancelled"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            chips.addView(Ui.chip(this, names[i], filter == i, v -> { filter = idx; render(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        }
        chips.addView(Ui.chip(this, branchFilter.isEmpty() ? "All branches" : branchFilter, !branchFilter.isEmpty(), v -> pickBranch()), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        chips.addView(Ui.chip(this, autoOn ? "Auto-refresh on" : "Auto-refresh off", autoOn, v -> { autoOn = !autoOn; renderChips(); if (autoOn) refresh(); }));
    }

    private void pickBranch() {
        Set<String> set = new LinkedHashSet<>();
        for (Run r : all) if (r.branch != null) set.add(r.branch);
        final List<String> names = new ArrayList<>();
        names.add("All branches");
        names.addAll(set);
        Ui.pickList(this, "Filter by branch", names.toArray(new String[0]), i -> { branchFilter = i == 0 ? "" : names.get(i); render(); renderChips(); });
    }

    private boolean match(Run r) {
        if (!branchFilter.isEmpty() && !branchFilter.equals(r.branch)) return false;
        switch (filter) {
            case 1: return r.active();
            case 2: return "success".equals(r.conclusion);
            case 3: return "failure".equals(r.conclusion) || "timed_out".equals(r.conclusion) || "startup_failure".equals(r.conclusion);
            case 4: return "cancelled".equals(r.conclusion) || "skipped".equals(r.conclusion);
            default: return true;
        }
    }

    private void render() {
        shown.clear(); live.clear();
        int ok = 0, bad = 0, run = 0;
        for (Run r : all) {
            if (match(r)) shown.add(r);
            if (r.active()) run++; else if ("success".equals(r.conclusion)) ok++; else if (r.conclusion != null && !"cancelled".equals(r.conclusion) && !"skipped".equals(r.conclusion)) bad++;
        }
        adapter.notifyDataSetChanged();
        long sum = 0; int n = 0;
        int cap = Math.min(14, all.size());
        float[] bars = new float[cap]; String[] labs = new String[cap];
        for (int i = 0; i < cap; i++) {
            Run r = all.get(cap - 1 - i);
            bars[i] = durationMs(r) / 60000f; labs[i] = "#" + r.number;
            if (!r.active() && durationMs(r) > 0) { sum += durationMs(r); n++; }
        }
        summary.setText(all.isEmpty() ? "No runs" : ok + " passed  •  " + bad + " failed  •  " + run + " running" + (n > 0 ? "  •  avg " + Ui.dur(sum / n) : ""));
        if (cap > 0) chart.setData(bars, labs, Ui.color(this, R.attr.gpText), Ui.color(this, R.attr.gpMuted), Ui.color(this, R.attr.gpMuted), Ui.color(this, R.attr.gpAuroraA));
        chart.setVisibility(cap > 0 ? View.VISIBLE : View.GONE);
        if (loading) status.setText("Loading…");
        else if (error != null && all.isEmpty()) status.setText("Couldn't load runs\n" + error);
        else if (all.isEmpty()) status.setText("No workflow runs yet.\nPush a workflow to .github/workflows/ and it will show up here.");
        else if (shown.isEmpty()) status.setText("No runs match this filter.");
        else status.setText("");
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    private View row(final Run r) {
        LinearLayout wrap = Ui.vbox(this);
        wrap.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        ImageView ic = Ui.iconTileRes(this, iconFor(r), 40);
        if (r.active()) {
            ObjectAnimator a = ObjectAnimator.ofFloat(ic, "rotation", 0f, 360f);
            a.setDuration(1400); a.setRepeatCount(ObjectAnimator.INFINITE); a.setInterpolator(new LinearInterpolator()); a.start();
        }
        h.addView(ic, Ui.lp(this, Ui.dp(this, 40), Ui.dp(this, 40), 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(this);
        TextView title = Ui.title(this, r.title);
        title.setMaxLines(2);
        t.addView(title);
        TextView sub = Ui.muted(this, "#" + r.number + " • " + r.name + " • " + (r.branch == null ? "" : r.branch));
        sub.setSingleLine(true);
        t.addView(sub);
        t.addView(Ui.label(this, (r.conclusion == null ? r.status.replace('_', ' ') : r.conclusion.replace('_', ' ')) + " • " + r.event + " • " + Ui.agoIso(r.createdAt)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        h.addView(t, Ui.weight(1f));
        LinearLayout right = Ui.vbox(this);
        right.setGravity(android.view.Gravity.END);
        TextView d = Ui.text(this, Ui.dur(durationMs(r)), 12, true, R.attr.gpText);
        right.addView(d, Ui.lp(Ui.WRAP, Ui.WRAP));
        ImageView re = Ui.iconRes(this, R.drawable.i_refresh, 18, R.attr.gpMuted);
        re.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        re.setLayoutParams(Ui.lp(this, Ui.dp(this, 36), Ui.dp(this, 36), 0, 4, 0, 0));
        re.setOnClickListener(v -> { spin(v); Ui.confirm(this, "Re-run #" + r.number + "?", "All jobs of this run start again.", "Re-run", () -> act(() -> { api.rerun(owner, repo, r.id, false); return "Re-run started"; })); });
        right.addView(re);
        h.addView(right);
        c.addView(h);
        if (r.active()) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setIndeterminate(true);
            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.attr.gpText)));
            c.addView(pb, Ui.lp(this, Ui.MATCH, Ui.dp(this, 4), 0, 10, 0, 0));
            live.add(new Object[]{d, r});
        }
        c.setOnClickListener(v -> RunDetailActivity.open(this, owner, repo, r.id));
        c.setOnLongClickListener(v -> { Ui.haptic(v); runMenu(r); return true; });
        wrap.addView(c);
        return wrap;
    }

    private void runMenu(final Run r) {
        List<String> items = new ArrayList<>();
        items.add("🔍  Open run details");
        items.add("🔁  Re-run all jobs");
        items.add("🔁  Re-run failed jobs");
        if (r.active()) { items.add("⛔  Cancel run"); items.add(KeepAliveService.isWatching(r.id) ? "🔔  Already watching" : "🔔  Notify me when it finishes"); }
        items.add("🗑  Delete run");
        items.add("🌐  Open on GitHub");
        final String[] arr = items.toArray(new String[0]);
        Ui.pickList(this, "#" + r.number + "  " + r.title, arr, i -> {
            String l = arr[i];
            if (l.contains("details")) RunDetailActivity.open(this, owner, repo, r.id);
            else if (l.contains("all jobs")) act(() -> { api.rerun(owner, repo, r.id, false); return "Re-run started"; });
            else if (l.contains("failed jobs")) act(() -> { api.rerun(owner, repo, r.id, true); return "Re-running failed jobs"; });
            else if (l.contains("Cancel")) act(() -> { api.cancelRun(owner, repo, r.id); return "Cancel requested"; });
            else if (l.contains("Notify")) { KeepAliveService.watchRun(this, owner, repo, r.id, r.title); Ui.toast(this, "You'll get a notification when it finishes"); }
            else if (l.contains("Delete")) Ui.confirmDanger(this, "Delete run #" + r.number + "?", "The run and its logs are removed from GitHub.", "Delete", () -> act(() -> { api.deleteRun(owner, repo, r.id); return "Run deleted"; }));
            else if (l.contains("GitHub")) Ui.openUrl(this, r.htmlUrl);
        });
    }

    private void act(final Ui.Task<String> t) {
        async(t, m -> { Ui.toast(this, m); app.main.postDelayed(this::refresh, 1500); }, e -> Ui.toast(this, msg(e)));
    }

    private void workflows() {
        async(() -> api.workflows(owner, repo), list -> {
            if (list.isEmpty()) { Ui.toast(this, "No workflows found in this repository"); return; }
            String[] labels = new String[list.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = ("active".equals(list.get(i).state) ? "🟢  " : "⚪  ") + list.get(i).name;
            Ui.pickList(this, "Workflows", labels, i -> workflowMenu(list.get(i)));
        }, e -> Ui.toast(this, msg(e)));
    }

    private void workflowMenu(final Workflow w) {
        final boolean active = "active".equals(w.state);
        String[] items = {"▶  Run workflow…", active ? "⏸  Disable workflow" : "▶  Enable workflow", "📄  View workflow file"};
        Ui.pickList(this, w.name, items, i -> {
            if (i == 0) dispatchDialog(w);
            else if (i == 1) act(() -> { api.setWorkflowEnabled(owner, repo, w.id, !active); return active ? "Workflow disabled" : "Workflow enabled"; });
            else IdeActivity.open(this, owner, repo, w.path);
        });
    }

    private void dispatchDialog(final Workflow w) {
        LinearLayout box = Ui.vbox(this);
        final android.widget.EditText ref = Ui.input(this, "Branch or tag", defaultBranch);
        ref.setSingleLine(true);
        final android.widget.EditText inputs = Ui.input(this, "Inputs (optional), one per line: name=value", "");
        inputs.setMinLines(3);
        inputs.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputs.setGravity(android.view.Gravity.TOP);
        box.addView(ref, Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(inputs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        box.addView(Ui.muted(this, "The workflow needs a workflow_dispatch trigger."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        new GlassDialog(this).setTitle("Run " + w.name).setIcon("▶").setView(box).setPositiveButton("Run", (d, x) -> {
            final JSONObject in = new JSONObject();
            for (String line : inputs.getText().toString().split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) try { in.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim()); } catch (Exception ignored) { }
            }
            final String r = ref.getText().toString().trim();
            act(() -> { api.dispatch(owner, repo, w.id, r, in); return "Workflow dispatched"; });
        }).setNegativeButton("Cancel", null).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Downloader.onResult(this, req, res, data);
    }
}
