package com.wrist.tingji.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Lightweight rolling level bars; no animation thread, driven by UI polling. */
public class WaveformView extends View {
    private static final int BARS = 42;
    private final float[] levels = new float[BARS];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int accent = Ui.ACCENT;

    public WaveformView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setAccent(int color) {
        accent = color;
        invalidate();
    }

    public void push(float level) {
        float v = Math.max(0f, Math.min(1f, level));
        System.arraycopy(levels, 1, levels, 0, BARS - 1);
        levels[BARS - 1] = v;
        invalidate();
    }

    public void decay() {
        for (int i = 0; i < BARS; i++) levels[i] *= 0.86f;
        invalidate();
    }

    public void clear() {
        for (int i = 0; i < BARS; i++) levels[i] = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float gap = Ui.dp(getContext(), 3f);
        float bw = Math.max(Ui.dp(getContext(), 1.5f),
                (w - gap * (BARS - 1)) / (float) BARS);
        float center = h / 2f;
        boolean idle = true;
        for (float lv : levels) {
            if (lv > 0.015f) {
                idle = false;
                break;
            }
        }
        if (idle) {
            paint.setColor(accent);
            paint.setAlpha(90);
            for (int i = -1; i <= 1; i++) {
                canvas.drawCircle(w / 2f + i * Ui.dp(getContext(), 5f), center,
                        Ui.dp(getContext(), 1.4f), paint);
            }
            return;
        }
        for (int i = 0; i < BARS; i++) {
            float v = levels[i];
            float bh = Ui.dp(getContext(), 2.2f) + v * (h * 0.42f);
            if (bh > h * 0.92f) bh = h * 0.92f;
            int a = (int) (70 + 185 * (i / (float) BARS));
            paint.setColor(accent);
            paint.setAlpha(a);
            float x = i * (bw + gap);
            canvas.drawRoundRect(x, center - bh, x + bw, center + bh,
                    bw / 2f, bw / 2f, paint);
        }
        paint.setAlpha(40);
        paint.setColor(accent);
        canvas.drawRect(0, center - Ui.dp(getContext(), 0.5f), w,
                center + Ui.dp(getContext(), 0.5f), paint);
    }
}
