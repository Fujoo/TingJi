package com.wrist.tingji.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import com.wrist.tingji.util.Ui;

/**
 * Dependency-free "liquid glass" circle used for record / pause / mark.
 * Simulates layered translucency, a refracted rim, a specular highlight and
 * press spring without RenderEffect (unavailable on Android 11).
 */
public class LiquidGlassButton extends android.widget.FrameLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int tint = 0xFF14B8A6;
    private boolean strong;
    private boolean down;
    private float press;
    private float ripple;
    private ValueAnimator pressAnim;
    private ValueAnimator rippleAnim;

    public LiquidGlassButton(Context c) {
        super(c);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(Ui.dp(c, 1.1f));
    }

    public void setTint(int color) {
        tint = color;
        invalidate();
    }

    public void setStrong(boolean value) {
        strong = value;
        invalidate();
    }

    private void animatePress(float target, long duration, boolean release) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(press, target);
        pressAnim.setDuration(duration);
        pressAnim.setInterpolator(release
                ? new OvershootInterpolator(2.2f) : new DecelerateInterpolator(1.7f));
        pressAnim.addUpdateListener(a -> {
            press = (float) a.getAnimatedValue();
            float s = 1f - 0.045f * press;
            setScaleX(s);
            setScaleY(s);
            invalidate();
        });
        pressAnim.start();
    }

    private void animateRipple() {
        if (rippleAnim != null) rippleAnim.cancel();
        ripple = 0f;
        rippleAnim = ValueAnimator.ofFloat(0f, 1f);
        rippleAnim.setDuration(420);
        rippleAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        rippleAnim.addUpdateListener(a -> {
            ripple = (float) a.getAnimatedValue();
            invalidate();
        });
        rippleAnim.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = true;
                animatePress(1f, 90, false);
                animateRipple();
                return true;
            case MotionEvent.ACTION_UP:
                if (!down) return false;
                down = false;
                animatePress(0f, 300, true);
                if (event.getX() >= 0 && event.getY() >= 0
                        && event.getX() <= getWidth() && event.getY() <= getHeight()) {
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                down = false;
                animatePress(0f, 220, true);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - Ui.dp(getContext(), 2f);
        if (r <= 0) return;

        // The glass body itself carries the effect: no outer halo, arc or dot.
        int top = lighten(tint, strong ? 48 : 34);
        int bottom = darken(tint, strong ? 18 : 12);
        paint.setShader(new LinearGradient(cx - r * 0.65f, cy - r * 0.75f,
                cx + r * 0.55f, cy + r * 0.75f,
                new int[]{withAlpha(top, 225), withAlpha(tint, 205),
                        withAlpha(bottom, 225)},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, paint);

        // very low-contrast refracted rim
        rimPaint.setStrokeWidth(Ui.dp(getContext(), 0.8f));
        rimPaint.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                new int[]{withAlpha(Color.WHITE, 82), withAlpha(Color.WHITE, 8),
                        withAlpha(Color.WHITE, 44)},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r - Ui.dp(getContext(), 0.4f), rimPaint);

        // a single soft inner light, no separate specular dot
        paint.setShader(new RadialGradient(cx - r * 0.38f, cy - r * 0.42f, r * 0.82f,
                new int[]{withAlpha(Color.WHITE, strong ? 30 : 18),
                        withAlpha(Color.WHITE, 4), withAlpha(Color.WHITE, 0)},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, paint);

        // only interactive feedback: a barely visible expanding ring
        if (ripple > 0f) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(getContext(), 0.8f));
            paint.setColor(withAlpha(Color.WHITE, (int) (34 * (1f - ripple))));
            float rr = r * (0.86f + 0.20f * ripple);
            canvas.drawCircle(cx, cy, rr, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int lighten(int color, int amount) {
        int r = Math.min(255, ((color >> 16) & 0xff) + amount);
        int g = Math.min(255, ((color >> 8) & 0xff) + amount);
        int b = Math.min(255, (color & 0xff) + amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, int amount) {
        int r = Math.max(0, ((color >> 16) & 0xff) - amount);
        int g = Math.max(0, ((color >> 8) & 0xff) - amount);
        int b = Math.max(0, (color & 0xff) - amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
