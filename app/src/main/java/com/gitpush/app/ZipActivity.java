package com.gitpush.app;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.Pusher;
import com.gitpush.app.data.Stager;
import com.gitpush.app.data.Stager.StagedFile;
import com.gitpush.app.data.ZipTools;
import com.gitpush.app.data.ZipTools.Entry;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;
import com.gitpush.app.util.ZipRules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Future;

/**
 * Advanced ZIP extractor v2: root folder & strip levels, tree / list views, folder selection, extension and size
 * filters, path find & replace, case/space normalising, presets, previews, ZIP info, pattern select, undo, push-now.
 */
public class ZipActivity extends BaseActivity {
    private File zip;
    private List<Entry> entries = new ArrayList<>();
    private String[] newPaths = new String[0];
    private final ZipRules rules = new ZipRules();
    private final Set<String> manualOff = new HashSet<>();
    private final Deque<Set<String>> undoStack = new ArrayDeque<>();
    private final Set<String> expanded = new HashSet<>();
    private final List<VRow> vrows = new ArrayList<>();
    private List<String> chain = new ArrayList<>(), folders = new ArrayList<>();
    private Map<String, Integer> extCounts = new LinkedHashMap<>();

    private BaseAdapter adapter;
    private TextView summary, example, warn, stripTv, replaceInfo;
    private Button rootBtn, addBtn, pushBtn;
    private LinearLayout chainBox, toggleBox, extBox, sizeBox, viewBox;
    private EditText search, prefixIn, excludeIn, includeIn;
    private String zipName = "archive.zip";
    private Future<?> extractJob;
    private boolean treeMode = false, onlySelected = false;
    private int sort = 0;

    private static final class VRow { int idx = -1; String path, name; boolean dir; int depth, count; long size; boolean open; List<Integer> kids; }
    private static final class TN { String name, path; boolean dir; int idx = -1; final TreeMap<String, TN> kids = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildScreen("ZIP extractor", "Reading…", true);
        addAction(R.drawable.i_info, v -> zipInfo());
        addAction(R.drawable.i_export, v -> sharePaths());
        final Uri uri = getIntent().getData();
        if (uri == null) { finish(); return; }
        TextView loading = Ui.muted(this, "Reading ZIP…");
        loading.setGravity(android.view.Gravity.CENTER);
        body.addView(loading);
        async(() -> {
            zipName = Stager.nameAndSize(this, uri)[0];
            File f = ZipTools.copyToCache(this, uri);
            return new Object[]{f, ZipTools.list(f)};
        }, r -> {
            @SuppressWarnings("unchecked") List<Entry> list = (List<Entry>) r[1];
            zip = (File) r[0]; entries = list;
            if (entries.isEmpty()) { Ui.toast(this, "The ZIP has no usable files"); finish(); return; }
            List<String> paths = new ArrayList<>();
            for (Entry e : entries) {
                paths.add(e.path);
                String n = PathUtils.baseName(e.path);
                int dot = n.lastIndexOf('.');
                String ext = dot <= 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
                Integer c = extCounts.get(ext);
                extCounts.put(ext, c == null ? 1 : c + 1);
            }
            chain = ZipRules.topChain(paths);
            folders = ZipRules.allFolders(paths, 400);
            buildUi();
        }, e -> { Ui.toast(this, msg(e)); finish(); });
    }

    // ================================================================= UI
    private void buildUi() {
        body.removeAllViews();
        long total = 0;
        for (Entry e : entries) total += e.size;
        setSubtitle(zipName + " • " + entries.size() + " files • " + PathUtils.formatSize(total));
        LinearLayout page = Ui.vbox(this);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.addHeaderView(header(), null, false);
        adapter = new BaseAdapter() {
            public int getCount() { return vrows.size(); }
            public Object getItem(int i) { return vrows.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { return row(vrows.get(i)); }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        LinearLayout bar = Ui.hbox(this);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 14));
        addBtn = Ui.button(this, "", v -> startExtract(false));
        pushBtn = Ui.outlineButton(this, "🚀 Add & push", v -> startExtract(true));
        bar.addView(addBtn, Ui.weight(1f));
        bar.addView(pushBtn, Ui.lp(this, Ui.WRAP, Ui.WRAP, 8, 0, 0, 0));
        page.addView(bar, Ui.lp(Ui.MATCH, Ui.WRAP));
        body.addView(page);
        recompute();
    }

