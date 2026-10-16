package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.gitpush.app.ui.GlassDialog;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.GitOps;
import com.gitpush.app.data.Models.Branch;
import com.gitpush.app.data.Models.ContentItem;
import com.gitpush.app.data.Models.RefResult;
import com.gitpush.app.data.Models.TreeInfo;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BrowserActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, BrowserActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private String owner, repo, branch = "", path = "";
    private List<Branch> branches = new ArrayList<>();
    private final List<ContentItem> items = new ArrayList<>();
    private List<TreeInfo> allTree;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<Object> shown = new ArrayList<>();   // ContentItem or TreeInfo
    private boolean loading = true;
    private String error;
    private LinearLayout crumbs;
    private EditText search;
    private TextView status;
    private BaseAdapter adapter;
    private GitHubApi api;
    private TextView deleteAction;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        buildScreen(repo, owner, true);
        addTextAction("History", v -> { st.browserBranch = branch; CommitsActivity.open(this, owner, repo); });
        addTextAction("Branch", v -> branchMenu(v));
        addTextAction("＋ File", v -> newFile());
        deleteAction = addTextAction("Delete", v -> confirmDelete());
        deleteAction.setVisibility(View.GONE);

        LinearLayout page = Ui.vbox(this);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        crumbs = Ui.hbox(this);
        crumbs.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        hs.addView(crumbs);
        page.addView(hs, Ui.lp(Ui.MATCH, Ui.WRAP));
        search = Ui.input(this, "🔍  Search files in repository", "");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { onSearch(); }
        });
        page.addView(search, Ui.lp(this, Ui.MATCH, Ui.WRAP, 16, 4, 16, 6));
        status = Ui.muted(this, "");
        status.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), 0);
        page.addView(status, Ui.lp(Ui.MATCH, Ui.WRAP));
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 24));
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { return row(shown.get(i)); }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        body.addView(page);

        branch = st.browserBranch != null ? st.browserBranch : "";
        async(() -> {
            if (branch.isEmpty()) {
                com.gitpush.app.data.Models.Repo r = st.findRepo(owner, repo);
                branch = r != null ? r.defaultBranch : api.repo(owner, repo).defaultBranch;
            }
            return api.branches(owner, repo);
        }, list -> { branches = list; load(); }, e -> { loading = false; error = msg(e); render(); });
        renderCrumbs();
    }

    @Override protected void onResume() { super.onResume(); if (!branch.isEmpty() && !loading) load(); }

    @Override public void onBackPressed() {
        if (!selected.isEmpty()) { selected.clear(); updateSelection(); adapter.notifyDataSetChanged(); }
        else if (!path.isEmpty()) { path = PathUtils.parent(path); load(); }
        else super.onBackPressed();
    }

    private void load() {
        loading = true; error = null; renderCrumbs(); render();
        final String p = path, br = branch;
        async(() -> api.contents(owner, repo, p, br), list -> { loading = false; items.clear(); items.addAll(list); selected.clear(); updateSelection(); render(); },
                e -> { loading = false; error = msg(e); items.clear(); render(); });
    }

    private void onSearch() {
        final String q = search.getText().toString().trim();
        if (q.length() >= 2 && allTree == null && !branch.isEmpty()) {
            async(() -> {
                RefResult r = api.ref(owner, repo, branch);
                if (r.kind != RefResult.FOUND) return new ArrayList<TreeInfo>();
                return api.treeRecursive(owner, repo, api.commitTree(owner, repo, r.sha));
            }, t -> { allTree = t; render(); }, e -> Ui.toast(this, msg(e)));
        }
        render();
    }

    private void render() {
        shown.clear();
        String q = search.getText().toString().trim().toLowerCase();
        if (q.length() >= 2) {
            if (allTree != null) for (TreeInfo t : allTree) { if (t.path.toLowerCase().contains(q)) { shown.add(t); if (shown.size() >= 200) break; } }
            status.setText(shown.isEmpty() ? (allTree == null ? "Searching…" : "No matches for \"" + q + "\".") : "");
        } else if (loading) status.setText("Loading…");
        else if (error != null) status.setText("Couldn't open\n" + error);
        else if (items.isEmpty()) status.setText("This folder is empty.");
        else { shown.addAll(items); status.setText(""); }
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void renderCrumbs() {
        crumbs.removeAllViews();
        crumbs.addView(crumb("root", ""));
        String acc = "";
        if (!path.isEmpty()) for (String seg : path.split("/")) {
            acc = acc.isEmpty() ? seg : acc + "/" + seg;
            crumbs.addView(Ui.muted(this, "›"));
            crumbs.addView(crumb(seg, acc));
        }
        setSubtitle(owner + " • " + branch);
    }

    private TextView crumb(String label, final String target) {
        TextView t = Ui.text(this, label, 13, true, R.attr.gpPrimary);
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        t.setOnClickListener(v -> { path = target; load(); });
        return t;
    }

    private View row(Object o) {
        final boolean isDir; final String name, p, sha, sub; final long size;
        if (o instanceof ContentItem) { ContentItem c = (ContentItem) o; isDir = c.isDir; name = c.name; p = c.path; sha = c.sha; size = c.size; sub = isDir ? "" : PathUtils.formatSize(size); }
        else { TreeInfo t = (TreeInfo) o; isDir = t.type.equals("tree"); name = PathUtils.baseName(t.path); p = t.path; sha = t.sha; size = t.size; sub = p; }
        LinearLayout wrap = Ui.vbox(this);
        wrap.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
        LinearLayout c = Ui.card(this);
        c.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        LinearLayout r = Ui.hbox(this);
        boolean sel = selected.contains(p);
        r.addView(Ui.text(this, sel ? "☑" : isDir ? "📁" : "📄", 18, false, sel ? R.attr.gpPrimary : R.attr.gpText), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(this);
        TextView n = Ui.body(this, name);
        n.setSingleLine(true);
        t.addView(n);
        if (!sub.isEmpty()) { TextView m = Ui.text(this, sub, 11, false, R.attr.gpMuted); m.setSingleLine(true); t.addView(m); }
        r.addView(t, Ui.weight(1f));
        c.addView(r);
        c.setOnClickListener(v -> {
            if (!selected.isEmpty() && o instanceof ContentItem) toggle(p);
            else if (isDir) { search.setText(""); path = p; load(); }
            else openFile(p, sha);
        });
        c.setOnLongClickListener(v -> { if (o instanceof ContentItem) { Ui.haptic(v); toggle(p); } return true; });
        wrap.addView(c);
        return wrap;
    }

    private void toggle(String p) { if (!selected.remove(p)) selected.add(p); updateSelection(); adapter.notifyDataSetChanged(); }

    private void updateSelection() {
        deleteAction.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
        deleteAction.setText("Delete (" + selected.size() + ")");
    }

    private void openFile(String p, String sha) {
        st.viewerPath = p; st.viewerSha = sha; st.viewerBranch = branch;
        FileViewerActivity.open(this, owner, repo);
    }

    private void branchMenu(View anchor) {
        Ui.pickList(this, "Switch branch", branchNames(), i -> { branch = branches.get(i).name; st.browserBranch = branch; path = ""; allTree = null; load(); });
    }

    private String[] branchNames() {
        String[] n = new String[branches.size()];
        for (int i = 0; i < n.length; i++) n[i] = (branches.get(i).name.equals(branch) ? "✓  " : "      ") + branches.get(i).name;
        return n;
    }

    private void confirmDelete() {
        final List<String> paths = new ArrayList<>(selected);
        StringBuilder sb = new StringBuilder("Files and whole folders will be deleted from '" + branch + "' in ONE commit. You can revert it from the commit history.\n\n");
        for (String p : paths) sb.append("• ").append(p).append('\n');
        Ui.confirmDanger(this, "Delete " + paths.size() + " item(s)?", sb.toString(), "Delete", () -> {
            final String msg = paths.size() == 1 ? "Delete " + paths.get(0) : "Delete " + paths.size() + " items";
            Ui.toast(this, "Deleting…");
            async(() -> GitOps.deletePaths(api, owner, repo, branch, paths, msg), sha -> { Ui.toast(this, "Deleted in one commit"); allTree = null; load(); }, e -> Ui.toast(this, msg(e)));
        });
    }

    private void newFile() {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(Ui.muted(this, "In: /" + path));
        final EditText name = Ui.input(this, "File name (e.g. notes.txt)", "");
        final EditText content = Ui.input(this, "Content", "");
        content.setMinLines(4); content.setGravity(android.view.Gravity.TOP);
        content.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(name, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        box.addView(content, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        new GlassDialog(this).setTitle("New file").setView(box).setPositiveButton("Create", (d, w) -> {
            final String n = name.getText().toString().trim();
            final String full = PathUtils.join(path, n);
            if (n.isEmpty() || !PathUtils.isValidRepoPath(full)) { Ui.toast(this, "Invalid file name"); return; }
            final String text = content.getText().toString();
            async(() -> api.putText(owner, repo, full, "Create " + n, branch, null, text), r -> { Ui.toast(this, "Created " + n); allTree = null; load(); }, e -> Ui.toast(this, msg(e)));
        }).setNegativeButton("Cancel", null).show();
    }
}
