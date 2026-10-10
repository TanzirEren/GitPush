package com.gitpush.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public final class Notifier {
    private Notifier() {}
    private static final String CHANNEL = "uploads";

    @SuppressWarnings("deprecation")
    public static void show(Context ctx, String title, String text) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Upload status", NotificationManager.IMPORTANCE_DEFAULT));
            b = new Notification.Builder(ctx, CHANNEL);
        } else b = new Notification.Builder(ctx);
        b.setSmallIcon(android.R.drawable.stat_sys_upload_done).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true);
        try { nm.notify((int) (System.currentTimeMillis() % 100000), b.build()); } catch (SecurityException ignored) { }
    }
}
