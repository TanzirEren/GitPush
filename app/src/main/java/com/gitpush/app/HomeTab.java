package com.gitpush.app;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.Models.HistoryEntry;
import com.gitpush.app.data.Models.Run;
import com.gitpush.app.ui.BarChartView;
import com.gitpush.app.ui.ImageLoader;
import com.gitpush.app.ui.RepoPicker;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Glass dashboard: hero, stats, 7-day chart, quick actions, latest build, projects and activity. */
class HomeTab {
    final MainActivity a;
    final ScrollView view;
    private final LinearLayout col;
    private TextView reposNum;

    HomeTab(MainActivity a) {
        this.a = a;
        view = new ScrollView(a);
        view.setClipToPadding(false);
        col = Ui.vbox(a);
        col.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, MainActivity.NAV_SPACE));
        view.addView(col);
    }

    private LinearLayout.LayoutParams side(float top, float bottom) { return Ui.lp(a, Ui.MATCH, Ui.WRAP, 16, top, 16, bottom); }
    private LinearLayout.LayoutParams cell(float l, float r) { LinearLayout.LayoutParams p = Ui.lp(a, 0, Ui.WRAP, l, 0, r, 0); p.weight = 1f; return p; }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 5) return "Good night 🌙";
        if (h < 12) return "Good morning ☀";
        if (h < 17) return "Good afternoon 🌤";
        if (h < 21) return "Good evening 🌆";
        return "Good night 🌙";
    }

    void refresh() {
        col.removeAllViews();
        final AppState st = a.getAppState();
        int pushes = 0, files = 0, weekPushes = 0;
        long bytes = 0;
        float[] perDay = new float[7];
        String[] labels = new String[7];
        Calendar today = Calendar.getInstance();
        long dayMs = 86400000L;
        Calendar start = (Calendar) today.clone();
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0);
        long todayStart = start.getTimeInMillis();
        for (int i = 0; i < 7; i++) {
            Calendar d = (Calendar) start.clone();
            d.add(Calendar.DAY_OF_YEAR, i - 6);
            labels[i] = d.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
        }
        for (HistoryEntry h : st.history) {
            if (!h.success) continue;
            pushes++; files += h.fileCount; bytes += h.bytes;
            Calendar hc = Calendar.getInstance();
            hc.setTimeInMillis(h.time);
            hc.set(Calendar.HOUR_OF_DAY, 0); hc.set(Calendar.MINUTE, 0); hc.set(Calendar.SECOND, 0); hc.set(Calendar.MILLISECOND, 0);
            int daysAgo = (int) Math.round((todayStart - hc.getTimeInMillis()) / (double) dayMs);
            if (daysAgo >= 0 && daysAgo < 7) { perDay[6 - daysAgo] += h.fileCount; weekPushes++; }
        }

        // ---- hero
        LinearLayout hero = Ui.vbox(a);
        hero.setBackgroundResource(R.drawable.bg_hero);
        hero.setPadding(Ui.dp(a, 20), Ui.dp(a, 18), Ui.dp(a, 20), Ui.dp(a, 16));
        hero.setElevation(Ui.dp(a, 6));
        LinearLayout top = Ui.hbox(a);
        LinearLayout names = Ui.vbox(a);
        TextView g = Ui.text(a, greeting(), 12, false, R.attr.gpOnPrimary); g.setAlpha(0.85f);
        names.addView(g);
        TextView nm = Ui.text(a, st.active == null ? "Developer" : st.active.display(), 22, true, R.attr.gpOnPrimary);
        nm.setSingleLine(true);
        names.addView(nm);
        if (st.active != null) { TextView hd = Ui.text(a, "@" + st.active.login, 12, false, R.attr.gpOnPrimary); hd.setAlpha(0.8f); names.addView(hd); }
        top.addView(names, Ui.weight(1f));
        ImageView av = new ImageView(a);
        ImageLoader.circle(av, st.active == null ? null : st.active.avatar, 56);
        top.addView(av);
        hero.addView(top);
        final TextView sub = Ui.text(a, "☁ " + weekPushes + " push" + (weekPushes == 1 ? "" : "es") + " this week  •  " + PathUtils.formatSize(bytes) + " uploaded", 12, true, R.attr.gpOnPrimary);
        sub.setAlpha(0.9f);
        hero.addView(sub, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
        col.addView(hero, side(4, 12));
        if (st.api() != null) Ui.async(() -> st.api().rateLimit(), rl ->
                sub.setText(sub.getText() + "\n⚡ API " + rl[0] + " / " + rl[1] + " requests left"), null);

        // ---- stats
        LinearLayout stats = Ui.hbox(a);
        stats.addView(stat("Pushes", String.valueOf(pushes), pushes), cell(0, 4));
        stats.addView(stat("Files", String.valueOf(files), files), cell(4, 4));
        View repoCard = stat("Repos", String.valueOf(st.repos.size()), st.repos.size());
        reposNum = (TextView) ((LinearLayout) repoCard).getChildAt(0);
        stats.addView(repoCard, cell(4, 4));
        stats.addView(stat("Data", PathUtils.formatSize(bytes), -1), cell(4, 0));
        col.addView(stats, side(0, 12));
        if (!st.reposLoaded) st.loadRepos(false, () -> { if (reposNum != null) Ui.countUp(reposNum, st.repos.size(), ""); });

        // ---- chart
        LinearLayout chartCard = Ui.card(a);
        LinearLayout ch = Ui.hbox(a);
        ch.addView(Ui.text(a, "Last 7 days", 14, true, R.attr.gpText), Ui.weight(1f));
        ch.addView(Ui.muted(a, "files pushed"));
        chartCard.addView(ch);
        BarChartView chart = new BarChartView(a);
        chart.setData(perDay, labels, Ui.color(a, R.attr.gpPrimary), Ui.color(a, R.attr.gpSecondary), Ui.color(a, R.attr.gpMuted), Ui.color(a, R.attr.gpAuroraA));
        chartCard.addView(chart, Ui.lp(a, Ui.MATCH, Ui.dp(a, 150), 0, 6, 0, 0));
        col.addView(chartCard, side(0, 12));

        // ---- quick actions
        col.addView(Ui.section(a, "Quick actions"));
        LinearLayout r1 = Ui.hbox(a), r2 = Ui.hbox(a);
        r1.addView(action("☁", "New push", v -> a.select(1)), cell(0, 4));
        r1.addView(action("⚡", "Actions", v -> RepoPicker.pick(a, "Actions for…", r -> ActionsActivity.open(a, r.owner, r.name))), cell(4, 4));
        r1.addView(action("🏷", "Releases", v -> RepoPicker.pick(a, "Releases for…", r -> ReleasesActivity.open(a, r.owner, r.name))), cell(4, 0));
        r2.addView(action("✨", "AI README", v -> RepoPicker.pick(a, "Write README for…", r -> AiReadmeActivity.open(a, r.owner, r.name))), cell(0, 4));
        r2.addView(action("🧰", "Tools", v -> a.startActivity(new Intent(a, ToolsActivity.class))), cell(4, 4));
        r2.addView(action("📄", "Report", v -> report()), cell(4, 0));
        col.addView(r1, side(0, 8));
        col.addView(r2, side(0, 4));

        // ---- latest build
        final LinearLayout latest = Ui.vbox(a);
        col.addView(latest, side(8, 0));
        if (!st.history.isEmpty() && st.api() != null) {
            final String[] parts = st.history.get(0).repoFullName.split("/");
            if (parts.length == 2) Ui.async(() -> st.api().runs(parts[0], parts[1], 1), (List<Run> runs) -> {
                if (runs.isEmpty() || latest.getChildCount() > 0) return;
                Run r = runs.get(0);
                LinearLayout c = Ui.card(a);
                LinearLayout row = Ui.hbox(a);
                row.addView(Ui.text(a, ActionsActivity.glyph(r.status, r.conclusion), 22, true, ActionsActivity.colorFor(r.status, r.conclusion)), Ui.lp(a, Ui.dp(a, 34), Ui.WRAP, 0, 0, 8, 0));
                LinearLayout t = Ui.vbox(a);
                t.addView(Ui.text(a, "Latest build • " + parts[1], 11, true, R.attr.gpPrimary));
                TextView tt = Ui.title(a, r.title);
                tt.setSingleLine(true);
                t.addView(tt);
                t.addView(Ui.muted(a, "#" + r.number + " • " + (r.conclusion == null ? r.status.replace('_', ' ') : r.conclusion) + " • " + Ui.agoIso(r.createdAt)));
                row.addView(t, Ui.weight(1f));
                c.addView(row);
                c.setOnClickListener(v -> ActionsActivity.open(a, parts[0], parts[1]));
                latest.addView(c);
                Ui.fadeIn(c, 0);
            }, null);
        }

        // ---- favourites + recent projects
        Set<String> seen = new HashSet<>();
        LinearLayout row = Ui.hbox(a);
        for (String fav : st.favorites) if (seen.add(fav)) row.addView(projectCard(fav, "⭐ Favorite", null), Ui.lp(a, Ui.dp(a, 178), Ui.WRAP, 0, 0, 10, 0));
        for (HistoryEntry h : st.history) if (seen.add(h.repoFullName) && seen.size() <= 12) row.addView(projectCard(h.repoFullName, h.branch + (h.targetDir.isEmpty() ? "" : " / " + h.targetDir), h), Ui.lp(a, Ui.dp(a, 178), Ui.WRAP, 0, 0, 10, 0));
        col.addView(Ui.section(a, "Favorites & recent projects"));
        if (seen.isEmpty()) col.addView(Ui.empty(a, "📁", "No projects yet", "Tap the + button, choose a repository and push your first files."));
        else {
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            row.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 6), 0);
            hs.addView(row);
            col.addView(hs);
        }

        // ---- activity
        col.addView(Ui.section(a, "Recent activity"));
        if (st.history.isEmpty()) col.addView(Ui.empty(a, "🕘", "Nothing here yet", "Your pushes and failed uploads will show up here."));
        for (int i = 0; i < st.history.size() && i < 20; i++) col.addView(activityRow(st.history.get(i)), side(0, 8));
        Ui.stagger(col);
    }

    private View stat(String label, String value, int countTo) {
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12));
        TextView v = Ui.center(Ui.text(a, value, value.length() > 6 ? 13 : 20, true, R.attr.gpPrimary));
        c.addView(v);
        c.addView(Ui.center(Ui.muted(a, label)));
        if (countTo >= 0) Ui.countUp(v, countTo, "");
        return c;
    }

    private View action(String emoji, String label, final View.OnClickListener l) {
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 6), Ui.dp(a, 12));
        TextView e = Ui.center(Ui.text(a, emoji, 22, false, R.attr.gpText));
        e.setGravity(Gravity.CENTER);
        e.setBackgroundResource(R.drawable.bg_icon_tile);
        c.addView(e, Ui.lp(a, Ui.dp(a, 46), Ui.dp(a, 46), 0, 0, 0, 6));
        ((LinearLayout.LayoutParams) e.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        c.addView(Ui.center(Ui.text(a, label, 12, true, R.attr.gpText)));
        c.setOnClickListener(v -> { Ui.haptic(v); l.onClick(v); });
        return c;
    }

    private View projectCard(final String full, String sub, HistoryEntry h) {
        LinearLayout c = Ui.card(a);
        String[] parts = full.split("/");
        c.addView(Ui.text(a, "📁", 20, false, R.attr.gpText));
        TextView name = Ui.title(a, parts[parts.length - 1]);
        name.setSingleLine(true);
        c.addView(name, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        TextView br = Ui.muted(a, sub);
        br.setSingleLine(true);
        c.addView(br);
        if (h != null) c.addView(Ui.text(a, h.fileCount + " files • " + Ui.ago(h.time), 11, true, R.attr.gpPrimary));
        final String owner = parts.length == 2 ? parts[0] : "", repo = parts.length == 2 ? parts[1] : "";
        c.setOnClickListener(v -> { if (!owner.isEmpty()) UploadActivity.open(a, owner, repo); });
        return c;
    }

    private View activityRow(final HistoryEntry h) {
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12));
        LinearLayout r = Ui.hbox(a);
        TextView ic = Ui.text(a, h.success ? "✔" : "✘", 16, true, h.success ? R.attr.gpOk : R.attr.gpError);
        ic.setGravity(Gravity.CENTER);
        ic.setBackgroundResource(R.drawable.bg_icon_tile);
        r.addView(ic, Ui.lp(a, Ui.dp(a, 38), Ui.dp(a, 38), 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(a);
        TextView msg = Ui.title(a, h.message.isEmpty() ? "(no message)" : h.message);
        msg.setSingleLine(true);
        t.addView(msg);
        TextView repo = Ui.muted(a, h.repoFullName + " • " + h.branch);
        repo.setSingleLine(true);
        t.addView(repo);
        t.addView(Ui.text(a, h.success ? h.fileCount + " files • " + PathUtils.formatSize(h.bytes) + " • " + Ui.ago(h.time) : (h.error == null ? "Failed" : h.error),
                11, false, h.success ? R.attr.gpPrimary : R.attr.gpError));
        r.addView(t, Ui.weight(1f));
        if (h.success && !h.sha.isEmpty()) {
            r.addView(Ui.textButton(a, "SHA", R.attr.gpPrimary, v -> { Ui.copy(a, "SHA", h.sha); Ui.toast(a, "SHA copied"); }));
            if (!h.url.isEmpty()) r.addView(Ui.textButton(a, "Open", R.attr.gpPrimary, v -> Ui.openUrl(a, h.url)));
        }
        c.addView(r);
        final String[] parts = h.repoFullName.split("/");
        c.setOnClickListener(v -> { if (parts.length == 2) UploadActivity.open(a, parts[0], parts[1]); });
        return c;
    }

    private void report() {
        AppState st = a.getAppState();
        if (st.history.isEmpty()) { Ui.toast(a, "No uploads yet"); return; }
        StringBuilder sb = new StringBuilder("GitPush upload report\n\n");
        for (HistoryEntry h : st.history) {
            sb.append(h.success ? "✔ " : "✘ ").append(h.repoFullName).append(" [").append(h.branch).append("] ").append(h.message)
                    .append(" – ").append(h.fileCount).append(" files, ").append(PathUtils.formatSize(h.bytes));
            if (!h.sha.isEmpty()) sb.append(" – ").append(h.sha, 0, Math.min(7, h.sha.length()));
            if (!h.success) sb.append(" – ").append(h.error == null ? "failed" : h.error);
            sb.append('\n');
        }
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString());
        a.startActivity(Intent.createChooser(i, "Export upload report"));
    }
}
