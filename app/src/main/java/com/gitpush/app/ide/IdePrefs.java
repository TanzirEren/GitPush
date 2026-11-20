package com.gitpush.app.ide;

import android.content.SharedPreferences;

import com.gitpush.app.GitPushApp;

/** Persistent Code IDE preferences. */
public final class IdePrefs {
    private IdePrefs() {}

    private static SharedPreferences p() { return GitPushApp.get().state.store.prefs; }

    public static float font() { return p().getFloat("ide_font", 12f); }
    public static void font(float v) { p().edit().putFloat("ide_font", Math.max(8f, Math.min(28f, v))).apply(); }
    public static boolean wrap() { return p().getBoolean("ide_wrap", false); }
    public static void wrap(boolean v) { p().edit().putBoolean("ide_wrap", v).apply(); }
    public static boolean lines() { return p().getBoolean("ide_lines", true); }
    public static void lines(boolean v) { p().edit().putBoolean("ide_lines", v).apply(); }
    public static boolean color() { return p().getBoolean("ide_color", false); }
    public static void color(boolean v) { p().edit().putBoolean("ide_color", v).apply(); }
    public static boolean indent() { return p().getBoolean("ide_indent", true); }
    public static void indent(boolean v) { p().edit().putBoolean("ide_indent", v).apply(); }
    public static int tab() { return p().getInt("ide_tab", 4); }
    public static void tab(int v) { p().edit().putInt("ide_tab", v).apply(); }
    public static boolean symbols() { return p().getBoolean("ide_symbols", true); }
    public static void symbols(boolean v) { p().edit().putBoolean("ide_symbols", v).apply(); }
    public static boolean lint() { return p().getBoolean("ide_lint", true); }
    public static void lint(boolean v) { p().edit().putBoolean("ide_lint", v).apply(); }
}
