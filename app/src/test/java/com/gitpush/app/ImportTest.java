package com.gitpush.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gitpush.app.imp.Src;
import com.gitpush.app.imp.Tree;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

public class ImportTest {
    @Test public void parsesUrls() {
        assertArrayEquals(new String[]{"a", "b", ""}, Src.parse("a/b"));
        assertArrayEquals(new String[]{"a", "b", ""}, Src.parse("https://github.com/a/b.git"));
        assertArrayEquals(new String[]{"a", "b", ""}, Src.parse("git@github.com:a/b.git"));
        assertArrayEquals(new String[]{"a", "b", "dev"}, Src.parse("https://github.com/a/b/tree/dev"));
        assertArrayEquals(new String[]{"a", "b", "feature/x"}, Src.parse("https://github.com/a/b/tree/feature/x"));
        assertArrayEquals(new String[]{"a", "b", "v1.0"}, Src.parse("https://github.com/a/b/releases/tag/v1.0"));
        assertNull(Src.parse("not a repo"));
    }

    private Tree sample() {
        Tree t = new Tree();
        t.add("README.md", 10, "s1", "100644");
        t.add("app/src/Main.java", 100, "s2", "100644");
        t.add("app/build/out.apk", 1000, "s3", "100644");
        t.add("gradlew", 5, "s4", "100755");
        t.add(".github/workflows/build.yml", 50, "s5", "100644");
        t.recount();
        return t;
    }

    @Test public void selectionAndTriState() {
        Tree t = sample();
        assertTrue(t.allSelected());
        assertEquals("100755", t.modes.get("gradlew"));
        Tree.Node app = t.file("app/src/Main.java").parent.parent;
        assertEquals(2, app.state());
        t.toggle(t.file("app/build/out.apk"));
        assertEquals(1, app.state());
        assertEquals(4, t.root.sel);
        assertFalse(t.allSelected());
        assertEquals(Arrays.asList(".github", "README.md", "app/src", "gradlew").size(), t.prefixes().size());
        t.toggle(app);
        assertEquals(2, app.state());
        t.toggle(app);
        assertEquals(0, app.state());
    }

    @Test public void presetsAndGlobs() {
        Tree t = sample();
        t.filterOut(Tree::isJunk);
        assertFalse(t.selected.contains("app/build/out.apk"));
        assertTrue(t.selected.contains("app/src/Main.java"));
        t.selectNone();
        assertEquals(1, t.glob("*.java", true));
        t.invert();
        assertEquals(4, t.selected.size());
    }

    @Test public void prefixMatching() {
        assertTrue(Tree.inPrefixes(Arrays.asList("app/src", "README.md"), "app/src/Main.java"));
        assertTrue(Tree.inPrefixes(Arrays.asList("app/src", "README.md"), "README.md"));
        assertFalse(Tree.inPrefixes(Arrays.asList("app/src"), "app/srcother/x"));
    }

    @Test public void workflowPaths() {
        assertTrue(Tree.isWorkflowPath(".github/workflows/build.yml"));
        assertTrue(Tree.isWorkflowPath(".github/workflows/Release.YAML"));
        assertFalse(Tree.isWorkflowPath(".github/workflows/sub/x.yml"));
        assertFalse(Tree.isWorkflowPath(".github/ISSUE_TEMPLATE/a.yml"));
    }

    @Test public void searchRows() {
        Tree t = sample();
        assertEquals(1, t.rows(new HashSet<String>(), "main").size());
        assertTrue(t.rows(new HashSet<String>(), "").size() >= 4);
    }
}
