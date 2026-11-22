package com.gitpush.app.features;

import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Webhooks: create, edit, enable/disable, ping, test push, recent deliveries + redeliver, delete. */
public class HooksFeature extends Feature {
    @Override public String title() { return "Webhooks"; }
    @Override public String icon() { return "🔗"; }
    @Override public String blurb() { return "Create, ping, deliveries, redeliver"; }
    @Override public boolean canAdd() { return true; }
    @Override public String empty() { return "No webhooks yet. Tap ＋ to add one."; }

    @Override public List<Row> load() throws Exception {
        JSONArray arr = a.api.getAll(rp() + "/hooks", null, 2);
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            out.add(new Row(s(o(j, "config"), "url"), "events: " + join(j.optJSONArray("events"), ""), j.optBoolean("active") ? "active" : "disabled", j));
        }
        return out;
    }

    @Override public void onAdd() { form(null); }

    private void form(final JSONObject hook) {
        JSONObject cfg = hook == null ? new JSONObject() : o(hook, "config");
        Form.show(a, hook == null ? "New webhook" : "Edit webhook", "🔗", "Save", Form.list(Form.text("url", "Payload URL (https://…)", s(cfg, "url")),
                Form.choice("ct", "Content type", new String[]{"json", "form"}, "form".equals(s(cfg, "content_type")) ? 1 : 0), Form.secret("secret", "Secret (optional, leave blank to keep)"),
                Form.text("events", "Events (comma separated, * = all)", hook == null ? "push" : join(hook.optJSONArray("events"), "")), Form.toggle("active", "Active", hook == null || hook.optBoolean("active"))), m -> {
            if (!m.get("url").startsWith("https://") && !m.get("url").startsWith("http://")) { Ui.toast(a, "Enter a valid URL"); return; }
            a.act(() -> {
                JSONObject c = new JSONObject().put("url", m.get("url")).put("content_type", m.get("ct")).put("insecure_ssl", "0");
                if (!m.get("secret").isEmpty()) c.put("secret", m.get("secret"));
                JSONObject b = new JSONObject().put("active", "true".equals(m.get("active"))).put("events", list(m.get("events"))).put("config", c);
                if (hook == null) { b.put("name", "web"); return a.api.send("POST", rp() + "/hooks", b); }
                return a.api.send("PATCH", rp() + "/hooks/" + hook.optLong("id"), b);
            }, "Webhook saved");
        });
    }

    @Override public void onRow(final Row r) {
        final long id = r.j.optLong("id");
        final boolean active = r.j.optBoolean("active");
        final String u = rp() + "/hooks/" + id;
        final String[] items = {"✏  Edit", active ? "⏸  Disable" : "▶  Enable", "📡  Ping", "🧪  Send test push", "📜  Recent deliveries", "🗑  Delete"};
        Ui.pickList(a, s(o(r.j, "config"), "url"), items, i -> {
            if (i == 0) form(r.j);
            else if (i == 1) a.act(() -> a.api.send("PATCH", u, new JSONObject().put("active", !active)), active ? "Disabled" : "Enabled");
            else if (i == 2) a.act(() -> a.api.send("POST", u + "/pings", null), "Ping sent");
            else if (i == 3) a.act(() -> a.api.send("POST", u + "/tests", null), "Test push sent");
            else if (i == 4) deliveries(u);
            else Ui.confirmDanger(a, "Delete webhook?", "It stops receiving events immediately.", "Delete", () -> a.act(() -> a.api.send("DELETE", u, null), "Deleted"));
        });
    }

    private void deliveries(final String u) {
        a.run(() -> a.api.getArr(u + "/deliveries?per_page=30"), arr -> {
            if (arr.length() == 0) { Ui.toast(a, "No deliveries yet"); return; }
            final String[] labels = new String[arr.length()];
            final long[] ids = new long[arr.length()];
            for (int i = 0; i < labels.length; i++) {
                JSONObject d = arr.optJSONObject(i);
                ids[i] = d.optLong("id");
                labels[i] = (d.optInt("status_code") >= 200 && d.optInt("status_code") < 300 ? "✔  " : "✘  ") + d.optInt("status_code") + "  " + s(d, "event") + "  " + ago(s(d, "delivered_at"));
            }
            Ui.pickList(a, "Tap one to redeliver", labels, i -> Ui.confirm(a, "Redeliver?", labels[i], "Redeliver", () -> a.act(() -> a.api.send("POST", u + "/deliveries/" + ids[i] + "/attempts", null), "Redelivery queued")));
        });
    }
}
