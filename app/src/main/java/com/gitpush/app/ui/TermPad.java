package com.gitpush.app.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.GitPushApp;
import com.gitpush.app.R;
import com.gitpush.app.data.AiClient;
import com.gitpush.app.data.Bridge;
import com.gitpush.app.data.CsAi;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Snippets;
import com.gitpush.app.data.Store;
import com.gitpush.app.util.CsUtil;
import com.gitpush.app.util.ShellKit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Native "terminal pad" under the VS Code WebView: Run code, mirrored Output (select / long-press / copy), Snippets, AI and Ports.
 * Text reaches the real terminal by pasting into VS Code Web; output comes back through the codespace bridge (see Bridge).
 */
public class TermPad extends LinearLayout {
    public interface Host {
        /** Pastes [text] into the focused VS Code terminal and optionally presses Enter. */
        void sendText(String text, boolean enter);
        /** Ctrl+C etc. */
        void ctrl(char letter);
        void focusTerminal();
        void openPort(String url);
        void collapse();
    }

    private static final String[] TABS = {"Run", "Output", "Snippets", "AI", "Ports"};

    private final Activity a;
    private final Host host;
    private final Store store;
    private final android.os.Handler main = GitPushApp.get().main;
    private String codespace = "", repoFull = "";
    private int tab = 0;
    private final LinearLayout tabRow, content;
    private final EditText cmdBar;

    // run
    private String langId = "bash";
    private EditText codeEt;
    private boolean capture = true, bridgeUp = false;
    // output
    private TextView outTv, outStatus;
    private ScrollView outScroll;
    private String lastOut = "";
    private int pollToken = 0;
    private long pollUntil = 0;
    // snippets
    private String snipGroup = "★";
    // ai
    private String aiResult = "";

    public TermPad(Activity a, Host host) {
        super(a);
        this.a = a;
        this.host = host;
        this.store = GitPushApp.get().state.store;
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_glass);

