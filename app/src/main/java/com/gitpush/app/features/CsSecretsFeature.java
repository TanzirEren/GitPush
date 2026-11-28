package com.gitpush.app.features;

import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.gitpush.app.data.CodespacesApi;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Codespaces secrets: account-level (with a choice of repositories) or for one repository. Values are sealed on the device; GitHub never returns them. */
public class CsSecretsFeature extends Feature {
    @Override public String title() { return "Codespaces secrets"; }
    @Override public String icon() { return "🔑"; }
    @Override public String blurb() { return "Secrets available inside your codespaces"; }
    @Override public boolean canAdd() { return true; }
    @Override public boolean needsRepo() { return false; }
    @Override public String empty() { return "No Codespaces secrets yet. Tap ＋ to add one."; }

    private boolean user() { return a.repo == null || a.repo.isEmpty(); }
    private CodespacesApi api() { return new CodespacesApi(a.api); }

    @Override public List<Row> load() throws Exception {
        JSONArray arr = api().secrets(a.owner, a.repo);
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            String vis = s(j, "visibility");
            out.add(new Row(s(j, "name"), "updated " + ago(s(j, "updated_at")) + (user() && !vis.isEmpty() ? "  ·  " + visLabel(vis) : "") + "\nValue is hidden (GitHub never shows it)", user() ? vis : "repo", j));
        }
        return out;
    }

    private static String visLabel(String v) { return "all".equals(v) ? "all repositories" : "private".equals(v) ? "no repositories" : "selected repositories"; }

    @Override public void onAdd() {
        Form.show(a, user() ? "New account secret" : "New repository secret", "🔑", user() ? "Next" : "Save", Form.list(Form.text("n", "Name (UPPER_SNAKE_CASE)", "").plain(), Form.secret("v", "Value")), m -> {
            final String name = m.get("n").toUpperCase().replaceAll("[^A-Z0-9_]", "_");
            if (name.isEmpty() || m.get("v").isEmpty()) { Ui.toast(a, "Name and value are required"); return; }
            if (name.startsWith("GITHUB_")) { Ui.toast(a, "Names can't start with GITHUB_"); return; }
            if (!user()) { save(name, m.get("v"), null); return; }
            pickRepos(new HashSet<Long>(), ids -> save(name, m.get("v"), ids));
        });
    }

    private void save(final String name, final String value, final long[] ids) {
        a.act(() -> { api().putSecret(user() ? "" : a.owner, a.repo, name, value, ids); return ""; }, "Secret saved");
    }

    @Override public void onRow(final Row r) {
        final String name = r.title;
        final String[] items = user() ? new String[]{"✏  Update value", "📂  Choose repositories", "🗑  Delete"} : new String[]{"✏  Update value", "🗑  Delete"};
        Ui.pickList(a, name, items, i -> {
            if (i == 0) Form.show(a, "Update " + name, "🔑", "Save", Form.list(Form.secret("v", "New value")), m -> {
                if (m.get("v").isEmpty()) return;
                if (!user()) { save(name, m.get("v"), null); return; }
                a.run(() -> api().secretRepos(name), arr -> {
                    Set<Long> cur = new HashSet<>();
                    for (int k = 0; k < arr.length(); k++) cur.add(arr.optJSONObject(k).optLong("id"));
                    long[] ids = new long[cur.size()];
                    int n = 0;
                    for (long id : cur) ids[n++] = id;
                    save(name, m.get("v"), ids);
                });
            });
            else if (user() && i == 1) a.run(() -> api().secretRepos(name), arr -> {
                Set<Long> cur = new HashSet<>();
                for (int k = 0; k < arr.length(); k++) cur.add(arr.optJSONObject(k).optLong("id"));
                pickRepos(cur, ids -> a.act(() -> { api().setSecretRepos(name, ids); return ""; }, "Repositories updated"));
            });
            else Ui.confirmDanger(a, "Delete secret?", name + " is removed from all codespaces that use it.", "Delete", () -> a.act(() -> { api().deleteSecret(user() ? "" : a.owner, a.repo, name); return ""; }, "Deleted"));
        });
    }

    /** Checkbox list of the user's repositories. */
    private void pickRepos(final Set<Long> initial, final Ui.Cb<long[]> done) {
        final List<Repo> repos = a.st().repos;
        if (repos.isEmpty()) { Ui.toast(a, "Open the Repos tab once so repositories are loaded"); return; }
        final Set<Long> sel = new HashSet<>(initial);
        LinearLayout box = Ui.vbox(a);
        box.addView(Ui.muted(a, "Which repositories may read this secret in their codespaces? None selected = private (no repositories)."));
        for (final Repo r : repos) {
            final android.widget.TextView c = Ui.chip(a, (sel.contains(r.id) ? "✔ " : "☐ ") + r.fullName, sel.contains(r.id), null);
            c.setOnClickListener(v -> {
                if (!sel.remove(r.id)) sel.add(r.id);
                boolean on = sel.contains(r.id);
                c.setText((on ? "✔ " : "☐ ") + r.fullName);
                c.setBackgroundResource(on ? com.gitpush.app.R.drawable.bg_chip_on : com.gitpush.app.R.drawable.bg_chip_off);
                c.setTextColor(Ui.color(a, on ? com.gitpush.app.R.attr.gpOnPrimary : com.gitpush.app.R.attr.gpText));
            });
            box.addView(c, Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 6, 0, 0));
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        LinearLayout wrap = Ui.vbox(a);
        wrap.addView(sv, Ui.lp(Ui.MATCH, Ui.dp(a, 360)));
        new GlassDialog(a).setTitle("Repository access").setIcon("📂").setView(wrap).setPositiveButton("Save", (d, w) -> {
            long[] ids = new long[sel.size()];
            int n = 0;
            for (long id : sel) ids[n++] = id;
            done.on(ids);
        }).setNegativeButton("Cancel", null).show();
    }
}
