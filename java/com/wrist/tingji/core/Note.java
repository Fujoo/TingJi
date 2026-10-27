package com.wrist.tingji.core;

/** A single meeting / class note. Plain data object, filled by {@link NoteStore}. */
public class Note {
    public static final int RECORDING = 0;
    public static final int TRANSCRIBING = 1;
    public static final int ANALYZING = 2;
    public static final int READY = 3;
    public static final int DRAFT = 4;
    public static final int FAILED = 5;

    public long id;
    public String title = "";
    public String kind = "会议";
    public String subject = "";
    public int status = DRAFT;
    public long createdAt;
    public long updatedAt;
    public long durationMs;
    public String transcript = "";
    public String corrected = "";
    public String summary = "";
    public String keyPoints = "";
    public String examples = "";
    public String actions = "";
    public String decisions = "";
    public String tags = "";
    public String markers = "";
    public String aiError = "";
    public int segmentsTotal;
    public int segmentsDone;
    public int segmentsFailed;

    public boolean isBusy() {
        return status == RECORDING || status == TRANSCRIBING || status == ANALYZING;
    }

    public boolean hasAI() {
        return status == READY && (summary.length() > 0 || keyPoints.length() > 0);
    }

    public String statusLabel() {
        switch (status) {
            case RECORDING: return "录音中";
            case TRANSCRIBING: return "转写中";
            case ANALYZING: return "AI 整理中";
            case READY: return "已整理";
            case FAILED: return "未完成";
            default: return segmentsFailed > 0 ? "待重试" : "草稿";
        }
    }

    public String bodyText() {
        if (corrected != null && corrected.trim().length() > 0) return corrected;
        return transcript == null ? "" : transcript;
    }
}
