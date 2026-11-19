package com.jshz.expert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地 LLM 语义抽取（Ollama，数据不出内网）——简历解析的语义理解策略。
 * 环境变量：OLLAMA_URL（如 http://127.0.0.1:11434，未配置则语义层停用）与 OLLAMA_MODEL（默认 qwen2.5:7b）。
 * 参考 open-resume / Resume-Matcher 的"抽取→结构化 JSON"范式；失败返回 null，调用方回退启发式。
 */
@Slf4j
@Service
public class OllamaService {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    // 默认值单一来源（可分别由 OLLAMA_MODEL / OLLAMA_TIMEOUT 环境变量覆盖）
    private static final String DEFAULT_MODEL = "qwen2.5:7b";
    private static final String DEFAULT_TIMEOUT_SEC = "180";
    private static final String DEFAULT_KEEP_ALIVE = "60m";

    private static String baseUrl() {
        return System.getenv("OLLAMA_URL").replaceAll("/+$", "");
    }

    private static String modelOrDefault() {
        return System.getenv().getOrDefault("OLLAMA_MODEL", DEFAULT_MODEL);
    }

    private static long resolveTimeoutSeconds() {
        try {
            return Long.parseLong(System.getenv().getOrDefault("OLLAMA_TIMEOUT", DEFAULT_TIMEOUT_SEC));
        } catch (Exception ignored) {
            return Long.parseLong(DEFAULT_TIMEOUT_SEC);
        }
    }

    /** 语义层是否已配置启用（OLLAMA_URL 存在即启用）。 */
    public boolean available() {
        return System.getenv("OLLAMA_URL") != null && !System.getenv("OLLAMA_URL").isBlank();
    }

