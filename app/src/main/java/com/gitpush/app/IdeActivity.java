package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.AiClient;
import com.gitpush.app.data.ApiException;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.FileData;
import com.gitpush.app.data.Models.RefResult;
import com.gitpush.app.data.Pusher;
import com.gitpush.app.data.Stager;
import com.gitpush.app.ide.CodeEditText;
import com.gitpush.app.ide.IdePrefs;
import com.gitpush.app.ide.Lint;
import com.gitpush.app.ide.WorkTree;
import com.gitpush.app.ide.WorkTree.Node;
import com.gitpush.app.ide.WorkTree.Row;
import com.gitpush.app.ui.AiSettings;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Downloader;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Rich;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.BracketLint;
import com.gitpush.app.util.PathUtils;
import com.gitpush.app.util.ZipRules;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.TreeSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Code IDE for a GitHub repository: glass file explorer, tabs, fast code editor with pinch-zoom, error marks,
 * Markdown / HTML preview, file operations (create, rename, move, delete, import, export) and one-commit saving.
 */
public class IdeActivity extends BaseActivity {
    private static final int REQ_EXPORT_FILE = 801, REQ_EXPORT_ZIP = 802, REQ_IMPORT = 803;

    public static void open(Context c, String owner, String repo, String openPath) {
        c.startActivity(new Intent(c, IdeActivity.class).putExtra("owner", owner).putExtra("repo", repo).putExtra("path", openPath));
    }

    private static final class Tab {
        String path, kind = "loading";
        CodeEditText.History hist = new CodeEditText.History();
        int cursor;
        boolean preview, jsOn, dirty;
        Bitmap bmp;
        long size;
        Tab(String p) { path = p; }
    }

    private String owner, repo, branch = "";
    private GitHubApi api;
    private WorkTree tree;
    private final Set<String> expanded = new HashSet<>();
    private String selectedDir = "";
    private final List<Tab> tabs = new ArrayList<>();
    private Tab cur;
    private final List<WorkTree.Row> rows = new ArrayList<>();
    private List<BracketLint.Issue> issues = new ArrayList<>();

