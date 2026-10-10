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
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.gitpush.app.data.Models.Repo;
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
            public View getView(int i, View cv, ViewGroup p) { return row(shown.get(i)); }
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
        TextView n = Ui.title(a, r.name);
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

    private void menu(View anchor, Repo r) {
        PopupMenu pm = new PopupMenu(a, anchor);
        String[] items = {"Upload files", "Browse files", "Commit history", "Details & permissions", "Copy clone URL", "Open on GitHub"};
        for (int i = 0; i < items.length; i++) pm.getMenu().add(0, i, i, items[i]);
        pm.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 0: UploadActivity.open(a, r.owner, r.name); break;
                case 1: a.getAppState().browserBranch = null; BrowserActivity.open(a, r.owner, r.name); break;
                case 2: a.getAppState().browserBranch = null; CommitsActivity.open(a, r.owner, r.name); break;
                case 3: details(r); break;
                case 4: Ui.copy(a, "Clone URL", r.cloneUrl); Ui.toast(a, "Copied"); break;
                default: Ui.openUrl(a, r.htmlUrl);
            }
            return true;
        });
        pm.show();
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
        new AlertDialog.Builder(a).setTitle(r.fullName).setMessage(msg).setPositiveButton("Close", null).show();
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
        new AlertDialog.Builder(a).setTitle("New repository").setView(box)
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
