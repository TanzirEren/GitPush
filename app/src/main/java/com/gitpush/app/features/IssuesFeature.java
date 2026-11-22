package com.gitpush.app.features;

import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Issues: filter, search, create, comment, edit, close/reopen, labels, assignees, lock. */
public class IssuesFeature extends Feature {
    private static final String[] STATES = {"open", "closed", "all"};

    @Override public String title() { return "Issues"; }
    @Override public String icon() { return "⚠"; }
    @Override public String blurb() { return "Create, comment, label, assign, lock, close"; }
    @Override public String[] filters() { return new String[]{"Open", "Closed", "All"}; }
    @Override public boolean canAdd() { return true; }
    @Override public String empty() { return "No issues here."; }

    @Override public List<Row> load() throws Exception {
        JSONArray arr = a.api.getAll(rp() + "/issues?state=" + STATES[filter], null, 3);
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null || j.has("pull_request")) continue;
            String labels = join(j.optJSONArray("labels"), "name");
            out.add(new Row("#" + j.optInt("number") + "  " + s(j, "title"), "by " + s(o(j, "user"), "login") + "  •  " + j.optInt("comments") + " comments  •  " + ago(s(j, "created_at")) + (labels.isEmpty() ? "" : "\n" + labels),
                    j.optBoolean("locked") ? "locked" : s(j, "state"), j));
        }
        return out;
    }

    @Override public void onAdd() {
        Form.show(a, "New issue", "➕", "Create", Form.list(Form.text("title", "Title", ""), Form.multi("body", "Description (Markdown)", ""),
                Form.text("labels", "Labels (comma separated)", ""), Form.text("assignees", "Assignees (usernames, comma separated)", "")), m -> {
            if (m.get("title").isEmpty()) { Ui.toast(a, "Enter a title"); return; }
            a.act(() -> a.api.send("POST", rp() + "/issues", new JSONObject().put("title", m.get("title")).put("body", m.get("body"))
                    .put("labels", list(m.get("labels"))).put("assignees", list(m.get("assignees")))), "Issue created");
        });
    }

    @Override public void onRow(final Row r) {
        final JSONObject j = r.j;
        final int n = j.optInt("number");
        final boolean open = "open".equals(s(j, "state")), locked = j.optBoolean("locked");
        final String[] items = {"💬  View thread", "✏  Add comment", "📝  Edit title / description", open ? "✔  Close issue" : "🔁  Reopen issue", "🏷  Labels…", "👤  Assignees…", locked ? "🔓  Unlock conversation" : "🔒  Lock conversation", "🔗  Copy link", "🌐  Open on GitHub"};
        Ui.pickList(a, "#" + n, items, i -> {
            final String u = rp() + "/issues/" + n;
            if (i == 0) a.run(() -> {
                StringBuilder sb = new StringBuilder(s(j, "title") + "\n" + s(o(j, "user"), "login") + " opened " + ago(s(j, "created_at")) + "\n\n" + s(j, "body") + "\n");
                JSONArray cs = a.api.getAll(u + "/comments", null, 2);
                for (int k = 0; k < cs.length(); k++) { JSONObject c = cs.optJSONObject(k); sb.append("\n──── ").append(s(o(c, "user"), "login")).append("  ").append(ago(s(c, "created_at"))).append("\n").append(s(c, "body")).append("\n"); }
                return sb.toString();
            }, t -> showText("#" + n, t));
            else if (i == 1) Ui.prompt(a, "Comment", "Write a comment…", "", t -> { if (!t.trim().isEmpty()) a.act(() -> a.api.send("POST", u + "/comments", new JSONObject().put("body", t)), "Comment posted"); });
            else if (i == 2) Form.show(a, "Edit #" + n, "✏", "Save", Form.list(Form.text("title", "Title", s(j, "title")), Form.multi("body", "Description", s(j, "body"))),
                    m -> a.act(() -> a.api.send("PATCH", u, new JSONObject().put("title", m.get("title")).put("body", m.get("body"))), "Saved"));
            else if (i == 3) a.act(() -> a.api.send("PATCH", u, new JSONObject().put("state", open ? "closed" : "open")), open ? "Closed" : "Reopened");
            else if (i == 4) Ui.prompt(a, "Labels (comma separated)", "bug, help wanted", join(j.optJSONArray("labels"), "name"), t -> a.act(() -> a.api.send("PATCH", u, new JSONObject().put("labels", list(t))), "Labels saved"));
            else if (i == 5) Ui.prompt(a, "Assignees (usernames)", "octocat", join(j.optJSONArray("assignees"), "login"), t -> a.act(() -> a.api.send("PATCH", u, new JSONObject().put("assignees", list(t))), "Assignees saved"));
            else if (i == 6) { if (locked) a.act(() -> a.api.send("DELETE", u + "/lock", null), "Unlocked"); else a.act(() -> a.api.send("PUT", u + "/lock", new JSONObject().put("lock_reason", "resolved")), "Locked"); }
            else if (i == 7) { Ui.copy(a, "link", s(j, "html_url")); Ui.toast(a, "Link copied"); }
            else Ui.openUrl(a, s(j, "html_url"));
        });
    }
}
