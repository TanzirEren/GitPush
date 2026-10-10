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
    }
}
