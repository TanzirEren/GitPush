package com.gitpush.app.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Minimal client for Anthropic and OpenAI-compatible chat APIs (user supplies their own key). */
public final class AiClient {
    private AiClient() {}

    /** {label, type, baseUrl, defaultModel} */
    public static final String[][] PRESETS = {
            {"Claude (Anthropic)", "anthropic", "https://api.anthropic.com", "claude-sonnet-5-5"},
            {"OpenAI", "openai", "https://api.openai.com/v1", "gpt-4o-mini"},
            {"Google Gemini", "openai", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"},
            {"Groq", "openai", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"},
            {"OpenRouter", "openai", "https://openrouter.ai/api/v1", "openrouter/auto"},
            {"Custom (OpenAI-compatible)", "openai", "", ""}
    };

    private static final OkHttpClient CLIENT = GitHubApi.CLIENT.newBuilder().readTimeout(180, TimeUnit.SECONDS).build();

    public static boolean configured(Store s) { return !s.aiKey().trim().isEmpty() && !s.aiModel().trim().isEmpty() && !s.aiBase().trim().isEmpty(); }

    public static String complete(Store s, String system, String user, int maxTokens) throws ApiException {
        String base = s.aiBase().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        boolean anthropic = s.aiProvider().equals("anthropic");
        try {
            JSONObject body = new JSONObject().put("model", s.aiModel().trim());
            Request.Builder rb = new Request.Builder();
            if (anthropic) {
                body.put("max_tokens", maxTokens).put("system", system)
                        .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", user)));
                rb.url(base + "/v1/messages").header("x-api-key", s.aiKey().trim()).header("anthropic-version", "2023-06-01");
            } else {
                body.put("max_tokens", maxTokens).put("temperature", 0.4)
                        .put("messages", new JSONArray().put(new JSONObject().put("role", "system").put("content", system))
                                .put(new JSONObject().put("role", "user").put("content", user)));
                rb.url(base + "/chat/completions").header("Authorization", "Bearer " + s.aiKey().trim());
            }
            Request req = rb.post(RequestBody.create(body.toString(), GitHubApi.JSON)).build();
            try (Response r = CLIENT.newCall(req).execute()) {
                String text = r.body() != null ? r.body().string() : "";
                if (!r.isSuccessful()) {
                    String m = "";
                    try { m = new JSONObject(text).optJSONObject("error").optString("message"); } catch (Exception ignored) { }
                    if (m == null || m.isEmpty()) m = text.length() > 200 ? text.substring(0, 200) : text;
                    throw new ApiException(r.code(), "AI error " + r.code() + ": " + m);
                }
                JSONObject j = new JSONObject(text);
                if (anthropic) {
                    JSONArray c = j.optJSONArray("content");
                    StringBuilder sb = new StringBuilder();
                    if (c != null) for (int i = 0; i < c.length(); i++) sb.append(c.getJSONObject(i).optString("text"));
                    if (sb.length() == 0) throw new ApiException(-3, "The AI returned an empty answer.");
                    return sb.toString().trim();
                }
                JSONArray ch = j.optJSONArray("choices");
                String out = ch == null || ch.length() == 0 ? "" : ch.getJSONObject(0).getJSONObject("message").optString("content");
                if (out.isEmpty()) throw new ApiException(-3, "The AI returned an empty answer.");
                return out.trim();
            }
        } catch (IOException e) {
            throw new ApiException(-1, "Network error: " + (e.getMessage() == null ? "connection failed" : e.getMessage()));
        } catch (org.json.JSONException e) {
            throw new ApiException(-3, "Unexpected AI response format");
        }
    }

    /** Removes a wrapping ```markdown fence if the model added one. */
    public static String stripFence(String t) {
        String s = t.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }
}
