package com.gitpush.app.util;

import java.util.regex.Pattern;

/** Pure shell helpers: quoting, "run this code" builders, ANSI cleanup, risky-command check. */
public final class ShellKit {
    private ShellKit() {}

    public static final class Lang {
        public final String id, label, ext, run;   // run: command template, %F = file path
        Lang(String id, String label, String ext, String run) { this.id = id; this.label = label; this.ext = ext; this.run = run; }
    }

    public static final Lang[] LANGS = {
            new Lang("bash", "Bash", "sh", "bash %F"),
            new Lang("python", "Python", "py", "python3 %F"),
            new Lang("node", "Node.js", "js", "node %F"),
            new Lang("ts", "TypeScript", "ts", "npx -y tsx %F"),
            new Lang("java", "Java", "java", "java %F"),
            new Lang("go", "Go", "go", "go run %F"),
            new Lang("c", "C", "c", "gcc -O1 -o /tmp/gp_c_out %F && /tmp/gp_c_out"),
            new Lang("cpp", "C++", "cpp", "g++ -O1 -o /tmp/gp_cpp_out %F && /tmp/gp_cpp_out"),
            new Lang("rust", "Rust", "rs", "rustc -o /tmp/gp_rs_out %F && /tmp/gp_rs_out"),
            new Lang("php", "PHP", "php", "php %F"),
            new Lang("ruby", "Ruby", "rb", "ruby %F"),
    };

    public static Lang lang(String id) { for (Lang l : LANGS) if (l.id.equals(id)) return l; return LANGS[0]; }

    /** Single-quote a string for POSIX shells. */
    public static String quote(String s) { return "'" + (s == null ? "" : s).replace("'", "'\\''") + "'"; }

    /** A delimiter that does not occur in the code. */
    public static String delimiter(String code) {
        String d = "GP_EOF";
        int i = 0;
        while (code.contains(d)) d = "GP_EOF_" + (++i);
        return d;
    }

    /** Directory where GitPush keeps its run files inside the codespace. */
    public static final String DIR = "/tmp/gp";
    public static final String OUT = DIR + "/out.txt";
    public static final String STATUS = DIR + "/status.txt";
    public static final int BRIDGE_PORT = 7799;

    /**
     * Writes [code] to a temp file with a quoted heredoc and runs it. With [capture] the output is mirrored to /tmp/gp/out.txt
     * (served by the output bridge) and the exit code to /tmp/gp/status.txt.
     */
    public static String runCode(Lang l, String code, boolean capture) {
        String body = code.replace("\r\n", "\n");
        if (!body.endsWith("\n")) body += "\n";
        String file = l.id.equals("java") ? DIR + "/Main.java" : DIR + "/run." + l.ext;
        String d = delimiter(body);
        String run = l.run.replace("%F", file);
        StringBuilder sb = new StringBuilder();
        sb.append("mkdir -p ").append(DIR).append(" && cat > ").append(file).append(" <<'").append(d).append("'\n").append(body).append(d).append('\n');
        sb.append(capture ? capture(run) : run);
        return sb.toString();
    }

    /** Wraps a command so output and exit code are mirrored for the bridge. */
    public static String capture(String cmd) {
        return "mkdir -p " + DIR + " && rm -f " + STATUS + " && : > " + OUT + " && { " + cmd + " ; } 2>&1 | tee " + OUT + " ; echo \"exit ${PIPESTATUS[0]}\" > " + STATUS;
    }

    /** One-time command that serves /tmp/gp read-only on a local port (the codespace forwards it as a private port). */
    public static String bridgeStart() {
        return "mkdir -p " + DIR + " && touch " + OUT + " && (cd " + DIR + " && nohup python3 -m http.server " + BRIDGE_PORT + " --bind 127.0.0.1 >/dev/null 2>&1 &) ; echo bridge-ready";
    }

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B\\][^\u0007]*\u0007|\u001B[()][A-Za-z0-9]|\r(?!\n)");

    public static String stripAnsi(String s) { return s == null ? "" : ANSI.matcher(s).replaceAll(""); }

    /** Heuristic check for commands that deserve a second look before running AI-written or pasted text. */
    public static String risk(String cmd) {
        if (cmd == null) return null;
        String c = cmd.toLowerCase();
        if (Pattern.compile("rm\\s+-[a-z]*r[a-z]*f?[a-z]*\\s+(/|~|\\$home|\\*)(\\s|$)").matcher(c).find()) return "deletes a whole folder tree";
        if (Pattern.compile("(curl|wget)[^|\\n]*\\|\\s*(sudo\\s+)?(ba|z)?sh").matcher(c).find()) return "pipes a download straight into a shell";
        if (c.contains(":(){") ) return "looks like a fork bomb";
        if (Pattern.compile("\\bmkfs\\b|\\bdd\\s+if=.*of=/dev/").matcher(c).find()) return "can wipe a disk";
        if (Pattern.compile("git\\s+push[^\\n]*(--force|-f)\\b").matcher(c).find()) return "force-pushes and can overwrite remote history";
        if (c.contains("chmod -r 777 /") ) return "opens permissions on the whole system";
        return null;
    }

    /** Bash-style line splitter that keeps backslash continuations and heredocs together is overkill; we send text as one paste. */
    public static String normalizeNewlines(String s) { return s == null ? "" : s.replace("\r\n", "\n").replace('\r', '\n'); }

    /** Pulls a runnable command out of an AI answer (first fenced block, else the whole text without fences). */
    public static String extractCommand(String ai) {
        if (ai == null) return "";
        String t = ai.trim();
        int a = t.indexOf("```");
        if (a >= 0) {
            int nl = t.indexOf('\n', a);
            int b = t.indexOf("```", nl < 0 ? a + 3 : nl);
            if (nl >= 0 && b > nl) return t.substring(nl + 1, b).trim();
        }
        return t.replace("`", "").trim();
    }
}
