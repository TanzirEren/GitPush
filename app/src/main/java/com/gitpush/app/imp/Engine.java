package com.gitpush.app.imp;

import android.content.Context;

import com.gitpush.app.GitPushApp;
import com.gitpush.app.KeepAliveService;
import com.gitpush.app.Notifier;
import com.gitpush.app.data.ApiException;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Asset;
import com.gitpush.app.data.Models.NewEntry;
import com.gitpush.app.data.Models.RefResult;
import com.gitpush.app.data.Models.Release;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.data.Models.TreeInfo;
import com.gitpush.app.data.Models.Workflow;
import com.gitpush.app.data.Pusher;
import com.gitpush.app.data.Pusher.PushSession;
import com.gitpush.app.data.SecretsPush;
import com.gitpush.app.data.Stager.StagedFile;
import com.gitpush.app.data.ZipTools;
import com.gitpush.app.util.PathUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Runs an import plan in the background (own thread, foreground-service notification, resumable steps). */
public final class Engine {
    private Engine() {}

    public static final String S_REPO = "Repository", S_FILES = "Files", S_TOPICS = "Topics", S_VARS = "Variables", S_SECRETS = "Secrets",
            S_LABELS = "Labels", S_ISSUES = "Issues", S_RELEASES = "Releases", S_WF = "Workflows";

    public static boolean isWorkflowPath(String p) {
        return Tree.isWorkflowPath(p);
    }

    public static Job start(final GitPushApp app, Plan p, String token) {
        final Job j = new Job(p, token);
        j.steps.put(S_REPO, Job.PENDING);
        j.steps.put(S_FILES, Job.PENDING);
        if (!p.existing && !p.topics.isEmpty()) j.steps.put(S_TOPICS, Job.PENDING);
        if (p.copyVars && !p.varValues.isEmpty()) j.steps.put(S_VARS, Job.PENDING);
        boolean anySecret = false;
        for (String v : p.secrets.values()) if (v != null && !v.isEmpty()) anySecret = true;
        if (anySecret) j.steps.put(S_SECRETS, Job.PENDING);
        if (p.labels || p.issues) j.steps.put(S_LABELS, Job.PENDING);
        if (p.issues) j.steps.put(S_ISSUES, Job.PENDING);
        if (p.releases) j.steps.put(S_RELEASES, Job.PENDING);
        if (!p.workflowBlobs.isEmpty()) j.steps.put(S_WF, Job.PENDING);
        Jobs.add(j);
        launch(app, j);
        return j;
    }

    public static void retry(GitPushApp app, Job j) {
        if (j.running()) return;
        synchronized (j.steps) { for (Map.Entry<String, Integer> e : j.steps.entrySet()) if (e.getValue() == Job.FAILED || e.getValue() == Job.RUNNING) e.setValue(Job.PENDING); }
        j.log("↻ Retrying failed steps…");
        launch(app, j);
    }

    public static void cancel(Job j) {
        j.cancel = true;
        GitHubApi a = j.api; if (a != null) a.abort();
        PushSession s = j.push; if (s != null) s.cancel();
    }

    private static void launch(final GitPushApp app, final Job j) {
        j.status = "queued"; j.endedAt = 0;
        j.future = app.io.submit(() -> run(app, j));
    }

    private static boolean cancelled(Job j, Exception e) {
        return j.cancel || (e instanceof ApiException && ((ApiException) e).code == -2) || e instanceof InterruptedException;
    }

    private static void prog(Job j, String phase, float f) {
        j.phase = phase; j.progress = f;
        KeepAliveService.update(GitPushApp.get(), phase, f < 0 ? -1 : Math.min(100, (int) (f * 100)));
    }

