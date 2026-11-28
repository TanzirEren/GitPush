package com.gitpush.app.data;

import android.util.Base64;

import com.gitpush.app.util.SealedBox;

import org.json.JSONArray;
import org.json.JSONObject;

/** GitHub Codespaces REST API (needs the "codespace" token scope). All calls block: use on a background thread. */
public final class CodespacesApi {
    private final GitHubApi api;
    public CodespacesApi(GitHubApi api) { this.api = api; }

    private static String e(String s) { return GitHubApi.enc2(s); }
    private static String rp(String owner, String repo) { return "/repos/" + e(owner) + "/" + e(repo); }

    // ------------------------------------------------------------------ codespaces
    public JSONArray list() throws ApiException { return api.getAll("/user/codespaces", "codespaces", 3); }
    public JSONArray listForRepo(String owner, String repo) throws ApiException { return api.getAll("/repos/" + e(owner) + "/" + e(repo) + "/codespaces", "codespaces", 2); }
    public JSONObject get(String name) throws ApiException { return api.getObj("/user/codespaces/" + e(name)); }
    public JSONObject start(String name) throws ApiException { return api.send("POST", "/user/codespaces/" + e(name) + "/start", null); }
    public JSONObject stop(String name) throws ApiException { return api.send("POST", "/user/codespaces/" + e(name) + "/stop", null); }
    public void delete(String name) throws ApiException { api.send("DELETE", "/user/codespaces/" + e(name), null); }
    public JSONObject update(String name, JSONObject patch) throws ApiException { return api.send("PATCH", "/user/codespaces/" + e(name), patch); }
    public JSONArray machinesOf(String name) throws ApiException { return api.getObj("/user/codespaces/" + e(name) + "/machines").optJSONArray("machines"); }

    public JSONObject create(String owner, String repo, JSONObject body) throws ApiException { return api.send("POST", rp(owner, repo) + "/codespaces", body); }
    public JSONObject createFromPull(String owner, String repo, int pr, JSONObject body) throws ApiException { return api.send("POST", rp(owner, repo) + "/pulls/" + pr + "/codespaces", body); }

    // ------------------------------------------------------------------ create helpers
    public JSONArray repoMachines(String owner, String repo, String ref) throws ApiException {
        JSONObject j = api.getObj(rp(owner, repo) + "/codespaces/machines" + (ref == null || ref.isEmpty() ? "" : "?ref=" + e(ref)));
        JSONArray m = j.optJSONArray("machines");
        return m == null ? new JSONArray() : m;
    }
    public JSONArray devcontainers(String owner, String repo) throws ApiException {
        JSONArray a = api.getObj(rp(owner, repo) + "/codespaces/devcontainers").optJSONArray("devcontainers");
        return a == null ? new JSONArray() : a;
    }
    public JSONObject newDefaults(String owner, String repo, String ref) throws ApiException {
        return api.getObj(rp(owner, repo) + "/codespaces/new" + (ref == null || ref.isEmpty() ? "" : "?ref=" + e(ref)));
    }
    /** {accepted: bool, download_url?} – accepted=false means extra permissions must be approved on github.com first. */
    public JSONObject permissionsCheck(String owner, String repo, String ref, String devcontainerPath) throws ApiException {
        return api.getObj(rp(owner, repo) + "/codespaces/permissions_check?ref=" + e(ref) + "&devcontainer_path=" + e(devcontainerPath == null ? "" : devcontainerPath));
    }

    // ------------------------------------------------------------------ export / publish
    public JSONObject export(String name) throws ApiException { return api.send("POST", "/user/codespaces/" + e(name) + "/exports", null); }
    public JSONObject exportLatest(String name) throws ApiException { return api.getObj("/user/codespaces/" + e(name) + "/exports/latest"); }
    public JSONObject publish(String name, String repoName, boolean isPrivate) throws ApiException {
        return api.send("POST", "/user/codespaces/" + e(name) + "/publish", GitHubApi.jo("name", repoName, "private", isPrivate));
    }

    // ------------------------------------------------------------------ secrets (user-level and repo-level)
    private String sbase(String owner, String repo) { return owner == null || owner.isEmpty() ? "/user/codespaces/secrets" : rp(owner, repo) + "/codespaces/secrets"; }

    public JSONArray secrets(String owner, String repo) throws ApiException { return api.getAll(sbase(owner, repo), "secrets", 3); }

    public JSONArray secretRepos(String name) throws ApiException {
        JSONArray a = api.getObj("/user/codespaces/secrets/" + e(name) + "/repositories").optJSONArray("repositories");
        return a == null ? new JSONArray() : a;
    }

    /** [selectedRepoIds] only applies to user secrets (null = keep unchanged / none). Values are sealed on the device. */
    public void putSecret(String owner, String repo, String name, String value, long[] selectedRepoIds) throws Exception {
        String base = sbase(owner, repo);
        JSONObject pk = api.getObj(base + "/public-key");
        byte[] key = Base64.decode(pk.optString("key"), Base64.DEFAULT);
        byte[] box = SealedBox.seal(value.getBytes("UTF-8"), key);
        JSONObject body = GitHubApi.jo("encrypted_value", Base64.encodeToString(box, Base64.NO_WRAP), "key_id", pk.optString("key_id"));
        if (owner == null || owner.isEmpty()) {
            JSONArray ids = new JSONArray();
            if (selectedRepoIds != null) for (long id : selectedRepoIds) ids.put(String.valueOf(id));
            body.put("selected_repository_ids", ids);
        }
        api.send("PUT", base + "/" + e(name), body);
    }

    /** Replaces the list of repositories that may read a user secret. */
    public void setSecretRepos(String name, long[] ids) throws ApiException {
        JSONArray a = new JSONArray();
        for (long id : ids) a.put(id);
        api.send("PUT", "/user/codespaces/secrets/" + e(name) + "/repositories", GitHubApi.jo("selected_repository_ids", a));
    }

    public void deleteSecret(String owner, String repo, String name) throws ApiException { api.send("DELETE", sbase(owner, repo) + "/" + e(name), null); }

    // ------------------------------------------------------------------ organizations
    public JSONArray orgs() throws ApiException { return api.getAll("/user/orgs", null, 1); }
    public JSONArray orgCodespaces(String org) throws ApiException { return api.getAll("/orgs/" + e(org) + "/codespaces", "codespaces", 2); }
    public void orgStop(String org, String user, String name) throws ApiException { api.send("POST", "/orgs/" + e(org) + "/members/" + e(user) + "/codespaces/" + e(name) + "/stop", null); }
    public void orgDelete(String org, String user, String name) throws ApiException { api.send("DELETE", "/orgs/" + e(org) + "/members/" + e(user) + "/codespaces/" + e(name), null); }
}
