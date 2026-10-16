package com.gitpush.app.ui;

import android.app.Activity;

import com.gitpush.app.AppState;
import com.gitpush.app.GitPushApp;
import com.gitpush.app.data.Models.Repo;

import java.util.ArrayList;
import java.util.List;

/** "Choose a repository" glass list (favourites first). */
public final class RepoPicker {
    private RepoPicker() {}

    public static void pick(final Activity a, final String title, final Ui.Cb<Repo> cb) {
        final AppState st = GitPushApp.get().state;
        if (st.repos.isEmpty()) {
            Ui.toast(a, "Loading repositories…");
            st.loadRepos(true, () -> { if (!st.repos.isEmpty() && !a.isFinishing()) show(a, title, cb); else Ui.toast(a, st.reposError == null ? "No repositories found" : st.reposError); });
            return;
        }
        show(a, title, cb);
    }

    private static void show(Activity a, String title, Ui.Cb<Repo> cb) {
        AppState st = GitPushApp.get().state;
        final List<Repo> sorted = new ArrayList<>();
        for (Repo r : st.repos) if (st.isFavorite(r.fullName)) sorted.add(r);
        for (Repo r : st.repos) if (!st.isFavorite(r.fullName)) sorted.add(r);
        String[] labels = new String[sorted.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = (st.isFavorite(sorted.get(i).fullName) ? "⭐  " : sorted.get(i).isPrivate ? "🔒  " : "      ") + sorted.get(i).fullName;
        Ui.pickList(a, title, labels, i -> cb.on(sorted.get(i)));
    }
}
