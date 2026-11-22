package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.features.Feature;
import com.gitpush.app.features.Features;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;

import java.util.Map;

/** Control center: every GitHub feature for one repository, or the account-level toolbox when no repo is given. */
public class ControlActivity extends BaseActivity {
    public static void open(Context c, String owner, String repo) {
        c.startActivity(new Intent(c, ControlActivity.class).putExtra("owner", owner == null ? "" : owner).putExtra("repo", repo == null ? "" : repo));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String owner = getIntent().getStringExtra("owner"), repo = getIntent().getStringExtra("repo");
        final boolean account = repo.isEmpty();
        buildScreen(account ? "GitHub toolbox" : "Control center", account ? "Your account" : owner + "/" + repo, true);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(col);
        body.addView(sv);
        Map<String, Feature> m = Features.meta(account ? Features.ACCOUNT : Features.REPO);
        for (final Map.Entry<String, Feature> e : m.entrySet()) {
            Feature f = e.getValue();
            card(col, f.icon() + "  " + f.title(), f.blurb(), v -> FeatureActivity.open(this, e.getKey(), owner, repo));
        }
        if (!account) {
            card(col, "🖼  Social preview", "Prepare and upload the repo's social card (1280×640)", v -> SocialPreviewActivity.open(this, owner, repo));
            card(col, "🏷  Release order", "Put any release first, last or in between", v -> ReleaseOrderActivity.open(this, owner, repo));
            card(col, "🔑  APK signing keys", "Generate a keystore and push the four secrets", v -> KeyToolActivity.open(this));
        } else {
            card(col, "🔑  APK signing keys", "Generate a keystore and push the four secrets", v -> KeyToolActivity.open(this));
        }
    }

    private void card(LinearLayout col, String title, String sub, android.view.View.OnClickListener l) {
        LinearLayout c = Ui.card(this);
        TextView t = Ui.title(this, title);
        Ui.setTextWithIcon(t, title, 18);
        c.addView(t);
        c.addView(Ui.muted(this, sub), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 3, 0, 0));
        c.setOnClickListener(v -> { Ui.haptic(v); l.onClick(v); });
        col.addView(c, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 10));
    }
}
