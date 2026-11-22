package com.gitpush.app.features;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.gitpush.app.R;
import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.Ui;

import org.json.JSONObject;

/** GitHub Pages: enable, change source branch/folder, custom domain, enforce HTTPS, rebuild, latest build, disable. */
public class PagesFeature extends Feature {
    private LinearLayout col;

    @Override public String title() { return "GitHub Pages"; }
    @Override public String icon() { return "🌐"; }
    @Override public String blurb() { return "Publish a site: source, domain, HTTPS, rebuild"; }

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
        a.async2(() -> {
            try { return a.api.getObj(rp() + "/pages"); }
            catch (com.gitpush.app.data.ApiException e) { if (e.code == 404) return null; throw e; }
        }, this::build, e -> { col.removeAllViews(); col.addView(Ui.muted(a, "Couldn't load Pages\n" + a.err(e))); });
    }

    private void build(final JSONObject p) {
        col.removeAllViews();
        if (p == null) {
            LinearLayout c = Ui.card(a);
            c.addView(Ui.text(a, "Pages is not enabled", 14, true, R.attr.gpText));
            c.addView(Ui.muted(a, "Publish the files of a branch as a website at https://" + a.owner + ".github.io/" + a.repo), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 4, 0, 10));
            c.addView(Ui.button(a, "🚀  Enable GitHub Pages", v -> source(null)));
            col.addView(c);
            return;
        }
        JSONObject src = o(p, "source");
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, "Your site", 13, true, R.attr.gpPrimary));
        c.addView(Ui.body(a, s(p, "html_url")), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        c.addView(Ui.muted(a, "Status: " + s(p, "status") + "\nSource: " + s(src, "branch") + "  " + s(src, "path") + "\nHTTPS enforced: " + p.optBoolean("https_enforced") + (s(p, "cname").isEmpty() ? "" : "\nCustom domain: " + s(p, "cname"))), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        col.addView(c, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        LinearLayout b = Ui.vbox(a);
        b.addView(Ui.button(a, "🌐  Open site", v -> Ui.openUrl(a, s(p, "html_url"))));
        b.addView(Ui.outlineButton(a, "🔁  Rebuild now", v -> a.run(() -> a.api.send("POST", rp() + "/pages/builds", null), r -> Ui.toast(a, "Build requested"))), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        b.addView(Ui.outlineButton(a, "📜  Latest build", v -> a.run(() -> a.api.getObj(rp() + "/pages/builds/latest"), r -> showText("Latest build", "Status: " + s(r, "status") + "\nCommit: " + s(r, "commit") + "\nDuration: " + r.optInt("duration") + " ms\nUpdated: " + s(r, "updated_at") + (r.isNull("error") || s(o(r, "error"), "message").isEmpty() ? "" : "\n\nError: " + s(o(r, "error"), "message"))))), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        b.addView(Ui.outlineButton(a, "✏  Change source branch / folder", v -> source(p)), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        b.addView(Ui.outlineButton(a, "🔗  Custom domain", v -> Ui.prompt(a, "Custom domain (blank = remove)", "www.example.com", s(p, "cname"), t -> { try { put2(new JSONObject().put("cname", t.trim().isEmpty() ? JSONObject.NULL : t.trim()), "Domain saved"); } catch (Exception e) { } })), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        b.addView(Ui.outlineButton(a, p.optBoolean("https_enforced") ? "🔓  Stop enforcing HTTPS" : "🔒  Enforce HTTPS", v -> { try { put2(new JSONObject().put("https_enforced", !p.optBoolean("https_enforced")), "Saved"); } catch (Exception e) { } }), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        b.addView(Ui.outlineButton(a, "🗑  Disable Pages", v -> Ui.confirmDanger(a, "Disable Pages?", "The site goes offline.", "Disable", () -> a.run(() -> a.api.send("DELETE", rp() + "/pages", null), r -> { Ui.toast(a, "Pages disabled"); refresh(); }))), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(b);
    }

    private void put2(final JSONObject body, final String ok) {
        a.run(() -> a.api.send("PUT", rp() + "/pages", body), r -> { Ui.toast(a, ok); refresh(); });
    }

    private void source(final JSONObject existing) {
        a.run(() -> a.api.branches(a.owner, a.repo), bl -> {
            String[] n = new String[bl.size()];
            int idx = 0;
            for (int i = 0; i < n.length; i++) { n[i] = bl.get(i).name; if (existing != null && n[i].equals(s(o(existing, "source"), "branch"))) idx = i; else if (existing == null && (n[i].equals("main") || n[i].equals("master"))) idx = i; }
            Form.show(a, "Pages source", "🌐", "Save", Form.list(Form.choice("branch", "Branch", n, idx), Form.choice("path", "Folder", new String[]{"/", "/docs"}, existing != null && "/docs".equals(s(o(existing, "source"), "path")) ? 1 : 0)), m -> {
                try {
                    JSONObject body = new JSONObject().put("source", new JSONObject().put("branch", m.get("branch")).put("path", m.get("path")));
                    if (existing == null) a.run(() -> a.api.send("POST", rp() + "/pages", body), r -> { Ui.toast(a, "Pages enabled"); refresh(); });
                    else put2(body, "Source saved");
                } catch (Exception e) { Ui.toast(a, e.getMessage()); }
            });
        });
    }
}
