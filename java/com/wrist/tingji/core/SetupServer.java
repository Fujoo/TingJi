package com.wrist.tingji.core;

import android.content.Context;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Token-protected phone setup page. It listens only while the watch's setup
 * screen is open, so the API keys never leave the local network.
 */
public class SetupServer {
    public interface Listener {
        void onSaved();
    }

    private final Context context;
    private final Settings settings;
    private final Listener listener;
    private ServerSocket server;
    private Thread thread;
    private volatile boolean running;
    private int port;
    private String token;

    public SetupServer(Context context, Settings settings, Listener listener) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        this.listener = listener;
    }

    public int start() throws IOException {
        token = randomToken();
        IOException last = null;
        for (int p : new int[]{8787, 8788, 8789, 8790}) {
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(p), 8);
                port = p;
                last = null;
                break;
            } catch (IOException e) {
                last = e;
                closeQuietly(server);
                server = null;
            }
        }
        if (server == null) throw last == null ? new IOException("端口被占用") : last;
        running = true;
        thread = new Thread(this::acceptLoop, "tingji-setup");
        thread.setDaemon(true);
        thread.start();
        return port;
    }

    public void stop() {
        running = false;
        closeQuietly(server);
        server = null;
    }

    public String url() {
        String ip = localIp();
        return ip == null ? null : "http://" + ip + ":" + port + "/?t=" + token;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                s.setSoTimeout(7000);
                handle(s);
            } catch (Throwable t) {
                if (!running) break;
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            InputStream in = new BufferedInputStream(s.getInputStream());
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];

            Map<String, String> headers = new HashMap<>();
            String line;
            int guard = 0;
            while ((line = readLine(in)) != null && !line.isEmpty() && guard++ < 60) {
                int p = line.indexOf(':');
                if (p > 0) headers.put(line.substring(0, p).trim().toLowerCase(Locale.US),
                        line.substring(p + 1).trim());
            }
            int len = 0;
            try {
                len = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (Throwable ignored) {
            }
            if (len < 0 || len > 131072) len = 0;
            byte[] body = new byte[len];
            int read = 0;
            while (read < len) {
                int n = in.read(body, read, len - read);
                if (n <= 0) break;
                read += n;
            }
            String form = new String(body, 0, read, StandardCharsets.UTF_8);
            Map<String, String> params = parseForm(form);
            String q = path;
            int qi = q.indexOf('?');
            if (qi >= 0) {
                params.putAll(parseForm(q.substring(qi + 1)));
                q = q.substring(0, qi);
            }
            if (!token.equals(params.get("t"))) {
                respond(s, 403, page("无法打开",
                        "<p class=\"err\">链接已失效，请在手表上重新打开配置页。</p>"));
                return;
            }
            if ("GET".equals(method) && ("/".equals(q) || "/index.html".equals(q))) {
                respond(s, 200, formPage());
                return;
            }
            if ("POST".equals(method) && "/save".equals(q)) {
                save(params);
                if (listener != null) listener.onSaved();
                respond(s, 200, successPage());
                return;
            }
            respond(s, 404, page("未找到", "<p class=\"err\">页面不存在。</p>"));
        } catch (Throwable ignored) {
        }
    }

    private void save(Map<String, String> p) {
        String ds = p.get("ds_key");
        if (!TextUtils.isEmpty(ds) && !ds.trim().isEmpty()) settings.setDeepSeekKey(ds.trim());
        String asr = p.get("asr_key");
        if (!TextUtils.isEmpty(asr) && !asr.trim().isEmpty()) settings.setAsrKey(asr.trim());

        String base = p.get("ds_base");
        if (!TextUtils.isEmpty(base) && !base.trim().isEmpty()) settings.setDeepSeekBase(base.trim());
        String model = p.get("ds_model");
        if (!TextUtils.isEmpty(model) && !model.trim().isEmpty()) settings.setModel(model.trim());

        String preset = p.get("asr_preset");
        if ("siliconflow".equals(preset) || "qwen".equals(preset)
                || "openai".equals(preset) || "groq".equals(preset)) {
            settings.applyAsrPreset(preset);
        } else if ("custom".equals(preset)) {
            // Keep the current endpoint/model; editable fields below override.
            settings.applyAsrPreset("custom");
        }
        if ("custom".equals(preset)) {
            String endpoint = p.get("asr_endpoint");
            if (!TextUtils.isEmpty(endpoint) && !endpoint.trim().isEmpty()) {
                settings.setAsrEndpoint(endpoint.trim());
            }
            String asrModel = p.get("asr_model");
            if (!TextUtils.isEmpty(asrModel) && !asrModel.trim().isEmpty()) {
                settings.setAsrModel(asrModel.trim());
            }
            String lang = p.get("asr_language");
            if (lang != null) settings.setAsrLanguage(lang.trim());
        }

        settings.setAutoTranscribe("on".equals(p.get("auto_transcribe")));
        settings.setAutoCorrect("on".equals(p.get("auto_correct")));
        settings.setAutoSummarize("on".equals(p.get("auto_summarize")));
        String hot = p.get("hotwords");
        if (hot != null) settings.setHotwords(hot.trim());
    }

    private String formPage() {
        String dsSet = settings.hasDeepSeekKey() ? "已设置，留空不修改" : "sk-...";
        String asrSet = settings.hasAsrKey() ? "已设置，留空不修改" : "sk-...";
        String preset = settings.getAsrPreset();
        String checkedT = settings.isAutoTranscribe() ? "checked" : "";
        String checkedC = settings.isAutoCorrect() ? "checked" : "";
        String checkedS = settings.isAutoSummarize() ? "checked" : "";
        return page("手机配置", "" +
                "<p class=\"lead\">在手机上填好，手表立即生效。密钥只保存在手表本地。</p>" +
                "<form method=\"post\" action=\"/save\">" +
                "<input type=\"hidden\" name=\"t\" value=\"" + esc(token) + "\">" +
                "<div class=\"sec\">AI 大脑</div>" +
                "<label>DeepSeek API Key</label>" +
                "<input type=\"password\" name=\"ds_key\" placeholder=\"" + esc(dsSet) + "\" autocomplete=\"off\" spellcheck=\"false\">" +
                "<label>接口地址</label>" +
                "<input type=\"url\" name=\"ds_base\" value=\"" + esc(settings.getDeepSeekBase()) + "\" placeholder=\"https://api.deepseek.com\">" +
                "<label>对话模型</label>" +
                "<input type=\"text\" name=\"ds_model\" value=\"" + esc(settings.getModel()) + "\" placeholder=\"deepseek-flash\">" +
                "<div class=\"hint\">纠错、摘要、要点、待办、分类都由这个接口完成。</div>" +
                "<div class=\"sec\">语音转写</div>" +
                "<label>语音识别 API Key</label>" +
                "<input type=\"password\" name=\"asr_key\" placeholder=\"" + esc(asrSet) + "\" autocomplete=\"off\" spellcheck=\"false\">" +
                "<label>语音服务</label>" +
                "<select name=\"asr_preset\">" +
                "<option value=\"siliconflow\"" + ("siliconflow".equals(preset) ? " selected" : "") + ">硅基流动 · TeleSpeechASR（推荐）</option>" +
                "<option value=\"qwen\"" + ("qwen".equals(preset) ? " selected" : "") + ">硅基流动 · Qwen3-ASR-1.7B</option>" +
                "<option value=\"openai\"" + ("openai".equals(preset) ? " selected" : "") + ">OpenAI Whisper</option>" +
                "<option value=\"groq\"" + ("groq".equals(preset) ? " selected" : "") + ">Groq Whisper</option>" +
                "<option value=\"custom\"" + ("custom".equals(preset) ? " selected" : "") + ">自定义 OpenAI 兼容接口</option>" +
                "</select>" +
                "<label>识别接口地址</label>" +
                "<input type=\"url\" name=\"asr_endpoint\" value=\"" + esc(settings.getAsrEndpoint()) + "\">" +
                "<label>识别模型</label>" +
                "<input type=\"text\" name=\"asr_model\" value=\"" + esc(settings.getAsrModel()) + "\" placeholder=\"TeleAI/TeleSpeechASR\">" +
                "<label>识别语言</label>" +
                "<input type=\"text\" name=\"asr_language\" value=\"" + esc(settings.getAsrLanguage()) + "\" placeholder=\"zh\">" +
                "<div class=\"hint\">DeepSeek 开放平台没有语音转写接口，需要单独的 ASR 服务；文字智能功能仍由 DeepSeek 驱动。</div>" +
                "<div class=\"sec\">自动整理</div>" +
                "<label class=\"check\"><input type=\"checkbox\" name=\"auto_transcribe\" " + checkedT + "> 录音时自动分段转写</label>" +
                "<label class=\"check\"><input type=\"checkbox\" name=\"auto_correct\" " + checkedC + "> 用 DeepSeek 自动纠错</label>" +
                "<label class=\"check\"><input type=\"checkbox\" name=\"auto_summarize\" " + checkedS + "> 自动总结、提取待办并分类</label>" +
                "<label>专有名词 / 热词（用逗号或顿号分隔）</label>" +
                "<textarea name=\"hotwords\" rows=\"3\" placeholder=\"例如：DeepSeek、张伟、Q4 复盘\">" + esc(settings.getHotwords()) + "</textarea>" +
                "<button type=\"submit\">保存到手表</button>" +
                "</form>" +
                "<p class=\"foot\">页面关闭后本地配置服务会自动停止</p>");
    }

    private String successPage() {
        return page("已保存", "<div class=\"ok\">✓</div><h2>配置已保存</h2>" +
                "<p class=\"lead\">可以关闭这个页面，回到手表继续使用。</p>" +
                "<p class=\"foot\">修改密钥后，下一次请求立即生效。</p>");
    }

    private String page(String title, String content) {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">" +
                "<meta name=\"theme-color\" content=\"#07090b\">" +
                "<title>" + esc(title) + " · 听记</title><style>" +
                "*{box-sizing:border-box}body{margin:0;background:#07090b;color:#f1f7f5;" +
                "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans SC',sans-serif;" +
                "line-height:1.5;padding:18px 14px 34px}.wrap{max-width:560px;margin:0 auto}" +
                ".brand{display:flex;align-items:center;gap:10px;margin:6px 0 18px}" +
                ".logo{width:40px;height:40px;border-radius:12px;background:linear-gradient(145deg,#5eead4,#0f766e);" +
                "display:grid;place-items:center;color:#042f2e;font-size:20px;font-weight:800}.brand b{font-size:18px}" +
                ".card{background:#11161a;border:1px solid #27343a;border-radius:18px;padding:16px}" +
                "h2{font-size:21px;margin:8px 0 4px;text-align:center}.lead{color:#93a6a3;font-size:13px;margin:0 0 12px}" +
                ".sec{color:#5eead4;font-size:12px;font-weight:700;letter-spacing:.08em;margin:22px 0 2px;padding-top:14px;border-top:1px solid #27343a}" +
                "label{display:block;color:#b9cbc7;font-size:12px;margin:13px 0 6px}" +
                "input[type=password],input[type=url],input[type=text]{width:100%;height:45px;border-radius:12px;" +
                "border:1px solid #2d3c42;background:#0b1013;color:#f1f7f5;padding:0 12px;font-size:15px;outline:none}" +
                "textarea{width:100%;border-radius:12px;border:1px solid #2d3c42;background:#0b1013;color:#f1f7f5;" +
                "padding:10px 12px;font-size:14px;outline:none;resize:vertical}" +
                "input:focus,textarea:focus,select:focus{border-color:#5eead4}select{width:100%;height:45px;border-radius:12px;" +
                "border:1px solid #2d3c42;background:#0b1013;color:#f1f7f5;padding:0 10px;font-size:14px}" +
                ".check{display:flex;align-items:center;gap:9px;font-size:14px;color:#f1f7f5;margin:14px 0}" +
                ".check input{width:20px;height:20px;accent-color:#5eead4}.hint{color:#637572;font-size:11px;margin:7px 0 2px}" +
                "button{width:100%;height:50px;border:0;border-radius:14px;background:#5eead4;color:#042f2e;" +
                "font-size:16px;font-weight:700;margin-top:20px}button:active{background:#2dd4bf}" +
                ".foot{text-align:center;color:#526360;font-size:11px;margin:15px 0 0}" +
                ".ok{width:58px;height:58px;border-radius:50%;background:#123a32;color:#5eead4;" +
                "display:grid;place-items:center;font-size:30px;margin:14px auto}.err{color:#ff8a8a}" +
                "</style></head><body><div class=\"wrap\"><div class=\"brand\">" +
                "<div class=\"logo\">听</div><b>听记</b></div>" +
                "<div class=\"card\">" + content + "</div></div></body></html>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static Map<String, String> parseForm(String s) {
        Map<String, String> map = new HashMap<>();
        if (s == null || s.isEmpty()) return map;
        for (String pair : s.split("&")) {
            int i = pair.indexOf('=');
            String k = i >= 0 ? pair.substring(0, i) : pair;
            String v = i >= 0 ? pair.substring(i + 1) : "";
            try {
                k = URLDecoder.decode(k, "UTF-8");
                v = URLDecoder.decode(v, "UTF-8");
            } catch (Throwable ignored) {
            }
            map.put(k, v);
        }
        return map;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) return out.size() == 0 ? null : out.toString("UTF-8");
            if (b == '\n') break;
            if (b != '\r') out.write(b);
            if (out.size() > 8192) break;
        }
        return out.toString("UTF-8");
    }

    private static void respond(Socket s, int code, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : code == 403 ? " Forbidden" : " Error") + "\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: " + body.length + "\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n" +
                "X-Frame-Options: DENY\r\n\r\n";
        OutputStream out = s.getOutputStream();
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String randomToken() {
        String chars = "abcdefghjkmnpqrstuvwxyz23456789";
        Random r = new Random();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 8; i++) b.append(chars.charAt(r.nextInt(chars.length())));
        return b.toString();
    }

    public static String localIp() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                String name = n.getName().toLowerCase(Locale.US);
                if (name.startsWith("rmnet") || name.startsWith("tun") || name.startsWith("ppp")) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void closeQuietly(ServerSocket s) {
        if (s != null) {
            try {
                s.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
