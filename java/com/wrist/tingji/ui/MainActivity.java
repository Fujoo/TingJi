package com.wrist.tingji.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wrist.tingji.App;
import com.wrist.tingji.core.Note;
import com.wrist.tingji.core.NoteStore;
import com.wrist.tingji.core.RecorderState;
import com.wrist.tingji.core.Settings;
import com.wrist.tingji.util.GlyphView;
import com.wrist.tingji.util.Ui;

import java.util.List;

public class MainActivity extends ScaledActivity {
    private Settings settings;
    private NoteStore store;
    private LinearLayout listContainer;
    private TextView statsView;
    private TextView recordLabel;
    private GlyphView recordIcon;
    private FrameLayout recordButton;
    private View emptyView;
    private EditText search;
    private LinearLayout filterRow;
    private String filter = "全部";
    private String lastSignature = "";
    private boolean resumed;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            refresh(false);
            App.MAIN.postDelayed(this, 900);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = App.get().settings;
        store = App.get().store;
        fullscreen();
        buildUi();
        refresh(true);
        App.MAIN.postDelayed(refreshTask, 900);
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

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.dp(this, 13), Ui.dp(this, 6), Ui.dp(this, 13), 0);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50)));

        column.addView(buildSearch(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 34)));
        Ui.setMargins(column.getChildAt(column.getChildCount() - 1), 0, Ui.dp(this, 6), 0, 0);

        column.addView(buildFilters(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 31)));
        Ui.setMargins(column.getChildAt(column.getChildCount() - 1), 0, Ui.dp(this, 4), 0, 2);

        FrameLayout listArea = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, Ui.dp(this, 6));
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        listArea.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = buildEmpty();
        listArea.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.addView(listArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        column.addView(buildRecordBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 60)));

        setContentView(root);
        updateFilterChips();
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout logo = new FrameLayout(this);
        logo.setBackground(Ui.gradient(0xFF5EEAD4, 0xFF0F766E, 11, this));
        GlyphView lg = new GlyphView(this, GlyphView.NOTE);
        lg.setColor(Ui.ACCENT_INK);
        lg.setStrokeDp(2.0f);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                Ui.dp(this, 17), Ui.dp(this, 17));
        llp.gravity = Gravity.CENTER;
        logo.addView(lg, llp);
        bar.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 31), Ui.dp(this, 31)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 9), 0, 0, 0);
        TextView title = Ui.mediumText(this, "听记", 19.5f, Ui.TEXT);
        title.setLetterSpacing(0.02f);
        titles.addView(title);
        statsView = Ui.text(this, "会议 · 课堂 · AI 整理", 9.5f, Ui.MUTED);
        statsView.setPadding(0, Ui.dp(this, 1), 0, 0);
        titles.addView(statsView);
        bar.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout settingsButton = new FrameLayout(this);
        settingsButton.setBackground(Ui.ripple(0x22FFFFFF, 18, this));
        settingsButton.setContentDescription("设置");
        GlyphView gear = new GlyphView(this, GlyphView.SETTINGS);
        gear.setColor(Ui.MUTED);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18));
        glp.gravity = Gravity.CENTER;
        settingsButton.addView(gear, glp);
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bar.addView(settingsButton, new LinearLayout.LayoutParams(
                Ui.dp(this, 36), Ui.dp(this, 36)));
        return bar;
    }

    private View buildSearch() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
        box.setBackground(Ui.roundStroke(Ui.SURFACE, 11, Ui.BORDER_SOFT, 1, this));
        GlyphView icon = new GlyphView(this, GlyphView.SEARCH);
        icon.setColor(Ui.DIM);
        box.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 14)));
        search = new EditText(this);
        search.setBackground(null);
        search.setHint("搜索标题、内容、标签");
        search.setHintTextColor(Ui.DIM);
        search.setTextColor(Ui.TEXT);
        search.setTextSize(11.5f);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(Ui.dp(this, 7), 0, 0, 0);
        search.setIncludeFontPadding(false);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh(true);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search.clearFocus();
                return true;
            }
            return false;
        });
        box.addView(search, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return box;
    }

    private View buildFilters() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String[] items = {"全部", "会议", "课堂"};
        for (String item : items) {
            TextView chip = Ui.mediumText(this, item, 10.5f, Ui.MUTED);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
            chip.setTag(item);
            chip.setBackground(Ui.rippleBg(Ui.round(Ui.SURFACE2, 15, this),
                    0x22FFFFFF, 15, this));
            chip.setOnClickListener(v -> {
                filter = item;
                updateFilterChips();
                refresh(true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 27));
            lp.rightMargin = Ui.dp(this, 6);
            row.addView(chip, lp);
        }
        filterRow = row;
        return row;
    }

    private void updateFilterChips() {
        if (filterRow == null) return;
        for (int i = 0; i < filterRow.getChildCount(); i++) {
            TextView chip = (TextView) filterRow.getChildAt(i);
            boolean on = chip.getTag().equals(filter);
            chip.setTextColor(on ? Ui.ACCENT_INK : Ui.MUTED);
            chip.setBackground(on
                    ? Ui.rippleBg(Ui.round(Ui.ACCENT, 15, this), 0x44FFFFFF, 15, this)
                    : Ui.rippleBg(Ui.round(Ui.SURFACE2, 15, this), 0x22FFFFFF, 15, this));
        }
    }

    private View buildRecordBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));

        recordButton = new FrameLayout(this);
        recordButton.setBackground(Ui.rippleBg(
                Ui.gradient(0xFF5EEAD4, 0xFF14B8A6, 17, this), 0x44FFFFFF, 17, this));
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER);
        recordIcon = new GlyphView(this, GlyphView.MIC);
        recordIcon.setColor(Ui.ACCENT_INK);
        recordIcon.setStrokeDp(2.0f);
        inner.addView(recordIcon, new LinearLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18)));
        recordLabel = Ui.mediumText(this, "开始记录", 14.5f, Ui.ACCENT_INK);
        recordLabel.setPadding(Ui.dp(this, 7), 0, 0, 0);
        inner.addView(recordLabel);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.gravity = Gravity.CENTER;
        recordButton.addView(inner, ilp);
        recordButton.setOnClickListener(v -> openRecord());
        bar.addView(recordButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46)));
        return bar;
    }

    private View buildEmpty() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 20));

        FrameLayout circle = new FrameLayout(this);
        circle.setBackground(Ui.roundStroke(0xFF0F1B1B, 30, 0xFF1D3A36, 1, this));
        GlyphView mic = new GlyphView(this, GlyphView.MIC);
        mic.setColor(Ui.ACCENT);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                Ui.dp(this, 26), Ui.dp(this, 26));
        mlp.gravity = Gravity.CENTER;
        circle.addView(mic, mlp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                Ui.dp(this, 60), Ui.dp(this, 60));
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(circle, clp);

        TextView title = Ui.mediumText(this, "还没有记录", 15f, Ui.TEXT);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 12), 0, 0);
        box.addView(title);
        TextView sub = Ui.text(this, "点下面开始，录音会自动分段转写，\n再用 AI 纠错、总结和分类", 10.5f, Ui.DIM);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(Ui.dp(this, 4), 1f);
        sub.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), 0);
        box.addView(sub);
        return box;
    }

    private void refresh(boolean force) {
        if (settings == null) return;
        int[] stats = store.stats();
        String time = Ui.timeOnly(System.currentTimeMillis());
        if (stats[0] == 0) {
            statsView.setText(time + " · 会议 · 课堂 · AI 整理");
        } else {
            statsView.setText(time + " · " + stats[0] + " 条记录 · " + stats[3] + " 分钟");
        }

        String q = search == null ? "" : search.getText().toString();
        List<Note> notes = store.listNotes(q, filter);
        StringBuilder sig = new StringBuilder();
        sig.append(filter).append('|').append(q).append('|');
        for (Note n : notes) {
            sig.append(n.id).append(':').append(n.updatedAt).append(':')
                    .append(n.status).append(';');
        }
        sig.append("api=").append(settings.hasDeepSeekKey());
        if (!force && sig.toString().equals(lastSignature)) {
            updateRecordButton();
            return;
        }
        lastSignature = sig.toString();
        rebuildList(notes);
        updateRecordButton();
    }

    private void rebuildList(List<Note> notes) {
        listContainer.removeAllViews();
        boolean hasAny = store.stats()[0] > 0;
        if (!settings.hasDeepSeekKey() || !settings.hasAsrKey()) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.bottomMargin = Ui.dp(this, 7);
            listContainer.addView(buildSetupBanner(), blp);
        }
        if (notes.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            TextView t = (TextView) ((LinearLayout) emptyView).getChildAt(1);
            TextView s = (TextView) ((LinearLayout) emptyView).getChildAt(2);
            if (hasAny) {
                t.setText("没有找到");
                s.setText("换个关键词，或切换筛选条件");
            } else {
                t.setText("还没有记录");
                s.setText("点下面开始，录音会自动分段转写，\n再用 AI 纠错、总结和分类");
            }
            return;
        }
        emptyView.setVisibility(View.GONE);
        for (Note n : notes) {
            View card = buildNoteCard(n);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(this, 7);
            listContainer.addView(card, lp);
        }
    }

    private View buildSetupBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        banner.setBackground(Ui.rippleBg(
                Ui.roundStroke(0xFF10211E, 12, 0xFF1F4A42, 1, this),
                0x22FFFFFF, 12, this));
        TextView t = Ui.text(this, "先配置 API，才能自动纠错、总结和分类", 10.2f, Ui.ACCENT);
        banner.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        GlyphView arrow = new GlyphView(this, GlyphView.CHEVRON);
        arrow.setColor(Ui.ACCENT);
        banner.addView(arrow, new LinearLayout.LayoutParams(
                Ui.dp(this, 13), Ui.dp(this, 13)));
        banner.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        return banner;
    }

    private View buildNoteCard(Note n) {
        int color = Ui.kindColor(n.kind);
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.rippleBg(
                Ui.roundStroke(Ui.SURFACE, 16, Ui.BORDER_SOFT, 1, this),
                0x18FFFFFF, 16, this));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.round((color & 0x00FFFFFF) | 0x22000000, 9, this));
        TextView glyph = Ui.mediumText(this, Ui.kindGlyph(n.kind), 11f, color);
        glyph.setGravity(Gravity.CENTER);
        badge.addView(glyph, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(badge, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 9), 0, Ui.dp(this, 4), 0);
        TextView title = Ui.mediumText(this, n.title, 13.2f, Ui.TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);

        String meta = metaText(n);
        TextView metaView = Ui.text(this, meta, 9.7f, n.isBusy() ? Ui.ACCENT : Ui.MUTED);
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        metaView.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 1));
        texts.addView(metaView);

        String snippet = n.summary != null && !n.summary.trim().isEmpty()
                ? n.summary : n.bodyText();
        if (snippet == null || snippet.trim().isEmpty()) snippet = "录音片段已保存，等待转写";
        TextView snip = Ui.text(this, Ui.oneLine(snippet, 60), 10.2f, Ui.DIM);
        snip.setSingleLine(true);
        snip.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(snip);

        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        GlyphView chevron = new GlyphView(this, GlyphView.CHEVRON);
        chevron.setColor(0xFF435452);
        row.addView(chevron, new LinearLayout.LayoutParams(
                Ui.dp(this, 13), Ui.dp(this, 13)));

        card.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOnClickListener(v -> {
            Intent i = new Intent(this, NoteActivity.class);
            i.putExtra("note_id", n.id);
            startActivity(i);
        });
        card.setOnLongClickListener(v -> {
            Ui.confirm(this, "删除这条记录？", n.title, "删除", () -> {
                if (RecorderState.active && RecorderState.noteId == n.id) {
                    com.wrist.tingji.core.RecorderService.command(this,
                            com.wrist.tingji.core.RecorderService.ACTION_STOP);
                }
                store.deleteNote(n.id);
                lastSignature = "";
                refresh(true);
            });
            return true;
        });
        return card;
    }

    private String metaText(Note n) {
        StringBuilder b = new StringBuilder();
        b.append(n.kind).append(" · ");
        if (n.isBusy()) {
            b.append(n.statusLabel()).append(" · ");
        } else if (n.status == Note.READY) {
            b.append(Ui.durationShort(n.durationMs)).append(" · ");
        } else if (n.segmentsFailed > 0) {
            b.append("待重试 · ");
        } else if (n.status == Note.DRAFT) {
            b.append(n.transcript != null && !n.transcript.trim().isEmpty()
                    ? "待整理 · " : "草稿 · ");
        } else {
            b.append(Ui.durationShort(n.durationMs)).append(" · ");
        }
        b.append(Ui.relative(n.updatedAt));
        return b.toString();
    }

    private void updateRecordButton() {
        if (RecorderState.active) {
            recordLabel.setText("回到录音  " + RecorderState.clock());
            recordLabel.setTextColor(Color.WHITE);
            recordIcon.setType(GlyphView.MIC);
            recordIcon.setColor(Color.WHITE);
            recordButton.setBackground(Ui.rippleBg(
                    Ui.gradient(0xFFFF5C63, 0xFFB91C1C, 17, this), 0x44FFFFFF, 17, this));
        } else if (RecorderState.processing) {
            recordLabel.setText("正在整理 · 查看进度");
            recordLabel.setTextColor(Ui.ACCENT_INK);
            recordIcon.setType(GlyphView.SPARK);
            recordIcon.setColor(Ui.ACCENT_INK);
            recordButton.setBackground(Ui.rippleBg(
                    Ui.gradient(0xFF5EEAD4, 0xFF14B8A6, 17, this), 0x44FFFFFF, 17, this));
        } else {
            recordLabel.setText("开始记录");
            recordLabel.setTextColor(Ui.ACCENT_INK);
            recordIcon.setType(GlyphView.MIC);
            recordIcon.setColor(Ui.ACCENT_INK);
            recordButton.setBackground(Ui.rippleBg(
                    Ui.gradient(0xFF5EEAD4, 0xFF14B8A6, 17, this), 0x44FFFFFF, 17, this));
        }
    }

    private void openRecord() {
        if (RecorderState.processing && RecorderState.noteId > 0) {
            Intent i = new Intent(this, NoteActivity.class);
            i.putExtra("note_id", RecorderState.noteId);
            startActivity(i);
        } else {
            startActivity(new Intent(this, RecordActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        lastSignature = "";
        App.MAIN.removeCallbacks(refreshTask);
        refresh(true);
        App.MAIN.postDelayed(refreshTask, 900);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        App.MAIN.removeCallbacks(refreshTask);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) fullscreen();
    }
}
