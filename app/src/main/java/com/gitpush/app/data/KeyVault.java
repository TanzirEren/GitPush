package com.gitpush.app.data;

import com.gitpush.app.util.KeystoreKit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/** Saved signing keys (stored in the encrypted store). Passwords never leave the device unless you export or push them. */
public final class KeyVault {
    private KeyVault() {}

    public static final class Entry {
        public String id, name = "", app = "", pkg = "", alias = "", storePass = "", keyPass = "", type = "PKCS12", fileB64 = "", algo = "RSA";
        public String sha1 = "", sha256 = "", md5 = "", fb = "", subject = "", validTo = "";
        public long created, expires;

        public byte[] file() { return KeystoreKit.unb64(fileB64); }

        public JSONObject toJson() throws Exception {
            return new JSONObject().put("id", id).put("name", name).put("app", app).put("pkg", pkg).put("alias", alias).put("sp", storePass).put("kp", keyPass)
                    .put("type", type).put("file", fileB64).put("algo", algo).put("created", created).put("exp", expires)
                    .put("sha1", sha1).put("sha256", sha256).put("md5", md5).put("fb", fb).put("subj", subject).put("valid", validTo);
        }

        static Entry from(JSONObject o) {
            Entry e = new Entry();
            e.id = o.optString("id"); e.name = o.optString("name"); e.app = o.optString("app"); e.pkg = o.optString("pkg"); e.alias = o.optString("alias");
            e.storePass = o.optString("sp"); e.keyPass = o.optString("kp"); e.type = o.optString("type", "PKCS12"); e.fileB64 = o.optString("file");
            e.algo = o.optString("algo", "RSA"); e.created = o.optLong("created"); e.expires = o.optLong("exp");
            e.sha1 = o.optString("sha1"); e.sha256 = o.optString("sha256"); e.md5 = o.optString("md5"); e.fb = o.optString("fb");
            e.subject = o.optString("subj"); e.validTo = o.optString("valid");
            return e;
        }

        /** Fill the fingerprint fields from the leaf certificate. */
        public void fillFrom(KeystoreKit.Material m) throws Exception {
            byte[] der = m.chain.get(0);
            X509Certificate c = m.leaf();
            sha1 = KeystoreKit.fingerprint(der, "SHA-1"); sha256 = KeystoreKit.fingerprint(der, "SHA-256"); md5 = KeystoreKit.fingerprint(der, "MD5");
            fb = KeystoreKit.facebookHash(der);
            subject = c.getSubjectX500Principal().getName();
            expires = c.getNotAfter().getTime();
            validTo = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(c.getNotAfter());
            algo = c.getPublicKey().getAlgorithm();
        }
    }

    public static List<Entry> load(Store s) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(s.vaultJson());
            for (int i = 0; i < a.length(); i++) out.add(Entry.from(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    public static void save(Store s, List<Entry> list) {
        JSONArray a = new JSONArray();
        try { for (Entry e : list) a.put(e.toJson()); } catch (Exception ignored) { }
        s.vaultJson(a.toString());
    }

    public static void put(Store s, Entry e) {
        List<Entry> l = load(s);
        boolean found = false;
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(e.id)) { l.set(i, e); found = true; }
        if (!found) l.add(0, e);
        save(s, l);
    }

    public static void remove(Store s, String id) {
        List<Entry> l = load(s);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(id)) l.remove(i);
        save(s, l);
    }

    public static Entry find(Store s, String id) {
        for (Entry e : load(s)) if (e.id.equals(id)) return e;
        return null;
    }

    // ---- custom secret names (defaults match the usual workflow) ----
    public static final String[] DEFAULT_NAMES = {"KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"};

    public static String[] names(Store s) {
        String[] n = DEFAULT_NAMES.clone();
        for (int i = 0; i < 4; i++) { String v = s.prefs.getString("secret_name_" + i, ""); if (!v.isEmpty()) n[i] = v; }
        return n;
    }

    public static void setNames(Store s, String[] n) {
        android.content.SharedPreferences.Editor ed = s.prefs.edit();
        for (int i = 0; i < 4; i++) ed.putString("secret_name_" + i, n[i].equals(DEFAULT_NAMES[i]) ? "" : n[i]);
        ed.apply();
    }
}
