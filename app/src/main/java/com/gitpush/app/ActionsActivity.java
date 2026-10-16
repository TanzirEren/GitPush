package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.ArtifactInfo;
import com.gitpush.app.data.Models.Job;
import com.gitpush.app.data.Models.Run;
import com.gitpush.app.data.Models.Step;
import com.gitpush.app.data.Models.Workflow;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Downloader;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.util.ArrayList;
import java.util.List;

/** GitHub Actions: runs, jobs, re-run / cancel / delete, artifacts download, workflow dispatch. */
public class ActionsActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, ActionsActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private String owner, repo, defaultBranch = "main";
    private GitHubApi api;
    private final List<Run> runs = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView status;
    private boolean loading = true, resumed;
    private String error;
    private final Runnable auto = this::refresh;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        buildScreen("Actions", owner + "/" + repo, true);
        addTextAction("Workflows", v -> workflows());
        addTextAction("⟳", v -> refresh());
        LinearLayout page = Ui.vbox(this);
        status = Ui.muted(this, "Loading…");
        status.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), 0);
        page.addView(status);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        adapter = new BaseAdapter() {
            public int getCount() { return runs.size(); }
            public Object getItem(int i) { return runs.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { View v = row(runs.get(i)); if (cv == null) Ui.fadeIn(v, i); return v; }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        body.addView(page);
        com.gitpush.app.data.Models.Repo r = st.findRepo(owner, repo);
        if (r != null) defaultBranch = r.defaultBranch;
        refresh();
    }

    @Override protected void onResume() { super.onResume(); resumed = true; }
    @Override protected void onPause() { super.onPause(); resumed = false; app.main.removeCallbacks(auto); }
    @Override protected void onDestroy() { app.main.removeCallbacks(auto); super.onDestroy(); }

    private void refresh() {
        app.main.removeCallbacks(auto);
        async(() -> api.runs(owner, repo, 30), list -> {
            loading = false; error = null; runs.clear(); runs.addAll(list); render();
            boolean active = false;
            for (Run r : list) if (r.active()) active = true;
            if (active && resumed) app.main.postDelayed(auto, 8000);
        }, e -> { loading = false; error = msg(e); render(); });
    }

    private void render() {
        adapter.notifyDataSetChanged();
        if (loading) status.setText("Loading…");
        else if (error != null && runs.isEmpty()) status.setText("Couldn't load runs\n" + error);
        else if (runs.isEmpty()) status.setText("No workflow runs yet.\nPush a workflow file to .github/workflows/ and it will show up here.");
        else status.setText("");
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    static String glyph(String status, String conclusion) {
        if (conclusion == null) return "⏳";
        switch (conclusion) {
            case "success": return "✔";
            case "failure": case "timed_out": case "startup_failure": return "✘";
            case "cancelled": case "skipped": return "⊘";
            default: return "•";
        }
    }

    static int colorFor(String status, String conclusion) {
        if (conclusion == null) return R.attr.gpWarn;
        if (conclusion.equals("success")) return R.attr.gpOk;
        if (conclusion.equals("cancelled") || conclusion.equals("skipped")) return R.attr.gpMuted;
        return R.attr.gpError;
    }

    private View row(final Run r) {
        LinearLayout wrap = Ui.vbox(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.text(this, glyph(r.status, r.conclusion), 20, true, colorFor(r.status, r.conclusion)), Ui.lp(this, Ui.dp(this, 30), Ui.WRAP, 0, 0, 10, 0));
        LinearLayout t = Ui.vbox(this);
        TextView title = Ui.title(this, r.title);
        title.setMaxLines(2);
        t.addView(title);
        t.addView(Ui.muted(this, "#" + r.number + " • " + r.name + " • " + (r.branch == null ? "" : r.branch) + " • " + r.event));
        t.addView(Ui.text(this, (r.conclusion == null ? r.status.replace('_', ' ') : r.conclusion.replace('_', ' ')) + " • " + Ui.agoIso(r.createdAt) + " • " + r.actor,
                11, false, colorFor(r.status, r.conclusion)));
        h.addView(t, Ui.weight(1f));
        c.addView(h);
        c.setOnClickListener(v -> runMenu(r));
        wrap.addView(c);
        return wrap;
    }

    private void runMenu(final Run r) {
        List<String> items = new ArrayList<>();
        items.add("🔍  Jobs & steps");
        items.add("🔁  Re-run all jobs");
        items.add("🔁  Re-run failed jobs");
        if (r.active()) items.add("⛔  Cancel run");
        items.add("📦  Artifacts (download)");
        items.add("📜  Download logs");
        items.add("🗑  Delete run");
        items.add("🌐  Open on GitHub");
        final String[] arr = items.toArray(new String[0]);
        Ui.pickList(this, "#" + r.number + "  " + r.title, arr, i -> {
            String label = arr[i];
            if (label.contains("Jobs")) jobs(r);
            else if (label.contains("all jobs")) act(() -> { api.rerun(owner, repo, r.id, false); return "Re-run started"; });
            else if (label.contains("failed jobs")) act(() -> { api.rerun(owner, repo, r.id, true); return "Re-running failed jobs"; });
            else if (label.contains("Cancel")) act(() -> { api.cancelRun(owner, repo, r.id); return "Cancel requested"; });
            else if (label.contains("Artifacts")) artifacts(r);
            else if (label.contains("logs")) Downloader.request(this, api, "/repos/" + owner + "/" + repo + "/actions/runs/" + r.id + "/logs", repo + "-run-" + r.number + "-logs.zip");
            else if (label.contains("Delete")) Ui.confirmDanger(this, "Delete run #" + r.number + "?", "The run and its logs are removed from GitHub.", "Delete", () -> act(() -> { api.deleteRun(owner, repo, r.id); return "Run deleted"; }));
            else Ui.openUrl(this, r.htmlUrl);
        });
    }

    private void act(final Ui.Task<String> t) {
        async(t, m -> { Ui.toast(this, m); app.main.postDelayed(this::refresh, 1500); }, e -> Ui.toast(this, msg(e)));
    }

    private void jobs(final Run r) {
        async(() -> api.jobs(owner, repo, r.id), list -> {
            StringBuilder sb = new StringBuilder();
            if (list.isEmpty()) sb.append("No jobs yet.");
            for (Job j : list) {
                sb.append(glyph(j.status, j.conclusion)).append("  ").append(j.name).append('\n');
                for (Step s : j.steps) sb.append("     ").append(glyph(s.status, s.conclusion)).append(' ').append(s.number).append(". ").append(s.name).append('\n');
                sb.append('\n');
            }
            new GlassDialog(this).setTitle("Jobs • #" + r.number).setIcon("⚡").setMessage(sb.toString().trim()).setPositiveButton("Close", null)
                    .setNeutralButton("Open", (d, w) -> Ui.openUrl(this, r.htmlUrl)).show();
        }, e -> Ui.toast(this, msg(e)));
    }

    private void artifacts(final Run r) {
        async(() -> api.artifacts(owner, repo, r.id), list -> {
            final List<ArtifactInfo> ok = new ArrayList<>();
            for (ArtifactInfo a : list) if (!a.expired) ok.add(a);
            if (ok.isEmpty()) { Ui.toast(this, "No downloadable artifacts for this run"); return; }
            String[] labels = new String[ok.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = "📦  " + ok.get(i).name + "  (" + PathUtils.formatSize(ok.get(i).size) + ")";
            Ui.pickList(this, "Download artifact", labels, i -> Downloader.request(this, api, ok.get(i).url, ok.get(i).name + ".zip"));
        }, e -> Ui.toast(this, msg(e)));
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
        String[] items = {"▶  Run workflow…", active ? "⏸  Disable workflow" : "▶  Enable workflow", "📄  View file"};
        Ui.pickList(this, w.name, items, i -> {
            if (i == 0) Ui.prompt(this, "Run on branch", "Branch or tag", defaultBranch,
                    ref -> act(() -> { api.dispatch(owner, repo, w.id, ref.trim()); return "Workflow dispatched (needs workflow_dispatch trigger)"; }));
            else if (i == 1) act(() -> { api.setWorkflowEnabled(owner, repo, w.id, !active); return active ? "Workflow disabled" : "Workflow enabled"; });
            else { st.viewerPath = w.path; st.viewerSha = ""; st.viewerBranch = defaultBranch; FileViewerActivity.open(this, owner, repo); }
        });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Downloader.onResult(this, req, res, data);
    }
}
