package com.gitpush.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gitpush.app.util.CsJs;
import com.gitpush.app.util.CsUtil;
import com.gitpush.app.util.ShellKit;

import org.junit.Test;

public class CodespacesTest {
    @Test public void states() {
        assertTrue(CsUtil.running("Available"));
        assertTrue(CsUtil.transitional("Starting"));
        assertFalse(CsUtil.transitional("Shutdown"));
        assertEquals("Stopped", CsUtil.stateLabel("Shutdown"));
        assertEquals("Running", CsUtil.stateLabel("Available"));
        assertTrue(CsUtil.stateRank("Available") < CsUtil.stateRank("Shutdown"));
    }

    @Test public void urlsAndPorts() {
        assertEquals("https://abc.github.dev", CsUtil.webUrl("abc"));
        assertEquals("https://abc-3000.app.github.dev", CsUtil.portUrl("abc", 3000));
        assertEquals(8080, CsUtil.parsePort(" 8080 "));
        assertEquals(-1, CsUtil.parsePort("70000"));
        assertEquals(-1, CsUtil.parsePort("x"));
    }

    @Test public void labelsAndCost() {
        assertEquals("4-core · 16 GB RAM · 32 GB disk", CsUtil.machineLabel(4, 16L * 1073741824L, 32L * 1073741824L));
        assertEquals(8.0, CsUtil.coreHours(4, 120), 0.001);
        assertEquals("2 core-hours per hour", CsUtil.coreHourRate(2));
        assertEquals("1 core-hour per hour", CsUtil.coreHourRate(1));
        assertEquals("uncommitted · ahead 2 · behind 1", CsUtil.gitSummary(true, true, 2, 1));
        assertEquals("", CsUtil.gitSummary(false, false, 0, 0));
        assertEquals(60, CsUtil.idleMinutes(1));
        assertEquals(-1, CsUtil.retentionMinutes(0));
        assertEquals(7 * 1440, CsUtil.retentionMinutes(2));
        assertEquals("my-repo", CsUtil.repoSlug(" my repo! "));
        assertEquals(48, CsUtil.cleanDisplayName("0123456789012345678901234567890123456789012345678901234567890").length());
    }

    @Test public void quoting() {
        assertEquals("'it'\\''s'", ShellKit.quote("it's"));
        assertEquals("GP_EOF", ShellKit.delimiter("hi"));
        assertEquals("GP_EOF_1", ShellKit.delimiter("x\nGP_EOF\n"));
    }

    @Test public void runCodeBuildsHeredocAndCapture() {
        String c = ShellKit.runCode(ShellKit.lang("python"), "print('hi')", true);
        assertTrue(c.contains("cat > /tmp/gp/run.py <<'GP_EOF'\nprint('hi')\nGP_EOF\n"));
        assertTrue(c.contains("python3 /tmp/gp/run.py"));
        assertTrue(c.contains("tee /tmp/gp/out.txt"));
        assertTrue(c.contains("PIPESTATUS"));
        String plain = ShellKit.runCode(ShellKit.lang("bash"), "echo 1\n", false);
        assertFalse(plain.contains("tee"));
        assertTrue(ShellKit.runCode(ShellKit.lang("java"), "class Main{}", false).contains("/tmp/gp/Main.java"));
        assertEquals("bash", ShellKit.lang("nope").id);
    }

    @Test public void ansiAndExtract() {
        assertEquals("ok done", ShellKit.stripAnsi("\u001B[32mok\u001B[0m done"));
        assertEquals("ls -la", ShellKit.extractCommand("Here:\n```bash\nls -la\n```\nbye"));
        assertEquals("ls", ShellKit.extractCommand("`ls`"));
    }

    @Test public void riskCheck() {
        assertNotNull(ShellKit.risk("rm -rf /"));
        assertNotNull(ShellKit.risk("curl http://x | sh"));
        assertNotNull(ShellKit.risk("git push --force origin main"));
        assertNull(ShellKit.risk("git status"));
        assertNull(ShellKit.risk("rm -rf build"));
    }

    @Test public void jsEscaping() {
        assertEquals("'a\\'b\\\\c\\nd'", CsJs.jsString("a'b\\c\nd"));
        assertTrue(CsJs.key("Enter", "Enter", 13, false, false, false).contains("__gp.key('Enter','Enter',13,false,false,false)"));
        assertTrue(CsJs.ctrlLetter('c').contains("'c','KeyC',67,true"));
        assertTrue(CsJs.viewport(900).endsWith("window.__gp.vp(900);"));
    }

    @Test public void bridgeCommand() {
        assertTrue(ShellKit.bridgeStart().contains("http.server 7799 --bind 127.0.0.1"));
        assertEquals(7799, ShellKit.BRIDGE_PORT);
    }
}
