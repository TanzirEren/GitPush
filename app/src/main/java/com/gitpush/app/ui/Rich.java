package com.gitpush.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import com.gitpush.app.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Syntax highlighting, Markdown and diff rendering using plain Spannables. */
public final class Rich {
    private Rich() {}

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while", "return", "import", "package", "private", "public",
            "protected", "static", "final", "void", "new", "this", "null", "true", "false", "try", "catch", "throw", "throws", "override", "suspend", "const",
            "def", "function", "let", "async", "await", "export", "from", "in", "is", "as", "break", "continue", "extends", "implements", "int", "long",
            "string", "boolean", "lambda", "None", "True", "False", "elif", "except", "with", "pass", "struct", "enum", "switch", "case", "default", "do", "select"));
    private static final Pattern TOKEN = Pattern.compile(
            "//.*|#.*|/\\*.*?\\*/|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|<!--.*?-->|</?[A-Za-z][\\w:.-]*|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b");
    private static final Set<String> HASH_EXT = new HashSet<>(Arrays.asList("py", "sh", "yml", "yaml", "toml", "properties", "gitignore", "rb", "conf"));
    private static final Set<String> MARKUP_EXT = new HashSet<>(Arrays.asList("xml", "html", "htm", "svg"));

    public static final class CodeColors {
        public final int comment, string, number, keyword, tag;
        CodeColors(int c, int s, int n, int k, int t) { comment = c; string = s; number = n; keyword = k; tag = t; }
    }

    public static CodeColors codeColors(Context c) {
        boolean dark = (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        return dark ? new CodeColors(0xFF7A8F89, 0xFFA5E3A0, 0xFFFFB86C, 0xFF8AB4FF, 0xFF47C8AF)
                : new CodeColors(0xFF6B7F79, 0xFF1B7F3B, 0xFFB45309, 0xFF1D4ED8, 0xFF0F766E);
    }

    public static CharSequence highlight(String line, String ext, CodeColors c) {
        SpannableString sp = new SpannableString(line);
        if (line.length() > 600) return sp;
        boolean markup = MARKUP_EXT.contains(ext), hash = HASH_EXT.contains(ext);
        Matcher m = TOKEN.matcher(line);
        while (m.find()) {
            String t = m.group();
            Integer col = null; boolean bold = false;
            if (t.startsWith("//")) col = c.comment;
            else if (t.startsWith("#")) { if (hash) col = c.comment; }
            else if (t.startsWith("/*") || t.startsWith("<!--")) col = c.comment;
            else if (t.startsWith("\"") || t.startsWith("'")) col = c.string;
            else if (t.startsWith("<")) { if (markup) { col = c.tag; bold = true; } }
            else if (Character.isDigit(t.charAt(0))) col = c.number;
            else if (!markup && KEYWORDS.contains(t)) { col = c.keyword; bold = true; }
            if (col != null) {
                sp.setSpan(new ForegroundColorSpan(col), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (bold) sp.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sp;
    }

    public static CharSequence diff(Context ctx, String patch) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int primary = Ui.color(ctx, R.attr.gpPrimary);
        for (String l : patch.split("\n", -1)) {
            int s = sb.length();
            sb.append(l.isEmpty() ? " " : l).append('\n');
            int e = sb.length();
            if (l.startsWith("+")) sb.setSpan(new BackgroundColorSpan(0x3330D158), s, e, 0);
            else if (l.startsWith("-")) sb.setSpan(new BackgroundColorSpan(0x33FF453A), s, e, 0);
            else if (l.startsWith("@@")) { sb.setSpan(new BackgroundColorSpan(0x223B82F6), s, e, 0); sb.setSpan(new ForegroundColorSpan(primary), s, e, 0); }
        }
        return sb;
    }

    private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`");

    private static void inline(SpannableStringBuilder out, String s, int primary, int codeBg) {
        Matcher m = INLINE.matcher(s);
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            int st = out.length();
            if (m.group(1) != null) { out.append(m.group(1)); out.setSpan(new StyleSpan(Typeface.BOLD), st, out.length(), 0); }
            else { out.append(m.group(2)); out.setSpan(new TypefaceSpan("monospace"), st, out.length(), 0); out.setSpan(new ForegroundColorSpan(primary), st, out.length(), 0); out.setSpan(new BackgroundColorSpan(codeBg), st, out.length(), 0); }
            last = m.end();
        }
        out.append(s, last, s.length());
    }

    public static CharSequence markdown(Context ctx, String text) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        int primary = Ui.color(ctx, R.attr.gpPrimary), codeBg = Ui.color(ctx, R.attr.gpGlass);
        boolean inCode = false;
        for (String raw : text.split("\n", -1)) {
            String l = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (l.trim().startsWith("```")) { inCode = !inCode; continue; }
            int s = out.length();
            if (inCode) {
                out.append(l).append('\n');
                out.setSpan(new TypefaceSpan("monospace"), s, out.length(), 0);
                out.setSpan(new BackgroundColorSpan(codeBg), s, out.length(), 0);
                continue;
            }
            float size = 1f; boolean bold = false; String body = l;
            if (l.startsWith("### ")) { body = l.substring(4); size = 1.1f; bold = true; }
            else if (l.startsWith("## ")) { body = l.substring(3); size = 1.25f; bold = true; }
            else if (l.startsWith("# ")) { body = l.substring(2); size = 1.5f; bold = true; }
            else if (l.startsWith("- ") || l.startsWith("* ")) body = "•  " + l.substring(2);
            else if (l.startsWith("> ")) body = "│ " + l.substring(2);
            inline(out, body, primary, codeBg);
            if (size != 1f) out.setSpan(new RelativeSizeSpan(size), s, out.length(), 0);
            if (bold) out.setSpan(new StyleSpan(Typeface.BOLD), s, out.length(), 0);
            out.append('\n');
        }
        return out;
    }
}
