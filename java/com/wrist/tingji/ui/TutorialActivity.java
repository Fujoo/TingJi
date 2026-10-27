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

/** First-run step 2: four short pages teaching the core workflow. */
public class TutorialActivity extends ScaledActivity {
    private Settings settings;
    private int page;
    private boolean replay;
    private GlyphView icon;
    private TextView title;
    private TextView body;
    private TextView prev;
    private TextView next;
    private LinearLayout dots;

    private static final int[] ICONS = {GlyphView.MIC, GlyphView.SPARK,
            GlyphView.NOTE, GlyphView.SETTINGS};
    private static final String[] TITLES = {
            "会议、课堂，一键记录",
            "自动转写，AI 整理",
            "课堂笔记更细",
            "权限与隐私"
    };
    private static final String[] BODIES = {
            "先选会议或课堂，点中间按钮开始。\n暂停、标记重点、结束都在这页。\n退出或息屏，录音会在后台继续。",
            "配好语音识别和 DeepSeek Key，\n结束后自动转写、纠错、摘要、\n提取待办并分类。",
            "课堂会自动识别学科，整理\n知识点、例题、作业和重点难点。\n主页点“学科”可按科目分组。",
            "首次录音请选“始终允许”，\n否则息屏后系统可能拒绝麦克风。\n记录和密钥只保存在手表本地。"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = App.get().settings;
        replay = getIntent().getBooleanExtra("replay", false);
        fullscreen();
        buildUi();
        showPage(0);
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
        TextView label = Ui.mediumText(this, "使用教程", 9.2f, Ui.DIM);
        label.setLetterSpacing(0.06f);
        top.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView skip = Ui.mediumText(this, replay ? "关闭" : "跳过", 9.5f, Ui.ACCENT);
        skip.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        skip.setOnClickListener(v -> finishTutorial());
        top.addView(skip);
        col.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));

        FrameLayout content = new FrameLayout(this);
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout iconCircle = new FrameLayout(this);
        iconCircle.setBackground(Ui.roundStroke(0xFF0F201D, 27, 0xFF245048, 1, this));
        icon = new GlyphView(this, GlyphView.MIC);
        icon.setColor(Ui.ACCENT);
        icon.setStrokeDp(2.1f);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                Ui.dp(this, 29), Ui.dp(this, 29));
        ilp.gravity = Gravity.CENTER;
        iconCircle.addView(icon, ilp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                Ui.dp(this, 58), Ui.dp(this, 58));
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        center.addView(iconCircle, clp);

        title = Ui.mediumText(this, "", 15.5f, Ui.TEXT);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 10), 0, 0);
        center.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        body = Ui.text(this, "", 9.8f, Ui.MUTED);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(Ui.dp(this, 3), 1f);
        body.setPadding(Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2), 0);
        center.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        centerLp.gravity = Gravity.CENTER;
        content.addView(center, centerLp);
        col.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < TITLES.length; i++) {
            View dot = new View(this);
            dot.setBackground(Ui.round(Ui.SURFACE3, 3, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Ui.dp(this, 6), Ui.dp(this, 6));
            lp.rightMargin = Ui.dp(this, 5);
            dots.addView(dot, lp);
        }
        col.addView(dots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 16)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        prev = Ui.mediumText(this, "上一步", 9.5f, Ui.DIM);
        prev.setGravity(Gravity.CENTER);
        prev.setBackground(Ui.rippleBg(Ui.roundStroke(Ui.SURFACE, 10, Ui.BORDER_SOFT, 1, this),
                0x22FFFFFF, 10, this));
        prev.setOnClickListener(v -> showPage(page - 1));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                Ui.dp(this, 64), Ui.dp(this, 42));
        plp.rightMargin = Ui.dp(this, 6);
        nav.addView(prev, plp);

        next = Ui.mediumText(this, "下一步", 11.5f, Ui.ACCENT_INK);
        next.setGravity(Gravity.CENTER);
        next.setBackground(Ui.rippleBg(
                Ui.gradient(0xFF5EEAD4, 0xFF14B8A6, 12, this), 0x44FFFFFF, 12, this));
        next.setOnClickListener(v -> {
            if (page >= TITLES.length - 1) finishTutorial();
            else showPage(page + 1);
        });
        nav.addView(next, new LinearLayout.LayoutParams(
                0, Ui.dp(this, 42), 1f));
        col.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        setContentView(root);
    }

    private void showPage(int index) {
        if (index < 0) index = 0;
        if (index >= TITLES.length) index = TITLES.length - 1;
        page = index;
        icon.setType(ICONS[index]);
        title.setText(TITLES[index]);
        body.setText(BODIES[index]);
        prev.setVisibility(index == 0 ? View.GONE : View.VISIBLE);
        next.setText(index == TITLES.length - 1 ? "开始使用" : "下一步");
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            dot.setBackground(i == index
                    ? Ui.round(Ui.ACCENT, 3, this)
                    : Ui.round(Ui.SURFACE3, 3, this));
            dot.setAlpha(i == index ? 1f : 0.55f);
        }
    }

    private void finishTutorial() {
        settings.setTutorialShown(true);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (replay) {
            finish();
        } else if (page > 0) {
            showPage(page - 1);
        } else {
            finishTutorial();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) fullscreen();
    }
}
