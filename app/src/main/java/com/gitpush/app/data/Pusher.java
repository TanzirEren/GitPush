package com.gitpush.app.data;

import android.content.Context;

import com.gitpush.app.data.Models.NewEntry;
import com.gitpush.app.data.Models.RefResult;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.data.Stager.StagedFile;
import com.gitpush.app.util.GitHash;
import com.gitpush.app.util.PathUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Push engine. Everything is uploaded as blobs/trees first and only ONE commit is created at the very end,
 * so a cancelled or failed upload never leaves a half-finished state on the branch, and retries can't
 * create duplicate commits.
 */
public final class Pusher {
    private Pusher() {}

    public static final long INLINE_MAX = 900_000L;
    public static final long HARD_LIMIT = 50L * 1024 * 1024;
    public static final long WARN_LIMIT = 25L * 1024 * 1024;

    public static final int PENDING = 0, UPLOADING = 1, UPLOADED = 2, DONE = 3, FAILED = 4, SKIPPED = 5;
    public static final int REPLACE = 0, SKIP = 1, CANCEL = 2;

    public static class PushSession {
        public final Repo repo;
        public final String branch, targetDir, message, description;
        public final List<StagedFile> files;
        public String[] paths;
        private long[] sizes;
        private int[] state;
        private String[] note;

        public volatile String phase = "Preparing…";
        public volatile boolean running, finished, cancelled, noChanges;
        public volatile String error, commitSha, commitUrl;
        public volatile List<String> conflictChanged;
        public volatile int conflictIdentical;

        final BlockingQueue<Integer> decision = new ArrayBlockingQueue<>(1);
        boolean conflictChecked;
        final Map<String, String> blobCache = new ConcurrentHashMap<>();
        final Set<String> textPaths = ConcurrentHashMap.newKeySet();
        volatile GitHubApi api;
        volatile Thread thread;

        public PushSession(Repo repo, String branch, String targetDir, List<StagedFile> files, String message, String description) {
            this.repo = repo; this.branch = branch; this.targetDir = targetDir; this.files = files; this.message = message; this.description = description;
        }

        public long totalBytes() { long t = 0; for (StagedFile f : files) t += f.size; return t; }

        synchronized void set(int i, int st, String n) { state[i] = st; note[i] = n; }
        synchronized int stateOf(int i) { return state[i]; }
        public synchronized int count(int... states) {
            if (state == null) return 0;
            int c = 0;
            for (int s : state) for (int x : states) if (s == x) { c++; break; }
            return c;
        }
        public synchronized int total() { return state == null ? 0 : state.length; }
        public int doneCount() { return count(UPLOADED, DONE, SKIPPED); }
        public int failedCount() { return count(FAILED); }
        public float progress() { int t = total(); return t == 0 ? 0f : (float) doneCount() / t; }
        public synchronized int[] snapState() { return state == null ? new int[0] : state.clone(); }
        public synchronized String[] snapNote() { return note == null ? new String[0] : note.clone(); }

        public void resolveConflict(int choice) { decision.offer(choice); }

        public void cancel() {
            cancelled = true;
            GitHubApi a = api; if (a != null) a.abort();
            Thread t = thread; if (t != null) t.interrupt();
        }
    }

