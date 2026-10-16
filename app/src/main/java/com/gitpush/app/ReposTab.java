package com.gitpush.app;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.gitpush.app.ui.GlassDialog;

import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.ui.Downloader;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.util.ArrayList;
import java.util.List;

class ReposTab {
    final MainActivity a;
    final LinearLayout view;
    private final TextView subtitle, status;
    private final ListView list;
    private final LinearLayout chips;
    private final EditText search;
    private final List<Repo> shown = new ArrayList<>();
    private int filter = 0;
    private final BaseAdapter adapter;

    ReposTab(MainActivity a) {
        this.a = a;
        view = Ui.vbox(a);
        LinearLayout head = Ui.hbox(a);
        head.setPadding(Ui.dp(a, 20), Ui.dp(a, 12), Ui.dp(a, 8), 0);
        LinearLayout titles = Ui.vbox(a);
        titles.addView(Ui.text(a, "Choose a repository", 20, true, R.attr.gpText));
        subtitle = Ui.text(a, "Tap a repo to upload files", 12, false, R.attr.gpPrimary);
        titles.addView(subtitle);
        head.addView(titles, Ui.weight(1f));
        head.addView(Ui.textButton(a, "⟳", R.attr.gpText, v -> { a.getAppState().loadRepos(true, this::render); render(); }));
        head.addView(Ui.textButton(a, "＋", R.attr.gpText, v -> createDialog()));
        view.addView(head);

        search = Ui.input(a, "🔍  Search repositories", "");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int i, int c, int n) { }
            public void onTextChanged(CharSequence s, int i, int b, int c) { }
            public void afterTextChanged(Editable e) { render(); }
        });
        view.addView(search, Ui.lp(a, Ui.MATCH, Ui.WRAP, 20, 8, 20, 4));
        chips = Ui.hbox(a);
        chips.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, 8));
        view.addView(chips);
        status = Ui.muted(a, "");
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(a, 24), Ui.dp(a, 24), Ui.dp(a, 24), 0);
        view.addView(status, Ui.lp(Ui.MATCH, Ui.WRAP));
        list = new ListView(a);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), Ui.dp(a, MainActivity.NAV_SPACE));
        list.setSelector(android.R.color.transparent);
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { View v = row(shown.get(i)); if (cv == null) Ui.fadeIn(v, i); return v; }
        };
        list.setAdapter(adapter);
        view.addView(list, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
    }

    void refresh() {
        AppState st = a.getAppState();
        subtitle.setText(st.sharedBanner ? st.staged.size() + " shared file(s) ready – pick where to push" : "Tap a repo to upload files");
        st.loadRepos(false, this::render);
        render();
    }

    private void renderChips() {
        chips.removeAllViews();
        String[] names = {"All", "Public", "Private"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            chips.addView(Ui.chip(a, names[i], filter == i, v -> { filter = idx; render(); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        }
    }

    private void render() {
        AppState st = a.getAppState();
        renderChips();
        shown.clear();
        String q = search.getText().toString().trim().toLowerCase();
        for (Repo r : st.repos) {
            if (filter == 1 && r.isPrivate) continue;
            if (filter == 2 && !r.isPrivate) continue;
            if (!q.isEmpty() && !r.fullName.toLowerCase().contains(q)) continue;
            shown.add(r);
        }
        List<Repo> favs = new ArrayList<>(), rest = new ArrayList<>();
        for (Repo r : shown) (st.isFavorite(r.fullName) ? favs : rest).add(r);
        shown.clear(); shown.addAll(favs); shown.addAll(rest);
        adapter.notifyDataSetChanged();
        if (st.reposLoading && st.repos.isEmpty()) { status.setText("Loading repositories…"); status.setVisibility(View.VISIBLE); }
        else if (st.reposError != null && st.repos.isEmpty()) { status.setText("Couldn't load repositories\n" + st.reposError); status.setVisibility(View.VISIBLE); }
        else if (shown.isEmpty()) { status.setText("No repositories found.\nTry another search or create one with ＋."); status.setVisibility(View.VISIBLE); }
        else status.setVisibility(View.GONE);
    }

    private View row(Repo r) {
        FrameLayout wrap = new FrameLayout(a);
        wrap.setPadding(0, Ui.dp(a, a.getAppState().compact ? 3 : 6), 0, Ui.dp(a, a.getAppState().compact ? 3 : 6));
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 14), Ui.dp(a, a.getAppState().compact ? 8 : 14), Ui.dp(a, 4), Ui.dp(a, a.getAppState().compact ? 8 : 14));
        LinearLayout h = Ui.hbox(a);
        ImageView ic = Ui.icon(a, r.isPrivate ? R.drawable.ic_lock : R.drawable.ic_public, 22);
        ic.setColorFilter(Ui.color(a, r.isPrivate ? R.attr.gpWarn : R.attr.gpOk));
        h.addView(ic);
        LinearLayout t = Ui.vbox(a);
        t.setPadding(Ui.dp(a, 12), 0, 0, 0);
        TextView n = Ui.title(a, (a.getAppState().isFavorite(r.fullName) ? "⭐ " : "") + r.name);
        n.setSingleLine(true);
        t.addView(n);
        if (r.description != null && !a.getAppState().compact) { TextView d = Ui.muted(a, r.description); d.setSingleLine(true); t.addView(d); }
        StringBuilder meta = new StringBuilder(r.owner);
        if (r.language != null) meta.append(" • ").append(r.language);
        if (!r.canPush) meta.append(" • read-only");
        String when = Ui.agoIso(r.updatedAt);
        if (!when.isEmpty()) meta.append(" • ").append(when);
        TextView m = Ui.text(a, meta.toString(), 11, false, R.attr.gpMuted);
        m.setSingleLine(true);
        t.addView(m);
        h.addView(t, Ui.weight(1f));
        ImageView more = Ui.icon(a, R.drawable.ic_more, 24);
        more.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        more.setLayoutParams(Ui.lp(Ui.dp(a, 44), Ui.dp(a, 44)));
        more.setOnClickListener(v -> menu(v, r));
        h.addView(more);
        c.addView(h);
        c.setOnClickListener(v -> { Ui.haptic(v); UploadActivity.open(a, r.owner, r.name); });
        wrap.addView(c);
        return wrap;
    }

    private void menu(View anchor, final Repo r) {
        final AppState st = a.getAppState();
        final boolean fav = st.isFavorite(r.fullName);
        final String[] items = {"⬆  Upload files", "📂  Browse files", "🕘  Commit history", "⚡  GitHub Actions", "🏷  Releases", "✨  AI README writer",
                fav ? "☆  Remove from favorites" : "⭐  Add to favorites", "✏  Edit repo info", "🏷  Topics", "⬇  Download ZIP", "ℹ  Details & permissions",
                "🔗  Copy clone URL", "🌐  Open on GitHub", "🗑  Delete repository"};
        Ui.pickList(a, r.fullName, items, i -> {
            switch (i) {
                case 0: UploadActivity.open(a, r.owner, r.name); break;
                case 1: st.browserBranch = null; BrowserActivity.open(a, r.owner, r.name); break;
                case 2: st.browserBranch = null; CommitsActivity.open(a, r.owner, r.name); break;
                case 3: ActionsActivity.open(a, r.owner, r.name); break;
                case 4: ReleasesActivity.open(a, r.owner, r.name); break;
                case 5: AiReadmeActivity.open(a, r.owner, r.name); break;
                case 6: st.toggleFavorite(r.fullName); render(); Ui.toast(a, fav ? "Removed from favorites" : "Added to favorites"); break;
                case 7: editInfo(r); break;
                case 8: topics(r); break;
                case 9: Downloader.request(a, st.api(), "/repos/" + r.owner + "/" + r.name + "/zipball/" + r.defaultBranch, r.name + "-" + r.defaultBranch + ".zip"); break;
                case 10: details(r); break;
                case 11: Ui.copy(a, "Clone URL", r.cloneUrl); Ui.toast(a, "Copied"); break;
                case 12: Ui.openUrl(a, r.htmlUrl); break;
                default: deleteRepo(r);
            }
        });
    }

    private void replaceRepo(Repo nr) {
        List<Repo> list = a.getAppState().repos;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == nr.id) list.set(i, nr);
        render();
    }

    private void editInfo(final Repo r) {
        LinearLayout box = Ui.vbox(a);
        final EditText name = Ui.input(a, "Repository name", r.name);
        final EditText desc = Ui.input(a, "Description", r.description == null ? "" : r.description);
        final CheckBox priv = new CheckBox(a);
        priv.setText("Private"); priv.setChecked(r.isPrivate); priv.setTextColor(Ui.color(a, R.attr.gpText));
        name.setSingleLine(true);
        box.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(desc, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        box.addView(priv, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        new GlassDialog(a).setTitle("Edit repository").setIcon("✏").setView(box).setPositiveButton("Save", (d, w) -> {
            final org.json.JSONObject patch = new org.json.JSONObject();
            try { patch.put("name", name.getText().toString().trim().replace(' ', '-')).put("description", desc.getText().toString().trim()).put("private", priv.isChecked()); } catch (Exception ignored) { }
            final com.gitpush.app.data.GitHubApi api = a.getAppState().api();
            Ui.async(() -> api.updateRepo(r.owner, r.name, patch), nr -> { replaceRepo(nr); Ui.toast(a, "Repository updated"); }, e -> Ui.toast(a, e.getMessage()));
        }).setNegativeButton("Cancel", null).show();
    }

    private void topics(final Repo r) {
        final com.gitpush.app.data.GitHubApi api = a.getAppState().api();
        Ui.async(() -> api.topics(r.owner, r.name), list -> {
            StringBuilder sb = new StringBuilder();
            for (String t : list) { if (sb.length() > 0) sb.append(", "); sb.append(t); }
            Ui.prompt(a, "Topics (comma separated)", "android, java, github", sb.toString(), text -> {
                final List<String> names = new ArrayList<>();
                for (String t : text.split(",")) { String n = t.trim().toLowerCase().replace(' ', '-'); if (!n.isEmpty()) names.add(n); }
                Ui.async(() -> { api.setTopics(r.owner, r.name, names); return "ok"; }, x -> Ui.toast(a, "Topics saved"), e -> Ui.toast(a, e.getMessage()));
            });
        }, e -> Ui.toast(a, e.getMessage()));
    }

    private void deleteRepo(final Repo r) {
        LinearLayout box = Ui.vbox(a);
        box.addView(Ui.muted(a, "This permanently deletes " + r.fullName + " with all its files, issues and releases. Needs the delete_repo token scope.\n\nType the repository name to confirm:"));
        final EditText in = Ui.input(a, r.name, "");
        in.setSingleLine(true);
        box.addView(in, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        new GlassDialog(a).setTitle("Delete repository?").setIcon("⚠").setDanger(true).setView(box).setPositiveButton("Delete forever", (d, w) -> {
            if (!in.getText().toString().trim().equals(r.name)) { Ui.toast(a, "Name does not match – nothing deleted"); return; }
            final com.gitpush.app.data.GitHubApi api = a.getAppState().api();
            Ui.async(() -> { api.deleteRepo(r.owner, r.name); return "ok"; }, x -> {
                List<Repo> list = a.getAppState().repos;
                for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id == r.id) list.remove(i);
                render(); Ui.toast(a, "Repository deleted");
            }, e -> Ui.toast(a, e.getMessage()));
        }).setNegativeButton("Cancel", null).show();
    }

    private void details(Repo r) {
        String scopes = a.getAppState().active == null ? "" : a.getAppState().active.scopes;
        String msg = (r.description == null ? "No description" : r.description)
                + "\n\nVisibility: " + (r.isPrivate ? "Private" : "Public")
                + "\nDefault branch: " + r.defaultBranch
                + "\nSize: " + PathUtils.formatSize(r.sizeKb * 1024L)
                + "\nLanguage: " + (r.language == null ? "–" : r.language) + "   ★ " + r.stars
                + "\n\nYour permission: " + (r.isAdmin ? "Admin" : r.canPush ? "Write (can push)" : "Read only – pushing will fail")
                + "\nToken scopes: " + (scopes.isEmpty() ? "fine-grained / not reported" : scopes);
        new GlassDialog(a).setTitle(r.fullName).setMessage(msg).setPositiveButton("Close", null).show();
    }

    private void createDialog() {
        LinearLayout box = Ui.vbox(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), 0);
        final EditText name = Ui.input(a, "Repository name", "");
        final EditText desc = Ui.input(a, "Description (optional)", "");
        final CheckBox priv = new CheckBox(a);
        priv.setText("Private"); priv.setChecked(true); priv.setTextColor(Ui.color(a, R.attr.gpText));
        box.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(desc, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        box.addView(priv);
        new GlassDialog(a).setTitle("New repository").setView(box)
                .setPositiveButton("Create", (d, w) -> {
                    String n = name.getText().toString().trim().replace(' ', '-');
                    if (n.isEmpty()) { Ui.toast(a, "Enter a name"); return; }
                    final AppState st = a.getAppState();
                    final com.gitpush.app.data.GitHubApi api = st.api();
                    GitPushApp.get().io.execute(() -> {
                        Repo created = null; String err = null;
                        try { created = api.createRepo(n, desc.getText().toString().trim(), priv.isChecked()); } catch (Exception e) { err = e.getMessage(); }
                        final Repo fc = created; final String fe = err;
                        GitPushApp.get().main.post(() -> {
                            if (fc != null) { st.repos.add(0, fc); Ui.toast(a, "Repository " + fc.name + " created"); render(); }
                            else Ui.toast(a, fe == null ? "Could not create repository" : fe);
                        });
                    });
                }).setNegativeButton("Cancel", null).show();
    }
}
