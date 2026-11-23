package com.gitpush.app.imp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** In-memory registry of this session's jobs + a small persisted history of finished imports. */
public final class Jobs {
    private Jobs() {}

    public static final List<Job> ALL = new ArrayList<>();      // main thread / synchronized access
    private static final String KEY = "imp_history";

    public static synchronized void add(Job j) { ALL.add(0, j); while (ALL.size() > 12) ALL.remove(ALL.size() - 1); }
    public static synchronized List<Job> snapshot() { return new ArrayList<>(ALL); }

    public static synchronized void remember(SharedPreferences p, Job j) {
        try {
            JSONArray old = new JSONArray(p.getString(KEY, "[]"));
            JSONArray out = new JSONArray();
            out.put(new JSONObject().put("title", j.plan.title()).put("status", j.status).put("time", System.currentTimeMillis())
                    .put("sum", j.summary()).put("repo", j.target == null ? "" : j.target.fullName));
            for (int i = 0; i < old.length() && i < 19; i++) out.put(old.get(i));
            p.edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Rows: title, status, time, summary, repo. */
    public static synchronized List<String[]> history(SharedPreferences p) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new String[]{o.optString("title"), o.optString("status"), String.valueOf(o.optLong("time")), o.optString("sum"), o.optString("repo")});
            }
        } catch (Exception ignored) { }
        return out;
    }
}
