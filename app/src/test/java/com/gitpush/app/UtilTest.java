package com.gitpush.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gitpush.app.util.GitHash;
import com.gitpush.app.util.PathUtils;

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
}
