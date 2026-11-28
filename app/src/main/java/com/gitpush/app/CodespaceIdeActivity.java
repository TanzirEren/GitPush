package com.gitpush.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.gitpush.app.data.CodespacesApi;
import com.gitpush.app.data.Snippets;
import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.CsIdeView;
import com.gitpush.app.ui.Ui;

import org.json.JSONObject;

/** Full-screen mobile VS Code for one codespace (phones). Tablets embed the same CsIdeView in the hub. */
public class CodespaceIdeActivity extends BaseActivity {
    public static void open(Context c, JSONObject cs) {
        c.startActivity(new Intent(c, CodespaceIdeActivity.class).putExtra("cs", cs.toString()));
    }

    private CsIdeView ide;
    private String csName = "";
    private boolean fullscreen;
    private TextView exitFs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        JSONObject cs;
        try { cs = new JSONObject(getIntent().getStringExtra("cs")); } catch (Exception e) { finish(); return; }
        csName = cs.optString("name");
        buildScreen(cs.optString("display_name", csName), "VS Code", true);
        ide = new CsIdeView(this, new CsIdeView.Callbacks() {
            @Override public void onTitle(String title, String sub) { titleTv.setText(title); setSubtitle(sub); }
            @Override public void onReady(String name) { }
        });
        body.addView(ide, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        addAction(R.drawable.i_refresh, v -> ide.reload());
        addAction(R.drawable.i_terminal, v -> ide.togglePad());
        addAction(R.drawable.i_external, v -> toggleFullscreen());
        addAction(R.drawable.i_more, v -> ide.showMenu(this::finish));

        exitFs = Ui.text(this, "Exit full screen", 12, true, R.attr.gpOnPrimary);
        exitFs.setBackgroundResource(R.drawable.bg_chip_on);
        exitFs.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        exitFs.setVisibility(View.GONE);
        exitFs.setOnClickListener(v -> toggleFullscreen());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.END);
        lp.topMargin = Ui.dp(this, 8); lp.rightMargin = Ui.dp(this, 8);
        body.addView(exitFs, lp);

        int remind = Snippets.remindMinutes(st.store);
        if (remind > 0) CsReminderReceiver.schedule(this, csName, remind);
        ide.open(cs);
    }

    @SuppressWarnings("deprecation")
    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        ViewGroup page = (ViewGroup) body.getParent();
        page.getChildAt(0).setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        page.getChildAt(1).setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        page.setFitsSystemWindows(!fullscreen);
        exitFs.setVisibility(fullscreen ? View.VISIBLE : View.GONE);
        int flags = fullscreen ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE : View.SYSTEM_UI_FLAG_VISIBLE;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        page.requestLayout();
    }

    @Override protected void onResume() { super.onResume(); if (ide != null) ide.resume(); }

    @Override public void onBackPressed() {
        if (ide != null && ide.back()) return;
        if (fullscreen) { toggleFullscreen(); return; }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (ide != null) ide.destroy();
        if (isFinishing() && Snippets.stopOnLeave(st.store) && !csName.isEmpty() && st.api() != null) {
            final CodespacesApi api = new CodespacesApi(st.api());
            final String n = csName;
            app.io.execute(() -> { try { api.stop(n); } catch (Exception ignored) { } });
            CsReminderReceiver.cancel(this, n);
        }
        super.onDestroy();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == CsIdeView.REQ_FILE && ide != null) ide.onFileResult(res, data);
    }
}
