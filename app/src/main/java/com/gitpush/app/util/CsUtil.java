package com.gitpush.app.util;

import java.util.Locale;

/** Pure helpers for Codespaces (no Android classes, unit-testable). */
public final class CsUtil {
    private CsUtil() {}

    public static final String[] REGIONS = {"Auto", "UsEast", "UsWest", "EuropeWest", "SoutheastAsia"};
    public static final String[] IDLE_LABELS = {"30 min", "1 hour", "2 hours", "4 hours", "8 hours", "24 hours"};
    public static final int[] IDLE_MIN = {30, 60, 120, 240, 480, 1440};
    public static final String[] RETAIN_LABELS = {"Default", "1 day", "7 days", "14 days", "30 days"};
    public static final int[] RETAIN_DAYS = {-1, 1, 7, 14, 30};
    public static final int[] COMMON_PORTS = {3000, 3001, 4200, 5000, 5173, 8000, 8080, 8888};

    public static boolean running(String state) { return "Available".equals(state); }
    public static boolean stopped(String state) { return "Shutdown".equals(state) || "Archived".equals(state) || "Unavailable".equals(state); }

    /** States in which GitHub is still working and the list should keep refreshing. */
    public static boolean transitional(String state) {
        if (state == null) return false;
        switch (state) {
            case "Queued": case "Provisioning": case "Starting": case "ShuttingDown": case "Exporting": case "Updating": case "Rebuilding": case "Awaiting": case "Created":
                return true;
            default: return false;
        }
    }

    public static String stateLabel(String state) {
        if (state == null || state.isEmpty()) return "Unknown";
        if ("Available".equals(state)) return "Running";
        if ("Shutdown".equals(state)) return "Stopped";
        if ("ShuttingDown".equals(state)) return "Stopping";
        return state;
    }

    public static String humanBytes(long b) {
        if (b <= 0) return "0 GB";
        double gb = b / 1073741824.0;
        if (gb >= 1) return (gb == Math.floor(gb) ? String.valueOf((long) gb) : String.format(Locale.US, "%.1f", gb)) + " GB";
        return Math.round(b / 1048576.0) + " MB";
    }

    /** "4-core · 16 GB RAM · 32 GB disk". */
    public static String machineLabel(int cpus, long memBytes, long storageBytes) {
        return cpus + "-core · " + humanBytes(memBytes) + " RAM · " + humanBytes(storageBytes) + " disk";
    }

    /** GitHub bills codespaces in core-hours: cores × hours. */
    public static double coreHours(int cpus, long minutes) { return cpus * (minutes / 60.0); }

    public static String coreHourRate(int cpus) { return cpus + " core-hour" + (cpus == 1 ? "" : "s") + " per hour"; }

    public static String webUrl(String name) { return "https://" + name + ".github.dev"; }
    public static String portUrl(String name, int port) { return "https://" + name + "-" + port + ".app.github.dev"; }

    public static boolean validPort(int p) { return p >= 1 && p <= 65535; }

    public static int parsePort(String s) {
        try { int p = Integer.parseInt(s.trim()); return validPort(p) ? p : -1; } catch (Exception e) { return -1; }
    }

    /** Display names: max 48 chars, no control chars. */
    public static String cleanDisplayName(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return t.length() > 48 ? t.substring(0, 48).trim() : t;
    }

    public static int idleMinutes(int idx) { return IDLE_MIN[Math.max(0, Math.min(IDLE_MIN.length - 1, idx))]; }
    public static int retentionMinutes(int idx) { int d = RETAIN_DAYS[Math.max(0, Math.min(RETAIN_DAYS.length - 1, idx))]; return d < 0 ? -1 : d * 24 * 60; }

    /** "ahead 2 · behind 1 · uncommitted" or "" when clean. */
    public static String gitSummary(boolean uncommitted, boolean unpushed, int ahead, int behind) {
        StringBuilder sb = new StringBuilder();
        if (uncommitted) sb.append("uncommitted");
        if (unpushed || ahead > 0) { if (sb.length() > 0) sb.append(" · "); sb.append(ahead > 0 ? "ahead " + ahead : "unpushed"); }
        if (behind > 0) { if (sb.length() > 0) sb.append(" · "); sb.append("behind ").append(behind); }
        return sb.toString();
    }

    /** Lower sort key = shown first: running, transitional, stopped; then most recently used. */
    public static int stateRank(String state) { return running(state) ? 0 : transitional(state) ? 1 : 2; }

    /** Parses owner/repo from "owner/repo". Returns null when invalid. */
    public static String[] splitRepo(String full) {
        if (full == null) return null;
        String[] p = full.split("/");
        return p.length == 2 && !p[0].isEmpty() && !p[1].isEmpty() ? p : null;
    }

    /** Safe repo name for "publish to new repo". */
    public static String repoSlug(String s) {
        String t = s == null ? "" : s.trim().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return t.length() > 90 ? t.substring(0, 90) : t;
    }
}
