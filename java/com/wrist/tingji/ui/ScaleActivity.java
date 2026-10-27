package com.wrist.tingji.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wrist.tingji.App;
import com.wrist.tingji.core.Settings;
import com.wrist.tingji.util.GlyphView;
import com.wrist.tingji.util.Ui;

/** First-run step 1: choose a comfortable UI scale before the tutorial. */
public class ScaleActivity extends ScaledActivity {
    private Settings settings;
    private float pending;
    private float current;
    private TextView percent;
    private View preview;
    private TextView previewHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = App.get().settings;
        pending = settings.getUiScale();
        current = settings.getUiScale();
        fullscreen();
        buildUi();
    }

    private void fullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 6));
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView step = Ui.mediumText(this, "初次设置 · 1/2", 9.2f, Ui.DIM);
        step.setLetterSpacing(0.06f);
        top.addView(step, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView skip = Ui.mediumText(this, "跳过", 9.5f, Ui.ACCENT);
        skip.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        skip.setOnClickListener(v -> finishOnboarding());
        top.addView(skip);
        col.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));

        TextView title = Ui.mediumText(this, "调整界面大小", 16f, Ui.TEXT);
        col.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 25)));
        TextView sub = Ui.text(this, "拖到文字和按钮都舒服的大小\n下一步再看使用教程", 9.2f, Ui.MUTED);
        sub.setLineSpacing(Ui.dp(this, 2), 1f);
        col.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 25)));

        FrameLayout frame = new FrameLayout(this);
        frame.setClipChildren(true);
        frame.setBackground(Ui.roundStroke(Ui.SURFACE, 14, Ui.BORDER_SOFT, 1, this));
        preview = buildPreview();
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                Ui.dp(this, 184), Ui.dp(this, 98));
        plp.gravity = Gravity.CENTER;
        frame.addView(preview, plp);
        col.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.setMargins(frame, 0, Ui.dp(this, 4), 0, Ui.dp(this, 4));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        TextView minus = roundButton("−", 14f);
        minus.setOnClickListener(v -> change(-0.04f));
        controls.addView(minus, new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42)));
        percent = Ui.mediumText(this, "", 15f, Ui.ACCENT);
        percent.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pctLp = new LinearLayout.LayoutParams(
                Ui.dp(this, 78), Ui.dp(this, 42));
        controls.addView(percent, pctLp);
        TextView plus = roundButton("+", 15f);
        plus.setOnClickListener(v -> change(0.04f));
        controls.addView(plus, new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42)));
        col.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));
        previewHint = Ui.text(this, "", 8.4f, Ui.DIM);
        previewHint.setGravity(Gravity.CENTER);
        col.addView(previewHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 13)));

        TextView next = Ui.mediumText(this, "下一步", 12.5f, Ui.ACCENT_INK);
        next.setGravity(Gravity.CENTER);
        next.setBackground(Ui.rippleBg(
                Ui.gradient(0xFF5EEAD4, 0xFF14B8A6, 14, this), 0x44FFFFFF, 14, this));
        next.setOnClickListener(v -> {
            settings.setUiScale(pending);
            settings.setScaleConfigured();
            finish();
        });
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        nlp.topMargin = Ui.dp(this, 2);
        col.addView(next, nlp);

        setContentView(root);
        preview.post(this::applyPreview);
        updateLabel();
    }

    private View buildPreview() {
        LinearLayout sample = new LinearLayout(this);
        sample.setOrientation(LinearLayout.VERTICAL);
        sample.setGravity(Gravity.CENTER);
        sample.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        sample.setBackground(Ui.round(0xFF0B1113, 12, this));

        TextView title = Ui.mediumText(this, "听记", 15f, Ui.TEXT);
        title.setGravity(Gravity.CENTER);
        sample.addView(title);
        TextView sub = Ui.text(this, "会议 · 课堂 · AI 整理", 8f, Ui.MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(this, 1), 0, Ui.dp(this, 5));
        sample.addView(sub);

        TextView button = Ui.mediumText(this, "开始记录", 9.5f, Ui.ACCENT_INK);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.gradient(0xFF5EEAD4, 0xFF14B8A6, 9, this));
        sample.addView(button, new LinearLayout.LayoutParams(
                Ui.dp(this, 104), Ui.dp(this, 28)));
        return sample;
    }

    private TextView roundButton(String text, float size) {
        TextView v = Ui.mediumText(this, text, size, Ui.TEXT);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Ui.rippleBg(Ui.roundStroke(Ui.SURFACE2, 21, Ui.BORDER, 1, this),
                0x22FFFFFF, 21, this));
        return v;
    }

    private void change(float delta) {
        pending = Math.max(Settings.MIN_UI_SCALE,
                Math.min(Settings.MAX_UI_SCALE, pending + delta));
        pending = Math.round(pending * 100f) / 100f;
        updateLabel();
        applyPreview();
    }

    private void updateLabel() {
        int pct = Math.round(pending * 100);
        percent.setText(pct + "%");
        if (pct == 100) previewHint.setText("推荐：新基准大小");
        else if (pct < 100) previewHint.setText("更小：一屏显示更多");
        else previewHint.setText("更大：文字更清楚");
    }

    private void applyPreview() {
        if (preview == null || current <= 0) return;
        float ratio = pending / current;
        preview.setPivotX(preview.getWidth() / 2f);
        preview.setPivotY(preview.getHeight() / 2f);
        preview.setScaleX(ratio);
        preview.setScaleY(ratio);
    }

    private void finishOnboarding() {
        settings.setUiScale(1f);
        settings.setScaleConfigured();
        finish();
    }

    @Override
    public void onBackPressed() {
        finishOnboarding();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) fullscreen();
    }
}
