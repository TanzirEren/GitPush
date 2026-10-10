package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.GitOps;
import com.gitpush.app.data.Models.CommitInfo;
import com.gitpush.app.data.Models.FileData;
import com.gitpush.app.data.Pusher;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Rich;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.io.OutputStream;
import java.util.List;

public class FileViewerActivity extends BaseActivity {
    private static final int REQ_SAVE = 201;

    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, FileViewerActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private String owner, repo, path, branch, name, sha;
    private GitHubApi api;
    private byte[] bytes;
    private String text;
    private Bitmap bitmap;
    private boolean editing, preview;
    private EditText editor;
    private TextView editAction, previewAction, saveAction;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        path = st.viewerPath; branch = st.viewerBranch; sha = st.viewerSha;
        name = PathUtils.baseName(path);
        api = st.api();
        buildScreen(name, path, true);
        previewAction = addTextAction("Preview", v -> { preview = !preview; render(); });
        editAction = addTextAction("Edit", v -> { editing = true; preview = false; render(); });
        saveAction = addTextAction("Save", v -> commitDialog());
        addTextAction("⋮", v -> menu(v));
        previewAction.setVisibility(View.GONE); editAction.setVisibility(View.GONE); saveAction.setVisibility(View.GONE);
        load();
    }

    private void load() {
        body.removeAllViews();
        body.addView(Ui.muted(this, "Loading…"), new android.widget.FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        async(() -> {
            FileData f = api.file(owner, repo, path, branch);
            Bitmap bm = null; String t = null;
            if (PathUtils.isImage(name)) bm = BitmapFactory.decodeByteArray(f.bytes, 0, f.bytes.length);
            else if (f.bytes.length <= 2_000_000) t = Pusher.tryText(f.bytes);
            return new Object[]{f, bm, t};
        }, r -> {
            FileData f = (FileData) r[0];
            sha = f.sha; bytes = f.bytes; bitmap = (Bitmap) r[1]; text = (String) r[2];
            render();
        }, e -> { body.removeAllViews(); body.addView(Ui.text(this, msg(e), 13, false, R.attr.gpError), new android.widget.FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER)); });
    }

    private void render() {
        body.removeAllViews();
        boolean isText = text != null;
        editAction.setVisibility(isText && !editing ? View.VISIBLE : View.GONE);
        saveAction.setVisibility(editing ? View.VISIBLE : View.GONE);
        previewAction.setVisibility(isText && !editing && PathUtils.isMarkdown(name) ? View.VISIBLE : View.GONE);
        previewAction.setText(preview ? "Code" : "Preview");
        if (bitmap != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bitmap);
            iv.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            body.addView(iv);
        } else if (!isText) {
            LinearLayout box = Ui.empty(this, "🗄", "Binary or very large file", PathUtils.formatSize(bytes == null ? 0 : bytes.length) + " – use ⋮ → Download.");
            body.addView(box);
        } else if (editing) {
            editor = new EditText(this);
            editor.setText(text);
            editor.setTypeface(Typeface.MONOSPACE);
            editor.setTextSize(12);
            editor.setGravity(Gravity.TOP | Gravity.START);
            editor.setBackground(null);
            editor.setTextColor(Ui.color(this, R.attr.gpText));
            editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setHorizontallyScrolling(false);
            editor.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            ScrollView sv = new ScrollView(this);
            sv.setFillViewport(true);
            sv.addView(editor, Ui.lp(Ui.MATCH, Ui.MATCH));
            body.addView(sv);
        } else if (preview) {
            ScrollView sv = new ScrollView(this);
            TextView t = Ui.body(this, "");
            t.setText(Rich.markdown(this, text));
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 24));
            sv.addView(t);
            body.addView(sv);
        } else body.addView(codeList());
    }

    private View codeList() {
        final String[] lines = text.split("\n", -1);
        final String ext = PathUtils.extension(name);
        final Rich.CodeColors colors = Rich.codeColors(this);
        final boolean hl = lines.length <= 4000;
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setAdapter(new BaseAdapter() {
            public int getCount() { return lines.length; }
            public Object getItem(int i) { return lines[i]; }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) {
                LinearLayout row = Ui.hbox(FileViewerActivity.this);
                row.setGravity(Gravity.TOP);
                TextView n = new TextView(FileViewerActivity.this);
                n.setText(String.valueOf(i + 1));
                n.setTypeface(Typeface.MONOSPACE); n.setTextSize(11); n.setGravity(Gravity.END);
                n.setTextColor(Ui.color(FileViewerActivity.this, R.attr.gpMuted));
                n.setPadding(0, 0, Ui.dp(FileViewerActivity.this, 10), 0);
                row.addView(n, Ui.lp(Ui.dp(FileViewerActivity.this, 48), Ui.WRAP));
                TextView c = new TextView(FileViewerActivity.this);
                String l = lines[i].endsWith("\r") ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
                c.setText(hl ? Rich.highlight(l, ext, colors) : l);
                c.setTypeface(Typeface.MONOSPACE); c.setTextSize(12);
                c.setTextColor(Ui.color(FileViewerActivity.this, R.attr.gpText));
                row.addView(c, Ui.weight(1f));
                return row;
            }
        });
        return lv;
    }

    private void commitDialog() {
        final String newText = editor.getText().toString();
        Ui.prompt(this, "Commit changes", "Commit message", "Update " + name, m -> {
            final String msg = m.trim().isEmpty() ? "Update " + name : m.trim();
            async(() -> api.putText(owner, repo, path, msg, branch, sha, newText), r -> { Ui.toast(this, "Committed"); editing = false; load(); }, e -> Ui.toast(this, msg(e)));
        });
    }

    private void menu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        String[] items = {"Copy path", "Copy raw URL", "Download", "Rename / move", "File history", "Delete"};
        for (int i = 0; i < items.length; i++) pm.getMenu().add(0, i, i, items[i]);
        pm.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 0: Ui.copy(this, "path", path); Ui.toast(this, "Path copied"); break;
                case 1: Ui.copy(this, "url", "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + path); Ui.toast(this, "Raw URL copied"); break;
                case 2: startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_TITLE, name), REQ_SAVE); break;
                case 3: rename(); break;
                case 4: history(); break;
                default: delete();
            }
            return true;
        });
        pm.show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_SAVE || res != RESULT_OK || data == null || data.getData() == null || bytes == null) return;
        final Uri uri = data.getData();
        final byte[] b = bytes;
        async(() -> { try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(b); } return "Saved"; }, m -> Ui.toast(this, m), e -> Ui.toast(this, "Save failed: " + msg(e)));
    }

    private void rename() {
        Ui.prompt(this, "Rename / move", "New path", path, np -> {
            final String n = np.trim().replace("\\", "/");
            if (n.equals(path) || !PathUtils.isValidRepoPath(n)) { Ui.toast(this, "Invalid or unchanged path"); return; }
            async(() -> GitOps.rename(api, owner, repo, branch, path, n, "Move " + path + " to " + n), r -> { Ui.toast(this, "Moved"); finish(); }, e -> Ui.toast(this, msg(e)));
        });
    }

    private void delete() {
        Ui.confirm(this, "Delete " + name + "?", "The file is deleted from '" + branch + "' with a commit. You can revert it later from history.", "Delete", () ->
                async(() -> { api.deleteFile(owner, repo, path, sha, "Delete " + path, branch); return "ok"; }, r -> { Ui.toast(this, "Deleted"); finish(); }, e -> Ui.toast(this, msg(e))));
    }

    private void history() {
        async(() -> api.commits(owner, repo, branch, 1, path), (List<CommitInfo> list) -> {
            if (list.isEmpty()) { Ui.toast(this, "No commits found"); return; }
            final String[] labels = new String[list.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = list.get(i).firstLine() + "\n" + list.get(i).author + " • " + Ui.agoIso(list.get(i).date) + " • " + list.get(i).sha.substring(0, 7);
            new AlertDialog.Builder(this).setTitle("File history").setItems(labels, (d, w) -> CommitDetailActivity.open(this, owner, repo, list.get(w).sha)).show();
        }, e -> Ui.toast(this, msg(e)));
    }
}
