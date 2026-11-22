package com.gitpush.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gitpush.app.util.BracketLint;
import com.gitpush.app.util.GitHash;
import com.gitpush.app.util.KeystoreKit;
import com.gitpush.app.util.ReleaseOrder;
import com.gitpush.app.util.SealedBox;
import com.gitpush.app.util.PathUtils;
import com.gitpush.app.util.ZipRules;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import java.io.ByteArrayInputStream;

public class UtilTest {
    @Test public void zipPathBlocksTraversal() {
        assertNull(PathUtils.cleanZipPath("../evil.txt"));
        assertNull(PathUtils.cleanZipPath("a/../../b"));
        assertEquals("a/b.txt", PathUtils.cleanZipPath("/a//b.txt"));
        assertNull(PathUtils.cleanZipPath("__MACOSX/x"));
    }

    @Test public void repoPathValidation() {
        assertTrue(PathUtils.isValidRepoPath("src/Main.java"));
        assertFalse(PathUtils.isValidRepoPath("/abs"));
        assertFalse(PathUtils.isValidRepoPath("a//b"));
        assertFalse(PathUtils.isValidRepoPath(".git/config"));
        assertFalse(PathUtils.isValidRepoPath("a/../b"));
    }

    @Test public void joinPaths() {
        assertEquals("docs/a.txt", PathUtils.join("/docs/", "a.txt"));
        assertEquals("a.txt", PathUtils.join("", "a.txt"));
    }

    @Test public void gitBlobSha() throws Exception {
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", GitHash.blobSha(0, new ByteArrayInputStream(new byte[0])));
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", GitHash.blobSha(6, new ByteArrayInputStream("hello\n".getBytes("UTF-8"))));
    }

    @Test public void sha256Empty() throws Exception {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", GitHash.sha256(new ByteArrayInputStream(new byte[0])));
    }

    @Test public void zipRulesStripAndRoot() {
        ZipRules r = new ZipRules();
        r.stripLevels = 1;
        assertEquals("Buildora/others/Main.java", r.apply("Buildora-v2.0.0/Buildora/others/Main.java"));
        assertNull(r.apply("top.txt"));
        ZipRules r2 = new ZipRules();
        r2.rootFolder = "Buildora-v2.0.0/Buildora";
        r2.addPrefix = "app";
        assertEquals("app/others/Main.java", r2.apply("Buildora-v2.0.0/Buildora/others/Main.java"));
        assertNull(r2.apply("Buildora-v2.0.0/Other/x.txt"));
    }

    @Test public void zipRulesFilters() {
        ZipRules r = new ZipRules();
        r.exclude = "build/, *.class";
        r.skipHidden = true;
        assertNull(r.apply("a/build/x.txt"));
        assertNull(r.apply("a/B.class"));
        assertNull(r.apply("a/.hidden/x.txt"));
        assertEquals("a/.gitignore", r.apply("a/.gitignore"));
        assertNull(r.apply("__MACOSX/a"));
        ZipRules f = new ZipRules();
        f.flatten = true;
        assertEquals("Main.java", f.apply("a/b/Main.java"));
    }

    @Test public void zipTopChain() {
        List<String> p = Arrays.asList("A/B/x.txt", "A/B/y/z.txt", "A/B/README.md");
        assertEquals(Arrays.asList("A", "A/B"), ZipRules.topChain(p));
    }

    @Test public void zipRulesV2() {
        ZipRules r = new ZipRules();
        r.skipEmpty = true;
        assertNull(r.apply("a/b.txt", 0));
        assertEquals("a/b.txt", r.apply("a/b.txt", 5));
        ZipRules m = new ZipRules();
        m.maxSize = 100;
        assertNull(m.apply("big.bin", 500));
        ZipRules h = new ZipRules();
        h.hiddenExts.add("class");
        assertNull(h.apply("a/B.class", 1));
        assertEquals("a/B.java", h.apply("a/B.java", 1));
        ZipRules rp = new ZipRules();
        rp.replaceFind = "-main";
        assertEquals("proj/src/a.txt", rp.apply("proj-main/src/a.txt"));
        ZipRules c = new ZipRules();
        c.caseMode = 1; c.spacesToUnderscore = true;
        assertEquals("my_docs/read_me.txt", c.apply("My Docs/Read Me.TXT"));
        assertTrue(ZipRules.matchesGlobs("*.java, docs/", "a/b/C.java"));
    }