        LinearLayout head = Ui.hbox(a);
        head.setPadding(Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6), 0);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        tabRow = Ui.hbox(a);
        hs.addView(tabRow);
        head.addView(hs, new LayoutParams(0, Ui.WRAP, 1f));
        TextView close = Ui.text(a, "Hide  ▾", 12, true, R.attr.gpMuted);
        close.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        close.setOnClickListener(v -> host.collapse());
        head.addView(close);
        addView(head, new LayoutParams(Ui.MATCH, Ui.WRAP));

        content = Ui.vbox(a);
        addView(content, new LayoutParams(Ui.MATCH, 0, 1f));

        // persistent command bar
        LinearLayout bar = Ui.hbox(a);
        bar.setPadding(Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 6));
        cmdBar = Ui.input(a, "Type a command…", "");
        cmdBar.setTypeface(Ui.mono(a, false));
        cmdBar.setTextSize(13);
        cmdBar.setSingleLine(true);
        cmdBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        cmdBar.setImeOptions(EditorInfo.IME_ACTION_SEND);
        cmdBar.setOnEditorActionListener((v, id, e) -> { sendCmdBar(); return true; });
        selectionMenu(cmdBar);
        bar.addView(cmdBar, new LayoutParams(0, Ui.WRAP, 1f));
        bar.addView(barBtn("▶", v -> sendCmdBar()));
        bar.addView(barBtn("History", v -> historyPick()));
        bar.addView(barBtn("Ctrl+C", v -> host.ctrl('c')));
        addView(bar, new LayoutParams(Ui.MATCH, Ui.WRAP));
        renderTabs();
        renderTab();
    }

    private TextView barBtn(String label, OnClickListener l) {
        TextView t = Ui.text(a, label, 12, true, R.attr.gpText);
        t.setBackgroundResource(R.drawable.bg_chip_off);
        t.setPadding(Ui.dp(a, 10), Ui.dp(a, 9), Ui.dp(a, 10), Ui.dp(a, 9));
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(v -> { Ui.haptic(v); l.onClick(v); });
        LayoutParams lp = new LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.leftMargin = Ui.dp(a, 6);
        t.setLayoutParams(lp);
        return t;
    }

    public void setCodespace(String name, String repo) {
        if (!name.equals(codespace)) { bridgeUp = false; lastOut = ""; pollToken++; }
        codespace = name;
        repoFull = repo == null ? "" : repo;
    }

    public void stop() { pollToken++; }

    private void renderTabs() {
        tabRow.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int k = i;
            tabRow.addView(Ui.chip(a, TABS[i], tab == i, v -> { tab = k; renderTabs(); renderTab(); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        }
    }

    public void show(int t) { tab = t; renderTabs(); renderTab(); }

    private void renderTab() {
        content.removeAllViews();
        switch (tab) {
            case 0: buildRun(); break;
            case 1: buildOutput(); break;
            case 2: buildSnippets(); break;
            case 3: buildAi(); break;
            default: buildPorts();
        }
    }

    // ------------------------------------------------------------------------------------ shared
    private void sendCmdBar() {
        String c = cmdBar.getText().toString();
        if (c.trim().isEmpty()) { host.sendText("", true); return; }
        runCommand(c, true);
        cmdBar.setText("");
    }

    /** Pastes a command into the terminal (asks first when it looks risky). */
    public void runCommand(final String cmd, final boolean enter) {
        final String risk = ShellKit.risk(cmd);
        Runnable go = () -> { Snippets.pushHistory(store, cmd.trim()); host.sendText(ShellKit.normalizeNewlines(cmd), enter); };
        if (risk != null && enter) Ui.confirmDanger(a, "Run this command?", "It " + risk + ":\n\n" + cmd, "Run anyway", go);
        else go.run();
    }

    private void historyPick() {
        final List<String> h = Snippets.history(store);
        if (h.isEmpty()) { Ui.toast(a, "No history yet"); return; }
        String[] labels = new String[h.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = h.get(i).length() > 70 ? h.get(i).substring(0, 70) + "…" : h.get(i);
        Ui.pickList(a, "Command history", labels, i -> cmdBar.setText(h.get(i)));
    }

    private static String clip(Context c) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return "";
        CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(c);
        return t == null ? "" : t.toString();
    }

    /** Adds "Run" and "Explain" to the system text-selection toolbar (long-press / select), next to Copy, Paste and Select all. */
    private void selectionMenu(final TextView tv) {
        tv.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode m, Menu menu) {
                menu.add(0, 9001, 0, "Run");
                menu.add(0, 9002, 1, "Explain");
                menu.add(0, 9003, 2, "Save snippet");
                return true;
            }
            @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode m, MenuItem item) {
                int s = Math.min(tv.getSelectionStart(), tv.getSelectionEnd()), e = Math.max(tv.getSelectionStart(), tv.getSelectionEnd());
                String sel = s < 0 || e <= s ? "" : tv.getText().subSequence(s, e).toString();
                int id = item.getItemId();
                if (id == 9001 || id == 9002 || id == 9003) {
                    m.finish();
                    if (sel.trim().isEmpty()) { Ui.toast(a, "Select some text first"); return true; }
                    if (id == 9001) runCommand(sel, true);
                    else if (id == 9002) { show(3); aiExplain(sel); }
                    else saveSnippet(sel);
                    return true;
                }
                return false;
            }
            @Override public void onDestroyActionMode(ActionMode m) { }
        });
    }

    private void saveSnippet(String cmd) {
        Form.show(a, "Save snippet", "💾", "Save", Form.list(Form.text("t", "Title", cmd.length() > 24 ? cmd.substring(0, 24) : cmd).plain(), Form.text("g", "Group", "custom").plain(), Form.multi("c", "Command", cmd)), m -> {
            List<Snippets.Item> all = Snippets.all(store);
            all.add(new Snippets.Item(m.get("g").isEmpty() ? "custom" : m.get("g"), m.get("t"), m.get("c"), false));
            Snippets.save(store, all);
            Ui.toast(a, "Snippet saved");
            if (tab == 2) renderTab();
        });
    }

    private LayoutParams grow(int rightDp) {
        LayoutParams lp = new LayoutParams(0, Ui.WRAP, 1f);
        lp.rightMargin = Ui.dp(a, rightDp);
        return lp;
    }

    private LinearLayout pad() {
        LinearLayout col = Ui.vbox(a);
        col.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        return col;
    }

    private ScrollView scroll(LinearLayout col) {
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        content.addView(sv, new LayoutParams(Ui.MATCH, 0, 1f));
        return sv;
    }

    private Button small(String label, boolean primary, OnClickListener l) {
        Button b = primary ? Ui.button(a, label, l) : Ui.outlineButton(a, label, l);
        b.setTextSize(12);
        b.setAllCaps(false);
        return b;
    }

    // ------------------------------------------------------------------------------------ RUN
    private void buildRun() {
        LinearLayout col = pad();
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        final LinearLayout chips = Ui.hbox(a);
        hs.addView(chips);
        col.addView(hs);
        renderLangChips(chips);
        codeEt = Ui.input(a, "Paste or write code, then Run. It is saved to /tmp/gp and executed in the terminal.", codeDraft);
        codeEt.setTypeface(Ui.mono(a, false));
        codeEt.setTextSize(12);
        codeEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        codeEt.setMinLines(6);
        codeEt.setGravity(Gravity.TOP);
        codeEt.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(android.text.Editable e) { codeDraft = e.toString(); }
        });
        selectionMenu(codeEt);
        col.addView(codeEt, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        final TextView cap = Ui.chip(a, (capture ? "✔ " : "☐ ") + "Mirror output in the Output tab", capture, null);
        cap.setOnClickListener(v -> { capture = !capture; cap.setText((capture ? "✔ " : "☐ ") + "Mirror output in the Output tab"); cap.setBackgroundResource(capture ? R.drawable.bg_chip_on : R.drawable.bg_chip_off); cap.setTextColor(Ui.color(a, capture ? R.attr.gpOnPrimary : R.attr.gpText)); });
        col.addView(cap, Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));
        LinearLayout row = Ui.hbox(a);
        row.addView(small("▶  Run in terminal", true, v -> runCode()), grow(6));
        row.addView(small("Paste", false, v -> { String t = clip(a); if (t.isEmpty()) Ui.toast(a, "Clipboard is empty"); else codeEt.getText().insert(Math.max(0, codeEt.getSelectionStart()), t); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        row.addView(small("Copy", false, v -> { Ui.copy(a, "code", codeEt.getText().toString()); Ui.toast(a, "Copied"); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        row.addView(small("Clear", false, v -> codeEt.setText("")), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 0, 0));
        col.addView(row, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(Ui.muted(a, "Tip: long-press or select text in the box to Copy, Paste, Run it, or Explain it with AI."), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        scroll(col);
    }

    private String codeDraft = "";

    private void renderLangChips(final LinearLayout chips) {
        chips.removeAllViews();
        for (final ShellKit.Lang l : ShellKit.LANGS) {
            chips.addView(Ui.chip(a, l.label, l.id.equals(langId), v -> { langId = l.id; renderLangChips(chips); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        }
    }

    private void runCode() {
        final String code = codeEt.getText().toString();
        if (code.trim().isEmpty()) { Ui.toast(a, "Write or paste some code first"); return; }
        final ShellKit.Lang l = ShellKit.lang(langId);
        final String risk = ShellKit.risk(code);
        Runnable go = () -> doRun(l, code);
        if (risk != null) Ui.confirmDanger(a, "Run this code?", "It " + risk + ".", "Run anyway", go);
        else go.run();
    }

    private void doRun(ShellKit.Lang l, String code) {
        Ui.hideKeyboard(a);
        if (capture && !bridgeUp) {
            host.sendText(ShellKit.bridgeStart(), true);
            bridgeUp = true;
            main.postDelayed(() -> host.sendText(ShellKit.runCode(l, code, true), true), 1800);
        } else host.sendText(ShellKit.runCode(l, code, capture), true);
        Snippets.pushHistory(store, "run " + l.label + ": " + firstLine(code));
        if (capture) { lastRunCode = code; lastRunLang = l.label; show(1); startPolling(); }
        else Ui.toast(a, "Sent to the terminal");
    }

    private String lastRunCode = "", lastRunLang = "";

    private static String firstLine(String s) { String t = s.trim(); int n = t.indexOf('\n'); return n < 0 ? t : t.substring(0, n); }

    // ------------------------------------------------------------------------------------ OUTPUT
    private void buildOutput() {
        LinearLayout col = Ui.vbox(a);
        col.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        outStatus = Ui.muted(a, lastOut.isEmpty() ? "No output yet. Run code with “Mirror output”, or tap Fetch to read the last run." : "");
        col.addView(outStatus);
        LinearLayout row = Ui.hbox(a);
        row.addView(small("Fetch", false, v -> { startPolling(); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        row.addView(small("Copy all", false, v -> { Ui.copy(a, "output", lastOut); Ui.toast(a, "Copied output"); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        row.addView(small("Copy last 30 lines", false, v -> { Ui.copy(a, "output", lastLines(lastOut, 30)); Ui.toast(a, "Copied"); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        row.addView(small("Explain", false, v -> { show(3); aiExplain(lastOut); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        row.addView(small("Fix code", false, v -> { show(3); aiFix(); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        col.addView(hs, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 6));
        outTv = Ui.text(a, lastOut, 12, false, R.attr.gpText);
        outTv.setTypeface(Ui.mono(a, false));
        outTv.setTextIsSelectable(true);
        selectionMenu(outTv);
        outTv.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8));
        outTv.setBackgroundResource(R.drawable.bg_input);
        outScroll = new ScrollView(a);
        outScroll.addView(outTv);
        col.addView(outScroll, new LayoutParams(Ui.MATCH, 0, 1f));
        content.addView(col, new LayoutParams(Ui.MATCH, 0, 1f));
    }

    private static String lastLines(String s, int n) {
        String[] ls = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, ls.length - n); i < ls.length; i++) sb.append(ls[i]).append('\n');
        return sb.toString().trim();
    }

    private void startPolling() {
        pollToken++;
        pollUntil = System.currentTimeMillis() + 10 * 60 * 1000L;
        if (outStatus != null) outStatus.setText("Waiting for output…");
        poll(pollToken, 0);
    }

    private void poll(final int token, long delay) {
        main.postDelayed(() -> {
            if (token != pollToken || !isAttachedToWindow()) return;
            final GitHubApi api = GitPushApp.get().state.api();
            final String cs = codespace;
            if (api == null || cs.isEmpty()) return;
            GitPushApp.get().io.execute(() -> {
                String out = null, stat = null, err = null;
                try { out = Bridge.fetch(api, cs, "out.txt"); stat = Bridge.fetch(api, cs, "status.txt"); }
                catch (Exception e) { err = e.getMessage(); }
                final String fo = out, fs = stat, fe = err;
                main.post(() -> {
                    if (token != pollToken) return;
                    if (fe != null) { if (outStatus != null) outStatus.setText(fe); if (System.currentTimeMillis() < pollUntil && fe.contains("not running")) poll(token, 2500); return; }
                    if (fo != null && !fo.equals(lastOut)) {
                        lastOut = fo;
                        if (outTv != null && !outTv.hasSelection()) {
                            outTv.setText(lastOut);
                            if (outScroll != null) outScroll.post(() -> outScroll.fullScroll(View.FOCUS_DOWN));
                        }
                    }
                    if (fs != null) { if (outStatus != null) outStatus.setText("Finished · " + fs.trim()); return; }
                    if (outStatus != null) outStatus.setText("Running…");
                    if (System.currentTimeMillis() < pollUntil) poll(token, 1500);
                });
            });
        }, delay);
    }

    // ------------------------------------------------------------------------------------ SNIPPETS
    private void buildSnippets() {
        LinearLayout col = pad();
        List<Snippets.Item> all = Snippets.all(store);
        List<String> groups = new ArrayList<>();
        groups.add("★");
        groups.add("History");
        for (Snippets.Item i : all) if (!groups.contains(i.group)) groups.add(i.group);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.hbox(a);
        for (final String g : groups) chips.addView(Ui.chip(a, g, g.equals(snipGroup), v -> { snipGroup = g; renderTab(); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        chips.addView(Ui.chip(a, "＋ New", false, v -> saveSnippet("")), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        hs.addView(chips);
        col.addView(hs);
        if (snipGroup.equals("History")) {
            for (final String h : Snippets.history(store)) col.addView(snippetRow(null, h, h), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
            if (Snippets.history(store).isEmpty()) col.addView(Ui.muted(a, "Commands you run appear here."), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
            else col.addView(small("Clear history", false, v -> { Snippets.clearHistory(store); renderTab(); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
        } else {
            boolean any = false;
            for (final Snippets.Item i : all) {
                if (snipGroup.equals("★") ? !i.fav : !i.group.equals(snipGroup)) continue;
                any = true;
                col.addView(snippetRow(i, i.title, i.cmd), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
            }
            if (!any) col.addView(Ui.muted(a, snipGroup.equals("★") ? "Long-press a snippet → Favorite to pin it here." : "Empty."), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        }
        scroll(col);
    }

    private View snippetRow(final Snippets.Item item, String title, final String cmd) {
        LinearLayout c = Ui.card(a);
        TextView t = Ui.text(a, (item != null && item.fav ? "★ " : "") + title, 13, true, R.attr.gpText);
        c.addView(t);
        if (item != null) {
            TextView m = Ui.text(a, cmd.length() > 90 ? cmd.substring(0, 90) + "…" : cmd, 11, false, R.attr.gpMuted);
            m.setTypeface(Ui.mono(a, false));
            c.addView(m, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 2, 0, 0));
        }
        c.setOnClickListener(v -> { Ui.haptic(v); runCommand(cmd, true); });
        c.setOnLongClickListener(v -> {
            Ui.haptic(v);
            String[] items = item == null ? new String[]{"Paste (don't run)", "Copy", "Save as snippet"} : new String[]{"Paste (don't run)", "Copy", item.fav ? "Remove favorite" : "Favorite", "Edit", "Delete"};
            Ui.pickList(a, title, items, i -> {
                if (i == 0) host.sendText(cmd, false);
                else if (i == 1) { Ui.copy(a, "snippet", cmd); Ui.toast(a, "Copied"); }
                else if (item == null) saveSnippet(cmd);
                else if (i == 2) { item.fav = !item.fav; persist(item); }
                else if (i == 3) editSnippet(item);
                else { List<Snippets.Item> all = Snippets.all(store); for (int k = 0; k < all.size(); k++) if (all.get(k).title.equals(item.title) && all.get(k).cmd.equals(item.cmd)) { all.remove(k); break; } Snippets.save(store, all); renderTab(); }
            });
            return true;
        });
        return c;
    }

    private void persist(Snippets.Item changed) {
        List<Snippets.Item> all = Snippets.all(store);
        for (Snippets.Item i : all) if (i.title.equals(changed.title) && i.cmd.equals(changed.cmd)) i.fav = changed.fav;
        Snippets.save(store, all);
        renderTab();
    }

    private void editSnippet(final Snippets.Item item) {
        final String ot = item.title, oc = item.cmd;
        Form.show(a, "Edit snippet", "✏", "Save", Form.list(Form.text("t", "Title", item.title).plain(), Form.text("g", "Group", item.group).plain(), Form.multi("c", "Command", item.cmd)), m -> {
            List<Snippets.Item> all = Snippets.all(store);
            for (Snippets.Item i : all) if (i.title.equals(ot) && i.cmd.equals(oc)) { i.title = m.get("t"); i.group = m.get("g").isEmpty() ? "custom" : m.get("g"); i.cmd = m.get("c"); }
            Snippets.save(store, all);
            renderTab();
        });
    }

    // ------------------------------------------------------------------------------------ AI
    private TextView aiOut;
    private EditText aiIn;

    private void buildAi() {
        LinearLayout col = pad();
        if (!AiClient.configured(store)) {
            col.addView(Ui.muted(a, "Add your own AI key (Claude, OpenAI, Gemini, Groq…) to turn plain words into commands, explain errors and fix code. The key stays encrypted on this device."));
            col.addView(small("AI setup", true, v -> AiSettings.show(a, this::renderTab)), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
            scroll(col);
            return;
        }
        aiIn = Ui.input(a, "Describe what you want, e.g. “find the 10 biggest files”", "");
        aiIn.setMinLines(2);
        aiIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        selectionMenu(aiIn);
        col.addView(aiIn);
        LinearLayout row = Ui.hbox(a);
        row.addView(small("Make command", true, v -> aiCommand()), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        row.addView(small("Explain output", false, v -> aiExplain(lastOut.isEmpty() ? aiIn.getText().toString() : lastOut)), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        row.addView(small("Fix my code", false, v -> aiFix()), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 0, 0));
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        col.addView(hs, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        aiOut = Ui.text(a, aiResult, 12, false, R.attr.gpText);
        aiOut.setTypeface(Ui.mono(a, false));
        aiOut.setTextIsSelectable(true);
        selectionMenu(aiOut);
        aiOut.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8));
        aiOut.setBackgroundResource(R.drawable.bg_input);
        col.addView(aiOut, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        LinearLayout acts = Ui.hbox(a);
        acts.addView(small("Run", true, v -> { String c = ShellKit.extractCommand(aiResult); if (c.isEmpty()) Ui.toast(a, "Nothing to run yet"); else runCommand(c, true); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        acts.addView(small("Paste only", false, v -> { String c = ShellKit.extractCommand(aiResult); if (!c.isEmpty()) host.sendText(c, false); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        acts.addView(small("Copy", false, v -> { Ui.copy(a, "ai", ShellKit.extractCommand(aiResult)); Ui.toast(a, "Copied"); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        acts.addView(small("To code box", false, v -> { codeDraft = ShellKit.extractCommand(aiResult); show(0); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 0, 0));
        col.addView(acts, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(Ui.muted(a, "AI answers are never run automatically. Risky commands ask for confirmation. Only the text shown here is sent to your AI provider."), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        scroll(col);
    }

    private void aiBusy(String msg) { aiResult = msg; if (aiOut != null) aiOut.setText(msg); }

    private void aiCommand() {
        final String req = aiIn.getText().toString().trim();
        if (req.isEmpty()) { Ui.toast(a, "Describe the task first"); return; }
        aiBusy("Thinking…");
        Ui.async(() -> CsAi.command(store, req, repoFull), r -> { aiResult = r; if (aiOut != null) aiOut.setText(r); }, e -> aiBusy(e.getMessage()));
    }

    private void aiExplain(final String text) {
        if (!AiClient.configured(store)) { show(3); return; }
        if (text == null || text.trim().isEmpty()) { Ui.toast(a, "Nothing to explain yet"); return; }
        aiBusy("Reading…");
        Ui.async(() -> CsAi.explain(store, text, null), r -> { aiResult = r; if (aiOut != null) aiOut.setText(r); }, e -> aiBusy(e.getMessage()));
    }

    private void aiFix() {
        if (!AiClient.configured(store)) { show(3); return; }
        final String code = codeDraft.isEmpty() ? lastRunCode : codeDraft;
        if (code.trim().isEmpty()) { Ui.toast(a, "Run some code first (Run tab)"); return; }
        final String lang = lastRunLang.isEmpty() ? ShellKit.lang(langId).label : lastRunLang;
        aiBusy("Fixing…");
        Ui.async(() -> CsAi.fixCode(store, lang, code, lastOut), r -> { aiResult = r; if (aiOut != null) aiOut.setText(r + "\n\n(Tap “To code box”, then Run.)"); }, e -> aiBusy(e.getMessage()));
    }

    // ------------------------------------------------------------------------------------ PORTS
    private void buildPorts() {
        LinearLayout col = pad();
        col.addView(Ui.muted(a, "Forwarded ports open at https://" + (codespace.isEmpty() ? "<name>" : codespace) + "-<port>.app.github.dev. "
                + "GitHub's public API cannot change port visibility – use VS Code's Ports tab (right-click → Port Visibility) for that. Private ports need you to be signed in to GitHub in this app's browser."));
        final LinearLayout chips = Ui.vbox(a);
        col.addView(chips, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        List<Integer> ports = Snippets.ports(store, codespace);
        for (int p : CsUtil.COMMON_PORTS) if (!ports.contains(p)) ports.add(p);
        for (final int p : ports) {
            LinearLayout r = Ui.hbox(a);
            TextView t = Ui.text(a, ":" + p, 14, true, R.attr.gpText);
            t.setTypeface(Ui.mono(a, true));
            r.addView(t, new LayoutParams(0, Ui.WRAP, 1f));
            r.addView(small("Open here", true, v -> host.openPort(CsUtil.portUrl(codespace, p))), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
            r.addView(small("Browser", false, v -> Ui.openUrl(a, CsUtil.portUrl(codespace, p))), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
            r.addView(small("Copy URL", false, v -> { Ui.copy(a, "url", CsUtil.portUrl(codespace, p)); Ui.toast(a, "Copied"); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 0, 0));
            chips.addView(r, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
        }
        col.addView(small("＋ Add port", false, v -> Ui.prompt(a, "Port number", "e.g. 3000", "", s -> {
            int p = CsUtil.parsePort(s);
            if (p < 0) { Ui.toast(a, "Enter a port between 1 and 65535"); return; }
            Snippets.addPort(store, codespace, p);
            renderTab();
        })), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));
        scroll(col);
    }
}
