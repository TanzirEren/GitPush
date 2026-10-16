package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.AiClient;
import com.gitpush.app.data.ApiException;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.FileData;
import com.gitpush.app.data.Models.RefResult;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.data.Models.TreeInfo;
import com.gitpush.app.data.Pusher;
import com.gitpush.app.ui.AiSettings;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Rich;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Generates a README.md for a repository with the user's own AI key, previews it and commits it. */
public class AiReadmeActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, AiReadmeActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private static final String[] STYLES = {"Detailed", "Minimal", "Showcase (badges + emojis)"};
    private static final String[] LANGS = {"English", "Bangla (বাংলা)", "Banglish"};
    private static final List<String> KEY_FILES = Arrays.asList("README.md", "package.json", "pubspec.yaml", "requirements.txt", "pyproject.toml", "Cargo.toml", "go.mod",
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "app/build.gradle", "app/build.gradle.kts", "index.html", "composer.json");
    private static final List<String> MAIN_FILES = Arrays.asList("AndroidManifest.xml", "MainActivity.java", "MainActivity.kt", "main.py", "app.py", "index.js", "main.js",
            "main.dart", "main.go", "main.rs", "Main.java", "App.js", "server.js");

    private String owner, repo, branch = "main";
    private GitHubApi api;
    private int style = 0, lang = 0;
    private EditText extra, editor;
    private TextView preview;
    private LinearLayout chips, resultBox;
    private String result = "";
    private boolean editing;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        buildScreen("AI README writer", owner + "/" + repo, true);
        addTextAction("AI setup", v -> AiSettings.show(this, null));
        Repo r = st.findRepo(owner, repo);
        if (r != null) branch = r.defaultBranch;

        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);

        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "✨ Write a README with AI", 15, true, R.attr.gpText));
        c.addView(Ui.muted(this, "The AI reads your file tree and a few key files (about 24 KB max), then writes the README. Uses your own API key."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 8));
        chips = Ui.vbox(this);
        c.addView(chips);
        extra = Ui.input(this, "Extra instructions (optional) e.g. mention the OAuth login", "");
        extra.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        c.addView(extra, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 10));
        c.addView(Ui.button(this, "✨  Generate README", v -> generate()));
        col.addView(c, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        resultBox = Ui.vbox(this);
        col.addView(resultBox);
        renderChips();
    }

    private void renderChips() {
        chips.removeAllViews();
        chips.addView(Ui.muted(this, "Style"));
        chips.addView(chipRow(STYLES, style, i -> { style = i; renderChips(); }));
        chips.addView(Ui.muted(this, "Language"), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        chips.addView(chipRow(LANGS, lang, i -> { lang = i; renderChips(); }));
    }

    private HorizontalScrollView chipRow(String[] names, int sel, final Ui.Cb<Integer> cb) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.hbox(this);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            row.addView(Ui.chip(this, names[i], sel == i, v -> cb.on(idx)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 6, 0));
        }
        hs.addView(row);
        return hs;
    }

    private void generate() {
        if (!AiClient.configured(st.store)) { AiSettings.show(this, this::generate); return; }
        final ProgressDlg dlg = new ProgressDlg(this, "Writing README", "✨", null);
        dlg.set("Reading repository…", -1f);
        final String styleName = STYLES[style], langName = LANGS[lang], more = extra.getText().toString().trim();
        async(() -> {
            Repo r = st.findRepo(owner, repo);
            if (r == null) r = api.repo(owner, repo);
            String br = r.defaultBranch;
            RefResult ref = api.ref(owner, repo, br);
            if (ref.kind != RefResult.FOUND) throw new ApiException(404, "This repository has no commits yet – push some files first.");
            List<TreeInfo> tree = api.treeRecursive(owner, repo, api.commitTree(owner, repo, ref.sha));
            StringBuilder paths = new StringBuilder();
            List<String> blobs = new ArrayList<>();
            boolean hasLicense = false;
            for (TreeInfo t : tree) {
                if (!t.type.equals("blob")) continue;
                String p = t.path;
                if (p.startsWith("node_modules/") || p.contains("/node_modules/") || p.startsWith("build/") || p.contains("/build/") || p.startsWith(".git/") || p.contains(".gradle/")) continue;
                if (p.equalsIgnoreCase("LICENSE") || p.equalsIgnoreCase("LICENSE.md")) hasLicense = true;
                blobs.add(p);
                if (blobs.size() <= 250) paths.append(p).append('\n');
            }
            StringBuilder files = new StringBuilder();
            int budget = 24000;
            List<String> chosen = new ArrayList<>();
            for (String k : KEY_FILES) for (String p : blobs) if (p.equals(k) && !chosen.contains(p)) chosen.add(p);
            for (String p : blobs) if (chosen.size() < 10 && MAIN_FILES.contains(PathUtils.baseName(p)) && !chosen.contains(p)) chosen.add(p);
            for (String p : chosen) {
                if (budget <= 0) break;
                try {
                    FileData f = api.file(owner, repo, p, br);
                    if (f.bytes.length > 80000) continue;
                    String text = Pusher.tryText(f.bytes);
                    if (text == null) continue;
                    int limit = Math.min(5000, budget);
                    if (text.length() > limit) text = text.substring(0, limit) + "\n…(truncated)";
                    budget -= text.length();
                    files.append("\n--- ").append(p).append(" ---\n").append(text).append('\n');
                } catch (ApiException ignored) { }
            }
            List<String> topics = new ArrayList<>();
            try { topics = api.topics(owner, repo); } catch (ApiException ignored) { }
            String user = "Repository: " + owner + "/" + repo + "\nDescription: " + (r.description == null ? "(none)" : r.description)
                    + "\nMain language: " + (r.language == null ? "unknown" : r.language) + "\nTopics: " + topics
                    + "\nHas LICENSE file: " + hasLicense + "\nDefault branch: " + br
                    + "\n\nREADME style: " + styleName + "\nWrite the README in: " + langName
                    + (more.isEmpty() ? "" : "\nExtra instructions: " + more)
                    + "\n\nFile tree (" + blobs.size() + " files" + (blobs.size() > 250 ? ", first 250 shown" : "") + "):\n" + paths
                    + "\nKey files:" + files;
            String system = "You are an expert open-source technical writer. Write a complete, accurate README.md in GitHub-flavoured Markdown for the repository described by the user. "
                    + "Use only facts supported by the provided files; never invent features, URLs, commands or screenshots (use clearly marked placeholders instead). "
                    + "Typical sections: title + one-line summary, features, installation / build, usage, project structure, tech stack, contributing, license. "
                    + "Match the requested style and language. Return ONLY the Markdown with no surrounding code fence or commentary.";
            branch = br;
            return AiClient.stripFence(AiClient.complete(st.store, system, user, 3500));
        }, text -> { dlg.close(); result = text; editing = false; renderResult(); },
                e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    private void renderResult() {
        resultBox.removeAllViews();
        if (result.isEmpty()) return;
        LinearLayout c = Ui.card(this);
        LinearLayout tabs = Ui.hbox(this);
        tabs.addView(Ui.chip(this, "Preview", !editing, v -> { if (editor != null) result = editor.getText().toString(); editing = false; renderResult(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        tabs.addView(Ui.chip(this, "Edit", editing, v -> { editing = true; renderResult(); }));
        c.addView(tabs);
        if (editing) {
            editor = Ui.input(this, "README.md", result);
            editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editor.setTypeface(android.graphics.Typeface.MONOSPACE);
            editor.setTextSize(12); editor.setMinLines(14); editor.setGravity(Gravity.TOP);
            c.addView(editor, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        } else {
            preview = Ui.body(this, "");
            preview.setText(Rich.markdown(this, result));
            c.addView(preview, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        }
        LinearLayout actions = Ui.hbox(this);
        actions.addView(Ui.outlineButton(this, "Copy", v -> { Ui.copy(this, "README", current()); Ui.toast(this, "Copied"); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        actions.addView(Ui.outlineButton(this, "Add to upload", v -> st.addGenerated("README.md", current())));
        c.addView(actions, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        c.addView(Ui.button(this, "💾  Commit README.md to " + branch, v -> commit()), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        resultBox.addView(c);
        Ui.stagger(resultBox);
    }

    private String current() { return editing && editor != null ? editor.getText().toString() : result; }

    private void commit() {
        final String text = current();
        Ui.confirm(this, "Commit README.md?", "This creates or replaces README.md on '" + branch + "' with a new commit.", "Commit", () -> {
            final ProgressDlg dlg = new ProgressDlg(this, "Committing", "💾", null);
            async(() -> {
                String sha = null;
                try { sha = api.file(owner, repo, "README.md", branch).sha; } catch (ApiException e) { if (e.code != 404) throw e; }
                return api.putText(owner, repo, "README.md", "docs: update README (AI generated)", branch, sha, text);
            }, r -> { dlg.close(); Ui.toast(this, "README committed"); }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
        });
    }
}
