package com.gitpush.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import com.gitpush.app.GitPushApp;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.util.PathUtils;

import java.io.OutputStream;
import java.util.Locale;

/**
 * Saves downloads (zipballs, artifacts, logs, release assets, exports) to the device.
 *
 * v2.1: the old "Save as" dialog was opened with application/octet-stream, so Android's file provider
 * glued ".bin" or " 1" onto names that already existed ("app-apk.zip 1"). Now the file is created with its REAL
 * MIME type, name clashes are resolved here as "name (1).zip" (extension always stays last) and a chosen folder is remembered.
 */
public final class Downloader {
    private Downloader() {}

    public static final int REQ = 4001, REQ_TREE = 4002;

    /** Writes the content; may call progress. Runs on a background thread. */
    public interface Job { void write(OutputStream os, GitHubApi.ProgressCb cb) throws Exception; }

    private static Job pendingJob;
    private static String pendingName, pendingMime;

    // ------------------------------------------------------------------ public API
    public static void request(Activity a, GitHubApi api, String url, String filename) { request(a, api, url, filename, "application/vnd.github+json"); }

    public static void request(Activity a, final GitHubApi api, final String url, String filename, final String accept) {
        requestWrite(a, filename, (os, cb) -> api.stream(url, os, cb, accept), api::abort);
    }

    /** Generic save: [job] produces the bytes. */
    public static void requestWrite(Activity a, String filename, Job job) { requestWrite(a, filename, job, null); }

    private static Runnable pendingAbort;

    public static void requestWrite(final Activity a, String filename, Job job, Runnable abort) {
        pendingJob = job; pendingName = safeName(filename); pendingMime = mimeFor(pendingName); pendingAbort = abort;
        final String saved = GitPushApp.get().state.store.downloadTree();
        if (!saved.isEmpty() && treeUsable(a, Uri.parse(saved))) { run(a, Uri.parse(saved)); return; }
        new GlassDialog(a).setTitle("Save " + pendingName).setIcon("⬇")
                .setMessage("Pick a folder once – GitPush remembers it. Name clashes become \"name (1).ext\" and the file type is kept, so ZIP/APK files always open and unzip.")
                .setPositiveButton("Choose folder", (d, w) -> chooseFolder(a))
                .setNegativeButton("Save as…", (d, w) -> saveAs(a))
                .setNeutralButton("Cancel", null).show();
    }

    /** Opens the folder picker (also used by the settings entry "Download folder"). */
    public static void chooseFolder(Activity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        a.startActivityForResult(i, REQ_TREE);
    }

    public static void forgetFolder() { GitPushApp.get().state.store.downloadTree(""); }

    public static String folderLabel() {
        String s = GitPushApp.get().state.store.downloadTree();
        if (s.isEmpty()) return "Not set (you will be asked)";
        try { String d = Uri.parse(s).getLastPathSegment(); return d == null ? s : d.replace("primary:", "Internal storage/"); } catch (Exception e) { return s; }
    }

