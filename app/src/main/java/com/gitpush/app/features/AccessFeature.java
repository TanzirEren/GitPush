package com.gitpush.app.features;

import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Collaborators (add, change role, remove) and pending invitations (cancel, change role). */
public class AccessFeature extends Feature {
    private static final String[] ROLES = {"pull", "triage", "push", "maintain", "admin"};

    @Override public String title() { return "Collaborators"; }
    @Override public String icon() { return "👤"; }
    @Override public String blurb() { return "Invite, change roles, remove, pending invites"; }
    @Override public String[] filters() { return new String[]{"People", "Invitations"}; }
    @Override public boolean canAdd() { return true; }

    @Override public List<Row> load() throws Exception {
        List<Row> out = new ArrayList<>();
        if (filter == 0) {
            JSONArray arr = a.api.getAll(rp() + "/collaborators", null, 3);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i);
                if (j == null) continue;
                JSONObject p = o(j, "permissions");
                String role = p.optBoolean("admin") ? "admin" : p.optBoolean("maintain") ? "maintain" : p.optBoolean("push") ? "write" : p.optBoolean("triage") ? "triage" : "read";
                out.add(new Row(s(j, "login"), s(j, "role_name"), role, j));
            }
        } else {
            JSONArray arr = a.api.getAll(rp() + "/invitations", null, 2);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i);
                if (j == null) continue;
                out.add(new Row(s(o(j, "invitee"), "login"), "invited " + ago(s(j, "created_at")) + " by " + s(o(j, "inviter"), "login"), s(j, "permissions"), j));
            }
        }
        return out;
    }

    @Override public String empty() { return filter == 0 ? "No collaborators (or no permission to list them)." : "No pending invitations."; }

    @Override public void onAdd() {
        Form.show(a, "Invite collaborator", "➕", "Invite", Form.list(Form.text("user", "GitHub username", ""), Form.choice("role", "Permission", ROLES, 2)), m -> {
            if (m.get("user").isEmpty()) { Ui.toast(a, "Enter a username"); return; }
            a.act(() -> a.api.send("PUT", rp() + "/collaborators/" + m.get("user"), new JSONObject().put("permission", m.get("role"))), "Invitation sent");
        });
    }

    @Override public void onRow(final Row r) {
        final String name = s(r.j, "login").isEmpty() ? s(o(r.j, "invitee"), "login") : s(r.j, "login");
        final boolean inv = filter == 1;
        final String[] items = {"🔁  Change permission", inv ? "✘  Cancel invitation" : "🗑  Remove from repository", "🌐  Open profile"};
        Ui.pickList(a, name, items, i -> {
            if (i == 0) Form.show(a, "Permission for " + name, "👤", "Save", Form.list(Form.choice("role", "Permission", ROLES, 2)), m -> {
                if (inv) a.act(() -> a.api.send("PATCH", rp() + "/invitations/" + r.j.optLong("id"), new JSONObject().put("permissions", m.get("role"))), "Updated");
                else a.act(() -> a.api.send("PUT", rp() + "/collaborators/" + name, new JSONObject().put("permission", m.get("role"))), "Updated");
            });
            else if (i == 1) Ui.confirmDanger(a, (inv ? "Cancel invitation for " : "Remove ") + name + "?", "", inv ? "Cancel invite" : "Remove",
                    () -> a.act(() -> a.api.send("DELETE", inv ? rp() + "/invitations/" + r.j.optLong("id") : rp() + "/collaborators/" + name, null), "Done"));
            else Ui.openUrl(a, "https://github.com/" + name);
        });
    }
}
