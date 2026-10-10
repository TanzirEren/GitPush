package com.gitpush.app;

import android.net.Uri;

import androidx.appcompat.app.AppCompatDelegate;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.*;
import com.gitpush.app.data.Pusher;
import com.gitpush.app.data.Pusher.PushSession;
import com.gitpush.app.data.Stager;
import com.gitpush.app.data.Stager.StagedFile;
import com.gitpush.app.data.Store;
import com.gitpush.app.ui.Ui;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Single in-memory source of truth shared by all screens. Mutate on the main thread only. */
public class AppState {
    private final GitPushApp app;
    public final Store store;

    public String theme;
    public int accent;
    public boolean compact, haptics, onboardingDone;
    public String clientId;

    public final List<Account> accounts;
    public Account active;
    private GitHubApi apiCache;
    private String apiToken = "";

    public final List<Repo> repos = new ArrayList<>();
    public boolean reposLoading, reposLoaded;
    public String reposError;

    public final List<HistoryEntry> history;

    public final List<StagedFile> staged = new ArrayList<>();
    public String stagedFor;
    public boolean sharedBanner, stageBusy;

    public PushSession session;

    public String viewerPath = "", viewerSha = "", viewerBranch = "", browserBranch;

    public AppState(GitPushApp app) {
        this.app = app;
        store = new Store(app);
        theme = store.theme(); accent = store.accent(); compact = store.compact(); haptics = store.haptics();
        onboardingDone = store.onboardingDone(); clientId = store.clientId();
        Ui.hapticsOn = haptics;
        accounts = new ArrayList<>(store.loadAccounts());
        String last = store.activeLogin();
        for (Account a : accounts) if (a.login.equals(last)) active = a;
        if (active == null && !accounts.isEmpty()) active = accounts.get(0);
        history = new ArrayList<>(store.loadHistory());
    }

    // ---------------------------------------------------------------- settings
    public void applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(theme.equals("dark") ? AppCompatDelegate.MODE_NIGHT_YES
                : theme.equals("light") ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    public void setThemeMode(String v) { theme = v; store.theme(v); applyNightMode(); }
    public void setAccent(int v) { accent = v; store.accent(v); }
    public void setCompact(boolean v) { compact = v; store.compact(v); }
    public void setHaptics(boolean v) { haptics = v; Ui.hapticsOn = v; store.haptics(v); }
    public void finishOnboarding() { onboardingDone = true; store.onboardingDone(true); }
    public void saveClientId(String id) { clientId = id; store.clientId(id); }

    // ---------------------------------------------------------------- accounts
    public GitHubApi api() {
        if (active == null) return null;
        if (apiCache == null || !apiToken.equals(active.token)) { apiCache = new GitHubApi(active.token); apiToken = active.token; }
        return apiCache;
    }

    private void persistAccounts() { store.saveAccounts(accounts, active == null ? null : active.login); }

    public void addOrUpdateAccount(Account a) {
        int idx = -1;
        for (int i = 0; i < accounts.size(); i++) if (accounts.get(i).login.equals(a.login)) idx = i;
        if (idx >= 0) accounts.set(idx, a); else accounts.add(a);
        active = a; repos.clear(); reposLoaded = false; reposError = null;
        persistAccounts();
    }

    public void switchTo(String login) {
        for (Account a : accounts) if (a.login.equals(login) && (active == null || !active.login.equals(login))) {
            active = a; repos.clear(); reposLoaded = false; reposError = null; persistAccounts();
        }
    }

    public void logout(String login) {
        for (int i = accounts.size() - 1; i >= 0; i--) if (accounts.get(i).login.equals(login)) accounts.remove(i);
        if (active != null && active.login.equals(login)) { active = accounts.isEmpty() ? null : accounts.get(0); repos.clear(); reposLoaded = false; }
        persistAccounts();
    }

    /** Validates the token and stores the account. cb gets null on success or an error message. */
    public void loginWithToken(String token, String type, Ui.Cb<String> cb) {
        final String t = token.trim();
        if (t.isEmpty()) { cb.on("Paste your token first."); return; }
        app.io.execute(() -> {
            String err = null;
            try {
                String[] scopes = new String[1];
                JSONObject u = new GitHubApi(t).user(scopes);
                String nm = u.isNull("name") ? null : u.optString("name");
                Account acc = new Account(u.optString("login"), nm, u.optString("avatar_url"), t, scopes[0], type);
                app.main.post(() -> { addOrUpdateAccount(acc); cb.on(null); });
                return;
            } catch (Exception e) { err = e.getMessage() != null ? e.getMessage() : "Login failed"; }
            final String fe = err;
            app.main.post(() -> cb.on(fe));
        });
    }

    // ---------------------------------------------------------------- repos
    public void loadRepos(boolean force, Runnable done) {
        if (active == null || reposLoading || (reposLoaded && !force)) { if (done != null && !reposLoading) done.run(); return; }
        reposLoading = true; reposError = null;
        final GitHubApi a = api();
        app.io.execute(() -> {
            List<Repo> list = null; String err = null;
            try { list = a.repos(); } catch (Exception e) { err = e.getMessage(); }
            final List<Repo> fl = list; final String fe = err;
            app.main.post(() -> {
                reposLoading = false;
                if (fl != null) { repos.clear(); repos.addAll(fl); reposLoaded = true; } else reposError = fe;
                if (done != null) done.run();
            });
        });
    }

    public Repo findRepo(String owner, String name) {
        for (Repo r : repos) if (r.owner.equals(owner) && r.name.equals(name)) return r;
        return null;
    }

    // ---------------------------------------------------------------- history
    public void addHistory(HistoryEntry e) {
        history.add(0, e);
        while (history.size() > 300) history.remove(history.size() - 1);
        store.saveHistory(history);
    }
    public void clearHistory() { history.clear(); store.saveHistory(history); }

    // ---------------------------------------------------------------- staging
    public void openUpload(Repo r) {
        if (stagedFor != null && !stagedFor.equals(r.fullName)) staged.clear();
        stagedFor = r.fullName; sharedBanner = false;
    }

    private void addStaged(List<StagedFile> list) {
        Set<String> keys = new HashSet<>(), paths = new HashSet<>();
        for (StagedFile f : staged) { keys.add(f.key()); paths.add(f.path); }
        int dup = 0;
        for (StagedFile f : list) {
            if (keys.contains(f.key()) || paths.contains(f.path)) { dup++; continue; }
            staged.add(f); keys.add(f.key()); paths.add(f.path);
        }
        Ui.toast(app, dup > 0 ? (list.size() - dup) + " added, " + dup + " duplicate(s) ignored" : list.size() + " item(s) added");
    }

    private interface Loader { List<StagedFile> load() throws Exception; }

    private void stage(Loader l, Runnable changed) {
        stageBusy = true;
        if (changed != null) changed.run();
        app.io.execute(() -> {
            List<StagedFile> res = null; String err = null;
            try { res = l.load(); } catch (Exception e) { err = e.getMessage() != null ? e.getMessage() : "Could not read files"; }
            final List<StagedFile> fr = res; final String fe = err;
            app.main.post(() -> {
                stageBusy = false;
                if (fr != null) { if (fr.isEmpty()) Ui.toast(app, "Nothing usable found"); else addStaged(fr); } else Ui.toast(app, fe);
                if (changed != null) changed.run();
            });
        });
    }

    public void addFiles(List<Uri> uris, Runnable changed) { if (!uris.isEmpty()) stage(() -> Stager.filesFromUris(app, uris), changed); }
    public void addFolder(Uri uri, Runnable changed) { if (uri != null) stage(() -> Stager.folderFromTree(app, uri), changed); }
    public void addZip(Uri uri, Runnable changed) { if (uri != null) stage(() -> Stager.extractZip(app, uri), changed); }

    public void addGenerated(String path, String text) {
        byte[] b = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = staged.size() - 1; i >= 0; i--) if (staged.get(i).path.equals(path)) staged.remove(i);
        staged.add(new StagedFile(path, b.length, null, null, b));
        Ui.toast(app, path + " added to the upload list");
    }

