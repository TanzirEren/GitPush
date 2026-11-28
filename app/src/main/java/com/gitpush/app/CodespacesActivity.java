package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.CodespacesApi;
import com.gitpush.app.data.CsAi;
import com.gitpush.app.data.AiClient;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.ContentItem;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.data.Snippets;
import com.gitpush.app.ui.AiSettings;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.CsIdeView;
import com.gitpush.app.ui.Form;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.RepoPicker;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.CsUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Codespaces hub: list, create (guided / quick / from PR), start, stop, rename, machine, export, publish, bulk, ports, secrets, org. */
public class CodespacesActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, CodespacesActivity.class).putExtra("owner", owner == null ? "" : owner).putExtra("repo", repo == null ? "" : repo));
    }

    private static final String[] FILTERS = {"All", "Running", "Stopped", "Unsaved"};

    private CodespacesApi cs;
    private String fOwner = "", fRepo = "";
    private final List<JSONObject> all = new ArrayList<>();
    private int filter = 0;
    private EditText search;
    private LinearLayout list, chips, bulkBar, resume;
    private TextView status, summary;
    private boolean selecting, split, loading;
    private final Set<String> picked = new HashSet<>();
    private CsIdeView ide;
    private TextView paneTitle;
    private int refreshToken = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        fOwner = getIntent().getStringExtra("owner");
        fRepo = getIntent().getStringExtra("repo");
        cs = new CodespacesApi(st.api());
        buildScreen("Codespaces", fRepo.isEmpty() ? "Your account" : fOwner + "/" + fRepo, true);
        addAction(R.drawable.i_refresh, v -> load());
        addAction(R.drawable.i_plus, v -> createMenu());
        addAction(R.drawable.i_more, v -> moreMenu());

        int wdp = Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        split = wdp >= 840;
        LinearLayout left = buildList();
        if (split) {
            ((View) body.getParent()).setPadding(0, 0, 0, 0);   // full width for the split view
            LinearLayout row = Ui.hbox(this);
            row.addView(left, new LinearLayout.LayoutParams(Ui.dp(this, 380), Ui.MATCH));
            View div = new View(this);
            div.setBackgroundColor(Ui.color(this, R.attr.gpOutline));
            row.addView(div, new LinearLayout.LayoutParams(1, Ui.MATCH));
            LinearLayout right = Ui.vbox(this);
            paneTitle = Ui.muted(this, "Select a codespace and tap Open");
            paneTitle.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            right.addView(paneTitle);
            ide = new CsIdeView(this, new CsIdeView.Callbacks() {
                @Override public void onTitle(String t, String s) { paneTitle.setText(t + (s.isEmpty() ? "" : "  ·  " + s)); }
                @Override public void onReady(String n) { }
            });
            ide.setVisibility(View.GONE);
            right.addView(ide, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
            row.addView(right, new LinearLayout.LayoutParams(0, Ui.MATCH, 1f));
            body.addView(row, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            addAction(R.drawable.i_terminal, v -> { if (ide.name().isEmpty()) Ui.toast(this, "Open a codespace first"); else ide.togglePad(); });
        } else body.addView(left, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        load();
    }

    private LinearLayout buildList() {
        LinearLayout page = Ui.vbox(this);
        summary = Ui.muted(this, "");
        summary.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), 0);
        page.addView(summary);
        resume = Ui.vbox(this);
        page.addView(resume);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chips = Ui.hbox(this);
        chips.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), 0);
        hs.addView(chips);
        page.addView(hs);
        renderChips();
        search = Ui.input(this, "Search codespaces", "");
        search.setSingleLine(true);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(android.text.Editable e) { render(); }
        });
        page.addView(search, Ui.lp(this, Ui.MATCH, Ui.WRAP, 16, 8, 16, 2));
        status = Ui.muted(this, "Loading…");
        status.setPadding(Ui.dp(this, 24), Ui.dp(this, 10), Ui.dp(this, 24), 0);
        page.addView(status);
        ScrollView sv = new ScrollView(this);
        list = Ui.vbox(this);
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 24));
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        bulkBar = Ui.hbox(this);
        bulkBar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        bulkBar.setBackgroundResource(R.drawable.bg_glass);
        bulkBar.setVisibility(View.GONE);
        page.addView(bulkBar, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        return page;
    }

    private void renderChips() {
        chips.removeAllViews();
        for (int i = 0; i < FILTERS.length; i++) {
            final int k = i;
            chips.addView(Ui.chip(this, FILTERS[i], filter == i, v -> { filter = k; renderChips(); render(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        }
    }

    // ------------------------------------------------------------------------------------ data
    private void load() {
        loading = true;
        status.setText("Loading…");
        status.setVisibility(View.VISIBLE);
        final int token = ++refreshToken;
        async(() -> fRepo.isEmpty() ? cs.list() : cs.listForRepo(fOwner, fRepo), arr -> {
            loading = false;
            all.clear();
            for (int i = 0; i < arr.length(); i++) { JSONObject j = arr.optJSONObject(i); if (j != null) all.add(j); }
            Collections.sort(all, new Comparator<JSONObject>() {
                @Override public int compare(JSONObject x, JSONObject y) {
                    int r = CsUtil.stateRank(x.optString("state")) - CsUtil.stateRank(y.optString("state"));
                    return r != 0 ? r : y.optString("last_used_at").compareTo(x.optString("last_used_at"));
                }
            });
            render();
            if (token == refreshToken) scheduleRefresh(token);
        }, e -> {
            loading = false;
            all.clear();
            render();
            String m = msg(e);
            status.setVisibility(View.VISIBLE);
            status.setText("Couldn't load codespaces\n" + m + (m.contains("403") || m.toLowerCase().contains("scope") || m.contains("404") ? "\n\nYour token needs the “codespace” scope. Sign in again (Profile → Log out → Sign in) or create a new token with the codespace scope." : ""));
        });
    }

    private void scheduleRefresh(final int token) {
        boolean busy = false;
        for (JSONObject j : all) if (CsUtil.transitional(j.optString("state"))) busy = true;
        if (!busy) return;
        app.main.postDelayed(() -> { if (alive() && token == refreshToken && !loading) quietReload(token); }, 5000);
    }

    private void quietReload(final int token) {
        async(() -> fRepo.isEmpty() ? cs.list() : cs.listForRepo(fOwner, fRepo), arr -> {
            if (token != refreshToken) return;
            all.clear();
            for (int i = 0; i < arr.length(); i++) { JSONObject j = arr.optJSONObject(i); if (j != null) all.add(j); }
            render();
            scheduleRefresh(token);
        }, e -> { });
    }

    private void render() {
        String q = search.getText().toString().trim().toLowerCase();
        list.removeAllViews();
        int running = 0, cores = 0;
        for (JSONObject j : all) if (CsUtil.running(j.optString("state"))) { running++; cores += o(j, "machine").optInt("cpus"); }
        summary.setText(all.size() + " codespace" + (all.size() == 1 ? "" : "s") + (running > 0 ? "  ·  " + running + " running (" + CsUtil.coreHourRate(cores) + ")" : ""));
        renderResume();
        int shown = 0;
        for (final JSONObject j : all) {
            String state = j.optString("state");
            JSONObject git = o(j, "git_status");
            boolean dirty = git.optBoolean("has_uncommitted_changes") || git.optBoolean("has_unpushed_changes");
            if (filter == 1 && !CsUtil.running(state)) continue;
            if (filter == 2 && CsUtil.running(state)) continue;
            if (filter == 3 && !dirty) continue;
            String hay = (j.optString("display_name") + " " + j.optString("name") + " " + o(j, "repository").optString("full_name") + " " + git.optString("ref")).toLowerCase();
            if (!q.isEmpty() && !hay.contains(q)) continue;
            list.addView(card(j), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
            shown++;
        }
        status.setVisibility(shown == 0 && !loading ? View.VISIBLE : View.GONE);
        if (shown == 0 && !loading) status.setText(all.isEmpty() ? "No codespaces yet. Tap ＋ to create one." : "Nothing matches.");
        updateBulk();
    }

    private void renderResume() {
        resume.removeAllViews();
        String last = st.store.prefs.getString("cs_last", "");
        if (last.isEmpty() || selecting) return;
        for (final JSONObject j : all) {
            if (!last.equals(j.optString("name"))) continue;
            LinearLayout c = Ui.card(this);
            c.addView(Ui.label(this, "Resume"));
            c.addView(Ui.title(this, displayName(j)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 2, 0, 0));
            c.addView(Ui.muted(this, CsUtil.stateLabel(j.optString("state")) + " · tap to continue where you left off"));
            c.setOnClickListener(v -> openIde(j));
            resume.addView(c, Ui.lp(this, Ui.MATCH, Ui.WRAP, 16, 10, 16, 0));
            return;
        }
    }

    private static String displayName(JSONObject j) { String d = j.optString("display_name"); return d.isEmpty() ? j.optString("name") : d; }
    private static JSONObject o(JSONObject j, String k) { JSONObject r = j == null ? null : j.optJSONObject(k); return r == null ? new JSONObject() : r; }

    private View card(final JSONObject j) {
        final String name = j.optString("name"), state = j.optString("state");
        final JSONObject git = o(j, "git_status"), mach = o(j, "machine");
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        if (selecting) {
            TextView cb = Ui.text(this, picked.contains(name) ? "✔" : "☐", 16, true, R.attr.gpText);
            Ui.setTextWithIcon(cb, picked.contains(name) ? "✔" : "☐", 18);
            h.addView(cb, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        }
        TextView t = Ui.title(this, displayName(j));
        t.setTextSize(14);
        t.setMaxLines(2);
        h.addView(t, Ui.weight(1f));
        h.addView(Ui.pill(this, CsUtil.stateLabel(state), CsUtil.running(state) ? R.attr.gpText : R.attr.gpMuted), Ui.lp(this, Ui.WRAP, Ui.WRAP, 8, 0, 0, 0));
        c.addView(h);
        String repoName = o(j, "repository").optString("full_name");
        String ref = git.optString("ref");
        c.addView(Ui.text(this, repoName + (ref.isEmpty() ? "" : "  ·  " + ref), 12, false, R.attr.gpMuted), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        String m = mach.optInt("cpus") > 0 ? CsUtil.machineLabel(mach.optInt("cpus"), mach.optLong("memory_in_bytes"), mach.optLong("storage_in_bytes")) : "";
        String used = Ui.agoIso(j.optString("last_used_at"));
        c.addView(Ui.text(this, m + (m.isEmpty() || used.isEmpty() ? "" : "  ·  ") + (used.isEmpty() ? "" : "used " + used), 11, false, R.attr.gpMuted), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 2, 0, 0));
        String gs = CsUtil.gitSummary(git.optBoolean("has_uncommitted_changes"), git.optBoolean("has_unpushed_changes"), git.optInt("ahead"), git.optInt("behind"));
        if (!gs.isEmpty()) c.addView(Ui.pill(this, "●  " + gs, R.attr.gpText), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));
        if (!selecting) {
            LinearLayout row = Ui.hbox(this);
            row.addView(Ui.button(this, "Open", v -> openIde(j)), wlp(6));
            boolean run = CsUtil.running(state);
            row.addView(Ui.outlineButton(this, run ? "Stop" : "Start", v -> {
                if (run) act("Stopping…", () -> cs.stop(name)); else act("Starting…", () -> cs.start(name));
            }), wlp(6));
            row.addView(Ui.outlineButton(this, "More", v -> moreFor(j)), wlp(0));
            c.addView(row, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        }
        c.setOnClickListener(v -> {
            Ui.haptic(v);
            if (selecting) { if (!picked.add(name)) picked.remove(name); render(); }
            else openIde(j);
        });
        c.setOnLongClickListener(v -> { if (!selecting) { selecting = true; picked.add(name); render(); } return true; });
        return c;
    }

    private LinearLayout.LayoutParams wlp(int rightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f);
        lp.rightMargin = Ui.dp(this, rightDp);
        return lp;
    }

    private void act(String toast, final Ui.Task<?> task) {
        Ui.toast(this, toast);
        async(task, x -> load(), e -> Ui.toast(this, msg(e)));
    }

    // ------------------------------------------------------------------------------------ open
    private void openIde(JSONObject j) {
        if (split) { ide.setVisibility(View.VISIBLE); ide.open(j); }
        else CodespaceIdeActivity.open(this, j);
    }

    // ------------------------------------------------------------------------------------ per-codespace menu
    private void moreFor(final JSONObject j) {
        final String name = j.optString("name");
        final String[] items = {"✏  Rename", "⚙  Change machine type", "📡  Ports & preview", "📤  Export to a branch", "☁  Publish to a new repo", "🌐  Open in browser", "📋  Copy VS Code URL", "ℹ  Details", "🗑  Delete"};
        Ui.pickList(this, displayName(j), items, i -> {
            if (i == 0) Ui.prompt(this, "Display name", "Name", displayName(j), s -> {
                String n = CsUtil.cleanDisplayName(s);
                if (n.isEmpty()) return;
                act("Renaming…", () -> cs.update(name, GitHubApi.jo("display_name", n)));
            });
            else if (i == 1) changeMachine(name);
            else if (i == 2) portsSheet(j);
            else if (i == 3) exportBranch(name);
            else if (i == 4) publish(j);
            else if (i == 5) Ui.openUrl(this, CsUtil.webUrl(name));
            else if (i == 6) { Ui.copy(this, "url", CsUtil.webUrl(name)); Ui.toast(this, "Copied"); }
            else if (i == 7) details(j);
            else Ui.confirmDanger(this, "Delete codespace?", "Anything not pushed to GitHub is lost.\n\n" + displayName(j), "Delete", () -> act("Deleting…", () -> { cs.delete(name); return ""; }));
        });
    }

    private void details(JSONObject j) {
        JSONObject m = o(j, "machine"), g = o(j, "git_status");
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(j.optString("name")).append('\n');
        sb.append("Repo: ").append(o(j, "repository").optString("full_name")).append('\n');
        sb.append("Branch: ").append(g.optString("ref")).append('\n');
        sb.append("State: ").append(CsUtil.stateLabel(j.optString("state"))).append('\n');
        sb.append("Machine: ").append(m.optString("display_name")).append("  (").append(CsUtil.machineLabel(m.optInt("cpus"), m.optLong("memory_in_bytes"), m.optLong("storage_in_bytes"))).append(")\n");
        sb.append("Billing rate: ").append(CsUtil.coreHourRate(m.optInt("cpus"))).append('\n');
        sb.append("Region: ").append(j.optString("location")).append('\n');
        sb.append("Dev container: ").append(j.optString("devcontainer_path", "default")).append('\n');
        sb.append("Idle timeout: ").append(j.optInt("idle_timeout_minutes")).append(" min\n");
        if (!j.isNull("retention_period_minutes")) sb.append("Retention: ").append(j.optInt("retention_period_minutes") / 1440).append(" days\n");
        sb.append("Created: ").append(j.optString("created_at")).append('\n');
        sb.append("Last used: ").append(j.optString("last_used_at")).append('\n');
        sb.append("Git: ").append(CsUtil.gitSummary(g.optBoolean("has_uncommitted_changes"), g.optBoolean("has_unpushed_changes"), g.optInt("ahead"), g.optInt("behind")).isEmpty() ? "clean" : CsUtil.gitSummary(g.optBoolean("has_uncommitted_changes"), g.optBoolean("has_unpushed_changes"), g.optInt("ahead"), g.optInt("behind"))).append('\n');
        showText("Details", sb.toString());
    }

    private void showText(String title, final String text) {
        TextView t = Ui.body(this, text);
        t.setTextSize(12);
        t.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        LinearLayout box = Ui.vbox(this);
        box.addView(sv, Ui.lp(Ui.MATCH, Ui.dp(this, 300)));
        new GlassDialog(this).setTitle(title).setView(box).setPositiveButton("Close", null)
                .setNegativeButton("Copy", (d, w) -> { Ui.copy(this, title, text); Ui.toast(this, "Copied"); }).show();
    }

    private void changeMachine(final String name) {
        async(() -> cs.machinesOf(name), arr -> {
            if (arr == null || arr.length() == 0) { Ui.toast(this, "No other machine types are available for this codespace"); return; }
            final String[] labels = new String[arr.length()], ids = new String[arr.length()];
            for (int i = 0; i < labels.length; i++) {
                JSONObject m = arr.optJSONObject(i);
                ids[i] = m.optString("name");
                labels[i] = m.optString("display_name") + " — " + CsUtil.machineLabel(m.optInt("cpus"), m.optLong("memory_in_bytes"), m.optLong("storage_in_bytes"));
            }
            Ui.pickList(this, "Machine type (restart needed)", labels, i -> act("Changing machine…", () -> cs.update(name, GitHubApi.jo("machine", ids[i]))));
        }, e -> Ui.toast(this, msg(e)));
    }

    private void exportBranch(final String name) {
        Ui.confirm(this, "Export to a branch?", "GitHub saves this codespace's uncommitted changes to a new branch of its repository.", "Export", () -> {
            Ui.toast(this, "Exporting…");
            async(() -> {
                cs.export(name);
                for (int i = 0; i < 40; i++) {
                    Thread.sleep(3000);
                    JSONObject j = cs.exportLatest(name);
                    String s = j.optString("state");
                    if ("succeeded".equals(s)) return j;
                    if ("failed".equals(s)) throw new Exception("Export failed. Is there anything to export?");
                }
                throw new Exception("Export is taking long. Check the repository's branches in a minute.");
            }, j -> showText("Exported", "Branch: " + j.optString("branch") + "\nCommit: " + j.optString("sha") + "\n" + j.optString("html_url")), e -> Ui.toast(this, msg(e)));
        });
    }

    private void publish(final JSONObject j) {
        Form.show(this, "Publish to a new repo", "☁", "Publish", Form.list(Form.text("n", "Repository name", CsUtil.repoSlug(displayName(j))).plain(), Form.toggle("p", "Private", true)), m -> {
            final String n = CsUtil.repoSlug(m.get("n"));
            if (n.isEmpty()) { Ui.toast(this, "Enter a repository name"); return; }
            async(() -> cs.publish(j.optString("name"), n, "true".equals(m.get("p"))), r -> { Ui.toast(this, "Published " + r.optString("full_name", n)); load(); },
                    e -> Ui.toast(this, msg(e) + "\n(Only codespaces created from a template, not from a repository, can be published.)"));
        });
    }

    private void portsSheet(final JSONObject j) {
        final String name = j.optString("name");
        List<Integer> ports = Snippets.ports(st.store, name);
        for (int p : CsUtil.COMMON_PORTS) if (!ports.contains(p)) ports.add(p);
        final String[] labels = new String[ports.size() + 1];
        final int[] vals = new int[ports.size()];
        for (int i = 0; i < vals.length; i++) { vals[i] = ports.get(i); labels[i] = "🌐  :" + vals[i] + "   " + CsUtil.portUrl(name, vals[i]).replace("https://", ""); }
        labels[vals.length] = "＋  Other port…";
        Ui.pickList(this, "Open forwarded port", labels, i -> {
            if (i == vals.length) Ui.prompt(this, "Port number", "e.g. 3000", "", s -> {
                int p = CsUtil.parsePort(s);
                if (p < 0) { Ui.toast(this, "Enter a port from 1 to 65535"); return; }
                Snippets.addPort(st.store, name, p);
                Ui.openUrl(this, CsUtil.portUrl(name, p));
            });
            else Ui.openUrl(this, CsUtil.portUrl(name, vals[i]));
        });
        Ui.toast(this, "GitHub's API can't change port visibility – set it in VS Code's Ports tab.");
    }

    // ------------------------------------------------------------------------------------ create
    private void createMenu() {
        final String[] items = {"🚀  Guided (branch, machine, dev container…)", "⚡  Quick create (repo defaults)", "🔀  From a pull request"};
        Ui.pickList(this, "New codespace", items, i -> {
            if (!fRepo.isEmpty()) { Repo r = st.findRepo(fOwner, fRepo); if (r != null) { chooseRef(r, i); return; } }
            RepoPicker.pick(this, "Create a codespace for…", r -> chooseRef(r, i));
        });
    }

    private void chooseRef(final Repo r, final int mode) {
        if (mode == 1) { createWith(r, r.defaultBranch, null, null, null, 0, -1, "Auto", 0); return; }
        if (mode == 2) {
            async(() -> st.api().getArr("/repos/" + GitHubApi.enc2(r.owner) + "/" + GitHubApi.enc2(r.name) + "/pulls?state=open&per_page=50"), arr -> {
                if (arr.length() == 0) { Ui.toast(this, "No open pull requests"); return; }
                final String[] labels = new String[arr.length()];
                final int[] nums = new int[arr.length()];
                for (int i = 0; i < labels.length; i++) { JSONObject p = arr.optJSONObject(i); nums[i] = p.optInt("number"); labels[i] = "#" + nums[i] + "  " + p.optString("title"); }
                Ui.pickList(this, "Pull request", labels, i -> createWith(r, r.defaultBranch, null, null, null, nums[i], -1, "Auto", 0));
            }, e -> Ui.toast(this, msg(e)));
            return;
        }
        final String[] items = {"Default branch (" + r.defaultBranch + ")", "Another branch…"};
        Ui.pickList(this, r.fullName, items, i -> {
            if (i == 0) chooseMachine(r, r.defaultBranch);
            else async(() -> st.api().branches(r.owner, r.name), br -> {
                final String[] names = new String[br.size()];
                for (int k = 0; k < names.length; k++) names[k] = br.get(k).name;
                Ui.pickList(this, "Branch", names, k -> chooseMachine(r, names[k]));
            }, e -> Ui.toast(this, msg(e)));
        });
    }

    private void chooseMachine(final Repo r, final String ref) {
        Ui.toast(this, "Loading machine types…");
        async(() -> {
            JSONObject o = new JSONObject();
            o.put("machines", cs.repoMachines(r.owner, r.name, ref));
            o.put("dev", cs.devcontainers(r.owner, r.name));
            return o;
        }, o -> {
            final JSONArray ms = o.optJSONArray("machines"), dev = o.optJSONArray("dev");
            if (ms.length() == 0) {
                Ui.confirm(this, "No machine list", "GitHub returned no machine types for this repo (billing, policy or permissions). Create with the default machine?", "Create", () -> chooseDev(r, ref, null, dev));
                return;
            }
            final String[] labels = new String[ms.length()], ids = new String[ms.length()];
            for (int i = 0; i < labels.length; i++) {
                JSONObject m = ms.optJSONObject(i);
                ids[i] = m.optString("name");
                String pre = m.optString("prebuild_availability");
                labels[i] = m.optString("display_name") + "\n" + CsUtil.machineLabel(m.optInt("cpus"), m.optLong("memory_in_bytes"), m.optLong("storage_in_bytes")) + "  ·  " + CsUtil.coreHourRate(m.optInt("cpus"))
                        + (pre.isEmpty() || "none".equals(pre) ? "" : "  ·  prebuild " + pre);
            }
            Ui.pickList(this, "Machine type", labels, i -> chooseDev(r, ref, ids[i], dev));
        }, e -> Ui.toast(this, msg(e)));
    }

    private void chooseDev(final Repo r, final String ref, final String machine, JSONArray dev) {
        if (dev == null || dev.length() <= 1) { options(r, ref, machine, dev == null || dev.length() == 0 ? null : dev.optJSONObject(0).optString("path")); return; }
        final String[] labels = new String[dev.length() + 1], paths = new String[dev.length() + 1];
        labels[0] = "Default configuration"; paths[0] = null;
        for (int i = 0; i < dev.length(); i++) { JSONObject d = dev.optJSONObject(i); paths[i + 1] = d.optString("path"); labels[i + 1] = d.optString("display_name", d.optString("name", d.optString("path"))); }
        Ui.pickList(this, "Dev container", labels, i -> options(r, ref, machine, paths[i]));
    }

    private void options(final Repo r, final String ref, final String machine, final String devPath) {
        Form.show(this, "Codespace options", "⚙", "Check & create", Form.list(Form.text("n", "Display name (optional)", "").plain(),
                Form.choice("idle", "Stop when idle after", CsUtil.IDLE_LABELS, 1), Form.choice("ret", "Delete after being stopped for", CsUtil.RETAIN_LABELS, 0),
                Form.choice("geo", "Region", CsUtil.REGIONS, 0)), m -> {
            int idle = indexOf(CsUtil.IDLE_LABELS, m.get("idle")), ret = indexOf(CsUtil.RETAIN_LABELS, m.get("ret"));
            final String geo = m.get("geo");
            final String dn = CsUtil.cleanDisplayName(m.get("n"));
            Ui.toast(this, "Checking permissions…");
            async(() -> devPath == null ? new JSONObject().put("accepted", true) : cs.permissionsCheck(r.owner, r.name, ref, devPath), pc -> {
                if (!pc.optBoolean("accepted", true)) {
                    new GlassDialog(this).setTitle("Permissions needed").setIcon("🔒")
                            .setMessage("This dev container asks for extra repository permissions that you haven't approved yet. Approve them on GitHub once, then create again.")
                            .setPositiveButton("Open GitHub", (d, w) -> Ui.openUrl(this, "https://github.com/" + r.fullName + "/codespaces")).setNegativeButton("Cancel", null).show();
                    return;
                }
                createWith(r, ref, machine, devPath, dn.isEmpty() ? null : dn, 0, CsUtil.retentionMinutes(ret), geo, CsUtil.idleMinutes(idle));
            }, e -> createWith(r, ref, machine, devPath, dn.isEmpty() ? null : dn, 0, CsUtil.retentionMinutes(ret), geo, CsUtil.idleMinutes(idle)));
        });
    }

    private static int indexOf(String[] a, String v) { for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i; return 0; }

    private void createWith(final Repo r, final String ref, final String machine, final String devPath, final String name, final int pr, final int retention, final String geo, final int idle) {
        Ui.toast(this, "Creating codespace…");
        async(() -> {
            JSONObject b = new JSONObject();
            if (pr == 0) b.put("ref", ref);
            if (machine != null) b.put("machine", machine);
            if (devPath != null) b.put("devcontainer_path", devPath);
            if (name != null) b.put("display_name", name);
            if (idle > 0) b.put("idle_timeout_minutes", idle);
            if (retention >= 0) b.put("retention_period_minutes", retention);
            if (geo != null && !"Auto".equals(geo)) b.put("geo", geo);
            return pr > 0 ? cs.createFromPull(r.owner, r.name, pr, b) : cs.create(r.owner, r.name, b);
        }, j -> {
            Ui.toast(this, "Codespace is being created");
            load();
            if (j.optString("name").length() > 0) new GlassDialog(this).setTitle("Codespace created").setIcon("🚀").setMessage("Open it now? It will start automatically.")
                    .setPositiveButton("Open", (d, w) -> openIde(j)).setNegativeButton("Later", null).show();
        }, e -> {
            String m = msg(e);
            if (m.contains("402") || m.toLowerCase().contains("billing") || m.toLowerCase().contains("spending")) m += "\n\nA billing or spending-limit setting blocks this. Check github.com/settings/billing.";
            else if (m.contains("403")) m += "\n\nThe repository's owner or your token may not allow Codespaces (needs the “codespace” scope).";
            Ui.toast(this, m);
        });
    }

    // ------------------------------------------------------------------------------------ bulk
    private void updateBulk() {
        bulkBar.removeAllViews();
        bulkBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
        if (!selecting) return;
        bulkBar.addView(Ui.text(this, picked.size() + " selected", 13, true, R.attr.gpText), Ui.weight(1f));
        bulkBar.addView(Ui.outlineButton(this, "Stop", v -> bulk(false)), Ui.lp(this, Ui.WRAP, Ui.dp(this, 40), 0, 0, 6, 0));
        bulkBar.addView(Ui.outlineButton(this, "Delete", v -> bulk(true)), Ui.lp(this, Ui.WRAP, Ui.dp(this, 40), 0, 0, 6, 0));
        bulkBar.addView(Ui.outlineButton(this, "Done", v -> { selecting = false; picked.clear(); render(); }), Ui.lp(this, Ui.WRAP, Ui.dp(this, 40), 0, 0, 0, 0));
    }

    private void bulk(final boolean delete) {
        if (picked.isEmpty()) { Ui.toast(this, "Pick at least one"); return; }
        final List<String> names = new ArrayList<>(picked);
        Runnable go = () -> async(() -> {
            int ok = 0;
            for (String n : names) { try { if (delete) cs.delete(n); else cs.stop(n); ok++; } catch (Exception ignored) { } }
            return ok;
        }, n -> { Ui.toast(this, n + " of " + names.size() + (delete ? " deleted" : " stopping")); selecting = false; picked.clear(); load(); }, e -> Ui.toast(this, msg(e)));
        if (delete) Ui.confirmDanger(this, "Delete " + names.size() + " codespaces?", "Anything not pushed to GitHub is lost.", "Delete", go);
        else go.run();
    }

    // ------------------------------------------------------------------------------------ more menu
    private void moreMenu() {
        final String[] items = {"☑  Select several (bulk stop / delete)", "⏹  Stop all running", "🔑  Codespaces secrets", "⏱  Usage & cost safety", "✨  AI dev container for a repo", "🏢  Organization codespaces", "🔔  Reminder settings"};
        Ui.pickList(this, "Codespaces", items, i -> {
            if (i == 0) { selecting = true; picked.clear(); render(); Ui.toast(this, "Tap codespaces to select them"); }
            else if (i == 1) stopAll();
            else if (i == 2) FeatureActivity.open(this, "cssecrets", fOwner, fRepo);
            else if (i == 3) costSafety();
            else if (i == 4) aiDevcontainer();
            else if (i == 5) FeatureActivity.open(this, "csorg", "", "");
            else if (ide != null && !ide.name().isEmpty()) ide.remindPicker(); else remindOnly();
        });
    }

    private void remindOnly() {
        final int[] v = {0, 30, 60, 120, 240};
        String[] l = {"Off", "After 30 minutes", "After 1 hour", "After 2 hours", "After 4 hours"};
        Ui.pickList(this, "Remind me while a codespace keeps running", l, i -> { Snippets.remindMinutes(st.store, v[i]); Ui.toast(this, v[i] == 0 ? "Reminders off" : "Saved. Reminders start when you open a codespace."); });
    }

    private void stopAll() {
        final List<String> names = new ArrayList<>();
        for (JSONObject j : all) if (CsUtil.running(j.optString("state"))) names.add(j.optString("name"));
        if (names.isEmpty()) { Ui.toast(this, "Nothing is running"); return; }
        Ui.confirm(this, "Stop " + names.size() + " running codespace" + (names.size() == 1 ? "" : "s") + "?", "Saves core-hours. Files and git changes stay.", "Stop all", () -> async(() -> {
            int ok = 0;
            for (String n : names) { try { cs.stop(n); ok++; } catch (Exception ignored) { } }
            return ok;
        }, n -> { Ui.toast(this, n + " stopping"); load(); }, e -> Ui.toast(this, msg(e))));
    }

    private void costSafety() {
        int cores = 0, run = 0;
        for (JSONObject j : all) if (CsUtil.running(j.optString("state"))) { run++; cores += o(j, "machine").optInt("cpus"); }
        String msg = run == 0 ? "Nothing is running right now." : run + " running · " + CsUtil.coreHourRate(cores) + " in total.";
        msg += "\nGitHub bills core-hours (cores × hours) plus storage. Your free allowance depends on your plan – see github.com/settings/billing.";
        Form.show(this, "Usage & cost safety", "⏱", "Save", Form.list(Form.toggle("sol", "Stop the codespace when I leave the editor", Snippets.stopOnLeave(st.store)),
                Form.choice("rem", "Remind me when still running", new String[]{"Off", "30 min", "1 hour", "2 hours", "4 hours"}, remIndex(Snippets.remindMinutes(st.store)))), m -> {
            Snippets.stopOnLeave(st.store, "true".equals(m.get("sol")));
            int[] v = {0, 30, 60, 120, 240};
            Snippets.remindMinutes(st.store, v[indexOf(new String[]{"Off", "30 min", "1 hour", "2 hours", "4 hours"}, m.get("rem"))]);
            Ui.toast(this, "Saved");
        });
        Ui.toast(this, msg);
    }

    private static int remIndex(int min) { int[] v = {0, 30, 60, 120, 240}; for (int i = 0; i < v.length; i++) if (v[i] == min) return i; return 2; }

    // ------------------------------------------------------------------------------------ AI dev container
    private void aiDevcontainer() {
        if (!AiClient.configured(st.store)) { AiSettings.show(this, this::aiDevcontainer); return; }
        RepoPicker.pick(this, "AI dev container for…", r -> {
            Ui.toast(this, "Reading the repository…");
            async(() -> {
                GitHubApi api = st.api();
                StringBuilder sb = new StringBuilder("Repository: " + r.fullName + (r.language == null ? "" : " (" + r.language + ")") + "\nFiles in root:\n");
                List<ContentItem> items = api.contents(r.owner, r.name, "", r.defaultBranch);
                String[] key = {"package.json", "requirements.txt", "pyproject.toml", "pom.xml", "build.gradle", "build.gradle.kts", "Cargo.toml", "go.mod", "Gemfile", "composer.json", "Dockerfile"};
                for (ContentItem c : items) sb.append(c.isDir ? "  " + c.name + "/\n" : "  " + c.name + "\n");
                for (ContentItem c : items) for (String k : key) if (!c.isDir && c.name.equals(k) && c.size < 20000) {
                    try { sb.append("\n--- ").append(k).append(" ---\n").append(new String(api.file(r.owner, r.name, k, r.defaultBranch).bytes, "UTF-8")); } catch (Exception ignored) { }
                }
                return AiClient.stripFence(CsAi.devcontainer(st.store, sb.toString()));
            }, txt -> {
                final String json = txt.startsWith("json") ? txt.substring(4).trim() : txt;
                TextView t = Ui.body(this, json);
                t.setTextSize(11);
                t.setTextIsSelectable(true);
                t.setTypeface(Ui.mono(this, false));
                ScrollView sv = new ScrollView(this);
                sv.addView(t);
                LinearLayout box = Ui.vbox(this);
                box.addView(sv, Ui.lp(Ui.MATCH, Ui.dp(this, 300)));
                new GlassDialog(this).setTitle(".devcontainer/devcontainer.json").setIcon("✨").setView(box)
                        .setPositiveButton("Commit to repo", (d, w) -> commitDevcontainer(r, json)).setNegativeButton("Copy", (d, w) -> { Ui.copy(this, "devcontainer", json); Ui.toast(this, "Copied"); }).show();
            }, e -> Ui.toast(this, msg(e)));
        });
    }

    private void commitDevcontainer(final Repo r, final String json) {
        async(() -> {
            String sha = null;
            try { sha = st.api().file(r.owner, r.name, ".devcontainer/devcontainer.json", r.defaultBranch).sha; } catch (Exception ignored) { }
            return st.api().putText(r.owner, r.name, ".devcontainer/devcontainer.json", "Add dev container (GitPush AI)", r.defaultBranch, sha, json.endsWith("\n") ? json : json + "\n");
        }, x -> Ui.toast(this, "Committed to " + r.defaultBranch), e -> Ui.toast(this, msg(e)));
    }

    // ------------------------------------------------------------------------------------ lifecycle
    @Override public void onBackPressed() {
        if (ide != null && ide.back()) return;
        if (selecting) { selecting = false; picked.clear(); render(); return; }
        super.onBackPressed();
    }

    @Override protected void onResume() { super.onResume(); if (ide != null) ide.resume(); }

    @Override protected void onDestroy() { refreshToken++; if (ide != null) ide.destroy(); super.onDestroy(); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == CsIdeView.REQ_FILE && ide != null) ide.onFileResult(res, data);
    }
}
