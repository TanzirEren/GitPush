package com.gitpush.app.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gitpush.app.GitPushApp;
import com.gitpush.app.R;
import com.gitpush.app.data.CodespacesApi;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Snippets;
import com.gitpush.app.data.Store;
import com.gitpush.app.util.CsJs;
import com.gitpush.app.util.CsUtil;

import org.json.JSONObject;

/**
 * Mobile VS Code: the real VS Code Web of a codespace in a WebView with a phone key bar, a native terminal pad
 * (run code, output, snippets, AI, ports) and a port preview. Used full-screen on phones and as the right pane on tablets.
 */
public class CsIdeView extends LinearLayout implements TermPad.Host {
    public interface Callbacks { void onTitle(String title, String sub); void onReady(String codespace); }

    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    public static final int REQ_FILE = 7311;

    private final Activity a;
    private final Callbacks cb;
    private final Store store = GitPushApp.get().state.store;
    private final FrameLayout stage;
    private final WebView web, preview;
    private final LinearLayout previewBox, overlay, keyBox;
    private final TextView overlayTitle, overlaySub, previewUrl;
    private final ProgressBar overlayBar, pageBar;
    private final HorizontalScrollView keyScroll;
    private final TermPad pad;
    private ValueCallback<Uri[]> fileCb;
    private String name = "", repo = "", state = "";
    private int poll = 0;
    private boolean padOpen = false, padFull = false, keysOn = true, altOn = false, shiftOn = false;
    private TextView altBtn, shiftBtn;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    public CsIdeView(Activity a, Callbacks cb) {
        super(a);
        this.a = a;
        this.cb = cb;
        setOrientation(VERTICAL);
        stage = new FrameLayout(a);
        addView(stage, new LayoutParams(Ui.MATCH, 0, 1f));

        web = new WebView(a);
        setup(web, true);
        stage.addView(web, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        pageBar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        pageBar.setMax(100);
        pageBar.setVisibility(GONE);
        stage.addView(pageBar, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(a, 3), Gravity.TOP));

        // port preview overlay
        preview = new WebView(a);
        setup(preview, false);
        previewBox = Ui.vbox(a);
        previewBox.setBackgroundColor(Ui.color(a, R.attr.gpBg));
        LinearLayout pb = Ui.hbox(a);
        pb.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 6), Ui.dp(a, 4));
        previewUrl = Ui.muted(a, "");
        previewUrl.setSingleLine(true);
        previewUrl.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        pb.addView(previewUrl, new LayoutParams(0, Ui.WRAP, 1f));
        pb.addView(textBtn("Reload", v -> preview.reload()));
        pb.addView(textBtn("Browser", v -> Ui.openUrl(a, previewUrl.getText().toString())));
        pb.addView(textBtn("Close", v -> { previewBox.setVisibility(GONE); preview.loadUrl("about:blank"); }));
        previewBox.addView(pb, new LayoutParams(Ui.MATCH, Ui.WRAP));
        previewBox.addView(preview, new LayoutParams(Ui.MATCH, 0, 1f));
        previewBox.setVisibility(GONE);
        stage.addView(previewBox, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // starting overlay
        overlay = Ui.vbox(a);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(Ui.color(a, R.attr.gpBg));
        overlay.setPadding(Ui.dp(a, 28), 0, Ui.dp(a, 28), 0);
        overlayBar = new ProgressBar(a);
        overlay.addView(overlayBar, new LayoutParams(Ui.dp(a, 36), Ui.dp(a, 36)));
        overlayTitle = Ui.text(a, "Starting…", 17, true, R.attr.gpText);
        overlayTitle.setGravity(Gravity.CENTER);
        overlay.addView(overlayTitle, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
        overlaySub = Ui.muted(a, "");
        overlaySub.setGravity(Gravity.CENTER);
        overlay.addView(overlaySub, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        overlay.setVisibility(GONE);
        stage.addView(overlay, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        pad = new TermPad(a, this);
        pad.setVisibility(GONE);
        addView(pad, new LayoutParams(Ui.MATCH, Ui.dp(a, 300)));

        keyScroll = new HorizontalScrollView(a);
        keyScroll.setHorizontalScrollBarEnabled(false);
        keyScroll.setBackgroundResource(R.drawable.bg_glass);
        keyBox = Ui.hbox(a);
        keyBox.setPadding(Ui.dp(a, 6), Ui.dp(a, 5), Ui.dp(a, 6), Ui.dp(a, 5));
        keyScroll.addView(keyBox);
        buildKeys();
        addView(keyScroll, new LayoutParams(Ui.MATCH, Ui.WRAP));
    }

    private TextView textBtn(String label, OnClickListener l) {
        TextView t = Ui.text(a, label, 12, true, R.attr.gpPrimary);
        t.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
        t.setOnClickListener(l);
        return t;
    }

    // ------------------------------------------------------------------------------------ WebView
    @SuppressLint("SetJavaScriptEnabled")
    private void setup(final WebView w, final boolean main) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (main) s.setUserAgentString(DESKTOP_UA);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);
        w.setBackgroundColor(Ui.color(a, R.attr.gpBg));
        w.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sch = u.getScheme() == null ? "" : u.getScheme();
                if (!sch.equals("http") && !sch.equals("https")) { Ui.openUrl(a, u.toString()); return true; }
                if (!main) return false;
                return !hostAllowed(u.getHost());
            }
            @Override public void onPageStarted(WebView v, String url, Bitmap f) { if (main) { pageBar.setVisibility(VISIBLE); pageBar.setProgress(5); } }
            @Override public void onPageFinished(WebView v, String url) {
                if (main) { pageBar.setVisibility(GONE); applyViewport(); cb.onReady(name); }
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, android.webkit.WebResourceError e) {
                if (main && r.isForMainFrame()) failOverlay("Couldn't load VS Code (" + e.getDescription() + "). Check your connection and tap reload.");
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) { if (main) { pageBar.setProgress(p); pageBar.setVisibility(p >= 100 ? GONE : VISIBLE); } }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cbk, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cbk;
                try { a.startActivityForResult(p.createIntent(), REQ_FILE); }
                catch (Exception e) { fileCb = null; Ui.toast(a, "No file picker available"); return false; }
                return true;
            }
        });
        w.setDownloadListener((url, ua, cd, mime, len) -> {
            if (url.startsWith("blob:") || url.startsWith("data:")) Ui.toast(a, "Downloads from VS Code Web aren't supported in the in-app view. Push the file to GitHub or open in the browser.");
            else Ui.openUrl(a, url);
        });
    }

    private static boolean hostAllowed(String h) {
        if (h == null) return false;
        h = h.toLowerCase();
        return h.endsWith("github.com") || h.endsWith("github.dev") || h.endsWith("githubassets.com") || h.endsWith("githubusercontent.com")
                || h.endsWith("vscode-cdn.net") || h.endsWith("visualstudio.com") || h.endsWith("microsoft.com") || h.endsWith("live.com")
                || h.endsWith("microsoftonline.com") || h.endsWith("githubcopilot.com") || h.endsWith("vsassets.io") || h.endsWith("azureedge.net");
    }

    public void onFileResult(int res, Intent data) {
        if (fileCb == null) return;
        fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
        fileCb = null;
    }

    public String name() { return name; }

    // ------------------------------------------------------------------------------------ open / start
    /** [cs] is a codespace JSON object from the API. Starts it when stopped, then loads VS Code Web. */
    public void open(JSONObject cs) {
        name = cs.optString("name");
        JSONObject r = cs.optJSONObject("repository");
        repo = r == null ? "" : r.optString("full_name");
        state = cs.optString("state");
        pad.setCodespace(name, repo);
        String disp = cs.optString("display_name", name);
        cb.onTitle(disp.isEmpty() ? name : disp, repo.isEmpty() ? CsUtil.stateLabel(state) : repo);
        store.prefs.edit().putString("cs_last", name).apply();
        poll++;
        if (CsUtil.running(state)) { load(); return; }
        showOverlay("Starting codespace", "This can take up to a minute.");
        final int token = poll;
        final CodespacesApi api = new CodespacesApi(GitPushApp.get().state.api());
        final boolean needStart = !CsUtil.transitional(state);
        GitPushApp.get().io.execute(() -> {
            try { if (needStart) api.start(name); } catch (Exception e) { post(token, () -> failOverlay(e.getMessage())); return; }
            waitRunning(api, token, 0);
        });
    }

    private void post(int token, Runnable r) { GitPushApp.get().main.post(() -> { if (token == poll) r.run(); }); }

    private void waitRunning(final CodespacesApi api, final int token, final int tries) {
        if (token != poll) return;
        try {
            JSONObject j = api.get(name);
            final String st = j.optString("state");
            post(token, () -> { state = st; overlaySub.setText("State: " + CsUtil.stateLabel(st)); });
            if (CsUtil.running(st)) { post(token, this::load); return; }
            if ("Failed".equals(st) || "Deleted".equals(st)) { post(token, () -> failOverlay("The codespace is " + CsUtil.stateLabel(st).toLowerCase() + ".")); return; }
        } catch (Exception e) { if (tries > 3) { final String m = e.getMessage(); post(token, () -> failOverlay(m)); return; } }
        if (tries > 90) { post(token, () -> failOverlay("Timed out waiting for the codespace to start.")); return; }
        try { Thread.sleep(4000); } catch (InterruptedException ignored) { return; }
        waitRunning(api, token, tries + 1);
    }

    private void showOverlay(String title, String sub) {
        overlay.setVisibility(VISIBLE);
        overlayBar.setVisibility(VISIBLE);
        overlayTitle.setText(title);
        overlaySub.setText(sub);
    }

    private void failOverlay(String msg) {
        overlayBar.setVisibility(GONE);
        overlayTitle.setText("Couldn't start");
        overlaySub.setText(msg == null ? "Unknown error" : msg);
        overlay.setVisibility(VISIBLE);
    }

    private void load() {
        overlay.setVisibility(GONE);
        web.loadUrl(CsUtil.webUrl(name));
    }

    public void reload() { if (name.isEmpty()) return; if (web.getUrl() == null) open(stub()); else web.reload(); }

    private JSONObject stub() { try { return new JSONObject().put("name", name).put("state", state).put("repository", new JSONObject().put("full_name", repo)); } catch (Exception e) { return new JSONObject(); } }

    public boolean back() {
        if (previewBox.getVisibility() == VISIBLE) { previewBox.setVisibility(GONE); return true; }
        if (padOpen) { collapse(); return true; }
        return false;   // VS Code Web is a single-page app; leave the screen instead of navigating history
    }

    public void destroy() { poll++; pad.stop(); try { web.destroy(); preview.destroy(); } catch (Exception ignored) { } }

    public void resume() { try { web.resumeTimers(); web.onResume(); } catch (Exception ignored) { } }

    // ------------------------------------------------------------------------------------ viewport / panels
    public void applyViewport() { eval(CsJs.viewport(Snippets.layoutWidth(store))); }

    public void layoutPicker() {
        final int[] w = {600, 800, 1000, 1280, 0};
        String[] l = {"Phone  (600 px)", "Compact  (800 px)", "Tablet  (1000 px)", "Desktop  (1280 px)", "Auto (device width)"};
        Ui.pickList(a, "VS Code layout width", l, i -> { Snippets.layoutWidth(store, w[i]); applyViewport(); });
    }

    public void togglePad() { padOpen = !padOpen; applyPad(); if (padOpen) pad.show(0); }
    public boolean padOpen() { return padOpen; }
    public void showPadTab(int t) { padOpen = true; applyPad(); pad.show(t); }

    private void applyPad() {
        pad.setVisibility(padOpen ? VISIBLE : GONE);
        int h = getHeight() > 0 ? getHeight() : a.getResources().getDisplayMetrics().heightPixels;
        LayoutParams lp = (LayoutParams) pad.getLayoutParams();
        lp.height = (int) (h * (padFull ? 0.72 : 0.42));
        pad.setLayoutParams(lp);
    }

    public void togglePadSize() { padFull = !padFull; applyPad(); }
    public void toggleKeys() { keysOn = !keysOn; keyScroll.setVisibility(keysOn ? VISIBLE : GONE); }

    @Override public void collapse() { padOpen = false; applyPad(); }

    // ------------------------------------------------------------------------------------ TermPad.Host
    @Override public void sendText(final String text, final boolean enter) {
        focusIfNeeded();
        if (!text.isEmpty()) eval(CsJs.paste(text));
        if (enter) GitPushApp.get().main.postDelayed(() -> eval(CsJs.key("Enter", "Enter", 13, false, false, false)), 350);
    }

    @Override public void ctrl(char letter) { eval(CsJs.ctrlLetter(letter)); }

    @Override public void focusTerminal() { eval(CsJs.key("`", "Backquote", 192, true, false, false)); }

    @Override public void openPort(String url) {
        previewUrl.setText(url);
        previewBox.setVisibility(VISIBLE);
        preview.loadUrl(url);
    }

    /** Terminal-bound text must reach the xterm textarea: if nothing editable is focused, open the terminal first. */
    private void focusIfNeeded() {
        eval(CsJs.INSTALL + "(function(){var t=document.querySelector('.xterm-helper-textarea');if(t&&document.activeElement!==t){var x=document.querySelector('.terminal-wrapper')||document.querySelector('.xterm');if(x){x.click();}t.focus();}})();");
    }

    private void eval(String js) { web.evaluateJavascript(js, null); }

    // ------------------------------------------------------------------------------------ key bar
    private void buildKeys() {
        key("Esc", "Escape", "Escape", 27);
        key("Tab", "Tab", "Tab", 9);
        keyBox.addView(mod("Ctrl", v -> ctrlGrid()));
        altBtn = mod("Alt", null);
        altBtn.setOnClickListener(v -> { altOn = !altOn; paintMods(); });
        keyBox.addView(altBtn);
        shiftBtn = mod("Shift", null);
        shiftBtn.setOnClickListener(v -> { shiftOn = !shiftOn; paintMods(); });
        keyBox.addView(shiftBtn);
        key("↑", "ArrowUp", "ArrowUp", 38);
        key("↓", "ArrowDown", "ArrowDown", 40);
        key("←", "ArrowLeft", "ArrowLeft", 37);
        key("→", "ArrowRight", "ArrowRight", 39);
        key("Home", "Home", "Home", 36);
        key("End", "End", "End", 35);
        key("PgUp", "PageUp", "PageUp", 33);
        key("PgDn", "PageDown", "PageDown", 34);
        key("Del", "Delete", "Delete", 46);
        for (String s : new String[]{"|", "~", "/", "\\", "{", "}", "[", "]", "(", ")", "<", ">", ";", ":", "'", "\"", "`", "-", "_", "=", "$", "&", "*", "#", "@", "!", "?", "+", "%", "^"}) sym(s);
        keyBox.addView(act("^C", v -> ctrl('c')));
        keyBox.addView(act("^D", v -> ctrl('d')));
        keyBox.addView(act("^L", v -> ctrl('l')));
        keyBox.addView(act("^R", v -> ctrl('r')));
        keyBox.addView(act("^Z", v -> ctrl('z')));
        keyBox.addView(act("Palette", v -> { eval(CsJs.key("F1", "F1", 112, false, false, false)); }));
        keyBox.addView(act("Terminal", v -> focusTerminal()));
        keyBox.addView(act("Copy", v -> copySelection()));
        keyBox.addView(act("Paste", v -> pasteClipboard()));
        keyBox.addView(act("Pad", v -> togglePad()));
    }

    private TextView mod(String label, OnClickListener l) {
        TextView t = act(label, l);
        return t;
    }

    private void paintMods() {
        altBtn.setBackgroundResource(altOn ? R.drawable.bg_chip_on : R.drawable.bg_chip_off);
        altBtn.setTextColor(Ui.color(a, altOn ? R.attr.gpOnPrimary : R.attr.gpText));
        shiftBtn.setBackgroundResource(shiftOn ? R.drawable.bg_chip_on : R.drawable.bg_chip_off);
        shiftBtn.setTextColor(Ui.color(a, shiftOn ? R.attr.gpOnPrimary : R.attr.gpText));
    }

    private TextView act(String label, OnClickListener l) {
        TextView t = Ui.text(a, label, 13, true, R.attr.gpText);
        t.setTypeface(Ui.mono(a, true));
        t.setBackgroundResource(R.drawable.bg_chip_off);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(Ui.dp(a, 42));
        t.setPadding(Ui.dp(a, 11), Ui.dp(a, 9), Ui.dp(a, 11), Ui.dp(a, 9));
        LayoutParams lp = new LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.rightMargin = Ui.dp(a, 5);
        t.setLayoutParams(lp);
        if (l != null) t.setOnClickListener(v -> { Ui.haptic(v); l.onClick(v); });
        return t;
    }

    private void key(String label, final String k, final String code, final int kc) {
        keyBox.addView(act(label, v -> {
            eval(CsJs.key(k, code, kc, false, altOn, shiftOn));
            if (altOn || shiftOn) { altOn = false; shiftOn = false; paintMods(); }
        }));
    }

    private void sym(final String s) { keyBox.addView(act(s, v -> eval(CsJs.text(s)))); }

    private void ctrlGrid() {
        final GlassDialog[] dd = new GlassDialog[1];
        LinearLayout box = Ui.vbox(a);
        LinearLayout row = null;
        for (int i = 0; i < 26; i++) {
            if (i % 6 == 0) { row = Ui.hbox(a); box.addView(row, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0)); }
            final char c = (char) ('a' + i);
            TextView t = Ui.chip(a, "^" + Character.toUpperCase(c), false, v -> { ctrl(c); if (dd[0] != null) dd[0].dismiss(); });
            row.addView(t, Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        }
        dd[0] = new GlassDialog(a).setTitle("Press Ctrl + …").setView(box).setNegativeButton("Close", null);
        dd[0].show();
    }

    private void copySelection() {
        web.evaluateJavascript(CsJs.SELECTION, v -> {
            String t = v == null || v.equals("null") ? "" : unquote(v);
            if (t.isEmpty()) { eval(CsJs.ctrlLetter('c')); Ui.toast(a, "Sent Ctrl+C (copies in the editor). Use the Output tab for long-press copy."); }
            else { Ui.copy(a, "selection", t); Ui.toast(a, "Copied"); }
        });
    }

    private static String unquote(String js) {
        try { return new org.json.JSONArray("[" + js + "]").getString(0); } catch (Exception e) { return ""; }
    }

    private void pasteClipboard() {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) { Ui.toast(a, "Clipboard is empty"); return; }
        CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(a);
        if (t == null || t.length() == 0) { Ui.toast(a, "Clipboard is empty"); return; }
        eval(CsJs.paste(t.toString()));
    }

    // ------------------------------------------------------------------------------------ shared menu
    /** "More" menu used by the phone screen and the tablet pane. [onStopped] runs after the codespace was asked to stop. */
    public void showMenu(final Runnable onStopped) {
        if (name.isEmpty()) return;
        final boolean sol = Snippets.stopOnLeave(store);
        String[] items = {"🖥  VS Code layout width", keysOn ? "⌨  Hide key bar" : "⌨  Show key bar", "💻  Terminal pad: " + (padFull ? "smaller" : "taller"),
                (sol ? "✔" : "☐") + "  Stop when I leave the editor", "⏱  Still-running reminder", "🌐  Open in browser", "📋  Copy VS Code URL", "⏹  Stop this codespace"};
        Ui.pickList(a, "Editor options", items, i -> {
            if (i == 0) layoutPicker();
            else if (i == 1) toggleKeys();
            else if (i == 2) togglePadSize();
            else if (i == 3) { Snippets.stopOnLeave(store, !sol); Ui.toast(a, !sol ? "Will stop the codespace when you leave" : "Stays running when you leave"); }
            else if (i == 4) remindPicker();
            else if (i == 5) Ui.openUrl(a, CsUtil.webUrl(name));
            else if (i == 6) { Ui.copy(a, "url", CsUtil.webUrl(name)); Ui.toast(a, "Copied"); }
            else Ui.confirm(a, "Stop codespace?", "Unsaved terminal processes end. Files and git changes stay.", "Stop", () -> {
                final CodespacesApi api = new CodespacesApi(GitPushApp.get().state.api());
                final String n = name;
                Ui.async(() -> api.stop(n), x -> { Ui.toast(a, "Stopping…"); com.gitpush.app.CsReminderReceiver.cancel(a, n); if (onStopped != null) onStopped.run(); }, e -> Ui.toast(a, e.getMessage()));
            });
        });
    }

    public void remindPicker() {
        final int[] v = {0, 30, 60, 120, 240};
        String[] l = {"Off", "After 30 minutes", "After 1 hour", "After 2 hours", "After 4 hours"};
        Ui.pickList(a, "Remind me while a codespace keeps running", l, i -> {
            Snippets.remindMinutes(store, v[i]);
            if (v[i] > 0 && !name.isEmpty()) com.gitpush.app.CsReminderReceiver.schedule(a, name, v[i]); else com.gitpush.app.CsReminderReceiver.cancel(a, name);
            Ui.toast(a, v[i] == 0 ? "Reminders off" : "Reminder set");
        });
    }
}
