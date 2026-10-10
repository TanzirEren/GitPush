package com.gitpush.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.gitpush.app.data.Models.Account;
import com.gitpush.app.ui.ImageLoader;
import com.gitpush.app.ui.Ui;

import java.util.ArrayList;
import java.util.List;

class ProfileTab {
    static final String[] FEATURES = {
            "GitHub OAuth (Device Flow) & Personal Access Token login", "Multiple accounts with instant switching", "Encrypted token storage (Android Keystore)",
            "Public / private repository filter & search", "Create repositories", "Repository permission checker",
            "Pick files and folders with folder structure preserved", "ZIP upload with automatic extraction", "Review, select & delete staged files/folders before pushing",
            "Duplicate detection (same file selected twice)", "Empty-folder handling (.gitkeep)", "Upload size calculator & large-file warning",
            "Branch selector, create & delete branches", "Target folder inside the repo", "Single atomic commit (no half-pushed uploads)",
            "Commit message + description with smart suggestions", "Live progress, per-file status, cancel & retry failed", "Existing-file conflict detection: Replace / Skip / Cancel",
            "Unchanged files auto-skipped (SHA compare)", "Rate-limit & network retry handling", "Repository browser with breadcrumbs and search",
            "Code viewer with syntax colors & line numbers", "Code editor with commit", "Create / rename / move / delete files",
            "Delete one or many files & folders in one commit", "Image preview & Markdown preview", "Download file, copy path & raw URL",
            "Commit history, commit details & diff viewer", "Compare & merge branches, revert commit", ".gitignore & README generators",
            "SHA-256 / Git-SHA file checker", "Share files from other apps into GitPush", "Upload history, repeat last config & report export",
            "Upload notifications", "Light / Dark / System themes, accent colors, density, haptics", "Glass UI, splash & onboarding animations"
    };
    private static final String[] ACCENTS = {"Mint", "Sky", "Violet", "Amber", "Rose"};
    private static final int[] ACCENT_COLORS = {0xFF47C8AF, 0xFF7CA8FF, 0xFFB794FF, 0xFFFFA45C, 0xFFFF7A9C};

    final MainActivity a;
    final ScrollView view;
    private final LinearLayout col;

