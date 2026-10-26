package com.wrist.tingji.core;

public class Segment {
    public static final int PENDING = 0;
    public static final int DONE = 1;
    public static final int FAILED = 2;

    public long id;
    public long noteId;
    public int index;
    public String path = "";
    public long durationMs;
    public long offsetMs;
    public String text = "";
    public int status = PENDING;
    public String error = "";
}
