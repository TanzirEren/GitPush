package com.gitpush.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gitpush.app.R;

/** Small glass progress dialog used for extraction, downloads and uploads. */
public class ProgressDlg {
    private final GlassDialog dialog;
    private final TextView label;
    private final ProgressBar bar;

    public ProgressDlg(Context c, String title, String icon, final Runnable onCancel) {
        LinearLayout box = Ui.vbox(c);
        label = Ui.text(c, "Starting…", 13, false, R.attr.gpMuted);
        bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setIndeterminate(true);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.color(c, R.attr.gpPrimary)));
        bar.setIndeterminateTintList(ColorStateList.valueOf(Ui.color(c, R.attr.gpPrimary)));
        box.addView(bar, Ui.lp(Ui.MATCH, Ui.WRAP));
        box.addView(label, Ui.lp(c, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        GlassDialog d = new GlassDialog(c).setTitle(title).setIcon(icon).setView(box).setCancelable(false);
        if (onCancel != null) d.setNegativeButton("Cancel", (x, w) -> onCancel.run());
        dialog = d.show();
    }

    /** frac < 0 = indeterminate. */
    public void set(String text, float frac) {
        label.setText(text);
        if (frac < 0) bar.setIndeterminate(true);
        else { bar.setIndeterminate(false); bar.setProgress((int) (frac * 1000)); }
    }

    public void close() { dialog.dismiss(); }
}
