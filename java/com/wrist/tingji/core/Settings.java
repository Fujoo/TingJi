package com.wrist.tingji.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/** All user preferences live here. Secrets are encrypted by {@link SecureStore}. */
public class Settings {
    public static final String DEFAULT_DS_BASE = "https://api.deepseek.com";
    public static final String DEFAULT_DS_MODEL = "deepseek-flash";
    public static final String DEFAULT_ASR_ENDPOINT =
            "https://api.siliconflow.cn/v1/audio/transcriptions";
    public static final String DEFAULT_ASR_MODEL = "TeleAI/TeleSpeechASR";
    public static final String QWEN_ASR_MODEL = "Qwen/Qwen3-ASR-1.7B";

    private final SharedPreferences p;
    private final SecureStore secrets;

    public Settings(Context context) {
        p = context.getSharedPreferences("tingji", Context.MODE_PRIVATE);
        secrets = new SecureStore();
    }

    private String s(String key, String def) {
        String v = p.getString(key, def);
        return v == null ? def : v;
    }

    private void put(String key, String value) {
        p.edit().putString(key, value == null ? "" : value).apply();
    }

    public String getDeepSeekKey() {
        return secrets.decrypt(s("ds_key", ""));
    }

    public void setDeepSeekKey(String value) {
        put("ds_key", secrets.encrypt(value == null ? "" : value.trim()));
    }

    public String getAsrKey() {
        return secrets.decrypt(s("asr_key", ""));
    }

    public void setAsrKey(String value) {
        put("asr_key", secrets.encrypt(value == null ? "" : value.trim()));
    }

    public String getDeepSeekBase() {
        String v = s("ds_base", DEFAULT_DS_BASE).trim();
        if (v.isEmpty()) v = DEFAULT_DS_BASE;
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    public void setDeepSeekBase(String value) {
        put("ds_base", value == null ? "" : value.trim());
    }

    /** Accepts an API root or a full /chat/completions URL. */
    public String getChatEndpoint() {
        String base = getDeepSeekBase();
        if (base.endsWith("/chat/completions")) return base;
        if (base.endsWith("/v1")) return base + "/chat/completions";
        return base + "/chat/completions";
    }

    public String getModel() {
        String v = s("ds_model", DEFAULT_DS_MODEL).trim();
        if (v.isEmpty()) return DEFAULT_DS_MODEL;
        return v;
    }

    public void setModel(String value) {
        put("ds_model", value == null ? "" : value.trim());
    }

    public String getAsrEndpoint() {
        String v = s("asr_endpoint", DEFAULT_ASR_ENDPOINT).trim();
        if (v.isEmpty()) v = DEFAULT_ASR_ENDPOINT;
        return v;
    }

    public void setAsrEndpoint(String value) {
        put("asr_endpoint", value == null ? "" : value.trim());
    }

    public String getAsrModel() {
        String v = s("asr_model", DEFAULT_ASR_MODEL).trim();
        return v.isEmpty() ? DEFAULT_ASR_MODEL : v;
    }

    public void setAsrModel(String value) {
        put("asr_model", value == null ? "" : value.trim());
    }

    public String getAsrLanguage() {
        return s("asr_language", "").trim();
    }

    public void setAsrLanguage(String value) {
        put("asr_language", value == null ? "" : value.trim());
    }

    public String getAsrPreset() {
        return s("asr_preset", "siliconflow");
    }

    public void applyAsrPreset(String preset) {
        if ("siliconflow".equals(preset)) {
            setAsrEndpoint(DEFAULT_ASR_ENDPOINT);
            setAsrModel(DEFAULT_ASR_MODEL);
            setAsrLanguage("");
        } else if ("qwen".equals(preset)) {
            setAsrEndpoint(DEFAULT_ASR_ENDPOINT);
            setAsrModel(QWEN_ASR_MODEL);
            setAsrLanguage("");
        } else if ("openai".equals(preset)) {
            setAsrEndpoint("https://api.openai.com/v1/audio/transcriptions");
            setAsrModel("whisper-1");
            setAsrLanguage("zh");
        } else if ("groq".equals(preset)) {
            setAsrEndpoint("https://api.groq.com/openai/v1/audio/transcriptions");
            setAsrModel("whisper-large-v3-turbo");
            setAsrLanguage("zh");
        }
        put("asr_preset", preset == null ? "" : preset);
    }

    public String getAsrPresetName() {
        String p = getAsrPreset();
        if ("siliconflow".equals(p)) return "硅基流动 · TeleSpeech";
        if ("qwen".equals(p)) return "硅基流动 · Qwen3-ASR";
        if ("openai".equals(p)) return "OpenAI";
        if ("groq".equals(p)) return "Groq";
        return "自定义";
    }

    public boolean isAutoTranscribe() {
        return p.getBoolean("auto_transcribe", true);
    }

    public void setAutoTranscribe(boolean value) {
        p.edit().putBoolean("auto_transcribe", value).apply();
    }

    public boolean isAutoCorrect() {
        return p.getBoolean("auto_correct", true);
    }

    public void setAutoCorrect(boolean value) {
        p.edit().putBoolean("auto_correct", value).apply();
    }

    public boolean isAutoSummarize() {
        return p.getBoolean("auto_summarize", true);
    }

    public void setAutoSummarize(boolean value) {
        p.edit().putBoolean("auto_summarize", value).apply();
    }

    public boolean isAutoAI() {
        return isAutoCorrect() || isAutoSummarize();
    }

    public String getHotwords() {
        return s("hotwords", "").trim();
    }

    public void setHotwords(String value) {
        put("hotwords", value == null ? "" : value.trim());
    }

    public String getLastKind() {
        String k = s("last_kind", "会议");
        if ("课堂".equals(k) || "会议".equals(k)) return k;
        return "会议";
    }

    public void setLastKind(String value) {
        if ("课堂".equals(value) || "会议".equals(value)) put("last_kind", value);
    }

    public boolean isHapticsEnabled() {
        return p.getBoolean("haptics", true);
    }

    public void setHapticsEnabled(boolean value) {
        p.edit().putBoolean("haptics", value).apply();
    }

    public static final float MIN_UI_SCALE = 0.75f;
    public static final float MAX_UI_SCALE = 1.30f;

    public float getUiScale() {
        float v = p.getFloat("ui_scale", 1f);
        return Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, v));
    }

    public void setUiScale(float value) {
        float v = Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, value));
        p.edit().putFloat("ui_scale", Math.round(v * 100f) / 100f).apply();
    }

    public boolean isScaleConfigured() {
        return p.getBoolean("ui_scale_configured", false);
    }

    public void setScaleConfigured() {
        p.edit().putBoolean("ui_scale_configured", true).apply();
    }

    public boolean hasDeepSeekKey() {
        return !TextUtils.isEmpty(getDeepSeekKey());
    }

    public boolean hasAsrKey() {
        return !TextUtils.isEmpty(getAsrKey());
    }

    public String keyHint(String key) {
        if (TextUtils.isEmpty(key)) return "未设置";
        if (key.length() <= 8) return "已设置";
        return "已设置 · " + key.substring(key.length() - 4);
    }
}
