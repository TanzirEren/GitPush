package com.gitpush.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;

public class OnboardingActivity extends BaseActivity {
    private static final String[][] PAGES = {
            {"☁", "Push from your phone", "Pick files and folders from your storage and push them to any of your GitHub repositories in a single, clean commit."},
            {"🗂", "Folders & ZIP, done right", "Folder structure is preserved exactly. Drop a ZIP and GitPush extracts it, lets you review and delete files, then pushes."},
            {"🔒", "Safe, fast & beautiful", "Tokens are stored with Android Keystore encryption. Multiple accounts, code editor, commit history and a glassy modern UI."}
    };
    private int index = 0;
    private TextView emoji, title, text, next;
    private LinearLayout dots, pageBox;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.vbox(this);
        root.setFitsSystemWindows(true);
        LinearLayout top = Ui.hbox(this);
        top.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        top.addView(Ui.textButton(this, "Skip", R.attr.gpMuted, v -> finishOnboarding()));
        root.addView(top, Ui.lp(Ui.MATCH, Ui.WRAP));

        pageBox = Ui.vbox(this);
        pageBox.setGravity(Gravity.CENTER);
        pageBox.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        emoji = Ui.text(this, "", 64, false, R.attr.gpText);
        emoji.setGravity(Gravity.CENTER);
        emoji.setBackgroundResource(R.drawable.bg_circle);
        title = Ui.text(this, "", 24, true, R.attr.gpText);
        title.setGravity(Gravity.CENTER);
        text = Ui.text(this, "", 15, false, R.attr.gpMuted);
        text.setGravity(Gravity.CENTER);
        pageBox.addView(emoji, Ui.lp(Ui.dp(this, 180), Ui.dp(this, 180)));
        pageBox.addView(title, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 32, 0, 0));
        pageBox.addView(text, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 12, 0, 0));
        root.addView(pageBox, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        dots = Ui.hbox(this);
        dots.setGravity(Gravity.CENTER);
        root.addView(dots, Ui.lp(Ui.MATCH, Ui.WRAP));
        next = Ui.button(this, "Next", v -> advance());
        root.addView(next, Ui.lp(this, Ui.MATCH, Ui.WRAP, 32, 16, 32, 24));
        setContentView(root);

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent a, MotionEvent c, float vx, float vy) {
                if (Math.abs(vx) < 300) return false;
                if (vx < 0) advance(); else if (index > 0) { index--; show(); }
                return true;
            }
        });
        pageBox.setOnTouchListener((v, e) -> gd.onTouchEvent(e) || true);
        show();
    }

    private void advance() { if (index >= PAGES.length - 1) finishOnboarding(); else { index++; show(); } }

    private void show() {
        pageBox.setAlpha(0f); pageBox.setTranslationX(Ui.dp(this, 40));
        emoji.setText(PAGES[index][0]); title.setText(PAGES[index][1]); text.setText(PAGES[index][2]);
        pageBox.animate().alpha(1f).translationX(0).setDuration(280).start();
        next.setText(index == PAGES.length - 1 ? "Get started" : "Next");
        dots.removeAllViews();
        for (int i = 0; i < PAGES.length; i++) {
            View d = new View(this);
            d.setBackgroundResource(i == index ? R.drawable.bg_chip_on : R.drawable.bg_chip_off);
            dots.addView(d, Ui.lp(this, Ui.dp(this, i == index ? 26 : 8) / 1, Ui.dp(this, 8), 4, 0, 4, 0));
        }
    }

    private void finishOnboarding() {
        st.finishOnboarding();
        startActivity(new Intent(this, st.active == null ? LoginActivity.class : MainActivity.class));
        finish();
    }
}
