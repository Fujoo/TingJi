package com.wrist.tingji.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Tiny WAV helpers for the 16 kHz/16-bit/mono segments produced by the
 * recorder. Kept dependency-free and allocation-conscious: a 65 s segment is
 * about 2 MB, safe even on a 32-bit watch.
 */
public final class WavUtil {
    public static final int RATE = 16000;
    public static final int HEADER = 44;

    private WavUtil() {
    }

    public static byte[] header(int dataLen) {
        byte[] h = new byte[HEADER];
        int p = 0;
        h[p++] = 'R'; h[p++] = 'I'; h[p++] = 'F'; h[p++] = 'F';
        putInt(h, p, 36 + dataLen); p += 4;
        h[p++] = 'W'; h[p++] = 'A'; h[p++] = 'V'; h[p++] = 'E';
        h[p++] = 'f'; h[p++] = 'm'; h[p++] = 't'; h[p++] = ' ';
        putInt(h, p, 16); p += 4;
        putShort(h, p, 1); p += 2;
        putShort(h, p, 1); p += 2;
        putInt(h, p, RATE); p += 4;
        putInt(h, p, RATE * 2); p += 4;
        putShort(h, p, 2); p += 2;
        putShort(h, p, 16); p += 2;
        h[p++] = 'd'; h[p++] = 'a'; h[p++] = 't'; h[p++] = 'a';
        putInt(h, p, dataLen);
        return h;
    }

    public static void writeHeader(RandomAccessFile raf, long dataLen) throws IOException {
        raf.seek(0);
        raf.write(header((int) Math.min(Integer.MAX_VALUE, dataLen)));
    }

    public static int dataLength(File f) {
        long len = f == null ? 0 : f.length();
        return (int) Math.max(0, len - HEADER);
    }

    public static long durationMs(File f) {
        return dataLength(f) * 1000L / (RATE * 2L);
    }

    public static short[] readSamples(File f) throws IOException {
        byte[] all = readAll(f);
        int start = all.length >= HEADER ? HEADER : 0;
        int n = (all.length - start) / 2;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            int lo = all[start + i * 2] & 0xff;
            int hi = all[start + i * 2 + 1];
            out[i] = (short) (lo | (hi << 8));
        }
        return out;
    }

    /**
     * Copies a segment while applying a gentle high-pass and peak
     * normalisation. Meeting audio recorded on a watch is often 20-30 dB too
     * quiet for ASR; this lifts it without clipping.
     */
    public static void normalizedCopy(File src, File dst) throws IOException {
        short[] in = readSamples(src);
        if (in.length == 0) {
            writeFile(dst, new short[0]);
            return;
        }
        int peak = 0;
        double sum = 0;
        for (short v : in) {
            int a = Math.abs((int) v);
            if (a > peak) peak = a;
            sum += (double) v * v;
        }
        double rms = Math.sqrt(sum / in.length);
        double gain = 1.0;
        if (peak > 90) {
            gain = Math.min(8.0, 0.72 * 32767.0 / peak);
            if (gain < 1.05) gain = 1.0;
            // Do not turn a very quiet hiss into a roar.
            if (rms < 18) gain = Math.min(gain, 1.5);
        }

        short[] out = new short[in.length];
        double a = Math.exp(-2.0 * Math.PI * 70.0 / RATE);
        double prevIn = 0, prevOut = 0;
        int fade = RATE / 50;
        double tanhNorm = Math.tanh(1.1);
        for (int i = 0; i < in.length; i++) {
            double x = in[i];
            double hp = a * (prevOut + x - prevIn);
            prevIn = x;
            prevOut = hp;
            double v = hp * gain / 32768.0;
            v = Math.tanh(v * 1.1) / tanhNorm;
            if (i < fade) v *= i / (double) fade;
            if (i > in.length - fade) v *= (in.length - i) / (double) fade;
            int iv = (int) Math.round(v * 32767.0);
            if (iv > 32767) iv = 32767;
            if (iv < -32768) iv = -32768;
            out[i] = (short) iv;
        }
        writeFile(dst, out);
    }

    public static void writeFile(File dst, short[] samples) throws IOException {
        int dataLen = samples.length * 2;
        try (FileOutputStream out = new FileOutputStream(dst)) {
            out.write(header(dataLen));
            byte[] buf = new byte[8192];
            int p = 0;
            for (short s : samples) {
                buf[p++] = (byte) (s & 0xff);
                buf[p++] = (byte) ((s >> 8) & 0xff);
                if (p == buf.length) {
                    out.write(buf, 0, p);
                    p = 0;
                }
            }
            if (p > 0) out.write(buf, 0, p);
        }
    }

    public static void writeSilence(File dst, int durationMs) throws IOException {
        int samples = RATE * Math.max(1, durationMs) / 1000;
        writeFile(dst, new short[samples]);
    }

    public static byte[] readAll(File f) throws IOException {
        long len = f.length();
        if (len <= 0) return new byte[0];
        if (len > 8L * 1024 * 1024) throw new IOException("录音片段过大");
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) len);
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    private static void putInt(byte[] b, int p, int v) {
        b[p] = (byte) (v & 0xff);
        b[p + 1] = (byte) ((v >> 8) & 0xff);
        b[p + 2] = (byte) ((v >> 16) & 0xff);
        b[p + 3] = (byte) ((v >> 24) & 0xff);
    }

    private static void putShort(byte[] b, int p, int v) {
        b[p] = (byte) (v & 0xff);
        b[p + 1] = (byte) ((v >> 8) & 0xff);
    }
}
