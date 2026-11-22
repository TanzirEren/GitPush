package com.gitpush.app.features;

import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Deploy keys: list, add (read-only or read/write), copy, delete. */
public class KeysFeature extends Feature {
    @Override public String title() { return "Deploy keys"; }
    @Override public String icon() { return "🔑"; }
    @Override public String blurb() { return "Add or remove SSH deploy keys"; }
    @Override public boolean canAdd() { return true; }
    @Override public String empty() { return "No deploy keys."; }

    @Override public List<Row> load() throws Exception {
        JSONArray arr = a.api.getAll(rp() + "/keys", null, 2);
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            String k = s(j, "key");
            out.add(new Row(s(j, "title"), (k.length() > 46 ? k.substring(0, 46) + "…" : k) + "\nadded " + ago(s(j, "created_at")), j.optBoolean("read_only") ? "read-only" : "read/write", j));
        }
        return out;
    }

    @Override public void onAdd() {
        Form.show(a, "Add deploy key", "🔑", "Add", Form.list(Form.text("title", "Title", ""), Form.multi("key", "Public key (ssh-ed25519 AAAA… or ssh-rsa …)", ""), Form.toggle("ro", "Read-only (recommended)", true)), m -> {
            if (m.get("title").isEmpty() || m.get("key").isEmpty()) { Ui.toast(a, "Title and key are required"); return; }
            a.act(() -> a.api.send("POST", rp() + "/keys", new JSONObject().put("title", m.get("title")).put("key", m.get("key")).put("read_only", "true".equals(m.get("ro")))), "Key added");
        });
    }

    @Override public void onRow(final Row r) {
        Ui.pickList(a, s(r.j, "title"), new String[]{"📋  Copy public key", "🗑  Delete key"}, i -> {
            if (i == 0) { Ui.copy(a, "key", s(r.j, "key")); Ui.toast(a, "Copied"); }
            else Ui.confirmDanger(a, "Delete this key?", "Anything using it loses access.", "Delete", () -> a.act(() -> a.api.send("DELETE", rp() + "/keys/" + r.j.optLong("id"), null), "Deleted"));
        });
    }
}
