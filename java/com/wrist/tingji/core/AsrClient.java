package com.wrist.tingji.core;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * OpenAI-compatible /audio/transcriptions client. Defaults to SiliconFlow's
 * TeleSpeechASR, which is quick, good at Chinese, and robust on quiet
 * watch-microphone audio. Any compatible endpoint can be configured.
 */
public class AsrClient {

    public String transcribe(Settings settings, File wav) throws Exception {
        if (wav == null || !wav.isFile() || wav.length() <= WavUtil.HEADER) {
            throw new Exception("录音片段为空");
        }
        String endpoint = settings.getAsrEndpoint();
        if (TextUtils.isEmpty(endpoint)) throw new Exception("还没有配置语音识别服务");
        String key = settings.getAsrKey();
        if (TextUtils.isEmpty(key)) throw new Exception("还没有配置语音识别 Key");

        byte[] wavBytes = WavUtil.readAll(wav);
        String boundary = "----TJ" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, settings.getAsrModel(), settings.getAsrLanguage(), wavBytes);

        HttpURLConnection c = null;
        try {
            c = Http.open(endpoint, 15000, 180000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(body.length);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            c.setRequestProperty("Accept", "application/json");
            if (!TextUtils.isEmpty(key)) c.setRequestProperty("Authorization", "Bearer " + key);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body);
                os.flush();
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String raw = Http.readAll(in);
            if (code != 200) throw new Exception(asrError(code, raw));
            String text = parseText(raw);
            if (TextUtils.isEmpty(text)) {
                throw new Exception("这段没有听清，已保留录音，可重试");
            }
            return text;
        } catch (Exception e) {
            throw new Exception(networkMessage(e));
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Connectivity test. A 200 with empty text still proves endpoint/key/model work. */
    public String test(Settings settings, File tempWav) throws Exception {
        if (tempWav == null || !tempWav.isFile()) throw new Exception("测试音频不可用");
        String endpoint = settings.getAsrEndpoint();
        String key = settings.getAsrKey();
        if (TextUtils.isEmpty(key)) throw new Exception("还没有配置语音识别 Key");
        byte[] wavBytes = WavUtil.readAll(tempWav);
        String boundary = "----TJ" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, settings.getAsrModel(), settings.getAsrLanguage(), wavBytes);
        HttpURLConnection c = null;
        try {
            c = Http.open(endpoint, 15000, 60000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(body.length);
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + key);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body);
                os.flush();
            }
            int code = c.getResponseCode();
            String raw = Http.readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code != 200) throw new Exception(asrError(code, raw));
            return "语音接口连接成功";
        } catch (Exception e) {
            throw new Exception(networkMessage(e));
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static byte[] multipart(String boundary, String model, String language,
                                    byte[] wav) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(wav.length + 2048);
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        write(out, model == null ? "" : model);
        write(out, "\r\n");
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"response_format\"\r\n\r\njson\r\n");
        if (!TextUtils.isEmpty(language)) {
            write(out, "--" + boundary + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"language\"\r\n\r\n");
            write(out, language);
            write(out, "\r\n");
        }
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"segment.wav\"\r\n");
        write(out, "Content-Type: audio/wav\r\n\r\n");
        out.write(wav);
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) throws Exception {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String parseText(String raw) {
        String text = null;
        try {
            JSONObject o = new JSONObject(raw);
            text = o.optString("text", "");
            if (TextUtils.isEmpty(text)) {
                Object r = o.opt("result");
                if (r instanceof JSONArray && ((JSONArray) r).length() > 0) {
                    text = ((JSONArray) r).optString(0, "");
                } else if (r instanceof JSONObject) {
                    text = ((JSONObject) r).optString("text", "");
                } else if (r != null) {
                    text = String.valueOf(r);
                }
            }
            if (TextUtils.isEmpty(text)) {
                JSONObject data = o.optJSONObject("data");
                if (data != null) text = data.optString("text", "");
            }
        } catch (Throwable t) {
            text = raw;
        }
        if (text == null) return "";
        return clean(text);
    }

    public static String clean(String text) {
        if (text == null) return "";
        String s = text;
        s = s.replace("\\n", " ").replace("\\r", " ");
        s = s.replaceAll("<\\|[^|>]*\\|>", " ");
        s = s.replaceAll("\\[[A-Za-z]{2,8}\\]", " ");
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        s = s.replaceAll("^[\"'“”‘’`]+", "").replaceAll("[\"'“”‘’`]+$", "");
        s = s.replaceAll("\\s+", " ").trim();
        if ("null".equalsIgnoreCase(s) || "[BLANK_AUDIO]".equalsIgnoreCase(s)
                || "(silence)".equalsIgnoreCase(s) || "silence".equalsIgnoreCase(s)) {
            return "";
        }
        return s.trim();
    }

    private static String asrError(int code, String raw) {
        String detail = "";
        try {
            JSONObject o = new JSONObject(raw);
            detail = o.optString("message", "");
            if (detail.isEmpty() && o.optJSONObject("error") != null) {
                detail = o.optJSONObject("error").optString("message", "");
            }
            if (detail.isEmpty()) detail = o.optString("detail", "");
        } catch (Throwable ignored) {
        }
        switch (code) {
            case 401:
            case 403:
                return "语音识别 Key 无效或权限不足";
            case 404:
                return "语音识别接口不存在，请检查服务地址";
            case 413:
                return "录音片段太大，请缩短分段";
            case 429:
                return "语音识别请求太频繁，请稍后再试";
            default:
                return detail.isEmpty() ? ("语音识别失败（HTTP " + code + "）") : detail;
        }
    }

    private static String networkMessage(Exception e) {
        String m = e == null ? null : e.getMessage();
        if (m == null || m.trim().isEmpty()) return "语音识别失败，请稍后再试";
        if (e instanceof java.net.SocketTimeoutException || m.contains("timeout")
                || m.contains("timed out")) {
            return "语音识别超时，请检查手表网络";
        }
        if (e instanceof java.net.UnknownHostException) return "无法连接语音服务，请检查网络";
        if (e instanceof javax.net.ssl.SSLException) return "安全连接失败，请检查网络或代理";
        return m;
    }
}
