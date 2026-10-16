package com.gitpush.app.data;

import android.util.Base64;
import android.util.Base64OutputStream;

import com.gitpush.app.data.Models.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/** Blocking GitHub REST client – always call from a background thread. */
public class GitHubApi {
    static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).writeTimeout(180, TimeUnit.SECONDS).build();
    static final MediaType JSON = MediaType.get("application/json");

    public interface StreamSource { InputStream open() throws IOException; }

    /** Streams {prefix + base64(file) + suffix} so big files never sit in memory as one string. */
    static class StreamJsonBody extends RequestBody {
        private final String prefix, suffix;
        private final StreamSource src;
        StreamJsonBody(String prefix, String suffix, StreamSource src) { this.prefix = prefix; this.suffix = suffix; this.src = src; }
        @Override public MediaType contentType() { return JSON; }
        @Override public long contentLength() { return -1L; }
        @Override public boolean isOneShot() { return false; }
        @Override public void writeTo(BufferedSink sink) throws IOException {
            sink.writeUtf8(prefix);
            OutputStream guard = new FilterOutputStream(sink.outputStream()) {
                @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
                @Override public void close() throws IOException { flush(); }
            };
            Base64OutputStream b64 = new Base64OutputStream(guard, Base64.NO_WRAP);
            try (InputStream in = src.open()) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) b64.write(buf, 0, n);
            }
            b64.close();
            sink.writeUtf8(suffix);
        }
    }

    static class Resp {
        final int code; final String body; final Headers headers;
        Resp(int code, String body, Headers headers) { this.code = code; this.body = body; this.headers = headers; }
        JSONObject json() throws ApiException { try { return new JSONObject(body); } catch (JSONException e) { throw new ApiException(-3, "Unexpected response from GitHub"); } }
        JSONArray array() throws ApiException { try { return new JSONArray(body); } catch (JSONException e) { throw new ApiException(-3, "Unexpected response from GitHub"); } }
    }

    private final String token;
    private volatile boolean aborted;
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();

    public GitHubApi(String token) { this.token = token; }

    public void abort() { aborted = true; for (Call c : calls) c.cancel(); }

    // ---------------------------------------------------------------- plumbing
    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
    }
    private static String encPath(String p) {
        String[] parts = p.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) { if (i > 0) sb.append('/'); sb.append(enc(parts[i])); }
        return sb.toString();
    }
    private static String rp(String owner, String repo) { return "/repos/" + enc(owner) + "/" + enc(repo); }
    private static RequestBody jsonBody(JSONObject o) { return RequestBody.create(o.toString(), JSON); }
    private static JSONObject o(JSONObject j, String k) { JSONObject r = j.optJSONObject(k); return r != null ? r : new JSONObject(); }
    private static String str(JSONObject j, String k) { return j.isNull(k) ? null : j.optString(k); }
    private static JSONObject obj(Object... kv) {
        JSONObject j = new JSONObject();
        try { for (int i = 0; i < kv.length; i += 2) j.put((String) kv[i], kv[i + 1]); } catch (JSONException ignored) { }
        return j;
    }

    private Resp call(String method, String path, RequestBody body) throws ApiException {
        IOException last = null;
        for (int attempt = 0; attempt <= 3; attempt++) {
            if (aborted) throw new ApiException(-2, "Cancelled");
            Call call = null;
            try {
                String url = path.startsWith("http") ? path : "https://api.github.com" + path;
                Request.Builder b = new Request.Builder().url(url)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", "GitPush-Android");
                Request req;
                if (method.equals("GET")) req = b.get().build();
                else if (method.equals("DELETE")) req = b.delete(body).build();
                else req = b.method(method, body != null ? body : RequestBody.create("", JSON)).build();
                call = CLIENT.newCall(req);
                calls.add(call);
                Resp resp;
                try (Response r = call.execute()) {
                    String text = r.body() != null ? r.body().string() : "";
                    resp = new Resp(r.code(), text, r.headers());
                }
                String ra = resp.headers.get("Retry-After");
                if ((resp.code == 403 || resp.code == 429) && ra != null && attempt < 3) {
                    long secs = 5; try { secs = Long.parseLong(ra.trim()); } catch (NumberFormatException ignored) { }
                    sleep(Math.min(secs, 60) * 1000L); continue;
                }
                if (resp.code >= 502 && resp.code <= 504 && attempt < 2) { sleep(1500L * (attempt + 1)); continue; }
                return resp;
            } catch (IOException e) {
                if (aborted) throw new ApiException(-2, "Cancelled");
                last = e;
                if (attempt < 2) sleep(1500L * (attempt + 1));
            } finally {
                if (call != null) calls.remove(call);
            }
        }
        throw new ApiException(-1, "Network error: " + (last != null && last.getMessage() != null ? last.getMessage() : "connection failed"));
    }

    private void sleep(long ms) throws ApiException {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(-2, "Cancelled"); }
    }

    private ApiException fail(Resp r) {
        String msg = "";
        try { msg = new JSONObject(r.body).optString("message"); } catch (Exception ignored) { }
        String remaining = r.headers.get("X-RateLimit-Remaining");
        String text;
        if (r.code == 401) text = "Token is invalid, expired or revoked (401). Please sign in again.";
        else if (r.code == 403 && "0".equals(remaining)) text = "GitHub rate limit reached (403). Try again later.";
        else if (r.code == 403) text = "Permission denied (403). " + msg;
        else if (r.code == 404) text = "Not found (404). Check repo, branch, path or token permissions. " + msg;
        else if (r.code == 409) text = "Conflict (409). " + msg;
        else if (r.code == 422) text = "Validation failed (422). " + msg;
        else text = "GitHub error " + r.code + ". " + msg;
        return new ApiException(r.code, text.trim());
    }

    private Resp ok(Resp r) throws ApiException { if (r.code >= 400) throw fail(r); return r; }

    // ---------------------------------------------------------------- account & repos
    /** Returns {login json, scopes}. */
    public JSONObject user(String[] scopesOut) throws ApiException {
        Resp r = ok(call("GET", "/user", null));
        String sc = r.headers.get("X-OAuth-Scopes");
        scopesOut[0] = sc == null ? "" : sc;
        return r.json();
    }

    private Repo parseRepo(JSONObject j) {
        Repo r = new Repo();
        JSONObject perms = j.optJSONObject("permissions");
        r.id = j.optLong("id"); r.name = j.optString("name"); r.fullName = j.optString("full_name");
        r.owner = o(j, "owner").optString("login"); r.isPrivate = j.optBoolean("private");
        String d = str(j, "description"); r.description = d != null && !d.trim().isEmpty() ? d : null;
        String db = str(j, "default_branch"); r.defaultBranch = db != null ? db : "main";
        r.htmlUrl = j.optString("html_url"); r.cloneUrl = j.optString("clone_url");
        String up = str(j, "pushed_at"); if (up == null) up = str(j, "updated_at");
        r.updatedAt = up != null ? up : "";
        r.language = str(j, "language"); r.stars = j.optInt("stargazers_count"); r.sizeKb = j.optInt("size");
        r.canPush = perms == null || perms.optBoolean("push", true);
        r.isAdmin = perms != null && perms.optBoolean("admin", false);
        return r;
    }

    public List<Repo> repos() throws ApiException {
        List<Repo> out = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            JSONArray arr = ok(call("GET", "/user/repos?per_page=100&sort=pushed&page=" + page
                    + "&affiliation=owner,collaborator,organization_member", null)).array();
            for (int i = 0; i < arr.length(); i++) { JSONObject j = arr.optJSONObject(i); if (j != null) out.add(parseRepo(j)); }
            if (arr.length() < 100) break;
        }
        return out;
    }

    public Repo repo(String owner, String repo) throws ApiException { return parseRepo(ok(call("GET", rp(owner, repo), null)).json()); }

    public Repo createRepo(String name, String description, boolean isPrivate) throws ApiException {
        return parseRepo(ok(call("POST", "/user/repos", jsonBody(obj("name", name, "description", description, "private", isPrivate)))).json());
    }

    // ---------------------------------------------------------------- branches
    public List<Branch> branches(String owner, String repo) throws ApiException {
        Resp r = call("GET", rp(owner, repo) + "/branches?per_page=100", null);
        List<Branch> out = new ArrayList<>();
        if (r.code == 409 || r.code == 404) return out;
        JSONArray arr = ok(r).array();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j != null) out.add(new Branch(j.optString("name"), o(j, "commit").optString("sha")));
        }
        return out;
    }

    public void createBranch(String owner, String repo, String name, String fromSha) throws ApiException {
        ok(call("POST", rp(owner, repo) + "/git/refs", jsonBody(obj("ref", "refs/heads/" + name, "sha", fromSha))));
    }

    public void deleteBranch(String owner, String repo, String name) throws ApiException {
        ok(call("DELETE", rp(owner, repo) + "/git/refs/heads/" + encPath(name), null));
    }

    // ---------------------------------------------------------------- contents
    private String contentsUrl(String owner, String repo, String path, String ref) {
        String p = path.isEmpty() ? "" : "/" + encPath(path);
        String q = ref == null || ref.isEmpty() ? "" : "?ref=" + enc(ref);
        return rp(owner, repo) + "/contents" + p + q;
    }

    public List<ContentItem> contents(String owner, String repo, String path, String ref) throws ApiException {
        Resp r = call("GET", contentsUrl(owner, repo, path, ref), null);
        List<ContentItem> out = new ArrayList<>();
        if (r.code == 404 && path.isEmpty()) return out;
        if ((r.code == 404 || r.code == 409) && r.body.toLowerCase().contains("empty")) return out;
        ok(r);
        List<JSONObject> list = new ArrayList<>();
        if (r.body.trim().startsWith("[")) {
            JSONArray arr = r.array();
            for (int i = 0; i < arr.length(); i++) { JSONObject j = arr.optJSONObject(i); if (j != null) list.add(j); }
        } else list.add(r.json());
        for (JSONObject j : list)
            out.add(new ContentItem(j.optString("name"), j.optString("path"), "dir".equals(j.optString("type")), j.optLong("size"), j.optString("sha")));
        out.sort((a, b) -> a.isDir != b.isDir ? (a.isDir ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
        return out;
    }

    public FileData file(String owner, String repo, String path, String ref) throws ApiException {
        JSONObject j = ok(call("GET", contentsUrl(owner, repo, path, ref), null)).json();
        String sha = j.optString("sha");
        String content = j.optString("content", "");
        byte[] bytes = !content.isEmpty() && "base64".equals(j.optString("encoding"))
                ? Base64.decode(content, Base64.DEFAULT) : blobBytes(owner, repo, sha);
        return new FileData(sha, bytes, j.optLong("size"));
    }

    public byte[] blobBytes(String owner, String repo, String sha) throws ApiException {
        JSONObject j = ok(call("GET", rp(owner, repo) + "/git/blobs/" + sha, null)).json();
        return Base64.decode(j.optString("content", ""), Base64.DEFAULT);
    }

    /** Create/update a file via the Contents API. Returns {commit sha, html url}. */
    public String[] putFile(String owner, String repo, String path, String message, String branch, String sha, StreamSource src) throws ApiException {
        StringBuilder prefix = new StringBuilder("{\"message\":").append(JSONObject.quote(message)).append(',');
        if (branch != null && !branch.isEmpty()) prefix.append("\"branch\":").append(JSONObject.quote(branch)).append(',');
        if (sha != null && !sha.isEmpty()) prefix.append("\"sha\":").append(JSONObject.quote(sha)).append(',');
        prefix.append("\"content\":\"");
        JSONObject c = o(ok(call("PUT", contentsUrl(owner, repo, path, null), new StreamJsonBody(prefix.toString(), "\"}", src))).json(), "commit");
        return new String[]{c.optString("sha"), c.optString("html_url")};
    }

    public String[] putText(String owner, String repo, String path, String message, String branch, String sha, String text) throws ApiException {
        final byte[] b = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return putFile(owner, repo, path, message, branch, sha, () -> new java.io.ByteArrayInputStream(b));
    }

    public void deleteFile(String owner, String repo, String path, String sha, String message, String branch) throws ApiException {
        ok(call("DELETE", contentsUrl(owner, repo, path, null), jsonBody(obj("message", message, "sha", sha, "branch", branch))));
    }

    // ---------------------------------------------------------------- git data
    public RefResult ref(String owner, String repo, String branch) throws ApiException {
        Resp r = call("GET", rp(owner, repo) + "/git/ref/heads/" + encPath(branch), null);
        if (r.code == 200) return new RefResult(RefResult.FOUND, o(r.json(), "object").optString("sha"));
        if (r.code == 409) return new RefResult(RefResult.EMPTY, null);
        if (r.code == 404) return new RefResult(RefResult.MISSING, null);
        throw fail(r);
    }

    public String commitTree(String owner, String repo, String commitSha) throws ApiException {
        return o(ok(call("GET", rp(owner, repo) + "/git/commits/" + commitSha, null)).json(), "tree").optString("sha");
    }

    public List<TreeInfo> treeRecursive(String owner, String repo, String treeSha) throws ApiException {
        JSONObject j = ok(call("GET", rp(owner, repo) + "/git/trees/" + treeSha + "?recursive=1", null)).json();
        JSONArray arr = j.optJSONArray("tree");
        List<TreeInfo> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject t = arr.optJSONObject(i);
            if (t != null) out.add(new TreeInfo(t.optString("path"), t.optString("type"), t.optString("sha"), t.optLong("size")));
        }
        return out;
    }

    public String createBlob(String owner, String repo, StreamSource src) throws ApiException {
        return ok(call("POST", rp(owner, repo) + "/git/blobs", new StreamJsonBody("{\"encoding\":\"base64\",\"content\":\"", "\"}", src))).json().optString("sha");
    }

    public String createTree(String owner, String repo, String base, List<NewEntry> entries) throws ApiException {
        JSONArray arr = new JSONArray();
        try {
            for (NewEntry e : entries) {
                JSONObject j = new JSONObject().put("path", e.path).put("mode", "100644").put("type", "blob");
                if (e.delete) j.put("sha", JSONObject.NULL);
                else if (e.content != null) j.put("content", e.content);
                else j.put("sha", e.sha);
                arr.put(j);
            }
            JSONObject body = new JSONObject().put("tree", arr);
            if (base != null) body.put("base_tree", base);
            return ok(call("POST", rp(owner, repo) + "/git/trees", jsonBody(body))).json().optString("sha");
        } catch (JSONException ex) { throw new ApiException(-3, "Could not build tree: " + ex.getMessage()); }
    }

    public String createCommit(String owner, String repo, String message, String tree, List<String> parents) throws ApiException {
        JSONArray p = new JSONArray();
        for (String s : parents) p.put(s);
        return ok(call("POST", rp(owner, repo) + "/git/commits", jsonBody(obj("message", message, "tree", tree, "parents", p)))).json().optString("sha");
    }

    public void updateRef(String owner, String repo, String branch, String sha, boolean force) throws ApiException {
        ok(call("PATCH", rp(owner, repo) + "/git/refs/heads/" + encPath(branch), jsonBody(obj("sha", sha, "force", force))));
    }

    // ---------------------------------------------------------------- history / compare / merge
    private CommitInfo parseCommit(JSONObject j) {
        CommitInfo c = new CommitInfo();
        JSONObject cm = o(j, "commit");
        JSONObject author = o(cm, "author");
        String login = o(j, "author").optString("login", "");
        c.sha = j.optString("sha"); c.message = cm.optString("message");
        c.author = !login.isEmpty() ? login : author.optString("name", "unknown");
        c.date = author.optString("date", ""); c.htmlUrl = j.optString("html_url");
        JSONArray ps = j.optJSONArray("parents");
        if (ps != null) for (int i = 0; i < ps.length(); i++) { JSONObject p = ps.optJSONObject(i); if (p != null) c.parents.add(p.optString("sha")); }
        return c;
    }

    private List<CommitFile> parseFiles(JSONArray arr) {
        List<CommitFile> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            CommitFile f = new CommitFile();
            f.filename = j.optString("filename"); f.previous = str(j, "previous_filename"); f.status = j.optString("status");
            f.additions = j.optInt("additions"); f.deletions = j.optInt("deletions"); f.patch = str(j, "patch");
            out.add(f);
        }
        return out;
    }

    public List<CommitInfo> commits(String owner, String repo, String branch, int page, String path) throws ApiException {
        String p = path == null || path.isEmpty() ? "" : "&path=" + enc(path);
        Resp r = call("GET", rp(owner, repo) + "/commits?sha=" + enc(branch) + "&per_page=30&page=" + page + p, null);
        List<CommitInfo> out = new ArrayList<>();
        if (r.code == 409) return out;
        JSONArray arr = ok(r).array();
        for (int i = 0; i < arr.length(); i++) { JSONObject j = arr.optJSONObject(i); if (j != null) out.add(parseCommit(j)); }
        return out;
    }

    public CommitDetail commit(String owner, String repo, String sha) throws ApiException {
        JSONObject j = ok(call("GET", rp(owner, repo) + "/commits/" + sha, null)).json();
        CommitDetail d = new CommitDetail();
        d.info = parseCommit(j); d.files = parseFiles(j.optJSONArray("files"));
        return d;
    }

    public CompareResult compare(String owner, String repo, String base, String head) throws ApiException {
        JSONObject j = ok(call("GET", rp(owner, repo) + "/compare/" + enc(base) + "..." + enc(head), null)).json();
        CompareResult c = new CompareResult();
        c.status = j.optString("status"); c.ahead = j.optInt("ahead_by"); c.behind = j.optInt("behind_by"); c.files = parseFiles(j.optJSONArray("files"));
        return c;
    }

    public String merge(String owner, String repo, String base, String head, String message) throws ApiException {
        Resp r = call("POST", rp(owner, repo) + "/merges", jsonBody(obj("base", base, "head", head, "commit_message", message)));
        if (r.code == 201) return "Merged successfully.";
        if (r.code == 204) return "Nothing to merge – branches are already up to date.";
        if (r.code == 409) throw new ApiException(409, "Merge conflict. Resolve it on GitHub.");
        throw fail(r);
    }

    // ---------------------------------------------------------------- progress / raw bodies / streaming
    public interface ProgressCb { void on(long done, long total); }

    static class RawBody extends RequestBody {
        private final MediaType type; private final long size; private final StreamSource src; private final ProgressCb cb;
        RawBody(String mime, long size, StreamSource src, ProgressCb cb) {
            MediaType t;
            try { t = MediaType.get(mime == null || mime.isEmpty() ? "application/octet-stream" : mime); } catch (Exception e) { t = MediaType.get("application/octet-stream"); }
            this.type = t; this.size = size; this.src = src; this.cb = cb;
        }
        @Override public MediaType contentType() { return type; }
        @Override public long contentLength() { return size; }
        @Override public boolean isOneShot() { return false; }
        @Override public void writeTo(BufferedSink sink) throws IOException {
            try (InputStream in = src.open()) {
                byte[] buf = new byte[64 * 1024]; int n; long done = 0;
                OutputStream os = sink.outputStream();
                while ((n = in.read(buf)) >= 0) { os.write(buf, 0, n); done += n; if (cb != null) cb.on(done, size); }
            }
        }
    }

    /** Authenticated GET streamed into [out] (follows redirects; used for zipballs, logs, artifacts). */
    public void stream(String url, OutputStream out, ProgressCb cb) throws ApiException {
        Call call = null;
        try {
            String full = url.startsWith("http") ? url : "https://api.github.com" + url;
            Request req = new Request.Builder().url(full).header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "GitPush-Android").get().build();
            call = CLIENT.newCall(req);
            calls.add(call);
            try (Response r = call.execute()) {
                if (!r.isSuccessful()) {
                    String t = r.body() != null ? r.body().string() : "";
                    throw fail(new Resp(r.code(), t, r.headers()));
                }
                okhttp3.ResponseBody b = r.body();
                if (b == null) throw new ApiException(-3, "Empty response");
                long total = b.contentLength();
                try (InputStream in = b.byteStream()) {
                    byte[] buf = new byte[64 * 1024]; int n; long done = 0;
                    while ((n = in.read(buf)) >= 0) { out.write(buf, 0, n); done += n; if (cb != null) cb.on(done, total); }
                }
            }
        } catch (IOException e) {
            if (aborted) throw new ApiException(-2, "Cancelled");
            throw new ApiException(-1, "Network error: " + (e.getMessage() == null ? "download failed" : e.getMessage()));
        } finally { if (call != null) calls.remove(call); }
    }

    // ---------------------------------------------------------------- repo management
    public Repo updateRepo(String owner, String repo, JSONObject patch) throws ApiException {
        return parseRepo(ok(call("PATCH", rp(owner, repo), jsonBody(patch))).json());
    }

    public void deleteRepo(String owner, String repo) throws ApiException { ok(call("DELETE", rp(owner, repo), null)); }

    public List<String> topics(String owner, String repo) throws ApiException {
        JSONArray arr = ok(call("GET", rp(owner, repo) + "/topics", null)).json().optJSONArray("names");
        List<String> out = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        return out;
    }

    public void setTopics(String owner, String repo, List<String> names) throws ApiException {
        JSONArray a = new JSONArray();
        for (String n : names) a.put(n);
        ok(call("PUT", rp(owner, repo) + "/topics", jsonBody(obj("names", a))));
    }

    /** Returns {remaining, limit, resetEpochSeconds}. */
    public long[] rateLimit() throws ApiException {
        JSONObject core = o(o(ok(call("GET", "/rate_limit", null)).json(), "resources"), "core");
        return new long[]{core.optLong("remaining"), core.optLong("limit"), core.optLong("reset")};
    }

    // ---------------------------------------------------------------- Actions
    private Run parseRun(JSONObject j) {
        Run r = new Run();
        r.id = j.optLong("id"); r.workflowId = j.optLong("workflow_id"); r.name = j.optString("name");
        r.title = str(j, "display_title"); if (r.title == null) r.title = r.name;
        r.status = j.optString("status"); r.conclusion = str(j, "conclusion"); r.branch = str(j, "head_branch");
        r.event = j.optString("event"); r.sha = j.optString("head_sha"); r.htmlUrl = j.optString("html_url");
        r.createdAt = j.optString("created_at"); r.actor = o(j, "actor").optString("login");
        r.number = j.optInt("run_number"); r.attempt = j.optInt("run_attempt", 1);
        return r;
    }

    public List<Run> runs(String owner, String repo, int perPage) throws ApiException {
        JSONArray arr = ok(call("GET", rp(owner, repo) + "/actions/runs?per_page=" + perPage, null)).json().optJSONArray("workflow_runs");
        List<Run> out = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) { JSONObject j = arr.optJSONObject(i); if (j != null) out.add(parseRun(j)); }
        return out;
    }

    public List<Job> jobs(String owner, String repo, long runId) throws ApiException {
        JSONArray arr = ok(call("GET", rp(owner, repo) + "/actions/runs/" + runId + "/jobs?per_page=50", null)).json().optJSONArray("jobs");
        List<Job> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            Job job = new Job();
            job.id = j.optLong("id"); job.name = j.optString("name"); job.status = j.optString("status"); job.conclusion = str(j, "conclusion");
            job.startedAt = str(j, "started_at"); job.completedAt = str(j, "completed_at");
            JSONArray st = j.optJSONArray("steps");
            if (st != null) for (int k = 0; k < st.length(); k++) {
                JSONObject sj = st.optJSONObject(k);
                if (sj == null) continue;
                Step step = new Step();
                step.name = sj.optString("name"); step.status = sj.optString("status"); step.conclusion = str(sj, "conclusion"); step.number = sj.optInt("number");
                job.steps.add(step);
            }
            out.add(job);
        }
        return out;
    }

    public List<ArtifactInfo> artifacts(String owner, String repo, long runId) throws ApiException {
        JSONArray arr = ok(call("GET", rp(owner, repo) + "/actions/runs/" + runId + "/artifacts", null)).json().optJSONArray("artifacts");
        List<ArtifactInfo> out = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            ArtifactInfo a = new ArtifactInfo();
            a.id = j.optLong("id"); a.name = j.optString("name"); a.size = j.optLong("size_in_bytes"); a.expired = j.optBoolean("expired");
            a.url = j.optString("archive_download_url");
            out.add(a);
        }
        return out;
    }

    public void rerun(String owner, String repo, long runId, boolean failedOnly) throws ApiException {
        ok(call("POST", rp(owner, repo) + "/actions/runs/" + runId + (failedOnly ? "/rerun-failed-jobs" : "/rerun"), null));
    }

    public void cancelRun(String owner, String repo, long runId) throws ApiException { ok(call("POST", rp(owner, repo) + "/actions/runs/" + runId + "/cancel", null)); }

    public void deleteRun(String owner, String repo, long runId) throws ApiException { ok(call("DELETE", rp(owner, repo) + "/actions/runs/" + runId, null)); }

    public List<Workflow> workflows(String owner, String repo) throws ApiException {
        JSONArray arr = ok(call("GET", rp(owner, repo) + "/actions/workflows?per_page=50", null)).json().optJSONArray("workflows");
        List<Workflow> out = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            Workflow w = new Workflow();
            w.id = j.optLong("id"); w.name = j.optString("name"); w.path = j.optString("path"); w.state = j.optString("state");
            out.add(w);
        }
        return out;
    }

    public void dispatch(String owner, String repo, long workflowId, String ref) throws ApiException {
        ok(call("POST", rp(owner, repo) + "/actions/workflows/" + workflowId + "/dispatches", jsonBody(obj("ref", ref))));
    }

    public void setWorkflowEnabled(String owner, String repo, long workflowId, boolean enabled) throws ApiException {
        ok(call("PUT", rp(owner, repo) + "/actions/workflows/" + workflowId + (enabled ? "/enable" : "/disable"), null));
    }

    // ---------------------------------------------------------------- Releases
    private Release parseRelease(JSONObject j) {
        Release r = new Release();
        r.id = j.optLong("id"); r.tag = j.optString("tag_name"); r.name = str(j, "name"); r.body = str(j, "body");
        r.target = str(j, "target_commitish"); r.htmlUrl = j.optString("html_url"); r.createdAt = j.optString("created_at");
        r.publishedAt = str(j, "published_at"); r.draft = j.optBoolean("draft"); r.prerelease = j.optBoolean("prerelease");
        JSONArray as = j.optJSONArray("assets");
        if (as != null) for (int i = 0; i < as.length(); i++) {
            JSONObject aj = as.optJSONObject(i);
            if (aj != null) r.assets.add(parseAsset(aj));
        }
        return r;
    }

    private Asset parseAsset(JSONObject aj) {
        Asset a = new Asset();
        a.id = aj.optLong("id"); a.name = aj.optString("name"); a.size = aj.optLong("size"); a.downloads = aj.optInt("download_count");
        a.url = aj.optString("browser_download_url"); a.contentType = aj.optString("content_type");
        return a;
    }

    public List<Release> releases(String owner, String repo) throws ApiException {
        JSONArray arr = ok(call("GET", rp(owner, repo) + "/releases?per_page=30", null)).array();
        List<Release> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) { JSONObject j = arr.optJSONObject(i); if (j != null) out.add(parseRelease(j)); }
        return out;
    }

    public Release createRelease(String owner, String repo, String tag, String target, String name, String body,
                                 boolean draft, boolean prerelease, boolean generateNotes) throws ApiException {
        JSONObject j = obj("tag_name", tag, "name", name, "body", body, "draft", draft, "prerelease", prerelease, "generate_release_notes", generateNotes);
        if (target != null && !target.isEmpty()) try { j.put("target_commitish", target); } catch (JSONException ignored) { }
        return parseRelease(ok(call("POST", rp(owner, repo) + "/releases", jsonBody(j))).json());
    }

    public Release updateRelease(String owner, String repo, long id, JSONObject patch) throws ApiException {
        return parseRelease(ok(call("PATCH", rp(owner, repo) + "/releases/" + id, jsonBody(patch))).json());
    }

    public void deleteRelease(String owner, String repo, long id) throws ApiException { ok(call("DELETE", rp(owner, repo) + "/releases/" + id, null)); }

    public void deleteAsset(String owner, String repo, long assetId) throws ApiException { ok(call("DELETE", rp(owner, repo) + "/releases/assets/" + assetId, null)); }

    public Asset uploadAsset(String owner, String repo, long releaseId, String name, String mime, long size, StreamSource src, ProgressCb cb) throws ApiException {
        String url = "https://uploads.github.com/repos/" + enc(owner) + "/" + enc(repo) + "/releases/" + releaseId + "/assets?name=" + enc(name);
        return parseAsset(ok(call("POST", url, new RawBody(mime, size, src, cb))).json());
    }

    /** Returns {name, body} generated by GitHub. */
    public String[] generateNotes(String owner, String repo, String tag, String target, String previousTag) throws ApiException {
        JSONObject j = obj("tag_name", tag);
        try {
            if (target != null && !target.isEmpty()) j.put("target_commitish", target);
            if (previousTag != null && !previousTag.isEmpty()) j.put("previous_tag_name", previousTag);
        } catch (JSONException ignored) { }
        JSONObject r = ok(call("POST", rp(owner, repo) + "/releases/generate-notes", jsonBody(j))).json();
        return new String[]{r.optString("name"), r.optString("body")};
    }

    public List<String> tags(String owner, String repo) throws ApiException {
        JSONArray arr = ok(call("GET", rp(owner, repo) + "/tags?per_page=30", null)).array();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) { JSONObject j = arr.optJSONObject(i); if (j != null) out.add(j.optString("name")); }
        return out;
    }
}
