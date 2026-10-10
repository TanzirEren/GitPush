package com.gitpush.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.gitpush.app.data.Models.Account;
import com.gitpush.app.data.Models.HistoryEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokens live in EncryptedSharedPreferences (Android Keystore-backed AES-256).
 * If the Keystore is unavailable the tokens are kept in memory only (never written in plain text).
 */
public class Store {
    private final Context ctx;
    private final SharedPreferences secure;
    public final SharedPreferences prefs;
    private String memAccounts = "[]";
    private String memActive = "";

    public Store(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = ctx.getSharedPreferences("gitpush_settings", Context.MODE_PRIVATE);
        this.secure = openSecure();
    }

    private SharedPreferences create() throws Exception {
        MasterKey key = new MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(ctx, "gitpush_secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    private SharedPreferences openSecure() {
        try { return create(); } catch (Exception e) {
            try { ctx.deleteSharedPreferences("gitpush_secure"); return create(); } catch (Exception e2) { return null; }
        }
    }

    // ---- accounts ----
    public List<Account> loadAccounts() {
        List<Account> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(secure != null ? secure.getString("accounts", "[]") : memAccounts);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Account(o.getString("login"), o.isNull("name") ? null : o.optString("name"), o.optString("avatar"),
                        o.getString("token"), o.optString("scopes"), o.optString("type", "pat")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public void saveAccounts(List<Account> list, String active) {
        JSONArray arr = new JSONArray();
        try {
            for (Account a : list) {
                arr.put(new JSONObject().put("login", a.login).put("name", a.name == null ? JSONObject.NULL : a.name)
                        .put("avatar", a.avatar).put("token", a.token).put("scopes", a.scopes).put("type", a.authType));
            }
        } catch (Exception ignored) { }
        if (secure != null) secure.edit().putString("accounts", arr.toString()).putString("active", active == null ? "" : active).apply();
        else { memAccounts = arr.toString(); memActive = active == null ? "" : active; }
    }

    public String activeLogin() { return secure != null ? secure.getString("active", "") : memActive; }

    // ---- settings ----
    public String theme() { return prefs.getString("theme", "system"); }
    public void theme(String v) { prefs.edit().putString("theme", v).apply(); }
    public int accent() { return prefs.getInt("accent", 0); }
    public void accent(int v) { prefs.edit().putInt("accent", v).apply(); }
    public boolean compact() { return prefs.getBoolean("compact", false); }
    public void compact(boolean v) { prefs.edit().putBoolean("compact", v).apply(); }
    public boolean haptics() { return prefs.getBoolean("haptics", true); }
    public void haptics(boolean v) { prefs.edit().putBoolean("haptics", v).apply(); }
    public boolean onboardingDone() { return prefs.getBoolean("onboarding_done", false); }
    public void onboardingDone(boolean v) { prefs.edit().putBoolean("onboarding_done", v).apply(); }
    public String clientId() { return prefs.getString("client_id", ""); }
    public void clientId(String v) { prefs.edit().putString("client_id", v).apply(); }

    // ---- upload history (not secret) ----
    public List<HistoryEntry> loadHistory() {
        List<HistoryEntry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("history", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(HistoryEntry.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    public void saveHistory(List<HistoryEntry> list) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < list.size() && i < 300; i++) arr.put(list.get(i).toJson());
        prefs.edit().putString("history", arr.toString()).apply();
    }
}
