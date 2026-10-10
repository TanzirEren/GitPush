package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.GitOps;
import com.gitpush.app.data.Models.CommitDetail;
import com.gitpush.app.data.Models.CommitFile;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Rich;
import com.gitpush.app.ui.Ui;

public class CommitDetailActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo, String sha) {
        c.startActivity(new Intent(c, CommitDetailActivity.class).putExtra("owner", owner).putExtra("repo", repo).putExtra("sha", sha));
    }

    private String owner, repo, sha, branch;
    private GitHubApi api;
    private CommitDetail detail;
    private LinearLayout list;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        sha = getIntent().getStringExtra("sha");
        branch = st.viewerBranch;
        api = st.api();
        buildScreen("Commit " + sha.substring(0, 7), repo, true);
        addTextAction("SHA", v -> { Ui.copy(this, "SHA", sha); Ui.toast(this, "SHA copied"); });
        addTextAction("Open", v -> Ui.openUrl(this, "https://github.com/" + owner + "/" + repo + "/commit/" + sha));
        addTextAction("Revert", v -> revert());
        ScrollView sv = new ScrollView(this);
        list = Ui.vbox(this);
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        list.addView(Ui.muted(this, "Loading…"));
        sv.addView(list);
        body.addView(sv);
        async(() -> api.commit(owner, repo, sha), d -> { detail = d; render(); }, e -> { list.removeAllViews(); list.addView(Ui.text(this, msg(e), 13, false, R.attr.gpError)); });
    }

    private void render() {
        list.removeAllViews();
        LinearLayout head = Ui.card(this);
        head.addView(Ui.body(this, detail.info.message));
        head.addView(Ui.muted(this, detail.info.author + " • " + Ui.agoIso(detail.info.date)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        int add = 0, del = 0;
        for (CommitFile f : detail.files) { add += f.additions; del += f.deletions; }
        head.addView(Ui.text(this, detail.files.size() + " changed file(s)  +" + add + "  −" + del, 12, true, R.attr.gpPrimary));
        list.addView(head, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
        for (final CommitFile f : detail.files) {
            final LinearLayout card = Ui.card(this);
            LinearLayout r = Ui.hbox(this);
            int col = f.status.equals("added") ? R.attr.gpOk : f.status.equals("removed") ? R.attr.gpError : R.attr.gpWarn;
            r.addView(Ui.pill(this, f.status.toUpperCase(), col), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
            TextView n = Ui.text(this, f.filename, 12, false, R.attr.gpText);
            r.addView(n, Ui.weight(1f));
            r.addView(Ui.text(this, "+" + f.additions + " −" + f.deletions, 11, false, R.attr.gpMuted));
            card.addView(r);
            final LinearLayout diffBox = Ui.vbox(this);
            diffBox.setVisibility(View.GONE);
            card.addView(diffBox);
            card.setOnClickListener(v -> {
                if (diffBox.getChildCount() == 0) {
                    if (f.patch == null) diffBox.addView(Ui.muted(this, "No text diff available (binary or too large)."));
                    else {
                        TextView t = new TextView(this);
                        t.setTypeface(android.graphics.Typeface.MONOSPACE);
                        t.setTextSize(11);
                        t.setTextColor(Ui.color(this, R.attr.gpText));
                        t.setText(Rich.diff(this, f.patch));
                        HorizontalScrollView hs = new HorizontalScrollView(this);
                        hs.addView(t);
                        diffBox.addView(hs);
                    }
                }
                diffBox.setVisibility(diffBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            });
            list.addView(card, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        }
    }

    private void revert() {
        if (detail == null) return;
        Ui.confirm(this, "Revert this commit?", "A new commit on '" + branch + "' restores the previous version of every file this commit changed. Later edits to those files will be overwritten.", "Revert", () ->
                async(() -> GitOps.revert(api, owner, repo, branch, detail), r -> Ui.toast(this, "Reverted in a new commit"), e -> Ui.toast(this, msg(e))));
    }
}
