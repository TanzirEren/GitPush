package com.gitpush.app.features;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.gitpush.app.R;
import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONObject;

/** Repository settings: features, merge options, homepage, template flag, security & analysis, transfer. */
public class SettingsFeature extends Feature {
    private LinearLayout col;
    private JSONObject repo = new JSONObject();
    private boolean busy;

    @Override public String title() { return "Repo settings"; }
    @Override public String icon() { return "⚙"; }
    @Override public String blurb() { return "Features, merge rules, security scanning, transfer"; }

    @Override public View view() {
        ScrollView sv = new ScrollView(a);
        col = Ui.vbox(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 28));
        sv.addView(col);
        refresh();
        return sv;
    }

    private void refresh() {
        col.removeAllViews();
        col.addView(Ui.muted(a, "Loading…"));
        a.run(() -> a.api.getObj(rp()), j -> { repo = j; build(); });
    }

    private void patch(String key, Object val, String label) {
        if (busy) return;
        busy = true;
        a.async2(() -> a.api.send("PATCH", rp(), new JSONObject().put(key, val)), r -> { busy = false; repo = r; Ui.toast(a, label + " updated"); build(); }, e -> { busy = false; Ui.toast(a, a.err(e)); build(); });
    }

    private void sw(LinearLayout parent, String label, final String key, boolean on, String help) {
        LinearLayout row = Ui.hbox(a);
        LinearLayout t = Ui.vbox(a);
        t.addView(Ui.body(a, label));
        if (!help.isEmpty()) t.addView(Ui.muted(a, help));
        row.addView(t, Ui.weight(1f));
        final Switch s = new Switch(a);
        s.setChecked(on);
        s.setOnCheckedChangeListener((b, v) -> { if (b.isPressed()) patch(key, v, label); });
        row.addView(s);
        row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        parent.addView(row);
    }

    private void security(LinearLayout parent, String label, final String key, final String status) {
        LinearLayout row = Ui.hbox(a);
        row.addView(Ui.body(a, label), Ui.weight(1f));
        final Switch s = new Switch(a);
        s.setChecked("enabled".equals(status));
        s.setOnCheckedChangeListener((b, v) -> {
            if (!b.isPressed()) return;
            a.async2(() -> a.api.send("PATCH", rp(), new JSONObject().put("security_and_analysis", new JSONObject().put(key, new JSONObject().put("status", v ? "enabled" : "disabled")))),
                    r -> { Ui.toast(a, label + (v ? " enabled" : " disabled")); refresh(); }, e -> { Ui.toast(a, a.err(e)); refresh(); });
        });
        row.addView(s);
        row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        parent.addView(row);
    }

    private void build() {
        col.removeAllViews();
        LinearLayout c1 = Ui.card(a);
        c1.addView(Ui.text(a, "Features", 13, true, R.attr.gpPrimary));
        sw(c1, "Issues", "has_issues", repo.optBoolean("has_issues"), "");
        sw(c1, "Wiki", "has_wiki", repo.optBoolean("has_wiki"), "");
        sw(c1, "Projects", "has_projects", repo.optBoolean("has_projects"), "");
        sw(c1, "Discussions", "has_discussions", repo.optBoolean("has_discussions"), "");
        sw(c1, "Template repository", "is_template", repo.optBoolean("is_template"), "Others can generate new repos from this one");
        col.addView(c1, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c2 = Ui.card(a);
        c2.addView(Ui.text(a, "Pull request merging", 13, true, R.attr.gpPrimary));
        sw(c2, "Allow merge commits", "allow_merge_commit", repo.optBoolean("allow_merge_commit"), "");
        sw(c2, "Allow squash merging", "allow_squash_merge", repo.optBoolean("allow_squash_merge"), "");
        sw(c2, "Allow rebase merging", "allow_rebase_merge", repo.optBoolean("allow_rebase_merge"), "");
        sw(c2, "Allow auto-merge", "allow_auto_merge", repo.optBoolean("allow_auto_merge"), "");
        sw(c2, "Delete branch after merge", "delete_branch_on_merge", repo.optBoolean("delete_branch_on_merge"), "");
        sw(c2, "Suggest updating PR branches", "allow_update_branch", repo.optBoolean("allow_update_branch"), "");
        sw(c2, "Require sign-off on web commits", "web_commit_signoff_required", repo.optBoolean("web_commit_signoff_required"), "");
        col.addView(c2, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        JSONObject sa = o(repo, "security_and_analysis");
        LinearLayout c3 = Ui.card(a);
        c3.addView(Ui.text(a, "Security & analysis", 13, true, R.attr.gpPrimary));
        security(c3, "Secret scanning", "secret_scanning", s(o(sa, "secret_scanning"), "status"));
        security(c3, "Push protection", "secret_scanning_push_protection", s(o(sa, "secret_scanning_push_protection"), "status"));
        security(c3, "Dependabot security updates", "dependabot_security_updates", s(o(sa, "dependabot_security_updates"), "status"));
        c3.addView(Ui.outlineButton(a, "Dependabot vulnerability alerts…", v -> alerts()), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        col.addView(c3, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c4 = Ui.card(a);
        c4.addView(Ui.text(a, "General", 13, true, R.attr.gpPrimary));
        c4.addView(Ui.outlineButton(a, "Homepage: " + (s(repo, "homepage").isEmpty() ? "not set" : s(repo, "homepage")), v -> Ui.prompt(a, "Homepage URL", "https://…", s(repo, "homepage"), t -> patch("homepage", t.trim(), "Homepage"))), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        c4.addView(Ui.outlineButton(a, "Visibility: " + (repo.optBoolean("private") ? "Private" : "Public"), v -> Ui.confirm(a, repo.optBoolean("private") ? "Make public?" : "Make private?", repo.optBoolean("private") ? "Everyone on the internet can see the code." : "Only you and collaborators can see it. Stars and watchers may be lost.", "Change", () -> patch("private", !repo.optBoolean("private"), "Visibility"))), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        c4.addView(Ui.outlineButton(a, "Transfer ownership…", v -> Form.show(a, "Transfer repository", "⚠", "Transfer", Form.list(Form.text("to", "New owner (user or organisation)", ""), Form.text("name", "New name (optional)", "")), m -> {
            if (m.get("to").isEmpty()) return;
            Ui.confirmDanger(a, "Transfer to " + m.get("to") + "?", "You lose control unless the new owner gives it back.", "Transfer", () -> a.run(() -> {
                JSONObject b = new JSONObject().put("new_owner", m.get("to"));
                if (!m.get("name").isEmpty()) b.put("new_name", m.get("name"));
                return a.api.send("POST", rp() + "/transfer", b);
            }, r -> Ui.toast(a, "Transfer requested")));
        })), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(c4);
    }

    private void alerts() {
        a.run(() -> { try { a.api.getObj(rp() + "/vulnerability-alerts"); return true; } catch (Exception e) { return false; } }, on ->
                Ui.pickList(a, "Vulnerability alerts are " + (on ? "ON" : "OFF"), new String[]{on ? "Turn off alerts" : "Turn on alerts", "Turn on automated security fixes", "Turn off automated security fixes"}, i -> {
                    final String m = i == 0 ? (on ? "DELETE" : "PUT") : i == 1 ? "PUT" : "DELETE";
                    final String path = i == 0 ? "/vulnerability-alerts" : "/automated-security-fixes";
                    a.run(() -> a.api.send(m, rp() + path, null), r -> Ui.toast(a, "Done"));
                }));
    }
}
