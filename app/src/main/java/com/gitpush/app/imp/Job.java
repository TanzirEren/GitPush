package com.gitpush.app.imp;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.data.Pusher.PushSession;
import com.gitpush.app.data.Stager.StagedFile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/** One running or finished import. Lives in memory so the Jobs screen can show a live log. */
public final class Job {
    public static final int PENDING = 0, DONE = 1, FAILED = 2, SKIPPED = 3, RUNNING = 4;

    public final long id = System.currentTimeMillis();
    public final Plan plan;
    public final String token;
    public volatile String status = "queued";           // queued, running, done, partial, failed, cancelled
    public volatile String phase = "Queued";
    public volatile float progress = -1f;
    public volatile boolean cancel;
    public final List<String> log = Collections.synchronizedList(new ArrayList<String>());
    public final Map<String, Integer> steps = Collections.synchronizedMap(new LinkedHashMap<String, Integer>());
    public final Map<String, String> stepNote = Collections.synchronizedMap(new LinkedHashMap<String, String>());

    // runtime state (kept for retry)
    public Repo target;
    public String branch = "";
    public volatile GitHubApi api;
    public volatile PushSession push;
    public Future<?> future;
    public final Set<String> doneTags = new HashSet<>();
    public final Set<Long> doneIssues = new HashSet<>();
    public final Map<String, Long> relIds = new LinkedHashMap<>();
    public final Map<Long, Integer> milestoneMap = new LinkedHashMap<>();
    public final List<StagedFile> held = new ArrayList<>();
    public boolean snapshotDone;
    public int replayDone;
    public long startedAt = System.currentTimeMillis(), endedAt;

    public Job(Plan plan, String token) { this.plan = plan; this.token = token; }

    private static final SimpleDateFormat HMS = new SimpleDateFormat("HH:mm:ss", Locale.US);

    public void log(String s) {
        synchronized (HMS) { log.add(HMS.format(new Date()) + "  " + s); }
        while (log.size() > 600) log.remove(0);
    }

    public String logText() {
        StringBuilder sb = new StringBuilder();
        synchronized (log) { for (String l : log) sb.append(l).append('\n'); }
        return sb.toString();
    }

    public boolean running() { return status.equals("running") || status.equals("queued"); }

    public int stepCount(int state) { int c = 0; synchronized (steps) { for (int v : steps.values()) if (v == state) c++; } return c; }

    public String summary() {
        int ok = stepCount(DONE), bad = stepCount(FAILED);
        return ok + " step(s) done" + (bad > 0 ? ", " + bad + " failed" : "");
    }
}
