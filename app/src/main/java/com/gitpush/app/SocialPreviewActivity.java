package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Downloader;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Social preview studio. GitHub has NO API for uploading the social preview (only the website's Settings page can),
 * so this screen prepares a perfect 1280x640 image (<1 MB), saves it / commits it, and takes you to the exact settings page.
 */
public class SocialPreviewActivity extends BaseActivity {
    private static final int REQ_IMAGE = 951, W = 1280, H = 640;

    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, SocialPreviewActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private String owner, repo;
    private Repo info;
    private Bitmap source, result;
    private byte[] encoded;
    private String mime = "image/png";
    private boolean cover = true;
    private int bg = 0;          // 0 dark, 1 light, 2 gray
    private boolean generated = true;
    private ImageView preview;
    private TextView checks;
    private LinearLayout chips;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        info = st.findRepo(owner, repo);
        buildScreen("Social preview", owner + "/" + repo, true);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);

        LinearLayout c1 = Ui.card(this);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundResource(R.drawable.bg_input);
        c1.addView(preview, Ui.lp(Ui.MATCH, Ui.dp(this, 170)));
        checks = Ui.muted(this, "");
        c1.addView(checks, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        col.addView(c1, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "Design", 13, true, R.attr.gpPrimary));
        chips = Ui.vbox(this);
        c2.addView(chips, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(c2, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c3 = Ui.card(this);
        c3.addView(Ui.text(this, "Use it", 13, true, R.attr.gpPrimary));
        c3.addView(Ui.button(this, "🌐  Open GitHub settings to upload", v -> uploadGuide()), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        c3.addView(Ui.outlineButton(this, "⬇  Save image to device", v -> save()), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        c3.addView(Ui.outlineButton(this, "⬆  Commit to repo (.github/social-preview)", v -> commit()), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        c3.addView(Ui.outlineButton(this, "👁  Show the current live card", v -> showCurrent()), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(c3, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        col.addView(Ui.muted(this, "Why a few manual steps? GitHub offers no API to set the social preview – it can only be uploaded on the website (Settings → Social preview). GitPush prepares the exact file and opens that page for you."));
        render();
        rebuild();
    }

    private void renderChips() {
        chips.removeAllViews();
        LinearLayout r1 = Ui.hbox(this);
        r1.addView(Ui.chip(this, "✨ Generate card", generated, v -> { generated = true; rebuild(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        r1.addView(Ui.chip(this, "🖼 Pick image", !generated, v -> pickImage()), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        chips.addView(r1);
        LinearLayout r2 = Ui.hbox(this);
        String[] bgs = {"Dark", "Light", "Gray"};
        for (int i = 0; i < 3; i++) { final int k = i; r2.addView(Ui.chip(this, bgs[i], bg == i, v -> { bg = k; rebuild(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 6, 0)); }
        if (!generated) {
            r2.addView(Ui.chip(this, "Fill (crop)", cover, v -> { cover = true; rebuild(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 6, 8, 6, 0));
            r2.addView(Ui.chip(this, "Fit (bars)", !cover, v -> { cover = false; rebuild(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 6, 0));
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(r2);
        chips.addView(hs);
    }

    private void pickImage() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_IMAGE);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (Downloader.onResult(this, req, res, data)) return;
        if (req != REQ_IMAGE || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri u = data.getData();
        async(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1;
            while (o.outWidth / sample > 3000 || o.outHeight / sample > 3000) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(u)) { Bitmap bm = BitmapFactory.decodeStream(in, null, o); if (bm == null) throw new Exception("Could not read that image"); return bm; }
        }, bm -> { source = bm; generated = false; rebuild(); }, e -> Ui.toast(this, msg(e)));
    }

    private int bgColor() { return bg == 0 ? 0xFF080808 : bg == 1 ? 0xFFFFFFFF : 0xFFE8E8E8; }
    private int fgColor() { return bg == 0 ? 0xFFFFFFFF : 0xFF080808; }
    private int subColor() { return bg == 0 ? 0xFF999999 : 0xFF777777; }

    private void rebuild() {
        renderChips();
        Bitmap bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        c.drawColor(bgColor());
        if (generated || source == null) drawCard(c); else drawImage(c);
        result = bm;
        encode();
        render();
    }

    private void drawImage(Canvas c) {
        int sw = source.getWidth(), sh = source.getHeight();
        float scale = cover ? Math.max((float) W / sw, (float) H / sh) : Math.min((float) W / sw, (float) H / sh);
        int dw = Math.round(sw * scale), dh = Math.round(sh * scale);
        Rect dst = new Rect((W - dw) / 2, (H - dh) / 2, (W - dw) / 2 + dw, (H - dh) / 2 + dh);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(source, null, dst, p);
    }

    private void drawCard(Canvas c) {
        Typeface head = Ui.font(this, true), body = Ui.font(this, false), mono = Ui.mono(this, true);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(bg == 0 ? 0xFF333333 : 0xFFCCCCCC);
        c.drawRect(64, 64, 72, H - 64, line);
        TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(subColor()); label.setTextSize(24); label.setLetterSpacing(0.18f);
        if (mono != null) label.setTypeface(mono);
        c.drawText("GITHUB REPOSITORY", 112, 120, label);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(fgColor()); t.setTextSize(112);
        if (head != null) t.setTypeface(head);
        String name = info != null ? info.name : repo;
        StaticLayout nl = layout(name, t, W - 112 - 80, 2);
        c.save(); c.translate(112, 170); nl.draw(c); c.restore();
        int y = 170 + nl.getHeight() + 24;
        String desc = info != null && info.description != null ? info.description : "";
        if (!desc.isEmpty()) {
            TextPaint d = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            d.setColor(subColor()); d.setTextSize(40);
            if (body != null) d.setTypeface(body);
            StaticLayout dl = layout(desc, d, W - 112 - 100, 3);
            c.save(); c.translate(112, y); dl.draw(c); c.restore();
        }
        TextPaint f = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(fgColor()); f.setTextSize(30);
        if (mono != null) f.setTypeface(mono);
        String foot = owner + "/" + repo + (info != null && info.language != null ? "   •   " + info.language : "") + (info != null && info.stars > 0 ? "   •   ★ " + info.stars : "");
        c.drawText(TextUtils.ellipsize(foot, f, W - 112 - 80, TextUtils.TruncateAt.END).toString(), 112, H - 84, f);
    }

    private static StaticLayout layout(String text, TextPaint p, int width, int maxLines) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), p, width).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0, 1.05f).build();
    }

    /** PNG, or JPEG when the PNG would not fit GitHub's 1 MB limit. */
    private void encode() {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        result.compress(Bitmap.CompressFormat.PNG, 100, bo);
        mime = "image/png";
        if (bo.size() > 1_000_000) {
            mime = "image/jpeg";
            for (int q = 95; q >= 40; q -= 10) { bo.reset(); result.compress(Bitmap.CompressFormat.JPEG, q, bo); if (bo.size() <= 960_000) break; }
        }
        encoded = bo.toByteArray();
    }

    private String ext() { return mime.equals("image/png") ? "png" : "jpg"; }

    private void render() {
        if (result == null) return;
        preview.setImageBitmap(result);
        int kb = encoded == null ? 0 : encoded.length / 1024;
        boolean small = !generated && source != null && (source.getWidth() < 640 || source.getHeight() < 320);
        checks.setText("✔ " + W + "×" + H + " px  (GitHub recommends 1280×640, minimum 640×320)\n" + (encoded != null && encoded.length < 1_000_000 ? "✔ " : "✘ ") + PathUtils.formatSize(encoded == null ? 0 : encoded.length) + "  •  " + (mime.equals("image/png") ? "PNG" : "JPEG") + "  (limit: under 1 MB)"
                + (small ? "\n⚠ The source picture is small and will look soft." : ""));
    }

    // ------------------------------------------------------------------ actions
    private void save() {
        if (encoded == null) return;
        final byte[] data = encoded;
        Downloader.requestWrite(this, "social-preview-" + repo + "." + ext(), (os, cb) -> os.write(data));
    }

    private void commit() {
        if (encoded == null) return;
        final byte[] data = encoded;
        final String path = ".github/social-preview." + ext();
        final String branch = info != null ? info.defaultBranch : "main";
        final ProgressDlg dlg = new ProgressDlg(this, "Committing image", "⬆", null);
        async(() -> {
            String sha = null;
            try { sha = st.api().file(owner, repo, path, branch).sha; } catch (Exception ignored) { }
            return st.api().putFile(owner, repo, path, "Add social preview image", branch, sha, () -> new ByteArrayInputStream(data))[1];
        }, url -> { dlg.close(); Ui.toast(this, "Committed " + path); }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    private void showCurrent() {
        final ProgressDlg dlg = new ProgressDlg(this, "Loading live card", "👁", null);
        async(() -> {
            byte[] b = st.api().fetchPublic("https://opengraph.githubassets.com/" + System.currentTimeMillis() + "/" + owner + "/" + repo, 3_000_000);
            Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
            if (bm == null) throw new Exception("Could not read the image");
            return bm;
        }, bm -> { dlg.close(); preview.setImageBitmap(bm); checks.setText("This is the card GitHub shows right now (generated cards can lag a few minutes). Tap any design chip to go back."); },
                e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    private void uploadGuide() {
        final String url = "https://github.com/" + owner + "/" + repo + "/settings";
        String msg = "1. Tap “Save image to device” first.\n2. Open Settings below and scroll to “Social preview”.\n3. Tap Edit → Upload an image… and choose the saved file.\n\n"
                + (info != null && info.isPrivate ? "This repository is private: the image only shows when the repo is public." : "The new card appears when the link is shared (chat apps cache old cards for a while).");
        new GlassDialog(this).setTitle("Upload on GitHub").setIcon("🌐").setMessage(msg)
                .setPositiveButton("Open settings", (d, w) -> Ui.openUrl(this, url)).setNegativeButton("Close", null).show();
    }
}