    /** Call from onActivityResult; returns true when the result belonged to a download. */
    public static boolean onResult(final Activity a, int req, int res, Intent data) {
        if (req == REQ_TREE) {
            if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri tree = data.getData();
                try { a.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) { }
                GitPushApp.get().state.store.downloadTree(tree.toString());
                if (pendingJob != null) run(a, tree); else Ui.toast(a, "Download folder saved");
            } else pendingJob = null;
            return true;
        }
        if (req != REQ) return false;
        if (res != Activity.RESULT_OK || data == null || data.getData() == null || pendingJob == null) { pendingJob = null; return true; }
        final Uri uri = data.getData();
        final String want = pendingName;
        final Job job = pendingJob; final Runnable abort = pendingAbort; pendingJob = null;
        final GitPushApp app = GitPushApp.get();
        final ProgressDlg dlg = new ProgressDlg(a, "Downloading", "⬇", abort);
        app.io.execute(() -> {
            String err = null;
            try (OutputStream os = a.getContentResolver().openOutputStream(uri)) { job.write(os, cb(app, dlg)); }
            catch (Exception e) { err = e.getMessage() != null ? e.getMessage() : e.toString(); }
            String shown = want;
            try { shown = fixName(a, uri, want); } catch (Exception ignored) { }
            final String fe = err, fs = shown;
            app.main.post(() -> { dlg.close(); Ui.toast(a, fe == null ? "Saved " + fs : "Download failed: " + fe); });
        });
        return true;
    }

    // ------------------------------------------------------------------ internals
    private static GitHubApi.ProgressCb cb(final GitPushApp app, final ProgressDlg dlg) {
        return (done, total) -> app.main.post(() -> dlg.set(PathUtils.formatSize(done) + (total > 0 ? " / " + PathUtils.formatSize(total) : ""), total > 0 ? (float) done / total : -1f));
    }

    private static void saveAs(Activity a) {
        a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(pendingMime).putExtra(Intent.EXTRA_TITLE, pendingName), REQ);
    }

    private static boolean treeUsable(Activity a, Uri tree) {
        try {
            for (android.content.UriPermission p : a.getContentResolver().getPersistedUriPermissions())
                if (p.getUri().equals(tree) && p.isWritePermission()) { DocumentFile d = DocumentFile.fromTreeUri(a, tree); return d != null && d.exists() && d.canWrite(); }
        } catch (Exception ignored) { }
        return false;
    }

    private static void run(final Activity a, final Uri tree) {
        final Job job = pendingJob; final String name = pendingName, mime = pendingMime; final Runnable abort = pendingAbort;
        pendingJob = null;
        final GitPushApp app = GitPushApp.get();
        final ProgressDlg dlg = new ProgressDlg(a, "Downloading", "⬇", abort);
        app.io.execute(() -> {
            String err = null, shown = name;
            try {
                DocumentFile dir = DocumentFile.fromTreeUri(a, tree);
                if (dir == null || !dir.canWrite()) throw new Exception("The chosen folder is not writable. Choose another folder (Android blocks the root of Download/storage – pick or create a sub-folder).");
                String unique = uniqueName(dir, name);
                DocumentFile f = dir.createFile(mime, unique);
                if (f == null) throw new Exception("Could not create the file in that folder");
                String made = f.getName();
                if (made != null && !made.equals(unique)) { try { f.renameTo(unique); } catch (Exception ignored) { } }
                shown = f.getName() == null ? unique : f.getName();
                try (OutputStream os = a.getContentResolver().openOutputStream(f.getUri())) { job.write(os, cb(app, dlg)); }
            } catch (Exception e) { err = e.getMessage() != null ? e.getMessage() : e.toString(); }
            final String fe = err, fs = shown;
            app.main.post(() -> { dlg.close(); Ui.toast(a, fe == null ? "Saved " + fs + " to " + folderLabel() : "Download failed: " + fe); });
        });
    }

    /** "app.zip" -> "app (1).zip" when "app.zip" exists – the extension always stays last. */
    static String uniqueName(DocumentFile dir, String name) {
        if (dir.findFile(name) == null) return name;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        if (base.endsWith(".tar") && ext.equals(".gz")) { base = base.substring(0, base.length() - 4); ext = ".tar.gz"; }
        for (int i = 1; i < 1000; i++) {
            String cand = base + " (" + i + ")" + ext;
            if (dir.findFile(cand) == null) return cand;
        }
        return base + "-" + System.currentTimeMillis() + ext;
    }

    /** After "Save as…": if the provider altered the name (e.g. "x.zip" -> "x.zip.bin" / "x.zip 1"), repair it. */
    private static String fixName(Activity a, Uri uri, String want) {
        try {
            android.database.Cursor c = a.getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c == null) return want;
            String got = null;
            try { if (c.moveToFirst()) got = c.getString(0); } finally { c.close(); }
            if (got == null || got.equals(want)) return want;
            String ext = extension(want);
            String repaired = got;
            if (!ext.isEmpty() && !got.toLowerCase(Locale.ROOT).endsWith("." + ext)) {
                int at = got.toLowerCase(Locale.ROOT).indexOf("." + ext);
                String stem = at > 0 ? got.substring(0, at) : got;
                String tail = at > 0 ? got.substring(at + ext.length() + 1).replaceAll("[^0-9]", "") : "";
                repaired = stem + (tail.isEmpty() ? "" : " (" + tail + ")") + "." + ext;
            }
            if (!repaired.equals(got)) {
                try { Uri nu = android.provider.DocumentsContract.renameDocument(a.getContentResolver(), uri, repaired); if (nu != null) return repaired; } catch (Exception ignored) { }
            }
            return got;
        } catch (Exception e) { return want; }
    }

    static String extension(String n) { int d = n.lastIndexOf('.'); return d < 0 || d == n.length() - 1 ? "" : n.substring(d + 1).toLowerCase(Locale.ROOT); }

    static String safeName(String n) {
        String s = n == null ? "download" : n.replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001f]", "_").trim();
        return s.isEmpty() ? "download" : s;
    }

    /** Always the platform's own MIME for the extension, so the file provider never appends ".bin"/" 1" to the name. */
    public static String mimeFor(String filename) {
        String e = extension(filename);
        String m = e.isEmpty() ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);
        return m == null ? "application/octet-stream" : m;
    }
}
