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

/** Monochrome SaaS dashboard: hero card, stat grid, 7-day chart, quick actions, latest build, projects, activity table. */
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
        return h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : h < 21 ? "Good evening" : "Good night";
    }

    void refresh() {
        col.removeAllViews();
        final AppState st = a.getAppState();
        int pushes = 0, files = 0, weekPushes = 0, weekFiles = 0;
        long bytes = 0;
        float[] perDay = new float[7];
        String[] labels = new String[7];
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0);
        long todayStart = start.getTimeInMillis(), dayMs = 86400000L;
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
            if (daysAgo >= 0 && daysAgo < 7) { perDay[6 - daysAgo] += h.fileCount; weekPushes++; weekFiles += h.fileCount; }
        }

        // ---- page title
        LinearLayout head = Ui.hbox(a);
        head.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 4));
        LinearLayout tt = Ui.vbox(a);
        tt.addView(Ui.label(a, greeting() + (st.active == null ? "" : ", " + st.active.display())));
        tt.addView(Ui.text(a, "Dashboard", 28, true, R.attr.gpText), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 2, 0, 0));
        head.addView(tt, Ui.weight(1f));
        ImageView av = new ImageView(a);
        ImageLoader.circle(av, st.active == null ? null : st.active.avatar, 44);
        head.addView(av);
        col.addView(head);

        // ---- hero card
        LinearLayout hero = Ui.vbox(a);
        hero.setBackgroundResource(R.drawable.bg_hero);
        hero.setPadding(Ui.dp(a, 22), Ui.dp(a, 20), Ui.dp(a, 22), Ui.dp(a, 18));
        hero.setElevation(Ui.dp(a, 6));
        TextView hl = Ui.label(a, "Files pushed this week");
        hl.setTextColor(Ui.color(a, R.attr.gpOnPrimary)); hl.setAlpha(0.7f);
        hero.addView(hl);
        TextView big = Ui.text(a, "0", 44, true, R.attr.gpOnPrimary);
        hero.addView(big, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        Ui.countUp(big, weekFiles, "");
        TextView sub = Ui.text(a, weekPushes + (weekPushes == 1 ? " push" : " pushes") + "  •  " + PathUtils.formatSize(bytes) + " total uploaded", 12, false, R.attr.gpOnPrimary);
        sub.setAlpha(0.8f);
        hero.addView(sub);
        LinearLayout hb = Ui.hbox(a);
        TextView who = Ui.label(a, st.active == null ? "" : "@" + st.active.login);
        who.setTextColor(Ui.color(a, R.attr.gpOnPrimary)); who.setAlpha(0.8f);
        hb.addView(who, Ui.weight(1f));
        final TextView rate = Ui.label(a, "API …");
        rate.setTextColor(Ui.color(a, R.attr.gpOnPrimary)); rate.setAlpha(0.8f);
        hb.addView(rate);
        hero.addView(hb, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 22, 0, 0));
        col.addView(hero, side(8, 12));
        if (st.api() != null) Ui.async(() -> st.api().rateLimit(), rl -> rate.setText("API " + rl[0] + "/" + rl[1]), e -> rate.setText(""));

        // ---- stat grid
        LinearLayout g1 = Ui.hbox(a), g2 = Ui.hbox(a);
        g1.addView(stat("Pushes", pushes, R.drawable.i_upload, false), cell(0, 5));
        g1.addView(stat("Files", files, R.drawable.i_file_text, false), cell(5, 0));
        View repoCard = stat("Repositories", st.repos.size(), R.drawable.i_folder, true);
        g2.addView(repoCard, cell(0, 5));
        g2.addView(stat("Data", -1, R.drawable.i_database, false, PathUtils.formatSize(bytes)), cell(5, 0));
        col.addView(g1, side(0, 10));
        col.addView(g2, side(0, 12));
        if (!st.reposLoaded) st.loadRepos(false, () -> { if (reposNum != null) Ui.countUp(reposNum, st.repos.size(), ""); });

        // ---- chart
        LinearLayout chartCard = Ui.card(a);
        LinearLayout ch = Ui.hbox(a);
        LinearLayout ct = Ui.vbox(a);
        ct.addView(Ui.label(a, "Activity"));
        ct.addView(Ui.text(a, "Last 7 days", 16, true, R.attr.gpText), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 2, 0, 0));
        ch.addView(ct, Ui.weight(1f));
        ch.addView(Ui.pill(a, "FILES", R.attr.gpMuted));
        chartCard.addView(ch);
        BarChartView chart = new BarChartView(a);
        chart.setData(perDay, labels, Ui.color(a, R.attr.gpText), Ui.color(a, R.attr.gpMuted), Ui.color(a, R.attr.gpMuted), Ui.color(a, R.attr.gpAuroraA));
        chartCard.addView(chart, Ui.lp(a, Ui.MATCH, Ui.dp(a, 150), 0, 8, 0, 0));
        col.addView(chartCard, side(0, 4));

        // ---- quick actions
        col.addView(Ui.section(a, "Quick actions"));
        LinearLayout r1 = Ui.hbox(a), r2 = Ui.hbox(a);
        r1.addView(action(R.drawable.i_cloud_upload, "New push", v -> a.select(1)), cell(0, 4));
        r1.addView(action(R.drawable.i_zap, "Actions", v -> RepoPicker.pick(a, "Actions for…", r -> ActionsActivity.open(a, r.owner, r.name))), cell(4, 4));
        r1.addView(action(R.drawable.i_tag, "Releases", v -> RepoPicker.pick(a, "Releases for…", r -> ReleasesActivity.open(a, r.owner, r.name))), cell(4, 0));
        r2.addView(action(R.drawable.i_code, "Code IDE", v -> RepoPicker.pick(a, "Open in Code IDE…", r -> IdeActivity.open(a, r.owner, r.name, null))), cell(0, 4));
        r2.addView(action(R.drawable.i_key, "Secrets", v -> RepoPicker.pick(a, "Secrets for…", r -> SecretsActivity.open(a, r.owner, r.name))), cell(4, 4));
        r2.addView(action(R.drawable.i_sparkles, "AI README", v -> RepoPicker.pick(a, "Write README for…", r -> AiReadmeActivity.open(a, r.owner, r.name))), cell(4, 0));
        LinearLayout r3 = Ui.hbox(a);
        r3.addView(action(R.drawable.i_import, "Import repo", v -> a.startActivity(new Intent(a, ImportRepoActivity.class))), cell(0, 4));
        r3.addView(action(R.drawable.i_toolbox, "Tools", v -> a.startActivity(new Intent(a, ToolsActivity.class))), cell(4, 4));
        r3.addView(action(R.drawable.i_file_text, "Report", v -> report()), cell(4, 0));
        col.addView(r1, side(0, 8));
        col.addView(r2, side(0, 8));
        col.addView(r3, side(0, 4));

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
                row.addView(Ui.iconTileRes(a, ActionsActivity.iconFor(r), 40), Ui.lp(a, Ui.dp(a, 40), Ui.dp(a, 40), 0, 0, 12, 0));
                LinearLayout t = Ui.vbox(a);
                t.addView(Ui.label(a, "Latest build • " + parts[1]));
                TextView tt2 = Ui.title(a, r.title);
                tt2.setSingleLine(true);
                t.addView(tt2);
                t.addView(Ui.muted(a, "#" + r.number + " • " + (r.conclusion == null ? r.status.replace('_', ' ') : r.conclusion) + " • " + Ui.agoIso(r.createdAt)));
                row.addView(t, Ui.weight(1f));
                row.addView(Ui.iconRes(a, R.drawable.i_chevron_right, 18, R.attr.gpMuted));
                c.addView(row);
                c.setOnClickListener(v -> ActionsActivity.open(a, parts[0], parts[1]));
                latest.addView(c);
                Ui.fadeIn(c, 0);
            }, null);
        }

        // ---- favourites + recent projects
        Set<String> seen = new HashSet<>();
        LinearLayout row = Ui.hbox(a);
        for (String fav : st.favorites) if (seen.add(fav)) row.addView(projectCard(fav, "Favorite", null), Ui.lp(a, Ui.dp(a, 176), Ui.WRAP, 0, 0, 10, 0));
        for (HistoryEntry h : st.history) if (seen.add(h.repoFullName) && seen.size() <= 12) row.addView(projectCard(h.repoFullName, h.branch + (h.targetDir.isEmpty() ? "" : " / " + h.targetDir), h), Ui.lp(a, Ui.dp(a, 176), Ui.WRAP, 0, 0, 10, 0));
        col.addView(Ui.section(a, "Favorites & recent projects"));
        if (seen.isEmpty()) col.addView(Ui.empty(a, "📁", "No projects yet", "Tap + , choose a repository and push your first files."));
        else {
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            row.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 6), Ui.dp(a, 4));
            hs.addView(row);
            col.addView(hs);
        }

        // ---- activity table
        col.addView(Ui.section(a, "Recent activity"));
        if (st.history.isEmpty()) col.addView(Ui.empty(a, "🕘", "Nothing here yet", "Your pushes and failed uploads will show up here."));
        else {
            LinearLayout table = Ui.card(a);
            table.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
            for (int i = 0; i < st.history.size() && i < 20; i++) {
                if (i > 0) { View d = new View(a); d.setBackgroundColor(Ui.color(a, R.attr.gpOutline)); table.addView(d, Ui.lp(a, Ui.MATCH, 1, 16, 0, 16, 0)); }
                table.addView(activityRow(st.history.get(i)));
            }
            col.addView(table, side(0, 8));
        }
        Ui.stagger(col);
    }

    private View stat(String label, int count, int icon, boolean isRepos) { return stat(label, count, icon, isRepos, null); }

    private View stat(String label, int count, int icon, boolean isRepos, String text) {
        LinearLayout c = Ui.card(a);
        LinearLayout top = Ui.hbox(a);
        top.addView(Ui.label(a, label), Ui.weight(1f));
        top.addView(Ui.iconRes(a, icon, 18, R.attr.gpMuted));
        c.addView(top);
        TextView v = Ui.text(a, text != null ? text : String.valueOf(count), text != null && text.length() > 6 ? 20 : 28, true, R.attr.gpText);
        c.addView(v, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        if (count >= 0 && text == null) Ui.countUp(v, count, "");
        if (isRepos) reposNum = v;
        return c;
    }

    private View action(int icon, String label, final View.OnClickListener l) {
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 12));
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView e = Ui.iconTileRes(a, icon, 44);
        c.addView(e, Ui.lp(a, Ui.dp(a, 44), Ui.dp(a, 44), 0, 0, 0, 8));
        ((LinearLayout.LayoutParams) e.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        TextView t = Ui.center(Ui.text(a, label, 12, true, R.attr.gpText));
        t.setSingleLine(true);
        c.addView(t);
        c.setOnClickListener(v -> { Ui.haptic(v); l.onClick(v); });
        return c;
    }

    private View projectCard(final String full, String sub, HistoryEntry h) {
        LinearLayout c = Ui.card(a);
        String[] parts = full.split("/");
        c.addView(Ui.iconTileRes(a, a.getAppState().isFavorite(full) ? R.drawable.i_star : R.drawable.i_folder, 36));
        TextView name = Ui.title(a, parts[parts.length - 1]);
        name.setSingleLine(true);
        c.addView(name, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        TextView br = Ui.muted(a, sub);
        br.setSingleLine(true);
        c.addView(br);
        if (h != null) c.addView(Ui.label(a, h.fileCount + " files • " + Ui.ago(h.time)), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        final String owner = parts.length == 2 ? parts[0] : "", repo = parts.length == 2 ? parts[1] : "";
        c.setOnClickListener(v -> { if (!owner.isEmpty()) UploadActivity.open(a, owner, repo); });
        return c;
    }

    private View activityRow(final HistoryEntry h) {
        LinearLayout r = Ui.hbox(a);
        r.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12));
        r.setBackgroundResource(R.drawable.bg_ripple_row);
        r.addView(Ui.iconRes(a, h.success ? R.drawable.i_check_circle : R.drawable.i_x_circle, 22, R.attr.gpText), Ui.lp(a, Ui.dp(a, 22), Ui.dp(a, 22), 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(a);
        TextView msg = Ui.title(a, h.message.isEmpty() ? "(no message)" : h.message);
        msg.setSingleLine(true);
        t.addView(msg);
        TextView repo = Ui.muted(a, h.repoFullName + " • " + h.branch);
        repo.setSingleLine(true);
        t.addView(repo);
        TextView meta = Ui.label(a, h.success ? h.fileCount + " files • " + PathUtils.formatSize(h.bytes) + " • " + Ui.ago(h.time) : "Failed • " + (h.error == null ? "" : h.error));
        meta.setSingleLine(true);
        t.addView(meta, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 3, 0, 0));
        r.addView(t, Ui.weight(1f));
        if (h.success && !h.sha.isEmpty()) {
            r.addView(iconBtn(R.drawable.i_copy, v -> { Ui.copy(a, "SHA", h.sha); Ui.toast(a, "SHA copied"); }));
            if (!h.url.isEmpty()) r.addView(iconBtn(R.drawable.i_external, v -> Ui.openUrl(a, h.url)));
        }
        final String[] parts = h.repoFullName.split("/");
        r.setOnClickListener(v -> { if (parts.length == 2) UploadActivity.open(a, parts[0], parts[1]); });
        return r;
    }

    private ImageView iconBtn(int res, View.OnClickListener l) {
        ImageView iv = Ui.iconRes(a, res, 18, R.attr.gpMuted);
        iv.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
        iv.setLayoutParams(Ui.lp(Ui.dp(a, 38), Ui.dp(a, 38)));
        iv.setOnClickListener(l);
        return iv;
    }

    private void report() {
        AppState st = a.getAppState();
        if (st.history.isEmpty()) { Ui.toast(a, "No uploads yet"); return; }
        StringBuilder sb = new StringBuilder("GitPush upload report\n\n");
        for (HistoryEntry h : st.history) {
            sb.append(h.success ? "[OK] " : "[FAIL] ").append(h.repoFullName).append(" [").append(h.branch).append("] ").append(h.message)
                    .append(" - ").append(h.fileCount).append(" files, ").append(PathUtils.formatSize(h.bytes));
            if (!h.sha.isEmpty()) sb.append(" - ").append(h.sha, 0, Math.min(7, h.sha.length()));
            if (!h.success) sb.append(" - ").append(h.error == null ? "failed" : h.error);
            sb.append('\n');
        }
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString());
        a.startActivity(Intent.createChooser(i, "Export upload report"));
    }
}
