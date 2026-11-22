package com.gitpush.app.features;

import com.gitpush.app.data.SecretsPush;
import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deployment environments with their own secrets and variables (the "environment secrets" gap of v2.0). */
public class EnvsFeature extends Feature {
    @Override public String title() { return "Environments"; }
    @Override public String icon() { return "🧩"; }
    @Override public String blurb() { return "Environments, environment secrets & variables"; }
    @Override public boolean canAdd() { return true; }
    @Override public String empty() { return "No environments. Tap ＋ to create one (for example production)."; }

    @Override public List<Row> load() throws Exception {
        JSONArray arr = a.api.getObj(rp() + "/environments").optJSONArray("environments");
        List<Row> out = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j != null) out.add(new Row(s(j, "name"), "updated " + ago(s(j, "updated_at")), "", j));
        }
        return out;
    }

    private String base(String env) throws Exception {
        long id = a.api.getObj(rp()).optLong("id");
        return "/repositories/" + id + "/environments/" + com.gitpush.app.data.GitHubApi.enc2(env);
    }

    @Override public void onAdd() {
        Form.show(a, "New environment", "🧩", "Create", Form.list(Form.text("name", "Name (e.g. production)", ""), Form.number("wait", "Wait timer in minutes (0 = none)", "0")), m -> {
            if (m.get("name").isEmpty()) { Ui.toast(a, "Enter a name"); return; }
            int w = 0; try { w = Math.max(0, Math.min(43200, Integer.parseInt(m.get("wait")))); } catch (Exception ignored) { }
            final int fw = w;
            a.act(() -> a.api.send("PUT", rp() + "/environments/" + com.gitpush.app.data.GitHubApi.enc2(m.get("name")), new JSONObject().put("wait_timer", fw)), "Environment saved");
        });
    }

    @Override public void onRow(final Row r) {
        final String env = s(r.j, "name");
        Ui.pickList(a, env, new String[]{"🔑  Secrets", "📋  Variables", "⏱  Change wait timer", "🗑  Delete environment"}, i -> {
            if (i == 0) secrets(env);
            else if (i == 1) variables(env);
            else if (i == 2) Ui.prompt(a, "Wait timer (minutes)", "0", "0", t -> { int w = 0; try { w = Integer.parseInt(t.trim()); } catch (Exception ignored) { } final int fw = w; a.act(() -> a.api.send("PUT", rp() + "/environments/" + com.gitpush.app.data.GitHubApi.enc2(env), new JSONObject().put("wait_timer", fw)), "Saved"); });
            else Ui.confirmDanger(a, "Delete " + env + "?", "Its secrets and variables are deleted too.", "Delete", () -> a.act(() -> a.api.send("DELETE", rp() + "/environments/" + com.gitpush.app.data.GitHubApi.enc2(env), null), "Deleted"));
        });
    }

    private void secrets(final String env) {
        a.run(() -> { JSONArray s = a.api.getObj(base(env) + "/secrets").optJSONArray("secrets"); return s == null ? new JSONArray() : s; }, arr -> {
            final List<String> names = new ArrayList<>();
            names.add("➕  Add / update a secret");
            for (int i = 0; i < arr.length(); i++) names.add("🔑  " + arr.optJSONObject(i).optString("name"));
            final String[] l = names.toArray(new String[0]);
            Ui.pickList(a, env + " secrets", l, i -> {
                if (i == 0) Form.show(a, "Environment secret", "🔑", "Save", Form.list(Form.text("name", "NAME", ""), Form.secret("value", "Value")), m -> {
                    if (m.get("name").isEmpty() || m.get("value").isEmpty()) return;
                    final Map<String, String> v = new LinkedHashMap<>();
                    v.put(m.get("name").toUpperCase().replaceAll("[^A-Z0-9_]", "_"), m.get("value"));
                    a.run(() -> SecretsPush.push(a.api, a.owner, a.repo, env, v), n -> Ui.toast(a, "Secret saved (encrypted on this device)"));
                });
                else { final String nm = l[i].substring(4).trim(); Ui.confirmDanger(a, "Delete " + nm + "?", "", "Delete", () -> a.run(() -> a.api.send("DELETE", base(env) + "/secrets/" + nm, null), x -> Ui.toast(a, "Deleted"))); }
            });
        });
    }

    private void variables(final String env) {
        a.run(() -> { JSONArray s = a.api.getObj(base(env) + "/variables").optJSONArray("variables"); return s == null ? new JSONArray() : s; }, arr -> {
            final List<String> names = new ArrayList<>();
            names.add("➕  Add a variable");
            for (int i = 0; i < arr.length(); i++) names.add("📋  " + arr.optJSONObject(i).optString("name") + " = " + arr.optJSONObject(i).optString("value"));
            final String[] l = names.toArray(new String[0]);
            Ui.pickList(a, env + " variables", l, i -> {
                if (i == 0) Form.show(a, "Environment variable", "📋", "Save", Form.list(Form.text("name", "NAME", ""), Form.text("value", "Value", "")), m -> {
                    final String nm = m.get("name").toUpperCase().replaceAll("[^A-Z0-9_]", "_");
                    if (nm.isEmpty()) return;
                    a.run(() -> { try { return a.api.send("POST", base(env) + "/variables", new JSONObject().put("name", nm).put("value", m.get("value"))); } catch (Exception e) { return a.api.send("PATCH", base(env) + "/variables/" + nm, new JSONObject().put("name", nm).put("value", m.get("value"))); } }, x -> Ui.toast(a, "Saved"));
                });
                else { final String nm = arr.optJSONObject(i - 1).optString("name"); Ui.confirmDanger(a, "Delete " + nm + "?", "", "Delete", () -> a.run(() -> a.api.send("DELETE", base(env) + "/variables/" + nm, null), x -> Ui.toast(a, "Deleted"))); }
            });
        });
    }
}
