package com.gitpush.app.imp;

import com.gitpush.app.util.ZipRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Source repository file tree with check-box selection (tri-state folders). Pure Java. */
public final class Tree {
    public static final class Node {
        public final String name, path;
        public final boolean dir;
        public final Node parent;
        public long size, selSize;
        public int files, sel;
        public String sha = "", mode = "100644";
        public final TreeMap<String, Node> kids = new TreeMap<>();

        Node(String name, String path, boolean dir, Node parent) { this.name = name; this.path = path; this.dir = dir; this.parent = parent; }

        /** 0 = none, 1 = some, 2 = all selected. */
        public int state() { return sel == 0 ? 0 : sel == files ? 2 : 1; }
    }

    public static final class Row {
        public final Node node;
        public final int depth;
        Row(Node n, int d) { node = n; depth = d; }
    }

    public final Node root = new Node("", "", true, null);
    private final Map<String, Node> files = new HashMap<>();
    public final Set<String> selected = new HashSet<>();
    public final Map<String, String> modes = new HashMap<>();   // path -> mode (only non-default)
    public int submodules, symlinks;

    private static String key(String name, boolean dir) { return (dir ? "0" : "1") + name.toLowerCase(Locale.ROOT) + "\u0000" + name; }

    public void add(String path, long size, String sha, String mode) {
        String[] parts = path.split("/");
        Node cur = root;
        StringBuilder acc = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (acc.length() > 0) acc.append('/');
            acc.append(parts[i]);
            boolean last = i == parts.length - 1;
            String k = key(parts[i], !last);
            Node next = cur.kids.get(k);
            if (next == null) { next = new Node(parts[i], acc.toString(), !last, cur); cur.kids.put(k, next); }
            cur = next;
        }
        cur.size = size; cur.sha = sha == null ? "" : sha; cur.mode = mode == null ? "100644" : mode;
        files.put(path, cur);
        selected.add(path);
        if (mode != null && !mode.equals("100644")) modes.put(path, mode);
    }

    public int totalFiles() { return files.size(); }
    public Node file(String path) { return files.get(path); }
    public Iterable<String> allPaths() { return files.keySet(); }

    public void recount() { count(root); }

    private void count(Node n) {
        if (!n.dir) {
            n.files = 1; n.sel = selected.contains(n.path) ? 1 : 0; n.selSize = n.sel == 1 ? n.size : 0;
            return;
        }
        n.files = 0; n.sel = 0; n.size = 0; n.selSize = 0;
        for (Node k : n.kids.values()) { count(k); n.files += k.files; n.sel += k.sel; n.size += k.size; n.selSize += k.selSize; }
    }

    private void collect(Node n, List<String> out) {
        if (!n.dir) { out.add(n.path); return; }
        for (Node k : n.kids.values()) collect(k, out);
    }

    public void toggle(Node n) {
        List<String> ps = new ArrayList<>();
        collect(n, ps);
        boolean all = n.state() == 2;
        if (all) selected.removeAll(ps); else selected.addAll(ps);
        recount();
    }

    public void selectAll() { selected.addAll(files.keySet()); recount(); }
    public void selectNone() { selected.clear(); recount(); }

    public void invert() {
        Set<String> next = new HashSet<>(files.keySet());
        next.removeAll(selected);
        selected.clear(); selected.addAll(next);
        recount();
    }

    /** Select (or unselect) every file matching comma/new-line separated globs such as *.java, build, docs/. */
    public int glob(String globs, boolean on) {
        int n = 0;
        for (String p : files.keySet()) {
            if (ZipRules.matchesGlobs(globs, p)) { n++; if (on) selected.add(p); else selected.remove(p); }
        }
        recount();
        return n;
    }

    /** Keeps only files accepted by [keep]; the rest are unselected. */
    public void only(java.util.function.Predicate<String> keep) {
        selected.clear();
        for (String p : files.keySet()) if (keep.test(p)) selected.add(p);
        recount();
    }

    public void filterOut(java.util.function.Predicate<String> drop) {
        for (String p : files.keySet()) if (drop.test(p)) selected.remove(p);
        recount();
    }

    public List<Row> rows(Set<String> expanded, String query) {
        List<Row> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) rec(root, 0, expanded, out);
        else flat(root, q, out);
        return out;
    }

    private void rec(Node n, int depth, Set<String> expanded, List<Row> out) {
        for (Node c : n.kids.values()) {
            out.add(new Row(c, depth));
            if (c.dir && expanded.contains(c.path)) rec(c, depth + 1, expanded, out);
        }
    }

    private void flat(Node n, String q, List<Row> out) {
        for (Node c : n.kids.values()) {
            if (!c.dir) { if (c.path.toLowerCase(Locale.ROOT).contains(q)) out.add(new Row(c, 0)); }
            else flat(c, q, out);
            if (out.size() > 400) return;
        }
    }

    /** Compact description of the selection: fully selected folders / files. Empty list = nothing selected. */
    public List<String> prefixes() {
        List<String> out = new ArrayList<>();
        pre(root, out);
        return out;
    }

    private void pre(Node n, List<String> out) {
        for (Node c : n.kids.values()) {
            int s = c.state();
            if (s == 2) out.add(c.path);
            else if (s == 1) pre(c, out);
        }
    }

    public boolean allSelected() { return files.size() > 0 && selected.size() == files.size(); }

    public static boolean inPrefixes(List<String> prefixes, String path) {
        for (String p : prefixes) if (path.equals(p) || path.startsWith(p + "/")) return true;
        return false;
    }

    public static boolean isJunk(String p) {
        String l = p.toLowerCase(Locale.ROOT);
        String[] segs = l.split("/");
        for (String s : segs) {
            if (s.equals("node_modules") || s.equals("build") || s.equals("dist") || s.equals("target") || s.equals(".gradle") || s.equals(".idea")
                    || s.equals("__pycache__") || s.equals(".cxx") || s.equals("vendor") || s.equals("out") || s.equals(".next") || s.equals("venv")) return true;
        }
        String n = segs[segs.length - 1];
        return n.equals(".ds_store") || n.equals("thumbs.db") || n.endsWith(".class") || n.endsWith(".apk") || n.endsWith(".aab") || n.endsWith(".jar") && !n.equals("gradle-wrapper.jar") || n.endsWith(".log") || n.endsWith(".iml");
    }

    /** Direct children of .github/workflows ending in .yml/.yaml – the files GitHub treats as workflows. */
    public static boolean isWorkflowPath(String p) {
        String l = p.toLowerCase(Locale.ROOT);
        return l.startsWith(".github/workflows/") && l.indexOf('/', 18) < 0 && (l.endsWith(".yml") || l.endsWith(".yaml"));
    }

    public static boolean isDoc(String p) {
        String l = p.toLowerCase(Locale.ROOT);
        return l.startsWith("docs/") || l.endsWith(".md") || l.endsWith(".txt") || l.startsWith("license") || l.startsWith("readme");
    }
}