    /**
     * 从简历文本中语义抽取专家字段；不可用/失败/超时返回 null（调用方回退启发式）。
     * 只输出命中的字段，缺失字段不猜测。
     */
    public Map<String, String> extractResumeFields(String text) {
        if (!available() || text == null || text.isBlank()) return null;
        String url = baseUrl();
        String model = modelOrDefault();
        String prompt = """
                你是专家档案录入助手。从以下简历文本中抽取字段，严格输出 JSON 对象（不要输出任何其他文字）：
                {"姓名":"","单位":"","科室":"","职称":"","联系方式":""}
                规则：只填写能从文本中确定的字段，不确定或缺失的输出空字符串；姓名为 2-4 个汉字的人名；
                联系方式填手机号或邮箱（仅一个）；不要编造信息。简历文本：
                """.replaceAll("\\n{3,}", "\n\n") + text.substring(0, Math.min(text.length(), 4000));
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "format", "json",
                    "keep_alive", DEFAULT_KEEP_ALIVE));
            // 冷加载模型 + 长文本预填充可能超过 60s，超时可由 OLLAMA_TIMEOUT 秒覆盖（默认 180）
            long timeoutSec = resolveTimeoutSeconds();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/generate"))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Ollama 语义抽取失败：HTTP {}", response.statusCode());
                return null;
            }
            JsonNode node = json.readTree(response.body());
            JsonNode fieldsNode = json.readTree(node.path("response").asText("{}"));
            Map<String, String> fields = new LinkedHashMap<>();
            for (String key : List.of("姓名", "单位", "科室", "职称", "联系方式")) {
                String v = fieldsNode.path(key).asText("").trim();
                if (!v.isEmpty()) fields.put(key, v);
            }
            return fields;
        } catch (Exception e) {
            log.warn("Ollama 语义抽取异常：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从日程文本中抽取参会专家与角色（2026-11-09 流程重塑）：规则打底过薄时的语义兜底。
     * 输出受限枚举 role：CHAIR 大会主席/HOST 大会主持/SPEAKER 大会讲者/DISCUSSANT 讨论嘉宾。
     * 不可用/失败/超时返回 null（调用方保留规则结果并提示手动录入）。
     */
    public java.util.List<com.jshz.expert.entity.ProjectSpeaker> extractScheduleSpeakers(String text) {
        if (!available() || text == null || text.isBlank()) return null;
        String url = baseUrl();
        String model = modelOrDefault();
        String prompt = """
                你是会议日程解析助手。从以下会议日程文本中抽取参会专家，严格输出 JSON 对象（不要输出任何其他文字）：
                {"speakers":[{"name":"","role":"","segmentTitle":"","segmentTime":""}]}
                规则：role 只能取 CHAIR(大会主席)/HOST(大会主持)/SPEAKER(大会讲者)/DISCUSSANT(讨论嘉宾)；
                讨论嘉宾=同一段落/议题下多人共同发言；按每次出场分别输出，保留同一人不同角色或不同议题
                姓名为 2-4 个汉字人名，去掉"教授"等头衔；议题和时间仅使用原文可确认的对应信息，缺失则留空；不要编造。
                日程文本：
                """.replaceAll("\\n{3,}", "\n\n") + text.substring(0, Math.min(text.length(), 4000));
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "format", "json",
                    "keep_alive", DEFAULT_KEEP_ALIVE));
            long timeoutSec = resolveTimeoutSeconds();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/generate"))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Ollama 日程抽取失败：HTTP {}", response.statusCode());
                return null;
            }
            JsonNode node = json.readTree(response.body());
            JsonNode speakers = json.readTree(node.path("response").asText("{}")).path("speakers");
            List<com.jshz.expert.entity.ProjectSpeaker> result = new java.util.ArrayList<>();
            Set<String> seen = new java.util.LinkedHashSet<>();
            for (JsonNode s : speakers) {
                String name = s.path("name").asText("").replaceAll("[\\s\\u3000\\u00a0]", "");
                String role = s.path("role").asText("");
                if (name.length() < 2 || name.length() > 4) continue;
                if (!Set.of("CHAIR", "HOST", "SPEAKER", "DISCUSSANT").contains(role)) continue;
                if (!seen.add(name)) continue;
                com.jshz.expert.entity.ProjectSpeaker sp = new com.jshz.expert.entity.ProjectSpeaker();
                sp.setExpertName(name);
                sp.setRole(role);
                String title = s.path("segmentTitle").asText("");
                String time = s.path("segmentTime").asText("");
                sp.setSegmentTitle(title.isBlank() ? null : title);
                sp.setSegmentTime(time.isBlank() ? null : time);
                result.add(sp);
            }
            return result;
        } catch (Exception e) {
            log.warn("Ollama 日程抽取异常：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 通用 JSON 生成（Owner 2026-11-10 合规重要性标记引入）：prompt 要求严格输出 JSON，返回模型 response 原文。
     * 不可用/失败/超时返回 null，调用方回退规则结果；不再各方法重复 HTTP 样板。
     */
    public String completeJson(String prompt) {
        return completeJson(prompt, resolveTimeoutSeconds());
    }

    /** 指定超时的 JSON 补全（AI 助手等交互场景用短超时，避免用户干等冷加载）。 */
    public String completeJson(String prompt, long timeoutSec) {
        if (!available() || prompt == null || prompt.isBlank()) return null;
        String url = baseUrl();
        String model = modelOrDefault();
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "format", "json",
                    "keep_alive", DEFAULT_KEEP_ALIVE));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/generate"))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Ollama 生成失败：HTTP {}", response.statusCode());
                return null;
            }
            return json.readTree(response.body()).path("response").asText(null);
        } catch (Exception e) {
            log.warn("Ollama 生成异常：{}", e.getMessage());
            return null;
        }
    }

    /** 自由文本对话（AI 助手闲聊，2026-11-16）：不限定 JSON 格式；失败/超时返回 null。 */
    public String chat(String prompt, long timeoutSec) {
        if (!available() || prompt == null || prompt.isBlank()) return null;
        String url = baseUrl();
        String model = modelOrDefault();
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "keep_alive", DEFAULT_KEEP_ALIVE));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/generate"))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Ollama 对话失败：HTTP {}", response.statusCode());
                return null;
            }
            return json.readTree(response.body()).path("response").asText(null);
        } catch (Exception e) {
            log.warn("Ollama 对话异常：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 合规草稿重要性分类（Owner 2026-11-10）：识别重要事项供解析链标记 priority=HIGH。
     * 判定口径（写入 prompt）：资金/合同/法务/付款/发票/时限截止/监管处罚/证件资质/审计等为重要，
     * 一般性事务性准备为普通。返回与入参等长的 boolean 列表（index 对应）；失败返回 null 回退。
     */
    public boolean[] complianceImportance(List<String> titles) {
        if (!available() || titles == null || titles.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < titles.size(); i++) {
            String t = titles.get(i) == null ? "" : titles.get(i).replaceAll("\\s+", " ");
            sb.append(i + 1).append('.').append(t, 0, Math.min(t.length(), 80)).append('\n');
        }
        String prompt = """
                你是合规事项审核助手。判断下面每个合规事项是否"重要"。严格输出 JSON 对象（不要输出任何其他文字）：
                {"flags":[true,false]}
                规则：flags 为与事项列表等长的布尔数组，按顺序对应；判定为重要的口径：涉及资金/付款/合同/法务/
                发票/时限截止/监管/处罚/证件/资质/审计/上报，或逾期会产生严重后果；一般性事务准备（如预订会场、
                收集名单、日常签到）为普通(false)。拿不准时输出 false。事项列表：
                """ + sb;
        String resp = completeJson(prompt);
        if (resp == null) return null;
        try {
            JsonNode flags = json.readTree(resp).path("flags");
            if (!flags.isArray() || flags.size() < titles.size()) return null;
            boolean[] result = new boolean[titles.size()];
            for (int i = 0; i < titles.size(); i++) result[i] = flags.get(i).asBoolean(false);
            return result;
        } catch (Exception e) {
            log.warn("Ollama 合规重要性解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 简历科室语义判定（Owner 2026-11-19）：从含"科"字的候选段中选出真正的医院科室。
     * 判定口径：学会/协会/委员会任职机构（如 肿瘤内科专业委员会）、公司、大学不是科室。
     * 返回候选列表中的原文（未命中返回 null 回退，不臆造）。
     */
    public String pickResumeDepartment(java.util.List<String> candidates) {
        if (!available() || candidates == null || candidates.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            sb.append(i + 1).append('.').append(candidates.get(i), 0, Math.min(candidates.get(i).length(), 30)).append('\n');
        }
        String prompt = """
                你是简历解析助手。判断下面哪些候选词是医生的"科室"（医院内部科室，如 乳腺甲状腺外科、
                肿瘤内科、放疗科、心内科、骨科）。注意：学会/协会/委员会等任职机构（如"肿瘤内科专业委员会"、
                "放射学会"）不是科室；公司、大学、研究领域（如"科研"）也不是科室。
                严格输出 JSON 对象（不要输出任何其他文字）：{"dept":"候选词原文"}
                候选中只有一个科室时输出它；没有科室则输出 {"dept":""}。候选列表：
                """ + sb;
        String resp = completeJson(prompt);
        if (resp == null) return null;
        try {
            String dept = json.readTree(resp).path("dept").asText("").replaceAll("\\s+", "");
            return candidates.contains(dept) ? dept : null;
        } catch (Exception e) {
            log.warn("Ollama 科室判定失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 启动预热（Owner 2026-11-07）：系统上线后自动把模型加载进显存/内存，
     * 避免首次简历解析承担冷加载耗时。异步执行，失败仅记日志。
     * keep_alive 60m：预热后模型驻留 60 分钟，期间解析请求秒级响应。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        if (!available()) return;
        Thread warm = new Thread(() -> {
            String url = baseUrl();
            String model = modelOrDefault();
            try {
                String body = json.writeValueAsString(Map.of(
                        "model", model,
                        "prompt", "预热",
                        "stream", false,
                        "keep_alive", DEFAULT_KEEP_ALIVE));
                HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/generate"))
                        .timeout(Duration.ofSeconds(resolveTimeoutSeconds()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("Ollama 预热完成（model={}，HTTP {}）", model, response.statusCode());
            } catch (Exception e) {
                log.warn("Ollama 预热失败（不影响功能，首次解析将承担冷加载）：{}", e.getMessage());
            }
        }, "ollama-warmup");
        warm.setDaemon(true);
        warm.start();
    }
}
