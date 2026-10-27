package com.wrist.tingji.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.wrist.tingji.R;
import com.wrist.tingji.ui.NoteActivity;
import com.wrist.tingji.ui.RecordActivity;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground recorder. Audio is captured continuously and cut into 30-60 s WAV
 * segments at natural pauses. Segments are transcribed in the background while
 * recording continues; after stop the transcript is corrected, summarised and
 * classified by the configured chat API.
 */
public class RecorderService extends Service {
    private static final String TAG = "TingJiRecorder";
    private static final int NOTIF_ID = 41;
    private static final String CHANNEL = "tingji_recording";
    private static final long MIN_SEGMENT_MS = 30_000;
    private static final long MAX_SEGMENT_MS = 60_000;
    private static final long SILENCE_CUT_MS = 550;
    private static final long POISON = -1L;

    public static final String ACTION_START = "com.wrist.tingji.START";
    public static final String ACTION_PAUSE = "com.wrist.tingji.PAUSE";
    public static final String ACTION_RESUME = "com.wrist.tingji.RESUME";
    public static final String ACTION_STOP = "com.wrist.tingji.STOP";
    public static final String ACTION_REPROCESS = "com.wrist.tingji.REPROCESS";
    public static final String EXTRA_NOTE_ID = "note_id";
    public static final String EXTRA_KIND = "kind";

    private NoteStore store;
    private Settings settings;
    private PowerManager.WakeLock wakeLock;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BlockingQueue<Long> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean finishOnce = new AtomicBoolean(false);
    private final AsrClient asr = new AsrClient();
    private final NoteAI noteAI = new NoteAI();

    private volatile long noteId;
    private volatile String kind = "会议";
    private volatile boolean sessionActive;
    private volatile boolean paused;
    private volatile boolean stopping;
    private volatile boolean engineRunning;
    private volatile boolean pauseRequest;
    private volatile boolean stopRequest;
    private volatile boolean workerRunning;
    private volatile long runBaseElapsed;
    private volatile long runStartedAt;
    private volatile long lastNotificationAt;
    private volatile Thread recordThread;
    private volatile Thread workerThread;

    private int nextIndex;
    private File segmentDir;
    private File tempDir;

