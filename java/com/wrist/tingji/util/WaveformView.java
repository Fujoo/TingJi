package com.wrist.tingji.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A slim, centre-symmetric waveform. Bars are narrow rounded vertical strokes,
 * low-brightness teal with a few mint highlights, and a single very soft glow
 * behind the whole shape rather than per-bar neon.
 */
public class WaveformView extends View {
    private static final int BARS = 48;
    private final float[] levels = new float[BARS];
    private final float[] targets = new float[BARS];
    private final float[] from = new float[BARS];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
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
        System.arraycopy(targets, 1, targets, 0, BARS - 1);
        targets[BARS - 1] = v;
        animateToTargets(260);
    }

    public void decay() {
        for (int i = 0; i < BARS; i++) targets[i] *= 0.72f;
        animateToTargets(300);
    }

    public void clear() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        for (int i = 0; i < BARS; i++) {
            levels[i] = 0f;
            targets[i] = 0f;
        }
        invalidate();
    }

    private void animateToTargets(long duration) {
        if (animator != null) animator.cancel();
        System.arraycopy(levels, 0, from, 0, BARS);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            for (int i = 0; i < BARS; i++) {
                levels[i] = from[i] + (targets[i] - from[i]) * p;
            }
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float center = h / 2f;
        boolean idle = true;
        for (float lv : levels) {
            if (lv > 0.012f) {
                idle = false;
                break;
            }
        }
        if (idle) {
            paint.setShader(null);
            paint.setColor(0xFF2A7F73);
            paint.setAlpha(105);
            for (int i = -1; i <= 1; i++) {
                canvas.drawCircle(w / 2f + i * Ui.dp(getContext(), 5.5f), center,
                        Ui.dp(getContext(), 1.25f), paint);
            }
            return;
        }

        float barW = Ui.dp(getContext(), 1.75f);
        float gap = Ui.dp(getContext(), 3.7f);
        int count = Math.min(BARS, Math.max(20,
                (int) Math.floor((w + gap) / (barW + gap))));
        count = Math.min(count, BARS);
        float total = count * barW + (count - 1) * gap;
        float startX = (w - total) / 2f;
        float maxH = h * 0.34f;
        float minH = Ui.dp(getContext(), 1.1f);

        // one soft ambient glow behind the whole waveform, not per bar
        paint.setShader(new LinearGradient(0, center - maxH - Ui.dp(getContext(), 3f),
                0, center + maxH + Ui.dp(getContext(), 3f),
                new int[]{0x002A7F73, 0x142A7F73, 0x002A7F73},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        RectF glow = new RectF(0, center - maxH - Ui.dp(getContext(), 2f), w,
                center + maxH + Ui.dp(getContext(), 2f));
        canvas.drawRoundRect(glow, Ui.dp(getContext(), 6f), Ui.dp(getContext(), 6f), paint);
        paint.setShader(null);

        // faint centre axis
        paint.setColor(0xFF2A7F73);
        paint.setAlpha(34);
        canvas.drawRect(0, center - Ui.dp(getContext(), 0.35f), w,
                center + Ui.dp(getContext(), 0.35f), paint);

        int first = BARS - count;
        for (int i = 0; i < count; i++) {
            float v = levels[first + i];
            float bh = minH + v * (maxH - minH);
            float x = startX + i * (barW + gap);
            boolean highlight = v > 0.62f || i >= count - 5;
            if (highlight) {
                paint.setColor(accent);
                paint.setAlpha((int) (150 + 90 * Math.min(1f, v * 1.6f)));
            } else {
                paint.setColor(0xFF2C8578);
                paint.setAlpha((int) (100 + 70 * v));
            }
            canvas.drawRoundRect(x, center - bh, x + barW, center + bh,
                    barW / 2f, barW / 2f, paint);
        }
        paint.setAlpha(255);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }
}