    private static void run(final GitPushApp app, final Job j) {
        j.status = "running"; j.cancel = false;
        KeepAliveService.begin(app, "Importing " + j.plan.srcName);
        j.api = new GitHubApi(j.token);
        boolean fatal = false;
        try {
            j.log("Started: " + j.plan.title());
            for (String s : new ArrayList<>(j.steps.keySet())) {
                int st = j.steps.get(s);
                if (st == Job.DONE || st == Job.SKIPPED) continue;
                if (j.cancel) break;
                j.steps.put(s, Job.RUNNING);
                prog(j, s, -1f);
                try {
                    runStep(app, j, s);
                    if (j.steps.get(s) == Job.RUNNING) j.steps.put(s, Job.DONE);
                    j.log("✔ " + s + " done");
                } catch (Exception e) {
                    if (cancelled(j, e)) { j.steps.put(s, Job.PENDING); break; }
                    String m = e.getMessage() != null ? e.getMessage() : e.toString();
                    j.steps.put(s, Job.FAILED); j.stepNote.put(s, m);
                    j.log("✘ " + s + ": " + m);
                    if (s.equals(S_REPO) || s.equals(S_FILES)) { fatal = true; break; }
                }
            }
        } finally {
            j.endedAt = System.currentTimeMillis();
            if (j.cancel) j.status = "cancelled";
            else if (fatal) j.status = "failed";
            else if (j.stepCount(Job.FAILED) > 0) j.status = "partial";
            else j.status = "done";
            j.progress = j.status.equals("done") ? 1f : -1f;
            j.phase = j.status.equals("done") ? "Finished" : j.status;
            j.log("Finished: " + j.status + " (" + j.summary() + ")");
            Jobs.remember(app.state.store.prefs, j);
            String repo = j.target == null ? j.plan.name : j.target.fullName;
            Notifier.show(app, j.status.equals("done") ? "Import complete" : j.status.equals("partial") ? "Import finished with problems" : j.status.equals("cancelled") ? "Import cancelled" : "Import failed",
                    repo + " • " + j.summary());
            if ((j.status.equals("done") || j.status.equals("partial")) && j.plan.syncMin > 0 && j.target != null && j.steps.get(S_FILES) != null && j.steps.get(S_FILES) == Job.DONE)
                SyncLoop.addFrom(app, j);
            KeepAliveService.end(app);
        }
    }

