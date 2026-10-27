package com.wrist.tingji.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wrist.tingji.App;
import com.wrist.tingji.core.Note;
import com.wrist.tingji.core.NoteAI;
import com.wrist.tingji.core.NoteStore;
import com.wrist.tingji.core.RecorderService;
import com.wrist.tingji.core.RecorderState;
import com.wrist.tingji.core.Settings;
import com.wrist.tingji.util.GlyphView;
import com.wrist.tingji.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class NoteActivity extends ScaledActivity {
    private static final String[] TABS = {"摘要", "要点", "待办", "全文"};

    private Settings settings;
    private NoteStore store;
    private long noteId;
    private Note note;
    private String tab = "摘要";
    private boolean showCorrected = true;

    private TextView titleView;
    private TextView metaView;
    private TextView catView;
    private FrameLayout catBadge;
    private LinearLayout tabsRow;
    private LinearLayout contentBox;
    private ScrollView contentScroll;
    private TextView aiLabel;
    private View aiButton;
    private String contentSignature = "";
    private String tabsKind = "";

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            reload();
            App.MAIN.postDelayed(this, 1100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = App.get().settings;
        store = App.get().store;
        noteId = getIntent().getLongExtra("note_id", 0);
        fullscreen();
        buildUi();
        updateTabs();
        reload();
        App.MAIN.post(refreshTask);
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
        col.setPadding(Ui.dp(this, 13), Ui.dp(this, 4), Ui.dp(this, 13), Ui.dp(this, 4));
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42)));
        col.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(buildTabs(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 31)));
        Ui.setMargins(col.getChildAt(col.getChildCount() - 1), 0, Ui.dp(this, 4), 0, Ui.dp(this, 3));

        contentScroll = new ScrollView(this);
        contentScroll.setVerticalScrollBarEnabled(false);
        contentScroll.setClipToPadding(false);
        contentBox = new LinearLayout(this);
        contentBox.setOrientation(LinearLayout.VERTICAL);
        contentBox.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 6));
        contentScroll.addView(contentBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(contentScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        col.addView(buildActions(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
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
                Ui.dp(this, 18), Ui.dp(this, 18));
        alp.gravity = Gravity.CENTER;
        back.addView(arrow, alp);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));

        catBadge = new FrameLayout(this);
        catBadge.setBackground(Ui.round(0x22FFFFFF, 10, this));
        catView = Ui.mediumText(this, "会议", 9.8f, Ui.MEETING);
        catView.setGravity(Gravity.CENTER);
        catView.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        catBadge.addView(catView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        catBadge.setOnClickListener(v -> showKindPicker());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 25));
        clp.leftMargin = Ui.dp(this, 8);
        bar.addView(catBadge, clp);

        TextView spacer = new TextView(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        FrameLayout more = new FrameLayout(this);
        more.setBackground(Ui.ripple(0x1FFFFFFF, 18, this));
        more.setContentDescription("更多");
        GlyphView dots = new GlyphView(this, GlyphView.MORE);
        dots.setColor(Ui.MUTED);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18));
        mlp.gravity = Gravity.CENTER;
        more.addView(dots, mlp);
        more.setOnClickListener(v -> showMore());
        bar.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        return bar;
    }

    private View buildHeader() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 1), Ui.dp(this, 1), Ui.dp(this, 1), Ui.dp(this, 2));

        titleView = Ui.mediumText(this, "加载中…", 16.5f, Ui.TEXT);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setLineSpacing(Ui.dp(this, 2), 1f);
        titleView.setOnClickListener(v -> rename());
        box.addView(titleView);

        metaView = Ui.text(this, "", 9.5f, Ui.MUTED);
        metaView.setSingleLine(true);
        metaView.setPadding(0, Ui.dp(this, 3), 0, 0);
        box.addView(metaView);
        return box;
    }

    private View buildTabs() {
        tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setBackground(Ui.round(Ui.SURFACE, 10, this));
        tabsRow.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        for (String item : TABS) {
            TextView v = Ui.mediumText(this, item, 10.2f, Ui.MUTED);
            v.setGravity(Gravity.CENTER);
            v.setTag(item);
            v.setOnClickListener(x -> {
                tab = item;
                updateTabs();
                contentSignature = "";
                renderContent();
            });
            tabsRow.addView(v, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return tabsRow;
    }

    private boolean isClassNote() {
        return note != null && "课堂".equals(note.kind);
    }

    private void updateTabs() {
        boolean classNote = isClassNote();
        String labelKind = classNote ? "课堂" : "会议";
        boolean labelsChanged = !labelKind.equals(tabsKind);
        tabsKind = labelKind;
        String[] labels = classNote
                ? new String[]{"摘要", "知识点", "作业", "全文"}
                : new String[]{"摘要", "要点", "待办", "全文"};
        for (int i = 0; i < tabsRow.getChildCount(); i++) {
            TextView v = (TextView) tabsRow.getChildAt(i);
            if (labelsChanged) v.setText(labels[i]);
            boolean on = tab.equals(v.getTag());
            v.setTextColor(on ? Ui.ACCENT : Ui.MUTED);
            v.setBackground(on ? Ui.round(Ui.SURFACE3, 8, this) : null);
        }
    }

    private View buildActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 1));

        LinearLayout share = new LinearLayout(this);
        share.setOrientation(LinearLayout.HORIZONTAL);
        share.setGravity(Gravity.CENTER);
        share.setBackground(Ui.rippleBg(Ui.roundStroke(Ui.SURFACE, 13, Ui.BORDER, 1, this),
                0x22FFFFFF, 13, this));
        GlyphView sh = new GlyphView(this, GlyphView.SHARE);
        sh.setColor(Ui.MUTED);
        share.addView(sh, new LinearLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 14)));
        TextView sl = Ui.mediumText(this, "分享", 10f, Ui.MUTED);
        sl.setPadding(Ui.dp(this, 5), 0, 0, 0);
        share.addView(sl);
        share.setOnClickListener(v -> shareNote());
        LinearLayout.LayoutParams shlp = new LinearLayout.LayoutParams(
                Ui.dp(this, 78), Ui.dp(this, 43));
        shlp.rightMargin = Ui.dp(this, 7);
        row.addView(share, shlp);

        aiButton = new LinearLayout(this);
        ((LinearLayout) aiButton).setOrientation(LinearLayout.HORIZONTAL);
        ((LinearLayout) aiButton).setGravity(Gravity.CENTER);
        aiButton.setBackground(Ui.rippleBg(
                Ui.gradient(0xFF5EEAD4, 0xFF14B8A6, 13, this), 0x44FFFFFF, 13, this));
        GlyphView spark = new GlyphView(this, GlyphView.SPARK);
        spark.setColor(Ui.ACCENT_INK);
        ((LinearLayout) aiButton).addView(spark, new LinearLayout.LayoutParams(
                Ui.dp(this, 14), Ui.dp(this, 14)));
        aiLabel = Ui.mediumText(this, "重新整理", 10.5f, Ui.ACCENT_INK);
        aiLabel.setPadding(Ui.dp(this, 5), 0, 0, 0);
        ((LinearLayout) aiButton).addView(aiLabel);
        aiButton.setOnClickListener(v -> reprocess());
        row.addView(aiButton, new LinearLayout.LayoutParams(0, Ui.dp(this, 43), 1f));
        return row;
    }

    private void reload() {
        Note n = noteId > 0 ? store.getNote(noteId) : null;
        if (n == null) {
            if (!isFinishing()) {
                Ui.toast(this, "记录不存在");
                finish();
            }
            return;
        }
        note = n;
        updateHeader();
        if (!note.kind.equals(tabsKind)) updateTabs();
        updateAction();
        String sig = note.id + "|" + note.updatedAt + "|" + note.status + "|" + tab
                + "|" + showCorrected + "|" + note.segmentsDone + "|" + note.segmentsFailed;
        if (!sig.equals(contentSignature)) {
            contentSignature = sig;
            renderContent();
        }
    }

    private String headerSignature = "";

    private void updateHeader() {
        String sig = note.title + "|" + note.kind + "|" + note.subject + "|"
                + note.createdAt + "|" + note.durationMs
                + "|" + note.status + "|" + note.segmentsDone + "|" + note.segmentsFailed;
        if (sig.equals(headerSignature)) return;
        headerSignature = sig;
        titleView.setText(note.title);
        catView.setText(note.kind);
        catView.setTextColor(Ui.kindColor(note.kind));
        catBadge.setBackground(Ui.round(
                (Ui.kindColor(note.kind) & 0x00FFFFFF) | 0x22000000, 10, this));
        StringBuilder meta = new StringBuilder();
        meta.append(Ui.dateTime(note.createdAt));
        if ("课堂".equals(note.kind) && note.subject != null && !note.subject.trim().isEmpty()) {
            meta.append(" · ").append(note.subject.trim());
        }
        if (note.durationMs > 0) meta.append(" · ").append(Ui.durationShort(note.durationMs));
        if (note.status == Note.READY && note.segmentsDone > 0) {
            meta.append(" · ").append(note.segmentsDone).append(" 段 · 已整理");
        } else if (note.isBusy()) {
            meta.append(" · ").append(note.statusLabel());
        } else if (note.segmentsFailed > 0) {
            meta.append(" · ").append(note.segmentsFailed).append(" 段待重试");
        } else if (note.status == Note.READY) {
            meta.append(" · 已整理");
        }
        metaView.setText(meta.toString());
        metaView.setTextColor(note.isBusy() ? Ui.ACCENT
                : note.segmentsFailed > 0 ? Ui.ERROR : Ui.MUTED);
    }

    private void updateAction() {
        if (note.isBusy()) {
            aiLabel.setText(note.status == Note.RECORDING ? "录音中…" : "整理中…");
            aiButton.setEnabled(false);
            aiButton.setAlpha(0.65f);
            aiButton.setBackground(Ui.round(Ui.SURFACE2, 13, this));
            aiLabel.setTextColor(Ui.MUTED);
            return;
        }
        aiButton.setEnabled(true);
        aiButton.setAlpha(1f);
        aiButton.setBackground(Ui.rippleBg(
                Ui.gradient(0xFF5EEAD4, 0xFF14B8A6, 13, this), 0x44FFFFFF, 13, this));
        aiLabel.setTextColor(Ui.ACCENT_INK);
        if (note.status == Note.DRAFT && note.segmentsFailed > 0) {
            aiLabel.setText("重试转写");
        } else if (note.status == Note.READY) {
            aiLabel.setText("重新整理");
        } else {
            aiLabel.setText("AI 整理");
        }
    }

    private void renderContent() {
        contentBox.removeAllViews();
        switch (tab) {
            case "要点":
                renderPoints();
                break;
            case "待办":
                renderActions();
                break;
            case "全文":
                renderTranscript();
                break;
            default:
                renderSummary();
                break;
        }
    }

    private void renderSummary() {
        if (note.status == Note.RECORDING) {
            contentBox.addView(progressCard("正在录音",
                    "回到录音页结束录音后，会自动转写并整理。"));
        } else if (note.status == Note.TRANSCRIBING) {
            contentBox.addView(progressCard("正在转写录音",
                    "已转写 " + note.segmentsDone + " / " + Math.max(1, note.segmentsTotal)
                            + " 段，完成后自动纠错、总结和分类。"));
        } else if (note.status == Note.ANALYZING) {
            contentBox.addView(progressCard("AI 正在整理",
                    isClassNote() ? "正在生成课堂摘要、知识点、例题和作业。长录音需要一点时间。"
                            : "正在纠错并生成摘要、要点和待办。长录音需要一点时间。"));
        }

        if (!TextUtils.isEmpty(note.summary)) {
            contentBox.addView(sectionTitle(isClassNote() ? "本课摘要" : "摘要", GlyphView.SPARK));
            TextView body = body(note.summary, 12.4f);
            body.setTextIsSelectable(true);
            contentBox.addView(body, blockLp());
        } else if (note.status == Note.DRAFT) {
            String err = TextUtils.isEmpty(note.aiError) ? "还没有生成摘要" : note.aiError;
            contentBox.addView(errorCard(err));
        }

        if (!TextUtils.isEmpty(note.decisions)) {
            contentBox.addView(sectionTitle(isClassNote() ? "重点 / 难点 / 易错点" : "结论 / 决定",
                    GlyphView.CHECK));
            contentBox.addView(bulletList(note.decisions, Ui.ACCENT));
        }

        if (!TextUtils.isEmpty(note.tags)) {
            contentBox.addView(sectionTitle(isClassNote() ? "知识点标签" : "标签", GlyphView.MARK));
            contentBox.addView(tagRow(note.tags));
        }

        if (note.status == Note.READY && TextUtils.isEmpty(note.summary)
                && TextUtils.isEmpty(note.decisions)) {
            contentBox.addView(emptyHint("这条记录还没有摘要，可以点“重新整理”。"));
        }
    }

    private void renderPoints() {
        boolean classNote = isClassNote();
        if (TextUtils.isEmpty(note.keyPoints)) {
            contentBox.addView(emptyHint(note.isBusy()
                    ? (classNote ? "AI 正在提取知识点…" : "AI 正在提取要点…")
                    : (classNote ? "还没有知识点。点“AI 整理”生成。" : "还没有要点。点“AI 整理”生成。")));
            return;
        }
        contentBox.addView(sectionTitle(classNote ? "知识点" : "关键要点", GlyphView.SPARK));
        String[] items = note.keyPoints.split("\\n");
        int index = 1;
        for (String item : items) {
            String s = item.trim();
            if (s.isEmpty()) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));

            TextView num = Ui.mediumText(this, String.valueOf(index), 9.5f, Ui.ACCENT);
            num.setGravity(Gravity.CENTER);
            num.setBackground(Ui.round(0xFF12312C, 10, this));
            row.addView(num, new LinearLayout.LayoutParams(
                    Ui.dp(this, 20), Ui.dp(this, 20)));
            TextView body = Ui.text(this, s, 11.4f, Ui.TEXT);
            body.setLineSpacing(Ui.dp(this, 3), 1f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blp.leftMargin = Ui.dp(this, 8);
            row.addView(body, blp);
            contentBox.addView(row, blockLp());
            index++;
        }
        if (classNote && !TextUtils.isEmpty(note.examples)) {
            contentBox.addView(sectionTitle("例题 / 案例", GlyphView.NOTE));
            contentBox.addView(bulletList(note.examples, Ui.CLASS));
        }
    }

    private void renderActions() {
        boolean classNote = isClassNote();
        if (TextUtils.isEmpty(note.actions)) {
            contentBox.addView(emptyHint(note.isBusy()
                    ? (classNote ? "AI 正在整理作业…" : "AI 正在提取待办…")
                    : (classNote ? "老师没有布置作业，或 AI 没有提取到。" : "没有待办事项。")));
            return;
        }
        contentBox.addView(sectionTitle(classNote ? "作业与复习" : "待办事项", GlyphView.CHECK));
        for (String item : note.actions.split("\\n")) {
            String s = item.trim();
            if (s.isEmpty()) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Ui.dp(this, 9), Ui.dp(this, 8), Ui.dp(this, 9), Ui.dp(this, 8));
            card.setBackground(Ui.roundStroke(Ui.SURFACE, 11, Ui.BORDER_SOFT, 1, this));
            View dot = new View(this);
            dot.setBackground(Ui.roundStroke(Color.TRANSPARENT, 8, Ui.ACCENT, 1.5f, this));
            card.addView(dot, new LinearLayout.LayoutParams(
                    Ui.dp(this, 15), Ui.dp(this, 15)));
            TextView body = Ui.text(this, s, 11.1f, Ui.TEXT);
            body.setLineSpacing(Ui.dp(this, 2), 1f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blp.leftMargin = Ui.dp(this, 8);
            card.addView(body, blp);
            contentBox.addView(card, blockLp());
        }
    }

    private void renderTranscript() {
        if (!TextUtils.isEmpty(note.markers)) {
            contentBox.addView(sectionTitle("重点标记", GlyphView.MARK));
            contentBox.addView(body(note.markers, 10.5f, Ui.MEETING), blockLp());
        }

        boolean hasCorrected = !TextUtils.isEmpty(note.corrected);
        if (hasCorrected) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackground(Ui.round(Ui.SURFACE, 10, this));
            row.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
            TextView fixed = toggleChip("纠错后", showCorrected);
            TextView raw = toggleChip("原转写", !showCorrected);
            fixed.setOnClickListener(v -> {
                showCorrected = true;
                contentSignature = "";
                renderContent();
            });
            raw.setOnClickListener(v -> {
                showCorrected = false;
                contentSignature = "";
                renderContent();
            });
            row.addView(fixed, new LinearLayout.LayoutParams(0,
                    Ui.dp(this, 27), 1f));
            row.addView(raw, new LinearLayout.LayoutParams(0,
                    Ui.dp(this, 27), 1f));
            LinearLayout.LayoutParams rlp = blockLp();
            rlp.bottomMargin = Ui.dp(this, 5);
            contentBox.addView(row, rlp);
        }

        String text = showCorrected && hasCorrected ? note.corrected : note.transcript;
        if (TextUtils.isEmpty(text)) {
            contentBox.addView(emptyHint(note.isBusy()
                    ? "正在等待第一段转写…" : "没有转写内容，可以重试转写。"));
            return;
        }
        contentBox.addView(sectionTitle("全文 · " + text.length() + " 字", GlyphView.NOTE));
        TextView body = body(text, 11.4f);
        body.setTextIsSelectable(true);
        contentBox.addView(body, blockLp());
    }

    private TextView toggleChip(String text, boolean on) {
        TextView v = Ui.mediumText(this, text, 9.6f, on ? Ui.ACCENT_INK : Ui.MUTED);
        v.setGravity(Gravity.CENTER);
        v.setBackground(on ? Ui.round(Ui.ACCENT, 8, this) : null);
        return v;
    }

    private LinearLayout progressCard(String title, String sub) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11), Ui.dp(this, 11));
        card.setBackground(Ui.roundStroke(0xFF0E1D1B, 13, 0xFF1D3A36, 1, this));
        ProgressBar bar = new ProgressBar(this);
        bar.setIndeterminate(true);
        try {
            bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        } catch (Throwable ignored) {
        }
        card.addView(bar, new LinearLayout.LayoutParams(
                Ui.dp(this, 21), Ui.dp(this, 21)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 9), 0, 0, 0);
        texts.addView(Ui.mediumText(this, title, 11.5f, Ui.ACCENT));
        TextView s = Ui.text(this, sub, 9.8f, Ui.MUTED);
        s.setLineSpacing(Ui.dp(this, 2), 1f);
        s.setPadding(0, Ui.dp(this, 2), 0, 0);
        texts.addView(s);
        card.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }

    private LinearLayout errorCard(String text) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 11), Ui.dp(this, 9), Ui.dp(this, 11), Ui.dp(this, 9));
        card.setBackground(Ui.roundStroke(0xFF201313, 12, 0xFF4A2424, 1, this));
        card.addView(Ui.mediumText(this, "还没整理好", 10.5f, Ui.ERROR));
        TextView s = Ui.text(this, text, 10f, Ui.MUTED);
        s.setLineSpacing(Ui.dp(this, 2), 1f);
        s.setPadding(0, Ui.dp(this, 3), 0, 0);
        card.addView(s);
        return card;
    }

    private LinearLayout sectionTitle(String text, int glyph) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 4));
        GlyphView icon = new GlyphView(this, glyph);
        icon.setColor(Ui.ACCENT);
        icon.setStrokeDp(1.7f);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12)));
        TextView t = Ui.mediumText(this, text, 9.6f, Ui.ACCENT);
        t.setLetterSpacing(0.06f);
        t.setPadding(Ui.dp(this, 5), 0, 0, 0);
        row.addView(t);
        return row;
    }

    private TextView body(String text, float size) {
        return body(text, size, Ui.TEXT);
    }

    private TextView body(String text, float size, int color) {
        TextView v = Ui.text(this, text, size, color);
        v.setLineSpacing(Ui.dp(this, 4), 1f);
        return v;
    }

    private LinearLayout bulletList(String text, int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (String item : text.split("\\n")) {
            String s = item.trim();
            if (s.isEmpty()) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
            TextView dot = Ui.text(this, "•", 13f, color);
            row.addView(dot, new LinearLayout.LayoutParams(
                    Ui.dp(this, 14), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView v = Ui.text(this, s, 11.3f, Ui.TEXT);
            v.setLineSpacing(Ui.dp(this, 3), 1f);
            row.addView(v, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            box.addView(row);
        }
        return box;
    }

    private View tagRow(String tags) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String raw : tags.split(",")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;
            TextView chip = Ui.mediumText(this, "# " + t, 9.3f, Ui.ACCENT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
            chip.setBackground(Ui.round(0xFF102521, 10, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 25));
            lp.rightMargin = Ui.dp(this, 5);
            row.addView(chip, lp);
        }
        hs.addView(row);
        return hs;
    }

    private View emptyHint(String text) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22));
        GlyphView icon = new GlyphView(this, GlyphView.NOTE);
        icon.setColor(Ui.DIM);
        box.addView(icon, new LinearLayout.LayoutParams(
                Ui.dp(this, 28), Ui.dp(this, 28)));
        TextView t = Ui.text(this, text, 10.4f, Ui.DIM);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(Ui.dp(this, 3), 1f);
        t.setPadding(0, Ui.dp(this, 7), 0, 0);
        box.addView(t);
        return box;
    }

    private LinearLayout.LayoutParams blockLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 3);
        return lp;
    }

    private void reprocess() {
        if (note == null) return;
        if (!settings.hasDeepSeekKey()) {
            Ui.confirm(this, "需要 DeepSeek API Key",
                    "纠错、摘要、要点、待办和分类都由 DeepSeek 接口完成。",
                    "去设置", () -> startActivity(new Intent(this, SettingsActivity.class)));
            return;
        }
        if (note.transcript.isEmpty() && !settings.hasAsrKey()) {
            Ui.confirm(this, "需要语音识别服务",
                    "DeepSeek 没有语音转写接口，需要先配置一个 ASR Key。",
                    "去设置", () -> startActivity(new Intent(this, SettingsActivity.class)));
            return;
        }
        aiLabel.setText("准备中…");
        aiButton.setEnabled(false);
        RecorderService.reprocess(this, noteId);
        contentSignature = "";
        App.MAIN.postDelayed(this::reload, 600);
    }

    private void shareNote() {
        if (note == null) return;
        Ui.share(this, markdown(note));
    }

    private String markdown(Note n) {
        StringBuilder b = new StringBuilder();
        b.append("# ").append(n.title).append("\n\n");
        b.append("> ").append(n.kind);
        if ("课堂".equals(n.kind) && n.subject != null && !n.subject.trim().isEmpty()) {
            b.append(" · ").append(n.subject.trim());
        }
        b.append(" · ").append(Ui.dateTime(n.createdAt));
        if (n.durationMs > 0) b.append(" · ").append(Ui.durationShort(n.durationMs));
        b.append("\n\n");
        if (!TextUtils.isEmpty(n.summary)) {
            b.append("## 摘要\n\n").append(n.summary).append("\n\n");
        }
        boolean classNote = "课堂".equals(n.kind);
        if (!TextUtils.isEmpty(n.keyPoints)) {
            b.append(classNote ? "## 知识点\n\n" : "## 关键要点\n\n");
            for (String s : n.keyPoints.split("\\n")) {
                if (!s.trim().isEmpty()) b.append("- ").append(s.trim()).append('\n');
            }
            b.append('\n');
        }
        if (!TextUtils.isEmpty(n.examples)) {
            b.append("## 例题 / 案例\n\n");
            for (String s : n.examples.split("\\n")) {
                if (!s.trim().isEmpty()) b.append("- ").append(s.trim()).append('\n');
            }
            b.append('\n');
        }
        if (!TextUtils.isEmpty(n.actions)) {
            b.append(classNote ? "## 作业与复习\n\n" : "## 待办事项\n\n");
            for (String s : n.actions.split("\\n")) {
                if (!s.trim().isEmpty()) b.append("- [ ] ").append(s.trim()).append('\n');
            }
            b.append('\n');
        }
        if (!TextUtils.isEmpty(n.decisions)) {
            b.append(classNote ? "## 重点 / 难点 / 易错点\n\n" : "## 结论 / 决定\n\n");
            for (String s : n.decisions.split("\\n")) {
                if (!s.trim().isEmpty()) b.append("- ").append(s.trim()).append('\n');
            }
            b.append('\n');
        }
        if (!TextUtils.isEmpty(n.tags)) {
            b.append("## 标签\n\n");
            for (String s : n.tags.split(",")) {
                if (!s.trim().isEmpty()) b.append('#').append(s.trim()).append(' ');
            }
            b.append("\n\n");
        }
        if (!TextUtils.isEmpty(n.markers)) {
            b.append("## 重点标记\n\n").append(n.markers).append("\n\n");
        }
        String text = n.bodyText();
        if (!TextUtils.isEmpty(text)) {
            b.append("## 全文\n\n").append(text).append('\n');
        }
        b.append("\n---\n由「听记」记录 · AI 整理\n");
        return b.toString();
    }

    private void rename() {
        if (note == null) return;
        Ui.input(this, "重命名", note.title, "记录标题", false, false, value -> {
            if (!value.isEmpty()) {
                store.setTitle(noteId, value);
                contentSignature = "";
                reload();
            }
        });
    }

    private void showKindPicker() {
        if (note == null) return;
        final String[] kinds = {"会议", "课堂", "访谈", "其他"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("记录类型")
                .setItems(kinds, (d, which) -> {
                    store.setKind(noteId, kinds[which]);
                    contentSignature = "";
                    reload();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSubjectPicker() {
        if (note == null) return;
        String[] subjects = NoteAI.SUBJECTS;
        new android.app.AlertDialog.Builder(this)
                .setTitle("选择学科")
                .setItems(subjects, (d, which) -> {
                    store.setSubject(noteId, subjects[which]);
                    contentSignature = "";
                    reload();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMore() {
        if (note == null) return;
        List<String> items = new ArrayList<>();
        items.add("重命名");
        items.add("修改类型");
        if ("课堂".equals(note.kind)) items.add("修改学科");
        items.add("复制 Markdown");
        items.add("分享");
        if (note.status == Note.DRAFT) items.add("重试转写 / 整理");
        items.add("删除记录");
        new android.app.AlertDialog.Builder(this)
                .setTitle("记录操作")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String item = items.get(which);
                    if ("重命名".equals(item)) rename();
                    else if ("修改类型".equals(item)) showKindPicker();
                    else if ("修改学科".equals(item)) showSubjectPicker();
                    else if ("复制 Markdown".equals(item)) Ui.copy(this, markdown(note), "听记");
                    else if ("分享".equals(item)) shareNote();
                    else if (item.startsWith("重试")) reprocess();
                    else if ("删除记录".equals(item)) deleteNote();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteNote() {
        if (note == null) return;
        Ui.confirm(this, "删除这条记录？", "转写、摘要和未上传的录音片段都会删除。", "删除", () -> {
            if (RecorderState.active && RecorderState.noteId == noteId) {
                RecorderService.command(this, RecorderService.ACTION_STOP);
            }
            store.deleteNote(noteId);
            finish();
        });
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        long id = intent.getLongExtra("note_id", 0);
        if (id > 0 && id != noteId) {
            noteId = id;
            note = null;
            headerSignature = "";
            contentSignature = "";
            reload();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        contentSignature = "";
        App.MAIN.removeCallbacks(refreshTask);
        App.MAIN.post(refreshTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.MAIN.removeCallbacks(refreshTask);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) fullscreen();
    }
}
