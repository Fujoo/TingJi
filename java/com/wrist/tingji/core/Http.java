package com.wrist.tingji.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Http {
    public static final String UA = "TingJi/1.0 (Android Watch)";

    public static HttpURLConnection open(String url, int connectMs, int readMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setUseCaches(false);
        return c;
    }

    public static void writeJson(HttpURLConnection c, String body, String apiKey) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(bytes.length);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        try (OutputStream os = c.getOutputStream()) {
            os.write(bytes);
            os.flush();
        }
    }

    public static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public static String errorBody(HttpURLConnection c) {
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            return readAll(in);
        } catch (Throwable t) {
            return "";
        }
    }

    public static String apiMessage(int code, String body) {
        String detail = "";
        try {
            JSONObject o = new JSONObject(body);
            if (o.has("error")) {
                Object e = o.get("error");
                if (e instanceof JSONObject) {
                    detail = ((JSONObject) e).optString("message", "");
                } else {
                    detail = String.valueOf(e);
                }
            }
            if (detail.isEmpty()) detail = o.optString("message", "");
            if (detail.isEmpty()) detail = o.optString("detail", "");
        } catch (Throwable ignored) {
        }
        switch (code) {
            case 400:
                return detail.isEmpty() ? "请求格式不正确" : detail;
            case 401:
                return "API Key 无效或已过期";
            case 402:
                return "账户余额不足，请先充值";
            case 403:
                return "没有访问权限" + (detail.isEmpty() ? "" : "：" + detail);
            case 404:
                return "接口地址或模型不存在，请检查设置";
            case 413:
                return "上传内容太大";
            case 429:
                return "请求太频繁，请稍后再试";
            case 500:
            case 502:
            case 503:
            case 504:
                return "服务暂时不可用，请稍后再试";
            default:
                return detail.isEmpty() ? ("网络错误（HTTP " + code + "）") : detail;
        }
    }

    public static boolean networkAvailable(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            NetworkInfo n = cm.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Throwable t) {
            return true;
        }
    }
}
