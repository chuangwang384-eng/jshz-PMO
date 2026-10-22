package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查（正式版 V1.001）。
 * /health 存活探测、/ready 就绪探测（部署探针）。
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("time", LocalDateTime.now());
        return ApiResponse.ok(data);
    }

    @GetMapping("/ready")
    public ApiResponse<Map<String, Object>> ready() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ready", true);
        data.put("time", LocalDateTime.now());
        return ApiResponse.ok(data);
    }
}
