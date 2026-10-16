package com.gitpush.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.gitpush.app.GitPushApp;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.util.PathUtils;

import java.io.OutputStream;

/** Saves an authenticated GitHub download (zipball, artifact, logs) to a user-chosen location. */
public final class Downloader {
    private Downloader() {}

    public static final int REQ = 4001;
    private static String pendingUrl, pendingName;
    private static GitHubApi pendingApi;

    public static void request(Activity a, GitHubApi api, String url, String filename) {
        pendingApi = api; pendingUrl = url; pendingName = filename;
        a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, filename), REQ);
    }

    /** Call from onActivityResult; returns true when the result belonged to a download. */
    public static boolean onResult(final Activity a, int req, int res, Intent data) {
        if (req != REQ) return false;
        if (res != Activity.RESULT_OK || data == null || data.getData() == null || pendingApi == null) return true;
        final Uri uri = data.getData();
        final GitHubApi api = pendingApi;
        final String url = pendingUrl, name = pendingName;
        final GitPushApp app = GitPushApp.get();
        final ProgressDlg dlg = new ProgressDlg(a, "Downloading", "⬇", api::abort);
        app.io.execute(() -> {
            String err = null;
            try (OutputStream os = a.getContentResolver().openOutputStream(uri)) {
                api.stream(url, os, (done, total) -> app.main.post(() ->
                        dlg.set(PathUtils.formatSize(done) + (total > 0 ? " / " + PathUtils.formatSize(total) : ""), total > 0 ? (float) done / total : -1f)));
            } catch (Exception e) { err = e.getMessage() != null ? e.getMessage() : e.toString(); }
            final String fe = err;
            app.main.post(() -> { dlg.close(); Ui.toast(a, fe == null ? "Saved " + name : "Download failed: " + fe); });
        });
        return true;
    }
}
