package com.wrist.tingji.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.animation.ValueAnimator;

import com.wrist.tingji.util.Ui;

/**
 * Small transcript view with an Apple-like rolling transition: the old line
 * drifts up and blurs away while the new line rises from below. Kept as a
 * single software layer because API 30 has no text RenderEffect blur.
 */
public class LiveTranscriptView extends View {
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout current;
    private StaticLayout previous;
    private String currentText = "";
    private String previousText = "";
    private boolean hasPrevious;
    private float progress = 1f;
    private ValueAnimator animator;
    private int textColor = Ui.TEXT;
    private int maxLines = 2;
    private float lineSpacingExtra;

    public LiveTranscriptView(Context c) {
        super(c);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setTypeface(Ui.regular());
        paint.setTextSize(Ui.sp(c, 9.6f));
        paint.setColor(textColor);
        paint.setSubpixelText(true);
        lineSpacingExtra = Ui.dp(c, 2f);
    }

    public void setText(CharSequence value) {
        String next = value == null ? "" : value.toString();
        if (TextUtils.equals(currentText, next)) return;
        previousText = currentText;
        hasPrevious = !TextUtils.isEmpty(previousText);
        currentText = next;
        progress = 0f;
        buildLayouts();
        startAnimation();
    }

    public void setTextColor(int color) {
        textColor = color;
        invalidate();
    }

    public void setMaxLines(int lines) {
        maxLines = Math.max(1, lines);
        buildLayouts();
        invalidate();
    }

    public void setLineSpacing(float extra, float multiplier) {
        lineSpacingExtra = extra;
        buildLayouts();
        invalidate();
    }

    public void setEllipsize(TextUtils.TruncateAt where) {
        // Built into the StaticLayout below.
    }

    private void startAnimation() {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(420);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void buildLayouts() {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        if (w <= 0) return;
        current = layout(currentText, w);
        previous = hasPrevious ? layout(previousText, w) : null;
    }

    private StaticLayout layout(String text, int width) {
        if (text == null) text = "";
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(lineSpacingExtra, 1f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildLayouts();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (current == null) return;
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int clipBottom = getHeight() - getPaddingBottom();
        canvas.save();
        canvas.clipRect(left, top, getWidth() - getPaddingRight(), clipBottom);
        float p = progress;
        float currentY = top + (getHeight() - getPaddingTop() - getPaddingBottom()
                - current.getHeight()) / 2f + Ui.dp(getContext(), 7f) * (1f - p);
        if (previous != null && p < 1f) {
            float oldY = top + (getHeight() - getPaddingTop() - getPaddingBottom()
                    - previous.getHeight()) / 2f - Ui.dp(getContext(), 6f) * p;
            paint.setColor(Ui.MUTED);
            paint.setAlpha((int) (255 * (1f - p) * 0.9f));
            paint.setMaskFilter(blur(2.2f * (1f - p)));
            canvas.save();
            canvas.translate(left, oldY);
            previous.draw(canvas);
            canvas.restore();
        }
        paint.setColor(textColor);
        paint.setAlpha((int) (255 * Math.min(1f, p * 1.15f)));
        paint.setMaskFilter(blur(2.2f * (1f - p)));
        canvas.save();
        canvas.translate(left, currentY);
        current.draw(canvas);
        canvas.restore();
        paint.setMaskFilter(null);
        paint.setAlpha(255);
        canvas.restore();
    }

    private BlurMaskFilter blur(float radiusDp) {
        if (radiusDp < 0.15f) return null;
        float radius = Ui.dp(getContext(), radiusDp);
        if (radius < 0.5f) return null;
        return new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL);
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
