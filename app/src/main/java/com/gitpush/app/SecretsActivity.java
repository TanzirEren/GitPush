package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.SecretInfo;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.GlassDialog;
import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.SealedBox;

import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** GitHub Actions secrets (encrypted on-device with the repo's public key) and variables, with .env bulk import. */
public class SecretsActivity extends BaseActivity {
    private static final int REQ_ENV = 701;
    private static final Pattern NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, SecretsActivity.class).putExtra("owner", owner).putExtra("repo", repo));
    }

    private String owner, repo;
    private GitHubApi api;
    private boolean vars;
    private final List<SecretInfo> items = new ArrayList<>(), shown = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView status;
    private LinearLayout tabs;
    private EditText search;
    private boolean loading = true;
    private String error;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        owner = getIntent().getStringExtra("owner");
        repo = getIntent().getStringExtra("repo");
        api = st.api();
        buildScreen("Secrets & variables", owner + "/" + repo, true);
        addAction(R.drawable.i_refresh, v -> load());
        addAction(R.drawable.i_plus, v -> addMenu());
        LinearLayout page = Ui.vbox(this);
        tabs = Ui.hbox(this);
        tabs.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), 0);
        page.addView(tabs);
        search = Ui.input(this, "Search by name", "");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            public void onTextChanged(CharSequence s, int a, int c, int d) { }
            public void afterTextChanged(Editable e) { filter(); }
        });
        page.addView(search, Ui.lp(this, Ui.MATCH, Ui.WRAP, 16, 10, 16, 4));
        status = Ui.muted(this, "");
        status.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), 0);
        page.addView(status);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setSelector(android.R.color.transparent);
        lv.setClipToPadding(false);
        lv.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 24));
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup p) { View v = row(shown.get(i)); Ui.fadeIn(v, i); return v; }
        };
        lv.setAdapter(adapter);
        page.addView(lv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        body.addView(page);
        renderTabs();
        load();
    }

    private void renderTabs() {
        tabs.removeAllViews();
        tabs.addView(Ui.chip(this, "🔑 Secrets", !vars, v -> { vars = false; renderTabs(); load(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        tabs.addView(Ui.chip(this, "📋 Variables", vars, v -> { vars = true; renderTabs(); load(); }));
    }

    private void load() {
        loading = true; error = null; render();
        final boolean v = vars;
        async(() -> v ? api.variables(owner, repo) : api.secrets(owner, repo), list -> { loading = false; items.clear(); items.addAll(list); filter(); },
                e -> { loading = false; error = msg(e); items.clear(); filter(); });
    }

    private void filter() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase();
        shown.clear();
        for (SecretInfo s : items) if (q.isEmpty() || s.name.toLowerCase().contains(q)) shown.add(s);
        render();
    }

    private void render() {
        adapter.notifyDataSetChanged();
        if (loading) status.setText("Loading…");
        else if (error != null) status.setText("Couldn't load\n" + error + "\n\nSecrets and variables need a token with the repo scope and admin access to the repository.");
        else if (items.isEmpty()) status.setText(vars ? "No variables yet. Tap + to add one." : "No secrets yet. Tap + to add one.\nValues are encrypted on your phone before they are sent.");
        else if (shown.isEmpty()) status.setText("No match.");
        else status.setText("");
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    private View row(final SecretInfo s) {
        LinearLayout wrap = Ui.vbox(this);
        wrap.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        LinearLayout c = Ui.card(this);
        LinearLayout r = Ui.hbox(this);
        r.addView(Ui.iconTileRes(this, vars ? R.drawable.i_hash : R.drawable.i_key, 40), Ui.lp(this, Ui.dp(this, 40), Ui.dp(this, 40), 0, 0, 12, 0));
        LinearLayout t = Ui.vbox(this);
        TextView n = Ui.text(this, s.name, 13, true, R.attr.gpText);
        n.setTypeface(Ui.mono(this, true));
        n.setSingleLine(true);
        t.addView(n);
        if (vars) { TextView v = Ui.muted(this, s.value == null ? "" : s.value); v.setSingleLine(true); t.addView(v); }
        t.addView(Ui.label(this, (vars ? "variable" : "encrypted") + " • updated " + Ui.agoIso(s.updatedAt)), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 3, 0, 0));
        r.addView(t, Ui.weight(1f));
        r.addView(Ui.iconRes(this, R.drawable.i_more, 20, R.attr.gpMuted));
        c.addView(r);
        c.setOnClickListener(v -> rowMenu(s));
        wrap.addView(c);
        return wrap;
    }

    private void rowMenu(final SecretInfo s) {
        String[] items = vars ? new String[]{"✏  Edit value", "📋  Copy name", "📋  Copy value", "🗑  Delete"} : new String[]{"✏  Update value", "📋  Copy name", "🗑  Delete"};
        Ui.pickList(this, s.name, items, i -> {
            String l = items[i];
            if (l.contains("value") && l.startsWith("✏")) valueDialog(s.name, s.value, true);
            else if (l.contains("Copy name")) { Ui.copy(this, "name", s.name); Ui.toast(this, "Name copied"); }
            else if (l.contains("Copy value")) { Ui.copy(this, "value", s.value == null ? "" : s.value); Ui.toast(this, "Value copied"); }
            else Ui.confirmDanger(this, "Delete " + s.name + "?", "Workflows that use it will stop getting this " + (vars ? "variable" : "secret") + ".", "Delete",
                        () -> async(() -> { if (vars) api.deleteVariable(owner, repo, s.name); else api.deleteSecret(owner, repo, s.name); return "ok"; }, x -> { Ui.toast(this, "Deleted"); load(); }, e -> Ui.toast(this, msg(e))));
        });
    }

    private void addMenu() {
        String[] items = {vars ? "＋  New variable" : "＋  New secret", "📥  Import .env file", "📝  Paste .env text", "🔑  Generate random value", "📋  Copy all names"};
        Ui.pickList(this, "Add", items, i -> {
            if (i == 0) valueDialog("", "", false);
            else if (i == 1) startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_ENV);
            else if (i == 2) pasteEnv();
            else if (i == 3) generate();
            else { StringBuilder sb = new StringBuilder(); for (SecretInfo s : items()) sb.append(s.name).append('\n'); Ui.copy(this, "names", sb.toString()); Ui.toast(this, "Names copied"); }
        });
    }

    private List<SecretInfo> items() { return items; }

    private void valueDialog(final String presetName, String presetValue, final boolean editing) {
        LinearLayout box = Ui.vbox(this);
        final EditText name = Ui.input(this, "NAME_IN_CAPS", presetName);
        name.setSingleLine(true);
        name.setEnabled(!editing);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final EditText value = Ui.input(this, vars ? "Value" : "Secret value", presetValue == null ? "" : presetValue);
        value.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        value.setMinLines(2);
        value.setGravity(android.view.Gravity.TOP);
        if (!vars) value.setTransformationMethod(PasswordTransformationMethod.getInstance());
        box.addView(name, Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(value, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        LinearLayout tools = Ui.hbox(this);
        if (!vars) tools.addView(Ui.chip(this, "👁 Show", false, v -> { boolean hidden = value.getTransformationMethod() != null; value.setTransformationMethod(hidden ? null : PasswordTransformationMethod.getInstance()); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        tools.addView(Ui.chip(this, "🔑 Generate", false, v -> value.setText(randomValue(32))), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        tools.addView(Ui.chip(this, "📋 Paste", false, v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) value.setText(String.valueOf(cm.getPrimaryClip().getItemAt(0).coerceToText(this)));
        }));
        box.addView(tools, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        if (!vars) box.addView(Ui.muted(this, "Encrypted on this device with the repository's public key. GitHub never shows the value again."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
        new GlassDialog(this).setTitle(editing ? "Update " + (vars ? "variable" : "secret") : "New " + (vars ? "variable" : "secret")).setIcon(vars ? "📋" : "🔑").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String v = value.getText().toString();
                    String bad = validate(n);
                    if (bad != null) { Ui.toast(this, bad); return; }
                    if (v.isEmpty()) { Ui.toast(this, "Enter a value"); return; }
                    final Map<String, String> one = new LinkedHashMap<>();
                    one.put(n, v);
                    saveAll(one, editing);
                }).setNegativeButton("Cancel", null).show();
    }

    private String validate(String n) {
        if (n.isEmpty()) return "Enter a name";
        if (!NAME.matcher(n).matches()) return "Use letters, numbers and underscores only (no spaces, can't start with a number)";
        if (!vars && n.toUpperCase().startsWith("GITHUB_")) return "Secret names can't start with GITHUB_";
        return null;
    }

    private static String randomValue(int len) {
        byte[] b = new byte[len];
        new SecureRandom().nextBytes(b);
        String s = Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return s.length() > len ? s.substring(0, len) : s;
    }

    private void generate() {
        final String v = randomValue(40);
        new GlassDialog(this).setTitle("Random value").setIcon("🔑").setMessage(v).setPositiveButton("Copy", (d, w) -> { Ui.copy(this, "value", v); Ui.toast(this, "Copied"); }).setNegativeButton("Close", null).show();
    }

    private void saveAll(final Map<String, String> data, final boolean editing) {
        final ProgressDlg dlg = new ProgressDlg(this, "Saving", vars ? "📋" : "🔑", null);
        final boolean isVars = vars;
        final java.util.Set<String> existing = new java.util.HashSet<>();
        for (SecretInfo s : items) existing.add(s.name);
        async(() -> {
            int n = 0;
            String[] key = isVars ? null : api.secretsPublicKey(owner, repo);
            byte[] pk = key == null ? null : Base64.decode(key[1], Base64.DEFAULT);
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (isVars) {
                    if (existing.contains(e.getKey())) api.updateVariable(owner, repo, e.getKey(), e.getValue());
                    else api.createVariable(owner, repo, e.getKey(), e.getValue());
                } else {
                    byte[] box = SealedBox.seal(e.getValue().getBytes("UTF-8"), pk);
                    api.putSecret(owner, repo, e.getKey(), Base64.encodeToString(box, Base64.NO_WRAP), key[0]);
                }
                n++;
            }
            return n;
        }, n -> { dlg.close(); Ui.toast(this, n + (isVars ? " variable(s) saved" : " secret(s) saved")); load(); }, e -> { dlg.close(); Ui.toast(this, msg(e)); });
    }

    // ---------------------------------------------------------------- .env import
    static Map<String, String> parseEnv(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring(7).trim();
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim(), v = line.substring(eq + 1).trim();
            if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) v = v.substring(1, v.length() - 1);
            out.put(k, v);
        }
        return out;
    }

    private void pasteEnv() {
        final EditText in = Ui.input(this, "KEY=value\nANOTHER_KEY=value", "");
        in.setMinLines(6);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setGravity(android.view.Gravity.TOP);
        in.setTypeface(android.graphics.Typeface.MONOSPACE);
        new GlassDialog(this).setTitle("Paste .env text").setIcon("📝").setView(in).setPositiveButton("Review", (d, w) -> confirmImport(parseEnv(in.getText().toString()))).setNegativeButton("Cancel", null).show();
    }

    private void confirmImport(final Map<String, String> data) {
        if (data.isEmpty()) { Ui.toast(this, "No KEY=value lines found"); return; }
        StringBuilder sb = new StringBuilder();
        List<String> bad = new ArrayList<>();
        for (String k : data.keySet()) { String err = validate(k); if (err != null) bad.add(k); else sb.append("• ").append(k).append('\n'); }
        for (String k : bad) data.remove(k);
        if (data.isEmpty()) { Ui.toast(this, "All names were invalid"); return; }
        String msg = data.size() + " " + (vars ? "variable(s)" : "secret(s)") + " will be created or overwritten:\n\n" + sb + (bad.isEmpty() ? "" : "\nSkipped (invalid name): " + bad);
        new GlassDialog(this).setTitle("Import").setIcon("📥").setMessage(msg).setPositiveButton("Import", (d, w) -> saveAll(data, false)).setNegativeButton("Cancel", null).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_ENV || res != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri u = data.getData();
        async(() -> {
            try (InputStream in = getContentResolver().openInputStream(u)) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) >= 0 && bo.size() < 200000) bo.write(buf, 0, n);
                return bo.toString("UTF-8");
            }
        }, text -> confirmImport(parseEnv(text)), e -> Ui.toast(this, msg(e)));
    }
}
