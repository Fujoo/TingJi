package com.wrist.tingji.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a raw ASR transcript into a corrected transcript, summary, key points,
 * action items, decisions, tags and a category. All of it is driven by one
 * OpenAI-compatible chat API (DeepSeek by default).
 */
public class NoteAI {
    public interface Progress {
        void onStage(String stage);
    }

    public static class Result {
        public String title = "";
        public String category = "";
        public String subject = "";
        public String corrected = "";
        public String summary = "";
        public String keyPoints = "";
        public String examples = "";
        public String actions = "";
        public String decisions = "";
        public String tags = "";
    }

    private static final int CORRECT_CHUNK = 3200;
    private static final int ANALYZE_LIMIT = 14000;

    private final AiClient client = new AiClient();

    public Result process(Settings settings, Note note, Progress progress) throws Exception {
        if (note == null || note.transcript == null || note.transcript.trim().isEmpty()) {
            throw new Exception("还没有转写内容");
        }
        if (!settings.hasDeepSeekKey()) throw new Exception("还没有配置 DeepSeek API Key");

        String raw = note.transcript.trim();
        String story = "课堂".equals(note.kind) ? "课堂" : "会议";
        String corrected = raw;
        if (settings.isAutoCorrect()) {
            corrected = correct(settings, raw, story, settings.getHotwords(), progress);
        }

        Result r = new Result();
        r.corrected = corrected;
        if (!settings.isAutoSummarize()) {
            r.title = titleFrom(corrected, story);
            r.category = note.kind;
            return r;
        }

        if (progress != null) progress.onStage("正在总结与分类");
        String source = corrected;
        if (source.length() > ANALYZE_LIMIT) {
            source = hierarchicalDigest(settings, source, story, progress);
        }
        Analysis a = analyze(settings, source, story, settings.getHotwords(),
                note.markers, progress);
        r.title = limit(a.title.isEmpty() ? titleFrom(corrected, story) : a.title, 24);
        r.category = normalizeCategory(a.category, note.kind);
        if ("课堂".equals(r.category)) {
            r.subject = normalizeSubject(a.subject, r.title + " " + corrected);
        }
        r.summary = a.summary;
        r.keyPoints = join(a.keyPoints);
        r.examples = join(a.examples);
        r.actions = join(a.actions);
        r.decisions = join(a.decisions);
        r.tags = joinTags(a.tags);
        return r;
    }

    // ---------------------------------------------------------------- correct

    private String correct(Settings s, String raw, String kind, String hotwords,
                           Progress progress) throws Exception {
        List<String> chunks = split(raw, CORRECT_CHUNK);
        StringBuilder out = new StringBuilder(raw.length() + 64);
        String previous = "";
        for (int i = 0; i < chunks.size(); i++) {
            if (progress != null) {
                progress.onStage(chunks.size() == 1 ? "正在纠错"
                        : "正在纠错 " + (i + 1) + "/" + chunks.size());
            }
            String chunk = chunks.get(i);
            String system = "你是中文语音转写校对员。输入来自会议或课堂的实时语音识别，" +
                    "可能含同音字、错别字、断句错误、重复口癖和专有名词错误。" +
                    "只修正明显错误，保持原意、事实、语气和段落顺序；" +
                    "不要总结、不要补充、不要解释、不要加标题。" +
                    "专有名词以用户提供的词表为准。只输出修正后的正文，" +
                    "不要 Markdown 代码块，不要“修正后：”之类前缀。";
            StringBuilder user = new StringBuilder();
            user.append("场景：").append(kind).append('\n');
            if (hotwords != null && !hotwords.trim().isEmpty()) {
                user.append("专有名词：").append(limit(hotwords.trim(), 500)).append('\n');
            }
            if (!previous.isEmpty()) {
                user.append("上文（仅帮助理解，不要输出）：")
                        .append(limit(previous, 180)).append('\n');
            }
            user.append("待校对原文：\n").append(chunk);
            String fixed;
            try {
                fixed = client.completeText(s, system, user.toString(), 4096);
            } catch (Exception e) {
                throw new Exception("纠错失败：" + e.getMessage());
            }
            fixed = cleanCorrected(fixed);
            if (!sane(fixed, chunk)) fixed = chunk;
            if (out.length() > 0) out.append("\n\n");
            out.append(fixed);
            previous = chunk;
        }
        return out.toString();
    }