    private CodeEditText editor;
    private FrameLayout stage;
    private TextView titleTv, subTv, statusTv, problemsTv, previewChip, jsChip;
    private ImageView undoIv, redoIv, saveIv, previewIv;
    // bulk selection in the explorer
    private boolean selectMode;
    private final Set<String> picked = new LinkedHashSet<>();
    private LinearLayout selBar;
    private TextView selInfo;
    private LinearLayout tabBar;
    private HorizontalScrollView tabScroll, symbolBar;
    private FrameLayout explorerLayer;
    private LinearLayout explorerPanel;
    private View scrim;
    private ListView treeList;
    private BaseAdapter treeAdapter;
    private EditText treeSearch;
    private TextView changeInfo;
    private boolean explorerOpen, editorShown;
    private int panelW;
    private final android.os.Handler lintH = new android.os.Handler();
    private final Runnable lintRun = this::runLint;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        com.gitpush.app.data.Models.Repo r = st.findRepo(owner, repo);
        if (st.browserBranch != null) branch = st.browserBranch;
        else if (r != null) branch = r.defaultBranch;
        buildUi();
        loadTree(null, getIntent().getStringExtra("path"));
    }

    // ================================================================= UI
    private ImageView tool(int res, View.OnClickListener l) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setColorFilter(Ui.color(this, R.attr.gpText));
        v.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        v.setBackgroundResource(R.drawable.bg_ripple_row);
        v.setOnClickListener(x -> { Ui.haptic(x); l.onClick(x); });
        v.setLayoutParams(Ui.lp(Ui.dp(this, 36), Ui.dp(this, 36)));
        return v;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        LinearLayout main = Ui.vbox(this);
        root.addView(main, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // ---- top bar (small icons)
        LinearLayout top = Ui.hbox(this);
        top.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        top.addView(tool(R.drawable.i_menu, v -> toggleExplorer(!explorerOpen)));
        LinearLayout titles = Ui.vbox(this);
        titleTv = Ui.text(this, repo, 14, true, R.attr.gpText);
        titleTv.setSingleLine(true);
        subTv = Ui.label(this, owner + " • " + branch);
        subTv.setSingleLine(true);
        titles.addView(titleTv);
        titles.addView(subTv);
        top.addView(titles, Ui.weight(1f));
        undoIv = tool(R.drawable.i_undo, v -> { if (editorShown) editor.undo(); updateToolbar(); });
        redoIv = tool(R.drawable.i_redo, v -> { if (editorShown) editor.redo(); updateToolbar(); });
        saveIv = tool(R.drawable.i_save, v -> save(true));
        previewIv = tool(R.drawable.i_eye, v -> togglePreview());
        previewIv.setVisibility(View.GONE);
        top.addView(undoIv); top.addView(redoIv); top.addView(previewIv); top.addView(saveIv);
        top.addView(tool(R.drawable.i_upload, v -> commitDialog()));
        top.addView(tool(R.drawable.i_more, v -> moreMenu()));
        main.addView(top, Ui.lp(Ui.MATCH, Ui.WRAP));
        View line = new View(this);
        line.setBackgroundColor(Ui.color(this, R.attr.gpOutline));
        main.addView(line, Ui.lp(Ui.MATCH, 1));

        // ---- file tabs (one per file, one line)
        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabBar = Ui.hbox(this);
        tabBar.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        tabScroll.addView(tabBar);
        main.addView(tabScroll, Ui.lp(Ui.MATCH, Ui.dp(this, 40)));
        View line2 = new View(this);
        line2.setBackgroundColor(Ui.color(this, R.attr.gpOutline));
        main.addView(line2, Ui.lp(Ui.MATCH, 1));

        // ---- editor stage
        stage = new FrameLayout(this);
        main.addView(stage, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        editor = new CodeEditText(this);
        editor.setListener(new CodeEditText.Listener() {
            @Override public void onZoom(float sp) { IdePrefs.font(sp); }
            @Override public void onCursor(int ln, int col) { setStatus(ln, col); }
            @Override public void onEdited() {
                if (cur != null && !cur.dirty) { cur.dirty = true; renderTabs(); }
                updateToolbar();
                lintH.removeCallbacks(lintRun);
                if (IdePrefs.lint()) lintH.postDelayed(lintRun, 700);
            }
        });

        // ---- symbols bar
        symbolBar = new HorizontalScrollView(this);
        symbolBar.setHorizontalScrollBarEnabled(false);
        LinearLayout keys = Ui.hbox(this);
        keys.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        String[] sym = {"TAB", "{", "}", "(", ")", "[", "]", ";", "\"", "'", "<", ">", "/", "=", ":", "!", "&", "|", "_", "#", "$", "*", "+", "-"};
        for (final String s : sym) {
            TextView k = Ui.label(this, s);
            k.setTextSize(12);
            k.setTextColor(Ui.color(this, R.attr.gpText));
            k.setGravity(Gravity.CENTER);
            k.setBackgroundResource(R.drawable.bg_chip_off);
            k.setOnClickListener(v -> { if (editorShown) editor.insertAtCursor(s.equals("TAB") ? editor.tabString() : s); });
            keys.addView(k, Ui.lp(this, Ui.dp(this, s.length() > 1 ? 52 : 38), Ui.dp(this, 34), 0, 0, 6, 0));
        }
        symbolBar.addView(keys);
        main.addView(symbolBar, Ui.lp(Ui.MATCH, Ui.WRAP));

        // ---- status bar
        LinearLayout sb = Ui.hbox(this);
        sb.setBackgroundColor(Ui.color(this, R.attr.gpSurface));
        sb.setPadding(Ui.dp(this, 12), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));
        statusTv = Ui.label(this, "");
        sb.addView(statusTv, Ui.weight(1f));
        problemsTv = Ui.label(this, "");
        problemsTv.setTextColor(0xFFE5484D);
        problemsTv.setOnClickListener(v -> showProblems());
        problemsTv.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        sb.addView(problemsTv);
        jsChip = Ui.label(this, "JS off");
        jsChip.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        jsChip.setOnClickListener(v -> { if (cur != null) { cur.jsOn = !cur.jsOn; showTab(cur); } });
        jsChip.setVisibility(View.GONE);
        sb.addView(jsChip);
        previewChip = Ui.label(this, "Preview");
        previewChip.setTextColor(Ui.color(this, R.attr.gpText));
        previewChip.setBackgroundResource(R.drawable.bg_chip_off);
        previewChip.setPadding(Ui.dp(this, 10), Ui.dp(this, 3), Ui.dp(this, 10), Ui.dp(this, 3));
        previewChip.setOnClickListener(v -> togglePreview());
        previewChip.setVisibility(View.GONE);
        sb.addView(previewChip);
        View line3 = new View(this);
        line3.setBackgroundColor(Ui.color(this, R.attr.gpOutline));
        main.addView(line3, Ui.lp(Ui.MATCH, 1));
        main.addView(sb, Ui.lp(Ui.MATCH, Ui.WRAP));

        buildExplorer(root);
        setContentView(root);
        showEmpty();
        renderTabs();
    }

    private void buildExplorer(FrameLayout root) {
        explorerLayer = new FrameLayout(this);
        explorerLayer.setVisibility(View.GONE);
        scrim = new View(this);
        scrim.setBackgroundColor(0x66000000);
        scrim.setOnClickListener(v -> toggleExplorer(false));
        explorerLayer.addView(scrim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        int sw = getResources().getDisplayMetrics().widthPixels;
        panelW = Math.min(Ui.dp(this, 330), (int) (sw * 0.86f));
        explorerPanel = Ui.vbox(this);
        explorerPanel.setBackgroundColor(Ui.color(this, R.attr.gpSurface));
        explorerPanel.setElevation(Ui.dp(this, 16));
        explorerPanel.setClickable(true);
        explorerLayer.addView(explorerPanel, new FrameLayout.LayoutParams(panelW, Ui.MATCH, Gravity.START));
        root.addView(explorerLayer, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        LinearLayout head = Ui.hbox(this);
        head.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 4));
        LinearLayout ht = Ui.vbox(this);
        ht.addView(Ui.label(this, "Explorer"));
        TextView rn = Ui.text(this, repo, 15, true, R.attr.gpText);
        rn.setSingleLine(true);
        ht.addView(rn);
        head.addView(ht, Ui.weight(1f));
        head.addView(tool(R.drawable.i_square_check, v -> setSelectMode(!selectMode)));
        head.addView(tool(R.drawable.i_file_plus, v -> createMenu(selectedDir)));
        head.addView(tool(R.drawable.i_import, v -> importHere(selectedDir)));
        head.addView(tool(R.drawable.i_refresh, v -> reloadConfirm()));
        explorerPanel.addView(head);
        buildSelBar();
        treeSearch = Ui.input(this, "Search files", "");
        treeSearch.setSingleLine(true);
        treeSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            public void onTextChanged(CharSequence s, int a, int c, int d) { }
            public void afterTextChanged(Editable e) { refreshTree(); }
        });
        explorerPanel.addView(treeSearch, Ui.lp(this, Ui.MATCH, Ui.WRAP, 12, 4, 12, 6));
        treeList = new ListView(this);
        treeList.setDivider(null);
        treeList.setSelector(android.R.color.transparent);
        treeAdapter = new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int i) { return rows.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { return treeRow(rows.get(i)); }
        };
        treeList.setAdapter(treeAdapter);
        explorerPanel.addView(treeList, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        View ln = new View(this);
        ln.setBackgroundColor(Ui.color(this, R.attr.gpOutline));
        explorerPanel.addView(ln, Ui.lp(Ui.MATCH, 1));
        LinearLayout foot = Ui.hbox(this);
        foot.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 10));
        changeInfo = Ui.label(this, "No changes");
        foot.addView(changeInfo, Ui.weight(1f));
        foot.addView(Ui.button(this, "Commit", v -> { toggleExplorer(false); commitDialog(); }));
        explorerPanel.addView(foot);
    }

    private void toggleExplorer(boolean open) {
        if (open == explorerOpen) return;
        explorerOpen = open;
        if (open) {
            refreshTree();
            explorerLayer.setVisibility(View.VISIBLE);
            explorerPanel.setTranslationX(-panelW);
            scrim.setAlpha(0f);
            explorerPanel.animate().translationX(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            scrim.animate().alpha(1f).setDuration(240).start();
        } else {
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(treeSearch.getWindowToken(), 0);
            explorerPanel.animate().translationX(-panelW).setDuration(200).start();
            scrim.animate().alpha(0f).setDuration(200).withEndAction(() -> explorerLayer.setVisibility(View.GONE)).start();
        }
    }

    // ---------------------------------------------------------------- explorer rows
    private void refreshTree() {
        if (tree == null) return;
        rows.clear();
        rows.addAll(tree.rows(expanded, treeSearch == null ? "" : treeSearch.getText().toString()));
        treeAdapter.notifyDataSetChanged();
        int n = tree.changeCount();
        changeInfo.setText(n == 0 ? "No changes" : n + (n == 1 ? " change" : " changes"));
        updateSelInfo();
    }

    private View treeRow(final Row r) {
        LinearLayout row = Ui.hbox(this);
        row.setPadding(Ui.dp(this, 10 + r.depth * 14), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        row.setMinimumHeight(Ui.dp(this, 38));
        boolean active = cur != null && cur.path.equals(r.path);
        boolean isPicked = selectMode && picked.contains(r.path);
        row.setBackgroundResource(active || isPicked ? R.drawable.bg_tab_on : R.drawable.bg_ripple_row);
        if (selectMode) row.addView(Ui.iconRes(this, isPicked ? R.drawable.i_square_check : R.drawable.i_square, 18, R.attr.gpText), Ui.lp(this, Ui.dp(this, 18), Ui.dp(this, 18), 0, 0, 8, 0));
        if (r.dir) {
            ImageView chev = Ui.iconRes(this, r.open ? R.drawable.i_chevron_down : R.drawable.i_chevron_right, 14, R.attr.gpMuted);
            if (selectMode) { chev.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6)); chev.setOnClickListener(v -> { if (!expanded.remove(r.path)) expanded.add(r.path); refreshTree(); }); }
            row.addView(chev, selectMode ? Ui.lp(this, Ui.dp(this, 26), Ui.dp(this, 26), 0, 0, 2, 0) : Ui.lp(this, Ui.dp(this, 14), Ui.dp(this, 14), 0, 0, 6, 0));
            row.addView(Ui.iconRes(this, r.open ? R.drawable.i_folder_open : R.drawable.i_folder, 18, R.attr.gpText), Ui.lp(this, Ui.dp(this, 18), Ui.dp(this, 18), 0, 0, 8, 0));
        } else {
            if (!selectMode) row.addView(new View(this), Ui.lp(Ui.dp(this, 20), 1));
            row.addView(extBadge(r.name), Ui.lp(this, Ui.dp(this, 26), Ui.dp(this, 22), 0, 0, 8, 0));
        }
        TextView name = Ui.text(this, r.name, 13, !r.dir && false, R.attr.gpText);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(name, Ui.weight(1f));
        if (r.node != null && r.node.changed()) row.addView(Ui.iconRes(this, R.drawable.i_dot, 10, R.attr.gpText), Ui.lp(this, Ui.dp(this, 10), Ui.dp(this, 10), 6, 0, 0, 0));
        row.setOnClickListener(v -> {
            if (selectMode) { if (!picked.remove(r.path)) picked.add(r.path); refreshTree(); return; }
            if (r.dir) { if (!expanded.remove(r.path)) expanded.add(r.path); selectedDir = r.path; refreshTree(); }
            else { selectedDir = PathUtils.parent(r.path); openFile(r.path); toggleExplorer(false); }
        });
        row.setOnLongClickListener(v -> { Ui.haptic(v); if (selectMode) { if (!picked.remove(r.path)) picked.add(r.path); refreshTree(); } else itemMenu(r); return true; });
        return row;
    }

    // ---------------------------------------------------------------- bulk selection
    private void buildSelBar() {
        selBar = Ui.vbox(this);
        selBar.setVisibility(View.GONE);
        selBar.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 6));
        LinearLayout r1 = Ui.hbox(this);
        selInfo = Ui.label(this, "Nothing selected");
        r1.addView(selInfo, Ui.weight(1f));
        r1.addView(Ui.textButton(this, "All", R.attr.gpText, v -> { for (Row x : rows) picked.add(x.path); refreshTree(); }));
        r1.addView(Ui.textButton(this, "None", R.attr.gpText, v -> { picked.clear(); refreshTree(); }));
        r1.addView(Ui.textButton(this, "Invert", R.attr.gpText, v -> { for (Row x : rows) if (!picked.remove(x.path)) picked.add(x.path); refreshTree(); }));
        selBar.addView(r1);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout acts = Ui.hbox(this);
        String[] labels = {"⬇ Download", "📂 Move", "📋 Copy to", "🗑 Delete", "🔍 By pattern", "🔗 Copy paths", "✕ Done"};
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            acts.addView(Ui.chip(this, labels[i], i == 0, v -> bulkAction(k)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        }
        hs.addView(acts);
        selBar.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        explorerPanel.addView(selBar);
    }

    private void setSelectMode(boolean on) {
        selectMode = on;
        if (!on) picked.clear();
        selBar.setVisibility(on ? View.VISIBLE : View.GONE);
        refreshTree();
    }

    private List<Node> pickedFiles() {
        LinkedHashMap<String, Node> out = new LinkedHashMap<>();
        if (tree == null) return new ArrayList<>();
        for (String p : picked) {
            Node f = tree.file(p);
            if (f != null) { out.put(f.path, f); continue; }
            String pre = p + "/";
            for (Node n : tree.all()) if (n.path.startsWith(pre)) out.put(n.path, n);
        }
        return new ArrayList<>(out.values());
    }

    private void updateSelInfo() {
        if (selInfo == null) return;
        if (picked.isEmpty()) { selInfo.setText("Tap items to select"); return; }
        selInfo.setText(picked.size() + " selected  •  " + pickedFiles().size() + " files");
    }

    private void bulkAction(int k) {
        if (k == 6) { setSelectMode(false); return; }
        if (k == 4) {
            Ui.prompt(this, "Select by pattern", "*.java, src/**/*.xml, !*.png", "", g -> {
                String pat = g.trim();
                if (pat.isEmpty()) return;
                int n = 0;
                for (Node f : tree.all()) if (ZipRules.matchesGlobs(pat, f.path)) { picked.add(f.path); n++; }
                Ui.toast(this, n + " file(s) matched");
                refreshTree();
            });
            return;
        }
        if (picked.isEmpty()) { Ui.toast(this, "Select something first"); return; }
        syncCurrent();
        final List<String> paths = new ArrayList<>(picked);
        if (k == 0) bulkDownload();
        else if (k == 1) pickFolder("Move " + paths.size() + " item(s) to", dest -> bulkMove(paths, dest));
        else if (k == 2) pickFolder("Copy " + paths.size() + " item(s) to", dest -> bulkCopy(dest));
        else if (k == 3) Ui.confirmDanger(this, "Delete " + paths.size() + " item(s)?", pickedFiles().size() + " file(s) are removed from the workspace (committed with your next commit).", "Delete", () -> {
            for (String p : paths) {
                mutateQuiet(() -> tree.delete(p));
                for (int i = tabs.size() - 1; i >= 0; i--) { String tp = tabs.get(i).path; if (tp.equals(p) || tp.startsWith(p + "/")) closeTab(tabs.get(i)); }
            }
            picked.clear(); refreshTree(); renderTabs(); Ui.toast(this, "Deleted");
        });
        else if (k == 5) {
            StringBuilder sb = new StringBuilder();
            for (Node f : pickedFiles()) sb.append(f.path).append('\n');
            Ui.copy(this, "paths", sb.toString().trim()); Ui.toast(this, "Paths copied");
        }
    }

    private void mutateQuiet(Op op) { try { op.run(); } catch (IllegalArgumentException e) { Ui.toast(this, e.getMessage()); } }

    private void bulkDownload() {
        final List<Node> files = pickedFiles();
        if (files.isEmpty()) { Ui.toast(this, "No files in the selection"); return; }
        if (files.size() == 1 && picked.size() == 1 && tree.file(picked.iterator().next()) != null) {
            final Node n = files.get(0);
            Downloader.requestWrite(this, n.name(), (os, cb) -> os.write(contentOf(n)));
            return;
        }
        long tot = 0; for (Node n : files) tot += Math.max(n.size, 1);
        final long total = tot;
        String single = picked.size() == 1 ? PathUtils.baseName(picked.iterator().next()) : repo + "-selection";
        Downloader.requestWrite(this, single + ".zip", (os, cb) -> {
            long done = 0;
            ZipOutputStream zo = new ZipOutputStream(os);
            for (Node n : files) {
                zo.putNextEntry(new ZipEntry(n.path));
                byte[] b = contentOf(n);
                zo.write(b);
                zo.closeEntry();
                done += Math.max(n.size, 1);
                cb.on(Math.min(done, total), total);
            }
            zo.finish();
        });
    }

    private void bulkMove(List<String> paths, String dest) {
        int ok = 0, bad = 0; String err = "";
        for (String p : paths) {
            String to = join(dest, PathUtils.baseName(p));
            try { moveTo(p, to); ok++; } catch (IllegalArgumentException e) { bad++; err = e.getMessage(); }
        }
        picked.clear(); if (!dest.isEmpty()) expanded.add(dest);
        refreshTree(); renderTabs();
        Ui.toast(this, ok + " moved" + (bad > 0 ? ", " + bad + " skipped (" + err + ")" : ""));
    }

    private void bulkCopy(String dest) {
        int ok = 0, bad = 0;
        for (Node f : pickedFiles()) {
            // keep the folder structure of a selected folder below the destination
            String rel = f.name();
            for (String p : picked) if (f.path.startsWith(p + "/")) { rel = PathUtils.baseName(p) + f.path.substring(p.length()); break; }
            try { tree.duplicate(f.path, join(dest, rel)); ok++; } catch (IllegalArgumentException e) { bad++; }
        }
        picked.clear(); if (!dest.isEmpty()) expanded.add(dest);
        refreshTree();
        Ui.toast(this, ok + " copied" + (bad > 0 ? ", " + bad + " skipped (name exists)" : ""));
    }

    /** Folder chooser: root, every existing folder, or a typed path. */
    private void pickFolder(String title, final Ui.Cb<String> cb) {
        List<String> paths = new ArrayList<>();
        for (Node n : tree.all()) paths.add(n.path);
        final List<String> dirs = new ArrayList<>(new TreeSet<>(ZipRules.allFolders(paths, 400)));
        final String[] labels = new String[dirs.size() + 2];
        labels[0] = "📁  / (repository root)";
        labels[1] = "✏  Type a path…";
        for (int i = 0; i < dirs.size(); i++) labels[i + 2] = "📂  " + dirs.get(i);
        Ui.pickList(this, title, labels, i -> {
            if (i == 0) cb.on("");
            else if (i == 1) Ui.prompt(this, title, "folder/sub-folder", selectedDir, t -> cb.on(PathUtils.trimSlashes(t.trim().replace("\\", "/"))));
            else cb.on(dirs.get(i - 2));
        });
    }

    private TextView extBadge(String name) {
        TextView t = new TextView(this);
        t.setText(com.gitpush.app.ui.Icons.extBadge(name));
        t.setTextSize(7);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setTextColor(Ui.color(this, R.attr.gpText));
        Typeface f = Ui.mono(this, true);
        if (f != null) t.setTypeface(f);
        t.setBackgroundResource(R.drawable.bg_icon_tile);
        return t;
    }

    // ---------------------------------------------------------------- tabs
    private void renderTabs() {
        tabBar.removeAllViews();
        if (tabs.isEmpty()) {
            tabBar.addView(Ui.label(this, "No files open – tap the menu to browse"), Ui.lp(this, Ui.WRAP, Ui.WRAP, 8, 8, 0, 0));
            return;
        }
        for (final Tab t : tabs) {
            LinearLayout c = Ui.hbox(this);
            boolean active = t == cur;
            c.setBackgroundResource(active ? R.drawable.bg_tab_on : R.drawable.bg_ripple_row);
            c.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 4), 0);
            Node n = tree == null ? null : tree.file(t.path);
            boolean dirty = t.dirty || (n != null && n.changed());
            TextView nm = Ui.text(this, PathUtils.baseName(t.path), 12, active, active ? R.attr.gpText : R.attr.gpMuted);
            nm.setSingleLine(true);
            c.addView(nm);
            if (dirty) c.addView(Ui.iconRes(this, R.drawable.i_dot, 8, R.attr.gpText), Ui.lp(this, Ui.dp(this, 8), Ui.dp(this, 8), 5, 0, 0, 0));
            ImageView x = Ui.iconRes(this, R.drawable.i_x, 13, R.attr.gpMuted);
            x.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
            x.setLayoutParams(Ui.lp(Ui.dp(this, 26), Ui.dp(this, 26)));
            x.setOnClickListener(v -> closeTab(t));
            c.addView(x);
            c.setOnClickListener(v -> { if (t != cur) showTab(t); });
            tabBar.addView(c, Ui.lp(this, Ui.WRAP, Ui.dp(this, 32), 0, 0, 6, 0));
        }
    }

    private void closeTab(Tab t) {
        syncCurrent();
        int i = tabs.indexOf(t);
        tabs.remove(t);
        if (t == cur) {
            cur = null;
            if (tabs.isEmpty()) showEmpty(); else showTab(tabs.get(Math.max(0, Math.min(i, tabs.size() - 1))));
        }
        renderTabs();
    }

    // ---------------------------------------------------------------- open / show
    private void openFile(final String path) {
        if (tree == null) return;
        for (Tab t : tabs) if (t.path.equals(path)) { showTab(t); return; }
        final Node n = tree.file(path);
        if (n == null) { Ui.toast(this, "File not found: " + path); return; }
        String parent = PathUtils.parent(path);
        while (!parent.isEmpty()) { expanded.add(parent); parent = PathUtils.parent(parent); }
        final Tab t = new Tab(path);
        t.size = n.size;
        tabs.add(t);
        if (n.text != null) { t.kind = "text"; showTab(t); return; }
        if (n.importUri != null) { t.kind = PathUtils.isImage(n.name()) ? "image" : "binary"; showTab(t); return; }
        t.kind = "loading";
        showTab(t);
        async(() -> {
            FileData f = api.file(owner, repo, path, branch);
            Object[] r = new Object[3];
            r[0] = f;
            if (PathUtils.isImage(n.name())) r[1] = BitmapFactory.decodeByteArray(f.bytes, 0, f.bytes.length);
            else if (f.bytes.length <= 2_000_000) r[2] = Pusher.tryText(f.bytes);
            return r;
        }, r -> {
            FileData f = (FileData) r[0];
            t.size = f.bytes.length;
            if (r[1] != null) { t.kind = "image"; t.bmp = (Bitmap) r[1]; }
            else if (r[2] != null) { String txt = (String) r[2]; n.baseText = txt; if (n.text == null) n.text = txt; t.kind = "text"; }
            else t.kind = "binary";
            if (tabs.contains(t) && t == cur) showTab(t);
        }, e -> { Ui.toast(this, msg(e)); closeTab(t); });
    }

    private void showEmpty() {
        stage.removeAllViews();
        editorShown = false;
        LinearLayout e = Ui.empty(this, "💻", "Code IDE", "Open the explorer (menu icon) and pick a file to start editing.");
        e.setGravity(Gravity.CENTER);
        stage.addView(e, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH, Gravity.CENTER));
        titleTv.setText(repo);
        subTv.setText(owner + " • " + branch);
        symbolBar.setVisibility(View.GONE);
        previewChip.setVisibility(View.GONE); jsChip.setVisibility(View.GONE);
        statusTv.setText("");
        updateToolbar();
    }

    private void syncCurrent() {
        if (cur != null && cur.kind.equals("text") && editorShown && !cur.preview) {
            cur.cursor = editor.getSelectionStart();
            tree.setText(cur.path, editor.getText().toString());
        }
    }

    private boolean previewable(String name) { return PathUtils.isMarkdown(name) || PathUtils.extension(name).matches("html|htm|svg"); }

    private void showTab(Tab t) {
        syncCurrent();
        cur = t;
        stage.removeAllViews();
        editorShown = false;
        String name = PathUtils.baseName(t.path);
        titleTv.setText(name);
        subTv.setText(t.path);
        boolean canPrev = t.kind.equals("text") && previewable(name);
        previewChip.setVisibility(View.GONE);
        previewIv.setVisibility(canPrev ? View.VISIBLE : View.GONE);
        previewIv.setImageResource(t.preview ? R.drawable.i_code : R.drawable.i_eye);
        previewIv.setAlpha(1f);
        jsChip.setVisibility(canPrev && t.preview && !PathUtils.isMarkdown(name) ? View.VISIBLE : View.GONE);
        jsChip.setText(t.jsOn ? "JS on" : "JS off");
        symbolBar.setVisibility(View.GONE);
        problemsTv.setText("");
        Node n = tree.file(t.path);
        if (t.kind.equals("loading")) {
            TextView l = Ui.muted(this, "Loading " + name + "…");
            l.setGravity(Gravity.CENTER);
            stage.addView(l, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            statusTv.setText("LOADING");
        } else if (t.kind.equals("image")) {
            ImageView iv = new ImageView(this);
            iv.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
            if (t.bmp != null) iv.setImageBitmap(t.bmp);
            else if (n != null && n.importUri != null) iv.setImageURI(n.importUri);
            stage.addView(iv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            statusTv.setText("IMAGE • " + PathUtils.formatSize(t.size));
        } else if (t.kind.equals("binary")) {
            LinearLayout box = Ui.empty(this, "📦", name, "Binary file • " + PathUtils.formatSize(t.size) + "\nNot editable here – export it to your device.");
            box.addView(Ui.outlineButton(this, "⬇ Export", v -> exportFile(t.path)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 14, 0, 0));
            stage.addView(box, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH, Gravity.CENTER));
            statusTv.setText("BINARY • " + PathUtils.formatSize(t.size));
        } else if (t.preview && canPrev) showPreview(t, n);
        else showEditor(t, n);
        renderTabs();
        refreshTreeLight();
        updateToolbar();
    }

    private void refreshTreeLight() { if (explorerOpen) treeAdapter.notifyDataSetChanged(); }

    private void showEditor(Tab t, Node n) {
        editorShown = true;
        stage.addView(editor, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        editor.setFontSize(IdePrefs.font());
        editor.setLineNumbers(IdePrefs.lines());
        editor.setWrap(IdePrefs.wrap());
        editor.setColorSyntax(IdePrefs.color());
        editor.setAutoIndent(IdePrefs.indent());
        editor.setTabSize(IdePrefs.tab());
        editor.setExt(PathUtils.extension(PathUtils.baseName(t.path)));
        editor.setHistory(t.hist);
        editor.loadText(n != null && n.text != null ? n.text : "");
        editor.setSelection(Math.min(editor.getText().length(), Math.max(0, t.cursor)));
        symbolBar.setVisibility(IdePrefs.symbols() ? View.VISIBLE : View.GONE);
        setStatus(1, 1);
        editor.post(() -> { editor.requestFocus(); runLint(); });
    }

    private void showPreview(Tab t, Node n) {
        String text = n != null && n.text != null ? n.text : "";
        String name = PathUtils.baseName(t.path);
        statusTv.setText("PREVIEW • " + PathUtils.extension(name).toUpperCase());
        if (PathUtils.isMarkdown(name)) {
            ScrollView sv = new ScrollView(this);
            TextView tv = Ui.body(this, "");
            tv.setTextSize(14);
            tv.setText(Rich.markdown(this, text));
            tv.setLineSpacing(0, 1.2f);
            tv.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 40));
            tv.setTextIsSelectable(true);
            sv.addView(tv);
            stage.addView(sv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        } else {
            WebView wv = new WebView(this);
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(t.jsOn);
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            wv.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { Ui.openUrl(IdeActivity.this, r.getUrl().toString()); return true; }
            });
            String dir = PathUtils.parent(t.path);
            String base = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + (dir.isEmpty() ? "" : dir + "/");
            wv.loadDataWithBaseURL(base, text, "text/html", "UTF-8", null);
            stage.addView(wv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        }
    }

    private void togglePreview() {
        if (cur == null || !cur.kind.equals("text")) return;
        syncCurrent();
        cur.preview = !cur.preview;
        showTab(cur);
    }

    private void setStatus(int line, int col) {
        if (cur == null) return;
        statusTv.setText("LN " + line + ", COL " + col + " • " + PathUtils.extension(PathUtils.baseName(cur.path)).toUpperCase() + " • UTF-8");
    }

    private void updateToolbar() {
        boolean ed = editorShown;
        undoIv.setAlpha(ed && editor.canUndo() ? 1f : 0.3f);
        redoIv.setAlpha(ed && editor.canRedo() ? 1f : 0.3f);
        saveIv.setAlpha(ed ? 1f : 0.3f);
    }

    // ---------------------------------------------------------------- lint
    private void runLint() {
        if (!editorShown || cur == null || !IdePrefs.lint()) { editor.setErrorLines(new HashSet<Integer>()); problemsTv.setText(""); return; }
        final String ext = PathUtils.extension(PathUtils.baseName(cur.path)), text = editor.getText().toString();
        final Tab t = cur;
        app.io.execute(() -> {
            final List<BracketLint.Issue> found = Lint.run(ext, text);
            app.main.post(() -> {
                if (cur != t || !editorShown) return;
                issues = found;
                Set<Integer> lines = new HashSet<>();
                for (BracketLint.Issue i : found) lines.add(i.line);
                editor.setErrorLines(lines);
                problemsTv.setText(found.isEmpty() ? "" : found.size() + (found.size() == 1 ? " ERROR" : " ERRORS"));
            });
        });
    }

    private void showProblems() {
        if (issues.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (BracketLint.Issue i : issues) sb.append("Line ").append(i.line).append(":  ").append(i.message).append('\n');
        new GlassDialog(this).setTitle("Problems").setIcon("⚠").setMessage(sb.toString().trim()).setPositiveButton("Close", null)
                .setNeutralButton("Go to first", (d, w) -> goToLine(issues.get(0).line)).show();
    }

    // ================================================================= tree loading / saving
    private void loadTree(final Map<String, String> keepTexts, final String openAfter) {
        final ProgressDlg dlg = new ProgressDlg(this, "Opening repository", "📂", null);
        async(() -> {
            if (branch.isEmpty()) branch = api.repo(owner, repo).defaultBranch;
            RefResult ref = api.ref(owner, repo, branch);
            if (ref.kind != RefResult.FOUND) throw new ApiException(404, "This repository has no commits yet – push some files first.");
            return new WorkTree(api.treeRecursive(owner, repo, api.commitTree(owner, repo, ref.sha)));
        }, t -> {
            dlg.close();
            tree = t;
            subTv.setText(owner + " • " + branch);
            if (keepTexts != null) {
                for (Map.Entry<String, String> e : keepTexts.entrySet()) { Node n = tree.file(e.getKey()); if (n != null) { n.baseText = e.getValue(); n.text = e.getValue(); } }
                for (int i = tabs.size() - 1; i >= 0; i--) if (tree.file(tabs.get(i).path) == null) { if (tabs.get(i) == cur) cur = null; tabs.remove(i); }
                if (cur == null) { if (tabs.isEmpty()) showEmpty(); else showTab(tabs.get(0)); }
                else for (Tab tb : tabs) tb.dirty = false;
            }
            refreshTree();
            renderTabs();
            if (openAfter != null && !openAfter.isEmpty()) openFile(openAfter);
            else if (tabs.isEmpty()) toggleExplorer(true);
        }, e -> { dlg.close(); Ui.toast(this, msg(e)); finish(); });
    }

    private void reloadConfirm() {
        if (tree != null && tree.changeCount() > 0) Ui.confirmDanger(this, "Reload from GitHub?", "Your " + tree.changeCount() + " uncommitted change(s) will be discarded.", "Reload", this::reloadNow);
        else reloadNow();
    }

    private void reloadNow() {
        tabs.clear(); cur = null; showEmpty(); renderTabs();
        loadTree(null, null);
    }

    private void save(boolean toast) {
        if (!editorShown || cur == null) return;
        syncCurrent();
        cur.dirty = false;
        renderTabs(); refreshTree(); updateToolbar();
        if (toast) Ui.toast(this, "Saved to workspace – commit to push it to GitHub");
    }

    private void commitDialog() {
        if (tree == null) return;
        syncCurrent();
        final int n = tree.changeCount();
        if (n == 0) { Ui.toast(this, "No changes to commit"); return; }
        LinearLayout box = Ui.vbox(this);
        List<String> sum = tree.summary(12);
        StringBuilder sb = new StringBuilder();
        for (String s : sum) sb.append(s).append('\n');
        if (n > sum.size()) sb.append("… and ").append(n - sum.size()).append(" more");
        TextView list = Ui.text(this, sb.toString().trim(), 11, false, R.attr.gpMuted);
        list.setTypeface(Typeface.MONOSPACE);
        box.addView(list, Ui.lp(Ui.MATCH, Ui.WRAP));
        final EditText msg = Ui.input(this, "Commit message", n == 1 ? "Update " + PathUtils.baseName(sum.isEmpty() ? "file" : sum.get(0).substring(2).split("  ←")[0]) : "Update " + n + " files");
        msg.setSingleLine(true);
        box.addView(msg, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        final String summary = sb.toString();
        box.addView(Ui.chip(this, "✨ AI message", false, v -> aiMessage(msg, summary)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
        new GlassDialog(this).setTitle("Commit " + n + (n == 1 ? " change" : " changes")).setIcon("🚀").setView(box).setPositiveButton("Commit", (d, w) -> doCommit(msg.getText().toString().trim())).setNegativeButton("Cancel", null).show();
    }

    private void aiMessage(final EditText msg, final String summary) {
        if (!AiClient.configured(st.store)) { AiSettings.show(this, null); return; }
        final String old = msg.getText().toString();
        msg.setText("✨ writing…");
        async(() -> AiClient.complete(st.store, "Write ONE concise imperative git commit subject (max 72 chars, no quotes, no trailing period) for these changes (A=added, M=modified, R=renamed, D=deleted). Reply with the subject only.",
                owner + "/" + repo + "\n" + summary, 80), t -> msg.setText(AiClient.stripFence(t).split("\n")[0].trim()), e -> { msg.setText(old); Ui.toast(this, msg(e)); });
    }

    private void doCommit(final String message) {
        if (message.isEmpty()) { Ui.toast(this, "Enter a commit message"); return; }
        final ProgressDlg dlg = new ProgressDlg(this, "Committing", "🚀", () -> api.abort());
        KeepAliveService.begin(app, "Committing to " + repo);
        final GitHubApi a = new GitHubApi(st.active.token);
        final Map<String, String> texts = new HashMap<>();
        for (Tab t : tabs) { Node n = tree.file(t.path); if (n != null && n.text != null && t.kind.equals("text")) texts.put(t.path, n.text); }
        async(() -> tree.commit(this, a, owner, repo, branch, message), sha -> {
            KeepAliveService.end(app);
            dlg.close();
            Ui.toast(this, "Committed " + sha.substring(0, 7));
            loadTree(texts, null);
        }, e -> { KeepAliveService.end(app); dlg.close(); Ui.toast(this, msg(e)); });
    }

    // ================================================================= file operations
    private void createMenu(final String dir) {
        Ui.pickList(this, "Create in " + (dir.isEmpty() ? "/" : dir), new String[]{"📄  New file", "📁  New folder"}, i -> {
            if (i == 0) Ui.prompt(this, "New file", "name.ext  (or path/name.ext)", "", n -> mutate(() -> { String p = join(dir, n.trim()); WorkTree.Node node = tree.createFile(p, ""); expandTo(p); refreshTree(); openFile(p); }));
            else Ui.prompt(this, "New folder", "folder name", "", n -> mutate(() -> { String p = join(dir, n.trim()); tree.createFolder(p); expandTo(p + "/x"); refreshTree(); }));
        });
    }

    private interface Op { void run(); }

    private void mutate(Op op) {
        try { op.run(); } catch (IllegalArgumentException e) { Ui.toast(this, e.getMessage()); }
        refreshTree(); renderTabs();
    }

    private static String join(String dir, String name) { return dir.isEmpty() ? name : dir + "/" + name; }

    private void expandTo(String path) {
        String p = PathUtils.parent(path);
        while (!p.isEmpty()) { expanded.add(p); p = PathUtils.parent(p); }
    }

    private void itemMenu(final Row r) {
        final boolean dir = r.dir;
        final String path = r.path;
        List<String> items = new ArrayList<>();
        if (dir) { items.add("📄  New file here"); items.add("📁  New folder here"); items.add("📥  Import here"); }
        else { items.add("📂  Open"); }
        items.add("✏  Rename"); items.add("📂  Move / change path");
        if (!dir) items.add("📋  Duplicate");
        items.add("⬆  Export" + (dir ? " as ZIP" : " to device"));
        items.add("🔗  Copy path");
        items.add("☑  Select (bulk actions)");
        if (!dir && r.node != null && r.node.edited && !r.node.isNew) items.add("🧹  Discard my changes");
        items.add("🗑  Delete");
        final String[] arr = items.toArray(new String[0]);
        Ui.pickList(this, r.name, arr, i -> {
            String l = arr[i];
            if (l.contains("New file")) createMenuFile(path);
            else if (l.contains("New folder")) Ui.prompt(this, "New folder", "folder name", "", n -> mutate(() -> { tree.createFolder(join(path, n.trim())); expanded.add(path); }));
            else if (l.contains("Import here")) importHere(path);
            else if (l.contains("Open")) { openFile(path); toggleExplorer(false); }
            else if (l.contains("Rename")) Ui.prompt(this, "Rename", "new name", r.name, n -> mutate(() -> moveTo(path, join(PathUtils.parent(path), n.trim()))));
            else if (l.contains("Move")) Ui.prompt(this, "Move to path", "folder/new-name.ext", path, n -> mutate(() -> moveTo(path, n.trim().replace("\\", "/"))));
            else if (l.contains("Duplicate")) Ui.prompt(this, "Duplicate as", "path", path.replace(".", "-copy."), n -> mutate(() -> { syncCurrent(); tree.duplicate(path, n.trim()); expandTo(n.trim()); }));
            else if (l.contains("Export")) { if (dir) exportFolder(path); else exportFile(path); }
            else if (l.contains("Copy path")) { Ui.copy(this, "path", path); Ui.toast(this, "Path copied"); }
            else if (l.contains("Select (bulk")) { setSelectMode(true); picked.add(path); refreshTree(); }
            else if (l.contains("Discard")) mutate(() -> { tree.discard(path); for (Tab t : tabs) if (t.path.equals(path)) { t.dirty = false; if (t == cur) showTab(t); } });
            else Ui.confirmDanger(this, "Delete " + r.name + "?", dir ? "The folder and all files inside are removed from the workspace (committed with your next commit)." : "The file is removed from the workspace (committed with your next commit).", "Delete",
                        () -> mutate(() -> { tree.delete(path); for (int k = tabs.size() - 1; k >= 0; k--) { String tp = tabs.get(k).path; if (tp.equals(path) || tp.startsWith(path + "/")) closeTab(tabs.get(k)); } }));
        });
    }

    private void createMenuFile(final String dir) {
        Ui.prompt(this, "New file", "name.ext  (or path/name.ext)", "", n -> mutate(() -> { String p = join(dir, n.trim()); tree.createFile(p, ""); expandTo(p); expanded.add(dir); refreshTree(); openFile(p); }));
    }

    private void moveTo(String from, String to) {
        syncCurrent();
        tree.move(from, to);
        for (Tab t : tabs) {
            if (t.path.equals(from)) t.path = to;
            else if (t.path.startsWith(from + "/")) t.path = to + t.path.substring(from.length());
        }
        expandTo(to + "/x");
        if (cur != null) { titleTv.setText(PathUtils.baseName(cur.path)); subTv.setText(cur.path); }
    }

    // ---------------------------------------------------------------- import / export
    private String importDir = "";
    private String exportPath = "";

    private void importHere(String dir) {
        importDir = dir;
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQ_IMPORT);
    }

    private void exportFile(String path) {
        exportPath = path;
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_TITLE, PathUtils.baseName(path)), REQ_EXPORT_FILE);
    }

    private void exportFolder(String path) {
        exportPath = path;
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE, PathUtils.baseName(path) + ".zip"), REQ_EXPORT_ZIP);
    }

    private byte[] contentOf(Node n) throws Exception {
        if (n.text != null) return n.text.getBytes("UTF-8");
        if (n.importUri != null) {
            try (InputStream in = getContentResolver().openInputStream(n.importUri)) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[65536]; int k;
                while ((k = in.read(buf)) >= 0) bo.write(buf, 0, k);
                return bo.toByteArray();
            }
        }
        return api.blobBytes(owner, repo, n.sha);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (Downloader.onResult(this, req, res, data)) return;
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_IMPORT) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            int n = 0;
            for (Uri u : uris) {
                try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
                String[] ns = Stager.nameAndSize(this, u);
                try { tree.importFile(join(importDir, ns[0]), u, Long.parseLong(ns[1])); n++; } catch (IllegalArgumentException e) { Ui.toast(this, e.getMessage()); }
            }
            if (!importDir.isEmpty()) expanded.add(importDir);
            refreshTree(); renderTabs();
            Ui.toast(this, n + " file(s) added – commit to upload them");
        } else if (req == REQ_EXPORT_FILE && data.getData() != null) {
            final Uri u = data.getData();
            final Node n = tree.file(exportPath);
            if (n == null) return;
            syncCurrent();
            async(() -> { byte[] b = contentOf(n); try (OutputStream os = getContentResolver().openOutputStream(u)) { os.write(b); } return "Exported"; }, m -> Ui.toast(this, m), e -> Ui.toast(this, msg(e)));
        } else if (req == REQ_EXPORT_ZIP && data.getData() != null) {
            final Uri u = data.getData();
            final String folder = exportPath;
            syncCurrent();
            final ProgressDlg dlg = new ProgressDlg(this, "Exporting ZIP", "⬆", null);
            async(() -> {
                List<Node> files = new ArrayList<>();
                for (Node n : tree.all()) if (n.path.startsWith(folder + "/")) files.add(n);
                int i = 0;
                try (ZipOutputStream zo = new ZipOutputStream(getContentResolver().openOutputStream(u))) {
                    for (Node n : files) {
                        final int idx = ++i;
                        app.main.post(() -> dlg.set(idx + " / " + files.size(), (float) idx / files.size()));
                        zo.putNextEntry(new ZipEntry(n.path.substring(folder.length() + 1)));
                        zo.write(contentOf(n));
                        zo.closeEntry();
                    }
                }
                return files.size();
            }, n -> { dlg.close(); Ui.toast(this, n + " files exported"); }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
        }
    }

    // ================================================================= more menu & tools
    private void moreMenu() {
        String[] items = {"⚙  Editor settings", "🔍  Find & replace", "🔢  Go to line", "👁  Preview / code", "🧹  Trim trailing whitespace", "⚠  Problems", "⬇  Download repo ZIP", "🔁  Reload from GitHub"};
        Ui.pickList(this, "Menu", items, i -> {
            switch (i) {
                case 0: startActivity(new Intent(this, IdeSettingsActivity.class)); break;
                case 1: findDialog(); break;
                case 2: Ui.prompt(this, "Go to line", "line number", "", s -> { try { goToLine(Integer.parseInt(s.trim())); } catch (NumberFormatException e) { Ui.toast(this, "Enter a number"); } }); break;
                case 3: togglePreview(); break;
                case 4: if (editorShown) { String t = editor.getText().toString(); editor.getText().replace(0, t.length(), t.replaceAll("[ \t]+(?=\n|$)", "")); } break;
                case 5: if (issues.isEmpty()) Ui.toast(this, "No problems found"); else showProblems(); break;
                case 6: Downloader.request(this, api, "/repos/" + owner + "/" + repo + "/zipball/" + branch, repo + "-" + branch + ".zip"); break;
                default: reloadConfirm();
            }
        });
    }

    private void goToLine(int line) {
        if (!editorShown) return;
        String t = editor.getText().toString();
        int off = 0, l = 1;
        while (l < line && off < t.length()) { int nl = t.indexOf('\n', off); if (nl < 0) { off = t.length(); break; } off = nl + 1; l++; }
        editor.requestFocus();
        editor.setSelection(Math.min(off, t.length()));
    }

    private boolean matchCase, regex;

    private void findDialog() {
        if (!editorShown) { Ui.toast(this, "Open a code file first"); return; }
        LinearLayout box = Ui.vbox(this);
        final EditText find = Ui.input(this, "Find", "");
        final EditText rep = Ui.input(this, "Replace with", "");
        find.setSingleLine(true); rep.setSingleLine(true);
        box.addView(find, Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(rep, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        final LinearLayout chips = Ui.hbox(this);
        box.addView(chips, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            chips.removeAllViews();
            chips.addView(Ui.chip(this, "Match case", matchCase, v -> { matchCase = !matchCase; render[0].run(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
            chips.addView(Ui.chip(this, "Regex", regex, v -> { regex = !regex; render[0].run(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
            chips.addView(Ui.chip(this, "Replace", false, v -> replaceOne(find.getText().toString(), rep.getText().toString())));
        };
        render[0].run();
        final GlassDialog d = new GlassDialog(this).setTitle("Find & replace").setIcon("🔍").setView(box)
                .setPositiveButton("Replace all", (x, w) -> replaceAll(find.getText().toString(), rep.getText().toString()))
                .setNeutralButton("Find next", null).setNegativeButton("Close", null);
        d.show();
        d.getButton(GlassDialog.BUTTON_NEUTRAL).setOnClickListener(v -> findNext(find.getText().toString()));
    }

    private Pattern pattern(String q) {
        try { return Pattern.compile(regex ? q : Pattern.quote(q), matchCase ? 0 : Pattern.CASE_INSENSITIVE); } catch (Exception e) { Ui.toast(this, "Invalid pattern"); return null; }
    }

    private void findNext(String q) {
        if (q.isEmpty()) return;
        Pattern p = pattern(q);
        if (p == null) return;
        String t = editor.getText().toString();
        Matcher m = p.matcher(t);
        int from = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if (m.find(Math.min(from, t.length())) || m.find(0)) { editor.requestFocus(); editor.setSelection(m.start(), m.end()); }
        else Ui.toast(this, "Not found");
    }

    private void replaceOne(String q, String r) {
        if (q.isEmpty()) return;
        Pattern p = pattern(q);
        if (p == null) return;
        int a = editor.getSelectionStart(), b = editor.getSelectionEnd();
        if (a >= 0 && b > a) {
            String sel = editor.getText().subSequence(a, b).toString();
            Matcher m = p.matcher(sel);
            if (m.matches()) editor.getText().replace(a, b, regex ? m.replaceFirst(r) : r);
        }
        findNext(q);
    }

    private void replaceAll(String q, String r) {
        if (q.isEmpty()) return;
        Pattern p = pattern(q);
        if (p == null) return;
        String t = editor.getText().toString();
        Matcher m = p.matcher(t);
        int count = 0;
        while (m.find()) count++;
        String nt = p.matcher(t).replaceAll(regex ? r : Matcher.quoteReplacement(r));
        editor.getText().replace(0, t.length(), nt);
        Ui.toast(this, count + " replacement(s)");
    }

    // ================================================================= lifecycle
    @Override protected void onResume() {
        super.onResume();
        if (editorShown && cur != null) {
            editor.setFontSize(IdePrefs.font()); editor.setLineNumbers(IdePrefs.lines()); editor.setWrap(IdePrefs.wrap());
            editor.setColorSyntax(IdePrefs.color()); editor.setAutoIndent(IdePrefs.indent()); editor.setTabSize(IdePrefs.tab());
            symbolBar.setVisibility(IdePrefs.symbols() ? View.VISIBLE : View.GONE);
        }
    }

    @Override public void onBackPressed() {
        if (explorerOpen && selectMode) { setSelectMode(false); return; }
        if (explorerOpen) { toggleExplorer(false); return; }
        if (tree != null) {
            syncCurrent();
            int n = tree.changeCount();
            if (n > 0) {
                new GlassDialog(this).setTitle("Uncommitted changes").setIcon("⚠").setMessage("You have " + n + " change(s) that are not on GitHub yet.")
                        .setPositiveButton("Commit", (d, w) -> commitDialog()).setNegativeButton("Stay", null).setNeutralButton("Discard & leave", (d, w) -> finish()).show();
                return;
            }
        }
        super.onBackPressed();
    }
}
