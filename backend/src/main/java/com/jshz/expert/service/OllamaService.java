package com.jshz.expert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                    "format", "json"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/api/generate"))
                    .timeout(Duration.ofSeconds(60))
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
}
