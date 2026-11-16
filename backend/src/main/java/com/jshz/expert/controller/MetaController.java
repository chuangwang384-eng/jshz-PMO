package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前端元信息接口（方案 D，2026-11-13）。
 *
 * <p>上传限额单一真源：数值唯一定义在 application.yml expert.upload，
 * 前端（frontend/src/utils/limits.js）启动后经 GET /api/meta/limits 拉取做预校验，
 * 消除前后端各自硬编码字面量导致的口径漂移。</p>
 *
 * <p>权限：不在白名单内，登录即可访问（SecurityConfig anyRequest().authenticated()）。</p>
 */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    @Value("${expert.upload.photo-max-bytes:5242880}")
    private long photoMaxBytes;

    @Value("${expert.upload.resume-max-bytes:20971520}")
    private long resumeMaxBytes;

    @Value("${expert.upload.doc-max-bytes:52428800}")
    private long docMaxBytes;

    @GetMapping("/limits")
    public ApiResponse<Map<String, Long>> limits() {
        Map<String, Long> data = new LinkedHashMap<>();
        data.put("photoMaxBytes", photoMaxBytes);
        data.put("resumeMaxBytes", resumeMaxBytes);
        data.put("docMaxBytes", docMaxBytes);
        return ApiResponse.ok(data);
    }
}
