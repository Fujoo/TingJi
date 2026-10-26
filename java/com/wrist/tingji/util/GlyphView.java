package com.wrist.tingji.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Crisp, dependency-free icon set drawn on a 24x24 grid. */
public class GlyphView extends View {
    public static final int BACK = 1;
    public static final int SETTINGS = 2;
    public static final int MIC = 3;
    public static final int PAUSE = 4;
    public static final int PLAY = 5;
    public static final int STOP = 6;
    public static final int MARK = 7;
    public static final int SHARE = 8;
    public static final int TRASH = 9;
    public static final int SEARCH = 10;
    public static final int MORE = 11;
    public static final int NOTE = 12;
    public static final int CHECK = 13;
    public static final int PLUS = 14;
    public static final int CLOSE = 15;
    public static final int CHEVRON = 16;
    public static final int SPARK = 17;
    public static final int CLOCK = 18;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int type;
    private int color = Color.WHITE;
    private float strokeDp = 1.8f;

    public GlyphView(Context c) {
        this(c, MIC);
    }

    public GlyphView(Context c, int type) {
        super(c);
        this.type = type;
        init();
    }

    public GlyphView(Context c, AttributeSet attrs) {
        super(c, attrs);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setType(int type) {
        this.type = type;
        invalidate();
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public void setStrokeDp(float dp) {
        this.strokeDp = dp;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int def = Ui.dp(getContext(), 24);
        int w = resolve(widthMeasureSpec, def);
        int h = resolve(heightMeasureSpec, def);
        setMeasuredDimension(w, h);
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
        float base = Math.min(getWidth(), getHeight()) * 0.64f;
        if (base <= 0) return;
        float scale = base / 24f;
        canvas.save();
        canvas.translate((getWidth() - base) / 2f, (getHeight() - base) / 2f);
        canvas.scale(scale, scale);
        paint.setColor(color);
        paint.setStrokeWidth(strokeDp * 1.35f);
        paint.setStyle(Paint.Style.STROKE);
        path.reset();

        switch (type) {
            case BACK:
                path.moveTo(15.5f, 4.5f);
                path.lineTo(7.5f, 12f);
                path.lineTo(15.5f, 19.5f);
                canvas.drawPath(path, paint);
                break;
            case SETTINGS:
                canvas.drawLine(4f, 6.5f, 20f, 6.5f, paint);
                canvas.drawLine(4f, 12f, 20f, 12f, paint);
                canvas.drawLine(4f, 17.5f, 20f, 17.5f, paint);
                fillCircle(canvas, 9f, 6.5f, 2.1f);
                fillCircle(canvas, 15f, 12f, 2.1f);
                fillCircle(canvas, 10f, 17.5f, 2.1f);
                break;
            case MIC:
                RectF capsule = new RectF(9f, 3f, 15f, 14f);
                canvas.drawRoundRect(capsule, 3f, 3f, paint);
                path.moveTo(6f, 11f);
                path.cubicTo(6f, 15.4f, 8.5f, 17.7f, 12f, 17.7f);
                path.cubicTo(15.5f, 17.7f, 18f, 15.4f, 18f, 11f);
                canvas.drawPath(path, paint);
                canvas.drawLine(12f, 17.8f, 12f, 21f, paint);
                break;
            case PAUSE:
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(7f, 5f, 10.5f, 19f), 1.4f, 1.4f, paint);
                canvas.drawRoundRect(new RectF(13.5f, 5f, 17f, 19f), 1.4f, 1.4f, paint);
                break;
            case PLAY:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(7.5f, 4.8f);
                path.lineTo(19f, 12f);
                path.lineTo(7.5f, 19.2f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case STOP:
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(6.5f, 6.5f, 17.5f, 17.5f), 2.6f, 2.6f, paint);
                break;
            case MARK:
                canvas.drawLine(6f, 3.5f, 6f, 21f, paint);
                path.moveTo(6f, 4.5f);
                path.lineTo(19f, 4.5f);
                path.lineTo(15.2f, 9.2f);
                path.lineTo(19f, 13.9f);
                path.lineTo(6f, 13.9f);
                canvas.drawPath(path, paint);
                break;
            case SHARE:
                canvas.drawCircle(18f, 5.5f, 2.5f, paint);
                canvas.drawCircle(6f, 12f, 2.5f, paint);
                canvas.drawCircle(18f, 18.5f, 2.5f, paint);
                canvas.drawLine(8.2f, 10.8f, 15.8f, 6.6f, paint);
                canvas.drawLine(8.2f, 13.2f, 15.8f, 17.4f, paint);
                break;
            case TRASH:
                canvas.drawLine(4f, 6.5f, 20f, 6.5f, paint);
                canvas.drawLine(9f, 3.5f, 15f, 3.5f, paint);
                path.moveTo(6.5f, 6.5f);
                path.lineTo(7.6f, 20.5f);
                path.lineTo(16.4f, 20.5f);
                path.lineTo(17.5f, 6.5f);
                canvas.drawPath(path, paint);
                canvas.drawLine(10f, 10f, 10.3f, 17.5f, paint);
                canvas.drawLine(14f, 10f, 13.7f, 17.5f, paint);
                break;
            case SEARCH:
                canvas.drawCircle(10.5f, 10.5f, 6.2f, paint);
                canvas.drawLine(15.2f, 15.2f, 21f, 21f, paint);
                break;
            case MORE:
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(6f, 12f, 1.8f, paint);
                canvas.drawCircle(12f, 12f, 1.8f, paint);
                canvas.drawCircle(18f, 12f, 1.8f, paint);
                break;
            case NOTE:
                path.moveTo(6f, 3.5f);
                path.lineTo(14f, 3.5f);
                path.lineTo(19f, 8.5f);
                path.lineTo(19f, 20.5f);
                path.lineTo(6f, 20.5f);
                path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(14f, 3.5f, 14f, 8.5f, paint);
                canvas.drawLine(14f, 8.5f, 19f, 8.5f, paint);
                canvas.drawLine(9f, 12f, 16f, 12f, paint);
                canvas.drawLine(9f, 15.5f, 16f, 15.5f, paint);
                break;
            case CHECK:
                path.moveTo(5f, 12.5f);
                path.lineTo(10f, 17.5f);
                path.lineTo(20f, 6.5f);
                canvas.drawPath(path, paint);
                break;
            case PLUS:
                canvas.drawLine(12f, 5f, 12f, 19f, paint);
                canvas.drawLine(5f, 12f, 19f, 12f, paint);
                break;
            case CLOSE:
                canvas.drawLine(6f, 6f, 18f, 18f, paint);
                canvas.drawLine(18f, 6f, 6f, 18f, paint);
                break;
            case CHEVRON:
                path.moveTo(9f, 5f);
                path.lineTo(16f, 12f);
                path.lineTo(9f, 19f);
                canvas.drawPath(path, paint);
                break;
            case SPARK:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(12f, 2.5f);
                path.lineTo(14.1f, 9.9f);
                path.lineTo(21.5f, 12f);
                path.lineTo(14.1f, 14.1f);
                path.lineTo(12f, 21.5f);
                path.lineTo(9.9f, 14.1f);
                path.lineTo(2.5f, 12f);
                path.lineTo(9.9f, 9.9f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case CLOCK:
                canvas.drawCircle(12f, 12f, 8.5f, paint);
                canvas.drawLine(12f, 6.5f, 12f, 12f, paint);
                canvas.drawLine(12f, 12f, 16f, 14.5f, paint);
                break;
            default:
                canvas.drawCircle(12f, 12f, 4f, paint);
                break;
        }
        canvas.restore();
    }

    private void fillCircle(Canvas canvas, float x, float y, float r) {
        Paint.Style old = paint.getStyle();
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, r, paint);
        paint.setStyle(old);
    }
}
