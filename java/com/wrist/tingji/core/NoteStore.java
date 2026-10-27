package com.wrist.tingji.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteStore extends SQLiteOpenHelper {
    private static final String DB = "tingji.db";
    private static final int VERSION = 3;

    public NoteStore(Context context) {
        super(context.getApplicationContext(), DB, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "kind TEXT NOT NULL DEFAULT '会议'," +
                "subject TEXT NOT NULL DEFAULT ''," +
                "status INTEGER NOT NULL DEFAULT 4," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "duration_ms INTEGER NOT NULL DEFAULT 0," +
                "transcript TEXT NOT NULL DEFAULT ''," +
                "corrected TEXT NOT NULL DEFAULT ''," +
                "summary TEXT NOT NULL DEFAULT ''," +
                "key_points TEXT NOT NULL DEFAULT ''," +
                "examples TEXT NOT NULL DEFAULT ''," +
                "actions TEXT NOT NULL DEFAULT ''," +
                "decisions TEXT NOT NULL DEFAULT ''," +
                "tags TEXT NOT NULL DEFAULT ''," +
                "markers TEXT NOT NULL DEFAULT ''," +
                "ai_error TEXT NOT NULL DEFAULT ''," +
                "segments_total INTEGER NOT NULL DEFAULT 0," +
                "segments_done INTEGER NOT NULL DEFAULT 0," +
                "segments_failed INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE segments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "note_id INTEGER NOT NULL," +
                "idx INTEGER NOT NULL," +
                "path TEXT NOT NULL," +
                "duration_ms INTEGER NOT NULL DEFAULT 0," +
                "offset_ms INTEGER NOT NULL DEFAULT 0," +
                "text TEXT NOT NULL DEFAULT ''," +
                "status INTEGER NOT NULL DEFAULT 0," +
                "error TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX idx_notes_updated ON notes(updated_at)");
        db.execSQL("CREATE INDEX idx_segments_note ON segments(note_id, idx)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE notes ADD COLUMN examples TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE notes ADD COLUMN subject TEXT NOT NULL DEFAULT ''");
        }
    }

    public synchronized long createNote(String kind) {
        long now = System.currentTimeMillis();
        String k = "课堂".equals(kind) ? "课堂" : "会议";
        String title = k + " · " + new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
                .format(new Date(now));
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("kind", k);
        v.put("status", Note.RECORDING);
        v.put("created_at", now);
        v.put("updated_at", now);
        return getWritableDatabase().insert("notes", null, v);
    }

    public synchronized Note getNote(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM notes WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readNote(c) : null;
        } finally {
            c.close();
        }
    }

    public synchronized List<Note> listNotes(String query, String kind) {
        List<Note> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM notes WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (kind != null && !kind.isEmpty() && !"全部".equals(kind)) {
            sql.append(" AND kind=?");
            args.add(kind);
        }
        String q = query == null ? "" : query.trim();
        if (!q.isEmpty()) {
            sql.append(" AND (title LIKE ? OR transcript LIKE ? OR corrected LIKE ?")
                    .append(" OR summary LIKE ? OR tags LIKE ? OR key_points LIKE ?")
                    .append(" OR subject LIKE ?)");
            String like = "%" + q + "%";
            for (int i = 0; i < 7; i++) args.add(like);
        }
        sql.append(" ORDER BY updated_at DESC LIMIT 300");
        Cursor c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
        try {
            while (c.moveToNext()) out.add(readNote(c));
        } finally {
            c.close();
        }
        return out;
    }

    private static Note readNote(Cursor c) {
        Note n = new Note();
        n.id = c.getLong(c.getColumnIndexOrThrow("id"));
        n.title = c.getString(c.getColumnIndexOrThrow("title"));
        n.kind = c.getString(c.getColumnIndexOrThrow("kind"));
        n.subject = c.getString(c.getColumnIndexOrThrow("subject"));
        n.status = c.getInt(c.getColumnIndexOrThrow("status"));
        n.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        n.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        n.durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms"));
        n.transcript = c.getString(c.getColumnIndexOrThrow("transcript"));
        n.corrected = c.getString(c.getColumnIndexOrThrow("corrected"));
        n.summary = c.getString(c.getColumnIndexOrThrow("summary"));
        n.keyPoints = c.getString(c.getColumnIndexOrThrow("key_points"));
        n.examples = c.getString(c.getColumnIndexOrThrow("examples"));
        n.actions = c.getString(c.getColumnIndexOrThrow("actions"));
        n.decisions = c.getString(c.getColumnIndexOrThrow("decisions"));
        n.tags = c.getString(c.getColumnIndexOrThrow("tags"));
        n.markers = c.getString(c.getColumnIndexOrThrow("markers"));
        n.aiError = c.getString(c.getColumnIndexOrThrow("ai_error"));
        n.segmentsTotal = c.getInt(c.getColumnIndexOrThrow("segments_total"));
        n.segmentsDone = c.getInt(c.getColumnIndexOrThrow("segments_done"));
        n.segmentsFailed = c.getInt(c.getColumnIndexOrThrow("segments_failed"));
        return n;
    }

    public synchronized void setStatus(long id, int status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void setTitle(long id, String title) {
        if (title == null || title.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("title", title.trim());
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void setKind(long id, String kind) {
        if (kind == null || kind.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("kind", kind.trim());
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void setSubject(long id, String subject) {
        ContentValues v = new ContentValues();
        v.put("subject", subject == null ? "" : subject.trim());
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void setTranscript(long id, String transcript) {
        ContentValues v = new ContentValues();
        v.put("transcript", transcript == null ? "" : transcript);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void setDuration(long id, long durationMs) {
        ContentValues v = new ContentValues();
        v.put("duration_ms", Math.max(0, durationMs));
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized long addSegment(long noteId, int index, String path,
                                        long durationMs, long offsetMs) {
        ContentValues v = new ContentValues();
        v.put("note_id", noteId);
        v.put("idx", index);
        v.put("path", path);
        v.put("duration_ms", durationMs);
        v.put("offset_ms", offsetMs);
        v.put("status", Segment.PENDING);
        long id = getWritableDatabase().insert("segments", null, v);
        refreshCounts(noteId);
        return id;
    }

    public synchronized Segment getSegment(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM segments WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readSegment(c) : null;
        } finally {
            c.close();
        }
    }

    public synchronized List<Segment> segments(long noteId) {
        List<Segment> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM segments WHERE note_id=? ORDER BY idx ASC",
                new String[]{String.valueOf(noteId)});
        try {
            while (c.moveToNext()) out.add(readSegment(c));
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized List<Segment> pendingSegments(long noteId) {
        List<Segment> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM segments WHERE note_id=? AND status<>1 ORDER BY idx ASC",
                new String[]{String.valueOf(noteId)});
        try {
            while (c.moveToNext()) out.add(readSegment(c));
        } finally {
            c.close();
        }
        return out;
    }

    private static Segment readSegment(Cursor c) {
        Segment s = new Segment();
        s.id = c.getLong(c.getColumnIndexOrThrow("id"));
        s.noteId = c.getLong(c.getColumnIndexOrThrow("note_id"));
        s.index = c.getInt(c.getColumnIndexOrThrow("idx"));
        s.path = c.getString(c.getColumnIndexOrThrow("path"));
        s.durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms"));
        s.offsetMs = c.getLong(c.getColumnIndexOrThrow("offset_ms"));
        s.text = c.getString(c.getColumnIndexOrThrow("text"));
        s.status = c.getInt(c.getColumnIndexOrThrow("status"));
        s.error = c.getString(c.getColumnIndexOrThrow("error"));
        return s;
    }

    /**
     * Marks a segment done and appends its text exactly once. The status guard
     * makes retries idempotent.
     */
    public synchronized void markSegmentDone(long segmentId, String text) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long noteId = 0;
            int oldStatus = -1;
            Cursor c = db.rawQuery("SELECT note_id,status FROM segments WHERE id=?",
                    new String[]{String.valueOf(segmentId)});
            try {
                if (c.moveToFirst()) {
                    noteId = c.getLong(0);
                    oldStatus = c.getInt(1);
                }
            } finally {
                c.close();
            }
            if (noteId <= 0 || oldStatus == Segment.DONE) {
                db.setTransactionSuccessful();
                return;
            }
            ContentValues sv = new ContentValues();
            sv.put("text", text == null ? "" : text);
            sv.put("status", Segment.DONE);
            sv.put("error", "");
            db.update("segments", sv, "id=?", new String[]{String.valueOf(segmentId)});

            if (text != null && !text.trim().isEmpty()) {
                Cursor nc = db.rawQuery("SELECT transcript FROM notes WHERE id=?",
                        new String[]{String.valueOf(noteId)});
                String existing = "";
                try {
                    if (nc.moveToFirst()) existing = nc.getString(0);
                } finally {
                    nc.close();
                }
                String merged = existing == null ? "" : existing.trim();
                String piece = text.trim();
                if (merged.isEmpty()) merged = piece;
                else if (!merged.endsWith(piece)) merged = merged + "\n\n" + piece;
                ContentValues nv = new ContentValues();
                nv.put("transcript", merged);
                nv.put("updated_at", System.currentTimeMillis());
                db.update("notes", nv, "id=?", new String[]{String.valueOf(noteId)});
            }
            refreshCounts(db, noteId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void markSegmentFailed(long segmentId, String error) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long noteId = 0;
            Cursor c = db.rawQuery("SELECT note_id FROM segments WHERE id=?",
                    new String[]{String.valueOf(segmentId)});
            try {
                if (c.moveToFirst()) noteId = c.getLong(0);
            } finally {
                c.close();
            }
            ContentValues v = new ContentValues();
            v.put("status", Segment.FAILED);
            v.put("error", error == null ? "" : error);
            db.update("segments", v, "id=?", new String[]{String.valueOf(segmentId)});
            if (noteId > 0) refreshCounts(db, noteId);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void setSegmentText(long segmentId, String text) {
        ContentValues v = new ContentValues();
        v.put("text", text == null ? "" : text);
        getWritableDatabase().update("segments", v, "id=?", new String[]{String.valueOf(segmentId)});
    }

    public synchronized int nextSegmentIndex(long noteId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(MAX(idx),-1)+1 FROM segments WHERE note_id=?",
                new String[]{String.valueOf(noteId)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static void refreshCounts(SQLiteDatabase db, long noteId) {
        int total = 0, done = 0, failed = 0;
        Cursor c = db.rawQuery("SELECT COUNT(*), " +
                "SUM(CASE WHEN status=1 THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN status=2 THEN 1 ELSE 0 END) " +
                "FROM segments WHERE note_id=?", new String[]{String.valueOf(noteId)});
        try {
            if (c.moveToFirst()) {
                total = c.getInt(0);
                done = c.getInt(1);
                failed = c.getInt(2);
            }
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("segments_total", total);
        v.put("segments_done", done);
        v.put("segments_failed", failed);
        db.update("notes", v, "id=?", new String[]{String.valueOf(noteId)});
    }

    public synchronized void refreshCounts(long noteId) {
        refreshCounts(getWritableDatabase(), noteId);
    }

    public synchronized void setCorrected(long noteId, String corrected) {
        ContentValues v = new ContentValues();
        v.put("corrected", corrected == null ? "" : corrected);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(noteId)});
    }

    public synchronized void setAIError(long noteId, String error) {
        ContentValues v = new ContentValues();
        v.put("ai_error", error == null ? "" : error);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(noteId)});
    }

    public synchronized void applyAI(long noteId, String title, String category,
                                     String subject, String summary, String keyPoints,
                                     String examples, String actions, String decisions,
                                     String tags) {
        ContentValues v = new ContentValues();
        if (title != null && !title.trim().isEmpty()) v.put("title", title.trim());
        if (category != null && !category.trim().isEmpty()) v.put("kind", category.trim());
        v.put("subject", subject == null ? "" : subject.trim());
        v.put("summary", summary == null ? "" : summary);
        v.put("key_points", keyPoints == null ? "" : keyPoints);
        v.put("examples", examples == null ? "" : examples);
        v.put("actions", actions == null ? "" : actions);
        v.put("decisions", decisions == null ? "" : decisions);
        v.put("tags", tags == null ? "" : tags);
        v.put("ai_error", "");
        v.put("status", Note.READY);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(noteId)});
    }

    public synchronized void addMarker(long noteId, long offsetMs, String label) {
        Note n = getNote(noteId);
        if (n == null) return;
        String stamp = formatOffset(offsetMs);
        String line = stamp + "  " + (label == null || label.trim().isEmpty() ? "重点" : label.trim());
        String merged = n.markers == null || n.markers.trim().isEmpty()
                ? line : n.markers + "\n" + line;
        ContentValues v = new ContentValues();
        v.put("markers", merged);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(noteId)});
    }

    public synchronized void deleteNote(long noteId) {
        List<String> paths = segmentPaths(noteId);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("segments", "note_id=?", new String[]{String.valueOf(noteId)});
            db.delete("notes", "id=?", new String[]{String.valueOf(noteId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        for (String path : paths) deleteQuietly(path);
    }

    public synchronized void clearAll() {
        List<String> paths = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT path FROM segments", null);
        try {
            while (c.moveToNext()) paths.add(c.getString(0));
        } finally {
            c.close();
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("segments", null, null);
            db.delete("notes", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        for (String path : paths) deleteQuietly(path);
    }

    private List<String> segmentPaths(long noteId) {
        List<String> paths = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT path FROM segments WHERE note_id=?", new String[]{String.valueOf(noteId)});
        try {
            while (c.moveToNext()) paths.add(c.getString(0));
        } finally {
            c.close();
        }
        return paths;
    }

    private static void deleteQuietly(String path) {
        try {
            if (path != null && !path.isEmpty()) new File(path).delete();
        } catch (Throwable ignored) {
        }
    }

    /** A process death while recording should never leave a spinning "recording" row. */
    public synchronized void recoverInterrupted() {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("status", Note.DRAFT);
        v.put("ai_error", "上次的记录被中断，录音片段还在，可点“重试转写”继续");
        v.put("updated_at", System.currentTimeMillis());
        db.update("notes", v, "status IN (0,1,2)", null);
    }

    /** Fill subject for existing class notes without calling the API. */
    public synchronized void backfillSubjects() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,title,tags,transcript FROM notes " +
                        "WHERE kind='课堂' AND (subject IS NULL OR subject='')", null);
        List<Long> ids = new ArrayList<>();
        List<String> subjects = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                String text = safe(c.getString(1)) + " " + safe(c.getString(2))
                        + " " + safe(c.getString(3));
                String subject = NoteAI.guessSubject(text);
                if (!subject.isEmpty()) {
                    ids.add(c.getLong(0));
                    subjects.add(subject);
                }
            }
        } finally {
            c.close();
        }
        if (ids.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < ids.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("subject", subjects.get(i));
                db.update("notes", v, "id=?", new String[]{String.valueOf(ids.get(i))});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public synchronized int[] stats() {
        int total = 0, meetings = 0, classes = 0, subjects = 0;
        long duration = 0;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*), " +
                        "SUM(CASE WHEN kind='会议' THEN 1 ELSE 0 END), " +
                        "SUM(CASE WHEN kind='课堂' THEN 1 ELSE 0 END), " +
                        "SUM(duration_ms), " +
                        "COUNT(DISTINCT CASE WHEN kind='课堂' AND subject<>'' " +
                        "THEN subject END) FROM notes", null);
        try {
            if (c.moveToFirst()) {
                total = c.getInt(0);
                meetings = c.getInt(1);
                classes = c.getInt(2);
                duration = c.getLong(3);
                subjects = c.getInt(4);
            }
        } finally {
            c.close();
        }
        return new int[]{total, meetings, classes, (int) (duration / 60000L), subjects};
    }

    public synchronized long latestNoteId() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM notes ORDER BY updated_at DESC LIMIT 1", null);
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    public synchronized long oldestNoteId() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM notes ORDER BY created_at ASC LIMIT 1", null);
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    public static String formatOffset(long ms) {
        long total = Math.max(0, ms) / 1000L;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }
}
