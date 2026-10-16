package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Asset;
import com.gitpush.app.data.Models.Release;
import com.gitpush.app.ui.AssetUploader;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.Rich;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ReleasesActivity extends BaseActivity {
    private static final int REQ_ASSETS = 501;

    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, ReleasesActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private String owner, repo;
    private GitHubApi api;
    private final List<Release> releases = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView status;
    private boolean loading = true;
    private String error;
    private Release uploadTarget;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        buildScreen("Releases", owner + "/" + repo, true);
        addTextAction("＋ New", v -> ReleaseEditActivity.open(this, owner, repo, null));
        LinearLayout page = Ui.vbox(this);
        status = Ui.muted(this, "Loading…");
        status.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), 0);
        page.addView(status);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        adapter = new BaseAdapter() {
            public int getCount() { return releases.size(); }
            public Object getItem(int i) { return releases.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { View v = row(releases.get(i)); if (cv == null) Ui.fadeIn(v, i); return v; }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        body.addView(page);
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void load() {
        async(() -> api.releases(owner, repo), list -> { loading = false; error = null; releases.clear(); releases.addAll(list); render(); },
                e -> { loading = false; error = msg(e); render(); });
    }

    private void render() {
        adapter.notifyDataSetChanged();
        if (loading) status.setText("Loading…");
        else if (error != null && releases.isEmpty()) status.setText("Couldn't load releases\n" + error);
        else if (releases.isEmpty()) status.setText("No releases yet.\nTap ＋ New to publish your first release.");
        else status.setText("");
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    private View row(final Release r) {
        LinearLayout wrap = Ui.vbox(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        LinearLayout c = Ui.card(this);
        LinearLayout h = Ui.hbox(this);
        TextView t = Ui.title(this, r.title());
        t.setMaxLines(2);
        h.addView(t, Ui.weight(1f));
        if (r.draft) h.addView(Ui.pill(this, "DRAFT", R.attr.gpWarn), Ui.lp(this, Ui.WRAP, Ui.WRAP, 6, 0, 0, 0));
        if (r.prerelease) h.addView(Ui.pill(this, "PRE-RELEASE", R.attr.gpWarn), Ui.lp(this, Ui.WRAP, Ui.WRAP, 6, 0, 0, 0));
        c.addView(h);
        c.addView(Ui.text(this, "🏷 " + r.tag + "  •  " + Ui.agoIso(r.publishedAt != null ? r.publishedAt : r.createdAt) + "  •  " + r.assets.size() + " asset(s)", 12, false, R.attr.gpMuted),
                Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        c.setOnClickListener(v -> menu(r));
        wrap.addView(c);
        return wrap;
    }

    private void menu(final Release r) {
        List<String> items = new ArrayList<>();
        items.add("📖  Notes & assets");
        items.add("✏  Edit release");
        items.add("⬆  Upload assets");
        if (r.draft) items.add("🚀  Publish draft");
        items.add("📋  Copy tag");
        items.add("🌐  Open on GitHub");
        items.add("🗑  Delete release");
        final String[] arr = items.toArray(new String[0]);
        Ui.pickList(this, r.title(), arr, i -> {
            String l = arr[i];
            if (l.contains("Notes")) details(r);
            else if (l.contains("Edit")) ReleaseEditActivity.open(this, owner, repo, r);
            else if (l.contains("Upload")) { uploadTarget = r; startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), REQ_ASSETS); }
            else if (l.contains("Publish")) async(() -> api.updateRelease(owner, repo, r.id, new JSONObject().put("draft", false)), x -> { Ui.toast(this, "Published"); load(); }, e -> Ui.toast(this, msg(e)));
            else if (l.contains("Copy")) { Ui.copy(this, "tag", r.tag); Ui.toast(this, "Tag copied"); }
            else if (l.contains("Open")) Ui.openUrl(this, r.htmlUrl);
            else Ui.confirmDanger(this, "Delete release " + r.tag + "?", "The release and its assets are removed. The git tag stays in the repository.", "Delete",
                        () -> async(() -> { api.deleteRelease(owner, repo, r.id); return "ok"; }, x -> { Ui.toast(this, "Release deleted"); load(); }, e -> Ui.toast(this, msg(e))));
        });
    }

    private void details(final Release r) {
        ScrollView sv = new ScrollView(this);
        TextView t = Ui.body(this, "");
        t.setText(r.body == null || r.body.isEmpty() ? "No release notes." : Rich.markdown(this, r.body));
        sv.addView(t);
        LinearLayout box = Ui.vbox(this);
        box.addView(sv, Ui.lp(Ui.MATCH, Ui.dp(this, 260)));
        new GlassDialog(this).setTitle(r.title()).setIcon("🏷").setView(box).setPositiveButton("Close", null)
                .setNegativeButton("Assets (" + r.assets.size() + ")", (d, w) -> assets(r)).show();
    }

    private void assets(final Release r) {
        if (r.assets.isEmpty()) { Ui.toast(this, "No assets in this release"); return; }
        String[] labels = new String[r.assets.size()];
        for (int i = 0; i < labels.length; i++) { Asset a = r.assets.get(i); labels[i] = "📦  " + a.name + "  (" + PathUtils.formatSize(a.size) + " • ⬇" + a.downloads + ")"; }
        Ui.pickList(this, "Assets", labels, i -> {
            final Asset a = r.assets.get(i);
            Ui.pickList(this, a.name, new String[]{"🌐  Download in browser", "🔗  Copy download link", "🗑  Delete asset"}, k -> {
                if (k == 0) Ui.openUrl(this, a.url);
                else if (k == 1) { Ui.copy(this, "url", a.url); Ui.toast(this, "Link copied"); }
                else Ui.confirmDanger(this, "Delete " + a.name + "?", "This asset is removed from the release.", "Delete",
                        () -> async(() -> { api.deleteAsset(owner, repo, a.id); return "ok"; }, x -> { Ui.toast(this, "Asset deleted"); load(); }, e -> Ui.toast(this, msg(e))));
            });
        });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_ASSETS || res != RESULT_OK || data == null || uploadTarget == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        AssetUploader.upload(this, api, owner, repo, uploadTarget.id, uris, this::load);
    }
}
