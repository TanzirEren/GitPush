package com.gitpush.app.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gitpush.app.AppState;
import com.gitpush.app.GitPushApp;
import com.gitpush.app.R;

public abstract class BaseActivity extends AppCompatActivity {
    protected GitPushApp app;
    protected AppState st;
    protected LinearLayout actionsBox;
    protected TextView titleTv, subTv;
    protected FrameLayout body;

    @Override protected void onCreate(Bundle b) {
        app = GitPushApp.get();
        st = app.state;
        setTheme(Ui.themeFor(st.accent));
        super.onCreate(b);
    }

    /** Standard screen: header (back, title, subtitle, actions) + body container. */
    protected FrameLayout buildScreen(String title, String sub, boolean back) {
        LinearLayout page = Ui.vbox(this);
        page.setFitsSystemWindows(true);
        LinearLayout header = Ui.hbox(this);
        header.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        if (back) {
            ImageView bk = new ImageView(this);
            bk.setImageResource(R.drawable.ic_back);
            bk.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
            bk.setBackgroundResource(R.drawable.bg_ripple_row);
            bk.setOnClickListener(v -> onBackPressed());
            header.addView(bk, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
        } else header.addView(Ui.space(this, 1), Ui.lp(Ui.dp(this, 12), 1));
        LinearLayout titles = Ui.vbox(this);
        titleTv = Ui.text(this, title, 17, true, R.attr.gpText);
        titleTv.setSingleLine(true); titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subTv = Ui.muted(this, sub == null ? "" : sub);
        subTv.setSingleLine(true); subTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subTv.setVisibility(sub == null ? View.GONE : View.VISIBLE);
        titles.addView(titleTv); titles.addView(subTv);
        header.addView(titles, Ui.weight(1f));
        actionsBox = Ui.hbox(this);
        actionsBox.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(actionsBox);
        page.addView(header, Ui.lp(Ui.MATCH, Ui.WRAP));
        body = new FrameLayout(this);
        page.addView(body, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        setContentView(page);
        return body;
    }

    protected void setSubtitle(String s) { subTv.setText(s); subTv.setVisibility(s == null ? View.GONE : View.VISIBLE); }

    protected ImageView addAction(int iconRes, View.OnClickListener l) {
        ImageView v = new ImageView(this);
        v.setImageResource(iconRes);
        v.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        v.setBackgroundResource(R.drawable.bg_ripple_row);
        v.setOnClickListener(l);
        actionsBox.addView(v, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));
        return v;
    }

    protected TextView addTextAction(String label, View.OnClickListener l) {
        TextView t = Ui.text(this, label, 14, true, R.attr.gpPrimary);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        t.setOnClickListener(l);
        actionsBox.addView(t);
        return t;
    }

    protected boolean alive() { return !isFinishing() && !isDestroyed(); }

    protected <T> void async(Ui.Task<T> task, Ui.Cb<T> ok, Ui.Cb<Exception> err) {
        app.io.execute(() -> {
            try {
                final T r = task.run();
                app.main.post(() -> { if (alive() && ok != null) ok.on(r); });
            } catch (Exception e) {
                app.main.post(() -> { if (alive()) { if (err != null) err.on(e); else Ui.toast(this, e.getMessage()); } });
            }
        });
    }

    protected static String msg(Exception e) { return e.getMessage() != null ? e.getMessage() : e.toString(); }
}
