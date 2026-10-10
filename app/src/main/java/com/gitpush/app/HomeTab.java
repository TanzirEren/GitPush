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
import com.gitpush.app.ui.ImageLoader;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.util.HashSet;
import java.util.Set;

class HomeTab {
    final MainActivity a;
    final ScrollView view;
    private final LinearLayout col;

    HomeTab(MainActivity a) {
        this.a = a;
        view = new ScrollView(a);
        view.setClipToPadding(false);
        col = Ui.vbox(a);
        col.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, MainActivity.NAV_SPACE));
        view.addView(col);
    }

    private LinearLayout.LayoutParams side(float top, float bottom) { return Ui.lp(a, Ui.MATCH, Ui.WRAP, 20, top, 20, bottom); }

    void refresh() {
        col.removeAllViews();
        AppState st = a.getAppState();
        // header
        LinearLayout head = Ui.hbox(a);
        head.setPadding(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), Ui.dp(a, 12));
        LinearLayout names = Ui.vbox(a);
        names.addView(Ui.muted(a, "Hello,"));
        names.addView(Ui.text(a, st.active == null ? "Developer" : st.active.display(), 20, true, R.attr.gpText));
        head.addView(names, Ui.weight(1f));
        ImageView av = new ImageView(a);
        ImageLoader.circle(av, st.active == null ? null : st.active.avatar, 48);
        head.addView(av);
        col.addView(head);

        int pushes = 0, files = 0;
        for (HistoryEntry h : st.history) if (h.success) { pushes++; files += h.fileCount; }
        LinearLayout stats = Ui.hbox(a);
        stats.addView(stat("Pushes", String.valueOf(pushes)), w(0, 5));
        stats.addView(stat("Files", String.valueOf(files)), w(5, 5));
        stats.addView(stat("Repos", String.valueOf(st.repos.size())), w(5, 0));
        col.addView(stats, side(0, 0));

        LinearLayout quick = Ui.hbox(a);
        quick.addView(action("☁", "New push", v -> a.select(1)), w(0, 5));
        quick.addView(action("🧰", "Tools", v -> a.startActivity(new Intent(a, ToolsActivity.class))), w(5, 5));
        quick.addView(action("📄", "Report", v -> report()), w(5, 0));
        col.addView(quick, side(14, 0));

        col.addView(Ui.section(a, "Previously worked projects"));
        Set<String> seen = new HashSet<>();
        LinearLayout row = Ui.hbox(a);
        for (HistoryEntry h : st.history) {
            if (!seen.add(h.repoFullName) || seen.size() > 10) continue;
            row.addView(projectCard(h), Ui.lp(a, Ui.dp(a, 190) / 1, Ui.WRAP, 0, 0, 12, 0));
        }
        if (seen.isEmpty()) col.addView(Ui.empty(a, "📁", "No projects yet", "Tap the + button below, choose a repository and push your first files."));
        else {
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            row.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 8), 0);
            hs.addView(row);
            col.addView(hs);
        }

        col.addView(Ui.section(a, "Recent activity"));
        if (st.history.isEmpty()) col.addView(Ui.empty(a, "🕘", "Nothing here yet", "Your pushes and failed uploads will show up here."));
        for (int i = 0; i < st.history.size() && i < 25; i++) col.addView(activityRow(st.history.get(i)), side(0, 8));
    }

    private LinearLayout.LayoutParams w(float l, float r) { LinearLayout.LayoutParams p = Ui.lp(a, 0, Ui.WRAP, l, 0, r, 0); p.weight = 1f; return p; }

    private View stat(String label, String value) {
        LinearLayout c = Ui.card(a);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(Ui.text(a, value, 20, true, R.attr.gpPrimary));
        c.addView(Ui.muted(a, label));
        return c;
    }

    private View action(String emoji, String label, View.OnClickListener l) {
        LinearLayout c = Ui.card(a);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(Ui.text(a, emoji, 22, false, R.attr.gpText));
        c.addView(Ui.text(a, label, 12, true, R.attr.gpText));
        c.setOnClickListener(v -> { Ui.haptic(v); l.onClick(v); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        c.setLayoutParams(lp);
        return c;
    }

    private View projectCard(HistoryEntry h) {
        LinearLayout c = Ui.card(a);
        String[] parts = h.repoFullName.split("/");
        c.addView(Ui.text(a, "📁", 20, false, R.attr.gpText));
        TextView name = Ui.title(a, parts[parts.length - 1]);
        name.setSingleLine(true);
        c.addView(name, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        TextView br = Ui.muted(a, h.branch + (h.targetDir.isEmpty() ? "" : " / " + h.targetDir));
        br.setSingleLine(true);
        c.addView(br);
        c.addView(Ui.text(a, Ui.ago(h.time), 11, true, R.attr.gpPrimary));
        c.setOnClickListener(v -> { if (parts.length == 2) UploadActivity.open(a, parts[0], parts[1]); });
        return c;
    }

    private View activityRow(HistoryEntry h) {
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12));
        LinearLayout r = Ui.hbox(a);
        r.addView(Ui.text(a, h.success ? "✔" : "✘", 18, true, h.success ? R.attr.gpOk : R.attr.gpError), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(a);
        TextView msg = Ui.title(a, h.message.isEmpty() ? "(no message)" : h.message);
        msg.setSingleLine(true);
        t.addView(msg);
        t.addView(Ui.muted(a, h.repoFullName + " • " + h.branch));
        TextView info = Ui.text(a, h.success ? h.fileCount + " files • " + PathUtils.formatSize(h.bytes) + " • " + Ui.ago(h.time) : (h.error == null ? "Failed" : h.error),
                11, false, h.success ? R.attr.gpPrimary : R.attr.gpError);
        t.addView(info);
        r.addView(t, Ui.weight(1f));
        if (h.success && !h.sha.isEmpty()) {
            TextView cp = Ui.textButton(a, "SHA", R.attr.gpPrimary, v -> { Ui.copy(a, "SHA", h.sha); Ui.toast(a, "SHA copied"); });
            r.addView(cp);
            if (!h.url.isEmpty()) r.addView(Ui.textButton(a, "Open", R.attr.gpPrimary, v -> Ui.openUrl(a, h.url)));
        }
        c.addView(r);
        String[] parts = h.repoFullName.split("/");
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
