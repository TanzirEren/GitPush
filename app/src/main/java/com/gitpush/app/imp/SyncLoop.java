package com.gitpush.app.imp;

import android.content.SharedPreferences;

import com.gitpush.app.GitPushApp;
import com.gitpush.app.KeepAliveService;
import com.gitpush.app.Notifier;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Account;
import com.gitpush.app.data.Models.NewEntry;
import com.gitpush.app.data.Models.RefResult;
import com.gitpush.app.data.Models.TreeInfo;
import com.gitpush.app.util.PathUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Sync Loop: while it is switched on (foreground service keeps it alive) every enabled sync job is checked on its
 * interval. Only files whose git hash differs are copied, all in ONE commit per run.
 */
public final class SyncLoop {
    private SyncLoop() {}

    private static final String KEY = "sync_jobs", ON = "sync_loop_on";
    private static Thread thread;
    private static volatile boolean running;
    private static volatile String status = "Loop is off";

    public static synchronized List<SyncJob> load(SharedPreferences p) {
        List<SyncJob> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(SyncJob.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    public static synchronized void save(SharedPreferences p, List<SyncJob> list) {
        JSONArray a = new JSONArray();
        for (SyncJob s : list) a.put(s.toJson());
        p.edit().putString(KEY, a.toString()).apply();
    }

    public static boolean isOn() { return running; }
    public static String status() { return status; }

    public static synchronized void addFrom(GitPushApp app, Job j) {
        Plan p = j.plan;
        SyncJob s = new SyncJob();
        s.account = app.state.active == null ? "" : app.state.active.login;
        s.srcOwner = p.srcOwner; s.srcName = p.srcName; s.ref = p.srcRef; s.tOwner = j.target.owner; s.tName = j.target.name; s.tDir = p.tDir; s.branch = j.branch;
        s.lastSha = p.srcHead; s.intervalMin = p.syncMin; s.deleteRemoved = p.syncDelete; s.allFiles = p.allFiles; s.prefixes.addAll(p.prefixes);
        s.lastRun = System.currentTimeMillis(); s.lastResult = "Imported – watching for changes";
        SharedPreferences pr = app.state.store.prefs;
        List<SyncJob> all = load(pr);
        all.add(s);
        save(pr, all);
        j.log("Sync job saved: every " + s.intervalMin + " min");
        start(app);
    }

    public static synchronized void start(final GitPushApp app) {
        app.state.store.prefs.edit().putBoolean(ON, true).apply();
        if (running) return;
        running = true;
        status = "Loop running";
        KeepAliveService.begin(app, "Sync loop running");
        thread = new Thread(() -> {
            while (running) {
                try {
                    List<SyncJob> all = load(app.state.store.prefs);
                    long now = System.currentTimeMillis();
                    for (SyncJob s : all) {
                        if (!running) break;
                        if (!s.enabled || now - s.lastRun < s.intervalMin * 60_000L) continue;
                        runOne(app, s, true);
                    }
                    int active = 0;
                    for (SyncJob s : all) if (s.enabled) active++;
                    status = "Loop running • " + active + " job(s)";
                    for (int i = 0; i < 20 && running; i++) Thread.sleep(1000);
                } catch (InterruptedException e) { break; }
                catch (Exception ignored) { }
            }
        }, "sync-loop");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop(GitPushApp app) {
        app.state.store.prefs.edit().putBoolean(ON, false).apply();
        if (!running) return;
        running = false;
        status = "Loop is off";
        if (thread != null) thread.interrupt();
        KeepAliveService.end(app);
    }

    /** Called at app start: continue the loop if the user left it on. */
    public static void resume(GitPushApp app) {
        if (app.state.store.prefs.getBoolean(ON, false) && !load(app.state.store.prefs).isEmpty()) start(app);
    }

    /** Runs one job now (blocking), stores the result and returns it. */
    public static String runOne(GitPushApp app, SyncJob s, boolean notify) {
        String res;
        boolean changed = false;
        try {
            GitHubApi api = null;
            for (Account a : app.state.accounts) if (a.login.equals(s.account)) api = new GitHubApi(a.token);
            if (api == null) throw new Exception("Account " + s.account + " is not signed in");
            res = sync(api, s);
            changed = res.startsWith("Synced");
        } catch (Exception e) { res = "Failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()); }
        s.lastRun = System.currentTimeMillis(); s.lastResult = res;
        synchronized (SyncLoop.class) {
            SharedPreferences pr = app.state.store.prefs;
            List<SyncJob> all = load(pr);
            for (int i = 0; i < all.size(); i++) if (all.get(i).id == s.id) all.set(i, s);
            save(pr, all);
        }
        if (notify && (changed || res.startsWith("Failed"))) Notifier.show(app, changed ? "Sync complete" : "Sync failed", s.label() + " • " + res);
        return res;
    }

    private static boolean selected(SyncJob s, String path) { return s.allFiles || Tree.inPrefixes(s.prefixes, path); }

    static String sync(GitHubApi api, SyncJob s) throws Exception {
        String sp = GitHubApi.repoPath(s.srcOwner, s.srcName);
        JSONArray cl = api.getArr(sp + "/commits?sha=" + GitHubApi.enc2(s.ref.isEmpty() ? api.repo(s.srcOwner, s.srcName).defaultBranch : s.ref) + "&per_page=1");
        if (cl.length() == 0) return "Source has no commits";
        String head = cl.getJSONObject(0).optString("sha");
        String srcTree = cl.getJSONObject(0).getJSONObject("commit").getJSONObject("tree").optString("sha");
        if (head.equals(s.lastSha)) return "Up to date";
        JSONObject t = api.getObj(sp + "/git/trees/" + srcTree + "?recursive=1");
        if (t.optBoolean("truncated")) return "Failed: source tree is too large for incremental sync – run Import again";
        Map<String, String[]> srcFiles = new HashMap<>();   // rel path -> {sha, mode}
        JSONArray arr = t.optJSONArray("tree");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.getJSONObject(i);
            if (!"blob".equals(e.optString("type"))) continue;
            String path = e.optString("path");
            if (!selected(s, path) || e.optLong("size") > Pusher50()) continue;
            srcFiles.put(path, new String[]{e.optString("sha"), e.optString("mode", "100644")});
        }
        RefResult ref = api.ref(s.tOwner, s.tName, s.branch);
        if (ref.kind != RefResult.FOUND) return "Failed: target branch " + s.branch + " not found";
        String baseTree = api.commitTree(s.tOwner, s.tName, ref.sha);
        Map<String, String> tgt = new HashMap<>();
        for (TreeInfo ti : api.treeRecursive(s.tOwner, s.tName, baseTree)) if (ti.type.equals("blob")) tgt.put(ti.path, ti.sha);
        String dir = PathUtils.trimSlashes(s.tDir);
        List<NewEntry> es = new ArrayList<>();
        int add = 0, mod = 0, del = 0;
        List<String> todo = new ArrayList<>();
        for (Map.Entry<String, String[]> e : srcFiles.entrySet()) {
            String tp = PathUtils.join(dir, e.getKey());
            String have = tgt.get(tp);
            if (e.getValue()[0].equals(have)) continue;
            todo.add(e.getKey());
            if (have == null) add++; else mod++;
        }
        if (todo.size() > 400) return "Failed: " + todo.size() + " files changed – too many for incremental sync, run Import again";
        for (String rel : todo) {
            String[] v = srcFiles.get(rel);
            final byte[] b = api.blobBytes(s.srcOwner, s.srcName, v[0]);
            String sha = api.createBlob(s.tOwner, s.tName, () -> new ByteArrayInputStream(b));
            es.add(NewEntry.blob(PathUtils.join(dir, rel), sha).withMode(v[1]));
        }
        if (s.deleteRemoved) {
            Set<String> keep = srcFiles.keySet();
            for (String tp : tgt.keySet()) {
                String rel;
                if (dir.isEmpty()) rel = tp; else if (tp.startsWith(dir + "/")) rel = tp.substring(dir.length() + 1); else continue;
                if (selected(s, rel) && !keep.contains(rel)) { es.add(NewEntry.remove(tp)); del++; }
            }
        }
        if (es.isEmpty()) { s.lastSha = head; return "No file changes (source moved to " + head.substring(0, 7) + ")"; }
        String tree = baseTree;
        for (int i = 0; i < es.size(); i += 100) tree = api.createTree(s.tOwner, s.tName, tree, es.subList(i, Math.min(es.size(), i + 100)));
        List<String> parents = new ArrayList<>();
        parents.add(ref.sha);
        String msg = "Sync from " + s.srcOwner + "/" + s.srcName + " @ " + head.substring(0, 7) + " (+" + add + " ~" + mod + " -" + del + ")";
        String commit = api.createCommit(s.tOwner, s.tName, msg, tree, parents);
        api.updateRef(s.tOwner, s.tName, s.branch, commit, false);
        s.lastSha = head;
        return "Synced @ " + head.substring(0, 7) + " (+" + add + " ~" + mod + " -" + del + ")";
    }

    private static long Pusher50() { return com.gitpush.app.data.Pusher.HARD_LIMIT; }
}