    public static void startSession(Context c, long noteId, String kind) {
        Intent i = new Intent(c, RecorderService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_NOTE_ID, noteId)
                .putExtra(EXTRA_KIND, kind);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void command(Context c, String action) {
        try {
            c.startService(new Intent(c, RecorderService.class).setAction(action));
        } catch (Throwable t) {
            Log.w(TAG, "command failed", t);
        }
    }

    public static void reprocess(Context c, long noteId) {
        Intent i = new Intent(c, RecorderService.class)
                .setAction(ACTION_REPROCESS)
                .putExtra(EXTRA_NOTE_ID, noteId);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        store = new NoteStore(this);
        settings = new Settings(this);
        segmentDir = new File(getFilesDir(), "segments");
        if (!segmentDir.exists()) segmentDir.mkdirs();
        tempDir = new File(getCacheDir(), "asr_tmp");
        if (!tempDir.exists()) tempDir.mkdirs();
        createChannel();
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TingJi:recording");
                wakeLock.setReferenceCounted(false);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            long id = intent.getLongExtra(EXTRA_NOTE_ID, 0);
            String k = intent.getStringExtra(EXTRA_KIND);
            startSession(id, k == null ? "会议" : k);
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            pauseSession();
            return START_NOT_STICKY;
        }
        if (ACTION_RESUME.equals(action)) {
            resumeSession();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            stopSession();
            return START_NOT_STICKY;
        }
        if (ACTION_REPROCESS.equals(action)) {
            long id = intent.getLongExtra(EXTRA_NOTE_ID, 0);
            startReprocess(id);
            return START_NOT_STICKY;
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------- session

    private synchronized void startSession(long id, String k) {
        if (sessionActive || RecorderState.serviceRunning) {
            Log.i(TAG, "session already active");
            return;
        }
        Note note = store.getNote(id);
        if (note == null) {
            stopSelf();
            return;
        }
        noteId = id;
        kind = "课堂".equals(k) ? "课堂" : note.kind;
        sessionActive = true;
        paused = false;
        stopping = false;
        finishOnce.set(false);
        runBaseElapsed = 0;
        runStartedAt = System.currentTimeMillis();
        nextIndex = store.nextSegmentIndex(noteId);
        RecorderState.reset();
        RecorderState.serviceRunning = true;
        RecorderState.noteId = noteId;
        RecorderState.kind = kind;
        RecorderState.active = true;
        RecorderState.statusText = "正在录音";
        RecorderState.updatedAt = System.currentTimeMillis();
        store.setStatus(noteId, Note.RECORDING);

        startForeground(NOTIF_ID, buildNotification());
        if (settings.isAutoTranscribe() && settings.hasAsrKey()) startWorker();
        startEngine();
        acquireWakeLock();
        main.removeCallbacks(ticker);
        main.post(ticker);
    }

    private synchronized void startReprocess(long id) {
        if (sessionActive || RecorderState.processing || workerRunning) return;
        Note note = store.getNote(id);
        if (note == null) {
            stopSelf();
            return;
        }
        noteId = id;
        kind = note.kind;
        sessionActive = false;
        paused = false;
        stopping = false;
        processingStart();
        RecorderState.reset();
        RecorderState.serviceRunning = true;
        RecorderState.noteId = noteId;
        RecorderState.kind = kind;
        RecorderState.processing = true;
        RecorderState.elapsedMs = note.durationMs;
        RecorderState.statusText = "正在重新整理";
        RecorderState.segmentsDone = note.segmentsDone;
        RecorderState.segmentsFailed = note.segmentsFailed;
        RecorderState.segmentsPending = Math.max(0, note.segmentsTotal - note.segmentsDone);
        RecorderState.updatedAt = System.currentTimeMillis();
        store.setStatus(noteId, Note.TRANSCRIBING);
        startForeground(NOTIF_ID, buildNotification());
        startWorker();
        List<Segment> pending = store.pendingSegments(noteId);
        for (Segment s : pending) queue.offer(s.id);
        queue.offer(POISON);
        updateNotification(true);
    }

    private void processingStart() {
        try {
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(4 * 60 * 60 * 1000L);
            }
        } catch (Throwable ignored) {
        }
    }

    private synchronized void pauseSession() {
        if (!sessionActive || paused || !RecorderState.active) return;
        paused = true;
        pauseRequest = true;
        RecorderState.paused = true;
        RecorderState.statusText = "已暂停";
        RecorderState.elapsedMs = currentElapsed();
        runBaseElapsed = RecorderState.elapsedMs;
        RecorderState.level = 0;
        store.setDuration(noteId, RecorderState.elapsedMs);
        updateNotification(true);
    }

    private synchronized void resumeSession() {
        if (!sessionActive || !paused) return;
        paused = false;
        pauseRequest = false;
        RecorderState.paused = false;
        RecorderState.statusText = "正在录音";
        runBaseElapsed = RecorderState.elapsedMs;
        runStartedAt = System.currentTimeMillis();
        startEngine();
        updateNotification(true);
    }

    private synchronized void stopSession() {
        if (!sessionActive && !RecorderState.processing) {
            stopSelf();
            return;
        }
        if (!sessionActive) return;
        if (stopping) return;
        long finalElapsed = currentElapsed();
        stopping = true;
        paused = false;
        stopRequest = true;
        RecorderState.paused = false;
        RecorderState.statusText = "正在结束录音";
        RecorderState.elapsedMs = finalElapsed;
        runBaseElapsed = finalElapsed;
        store.setDuration(noteId, RecorderState.elapsedMs);
        updateNotification(true);
        if (!engineRunning) {
            beginFinish();
        }
    }

    private long currentElapsed() {
        if (!sessionActive) return RecorderState.elapsedMs;
        if (paused) return runBaseElapsed;
        if (!engineRunning && stopping) return runBaseElapsed;
        return runBaseElapsed + Math.max(0, System.currentTimeMillis() - runStartedAt);
    }

    // -------------------------------------------------------------- engine

    private synchronized void startEngine() {
        if (engineRunning) return;
        pauseRequest = false;
        stopRequest = false;
        engineRunning = true;
        recordThread = new Thread(this::recordLoop, "tingji-recorder");
        recordThread.start();
    }

    private void recordLoop() {
        AudioRecord rec = null;
        int index = nextIndex;
        try {
            int min = AudioRecord.getMinBufferSize(WavUtil.RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) min = 4096;
            int bufSize = Math.max(min * 2, 12800);
            try {
                rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, WavUtil.RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
                if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                    release(rec);
                    rec = new AudioRecord(MediaRecorder.AudioSource.MIC, WavUtil.RATE,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
                }
            } catch (Throwable t) {
                release(rec);
                rec = null;
            }
            if (rec == null || rec.getState() != AudioRecord.STATE_INITIALIZED) {
                failSession("麦克风初始化失败，请检查录音权限");
                return;
            }
            rec.startRecording();
            if (rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                failSession("麦克风被占用，请稍后再试");
                return;
            }

            short[] frame = new short[320];
            BufferedOutputStream out = null;
            File file = null;
            long segmentStartWall = 0;
            long segmentOffset = 0;
            long segmentBytes = 0;
            int silenceFrames = 0;
            int speechFrames = 0;
            final int speechThreshold = 100;

            while (!stopRequest && !pauseRequest) {
                int read = rec.read(frame, 0, frame.length);
                if (read <= 0) {
                    if (!stopRequest && !pauseRequest) failSession("录音中断，请重试");
                    break;
                }
                long now = System.currentTimeMillis();
                double rms = rms(frame, read);
                boolean voiced = rms > speechThreshold;
                if (voiced) {
                    speechFrames++;
                    silenceFrames = 0;
                } else {
                    silenceFrames++;
                }
                float level = (float) Math.min(1.0, Math.pow(rms / 2200.0, 0.55));
                if (level > RecorderState.level) RecorderState.level = level;

                if (out == null) {
                    segmentStartWall = now;
                    segmentOffset = runBaseElapsed + Math.max(0, now - runStartedAt);
                    file = new File(segmentDir, "n" + noteId + "_" + index + "_" + now + ".wav");
                    out = new BufferedOutputStream(new FileOutputStream(file), 65536);
                    out.write(new byte[WavUtil.HEADER]);
                    segmentBytes = 0;
                    speechFrames = 0;
                    silenceFrames = 0;
                }
                byte[] bytes = new byte[read * 2];
                for (int i = 0; i < read; i++) {
                    short v = frame[i];
                    bytes[i * 2] = (byte) (v & 0xff);
                    bytes[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
                }
                out.write(bytes);
                segmentBytes += bytes.length;
                long segMs = segmentBytes * 1000L / (WavUtil.RATE * 2L);
                boolean enoughSpeech = speechFrames >= 4;
                boolean cut = segMs >= MAX_SEGMENT_MS
                        || (segMs >= MIN_SEGMENT_MS && silenceFrames * 20L >= SILENCE_CUT_MS
                        && enoughSpeech);
                if (cut) {
                    finalizeSegment(file, out, segmentBytes, segmentOffset, segMs, enoughSpeech);
                    out = null;
                    file = null;
                    index++;
                }
                if (stopRequest || pauseRequest) break;
            }
            if (out != null && file != null) {
                long segMs = segmentBytes * 1000L / (WavUtil.RATE * 2L);
                finalizeSegment(file, out, segmentBytes, segmentOffset, segMs, speechFrames >= 4);
                out = null;
                file = null;
            }
        } catch (Throwable t) {
            Log.w(TAG, "record loop failed", t);
            if (!stopRequest && !pauseRequest) failSession("录音失败：" + t.getMessage());
        } finally {
            try {
                if (rec != null && rec.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop();
                }
            } catch (Throwable ignored) {
            }
            release(rec);
            engineRunning = false;
            recordThread = null;
            if (stopRequest) {
                main.post(this::onEngineStopped);
            } else if (pauseRequest) {
                main.post(this::onEnginePaused);
            }
        }
    }

    private void finalizeSegment(File file, BufferedOutputStream out, long bytes,
                                 long offsetMs, long durationMs, boolean hadSpeech) {
        try {
            out.flush();
            out.close();
            if (bytes < WavUtil.RATE * 2L / 4) {
                file.delete();
                return;
            }
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                WavUtil.writeHeader(raf, bytes);
            }
            if (!hadSpeech && durationMs < 3000) {
                // Too short and no voiced frame: keep storage tidy.
                file.delete();
                return;
            }
            Segment s = new Segment();
            s.noteId = noteId;
            s.index = nextIndex++;
            s.path = file.getAbsolutePath();
            s.durationMs = durationMs;
            s.offsetMs = offsetMs;
            s.id = store.addSegment(noteId, s.index, s.path, durationMs, offsetMs);
            RecorderState.segmentsPending++;
            if (settings.isAutoTranscribe() && settings.hasAsrKey()) {
                startWorker();
                queue.offer(s.id);
            }
            updateStateCounts();
        } catch (Throwable t) {
            Log.w(TAG, "finalize segment failed", t);
            try {
                if (file != null) file.delete();
            } catch (Throwable ignored) {
            }
        }
    }

    private static double rms(short[] b, int n) {
        if (n <= 0) return 0;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double v = b[i];
            sum += v * v;
        }
        return Math.sqrt(sum / n);
    }

