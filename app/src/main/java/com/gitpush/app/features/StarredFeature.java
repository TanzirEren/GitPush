package com.gitpush.app.features;

import com.gitpush.app.ImportRepoActivity;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Your starred repositories: search, unstar, fork, open. */
public class StarredFeature extends Feature {
    @Override public String title() { return "Starred repos"; }
    @Override public String icon() { return "⭐"; }
    @Override public String blurb() { return "Search, unstar, fork what you starred"; }
    @Override public boolean needsRepo() { return false; }
    @Override public String empty() { return "You haven't starred anything."; }

    @Override public List<Row> load() throws Exception {
        JSONArray arr = a.api.getAll("/user/starred", null, 5);
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            out.add(new Row(s(j, "full_name"), (s(j, "description").isEmpty() ? "" : s(j, "description") + "\n") + "★ " + j.optInt("stargazers_count") + "  •  " + s(j, "language"), j.optBoolean("private") ? "private" : "", j));
        }
        return out;
    }

    @Override public void onRow(final Row r) {
        final String full = s(r.j, "full_name");
        Ui.pickList(a, full, new String[]{"🌐  Open on GitHub", "☆  Unstar", "⑂  Fork / import…", "🔗  Copy link"}, i -> {
            if (i == 0) Ui.openUrl(a, s(r.j, "html_url"));
            else if (i == 1) a.act(() -> a.api.send("DELETE", "/user/starred/" + full, null), "Unstarred");
            else if (i == 2) a.startActivity(new android.content.Intent(a, ImportRepoActivity.class));
            else { Ui.copy(a, "link", s(r.j, "html_url")); Ui.toast(a, "Copied"); }
        });
    }
}
