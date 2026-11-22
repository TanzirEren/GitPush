package com.gitpush.app.features;

import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Security alerts: Dependabot, secret scanning and code scanning – view and dismiss/resolve. */
public class AlertsFeature extends Feature {
    @Override public String title() { return "Security alerts"; }
    @Override public String icon() { return "🛡"; }
    @Override public String blurb() { return "Dependabot, secret and code scanning alerts"; }
    @Override public String[] filters() { return new String[]{"Dependabot", "Secrets", "Code scanning"}; }
    @Override public String empty() { return "No open alerts – or the feature is not enabled for this repository."; }

    @Override public List<Row> load() throws Exception {
        String path = filter == 0 ? "/dependabot/alerts" : filter == 1 ? "/secret-scanning/alerts" : "/code-scanning/alerts";
        JSONArray arr = a.api.getAll(rp() + path + "?state=open", null, 2);
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            if (filter == 0) out.add(new Row(s(o(j, "security_advisory"), "summary"), s(o(o(j, "dependency"), "package"), "name") + "  •  " + s(o(o(j, "dependency"), "package"), "ecosystem") + "  •  " + ago(s(j, "created_at")), s(o(j, "security_advisory"), "severity"), j));
            else if (filter == 1) out.add(new Row(s(j, "secret_type_display_name").isEmpty() ? s(j, "secret_type") : s(j, "secret_type_display_name"), "found " + ago(s(j, "created_at")), "secret", j));
            else out.add(new Row(s(o(j, "rule"), "description"), s(o(o(j, "most_recent_instance"), "location"), "path") + "  •  " + ago(s(j, "created_at")), s(o(j, "rule"), "security_severity_level").isEmpty() ? s(o(j, "rule"), "severity") : s(o(j, "rule"), "security_severity_level"), j));
        }
        return out;
    }

    @Override public void onRow(final Row r) {
        final int n = r.j.optInt("number");
        final String base = rp() + (filter == 0 ? "/dependabot/alerts/" : filter == 1 ? "/secret-scanning/alerts/" : "/code-scanning/alerts/") + n;
        Ui.pickList(a, "Alert #" + n, new String[]{filter == 1 ? "✔  Mark resolved (revoked)" : "✔  Dismiss (not used / false positive)", "🌐  Open on GitHub"}, i -> {
            if (i == 1) { Ui.openUrl(a, s(r.j, "html_url")); return; }
            a.act(() -> {
                JSONObject b = new JSONObject();
                if (filter == 0) b.put("state", "dismissed").put("dismissed_reason", "not_used");
                else if (filter == 1) b.put("state", "resolved").put("resolution", "revoked");
                else b.put("state", "dismissed").put("dismissed_reason", "false positive");
                return a.api.send("PATCH", base, b);
            }, "Done");
        });
    }
}
