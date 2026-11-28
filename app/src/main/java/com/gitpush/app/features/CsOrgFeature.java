package com.gitpush.app.features;

import com.gitpush.app.data.CodespacesApi;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.CsUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Codespaces owned by members of your organizations (org owners/admins only): stop or delete them. */
public class CsOrgFeature extends Feature {
    @Override public String title() { return "Organization codespaces"; }
    @Override public String icon() { return "🏢"; }
    @Override public String blurb() { return "See, stop and delete your org members' codespaces"; }
    @Override public boolean needsRepo() { return false; }
    @Override public String empty() { return "No organization codespaces found. This needs org owner access and the codespace scope."; }

    @Override public List<Row> load() throws Exception {
        CodespacesApi cs = new CodespacesApi(a.api);
        JSONArray orgs = cs.orgs();
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < orgs.length() && i < 10; i++) {
            String org = orgs.optJSONObject(i).optString("login");
            JSONArray list;
            try { list = cs.orgCodespaces(org); } catch (Exception e) { continue; }   // not an owner / feature off -> skip
            for (int k = 0; k < list.length(); k++) {
                JSONObject j = list.optJSONObject(k);
                if (j == null) continue;
                JSONObject own = o(j, "owner");
                try { j.put("_org", org); } catch (Exception ignored) { }
                String dn = s(j, "display_name").isEmpty() ? s(j, "name") : s(j, "display_name");
                out.add(new Row(dn, org + "  ·  @" + s(own, "login") + "  ·  " + s(o(j, "repository"), "full_name") + "\nused " + ago(s(j, "last_used_at")), CsUtil.stateLabel(s(j, "state")), j));
            }
        }
        return out;
    }

    @Override public void onRow(final Row r) {
        final String org = r.j.optString("_org"), user = s(o(r.j, "owner"), "login"), name = s(r.j, "name");
        final boolean run = CsUtil.running(s(r.j, "state"));
        final String[] items = {run ? "⏹  Stop" : "ℹ  Not running", "🗑  Delete"};
        Ui.pickList(a, r.title, items, i -> {
            CodespacesApi cs = new CodespacesApi(a.api);
            if (i == 0) { if (run) a.act(() -> { cs.orgStop(org, user, name); return ""; }, "Stopping…"); }
            else Ui.confirmDanger(a, "Delete this member's codespace?", "Their unpushed work is lost.\n\n" + r.title, "Delete", () -> a.act(() -> { cs.orgDelete(org, user, name); return ""; }, "Deleted"));
        });
    }
}
