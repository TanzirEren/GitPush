package com.gitpush.app.ui;

import com.gitpush.app.R;

import java.util.HashMap;
import java.util.Map;

/** Maps legacy emoji glyphs used in labels to the monochrome vector icon family (Lucide-style outline icons). */
public final class Icons {
    private Icons() {}

    private static final Map<Integer, Integer> MAP = new HashMap<>();

    private static void m(int cp, int res) { MAP.put(cp, res); }

    static {
        m(0x2B06, R.drawable.i_upload); m(0x1F4C2, R.drawable.i_folder_open); m(0x1F558, R.drawable.i_history); m(0x26A1, R.drawable.i_zap);
        m(0x1F3F7, R.drawable.i_tag); m(0x2728, R.drawable.i_sparkles); m(0x2B50, R.drawable.i_star); m(0x2606, R.drawable.i_star);
        m(0x270F, R.drawable.i_edit); m(0x2B07, R.drawable.i_download); m(0x2139, R.drawable.i_info); m(0x1F517, R.drawable.i_link);
        m(0x1F310, R.drawable.i_globe); m(0x1F5D1, R.drawable.i_trash); m(0x1F4CB, R.drawable.i_copy); m(0x1F50D, R.drawable.i_search);
        m(0x1F4C1, R.drawable.i_folder); m(0x1F4C4, R.drawable.i_file_text); m(0x1F5DC, R.drawable.i_archive); m(0x2601, R.drawable.i_cloud_upload);
        m(0x1F9F0, R.drawable.i_toolbox); m(0x1F680, R.drawable.i_rocket); m(0x1F4E6, R.drawable.i_box); m(0x2714, R.drawable.i_check);
        m(0x2718, R.drawable.i_x); m(0x23F3, R.drawable.i_hourglass); m(0x2298, R.drawable.i_ban); m(0x1F6E1, R.drawable.i_shield);
        m(0x1F511, R.drawable.i_key); m(0x1F512, R.drawable.i_lock); m(0x26A0, R.drawable.i_alert); m(0x2753, R.drawable.i_help);
        m(0x23F8, R.drawable.i_pause); m(0x25B6, R.drawable.i_play); m(0x1F4DD, R.drawable.i_file_text); m(0x1F7E2, R.drawable.i_dot);
        m(0x26AA, R.drawable.i_circle); m(0x1F4BE, R.drawable.i_save); m(0x1F4D6, R.drawable.i_book); m(0x1F501, R.drawable.i_refresh);
        m(0x26D4, R.drawable.i_ban); m(0x1F4DC, R.drawable.i_file_text); m(0x27F3, R.drawable.i_refresh); m(0xFF0B, R.drawable.i_plus);
        m(0x2795, R.drawable.i_plus); m(0x2715, R.drawable.i_x); m(0x29C9, R.drawable.i_copy); m(0x2611, R.drawable.i_square_check);
        m(0x2610, R.drawable.i_square); m(0x1F5C4, R.drawable.i_archive); m(0x1F4E5, R.drawable.i_import); m(0x1F4E4, R.drawable.i_export);
        m(0x2699, R.drawable.i_settings); m(0x1F514, R.drawable.i_bell); m(0x1F4CA, R.drawable.i_chart); m(0x1F4BB, R.drawable.i_terminal);
        m(0x1F3A8, R.drawable.i_wand); m(0x1F501, R.drawable.i_refresh); m(0x1F4C5, R.drawable.i_clock); m(0x23F1, R.drawable.i_clock);
        m(0x2B50, R.drawable.i_star); m(0x1F5C2, R.drawable.i_folder); m(0x1F9E9, R.drawable.i_box); m(0x1F9FE, R.drawable.i_file_text);
        m(0x1F522, R.drawable.i_hash); m(0x1F441, R.drawable.i_eye); m(0x1F9F9, R.drawable.i_wand); m(0x23E9, R.drawable.i_chevron_right); m(0x1F4CC, R.drawable.i_pin);
        m(0x1F6AB, R.drawable.i_ban); m(0x1F501, R.drawable.i_refresh); m(0x1F4E1, R.drawable.i_cloud); m(0x1F5C3, R.drawable.i_archive);
    }

    public static final class Lead {
        public final int icon;
        public final String rest;
        Lead(int icon, String rest) { this.icon = icon; this.rest = rest; }
    }

    /** Returns the icon + remaining text when [t] starts with a mapped emoji, otherwise null. */
    public static Lead lead(CharSequence t) {
        if (t == null || t.length() == 0) return null;
        String s = t.toString();
        int cp = s.codePointAt(0);
        Integer res = MAP.get(cp);
        if (res == null) return null;
        int i = Character.charCount(cp);
        while (i < s.length() && (s.charAt(i) == '\uFE0F' || s.charAt(i) == '\u20E3')) i++;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return new Lead(res, s.substring(i));
    }

    /** Icon resource for an emoji string (or a default). */
    public static int resFor(String emoji, int fallback) {
        if (emoji == null || emoji.isEmpty()) return fallback;
        Integer r = MAP.get(emoji.codePointAt(0));
        return r == null ? fallback : r;
    }

    /** Icon for a file name, as a short extension badge label (e.g. "JAVA"). */
    public static String extBadge(String name) {
        int i = name.lastIndexOf('.');
        String e = i < 0 || i == name.length() - 1 ? "" : name.substring(i + 1).toUpperCase();
        if (e.isEmpty()) return name.startsWith(".") ? "DOT" : "TXT";
        return e.length() > 4 ? e.substring(0, 4) : e;
    }
}
