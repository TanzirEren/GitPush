package com.gitpush.app;

import android.content.Intent;
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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

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

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/** Advanced ZIP extractor: choose root folder, strip levels, filters, review every path, then add to the upload list. */
public class ZipActivity extends BaseActivity {
    private File zip;
    private List<Entry> entries = new ArrayList<>();
    private String[] newPaths = new String[0];
    private final ZipRules rules = new ZipRules();
    private final Set<String> manualOff = new HashSet<>();
    private final List<Integer> visible = new ArrayList<>();
    private List<String> chain = new ArrayList<>();
    private List<String> folders = new ArrayList<>();

    private BaseAdapter adapter;
    private TextView summary, example, stripTv;
    private Button rootBtn, addBtn;
    private LinearLayout chainBox, toggleBox;
    private EditText search;
    private String zipName = "archive.zip";
    private Future<?> extractJob;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildScreen("ZIP extractor", "Reading…", true);
        addTextAction("Share list", v -> sharePaths());
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
            for (Entry e : entries) paths.add(e.path);
            chain = ZipRules.topChain(paths);
            folders = ZipRules.allFolders(paths, 400);
            buildUi();
        }, e -> { Ui.toast(this, msg(e)); finish(); });
    }

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
            public int getCount() { return visible.size(); }
            public Object getItem(int i) { return visible.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { return row(visible.get(i)); }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        LinearLayout bar = Ui.vbox(this);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 16));
        addBtn = Ui.button(this, "", v -> startExtract());
        bar.addView(addBtn, Ui.lp(Ui.MATCH, Ui.WRAP));
        page.addView(bar, Ui.lp(Ui.MATCH, Ui.WRAP));
        body.addView(page);
        recompute();
    }

    private View header() {
        LinearLayout h = Ui.vbox(this);
        h.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 8));

        LinearLayout opt = Ui.card(this);
        opt.addView(Ui.text(this, "Where should the upload start?", 14, true, R.attr.gpPrimary));
        chainBox = Ui.hbox(this);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chainBox);
        opt.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));

        LinearLayout r1 = Ui.hbox(this);
        r1.addView(Ui.body(this, "Root folder"), Ui.weight(0.9f));
        rootBtn = Ui.outlineButton(this, "(ZIP root)", v -> pickRoot());
        rootBtn.setSingleLine(true);
        r1.addView(rootBtn, Ui.weight(1.6f));
        opt.addView(r1, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));

        LinearLayout r2 = Ui.hbox(this);
        r2.addView(Ui.body(this, "Strip first folders"), Ui.weight(1f));
        r2.addView(Ui.textButton(this, "−", R.attr.gpPrimary, v -> { if (rules.stripLevels > 0) { rules.stripLevels--; recompute(); } }));
        stripTv = Ui.text(this, "0", 16, true, R.attr.gpText);
        stripTv.setGravity(android.view.Gravity.CENTER);
        r2.addView(stripTv, Ui.lp(Ui.dp(this, 28), Ui.WRAP));
        r2.addView(Ui.textButton(this, "＋", R.attr.gpPrimary, v -> { if (rules.stripLevels < 12) { rules.stripLevels++; recompute(); } }));
        opt.addView(r2, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));

        opt.addView(field("Add folder prefix (e.g. Buildora)", t -> rules.addPrefix = t), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        opt.addView(field("Exclude (e.g. *.class, build/, node_modules)", t -> rules.exclude = t), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        opt.addView(field("Include only (e.g. *.java, *.xml)", t -> rules.include = t), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        toggleBox = Ui.hbox(this);
        HorizontalScrollView hs2 = new HorizontalScrollView(this);
        hs2.setHorizontalScrollBarEnabled(false);
        hs2.addView(toggleBox);
        opt.addView(hs2, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        h.addView(opt, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 10));

        summary = Ui.text(this, "", 13, true, R.attr.gpText);
        example = Ui.text(this, "", 11, false, R.attr.gpMuted);
        h.addView(summary);
        h.addView(example, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 2, 0, 8));

        search = Ui.input(this, "🔍  Filter list by path", "");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { refreshList(); }
        });
        h.addView(search, Ui.lp(Ui.MATCH, Ui.WRAP));
        LinearLayout ctl = Ui.hbox(this);
        ctl.addView(Ui.chip(this, "Select all", false, v -> bulk(0)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        ctl.addView(Ui.chip(this, "None", false, v -> bulk(1)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        ctl.addView(Ui.chip(this, "Invert", false, v -> bulk(2)));
        h.addView(ctl, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 4));
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
        chainBox.addView(Ui.chip(this, "Keep all", rules.rootFolder.isEmpty(), v -> { rules.rootFolder = ""; recompute(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        for (final String c : chain) {
            String label = "Start at " + PathUtils.baseName(c) + "/";
            chainBox.addView(Ui.chip(this, label, c.equals(rules.rootFolder), v -> { rules.rootFolder = c; recompute(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        }
        toggleBox.removeAllViews();
        toggleBox.addView(Ui.chip(this, "Skip hidden files", rules.skipHidden, v -> { rules.skipHidden = !rules.skipHidden; recompute(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        toggleBox.addView(Ui.chip(this, "Flatten folders", rules.flatten, v -> { rules.flatten = !rules.flatten; recompute(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        toggleBox.addView(Ui.chip(this, "Skip junk (.DS_Store…)", rules.skipJunk, v -> { rules.skipJunk = !rules.skipJunk; recompute(); }));
    }

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
        for (int i = 0; i < newPaths.length; i++) newPaths[i] = rules.apply(entries.get(i).path);
        renderChips();
        rootBtn.setText(rules.rootFolder.isEmpty() ? "(ZIP root)" : rules.rootFolder);
        stripTv.setText(String.valueOf(rules.stripLevels));
        refreshList();
    }

    private void refreshList() {
        visible.clear();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase();
        int inc = 0; long size = 0; String ex = null;
        for (int i = 0; i < entries.size(); i++) {
            if (included(i)) { inc++; size += entries.get(i).size; if (ex == null) ex = newPaths[i]; }
            if (q.isEmpty() || entries.get(i).path.toLowerCase().contains(q) || (newPaths[i] != null && newPaths[i].toLowerCase().contains(q))) visible.add(i);
        }
        summary.setText("Will upload " + inc + " of " + entries.size() + " files • " + PathUtils.formatSize(size));
        example.setText(ex == null ? "No files selected – adjust the options above." : "First file → " + ex);
        addBtn.setText(inc == 0 ? "Nothing to add" : "➕  Add " + inc + " file(s) to upload • " + PathUtils.formatSize(size));
        addBtn.setEnabled(inc > 0);
        adapter.notifyDataSetChanged();
    }

    private void bulk(int mode) {
        for (int i : visible) {
            if (newPaths[i] == null) continue;
            String raw = entries.get(i).raw;
            if (mode == 0) manualOff.remove(raw);
            else if (mode == 1) manualOff.add(raw);
            else if (!manualOff.remove(raw)) manualOff.add(raw);
        }
        refreshList();
    }

    private View row(final int i) {
        final Entry e = entries.get(i);
        boolean on = included(i), excluded = newPaths[i] == null;
        LinearLayout c = Ui.hbox(this);
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 7), Ui.dp(this, 16), Ui.dp(this, 7));
        TextView cb = Ui.text(this, excluded ? "⊘" : on ? "☑" : "☐", 20, false, on ? R.attr.gpPrimary : R.attr.gpMuted);
        cb.setPadding(0, 0, Ui.dp(this, 10), 0);
        c.addView(cb);
        LinearLayout t = Ui.vbox(this);
        t.addView(Ui.text(this, excluded ? e.path : newPaths[i], 12, true, R.attr.gpText));
        if (!excluded && !newPaths[i].equals(e.path)) t.addView(Ui.text(this, "from  " + e.path, 10, false, R.attr.gpMuted));
        if (excluded) t.addView(Ui.text(this, "excluded by rules", 10, false, R.attr.gpWarn));
        c.addView(t, Ui.weight(1f));
        c.addView(Ui.text(this, PathUtils.formatSize(e.size), 10, false, R.attr.gpMuted));
        c.setAlpha(excluded || !on ? 0.5f : 1f);
        c.setBackgroundResource(R.drawable.bg_ripple_row);
        c.setOnClickListener(v -> {
            if (newPaths[i] == null) { Ui.toast(this, "Excluded by your rules"); return; }
            if (!manualOff.remove(e.raw)) manualOff.add(e.raw);
            refreshList();
        });
        c.setOnLongClickListener(v -> {
            final String np = newPaths[i];
            new GlassDialog(this).setTitle(PathUtils.baseName(e.path)).setIcon("📄")
                    .setMessage("Original path:\n" + e.path + "\n\nUpload path:\n" + (np == null ? "(excluded)" : np) + "\n\nSize: " + PathUtils.formatSize(e.size))
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Copy path", (d, w) -> { Ui.copy(this, "path", np == null ? e.path : np); Ui.toast(this, "Copied"); }).show();
            return true;
        });
        return c;
    }

    private void sharePaths() {
        if (newPaths.length == 0) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) if (included(i)) sb.append(newPaths[i]).append('\n');
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString()), "Share path list"));
    }

    private void startExtract() {
        final List<String[]> mapping = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) if (included(i)) mapping.add(new String[]{entries.get(i).raw, newPaths[i]});
        if (mapping.isEmpty()) return;
        final ProgressDlg dlg = new ProgressDlg(this, "Extracting files", "🗜", () -> { if (extractJob != null) extractJob.cancel(true); });
        extractJob = app.io.submit(() -> {
            try {
                final List<StagedFile> res = ZipTools.extract(this, zip, mapping, (d, t) -> app.main.post(() -> dlg.set("Extracting " + d + " / " + t, (float) d / t)));
                app.main.post(() -> { dlg.close(); st.addStagedFiles(res); finish(); });
            } catch (Exception e) {
                app.main.post(() -> { dlg.close(); if (alive()) Ui.toast(this, msg(e)); });
            }
        });
    }
}
