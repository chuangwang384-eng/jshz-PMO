package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。
 *
 * <p>version 由项目根 version.txt 唯一源注入（经 version.properties 进入 classpath）；
 * buildTime 与 gitShort 来自 spring-boot-maven-plugin 的 build-info 产物
 * （META-INF/build-info.properties，构建期生成），用于验收时一眼确认「跑的是哪个构建」，
 * 无需再反编译 jar 比对。</p>
 *
 * <p>两项构建信息在未经脚本构建（如 IDE 直接运行）时可能缺失，此时统一返回 unknown，
 * 不抛异常、不影响存活探测语义。</p>
 */
@RestController
public class HealthController {

    private static final String UNKNOWN = "unknown";
    private static final DateTimeFormatter BUILD_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final BuildProperties buildProperties;

    @Value("${app.version:unknown}")
    private String appVersion;

    public HealthController(ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("time", LocalDateTime.now());
        data.put("version", sanitize(appVersion));
        data.put("buildTime", buildTime());
        data.put("gitShort", buildProperty("gitShort"));
        return ApiResponse.ok(data);
    }

    @GetMapping("/ready")
    public ApiResponse<Map<String, Object>> ready() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ready", true);
        data.put("time", LocalDateTime.now());
        return ApiResponse.ok(data);
    }

    /** 构建时间；缺失时返回 unknown。 */
    private String buildTime() {
        if (buildProperties == null || buildProperties.getTime() == null) {
            return UNKNOWN;
        }
        return BUILD_TIME_FORMAT.format(buildProperties.getTime());
    }

    /** 读取 build-info 中的自定义构建属性；缺失或未被 Maven 过滤替换时返回 unknown。 */
    private String buildProperty(String key) {
        if (buildProperties == null) {
            return UNKNOWN;
        }
        String value = buildProperties.get(key);
        if (value == null || value.isBlank() || value.contains("${")) {
            return UNKNOWN;
        }
        return value.trim();
    }

    /** 过滤未被 Maven 替换的占位符，避免把 ${revision} 直接暴露给调用方。 */
    private String sanitize(String value) {
        if (value == null || value.isBlank() || value.contains("${")) {
            return UNKNOWN;
        }
        return value.trim();
    }
}