    private HorizontalScrollView scrollRow(LinearLayout row) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        return hs;
    }

    private LinearLayout.LayoutParams cm(float l, float t, float r, float b) { return Ui.lp(this, Ui.WRAP, Ui.WRAP, l, t, r, b); }

    private View header() {
        LinearLayout h = Ui.vbox(this);
        h.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 8));

        // ---- start point
        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.label(this, "1 • Where should the upload start?"));
        chainBox = Ui.hbox(this);
        c1.addView(scrollRow(chainBox), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        LinearLayout r1 = Ui.hbox(this);
        r1.addView(Ui.body(this, "Root folder"), Ui.weight(0.9f));
        rootBtn = Ui.outlineButton(this, "(ZIP root)", v -> pickRoot());
        rootBtn.setSingleLine(true);
        r1.addView(rootBtn, Ui.weight(1.6f));
        c1.addView(r1, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        LinearLayout r2 = Ui.hbox(this);
        r2.addView(Ui.body(this, "Strip first folders"), Ui.weight(1f));
        r2.addView(Ui.textButton(this, "−", R.attr.gpText, v -> { if (rules.stripLevels > 0) { rules.stripLevels--; recompute(); } }));
        stripTv = Ui.text(this, "0", 16, true, R.attr.gpText);
        stripTv.setGravity(android.view.Gravity.CENTER);
        r2.addView(stripTv, Ui.lp(Ui.dp(this, 28), Ui.WRAP));
        r2.addView(Ui.textButton(this, "＋", R.attr.gpText, v -> { if (rules.stripLevels < 12) { rules.stripLevels++; recompute(); } }));
        c1.addView(r2, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        prefixIn = field("Add folder prefix (e.g. Buildora)", t -> rules.addPrefix = t);
        c1.addView(prefixIn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        h.addView(c1, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));

        // ---- filters
        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.label(this, "2 • Filter & clean paths"));
        excludeIn = field("Exclude  (*.class, build/, node_modules)", t -> rules.exclude = t);
        includeIn = field("Include only  (*.java, *.xml)", t -> rules.include = t);
        c2.addView(excludeIn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        c2.addView(includeIn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        toggleBox = Ui.hbox(this);
        c2.addView(scrollRow(toggleBox), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        c2.addView(Ui.label(this, "File types"), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        extBox = Ui.hbox(this);
        c2.addView(scrollRow(extBox), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        c2.addView(Ui.label(this, "Size"), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        sizeBox = Ui.hbox(this);
        c2.addView(scrollRow(sizeBox), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        LinearLayout rr = Ui.hbox(this);
        replaceInfo = Ui.muted(this, "No path replace");
        rr.addView(replaceInfo, Ui.weight(1f));
        rr.addView(Ui.outlineButton(this, "🔁 Find & replace", v -> replaceDialog()));
        c2.addView(rr, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        LinearLayout tools = Ui.hbox(this);
        tools.addView(Ui.chip(this, "💾 Presets", false, v -> presets()), cm(0, 0, 6, 0));
        tools.addView(Ui.chip(this, "ℹ ZIP info", false, v -> zipInfo()), cm(0, 0, 6, 0));
        tools.addView(Ui.chip(this, "Reset all", false, v -> resetAll()));
        c2.addView(scrollRow(tools), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        h.addView(c2, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));

        summary = Ui.text(this, "", 14, true, R.attr.gpText);
        warn = Ui.text(this, "", 11, false, R.attr.gpWarn);
        example = Ui.text(this, "", 11, false, R.attr.gpMuted);
        h.addView(summary);
        h.addView(warn);
        h.addView(example, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 2, 0, 8));

        viewBox = Ui.hbox(this);
        h.addView(scrollRow(viewBox), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 8));
        search = Ui.input(this, "🔍  Filter list by path", "");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { refreshList(); }
        });
        h.addView(search, Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout ctl = Ui.hbox(this);
        ctl.addView(Ui.chip(this, "Select all", false, v -> bulk(0)), cm(0, 0, 6, 0));
        ctl.addView(Ui.chip(this, "None", false, v -> bulk(1)), cm(0, 0, 6, 0));
        ctl.addView(Ui.chip(this, "Invert", false, v -> bulk(2)), cm(0, 0, 6, 0));
        ctl.addView(Ui.chip(this, "Pattern…", false, v -> patternSelect()), cm(0, 0, 6, 0));
        ctl.addView(Ui.chip(this, "↩ Undo", false, v -> undoSelection()));
        h.addView(scrollRow(ctl), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 4));
        return h;
    }

    private EditText field(String hint, final Ui.Cb<String> setter) {
        EditText e = Ui.input(this, hint, "");
        e.setSingleLine(true);
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable ed) { setter.on(ed.toString()); recompute(); }
        });
        return e;
    }

    private void renderChips() {
        chainBox.removeAllViews();
        chainBox.addView(Ui.chip(this, "Keep all", rules.rootFolder.isEmpty(), v -> { rules.rootFolder = ""; recompute(); }), cm(0, 0, 6, 0));
        for (final String c : chain) chainBox.addView(Ui.chip(this, "Start at " + PathUtils.baseName(c) + "/", c.equals(rules.rootFolder), v -> { rules.rootFolder = c; recompute(); }), cm(0, 0, 6, 0));
        toggleBox.removeAllViews();
        toggleBox.addView(Ui.chip(this, "Skip hidden", rules.skipHidden, v -> { rules.skipHidden = !rules.skipHidden; recompute(); }), cm(0, 0, 6, 0));
        toggleBox.addView(Ui.chip(this, "Flatten", rules.flatten, v -> { rules.flatten = !rules.flatten; recompute(); }), cm(0, 0, 6, 0));
        toggleBox.addView(Ui.chip(this, "Skip junk", rules.skipJunk, v -> { rules.skipJunk = !rules.skipJunk; recompute(); }), cm(0, 0, 6, 0));
        toggleBox.addView(Ui.chip(this, "lowercase", rules.caseMode == 1, v -> { rules.caseMode = rules.caseMode == 1 ? 0 : 1; recompute(); }), cm(0, 0, 6, 0));
        toggleBox.addView(Ui.chip(this, "UPPERCASE", rules.caseMode == 2, v -> { rules.caseMode = rules.caseMode == 2 ? 0 : 2; recompute(); }), cm(0, 0, 6, 0));
        toggleBox.addView(Ui.chip(this, "space → _", rules.spacesToUnderscore, v -> { rules.spacesToUnderscore = !rules.spacesToUnderscore; recompute(); }));
        extBox.removeAllViews();
        List<Map.Entry<String, Integer>> es = new ArrayList<>(extCounts.entrySet());
        Collections.sort(es, (a, b) -> b.getValue() - a.getValue());
        for (int i = 0; i < es.size() && i < 14; i++) {
            final String ext = es.get(i).getKey();
            extBox.addView(Ui.chip(this, (ext.isEmpty() ? "(none)" : "." + ext) + " " + es.get(i).getValue(), !rules.hiddenExts.contains(ext),
                    v -> { if (!rules.hiddenExts.remove(ext)) rules.hiddenExts.add(ext); recompute(); }), cm(0, 0, 6, 0));
        }
        sizeBox.removeAllViews();
        long[] lim = {0, 1L << 20, 5L << 20, 20L << 20};
        String[] nm = {"Any size", "≤ 1 MB", "≤ 5 MB", "≤ 20 MB"};
        for (int i = 0; i < lim.length; i++) { final long l = lim[i]; sizeBox.addView(Ui.chip(this, nm[i], rules.maxSize == l, v -> { rules.maxSize = l; recompute(); }), cm(0, 0, 6, 0)); }
        sizeBox.addView(Ui.chip(this, "Skip empty", rules.skipEmpty, v -> { rules.skipEmpty = !rules.skipEmpty; recompute(); }));
        viewBox.removeAllViews();
        viewBox.addView(Ui.chip(this, "List", !treeMode, v -> { treeMode = false; refreshList(); renderChips(); }), cm(0, 0, 6, 0));
        viewBox.addView(Ui.chip(this, "Tree", treeMode, v -> { treeMode = true; refreshList(); renderChips(); }), cm(0, 0, 6, 0));
        if (!treeMode) {
            String[] sn = {"Name", "Size", "Type"};
            for (int i = 0; i < sn.length; i++) { final int s = i; viewBox.addView(Ui.chip(this, "Sort: " + sn[i], sort == i, v -> { sort = s; refreshList(); renderChips(); }), cm(0, 0, 6, 0)); }
        } else {
            viewBox.addView(Ui.chip(this, "Expand all", false, v -> { for (String f : folders) expanded.add(f); refreshList(); }), cm(0, 0, 6, 0));
            viewBox.addView(Ui.chip(this, "Collapse", false, v -> { expanded.clear(); refreshList(); }), cm(0, 0, 6, 0));
        }
        viewBox.addView(Ui.chip(this, "Only selected", onlySelected, v -> { onlySelected = !onlySelected; refreshList(); renderChips(); }));
    }

    // ================================================================= data
    private void pickRoot() {
        int n = Math.min(folders.size(), 300);
        final String[] labels = new String[n + 1];
        labels[0] = "(ZIP root – keep everything)";
        for (int i = 0; i < n; i++) labels[i + 1] = "📁  " + folders.get(i);
        Ui.pickList(this, "Use this folder as the root", labels, i -> { rules.rootFolder = i == 0 ? "" : folders.get(i - 1); recompute(); });
    }

    private boolean included(int i) { return newPaths[i] != null && !manualOff.contains(entries.get(i).raw); }

    private void recompute() {
        newPaths = new String[entries.size()];
        for (int i = 0; i < newPaths.length; i++) newPaths[i] = rules.apply(entries.get(i).path, entries.get(i).size);
        renderChips();
        rootBtn.setText(rules.rootFolder.isEmpty() ? "(ZIP root)" : rules.rootFolder);
        stripTv.setText(String.valueOf(rules.stripLevels));
        replaceInfo.setText(rules.replaceFind.isEmpty() ? "No path replace" : "Replace  " + rules.replaceFind + "  →  " + (rules.replaceWith.isEmpty() ? "(nothing)" : rules.replaceWith));
        refreshList();
    }

    private void refreshList() {
        if (adapter == null) return;
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int inc = 0, long1 = 0;
        long size = 0;
        String ex = null;
        Map<String, Integer> seen = new HashMap<>();
        int dup = 0;
        List<Integer> vis = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (included(i)) {
                inc++; size += entries.get(i).size;
                if (ex == null) ex = newPaths[i];
                Integer c = seen.get(newPaths[i]);
                seen.put(newPaths[i], c == null ? 1 : c + 1);
                if (c != null) dup++;
                if (newPaths[i].length() > 150) long1++;
            }
            if (onlySelected && !included(i)) continue;
            if (q.isEmpty() || entries.get(i).path.toLowerCase(Locale.ROOT).contains(q) || (newPaths[i] != null && newPaths[i].toLowerCase(Locale.ROOT).contains(q))) vis.add(i);
        }
        summary.setText("Will upload " + inc + " of " + entries.size() + " files • " + PathUtils.formatSize(size));
        StringBuilder w = new StringBuilder();
        if (dup > 0) w.append("⚠ ").append(dup).append(" file(s) map to an existing path – later ones are skipped.  ");
        if (long1 > 0) w.append("⚠ ").append(long1).append(" very long path(s).");
        warn.setText(w.toString());
        warn.setVisibility(w.length() == 0 ? View.GONE : View.VISIBLE);
        example.setText(ex == null ? "No files selected – adjust the options above." : "First file → " + ex);
        addBtn.setText(inc == 0 ? "Nothing to add" : "➕  Add " + inc + " file(s) • " + PathUtils.formatSize(size));
        addBtn.setEnabled(inc > 0);
        pushBtn.setEnabled(inc > 0);
        buildRows(vis, !q.isEmpty());
        adapter.notifyDataSetChanged();
    }

    private void buildRows(List<Integer> vis, boolean searching) {
        vrows.clear();
        if (!treeMode || searching) {
            List<Integer> sorted = new ArrayList<>(vis);
            final int s = sort;
            Collections.sort(sorted, (a, b) -> {
                if (s == 1) return Long.compare(entries.get(b).size, entries.get(a).size);
                String pa = newPaths[a] != null ? newPaths[a] : entries.get(a).path, pb = newPaths[b] != null ? newPaths[b] : entries.get(b).path;
                if (s == 2) { int c = PathUtils.extension(pa).compareTo(PathUtils.extension(pb)); if (c != 0) return c; }
                return pa.compareToIgnoreCase(pb);
            });
            for (int i : sorted) { VRow r = new VRow(); r.idx = i; vrows.add(r); }
            return;
        }
        TN root = new TN();
        for (int i : vis) {
            if (newPaths[i] == null) continue;
            String[] segs = newPaths[i].split("/");
            TN cur = root;
            StringBuilder acc = new StringBuilder();
            for (int k = 0; k < segs.length; k++) {
                if (acc.length() > 0) acc.append('/');
                acc.append(segs[k]);
                boolean last = k == segs.length - 1;
                TN nx = cur.kids.get(segs[k] + (last ? "\u0001" : ""));
                if (nx == null) { nx = new TN(); nx.name = segs[k]; nx.path = acc.toString(); nx.dir = !last; cur.kids.put(segs[k] + (last ? "\u0001" : ""), nx); }
                if (last) nx.idx = i;
                cur = nx;
            }
        }
        flat(root, 0);
    }

    private List<Integer> leaves(TN n) {
        List<Integer> out = new ArrayList<>();
        if (!n.dir) { if (n.idx >= 0) out.add(n.idx); return out; }
        for (TN k : n.kids.values()) out.addAll(leaves(k));
        return out;
    }

    private void flat(TN dir, int depth) {
        List<TN> ds = new ArrayList<>(), fs = new ArrayList<>();
        for (TN k : dir.kids.values()) (k.dir ? ds : fs).add(k);
        for (TN d : ds) {
            VRow r = new VRow();
            r.dir = true; r.path = d.path; r.name = d.name; r.depth = depth; r.open = expanded.contains(d.path); r.kids = leaves(d); r.count = r.kids.size();
            for (int i : r.kids) r.size += entries.get(i).size;
            vrows.add(r);
            if (r.open) flat(d, depth + 1);
        }
        for (TN f : fs) { VRow r = new VRow(); r.idx = f.idx; r.depth = depth; vrows.add(r); }
    }

    // ================================================================= rows
    private View row(final VRow vr) {
        LinearLayout c = Ui.hbox(this);
        c.setBackgroundResource(R.drawable.bg_ripple_row);
        c.setPadding(Ui.dp(this, 16 + (treeMode ? vr.depth * 14 : 0)), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        if (vr.dir) {
            int inc = 0;
            for (int i : vr.kids) if (included(i)) inc++;
            c.addView(Ui.iconRes(this, inc == vr.count ? R.drawable.i_square_check : inc == 0 ? R.drawable.i_square : R.drawable.i_minus, 20, R.attr.gpText), Ui.lp(this, Ui.dp(this, 20), Ui.dp(this, 20), 0, 0, 8, 0));
            c.addView(Ui.iconRes(this, vr.open ? R.drawable.i_chevron_down : R.drawable.i_chevron_right, 14, R.attr.gpMuted), Ui.lp(this, Ui.dp(this, 14), Ui.dp(this, 14), 0, 0, 4, 0));
            c.addView(Ui.iconRes(this, vr.open ? R.drawable.i_folder_open : R.drawable.i_folder, 18, R.attr.gpText), Ui.lp(this, Ui.dp(this, 18), Ui.dp(this, 18), 0, 0, 8, 0));
            TextView t = Ui.text(this, vr.name, 13, true, R.attr.gpText);
            t.setSingleLine(true);
            c.addView(t, Ui.weight(1f));
            c.addView(Ui.label(this, vr.count + " • " + PathUtils.formatSize(vr.size)));
            final View cb = c.getChildAt(0);
            cb.setOnClickListener(v -> { pushUndo(); boolean allOn = true; for (int i : vr.kids) if (!included(i)) allOn = false; for (int i : vr.kids) { if (newPaths[i] == null) continue; if (allOn) manualOff.add(entries.get(i).raw); else manualOff.remove(entries.get(i).raw); } refreshList(); });
            c.setOnClickListener(v -> { if (!expanded.remove(vr.path)) expanded.add(vr.path); refreshList(); });
            return c;
        }
        final Entry e = entries.get(vr.idx);
        final boolean excluded = newPaths[vr.idx] == null, on = included(vr.idx);
        c.addView(Ui.iconRes(this, excluded ? R.drawable.i_ban : on ? R.drawable.i_square_check : R.drawable.i_square, 20, on ? R.attr.gpText : R.attr.gpMuted), Ui.lp(this, Ui.dp(this, 20), Ui.dp(this, 20), 0, 0, 10, 0));
        LinearLayout t = Ui.vbox(this);
        String shown = excluded ? e.path : treeMode && !search.getText().toString().trim().isEmpty() ? newPaths[vr.idx] : treeMode ? PathUtils.baseName(newPaths[vr.idx]) : newPaths[vr.idx];
        TextView p = Ui.text(this, shown, 12, true, R.attr.gpText);
        t.addView(p);
        if (!treeMode && !excluded && !newPaths[vr.idx].equals(e.path)) t.addView(Ui.text(this, "from  " + e.path, 10, false, R.attr.gpMuted));
        if (excluded) t.addView(Ui.text(this, "excluded by filters", 10, false, R.attr.gpMuted));
        else if (newPaths[vr.idx].length() > 150) t.addView(Ui.text(this, "⚠ very long path", 10, false, R.attr.gpWarn));
        c.addView(t, Ui.weight(1f));
        c.addView(Ui.label(this, PathUtils.formatSize(e.size)));
        c.setAlpha(excluded || !on ? 0.5f : 1f);
        c.setOnClickListener(v -> {
            if (newPaths[vr.idx] == null) { Ui.toast(this, "Excluded by your filters"); return; }
            if (!manualOff.remove(e.raw)) manualOff.add(e.raw);
            refreshList();
        });
        c.setOnLongClickListener(v -> { Ui.haptic(v); fileMenu(vr.idx); return true; });
        return c;
    }

    private void fileMenu(final int i) {
        final Entry e = entries.get(i);
        String[] items = {"👁  Preview file", "🔗  Copy upload path", "📋  Copy original path", "🚫  Exclude this extension"};
        Ui.pickList(this, PathUtils.baseName(e.path), items, k -> {
            if (k == 0) preview(e);
            else if (k == 1) { Ui.copy(this, "path", newPaths[i] == null ? e.path : newPaths[i]); Ui.toast(this, "Copied"); }
            else if (k == 2) { Ui.copy(this, "path", e.path); Ui.toast(this, "Copied"); }
            else { String n = PathUtils.baseName(e.path); int dot = n.lastIndexOf('.'); rules.hiddenExts.add(dot <= 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT)); recompute(); }
        });
    }

    private void preview(final Entry e) {
        async(() -> ZipTools.readEntry(zip, e.raw, 200_000), bytes -> {
            if (PathUtils.isImage(e.path)) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                iv.setAdjustViewBounds(true);
                LinearLayout box = Ui.vbox(this);
                box.addView(iv, Ui.lp(Ui.MATCH, Ui.dp(this, 240)));
                new GlassDialog(this).setTitle(PathUtils.baseName(e.path)).setIcon("📄").setView(box).setPositiveButton("Close", null).show();
                return;
            }
            String text = Pusher.tryText(bytes);
            new GlassDialog(this).setTitle(PathUtils.baseName(e.path)).setIcon("📄")
                    .setMessage(text == null ? "Binary file • " + PathUtils.formatSize(e.size) : text + (e.size > bytes.length ? "\n\n… (first " + PathUtils.formatSize(bytes.length) + " shown)" : ""))
                    .setPositiveButton("Close", null).show();
        }, x -> Ui.toast(this, msg(x)));
    }

    // ================================================================= bulk / undo / tools
    private void pushUndo() { undoStack.push(new HashSet<>(manualOff)); while (undoStack.size() > 20) undoStack.removeLast(); }

    private void undoSelection() {
        if (undoStack.isEmpty()) { Ui.toast(this, "Nothing to undo"); return; }
        manualOff.clear(); manualOff.addAll(undoStack.pop()); refreshList();
    }

    private void bulk(int mode) {
        pushUndo();
        for (VRow r : vrows) {
            List<Integer> idxs = new ArrayList<>();
            if (r.dir) { if (r.kids != null) idxs.addAll(r.kids); } else if (r.idx >= 0) idxs.add(r.idx);
            for (int i : idxs) {
                if (newPaths[i] == null) continue;
                String raw = entries.get(i).raw;
                if (mode == 0) manualOff.remove(raw);
                else if (mode == 1) manualOff.add(raw);
                else if (!manualOff.remove(raw)) manualOff.add(raw);
            }
        }
        refreshList();
    }

    private void patternSelect() {
        Ui.prompt(this, "Select by pattern", "e.g. *.md, docs/, test*", "", p -> {
            if (p.trim().isEmpty()) return;
            final String globs = p.trim();
            Ui.pickList(this, "Files matching \"" + globs + "\"", new String[]{"☑  Select matching", "☐  Deselect matching"}, k -> {
                pushUndo();
                int n = 0;
                for (int i = 0; i < entries.size(); i++) {
                    if (newPaths[i] == null || !ZipRules.matchesGlobs(globs, entries.get(i).path)) continue;
                    n++;
                    if (k == 0) manualOff.remove(entries.get(i).raw); else manualOff.add(entries.get(i).raw);
                }
                Ui.toast(this, n + " file(s) " + (k == 0 ? "selected" : "deselected"));
                refreshList();
            });
        });
    }

    private void replaceDialog() {
        LinearLayout box = Ui.vbox(this);
        final EditText f = Ui.input(this, "Find (regex allowed)", rules.replaceFind), w = Ui.input(this, "Replace with", rules.replaceWith);
        f.setSingleLine(true); w.setSingleLine(true);
        box.addView(f, Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(w, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        box.addView(Ui.muted(this, "Applied to every upload path, e.g.  -main  →  (nothing)  removes the GitHub \"-main\" suffix."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        new GlassDialog(this).setTitle("Find & replace in paths").setIcon("🔁").setView(box)
                .setPositiveButton("Apply", (d, x) -> { rules.replaceFind = f.getText().toString(); rules.replaceWith = w.getText().toString(); recompute(); })
                .setNeutralButton("Clear", (d, x) -> { rules.replaceFind = ""; rules.replaceWith = ""; recompute(); }).setNegativeButton("Cancel", null).show();
    }

    private void resetAll() {
        rules.stripLevels = 0; rules.rootFolder = ""; rules.addPrefix = ""; rules.skipHidden = false; rules.flatten = false; rules.skipJunk = true;
        rules.exclude = ""; rules.include = ""; rules.caseMode = 0; rules.spacesToUnderscore = false; rules.skipEmpty = false; rules.maxSize = 0;
        rules.replaceFind = ""; rules.replaceWith = ""; rules.hiddenExts.clear(); manualOff.clear();
        prefixIn.setText(""); excludeIn.setText(""); includeIn.setText("");
        recompute();
    }

    private void zipInfo() {
        if (entries.isEmpty()) return;
        long total = 0, comp = 0;
        int maxDepth = 0;
        String deepest = "";
        for (Entry e : entries) { total += e.size; comp += e.csize; int d = e.path.split("/").length; if (d > maxDepth) { maxDepth = d; deepest = e.path; } }
        List<Entry> big = new ArrayList<>(entries);
        Collections.sort(big, (a, b) -> Long.compare(b.size, a.size));
        StringBuilder sb = new StringBuilder();
        sb.append("Files: ").append(entries.size()).append("\nFolders: ").append(folders.size()).append("\nUncompressed: ").append(PathUtils.formatSize(total))
                .append("\nCompressed: ").append(PathUtils.formatSize(comp)).append(comp > 0 && total > 0 ? "  (" + (100 - comp * 100 / total) + "% smaller)" : "")
                .append("\nDeepest path: ").append(maxDepth).append(" levels\n\nLargest files:\n");
        for (int i = 0; i < Math.min(5, big.size()); i++) sb.append("• ").append(PathUtils.baseName(big.get(i).path)).append("  ").append(PathUtils.formatSize(big.get(i).size)).append('\n');
        sb.append("\nTop types: ");
        List<Map.Entry<String, Integer>> es = new ArrayList<>(extCounts.entrySet());
        Collections.sort(es, (a, b) -> b.getValue() - a.getValue());
        for (int i = 0; i < Math.min(6, es.size()); i++) sb.append(es.get(i).getKey().isEmpty() ? "(none)" : "." + es.get(i).getKey()).append(" ×").append(es.get(i).getValue()).append("  ");
        new GlassDialog(this).setTitle(zipName).setIcon("ℹ").setMessage(sb.toString()).setPositiveButton("Close", null).show();
    }

    private void sharePaths() {
        if (newPaths.length == 0) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) if (included(i)) sb.append(newPaths[i]).append('\n');
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString()), "Share path list"));
    }

    // ---------------------------------------------------------------- presets
    private JSONObject allPresets() { try { return new JSONObject(st.store.prefs.getString("zip_presets", "{}")); } catch (Exception e) { return new JSONObject(); } }

    private void presets() {
        final JSONObject all = allPresets();
        final List<String> names = new ArrayList<>();
        for (Iterator<String> it = all.keys(); it.hasNext(); ) names.add(it.next());
        List<String> items = new ArrayList<>();
        items.add("💾  Save current as preset…");
        for (String n : names) items.add("📌  " + n);
        if (!names.isEmpty()) items.add("🗑  Delete a preset…");
        final String[] arr = items.toArray(new String[0]);
        Ui.pickList(this, "Presets", arr, i -> {
            if (i == 0) Ui.prompt(this, "Preset name", "e.g. Android project", "", n -> { if (!n.trim().isEmpty()) savePreset(n.trim()); });
            else if (arr[i].contains("Delete a preset")) Ui.pickList(this, "Delete preset", names.toArray(new String[0]), k -> { all.remove(names.get(k)); st.store.prefs.edit().putString("zip_presets", all.toString()).apply(); Ui.toast(this, "Deleted"); });
            else applyPreset(all.optJSONObject(names.get(i - 1)));
        });
    }

    private void savePreset(String name) {
        try {
            JSONObject p = new JSONObject();
            p.put("strip", rules.stripLevels).put("prefix", rules.addPrefix).put("exclude", rules.exclude).put("include", rules.include).put("hidden", rules.skipHidden)
                    .put("flatten", rules.flatten).put("junk", rules.skipJunk).put("case", rules.caseMode).put("spaces", rules.spacesToUnderscore).put("empty", rules.skipEmpty)
                    .put("max", rules.maxSize).put("find", rules.replaceFind).put("with", rules.replaceWith).put("exts", new JSONArray(new ArrayList<>(rules.hiddenExts)));
            JSONObject all = allPresets();
            all.put(name, p);
            st.store.prefs.edit().putString("zip_presets", all.toString()).apply();
            Ui.toast(this, "Preset saved");
        } catch (Exception e) { Ui.toast(this, msg(e)); }
    }

    private void applyPreset(JSONObject p) {
        if (p == null) return;
        rules.stripLevels = p.optInt("strip"); rules.addPrefix = p.optString("prefix"); rules.exclude = p.optString("exclude"); rules.include = p.optString("include");
        rules.skipHidden = p.optBoolean("hidden"); rules.flatten = p.optBoolean("flatten"); rules.skipJunk = p.optBoolean("junk", true); rules.caseMode = p.optInt("case");
        rules.spacesToUnderscore = p.optBoolean("spaces"); rules.skipEmpty = p.optBoolean("empty"); rules.maxSize = p.optLong("max");
        rules.replaceFind = p.optString("find"); rules.replaceWith = p.optString("with");
        rules.hiddenExts.clear();
        JSONArray ex = p.optJSONArray("exts");
        if (ex != null) for (int i = 0; i < ex.length(); i++) rules.hiddenExts.add(ex.optString(i));
        prefixIn.setText(rules.addPrefix); excludeIn.setText(rules.exclude); includeIn.setText(rules.include);
        recompute();
        Ui.toast(this, "Preset applied");
    }

    // ================================================================= extract
    private void startExtract(final boolean pushNow) {
        final List<String[]> mapping = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) if (included(i)) mapping.add(new String[]{entries.get(i).raw, newPaths[i]});
        if (mapping.isEmpty()) return;
        final ProgressDlg dlg = new ProgressDlg(this, "Extracting files", "🗜", () -> { if (extractJob != null) extractJob.cancel(true); });
        extractJob = app.io.submit(() -> {
            try {
                final List<StagedFile> res = ZipTools.extract(this, zip, mapping, (d, t) -> app.main.post(() -> dlg.set("Extracting " + d + " / " + t, (float) d / t)));
                app.main.post(() -> { dlg.close(); st.addStagedFiles(res); st.autoPush = pushNow; finish(); });
            } catch (Exception e) {
                app.main.post(() -> { dlg.close(); if (alive()) Ui.toast(this, msg(e)); });
            }
        });
    }
}
