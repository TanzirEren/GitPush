package com.gitpush.app.ide;

import com.gitpush.app.util.BracketLint;
import com.gitpush.app.util.BracketLint.Issue;

import org.json.JSONException;
import org.json.JSONTokener;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Syntax checks that mark error lines in the editor (JSON, XML and bracket balance for C-like code). */
public final class Lint {
    private Lint() {}

    private static final Pattern POS = Pattern.compile("at character (\\d+)");

    public static List<Issue> run(String ext, String text) {
        List<Issue> out = new ArrayList<>();
        if (text.length() > 400_000) return out;
        try {
            if (ext.equals("json")) {
                try { new JSONTokener(text).nextValue(); } catch (JSONException e) {
                    int pos = 0;
                    Matcher m = POS.matcher(String.valueOf(e.getMessage()));
                    if (m.find()) pos = Math.min(text.length(), Integer.parseInt(m.group(1)));
                    int line = 1;
                    for (int i = 0; i < pos && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
                    String msg = String.valueOf(e.getMessage());
                    int cut = msg.indexOf(" at character");
                    out.add(new Issue(line, cut > 0 ? msg.substring(0, cut) : msg));
                }
                return out;
            }
            if (ext.equals("xml") || ext.equals("svg") || ext.equals("xhtml") || ext.equals("plist")) {
                try {
                    XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
                    p.setInput(new StringReader(text));
                    while (p.next() != XmlPullParser.END_DOCUMENT) { /* walk */ }
                } catch (org.xmlpull.v1.XmlPullParserException e) {
                    String msg = String.valueOf(e.getMessage());
                    int nl = msg.indexOf('\n');
                    out.add(new Issue(Math.max(1, e.getLineNumber()), nl > 0 ? msg.substring(0, nl) : msg));
                }
                return out;
            }
        } catch (Exception ignored) { return out; }
        return BracketLint.check(text, ext);
    }
}
