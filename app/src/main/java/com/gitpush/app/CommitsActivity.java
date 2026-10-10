package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Branch;
import com.gitpush.app.data.Models.CommitInfo;
import com.gitpush.app.data.Models.CompareResult;
import com.gitpush.app.data.Models.CommitFile;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;

import java.util.ArrayList;
import java.util.List;

public class CommitsActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, CommitsActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private String owner, repo, branch = "";
    private List<Branch> branches = new ArrayList<>();
    private final List<CommitInfo> commits = new ArrayList<>();
    private int page = 1;
    private boolean more = true, loading = true;
    private String error;
    private TextView status;
    private BaseAdapter adapter;
    private Button loadMore;
    private GitHubApi api;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        buildScreen("Commit history", repo, true);
        addTextAction("Merge", v -> compareDialog());
        addTextAction("Branch", v -> branchMenu(v));
        LinearLayout page = Ui.vbox(this);
        status = Ui.muted(this, "Loading…");
        status.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), 0);
        page.addView(status);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        loadMore = Ui.outlineButton(this, "Load more", v -> loadPage());
        lv.addFooterView(loadMore);
        adapter = new BaseAdapter() {
            public int getCount() { return commits.size(); }
            public Object getItem(int i) { return commits.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { return row(commits.get(i)); }
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
        }, list -> { branches = list; reload(); }, e -> { loading = false; error = msg(e); render(); });
    }

    private void reload() { commits.clear(); page = 0; more = true; setSubtitle(repo + " • " + branch); loadPage(); }

    private void loadPage() {
        loading = true; render();
        final int next = page + 1;
        final String br = branch;
        async(() -> api.commits(owner, repo, br, next, null), list -> {
            loading = false; error = null; page = next; commits.addAll(list); more = list.size() >= 30; render();
        }, e -> { loading = false; error = msg(e); render(); });
    }

    private void render() {
        adapter.notifyDataSetChanged();
        loadMore.setVisibility(more && !commits.isEmpty() && !loading ? View.VISIBLE : View.GONE);
        if (loading && commits.isEmpty()) status.setText("Loading…");
        else if (error != null && commits.isEmpty()) status.setText("Couldn't load\n" + error);
        else if (commits.isEmpty()) status.setText("No commits on this branch yet.");
        else status.setText("");
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    private View row(final CommitInfo c) {
        LinearLayout wrap = Ui.vbox(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        LinearLayout card = Ui.card(this);
        LinearLayout r = Ui.hbox(this);
        LinearLayout t = Ui.vbox(this);
        TextView m = Ui.title(this, c.firstLine());
        m.setMaxLines(2);
        t.addView(m);
        t.addView(Ui.muted(this, c.author + " • " + Ui.agoIso(c.date)));
        r.addView(t, Ui.weight(1f));
        r.addView(Ui.pill(this, c.sha.substring(0, 7), R.attr.gpPrimary));
        r.addView(Ui.textButton(this, "⧉", R.attr.gpMuted, v -> { Ui.copy(this, "SHA", c.sha); Ui.toast(this, "SHA copied"); }));
        card.addView(r);
        card.setOnClickListener(v -> { st.viewerBranch = branch; CommitDetailActivity.open(this, owner, repo, c.sha); });
        wrap.addView(card);
        return wrap;
    }

    private void branchMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        for (int i = 0; i < branches.size(); i++) pm.getMenu().add(0, i, i, branches.get(i).name);
        pm.setOnMenuItemClickListener(it -> { branch = branches.get(it.getItemId()).name; reload(); return true; });
        pm.show();
    }

    // ---------------------------------------------------------------- compare & merge
    private String base = "", head = "";
    private CompareResult result;

    private void compareDialog() {
        if (branches.size() < 2) { Ui.toast(this, "This repository has only one branch."); return; }
        base = branch;
        head = branches.get(0).name.equals(branch) ? branches.get(1).name : branches.get(0).name;
        result = null;
        final LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        final TextView info = Ui.muted(this, "");
        final Button baseBtn = Ui.outlineButton(this, "", null), headBtn = Ui.outlineButton(this, "", null);
        final Runnable refresh = () -> {
            baseBtn.setText("Base (into): " + base); headBtn.setText("Head (from): " + head);
            if (result == null) info.setText("Tap Compare to see the difference.");
            else {
                StringBuilder sb = new StringBuilder(head + " is " + result.ahead + " ahead / " + result.behind + " behind " + base + " • " + result.files.size() + " file(s) differ\n");
                for (int i = 0; i < result.files.size() && i < 8; i++) { CommitFile f = result.files.get(i); sb.append(f.status).append("  ").append(f.filename).append('\n'); }
                info.setText(sb.toString());
            }
        };
        baseBtn.setOnClickListener(v -> pickBranch(v, n -> { base = n; result = null; refresh.run(); }));
        headBtn.setOnClickListener(v -> pickBranch(v, n -> { head = n; result = null; refresh.run(); }));
        refresh.run();
        box.addView(baseBtn); box.addView(headBtn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 8));
        ScrollView sv = new ScrollView(this);
        sv.addView(info);
        box.addView(sv, Ui.lp(Ui.MATCH, Ui.dp(this, 140)));
        final AlertDialog d = new AlertDialog.Builder(this).setTitle("Compare & merge").setView(box)
                .setPositiveButton("Compare", null).setNeutralButton("Merge", null).setNegativeButton("Close", null).create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (base.equals(head)) { Ui.toast(this, "Choose two different branches"); return; }
            final String b1 = base, h1 = head;
            async(() -> api.compare(owner, repo, b1, h1), r -> { result = r; refresh.run(); }, e -> Ui.toast(this, msg(e)));
        });
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            if (result == null || result.ahead <= 0) { Ui.toast(this, "Compare first – nothing to merge yet"); return; }
            final String b1 = base, h1 = head;
            Ui.confirm(this, "Merge " + h1 + " into " + b1 + "?", "This creates a merge commit on '" + b1 + "'.", "Merge", () ->
                    async(() -> api.merge(owner, repo, b1, h1, "Merge " + h1 + " into " + b1), m -> { Ui.toast(this, m); d.dismiss(); reload(); }, e -> Ui.toast(this, msg(e))));
        });
    }

    private void pickBranch(View anchor, Ui.Cb<String> cb) {
        PopupMenu pm = new PopupMenu(this, anchor);
        for (int i = 0; i < branches.size(); i++) pm.getMenu().add(0, i, i, branches.get(i).name);
        pm.setOnMenuItemClickListener(it -> { cb.on(branches.get(it.getItemId()).name); return true; });
        pm.show();
    }
}
