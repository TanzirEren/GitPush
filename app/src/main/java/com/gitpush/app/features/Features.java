package com.gitpush.app.features;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registry of all control features. Repo-level kinds first, then account-level kinds. */
public final class Features {
    private Features() {}

    public static final String[] REPO = {"issues", "pulls", "branches", "access", "hooks", "keys", "labels", "insights", "settings", "pages", "cleaner", "envs", "alerts"};
    public static final String[] ACCOUNT = {"gists", "notifs", "starred", "bulk", "search", "profile"};

    public static Feature create(String kind) {
        if (kind == null) return null;
        switch (kind) {
            case "issues": return new IssuesFeature();
            case "pulls": return new PullsFeature();
            case "branches": return new BranchesFeature();
            case "access": return new AccessFeature();
            case "hooks": return new HooksFeature();
            case "keys": return new KeysFeature();
            case "labels": return new LabelsFeature();
            case "insights": return new InsightsFeature();
            case "settings": return new SettingsFeature();
            case "pages": return new PagesFeature();
            case "cleaner": return new CleanerFeature();
            case "envs": return new EnvsFeature();
            case "alerts": return new AlertsFeature();
            case "gists": return new GistsFeature();
            case "notifs": return new NotifsFeature();
            case "starred": return new StarredFeature();
            case "bulk": return new BulkFeature();
            case "search": return new SearchFeature();
            case "profile": return new ProfileFeature();
            default: return null;
        }
    }

    /** kind -> instance for hub metadata. */
    public static Map<String, Feature> meta(String[] kinds) {
        Map<String, Feature> m = new LinkedHashMap<>();
        for (String k : kinds) { Feature f = create(k); if (f != null) m.put(k, f); }
        return m;
    }
}
