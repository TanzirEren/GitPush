package com.gitpush.app;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;

public class GuideActivity extends BaseActivity {
    // {title, text, link (optional), linkLabel (optional)}
    private static final String[][] OAUTH = {
            {"Open GitHub Developer settings", "Sign in to GitHub in your browser, then open Settings → Developer settings → OAuth Apps.", "https://github.com/settings/developers", "Open Developer settings"},
            {"Create a new OAuth App", "Tap 'New OAuth App'. Application name: GitPush. Homepage URL: any valid URL (for example https://github.com). Authorization callback URL: https://github.com (it is not used by the Device Flow)."},
            {"Enable Device Flow", "After the app is registered, open its page and tick 'Enable Device Flow'. Then tap 'Update application'. This step is required."},
            {"Copy the Client ID", "On the same page copy the 'Client ID'. You do NOT need the client secret – never put it in a mobile app."},
            {"Paste it in GitPush", "On the login screen choose 'GitHub OAuth', paste the Client ID and tap 'Continue with GitHub'."},
            {"Authorize with the code", "GitPush shows a short code. Tap 'Copy code & open GitHub', paste the code on the GitHub page, press Continue and then Authorize. Return to GitPush – you are signed in automatically."},
            {"Good to know", "GitPush requests the 'repo' scope so it can push to your public and private repositories. You can revoke access any time in GitHub → Settings → Applications."}
    };
    private static final String[][] PAT = {
            {"Open the token page", "Sign in to GitHub in your browser and open Settings → Developer settings → Personal access tokens.", "https://github.com/settings/tokens/new?scopes=repo&description=GitPush", "Open 'New token (classic)'"},
            {"Choose a token type", "Easiest: 'Tokens (classic)' → Generate new token (classic). Advanced: 'Fine-grained tokens' limited to selected repositories."},
            {"Name it & set expiry", "Note: GitPush. Expiration: choose 30/90 days or custom – shorter is safer."},
            {"Select permissions", "Classic: tick the 'repo' scope (full control of private repositories). Fine-grained: select the repositories and set 'Contents' to Read and write, 'Metadata' to Read-only."},
            {"Generate and copy", "Tap 'Generate token'. Copy it immediately – GitHub shows it only once (it starts with ghp_ or github_pat_)."},
            {"Paste it in GitPush", "On the login screen choose 'Access Token', paste the token and tap 'Sign in'. GitPush verifies it with GitHub and stores it encrypted on your device."},
            {"Troubleshooting", "401 = token wrong/expired. 403 = missing permission or SSO not authorized (for organization repos open the token page and tap 'Configure SSO' → Authorize). 404 on a private repo = the token cannot see that repository."}
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean oauth = "oauth".equals(getIntent().getStringExtra("kind"));
        buildScreen(oauth ? "Get OAuth access" : "Get Personal Access Token", null, true);
        ScrollView sv = new ScrollView(this);
        LinearLayout list = Ui.vbox(this);
        list.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 24));
        String[][] steps = oauth ? OAUTH : PAT;
        for (int i = 0; i < steps.length; i++) {
            final String[] s = steps[i];
            LinearLayout card = Ui.card(this);
            LinearLayout row = Ui.hbox(this);
            row.setGravity(Gravity.TOP);
            TextView num = Ui.text(this, String.valueOf(i + 1), 14, true, R.attr.gpOnPrimary);
            num.setGravity(Gravity.CENTER);
            num.setBackgroundResource(R.drawable.bg_chip_on);
            row.addView(num, Ui.lp(Ui.dp(this, 32), Ui.dp(this, 32)));
            LinearLayout col = Ui.vbox(this);
            col.setPadding(Ui.dp(this, 14), 0, 0, 0);
            col.addView(Ui.title(this, s[0]));
            col.addView(Ui.text(this, s[1], 13, false, R.attr.gpMuted), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));
            if (s.length > 2) col.addView(Ui.textButton(this, s[3], R.attr.gpPrimary, v -> Ui.openUrl(this, s[2])));
            row.addView(col, Ui.weight(1f));
            card.addView(row);
            list.addView(card, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
        }
        sv.addView(list);
        body.addView(sv);
    }
}
