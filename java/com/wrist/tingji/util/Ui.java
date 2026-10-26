package com.wrist.tingji.util;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Ui {
    public static final int BG = 0xFF07090B;
    public static final int SURFACE = 0xFF11161A;
    public static final int SURFACE2 = 0xFF171F24;
    public static final int SURFACE3 = 0xFF202B31;
    public static final int BORDER = 0xFF27343A;
    public static final int BORDER_SOFT = 0xFF1C262B;
    public static final int TEXT = 0xFFF1F7F5;
    public static final int MUTED = 0xFF93A6A3;
    public static final int DIM = 0xFF637572;
    public static final int ACCENT = 0xFF5EEAD4;
    public static final int ACCENT_DARK = 0xFF0F766E;
    public static final int ACCENT_INK = 0xFF042F2E;
    public static final int RECORD = 0xFFFF5C63;
    public static final int RECORD_DARK = 0xFF7F1D1D;
    public static final int MEETING = 0xFFFCD34D;
    public static final int CLASS = 0xFF93C5FD;
    public static final int INTERVIEW = 0xFFF9A8D4;
    public static final int OTHER = 0xFFC4B5FD;
    public static final int ERROR = 0xFFFF8A8A;
    public static final int SUCCESS = 0xFF86EFAC;

    private static Typeface regular;
    private static Typeface medium;
    private static Typeface mono;

    private Ui() {
    }

    public static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics()));
    }

    public static int sp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                c.getResources().getDisplayMetrics()));
    }

    public static Typeface regular() {
        if (regular == null) regular = Typeface.create("sans-serif", Typeface.NORMAL);
        return regular;
    }

    public static Typeface medium() {
        if (medium == null) medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        return medium;
    }

    public static Typeface mono() {
        if (mono == null) mono = Typeface.create("monospace", Typeface.NORMAL);
        return mono;
    }

    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable roundStroke(int color, float radiusDp, int strokeColor,
                                               float strokeDp, Context c) {
        GradientDrawable d = round(color, radiusDp, c);
        d.setStroke(Math.max(1, dp(c, strokeDp)), strokeColor);
        return d;
    }

    public static GradientDrawable gradient(int top, int bottom, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static RippleDrawable ripple(int color, float radiusDp, Context c) {
        GradientDrawable mask = round(0xFFFFFFFF, radiusDp, c);
        return new RippleDrawable(ColorStateList.valueOf(color), null, mask);
    }

    public static RippleDrawable rippleBg(GradientDrawable bg, int rippleColor, float radiusDp,
                                          Context c) {
        GradientDrawable mask = round(0xFFFFFFFF, radiusDp, c);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), bg, mask);
    }

    public static TextView text(Context c, CharSequence value, float sizeSp, int color) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(sizeSp);
        v.setTextColor(color);
        v.setTypeface(regular());
        v.setIncludeFontPadding(false);
        v.setLineSpacing(dp(c, 2.5f), 1.0f);
        return v;
    }

    public static TextView mediumText(Context c, CharSequence value, float sizeSp, int color) {
        TextView v = text(c, value, sizeSp, color);
        v.setTypeface(medium());
        return v;
    }

    public static TextView monoText(Context c, CharSequence value, float sizeSp, int color) {
        TextView v = text(c, value, sizeSp, color);
        v.setTypeface(mono());
        return v;
    }

    public static void setMargins(View v, int left, int top, int right, int bottom) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.setMargins(left, top, right, bottom);
            v.setLayoutParams(lp);
        }
    }

    public static void toast(Context c, String message) {
        if (TextUtils.isEmpty(message)) return;
        Toast.makeText(c.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    public static void copy(Context c, String text, String label) {
        if (text == null) return;
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label == null ? "听记" : label, text));
            toast(c, "已复制");
        }
    }

    public static void share(Context c, String text) {
        if (TextUtils.isEmpty(text)) {
            toast(c, "没有可分享的内容");
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "听记 · 会议/课堂记录");
        i.putExtra(Intent.EXTRA_TEXT, text);
        try {
            c.startActivity(Intent.createChooser(i, "分享记录"));
        } catch (Throwable t) {
            copy(c, text, "听记");
        }
    }

    public static void haptic(Context c, int ms) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    public static String duration(long ms) {
        long total = Math.max(0, ms) / 1000L;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    public static String durationShort(long ms) {
        if (ms < 60_000) return Math.max(1, ms / 1000) + " 秒";
        long min = ms / 60_000;
        if (min < 60) return min + " 分钟";
        long h = min / 60;
        long m = min % 60;
        return m == 0 ? h + " 小时" : h + " 小时 " + m + " 分";
    }

    public static String dateTime(long ts) {
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(ts));
    }

    public static String timeOnly(long ts) {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(ts));
    }

    public static String relative(long ts) {
        long delta = Math.max(0, System.currentTimeMillis() - ts);
        if (delta < 60_000) return "刚刚";
        if (delta < 3_600_000) return (delta / 60_000) + " 分钟前";
        if (delta < 86_400_000L) return (delta / 3_600_000) + " 小时前";
        if (delta < 7 * 86_400_000L) return (delta / 86_400_000L) + " 天前";
        return new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date(ts));
    }

    public static String oneLine(String text, int max) {
        if (text == null) return "";
        String s = text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (s.length() > max) s = s.substring(0, max) + "…";
        return s;
    }

    public static int kindColor(String kind) {
        if ("课堂".equals(kind)) return CLASS;
        if ("访谈".equals(kind)) return INTERVIEW;
        if ("其他".equals(kind)) return OTHER;
        return MEETING;
    }

    public static String kindGlyph(String kind) {
        if ("课堂".equals(kind)) return "课";
        if ("访谈".equals(kind)) return "访";
        if ("其他".equals(kind)) return "记";
        return "会";
    }

    public static AlertDialog confirm(Context c, String title, String message,
                                      String positive, Runnable onYes) {
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, (x, y) -> {
                    if (onYes != null) onYes.run();
                })
                .setNegativeButton("取消", null)
                .create();
        d.show();
        return d;
    }

    public static void input(Context c, String title, String value, String hint,
                             boolean multiline, boolean password, InputDone done) {
        int pad = dp(c, 16);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(c, 5), pad, 0);
        EditText e = new EditText(c);
        e.setText(value == null ? "" : value);
        e.setHint(hint == null ? "" : hint);
        e.setTextSize(14);
        e.setTextColor(TEXT);
        e.setHintTextColor(DIM);
        e.setSingleLine(!multiline);
        if (multiline) {
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        if (password) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setSelectAllOnFocus(false);
        box.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("保存", (x, y) -> done.onDone(e.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    public interface InputDone {
        void onDone(String value);
    }
}
