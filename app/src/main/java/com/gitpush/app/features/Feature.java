package com.gitpush.app.features;

import android.view.View;

import com.gitpush.app.FeatureActivity;

import java.util.ArrayList;
import java.util.List;

/** One GitHub control screen. List features implement load()/onRow(); custom screens override view(). */
public abstract class Feature {
    public static final class Row {
        public String title = "", sub = "", pill = "";
        public org.json.JSONObject j;
        public Row(String title, String sub, String pill, org.json.JSONObject j) { this.title = title; this.sub = sub; this.pill = pill; this.j = j; }
    }

    protected FeatureActivity a;
    public void attach(FeatureActivity a) { this.a = a; }

    public abstract String title();
    public String icon() { return "⚙"; }
    public String blurb() { return ""; }
    /** Tab/filter chips above the list (null = none). Selected index is [filter]. */
    public String[] filters() { return null; }
    public int filter;
    public boolean canAdd() { return false; }
    public boolean needsRepo() { return true; }
    public void onAdd() { }
    public List<Row> load() throws Exception { return new ArrayList<>(); }
    public void onRow(Row r) { }
    public String empty() { return "Nothing here yet."; }
    /** Return a view to render a fully custom screen instead of the list. */
    public View view() { return null; }
    /** Extra header action (icon res, handler) or null. */
    public void menu() { }
    public boolean hasMenu() { return false; }

    protected String rp() { return "/repos/" + com.gitpush.app.data.GitHubApi.enc2(a.owner) + "/" + com.gitpush.app.data.GitHubApi.enc2(a.repo); }

    /** Scrollable, selectable text dialog with a Copy button. */
    protected void showText(String title, final String text) {
        android.widget.TextView t = com.gitpush.app.ui.Ui.body(a, text);
        t.setTextSize(12);
        t.setTextIsSelectable(true);
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(t);
        android.widget.LinearLayout box = com.gitpush.app.ui.Ui.vbox(a);
        box.addView(sv, com.gitpush.app.ui.Ui.lp(com.gitpush.app.ui.Ui.MATCH, com.gitpush.app.ui.Ui.dp(a, 320)));
        new com.gitpush.app.ui.GlassDialog(a).setTitle(title).setView(box).setPositiveButton("Close", null)
                .setNegativeButton("Copy", (d, w) -> { com.gitpush.app.ui.Ui.copy(a, title, text); com.gitpush.app.ui.Ui.toast(a, "Copied"); }).show();
    }

    protected static String csv(String v) { return v == null ? "" : v.trim(); }
    protected static org.json.JSONArray list(String csv) {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (String x : csv(csv).split(",")) { String t = x.trim(); if (!t.isEmpty()) arr.put(t); }
        return arr;
    }
    protected static String join(org.json.JSONArray arr, String key) {
        StringBuilder sb = new StringBuilder();
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            Object x = arr.opt(i);
            String v = x instanceof org.json.JSONObject ? ((org.json.JSONObject) x).optString(key) : String.valueOf(x);
            if (sb.length() > 0) sb.append(", ");
            sb.append(v);
        }
        return sb.toString();
    }
    protected String ago(String iso) { return com.gitpush.app.ui.Ui.agoIso(iso); }

    protected static String s(org.json.JSONObject o, String k) { return o == null || o.isNull(k) ? "" : o.optString(k, ""); }
    protected static org.json.JSONObject o(org.json.JSONObject o, String k) { org.json.JSONObject r = o == null ? null : o.optJSONObject(k); return r == null ? new org.json.JSONObject() : r; }
}
