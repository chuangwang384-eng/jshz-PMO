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
        String url = System.getenv("OLLAMA_URL").replaceAll("/+$", "");
        String model = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5:7b");
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
                    "keep_alive", "60m"));
            // 冷加载模型 + 长文本预填充可能超过 60s，超时可由 OLLAMA_TIMEOUT 秒覆盖（默认 180）
            long timeoutSec = 180;
            try {
                timeoutSec = Long.parseLong(System.getenv().getOrDefault("OLLAMA_TIMEOUT", "180"));
            } catch (Exception ignored) {}
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
        String url = System.getenv("OLLAMA_URL").replaceAll("/+$", "");
        String model = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5:7b");
        String prompt = """
                你是会议日程解析助手。从以下会议日程文本中抽取参会专家，严格输出 JSON 对象（不要输出任何其他文字）：
                {"speakers":[{"name":"","role":"","segmentTitle":"","segmentTime":""}]}
                规则：role 只能取 CHAIR(大会主席)/HOST(大会主持)/SPEAKER(大会讲者)/DISCUSSANT(讨论嘉宾)；
                讨论嘉宾=同一段落/议题下多人共同发言；同一个人有多个角色时取最高的一个
                （主席>主持>讲者>讨论嘉宾）；姓名为 2-4 个汉字人名，去掉"教授"等头衔；不要编造。
                日程文本：
                """.replaceAll("\\n{3,}", "\n\n") + text.substring(0, Math.min(text.length(), 4000));
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "format", "json",
                    "keep_alive", "60m"));
            long timeoutSec = 180;
            try {
                timeoutSec = Long.parseLong(System.getenv().getOrDefault("OLLAMA_TIMEOUT", "180"));
            } catch (Exception ignored) {}
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
     * 启动预热（Owner 2026-11-07）：系统上线后自动把模型加载进显存/内存，
     * 避免首次简历解析承担冷加载耗时。异步执行，失败仅记日志。
     * keep_alive 60m：预热后模型驻留 60 分钟，期间解析请求秒级响应。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        if (!available()) return;
        Thread warm = new Thread(() -> {
            String url = System.getenv("OLLAMA_URL").replaceAll("/+$", "");
            String model = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5:7b");
            try {
                String body = json.writeValueAsString(Map.of(
                        "model", model,
                        "prompt", "预热",
                        "stream", false,
                        "keep_alive", "60m"));
                HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/generate"))
                        .timeout(Duration.ofSeconds(180))
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
