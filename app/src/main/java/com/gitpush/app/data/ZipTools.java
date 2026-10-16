package com.gitpush.app.data;

import android.content.Context;
import android.net.Uri;

import com.gitpush.app.data.Stager.StagedFile;
import com.gitpush.app.util.PathUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Random-access ZIP helpers for the advanced extractor. */
public final class ZipTools {
    private ZipTools() {}

    public static class Entry {
        public final String raw, path;
        public final long size;
        public Entry(String raw, String path, long size) { this.raw = raw; this.path = path; this.size = size; }
    }

    public interface Progress { void on(int done, int total); }

    public static File copyToCache(Context ctx, Uri uri) throws IOException {
        File dir = new File(ctx.getCacheDir(), "zipsrc");
        dir.mkdirs();
        File out = new File(dir, "src_" + System.currentTimeMillis() + ".zip");
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Cannot open ZIP");
        try (InputStream i = in; FileOutputStream fo = new FileOutputStream(out)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = i.read(buf)) >= 0) fo.write(buf, 0, n);
        }
        return out;
    }

    public static List<Entry> list(File zip) throws IOException {
        List<Entry> out = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String clean = PathUtils.cleanZipPath(e.getName());
                if (clean == null) continue;
                out.add(new Entry(e.getName(), clean, Math.max(0, e.getSize())));
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("Not a valid ZIP file (encrypted ZIPs are not supported)");
        }
        return out;
    }

    /** Extracts the given entries (raw name → new path) into the cache and returns staged files. */
    public static List<StagedFile> extract(Context ctx, File zip, List<String[]> mapping, Progress cb) throws IOException {
        File dir = new File(ctx.getCacheDir(), "zip_" + System.currentTimeMillis());
        dir.mkdirs();
        String root = dir.getCanonicalPath() + File.separator;
        List<StagedFile> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        long total = 0;
        try (ZipFile zf = new ZipFile(zip)) {
            int done = 0;
            for (String[] m : mapping) {
                done++;
                if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                if (!used.add(m[1])) continue;
                ZipEntry e = zf.getEntry(m[0]);
                if (e == null) continue;
                File target = new File(dir, m[1]);
                if (!target.getCanonicalPath().startsWith(root)) continue;
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                try (InputStream in = zf.getInputStream(e); FileOutputStream fo = new FileOutputStream(target)) {
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) >= 0) {
                        total += n;
                        if (total > 1_500_000_000L) throw new IOException("Selection is too large (limit 1.5 GB)");
                        fo.write(buf, 0, n);
                    }
                }
                out.add(new StagedFile(m[1], target.length(), null, target, null));
                if (cb != null) cb.on(done, mapping.size());
            }
        }
        return out;
    }
}
