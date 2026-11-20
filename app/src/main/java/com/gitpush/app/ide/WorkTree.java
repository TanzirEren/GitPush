package com.gitpush.app.ide;

import android.content.Context;
import android.net.Uri;

import com.gitpush.app.data.ApiException;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.NewEntry;
import com.gitpush.app.data.Models.RefResult;
import com.gitpush.app.data.Models.TreeInfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * In-memory working copy of a repository tree. Every file operation (create, edit, rename, move, delete, import) is
 * recorded locally and sent as ONE commit through the Git Data API.
 */
public final class WorkTree {
    public static final class Node {
        public String path;
        public long size;
        public final String sha, origin;         // base blob sha and base path (null for new files)
        public String baseText, text;            // baseText = content at HEAD (once loaded), text = current content
        public boolean edited, isNew;
        public Uri importUri;
        public Node(String path, long size, String sha, String origin) { this.path = path; this.size = size; this.sha = sha; this.origin = origin; }
        public boolean moved() { return origin != null && !origin.equals(path); }
        public boolean changed() { return isNew || edited || moved() || importUri != null; }
        public String name() { int i = path.lastIndexOf('/'); return i < 0 ? path : path.substring(i + 1); }
    }

    public static final class Row {
        public final String path, name;
        public final boolean dir, open;
        public final int depth;
        public final Node node;
        Row(String path, String name, boolean dir, boolean open, int depth, Node node) { this.path = path; this.name = name; this.dir = dir; this.open = open; this.depth = depth; this.node = node; }
    }

    private final TreeMap<String, Node> files = new TreeMap<>();
    private final Set<String> basePaths = new HashSet<>();

    public WorkTree(List<TreeInfo> tree) {
        for (TreeInfo t : tree) if (t.type.equals("blob")) { files.put(t.path, new Node(t.path, t.size, t.sha, t.path)); basePaths.add(t.path); }
    }

    public Node file(String path) { return files.get(path); }
    public Collection<Node> all() { return files.values(); }
    public int fileCount() { return files.size(); }

    public boolean isFolder(String path) {
        String p = path + "/";
        for (String k : files.tailMap(p, true).keySet()) { if (k.startsWith(p)) return true; else break; }
        return false;
    }

    public boolean exists(String path) { return files.containsKey(path) || isFolder(path); }

