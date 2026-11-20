package com.gitpush.app;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.ArtifactInfo;
import com.gitpush.app.data.Models.Job;
import com.gitpush.app.data.Models.Run;
import com.gitpush.app.data.Models.Step;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Downloader;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.util.ArrayList;
import java.util.List;

/** One workflow run: live timer, step progress, per-job timeline with step durations, quick actions. */
public class RunDetailActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo, long runId) {
        c.startActivity(new Intent(c, RunDetailActivity.class).putExtra("owner", owner).putExtra("repo", repo).putExtra("run", runId));
    }

    private String owner, repo;
    private long runId;
    private GitHubApi api;
    private Run run;
    private List<Job> jobs = new ArrayList<>();
    private LinearLayout col;
    private ScrollView sv;
    private boolean resumed, firstProgress = true;
    private TextView elapsed;
    private final Runnable poll = this::load;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (elapsed != null && RunDetailActivity.this.run != null) elapsed.setText(Ui.dur(ActionsActivity.durationMs(RunDetailActivity.this.run)));
            if (resumed && RunDetailActivity.this.run != null && RunDetailActivity.this.run.active()) app.main.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        runId = getIntent().getLongExtra("run", 0);
        api = st.api();
        buildScreen("Run details", owner + "/" + repo, true);
        addAction(R.drawable.i_refresh, v -> { v.animate().rotationBy(360f).setDuration(500).start(); load(); });
        sv = new ScrollView(this);
        col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);
        col.addView(Ui.muted(this, "Loading…"));
    }

    @Override protected void onResume() { super.onResume(); resumed = true; load(); }
    @Override protected void onPause() { super.onPause(); resumed = false; app.main.removeCallbacks(poll); app.main.removeCallbacks(tick); }

    private void load() {
        app.main.removeCallbacks(poll);
        async(() -> new Object[]{api.run(owner, repo, runId), api.jobs(owner, repo, runId)}, r -> {
            run = (Run) r[0];
            @SuppressWarnings("unchecked") List<Job> js = (List<Job>) r[1];
            jobs = js;
            render();
            if (run.active() && resumed) { app.main.postDelayed(poll, 5000); app.main.removeCallbacks(tick); app.main.post(tick); }
        }, e -> { col.removeAllViews(); col.addView(Ui.text(this, msg(e), 13, false, R.attr.gpError)); });
    }

    private void render() {
        col.removeAllViews();
        setSubtitle(owner + "/" + repo + " • #" + run.number);
        // ---- summary
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        ImageView ic = Ui.iconTileRes(this, ActionsActivity.iconFor(run), 46);
        if (run.active()) {
            ObjectAnimator a = ObjectAnimator.ofFloat(ic, "rotation", 0f, 360f);
            a.setDuration(1400); a.setRepeatCount(ObjectAnimator.INFINITE); a.setInterpolator(new android.view.animation.LinearInterpolator()); a.start();
        }
        h.addView(ic, Ui.lp(this, Ui.dp(this, 46), Ui.dp(this, 46), 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(this);
        t.addView(Ui.label(this, run.conclusion == null ? run.status.replace('_', ' ') : run.conclusion.replace('_', ' ')));
        TextView title = Ui.text(this, run.title, 16, true, R.attr.gpText);
        title.setMaxLines(3);
        t.addView(title);
        h.addView(t, Ui.weight(1f));
        c.addView(h);
        LinearLayout stats = Ui.hbox(this);
        elapsed = Ui.text(this, Ui.dur(ActionsActivity.durationMs(run)), 26, true, R.attr.gpText);
        stats.addView(elapsed, Ui.weight(1f));
        int done = 0, total = 0;
        for (Job j : jobs) for (Step s : j.steps) { total++; if ("completed".equals(s.status)) done++; }
        stats.addView(Ui.label(this, total == 0 ? "no steps yet" : done + "/" + total + " steps"));
        c.addView(stats, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 6));
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(1000);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.attr.gpText)));
        pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.attr.gpAuroraA)));
        c.addView(pb, Ui.lp(this, Ui.MATCH, Ui.dp(this, 6), 0, 0, 0, 10));
        int target = total == 0 ? 0 : (int) (1000f * done / total);
        if (firstProgress) { ObjectAnimator.ofInt(pb, "progress", 0, target).setDuration(700).start(); firstProgress = false; }
        else pb.setProgress(target);
        LinearLayout pills = Ui.hbox(this);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        String[] meta = {run.branch == null ? "" : run.branch, run.event, run.sha.length() >= 7 ? run.sha.substring(0, 7) : run.sha, run.actor, "attempt " + run.attempt};
        for (String m : meta) if (!m.isEmpty()) pills.addView(Ui.pill(this, m, R.attr.gpMuted), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        hs.addView(pills);
        c.addView(hs);
        col.addView(c, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));

        // ---- actions
        LinearLayout acts = Ui.hbox(this);
        acts.addView(Ui.button(this, "🔁 Re-run", v -> act(() -> { api.rerun(owner, repo, runId, false); return "Re-run started"; })), cell(0, 4));
        if (run.active()) acts.addView(Ui.outlineButton(this, "⛔ Cancel", v -> act(() -> { api.cancelRun(owner, repo, runId); return "Cancel requested"; })), cell(4, 4));
        else acts.addView(Ui.outlineButton(this, "Failed only", v -> act(() -> { api.rerun(owner, repo, runId, true); return "Re-running failed jobs"; })), cell(4, 4));
        acts.addView(Ui.outlineButton(this, "More", v -> more()), cell(4, 0));
        col.addView(acts, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 4));

        // ---- jobs timeline
        col.addView(Ui.section(this, "Jobs & steps"));
        if (jobs.isEmpty()) col.addView(Ui.muted(this, "No jobs yet – they appear when a runner picks the run up."));
        for (final Job j : jobs) col.addView(jobCard(j), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        Ui.stagger(col);
    }

    private LinearLayout.LayoutParams cell(float l, float r) { LinearLayout.LayoutParams p = Ui.lp(this, 0, Ui.WRAP, l, 0, r, 0); p.weight = 1f; return p; }

    private long jobMs(Job j) {
        long s = Ui.isoMs(j.startedAt);
        if (s == 0) return 0;
        long e = j.completedAt != null ? Ui.isoMs(j.completedAt) : System.currentTimeMillis();
        return Math.max(0, e - s);
    }

    private long stepMs(Step s) {
        long a = Ui.isoMs(s.startedAt);
        if (a == 0) return 0;
        long e = s.completedAt != null ? Ui.isoMs(s.completedAt) : System.currentTimeMillis();
        return Math.max(0, e - a);
    }

    private View jobCard(final Job j) {
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.iconRes(this, ActionsActivity.iconFor(j.status, j.conclusion), 22, R.attr.gpText), Ui.lp(this, Ui.dp(this, 22), Ui.dp(this, 22), 0, 0, 10, 0));
        TextView n = Ui.title(this, j.name);
        n.setSingleLine(true);
        h.addView(n, Ui.weight(1f));
        h.addView(Ui.text(this, Ui.dur(jobMs(j)), 12, true, R.attr.gpMuted));
        c.addView(h);
        long max = 1;
        for (Step s : j.steps) max = Math.max(max, stepMs(s));
        for (Step s : j.steps) {
            LinearLayout r = Ui.hbox(this);
            r.setPadding(0, Ui.dp(this, 8), 0, 0);
            r.addView(Ui.iconRes(this, ActionsActivity.iconFor(s.status, s.conclusion), 14, R.attr.gpMuted), Ui.lp(this, Ui.dp(this, 14), Ui.dp(this, 14), 0, 0, 8, 0));
            LinearLayout mid = Ui.vbox(this);
            TextView sn = Ui.text(this, s.number + ".  " + s.name, 12, false, R.attr.gpText);
            sn.setSingleLine(true);
            mid.addView(sn);
            LinearLayout bar = Ui.hbox(this);
            float frac = Math.max(0.02f, (float) stepMs(s) / max);
            View fill = new View(this);
            fill.setBackgroundResource(R.drawable.bg_chip_on);
            View rest = new View(this);
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, Ui.dp(this, 3), frac), rp = new LinearLayout.LayoutParams(0, Ui.dp(this, 3), Math.max(0.001f, 1f - frac));
            bar.addView(fill, fp); bar.addView(rest, rp);
            mid.addView(bar, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 3, 0, 0));
            r.addView(mid, Ui.weight(1f));
            r.addView(Ui.text(this, Ui.dur(stepMs(s)), 11, false, R.attr.gpMuted), Ui.lp(this, Ui.WRAP, Ui.WRAP, 8, 0, 0, 0));
            c.addView(r);
        }
        c.addView(Ui.outlineButton(this, "📄 View logs", v -> JobLogActivity.open(this, owner, repo, j.id, j.name)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        return c;
    }

    private void act(final Ui.Task<String> t) {
        async(t, m -> { Ui.toast(this, m); app.main.postDelayed(this::load, 1500); }, e -> Ui.toast(this, msg(e)));
    }

    private void more() {
        String[] items = {"📦  Artifacts (download)", "📜  Download all logs (ZIP)", KeepAliveService.isWatching(runId) ? "🔔  Already watching" : "🔔  Notify me when it finishes", "🗑  Delete run", "🌐  Open on GitHub"};
        Ui.pickList(this, "Run #" + run.number, items, i -> {
            if (i == 0) async(() -> api.artifacts(owner, repo, runId), list -> {
                final List<ArtifactInfo> ok = new ArrayList<>();
                for (ArtifactInfo a : list) if (!a.expired) ok.add(a);
                if (ok.isEmpty()) { Ui.toast(this, "No downloadable artifacts for this run"); return; }
                String[] labels = new String[ok.size()];
                for (int k = 0; k < labels.length; k++) labels[k] = "📦  " + ok.get(k).name + "  (" + PathUtils.formatSize(ok.get(k).size) + ")";
                Ui.pickList(this, "Download artifact", labels, k -> Downloader.request(this, api, ok.get(k).url, ok.get(k).name + ".zip"));
            }, e -> Ui.toast(this, msg(e)));
            else if (i == 1) Downloader.request(this, api, "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/logs", repo + "-run-" + run.number + "-logs.zip");
            else if (i == 2) { KeepAliveService.watchRun(this, owner, repo, runId, run.title); Ui.toast(this, "You'll get a notification when it finishes"); }
            else if (i == 3) Ui.confirmDanger(this, "Delete run #" + run.number + "?", "The run and its logs are removed from GitHub.", "Delete", () ->
                    async(() -> { api.deleteRun(owner, repo, runId); return "ok"; }, x -> { Ui.toast(this, "Run deleted"); finish(); }, e -> Ui.toast(this, msg(e))));
            else Ui.openUrl(this, run.htmlUrl);
        });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Downloader.onResult(this, req, res, data);
    }
}