    ProfileTab(MainActivity a) {
        this.a = a;
        view = new ScrollView(a);
        col = Ui.vbox(a);
        col.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, MainActivity.NAV_SPACE));
        view.addView(col);
    }

    private LinearLayout.LayoutParams side(float top) { return Ui.lp(a, Ui.MATCH, Ui.WRAP, 20, top, 20, 0); }

    void refresh() {
        col.removeAllViews();
        final AppState st = a.getAppState();
        Account acc = st.active;
        LinearLayout head = Ui.vbox(a);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 12));
        ImageView av = new ImageView(a);
        ImageLoader.circle(av, acc == null ? null : acc.avatar, 92);
        head.addView(av);
        head.addView(Ui.text(a, acc == null ? "Not signed in" : acc.display(), 20, true, R.attr.gpText), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 10, 0, 0));
        if (acc != null) head.addView(Ui.muted(a, "@" + acc.login));
        col.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));

        col.addView(Ui.section(a, "Accounts"));
        for (final Account x : new ArrayList<>(st.accounts)) {
            LinearLayout c = Ui.card(a);
            c.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10));
            LinearLayout r = Ui.hbox(a);
            ImageView iv = new ImageView(a);
            ImageLoader.circle(iv, x.avatar, 40);
            r.addView(iv);
            LinearLayout t = Ui.vbox(a);
            t.setPadding(Ui.dp(a, 12), 0, 0, 0);
            t.addView(Ui.title(a, x.display()));
            t.addView(Ui.muted(a, "@" + x.login + " • " + ("oauth".equals(x.authType) ? "OAuth" : "Token")));
            r.addView(t, Ui.weight(1f));
            boolean sel = acc != null && acc.login.equals(x.login);
            r.addView(sel ? Ui.pill(a, "ACTIVE", R.attr.gpOk) : Ui.text(a, "Switch", 13, true, R.attr.gpPrimary));
            r.addView(Ui.textButton(a, "⎋", R.attr.gpMuted, v -> Ui.confirm(a, "Sign out @" + x.login + "?",
                    "The saved token is removed from this device. You can sign in again any time.", "Sign out", () -> {
                        st.logout(x.login);
                        if (st.accounts.isEmpty()) { a.startActivity(new Intent(a, LoginActivity.class)); a.finish(); } else refresh();
                    })));
            c.addView(r);
            c.setOnClickListener(v -> { st.switchTo(x.login); Ui.toast(a, "Switched to @" + x.login); refresh(); });
            col.addView(c, side(0));
            ((LinearLayout.LayoutParams) c.getLayoutParams()).bottomMargin = Ui.dp(a, 8);
        }
        col.addView(Ui.outlineButton(a, "＋  Add new account", v -> a.startActivity(new Intent(a, LoginActivity.class).putExtra("add", true))), side(0));

        // permissions
        col.addView(Ui.section(a, "App permissions"));
        LinearLayout perm = Ui.card(a);
        perm.addView(permRow("GitHub access", acc == null || acc.scopes.isEmpty() ? "fine-grained token (limited to selected repos)" : acc.scopes));
        perm.addView(permRow("Internet", "Talks to the GitHub API over HTTPS only"));
        perm.addView(permRow("Files & folders", "Chosen one by one with the system picker – no broad storage permission"));
        boolean notif = Build.VERSION.SDK_INT < 33 || a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        LinearLayout nr = Ui.hbox(a);
        LinearLayout nt = Ui.vbox(a);
        nt.addView(Ui.title(a, "Notifications"));
        nt.addView(Ui.muted(a, "Upload finished / failed alerts"));
        nr.addView(nt, Ui.weight(1f));
        if (notif) nr.addView(Ui.pill(a, "ALLOWED", R.attr.gpOk));
        else nr.addView(Ui.textButton(a, "Allow", R.attr.gpPrimary, v -> { if (Build.VERSION.SDK_INT >= 33) a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12); }));
        perm.addView(nr);
        col.addView(perm, side(0));

        // settings
        col.addView(Ui.section(a, "Settings"));
        LinearLayout set = Ui.card(a);
        set.addView(Ui.title(a, "Theme"));
        LinearLayout themes = Ui.hbox(a);
        String[][] modes = {{"system", "System"}, {"light", "Light"}, {"dark", "Dark"}};
        for (final String[] m : modes)
            themes.addView(Ui.chip(a, m[1], st.theme.equals(m[0]), v -> { st.setThemeMode(m[0]); a.recreate(); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 0, 8, 0));
        set.addView(themes, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 12));
        set.addView(Ui.title(a, "Accent color"));
        LinearLayout acs = Ui.hbox(a);
        for (int i = 0; i < ACCENTS.length; i++) {
            final int idx = i;
            TextView dot = Ui.text(a, st.accent == i ? "✓" : "", 16, true, R.attr.gpBg);
            dot.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(ACCENT_COLORS[i]);
            if (st.accent == i) g.setStroke(Ui.dp(a, 3), Ui.color(a, R.attr.gpText));
            dot.setBackground(g);
            dot.setOnClickListener(v -> { st.setAccent(idx); a.recreate(); });
            acs.addView(dot, Ui.lp(a, Ui.dp(a, 38), Ui.dp(a, 38), 0, 0, 12, 0));
        }
        set.addView(acs, Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 12));
        set.addView(switchRow("Compact list density", st.compact, on -> st.setCompact(on)));
        set.addView(switchRow("Haptic feedback", st.haptics, on -> st.setHaptics(on)));
        set.addView(Ui.textButton(a, "Clear upload history", R.attr.gpError, v -> { st.clearHistory(); Ui.toast(a, "History cleared"); }));
        col.addView(set, side(0));

        LinearLayout tools = Ui.card(a);
        tools.addView(Ui.title(a, "🧰  Smart tools"));
        tools.addView(Ui.muted(a, ".gitignore, README, SHA-256 checker"));
        tools.setOnClickListener(v -> a.startActivity(new Intent(a, ToolsActivity.class)));
        col.addView(tools, side(10));

        col.addView(Ui.section(a, "About GitPush"));
        LinearLayout about = Ui.card(a);
        about.addView(Ui.title(a, "GitPush"));
        about.addView(Ui.muted(a, "Version " + BuildConfigInfo.VERSION + "  •  com.gitpush.app"));
        about.addView(Ui.muted(a, "Developer: TanzirDev"));
        about.addView(Ui.body(a, "Push files, folders and ZIPs to GitHub from your phone. Your token never leaves your device except to talk to api.github.com."), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        LinearLayout links = Ui.hbox(a);
        links.addView(Ui.textButton(a, "All " + FEATURES.length + " features", R.attr.gpPrimary, v -> features()));
        links.addView(Ui.textButton(a, "Portfolio", R.attr.gpPrimary, v -> Ui.openUrl(a, "https://tanzirlen.netlify.app")));
        about.addView(links);
        col.addView(about, side(0));
    }

    private View permRow(String title, String sub) {
        LinearLayout r = Ui.hbox(a);
        LinearLayout t = Ui.vbox(a);
        t.addView(Ui.title(a, title));
        t.addView(Ui.muted(a, sub));
        r.addView(t, Ui.weight(1f));
        r.addView(Ui.pill(a, "OK", R.attr.gpOk));
        r.setPadding(0, 0, 0, Ui.dp(a, 10));
        return r;
    }

    private View switchRow(String label, boolean on, Ui.Cb<Boolean> cb) {
        LinearLayout r = Ui.hbox(a);
        r.addView(Ui.body(a, label), Ui.weight(1f));
        Switch sw = new Switch(a);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, v) -> cb.on(v));
        r.addView(sw);
        r.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
        return r;
    }

    private void features() {
        StringBuilder sb = new StringBuilder();
        for (String f : FEATURES) sb.append("• ").append(f).append("\n");
        new AlertDialog.Builder(a).setTitle("Features").setMessage(sb.toString().trim()).setPositiveButton("Close", null).show();
    }
}