    @Test public void bracketLint() {
        assertEquals(0, BracketLint.check("class A { void f(){ String s = \"}{\"; } }", "java").size());
        assertEquals(1, BracketLint.check("class A { void f(){ }", "java").size());
        assertEquals(1, BracketLint.check("class A { } }", "java").size());
        assertEquals(0, BracketLint.check("def f(:", "py").size());
    }

    @Test public void sealedBoxKnownVector() throws Exception {
        byte[] esk = new byte[32];
        java.util.Arrays.fill(esk, (byte) 1);
        byte[] pk = java.util.Base64.getDecoder().decode("E75P6uryBMf9M1j8nAByGIHRdCeBKCJ+xnTzf3/pe20=");
        String out = java.util.Base64.getEncoder().encodeToString(SealedBox.seal("hello world".getBytes("UTF-8"), pk, esk));
        assertEquals("pOCSkrZRwni5dyxWn1+puxPZBrRqtoyd+dwrRAn4ogm3S+i/mdT9pPIqg7Co8GcaGiA0YOJfgkHTBcE=", out);
    }

    // ---------------------------------------------------------------- v2.1.0
    @Test public void releaseSlotsAreOneDayApart() {
        long d = 1_700_000_000_000L;
        long[] slots = ReleaseOrder.slots(new long[]{d, d + 1000, d - ReleaseOrder.DAY / 2, d - 5 * ReleaseOrder.DAY});
        for (int i = 1; i < slots.length; i++) assertTrue(slots[i - 1] - slots[i] >= ReleaseOrder.DAY);
        assertEquals(d + 1000, slots[0]);
    }

    @Test public void releaseMoveAndVersionSort() {
        java.util.List<String> l = new java.util.ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        ReleaseOrder.move(l, 3, 0);
        assertEquals(Arrays.asList("d", "a", "b", "c"), l);
        ReleaseOrder.move(l, 0, 99);
        assertEquals("d", l.get(3));
        assertTrue(ReleaseOrder.compareVersions("v1.10.0", "v1.9.9") > 0);
        assertTrue(ReleaseOrder.compareVersions("Buildora-v2.1.0", "Buildora-v2.1.3") < 0);
    }

    @Test public void changesOnlyWhereDayDiffers() {
        long d = 1_700_000_000_000L;
        long[] slots = {d, d - ReleaseOrder.DAY, d - 2 * ReleaseOrder.DAY};
        long[] old = {d + 100, d - 2 * ReleaseOrder.DAY, d - ReleaseOrder.DAY};
        assertEquals(Arrays.asList(1, 2), ReleaseOrder.changes(slots, old));
    }

    @Test public void keystoreJksRoundTripAndFingerprints() throws Exception {
        KeystoreKit.Spec sp = new KeystoreKit.Spec();
        sp.cn = "Test App"; sp.o = "TanzirDev"; sp.c = "BD"; sp.years = 25;
        KeystoreKit.Material m = KeystoreKit.generate(sp);
        m.alias = "release";
        char[] pw = "Secret123".toCharArray();
        byte[] jks = KeystoreKit.write("JKS", m, pw, pw);
        KeystoreKit.Material back = KeystoreKit.read(jks, pw, pw, "release");
        assertTrue(Arrays.equals(m.key.getEncoded(), back.key.getEncoded()));
        assertEquals(59, KeystoreKit.fingerprint(m.chain.get(0), "SHA-1").length());
        assertEquals(95, KeystoreKit.fingerprint(m.chain.get(0), "SHA-256").length());
        assertTrue(Arrays.equals(jks, KeystoreKit.unb64(KeystoreKit.b64(jks))));
        try { KeystoreKit.read(jks, "wrong1".toCharArray(), pw, null); org.junit.Assert.fail(); } catch (java.security.GeneralSecurityException expected) { }
    }

    @Test public void keystoreRejectsBadInput() {
        KeystoreKit.Spec sp = new KeystoreKit.Spec();
        assertTrue(KeystoreKit.validate(sp) != null);
        sp.cn = "x"; sp.c = "BANG";
        assertTrue(KeystoreKit.validate(sp) != null);
    }
}
