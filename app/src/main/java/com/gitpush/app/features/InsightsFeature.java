package com.gitpush.app.features;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.gitpush.app.R;
import com.gitpush.app.ui.BarChartView;
import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/** Traffic (views/clones), referrers, popular paths, languages, top contributors and weekly commit activity. */
public class InsightsFeature extends Feature {
    private LinearLayout col;

    @Override public String title() { return "Insights"; }
    @Override public String icon() { return "📊"; }
    @Override public String blurb() { return "Traffic, clones, languages, contributors, activity"; }

    @Override public View view() {
        ScrollView sv = new ScrollView(a);
        col = Ui.vbox(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 28));
        sv.addView(col);
        col.addView(Ui.muted(a, "Loading insights…"));
        a.run(() -> {
            JSONObject all = new JSONObject();
            all.put("repo", a.api.getObj(rp()));
            try { all.put("views", a.api.getObj(rp() + "/traffic/views")); } catch (Exception e) { all.put("viewsErr", e.getMessage()); }
            try { all.put("clones", a.api.getObj(rp() + "/traffic/clones")); } catch (Exception ignored) { }
            try { all.put("refs", a.api.getArr(rp() + "/traffic/popular/referrers")); } catch (Exception ignored) { }
            try { all.put("paths", a.api.getArr(rp() + "/traffic/popular/paths")); } catch (Exception ignored) { }
            try { all.put("langs", a.api.getObj(rp() + "/languages")); } catch (Exception ignored) { }
            try { all.put("contrib", a.api.getArr(rp() + "/contributors?per_page=10")); } catch (Exception ignored) { }
            try { all.put("weeks", a.api.getStatsArr(rp() + "/stats/commit_activity")); } catch (Exception ignored) { }
            return all;
        }, this::build);
        return sv;
    }

    private void build(JSONObject all) {
        col.removeAllViews();
        JSONObject repo = o(all, "repo");
        LinearLayout c0 = Ui.card(a);
        c0.addView(Ui.text(a, "Overview", 13, true, R.attr.gpPrimary));
        c0.addView(Ui.body(a, "★ " + repo.optInt("stargazers_count") + "   ⑂ " + repo.optInt("forks_count") + "   👁 " + repo.optInt("subscribers_count") + "   ⚠ " + repo.optInt("open_issues_count") + " open issues\nSize " + com.gitpush.app.util.PathUtils.formatSize(repo.optLong("size") * 1024L) + "  •  created " + ago(s(repo, "created_at")) + "  •  pushed " + ago(s(repo, "pushed_at"))), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        col.addView(c0, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        traffic(all.optJSONObject("views"), "Page views (14 days)", all.optString("viewsErr"));
        traffic(all.optJSONObject("clones"), "Git clones (14 days)", "");

        JSONArray refs = all.optJSONArray("refs");
        if (refs != null && refs.length() > 0) list("Top referrers", refs, "referrer", "count", "uniques");
        JSONArray paths = all.optJSONArray("paths");
        if (paths != null && paths.length() > 0) list("Popular content", paths, "path", "count", "uniques");

        JSONObject langs = all.optJSONObject("langs");
        if (langs != null && langs.length() > 0) {
            double total = 0;
            Iterator<String> it = langs.keys();
            while (it.hasNext()) total += langs.optDouble(it.next());
            LinearLayout c = Ui.card(a);
            c.addView(Ui.text(a, "Languages", 13, true, R.attr.gpPrimary));
            it = langs.keys();
            while (it.hasNext()) { String k = it.next(); c.addView(Ui.body(a, k + "  " + Math.round(langs.optDouble(k) * 1000 / total) / 10.0 + "%"), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0)); }
            col.addView(c, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        }

        JSONArray con = all.optJSONArray("contrib");
        if (con != null && con.length() > 0) list("Top contributors", con, "login", "contributions", null);

        JSONArray weeks = all.optJSONArray("weeks");
        if (weeks != null && weeks.length() > 0) {
            int n = Math.min(26, weeks.length());
            float[] v = new float[n];
            String[] l = new String[n];
            for (int i = 0; i < n; i++) { v[i] = weeks.optJSONObject(weeks.length() - n + i).optInt("total"); l[i] = i % 5 == 0 ? String.valueOf(i + 1) : ""; }
            LinearLayout c = Ui.card(a);
            c.addView(Ui.text(a, "Commits per week (last " + n + " weeks)", 13, true, R.attr.gpPrimary));
            BarChartView ch = new BarChartView(a);
            ch.setData(v, l, Ui.color(a, R.attr.gpText), Ui.color(a, R.attr.gpText), Ui.color(a, R.attr.gpMuted), Ui.color(a, R.attr.gpOutline));
            c.addView(ch, Ui.lp(a, Ui.MATCH, Ui.dp(a, 150), 0, 8, 0, 0));
            col.addView(c, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        }
    }

    private void traffic(JSONObject t, String title, String err) {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, title, 13, true, R.attr.gpPrimary));
        JSONArray arr = t == null ? null : t.optJSONArray(t.has("views") ? "views" : "clones");
        if (t == null || arr == null) c.addView(Ui.muted(a, err.isEmpty() ? "Not available." : "Needs push access to the repository.\n" + err), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        else {
            c.addView(Ui.body(a, t.optInt("count") + " total  •  " + t.optInt("uniques") + " unique"), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
            int n = arr.length();
            if (n > 0) {
                float[] v = new float[n];
                String[] l = new String[n];
                for (int i = 0; i < n; i++) { JSONObject d = arr.optJSONObject(i); v[i] = d.optInt("count"); l[i] = i % 3 == 0 ? s(d, "timestamp").substring(5, 10) : ""; }
                BarChartView ch = new BarChartView(a);
                ch.setData(v, l, Ui.color(a, R.attr.gpText), Ui.color(a, R.attr.gpText), Ui.color(a, R.attr.gpMuted), Ui.color(a, R.attr.gpOutline));
                c.addView(ch, Ui.lp(a, Ui.MATCH, Ui.dp(a, 140), 0, 8, 0, 0));
            }
        }
        col.addView(c, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
    }

    private void list(String title, JSONArray arr, String nameKey, String countKey, String uniqKey) {
        LinearLayout c = Ui.card(a);
        c.addView(Ui.text(a, title, 13, true, R.attr.gpPrimary));
        for (int i = 0; i < arr.length() && i < 10; i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            c.addView(Ui.body(a, s(j, nameKey) + "  —  " + j.optInt(countKey) + (uniqKey == null ? "" : "  (" + j.optInt(uniqKey) + " unique)")), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
        }
        col.addView(c, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
    }
}
