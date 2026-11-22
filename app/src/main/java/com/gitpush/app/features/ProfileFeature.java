package com.gitpush.app.features;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.gitpush.app.R;
import com.gitpush.app.ui.Ui;

import org.json.JSONObject;

/** Edit your GitHub profile: name, bio, company, location, website, X/Twitter, hireable. */
public class ProfileFeature extends Feature {
    private boolean hireable;

    @Override public String title() { return "Profile editor"; }
    @Override public String icon() { return "🪪"; }
    @Override public String blurb() { return "Name, bio, company, location, links"; }
    @Override public boolean needsRepo() { return false; }

    @Override public View view() {
        ScrollView sv = new ScrollView(a);
        final LinearLayout col = Ui.vbox(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 28));
        sv.addView(col);
        col.addView(Ui.muted(a, "Loading…"));
        a.run(() -> a.api.getObj("/user"), u -> {
            col.removeAllViews();
            LinearLayout c = Ui.card(a);
            final String[] keys = {"name", "bio", "company", "location", "blog", "twitter_username"};
            final String[] labels = {"Name", "Bio", "Company", "Location", "Website", "X / Twitter username"};
            final EditText[] ed = new EditText[keys.length];
            for (int i = 0; i < keys.length; i++) {
                c.addView(Ui.muted(a, labels[i]), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, i == 0 ? 0 : 10, 0, 0));
                ed[i] = Ui.input(a, labels[i], s(u, keys[i]));
                if (i != 1) ed[i].setSingleLine(true);
                c.addView(ed[i]);
            }
            hireable = u.optBoolean("hireable");
            c.addView(Ui.chip(a, "Available for hire", hireable, v -> { hireable = !hireable; ((android.widget.TextView) v).setText(hireable ? "✔ Available for hire" : "Available for hire"); }), Ui.lp(a, Ui.WRAP, Ui.WRAP, 0, 12, 0, 0));
            c.addView(Ui.button(a, "Save profile", v -> {
                try {
                    final JSONObject b = new JSONObject();
                    for (int i = 0; i < keys.length; i++) b.put(keys[i], ed[i].getText().toString().trim());
                    b.put("hireable", hireable);
                    a.run(() -> a.api.send("PATCH", "/user", b), r -> Ui.toast(a, "Profile saved"));
                } catch (Exception e) { Ui.toast(a, e.getMessage()); }
            }), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 14, 0, 0));
            col.addView(c);
            col.addView(Ui.muted(a, "Signed in as @" + s(u, "login") + " • " + u.optInt("public_repos") + " public repos • " + u.optInt("followers") + " followers"), Ui.lp(a, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        });
        return sv;
    }
}