    private static void release(AudioRecord r) {
        if (r == null) return;
        try {
            r.release();
        } catch (Throwable ignored) {
        }
    }

    private void onEnginePaused() {
        RecorderState.paused = true;
        RecorderState.statusText = "已暂停";
        RecorderState.level = 0;
    }

    private void onEngineStopped() {
        if (stopping) beginFinish();
    }

    // -------------------------------------------------------------- worker

    private synchronized void startWorker() {
        if (workerRunning) return;
        workerRunning = true;
        workerThread = new Thread(this::workerLoop, "tingji-asr");
        workerThread.start();
    }

    private void workerLoop() {
        while (true) {
            Long id;
            try {
                id = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            if (id == null) continue;
            if (id == POISON) {
                try {
                    finishSession();
                } catch (Throwable t) {
                    Log.w(TAG, "finish failed", t);
                }
                return;
            }
            processSegment(id);
        }
    }

    private void processSegment(long segmentId) {
        Segment s = store.getSegment(segmentId);
        if (s == null) return;
        File original = new File(s.path);
        File normalized = new File(tempDir, "asr_" + segmentId + ".wav");
        try {
            if (!original.isFile() || original.length() <= WavUtil.HEADER) {
                throw new Exception("录音片段已丢失");
            }
            RecorderState.statusText = "正在转写第 " + (s.index + 1) + " 段";
            updateNotification(false);
            WavUtil.normalizedCopy(original, normalized);
            String text = asr.transcribe(settings, normalized);
            text = AsrClient.clean(text);
            if (text.isEmpty()) throw new Exception("这段没有听清");
            store.markSegmentDone(segmentId, text);
            RecorderState.lastText = text;
            RecorderState.error = "";
            try {
                original.delete();
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            String message = t.getMessage() == null ? "转写失败" : t.getMessage();
            store.markSegmentFailed(segmentId, message);
            RecorderState.error = "第 " + (s.index + 1) + " 段：" + message;
            Log.w(TAG, "asr failed: " + message);
        } finally {
            try {
                normalized.delete();
            } catch (Throwable ignored) {
            }
            updateStateCounts();
            updateNotification(false);
        }
    }

    private void updateStateCounts() {
        Note n = store.getNote(noteId);
        if (n == null) return;
        RecorderState.segmentsDone = n.segmentsDone;
        RecorderState.segmentsFailed = n.segmentsFailed;
        RecorderState.segmentsPending = Math.max(0, n.segmentsTotal - n.segmentsDone);
        RecorderState.updatedAt = System.currentTimeMillis();
    }

    // -------------------------------------------------------------- finish

    private synchronized void beginFinish() {
        if (finishOnce.get()) return;
        RecorderState.active = false;
        RecorderState.paused = false;
        RecorderState.processing = true;
        RecorderState.statusText = "正在整理";
        RecorderState.elapsedMs = Math.max(RecorderState.elapsedMs, runBaseElapsed);
        store.setDuration(noteId, RecorderState.elapsedMs);
        store.setStatus(noteId, Note.TRANSCRIBING);
        updateNotification(true);
        if (settings.isAutoTranscribe() && settings.hasAsrKey() && workerRunning) {
            queue.offer(POISON);
        } else {
            new Thread(this::finishSession, "tingji-finish").start();
        }
    }

    private void finishSession() {
        if (!finishOnce.compareAndSet(false, true)) return;
        try {
            Note note = store.getNote(noteId);
            if (note == null) {
                stopSelf();
                return;
            }
            if (note.transcript == null || note.transcript.trim().isEmpty()) {
                String why = note.segmentsFailed > 0
                        ? "有 " + note.segmentsFailed + " 段转写失败，录音已保留，可点“重试转写”"
                        : "没有识别到有效内容";
                store.setAIError(noteId, why);
                store.setStatus(noteId, Note.DRAFT);
                RecorderState.error = why;
                RecorderState.statusText = "未完成";
                return;
            }
            if (settings.isAutoAI() && settings.hasDeepSeekKey()) {
                store.setStatus(noteId, Note.ANALYZING);
                RecorderState.processing = true;
                RecorderState.statusText = "AI 正在整理";
                updateNotification(true);
                NoteAI.Result result = noteAI.process(settings, note, stage -> {
                    RecorderState.statusText = stage;
                    updateNotification(false);
                });
                store.setCorrected(noteId, result.corrected);
                store.applyAI(noteId, result.title, result.category, result.subject,
                        result.summary, result.keyPoints, result.examples, result.actions,
                        result.decisions, result.tags);
                RecorderState.statusText = "整理完成";
            } else {
                store.setAIError(noteId, settings.isAutoAI()
                        ? "已转写，但还没有配置 DeepSeek API Key"
                        : "已转写，未开启自动整理");
                store.setStatus(noteId, Note.DRAFT);
                RecorderState.statusText = "转写完成";
            }
        } catch (Throwable t) {
            String msg = t.getMessage() == null ? "整理失败" : t.getMessage();
            store.setAIError(noteId, msg);
            store.setStatus(noteId, Note.DRAFT);
            RecorderState.error = msg;
            RecorderState.statusText = "整理失败";
            Log.w(TAG, "finish failed", t);
        } finally {
            RecorderState.processing = false;
            RecorderState.active = false;
            RecorderState.serviceRunning = false;
            RecorderState.updatedAt = System.currentTimeMillis();
            try {
                stopForeground(true);
            } catch (Throwable ignored) {
            }
            releaseWakeLock();
            stopSelf();
        }
    }

    private void failSession(String message) {
        if (!finishOnce.compareAndSet(false, true)) return;
        Thread wt = workerThread;
        if (wt != null && wt != Thread.currentThread()) wt.interrupt();
        try {
            if (noteId > 0) {
                store.setAIError(noteId, message);
                store.setStatus(noteId, Note.DRAFT);
                store.setDuration(noteId, RecorderState.elapsedMs);
            }
            RecorderState.error = message;
            RecorderState.statusText = "录音失败";
            RecorderState.active = false;
            RecorderState.processing = false;
            RecorderState.serviceRunning = false;
        } finally {
            try {
                stopForeground(true);
            } catch (Throwable ignored) {
            }
            releaseWakeLock();
            stopSelf();
        }
    }

    // ------------------------------------------------------------ notification

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "听记录音",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("录音、转写与 AI 整理进度");
        ch.setShowBadge(false);
        ch.enableVibration(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        String title;
        String text;
        if (RecorderState.processing) {
            title = "听记 · 正在整理";
            text = RecorderState.statusText.isEmpty() ? "正在转写与总结" : RecorderState.statusText;
        } else if (RecorderState.paused) {
            title = "听记 · 已暂停";
            text = RecorderState.clock() + " · " + kind;
        } else {
            title = "听记 · 正在录音";
            text = RecorderState.clock() + " · " + kind;
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_tingji)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(contentIntent());
        if (!RecorderState.processing) {
            Intent pause = new Intent(this, RecorderService.class)
                    .setAction(RecorderState.paused ? ACTION_RESUME : ACTION_PAUSE);
            PendingIntent pi = PendingIntent.getService(this, 11, pause,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_tingji),
                    RecorderState.paused ? "继续" : "暂停", pi).build());
            Intent stop = new Intent(this, RecorderService.class).setAction(ACTION_STOP);
            PendingIntent si = PendingIntent.getService(this, 12, stop,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_tingji),
                    "结束", si).build());
        }
        return b.build();
    }

    private PendingIntent contentIntent() {
        Intent i;
        if (RecorderState.active && noteId > 0) {
            i = new Intent(this, RecordActivity.class)
                    .putExtra(EXTRA_NOTE_ID, noteId)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        } else if (noteId > 0) {
            i = new Intent(this, NoteActivity.class)
                    .putExtra(EXTRA_NOTE_ID, noteId)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            i = new Intent(this, RecordActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return PendingIntent.getActivity(this, 10, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateNotification(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNotificationAt < 3000) return;
        lastNotificationAt = now;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification());
        } catch (Throwable ignored) {
        }
    }

    // --------------------------------------------------------------- ticker

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!sessionActive) return;
            if (!paused) {
                RecorderState.elapsedMs = runBaseElapsed + Math.max(0,
                        System.currentTimeMillis() - runStartedAt);
            }
            float lv = RecorderState.level;
            RecorderState.level = Math.max(0f, lv * 0.78f);
            RecorderState.updatedAt = System.currentTimeMillis();
            updateNotification(false);
            main.postDelayed(this, 350);
        }
    };

    // --------------------------------------------------------------- lifecycle

    private void acquireWakeLock() {
        try {
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(4 * 60 * 60 * 1000L);
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(ticker);
        stopRequest = true;
        pauseRequest = true;
        Thread rt = recordThread;
        if (rt != null) {
            try {
                rt.join(800);
            } catch (InterruptedException ignored) {
            }
        }
        if (sessionActive && !finishOnce.get()) {
            try {
                store.setAIError(noteId, "录音服务被系统停止，片段已保留，可重试转写");
                store.setStatus(noteId, Note.DRAFT);
                store.setDuration(noteId, RecorderState.elapsedMs);
            } catch (Throwable ignored) {
            }
        }
        RecorderState.active = false;
        RecorderState.processing = false;
        RecorderState.serviceRunning = false;
        releaseWakeLock();
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
