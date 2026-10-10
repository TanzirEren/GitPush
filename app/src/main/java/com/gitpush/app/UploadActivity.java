package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Branch;
import com.gitpush.app.data.Models.HistoryEntry;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.data.Pusher;
import com.gitpush.app.data.Pusher.PushSession;
import com.gitpush.app.data.Stager;
import com.gitpush.app.data.Stager.Row;
import com.gitpush.app.data.Stager.StagedFile;
import com.gitpush.app.data.Stager.TNode;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UploadActivity extends BaseActivity {
    private static final int REQ_FILES = 101, REQ_FOLDER = 102, REQ_ZIP = 103;

    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, UploadActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private String owner, repoName, branch = "", targetDir = "";
    private Repo repo;
    private List<Branch> branches = new ArrayList<>();
    private int mode = 0;
    private final Set<String> expanded = new HashSet<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private TNode tree;

    private ListView listView;
    private BaseAdapter adapter;
    private LinearLayout dynBox, pickBox;
    private Button branchBtn, pushBtn;
    private EditText dirInput;
    private TextView errorTv;
    private LinearLayout pushBar;

    // push dialog
    private AlertDialog pushDialog;
    private boolean conflictShown;
    private final Runnable poll = this::pollPush;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repoName = getIntent().getStringExtra("repo");
        buildScreen(repoName, owner, true);
        addTextAction("Browse", v -> { st.browserBranch = branch.isEmpty() ? null : branch; BrowserActivity.open(this, owner, repoName); });

        LinearLayout page = Ui.vbox(this);
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setSelector(android.R.color.transparent);
        listView.addHeaderView(buildHeader(), null, false);
        adapter = new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int i) { return rows.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { return treeRow(rows.get(i)); }
        };
        listView.setAdapter(adapter);
        page.addView(listView, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        pushBar = Ui.vbox(this);
        pushBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 16));
        pushBtn = Ui.button(this, "", v -> onPushClicked());
        pushBar.addView(pushBtn, Ui.lp(Ui.MATCH, Ui.WRAP));
        page.addView(pushBar, Ui.lp(Ui.MATCH, Ui.WRAP));
        body.addView(page);

        repo = st.findRepo(owner, repoName);
        if (repo != null) onRepoReady();
        else async(() -> st.api().repo(owner, repoName), r -> { repo = r; onRepoReady(); }, e -> showErr(msg(e)));
    }

    private void onRepoReady() {
        st.openUpload(repo);
        if (branch.isEmpty()) branch = repo.defaultBranch;
        for (HistoryEntry h : st.history) if (h.repoFullName.equals(repo.fullName) && h.success) { targetDir = h.targetDir; dirInput.setText(targetDir); break; }
        branchBtn.setText("Branch: " + branch + "  ▾");
        if (!repo.canPush) showErr("Your account only has read access here. Pushing will fail.");
        final GitHubApi api = st.api();
        async(() -> api.branches(owner, repoName), list -> branches = list, e -> showErr(msg(e)));
        renderAll();
    }

    private void showErr(String m) { errorTv.setText(m); errorTv.setVisibility(m == null ? View.GONE : View.VISIBLE); }

    // ---------------------------------------------------------------- header
    private View buildHeader() {
        LinearLayout h = Ui.vbox(this);
        h.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 8));
        errorTv = Ui.text(this, "", 12, false, R.attr.gpWarn);
        errorTv.setVisibility(View.GONE);
        h.addView(errorTv, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));

        LinearLayout dest = Ui.card(this);
        dest.addView(Ui.text(this, "Destination", 13, true, R.attr.gpPrimary));
        branchBtn = Ui.outlineButton(this, "Branch: …", v -> branchMenu(v));
        dest.addView(branchBtn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        dirInput = Ui.input(this, "Target folder in repo (empty = root)", "");
        dirInput.setSingleLine(true);
        dirInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(android.text.Editable e) { targetDir = e.toString().replace("\\", "/"); }
        });
        dest.addView(dirInput, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        h.addView(dest, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));

        pickBox = Ui.card(this);
        h.addView(pickBox, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        dynBox = Ui.vbox(this);
        h.addView(dynBox, Ui.lp(Ui.MATCH, Ui.WRAP));
        renderPick();
        return h;
    }

    private void renderPick() {
        pickBox.removeAllViews();
        LinearLayout tabs = Ui.hbox(this);
        tabs.addView(Ui.chip(this, "Files & folders", mode == 0, v -> { mode = 0; renderPick(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        tabs.addView(Ui.chip(this, "ZIP file", mode == 1, v -> { mode = 1; renderPick(); }));
        pickBox.addView(tabs);
        if (mode == 0) {
            LinearLayout r = Ui.hbox(this);
            r.addView(Ui.outlineButton(this, "📄 Add files", v -> pick(REQ_FILES)), w(0, 5));
            r.addView(Ui.outlineButton(this, "📁 Add folder", v -> pick(REQ_FOLDER)), w(5, 0));
            pickBox.addView(r, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
            pickBox.addView(Ui.muted(this, "Folders keep their name and structure. Tap again to add more. Empty folders get a .gitkeep."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        } else {
            pickBox.addView(Ui.outlineButton(this, "🗜 Select ZIP & extract", v -> pick(REQ_ZIP)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
            pickBox.addView(Ui.muted(this, "The ZIP is extracted automatically. Review the files below, remove what you don't want, then push."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        }
    }

    private LinearLayout.LayoutParams w(float l, float r) { LinearLayout.LayoutParams p = Ui.lp(this, 0, Ui.WRAP, l, 0, r, 0); p.weight = 1f; return p; }

    private void pick(int req) {
        Intent i;
        if (req == REQ_FOLDER) i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        else {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            if (req == REQ_FILES) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            else i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, req);
    }

    private void persist(Uri u) {
        try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        Runnable changed = this::renderAll;
        if (req == REQ_FILES) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            for (Uri u : uris) persist(u);
            st.addFiles(uris, changed);
        } else if (req == REQ_FOLDER && data.getData() != null) { persist(data.getData()); st.addFolder(data.getData(), changed); }
        else if (req == REQ_ZIP && data.getData() != null) st.addZip(data.getData(), changed);
    }

    // ---------------------------------------------------------------- branch
    private void branchMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        for (int i = 0; i < branches.size(); i++) pm.getMenu().add(0, i, i, branches.get(i).name);
        pm.getMenu().add(1, 1000, 1000, "＋ New branch…");
        pm.setOnMenuItemClickListener(it -> {
            if (it.getItemId() == 1000) newBranch();
            else { branch = branches.get(it.getItemId()).name; branchBtn.setText("Branch: " + branch + "  ▾"); }
            return true;
        });
        pm.show();
    }

    private void newBranch() {
        Ui.prompt(this, "New branch from " + branch, "Branch name", "", name -> {
            final String n = name.trim().replace(' ', '-');
            if (n.isEmpty()) return;
            String from = null;
            for (Branch b : branches) if (b.name.equals(branch)) from = b.sha;
            if (from == null) { Ui.toast(this, "Source branch not found"); return; }
            final String sha = from;
            final GitHubApi api = st.api();
            async(() -> { api.createBranch(owner, repoName, n, sha); return api.branches(owner, repoName); },
                    list -> { branches = list; branch = n; branchBtn.setText("Branch: " + branch + "  ▾"); Ui.toast(this, "Branch " + n + " created"); },
                    e -> Ui.toast(this, msg(e)));
        });
    }

    // ---------------------------------------------------------------- staged list
    private void renderAll() {
        tree = Stager.buildTree(st.staged);
        rows.clear();
        rows.addAll(Stager.flatten(tree, expanded));
        adapter.notifyDataSetChanged();
        dynBox.removeAllViews();
        long total = 0, big = 0;
        int blocked = 0;
        for (StagedFile f : st.staged) { total += f.size; if (f.size > Pusher.WARN_LIMIT) big++; if (f.size > Pusher.HARD_LIMIT) blocked++; }
        if (st.stageBusy) dynBox.addView(Ui.muted(this, "⏳ Reading files…"), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        if (big > 0) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, "⚠ " + big + " large file(s) over " + PathUtils.formatSize(Pusher.WARN_LIMIT) + ": uploading may be slow. Files over "
                    + PathUtils.formatSize(Pusher.HARD_LIMIT) + " are blocked (use Git LFS).", 12, false, R.attr.gpWarn));
            dynBox.addView(c, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        }
        if (st.staged.isEmpty()) {
            if (!st.stageBusy) dynBox.addView(Ui.empty(this, "📄", "Nothing selected", "Add files, a folder or a ZIP to preview exactly what will be pushed."));
        } else {
            LinearLayout head = Ui.hbox(this);
            LinearLayout t = Ui.vbox(this);
            t.addView(Ui.text(this, "Ready to push", 13, true, R.attr.gpPrimary));
            t.addView(Ui.muted(this, st.staged.size() + " files • " + PathUtils.formatSize(total)));
            head.addView(t, Ui.weight(1f));
            head.addView(Ui.textButton(this, "Select all", R.attr.gpPrimary, v -> {
                Set<String> all = new LinkedHashSet<>();
                for (TNode n : tree.children.values()) all.add(n.path);
                if (selected.containsAll(all)) selected.clear(); else { selected.clear(); selected.addAll(all); }
                adapter.notifyDataSetChanged();
            }));
            head.addView(Ui.textButton(this, "Remove", R.attr.gpError, v -> {
                if (selected.isEmpty()) { Ui.toast(this, "Select items first"); return; }
                Ui.confirm(this, "Remove " + selected.size() + " item(s)?", "Selected files/folders are removed from the upload list only. Nothing is deleted from your phone or GitHub.", "Remove",
                        () -> { st.removeStaged(new HashSet<>(selected)); selected.clear(); renderAll(); });
            }));
            head.addView(Ui.textButton(this, "Clear all", R.attr.gpError, v -> Ui.confirm(this, "Remove everything?",
                    "All " + st.staged.size() + " selected files will be removed from the upload list (your phone files are not touched).", "Remove all",
                    () -> { st.staged.clear(); selected.clear(); renderAll(); })));
            dynBox.addView(head);
        }
        pushBar.setVisibility(st.staged.isEmpty() ? View.GONE : View.VISIBLE);
        pushBtn.setText("🚀  Push " + st.staged.size() + " file(s) • " + PathUtils.formatSize(total));
        pushBtn.setEnabled(blocked == 0);
    }

    private View treeRow(Row r) {
        final TNode n = r.node;
        LinearLayout row = Ui.hbox(this);
        row.setPadding(Ui.dp(this, 16 + r.depth * 16), Ui.dp(this, st.compact ? 2 : 6), Ui.dp(this, 16), Ui.dp(this, st.compact ? 2 : 6));
        boolean sel = selected.contains(n.path);
        for (String s : selected) if (n.path.startsWith(s + "/")) sel = true;
        TextView cb = Ui.text(this, sel ? "☑" : "☐", 20, false, sel ? R.attr.gpPrimary : R.attr.gpMuted);
        cb.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        cb.setOnClickListener(v -> { if (!selected.remove(n.path)) selected.add(n.path); adapter.notifyDataSetChanged(); });
        row.addView(cb);
        row.addView(Ui.text(this, n.isDir ? (expanded.contains(n.path) ? "▾ 📁" : "▸ 📁") : "📄", 15, false, R.attr.gpText), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        LinearLayout t = Ui.vbox(this);
        TextView name = Ui.body(this, n.name);
        name.setSingleLine(true);
        t.addView(name);
        t.addView(Ui.text(this, n.isDir ? n.count + " files • " + PathUtils.formatSize(n.size) : PathUtils.formatSize(n.size), 11, false,
                !n.isDir && n.size > Pusher.WARN_LIMIT ? R.attr.gpWarn : R.attr.gpMuted));
        row.addView(t, Ui.weight(1f));
        row.setOnClickListener(v -> {
            if (n.isDir) { if (!expanded.remove(n.path)) expanded.add(n.path); renderAll(); }
            else { if (!selected.remove(n.path)) selected.add(n.path); adapter.notifyDataSetChanged(); }
        });
        return row;
    }

    // ---------------------------------------------------------------- push flow
    private void onPushClicked() {
        if (repo == null || branch.isEmpty()) { Ui.toast(this, "Still loading repository…"); return; }
        final List<String> names = new ArrayList<>();
        for (StagedFile f : st.staged) names.add(f.path);
        final List<String> sugg = suggest(names);
        String initial = "";
        for (HistoryEntry h : st.history) if (h.repoFullName.equals(repo.fullName) && h.success) { initial = h.message; break; }
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        final EditText msg = Ui.input(this, "Commit message", initial.isEmpty() ? sugg.get(0) : initial);
        msg.setSingleLine(true);
        final EditText desc = Ui.input(this, "Description (optional)", "");
        desc.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        desc.setMinLines(3); desc.setGravity(Gravity.TOP);
        box.addView(msg, Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(desc, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout chips = Ui.hbox(this);
        for (final String s : sugg) chips.addView(Ui.chip(this, s, false, v -> msg.setText(s)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        hs.addView(chips);
        box.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        new AlertDialog.Builder(this).setTitle("Commit & push").setView(box)
                .setPositiveButton("Push", (d, w) -> {
                    String m = msg.getText().toString().trim();
                    if (m.isEmpty()) { Ui.toast(this, "Enter a commit message"); return; }
                    conflictShown = false;
                    st.startPush(repo, branch, PathUtils.trimSlashes(targetDir.trim()), m, desc.getText().toString());
                    showPushDialog();
                }).setNegativeButton("Cancel", null).show();
    }

    private List<String> suggest(List<String> names) {
        List<String> out = new ArrayList<>();
        out.add(names.size() == 1 ? "Add " + PathUtils.baseName(names.get(0)) : "Add " + names.size() + " files");
        Set<String> tops = new LinkedHashSet<>();
        for (String n : names) if (n.contains("/")) tops.add(n.substring(0, n.indexOf('/')));
        if (tops.size() == 1) out.add("Add " + tops.iterator().next() + " folder");
        out.add("Update project files");
        out.add("Upload via GitPush");
        return out;
    }

    private TextView pdStatus, pdDetail;
    private ProgressBar pdBar;
    private LinearLayout pdButtons;

    private void showPushDialog() {
        if (pushDialog != null && pushDialog.isShowing()) return;
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22), Ui.dp(this, 4));
        box.addView(Ui.muted(this, st.session.repo.fullName + " • " + st.session.branch));
        pdStatus = Ui.text(this, "", 15, true, R.attr.gpText);
        box.addView(pdStatus, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 6));
        pdBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pdBar.setMax(1000);
        pdBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, R.attr.gpPrimary)));
        box.addView(pdBar, Ui.lp(Ui.MATCH, Ui.WRAP));
        pdDetail = Ui.text(this, "", 12, false, R.attr.gpMuted);
        pdDetail.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView sv = new ScrollView(this);
        sv.addView(pdDetail);
        box.addView(sv, Ui.lp(this, Ui.MATCH, Ui.dp(this, 150), 0, 8, 0, 0));
        pdButtons = Ui.hbox(this);
        pdButtons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        box.addView(pdButtons, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        pushDialog = new AlertDialog.Builder(this).setView(box).setCancelable(false).create();
        pushDialog.show();
        pollPush();
    }

    private void pollPush() {
        app.main.removeCallbacks(poll);
        final PushSession s = st.session;
        if (s == null || pushDialog == null || !alive()) { if (pushDialog != null) pushDialog.dismiss(); return; }
        boolean ok = s.finished && !s.cancelled && s.error == null;
        pdStatus.setText(ok ? (s.noChanges ? "Nothing to push" : "✔ Pushed!") : s.cancelled ? "Cancelled" : s.error != null ? "Upload problem" : s.phase + "  " + s.doneCount() + "/" + s.total());
        pdBar.setProgress((int) (s.progress() * 1000));
        StringBuilder d = new StringBuilder();
        if (ok && !s.noChanges) {
            d.append(s.count(Pusher.DONE)).append(" file(s) committed.\n");
            if (s.commitSha != null) d.append("Commit ").append(s.commitSha, 0, Math.min(7, s.commitSha.length())).append('\n');
            int sk = s.count(Pusher.SKIPPED);
            if (sk > 0) d.append(sk).append(" skipped (unchanged / existing).\n");
        } else if (ok) d.append("Every file is already identical in the repository.\n");
        if (s.error != null) d.append(s.error).append("\n\n");
        int[] state = s.snapState(); String[] note = s.snapNote();
        int shown = 0;
        for (int i = 0; i < state.length && shown < 40; i++) {
            if (state[i] == Pusher.FAILED || (state[i] == Pusher.UPLOADING && s.running)) {
                d.append(state[i] == Pusher.FAILED ? "✘ " : "↑ ").append(s.paths[i]);
                if (note[i] != null && state[i] == Pusher.FAILED) d.append("\n   ").append(note[i]);
                d.append('\n'); shown++;
            }
        }
        pdDetail.setText(d.toString());

        pdButtons.removeAllViews();
        if (s.running) {
            pdButtons.addView(Ui.textButton(this, "Cancel", R.attr.gpError, v -> st.cancelPush()));
        } else {
            if (ok && !s.noChanges && s.commitUrl != null) {
                pdButtons.addView(Ui.textButton(this, "Copy SHA", R.attr.gpPrimary, v -> { Ui.copy(this, "SHA", s.commitSha); Ui.toast(this, "SHA copied"); }));
                pdButtons.addView(Ui.textButton(this, "Open commit", R.attr.gpPrimary, v -> Ui.openUrl(this, s.commitUrl)));
            }
            if (s.error != null) pdButtons.addView(Ui.textButton(this, s.failedCount() > 0 ? "Retry failed" : "Retry", R.attr.gpPrimary, v -> { conflictShown = false; st.retryPush(); }));
            pdButtons.addView(Ui.textButton(this, ok ? "Done" : "Close", R.attr.gpText, v -> { st.closeSession(); pushDialog.dismiss(); pushDialog = null; selected.clear(); renderAll(); }));
        }
        List<String> conflict = s.conflictChanged;
        if (conflict != null && !conflictShown) { conflictShown = true; showConflict(s, conflict); }
        if (pushDialog != null) app.main.postDelayed(poll, 300);
    }

    private void showConflict(final PushSession s, List<String> changed) {
        StringBuilder sb = new StringBuilder("These files exist in the repository with different content:\n\n");
        for (int i = 0; i < changed.size() && i < 25; i++) sb.append("• ").append(changed.get(i)).append('\n');
        if (changed.size() > 25) sb.append("… and ").append(changed.size() - 25).append(" more\n");
        if (s.conflictIdentical > 0) sb.append('\n').append(s.conflictIdentical).append(" identical file(s) are skipped automatically.");
        new AlertDialog.Builder(this).setTitle(changed.size() + " file(s) already exist").setMessage(sb.toString()).setCancelable(false)
                .setPositiveButton("Replace", (d, w) -> s.resolveConflict(Pusher.REPLACE))
                .setNegativeButton("Skip", (d, w) -> s.resolveConflict(Pusher.SKIP))
                .setNeutralButton("Cancel", (d, w) -> s.resolveConflict(Pusher.CANCEL)).show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (repo != null || st.staged.size() >= 0) renderAll();
        if (st.session != null && repo != null && st.session.repo.fullName.equals(repo.fullName) && (pushDialog == null || !pushDialog.isShowing())) showPushDialog();
    }

    @Override protected void onDestroy() {
        app.main.removeCallbacks(poll);
        if (pushDialog != null && pushDialog.isShowing()) pushDialog.dismiss();
        pushDialog = null;
        super.onDestroy();
    }
}
