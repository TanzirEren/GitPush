package com.gitpush.app.features;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.gitpush.app.R;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

/** Search GitHub: repositories, code, issues/PRs and users. */
public class SearchFeature extends Feature {
    private static final String[] KINDS = {"repositories", "code", "issues", "users"};
    private static final String[] LABELS = {"Repos", "Code", "Issues & PRs", "Users"};
    private int kind;
    private LinearLayout results, chips;
    private EditText q;

    @Override public String title() { return "Search GitHub"; }
    @Override public String icon() { return "🔍"; }
    @Override public String blurb() { return "Repos, code, issues and users"; }
    @Override public boolean needsRepo() { return false; }

    @Override public View view() {
        LinearLayout page = Ui.vbox(a);
        q = Ui.input(a, "Search…", "");
        q.setSingleLine(true);
        q.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        q.setOnEditorActionListener((v, id, e) -> { go(); return true; });
        page.addView(q, Ui.lp(a, Ui.MATCH, Ui.WRAP, 16, 10, 16, 4));
        chips = Ui.hbox(a);
        chips.setPadding(Ui.dp(a, 16), Ui.dp(a, 2), Ui.dp(a, 16), Ui.dp(a, 6));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        page.addView(hs);
        renderChips();
        ScrollView sv = new ScrollView(a);
        results = Ui.vbox(a);
        results.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 24));
        sv.addView(results);
        page.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        results.addView(Ui.muted(a, "Type a query and press search.\nTips: language:java stars:>100  •  user:octocat  •  filename:build.gradle"));
        return page;
    }

    private void renderChips() {
        chips.removeAllViews();
        for (int i = 0; i < LABELS.length; i++) { final int k = i; chips.addView(Ui.chip(a, LABELS[i], kind == i, v -> { kind = k; renderChips(); if (!q.getText().toString().trim().isEmpty()) go(); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0)); }
    }

    private void go() {
        final String text = q.getText().toString().trim();
        if (text.isEmpty()) return;
        results.removeAllViews();
        results.addView(Ui.muted(a, "Searching…"));
        a.run(() -> a.api.getObj("/search/" + KINDS[kind] + "?per_page=30&q=" + GitHubApi.enc2(text)), r -> {
            results.removeAllViews();
            JSONArray items = r.optJSONArray("items");
            results.addView(Ui.muted(a, r.optInt("total_count") + " results"), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 0, 6));
            if (items == null) return;
            for (int i = 0; i < items.length(); i++) {
                final JSONObject j = items.optJSONObject(i);
                if (j == null) continue;
                String title, sub;
                if (kind == 0) { title = s(j, "full_name"); sub = s(j, "description") + "\n★ " + j.optInt("stargazers_count") + "  •  " + s(j, "language"); }
                else if (kind == 1) { title = s(j, "path"); sub = s(o(j, "repository"), "full_name"); }
                else if (kind == 2) { title = s(j, "title"); sub = s(j, "repository_url").replace("https://api.github.com/repos/", "") + "  •  " + s(j, "state"); }
                else { title = s(j, "login"); sub = s(j, "type"); }
                LinearLayout c = Ui.card(a);
                c.addView(Ui.title(a, title));
                if (!sub.trim().isEmpty()) c.addView(Ui.muted(a, sub), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 3, 0, 0));
                c.setOnClickListener(v -> menu(j));
                results.addView(c, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
            }
        });
    }

    private void menu(final JSONObject j) {
        final String url = s(j, "html_url");
        final boolean repo = kind == 0;
        Ui.pickList(a, "Result", repo ? new String[]{"🌐  Open on GitHub", "⭐  Star", "🔗  Copy link"} : new String[]{"🌐  Open on GitHub", "🔗  Copy link"}, i -> {
            if (i == 0) Ui.openUrl(a, url);
            else if (repo && i == 1) a.run(() -> a.api.send("PUT", "/user/starred/" + s(j, "full_name"), null), x -> Ui.toast(a, "Starred"));
            else { Ui.copy(a, "link", url); Ui.toast(a, "Copied"); }
        });
    }
}
