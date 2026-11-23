package com.gitpush.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Asset;
import com.gitpush.app.data.Models.CommitInfo;
import com.gitpush.app.data.Models.Release;
import com.gitpush.app.data.Models.Run;
import com.gitpush.app.data.Models.SecretInfo;
import com.gitpush.app.imp.Engine;
import com.gitpush.app.imp.Job;
import com.gitpush.app.imp.Jobs;
import com.gitpush.app.imp.Plan;
import com.gitpush.app.imp.Plans;
import com.gitpush.app.imp.Src;
import com.gitpush.app.imp.Tree;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.RepoPicker;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Import Studio (v2.2.0): look up any repository, browse it like GitHub (files, releases, Actions, secrets, commits, issues),
 * tick what to copy (nothing ticked = everything), then import in the background with notifications.
 */
public class ImportRepoActivity extends BaseActivity {
    private static final String[] TABS = {"Overview", "Files", "Releases", "Actions", "Secrets", "Commits", "Issues"};

    private GitHubApi api;
    private LinearLayout homeCol;
    private EditText input;
    private Src src;
    private FrameLayout content;
    private LinearLayout tabsBar;
    private TextView summary;
    private View studio;
    private int tab = 0;

    // options
    private boolean optReleases = true, optAssets = true, optVars = true, optLabels = true, optIssues = false, optComments = false, optKeepAuthors = true, optRunWf = false, optDisableWf = false;
    private int optIssueState = 1, optIssueLimit = 100, optReplay = 0;
    private final Set<Long> relSel = new LinkedHashSet<>();
    private final Map<String, String> secretVals = new LinkedHashMap<>();
    private final Set<String> varOff = new HashSet<>();
    // tree UI
    private final Set<String> expanded = new HashSet<>();
    private String query = "";
    private List<Tree.Row> rows = new ArrayList<>();
    private BaseAdapter adapter;
    // pre-import dialog memory
    private String dName = "", dDesc = "", dHome = "", dTopics = "", dExisting = "", dDir = "";
    private boolean dPriv = true, dNote = false, dSyncDel = false;
    private int dTarget = 0, dSync = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        api = st.api();
        buildScreen("Import repository", "Clone like GitHub – pick exactly what to copy", true);
        addAction(R.drawable.i_history, v -> startActivity(new android.content.Intent(this, ImportJobsActivity.class)));
        showHome();
    }

    @Override public void onBackPressed() {
        if (studio != null && studio.getParent() != null) { body.removeView(studio); studio = null; src = null; homeCol.setVisibility(View.VISIBLE); setSubtitle("Clone like GitHub – pick exactly what to copy"); return; }
        super.onBackPressed();
    }

    // ================================================================== home (look-up screen)
    private void showHome() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        homeCol = col;
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);

        LinearLayout c = Ui.card(this);
        c.addView(Ui.label(this, "Source repository"));
        input = Ui.input(this, "github.com/owner/repo  or  owner/repo", "");
        input.setSingleLine(true);
        c.addView(input, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        c.addView(Ui.button(this, "🔍 Look up", v -> lookup(input.getText().toString(), null)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        LinearLayout two = Ui.hbox(this);
        two.addView(Ui.outlineButton(this, "📋 Paste", v -> paste()), weightLp(6, 0));
        two.addView(Ui.outlineButton(this, "🔁 Batch", v -> batch()), weightLp(0, 6));
        c.addView(two, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        c.addView(Ui.muted(this, "Public repos and private ones your token can access. Branch / tag links (…/tree/dev) and .git or SSH URLs work too."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(c, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout f = Ui.card(this);
        f.addView(Ui.label(this, "What the studio can copy"));
        String[][] feats = {
                {"📂", "File tree", "Tick files and folders, search, glob patterns, presets"},
                {"🏷", "Releases", "Notes, pre-release flags and assets, oldest first"},
                {"⚡", "Actions", "Workflow files pushed last with [skip ci], optional disable"},
                {"🔑", "Secrets & variables", "Variables copied, secrets re-entered and encrypted on device"},
                {"🕘", "Commit history", "Replay the last N commits with authors and dates"},
                {"💬", "Issues & labels", "With comments, milestones, closed state, mentions neutralised"},
                {"🔁", "Sync loop", "Keep the copy updated automatically, only changed files"}};
        for (String[] r : feats) {
            LinearLayout line = Ui.hbox(this);
            line.addView(Ui.iconTile(this, r[0], 34), Ui.lp(this, Ui.dp(this, 34), Ui.dp(this, 34), 0, 0, 12, 0));
            LinearLayout t = Ui.vbox(this);
            t.addView(Ui.text(this, r[1], 14, true, R.attr.gpText));
            t.addView(Ui.muted(this, r[2]));
            line.addView(t, Ui.weight(1f));
            f.addView(line, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        }
        col.addView(f, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        List<String[]> hist = Jobs.history(st.store.prefs);
        if (!hist.isEmpty()) {
            LinearLayout h = Ui.card(this);
            h.addView(Ui.label(this, "Recent imports"));
            for (int i = 0; i < Math.min(4, hist.size()); i++) {
                String[] r = hist.get(i);
                h.addView(Ui.text(this, r[0], 13, true, R.attr.gpText), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
                h.addView(Ui.muted(this, r[1] + " • " + r[3] + " • " + Ui.ago(Long.parseLong(r[2]))));
            }
            h.addView(Ui.outlineButton(this, "Jobs, logs and Sync loop", v -> startActivity(new android.content.Intent(this, ImportJobsActivity.class))), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
            col.addView(h);
        } else {
            col.addView(Ui.outlineButton(this, "Jobs, logs and Sync loop", v -> startActivity(new android.content.Intent(this, ImportJobsActivity.class))));
        }
        Ui.stagger(col);
    }

    private LinearLayout.LayoutParams weightLp(int r, int l) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        p.setMargins(Ui.dp(this, l), 0, Ui.dp(this, r), 0);
        return p;
    }

    private void paste() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            CharSequence t = cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0 ? cm.getPrimaryClip().getItemAt(0).coerceToText(this) : "";
            input.setText(t);
            if (Src.parse(t.toString()) != null) lookup(t.toString(), null);
        } catch (Exception e) { Ui.toast(this, "Clipboard is empty"); }
    }

    // ================================================================== batch (loop over many URLs)
    private void batch() {
        Form.show(this, "Batch import", "🔁", "Import all", Form.list(Form.multi("urls", "One repository per line", ""), Form.toggle("priv", "Create as private", true)), m -> {
            final List<String[]> list = new ArrayList<>();
            for (String line : m.get("urls").split("\\n")) { String[] p = Src.parse(line); if (p != null) list.add(p); }
            if (list.isEmpty()) { Ui.toast(this, "No valid repositories found"); return; }
            final boolean priv = Boolean.parseBoolean(m.get("priv"));
            final String token = st.active.token, login = st.active.login;
            Ui.toast(this, "Batch of " + list.size() + " started in the background");
            KeepAliveService.begin(app, "Batch import");
            app.io.execute(() -> {
                int ok = 0, n = 0;
                GitHubApi a = new GitHubApi(token);
                for (String[] p : list) {
                    n++;
                    try {
                        KeepAliveService.update(app, "Batch " + n + "/" + list.size() + ": " + p[1], -1);
                        Src s = Src.load(a, p[0], p[1], p[2], null);
                        Plan plan = Plans.basic(s);
                        plan.priv = priv;
                        String base = s.name, nm = base;
                        for (int k = 2; k < 30 && exists(a, login, nm); k++) nm = base + "-" + k;
                        plan.name = nm;
                        Job j = Engine.start(app, plan, token);
                        j.future.get();
                        if (j.status.equals("done")) ok++;
                    } catch (Exception ignored) { }
                }
                Notifier.show(app, "Batch import finished", ok + " of " + list.size() + " repositories imported");
                KeepAliveService.end(app);
            });
        });
    }

    private static boolean exists(GitHubApi a, String owner, String name) {
        try { a.repo(owner, name); return true; } catch (Exception e) { return false; }
    }

    // ================================================================== look-up
    private void lookup(String text, String ref) {
        final String[] p = Src.parse(text);
        if (p == null) { Ui.toast(this, "Enter owner/repo or a GitHub URL"); return; }
        final String wantRef = ref != null ? ref : p[2];
        final ProgressDlg dlg = new ProgressDlg(this, "Looking up", "🔍", null);
        async(() -> Src.load(api, p[0], p[1], wantRef, t -> app.main.post(() -> dlg.set(t, -1f))), s -> {
            dlg.close();
            src = s;
            resetOptions();
            openStudio();
        }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    private void resetOptions() {
        optReleases = !src.releases.isEmpty(); optAssets = true; optVars = !src.variables.isEmpty(); optLabels = true;
        optIssues = false; optComments = false; optKeepAuthors = true; optRunWf = false; optDisableWf = false;
        optIssueState = 1; optIssueLimit = 100; optReplay = 0;
        relSel.clear(); secretVals.clear(); varOff.clear(); expanded.clear(); query = "";
        dName = src.name; dDesc = src.repo.description == null ? "" : src.repo.description; dHome = src.homepage;
        dTopics = TextUtils.join(", ", src.topics);
    }

    // ================================================================== studio shell
    private void openStudio() {
        homeCol.setVisibility(View.GONE);
        if (studio != null) body.removeView(studio);
        LinearLayout root = Ui.vbox(this);
        studio = root;
        // header card
        LinearLayout h = Ui.hbox(this);
        h.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 4));
        h.addView(Ui.iconTileRes(this, src.repo.isPrivate ? R.drawable.i_lock : R.drawable.i_globe, 40), Ui.lp(this, Ui.dp(this, 40), Ui.dp(this, 40), 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(this);
        TextView nm = Ui.title(this, src.full());
        nm.setSingleLine(true); nm.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(nm);
        t.addView(Ui.label(this, src.ref + " • " + src.headSha.substring(0, 7)));
        h.addView(t, Ui.weight(1f));
        h.addView(Ui.outlineButton(this, "🌿 Ref", v -> pickRef()));
        root.addView(h, Ui.lp(Ui.MATCH, Ui.WRAP));
        // tabs
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabsBar = Ui.hbox(this);
        tabsBar.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 6));
        hs.addView(tabsBar);
        root.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        // bottom bar
        LinearLayout bar = Ui.hbox(this);
        bar.setBackgroundResource(R.drawable.bg_glass);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        summary = Ui.muted(this, "");
        bar.addView(summary, Ui.weight(1f));
        bar.addView(Ui.button(this, "📥 Import", v -> beginFlow()));
        root.addView(bar, Ui.lp(Ui.MATCH, Ui.WRAP));
        body.addView(root, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        setSubtitle(src.full());
        showTab(0);
    }

    private void showTab(int i) {
        tab = i;
        tabsBar.removeAllViews();
        for (int k = 0; k < TABS.length; k++) {
            final int kk = k;
            String label = TABS[k];
            if (k == 2 && !src.releases.isEmpty()) label += " " + src.releases.size();
            if (k == 3 && !src.workflowPaths.isEmpty()) label += " " + src.workflowPaths.size();
            if (k == 6 && src.issueTotal > 0) label += " " + src.issueTotal;
            tabsBar.addView(Ui.chip(this, label, k == i, v -> showTab(kk)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        }
        content.removeAllViews();
        View v;
        switch (i) {
            case 1: v = filesTab(); break;
            case 2: v = releasesTab(); break;
            case 3: v = actionsTab(); break;
            case 4: v = secretsTab(); break;
            case 5: v = commitsTab(); break;
            case 6: v = issuesTab(); break;
            default: v = overviewTab(); break;
        }
        content.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        updateSummary();
    }

    private void updateSummary() {
        Tree t = src.tree;
        StringBuilder sb = new StringBuilder();
        sb.append(t.root.sel).append('/').append(t.root.files).append(" files • ").append(PathUtils.formatSize(t.root.selSize));
        if (optReleases && !src.releases.isEmpty()) sb.append(" • ").append(relSel.isEmpty() ? src.releases.size() : relSel.size()).append(" rel");
        if (optIssues) sb.append(" • issues");
        if (optReplay > 0) sb.append(" • ").append(optReplay).append(" commits");
        summary.setText(sb.toString());
    }

    private LinearLayout scrollCol() {
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        return col;
    }

    private View wrapScroll(LinearLayout col) {
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        return sv;
    }

    private LinearLayout line(int icon, String title, String sub, View right, View.OnClickListener l) {
        LinearLayout r = Ui.hbox(this);
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        r.addView(Ui.iconRes(this, icon, 20, R.attr.gpText), Ui.lp(this, Ui.dp(this, 20), Ui.dp(this, 20), 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(this);
        TextView a = Ui.text(this, title, 14, true, R.attr.gpText);
        a.setMaxLines(2); a.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(a);
        if (sub != null && !sub.isEmpty()) { TextView s = Ui.muted(this, sub); s.setMaxLines(2); s.setEllipsize(TextUtils.TruncateAt.END); t.addView(s); }
        r.addView(t, Ui.weight(1f));
        if (right != null) r.addView(right);
        if (l != null) { r.setBackgroundResource(R.drawable.bg_ripple_row); r.setOnClickListener(l); }
        return r;
    }

    private TextView toggle(String label, boolean on, final Ui.Cb<Boolean> cb) {
        return Ui.chip(this, (on ? "✔ " : "☐ ") + label, on, v -> { cb.on(!on); showTab(tab); });
    }

    private void pickRef() {
        final List<String> refs = new ArrayList<>();
        for (com.gitpush.app.data.Models.Branch b : src.branches) refs.add(b.name);
        for (String t : src.tags) refs.add("tag: " + t);
        refs.add("Enter a tag, branch or commit SHA…");
        Ui.pickList(this, "Branch / tag", refs.toArray(new String[0]), i -> {
            if (i == refs.size() - 1) { Ui.prompt(this, "Ref", "branch, tag or SHA", src.ref, r -> reload(r.trim())); return; }
            String r = refs.get(i);
            reload(r.startsWith("tag: ") ? r.substring(5) : r);
        });
    }

    private void reload(String ref) {
        if (ref.isEmpty() || ref.equals(src.ref)) return;
        final ProgressDlg dlg = new ProgressDlg(this, "Loading " + ref, "🌿", null);
        final String o = src.owner, n = src.name;
        async(() -> Src.load(api, o, n, ref, t -> app.main.post(() -> dlg.set(t, -1f))), s -> { dlg.close(); src = s; resetOptions(); openStudio(); }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    // ================================================================== tab: overview
    private View overviewTab() {
        LinearLayout col = scrollCol();
        LinearLayout c = Ui.card(this);
        c.addView(Ui.label(this, "About"));
        c.addView(Ui.body(this, src.repo.description == null ? "No description" : src.repo.description), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        if (!src.homepage.isEmpty()) c.addView(Ui.textButton(this, "🔗 " + src.homepage, R.attr.gpPrimary, v -> Ui.openUrl(this, src.homepage.startsWith("http") ? src.homepage : "https://" + src.homepage)));
        c.addView(Ui.textButton(this, "🌐 github.com/" + src.full(), R.attr.gpPrimary, v -> Ui.openUrl(this, "https://github.com/" + src.full())));
        if (!src.topics.isEmpty()) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout tl = Ui.hbox(this);
            for (String t : src.topics) tl.addView(Ui.pill(this, t, R.attr.gpMuted), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
            hs.addView(tl);
            c.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        }
        col.addView(c, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout s = Ui.card(this);
        s.addView(Ui.label(this, "Repository facts"));
        String[][] f = {
                {"Language", src.repo.language == null ? "–" : src.repo.language}, {"License", src.license.isEmpty() ? "–" : src.license},
                {"Stars / forks", "★ " + src.repo.stars + "  •  " + src.forks}, {"Size", PathUtils.formatSize(src.repo.sizeKb * 1024L)},
                {"Created", Ui.agoIso(src.createdAt)}, {"Last push", Ui.agoIso(src.pushedAt)},
                {"Branches / tags", src.branches.size() + " / " + src.tags.size() + (src.tags.size() >= 30 ? "+" : "")},
                {"Files", src.tree.totalFiles() + (src.treeTruncated ? "+" : "")}, {"Releases", String.valueOf(src.releases.size())},
                {"Workflows", String.valueOf(src.workflowPaths.size())}, {"Issues", src.issueTotal < 0 ? "–" : src.issueOpen + " open / " + src.issueTotal},
                {"Labels", src.labelCount < 0 ? "–" : String.valueOf(src.labelCount)}};
        for (String[] r : f) {
            LinearLayout line = Ui.hbox(this);
            line.addView(Ui.muted(this, r[0]), Ui.weight(1f));
            line.addView(Ui.text(this, r[1], 13, true, R.attr.gpText));
            s.addView(line, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        }
        if (src.fork) s.addView(Ui.muted(this, "This repository is itself a fork."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        if (src.archived) s.addView(Ui.muted(this, "Archived (read-only) at the source."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(s, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        if (!src.notes.isEmpty()) {
            LinearLayout n = Ui.card(this);
            n.addView(Ui.label(this, "Heads-up"));
            for (String x : src.notes) n.addView(Ui.muted(this, "⚠ " + x), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
            col.addView(n, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        }
        LinearLayout q = Ui.card(this);
        q.addView(Ui.label(this, "Quick modes"));
        q.addView(Ui.muted(this, "Nothing ticked? Everything is copied. Use the tabs to narrow it down."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        q.addView(Ui.outlineButton(this, "🌐 Fork instead (keeps full history)", v -> Ui.confirm(this, "Fork", "Create a GitHub fork of " + src.full() + " in your account?", "Fork", this::doFork)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        col.addView(q);
        Ui.stagger(col);
        return wrapScroll(col);
    }

    private void doFork() {
        final ProgressDlg dlg = new ProgressDlg(this, "Forking", "🌐", null);
        async(() -> api.fork(src.owner, src.name), r -> {
            dlg.close(); st.repos.add(0, r);
            new GlassDialog(this).setTitle("Forked").setIcon("✔").setMessage(r.fullName + " is ready (GitHub finishes copying in a moment).")
                    .setPositiveButton("Open in IDE", (d, w) -> { IdeActivity.open(this, r.owner, r.name, null); finish(); }).setNegativeButton("Close", null).show();
        }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    // ================================================================== tab: files
    private View filesTab() {
        LinearLayout root = Ui.vbox(this);
        LinearLayout top = Ui.vbox(this);
        top.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4));
        final EditText search = Ui.input(this, "Search files…", query);
        search.setSingleLine(true);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(android.text.Editable e) { query = e.toString(); refreshRows(); }
        });
        top.addView(search);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.hbox(this);
        String[] names = {"All", "None", "Invert", "Pattern…", "Source only", "Skip junk", "Docs", "Expand all"};
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            chips.addView(Ui.chip(this, names[i], false, v -> filePreset(k)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        }
        hs.addView(chips);
        top.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        root.addView(top, Ui.lp(Ui.MATCH, Ui.WRAP));
        ListView lv = new ListView(this);
        lv.setDivider(null); lv.setDividerHeight(0);
        adapter = new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int i) { return rows.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup parent) { return rowView(rows.get(i)); }
        };
        lv.setAdapter(adapter);
        root.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        refreshRows();
        return root;
    }

    private void refreshRows() {
        rows = src.tree.rows(expanded, query);
        if (adapter != null) adapter.notifyDataSetChanged();
        updateSummary();
    }

    private View rowView(final Tree.Row r) {
        final Tree.Node n = r.node;
        LinearLayout row = Ui.hbox(this);
        row.setPadding(Ui.dp(this, 12 + r.depth * 16), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        row.setBackgroundResource(R.drawable.bg_ripple_row);
        int st2 = n.state();
        ImageView cb = Ui.iconRes(this, st2 == 2 ? R.drawable.i_square_check : st2 == 1 ? R.drawable.i_minus : R.drawable.i_square, 22, R.attr.gpText);
        cb.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        cb.setOnClickListener(v -> { src.tree.toggle(n); refreshRows(); });
        row.addView(cb, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));
        row.addView(Ui.iconRes(this, n.dir ? (expanded.contains(n.path) ? R.drawable.i_folder_open : R.drawable.i_folder) : R.drawable.i_file_text, 18, R.attr.gpMuted), Ui.lp(this, Ui.dp(this, 18), Ui.dp(this, 18), 0, 0, 10, 0));
        LinearLayout t = Ui.vbox(this);
        TextView nm = Ui.text(this, query.isEmpty() ? n.name : n.path, 14, n.dir, R.attr.gpText);
        nm.setSingleLine(true); nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.addView(nm);
        t.addView(Ui.muted(this, n.dir ? n.sel + "/" + n.files + " files • " + PathUtils.formatSize(n.size) : PathUtils.formatSize(n.size) + (n.mode.equals("100755") ? " • executable" : "")));
        row.addView(t, Ui.weight(1f));
        if (n.dir) {
            row.addView(Ui.iconRes(this, expanded.contains(n.path) ? R.drawable.i_chevron_down : R.drawable.i_chevron_right, 18, R.attr.gpMuted));
            row.setOnClickListener(v -> { if (!expanded.remove(n.path)) expanded.add(n.path); refreshRows(); });
        } else row.setOnClickListener(v -> { src.tree.toggle(n); refreshRows(); });
        return row;
    }

    private void filePreset(int k) {
        Tree t = src.tree;
        switch (k) {
            case 0: t.selectAll(); break;
            case 1: t.selectNone(); break;
            case 2: t.invert(); break;
            case 3:
                Form.show(this, "Select by pattern", "🔍", "Apply", Form.list(Form.text("g", "Globs (comma separated, e.g. *.java, docs/)", ""), Form.choice("m", "Action", new String[]{"Select", "Unselect"}, 0)), m -> {
                    int n = t.glob(m.get("g"), m.get("m").equals("Select"));
                    Ui.toast(this, n + " file(s) matched"); refreshRows();
                });
                return;
            case 4: t.only(p -> !Tree.isJunk(p) && !Tree.isDoc(p) || p.toLowerCase().startsWith("readme") || p.toLowerCase().startsWith("license")); break;
            case 5: t.filterOut(Tree::isJunk); break;
            case 6: t.only(Tree::isDoc); break;
            case 7: for (Tree.Row r : src.tree.rows(new HashSet<String>(), "")) expandAll(r.node); break;
            default: break;
        }
        refreshRows();
    }

    private void expandAll(Tree.Node n) { if (n.dir) { expanded.add(n.path); for (Tree.Node c : n.kids.values()) expandAll(c); } }

    // ================================================================== tab: releases
    private View releasesTab() {
        LinearLayout col = scrollCol();
        if (src.releases.isEmpty()) { col.addView(Ui.empty(this, "🏷", "No releases", "This repository has no GitHub releases.")); return wrapScroll(col); }
        LinearLayout opts = Ui.hbox(this);
        opts.addView(toggle("Copy releases", optReleases, x -> optReleases = x), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        opts.addView(toggle("With assets", optAssets, x -> optAssets = x));
        col.addView(opts);
        col.addView(Ui.muted(this, "Tick specific releases, or leave all unticked to copy every one. Created oldest first so the newest ends up as Latest. Tags point at the import commit."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 8));
        for (final Release r : src.releases) {
            boolean on = relSel.contains(r.id);
            long bytes = 0; for (Asset a : r.assets) bytes += a.size;
            String sub = r.tag + (r.draft ? " • draft" : "") + (r.prerelease ? " • pre-release" : "") + " • " + r.assets.size() + " asset(s)" + (bytes > 0 ? " " + PathUtils.formatSize(bytes) : "") + " • " + Ui.agoIso(r.publishedAt == null ? r.createdAt : r.publishedAt);
            col.addView(line(on ? R.drawable.i_square_check : R.drawable.i_square, r.title(), sub, null, v -> { if (!relSel.remove(r.id)) relSel.add(r.id); showTab(2); }));
        }
        return wrapScroll(col);
    }

    // ================================================================== tab: actions
    private View actionsTab() {
        LinearLayout col = scrollCol();
        if (src.workflowPaths.isEmpty()) col.addView(Ui.muted(this, "No workflow files found in .github/workflows."));
        else {
            col.addView(Ui.label(this, "Workflow files"));
            col.addView(Ui.muted(this, "Pushed last, in a separate commit, so releases and the import never start them by accident."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
            for (final String wp : src.workflowPaths) {
                Tree.Node n = src.tree.file(wp);
                boolean on = src.tree.selected.contains(wp);
                col.addView(line(on ? R.drawable.i_square_check : R.drawable.i_square, wp.substring(wp.lastIndexOf('/') + 1), wp + (n == null ? "" : " • " + PathUtils.formatSize(n.size)), null, v -> {
                    if (!src.tree.selected.remove(wp)) src.tree.selected.add(wp);
                    src.tree.recount(); showTab(3);
                }));
            }
            LinearLayout o = Ui.hbox(this);
            o.addView(toggle("Run workflows after import", optRunWf, x -> optRunWf = x), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout o2 = Ui.hbox(this);
            o2.addView(toggle("Disable them (manual enable)", optDisableWf, x -> optDisableWf = x));
            col.addView(o, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
            col.addView(o2, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
            String scopes = st.active == null || st.active.scopes == null ? "" : st.active.scopes;
            if (!scopes.isEmpty() && !scopes.contains("workflow")) col.addView(Ui.muted(this, "⚠ Your token has no 'workflow' scope – GitHub will refuse to create workflow files. Sign in again with that scope."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        }
        col.addView(Ui.label(this, "Recent runs at the source"), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 18, 0, 4));
        if (src.runs.isEmpty()) col.addView(Ui.muted(this, "No runs visible. Run history cannot be copied (GitHub has no API for that) – only the workflow files."));
        for (Run r : src.runs) col.addView(line(r.active() ? R.drawable.i_hourglass : "success".equals(r.conclusion) ? R.drawable.i_check_circle : R.drawable.i_x_circle, r.title == null || r.title.isEmpty() ? r.name : r.title, "#" + r.number + " • " + r.name + " • " + (r.conclusion == null ? r.status : r.conclusion) + " • " + Ui.agoIso(r.createdAt), null, null));
        return wrapScroll(col);
    }

    // ================================================================== tab: secrets & variables
    private View secretsTab() {
        LinearLayout col = scrollCol();
        col.addView(Ui.label(this, "Variables"));
        if (src.variables.isEmpty()) col.addView(Ui.muted(this, src.refVars.isEmpty() ? "No repository variables readable at the source." : "Workflows use vars: " + TextUtils.join(", ", src.refVars) + " (values not readable – add them later in Secrets)."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
        else {
            col.addView(toggle("Copy variables", optVars, x -> optVars = x), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 6, 0, 4));
            for (final SecretInfo v : src.variables) {
                boolean on = !varOff.contains(v.name);
                col.addView(line(on ? R.drawable.i_square_check : R.drawable.i_square, v.name, v.value, null, x -> { if (!varOff.remove(v.name)) varOff.add(v.name); showTab(4); }));
            }
        }
        col.addView(Ui.label(this, "Secrets"), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 18, 0, 4));
        col.addView(Ui.muted(this, "GitHub never reveals secret values, so they cannot be copied. Type the value for each one you want in the new repo – it is encrypted on this device and sent straight to GitHub. Empty = skipped."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));
        if (src.refSecrets.isEmpty()) col.addView(Ui.muted(this, "No secrets referenced by the workflows."));
        for (final String name : src.refSecrets) {
            LinearLayout c = Ui.vbox(this);
            c.addView(Ui.text(this, name + (src.secretNames.contains(name) ? "  (exists at source)" : "  (used in workflows)"), 13, true, R.attr.gpText), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            final EditText e = Ui.input(this, "value for " + name, secretVals.containsKey(name) ? secretVals.get(name) : "");
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            e.setSingleLine(true);
            e.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
                public void onTextChanged(CharSequence s, int a, int b, int d) { }
                public void afterTextChanged(android.text.Editable x) { secretVals.put(name, x.toString()); }
            });
            c.addView(e, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
            col.addView(c);
        }
        col.addView(Ui.muted(this, "Tip: a signing key for APK builds can be pushed from the Signing tool after the import."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        return wrapScroll(col);
    }

    // ================================================================== tab: commits
    private View commitsTab() {
        LinearLayout col = scrollCol();
        col.addView(Ui.label(this, "History to bring along"));
        col.addView(Ui.muted(this, "Fresh history = one clean import commit. Replay recreates the last N commits (first-parent line) with their messages, and authors and dates if you keep them. Each commit costs several API calls."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 8));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout ch = Ui.hbox(this);
        int[] opts = {0, 10, 25, 50, 100};
        for (final int o : opts) ch.addView(Ui.chip(this, o == 0 ? "Fresh" : "Last " + o, optReplay == o, v -> { optReplay = o; showTab(5); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        hs.addView(ch);
        col.addView(hs);
        if (optReplay > 0) col.addView(toggle("Keep original authors and dates", optKeepAuthors, x -> optKeepAuthors = x), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
        col.addView(Ui.label(this, "Latest commits at the source"), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 18, 0, 4));
        for (final CommitInfo c : src.commits) col.addView(line(R.drawable.i_commit, c.firstLine(), c.sha.substring(0, 7) + " • " + c.author + " • " + Ui.agoIso(c.date), null, v -> Ui.copy(this, "sha", c.sha)));
        return wrapScroll(col);
    }

    // ================================================================== tab: issues
    private View issuesTab() {
        LinearLayout col = scrollCol();
        col.addView(Ui.muted(this, src.issueTotal < 0 ? "Issue count unavailable." : src.issueTotal + " issue(s) at the source (" + src.issueOpen + " open). Pull requests are not copied."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        LinearLayout a = Ui.hbox(this);
        a.addView(toggle("Copy issues", optIssues, x -> optIssues = x), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        a.addView(toggle("Labels", optLabels, x -> optLabels = x));
        col.addView(a);
        if (optIssues) {
            col.addView(toggle("With comments (slower)", optComments, x -> optComments = x), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout ch = Ui.hbox(this);
            ch.addView(Ui.chip(this, "Open only", optIssueState == 0, v -> { optIssueState = 0; showTab(6); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
            ch.addView(Ui.chip(this, "Open + closed", optIssueState == 1, v -> { optIssueState = 1; showTab(6); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 14, 0));
            for (final int n : new int[]{25, 50, 100, 250}) ch.addView(Ui.chip(this, "Max " + n, optIssueLimit == n, v -> { optIssueLimit = n; showTab(6); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
            hs.addView(ch);
            col.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
            col.addView(Ui.muted(this, "Each issue keeps title, body, labels, milestone and closed state with a note about the original author and date. @mentions are neutralised so nobody gets notified. A pause between issues keeps GitHub's abuse limits happy."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        }
        return wrapScroll(col);
    }

    // ================================================================== import flow
    private void beginFlow() {
        if (src.tree.root.files > 0 && src.tree.selected.isEmpty()) {
            Ui.confirm(this, "Nothing selected", "No files are ticked, so everything will be imported. Continue?", "Import everything", () -> { src.tree.selectAll(); if (tab == 1) refreshRows(); settingsDialog(); });
            return;
        }
        settingsDialog();
    }

    private void settingsDialog() {
        if (src.repo.sizeKb > 150 * 1024) { Ui.toast(this, "Repository is over 150 MB – too big to import on a phone"); return; }
        List<Form.F> f = Form.list(
                Form.choice("target", "Import into", new String[]{"New repository", "Existing repository"}, dTarget),
                Form.text("name", "New repository name", dName),
                Form.text("desc", "Description", dDesc),
                Form.text("home", "Homepage URL", dHome),
                Form.text("topics", "Topics (comma separated)", dTopics),
                Form.choice("vis", "Visibility", new String[]{"Private", "Public"}, dPriv ? 0 : 1),
                Form.text("existing", "Existing repo (owner/name) – only if importing into existing", dExisting),
                Form.text("dir", "Folder inside the existing repo (optional)", dDir),
                Form.toggle("note", "Add “imported from” note to description", dNote),
                Form.choice("sync", "Keep in sync (loop)", new String[]{"Off", "15 min", "1 hour", "6 hours", "24 hours"}, dSync),
                Form.toggle("syncdel", "Sync also deletes removed files", dSyncDel));
        Form.show(this, "Import settings", "📥", "Review", f, m -> {
            dTarget = m.get("target").startsWith("New") ? 0 : 1;
            dName = m.get("name").trim().replace(' ', '-'); dDesc = m.get("desc"); dHome = m.get("home"); dTopics = m.get("topics");
            dPriv = m.get("vis").equals("Private"); dExisting = m.get("existing").trim(); dDir = m.get("dir").trim();
            dNote = Boolean.parseBoolean(m.get("note")); dSyncDel = Boolean.parseBoolean(m.get("syncdel"));
            String sy = m.get("sync"); dSync = sy.equals("Off") ? 0 : sy.equals("15 min") ? 1 : sy.equals("1 hour") ? 2 : sy.equals("6 hours") ? 3 : 4;
            validateAndReview();
        });
    }

    private Plan buildPlan() {
        Plan p = Plans.basic(src);
        p.existing = dTarget == 1;
        if (p.existing) {
            String[] t = Src.parse(dExisting);
            if (t != null) { p.tOwner = t[0]; p.tName = t[1]; }
            p.tDir = dDir;
        }
        p.name = dName; p.description = dDesc; p.homepage = dHome; p.priv = dPriv; p.note = dNote;
        p.topics.clear();
        for (String t : dTopics.split(",")) if (!t.trim().isEmpty()) p.topics.add(t.trim());
        p.releases = optReleases && !src.releases.isEmpty(); p.assets = optAssets; p.releaseIds.clear(); p.releaseIds.addAll(relSel);
        p.copyVars = optVars && !src.variables.isEmpty();
        p.varValues.clear();
        if (p.copyVars) for (SecretInfo v : src.variables) if (!varOff.contains(v.name) && v.value != null) p.varValues.put(v.name, v.value);
        p.secrets.putAll(secretVals);
        p.labels = optLabels; p.issues = optIssues; p.comments = optComments; p.issueState = optIssueState; p.issueLimit = optIssueLimit;
        p.replay = optReplay; p.keepAuthors = optKeepAuthors;
        p.runWorkflows = optRunWf; p.disableWorkflows = optDisableWf;
        int[] mins = {0, 15, 60, 360, 1440};
        p.syncMin = mins[dSync]; p.syncDelete = dSyncDel;
        return p;
    }

    private void validateAndReview() {
        final Plan p = buildPlan();
        if (p.existing) {
            if (p.tName.isEmpty()) { Ui.toast(this, "Enter the existing repository as owner/name"); settingsDialog(); return; }
            final ProgressDlg dlg = new ProgressDlg(this, "Checking target", "🔍", null);
            async(() -> api.repo(p.tOwner, p.tName), r -> { dlg.close(); if (!r.canPush) { Ui.toast(this, "No push access to " + r.fullName); settingsDialog(); } else review(p); }, e -> { dlg.close(); Ui.toast(this, msg(e)); settingsDialog(); });
        } else {
            if (p.name.isEmpty() || !p.name.matches("[A-Za-z0-9._-]+")) { Ui.toast(this, "Repository name may only use letters, numbers, . _ -"); settingsDialog(); return; }
            final ProgressDlg dlg = new ProgressDlg(this, "Checking name", "🔍", null);
            final String login = st.active.login;
            async(() -> exists(api, login, p.name), taken -> {
                dlg.close();
                if (taken) { dName = p.name + "-2"; Ui.toast(this, p.name + " already exists in your account – try another name"); settingsDialog(); }
                else review(p);
            }, e -> { dlg.close(); review(p); });
        }
    }

    private void review(final Plan p) {
        StringBuilder sb = new StringBuilder();
        Tree t = src.tree;
        sb.append("From  ").append(src.full()).append(" @ ").append(src.ref).append("\nTo      ").append(p.existing ? p.tOwner + "/" + p.tName + (p.tDir.isEmpty() ? "" : "/" + p.tDir) : st.active.login + "/" + p.name + (p.priv ? " (private)" : " (public)")).append("\n\n");
        sb.append("• ").append(p.allFiles ? "All " + t.root.files + " files" : t.root.sel + " of " + t.root.files + " files").append(" (").append(PathUtils.formatSize(p.allFiles ? t.root.size : t.root.selSize)).append(")\n");
        sb.append("• ").append(p.replay > 0 ? "History: replay last " + p.replay + " commits" + (p.keepAuthors ? " with authors" : "") : "History: one clean import commit").append('\n');
        if (!p.workflowBlobs.isEmpty()) sb.append("• ").append(p.workflowBlobs.size()).append(" workflow file(s) pushed last").append(p.runWorkflows ? "" : " [skip ci]").append(p.disableWorkflows ? ", then disabled" : "").append('\n');
        if (p.releases) sb.append("• Releases: ").append(p.releaseIds.isEmpty() ? "all " + src.releases.size() : p.releaseIds.size() + " selected").append(p.assets ? " + assets" : "").append('\n');
        if (p.copyVars && !p.varValues.isEmpty()) sb.append("• ").append(p.varValues.size()).append(" variable(s)\n");
        int sc = 0; for (String v : p.secrets.values()) if (v != null && !v.isEmpty()) sc++;
        if (sc > 0) sb.append("• ").append(sc).append(" secret(s) you typed (encrypted on device)\n");
        if (p.labels) sb.append("• Labels\n");
        if (p.issues) sb.append("• Issues (").append(p.issueState == 0 ? "open" : "open + closed").append(", max ").append(p.issueLimit).append(p.comments ? ", with comments" : "").append(")\n");
        if (p.syncMin > 0) sb.append("• Sync loop every ").append(p.syncMin >= 60 ? p.syncMin / 60 + " h" : p.syncMin + " min").append('\n');
        sb.append("\nSkipped by design: files over ").append(p.maxFileMb).append(" MB, submodule contents, pull requests, Actions run history.");
        for (String n : src.notes) sb.append("\n⚠ ").append(n);
        new GlassDialog(this).setTitle("Ready to import").setIcon("📥").setMessage(sb.toString())
                .setPositiveButton("Import", (d, w) -> launch(p)).setNegativeButton("Back", null).show();
    }

    private void launch(Plan p) {
        if (st.active == null) { Ui.toast(this, "Sign in first"); return; }
        final Job j = Engine.start(app, p, st.active.token);
        new GlassDialog(this).setTitle("Import started").setIcon("🚀")
                .setMessage("It runs in the background – you can leave the app. A notification shows progress and tells you when it is finished.")
                .setPositiveButton("View progress", (d, w) -> { startActivity(new android.content.Intent(this, ImportJobsActivity.class)); finish(); })
                .setNegativeButton("Close", (d, w) -> finish()).show();
    }
}
