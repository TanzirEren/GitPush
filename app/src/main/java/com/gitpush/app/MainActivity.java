package com.gitpush.app;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;

public class MainActivity extends BaseActivity {
    private int tab = 0;
    private FrameLayout content;
    private HomeTab home;
    private ReposTab repos;
    private ProfileTab profile;
    private LinearLayout navHome, navProfile;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (st.active == null) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }
        if (b != null) tab = b.getInt("tab", 0);
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        home = new HomeTab(this); repos = new ReposTab(this); profile = new ProfileTab(this);
        View scrim = new View(this);
        scrim.setBackgroundResource(R.drawable.bg_scrim);
        root.addView(scrim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 150), Gravity.BOTTOM));
        root.addView(buildNav(), new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM));
        st.loadRepos(false, null);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33 && !st.store.prefs.getBoolean("asked_notif", false)) {
            st.store.prefs.edit().putBoolean("asked_notif", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("tab", tab); }

    @Override protected void onResume() {
        super.onResume();
        if (st.active == null) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }
        if (st.sharedBanner) tab = 1;
        select(tab);
    }

    public void select(int t) {
        tab = t;
        content.removeAllViews();
        View v;
        if (t == 0) { home.refresh(); v = home.view; }
        else if (t == 1) { repos.refresh(); v = repos.view; }
        else { profile.refresh(); v = profile.view; }
        content.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(180).start();
        tint(navHome, t == 0); tint(navProfile, t == 2);
    }

    private void tint(LinearLayout item, boolean on) {
        if (item == null) return;
        item.setAlpha(on ? 1f : 0.55f);
        ((TextView) item.getChildAt(1)).setTextColor(Ui.color(this, on ? R.attr.gpPrimary : R.attr.gpMuted));
    }

    private LinearLayout navItem(int icon, String label, int target) {
        LinearLayout l = Ui.vbox(this);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundResource(R.drawable.bg_ripple_row);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        l.addView(iv, Ui.lp(Ui.dp(this, 24), Ui.dp(this, 24)));
        l.addView(Ui.text(this, label, 11, true, R.attr.gpMuted), Ui.lp(Ui.WRAP, Ui.WRAP));
        l.setOnClickListener(v -> { Ui.haptic(v); select(target); });
        return l;
    }

    private View buildNav() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(Ui.dp(this, 28), Ui.dp(this, 14), Ui.dp(this, 28), Ui.dp(this, 14));
        wrap.setClipChildren(false);
        LinearLayout pill = Ui.hbox(this);
        pill.setBackgroundResource(R.drawable.bg_pill_nav);
        pill.setElevation(Ui.dp(this, 10));
        pill.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        navHome = navItem(R.drawable.ic_home, "Home", 0);
        navProfile = navItem(R.drawable.ic_person, "Profile", 2);
        pill.addView(navHome, Ui.weight(1f));
        pill.addView(new View(this), Ui.weight(1f));
        pill.addView(navProfile, Ui.weight(1f));
        wrap.addView(pill, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 66), Gravity.BOTTOM));
        ImageView fab = new ImageView(this);
        fab.setImageResource(R.drawable.ic_plus);
        fab.setBackgroundResource(R.drawable.bg_fab);
        fab.setColorFilter(Ui.color(this, R.attr.gpOnPrimary));
        fab.setPadding(Ui.dp(this, 17), Ui.dp(this, 17), Ui.dp(this, 17), Ui.dp(this, 17));
        fab.setElevation(Ui.dp(this, 14));
        fab.setOnClickListener(v -> { Ui.haptic(v); select(1); });
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(this, 70), Ui.dp(this, 70), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fp.bottomMargin = Ui.dp(this, 30);
        wrap.addView(fab, fp);
        return wrap;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        com.gitpush.app.ui.Downloader.onResult(this, req, res, data);
    }

    @Override public void onBackPressed() { if (tab != 0) select(0); else super.onBackPressed(); }

    public static final int NAV_SPACE = 130;

    AppState getAppState() { return st; }
}
