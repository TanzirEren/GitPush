package com.gitpush.app.features;

import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Labels (create, recolor, delete) and milestones (create, close/reopen, edit, delete). */
public class LabelsFeature extends Feature {
    @Override public String title() { return "Labels & milestones"; }
    @Override public String icon() { return "🏷"; }
    @Override public String blurb() { return "Organise issues with labels and milestones"; }
    @Override public String[] filters() { return new String[]{"Labels", "Milestones"}; }
    @Override public boolean canAdd() { return true; }

    @Override public List<Row> load() throws Exception {
        List<Row> out = new ArrayList<>();
        if (filter == 0) {
            JSONArray arr = a.api.getAll(rp() + "/labels", null, 3);
            for (int i = 0; i < arr.length(); i++) { JSONObject j = arr.optJSONObject(i); if (j != null) out.add(new Row(s(j, "name"), s(j, "description"), "#" + s(j, "color"), j)); }
        } else {
            JSONArray arr = a.api.getAll(rp() + "/milestones?state=all", null, 2);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i);
                if (j == null) continue;
                String due = s(j, "due_on");
                out.add(new Row(s(j, "title"), j.optInt("open_issues") + " open • " + j.optInt("closed_issues") + " closed" + (due.isEmpty() ? "" : " • due " + due.substring(0, 10)), s(j, "state"), j));
            }
        }
        return out;
    }

    @Override public void onAdd() { if (filter == 0) labelForm(null); else milestoneForm(null); }

    private void labelForm(final JSONObject l) {
        Form.show(a, l == null ? "New label" : "Edit label", "🏷", "Save", Form.list(Form.text("name", "Name", l == null ? "" : s(l, "name")), Form.text("color", "Color hex (e.g. 1d76db)", l == null ? "ededed" : s(l, "color")), Form.text("desc", "Description", l == null ? "" : s(l, "description"))), m -> {
            String c = m.get("color").replace("#", "");
            if (m.get("name").isEmpty() || !c.matches("[0-9a-fA-F]{6}")) { Ui.toast(a, "Name and a 6-digit hex color are required"); return; }
            a.act(() -> l == null ? a.api.send("POST", rp() + "/labels", new JSONObject().put("name", m.get("name")).put("color", c).put("description", m.get("desc")))
                    : a.api.send("PATCH", rp() + "/labels/" + com.gitpush.app.data.GitHubApi.enc2(s(l, "name")), new JSONObject().put("new_name", m.get("name")).put("color", c).put("description", m.get("desc"))), "Label saved");
        });
    }

    private void milestoneForm(final JSONObject ms) {
        Form.show(a, ms == null ? "New milestone" : "Edit milestone", "🏷", "Save", Form.list(Form.text("title", "Title", ms == null ? "" : s(ms, "title")), Form.text("desc", "Description", ms == null ? "" : s(ms, "description")),
                Form.text("due", "Due date (yyyy-MM-dd, optional)", ms == null || s(ms, "due_on").isEmpty() ? "" : s(ms, "due_on").substring(0, 10))), m -> {
            if (m.get("title").isEmpty()) { Ui.toast(a, "Enter a title"); return; }
            if (!m.get("due").isEmpty() && !m.get("due").matches("\\d{4}-\\d\\d-\\d\\d")) { Ui.toast(a, "Use the date format yyyy-MM-dd"); return; }
            a.act(() -> {
                JSONObject b = new JSONObject().put("title", m.get("title")).put("description", m.get("desc"));
                if (!m.get("due").isEmpty()) b.put("due_on", m.get("due") + "T12:00:00Z");
                return ms == null ? a.api.send("POST", rp() + "/milestones", b) : a.api.send("PATCH", rp() + "/milestones/" + ms.optInt("number"), b);
            }, "Milestone saved");
        });
    }

    @Override public void onRow(final Row r) {
        if (filter == 0) {
            Ui.pickList(a, s(r.j, "name"), new String[]{"✏  Edit", "🗑  Delete"}, i -> {
                if (i == 0) labelForm(r.j);
                else Ui.confirmDanger(a, "Delete label?", s(r.j, "name") + " is removed from all issues.", "Delete", () -> a.act(() -> a.api.send("DELETE", rp() + "/labels/" + com.gitpush.app.data.GitHubApi.enc2(s(r.j, "name")), null), "Deleted"));
            });
        } else {
            final boolean open = "open".equals(s(r.j, "state"));
            Ui.pickList(a, s(r.j, "title"), new String[]{"✏  Edit", open ? "✔  Close" : "🔁  Reopen", "🗑  Delete"}, i -> {
                final String u = rp() + "/milestones/" + r.j.optInt("number");
                if (i == 0) milestoneForm(r.j);
                else if (i == 1) a.act(() -> a.api.send("PATCH", u, new JSONObject().put("state", open ? "closed" : "open")), "Updated");
                else Ui.confirmDanger(a, "Delete milestone?", "", "Delete", () -> a.act(() -> a.api.send("DELETE", u, null), "Deleted"));
            });
        }
    }
}
