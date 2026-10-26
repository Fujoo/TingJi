package com.wrist.tingji.core;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;

/** DeepSeek / OpenAI-compatible chat-completions client. */
public class AiClient {

    public String completeText(Settings s, String system, String user, int maxTokens)
            throws Exception {
        return complete(s, system, user, maxTokens, false);
    }

    public String completeJson(Settings s, String system, String user, int maxTokens)
            throws Exception {
        return complete(s, system, user, maxTokens, true);
    }

    private String complete(Settings s, String system, String user, int maxTokens,
                            boolean jsonMode) throws Exception {
        String key = s.getDeepSeekKey();
        if (TextUtils.isEmpty(key)) throw new Exception("还没有配置 DeepSeek API Key");

        JSONObject body = new JSONObject();
        body.put("model", s.getModel());
        body.put("stream", false);
        body.put("temperature", 0.2);
        body.put("max_tokens", maxTokens);
        if (jsonMode) {
            JSONObject rf = new JSONObject();
            rf.put("type", "json_object");
            body.put("response_format", rf);
        }
        JSONArray messages = new JSONArray();
        if (!TextUtils.isEmpty(system)) {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", system);
            messages.put(sys);
        }
        JSONObject u = new JSONObject();
        u.put("role", "user");
        u.put("content", user);
        messages.put(u);
        body.put("messages", messages);

        try {
            return request(s, body.toString(), key, 180000);
        } catch (ApiException e) {
            // A few OpenAI-compatible gateways reject response_format. Retry
            // once without it; the prompts still ask for strict JSON.
            if (jsonMode && e.code == 400) {
                body.remove("response_format");
                return request(s, body.toString(), key, 180000);
            }
            throw e;
        }
    }

    private String request(Settings s, String body, String key, int readTimeout)
            throws Exception {
        HttpURLConnection c = null;
        try {
            c = Http.open(s.getChatEndpoint(), 15000, readTimeout);
            Http.writeJson(c, body, key);
            int code = c.getResponseCode();
            String raw = Http.readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code != 200) throw new ApiException(code, Http.apiMessage(code, raw));
            JSONObject o = new JSONObject(raw);
            JSONArray choices = o.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw new Exception("AI 没有返回内容");
            JSONObject msg = choices.optJSONObject(0).optJSONObject("message");
            if (msg == null) throw new Exception("AI 没有返回内容");
            String content = msg.isNull("content") ? "" : msg.optString("content", "");
            if (TextUtils.isEmpty(content)) throw new Exception("AI 返回了空内容");
            return content.trim();
        } catch (ApiException e) {
            throw e;
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

    public void test(Settings s) throws Exception {
        String out = completeText(s, "只回复 OK 两个字母。", "ping", 8);
        if (TextUtils.isEmpty(out)) throw new Exception("AI 没有回复");
    }

    public static String networkMessage(Exception e) {
        if (e == null) return "网络错误";
        String m = e.getMessage();
        if (e instanceof java.net.SocketTimeoutException) return "AI 请求超时，请检查手表网络";
        if (e instanceof java.net.UnknownHostException) return "无法连接 AI 服务，请检查网络";
        if (e instanceof javax.net.ssl.SSLException) return "安全连接失败，请检查网络或代理";
        if (m == null || m.trim().isEmpty()) return "网络错误，请稍后重试";
        if (m.contains("timeout") || m.contains("timed out")) return "AI 请求超时，请检查手表网络";
        return m;
    }

    public static class ApiException extends Exception {
        public final int code;

        public ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