    public void removeStaged(Set<String> paths) {
        for (int i = staged.size() - 1; i >= 0; i--) {
            String sp = staged.get(i).path;
            for (String p : paths) if (sp.equals(p) || sp.startsWith(p + "/")) { staged.remove(i); break; }
        }
    }

    public void receiveShared(List<Uri> uris) {
        if (uris.isEmpty()) return;
        stagedFor = null; staged.clear(); sharedBanner = true;
        addFiles(uris, null);
    }

    // ---------------------------------------------------------------- push
    public void startPush(Repo repo, String branch, String dir, String message, String description) {
        PushSession s = new PushSession(repo, branch, dir, new ArrayList<>(staged), message.trim(), description.trim());
        session = s;
        runPush(s);
    }

    public void retryPush() { if (session != null && !session.running) runPush(session); }
    public void cancelPush() { if (session != null) session.cancel(); }
    public void closeSession() { if (session != null && !session.running) session = null; }

    private void runPush(PushSession s) {
        final GitHubApi a = new GitHubApi(active.token);
        s.running = true;
        app.io.execute(() -> {
            Pusher.run(app, a, s);
            app.main.post(() -> afterPush(s));
        });
    }

    private void afterPush(PushSession s) {
        if (s.finished && !s.cancelled) {
            if (!s.noChanges) {
                HistoryEntry h = new HistoryEntry();
                h.repoFullName = s.repo.fullName; h.branch = s.branch; h.targetDir = s.targetDir; h.message = s.message;
                h.fileCount = s.count(Pusher.DONE); h.bytes = s.totalBytes(); h.sha = s.commitSha == null ? "" : s.commitSha;
                h.url = s.commitUrl == null ? "" : s.commitUrl; h.time = System.currentTimeMillis(); h.success = true;
                addHistory(h);
                Notifier.show(app, "Pushed to " + s.repo.name, s.message + " • " + h.sha.substring(0, Math.min(7, h.sha.length())));
                staged.clear();
            }
        } else if (s.error != null) {
            HistoryEntry h = new HistoryEntry();
            h.repoFullName = s.repo.fullName; h.branch = s.branch; h.targetDir = s.targetDir; h.message = s.message;
            h.fileCount = s.files.size(); h.bytes = s.totalBytes(); h.time = System.currentTimeMillis(); h.success = false; h.error = s.error;
            addHistory(h);
            Notifier.show(app, "Upload failed", s.error);
        }
    }
}
