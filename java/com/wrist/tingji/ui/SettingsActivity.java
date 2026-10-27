package com.wrist.tingji.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wrist.tingji.App;
import com.wrist.tingji.core.AiClient;
import com.wrist.tingji.core.AsrClient;
import com.wrist.tingji.core.NoteStore;
import com.wrist.tingji.core.Settings;
import com.wrist.tingji.core.WavUtil;
import com.wrist.tingji.util.GlyphView;
import com.wrist.tingji.util.TinySwitch;
import com.wrist.tingji.util.Ui;

import java.io.File;

public class SettingsActivity extends ScaledActivity {
    private Settings settings;
    private NoteStore store;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = App.get().settings;
        store = App.get().store;
        fullscreen();
        buildUi();
        reload();
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
        col.setPadding(Ui.dp(this, 13), Ui.dp(this, 4), Ui.dp(this, 13), 0);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout back = new FrameLayout(this);
        back.setBackground(Ui.ripple(0x1FFFFFFF, 18, this));
        GlyphView arrow = new GlyphView(this, GlyphView.BACK);
        arrow.setColor(Ui.TEXT);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18));
        alp.gravity = Gravity.CENTER;
        back.addView(arrow, alp);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        TextView title = Ui.mediumText(this, "设置", 17f, Ui.TEXT);
        title.setPadding(Ui.dp(this, 9), 0, 0, 0);
        bar.addView(title);
        col.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(this, 14));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void reload() {
        content.removeAllViews();
        content.addView(setupCard());
        content.addView(sectionLabel("AI 大脑"));
        content.addView(aiCard());
        content.addView(sectionLabel("语音转写"));
        content.addView(asrCard());
        content.addView(sectionLabel("自动整理"));
        content.addView(autoCard());
        content.addView(sectionLabel("外观与反馈"));
        content.addView(lookCard());
        content.addView(sectionLabel("数据"));
        content.addView(dataCard());
        content.addView(aboutCard());
    }

    private View setupCard() {
        LinearLayout card = card();
        card.setBackground(Ui.rippleBg(
                Ui.gradient(0xFF0F2A26, 0xFF0B1B19, 15, this), 0x22FFFFFF, 15, this));
        card.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.mediumText(this, "用手机配置", 13f, Ui.TEXT);
        card.addView(t);
        TextView s = Ui.text(this, "在手机浏览器里填 Key，不用在 1.4 英寸屏幕上打字。", 9.8f, Ui.MUTED);
        s.setLineSpacing(Ui.dp(this, 2), 1f);
        s.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 7));
        card.addView(s);
        TextView btn = actionButton("打开手机配置页", true);
        btn.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        card.addView(btn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 36)));
        return card;
    }

    private View aiCard() {
        LinearLayout card = card();
        card.addView(row("DeepSeek API Key", settings.keyHint(settings.getDeepSeekKey()),
                () -> Ui.input(this, "DeepSeek API Key", settings.getDeepSeekKey(),
                        "sk-...", false, true, v -> {
                            settings.setDeepSeekKey(v);
                            reload();
                        })));
        card.addView(divider());
        card.addView(row("接口地址", settings.getDeepSeekBase(),
                () -> Ui.input(this, "接口地址", settings.getDeepSeekBase(),
                        "https://api.deepseek.com", false, false, v -> {
                            settings.setDeepSeekBase(v);
                            reload();
                        })));
        card.addView(divider());
        card.addView(row("对话模型", settings.getModel(),
                () -> Ui.input(this, "对话模型", settings.getModel(),
                        "deepseek-flash", false, false, v -> {
                            settings.setModel(v);
                            reload();
                        })));
        TextView test = actionButton("测试 AI 连接", false);
        test.setOnClickListener(v -> testAI());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 35));
        lp.topMargin = Ui.dp(this, 8);
        card.addView(test, lp);
        return card;
    }

    private View asrCard() {
        LinearLayout card = card();
        card.addView(row("语音服务", settings.getAsrPresetName(),
                this::showAsrPreset));
        card.addView(divider());
        card.addView(row("ASR Key", settings.keyHint(settings.getAsrKey()),
                () -> Ui.input(this, "语音识别 API Key", settings.getAsrKey(),
                        "sk-...", false, true, v -> {
                            settings.setAsrKey(v);
                            reload();
                        })));
        card.addView(divider());
        card.addView(row("识别接口", settings.getAsrEndpoint(), () ->
                Ui.input(this, "识别接口", settings.getAsrEndpoint(),
                        "https://.../audio/transcriptions", false, false, v -> {
                            settings.setAsrEndpoint(v);
                            settings.applyAsrPreset("custom");
                            reload();
                        })));
        card.addView(divider());
        card.addView(row("识别模型", settings.getAsrModel(), () ->
                Ui.input(this, "识别模型", settings.getAsrModel(),
                        "TeleAI/TeleSpeechASR", false, false, v -> {
                            settings.setAsrModel(v);
                            settings.applyAsrPreset("custom");
                            reload();
                        })));
        card.addView(divider());
        card.addView(row("识别语言", settings.getAsrLanguage(), () ->
                Ui.input(this, "识别语言", settings.getAsrLanguage(),
                        "zh", false, false, v -> {
                            settings.setAsrLanguage(v);
                            reload();
                        })));
        TextView test = actionButton("测试语音接口", false);
        test.setOnClickListener(v -> testASR());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 35));
        lp.topMargin = Ui.dp(this, 8);
        card.addView(test, lp);
        return card;
    }

    private View autoCard() {
        LinearLayout card = card();
        card.addView(toggleRow("录音时自动转写", "每 30–60 秒一段，边录边转",
                settings.isAutoTranscribe(), checked -> settings.setAutoTranscribe(checked)));
        card.addView(divider());
        card.addView(toggleRow("自动纠错", "结合上下文和热词修正同音字、断句",
                settings.isAutoCorrect(), checked -> settings.setAutoCorrect(checked)));
        card.addView(divider());
        card.addView(toggleRow("自动总结与分类", "生成摘要、要点、待办和标签",
                settings.isAutoSummarize(), checked -> settings.setAutoSummarize(checked)));
        card.addView(divider());
        String hot = settings.getHotwords();
        card.addView(row("专有名词 / 热词",
                hot.isEmpty() ? "未设置" : Ui.oneLine(hot, 22),
                () -> Ui.input(this, "专有名词 / 热词", hot,
                        "例如：DeepSeek、张伟、Q4 复盘", true, false, v -> {
                            settings.setHotwords(v);
                            reload();
                        })));
        return card;
    }

    private View lookCard() {
        LinearLayout card = card();
        card.addView(toggleRow("震动反馈", "按键和标记时轻震",
                settings.isHapticsEnabled(), settings::setHapticsEnabled));
        card.addView(divider());
        card.addView(scaleRow());
        return card;
    }

    private View scaleRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.mediumText(this, "界面缩放", 11.4f, Ui.TEXT));
        int pct = Math.round(settings.getUiScale() * 100);
        TextView sub = Ui.text(this, pct + "% · 调整后界面会重新载入", 9.2f, Ui.MUTED);
        sub.setPadding(0, Ui.dp(this, 1), 0, 0);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView minus = smallButton("−");
        minus.setOnClickListener(v -> changeScale(-0.04f));
        row.addView(minus, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 28)));
        TextView plus = smallButton("+");
        plus.setOnClickListener(v -> changeScale(0.04f));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                Ui.dp(this, 30), Ui.dp(this, 28));
        plp.leftMargin = Ui.dp(this, 5);
        row.addView(plus, plp);
        return row;
    }

    private void changeScale(float delta) {
        settings.setUiScale(settings.getUiScale() + delta);
        settings.setScaleConfigured();
        Ui.toast(this, "界面缩放 " + Math.round(settings.getUiScale() * 100) + "%");
        App.MAIN.postDelayed(this::recreate, 160);
    }

    private View dataCard() {
        LinearLayout card = card();
        int[] stats = store.stats();
        TextView count = Ui.text(this, "本地 " + stats[0] + " 条记录 · 共 " + stats[3] + " 分钟", 10.2f, Ui.MUTED);
        count.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 7));
        card.addView(count);
        TextView tutorial = actionButton("重新查看使用教程", false);
        tutorial.setOnClickListener(v -> startActivity(
                new Intent(this, TutorialActivity.class).putExtra("replay", true)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 35));
        tlp.topMargin = Ui.dp(this, 8);
        card.addView(tutorial, tlp);
        TextView clear = actionButton("清空全部记录", false);
        clear.setTextColor(Ui.ERROR);
        clear.setOnClickListener(v -> Ui.confirm(this, "清空全部记录？",
                "所有转写、摘要和本地录音片段都会删除，无法恢复。", "清空", () -> {
                    store.clearAll();
                    Ui.toast(this, "已清空");
                    reload();
                }));
        card.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 35)));
        return card;
    }

    private View aboutCard() {
        LinearLayout card = card();
        card.setBackground(Ui.roundStroke(Ui.SURFACE, 14, Ui.BORDER_SOFT, 1, this));
        TextView t = Ui.mediumText(this, "听记 1.0.4", 11.5f, Ui.TEXT);
        card.addView(t);
        TextView s = Ui.text(this, "32 位纯 Java · 无原生库 · 适配 Android 6+\n"
                + "记录、密钥只保存在手表本地。\n"
                + "语音转写由你配置的 ASR 服务完成；DeepSeek 负责纠错、总结、待办和分类。",
                9.3f, Ui.DIM);
        s.setLineSpacing(Ui.dp(this, 3), 1f);
        s.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 1));
        card.addView(s);
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 11), Ui.dp(this, 7), Ui.dp(this, 11), Ui.dp(this, 9));
        card.setBackground(Ui.roundStroke(Ui.SURFACE, 14, Ui.BORDER_SOFT, 1, this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 2);
        card.setLayoutParams(lp);
        return card;
    }

    private View sectionLabel(String text) {
        TextView v = Ui.mediumText(this, text, 9.2f, Ui.DIM);
        v.setLetterSpacing(0.1f);
        v.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, Ui.dp(this, 4));
        return v;
    }

    private View row(String title, String value, Runnable click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.mediumText(this, title, 11.1f, Ui.TEXT));
        TextView v = Ui.text(this, value == null ? "" : value, 9.2f, Ui.MUTED);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setPadding(0, Ui.dp(this, 1), 0, 0);
        texts.addView(v);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        GlyphView chevron = new GlyphView(this, GlyphView.CHEVRON);
        chevron.setColor(0xFF435452);
        row.addView(chevron, new LinearLayout.LayoutParams(
                Ui.dp(this, 12), Ui.dp(this, 12)));
        row.setBackground(Ui.ripple(0x12FFFFFF, 8, this));
        row.setOnClickListener(v2 -> click.run());
        return row;
    }

    private View toggleRow(String title, String sub, boolean checked, TinySwitch.OnChanged changed) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.mediumText(this, title, 11.1f, Ui.TEXT));
        TextView s = Ui.text(this, sub, 9.1f, Ui.MUTED);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        s.setPadding(0, Ui.dp(this, 1), 0, 0);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TinySwitch sw = new TinySwitch(this);
        sw.setChecked(checked);
        sw.setContentDescription(title);
        sw.setOnChanged(changed);
        row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 24)));
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.BORDER_SOFT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = Ui.dp(this, 1);
        lp.bottomMargin = Ui.dp(this, 1);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView actionButton(String text, boolean accent) {
        TextView v = Ui.mediumText(this, text, 10.5f, accent ? Ui.ACCENT_INK : Ui.ACCENT);
        v.setGravity(Gravity.CENTER);
        v.setBackground(accent
                ? Ui.rippleBg(Ui.gradient(0xFF5EEAD4, 0xFF14B8A6, 11, this),
                0x44FFFFFF, 11, this)
                : Ui.rippleBg(Ui.roundStroke(Ui.SURFACE2, 11, Ui.BORDER, 1, this),
                0x22FFFFFF, 11, this));
        return v;
    }

    private TextView smallButton(String text) {
        TextView v = Ui.mediumText(this, text, 15f, Ui.TEXT);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Ui.rippleBg(Ui.roundStroke(Ui.SURFACE2, 9, Ui.BORDER, 1, this),
                0x22FFFFFF, 9, this));
        return v;
    }

    private void showAsrPreset() {
        String[] items = {"硅基流动 · TeleSpeechASR", "硅基流动 · Qwen3-ASR-1.7B",
                "OpenAI Whisper", "Groq Whisper", "自定义接口"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("语音识别服务")
                .setItems(items, (d, which) -> {
                    if (which == 0) settings.applyAsrPreset("siliconflow");
                    else if (which == 1) settings.applyAsrPreset("qwen");
                    else if (which == 2) settings.applyAsrPreset("openai");
                    else if (which == 3) settings.applyAsrPreset("groq");
                    else settings.applyAsrPreset("custom");
                    reload();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void testAI() {
        if (!settings.hasDeepSeekKey()) {
            Ui.toast(this, "先填写 DeepSeek API Key");
            return;
        }
        Ui.toast(this, "正在测试 AI…");
        App.IO.execute(() -> {
            try {
                new AiClient().test(settings);
                App.MAIN.post(() -> Ui.toast(this, "AI 连接成功"));
            } catch (Throwable t) {
                String m = t.getMessage() == null ? "AI 连接失败" : t.getMessage();
                App.MAIN.post(() -> Ui.toast(this, m));
            }
        });
    }

    private void testASR() {
        if (!settings.hasAsrKey()) {
            Ui.toast(this, "先填写语音识别 Key");
            return;
        }
        Ui.toast(this, "正在测试语音接口…");
        App.IO.execute(() -> {
            File f = new File(getCacheDir(), "asr_test.wav");
            try {
                WavUtil.writeSilence(f, 3000);
                new AsrClient().test(settings, f);
                App.MAIN.post(() -> Ui.toast(this, "语音接口连接成功"));
            } catch (Throwable t) {
                String m = t.getMessage() == null ? "语音接口连接失败" : t.getMessage();
                App.MAIN.post(() -> Ui.toast(this, m));
            } finally {
                try {
                    f.delete();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) fullscreen();
    }
}
