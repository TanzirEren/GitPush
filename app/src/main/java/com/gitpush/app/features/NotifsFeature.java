package com.gitpush.app.features;

import com.gitpush.app.ui.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Notification inbox: unread/all, open, mark read, mark done, ignore thread, mark everything read. */
public class NotifsFeature extends Feature {
    @Override public String title() { return "Notifications"; }
    @Override public String icon() { return "🔔"; }
    @Override public String blurb() { return "Inbox: read, done, ignore, open"; }
    @Override public boolean needsRepo() { return false; }
    @Override public String[] filters() { return new String[]{"Unread", "All"}; }
    @Override public boolean hasMenu() { return true; }
    @Override public String empty() { return "You're all caught up."; }

    @Override public List<Row> load() throws Exception {
        JSONArray arr = a.api.getArr("/notifications?all=" + (filter == 1) + "&per_page=50");
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            out.add(new Row(s(o(j, "subject"), "title"), s(o(j, "repository"), "full_name") + "  •  " + s(j, "reason") + "  •  " + ago(s(j, "updated_at")), s(o(j, "subject"), "type"), j));
        }
        return out;
    }

    @Override public void menu() {
        Ui.confirm(a, "Mark all as read?", "Every notification is marked as read.", "Mark all", () -> a.act(() -> a.api.send("PUT", "/notifications", new JSONObject()), "All marked as read"));
    }

    private static String webUrl(JSONObject j) {
        String api = s(o(j, "subject"), "url");
        if (api.isEmpty()) return "https://github.com/" + s(o(j, "repository"), "full_name");
        return api.replace("https://api.github.com/repos/", "https://github.com/").replace("/pulls/", "/pull/").replace("/commits/", "/commit/");
    }

    @Override public void onRow(final Row r) {
        final String id = s(r.j, "id");
        Ui.pickList(a, r.title, new String[]{"🌐  Open on GitHub", "✔  Mark as read", "☑  Mark as done", "🔕  Ignore this thread"}, i -> {
            if (i == 0) { Ui.openUrl(a, webUrl(r.j)); a.run(() -> a.api.send("PATCH", "/notifications/threads/" + id, null), x -> { }); }
            else if (i == 1) a.act(() -> a.api.send("PATCH", "/notifications/threads/" + id, null), "Marked as read");
            else if (i == 2) a.act(() -> a.api.send("DELETE", "/notifications/threads/" + id, null), "Done");
            else a.act(() -> a.api.send("PUT", "/notifications/threads/" + id + "/subscription", new JSONObject().put("ignored", true)), "Thread ignored");
        });
    }
}
