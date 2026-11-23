package com.gitpush.app;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.imp.Engine;
import com.gitpush.app.imp.Job;
import com.gitpush.app.imp.Jobs;
import com.gitpush.app.imp.Src;
import com.gitpush.app.imp.SyncJob;
import com.gitpush.app.imp.SyncLoop;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.Ui;

import java.util.List;
import java.util.Map;

/** Import jobs (live log, retry, cancel), the Sync Loop and a short import history. */
public class ImportJobsActivity extends BaseActivity {
    private ScrollView sv;
    private LinearLayout col;
    private boolean ticking;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildScreen("Import jobs", "Live progress, logs and Sync loop", true);
        sv = new ScrollView(this);
        col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);
        render();
    }

    @Override protected void onResume() { super.onResume(); ticking = true; tick(); }
    @Override protected void onPause() { super.onPause(); ticking = false; }

    private void tick() {
        app.main.postDelayed(() -> { if (ticking && alive()) { render(); tick(); } }, 1500);
    }

    private void render() {
        final int y = sv.getScrollY();
        col.removeAllViews();
        // ---- sync loop
        LinearLayout lc = Ui.card(this);
        lc.addView(Ui.label(this, "Sync loop"));
        lc.addView(Ui.text(this, SyncLoop.isOn() ? "Running" : "Stopped", 18, true, R.attr.gpText), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        lc.addView(Ui.muted(this, SyncLoop.status() + ". While the loop is on a notification keeps GitPush alive and every enabled job is re-checked on its interval; only changed files are copied, in one commit."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        LinearLayout btn = Ui.hbox(this);
        btn.addView(SyncLoop.isOn() ? Ui.outlineButton(this, "⏸ Stop loop", v -> { SyncLoop.stop(app); render(); }) : Ui.button(this, "▶ Start loop", v -> {
            if (SyncLoop.load(st.store.prefs).isEmpty()) { Ui.toast(this, "Add a sync job first"); return; }
            SyncLoop.start(app); render();
        }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        btn.addView(Ui.outlineButton(this, "＋ Link repos", v -> addSync()));
        lc.addView(btn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        List<SyncJob> sj = SyncLoop.load(st.store.prefs);
        for (final SyncJob s : sj) {
            LinearLayout r = Ui.vbox(this);
            r.setPadding(0, Ui.dp(this, 10), 0, 0);
            r.addView(Ui.text(this, s.label(), 13, true, R.attr.gpText));
            r.addView(Ui.muted(this, (s.enabled ? "every " + (s.intervalMin >= 60 ? s.intervalMin / 60 + " h" : s.intervalMin + " min") : "paused") + (s.deleteRemoved ? " • mirrors deletions" : "") + " • " + (s.lastRun == 0 ? "never" : Ui.ago(s.lastRun)) + "\n" + s.lastResult));
            r.setOnClickListener(v -> syncMenu(s));
            lc.addView(r);
        }
        col.addView(lc, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        // ---- jobs
        List<Job> jobs = Jobs.snapshot();
        col.addView(Ui.label(this, "Import jobs"), Ui.lp(this, Ui.MATCH, Ui.WRAP, 4, 4, 0, 6));
        if (jobs.isEmpty()) col.addView(Ui.muted(this, "No imports in this session yet."));
        for (final Job j : jobs) {
            LinearLayout c = Ui.card(this);
            LinearLayout h = Ui.hbox(this);
            TextView t = Ui.text(this, j.plan.title(), 14, true, R.attr.gpText);
            t.setMaxLines(2);
            h.addView(t, Ui.weight(1f));
            h.addView(Ui.pill(this, j.status, R.attr.gpText));
            c.addView(h);
            c.addView(Ui.muted(this, j.running() ? j.phase : j.summary() + " • " + Ui.ago(j.endedAt == 0 ? j.startedAt : j.endedAt)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
            if (j.running()) {
                ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                pb.setMax(1000);
                if (j.progress < 0) pb.setIndeterminate(true); else pb.setProgress((int) (j.progress * 1000));
                pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.attr.gpPrimary)));
                pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.attr.gpPrimary)));
                c.addView(pb, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            }
            StringBuilder sb = new StringBuilder();
            synchronized (j.steps) {
                for (Map.Entry<String, Integer> e : j.steps.entrySet()) {
                    int v = e.getValue();
                    sb.append(v == Job.DONE ? "✔ " : v == Job.FAILED ? "✘ " : v == Job.RUNNING ? "▶ " : "☐ ").append(e.getKey()).append("   ");
                }
            }
            c.addView(Ui.muted(this, sb.toString()), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
            c.setOnClickListener(v -> logDialog(j));
            col.addView(c, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        }

        // ---- history
        List<String[]> hist = Jobs.history(st.store.prefs);
        if (!hist.isEmpty()) {
            col.addView(Ui.label(this, "History"), Ui.lp(this, Ui.MATCH, Ui.WRAP, 4, 12, 0, 6));
            for (final String[] h : hist) {
                col.addView(Ui.text(this, h[0], 13, true, R.attr.gpText), Ui.lp(this, Ui.MATCH, Ui.WRAP, 4, 8, 0, 0));
                col.addView(Ui.muted(this, h[1] + " • " + h[3] + " • " + Ui.ago(Long.parseLong(h[2]))), Ui.lp(this, Ui.MATCH, Ui.WRAP, 4, 0, 0, 0));
            }
        }
        sv.post(() -> sv.scrollTo(0, y));
    }

    private void logDialog(final Job j) {
        final TextView tv = Ui.text(this, j.logText(), 11, false, R.attr.gpText);
        tv.setTypeface(Ui.mono(this, false));
        tv.setTextIsSelectable(true);
        ScrollView s = new ScrollView(this);
        s.addView(tv);
        LinearLayout wrap = Ui.vbox(this);
        wrap.addView(s, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 320)));
        GlassDialog d = new GlassDialog(this).setTitle(j.plan.title()).setIcon("📜").setView(wrap)
                .setPositiveButton("Copy log", (x, w) -> Ui.copy(this, "import log", j.logText()))
                .setNegativeButton("Close", null);
        if (j.running()) d.setNeutralButton("Cancel job", (x, w) -> Engine.cancel(j));
        else if (j.stepCount(Job.FAILED) > 0 || j.status.equals("cancelled") || j.stepCount(Job.PENDING) > 0) d.setNeutralButton("Retry", (x, w) -> { Engine.retry(app, j); render(); });
        d.show();
    }

    private void addSync() {
        Form.show(this, "Link two repositories", "🔁", "Save", Form.list(
                Form.text("src", "Source (owner/repo or URL)", ""), Form.text("dst", "Target you own (owner/repo)", ""),
                Form.text("dir", "Folder in target (optional)", ""),
                Form.choice("int", "Check every", new String[]{"15 min", "1 hour", "6 hours", "24 hours"}, 1),
                Form.toggle("del", "Also delete files removed at the source", false)), m -> {
            final String[] a = Src.parse(m.get("src")), b = Src.parse(m.get("dst"));
            if (a == null || b == null) { Ui.toast(this, "Use owner/repo for both"); return; }
            final SyncJob s = new SyncJob();
            s.account = st.active.login; s.srcOwner = a[0]; s.srcName = a[1]; s.ref = a[2]; s.tOwner = b[0]; s.tName = b[1]; s.tDir = m.get("dir");
            String iv = m.get("int"); s.intervalMin = iv.startsWith("15") ? 15 : iv.startsWith("1 hour") ? 60 : iv.startsWith("6") ? 360 : 1440;
            s.deleteRemoved = Boolean.parseBoolean(m.get("del"));
            async(() -> { s.branch = st.api().repo(b[0], b[1]).defaultBranch; return s; }, x -> {
                List<SyncJob> all = SyncLoop.load(st.store.prefs);
                all.add(x);
                SyncLoop.save(st.store.prefs, all);
                Ui.toast(this, "Saved – press Sync now or start the loop");
                render();
            }, e -> Ui.toast(this, msg(e)));
        });
    }

    private void syncMenu(final SyncJob s) {
        Ui.pickList(this, s.label(), new String[]{"▶  Sync now", s.enabled ? "⏸  Pause this job" : "▶  Resume this job", s.deleteRemoved ? "☑  Mirror deletions: on (tap to turn off)" : "☐  Mirror deletions: off (tap to turn on)", "🗑  Remove job"}, i -> {
            if (i == 0) {
                final com.gitpush.app.ui.ProgressDlg dlg = new com.gitpush.app.ui.ProgressDlg(this, "Syncing", "🔁", null);
                async(() -> SyncLoop.runOne(app, s, false), r -> { dlg.close(); Ui.toast(this, r); render(); }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
                return;
            }
            List<SyncJob> all = SyncLoop.load(st.store.prefs);
            for (int k = 0; k < all.size(); k++) {
                if (all.get(k).id != s.id) continue;
                if (i == 1) all.get(k).enabled = !all.get(k).enabled;
                else if (i == 2) all.get(k).deleteRemoved = !all.get(k).deleteRemoved;
                else all.remove(k);
                break;
            }
            SyncLoop.save(st.store.prefs, all);
            render();
        });
    }
}
