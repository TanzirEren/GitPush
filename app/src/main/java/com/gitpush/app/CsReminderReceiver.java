package com.gitpush.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.gitpush.app.data.CodespacesApi;
import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Snippets;
import com.gitpush.app.util.CsUtil;

import org.json.JSONObject;

/** "Your codespace is still running" reminders (with a Stop button) – keeps core-hours from burning unnoticed. */
public class CsReminderReceiver extends BroadcastReceiver {
    static final String ACTION_REMIND = "com.gitpush.app.CS_REMIND", ACTION_STOP = "com.gitpush.app.CS_STOP";
    private static final String CHANNEL = "codespaces";

    private static PendingIntent pi(Context c, String action, String name) {
        Intent i = new Intent(c, CsReminderReceiver.class).setAction(action).putExtra("name", name);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c, (action + name).hashCode(), i, flags);
    }

    public static void schedule(Context c, String name, int minutes) {
        if (minutes <= 0 || name == null || name.isEmpty()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + minutes * 60_000L;
        try { am.set(AlarmManager.RTC_WAKEUP, at, pi(c, ACTION_REMIND, name)); } catch (Exception ignored) { }
    }

    public static void cancel(Context c, String name) {
        if (name == null || name.isEmpty()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi(c, ACTION_REMIND, name));
    }

    @SuppressWarnings("deprecation")
    private static void notify(Context c, int id, String title, String text, PendingIntent action, String actionLabel) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Codespace reminders", NotificationManager.IMPORTANCE_DEFAULT));
            b = new Notification.Builder(c, CHANNEL);
        } else b = new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true);
        if (action != null) b.addAction(new Notification.Action.Builder(null, actionLabel, action).build());
        try { nm.notify(id, b.build()); } catch (SecurityException ignored) { }
    }

    @Override public void onReceive(final Context c, Intent i) {
        final String name = i.getStringExtra("name");
        if (name == null) return;
        final GitHubApi api = GitPushApp.get().state.api();
        if (api == null) return;
        final PendingResult pr = goAsync();
        final boolean stop = ACTION_STOP.equals(i.getAction());
        GitPushApp.get().io.execute(() -> {
            try {
                CodespacesApi cs = new CodespacesApi(api);
                if (stop) {
                    cs.stop(name);
                    notify(c, name.hashCode(), "Codespace stopping", name, null, null);
                } else {
                    JSONObject j = cs.get(name);
                    if (CsUtil.running(j.optString("state"))) {
                        notify(c, name.hashCode(), "Codespace still running", j.optString("display_name", name) + " is running and uses core-hours. Stop it when you're done.", pi(c, ACTION_STOP, name), "Stop now");
                        int again = Snippets.remindMinutes(GitPushApp.get().state.store);
                        if (again > 0) schedule(c, name, Math.max(30, again));
                    }
                }
            } catch (Exception ignored) {
            } finally { pr.finish(); }
        });
    }
}
