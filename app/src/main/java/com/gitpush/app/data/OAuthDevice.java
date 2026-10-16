package com.gitpush.app.data;

import com.gitpush.app.data.Models.DeviceCode;
import com.gitpush.app.data.Models.PollResult;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

/** GitHub OAuth Device Flow – needs only a public Client ID (no client secret in the app). */
public final class OAuthDevice {
    private OAuthDevice() {}

    private static String post(String url, FormBody body) throws IOException {
        Request req = new Request.Builder().url(url).header("Accept", "application/json").post(body).build();
        try (Response r = GitHubApi.CLIENT.newCall(req).execute()) {
            return r.body() != null ? r.body().string() : "";
        }
    }

    public static DeviceCode start(String clientId) throws ApiException {
        try {
            JSONObject j = new JSONObject(post("https://github.com/login/device/code",
                    new FormBody.Builder().add("client_id", clientId).add("scope", "repo workflow delete_repo read:org user").build()));
            if (j.has("error")) {
                String desc = j.optString("error_description", j.optString("error"));
                throw new ApiException(400, desc + " (Enable 'Device Flow' in your OAuth app settings)");
            }
            DeviceCode d = new DeviceCode();
            d.deviceCode = j.optString("device_code"); d.userCode = j.optString("user_code");
            d.verificationUri = j.optString("verification_uri"); d.interval = j.optInt("interval", 5); d.expiresIn = j.optInt("expires_in", 900);
            return d;
        } catch (IOException e) {
            throw new ApiException(-1, "Network error: " + e.getMessage());
        } catch (org.json.JSONException e) {
            throw new ApiException(-3, "Unexpected response from GitHub");
        }
    }

    public static PollResult poll(String clientId, String deviceCode) {
        try {
            JSONObject j = new JSONObject(post("https://github.com/login/oauth/access_token",
                    new FormBody.Builder().add("client_id", clientId).add("device_code", deviceCode)
                            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build()));
            if (j.has("access_token")) return new PollResult(PollResult.TOKEN, j.optString("access_token"));
            String err = j.optString("error");
            switch (err) {
                case "authorization_pending": return new PollResult(PollResult.PENDING, null);
                case "slow_down": return new PollResult(PollResult.SLOW_DOWN, null);
                case "expired_token": return new PollResult(PollResult.FAILED, "The code expired. Start again.");
                case "access_denied": return new PollResult(PollResult.FAILED, "Access was denied.");
                default: return new PollResult(PollResult.FAILED, err.isEmpty() ? "Unknown error" : err);
            }
        } catch (Exception e) {
            return new PollResult(PollResult.FAILED, "Network error: " + e.getMessage());
        }
    }
}