    private static void runStep(GitPushApp app, Job j, String s) throws Exception {
        GitHubApi api = j.api;
        switch (s) {
            case S_REPO: stepRepo(j, api); break;
            case S_FILES: stepFiles(app, j, api); break;
            case S_TOPICS: stepTopics(j, api); break;
            case S_VARS: stepVars(j, api); break;
            case S_SECRETS: stepSecrets(j, api); break;
            case S_LABELS: stepLabels(j, api); break;
            case S_ISSUES: stepIssues(j, api); break;
            case S_RELEASES: stepReleases(app, j, api); break;
            case S_WF: stepWorkflows(app, j, api); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------ repository
    private static void stepRepo(Job j, GitHubApi api) throws Exception {
        Plan p = j.plan;
        if (j.target == null) {
            if (p.existing) {
                j.target = api.repo(p.tOwner, p.tName);
                if (!j.target.canPush) throw new Exception("You do not have push access to " + j.target.fullName);
                j.log("Using existing repository " + j.target.fullName);
            } else {
                String desc = p.description == null ? "" : p.description;
                if (p.note) desc = (desc.isEmpty() ? "" : desc + " ") + "(imported from " + p.srcOwner + "/" + p.srcName + ")";
                if (desc.length() > 340) desc = desc.substring(0, 340);
                JSONObject body = GitHubApi.jo("name", p.name, "description", desc, "private", p.priv, "auto_init", false);
                if (p.homepage != null && !p.homepage.isEmpty()) body.put("homepage", p.homepage);
                JSONObject r = api.send("POST", "/user/repos", body);
                j.target = api.repo(r.getJSONObject("owner").getString("login"), r.getString("name"));
                j.log("Created " + (p.priv ? "private" : "public") + " repository " + j.target.fullName);
            }
        }
        j.branch = j.target.defaultBranch;
    }

    // ------------------------------------------------------------------ files (+ history replay)
    private static final class RF { String path, status, previous, sha; }
    private static final class RC {
        String sha, parent = "", message, aName, aEmail, aDate, cName, cEmail, cDate;
        final List<RF> files = new ArrayList<>();
    }

    private static boolean selected(Plan p, String path) { return p.allFiles || Tree.inPrefixes(p.prefixes, path); }

    private static RC fetchRC(GitHubApi api, Plan p, String sha) throws Exception {
        String base = GitHubApi.repoPath(p.srcOwner, p.srcName) + "/commits/" + sha + "?per_page=100&page=";
        RC rc = new RC(); rc.sha = sha;
        for (int page = 1; page <= 4; page++) {
            JSONObject o = api.getObj(base + page);
            if (page == 1) {
                JSONArray ps = o.optJSONArray("parents");
                if (ps != null && ps.length() > 0) rc.parent = ps.getJSONObject(0).optString("sha");
                JSONObject c = o.getJSONObject("commit");
                rc.message = c.optString("message");
                JSONObject a = c.optJSONObject("author"), cm = c.optJSONObject("committer");
                if (a != null) { rc.aName = a.optString("name"); rc.aEmail = a.optString("email"); rc.aDate = a.optString("date"); }
                if (cm != null) { rc.cName = cm.optString("name"); rc.cEmail = cm.optString("email"); rc.cDate = cm.optString("date"); }
            }
            JSONArray fs = o.optJSONArray("files");
            if (fs == null) break;
            for (int i = 0; i < fs.length(); i++) {
                JSONObject f = fs.getJSONObject(i);
                RF r = new RF();
                r.path = f.optString("filename"); r.status = f.optString("status"); r.sha = f.optString("sha");
                r.previous = f.isNull("previous_filename") ? null : f.optString("previous_filename");
                rc.files.add(r);
            }
            if (fs.length() < 100) break;
        }
        return rc;
    }

    private static void stepFiles(GitPushApp app, Job j, GitHubApi api) throws Exception {
        Plan p = j.plan;
        List<RC> chain = new ArrayList<>();
        String base = p.srcHead;
        boolean rootSnap = false;
        if (p.replay > 0) {
            prog(j, "Reading history…", -1f);
            String cur = p.srcHead;
            for (int i = 0; i < p.replay && cur != null && !cur.isEmpty(); i++) {
                if (j.cancel) throw new ApiException(-2, "Cancelled");
                RC rc = fetchRC(api, p, cur);
                chain.add(rc);
                prog(j, "Reading history " + (i + 1) + "/" + p.replay, (float) (i + 1) / p.replay);
                cur = rc.parent;
            }
            Collections.reverse(chain);
            RC first = chain.get(0);
            if (first.parent.isEmpty()) { base = first.sha; rootSnap = true; chain.remove(0); }
            else base = first.parent;
            j.log("History: " + chain.size() + " commit(s) will be replayed" + (rootSnap ? " after the root commit" : ""));
        }
        if (!j.snapshotDone) { snapshot(app, j, api, base, rootSnap); j.snapshotDone = true; }
        if (!chain.isEmpty()) replay(j, api, chain);
    }

    private static void snapshot(GitPushApp app, Job j, GitHubApi api, String base, boolean rootSnap) throws Exception {
        Plan p = j.plan;
        Repo t = j.target;
        File zip = new File(app.getCacheDir(), "import_" + j.id + ".zip");
        try {
            prog(j, "Downloading source…", -1f);
            try (FileOutputStream fo = new FileOutputStream(zip)) {
                api.stream(GitHubApi.repoPath(p.srcOwner, p.srcName) + "/zipball/" + GitHubApi.enc2(base), fo,
                        (d, tot) -> prog(j, "Downloading " + PathUtils.formatSize(d) + (tot > 0 ? " / " + PathUtils.formatSize(tot) : ""), tot > 0 ? (float) d / tot : -1f));
            }
            prog(j, "Reading archive…", -1f);
            List<ZipTools.Entry> entries = ZipTools.list(zip);
            long max = p.maxFileMb * 1024L * 1024L;
            List<String[]> map = new ArrayList<>();
            int big = 0;
            for (ZipTools.Entry e : entries) {
                int i = e.path.indexOf('/');
                if (i < 0) continue;
                String rel = e.path.substring(i + 1);
                if (rel.isEmpty() || isWorkflowPath(rel)) continue;
                if (!selected(p, rel)) continue;
                if (e.size > max) { big++; j.log("Skipped (over " + p.maxFileMb + " MB): " + rel); continue; }
                map.add(new String[]{e.raw, rel});
            }
            if (big > 0) j.log(big + " large file(s) skipped");
            if (map.isEmpty()) { j.log("No regular files selected in the snapshot"); return; }
            prog(j, "Extracting " + map.size() + " files…", -1f);
            List<StagedFile> files = ZipTools.extract(app, zip, map, (d, tot) -> prog(j, "Extracting " + d + "/" + tot, (float) d / Math.max(1, tot)));
            String msg = rootSnap ? firstLine(j, base) : "Import from " + p.srcOwner + "/" + p.srcName + (p.srcRef.isEmpty() ? "" : " (" + p.srcRef + ")");
            if (p.replay > 0 && !rootSnap) msg = "Import snapshot before replayed history";
            PushSession s = new PushSession(t, j.branch, p.tDir, files, msg, "Imported with GitPush from https://github.com/" + p.srcOwner + "/" + p.srcName + " @ " + base.substring(0, Math.min(7, base.length())));
            for (StagedFile f : files) { String m = p.modes.get(f.path); if (m != null) s.modes.put(PathUtils.join(p.tDir, f.path), m); }
            runPush(app, j, api, s, "Uploading");
            j.log("Pushed " + files.size() + " file(s) (" + PathUtils.formatSize(s.totalBytes()) + ")" + (s.noChanges ? " – nothing new" : ""));
        } finally { //noinspection ResultOfMethodCallIgnored
            zip.delete();
        }
    }

    private static String firstLine(Job j, String sha) { return "Import root commit " + sha.substring(0, Math.min(7, sha.length())); }

    /** Runs the Pusher on this thread while a helper answers conflict questions and mirrors progress. */
    private static void runPush(final GitPushApp app, final Job j, GitHubApi api, final PushSession s, final String label) throws Exception {
        j.push = s;
        final boolean[] stop = {false};
        Thread w = new Thread(() -> {
            while (!stop[0]) {
                if (s.conflictChanged != null) s.resolveConflict(j.plan.conflict == 1 ? Pusher.SKIP : Pusher.REPLACE);
                prog(j, label + "  " + s.doneCount() + "/" + s.total() + "  " + s.phase, s.progress());
                try { Thread.sleep(400); } catch (InterruptedException e) { return; }
            }
        }, "import-watch");
        w.setDaemon(true); w.start();
        try { Pusher.run(app, api, s); } finally { stop[0] = true; w.interrupt(); j.push = null; }
        if (s.cancelled) throw new ApiException(-2, "Cancelled");
        if (s.error != null) throw new Exception(s.error);
    }

    private static void replay(Job j, GitHubApi api, List<RC> chain) throws Exception {
        Plan p = j.plan;
        Repo t = j.target;
        long max = p.maxFileMb * 1024L * 1024L;
        RefResult ref = api.ref(t.owner, t.name, j.branch);
        if (ref.kind != RefResult.FOUND) throw new Exception("Target branch not found for history replay");
        String curCommit = ref.sha;
        String curTree = api.commitTree(t.owner, t.name, curCommit);
        Set<String> paths = new HashSet<>();
        for (TreeInfo ti : api.treeRecursive(t.owner, t.name, curTree)) if (ti.type.equals("blob")) paths.add(ti.path);
        String repoP = GitHubApi.repoPath(t.owner, t.name);
        for (int k = j.replayDone; k < chain.size(); k++) {
            if (j.cancel) throw new ApiException(-2, "Cancelled");
            prog(j, "Replaying commit " + (k + 1) + "/" + chain.size(), (float) (k + 1) / chain.size());
            RC rc = chain.get(k);
            List<NewEntry> es = new ArrayList<>();
            List<String> addPaths = new ArrayList<>(), delPaths = new ArrayList<>();
            for (RF f : rc.files) {
                if (isWorkflowPath(f.path) && !(f.previous != null && !isWorkflowPath(f.previous))) continue;
                String tp = PathUtils.join(p.tDir, f.path);
                if (f.status.equals("removed")) {
                    if (selected(p, f.path) && paths.contains(tp)) { es.add(NewEntry.remove(tp)); delPaths.add(tp); }
                    continue;
                }
                if (f.previous != null && selected(p, f.previous)) {
                    String tpp = PathUtils.join(p.tDir, f.previous);
                    if (paths.contains(tpp)) { es.add(NewEntry.remove(tpp)); delPaths.add(tpp); }
                }
                if (!selected(p, f.path) || f.sha == null || f.sha.isEmpty()) continue;
                byte[] b = api.blobBytes(p.srcOwner, p.srcName, f.sha);
                if (b.length > max) { j.log("Skipped (too large): " + f.path); continue; }
                final byte[] fb = b;
                String sha = api.createBlob(t.owner, t.name, () -> new ByteArrayInputStream(fb));
                es.add(NewEntry.blob(tp, sha).withMode(p.modes.get(f.path)));
                addPaths.add(tp);
            }
            if (es.isEmpty()) { j.replayDone = k + 1; continue; }
            // deletions first so a rename to a case-variant cannot clash
            String tree = curTree;
            for (int i = 0; i < es.size(); i += 100) tree = api.createTree(t.owner, t.name, tree, es.subList(i, Math.min(es.size(), i + 100)));
            JSONObject body = new JSONObject();
            String trailer = "\n\nImported-from: " + p.srcOwner + "/" + p.srcName + "@" + rc.sha.substring(0, 7);
            body.put("message", (rc.message == null ? "" : rc.message) + trailer).put("tree", tree).put("parents", new JSONArray().put(curCommit));
            if (p.keepAuthors && rc.aEmail != null && !rc.aEmail.isEmpty()) {
                body.put("author", new JSONObject().put("name", rc.aName).put("email", rc.aEmail).put("date", rc.aDate));
                if (rc.cEmail != null && !rc.cEmail.isEmpty()) body.put("committer", new JSONObject().put("name", rc.cName).put("email", rc.cEmail).put("date", rc.cDate));
            }
            String sha = api.send("POST", repoP + "/git/commits", body).optString("sha");
            api.updateRef(t.owner, t.name, j.branch, sha, false);
            paths.removeAll(delPaths); paths.addAll(addPaths);
            curCommit = sha; curTree = tree;
            j.replayDone = k + 1;
        }
        j.log("Replayed " + chain.size() + " commit(s)" + (p.keepAuthors ? " with original authors and dates" : ""));
    }

    // ------------------------------------------------------------------ topics / variables / secrets
    private static void stepTopics(Job j, GitHubApi api) throws Exception {
        List<String> clean = new ArrayList<>();
        for (String t : j.plan.topics) {
            String c = t.toLowerCase().replaceAll("[^a-z0-9-]", "-");
            if (c.length() > 50) c = c.substring(0, 50);
            if (!c.isEmpty() && !clean.contains(c) && clean.size() < 20) clean.add(c);
        }
        api.setTopics(j.target.owner, j.target.name, clean);
        j.log("Topics: " + clean.size());
    }

    private static void stepVars(Job j, GitHubApi api) throws Exception {
        int n = 0;
        for (Map.Entry<String, String> e : j.plan.varValues.entrySet()) {
            try { api.createVariable(j.target.owner, j.target.name, e.getKey(), e.getValue()); }
            catch (ApiException ex) { api.updateVariable(j.target.owner, j.target.name, e.getKey(), e.getValue()); }
            n++;
        }
        j.log("Variables copied: " + n);
    }

    private static void stepSecrets(Job j, GitHubApi api) throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : j.plan.secrets.entrySet()) if (e.getValue() != null && !e.getValue().isEmpty()) m.put(e.getKey(), e.getValue());
        int n = SecretsPush.push(api, j.target.owner, j.target.name, null, m);
        j.log("Secrets written (encrypted on device): " + n);
    }

