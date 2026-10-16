package com.gitpush.app.ui;

import android.net.Uri;

import com.gitpush.app.GitPushApp;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Stager;
import com.gitpush.app.util.PathUtils;

import java.util.List;

/** Uploads picked files as release assets, one by one, with a progress dialog. */
public final class AssetUploader {
    private AssetUploader() {}

    public static void upload(final BaseActivity a, final GitHubApi api, final String owner, final String repo, final long releaseId,
                              final List<Uri> uris, final Runnable done) {
        if (uris.isEmpty()) { if (done != null) done.run(); return; }
        final GitPushApp app = GitPushApp.get();
        final ProgressDlg dlg = new ProgressDlg(a, "Uploading assets", "⬆", api::abort);
        app.io.execute(() -> {
            StringBuilder errors = new StringBuilder();
            int ok = 0;
            for (int i = 0; i < uris.size(); i++) {
                final Uri u = uris.get(i);
                final int idx = i;
                try {
                    String[] ns = Stager.nameAndSize(a, u);
                    final String name = ns[0];
                    final long size = Long.parseLong(ns[1]);
                    String mime = a.getContentResolver().getType(u);
                    api.uploadAsset(owner, repo, releaseId, name, mime, size, () -> a.getContentResolver().openInputStream(u), (d, t) ->
                            app.main.post(() -> dlg.set((idx + 1) + "/" + uris.size() + "  " + name + "\n" + PathUtils.formatSize(d) + " / " + PathUtils.formatSize(t), t > 0 ? (float) d / t : -1f)));
                    ok++;
                } catch (Exception e) { errors.append("• ").append(e.getMessage()).append('\n'); }
            }
            final int fok = ok;
            final String err = errors.toString();
            app.main.post(() -> {
                dlg.close();
                if (err.isEmpty()) Ui.toast(a, fok + " asset(s) uploaded");
                else new GlassDialog(a).setTitle("Some uploads failed").setIcon("⚠").setMessage(fok + " uploaded.\n\n" + err).setPositiveButton("OK", null).show();
                if (done != null) done.run();
            });
        });
    }
}
