package com.wrist.tingji.core;

import java.util.Locale;

/** Process-wide snapshot of the recording service, polled by the watch UI. */
public class RecorderState {
    public static volatile boolean serviceRunning;
    public static volatile long noteId;
    public static volatile String kind = "会议";
    public static volatile boolean active;
    public static volatile boolean paused;
    public static volatile boolean processing;
    public static volatile long elapsedMs;
    public static volatile float level;
    public static volatile String statusText = "";
    public static volatile String lastText = "";
    public static volatile int segmentsDone;
    public static volatile int segmentsFailed;
    public static volatile int segmentsPending;
    public static volatile String error = "";
    public static volatile long updatedAt;

    private RecorderState() {
    }

    public static void reset() {
        noteId = 0;
        kind = "会议";
        active = false;
        paused = false;
        processing = false;
        elapsedMs = 0;
        level = 0;
        statusText = "";
        lastText = "";
        segmentsDone = 0;
        segmentsFailed = 0;
        segmentsPending = 0;
        error = "";
        updatedAt = System.currentTimeMillis();
    }

    public static String clock() {
        long total = Math.max(0, elapsedMs) / 1000L;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    public static boolean sameNote(long id) {
        return id > 0 && noteId == id;
    }
}
