package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Pure dashboard projection rules; never change the project's lifecycle. */
public final class DashboardRules {
    private DashboardRules() {}

    public static String completion(String origin, Map<String,Object> config, long phaseId) {
        if (!"LEGACY_BASELINE".equals(origin)) return "UNKNOWN";
        List<Map<String,Object>> phases = ProjectConfigurationService.rows(config.get("phases"));
        List<Map<String,Object>> completed = phases.stream().filter(p -> "已完成".equals(p.get("name"))).toList();
        if (completed.size() != 1 || phases.stream().noneMatch(p -> Objects.equals(String.valueOf(p.get("phaseId")), Long.toString(phaseId)))) return "UNKNOWN";
        return Objects.equals(String.valueOf(completed.get(0).get("phaseId")), Long.toString(phaseId)) ? "COMPLETED" : "INCOMPLETE";
    }

    public static String warning(String completion, LocalDate end, LocalDate today) {
        if ("UNKNOWN".equals(completion)) return "UNKNOWN_COMPLETION";
        if (!"INCOMPLETE".equals(completion) || end == null) return "NONE";
        long d = ChronoUnit.DAYS.between(today, end);
        return d < 0 ? "OVERDUE" : d == 0 ? "DUE_TODAY" : d <= 7 ? "DUE_SOON" : "NONE";
    }

    public static String keyword(String raw) {
        String value = raw == null ? "" : raw.strip();
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > 100) throw new BusinessException(400, "关键词须为1–100个字符");
        return value;
    }

    // SQL LIKE uses ! as ESCAPE, so backslash is a literal, independent of SQL mode.
    public static String literalLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public static List<String> select(String raw, List<String> allowed) {
        if (raw == null) return allowed;
        List<String> values = Arrays.asList(raw.split(",", -1));
        if (values.isEmpty() || values.size() > allowed.size() || new HashSet<>(values).size() != values.size() || !allowed.containsAll(values))
            throw new BusinessException(400, "模块参数无效");
        return allowed.stream().filter(values::contains).toList();
    }

    public static Map<String,Object> target(String path, Map<String,?> query) {
        return Map.of("path", path, "query", query);
    }
}
