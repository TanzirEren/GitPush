package com.gitpush.app.data;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Models {
    private Models() {}

    public static class Account {
        public final String login, name, avatar, token, scopes, authType;
        public Account(String login, String name, String avatar, String token, String scopes, String authType) {
            this.login = login; this.name = name; this.avatar = avatar; this.token = token; this.scopes = scopes; this.authType = authType;
        }
        public String display() { return name != null && !name.isEmpty() ? name : login; }
    }

    public static class Repo {
        public long id;
        public String name, fullName, owner, description, defaultBranch, htmlUrl, cloneUrl, updatedAt, language;
        public boolean isPrivate, canPush, isAdmin;
        public int stars, sizeKb;
    }

    public static class Branch {
        public final String name, sha;
        public Branch(String name, String sha) { this.name = name; this.sha = sha; }
    }

    public static class ContentItem {
        public final String name, path, sha;
        public final boolean isDir;
        public final long size;
        public ContentItem(String name, String path, boolean isDir, long size, String sha) {
            this.name = name; this.path = path; this.isDir = isDir; this.size = size; this.sha = sha;
        }
    }

    public static class CommitInfo {
        public String sha, message, author, date, htmlUrl;
        public List<String> parents = new ArrayList<>();
        public String firstLine() {
            int i = message.indexOf('\n');
            return i < 0 ? message : message.substring(0, i);
        }
    }

    public static class CommitFile {
        public String filename, previous, status, patch;
        public int additions, deletions;
    }

    public static class CommitDetail {
        public CommitInfo info;
        public List<CommitFile> files = new ArrayList<>();
    }

    public static class CompareResult {
        public String status;
        public int ahead, behind;
        public List<CommitFile> files = new ArrayList<>();
    }

    public static class TreeInfo {
        public final String path, type, sha;
        public final long size;
        public TreeInfo(String path, String type, String sha, long size) { this.path = path; this.type = type; this.sha = sha; this.size = size; }
    }

    public static class NewEntry {
        public final String path, sha, content;
        public final boolean delete;
        public NewEntry(String path, String sha, String content, boolean delete) { this.path = path; this.sha = sha; this.content = content; this.delete = delete; }
        public static NewEntry blob(String path, String sha) { return new NewEntry(path, sha, null, false); }
        public static NewEntry text(String path, String content) { return new NewEntry(path, null, content, false); }
        public static NewEntry remove(String path) { return new NewEntry(path, null, null, true); }
    }

    public static class FileData {
        public final String sha;
        public final byte[] bytes;
        public final long size;
        public FileData(String sha, byte[] bytes, long size) { this.sha = sha; this.bytes = bytes; this.size = size; }
    }

    public static class DeviceCode {
        public String deviceCode, userCode, verificationUri;
        public int interval, expiresIn;
    }

    public static class PollResult {
        public static final int TOKEN = 0, PENDING = 1, SLOW_DOWN = 2, FAILED = 3;
        public final int kind;
        public final String value;
        public PollResult(int kind, String value) { this.kind = kind; this.value = value; }
    }

    public static class RefResult {
        public static final int FOUND = 0, EMPTY = 1, MISSING = 2;
        public final int kind;
        public final String sha;
        public RefResult(int kind, String sha) { this.kind = kind; this.sha = sha; }
    }

    public static class HistoryEntry {
        public String repoFullName = "", branch = "", targetDir = "", message = "", sha = "", url = "", error;
        public int fileCount;
        public long bytes, time;
        public boolean success;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("repo", repoFullName).put("branch", branch).put("dir", targetDir).put("msg", message)
                        .put("files", fileCount).put("bytes", bytes).put("sha", sha).put("url", url)
                        .put("time", time).put("ok", success).put("err", error == null ? "" : error);
            } catch (Exception ignored) { }
            return o;
        }

        public static HistoryEntry fromJson(JSONObject o) {
            HistoryEntry h = new HistoryEntry();
            h.repoFullName = o.optString("repo"); h.branch = o.optString("branch"); h.targetDir = o.optString("dir");
            h.message = o.optString("msg"); h.fileCount = o.optInt("files"); h.bytes = o.optLong("bytes");
            h.sha = o.optString("sha"); h.url = o.optString("url"); h.time = o.optLong("time");
            h.success = o.optBoolean("ok"); String e = o.optString("err"); h.error = e.isEmpty() ? null : e;
            return h;
        }
    }

    // ---------------------------------------------------------------- Actions
    public static class Run {
        public long id, workflowId;
        public String name, title, status, conclusion, branch, event, sha, htmlUrl, createdAt, actor;
        public int number, attempt;
        public boolean active() { return "in_progress".equals(status) || "queued".equals(status) || "waiting".equals(status) || "pending".equals(status) || "requested".equals(status); }
    }

    public static class Step {
        public String name, status, conclusion;
        public int number;
    }

    public static class Job {
        public long id;
        public String name, status, conclusion, startedAt, completedAt;
        public List<Step> steps = new ArrayList<>();
    }

    public static class ArtifactInfo {
        public long id, size;
        public String name, url;
        public boolean expired;
    }

    public static class Workflow {
        public long id;
        public String name, path, state;
    }

    // ---------------------------------------------------------------- Releases
    public static class Asset {
        public long id, size;
        public int downloads;
        public String name, url, contentType;
    }

    public static class Release {
        public long id;
        public String tag, name, body, target, htmlUrl, createdAt, publishedAt;
        public boolean draft, prerelease;
        public List<Asset> assets = new ArrayList<>();
        public String title() { return name != null && !name.isEmpty() ? name : tag; }
    }
}
