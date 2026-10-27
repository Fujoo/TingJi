package com.wrist.tingji.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wrist.tingji.App;
import com.wrist.tingji.core.Http;
import com.wrist.tingji.core.Note;
import com.wrist.tingji.core.NoteStore;
import com.wrist.tingji.core.RecorderService;
import com.wrist.tingji.core.RecorderState;
import com.wrist.tingji.core.Settings;
import com.wrist.tingji.util.GlyphView;
import com.wrist.tingji.util.Ui;
import com.wrist.tingji.util.WaveformView;

public class RecordActivity extends ScaledActivity {
    private static final int REQ_AUDIO = 91;

    private Settings settings;
    private NoteStore store;
    private long noteId;
    private String kind = "会议";
    private boolean started;
    private boolean stopRequested;
    private boolean finishingScreen;
    private int chrome = -1;
    private long startRequestedAt;
    private long lastOnlineCheck;
    private boolean lastOnline;

    private TextView timer;
    private TextView status;
    private LiveTranscriptView liveText;
    private TextView liveCount;
    private TextView online;
    private TextView meetingChip;
    private TextView classChip;
    private TextView mainLabel;
    private TextView pauseLabel;
    private TextView markLabel;
    private View mainButton;
    private View pauseButton;
    private View markButton;
    private GlyphView mainIcon;
    private GlyphView pauseIcon;
    private GlyphView markIcon;
    private WaveformView wave;
    private LinearLayout kindRow;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            tick();
            App.MAIN.postDelayed(this, 280);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = App.get().settings;
        store = App.get().store;
        fullscreen();
        kind = settings.getLastKind();
        long extra = getIntent().getLongExtra("note_id", 0);
        if (RecorderState.active && RecorderState.noteId > 0) {
            noteId = RecorderState.noteId;
            kind = RecorderState.kind;
            started = true;
        } else if (RecorderState.processing && RecorderState.noteId > 0) {
            Intent i = new Intent(this, NoteActivity.class)
                    .putExtra("note_id", RecorderState.noteId);
            startActivity(i);
            finish();
            return;
        } else if (extra > 0) {
            Note n = store.getNote(extra);
            if (n != null && n.status == Note.RECORDING) {
                noteId = n.id;
                kind = n.kind;
                started = true;
            }
        }
        buildUi();
        updateKind();
        applyIdle();
        App.MAIN.post(ticker);
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
        col.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 3));
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 36)));
        col.addView(buildKindRow(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 27)));
        Ui.setMargins(col.getChildAt(col.getChildCount() - 1), 0, Ui.dp(this, 3), 0, Ui.dp(this, 1));

        LinearLayout timerBox = new LinearLayout(this);
        timerBox.setOrientation(LinearLayout.VERTICAL);
        timerBox.setGravity(Gravity.CENTER_HORIZONTAL);
        timer = Ui.monoText(this, "00:00", 33f, Ui.TEXT);
        timer.setLetterSpacing(0.04f);
        timer.setGravity(Gravity.CENTER);
        timerBox.addView(timer);
        status = Ui.mediumText(this, "准备开始", 9.2f, Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(this, 1), 0, 0);
        timerBox.addView(status);
        col.addView(timerBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        wave = new WaveformView(this);
        col.addView(wave, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));

        col.addView(buildLiveCard(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54)));
        Ui.setMargins(col.getChildAt(col.getChildCount() - 1), 0, Ui.dp(this, 2), 0, 0);

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        col.addView(buildControls(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 82)));

        TextView hint = Ui.text(this, "息屏或退出后，录音会在后台继续", 8.5f, Ui.DIM);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 10)));

        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout back = new FrameLayout(this);
        back.setBackground(Ui.ripple(0x1FFFFFFF, 18, this));
        back.setContentDescription("返回");
        GlyphView arrow = new GlyphView(this, GlyphView.BACK);
        arrow.setColor(Ui.TEXT);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                Ui.dp(this, 19), Ui.dp(this, 19));
        alp.gravity = Gravity.CENTER;
        back.addView(arrow, alp);
        back.setOnClickListener(v -> onBackPressed());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.dp(this, 8), 0, 0, 0);
        TextView title = Ui.mediumText(this, "新记录", 13.5f, Ui.TEXT);
        mid.addView(title);
        online = Ui.text(this, "在线 · 自动转写", 9f, Ui.DIM);
        online.setPadding(0, Ui.dp(this, 1), 0, 0);
        mid.addView(online);
        bar.addView(mid, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return bar;
    }

    private View buildKindRow() {
        kindRow = new LinearLayout(this);
        kindRow.setOrientation(LinearLayout.HORIZONTAL);
        kindRow.setGravity(Gravity.CENTER);
        meetingChip = kindChip("会议");
        classChip = kindChip("课堂");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 24));
        lp.rightMargin = Ui.dp(this, 6);
        kindRow.addView(meetingChip, lp);
        kindRow.addView(classChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 24)));
        meetingChip.setOnClickListener(v -> {
            if (!started) {
                kind = "会议";
                updateKind();
            }
        });
        classChip.setOnClickListener(v -> {
            if (!started) {
                kind = "课堂";
                updateKind();
            }
        });
        return kindRow;
    }

    private TextView kindChip(String text) {
        TextView v = Ui.mediumText(this, text, 10.5f, Ui.MUTED);
        v.setGravity(Gravity.CENTER);
        v.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        v.setBackground(Ui.rippleBg(Ui.round(Ui.SURFACE2, 13, this),
                0x22FFFFFF, 13, this));
        return v;
    }

    private void updateKind() {
        boolean meeting = "会议".equals(kind);
        meetingChip.setTextColor(meeting ? Ui.ACCENT_INK : Ui.MUTED);
        meetingChip.setBackground(meeting
                ? Ui.rippleBg(Ui.round(Ui.ACCENT, 13, this), 0x44FFFFFF, 13, this)
                : Ui.rippleBg(Ui.round(Ui.SURFACE2, 13, this), 0x22FFFFFF, 13, this));
        classChip.setTextColor(!meeting ? Ui.ACCENT_INK : Ui.MUTED);
        classChip.setBackground(!meeting
                ? Ui.rippleBg(Ui.round(Ui.ACCENT, 13, this), 0x44FFFFFF, 13, this)
                : Ui.rippleBg(Ui.round(Ui.SURFACE2, 13, this), 0x22FFFFFF, 13, this));
        boolean enabled = !started;
        meetingChip.setAlpha(enabled ? 1f : 0.65f);
        classChip.setAlpha(enabled ? 1f : 0.65f);
        if (!started && liveText != null) liveText.setText(liveHint());
    }

    private String liveHint() {
        return "课堂".equals(kind)
                ? "点开始后，每 30–60 秒转写一段；\n课堂会整理知识点、例题、作业和重点"
                : "点开始后，每 30–60 秒转写一段；\n会议会整理结论、待办和负责人";
    }

    private View buildLiveCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 6));
        card.setBackground(Ui.round(0xFF0F1518, 14, this));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = Ui.mediumText(this, "实时转写", 9.0f, Ui.ACCENT);
        head.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        liveCount = Ui.text(this, "0 段", 8.8f, Ui.DIM);
        head.addView(liveCount);
        card.addView(head);

        liveText = new LiveTranscriptView(this);
        liveText.setText("点开始后，每 30–60 秒自动转写一段；\n说完停顿一下，分段会更整齐");
        liveText.setTextColor(Ui.MUTED);
        liveText.setLineSpacing(Ui.dp(this, 2), 1f);
        liveText.setPadding(0, Ui.dp(this, 3), 0, 0);
        liveText.setMaxLines(2);
        liveText.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(liveText);
        return card;
    }

    private View buildControls() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        LinearLayout pauseCol = controlColumn(48, GlyphView.PAUSE, "暂停", Ui.SURFACE2, Ui.MUTED);
        pauseButton = pauseCol.getChildAt(0);
        pauseIcon = (GlyphView) ((FrameLayout) pauseButton).getChildAt(0);
        pauseLabel = (TextView) pauseCol.getChildAt(1);
        pauseButton.setOnClickListener(v -> togglePause());
        row.addView(pauseCol, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 76)));

        View spacer1 = new View(this);
        row.addView(spacer1, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));

        LinearLayout mainCol = controlColumn(64, GlyphView.MIC, "开始", 0xFF14B8A6, Ui.ACCENT_INK);
        mainButton = mainCol.getChildAt(0);
        mainIcon = (GlyphView) ((FrameLayout) mainButton).getChildAt(0);
        mainLabel = (TextView) mainCol.getChildAt(1);
        mainButton.setOnClickListener(v -> onMainPressed());
        row.addView(mainCol, new LinearLayout.LayoutParams(Ui.dp(this, 74), Ui.dp(this, 82)));

        View spacer2 = new View(this);
        row.addView(spacer2, new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));

        LinearLayout markCol = controlColumn(48, GlyphView.MARK, "标记", Ui.SURFACE2, Ui.MUTED);
        markButton = markCol.getChildAt(0);
        markIcon = (GlyphView) ((FrameLayout) markButton).getChildAt(0);
        markLabel = (TextView) markCol.getChildAt(1);
        markButton.setOnClickListener(v -> addMark());
        row.addView(markCol, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 76)));
        return row;
    }

    private LinearLayout controlColumn(int sizeDp, int glyph, String label,
                                       int circleColor, int iconColor) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        LiquidGlassButton circle = new LiquidGlassButton(this);
        circle.setTint(circleColor);
        circle.setStrong(sizeDp >= 60);
        GlyphView icon = new GlyphView(this, glyph);
        icon.setColor(iconColor);
        icon.setStrokeDp(2.1f);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                Ui.dp(this, sizeDp * 0.46f), Ui.dp(this, sizeDp * 0.46f));
        ilp.gravity = Gravity.CENTER;
        circle.addView(icon, ilp);
        col.addView(circle, new LinearLayout.LayoutParams(
                Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)));

        TextView tv = Ui.mediumText(this, label, 9.5f, Ui.MUTED);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, Ui.dp(this, 4), 0, 0);
        col.addView(tv);
        return col;
    }

    private void setGlass(View v, int color, boolean strong) {
        if (v instanceof LiquidGlassButton) {
            LiquidGlassButton b = (LiquidGlassButton) v;
            b.setTint(color);
            b.setStrong(strong);
        }
    }

    private static int lighten(int color, int amount) {
        int r = Math.min(255, ((color >> 16) & 0xff) + amount);
        int g = Math.min(255, ((color >> 8) & 0xff) + amount);
        int b = Math.min(255, (color & 0xff) + amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void onMainPressed() {
        if (!started) {
            beginRecording();
        } else if (RecorderState.active) {
            confirmStop();
        } else if (RecorderState.processing) {
            openNote();
        } else if (noteId > 0) {
            openNote();
        }
    }

    private void beginRecording() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (RecorderState.active || RecorderState.processing) {
            Ui.toast(this, "已有一条录音正在进行");
            return;
        }
        if (!settings.hasAsrKey() || !settings.hasDeepSeekKey()) {
            String msg = "语音转写需要单独的 ASR Key；DeepSeek 负责纠错、总结和分类。"
                    + "现在可以只录音，配置后再整理。";
            new android.app.AlertDialog.Builder(this)
                    .setTitle("还没配置完整")
                    .setMessage(msg)
                    .setPositiveButton("继续录音", (d, w) -> startService())
                    .setNegativeButton("去设置",
                            (d, w) -> startActivity(new Intent(this, SettingsActivity.class)))
                    .show();
            return;
        }
        startService();
    }

    private void startService() {
        settings.setLastKind(kind);
        noteId = store.createNote(kind);
        startRequestedAt = System.currentTimeMillis();
        started = true;
        stopRequested = false;
        RecorderService.startSession(this, noteId, kind);
        Ui.haptic(this, 20);
        applyRecordingUi();
        App.MAIN.removeCallbacks(ticker);
        App.MAIN.post(ticker);
    }

    private void confirmStop() {
        Ui.confirm(this, "结束并整理？",
                "已录 " + RecorderState.clock() + "。结束后会自动转写，再让 AI 纠错、总结和分类。",
                "结束并整理", this::stopService);
    }

    private void stopService() {
        stopRequested = true;
        RecorderService.command(this, RecorderService.ACTION_STOP);
        status.setText("正在结束录音…");
        mainButton.setEnabled(false);
        mainButton.setAlpha(0.6f);
        pauseButton.setEnabled(false);
        markButton.setEnabled(false);
        App.MAIN.postDelayed(() -> {
            if (!isFinishing()) openNote();
        }, 650);
    }

    private void togglePause() {
        if (!started || !RecorderState.active) return;
        if (RecorderState.paused) {
            RecorderService.command(this, RecorderService.ACTION_RESUME);
            Ui.toast(this, "继续录音");
        } else {
            RecorderService.command(this, RecorderService.ACTION_PAUSE);
            Ui.toast(this, "已暂停");
        }
        Ui.haptic(this, 12);
        tick();
    }

    private void addMark() {
        if (!started || noteId <= 0) return;
        long at = RecorderState.elapsedMs;
        store.addMarker(noteId, at, "重点");
        Ui.haptic(this, 18);
        Ui.toast(this, "已标记 " + RecorderState.clock());
        status.setText("已标记重点 · " + RecorderState.clock());
    }

    private void openNote() {
        if (finishingScreen || noteId <= 0) return;
        finishingScreen = true;
        Intent i = new Intent(this, NoteActivity.class).putExtra("note_id", noteId);
        startActivity(i);
        finish();
    }

    private void applyIdle() {
        chrome = 0;
        timer.setText("00:00");
        status.setText(settings.hasAsrKey() ? "准备开始" : "可以录音，稍后配置转写");
        status.setTextColor(Ui.MUTED);
        liveText.setText(liveHint());
        liveText.setTextColor(Ui.MUTED);
        liveCount.setText("0 段");
        mainButton.setEnabled(true);
        mainButton.setAlpha(1f);
        pauseButton.setEnabled(false);
        pauseButton.setAlpha(0.35f);
        markButton.setEnabled(false);
        markButton.setAlpha(0.35f);
        mainIcon.setType(GlyphView.MIC);
        mainIcon.setColor(Ui.ACCENT_INK);
        mainLabel.setText("开始");
        pauseIcon.setType(GlyphView.PAUSE);
        pauseLabel.setText("暂停");
        updateOnline(true);
        wave.clear();
    }

    private void applyRecordingUi() {
        chrome = 1;
        mainButton.setEnabled(true);
        mainButton.setAlpha(1f);
        pauseButton.setEnabled(true);
        pauseButton.setAlpha(1f);
        markButton.setEnabled(true);
        markButton.setAlpha(1f);
        mainIcon.setType(GlyphView.STOP);
        mainIcon.setColor(Color.WHITE);
        mainLabel.setText("结束");
        liveText.setTextColor(Ui.TEXT);

        pauseIcon.setType(GlyphView.PAUSE);
        pauseIcon.setColor(Ui.TEXT);
        pauseLabel.setText("暂停");
        markIcon.setColor(Ui.TEXT);
        setGlass(mainButton, 0xFFB91C1C, true);
        setGlass(pauseButton, 0xFF243036, false);
        setGlass(markButton, 0xFF243036, false);
    }

    private void applyPausedUi() {
        chrome = 2;
        mainIcon.setType(GlyphView.STOP);
        mainIcon.setColor(Color.WHITE);
        mainLabel.setText("结束");
        pauseIcon.setType(GlyphView.PLAY);
        pauseIcon.setColor(Ui.ACCENT);
        pauseLabel.setText("继续");
        setGlass(pauseButton, 0xFF0F766E, false);
        liveText.setTextColor(Ui.MUTED);
        wave.decay();
    }

    private void applyProcessingUi() {
        chrome = 3;
        mainButton.setEnabled(true);
        mainButton.setAlpha(1f);
        mainIcon.setType(GlyphView.SPARK);
        mainIcon.setColor(Ui.ACCENT_INK);
        mainLabel.setText("进度");
        setGlass(mainButton, 0xFF14B8A6, true);
        pauseButton.setEnabled(false);
        pauseButton.setAlpha(0.35f);
        markButton.setEnabled(false);
        markButton.setAlpha(0.35f);
    }

    private void updateOnline(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastOnlineCheck < 5000) return;
        lastOnlineCheck = now;
        lastOnline = Http.networkAvailable(this);
        if (settings.hasAsrKey()) {
            online.setText(lastOnline ? "在线 · 自动转写" : "离线 · 录音会先保存");
        } else {
            online.setText(lastOnline ? "在线 · 先录音" : "离线 · 录音会先保存");
        }
        online.setTextColor(lastOnline ? Ui.DIM : Ui.RECORD);
    }

    private void tick() {
        if (!started) {
            if (chrome != 0) applyIdle();
            return;
        }
        if (RecorderState.active) {
            startRequestedAt = 0;
            noteId = RecorderState.noteId;
            kind = RecorderState.kind;
            timer.setText(RecorderState.clock());
            wave.push(RecorderState.level);
            updateOnline(false);
            int done = RecorderState.segmentsDone;
            int failed = RecorderState.segmentsFailed;
            liveCount.setText(done + " 段" + (failed > 0 ? " · " + failed + " 失败" : ""));
            if (RecorderState.paused) {
                if (chrome != 2) {
                    if (chrome != 1) applyRecordingUi();
                    applyPausedUi();
                }
                String last = RecorderState.lastText;
                if (!TextUtils.isEmpty(last)) {
                    liveText.setText(Ui.oneLine(last, 90));
                } else {
                    liveText.setText("已暂停。点“继续”接着记，这段时间不会录入。");
                }
                status.setText("已暂停 · 已转写 " + done + " 段");
                status.setTextColor(Ui.MUTED);
            } else {
                if (chrome != 1) applyRecordingUi();
                String last = RecorderState.lastText;
                if (!TextUtils.isEmpty(last)) {
                    liveText.setText(Ui.oneLine(last, 90));
                } else if (done == 0) {
                    liveText.setText("正在听… 第一段转写完成后会显示在这里");
                }
                if (!TextUtils.isEmpty(RecorderState.error)) {
                    status.setText(RecorderState.error);
                    status.setTextColor(Ui.ERROR);
                } else {
                    status.setText("正在录音 · 已转写 " + done + " 段"
                            + (failed > 0 ? " · " + failed + " 段失败" : ""));
                    status.setTextColor(Ui.ACCENT);
                }
            }
        } else if (RecorderState.processing) {
            if (chrome != 3) applyProcessingUi();
            timer.setText(RecorderState.clock());
            status.setText(RecorderState.statusText.isEmpty()
                    ? "AI 正在整理…" : RecorderState.statusText);
            status.setTextColor(Ui.ACCENT);
            if (stopRequested && !finishingScreen) {
                App.MAIN.postDelayed(() -> {
                    if (!isFinishing() && !finishingScreen) openNote();
                }, 450);
            }
        } else if (!TextUtils.isEmpty(RecorderState.error)) {
            status.setText(RecorderState.error);
            status.setTextColor(Ui.ERROR);
            if (chrome != 4) {
                chrome = 4;
                mainButton.setEnabled(true);
                mainButton.setAlpha(1f);
                mainIcon.setType(GlyphView.NOTE);
                mainIcon.setColor(Ui.ACCENT_INK);
                mainLabel.setText("查看");
                setGlass(mainButton, 0xFF14B8A6, true);
                pauseButton.setEnabled(false);
                pauseButton.setAlpha(0.35f);
                markButton.setEnabled(false);
                markButton.setAlpha(0.35f);
            }
            if (System.currentTimeMillis() - startRequestedAt > 4000) openNote();
        } else if (stopRequested) {
            openNote();
        } else if (startRequestedAt > 0
                && System.currentTimeMillis() - startRequestedAt < 3000) {
            status.setText("正在启动麦克风…");
            status.setTextColor(Ui.MUTED);
        } else if (noteId > 0) {
            openNote();
        } else {
            if (chrome != 0) applyIdle();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginRecording();
            } else {
                Ui.confirm(this, "需要麦克风权限",
                        "听记只在本机录音，音频仅发送到你配置的语音识别服务。",
                        "重试", () -> requestPermissions(
                                new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (started && RecorderState.active) {
            android.app.AlertDialog d = new android.app.AlertDialog.Builder(this)
                    .setTitle("录音正在进行")
                    .setMessage("退出后录音会在后台继续，通知栏可随时回来。")
                    .setPositiveButton("后台继续", (x, y) -> finish())
                    .setNeutralButton("结束记录", (x, y) -> {
                        stopRequested = true;
                        RecorderService.command(this, RecorderService.ACTION_STOP);
                        finish();
                    })
                    .setNegativeButton("取消", null)
                    .create();
            d.show();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.MAIN.removeCallbacks(ticker);
        App.MAIN.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.MAIN.removeCallbacks(ticker);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) fullscreen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        App.MAIN.removeCallbacks(ticker);
    }
}
