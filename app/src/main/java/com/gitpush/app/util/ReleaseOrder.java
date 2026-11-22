package com.gitpush.app.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pure helpers for reordering GitHub releases.
 * GitHub lists releases by the DATE OF THE TAG'S COMMIT (newest first, day granularity, semver as tie-break),
 * not by when the release was published. So a new order = new commit dates for the tags.
 */
public final class ReleaseOrder {
    private ReleaseOrder() {}

    public static final long DAY = 86_400_000L;

    /**
     * Date slots for [n] positions (index 0 = shown first / newest): the existing dates, newest first, nudged so that
     * every slot is at least one day older than the previous one (so GitHub never has to fall back on tie-breaks).
     */
    public static long[] slots(long[] oldDates) {
        long[] s = oldDates.clone();
        Arrays.sort(s);
        long[] out = new long[s.length];
        for (int i = 0; i < s.length; i++) {
            long v = s[s.length - 1 - i];
            out[i] = i == 0 ? v : Math.min(v, out[i - 1] - DAY);
        }
        return out;
    }

    public static boolean sameDay(long a, long b) { return Math.floorDiv(a, DAY) == Math.floorDiv(b, DAY); }

    /** Positions (new order) whose tag commit must be re-dated. [oldDateByPos] = current date of whoever now sits at that position. */
    public static List<Integer> changes(long[] slots, long[] oldDateByPos) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) if (!sameDay(slots[i], oldDateByPos[i])) out.add(i);
        return out;
    }

    public static String iso(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    /** Moves the element at [from] so that it ends up at index [to] (both 0-based). */
    public static <T> void move(List<T> list, int from, int to) {
        if (from < 0 || from >= list.size()) return;
        to = Math.max(0, Math.min(list.size() - 1, to));
        T x = list.remove(from);
        list.add(to, x);
    }

    // ---------------------------------------------------------------- version sorting
    /** Compares "v1.2.10" style names numerically; non-numeric parts compare as text. */
    public static int compareVersions(String a, String b) {
        long[] x = nums(a), y = nums(b);
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            long p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private static long[] nums(String s) {
        List<Long> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(s == null ? "" : s);
        while (m.find()) { try { out.add(Long.parseLong(m.group())); } catch (NumberFormatException e) { out.add(0L); } }
        long[] r = new long[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    public static <T> void reverse(List<T> l) { Collections.reverse(l); }
}
