package com.gitpush.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Tiny animated bar chart (no dependencies). */
public class BarChartView extends View {
    private float[] values = new float[0];
    private String[] labels = new String[0];
    private int c1, c2, textColor, trackColor;
    private float progress = 1f;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG), textPaint = new Paint(Paint.ANTI_ALIAS_FLAG), trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public BarChartView(Context c) {
        super(c);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(Ui.dp(c, 11));
    }

    public void setData(float[] v, String[] l, int colorA, int colorB, int text, int track) {
        values = v; labels = l; c1 = colorA; c2 = colorB; textColor = text; trackColor = track;
        textPaint.setColor(text);
        trackPaint.setColor(track);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(800);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(an -> { progress = (float) an.getAnimatedValue(); invalidate(); });
        a.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        int n = values.length;
        if (n == 0) return;
        float w = getWidth(), h = getHeight();
        float labelH = Ui.dp(getContext(), 20), topPad = Ui.dp(getContext(), 16);
        float chartH = h - labelH - topPad;
        float slot = w / n, barW = slot * 0.5f;
        float max = 1f;
        for (float x : values) max = Math.max(max, x);
        float r = Ui.dp(getContext(), 8);
        for (int i = 0; i < n; i++) {
            float left = i * slot + (slot - barW) / 2f;
            rect.set(left, topPad, left + barW, topPad + chartH);
            canvas.drawRoundRect(rect, r, r, trackPaint);
            float bh = Math.max(values[i] > 0 ? Ui.dp(getContext(), 6) : 0, chartH * (values[i] / max) * progress);
            if (bh > 0) {
                rect.set(left, topPad + chartH - bh, left + barW, topPad + chartH);
                barPaint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, c2, c1, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, r, r, barPaint);
            }
            if (values[i] > 0 && progress > 0.9f) canvas.drawText(String.valueOf((int) values[i]), left + barW / 2f, topPad + chartH - bh - Ui.dp(getContext(), 4), textPaint);
            canvas.drawText(i < labels.length ? labels[i] : "", left + barW / 2f, h - Ui.dp(getContext(), 4), textPaint);
        }
    }
}
