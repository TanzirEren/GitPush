package com.gitpush.app.features;

import com.gitpush.app.IdeActivity;
import com.gitpush.app.data.Models.Branch;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.ui.Downloader;
import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Branches (create, rename, delete, default, protect, compare, merge) and tags (create, move, delete, zip). */
public class BranchesFeature extends Feature {
    private String defaultBranch = "main";

    @Override public String title() { return "Branches & tags"; }
    @Override public String icon() { return "🌿"; }
    @Override public String blurb() { return "Create, rename, protect, merge, tag"; }
    @Override public String[] filters() { return new String[]{"Branches", "Tags"}; }
    @Override public boolean canAdd() { return true; }

    @Override public List<Row> load() throws Exception {
        Repo r = a.st().findRepo(a.owner, a.repo);
        defaultBranch = r != null ? r.defaultBranch : a.api.repo(a.owner, a.repo).defaultBranch;
        List<Row> out = new ArrayList<>();
        if (filter == 0) {
            JSONArray arr = a.api.getAll(rp() + "/branches", null, 3);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i);
                if (j == null) continue;
                String nm = s(j, "name");
                out.add(new Row(nm, s(o(j, "commit"), "sha").substring(0, Math.min(7, s(o(j, "commit"), "sha").length())), nm.equals(defaultBranch) ? "default" : j.optBoolean("protected") ? "protected" : "", j));
            }
        } else {
            JSONArray arr = a.api.getAll(rp() + "/tags", null, 3);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i);
                if (j == null) continue;
                String sha = s(o(j, "commit"), "sha");
                out.add(new Row(s(j, "name"), sha.substring(0, Math.min(7, sha.length())), "tag", j));
            }
        }
        return out;
    }

    @Override public void onAdd() {
        a.run(() -> a.api.branches(a.owner, a.repo), branches -> {
            String[] names = new String[branches.size()];
            int def = 0;
            for (int i = 0; i < names.length; i++) { names[i] = branches.get(i).name; if (names[i].equals(defaultBranch)) def = i; }
            final List<Branch> bl = branches;
            Form.show(a, filter == 0 ? "New branch" : "New tag", "➕", "Create", Form.list(Form.text("name", filter == 0 ? "Branch name" : "Tag name (e.g. v1.0.0)", ""), Form.choice("from", "Create from branch", names, def)), m -> {
                final String nm = m.get("name").trim().replace(' ', '-');
                if (nm.isEmpty()) { Ui.toast(a, "Enter a name"); return; }
                String sha = "";
                for (Branch b : bl) if (b.name.equals(m.get("from"))) sha = b.sha;
                final String fsha = sha;
                if (filter == 0) a.act(() -> { a.api.createBranch(a.owner, a.repo, nm, fsha); return "ok"; }, "Branch created");
                else a.act(() -> { a.api.setTag(a.owner, a.repo, nm, fsha); return "ok"; }, "Tag created");
            });
        });
    }

    @Override public void onRow(final Row r) {
        final String name = s(r.j, "name");
        if (filter == 1) { tagMenu(name, s(o(r.j, "commit"), "sha")); return; }
        final boolean prot = r.j.optBoolean("protected");
        final boolean isDef = name.equals(defaultBranch);
        final String[] items = {"💻  Open in Code IDE", "📖  Compare with " + defaultBranch, "🔁  Merge into " + defaultBranch, "⭐  Make default branch", "✏  Rename", prot ? "🔓  Edit / remove protection" : "🛡  Protect branch", "🗑  Delete branch", "🔗  Copy name"};
        Ui.pickList(a, name, items, i -> {
            if (i == 0) { a.st().browserBranch = name; IdeActivity.open(a, a.owner, a.repo, null); }
            else if (i == 1) a.run(() -> a.api.compare(a.owner, a.repo, defaultBranch, name), c -> showText(name + " vs " + defaultBranch, "Status: " + c.status + "\nAhead: " + c.ahead + "   Behind: " + c.behind + "\nFiles changed: " + c.files.size()));
            else if (i == 2) { if (isDef) Ui.toast(a, "That is the default branch"); else Ui.confirm(a, "Merge " + name + " into " + defaultBranch + "?", "Creates a merge commit on " + defaultBranch + ".", "Merge", () -> a.act(() -> a.api.merge(a.owner, a.repo, defaultBranch, name, "Merge " + name), "Merged")); }
            else if (i == 3) a.act(() -> a.api.send("PATCH", rp(), new JSONObject().put("default_branch", name)), "Default branch changed");
            else if (i == 4) Ui.prompt(a, "Rename branch", "new-name", name, t -> { if (!t.trim().isEmpty() && !t.trim().equals(name)) a.act(() -> a.api.send("POST", rp() + "/branches/" + enc(name) + "/rename", new JSONObject().put("new_name", t.trim())), "Renamed"); });
            else if (i == 5) protect(name, prot);
            else if (i == 6) { if (isDef) Ui.toast(a, "Change the default branch first"); else Ui.confirmDanger(a, "Delete " + name + "?", "The branch is removed from GitHub.", "Delete", () -> a.act(() -> { a.api.deleteBranch(a.owner, a.repo, name); return "ok"; }, "Branch deleted")); }
            else { Ui.copy(a, "branch", name); Ui.toast(a, "Copied"); }
        });
    }

    private static String enc(String s) { return com.gitpush.app.data.GitHubApi.enc2(s).replace("%2F", "/"); }

    private void protect(final String name, boolean already) {
        final String u = rp() + "/branches/" + enc(name) + "/protection";
        if (already) {
            Ui.pickList(a, "Protection of " + name, new String[]{"✏  Change rules", "🔓  Remove protection"}, i -> {
                if (i == 1) a.act(() -> a.api.send("DELETE", u, null), "Protection removed");
                else protectForm(name, u);
            });
        } else protectForm(name, u);
    }

    private void protectForm(final String name, final String u) {
        Form.show(a, "Protect " + name, "🛡", "Apply", Form.list(Form.toggle("pr", "Require a pull request before merging", true), Form.number("approvals", "Approvals required", "1"),
                Form.toggle("admins", "Apply to admins too", false), Form.toggle("linear", "Require linear history", false), Form.toggle("convo", "Require conversation resolution", false),
                Form.toggle("force", "Allow force pushes", false), Form.toggle("del", "Allow deletion", false)), m -> {
            a.act(() -> {
                JSONObject b = new JSONObject();
                b.put("required_status_checks", JSONObject.NULL);
                b.put("enforce_admins", "true".equals(m.get("admins")));
                if ("true".equals(m.get("pr"))) { int n = 1; try { n = Math.max(0, Math.min(6, Integer.parseInt(m.get("approvals")))); } catch (Exception ignored) { } b.put("required_pull_request_reviews", new JSONObject().put("required_approving_review_count", n)); }
                else b.put("required_pull_request_reviews", JSONObject.NULL);
                b.put("restrictions", JSONObject.NULL);
                b.put("required_linear_history", "true".equals(m.get("linear")));
                b.put("required_conversation_resolution", "true".equals(m.get("convo")));
                b.put("allow_force_pushes", "true".equals(m.get("force")));
                b.put("allow_deletions", "true".equals(m.get("del")));
                return a.api.send("PUT", u, b);
            }, "Branch protected");
        });
    }

    private void tagMenu(final String name, final String sha) {
        final String[] items = {"⬇  Download source ZIP", "📌  Move tag to a branch head…", "🔗  Copy name", "🌐  Open on GitHub", "🗑  Delete tag"};
        Ui.pickList(a, name, items, i -> {
            if (i == 0) Downloader.request(a, a.api, rp() + "/zipball/" + enc(name), a.repo + "-" + name.replace('/', '-') + ".zip");
            else if (i == 1) a.run(() -> a.api.branches(a.owner, a.repo), bl -> {
                String[] n = new String[bl.size()];
                for (int k = 0; k < n.length; k++) n[k] = bl.get(k).name;
                Ui.pickList(a, "Move " + name + " to", n, k -> Ui.confirm(a, "Move tag " + name + "?", "The tag is force-moved to the head of " + bl.get(k).name + ".", "Move", () -> a.act(() -> { a.api.setTag(a.owner, a.repo, name, bl.get(k).sha); return "ok"; }, "Tag moved")));
            });
            else if (i == 2) { Ui.copy(a, "tag", name); Ui.toast(a, "Copied"); }
            else if (i == 3) Ui.openUrl(a, "https://github.com/" + a.owner + "/" + a.repo + "/releases/tag/" + name);
            else Ui.confirmDanger(a, "Delete tag " + name + "?", "A release using this tag becomes a draft.", "Delete", () -> a.act(() -> { a.api.deleteTag(a.owner, a.repo, name); return "ok"; }, "Tag deleted"));
        });
    }
}
