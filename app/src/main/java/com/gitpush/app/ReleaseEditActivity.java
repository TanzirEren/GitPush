package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.AiClient;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Branch;
import com.gitpush.app.data.Models.CommitInfo;
import com.gitpush.app.data.Models.Release;
import com.gitpush.app.ui.AiSettings;
import com.gitpush.app.ui.AssetUploader;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Ui;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Create or edit a release: tag, title, notes (template / GitHub auto-notes / AI), assets. */
public class ReleaseEditActivity extends BaseActivity {
    private static final int REQ_ASSETS = 601;
    private static Release editing;

    public static void open(Context c, String owner, String repo, Release existing) {
        editing = existing;
        c.startActivity(new Intent(c, ReleaseEditActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private String owner, repo, target = "main";
    private GitHubApi api;
    private Release rel;
    private EditText tag, title, notes;
    private boolean draft, pre;
    private LinearLayout flags, assetBox;
    private TextView targetBtn;
    private final List<Uri> assets = new ArrayList<>();
    private List<Branch> branches = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        rel = editing;
        buildScreen(rel == null ? "New release" : "Edit release", owner + "/" + repo, true);
        com.gitpush.app.data.Models.Repo r = st.findRepo(owner, repo);
        if (r != null) target = r.defaultBranch;
        if (rel != null) { draft = rel.draft; pre = rel.prerelease; if (rel.target != null && !rel.target.isEmpty()) target = rel.target; }

        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);

        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.text(this, "Release", 13, true, R.attr.gpPrimary));
        tag = Ui.input(this, "Tag (e.g. v1.0.0)", rel == null ? "" : rel.tag);
        tag.setSingleLine(true);
        tag.setEnabled(rel == null);
        c1.addView(tag, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        targetBtn = Ui.outlineButton(this, "", v -> pickTarget());
        c1.addView(targetBtn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        title = Ui.input(this, "Release title", rel == null ? "" : rel.name == null ? "" : rel.name);
        title.setSingleLine(true);
        c1.addView(title, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        flags = Ui.hbox(this);
        c1.addView(flags, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        col.addView(c1, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "Release notes (Markdown)", 13, true, R.attr.gpPrimary));
        notes = Ui.input(this, "Describe what's new…", rel == null || rel.body == null ? "" : rel.body);
        notes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notes.setMinLines(9); notes.setGravity(Gravity.TOP);
        c2.addView(notes, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        LinearLayout tools = Ui.hbox(this);
        tools.addView(Ui.chip(this, "📝 Template", false, v -> template()), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        tools.addView(Ui.chip(this, "⚡ Auto notes", false, v -> autoNotes()), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        tools.addView(Ui.chip(this, "✨ AI notes", false, v -> aiNotes()));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tools);
        c2.addView(hs, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        col.addView(c2, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c3 = Ui.card(this);
        c3.addView(Ui.text(this, "Assets (APK, ZIP…)", 13, true, R.attr.gpPrimary));
        assetBox = Ui.vbox(this);
        c3.addView(assetBox, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        c3.addView(Ui.outlineButton(this, "＋ Add files", v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), REQ_ASSETS)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(c3, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 16));

        col.addView(Ui.button(this, rel == null ? "🚀  Publish release" : "💾  Save changes", v -> save()));
        renderFlags(); renderAssets();
        async(() -> api.branches(owner, repo), list -> branches = list, null);
    }

    private void renderFlags() {
        flags.removeAllViews();
        targetBtn.setText("Target branch: " + target + "  ▾");
        flags.addView(Ui.chip(this, "Draft", draft, v -> { draft = !draft; renderFlags(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        flags.addView(Ui.chip(this, "Pre-release", pre, v -> { pre = !pre; renderFlags(); }));
    }

    private void renderAssets() {
        assetBox.removeAllViews();
        if (assets.isEmpty()) assetBox.addView(Ui.muted(this, "No files selected."));
        for (int i = 0; i < assets.size(); i++) {
            final int idx = i;
            String name = com.gitpush.app.data.Stager.nameAndSize(this, assets.get(i))[0];
            LinearLayout r = Ui.hbox(this);
            r.addView(Ui.body(this, "📦  " + name), Ui.weight(1f));
            r.addView(Ui.textButton(this, "✕", R.attr.gpError, v -> { assets.remove(idx); renderAssets(); }));
            assetBox.addView(r);
        }
    }

    private void pickTarget() {
        if (branches.isEmpty()) { Ui.toast(this, "Loading branches…"); return; }
        String[] n = new String[branches.size()];
        for (int i = 0; i < n.length; i++) n[i] = branches.get(i).name;
        Ui.pickList(this, "Target branch", n, i -> { target = branches.get(i).name; renderFlags(); });
    }

    private void template() {
        String t = tag.getText().toString().trim();
        notes.setText("## ✨ Highlights\n- \n\n## 🐞 Fixes\n- \n\n## 📲 Install\nDownload the APK from **Assets** below" + (t.isEmpty() ? "" : " (" + t + ")") + ".\n\n## ⚠️ Known issues\n- \n");
    }

    private void autoNotes() {
        final String t = tag.getText().toString().trim();
        if (t.isEmpty()) { Ui.toast(this, "Enter a tag first"); return; }
        final ProgressDlg dlg = new ProgressDlg(this, "Generating notes", "⚡", null);
        async(() -> {
            String prev = null;
            for (String x : api.tags(owner, repo)) if (!x.equals(t)) { prev = x; break; }
            return api.generateNotes(owner, repo, t, target, prev);
        }, r -> { dlg.close(); notes.setText(r[1]); if (title.getText().toString().trim().isEmpty() && !r[0].isEmpty()) title.setText(r[0]); },
                e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    private void aiNotes() {
        if (!AiClient.configured(st.store)) { AiSettings.show(this, this::aiNotes); return; }
        final String t = tag.getText().toString().trim();
        final ProgressDlg dlg = new ProgressDlg(this, "AI is writing notes", "✨", null);
        dlg.set("Reading recent commits…", -1f);
        async(() -> {
            List<CommitInfo> commits = api.commits(owner, repo, target, 1, null);
            StringBuilder sb = new StringBuilder("Repository: " + owner + "/" + repo + "\nNew version tag: " + (t.isEmpty() ? "(not set)" : t) + "\n\nRecent commit messages (newest first):\n");
            for (CommitInfo c : commits) sb.append("- ").append(c.firstLine()).append('\n');
            return AiClient.stripFence(AiClient.complete(st.store,
                    "You write concise, friendly GitHub release notes in Markdown. Group changes under headings like Highlights, Improvements and Fixes using emojis sparingly. "
                            + "Only mention changes supported by the commit messages. Add a short Install section telling users to download the APK or file from Assets. Return only the Markdown.",
                    sb.toString(), 1500));
        }, text -> { dlg.close(); notes.setText(text); }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    private void save() {
        final String t = tag.getText().toString().trim();
        if (rel == null && t.isEmpty()) { Ui.toast(this, "Enter a tag name"); return; }
        final String name = title.getText().toString().trim(), body = notes.getText().toString();
        final ProgressDlg dlg = new ProgressDlg(this, rel == null ? "Publishing release" : "Saving", "🏷", null);
        async(() -> {
            if (rel == null) return api.createRelease(owner, repo, t, target, name.isEmpty() ? t : name, body, draft, pre, false);
            return api.updateRelease(owner, repo, rel.id, new JSONObject().put("name", name).put("body", body).put("draft", draft).put("prerelease", pre));
        }, r -> {
            dlg.close();
            Ui.toast(this, rel == null ? "Release created" : "Saved");
            AssetUploader.upload(this, api, owner, repo, r.id, assets, this::finish);
        }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_ASSETS || res != RESULT_OK || data == null) return;
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) assets.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) assets.add(data.getData());
        renderAssets();
    }
}
