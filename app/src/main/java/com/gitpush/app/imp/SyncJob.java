package com.gitpush.app.imp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A saved "keep in sync" link between a source repo and an imported copy (the Sync Loop works on these). */
public final class SyncJob {
    public long id = System.currentTimeMillis();
    public String account = "", srcOwner = "", srcName = "", ref = "", tOwner = "", tName = "", tDir = "", branch = "", lastSha = "", lastResult = "Never run";
    public boolean allFiles = true, enabled = true, deleteRemoved = false;
    public int intervalMin = 60;
    public long lastRun;
    public final List<String> prefixes = new ArrayList<>();

    public String label() { return srcOwner + "/" + srcName + " → " + tOwner + "/" + tName; }

    public JSONObject toJson() {
        try {
            return new JSONObject().put("id", id).put("acc", account).put("so", srcOwner).put("sn", srcName).put("ref", ref).put("to", tOwner).put("tn", tName)
                    .put("dir", tDir).put("br", branch).put("last", lastSha).put("res", lastResult).put("all", allFiles).put("en", enabled)
                    .put("del", deleteRemoved).put("min", intervalMin).put("run", lastRun).put("pre", new JSONArray(prefixes));
        } catch (Exception e) { return new JSONObject(); }
    }

    public static SyncJob fromJson(JSONObject o) {
        SyncJob s = new SyncJob();
        s.id = o.optLong("id"); s.account = o.optString("acc"); s.srcOwner = o.optString("so"); s.srcName = o.optString("sn"); s.ref = o.optString("ref");
        s.tOwner = o.optString("to"); s.tName = o.optString("tn"); s.tDir = o.optString("dir"); s.branch = o.optString("br"); s.lastSha = o.optString("last");
        s.lastResult = o.optString("res"); s.allFiles = o.optBoolean("all", true); s.enabled = o.optBoolean("en", true); s.deleteRemoved = o.optBoolean("del");
        s.intervalMin = o.optInt("min", 60); s.lastRun = o.optLong("run");
        JSONArray a = o.optJSONArray("pre");
        if (a != null) for (int i = 0; i < a.length(); i++) s.prefixes.add(a.optString(i));
        return s;
    }
}
