package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Release;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.ReleaseOrder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Release Order Studio: put any release first, last or anywhere in between.
 * GitHub orders releases by the date of the tag's commit, so applying a new order gives each tag a small "date anchor"
 * commit (same files, only the date differs) and moves the tag onto it. Nothing in your code changes, assets and notes stay.
 * The previous tag targets are saved, so the last reorder can be undone.
 */
public class ReleaseOrderActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, ReleaseOrderActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private static final class Item {
        Release r; String refSha = "", commitSha = ""; JSONObject commit; long date; boolean ready; String problem;
    }

    private String owner, repo;
    private GitHubApi api;
    private final List<Item> original = new ArrayList<>(), order = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView status;
    private TextView applyBtn;
    private boolean loading = true;
    private int drafts;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        buildScreen("Release order", owner + "/" + repo, true);
        addAction(R.drawable.i_history, v -> undoDialog());
        addAction(R.drawable.i_more, v -> sortMenu());
        LinearLayout page = Ui.vbox(this);
        status = Ui.muted(this, "Loading releases…");
        status.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 4));
        page.addView(status);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 12));
        adapter = new BaseAdapter() {
            public int getCount() { return order.size(); }
            public Object getItem(int i) { return order.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { return row(i); }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        applyBtn = Ui.button(this, "✔  Apply new order", v -> confirmApply());
        page.addView(applyBtn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 16, 4, 16, 14));
        body.addView(page);
        load();
    }

    // ------------------------------------------------------------------ loading
    private void load() {
        loading = true; status.setText("Loading releases…"); refreshUi();
        async(() -> {
            List<Release> rels = api.releases(owner, repo);
            final List<Item> items = new ArrayList<>();
            int dr = 0;
            for (Release r : rels) { if (r.draft) { dr++; continue; } Item it = new Item(); it.r = r; items.add(it); }
            drafts = dr;
            ExecutorService pool = Executors.newFixedThreadPool(4);
            List<Future<?>> fs = new ArrayList<>();
            final int[] done = {0};
            for (final Item it : items) fs.add(pool.submit(() -> {
                try {
                    String[] ref = api.tagRef(owner, repo, it.r.tag);
                    if (ref == null) { it.problem = "tag missing"; }
                    else {
                        it.refSha = ref[0];
                        it.commitSha = "commit".equals(ref[1]) ? ref[0] : api.tagCommit(owner, repo, it.r.tag);
                        it.commit = api.gitCommit(owner, repo, it.commitSha);
                        JSONObject c = it.commit.optJSONObject("committer");
                        it.date = c == null ? 0 : Ui.isoMs(c.optString("date"));
                        it.ready = it.date > 0;
                        if (!it.ready) it.problem = "no date";
                    }
                } catch (Exception e) { it.problem = e.getMessage() == null ? "error" : "error"; }
                synchronized (done) { done[0]++; final int n = done[0]; app.main.post(() -> { if (alive()) status.setText("Reading tag dates " + n + " / " + items.size() + "…"); }); }
            }));
            for (Future<?> f : fs) { try { f.get(); } catch (Exception ignored) { } }
            pool.shutdown();
            return items;
        }, items -> {
            loading = false;
            original.clear(); original.addAll(items);
            order.clear(); order.addAll(items);
            refreshUi();
        }, e -> { loading = false; status.setText("Couldn't load releases\n" + msg(e)); });
    }

    private int changedCount() {
        int n = 0;
        for (int i = 0; i < order.size(); i++) if (order.get(i) != original.get(i)) n++;
        return n;
    }

    private void refreshUi() {
        adapter.notifyDataSetChanged();
        int ch = changedCount();
        if (!loading) {
            String s = order.size() + " releases" + (drafts > 0 ? " • " + drafts + " draft(s) ignored" : "");
            status.setText(order.isEmpty() ? "No published releases to reorder." : s + "\nTap a release for moves, or use ▲ ▼. Position 1 is the top of the list (and gets the “Latest” badge).");
        }
        applyBtn.setText(ch == 0 ? "Nothing changed yet" : "Apply new order  (" + ch + " moved)");
        applyBtn.setAlpha(ch == 0 || loading ? 0.4f : 1f);
        applyBtn.setEnabled(ch != 0 && !loading);
    }

    // ------------------------------------------------------------------ rows
    private View row(final int pos) {
        final Item it = order.get(pos);
        LinearLayout wrap = Ui.vbox(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        TextView no = Ui.label(this, String.valueOf(pos + 1));
        no.setTextSize(15);
        no.setGravity(Gravity.CENTER);
        no.setBackgroundResource(R.drawable.bg_icon_tile);
        h.addView(no, Ui.lp(this, Ui.dp(this, 38), Ui.dp(this, 38), 0, 0, 12, 0));
        LinearLayout mid = Ui.vbox(this);
        TextView t = Ui.title(this, it.r.title());
        t.setTextSize(14);
        t.setMaxLines(2);
        mid.addView(t);
        String when = it.date > 0 ? java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new java.util.Date(it.date)) : "–";
        mid.addView(Ui.text(this, "🏷 " + it.r.tag + "  •  tag date " + when + (it.problem != null ? "  •  " + it.problem : ""), 11, false, R.attr.gpMuted));
        int was = original.indexOf(it);
        if (was != pos) mid.addView(Ui.pill(this, "was #" + (was + 1), R.attr.gpText), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        h.addView(mid, Ui.weight(1f));
        LinearLayout arrows = Ui.vbox(this);
        arrows.addView(arrow("▲", v -> { ReleaseOrder.move(order, pos, pos - 1); refreshUi(); }), Ui.lp(Ui.dp(this, 40), Ui.dp(this, 34)));
        arrows.addView(arrow("▼", v -> { ReleaseOrder.move(order, pos, pos + 1); refreshUi(); }), Ui.lp(Ui.dp(this, 40), Ui.dp(this, 34)));
        h.addView(arrows);
        c.addView(h);
        c.setOnClickListener(v -> itemMenu(pos));
        wrap.addView(c);
        return wrap;
    }

    private TextView arrow(String s, View.OnClickListener l) {
        TextView t = Ui.text(this, s, 13, true, R.attr.gpText);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_chip_off);
        t.setOnClickListener(v -> { Ui.haptic(v); l.onClick(v); });
        return t;
    }

    private void itemMenu(final int pos) {
        final Item it = order.get(pos);
        final String[] items = {"⏫  Move to first", "⏬  Move to last", "🔢  Move to position…", "🔁  Swap with another release…", "🌐  Open on GitHub"};
        Ui.pickList(this, it.r.title(), items, i -> {
            if (i == 0) { ReleaseOrder.move(order, pos, 0); refreshUi(); }
            else if (i == 1) { ReleaseOrder.move(order, pos, order.size() - 1); refreshUi(); }
            else if (i == 2) Ui.prompt(this, "Move to position (1–" + order.size() + ")", "e.g. 2", String.valueOf(pos + 1), s -> {
                try { ReleaseOrder.move(order, pos, Integer.parseInt(s.trim()) - 1); refreshUi(); } catch (NumberFormatException e) { Ui.toast(this, "Enter a number"); }
            });
            else if (i == 3) {
                String[] names = new String[order.size()];
                for (int k = 0; k < names.length; k++) names[k] = (k + 1) + ".  " + order.get(k).r.title();
                Ui.pickList(this, "Swap with", names, k -> { java.util.Collections.swap(order, pos, k); refreshUi(); });
            } else Ui.openUrl(this, it.r.htmlUrl);
        });
    }

    private void sortMenu() {
        final String[] items = {"🔁  Reverse the whole list", "🔢  Version: newest first", "🔢  Version: oldest first", "📝  Title A → Z", "📅  Published: newest first", "📅  Published: oldest first", "🧹  Reset to GitHub's order"};
        Ui.pickList(this, "Arrange", items, i -> {
            Comparator<Item> byVer = (a, b) -> ReleaseOrder.compareVersions(a.r.tag, b.r.tag);
            if (i == 0) ReleaseOrder.reverse(order);
            else if (i == 1) java.util.Collections.sort(order, (a, b) -> byVer.compare(b, a));
            else if (i == 2) java.util.Collections.sort(order, byVer);
            else if (i == 3) java.util.Collections.sort(order, (a, b) -> a.r.title().compareToIgnoreCase(b.r.title()));
            else if (i == 4) java.util.Collections.sort(order, (a, b) -> Long.compare(Ui.isoMs(b.r.publishedAt), Ui.isoMs(a.r.publishedAt)));
            else if (i == 5) java.util.Collections.sort(order, (a, b) -> Long.compare(Ui.isoMs(a.r.publishedAt), Ui.isoMs(b.r.publishedAt)));
            else { order.clear(); order.addAll(original); }
            refreshUi();
        });
    }

    // ------------------------------------------------------------------ apply
    private void confirmApply() {
        for (Item it : order) if (!it.ready) { Ui.toast(this, "\"" + it.r.tag + "\" has no readable tag commit (" + it.problem + "). Fix or delete that release first."); return; }
        int n = order.size();
        long[] old = new long[n];
        for (int i = 0; i < n; i++) old[i] = order.get(i).date;
        long[] allOld = new long[n];
        for (int i = 0; i < n; i++) allOld[i] = original.get(i).date;
        long[] slots = ReleaseOrder.slots(allOld);
        List<Integer> ch = ReleaseOrder.changes(slots, old);
        if (ch.isEmpty()) { Ui.toast(this, "GitHub already shows this order"); return; }
        String msg = ch.size() + " tag(s) get a new date anchor commit (same files, new date) so GitHub lists the releases in your order.\n\n"
                + "• Your code, release notes and assets are not touched.\n• Annotated tags become lightweight tags.\n• You can undo this from the history icon.\n• GitHub can take a minute to show the new order.";
        new GlassDialog(this).setTitle("Apply new order?").setIcon("🔁").setMessage(msg)
                .setPositiveButton("Apply", (d, w) -> apply(slots, ch)).setNegativeButton("Cancel", null).show();
    }

    private void apply(final long[] slots, final List<Integer> ch) {
        final ProgressDlg dlg = new ProgressDlg(this, "Reordering releases", "🔁", () -> api.abort());
        KeepAliveService.begin(app, "Reordering releases of " + repo);
        final List<Item> plan = new ArrayList<>(order);
        async(() -> {
            JSONArray undo = new JSONArray();
            int done = 0;
            for (int pos : ch) {
                final Item it = plan.get(pos);
                final int fd = ++done;
                app.main.post(() -> dlg.set(fd + " / " + ch.size() + "  •  " + it.r.tag, (float) fd / ch.size()));
                undo.put(new JSONObject().put("tag", it.r.tag).put("sha", it.refSha));
                String sha = api.createDatedCommit(owner, repo, it.commit, it.commitSha, "Re-date tag " + it.r.tag + " (GitPush release order)", ReleaseOrder.iso(slots[pos]));
                api.setTag(owner, repo, it.r.tag, sha);
            }
            for (Item it : plan) if (!it.r.prerelease) { try { api.makeLatest(owner, repo, it.r.id); } catch (Exception ignored) { } break; }
            st.store.prefs.edit().putString("release_undo:" + owner + "/" + repo, undo.toString()).apply();
            return ch.size();
        }, n -> { KeepAliveService.end(app); dlg.close(); Ui.toast(this, "Done – " + n + " tag(s) re-dated"); load(); },
                e -> { KeepAliveService.end(app); dlg.close(); Ui.toast(this, msg(e)); load(); });
    }

    private void undoDialog() {
        final String raw = st.store.prefs.getString("release_undo:" + owner + "/" + repo, "");
        if (raw.isEmpty()) { Ui.toast(this, "No reorder to undo for this repository"); return; }
        Ui.confirm(this, "Undo the last reorder?", "Every tag that was moved goes back to where it pointed before.", "Undo", () -> {
            final ProgressDlg dlg = new ProgressDlg(this, "Restoring tags", "🔁", null);
            async(() -> {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) { JSONObject o = a.getJSONObject(i); api.setTag(owner, repo, o.getString("tag"), o.getString("sha")); }
                st.store.prefs.edit().remove("release_undo:" + owner + "/" + repo).apply();
                return a.length();
            }, n -> { dlg.close(); Ui.toast(this, n + " tag(s) restored"); load(); }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
        });
    }
}
