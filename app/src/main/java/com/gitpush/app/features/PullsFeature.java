package com.gitpush.app.features;

import com.gitpush.app.data.Models.Branch;
import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Pull requests: list, create from a branch, details, merge (merge/squash/rebase), close, comment, update branch, files. */
public class PullsFeature extends Feature {
    private static final String[] STATES = {"open", "closed", "all"};

    @Override public String title() { return "Pull requests"; }
    @Override public String icon() { return "⚡"; }
    @Override public String blurb() { return "Create, review, merge, squash, rebase, close"; }
    @Override public String[] filters() { return new String[]{"Open", "Closed", "All"}; }
    @Override public boolean canAdd() { return true; }
    @Override public String empty() { return "No pull requests here."; }

    @Override public List<Row> load() throws Exception {
        JSONArray arr = a.api.getAll(rp() + "/pulls?state=" + STATES[filter], null, 3);
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            String st = !j.isNull("merged_at") ? "merged" : j.optBoolean("draft") ? "draft" : s(j, "state");
            out.add(new Row("#" + j.optInt("number") + "  " + s(j, "title"), s(o(j, "head"), "ref") + " → " + s(o(j, "base"), "ref") + "  •  by " + s(o(j, "user"), "login") + "  •  " + ago(s(j, "created_at")), st, j));
        }
        return out;
    }

    @Override public void onAdd() {
        a.run(() -> a.api.branches(a.owner, a.repo), branches -> {
            if (branches.size() < 2) { Ui.toast(a, "You need at least two branches"); return; }
            String[] names = new String[branches.size()];
            int base = 0;
            for (int i = 0; i < names.length; i++) { names[i] = branches.get(i).name; if (names[i].equals("main") || names[i].equals("master")) base = i; }
            Form.show(a, "New pull request", "⚡", "Create", Form.list(Form.choice("head", "From (compare)", names, names.length > 1 ? (base == 0 ? 1 : 0) : 0), Form.choice("base", "Into (base)", names, base),
                    Form.text("title", "Title", ""), Form.multi("body", "Description", ""), Form.toggle("draft", "Draft", false)), m -> {
                if (m.get("title").isEmpty()) { Ui.toast(a, "Enter a title"); return; }
                a.act(() -> a.api.send("POST", rp() + "/pulls", new JSONObject().put("title", m.get("title")).put("head", m.get("head")).put("base", m.get("base")).put("body", m.get("body")).put("draft", "true".equals(m.get("draft")))), "Pull request created");
            });
        });
    }

    @Override public void onRow(final Row r) {
        final JSONObject j = r.j;
        final int n = j.optInt("number");
        final boolean open = "open".equals(s(j, "state"));
        final List<String> items = new ArrayList<>();
        items.add("ℹ  Details & mergeability");
        items.add("📄  Files changed");
        if (open) { items.add("✔  Merge (merge commit)"); items.add("✔  Squash and merge"); items.add("✔  Rebase and merge"); items.add("🔁  Update branch"); items.add("✘  Close"); }
        else if (j.isNull("merged_at")) items.add("🔁  Reopen");
        items.add("✏  Comment");
        items.add("🌐  Open on GitHub");
        final String[] arr = items.toArray(new String[0]);
        final String u = rp() + "/pulls/" + n;
        Ui.pickList(a, "#" + n + "  " + s(j, "title"), arr, i -> {
            String l = arr[i];
            if (l.contains("Details")) a.run(() -> a.api.getObj(u), d -> showText("#" + n, s(d, "title") + "\n\n" + s(o(d, "head"), "ref") + " → " + s(o(d, "base"), "ref") + "\nCommits: " + d.optInt("commits") + "   Files: " + d.optInt("changed_files")
                    + "   +" + d.optInt("additions") + " −" + d.optInt("deletions") + "\nMergeable: " + (d.isNull("mergeable") ? "checking…" : d.optBoolean("mergeable") ? "yes" : "NO – conflicts") + "  (" + s(d, "mergeable_state") + ")\n\n" + s(d, "body")));
            else if (l.contains("Files changed")) a.run(() -> {
                JSONArray fs = a.api.getAll(u + "/files", null, 3);
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < fs.length(); k++) { JSONObject f = fs.optJSONObject(k); sb.append(s(f, "status").substring(0, 1).toUpperCase()).append("  ").append(s(f, "filename")).append("   +").append(f.optInt("additions")).append(" −").append(f.optInt("deletions")).append('\n'); }
                return sb.toString();
            }, t -> showText("Files in #" + n, t));
            else if (l.contains("Merge (merge")) merge(u, n, "merge");
            else if (l.contains("Squash")) merge(u, n, "squash");
            else if (l.contains("Rebase")) merge(u, n, "rebase");
            else if (l.contains("Update branch")) a.act(() -> a.api.send("PUT", u + "/update-branch", new JSONObject()), "Branch update queued");
            else if (l.contains("Close")) a.act(() -> a.api.send("PATCH", u, new JSONObject().put("state", "closed")), "Closed");
            else if (l.contains("Reopen")) a.act(() -> a.api.send("PATCH", u, new JSONObject().put("state", "open")), "Reopened");
            else if (l.contains("Comment")) Ui.prompt(a, "Comment", "Write a comment…", "", t -> { if (!t.trim().isEmpty()) a.act(() -> a.api.send("POST", rp() + "/issues/" + n + "/comments", new JSONObject().put("body", t)), "Comment posted"); });
            else Ui.openUrl(a, s(j, "html_url"));
        });
    }

    private void merge(final String u, final int n, final String method) {
        Ui.confirm(a, "Merge #" + n + " (" + method + ")?", "This changes the base branch on GitHub.", "Merge", () ->
                a.act(() -> a.api.send("PUT", u + "/merge", new JSONObject().put("merge_method", method)), "Merged"));
    }
}
