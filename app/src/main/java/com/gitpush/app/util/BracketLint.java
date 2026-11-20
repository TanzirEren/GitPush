package com.gitpush.app.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lightweight bracket / string balance checker for C-like languages (pure Java). */
public final class BracketLint {
    private BracketLint() {}

    public static final class Issue {
        public final int line;          // 1-based
        public final String message;
        public Issue(int line, String message) { this.line = line; this.message = message; }
    }

    private static final Set<String> C_LIKE = new HashSet<>(Arrays.asList("java", "kt", "kts", "js", "jsx", "ts", "tsx", "c", "h", "cpp", "hpp", "cc", "cs", "css", "scss",
            "gradle", "groovy", "php", "go", "rs", "swift", "dart", "json", "scala"));

    public static boolean supports(String ext) { return C_LIKE.contains(ext); }

    public static List<Issue> check(String text, String ext) {
        List<Issue> out = new ArrayList<>();
        if (!supports(ext)) return out;
        boolean css = ext.equals("css") || ext.equals("scss");
        int n = text.length(), line = 1;
        int[] stackLine = new int[2048];
        char[] stack = new char[2048];
        int sp = 0;
        for (int i = 0; i < n && out.size() < 20; i++) {
            char c = text.charAt(i);
            if (c == '\n') { line++; continue; }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/' && !(css && false)) { while (i < n && text.charAt(i) != '\n') i++; i--; continue; }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int startLine = line;
                i += 2;
                while (i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) { if (text.charAt(i) == '\n') line++; i++; }
                if (i + 1 >= n) out.add(new Issue(startLine, "Unterminated comment"));
                i++;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') {
                if (c == '"' && text.startsWith("\"\"\"", i)) {
                    int startLine = line;
                    i += 3;
                    while (i < n && !text.startsWith("\"\"\"", i)) { if (text.charAt(i) == '\n') line++; i++; }
                    if (i >= n) out.add(new Issue(startLine, "Unterminated text block"));
                    i += 2;
                    continue;
                }
                int startLine = line;
                boolean closed = false;
                int j = i + 1;
                for (; j < n; j++) {
                    char d = text.charAt(j);
                    if (d == '\\') { j++; continue; }
                    if (d == c) { closed = true; break; }
                    if (d == '\n') { if (c == '`') line++; else break; }
                }
                if (!closed && c != '\'') out.add(new Issue(startLine, "Unterminated string"));
                i = closed ? j : Math.max(i, j - 1);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                if (sp < stack.length) { stack[sp] = c; stackLine[sp] = line; sp++; }
            } else if (c == ')' || c == ']' || c == '}') {
                if (sp == 0) { out.add(new Issue(line, "Unexpected '" + c + "'")); continue; }
                char open = stack[sp - 1];
                char want = open == '(' ? ')' : open == '[' ? ']' : '}';
                if (c != want) out.add(new Issue(line, "Expected '" + want + "' but found '" + c + "' (opened at line " + stackLine[sp - 1] + ")"));
                sp--;
            }
        }
        while (sp > 0 && out.size() < 20) { sp--; out.add(new Issue(stackLine[sp], "Unclosed '" + stack[sp] + "'")); }
        return out;
    }
}
