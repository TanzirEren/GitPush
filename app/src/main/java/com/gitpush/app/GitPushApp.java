package com.gitpush.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GitPushApp extends Application {
    private static GitPushApp inst;
    public final Handler main = new Handler(Looper.getMainLooper());
    public final ExecutorService io = Executors.newCachedThreadPool();
    public AppState state;

    public static GitPushApp get() { return inst; }

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        state = new AppState(this);
        state.applyNightMode();
        io.execute(this::cleanCache);
    }

    /** Removes extracted ZIP leftovers older than a day. */
    private void cleanCache() {
        java.io.File[] kids = getCacheDir().listFiles();
        if (kids == null) return;
        long limit = System.currentTimeMillis() - 24L * 3600 * 1000;
        for (java.io.File f : kids) {
            if (!(f.getName().startsWith("zip_") || f.getName().equals("zipsrc")) || f.lastModified() > limit) continue;
            deleteRec(f);
        }
    }

    private static void deleteRec(java.io.File f) {
        java.io.File[] kids = f.listFiles();
        if (kids != null) for (java.io.File k : kids) deleteRec(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
