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
import com.wrist.tingji.core.SetupServer;
import com.wrist.tingji.core.Settings;
import com.wrist.tingji.util.GlyphView;
import com.wrist.tingji.util.Ui;

public class SetupActivity extends ScaledActivity {
    private Settings settings;
    private SetupServer server;
    private TextView urlView;
    private TextView statusView;
    private TextView copyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = App.get().settings;
        fullscreen();
        buildUi();
        startServer();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        col.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 8));
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
        TextView title = Ui.mediumText(this, "手机配置", 16f, Ui.TEXT);
        title.setPadding(Ui.dp(this, 9), 0, 0, 0);
        bar.addView(title);
        col.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 11), Ui.dp(this, 12), Ui.dp(this, 11));
        card.setBackground(Ui.roundStroke(0xFF0E1D1B, 15, 0xFF1D3A36, 1, this));

        TextView head = Ui.mediumText(this, "在手机浏览器打开这个地址", 11.5f, Ui.TEXT);
        card.addView(head);
        TextView sub = Ui.text(this, "手机和手表需连同一 WiFi；Key 只保存在手表本地。", 9.4f, Ui.MUTED);
        sub.setLineSpacing(Ui.dp(this, 2), 1f);
        sub.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 8));
        card.addView(sub);

        urlView = Ui.monoText(this, "正在启动…", 11.5f, Ui.ACCENT);
        urlView.setGravity(Gravity.CENTER_HORIZONTAL);
        urlView.setTextIsSelectable(true);
        urlView.setPadding(Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10));
        urlView.setBackground(Ui.round(0xFF08110F, 10, this));
        card.addView(urlView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        copyButton = Ui.mediumText(this, "复制地址", 10.5f, Ui.ACCENT_INK);
        copyButton.setGravity(Gravity.CENTER);
        copyButton.setBackground(Ui.rippleBg(Ui.gradient(0xFF5EEAD4, 0xFF14B8A6, 11, this),
                0x44FFFFFF, 11, this));
        copyButton.setOnClickListener(v -> {
            String u = urlView.getText().toString();
            if (u.startsWith("http")) Ui.copy(this, u, "听记配置");
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 35));
        clp.topMargin = Ui.dp(this, 10);
        card.addView(copyButton, clp);

        statusView = Ui.text(this, "", 9.2f, Ui.DIM);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(statusView);
        col.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView steps = Ui.text(this, "可以设置：\n"
                + "· DeepSeek API Key、接口和模型\n"
                + "· 语音识别 Key、服务和识别模型\n"
                + "· 自动转写 / 纠错 / 总结与分类\n"
                + "· 专有名词热词、识别语言\n\n"
                + "页面关闭后，这个本地配置服务会自动停止。", 9.4f, Ui.DIM);
        steps.setLineSpacing(Ui.dp(this, 3), 1f);
        steps.setPadding(Ui.dp(this, 3), Ui.dp(this, 12), Ui.dp(this, 3), 0);
        col.addView(steps, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void startServer() {
        server = new SetupServer(this, settings, () -> runOnUiThread(() -> {
            statusView.setText("✓ 配置已保存，可以关闭手机页面");
            statusView.setTextColor(Ui.SUCCESS);
            Ui.toast(this, "配置已保存");
        }));
        try {
            server.start();
            String url = server.url();
            if (url == null) {
                urlView.setText("未连接 WiFi\n请先在手表设置中连接 WiFi");
                urlView.setTextColor(Ui.ERROR);
                statusView.setText("配置服务已启动，但需要局域网地址");
                copyButton.setEnabled(false);
                copyButton.setAlpha(0.45f);
            } else {
                urlView.setText(url);
                urlView.setTextColor(Ui.ACCENT);
                statusView.setText("等待手机打开…");
                copyButton.setEnabled(true);
                copyButton.setAlpha(1f);
            }
        } catch (Throwable t) {
            urlView.setText("启动失败");
            urlView.setTextColor(Ui.ERROR);
            statusView.setText(t.getMessage() == null ? "端口被占用" : t.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (server != null) {
            String url = server.url();
            if (url != null && urlView != null && !urlView.getText().toString().startsWith("http")) {
                urlView.setText(url);
                urlView.setTextColor(Ui.ACCENT);
                statusView.setText("等待手机打开…");
                if (copyButton != null) {
                    copyButton.setEnabled(true);
                    copyButton.setAlpha(1f);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) server.stop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) fullscreen();
    }
}