    public static String tryText(byte[] bytes) {
        for (byte b : bytes) if (b == 0) return null;
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) { return null; }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536]; int n;
            while ((n = i.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /** Blocking – run on a background thread. */
    public static void run(Context ctx, GitHubApi api, PushSession s) {
        s.running = true; s.error = null; s.finished = false; s.cancelled = false;
        s.api = api; s.thread = Thread.currentThread();
        try {
            execute(ctx, api, s);
        } catch (InterruptedException e) {
            s.cancelled = true; s.phase = "Cancelled";
        } catch (ApiException e) {
            if (e.code == -2 || s.cancelled) { s.cancelled = true; s.phase = "Cancelled"; } else s.error = e.getMessage();
        } catch (Exception e) {
            if (s.cancelled) { s.phase = "Cancelled"; } else s.error = e.getMessage() != null ? e.getMessage() : e.toString();
        } finally {
            s.running = false; s.thread = null;
            Thread.interrupted();
        }
    }

    private static void execute(Context ctx, GitHubApi api, PushSession s) throws Exception {
        final String o = s.repo.owner, r = s.repo.name;
        int n = s.files.size();
        if (s.paths == null) {
            s.paths = new String[n]; s.sizes = new long[n]; s.state = new int[n]; s.note = new String[n];
            for (int i = 0; i < n; i++) { s.paths[i] = PathUtils.join(s.targetDir, s.files.get(i).path); s.sizes[i] = s.files.get(i).size; }
        } else {
            for (int i = 0; i < n; i++) if (s.stateOf(i) == FAILED || s.stateOf(i) == UPLOADING) s.set(i, PENDING, null);
        }
        for (int i = 0; i < n; i++) {
            if (!PathUtils.isValidRepoPath(s.paths[i])) throw new IllegalArgumentException("Invalid path: " + s.paths[i]);
            if (s.sizes[i] > HARD_LIMIT) throw new IllegalArgumentException(s.paths[i] + " is larger than " + PathUtils.formatSize(HARD_LIMIT) + ". Use Git LFS or remove it.");
        }
        final String fullMessage = s.description.isEmpty() ? s.message : s.message + "\n\n" + s.description;

        // 1) branch / empty-repo handling
        s.phase = "Checking branch…";
        RefResult ref = api.ref(o, r, s.branch);
        if (ref.kind == RefResult.MISSING) {
            if (api.branches(o, r).isEmpty()) ref = new RefResult(RefResult.EMPTY, null);
            else throw new ApiException(404, "Branch '" + s.branch + "' was not found.");
        }
        if (ref.kind == RefResult.EMPTY) {
            int idx = -1;
            for (int i = 0; i < n; i++) if (s.stateOf(i) == PENDING && (idx < 0 || s.sizes[i] < s.sizes[idx])) idx = i;
            if (idx < 0) return;
            s.phase = "Creating first commit…";
            s.set(idx, UPLOADING, null);
            final StagedFile first = s.files.get(idx);
            String[] res = api.putFile(o, r, s.paths[idx], fullMessage, null, null, () -> first.open(ctx));
            s.set(idx, DONE, null);
            ref = api.ref(o, r, s.branch);
            if (ref.kind != RefResult.FOUND) throw new ApiException(409, "Could not read the new branch after the first commit.");
            if (s.count(PENDING) == 0) {
                s.commitSha = res[0]; s.commitUrl = res[1]; s.finished = true; s.phase = "Done";
                return;
            }
        }
        final String head = ref.sha;
        final String baseTree = api.commitTree(o, r, head);

        // 2) compare with existing files
        if (!s.conflictChecked) {
            s.phase = "Comparing with repository…";
            Map<String, String> existing = new HashMap<>();
            for (Models.TreeInfo t : api.treeRecursive(o, r, baseTree)) if (t.type.equals("blob")) existing.put(t.path, t.sha);
            List<Integer> changed = new ArrayList<>();
            int identical = 0;
            for (int i = 0; i < n; i++) {
                if (s.stateOf(i) != PENDING) continue;
                String ex = existing.get(s.paths[i]);
                if (ex == null) continue;
                String local = "";
                try (InputStream in = s.files.get(i).open(ctx)) { local = GitHash.blobSha(s.sizes[i], in); } catch (IOException ignored) { }
                if (local.equals(ex)) { s.set(i, SKIPPED, "unchanged"); identical++; } else changed.add(i);
            }
            if (!changed.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (int i : changed) names.add(s.paths[i]);
                s.conflictIdentical = identical;
                s.conflictChanged = names;
                int choice = s.decision.take();
                s.conflictChanged = null;
                if (choice == CANCEL) { s.cancelled = true; s.phase = "Cancelled"; return; }
                if (choice == SKIP) for (int i : changed) s.set(i, SKIPPED, "already exists");
            }
            s.conflictChecked = true;
        }

        // 3) upload blobs (binary / large) – small text files are sent inline with the tree
        s.phase = "Uploading files…";
        List<Integer> todo = new ArrayList<>();
        for (int i = 0; i < n; i++) if (s.stateOf(i) == PENDING) todo.add(i);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Future<?>> fs = new ArrayList<>();
            for (final int i : todo) fs.add(pool.submit(() -> uploadOne(ctx, api, s, i)));
            for (Future<?> f : fs) {
                try { f.get(); } catch (ExecutionException e) { throw new IllegalStateException(e.getCause()); }
            }
        } finally { pool.shutdownNow(); }
        if (s.failedCount() > 0) {
            s.error = s.failedCount() + " file(s) failed. Nothing was committed – tap Retry to upload the failed files.";
            return;
        }

        // 4) tree
        s.phase = "Building commit…";
        List<Integer> ready = new ArrayList<>();
        for (int i = 0; i < n; i++) if (s.stateOf(i) == UPLOADED) ready.add(i);
        if (ready.isEmpty()) { s.noChanges = true; s.finished = true; s.phase = "Nothing to commit"; return; }
        String tree = baseTree;
        List<NewEntry> chunk = new ArrayList<>();
        long chunkBytes = 0;
        for (int i : ready) {
            String p = s.paths[i];
            if (s.textPaths.contains(p)) {
                String text = tryText(readAll(s.files.get(i).open(ctx)));
                if (text == null) throw new IllegalStateException("File changed while uploading: " + p);
                chunk.add(NewEntry.text(p, text)); chunkBytes += s.sizes[i];
            } else {
                String sha = s.blobCache.get(p);
                if (sha == null) throw new IllegalStateException("Missing blob for " + p);
                chunk.add(NewEntry.blob(p, sha));
            }
            if (chunk.size() >= 100 || chunkBytes > 6_000_000L) {
                tree = api.createTree(o, r, tree, chunk); chunk = new ArrayList<>(); chunkBytes = 0;
            }
        }
        if (!chunk.isEmpty()) tree = api.createTree(o, r, tree, chunk);
        if (tree.equals(baseTree)) { s.noChanges = true; s.finished = true; s.phase = "Nothing to commit"; return; }

        // 5) commit + move branch
        s.phase = "Committing…";
        List<String> parents = new ArrayList<>();
        parents.add(head);
        String commit = api.createCommit(o, r, fullMessage, tree, parents);
        s.phase = "Updating branch…";
        api.updateRef(o, r, s.branch, commit, false);
        for (int i = 0; i < n; i++) if (s.stateOf(i) == UPLOADED) s.set(i, DONE, null);
        s.commitSha = commit;
        s.commitUrl = "https://github.com/" + s.repo.fullName + "/commit/" + commit;
        s.finished = true;
        s.phase = "Done";
    }

    private static void uploadOne(Context ctx, GitHubApi api, PushSession s, int i) {
        if (s.cancelled) return;
        s.set(i, UPLOADING, null);
        final StagedFile f = s.files.get(i);
        final String path = s.paths[i];
        try {
            byte[] bytes = null;
            if (f.size >= 1 && f.size <= INLINE_MAX) {
                bytes = readAll(f.open(ctx));
                if (tryText(bytes) != null) { s.textPaths.add(path); s.set(i, UPLOADED, null); return; }
            }
            final byte[] fb = bytes;
            String sha = fb != null
                    ? api.createBlob(s.repo.owner, s.repo.name, () -> new ByteArrayInputStream(fb))
                    : api.createBlob(s.repo.owner, s.repo.name, () -> f.open(ctx));
            s.blobCache.put(path, sha);
            s.set(i, UPLOADED, null);
        } catch (Exception e) {
            s.set(i, FAILED, e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }
}
