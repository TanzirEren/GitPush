package com.gitpush.app.imp;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.Branch;
import com.gitpush.app.data.Models.CommitInfo;
import com.gitpush.app.data.Models.Release;
import com.gitpush.app.data.Models.Repo;
import com.gitpush.app.data.Models.Run;
import com.gitpush.app.data.Models.SecretInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Everything we learn about the source repository before importing (blocking – background thread only). */
public final class Src {
    /** owner/repo[/tree/ref] in many spellings: https URL, .git, ssh, plain owner/repo. */
    private static final Pattern URL = Pattern.compile("^(?:(?:https?://(?:www\\.)?github\\.com/)|(?:git@github\\.com:)|(?:github\\.com/))?([\\w.-]+)/([\\w.-]+?)(?:\\.git)?(?:/(?:tree|blob|commit|releases/tag)/([^?#]+?))?/?(?:[?#].*)?$");

    public static String[] parse(String text) {
        if (text == null) return null;
        Matcher m = URL.matcher(text.trim());
        if (!m.matches()) return null;
        String ref = m.group(3);
        return new String[]{m.group(1), m.group(2), ref == null ? "" : ref};
    }

    public String owner, name, ref = "", headSha = "", treeSha = "", homepage = "", license = "", createdAt = "", pushedAt = "", ownerType = "";
    public Repo repo;
    public int forks, watchers, openIssues, issueTotal = -1, issueOpen = -1;
    public boolean fork, archived, treeTruncated, hasWiki, hasPages;
    public final List<String> topics = new ArrayList<>();
    public final List<Branch> branches = new ArrayList<>();
    public final List<String> tags = new ArrayList<>();
    public final Tree tree = new Tree();
    public final List<Release> releases = new ArrayList<>();
    public final List<String> workflowPaths = new ArrayList<>();
    public final List<Run> runs = new ArrayList<>();
    public final List<CommitInfo> commits = new ArrayList<>();
    public final List<String> secretNames = new ArrayList<>();
    public final List<SecretInfo> variables = new ArrayList<>();
    public final Set<String> refSecrets = new LinkedHashSet<>(), refVars = new LinkedHashSet<>();
    public final List<String> notes = new ArrayList<>();
    public int labelCount = -1;

    public String full() { return owner + "/" + name; }

    public interface Step { void on(String text); }

    /** Loads (or reloads for another ref). Repo, ref and tree are required – everything else is best effort. */
    public static Src load(GitHubApi api, String owner, String name, String wantedRef, Step step) throws Exception {
        Src s = new Src();
        s.owner = owner; s.name = name;
        if (step != null) step.on("Reading repository…");
        s.repo = api.repo(owner, name);
        s.owner = s.repo.owner; s.name = s.repo.name;
        JSONObject j = api.getObj(GitHubApi.repoPath(s.owner, s.name));
        s.homepage = j.isNull("homepage") ? "" : j.optString("homepage");
        JSONObject lic = j.optJSONObject("license");
        s.license = lic == null ? "" : lic.optString("spdx_id", lic.optString("name"));
        s.createdAt = j.optString("created_at"); s.pushedAt = j.optString("pushed_at");
        s.forks = j.optInt("forks_count"); s.watchers = j.optInt("subscribers_count", j.optInt("watchers_count"));
        s.openIssues = j.optInt("open_issues_count"); s.fork = j.optBoolean("fork"); s.archived = j.optBoolean("archived");
        s.hasWiki = j.optBoolean("has_wiki"); s.hasPages = j.optBoolean("has_pages");
        JSONArray ts = j.optJSONArray("topics");
        if (ts != null) for (int i = 0; i < ts.length(); i++) s.topics.add(ts.optString(i));
        s.ref = wantedRef == null || wantedRef.isEmpty() ? s.repo.defaultBranch : wantedRef;

        if (step != null) step.on("Reading file tree…");
        JSONArray cl = null;
        String tryRef = s.ref;
        while (true) {   // URLs like /tree/feature/x/src: peel trailing path segments until the ref resolves
            try { cl = api.getArr(GitHubApi.repoPath(s.owner, s.name) + "/commits?sha=" + GitHubApi.enc2(tryRef) + "&per_page=1"); break; }
            catch (Exception e) {
                int i = tryRef.lastIndexOf('/');
                if (i <= 0) { if (wantedRef == null || wantedRef.isEmpty()) throw e; tryRef = s.repo.defaultBranch; s.notes.add("Ref '" + wantedRef + "' not found – using the default branch."); try { cl = api.getArr(GitHubApi.repoPath(s.owner, s.name) + "/commits?sha=" + GitHubApi.enc2(tryRef) + "&per_page=1"); } catch (Exception e2) { throw e2; } break; }
                tryRef = tryRef.substring(0, i);
            }
        }
        s.ref = tryRef;
        if (cl.length() == 0) throw new Exception("This repository (or ref) has no commits");
        JSONObject c0 = cl.getJSONObject(0);
        s.headSha = c0.optString("sha");
        s.treeSha = c0.getJSONObject("commit").getJSONObject("tree").optString("sha");
        JSONObject t = api.getObj(GitHubApi.repoPath(s.owner, s.name) + "/git/trees/" + s.treeSha + "?recursive=1");
        s.treeTruncated = t.optBoolean("truncated");
        JSONArray arr = t.optJSONArray("tree");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type"), path = e.optString("path");
            if (type.equals("blob")) {
                String mode = e.optString("mode", "100644");
                if (mode.equals("120000")) s.tree.symlinks++;
                s.tree.add(path, e.optLong("size"), e.optString("sha"), mode);
                String lp = path.toLowerCase();
                if (lp.startsWith(".github/workflows/") && (lp.endsWith(".yml") || lp.endsWith(".yaml")) && path.indexOf('/', 18) < 0) s.workflowPaths.add(path);
            } else if (type.equals("commit")) s.tree.submodules++;
        }
        s.tree.recount();
        if (s.treeTruncated) s.notes.add("GitHub truncated the tree (very large repo). The list is partial – Import everything still copies all files.");
        if (s.tree.submodules > 0) s.notes.add(s.tree.submodules + " submodule(s) are not copied (their content lives in other repos; .gitmodules is kept).");