    // ------------------------------------------------------------------ labels / milestones / issues
    private static void pace(Job j, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { if (j.cancel) throw new ApiException(-2, "Cancelled"); Thread.sleep(100); }
    }

    private static void stepLabels(Job j, GitHubApi api) throws Exception {
        Plan p = j.plan; Repo t = j.target;
        String sp = GitHubApi.repoPath(p.srcOwner, p.srcName), tp = GitHubApi.repoPath(t.owner, t.name);
        int n = 0;
        if (p.labels) {
            JSONArray ls = api.getAll(sp + "/labels", null, 5);
            for (int i = 0; i < ls.length(); i++) {
                JSONObject l = ls.getJSONObject(i);
                String name = l.optString("name");
                JSONObject body = GitHubApi.jo("name", name, "color", l.optString("color", "ededed"), "description", l.isNull("description") ? "" : l.optString("description"));
                try { api.send("POST", tp + "/labels", body); n++; }
                catch (ApiException e) {
                    if (e.code != 422) throw e;
                    try { api.send("PATCH", tp + "/labels/" + GitHubApi.enc2(name), body); n++; } catch (ApiException ignored) { }
                }
            }
            j.log("Labels copied: " + n);
        }
        if (p.issues) {
            JSONArray ms = api.getAll(sp + "/milestones?state=all", null, 3);
            for (int i = 0; i < ms.length(); i++) {
                JSONObject m = ms.getJSONObject(i);
                if (j.milestoneMap.containsKey(m.optLong("number"))) continue;
                JSONObject body = GitHubApi.jo("title", m.optString("title"), "state", m.optString("state", "open"), "description", m.isNull("description") ? "" : m.optString("description"));
                if (!m.isNull("due_on")) body.put("due_on", m.optString("due_on"));
                try { JSONObject r = api.send("POST", tp + "/milestones", body); j.milestoneMap.put(m.optLong("number"), r.optInt("number")); } catch (ApiException e) { if (e.code != 422) throw e; }
            }
            j.log("Milestones copied: " + j.milestoneMap.size());
        }
    }

