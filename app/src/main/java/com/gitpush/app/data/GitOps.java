package com.gitpush.app.data;

import com.gitpush.app.data.Models.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Multi-file operations done as ONE commit through the Git Data API. */
public final class GitOps {
    private GitOps() {}

    interface Builder { List<NewEntry> build(List<TreeInfo> existing) throws ApiException; }

    private static String apply(GitHubApi api, String o, String r, String branch, String message, Builder b) throws ApiException {
        RefResult ref = api.ref(o, r, branch);
        if (ref.kind != RefResult.FOUND) throw new ApiException(404, "Branch '" + branch + "' not found.");
        String head = ref.sha;
        String baseTree = api.commitTree(o, r, head);
        List<TreeInfo> existing = api.treeRecursive(o, r, baseTree);
        List<NewEntry> entries = b.build(existing);
        if (entries.isEmpty()) throw new ApiException(422, "Nothing to change.");
        String tree = baseTree;
        for (int i = 0; i < entries.size(); i += 200)
            tree = api.createTree(o, r, tree, entries.subList(i, Math.min(entries.size(), i + 200)));
        List<String> parents = new ArrayList<>();
        parents.add(head);
        String commit = api.createCommit(o, r, message, tree, parents);
        api.updateRef(o, r, branch, commit, false);
        return commit;
    }

    /** Deletes files and (recursively) folders in one commit. */
    public static String deletePaths(GitHubApi api, String o, String r, String branch, List<String> paths, String message) throws ApiException {
        return apply(api, o, r, branch, message, ex -> {
            Set<String> out = new LinkedHashSet<>();
            for (String p : paths)
                for (TreeInfo t : ex)
                    if (t.type.equals("blob") && (t.path.equals(p) || t.path.startsWith(p + "/"))) out.add(t.path);
            List<NewEntry> list = new ArrayList<>();
            for (String s : out) list.add(NewEntry.remove(s));
            return list;
        });
    }

    public static String rename(GitHubApi api, String o, String r, String branch, String from, String to, String message) throws ApiException {
        return apply(api, o, r, branch, message, ex -> {
            TreeInfo src = null;
            for (TreeInfo t : ex) {
                if (t.type.equals("blob") && t.path.equals(from)) src = t;
                if (t.path.equals(to)) throw new ApiException(409, "A file or folder named '" + to + "' already exists.");
            }
            if (src == null) throw new ApiException(404, "File not found: " + from);
            List<NewEntry> list = new ArrayList<>();
            list.add(NewEntry.blob(to, src.sha));
            list.add(NewEntry.remove(from));
            return list;
        });
    }

    /** Reverts a single-parent commit by restoring the parent's version of every file it touched. */
    public static String revert(GitHubApi api, String o, String r, String branch, CommitDetail d) throws ApiException {
        if (d.info.parents.size() != 1) throw new ApiException(422, "Merge or root commits can't be reverted here.");
        String parent = d.info.parents.get(0);
        String msg = "Revert \"" + d.info.firstLine() + "\"\n\nThis reverts commit " + d.info.sha + ".";
        return apply(api, o, r, branch, msg, ex -> {
            Set<String> now = new HashSet<>();
            for (TreeInfo t : ex) if (t.type.equals("blob")) now.add(t.path);
            Map<String, String> pf = new HashMap<>();
            for (TreeInfo t : api.treeRecursive(o, r, api.commitTree(o, r, parent))) if (t.type.equals("blob")) pf.put(t.path, t.sha);
            List<NewEntry> out = new ArrayList<>();
            for (CommitFile f : d.files) {
                switch (f.status) {
                    case "added": if (now.contains(f.filename)) out.add(NewEntry.remove(f.filename)); break;
                    case "removed": if (pf.containsKey(f.filename)) out.add(NewEntry.blob(f.filename, pf.get(f.filename))); break;
                    case "renamed":
                        if (now.contains(f.filename)) out.add(NewEntry.remove(f.filename));
                        if (f.previous != null && pf.containsKey(f.previous)) out.add(NewEntry.blob(f.previous, pf.get(f.previous)));
                        break;
                    default: if (pf.containsKey(f.filename)) out.add(NewEntry.blob(f.filename, pf.get(f.filename)));
                }
            }
            return out;
        });
    }
}
