package com.gitpush.app.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.documentfile.provider.DocumentFile;

import com.gitpush.app.util.PathUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Stager {
    private Stager() {}

    /** A file queued for upload. {@code path} is the relative path inside the repo (folder hierarchy preserved). */
    public static class StagedFile {
        public final String path;
        public final long size;
        public final Uri uri;
        public final File file;
        public final byte[] bytes;
        public StagedFile(String path, long size, Uri uri, File file, byte[] bytes) {
            this.path = path; this.size = size; this.uri = uri; this.file = file; this.bytes = bytes;
        }
        public InputStream open(Context ctx) throws IOException {
            if (bytes != null) return new ByteArrayInputStream(bytes);
            if (file != null) return new FileInputStream(file);
            if (uri != null) {
                InputStream in = ctx.getContentResolver().openInputStream(uri);
                if (in == null) throw new IOException("Cannot open " + path);
                return in;
            }
            throw new IOException("No source for " + path);
        }
        public String key() { return uri != null ? uri.toString() : file != null ? file.getAbsolutePath() : "gen:" + path; }
    }

    /** Tree node used by the staging list UI. */
    public static class TNode {
        public final String name, path;
        public final boolean isDir;
        public final TreeMap<String, TNode> children = new TreeMap<>(java.util.Comparator.comparing((String x) -> x.toLowerCase(java.util.Locale.ROOT)).thenComparing(java.util.Comparator.<String>naturalOrder()));
        public long size;
        public int count;
        public TNode(String name, String path, boolean isDir) { this.name = name; this.path = path; this.isDir = isDir; }
    }

    public static class Row {
        public final TNode node; public final int depth;
        public Row(TNode n, int d) { node = n; depth = d; }
    }

    public static String[] nameAndSize(Context ctx, Uri uri) {
        String name = uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "file";
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        long size = -1;
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME), si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
            }
        } catch (Exception ignored) { }
        if (size < 0) {
            size = 0;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in != null) { byte[] buf = new byte[65536]; int n; size = 0; while ((n = in.read(buf)) >= 0) size += n; }
            } catch (Exception ignored) { }
        }
        return new String[]{name, String.valueOf(size)};
    }

    public static List<StagedFile> filesFromUris(Context ctx, List<Uri> uris) {
        List<StagedFile> out = new ArrayList<>();
        for (Uri u : uris) {
            String[] ns = nameAndSize(ctx, u);
            out.add(new StagedFile(ns[0], Long.parseLong(ns[1]), u, null, null));
        }
        return out;
    }

    public static List<StagedFile> folderFromTree(Context ctx, Uri treeUri) {
        List<StagedFile> out = new ArrayList<>();
        DocumentFile root = DocumentFile.fromTreeUri(ctx, treeUri);
        if (root == null) return out;
        String rn = root.getName() != null ? root.getName() : "folder";
        walk(root, rn + "/", out);
        return out;
    }

    private static void walk(DocumentFile dir, String prefix, List<StagedFile> out) {
        boolean any = false;
        DocumentFile[] kids = dir.listFiles();
        for (DocumentFile k : kids) {
            String n = k.getName();
            if (n == null) continue;
            if (k.isDirectory()) {
                if (n.equals(".git")) continue;
                any = true;
                walk(k, prefix + n + "/", out);
            } else if (k.isFile()) {
                any = true;
                out.add(new StagedFile(prefix + n, k.length(), k.getUri(), null, null));
            }
        }
        // Git cannot store empty folders – keep them alive with a .gitkeep placeholder
        if (!any) out.add(new StagedFile(prefix + ".gitkeep", 0, null, null, new byte[0]));
    }

    public static List<StagedFile> extractZip(Context ctx, Uri uri) throws IOException {
        File dir = new File(ctx.getCacheDir(), "zip_" + System.currentTimeMillis());
        dir.mkdirs();
        String root = dir.getCanonicalPath() + File.separator;
        List<StagedFile> out = new ArrayList<>();
        long total = 0;
        InputStream raw = ctx.getContentResolver().openInputStream(uri);
        if (raw == null) throw new IOException("Cannot open ZIP");
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zin.getNextEntry()) != null) {
                String clean = PathUtils.cleanZipPath(e.getName());
                if (clean == null || e.isDirectory()) continue;
                File target = new File(dir, clean);
                if (!target.getCanonicalPath().startsWith(root)) continue;
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                try (FileOutputStream fo = new FileOutputStream(target)) {
                    int n;
                    while ((n = zin.read(buf)) >= 0) {
                        total += n;
                        if (total > 1_500_000_000L) throw new IOException("ZIP is too large (limit 1.5 GB extracted)");
                        fo.write(buf, 0, n);
                    }
                }
                out.add(new StagedFile(clean, target.length(), null, target, null));
            }
        }
        return out;
    }

    public static TNode buildTree(List<StagedFile> files) {
        TNode root = new TNode("", "", true);
        for (StagedFile f : files) {
            String[] parts = f.path.split("/");
            TNode cur = root;
            root.size += f.size; root.count++;
            StringBuilder acc = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (acc.length() > 0) acc.append('/');
                acc.append(parts[i]);
                boolean last = i == parts.length - 1;
                TNode next = cur.children.get(parts[i]);
                if (next == null) { next = new TNode(parts[i], acc.toString(), !last); cur.children.put(parts[i], next); }
                if (!last) { next.size += f.size; next.count++; } else { next.size = f.size; next.count = 1; }
                cur = next;
            }
        }
        return root;
    }

    public static List<Row> flatten(TNode root, Set<String> expanded) {
        List<Row> out = new ArrayList<>();
        rec(root, 0, expanded, out);
        return out;
    }

    private static void rec(TNode n, int depth, Set<String> expanded, List<Row> out) {
        for (TNode c : n.children.values()) {
            out.add(new Row(c, depth));
            if (c.isDir && expanded.contains(c.path)) rec(c, depth + 1, expanded, out);
        }
    }
}