    private static boolean sane(String fixed, String original) {
        if (fixed == null || fixed.trim().isEmpty()) return false;
        int a = fixed.trim().length();
        int b = Math.max(1, original.trim().length());
        return a >= b * 0.35 && a <= b * 2.8;
    }

    private static String cleanCorrected(String text) {
        if (text == null) return "";
        String s = text.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "");
            s = s.replaceFirst("\\s*```$", "");
        }
        s = s.replaceFirst("^(修正后|校对后|纠错后|修正结果|结果)[:：]\\s*", "");
        s = s.replaceFirst("^以下是[^\\n]*[:：]\\s*", "");
        return s.trim();
    }

    // ---------------------------------------------------------------- analyse

    private String hierarchicalDigest(Settings s, String text, String kind,
                                      Progress progress) throws Exception {
        List<String> chunks = split(text, 8000);
        StringBuilder digest = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (progress != null) progress.onStage("长文分段提炼 " + (i + 1) + "/" + chunks.size());
            String system = "课堂".equals(kind)
                    ? "你是课堂笔记提炼助手。请把这一段转写提炼成本节的知识点、定义或公式、" +
                    "例题方法、重点难点、作业。250字以内，只输出提炼文字，不要编造，不要 Markdown。"
                    : "你是会议记录提炼助手。请把这一段转写提炼成背景、关键结论、重要数据、" +
                    "待办事项。250字以内，只输出提炼文字，不要编造，不要 Markdown。";
            String out = client.completeText(s, system,
                    "记录类型：" + kind + "\n这一段：\n" + chunks.get(i), 700);
            if (digest.length() > 0) digest.append("\n\n");
            digest.append("第 ").append(i + 1).append(" 段：").append(out.trim());
        }
        if (progress != null) progress.onStage("正在汇总全文");
        return digest.toString();
    }

    private Analysis analyze(Settings s, String text, String kind, String hotwords,
                             String markers, Progress progress) throws Exception {
        String system = "课堂".equals(kind) ? classPrompt() : meetingPrompt();
        StringBuilder user = new StringBuilder();
        user.append("记录类型：").append(kind).append('\n');
        if (hotwords != null && !hotwords.trim().isEmpty()) {
            user.append("专有名词：").append(limit(hotwords.trim(), 500)).append('\n');
        }
        if (markers != null && !markers.trim().isEmpty()) {
            user.append("用户手动标记的重点（时间 + 说明）：\n")
                    .append(limit(markers.trim(), 1200)).append('\n');
        }
        user.append("转写全文：\n").append(text);
        String raw;
        try {
            raw = client.completeJson(s, system, user.toString(), 4096);
        } catch (Exception e) {
            throw new Exception("总结失败：" + e.getMessage());
        }
        return parseAnalysis(raw, kind, text);
    }

    private static Analysis parseAnalysis(String raw, String kind, String transcript)
            throws Exception {
        String json = raw == null ? "" : raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "");
            json = json.replaceFirst("\\s*```$", "");
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) json = json.substring(start, end + 1);
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (Throwable t) {
            throw new Exception("AI 返回的整理结果格式异常，请重试");
        }
        Analysis a = new Analysis();
        a.title = o.optString("title", "").trim();
        a.category = o.optString("category", "").trim();
        a.subject = o.optString("subject", "").trim();
        a.summary = o.optString("summary", "").trim();
        if (a.summary.length() > 1200) a.summary = a.summary.substring(0, 1200);
        if (a.summary.isEmpty()) {
            throw new Exception("AI 没有给出摘要，请重试");
        }
        a.keyPoints = list(o.opt("key_points"));
        a.examples = list(o.opt("examples"));
        a.actions = list(o.opt("actions"));
        a.decisions = list(o.opt("decisions"));
        a.tags = list(o.opt("tags"));
        if (a.title.isEmpty()) a.title = titleFrom(transcript, kind);
        return a;
    }

    private static String meetingPrompt() {
        return "你是专业的中文会议记录整理助手。只输出一个 JSON 对象，不要 Markdown，不要解释。字段如下：\n" +
                "title：18字以内、具体可辨认的标题；\n" +
                "category：只能是 会议、课堂、访谈、其他 之一；\n" +
                "summary：120-180字，客观概括主题、过程和结论；\n" +
                "key_points：字符串数组，3-6条，每条一句话；\n" +
                "actions：字符串数组，0-6条，每条格式“负责人｜事项｜时间”，未知写待定；\n" +
                "decisions：字符串数组，0-4条；\n" +
                "tags：字符串数组，3-6个短标签。\n" +
                "必须忠于原文，不要编造；没有的信息用空数组。";
    }

    private static String classPrompt() {
        return "你是擅长整理课堂笔记的助教。只输出一个 JSON 对象，不要 Markdown，不要解释。字段如下：\n" +
                "title：18字以内，课程主题或本节标题；\n" +
                "category：只能是 课堂、讲座、会议、访谈、其他 之一；\n" +
                "subject：学科，只能是 数学、语文、英语、物理、化学、生物、历史、地理、政治、计算机、科学、其他 之一；\n" +
                "summary：120-180字，概括本节讲了什么、核心方法或结论；\n" +
                "key_points：字符串数组，3-8条知识点，每条写清定义、公式或关键结论；公式用纯文本；\n" +
                "examples：字符串数组，0-4条课堂例题或案例，写清题目要点和解法；\n" +
                "actions：字符串数组，0-6条作业、预习或复习任务，格式“任务｜截止时间”，未知写待定；\n" +
                "decisions：字符串数组，0-4条重点、难点、易错点或考点；\n" +
                "tags：字符串数组，3-6个学科或知识点标签。\n" +
                "必须忠于原文，不要编造；没有的信息用空数组。";
    }

    private static List<String> list(Object value) {
        List<String> out = new ArrayList<>();
        if (value == null || value == JSONObject.NULL) return out;
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            for (int i = 0; i < arr.length(); i++) {
                Object item = arr.opt(i);
                String s = itemString(item);
                if (!s.isEmpty()) out.add(s);
            }
        } else {
            String s = String.valueOf(value).trim();
            if (!s.isEmpty()) {
                String[] parts = s.split("\\n|(?<=[。；;])");
                for (String p : parts) {
                    if (!p.trim().isEmpty()) out.add(p.trim());
                }
            }
        }
        return out;
    }

    private static String itemString(Object item) {
        if (item == null || item == JSONObject.NULL) return "";
        if (item instanceof JSONObject) {
            JSONObject o = (JSONObject) item;
            String task = first(o, "task", "item", "content", "事项", "内容");
            String owner = first(o, "owner", "who", "负责人");
            String due = first(o, "due", "deadline", "time", "时间");
            StringBuilder b = new StringBuilder();
            if (!owner.isEmpty()) b.append(owner).append("｜");
            b.append(task);
            if (!due.isEmpty()) b.append("｜").append(due);
            return b.toString().trim();
        }
        return String.valueOf(item).trim().replaceAll("^[-*•]\\s*", "");
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "").trim();
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private static String join(List<String> list) {
        StringBuilder b = new StringBuilder();
        for (String s : list) {
            if (s == null || s.trim().isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(s.trim());
        }
        return b.toString();
    }

    private static String joinTags(List<String> tags) {
        StringBuilder b = new StringBuilder();
        for (String t : tags) {
            String s = t.replace(",", " ").replace("，", " ").trim();
            if (s.isEmpty()) continue;
            if (b.length() > 0) b.append(',');
            b.append(s);
        }
        return b.toString();
    }

    public static final String[] SUBJECTS = {"数学", "语文", "英语", "物理", "化学",
            "生物", "历史", "地理", "政治", "计算机", "科学", "其他"};

    public static String guessSubject(String context) {
        return matchSubject(context == null ? "" : context);
    }

    public static String normalizeSubject(String value, String context) {
        String v = value == null ? "" : value.trim();
        String c = context == null ? "" : context;
        String hit = matchSubject(v);
        if (!hit.isEmpty()) return hit;
        hit = matchSubject(c);
        return hit.isEmpty() ? "其他" : hit;
    }

    private static String matchSubject(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = text;
        if (s.contains("数学") || s.contains("函数") || s.contains("方程")
                || s.contains("几何") || s.contains("概率") || s.contains("微积分")
                || s.contains("线性代数") || s.contains("导数") || s.contains("统计")) return "数学";
        if (s.contains("语文") || s.contains("古诗") || s.contains("文言文")
                || s.contains("作文") || s.contains("阅读理解") || s.contains("修辞")) return "语文";
        if (s.contains("英语") || s.contains("English") || s.contains("grammar")
                || s.contains("单词") || s.contains("听力") || s.contains("语法")) return "英语";
        if (s.contains("物理") || s.contains("力学") || s.contains("电磁")
                || s.contains("光学") || s.contains("牛顿") || s.contains("电路")) return "物理";
        if (s.contains("化学") || s.contains("元素") || s.contains("分子")
                || s.contains("反应") || s.contains("有机") || s.contains("溶液")) return "化学";
        if (s.contains("生物") || s.contains("细胞") || s.contains("遗传")
                || s.contains("基因") || s.contains("生态") || s.contains("光合")) return "生物";
        if (s.contains("历史") || s.contains("朝代") || s.contains("革命")
                || s.contains("战争") || s.contains("古代") || s.contains("近代")) return "历史";
        if (s.contains("地理") || s.contains("气候") || s.contains("地形")
                || s.contains("洋流") || s.contains("板块") || s.contains("经纬")) return "地理";
        if (s.contains("政治") || s.contains("道德与法治") || s.contains("法律")
                || s.contains("哲学") || s.contains("经济生活")) return "政治";
        if (s.contains("计算机") || s.contains("编程") || s.contains("算法")
                || s.contains("代码") || s.contains("信息技术") || s.contains("数据结构")) return "计算机";
        if (s.contains("科学") || s.contains("实验")) return "科学";
        return "";
    }

    public static String normalizeCategory(String value, String fallback) {
        String v = value == null ? "" : value.trim();
        if (v.contains("课堂") || v.contains("课程") || v.contains("讲座")) return "课堂";
        if (v.contains("会议") || v.contains("例会") || v.contains("评审")) return "会议";
        if (v.contains("访谈") || v.contains("采访")) return "访谈";
        if (v.equals("其他")) return "其他";
        return "课堂".equals(fallback) ? "课堂" : "会议";
    }

    public static String titleFrom(String text, String kind) {
        if (text == null) return "未命名记录";
        for (String line : text.split("\\n")) {
            String s = line.replaceAll("^[#>*\\-\\s]+", "").trim();
            if (s.length() < 4) continue;
            return limit(s.replaceAll("[，。！？；：、,.!?;:]+$", ""), 22);
        }
        return "课堂".equals(kind) ? "课堂记录" : "会议记录";
    }

    // ---------------------------------------------------------------- utils

    public static List<String> split(String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String rest = text.trim();
        while (rest.length() > max) {
            int cut = -1;
            int floor = max / 2;
            for (int i = Math.min(max, rest.length() - 1); i >= floor; i--) {
                char ch = rest.charAt(i);
                if (ch == '\n' || ch == '。' || ch == '！' || ch == '？'
                        || ch == '；' || ch == '.' || ch == '!' || ch == '?') {
                    cut = i + 1;
                    break;
                }
            }
            if (cut < floor) cut = max;
            out.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        if (!rest.isEmpty()) out.add(rest);
        return out;
    }

    public static String limit(String s, int max) {
        if (s == null) return "";
        String v = s.trim();
        return v.length() <= max ? v : v.substring(0, max) + "…";
    }

    private static class Analysis {
        String title;
        String category;
        String subject;
        String summary;
        List<String> keyPoints = new ArrayList<>();
        List<String> examples = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
        List<String> tags = new ArrayList<>();
    }
}
