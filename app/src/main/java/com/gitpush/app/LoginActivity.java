package com.gitpush.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gitpush.app.data.GitHubApi;
import com.gitpush.app.data.Models.DeviceCode;
import com.gitpush.app.data.Models.PollResult;
import com.gitpush.app.data.OAuthDevice;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;

public class LoginActivity extends BaseActivity {
    private boolean adding;
    private int mode = 0;
    private LinearLayout panel;
    private TextView errorTv;
    private EditText clientIdIn, tokenIn;
    private boolean busy, tokenVisible;
    private volatile boolean polling;
    private DeviceCode device;
    private String deviceStatus = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        adding = getIntent().getBooleanExtra("add", false);
        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.setFillViewport(true);
        LinearLayout root = Ui.vbox(this);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 24));
        if (adding) {
            ImageView bk = new ImageView(this);
            bk.setImageResource(R.drawable.ic_back);
            bk.setOnClickListener(v -> finish());
            bk.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
            root.addView(bk, Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        }
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_mark);
        root.addView(logo, centered(Ui.dp(this, 76), Ui.dp(this, 76), 24, 0));
        TextView h = Ui.text(this, "Welcome to GitPush", 24, true, R.attr.gpText);
        root.addView(h, centered(Ui.WRAP, Ui.WRAP, 12, 0));
        root.addView(Ui.muted(this, "Sign in to your GitHub account"), centered(Ui.WRAP, Ui.WRAP, 2, 20));

        LinearLayout tabs = Ui.hbox(this);
        tabs.setGravity(Gravity.CENTER);
        root.addView(tabs, Ui.lp(Ui.MATCH, Ui.WRAP));
        panel = Ui.card(this);
        root.addView(panel, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 16, 0, 0));
        errorTv = Ui.text(this, "", 12, false, R.attr.gpError);
        errorTv.setVisibility(View.GONE);

        root.addView(Ui.outlineButton(this, "How to get OAuth access token", v -> guide("oauth")), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 20, 0, 0));
        root.addView(Ui.outlineButton(this, "How to get Personal Access Token", v -> guide("pat")), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        sv.addView(root);
        setContentView(sv);
        renderTabs(tabs);
        renderPanel();
    }

    private LinearLayout.LayoutParams centered(int w, int h, float top, float bottom) {
        LinearLayout.LayoutParams p = Ui.lp(this, w, h, 0, top, 0, bottom);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        return p;
    }

    private void renderTabs(LinearLayout tabs) {
        tabs.removeAllViews();
        tabs.addView(Ui.chip(this, "🛡 GitHub OAuth", mode == 0, v -> { mode = 0; renderTabs(tabs); renderPanel(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 6, 0));
        tabs.addView(Ui.chip(this, "🔑 Access Token", mode == 1, v -> { mode = 1; renderTabs(tabs); renderPanel(); }), Ui.lp(this, Ui.WRAP, Ui.WRAP, 6, 0, 0, 0));
    }

    private void guide(String kind) { startActivity(new Intent(this, GuideActivity.class).putExtra("kind", kind)); }

    private void showError(String e) {
        errorTv.setText(e == null ? "" : e);
        errorTv.setVisibility(e == null || e.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void renderPanel() {
        panel.removeAllViews();
        if (mode == 0) {
            if (device == null) {
                panel.addView(Ui.text(this, "OAuth App Client ID", 14, true, R.attr.gpText));
                clientIdIn = Ui.input(this, "Iv1.xxxxxxxxxxxxxxxx", st.clientId);
                panel.addView(clientIdIn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
                panel.addView(Ui.muted(this, "No client secret needed – GitPush uses GitHub's secure Device Flow."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 14));
                panel.addView(Ui.button(this, busy ? "Please wait…" : "Continue with GitHub", v -> startDeviceFlow()));
            } else {
                panel.addView(Ui.text(this, "Your code", 14, true, R.attr.gpText));
                TextView code = Ui.text(this, device.userCode, 32, true, R.attr.gpPrimary);
                code.setGravity(Gravity.CENTER);
                panel.addView(code, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 8));
                panel.addView(Ui.muted(this, "Copy the code, open GitHub, paste it and tap Authorize. GitPush signs in automatically."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));
                panel.addView(Ui.button(this, "Copy code & open GitHub", v -> { Ui.copy(this, "GitHub code", device.userCode); Ui.openUrl(this, device.verificationUri); }));
                LinearLayout row = Ui.hbox(this);
                row.addView(Ui.muted(this, deviceStatus), Ui.weight(1f));
                row.addView(Ui.textButton(this, "Cancel", R.attr.gpPrimary, v -> { polling = false; device = null; busy = false; renderPanel(); }));
                panel.addView(row, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            }
        } else {
            panel.addView(Ui.text(this, "Personal Access Token", 14, true, R.attr.gpText));
            tokenIn = Ui.input(this, "ghp_… or github_pat_…", tokenIn != null ? tokenIn.getText().toString() : "");
            tokenIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            tokenIn.setTransformationMethod(tokenVisible ? null : PasswordTransformationMethod.getInstance());
            panel.addView(tokenIn, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            panel.addView(Ui.textButton(this, tokenVisible ? "Hide token" : "Show token", R.attr.gpPrimary, v -> { tokenVisible = !tokenVisible; renderPanel(); }));
            panel.addView(Ui.muted(this, "Use a classic token with: repo, workflow, delete_repo, read:org, user (see the guide below)."), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 14));
            panel.addView(Ui.button(this, busy ? "Checking…" : "Sign in", v -> signInToken()));
        }
        panel.addView(errorTv, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
    }

    private void signInToken() {
        if (busy) return;
        busy = true; showError(null); renderPanel();
        st.loginWithToken(tokenIn.getText().toString(), "pat", err -> { busy = false; if (err == null) done(); else { renderPanel(); showError(err); } });
    }

    private void done() {
        if (adding) finish();
        else { startActivity(new Intent(this, MainActivity.class)); finish(); }
    }

    private void startDeviceFlow() {
        final String id = clientIdIn.getText().toString().trim();
        if (id.isEmpty()) { showError("Enter your OAuth App Client ID first."); return; }
        st.saveClientId(id);
        busy = true; showError(null); deviceStatus = "Contacting GitHub…"; renderPanel();
        polling = true;
        app.io.execute(() -> {
            try {
                final DeviceCode dc = OAuthDevice.start(id);
                app.main.post(() -> { device = dc; deviceStatus = "Waiting for you to authorize on GitHub…"; if (alive()) renderPanel(); });
                long interval = Math.max(dc.interval, 5);
                long deadline = System.currentTimeMillis() + dc.expiresIn * 1000L;
                while (polling && System.currentTimeMillis() < deadline) {
                    Thread.sleep(interval * 1000L);
                    if (!polling) return;
                    PollResult r = OAuthDevice.poll(id, dc.deviceCode);
                    if (r.kind == PollResult.TOKEN) {
                        final String token = r.value;
                        app.main.post(() -> {
                            polling = false; device = null; busy = false;
                            st.loginWithToken(token, "oauth", err -> { if (err == null) { if (alive()) done(); } else if (alive()) { renderPanel(); showError(err); } });
                        });
                        return;
                    }
                    if (r.kind == PollResult.SLOW_DOWN) interval += 5;
                    if (r.kind == PollResult.FAILED) { fail(r.value); return; }
                }
                if (polling) fail("The code expired. Start again.");
            } catch (Exception e) { fail(msg(e)); }
        });
    }

    private void fail(String m) {
        app.main.post(() -> { polling = false; device = null; busy = false; if (alive()) { renderPanel(); showError(m); } });
    }

    @Override protected void onDestroy() { polling = false; super.onDestroy(); }
}
