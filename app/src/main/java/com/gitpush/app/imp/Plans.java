package com.gitpush.app.imp;

import com.gitpush.app.data.Models.SecretInfo;

/** Builds a Plan from the current state of a loaded source. */
public final class Plans {
    private Plans() {}

    /** Everything currently selected in the tree; all releases, variables and labels; no issues. Used by the studio and by batch import. */
    public static Plan basic(Src s) {
        Plan p = new Plan();
        p.srcOwner = s.owner; p.srcName = s.name; p.srcRef = s.ref; p.srcHead = s.headSha;
        p.name = s.name; p.description = s.repo.description == null ? "" : s.repo.description; p.homepage = s.homepage;
        p.topics.addAll(s.topics);
        p.allFiles = s.tree.selected.isEmpty() || s.tree.allSelected();
        if (!p.allFiles) p.prefixes = s.tree.prefixes();
        p.modes.putAll(s.tree.modes);
        for (String wp : s.workflowPaths) {
            Tree.Node n = s.tree.file(wp);
            if (n != null && (p.allFiles || s.tree.selected.contains(wp))) p.workflowBlobs.put(wp, n.sha);
        }
        for (SecretInfo v : s.variables) if (v.value != null) p.varValues.put(v.name, v.value);
        p.copyVars = !p.varValues.isEmpty();
        p.releases = !s.releases.isEmpty();
        return p;
    }
}
