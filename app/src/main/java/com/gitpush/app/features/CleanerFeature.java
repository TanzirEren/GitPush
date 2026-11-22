package com.gitpush.app.features;

import com.gitpush.app.ui.ProgressDlg;
import com.gitpush.app.ui.Ui;
import com.gitpush.app.util.PathUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Actions cleaner: caches (list, delete one/all/old) and workflow runs (bulk delete failed, cancelled, old or all finished). */
public class CleanerFeature extends Feature {
    @Override public String title() { return "Actions cleaner"; }
    @Override public String icon() { return "🧹"; }
    @Override public String blurb() { return "Free cache storage, bulk-delete old runs"; }
    @Override public String[] filters() { return new String[]{"Caches", "Runs"}; }
    @Override public boolean hasMenu() { return true; }
    @Override public String empty() { return filter == 0 ? "No caches stored." : "No workflow runs."; }

    @Override public List<Row> load() throws Exception {
        List<Row> out = new ArrayList<>();
        if (filter == 0) {
            JSONArray arr = a.api.getAll(rp() + "/actions/caches", "actions_caches", 3);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i);
                if (j != null) out.add(new Row(s(j, "key"), s(j, "ref") + "  •  used " + ago(s(j, "last_accessed_at")), PathUtils.formatSize(j.optLong("size_in_bytes")), j));
            }
        } else {
            JSONArray arr = a.api.getAll(rp() + "/actions/runs", "workflow_runs", 2);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i);
                if (j != null) out.add(new Row(s(j, "name") + " #" + j.optInt("run_number"), s(j, "head_branch") + "  •  " + s(j, "event") + "  •  " + ago(s(j, "created_at")), s(j, "conclusion").isEmpty() ? s(j, "status") : s(j, "conclusion"), j));
            }
        }
        return out;
    }

    @Override public void onRow(final Row r) {
        Ui.pickList(a, r.title, new String[]{"🗑  Delete", "🌐  Open on GitHub"}, i -> {
            if (i == 1) { if (filter == 1) Ui.openUrl(a, s(r.j, "html_url")); else Ui.openUrl(a, "https://github.com/" + a.owner + "/" + a.repo + "/actions/caches"); return; }
            String u = filter == 0 ? rp() + "/actions/caches/" + r.j.optLong("id") : rp() + "/actions/runs/" + r.j.optLong("id");
            a.act(() -> a.api.send("DELETE", u, null), "Deleted");
        });
    }

    @Override public void menu() {
        final String[] items = filter == 0 ? new String[]{"📊  Show storage used", "🗑  Delete ALL caches", "🗑  Delete caches unused for 7+ days"}
                : new String[]{"🗑  Delete failed runs", "🗑  Delete cancelled runs", "🗑  Delete runs older than 30 days", "🗑  Delete ALL finished runs"};
        Ui.pickList(a, filter == 0 ? "Caches" : "Runs", items, i -> {
            if (filter == 0) {
                if (i == 0) a.run(() -> a.api.getObj(rp() + "/actions/cache/usage"), u -> showText("Cache usage", u.optInt("active_caches_count") + " caches\n" + PathUtils.formatSize(u.optLong("active_caches_size_in_bytes")) + " used (GitHub allows 10 GB per repository)"));
                else if (i == 1) Ui.confirmDanger(a, "Delete all caches?", "Next builds start cold and take longer.", "Delete all", () -> bulk("caches", null, 0));
                else bulk("caches", null, 7);
            } else {
                String[] mode = {"failure", "cancelled", "old", "all"};
                final String m = mode[i];
                Ui.confirmDanger(a, items[i].substring(5).trim() + "?", "Runs and their logs are removed from GitHub. This cannot be undone.", "Delete", () -> bulk("runs", m, 30));
            }
        });
    }

    private void bulk(final String what, final String mode, final int days) {
        final ProgressDlg dlg = new ProgressDlg(a, "Cleaning", "🧹", () -> a.api.abort());
        a.async2(() -> {
            int n = 0;
            long cutoff = System.currentTimeMillis() - days * 86_400_000L;
            if (what.equals("caches")) {
                JSONArray arr = a.api.getAll(rp() + "/actions/caches", "actions_caches", 5);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject j = arr.optJSONObject(i);
                    if (j == null) continue;
                    if (days > 0 && Ui.isoMs(s(j, "last_accessed_at")) > cutoff) continue;
                    a.api.send("DELETE", rp() + "/actions/caches/" + j.optLong("id"), null);
                    n++;
                    final int fn = n;
                    com.gitpush.app.GitPushApp.get().main.post(() -> dlg.set(fn + " deleted", -1f));
                }
            } else {
                JSONArray arr = a.api.getAll(rp() + "/actions/runs", "workflow_runs", 5);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject j = arr.optJSONObject(i);
                    if (j == null || !"completed".equals(s(j, "status"))) continue;
                    String c = s(j, "conclusion");
                    boolean hit = mode.equals("all") || (mode.equals("failure") && (c.equals("failure") || c.equals("timed_out") || c.equals("startup_failure"))) || (mode.equals("cancelled") && c.equals("cancelled")) || (mode.equals("old") && Ui.isoMs(s(j, "created_at")) < cutoff);
                    if (!hit) continue;
                    try { a.api.send("DELETE", rp() + "/actions/runs/" + j.optLong("id"), null); n++; } catch (Exception ignored) { }
                    final int fn = n;
                    com.gitpush.app.GitPushApp.get().main.post(() -> dlg.set(fn + " deleted", -1f));
                }
            }
            return n;
        }, n -> { dlg.close(); Ui.toast(a, n + " deleted"); a.reload(); }, e -> { dlg.close(); Ui.toast(a, a.err(e)); a.reload(); });
    }
}