        if (step != null) step.on("Reading branches and tags…");
        try { s.branches.addAll(api.branches(s.owner, s.name)); } catch (Exception ignored) { }
        try { s.tags.addAll(api.tags(s.owner, s.name)); } catch (Exception ignored) { }
        if (step != null) step.on("Reading releases…");
        try { s.releases.addAll(api.releases(s.owner, s.name)); } catch (Exception ignored) { }
        if (step != null) step.on("Reading commits…");
        try { s.commits.addAll(api.commits(s.owner, s.name, s.ref, 1, null)); } catch (Exception ignored) { }
        if (step != null) step.on("Reading Actions…");
        try { s.runs.addAll(api.runs(s.owner, s.name, 8)); } catch (Exception ignored) { }
        if (step != null) step.on("Reading issues…");
        try {
            JSONObject a = api.getObj("/search/issues?q=" + GitHubApi.enc2("repo:" + s.full() + " is:issue") + "&per_page=1");
            s.issueTotal = a.optInt("total_count");
            JSONObject b = api.getObj("/search/issues?q=" + GitHubApi.enc2("repo:" + s.full() + " is:issue is:open") + "&per_page=1");
            s.issueOpen = b.optInt("total_count");
        } catch (Exception ignored) { }
        try { s.labelCount = api.getAll(GitHubApi.repoPath(s.owner, s.name) + "/labels", null, 3).length(); } catch (Exception ignored) { }
        if (step != null) step.on("Reading secrets and variables…");
        try { for (SecretInfo si : api.secrets(s.owner, s.name)) s.secretNames.add(si.name); } catch (Exception ignored) { }
        try { s.variables.addAll(api.variables(s.owner, s.name)); } catch (Exception ignored) { }
        Pattern sp = Pattern.compile("secrets\\.([A-Za-z_][A-Za-z0-9_]*)"), vp = Pattern.compile("\\bvars\\.([A-Za-z_][A-Za-z0-9_]*)");
        int scanned = 0;
        for (String wp : s.workflowPaths) {
            if (scanned++ >= 25) break;
            try {
                Tree.Node n = s.tree.file(wp);
                if (n == null || n.size > 300_000) continue;
                String txt = new String(api.blobBytes(s.owner, s.name, n.sha), "UTF-8");
                Matcher m = sp.matcher(txt);
                while (m.find()) if (!m.group(1).equals("GITHUB_TOKEN")) s.refSecrets.add(m.group(1));
                m = vp.matcher(txt);
                while (m.find()) s.refVars.add(m.group(1));
            } catch (Exception ignored) { }
        }
        for (String n : s.secretNames) s.refSecrets.add(n);
        return s;
    }
}
