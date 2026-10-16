package com.gitpush.app.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Path transformation rules for the advanced ZIP extractor (pure Java, unit-testable). */
public final class ZipRules {
    public int stripLevels = 0;
    public String rootFolder = "";
    public String addPrefix = "";
    public boolean skipHidden = false, flatten = false, skipJunk = true;
    public String exclude = "", include = "";

    private static final Set<String> JUNK = new HashSet<>(Arrays.asList("__MACOSX", ".DS_Store", "Thumbs.db", "desktop.ini"));
    private static final Set<String> KEEP_HIDDEN = new HashSet<>(Arrays.asList(".gitignore", ".gitattributes", ".gitkeep", ".github", ".editorconfig"));

    private static List<Pattern> compile(String globs) {
        List<Pattern> out = new ArrayList<>();
        for (String g : globs.split("[,\\n]")) {
            g = g.trim();
            if (g.isEmpty()) continue;
            if (g.endsWith("/")) g = g.substring(0, g.length() - 1);
            StringBuilder sb = new StringBuilder();
            for (char c : g.toCharArray()) {
                if (c == '*') sb.append("[^/]*");
                else if (c == '?') sb.append("[^/]");
                else sb.append(Pattern.quote(String.valueOf(c)));
            }
            out.add(Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE));
        }
        return out;
    }

    private static boolean matches(List<Pattern> pats, String path) {
        String[] segs = path.split("/");
        for (Pattern p : pats) {
            if (p.matcher(path).matches()) return true;
            for (String s : segs) if (p.matcher(s).matches()) return true;
        }
        return false;
    }

    /** Returns the new repo-relative path, or null when the entry is excluded. */
    public String apply(String path) {
        String p = path;
        String[] segs = p.split("/");
        if (skipJunk) for (String s : segs) if (JUNK.contains(s)) return null;
        String root = PathUtils.trimSlashes(rootFolder);
        if (!root.isEmpty()) {
            if (!p.startsWith(root + "/")) return null;
            p = p.substring(root.length() + 1);
        }
        if (stripLevels > 0) {
            String[] s2 = p.split("/");
            if (s2.length <= stripLevels) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = stripLevels; i < s2.length; i++) { if (sb.length() > 0) sb.append('/'); sb.append(s2[i]); }
            p = sb.toString();
        }
        if (p.isEmpty()) return null;
        if (skipHidden) for (String s : p.split("/")) if (s.startsWith(".") && !KEEP_HIDDEN.contains(s)) return null;
        if (!exclude.trim().isEmpty() && matches(compile(exclude), p)) return null;
        if (!include.trim().isEmpty() && !matches(compile(include), p)) return null;
        if (flatten) p = PathUtils.baseName(p);
        String pre = PathUtils.trimSlashes(addPrefix.trim().replace('\\', '/'));
        if (!pre.isEmpty()) p = pre + "/" + p;
        return PathUtils.isValidRepoPath(p) ? p : null;
    }

    /** Chain of single-child top folders, e.g. [Buildora-v2.0.0, Buildora-v2.0.0/Buildora]. */
    public static List<String> topChain(List<String> all) {
        List<String> paths = new ArrayList<>();
        for (String p : all) {
            boolean junk = false;
            for (String s : p.split("/")) if (JUNK.contains(s)) junk = true;
            if (!junk) paths.add(p);
        }
        List<String> chain = new ArrayList<>();
        if (paths.isEmpty()) return chain;
        String prefix = "";
        while (true) {
            Set<String> next = new HashSet<>();
            boolean allDeeper = true;
            for (String p : paths) {
                if (!prefix.isEmpty() && !p.startsWith(prefix + "/")) { allDeeper = false; break; }
                String rest = prefix.isEmpty() ? p : p.substring(prefix.length() + 1);
                int i = rest.indexOf('/');
                if (i < 0) { allDeeper = false; break; }
                next.add(rest.substring(0, i));
            }
            if (!allDeeper || next.size() != 1) break;
            prefix = prefix.isEmpty() ? next.iterator().next() : prefix + "/" + next.iterator().next();
            chain.add(prefix);
        }
        return chain;
    }

    /** All folder paths in the archive (sorted, capped). */
    public static List<String> allFolders(List<String> paths, int cap) {
        TreeSet<String> set = new TreeSet<>();
        for (String p : paths) {
            int i = p.indexOf('/');
            while (i >= 0) { set.add(p.substring(0, i)); i = p.indexOf('/', i + 1); }
        }
        List<String> out = new ArrayList<>(set);
        return out.size() > cap ? new ArrayList<>(out.subList(0, cap)) : out;
    }

    public static LinkedHashSet<String> unused() { return new LinkedHashSet<>(); }
}
