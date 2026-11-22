package com.gitpush.app.features;

import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Gists: list mine/starred, create, view & edit files, add files, star, delete. */
public class GistsFeature extends Feature {
    @Override public String title() { return "Gists"; }
    @Override public String icon() { return "📝"; }
    @Override public String blurb() { return "Create, edit, star and delete gists"; }
    @Override public boolean needsRepo() { return false; }
    @Override public String[] filters() { return new String[]{"Mine", "Starred"}; }
    @Override public boolean canAdd() { return true; }

    @Override public List<Row> load() throws Exception {
        JSONArray arr = a.api.getAll(filter == 0 ? "/gists" : "/gists/starred", null, 3);
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            JSONObject files = o(j, "files");
            String first = files.keys().hasNext() ? files.keys().next() : "(empty)";
            out.add(new Row(s(j, "description").isEmpty() ? first : s(j, "description"), files.length() + " file(s) • " + first + " • " + ago(s(j, "updated_at")), j.optBoolean("public") ? "public" : "secret", j));
        }
        return out;
    }

    @Override public void onAdd() {
        Form.show(a, "New gist", "📝", "Create", Form.list(Form.text("desc", "Description", ""), Form.text("name", "File name (e.g. notes.md)", "notes.md"), Form.multi("content", "Content", ""), Form.toggle("pub", "Public (otherwise secret link)", false)), m -> {
            if (m.get("name").isEmpty() || m.get("content").isEmpty()) { Ui.toast(a, "File name and content are required"); return; }
            a.act(() -> a.api.send("POST", "/gists", new JSONObject().put("description", m.get("desc")).put("public", "true".equals(m.get("pub"))).put("files", new JSONObject().put(m.get("name"), new JSONObject().put("content", m.get("content"))))), "Gist created");
        });
    }

    @Override public void onRow(final Row r) {
        final String id = s(r.j, "id");
        final String[] items = {"👁  View files", "➕  Add a file", "⭐  Star", "☆  Unstar", "🔗  Copy link", "🌐  Open on GitHub", "🗑  Delete gist"};
        Ui.pickList(a, r.title, items, i -> {
            if (i == 0) a.run(() -> a.api.getObj("/gists/" + id), g -> {
                final JSONObject files = o(g, "files");
                final List<String> names = new ArrayList<>();
                for (Iterator<String> it = files.keys(); it.hasNext(); ) names.add(it.next());
                final String[] arr = names.toArray(new String[0]);
                Ui.pickList(a, "Files", arr, k -> editFile(id, arr[k], s(o(files, arr[k]), "content")));
            });
            else if (i == 1) Form.show(a, "Add file", "📝", "Add", Form.list(Form.text("name", "File name", ""), Form.multi("content", "Content", "")), m -> {
                if (m.get("name").isEmpty() || m.get("content").isEmpty()) return;
                a.act(() -> a.api.send("PATCH", "/gists/" + id, new JSONObject().put("files", new JSONObject().put(m.get("name"), new JSONObject().put("content", m.get("content"))))), "File added");
            });
            else if (i == 2) a.act(() -> a.api.send("PUT", "/gists/" + id + "/star", null), "Starred");
            else if (i == 3) a.act(() -> a.api.send("DELETE", "/gists/" + id + "/star", null), "Unstarred");
            else if (i == 4) { Ui.copy(a, "gist", s(r.j, "html_url")); Ui.toast(a, "Link copied"); }
            else if (i == 5) Ui.openUrl(a, s(r.j, "html_url"));
            else Ui.confirmDanger(a, "Delete this gist?", "This cannot be undone.", "Delete", () -> a.act(() -> a.api.send("DELETE", "/gists/" + id, null), "Deleted"));
        });
    }

    private void editFile(final String id, final String name, final String content) {
        Form.show(a, name, "📝", "Save", Form.list(Form.multi("content", "Content", content)), m ->
                a.act(() -> a.api.send("PATCH", "/gists/" + id, new JSONObject().put("files", new JSONObject().put(name, new JSONObject().put("content", m.get("content"))))), "Saved"));
    }
}
