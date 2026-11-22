package com.gitpush.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.KeyVault;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.data.Models.SecretInfo;
import com.gitpush.app.data.SecretsPush;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Downloader;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.RepoPicker;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.KeystoreKit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** One saved key: copy every value, push the four secrets to repos, export everything. */
public class KeyDetailActivity extends BaseActivity {
    private KeyVault.Entry e;
    private LinearLayout col;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        e = KeyVault.find(st.store, getIntent().getStringExtra("id"));
        if (e == null) { finish(); return; }
        buildScreen(e.name.isEmpty() ? e.alias : e.name, "Signing key", true);
        addAction(R.drawable.i_trash, v -> Ui.confirmDanger(this, "Delete this key?", "It is removed from this device's vault. Secrets already pushed to GitHub stay. Export it first if you need a backup – a lost keystore cannot be recovered.", "Delete", () -> { KeyVault.remove(st.store, e.id); finish(); }));
        ScrollView sv = new ScrollView(this);
        col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);
        render();
    }

    private Map<String, String> secretValues() {
        String[] n = KeyVault.names(st.store);
        Map<String, String> m = new LinkedHashMap<>();
        m.put(n[0], KeystoreKit.b64(e.file())); m.put(n[1], e.storePass); m.put(n[2], e.alias); m.put(n[3], e.keyPass);
        return m;
    }

    private void render() {
        col.removeAllViews();
        LinearLayout acts = Ui.vbox(this);
        acts.addView(Ui.button(this, "🚀  Push 4 secrets to repositories", v -> pushDialog()));
        acts.addView(Ui.outlineButton(this, "⬇  Download everything (ZIP)", v -> exportZip()), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        LinearLayout r = Ui.hbox(this);
        r.addView(Ui.outlineButton(this, "📋 key.txt", v -> { Ui.copy(this, "key.txt", keyTxt()); Ui.toast(this, "key.txt copied"); }), Ui.weight(1f));
        r.addView(Ui.outlineButton(this, "Files…", v -> fileMenu()), Ui.lp(this, Ui.WRAP, Ui.WRAP, 8, 0, 0, 0));
        r.addView(Ui.outlineButton(this, "✔ Verify", v -> verify()), Ui.lp(this, Ui.WRAP, Ui.WRAP, 8, 0, 0, 0));
        acts.addView(r, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        col.addView(acts, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 12));

        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.text(this, "GitHub secrets", 13, true, R.attr.gpPrimary));
        String[] n = KeyVault.names(st.store);
        String b64 = KeystoreKit.b64(e.file());
        c1.addView(value(n[0], b64, b64.length() > 40 ? b64.substring(0, 36) + "…  (" + b64.length() + " chars)" : b64));
        c1.addView(value(n[1], e.storePass, e.storePass));
        c1.addView(value(n[2], e.alias, e.alias));
        c1.addView(value(n[3], e.keyPass, e.keyPass));
        col.addView(c1, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "Fingerprints (Firebase, Google Cloud, Facebook)", 13, true, R.attr.gpPrimary));
        c2.addView(value("SHA-1", e.sha1, e.sha1));
        c2.addView(value("SHA-256", e.sha256, e.sha256));
        c2.addView(value("MD5", e.md5, e.md5));
        c2.addView(value("Facebook key hash", e.fb, e.fb));
        col.addView(c2, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c3 = Ui.card(this);
        c3.addView(Ui.text(this, "Project", 13, true, R.attr.gpPrimary));
        if (!e.app.isEmpty()) c3.addView(value("App name", e.app, e.app));
        if (!e.pkg.isEmpty()) c3.addView(value("Package", e.pkg, e.pkg));
        c3.addView(value("Certificate", e.subject, e.subject));
        c3.addView(value("Valid until", e.validTo, e.validTo + "  •  " + e.algo + "  •  stored as " + e.type));
        col.addView(c3, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c4 = Ui.card(this);
        c4.addView(Ui.text(this, "Use in your workflow", 13, true, R.attr.gpPrimary));
        c4.addView(Ui.muted(this, "Copy the steps that decode the keystore and expose the passwords to Gradle."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 8));
        LinearLayout rr = Ui.hbox(this);
        rr.addView(Ui.outlineButton(this, "Workflow YAML", v -> { Ui.copy(this, "yaml", workflowYaml()); Ui.toast(this, "Copied"); }), Ui.weight(1f));
        rr.addView(Ui.outlineButton(this, "build.gradle.kts", v -> { Ui.copy(this, "gradle", gradleSnippet()); Ui.toast(this, "Copied"); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 8, 0, 0, 0));
        c4.addView(rr);
        col.addView(c4);
    }

    private View value(final String label, final String copy, String shown) {
        LinearLayout row = Ui.hbox(this);
        LinearLayout t = Ui.vbox(this);
        t.addView(Ui.label(this, label));
        TextView v = Ui.body(this, shown);
        v.setTypeface(android.graphics.Typeface.MONOSPACE);
        v.setTextSize(11);
        t.addView(v);
        row.addView(t, Ui.weight(1f));
        row.addView(Ui.textButton(this, "Copy", R.attr.gpText, x -> { Ui.copy(this, label, copy); Ui.toast(this, label + " copied"); }));
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
        return row;
    }

    // ------------------------------------------------------------------ text outputs
    private String keyTxt() {
        String[] n = KeyVault.names(st.store);
        StringBuilder sb = new StringBuilder();
        sb.append("GitPush signing key – ").append(e.name).append("\nCreated: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date(e.created))).append("\n\n");
        sb.append("== Project ==\nProject: ").append(e.name).append("\nApp name: ").append(e.app).append("\nPackage: ").append(e.pkg).append("\nCertificate: ").append(e.subject)
                .append("\nValid until: ").append(e.validTo).append("\nAlgorithm: ").append(e.algo).append("\n\n");
        sb.append("== GitHub Actions secrets ==\n").append(n[0]).append(" = ").append(KeystoreKit.b64(e.file())).append("\n").append(n[1]).append(" = ").append(e.storePass)
                .append("\n").append(n[2]).append(" = ").append(e.alias).append("\n").append(n[3]).append(" = ").append(e.keyPass).append("\n\n");
        sb.append("== Fingerprints (Firebase / Google Cloud / Play) ==\nSHA-1:   ").append(e.sha1).append("\nSHA-256: ").append(e.sha256).append("\nMD5:     ").append(e.md5)
                .append("\nFacebook key hash: ").append(e.fb).append("\n\nKEEP THIS FILE PRIVATE. Anyone with the keystore and passwords can sign apps as you.\n");
        return sb.toString();
    }

    private String workflowYaml() {
        String[] n = KeyVault.names(st.store);
        return "      - name: Decode keystore\n        run: |\n          echo \"${{ secrets." + n[0] + " }}\" | base64 -d > \"$RUNNER_TEMP/release.keystore\"\n"
                + "          echo \"KEYSTORE_FILE=$RUNNER_TEMP/release.keystore\" >> $GITHUB_ENV\n"
                + "          echo \"KEYSTORE_PASSWORD=${{ secrets." + n[1] + " }}\" >> $GITHUB_ENV\n"
                + "          echo \"KEY_ALIAS=${{ secrets." + n[2] + " }}\" >> $GITHUB_ENV\n"
                + "          echo \"KEY_PASSWORD=${{ secrets." + n[3] + " }}\" >> $GITHUB_ENV\n";
    }

    private String gradleSnippet() {
        return "    val ksPath = System.getenv(\"KEYSTORE_FILE\")\n    if (ksPath != null && File(ksPath).exists()) {\n        signingConfigs {\n            create(\"release\") {\n"
                + "                storeFile = File(ksPath)\n                storePassword = System.getenv(\"KEYSTORE_PASSWORD\")\n                keyAlias = System.getenv(\"KEY_ALIAS\")\n                keyPassword = System.getenv(\"KEY_PASSWORD\")\n            }\n        }\n    }\n"
                + "    // then in buildTypes { release { signingConfig = signingConfigs.getByName(\"release\") } }\n";
    }

    // ------------------------------------------------------------------ files
    private KeystoreKit.Material material() throws Exception { return KeystoreKit.read(e.file(), e.storePass.toCharArray(), e.keyPass.toCharArray(), e.alias); }

    private byte[] fileAs(String type) throws Exception {
        if (type.equals(e.type)) return e.file();
        return KeystoreKit.write(type, material(), e.storePass.toCharArray(), e.keyPass.toCharArray());
    }

    private String safe() { return (e.name.isEmpty() ? e.alias : e.name).replaceAll("[^A-Za-z0-9_-]+", "-"); }

    private void fileMenu() {
        final String[] items = {"⬇  keystore.p12", "⬇  keystore.jks", "⬇  keystore.bks", "⬇  certificate.pem (Play upload certificate)", "⬇  KEYSTORE_BASE64.txt", "⬇  key.txt"};
        Ui.pickList(this, "Save a single file", items, i -> {
            final String base = safe();
            if (i == 0) Downloader.requestWrite(this, base + ".p12", (os, cb) -> os.write(fileAs("PKCS12")));
            else if (i == 1) Downloader.requestWrite(this, base + ".jks", (os, cb) -> os.write(fileAs("JKS")));
            else if (i == 2) { if (!KeystoreKit.typeAvailable("BKS")) { Ui.toast(this, "BKS is not available on this device"); return; } Downloader.requestWrite(this, base + ".bks", (os, cb) -> os.write(fileAs("BKS"))); }
            else if (i == 3) Downloader.requestWrite(this, base + "-certificate.pem", (os, cb) -> os.write(KeystoreKit.pem(material().chain.get(0), "CERTIFICATE").getBytes("UTF-8")));
            else if (i == 4) Downloader.requestWrite(this, "KEYSTORE_BASE64.txt", (os, cb) -> os.write(KeystoreKit.b64(e.file()).getBytes("UTF-8")));
            else Downloader.requestWrite(this, "key.txt", (os, cb) -> os.write(keyTxt().getBytes("UTF-8")));
        });
    }

    private void exportZip() {
        Downloader.requestWrite(this, safe() + "-signing-key.zip", (os, cb) -> {
            ZipOutputStream z = new ZipOutputStream(os);
            String base = safe();
            put(z, "key.txt", keyTxt().getBytes("UTF-8"));
            put(z, base + ".p12", fileAs("PKCS12"));
            put(z, base + ".jks", fileAs("JKS"));
            if (KeystoreKit.typeAvailable("BKS")) { try { put(z, base + ".bks", fileAs("BKS")); } catch (Exception ignored) { } }
            put(z, base + "-certificate.pem", KeystoreKit.pem(material().chain.get(0), "CERTIFICATE").getBytes("UTF-8"));
            put(z, "KEYSTORE_BASE64.txt", KeystoreKit.b64(e.file()).getBytes("UTF-8"));
            put(z, "workflow-steps.yml", workflowYaml().getBytes("UTF-8"));
            put(z, "build.gradle.kts-snippet.txt", gradleSnippet().getBytes("UTF-8"));
            z.finish();
        });
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws Exception { z.putNextEntry(new ZipEntry(name)); z.write(data); z.closeEntry(); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Downloader.onResult(this, req, res, data);
    }

    // ------------------------------------------------------------------ push / verify
    private void pushDialog() {
        if (st.repos.isEmpty()) { Ui.toast(this, "Loading repositories…"); st.loadRepos(true, () -> { if (alive() && !st.repos.isEmpty()) pushDialog(); else if (alive()) Ui.toast(this, "No repositories found"); }); return; }
        final List<Repo> sorted = new ArrayList<>();
        for (Repo r : st.repos) if (st.isFavorite(r.fullName)) sorted.add(r);
        for (Repo r : st.repos) if (!st.isFavorite(r.fullName)) sorted.add(r);
        LinearLayout box = Ui.vbox(this);
        final EditText env = Ui.input(this, "Environment name (optional)", "");
        env.setSingleLine(true);
        box.addView(Ui.muted(this, "Pick one or more repositories. Values are encrypted on this device with each repo's public key."));
        final EditText filter = Ui.input(this, "Filter", "");
        filter.setSingleLine(true);
        box.addView(filter, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 4));
        LinearLayout list = Ui.vbox(this);
        final List<CheckBox> boxes = new ArrayList<>();
        for (Repo r : sorted) {
            CheckBox cb = new CheckBox(this);
            cb.setText((r.isPrivate ? "🔒 " : "") + r.fullName);
            cb.setTextColor(Ui.color(this, R.attr.gpText));
            cb.setEnabled(r.canPush);
            list.addView(cb);
            boxes.add(cb);
        }
        filter.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            public void onTextChanged(CharSequence s, int a, int c, int d) { }
            public void afterTextChanged(android.text.Editable x) {
                String q = x.toString().toLowerCase();
                for (int i = 0; i < boxes.size(); i++) boxes.get(i).setVisibility(q.isEmpty() || sorted.get(i).fullName.toLowerCase().contains(q) ? View.VISIBLE : View.GONE);
            }
        });
        ScrollView sv = new ScrollView(this);
        sv.addView(list);
        box.addView(sv, Ui.lp(Ui.MATCH, Ui.dp(this, 230)));
        box.addView(env, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        new GlassDialog(this).setTitle("Push secrets").setIcon("🚀").setView(box).setPositiveButton("Push", (d, w) -> {
            final List<Repo> chosen = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isChecked()) chosen.add(sorted.get(i));
            if (chosen.isEmpty()) { Ui.toast(this, "Select at least one repository"); return; }
            doPush(chosen, env.getText().toString().trim());
        }).setNegativeButton("Cancel", null).show();
    }

    private void doPush(final List<Repo> repos, final String envName) {
        final ProgressDlg dlg = new ProgressDlg(this, "Pushing secrets", "🚀", null);
        final Map<String, String> values = secretValues();
        async(() -> {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Repo r : repos) {
                final int fi = ++i; final String nm = r.fullName;
                app.main.post(() -> dlg.set(fi + " / " + repos.size() + "  •  " + nm, (float) fi / repos.size()));
                try { SecretsPush.push(st.api(), r.owner, r.name, envName, values); sb.append("✔ ").append(r.fullName).append('\n'); }
                catch (Exception ex) { sb.append("✘ ").append(r.fullName).append(": ").append(ex.getMessage()).append('\n'); }
            }
            return sb.toString().trim();
        }, text -> { dlg.close(); new GlassDialog(this).setTitle("Result").setIcon("🚀").setMessage(text).setPositiveButton("Close", null).show(); },
                ex -> { dlg.close(); Ui.toast(this, msg(ex)); });
    }

    private void verify() {
        RepoPicker.pick(this, "Check which repository?", r -> {
            final String[] names = KeyVault.names(st.store);
            async(() -> st.api().secrets(r.owner, r.name), list -> {
                java.util.Set<String> have = new java.util.HashSet<>();
                for (SecretInfo s : list) have.add(s.name);
                StringBuilder sb = new StringBuilder();
                for (String n : names) sb.append(have.contains(n) ? "✔ " : "✘ missing  ").append(n).append('\n');
                new GlassDialog(this).setTitle(r.fullName).setIcon("🔑").setMessage(sb.toString().trim()).setPositiveButton("Close", null).show();
            }, ex -> Ui.toast(this, msg(ex)));
        });
    }
}
