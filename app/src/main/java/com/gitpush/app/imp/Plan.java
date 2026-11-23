package com.gitpush.app.imp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything the user chose in the Import Studio. Pure data. */
public final class Plan {
    // source
    public String srcOwner = "", srcName = "", srcRef = "", srcHead = "";
    // target
    public boolean existing;
    public String tOwner = "", tName = "", tDir = "";
    public String name = "", description = "", homepage = "";
    public final List<String> topics = new ArrayList<>();
    public boolean priv = true;
    // files
    public boolean allFiles = true;
    public List<String> prefixes = new ArrayList<>();
    public final Map<String, String> modes = new LinkedHashMap<>();
    public final Map<String, String> workflowBlobs = new LinkedHashMap<>();   // selected workflow files: path -> blob sha (pushed last)
    public int maxFileMb = 50;
    public int conflict = 0;                                         // existing target: 0 replace, 1 skip
    // history
    public int replay = 0;
    public boolean keepAuthors = true;
    // releases
    public boolean releases = true, assets = true;
    public final Set<Long> releaseIds = new LinkedHashSet<>();       // empty = all
    public int maxAssetMb = 100;
    // actions / secrets
    public boolean runWorkflows = false, disableWorkflows = false;
    public boolean copyVars = true;
    public final Map<String, String> varValues = new LinkedHashMap<>();   // variables to copy: name -> value
    public final Map<String, String> secrets = new LinkedHashMap<>();
    // issues
    public boolean labels = true, issues = false, comments = false;
    public int issueState = 1, issueLimit = 100;                      // 0 open only, 1 all
    // extras
    public boolean note = false;
    public int syncMin = 0;                                          // > 0 = keep in sync (loop) every N minutes
    public boolean syncDelete = false;

    public String title() { return srcOwner + "/" + srcName + " → " + (existing ? tOwner + "/" + tName : name); }
}