    // ---------------------------------------------------------------- rows for the explorer
    public List<Row> rows(Set<String> expanded, String query) {
        List<Row> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            for (Node n : files.values()) if (n.path.toLowerCase(Locale.ROOT).contains(q)) out.add(new Row(n.path, n.path, false, false, 0, n));
            return out;
        }
        // folder -> children (folders first)
        Map<String, TreeSet<String>> dirs = new LinkedHashMap<>();
        Map<String, List<Node>> kids = new LinkedHashMap<>();
        dirs.put("", new TreeSet<String>(String.CASE_INSENSITIVE_ORDER));
        for (Node n : files.values()) {
            String parent = "";
            String[] segs = n.path.split("/");
            for (int i = 0; i < segs.length - 1; i++) {
                String child = parent.isEmpty() ? segs[i] : parent + "/" + segs[i];
                if (!dirs.containsKey(child)) dirs.put(child, new TreeSet<String>(String.CASE_INSENSITIVE_ORDER));
                dirs.get(parent).add(child);
                parent = child;
            }
            if (!kids.containsKey(parent)) kids.put(parent, new ArrayList<Node>());
            kids.get(parent).add(n);
        }
        walk("", 0, dirs, kids, expanded, out);
        return out;
    }

    private void walk(String dir, int depth, Map<String, TreeSet<String>> dirs, Map<String, List<Node>> kids, Set<String> expanded, List<Row> out) {
        TreeSet<String> sub = dirs.get(dir);
        if (sub != null) for (String d : sub) {
            boolean open = expanded.contains(d);
            out.add(new Row(d, d.substring(d.lastIndexOf('/') + 1), true, open, depth, null));
            if (open) walk(d, depth + 1, dirs, kids, expanded, out);
        }
        List<Node> ks = kids.get(dir);
        if (ks != null) {
            List<Node> sorted = new ArrayList<>(ks);
            java.util.Collections.sort(sorted, (a, b) -> a.name().compareToIgnoreCase(b.name()));
            for (Node n : sorted) out.add(new Row(n.path, n.name(), false, false, depth, n));
        }
    }

    // ---------------------------------------------------------------- operations
    private static void check(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.endsWith("/") || path.contains("//") || path.contains("..")
                || path.startsWith(".git/") || path.equals(".git")) throw new IllegalArgumentException("Invalid path: " + path);
    }

    public Node createFile(String path, String text) {
        check(path);
        if (exists(path)) throw new IllegalArgumentException("'" + path + "' already exists");
        Node n = new Node(path, text.length(), null, null);
        n.isNew = true; n.text = text; n.baseText = "";
        files.put(path, n);
        return n;
    }

    public void createFolder(String path) {
        check(path);
        if (exists(path)) throw new IllegalArgumentException("'" + path + "' already exists");
        createFile(path + "/.gitkeep", "");
    }

    public void move(String from, String to) {
        check(to);
        if (from.equals(to)) return;
        if (exists(to)) throw new IllegalArgumentException("'" + to + "' already exists");
        if (to.startsWith(from + "/")) throw new IllegalArgumentException("Can't move a folder into itself");
        if (files.containsKey(from)) {
            Node n = files.remove(from);
            n.path = to;
            files.put(to, n);
            return;
        }
        String p = from + "/";
        List<String> keys = new ArrayList<>();
        for (String k : files.keySet()) if (k.startsWith(p)) keys.add(k);
        if (keys.isEmpty()) throw new IllegalArgumentException("Nothing to move");
        for (String k : keys) {
            Node n = files.remove(k);
            n.path = to + "/" + k.substring(p.length());
            files.put(n.path, n);
        }
    }

    public void delete(String path) {
        if (files.remove(path) != null) return;
        String p = path + "/";
        List<String> keys = new ArrayList<>();
        for (String k : files.keySet()) if (k.startsWith(p)) keys.add(k);
        for (String k : keys) files.remove(k);
    }

    public Node duplicate(String from, String to) {
        Node src = files.get(from);
        if (src == null) throw new IllegalArgumentException("Not a file");
        check(to);
        if (exists(to)) throw new IllegalArgumentException("'" + to + "' already exists");
        Node n = new Node(to, src.size, src.sha, null);
        n.isNew = true;
        n.text = src.text != null ? src.text : src.baseText;
        n.baseText = "";
        n.importUri = src.importUri;
        if (n.text == null && n.importUri == null) n.isNew = true; // content is fetched at commit via blob sha
        files.put(to, n);
        return n;
    }

    public Node importFile(String path, Uri uri, long size) {
        check(path);
        String target = path;
        int i = 1;
        while (exists(target)) {
            int dot = path.lastIndexOf('.');
            int slash = path.lastIndexOf('/');
            target = dot > slash ? path.substring(0, dot) + " (" + i + ")" + path.substring(dot) : path + " (" + i + ")";
            i++;
        }
        Node n = new Node(target, size, null, null);
        n.isNew = true; n.importUri = uri;
        files.put(target, n);
        return n;
    }

    /** Applies edited editor text to a file; marks it edited only when it differs from HEAD. */
    public void setText(String path, String text) {
        Node n = files.get(path);
        if (n == null) return;
        n.text = text;
        n.size = text.length();
        if (n.isNew || n.baseText == null) { n.edited = n.edited || n.baseText != null; return; }
        n.edited = !text.equals(n.baseText);
    }

    public void discard(String path) {
        Node n = files.get(path);
        if (n != null && !n.isNew) { n.text = n.baseText; n.edited = false; }
    }

    // ---------------------------------------------------------------- change tracking
    public Set<String> deleted() {
        Set<String> keep = new HashSet<>();
        for (Node n : files.values()) if (n.origin != null && n.origin.equals(n.path)) keep.add(n.path);
        Set<String> out = new TreeSet<>(basePaths);
        out.removeAll(keep);
        // paths that were moved away are reported as moves, not deletions
        Set<String> movedFrom = new HashSet<>();
        for (Node n : files.values()) if (n.moved()) movedFrom.add(n.origin);
        out.removeAll(movedFrom);
        return out;
    }

    public int changeCount() {
        int c = deleted().size();
        for (Node n : files.values()) if (n.changed()) c++;
        return c;
    }

    public List<String> summary(int max) {
        List<String> out = new ArrayList<>();
        for (Node n : files.values()) {
            if (!n.changed()) continue;
            String tag = n.isNew || n.importUri != null ? "A " : n.moved() ? "R " : "M ";
            out.add(tag + n.path + (n.moved() ? "  ← " + n.origin : ""));
        }
        for (String d : deleted()) out.add("D " + d);
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    // ---------------------------------------------------------------- commit
    public String commit(final Context ctx, GitHubApi api, String owner, String repo, String branch, String message) throws Exception {
        RefResult ref = api.ref(owner, repo, branch);
        if (ref.kind != RefResult.FOUND) throw new ApiException(404, "Branch '" + branch + "' not found.");
        String head = ref.sha;
        String baseTree = api.commitTree(owner, repo, head);
        List<NewEntry> entries = new ArrayList<>();
        Set<String> writes = new HashSet<>();
        for (final Node n : files.values()) {
            if (!n.changed()) continue;
            if (n.importUri != null) {
                final Uri u = n.importUri;
                String sha = api.createBlob(owner, repo, () -> ctx.getContentResolver().openInputStream(u));
                entries.add(NewEntry.blob(n.path, sha));
            } else if (n.text != null) {
                if (n.text.isEmpty()) entries.add(NewEntry.blob(n.path, api.createBlob(owner, repo, () -> new ByteArrayInputStream(new byte[0]))));
                else entries.add(NewEntry.text(n.path, n.text));
            } else if (n.sha != null) entries.add(NewEntry.blob(n.path, n.sha));
            writes.add(n.path);
        }
        for (String d : deleted()) if (!writes.contains(d)) entries.add(NewEntry.remove(d));
        for (Node n : files.values()) if (n.moved() && !writes.contains(n.origin) && !hasEntry(entries, n.origin)) entries.add(NewEntry.remove(n.origin));
        if (entries.isEmpty()) throw new ApiException(422, "No changes to commit.");
        String tree = baseTree;
        for (int i = 0; i < entries.size(); i += 100) tree = api.createTree(owner, repo, tree, entries.subList(i, Math.min(entries.size(), i + 100)));
        List<String> parents = new ArrayList<>();
        parents.add(head);
        String commit = api.createCommit(owner, repo, message, tree, parents);
        api.updateRef(owner, repo, branch, commit, false);
        return commit;
    }

    private static boolean hasEntry(List<NewEntry> list, String path) {
        for (NewEntry e : list) if (e.path.equals(path)) return true;
        return false;
    }

    /** Reads the current content of a node as a stream when it is not loaded (used for duplicate/export of untouched files). */
    public InputStream stream(Node n, GitHubApi api, String owner, String repo) throws Exception {
        if (n.text != null) return new ByteArrayInputStream(n.text.getBytes("UTF-8"));
        if (n.sha == null) return new ByteArrayInputStream(new byte[0]);
        return new ByteArrayInputStream(api.blobBytes(owner, repo, n.sha));
    }
}
