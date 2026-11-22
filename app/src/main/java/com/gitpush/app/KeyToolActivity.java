package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.gitpush.app.data.KeyVault;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.KeystoreKit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** APK signing tool: key vault, generate / import keystores, inspect an APK's signing certificate. */
public class KeyToolActivity extends BaseActivity {
    private static final int REQ_IMPORT = 901, REQ_APK = 902;

    public static void open(Context c) { c.startActivity(new Intent(c, KeyToolActivity.class)); }

    private final List<KeyVault.Entry> list = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildScreen("APK signing", "Keystores • secrets • fingerprints", true);
        addAction(R.drawable.i_import, v -> pickFile(REQ_IMPORT));
        addAction(R.drawable.i_plus, v -> startActivity(new Intent(this, KeyCreateActivity.class)));
        LinearLayout page = Ui.vbox(this);
        LinearLayout tools = Ui.hbox(this);
        tools.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 2));
        tools.addView(Ui.chip(this, "🔍 Inspect APK", false, v -> pickFile(REQ_APK)), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        tools.addView(Ui.chip(this, "🔑 Secret names", false, v -> secretNames()), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        tools.addView(Ui.chip(this, "🔐 Password", false, v -> { String p = KeystoreKit.randomPassword(20); Ui.copy(this, "password", p); Ui.toast(this, "Strong password copied: " + p); }));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tools);
        page.addView(hs);
        status = Ui.muted(this, "");
        status.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), 0);
        page.addView(status);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        adapter = new BaseAdapter() {
            public int getCount() { return list.size(); }
            public Object getItem(int i) { return list.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { return card(list.get(i)); }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        body.addView(page);
    }

    @Override protected void onResume() {
        super.onResume();
        list.clear(); list.addAll(KeyVault.load(st.store));
        adapter.notifyDataSetChanged();
        status.setText(list.isEmpty() ? "No keys yet.\nTap ＋ to generate a keystore (with fingerprints and ready-to-push secrets), or the import icon to add one you already have." : "");
        status.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private View card(final KeyVault.Entry e) {
        LinearLayout wrap = Ui.vbox(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        LinearLayout c = Ui.card(this);
        c.addView(Ui.title(this, e.name.isEmpty() ? e.alias : e.name));
        boolean soon = e.expires > 0 && e.expires < System.currentTimeMillis() + 180L * 86_400_000L;
        c.addView(Ui.text(this, "🔑 " + e.alias + "  •  " + e.algo + "  •  " + e.type + "  •  valid to " + e.validTo + (soon ? "  ⚠ expires soon" : ""), 12, false, R.attr.gpMuted), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        if (!e.pkg.isEmpty()) c.addView(Ui.text(this, e.pkg, 12, false, R.attr.gpMuted));
        TextView fp = Ui.label(this, "SHA-256  " + (e.sha256.length() > 23 ? e.sha256.substring(0, 23) + "…" : e.sha256));
        c.addView(fp, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        c.setOnClickListener(v -> startActivity(new Intent(this, KeyDetailActivity.class).putExtra("id", e.id)));
        wrap.addView(c);
        return wrap;
    }

    private void secretNames() {
        final String[] n = KeyVault.names(st.store);
        LinearLayout box = Ui.vbox(this);
        final EditText[] ed = new EditText[4];
        String[] lab = {"Keystore (Base64)", "Keystore password", "Key alias", "Key password"};
        for (int i = 0; i < 4; i++) {
            box.addView(Ui.muted(this, lab[i]), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, i == 0 ? 0 : 8, 0, 0));
            ed[i] = Ui.input(this, KeyVault.DEFAULT_NAMES[i], n[i]);
            ed[i].setSingleLine(true);
            box.addView(ed[i]);
        }
        new GlassDialog(this).setTitle("Secret names").setIcon("🔑").setView(box).setPositiveButton("Save", (d, w) -> {
            String[] out = new String[4];
            for (int i = 0; i < 4; i++) {
                out[i] = ed[i].getText().toString().trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
                if (out[i].isEmpty()) out[i] = KeyVault.DEFAULT_NAMES[i];
            }
            KeyVault.setNames(st.store, out);
            Ui.toast(this, "Secret names saved");
        }).setNeutralButton("Defaults", (d, w) -> { KeyVault.setNames(st.store, KeyVault.DEFAULT_NAMES); Ui.toast(this, "Defaults restored"); }).setNegativeButton("Cancel", null).show();
    }

    private void pickFile(int req) {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), req);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (req == REQ_IMPORT) importFlow(uri);
        else if (req == REQ_APK) inspectApk(uri);
    }

    // ------------------------------------------------------------------ import
    private byte[] readAll(Uri u, int max) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) >= 0) { bo.write(buf, 0, n); if (bo.size() > max) throw new Exception("File is too large to be a keystore"); }
            return bo.toByteArray();
        }
    }

    private void importFlow(final Uri uri) {
        final String fileName = com.gitpush.app.data.Stager.nameAndSize(this, uri)[0];
        LinearLayout box = Ui.vbox(this);
        final EditText sp = Ui.input(this, "Keystore password", ""), al = Ui.input(this, "Key alias (blank = first key)", ""), kp = Ui.input(this, "Key password (blank = same)", "");
        sp.setSingleLine(true); al.setSingleLine(true); kp.setSingleLine(true);
        box.addView(Ui.muted(this, fileName + "\nSupports .jks, .p12/.pfx and .bks"));
        box.addView(sp, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        box.addView(al, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        box.addView(kp, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        new GlassDialog(this).setTitle("Import keystore").setIcon("📥").setView(box).setPositiveButton("Import", (d, w) -> {
            final String spw = sp.getText().toString(), kpw = kp.getText().toString().isEmpty() ? spw : kp.getText().toString(), alias = al.getText().toString().trim();
            final ProgressDlg dlg = new ProgressDlg(this, "Reading keystore", "📥", null);
            async(() -> {
                byte[] data = readAll(uri, 2_000_000);
                KeystoreKit.Material m = KeystoreKit.read(data, spw.toCharArray(), kpw.toCharArray(), alias);
                KeyVault.Entry e = new KeyVault.Entry();
                e.id = UUID.randomUUID().toString(); e.name = fileName.replaceAll("\\.[A-Za-z0-9]+$", ""); e.alias = m.alias == null ? alias : m.alias;
                e.storePass = spw; e.keyPass = kpw; e.type = KeystoreKit.detectType(data); e.fileB64 = KeystoreKit.b64(data); e.created = System.currentTimeMillis();
                e.fillFrom(m);
                KeyVault.put(st.store, e);
                return e;
            }, e -> { dlg.close(); startActivity(new Intent(this, KeyDetailActivity.class).putExtra("id", e.id)); },
                    e -> { dlg.close(); Ui.toast(this, "Could not import: " + msg(e)); });
        }).setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ inspect an APK
    @SuppressWarnings("deprecation")
    private void inspectApk(final Uri uri) {
        final ProgressDlg dlg = new ProgressDlg(this, "Reading APK", "🔍", null);
        async(() -> {
            File f = new File(getCacheDir(), "inspect.apk");
            try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream os = new FileOutputStream(f)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) >= 0) os.write(buf, 0, n);
            }
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo pi;
            android.content.pm.Signature[] sigs;
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                pi = pm.getPackageArchiveInfo(f.getAbsolutePath(), android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                sigs = pi == null || pi.signingInfo == null ? null : pi.signingInfo.getApkContentsSigners();
            } else {
                pi = pm.getPackageArchiveInfo(f.getAbsolutePath(), android.content.pm.PackageManager.GET_SIGNATURES);
                sigs = pi == null ? null : pi.signatures;
            }
            f.delete();
            if (pi == null) throw new Exception("This does not look like a valid APK");
            if (sigs == null || sigs.length == 0) throw new Exception("This APK is not signed");
            byte[] der = sigs[0].toByteArray();
            StringBuilder sb = new StringBuilder();
            sb.append("Package: ").append(pi.packageName).append("\nVersion: ").append(pi.versionName).append("\n\n");
            sb.append(KeystoreKit.describe(KeystoreKit.parse(der))).append("\n\nSHA-1\n").append(KeystoreKit.fingerprint(der, "SHA-1"))
                    .append("\n\nSHA-256\n").append(KeystoreKit.fingerprint(der, "SHA-256")).append("\n\nMD5\n").append(KeystoreKit.fingerprint(der, "MD5"))
                    .append("\n\nFacebook key hash\n").append(KeystoreKit.facebookHash(der));
            return sb.toString();
        }, text -> { dlg.close(); showText("APK signature", text); }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    private void showText(String title, final String text) {
        TextView t = Ui.body(this, text);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextSize(11);
        t.setTextIsSelectable(true);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(t);
        LinearLayout box = Ui.vbox(this);
        box.addView(sv, Ui.lp(Ui.MATCH, Ui.dp(this, 320)));
        new GlassDialog(this).setTitle(title).setIcon("🔍").setView(box).setPositiveButton("Close", null).setNegativeButton("Copy all", (d, w) -> { Ui.copy(this, title, text); Ui.toast(this, "Copied"); }).show();
    }
}
