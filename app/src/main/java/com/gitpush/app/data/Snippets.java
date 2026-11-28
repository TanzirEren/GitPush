package com.gitpush.app.data;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saved terminal snippets, command history and per-codespace ports (stored in app preferences). */
public final class Snippets {
    private Snippets() {}

    public static final class Item {
        public String title, cmd, group;
        public boolean fav;
        public Item(String group, String title, String cmd, boolean fav) { this.group = group; this.title = title; this.cmd = cmd; this.fav = fav; }
    }

    private static final String[][] DEFAULTS = {
            {"git", "Status", "git status -sb"}, {"git", "Pull", "git pull --rebase"}, {"git", "Log (graph)", "git log --oneline --graph --decorate -n 20"},
            {"git", "Commit all", "git add -A && git commit -m \"update\""}, {"git", "Push", "git push"},
            {"node", "Install", "npm install"}, {"node", "Dev server", "npm run dev"}, {"node", "Build", "npm run build"}, {"node", "Test", "npm test"},
            {"python", "Venv + install", "python3 -m venv .venv && . .venv/bin/activate && pip install -r requirements.txt"}, {"python", "Run main", "python3 main.py"}, {"python", "Pytest", "python3 -m pytest -q"},
            {"gradle", "Build debug", "./gradlew assembleDebug"}, {"gradle", "Unit tests", "./gradlew test"}, {"gradle", "Clean", "./gradlew clean"},
            {"system", "Disk usage", "df -h . && du -sh * | sort -h | tail"}, {"system", "Running ports", "ss -tlnp"}, {"system", "Processes", "ps aux --sort=-%mem | head"},
            {"system", "Who am I", "echo $CODESPACE_NAME && whoami && pwd"},
    };

    private static SharedPreferences p(Store s) { return s.prefs; }

    public static List<Item> all(Store s) {
        List<Item> out = new ArrayList<>();
        String raw = p(s).getString("cs_snippets", null);
        if (raw == null) {
            for (String[] d : DEFAULTS) out.add(new Item(d[0], d[1], d[2], false));
            return out;
        }
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Item(o.optString("g", "custom"), o.optString("t"), o.optString("c"), o.optBoolean("f")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static void save(Store s, List<Item> items) {
        JSONArray a = new JSONArray();
        try { for (Item i : items) a.put(new JSONObject().put("g", i.group).put("t", i.title).put("c", i.cmd).put("f", i.fav)); } catch (Exception ignored) { }
        p(s).edit().putString("cs_snippets", a.toString()).apply();
    }

    public static List<String> history(Store s) {
        List<String> out = new ArrayList<>();
        try { JSONArray a = new JSONArray(p(s).getString("cs_history", "[]")); for (int i = 0; i < a.length(); i++) out.add(a.getString(i)); } catch (Exception ignored) { }
        return out;
    }

    /** Newest first, no duplicates, max 60. */
    public static void pushHistory(Store s, String cmd) {
        String c = cmd == null ? "" : cmd.trim();
        if (c.isEmpty()) return;
        List<String> h = history(s);
        h.remove(c);
        h.add(0, c);
        while (h.size() > 60) h.remove(h.size() - 1);
        JSONArray a = new JSONArray();
        for (String x : h) a.put(x);
        p(s).edit().putString("cs_history", a.toString()).apply();
    }

    public static void clearHistory(Store s) { p(s).edit().remove("cs_history").apply(); }

    // ------------------------------------------------------------ ports remembered per codespace
    public static List<Integer> ports(Store s, String codespace) {
        List<Integer> out = new ArrayList<>();
        try { JSONArray a = new JSONArray(p(s).getString("cs_ports_" + codespace, "[]")); for (int i = 0; i < a.length(); i++) out.add(a.getInt(i)); } catch (Exception ignored) { }
        return out;
    }

    public static void addPort(Store s, String codespace, int port) {
        List<Integer> l = ports(s, codespace);
        if (!l.contains(port)) l.add(port);
        JSONArray a = new JSONArray();
        for (int x : l) a.put(x);
        p(s).edit().putString("cs_ports_" + codespace, a.toString()).apply();
    }

    public static void removePort(Store s, String codespace, int port) {
        List<Integer> l = ports(s, codespace);
        l.remove(Integer.valueOf(port));
        JSONArray a = new JSONArray();
        for (int x : l) a.put(x);
        p(s).edit().putString("cs_ports_" + codespace, a.toString()).apply();
    }

    // ------------------------------------------------------------ cost-safety settings
    public static boolean stopOnLeave(Store s) { return p(s).getBoolean("cs_stop_on_leave", false); }
    public static void stopOnLeave(Store s, boolean v) { p(s).edit().putBoolean("cs_stop_on_leave", v).apply(); }
    /** Minutes after which a "still running" reminder fires (0 = off). */
    public static int remindMinutes(Store s) { return p(s).getInt("cs_remind_min", 60); }
    public static void remindMinutes(Store s, int v) { p(s).edit().putInt("cs_remind_min", v).apply(); }
    public static int layoutWidth(Store s) { return p(s).getInt("cs_layout_w", 900); }
    public static void layoutWidth(Store s, int v) { p(s).edit().putInt("cs_layout_w", v).apply(); }
}
