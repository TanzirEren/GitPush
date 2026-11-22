package com.gitpush.app;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.KeyVault;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.KeystoreKit;

import java.util.Locale;
import java.util.UUID;

/** New project + keystore: project info, key type, passwords, certificate details → generated on the device. */
public class KeyCreateActivity extends BaseActivity {
    private EditText project, appName, pkg, alias, storePw, keyPw, cn, ou, org, city, state, country;
    private int algoIdx = 0, yearsIdx = 0, fmtIdx = 0;
    private boolean samePw = true, showPw;
    private LinearLayout chipsBox;
    private static final String[] ALGO = {"RSA 2048", "RSA 4096", "EC P-256"};
    private static final int[] YEARS = {25, 30, 50};
    private static final String[] FMT = {"PKCS12 (.p12)", "JKS (.jks)"};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildScreen("New keystore", "Generated on this device", true);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);

        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.text(this, "Project", 13, true, R.attr.gpPrimary));
        project = field(c1, "Project name (e.g. GitPush)", "");
        appName = field(c1, "App name (shown to users)", "");
        pkg = field(c1, "Package name (com.example.app)", "");
        col.addView(c1, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "Key", 13, true, R.attr.gpPrimary));
        alias = field(c2, "Key alias", "");
        chipsBox = Ui.vbox(this);
        c2.addView(chipsBox, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        col.addView(c2, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c3 = Ui.card(this);
        c3.addView(Ui.text(this, "Passwords", 13, true, R.attr.gpPrimary));
        storePw = field(c3, "Keystore password (min 6)", KeystoreKit.randomPassword(20));
        keyPw = field(c3, "Key password (min 6)", "");
        keyPw.setVisibility(android.view.View.GONE);
        LinearLayout pr = Ui.hbox(this);
        pr.addView(Ui.chip(this, "🔐 Generate strong", false, v -> { storePw.setText(KeystoreKit.randomPassword(20)); keyPw.setText(KeystoreKit.randomPassword(20)); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        pr.addView(Ui.chip(this, "👁 Show / hide", false, v -> { showPw = !showPw; applyPwVisibility(); }));
        c3.addView(pr, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        col.addView(c3, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        LinearLayout c4 = Ui.card(this);
        c4.addView(Ui.text(this, "Certificate owner", 13, true, R.attr.gpPrimary));
        cn = field(c4, "Name / CN (defaults to app name)", "");
        ou = field(c4, "Unit / OU (optional)", "");
        org = field(c4, "Organisation / O (optional)", "");
        city = field(c4, "City / L (optional)", "");
        state = field(c4, "State / ST (optional)", "");
        country = field(c4, "Country code (2 letters, e.g. BD)", "");
        country.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2)});
        col.addView(c4, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 16));

        col.addView(Ui.button(this, "🔑  Generate keystore", v -> generate()));
        col.addView(Ui.muted(this, "RSA 4096 can take up to a minute on slower phones. Keep the screen on until it finishes."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        renderChips();
    }

    private EditText field(LinearLayout parent, String hint, String text) {
        EditText e = Ui.input(this, hint, text);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        parent.addView(e, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        return e;
    }

    private void applyPwVisibility() {
        int t = InputType.TYPE_CLASS_TEXT | (showPw ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD);
        storePw.setInputType(t); keyPw.setInputType(t);
    }

    private void renderChips() {
        chipsBox.removeAllViews();
        chipsBox.addView(Ui.muted(this, "Algorithm"));
        LinearLayout r1 = Ui.hbox(this);
        for (int i = 0; i < ALGO.length; i++) { final int k = i; r1.addView(Ui.chip(this, ALGO[i], algoIdx == i, v -> { algoIdx = k; renderChips(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 6, 0)); }
        chipsBox.addView(r1);
        chipsBox.addView(Ui.muted(this, "Validity (Google Play needs 25+ years)"), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
        LinearLayout r2 = Ui.hbox(this);
        for (int i = 0; i < YEARS.length; i++) { final int k = i; r2.addView(Ui.chip(this, YEARS[i] + " years", yearsIdx == i, v -> { yearsIdx = k; renderChips(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 6, 0)); }
        chipsBox.addView(r2);
        chipsBox.addView(Ui.muted(this, "File for KEYSTORE_BASE64 (the ZIP always has .p12, .jks and .bks)"), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
        LinearLayout r3 = Ui.hbox(this);
        for (int i = 0; i < FMT.length; i++) { final int k = i; r3.addView(Ui.chip(this, FMT[i], fmtIdx == i, v -> { fmtIdx = k; renderChips(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 6, 0)); }
        chipsBox.addView(r3);
        r3.addView(Ui.chip(this, samePw ? "Same key password ✓" : "Separate key password", samePw, v -> { samePw = !samePw; keyPw.setVisibility(samePw ? android.view.View.GONE : android.view.View.VISIBLE); renderChips(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 6, 0));
    }

    private static String s(EditText e) { return e.getText().toString().trim(); }

    private void generate() {
        final String name = s(project).isEmpty() ? s(appName) : s(project);
        if (name.isEmpty()) { Ui.toast(this, "Enter a project or app name"); return; }
        final String al = (s(alias).isEmpty() ? name : s(alias)).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        final String sp = storePw.getText().toString(), kp = samePw ? sp : keyPw.getText().toString();
        if (sp.length() < 6 || kp.length() < 6) { Ui.toast(this, "Passwords need at least 6 characters"); return; }
        final KeystoreKit.Spec spec = new KeystoreKit.Spec();
        spec.algo = algoIdx == 2 ? "EC" : "RSA"; spec.bits = algoIdx == 1 ? 4096 : 2048; spec.years = YEARS[yearsIdx];
        spec.cn = s(cn).isEmpty() ? (s(appName).isEmpty() ? name : s(appName)) : s(cn); spec.ou = s(ou); spec.o = s(org); spec.l = s(city); spec.st = s(state); spec.c = s(country);
        String err = KeystoreKit.validate(spec);
        if (err != null) { Ui.toast(this, err); return; }
        final String type = fmtIdx == 1 ? "JKS" : "PKCS12";
        final String fApp = s(appName), fPkg = s(pkg);
        final ProgressDlg dlg = new ProgressDlg(this, "Generating " + ALGO[algoIdx], "🔑", null);
        dlg.set("Creating key pair and certificate…", -1f);
        android.view.WindowManager.LayoutParams unused = null;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        async(() -> {
            KeystoreKit.Material m = KeystoreKit.generate(spec);
            m.alias = al;
            byte[] file = KeystoreKit.write(type, m, sp.toCharArray(), kp.toCharArray());
            // prove the file reads back with the same passwords before saving it
            KeystoreKit.read(file, sp.toCharArray(), kp.toCharArray(), al);
            KeyVault.Entry e = new KeyVault.Entry();
            e.id = UUID.randomUUID().toString(); e.name = name; e.app = fApp; e.pkg = fPkg; e.alias = al; e.storePass = sp; e.keyPass = kp;
            e.type = type; e.fileB64 = KeystoreKit.b64(file); e.created = System.currentTimeMillis();
            e.fillFrom(m);
            KeyVault.put(st.store, e);
            return e;
        }, e -> {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            dlg.close();
            startActivity(new android.content.Intent(this, KeyDetailActivity.class).putExtra("id", e.id));
            finish();
        }, e -> { getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); dlg.close(); Ui.toast(this, "Failed: " + msg(e)); });
    }
}
