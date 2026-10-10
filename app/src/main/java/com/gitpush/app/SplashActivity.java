package com.gitpush.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gitpush.app.ui.BaseActivity;
import com.gitpush.app.ui.Ui;

public class SplashActivity extends BaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.vbox(this);
        root.setGravity(Gravity.CENTER);
        root.setFitsSystemWindows(true);

        FrameLayout logoBox = new FrameLayout(this);
        int big = Ui.dp(this, 210);
        View ring = new View(this);
        ring.setBackgroundResource(R.drawable.bg_circle);
        ring.setAlpha(0f);
        logoBox.addView(ring, new FrameLayout.LayoutParams(big, big, Gravity.CENTER));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_mark);
        logo.setScaleX(0.5f); logo.setScaleY(0.5f); logo.setAlpha(0f);
        logoBox.addView(logo, new FrameLayout.LayoutParams(Ui.dp(this, 130), Ui.dp(this, 130), Gravity.CENTER));
        root.addView(logoBox, Ui.lp(big, big));

        TextView name = Ui.text(this, "GitPush", 32, true, R.attr.gpText);
        name.setAlpha(0f);
        TextView tag = Ui.muted(this, "Push to GitHub from your pocket");
        tag.setAlpha(0f);
        root.addView(name, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 24, 0, 0));
        root.addView(tag);
        setContentView(root);

        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(new OvershootInterpolator(1.4f)).start();
        ring.animate().alpha(0.8f).setDuration(700).start();
        ObjectAnimator spin = ObjectAnimator.ofFloat(ring, "rotation", 0f, 360f);
        spin.setDuration(6000); spin.setRepeatCount(ObjectAnimator.INFINITE); spin.setInterpolator(new LinearInterpolator()); spin.start();
        name.animate().alpha(1f).setStartDelay(450).setDuration(500).start();
        tag.animate().alpha(1f).setStartDelay(650).setDuration(500).start();

        app.main.postDelayed(() -> {
            if (!alive()) return;
            Class<?> next = !st.onboardingDone ? OnboardingActivity.class : st.active == null ? LoginActivity.class : MainActivity.class;
            startActivity(new Intent(this, next));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 2000);
    }
}
