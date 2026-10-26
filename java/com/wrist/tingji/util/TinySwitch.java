package com.wrist.tingji.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Compact switch that fits a 1.4-inch watch face. */
public class TinySwitch extends View {
    public interface OnChanged {
        void onChanged(boolean checked);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;
    private OnChanged listener;

    public TinySwitch(Context c) {
        super(c);
        setClickable(true);
        setFocusable(true);
    }

    public void setChecked(boolean checked) {
        if (this.checked == checked) {
            invalidate();
            return;
        }
        this.checked = checked;
        invalidate();
    }

    public boolean isChecked() {
        return checked;
    }

    public void setOnChanged(OnChanged listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = Ui.dp(getContext(), 42);
        int h = Ui.dp(getContext(), 24);
        setMeasuredDimension(resolve(widthMeasureSpec, w), resolve(heightMeasureSpec, h));
    }

    private static int resolve(int spec, int def) {
        int mode = MeasureSpec.getMode(spec);
        int size = MeasureSpec.getSize(spec);
        if (mode == MeasureSpec.EXACTLY) return size;
        if (mode == MeasureSpec.AT_MOST) return Math.min(size, def);
        return def;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float w = getWidth();
        float inset = Ui.dp(getContext(), 1f);
        RectF track = new RectF(inset, inset, w - inset, h - inset);
        paint.setColor(checked ? Ui.ACCENT_DARK : Ui.SURFACE3);
        canvas.drawRoundRect(track, h / 2f, h / 2f, paint);
        if (checked) {
            paint.setColor(Ui.ACCENT);
            paint.setAlpha(50);
            canvas.drawRoundRect(track, h / 2f, h / 2f, paint);
            paint.setAlpha(255);
        }
        float knobR = h * 0.36f;
        float cx = checked ? w - knobR - Ui.dp(getContext(), 4f)
                : knobR + Ui.dp(getContext(), 4f);
        paint.setColor(checked ? Ui.ACCENT : Ui.DIM);
        canvas.drawCircle(cx, h / 2f, knobR, paint);
        paint.setColor(checked ? Ui.ACCENT_INK : Ui.SURFACE);
        canvas.drawCircle(cx, h / 2f, knobR * 0.38f, paint);
    }

    @Override
    public boolean performClick() {
        checked = !checked;
        invalidate();
        if (listener != null) listener.onChanged(checked);
        return true;
    }
}
