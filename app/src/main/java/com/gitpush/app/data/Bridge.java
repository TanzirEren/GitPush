package com.gitpush.app.data;

import com.gitpush.app.util.CsUtil;
import com.gitpush.app.util.ShellKit;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Output bridge: a tiny read-only file server inside the codespace (python http.server on 127.0.0.1:7799 serving /tmp/gp).
 * VS Code forwards the port as PRIVATE; the app reads it with the X-Github-Token header, so run output can be shown natively.
 * Best effort – GitHub has no public API for terminal output.
 */
public final class Bridge {
    private Bridge() {}

    private static final OkHttpClient C = GitHubApi.CLIENT.newBuilder().followRedirects(false).followSslRedirects(false)
            .readTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build();

    /** Returns file text, or null when the file does not exist yet (404). */
    public static String fetch(GitHubApi api, String codespace, String file) throws ApiException {
        String url = CsUtil.portUrl(codespace, ShellKit.BRIDGE_PORT) + "/" + file;
        Request req = new Request.Builder().url(url).header("X-Github-Token", api.token()).header("User-Agent", "GitPush-Android").get().build();
        try (Response r = C.newCall(req).execute()) {
            if (r.isRedirect() || r.code() == 401 || r.code() == 403)
                throw new ApiException(401, "Bridge port " + ShellKit.BRIDGE_PORT + " is not reachable yet. Make sure it shows in VS Code's Ports tab (private), then retry.");
            if (r.code() == 404) return null;
            if (r.code() == 502 || r.code() == 503) throw new ApiException(r.code(), "The bridge is not running in the terminal yet.");
            if (!r.isSuccessful() || r.body() == null) throw new ApiException(r.code(), "Bridge error " + r.code());
            String t = r.body().string();
            if (t.length() > 400000) t = t.substring(t.length() - 400000);
            String type = r.header("Content-Type", "");
            if (type != null && type.contains("text/html") && t.contains("<html")) throw new ApiException(401, "Bridge answered with a web page (login needed). Re-open the codespace and retry.");
            return ShellKit.stripAnsi(t);
        } catch (IOException e) {
            throw new ApiException(-1, "Bridge not reachable: " + (e.getMessage() == null ? "network error" : e.getMessage()));
        }
    }
}
