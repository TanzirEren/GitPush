package com.gitpush.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.Stager;
import com.gitpush.app.data.Stager.StagedFile;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.GitHash;
import com.gitpush.app.util.PathUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolsActivity extends BaseActivity {
    private static final int REQ_HASH = 301;
    private static final Map<String, String> IGNORE = new LinkedHashMap<>();
    static {
        IGNORE.put("Android", "# Android\n*.iml\n.gradle/\n/local.properties\n/.idea/\n.DS_Store\n/build/\n/app/build/\n/captures/\n.externalNativeBuild/\n.cxx/\n*.apk\n*.aab\n*.jks\n*.keystore\n");
        IGNORE.put("Node", "# Node\nnode_modules/\nnpm-debug.log*\n.env\n.env.local\ndist/\n.cache/\ncoverage/\n");
        IGNORE.put("Python", "# Python\n__pycache__/\n*.py[cod]\n.venv/\nvenv/\n.env\n*.egg-info/\ndist/\nbuild/\n.pytest_cache/\n");
        IGNORE.put("Java", "# Java\n*.class\n*.jar\n*.war\ntarget/\nout/\nhs_err_pid*\n");
        IGNORE.put("Web", "# Web\n.DS_Store\n.netlify/\n.vercel/\n*.log\n.sass-cache/\n");
        IGNORE.put("macOS", "# macOS\n.DS_Store\n.AppleDouble\n.LSOverride\n._*\n");
        IGNORE.put("Windows", "# Windows\nThumbs.db\nDesktop.ini\n$RECYCLE.BIN/\n");
        IGNORE.put("JetBrains", "# JetBrains\n.idea/\n*.iml\n");
        IGNORE.put("VS Code", "# VS Code\n.vscode/\n");
    }

    private final List<String> chosen = new ArrayList<>();
    private TextView ignorePreview, hashInfo, hashResult;
    private LinearLayout chipRow;
    private EditText pName, pDesc, pInstall, pLicense, expected;
    private String sha256 = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildScreen("Smart tools", null, true);
        chosen.add("Android");
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        sv.addView(col);
        body.addView(sv);

        // .gitignore
        LinearLayout g = Ui.card(this);
        g.addView(Ui.text(this, ".gitignore generator", 14, true, R.attr.gpPrimary));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        chipRow = Ui.hbox(this);
        hs.addView(chipRow);
        g.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 8));
        ignorePreview = Ui.text(this, "", 11, false, R.attr.gpMuted);
        ignorePreview.setTypeface(android.graphics.Typeface.MONOSPACE);
        g.addView(ignorePreview);
        LinearLayout gb = Ui.hbox(this);
        gb.addView(Ui.outlineButton(this, "Copy", v -> { Ui.copy(this, ".gitignore", ignoreText()); Ui.toast(this, "Copied"); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        gb.addView(Ui.outlineButton(this, "Add to upload", v -> { if (chosen.isEmpty()) return; st.addGenerated(".gitignore", ignoreText()); }));
        g.addView(gb, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        col.addView(g, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
        renderChips();

        // README
        LinearLayout r = Ui.card(this);
        r.addView(Ui.text(this, "README.md generator", 14, true, R.attr.gpPrimary));
        pName = field(r, "Project name", false);
        pDesc = field(r, "Description", true);
        pInstall = field(r, "Getting started", true);
        pLicense = field(r, "License", false);
        pLicense.setText("MIT");
        LinearLayout rb = Ui.hbox(this);
        rb.addView(Ui.outlineButton(this, "Copy", v -> { Ui.copy(this, "README", readme()); Ui.toast(this, "Copied"); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        rb.addView(Ui.outlineButton(this, "Add to upload", v -> st.addGenerated("README.md", readme())));
        r.addView(rb, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        col.addView(r, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));

        // checksum
        LinearLayout h = Ui.card(this);
        h.addView(Ui.text(this, "File checksum (SHA-256)", 14, true, R.attr.gpPrimary));
        h.addView(Ui.outlineButton(this, "Choose file", v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_HASH)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 8));
        hashInfo = Ui.muted(this, "");
        h.addView(hashInfo);
        expected = Ui.input(this, "Expected SHA-256 (optional)", "");
        expected.setSingleLine(true);
        expected.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            public void afterTextChanged(android.text.Editable e) { compare(); }
        });
        expected.setVisibility(android.view.View.GONE);
        h.addView(expected, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        hashResult = Ui.text(this, "", 13, true, R.attr.gpOk);
        h.addView(hashResult, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(h, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));

        // size calculator
        LinearLayout s = Ui.card(this);
        s.addView(Ui.text(this, "Upload size calculator", 14, true, R.attr.gpPrimary));
        long total = 0; StagedFile big = null;
        for (StagedFile f : st.staged) { total += f.size; if (big == null || f.size > big.size) big = f; }
        s.addView(Ui.body(this, st.staged.size() + " staged file(s) • " + PathUtils.formatSize(total)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        s.addView(Ui.muted(this, "Largest: " + (big == null ? "–" : big.path + " (" + PathUtils.formatSize(big.size) + ")")));
        col.addView(s);
    }

    private EditText field(LinearLayout parent, String hint, boolean multi) {
        EditText e = Ui.input(this, hint, "");
        if (multi) { e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); e.setMinLines(2); e.setGravity(Gravity.TOP); }
        parent.addView(e, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        return e;
    }

    private String ignoreText() {
        StringBuilder sb = new StringBuilder();
        for (String c : chosen) sb.append(IGNORE.get(c)).append('\n');
        return sb.toString();
    }

    private void renderChips() {
        chipRow.removeAllViews();
        for (final String k : IGNORE.keySet())
            chipRow.addView(Ui.chip(this, k, chosen.contains(k), v -> { if (!chosen.remove(k)) chosen.add(k); renderChips(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        String t = ignoreText();
        String[] lines = t.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length && i < 8; i++) sb.append(lines[i]).append('\n');
        ignorePreview.setText(sb.toString().trim());
    }

    private String readme() {
        String n = pName.getText().toString().trim(), d = pDesc.getText().toString().trim(), i = pInstall.getText().toString().trim(), l = pLicense.getText().toString().trim();
        return "# " + (n.isEmpty() ? "Project name" : n) + "\n\n" + (d.isEmpty() ? "A short description of the project." : d)
                + "\n\n## Getting started\n\n" + (i.isEmpty() ? "Clone the repository and follow the build steps." : i)
                + "\n\n## License\n\n" + (l.isEmpty() ? "MIT" : l) + "\n";
    }

    private void compare() {
        String e = expected.getText().toString().trim();
        if (e.isEmpty() || sha256.isEmpty()) { hashResult.setText(""); return; }
        boolean ok = e.equalsIgnoreCase(sha256);
        hashResult.setText(ok ? "✔ Checksum matches" : "✘ Checksum does NOT match");
        hashResult.setTextColor(Ui.color(this, ok ? R.attr.gpOk : R.attr.gpError));
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_HASH || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri u = data.getData();
        hashInfo.setText("Calculating…");
        async(() -> {
            String[] ns = Stager.nameAndSize(this, u);
            String a, b;
            try (InputStream in = getContentResolver().openInputStream(u)) { a = GitHash.sha256(in); }
            try (InputStream in = getContentResolver().openInputStream(u)) { b = GitHash.blobSha(Long.parseLong(ns[1]), in); }
            return new String[]{ns[0], ns[1], a, b};
        }, r -> {
            sha256 = r[2];
            hashInfo.setText(r[0] + " • " + PathUtils.formatSize(Long.parseLong(r[1])) + "\nSHA-256:\n" + r[2] + "\nGit blob SHA-1:\n" + r[3]);
            hashInfo.setTextIsSelectable(true);
            expected.setVisibility(android.view.View.VISIBLE);
            compare();
        }, e -> hashInfo.setText(msg(e)));
    }
}
