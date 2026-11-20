package com.gitpush.app;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Branch;
import com.gitpush.app.data.Models.HistoryEntry;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.data.Pusher;
import com.gitpush.app.data.Pusher.PushSession;
import com.gitpush.app.data.Stager.StagedFile;
import com.gitpush.app.data.ZipTools;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;
import com.gitpush.app.util.ZipRules;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Import / clone any accessible repository into your account (fork, or copy files into a fresh repo). */
public class ImportRepoActivity extends BaseActivity {
    private static final Pattern URL = Pattern.compile("^(?:https?://(?:www\\.)?github\\.com/)?([\\w.-]+)/([\\w.-]+?)(?:\\.git)?(?:[/#?].*)?$");

    private EditText input, newName, newDesc;
    private LinearLayout previewBox, optionsBox;
    private Repo source;
    private List<Branch> branches = new ArrayList<>();
    private String branch = "";
    private boolean fork = false, priv = true;
    private GitHubApi api;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        api = st.api();
        buildScreen("Import repository", "Clone to your account", true);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);

        LinearLayout c = Ui.card(this);
        c.addView(Ui.label(this, "Source repository"));
        input = Ui.input(this, "https://github.com/owner/repo  or  owner/repo", "");
        input.setSingleLine(true);
        c.addView(input, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        c.addView(Ui.button(this, "🔍 Look up", v -> lookup()), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        c.addView(Ui.muted(this, "Works with any public repository and with private ones your token can access."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(c, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        previewBox = Ui.vbox(this);
        optionsBox = Ui.vbox(this);
        col.addView(previewBox);
        col.addView(optionsBox);
    }

    private void lookup() {
        Matcher m = URL.matcher(input.getText().toString().trim());
        if (!m.matches()) { Ui.toast(this, "Enter owner/repo or a GitHub URL"); return; }
        final String o = m.group(1), n = m.group(2);
        final ProgressDlg dlg = new ProgressDlg(this, "Looking up", "🔍", null);
        async(() -> new Object[]{api.repo(o, n), api.branches(o, n)}, r -> {
            dlg.close();
            source = (Repo) r[0];
            @SuppressWarnings("unchecked") List<Branch> bl = (List<Branch>) r[1];
            branches = bl;
            branch = source.defaultBranch;
            showPreview();
        }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    private void showPreview() {
        previewBox.removeAllViews(); optionsBox.removeAllViews();
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        h.addView(Ui.iconTileRes(this, source.isPrivate ? R.drawable.i_lock : R.drawable.i_globe, 44), Ui.lp(this, Ui.dp(this, 44), Ui.dp(this, 44), 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(this);
        t.addView(Ui.title(this, source.fullName));
        t.addView(Ui.label(this, (source.language == null ? "unknown" : source.language) + " • " + PathUtils.formatSize(source.sizeKb * 1024L) + " • ★ " + source.stars));
        h.addView(t, Ui.weight(1f));
        c.addView(h);
        if (source.description != null) c.addView(Ui.muted(this, source.description), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        previewBox.addView(c, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout o = Ui.card(this);
        o.addView(Ui.label(this, "How to import"));
        LinearLayout modes = Ui.hbox(this);
        modes.addView(Ui.chip(this, "Copy files (fresh history)", !fork, v -> { fork = false; showPreview(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        modes.addView(Ui.chip(this, "Fork", fork, v -> { fork = true; showPreview(); }));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(modes);
        o.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        if (!fork) {
            newName = Ui.input(this, "New repository name", source.name);
            newName.setSingleLine(true);
            newDesc = Ui.input(this, "Description", source.description == null ? "" : source.description);
            o.addView(newName, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
            o.addView(newDesc, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            LinearLayout vis = Ui.hbox(this);
            vis.addView(Ui.chip(this, "🔒 Private", priv, v -> { priv = true; showPreview(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
            vis.addView(Ui.chip(this, "🌐 Public", !priv, v -> { priv = false; showPreview(); }));
            o.addView(vis, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
            o.addView(Ui.outlineButton(this, "Branch to copy: " + branch, v -> pickBranch()), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
            o.addView(Ui.muted(this, "Downloads the branch as a ZIP and pushes it as one commit into a new repository."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        } else o.addView(Ui.muted(this, "Creates a GitHub fork in your account (keeps history and links to the original)."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        o.addView(Ui.button(this, "📥 Import now", v -> start()), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
        optionsBox.addView(o);
        Ui.stagger(optionsBox);
    }

    private void pickBranch() {
        String[] n = new String[branches.size()];
        for (int i = 0; i < n.length; i++) n[i] = branches.get(i).name;
        Ui.pickList(this, "Branch to copy", n, i -> { branch = branches.get(i).name; showPreview(); });
    }

    private void start() {
        if (source.sizeKb > 150 * 1024) { Ui.toast(this, "This repository is larger than 150 MB – too big to import on a phone"); return; }
        if (fork) {
            final ProgressDlg dlg = new ProgressDlg(this, "Forking", "🌐", null);
            async(() -> api.fork(source.owner, source.name), r -> { dlg.close(); st.repos.add(0, r); done(r); }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
            return;
        }
        final String name = newName.getText().toString().trim().replace(' ', '-'), desc = newDesc.getText().toString().trim();
        if (name.isEmpty()) { Ui.toast(this, "Enter a repository name"); return; }
        final ProgressDlg dlg = new ProgressDlg(this, "Importing " + source.name, "📥", () -> { if (task != null) task.cancel(true); });
        KeepAliveService.begin(app, "Importing " + source.fullName);
        final Repo src = source;
        final String br = branch;
        final boolean isPriv = priv;
        task = app.io.submit(() -> {
            String err = null;
            Repo created = null;
            try {
                post(dlg, "Creating repository…", -1f);
                created = api.createRepo(name, desc, isPriv);
                File zip = new File(getCacheDir(), "import_" + System.currentTimeMillis() + ".zip");
                try (FileOutputStream fo = new FileOutputStream(zip)) {
                    api.stream("/repos/" + src.owner + "/" + src.name + "/zipball/" + br, fo, (d, t) -> post(dlg, "Downloading " + PathUtils.formatSize(d) + (t > 0 ? " / " + PathUtils.formatSize(t) : ""), t > 0 ? (float) d / t : -1f));
                }
                post(dlg, "Reading archive…", -1f);
                List<ZipTools.Entry> entries = ZipTools.list(zip);
                List<String> paths = new ArrayList<>();
                for (ZipTools.Entry e : entries) paths.add(e.path);
                List<String> chain = ZipRules.topChain(paths);
                ZipRules rules = new ZipRules();
                rules.skipJunk = false;
                if (!chain.isEmpty()) rules.rootFolder = chain.get(0);
                List<String[]> map = new ArrayList<>();
                for (ZipTools.Entry e : entries) { String np = rules.apply(e.path); if (np != null) map.add(new String[]{e.raw, np}); }
                if (map.isEmpty()) throw new Exception("The repository is empty");
                post(dlg, "Extracting " + map.size() + " files…", -1f);
                final List<StagedFile> files = ZipTools.extract(this, zip, map, null);
                zip.delete();
                final PushSession s = new PushSession(created, created.defaultBranch, "", files, "Import from " + src.fullName + " (" + br + ")", "Imported with GitPush");
                final Thread watcher = new Thread(() -> {
                    while (s.running || !s.finished && s.error == null && !s.cancelled) {
                        post(dlg, s.phase + "  " + s.doneCount() + "/" + s.total(), s.progress());
                        try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                        if (s.finished || s.error != null || s.cancelled) return;
                    }
                });
                watcher.start();
                Pusher.run(this, new GitHubApi(st.active.token), s);
                watcher.interrupt();
                if (s.error != null) throw new Exception(s.error);
                if (s.cancelled) throw new Exception("Cancelled");
                HistoryEntry h = new HistoryEntry();
                h.repoFullName = created.fullName; h.branch = created.defaultBranch; h.message = s.message; h.fileCount = files.size();
                h.bytes = s.totalBytes(); h.sha = s.commitSha == null ? "" : s.commitSha; h.url = s.commitUrl == null ? "" : s.commitUrl; h.time = System.currentTimeMillis(); h.success = true;
                final Repo fc = created;
                app.main.post(() -> { st.addHistory(h); st.repos.add(0, fc); });
            } catch (Exception e) { err = e.getMessage() != null ? e.getMessage() : e.toString(); }
            final String fe = err; final Repo fr = created;
            app.main.post(() -> {
                KeepAliveService.end(app);
                dlg.close();
                if (fe == null && fr != null) done(fr);
                else if (alive()) Ui.toast(this, "Import failed: " + fe);
            });
        });
    }

    private java.util.concurrent.Future<?> task;

    private void post(final ProgressDlg d, final String text, final float frac) { app.main.post(() -> d.set(text, frac)); }

    private void done(final Repo r) {
        if (!alive()) return;
        new GlassDialog(this).setTitle("Imported").setIcon("✔").setMessage(r.fullName + " is ready in your account.")
                .setPositiveButton("Open in IDE", (d, w) -> { IdeActivity.open(this, r.owner, r.name, null); finish(); })
                .setNegativeButton("Close", (d, w) -> finish()).show();
    }
}
