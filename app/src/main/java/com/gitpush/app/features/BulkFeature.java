package com.gitpush.app.features;

import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.gitpush.app.R;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Bulk repository actions: select many repos, then archive, change visibility, edit topics, star, or delete. */
public class BulkFeature extends Feature {
    private final List<Repo> repos = new ArrayList<>();
    private final List<CheckBox> boxes = new ArrayList<>();
    private LinearLayout list;

    @Override public String title() { return "Bulk repo actions"; }
    @Override public String icon() { return "🗂"; }
    @Override public String blurb() { return "Archive, visibility, topics, star, delete – many at once"; }
    @Override public boolean needsRepo() { return false; }

    @Override public View view() {
        LinearLayout page = Ui.vbox(a);
        final EditText filter = Ui.input(a, "Filter repositories", "");
        filter.setSingleLine(true);
        page.addView(filter, Ui.lp(a, Ui.MATCH, Ui.WRAP, 16, 10, 16, 4));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout acts = Ui.hbox(a);
        acts.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 4));
        String[] labels = {"☑ All", "☐ None", "📦 Archive", "📤 Unarchive", "🔒 Private", "🌍 Public", "🏷 Add topic", "➖ Remove topic", "⭐ Star", "☆ Unstar", "🗑 Delete"};
        for (int i = 0; i < labels.length; i++) { final int k = i; acts.addView(Ui.chip(a, labels[i], false, v -> action(k)), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0)); }
        hs.addView(acts);
        page.addView(hs);
        ScrollView sv = new ScrollView(a);
        list = Ui.vbox(a);
        list.setPadding(Ui.dp(a, 16), Ui.dp(a, 4), Ui.dp(a, 16), Ui.dp(a, 24));
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        filter.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(android.text.Editable e) {
                String q = e.toString().toLowerCase();
                for (int i = 0; i < boxes.size(); i++) boxes.get(i).setVisibility(q.isEmpty() || repos.get(i).fullName.toLowerCase().contains(q) ? View.VISIBLE : View.GONE);
            }
        });
        if (a.st().repos.isEmpty()) { list.addView(Ui.muted(a, "Loading repositories…")); a.st().loadRepos(true, this::fill); } else fill();
        return page;
    }

    private void fill() {
        list.removeAllViews(); repos.clear(); boxes.clear();
        for (Repo r : a.st().repos) {
            CheckBox cb = new CheckBox(a);
            cb.setText((r.isPrivate ? "🔒 " : "") + (r.archived ? "📦 " : "") + r.fullName);
            cb.setTextColor(Ui.color(a, R.attr.gpText));
            list.addView(cb);
            repos.add(r); boxes.add(cb);
        }
        if (repos.isEmpty()) list.addView(Ui.muted(a, "No repositories."));
    }

    private List<Repo> selected() {
        List<Repo> out = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isChecked() && boxes.get(i).getVisibility() == View.VISIBLE) out.add(repos.get(i));
        return out;
    }

    private void action(final int k) {
        if (k == 0) { for (CheckBox c : boxes) if (c.getVisibility() == View.VISIBLE) c.setChecked(true); return; }
        if (k == 1) { for (CheckBox c : boxes) c.setChecked(false); return; }
        final List<Repo> sel = selected();
        if (sel.isEmpty()) { Ui.toast(a, "Select repositories first"); return; }
        final String[] names = {"", "", "Archive", "Unarchive", "Make private", "Make public", "Add topic", "Remove topic", "Star", "Unstar", "Delete"};
        if (k == 6 || k == 7) { Ui.prompt(a, names[k] + " (" + sel.size() + " repos)", "android", "", t -> { if (!t.trim().isEmpty()) run(k, sel, t.trim().toLowerCase().replace(' ', '-')); }); return; }
        if (k == 10) { Ui.prompt(a, "Type DELETE to remove " + sel.size() + " repositories forever", "DELETE", "", t -> { if (t.equals("DELETE")) run(k, sel, ""); else Ui.toast(a, "Not confirmed"); }); return; }
        if (k == 5 || k == 4) { Ui.confirm(a, names[k] + " – " + sel.size() + " repos?", k == 5 ? "Their code becomes visible to everyone." : "Only you and collaborators will see them.", "Continue", () -> run(k, sel, "")); return; }
        run(k, sel, "");
    }

    private void run(final int k, final List<Repo> sel, final String arg) {
        final ProgressDlg dlg = new ProgressDlg(a, "Working", "🗂", () -> a.api.abort());
        a.async2(() -> {
            int ok = 0; StringBuilder bad = new StringBuilder();
            int i = 0;
            for (Repo r : sel) {
                final int fi = ++i; final String nm = r.fullName;
                com.gitpush.app.GitPushApp.get().main.post(() -> dlg.set(fi + " / " + sel.size() + "  •  " + nm, (float) fi / sel.size()));
                String u = "/repos/" + GitHubApi.enc2(r.owner) + "/" + GitHubApi.enc2(r.name);
                try {
                    if (k == 2) a.api.send("PATCH", u, new JSONObject().put("archived", true));
                    else if (k == 3) a.api.send("PATCH", u, new JSONObject().put("archived", false));
                    else if (k == 4) a.api.send("PATCH", u, new JSONObject().put("private", true));
                    else if (k == 5) a.api.send("PATCH", u, new JSONObject().put("private", false));
                    else if (k == 6 || k == 7) {
                        JSONArray cur = a.api.getObj(u + "/topics").optJSONArray("names");
                        List<String> names = new ArrayList<>();
                        if (cur != null) for (int x = 0; x < cur.length(); x++) names.add(cur.optString(x));
                        if (k == 6 && !names.contains(arg)) names.add(arg);
                        if (k == 7) names.remove(arg);
                        a.api.send("PUT", u + "/topics", new JSONObject().put("names", new JSONArray(names)));
                    }
                    else if (k == 8) a.api.send("PUT", "/user/starred/" + r.fullName, null);
                    else if (k == 9) a.api.send("DELETE", "/user/starred/" + r.fullName, null);
                    else a.api.send("DELETE", u, null);
                    ok++;
                } catch (Exception e) { bad.append("✘ ").append(r.fullName).append(": ").append(e.getMessage()).append('\n'); }
            }
            return ok + " of " + sel.size() + " done" + (bad.length() > 0 ? "\n\n" + bad : "");
        }, text -> { dlg.close(); a.st().loadRepos(true, this::fill); showText("Result", text.toString()); }, e -> { dlg.close(); Ui.toast(a, a.err(e)); });
    }
}
