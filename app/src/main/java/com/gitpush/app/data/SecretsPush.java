package com.gitpush.app.data;

import android.util.Base64;

import com.gitpush.app.util.SealedBox;

import org.json.JSONObject;

import java.util.Map;

/** Encrypts values on the device (libsodium sealed box) and writes them as repository or environment secrets. */
public final class SecretsPush {
    private SecretsPush() {}

    /** [environment] null/empty = repository secrets. Returns the number written. */
    public static int push(GitHubApi api, String owner, String repo, String environment, Map<String, String> values) throws Exception {
        String base;
        if (environment == null || environment.isEmpty()) base = GitHubApi.repoPath(owner, repo) + "/actions/secrets";
        else {
            long id = api.getObj(GitHubApi.repoPath(owner, repo)).optLong("id");
            base = "/repositories/" + id + "/environments/" + GitHubApi.enc2(environment) + "/secrets";
        }
        JSONObject pk = api.getObj(base + "/public-key");
        byte[] key = Base64.decode(pk.optString("key"), Base64.DEFAULT);
        int n = 0;
        for (Map.Entry<String, String> e : values.entrySet()) {
            byte[] box = SealedBox.seal(e.getValue().getBytes("UTF-8"), key);
            api.send("PUT", base + "/" + GitHubApi.enc2(e.getKey()), GitHubApi.jo("encrypted_value", Base64.encodeToString(box, Base64.NO_WRAP), "key_id", pk.optString("key_id")));
            n++;
        }
        return n;
    }
}
