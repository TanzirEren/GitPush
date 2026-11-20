package com.gitpush.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Run;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Keeps GitPush alive in the background (foreground service with a progress notification) while pushes, uploads and
 * downloads run, and watches GitHub Actions runs to notify when they finish.
 */
public class KeepAliveService extends Service {
    private static final String CH = "gitpush_bg";
    private static final int ID = 4242;
    private static int refs;
    private static String title = "GitPush", text = "Working in the background";
    private static int progress = -1;
    private static final List<String[]> WATCH = new ArrayList<>(); // owner, repo, runId, title
    private static Thread watcher;
    private static volatile boolean watching;

    public static synchronized void begin(Context c, String t) {
        refs++;
        title = "GitPush"; text = t; progress = -1;
        start(c);
    }

    public static synchronized void end(Context c) {
        refs = Math.max(0, refs - 1);
        if (refs == 0 && WATCH.isEmpty()) c.stopService(new Intent(c, KeepAliveService.class));
        else notifyNow(c);
    }

    public static synchronized void update(Context c, String t, int pct) {
        text = t; progress = pct;
        notifyNow(c);
    }

    /** Watch a workflow run and notify when it completes. */
    public static synchronized void watchRun(Context c, String owner, String repo, long runId, String runTitle) {
        for (String[] w : WATCH) if (w[2].equals(String.valueOf(runId))) return;
        WATCH.add(new String[]{owner, repo, String.valueOf(runId), runTitle});
        start(c);
        startWatcher(c.getApplicationContext());
    }

    public static synchronized boolean isWatching(long runId) {
        for (String[] w : WATCH) if (w[2].equals(String.valueOf(runId))) return true;
        return false;
    }

    private static void start(Context c) {
        Intent i = new Intent(c, KeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) { }
    }

    private static Notification build(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "Background work", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(c, CH);
        } else b = new Notification.Builder(c);
        String body = refs == 0 && !WATCH.isEmpty() ? "Watching " + WATCH.size() + " workflow run(s)" : text;
        b.setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(title).setContentText(body).setOngoing(true).setOnlyAlertOnce(true);
        if (progress >= 0) b.setProgress(100, progress, false); else b.setProgress(0, 0, refs > 0);
        return b.build();
    }

    private static void notifyNow(Context c) {
        try { ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(ID, build(c)); } catch (Exception ignored) { }
    }

    private static synchronized void startWatcher(final Context app) {
        if (watching) return;
        watching = true;
        watcher = new Thread(() -> {
            while (watching) {
                try { Thread.sleep(15000); } catch (InterruptedException e) { break; }
                List<String[]> copy;
                synchronized (KeepAliveService.class) { copy = new ArrayList<>(WATCH); }
                AppState st = GitPushApp.get().state;
                GitHubApi api = st.api();
                if (api == null) continue;
                for (String[] w : copy) {
                    try {
                        Run r = api.run(w[0], w[1], Long.parseLong(w[2]));
                        if (!r.active()) {
                            synchronized (KeepAliveService.class) {
                                for (Iterator<String[]> it = WATCH.iterator(); it.hasNext(); ) if (it.next()[2].equals(w[2])) it.remove();
                            }
                            Notifier.show(app, "Workflow " + (r.conclusion == null ? "finished" : r.conclusion), w[1] + " • " + w[3]);
                        }
                    } catch (Exception ignored) { }
                }
                synchronized (KeepAliveService.class) {
                    if (WATCH.isEmpty()) {
                        watching = false;
                        if (refs == 0) app.stopService(new Intent(app, KeepAliveService.class)); else notifyNow(app);
                    }
                }
            }
            watching = false;
        }, "run-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = build(this);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(ID, n);
        } catch (Exception e) { stopSelf(); }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
