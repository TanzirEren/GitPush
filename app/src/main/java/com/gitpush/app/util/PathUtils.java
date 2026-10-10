package com.gitpush.app.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure Java helpers (no Android dependencies) so they can be unit-tested on the JVM. */
public final class PathUtils {
    private PathUtils() {}

    private static final Set<String> IMAGES = new HashSet<>(Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp"));

    /** Returns a safe relative path for a ZIP entry, or null if the entry must be skipped. */
    public static String cleanZipPath(String name) {
        List<String> parts = new ArrayList<>();
        for (String p : name.replace('\\', '/').split("/")) if (!p.isEmpty() && !p.equals(".")) parts.add(p);
        if (parts.isEmpty()) return null;
        for (String p : parts) if (p.equals("..") || p.equals("__MACOSX") || p.equals(".git")) return null;
        if (parts.get(parts.size() - 1).equals(".DS_Store")) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) { if (i > 0) sb.append('/'); sb.append(parts.get(i)); }
        return sb.toString();
    }

    /** Validates a final repository path (what we will send to GitHub). */
    public static boolean isValidRepoPath(String path) {
        if (path == null || path.isEmpty() || path.length() > 4000) return false;
        if (path.startsWith("/") || path.endsWith("/")) return false;
        String[] parts = path.split("/", -1);
        for (String p : parts) if (p.isEmpty() || p.equals(".") || p.equals("..")) return false;
        if (parts[0].equalsIgnoreCase(".git")) return false;
        for (int i = 0; i < path.length(); i++) if (path.charAt(i) < 32) return false;
        return true;
    }

    public static String join(String dir, String path) {
        String d = trimSlashes(dir == null ? "" : dir.trim());
        String p = path.startsWith("/") ? path.substring(1) : path;
        return d.isEmpty() ? p : d + "/" + p;
    }

    public static String trimSlashes(String s) {
        int a = 0, b = s.length();
        while (a < b && s.charAt(a) == '/') a++;
        while (b > a && s.charAt(b - 1) == '/') b--;
        return s.substring(a, b);
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    public static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isImage(String name) { return IMAGES.contains(extension(name)); }

    public static boolean isMarkdown(String name) {
        String e = extension(name);
        return e.equals("md") || e.equals("markdown");
    }

    public static String parent(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    public static String baseName(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }
}