    private static final Pattern MENTION = Pattern.compile("(?<![\\w`@])@([A-Za-z0-9][A-Za-z0-9-]*)");

    /** Stops imported text from pinging real people (adds an invisible character after @). */
    static String shield(String s) { return s == null ? "" : MENTION.matcher(s).replaceAll("@\u200B$1"); }

    private static void stepIssues(Job j, GitHubApi api) throws Exception {
        Plan p = j.plan; Repo t = j.target;
        String sp = GitHubApi.repoPath(p.srcOwner, p.srcName), tp = GitHubApi.repoPath(t.owner, t.name);
        String st = p.issueState == 0 ? "open" : "all";
        int created = 0, seen = 0;
        outer:
        for (int page = 1; page <= 20; page++) {
            JSONArray arr = api.getArr(sp + "/issues?state=" + st + "&sort=created&direction=asc&per_page=100&page=" + page);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject is = arr.getJSONObject(i);
                if (is.has("pull_request")) continue;
                if (seen >= p.issueLimit) break outer;
                seen++;
                long num = is.optLong("number");
                if (j.doneIssues.contains(num)) continue;
                if (j.cancel) throw new ApiException(-2, "Cancelled");
                prog(j, "Issue #" + num + " (" + seen + "/" + p.issueLimit + ")", Math.min(1f, (float) seen / p.issueLimit));
                String who = is.optJSONObject("user") == null ? "unknown" : is.getJSONObject("user").optString("login");
                String head = "> Imported from " + p.srcOwner + "/" + p.srcName + "#" + num + " · opened by `" + who + "` on " + is.optString("created_at") + "\n\n";
                JSONObject body = new JSONObject().put("title", is.optString("title")).put("body", head + shield(is.isNull("body") ? "" : is.optString("body")));
                JSONArray lbl = new JSONArray();
                JSONArray ls = is.optJSONArray("labels");
                if (ls != null) for (int k = 0; k < ls.length(); k++) { JSONObject l = ls.optJSONObject(k); if (l != null) lbl.put(l.optString("name")); }
                if (lbl.length() > 0) body.put("labels", lbl);
                JSONObject ms = is.optJSONObject("milestone");
                if (ms != null && j.milestoneMap.containsKey(ms.optLong("number"))) body.put("milestone", j.milestoneMap.get(ms.optLong("number")));
                JSONObject r;
                try { r = api.send("POST", tp + "/issues", body); }
                catch (ApiException e) { if (e.code == 410) throw new Exception("Issues are disabled on the target repository"); throw e; }
                int newNum = r.optInt("number");
                if (is.optInt("comments") > 0 && p.comments) {
                    JSONArray cs = api.getAll(sp + "/issues/" + num + "/comments", null, 3);
                    for (int k = 0; k < cs.length(); k++) {
                        JSONObject c = cs.getJSONObject(k);
                        String cw = c.optJSONObject("user") == null ? "unknown" : c.getJSONObject("user").optString("login");
                        api.send("POST", tp + "/issues/" + newNum + "/comments", new JSONObject().put("body", "> `" + cw + "` commented on " + c.optString("created_at") + "\n\n" + shield(c.optString("body"))));
                        pace(j, 700);
                    }
                }
                if ("closed".equals(is.optString("state"))) {
                    JSONObject patch = new JSONObject().put("state", "closed");
                    String reason = is.isNull("state_reason") ? "" : is.optString("state_reason");
                    if (reason.equals("completed") || reason.equals("not_planned")) patch.put("state_reason", reason);
                    api.send("PATCH", tp + "/issues/" + newNum, patch);
                }
                j.doneIssues.add(num);
                created++;
                pace(j, 1100);
            }
            if (arr.length() < 100) break;
        }
        j.log("Issues copied: " + created + " (mentions are neutralised so nobody gets pinged)");
    }

    // ------------------------------------------------------------------ releases
    private static void stepReleases(GitPushApp app, Job j, GitHubApi api) throws Exception {
        Plan p = j.plan; Repo t = j.target;
        List<Release> src = api.releases(p.srcOwner, p.srcName);
        List<Release> pick = new ArrayList<>();
        for (Release r : src) if (p.releaseIds.isEmpty() || p.releaseIds.contains(r.id)) pick.add(r);
        if (pick.isEmpty()) { j.log("No releases to copy"); return; }
        Release latest = null;
        for (Release r : pick) if (!r.draft && !r.prerelease) { latest = r; break; }
        Collections.reverse(pick);
        long maxAsset = p.maxAssetMb * 1024L * 1024L;
        int n = 0, assets = 0;
        for (Release r : pick) {
            if (j.cancel) throw new ApiException(-2, "Cancelled");
            if (j.doneTags.contains(r.tag)) continue;
            prog(j, "Release " + r.tag, (float) (++n) / pick.size());
            Release nr;
            try { nr = api.createRelease(t.owner, t.name, r.tag, j.branch, r.title(), r.body == null ? "" : r.body, r.draft, r.prerelease, false); }
            catch (ApiException e) {
                if (e.code == 422) { j.log("Release " + r.tag + " already exists – skipped"); j.doneTags.add(r.tag); continue; }
                throw e;
            }
            j.relIds.put(r.tag, nr.id);
            if (p.assets) for (Asset a : r.assets) {
                if (a.size > maxAsset) { j.log("Asset skipped (over " + p.maxAssetMb + " MB): " + a.name); continue; }
                File tmp = new File(app.getCacheDir(), "asset_" + j.id + "_" + a.id);
                try {
                    try (FileOutputStream fo = new FileOutputStream(tmp)) {
                        api.stream(GitHubApi.repoPath(p.srcOwner, p.srcName) + "/releases/assets/" + a.id, fo,
                                (d, tot) -> prog(j, "Asset " + a.name + " " + PathUtils.formatSize(d), tot > 0 ? (float) d / tot : -1f), "application/octet-stream");
                    }
                    final File ft = tmp;
                    api.uploadAsset(t.owner, t.name, nr.id, a.name, a.contentType == null || a.contentType.isEmpty() ? "application/octet-stream" : a.contentType, tmp.length(), () -> new FileInputStream(ft), null);
                    assets++;
                } catch (ApiException e) {
                    if (e.code == -2) throw e;
                    j.log("Asset " + a.name + " failed: " + e.getMessage());
                } finally { //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                }
            }
            j.doneTags.add(r.tag);
            pace(j, 400);
        }
        if (latest != null && j.relIds.containsKey(latest.tag)) { try { api.makeLatest(t.owner, t.name, j.relIds.get(latest.tag)); } catch (Exception ignored) { } }
        j.log("Releases copied: " + j.doneTags.size() + ", assets: " + assets + " (tags point to the import commit)");
    }

    // ------------------------------------------------------------------ workflows (last, so releases and the import never trigger them)
    private static void stepWorkflows(GitPushApp app, Job j, GitHubApi api) throws Exception {
        Plan p = j.plan; Repo t = j.target;
        List<StagedFile> files = new ArrayList<>();
        for (Map.Entry<String, String> e : p.workflowBlobs.entrySet()) {
            byte[] b = api.blobBytes(p.srcOwner, p.srcName, e.getValue());
            files.add(new StagedFile(e.getKey(), b.length, null, null, b));
        }
        String msg = "Add GitHub Actions workflows" + (p.runWorkflows ? "" : " [skip ci]");
        PushSession s = new PushSession(t, j.branch, p.tDir, files, msg, "Imported from " + p.srcOwner + "/" + p.srcName);
        runPush(app, j, api, s, "Workflows");
        j.log("Workflow files pushed: " + files.size() + (p.runWorkflows ? "" : " (CI skipped for that commit)"));
        if (p.disableWorkflows) {
            Thread.sleep(4000);
            int n = 0;
            for (Workflow w : api.workflows(t.owner, t.name)) { try { api.setWorkflowEnabled(t.owner, t.name, w.id, false); n++; } catch (Exception ignored) { } }
            j.log("Workflows disabled: " + n + " (enable them in Actions when ready)");
        }
    }
}
