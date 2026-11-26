package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class MetaController {

    @Value("${expert.upload.photo-max-bytes:10485760}")
    private long photoMaxBytes;

    @Value("${expert.upload.resume-max-bytes}")
    private long resumeMaxBytes;

    @Value("${expert.upload.doc-max-bytes:104857600}")
    private long docMaxBytes;

    /** 结算资料单批次合计上限（2026-11-25 取消 20 个文件限制，仅保留可配置总量）。 */
    @Value("${expert.upload.settlement-batch-max-bytes:524288000}")
    private long settlementBatchMaxBytes;

    private final com.jshz.expert.service.SupplierImageService supplierImageService;

    /**
     * 上传/图片限额（P3-3 2026-11-26）：供应商图片三项直取 SupplierImageService 公共常量，
     * 与校验逻辑同源，杜绝前后端字面量漂移。
     */
    @GetMapping("/limits")
    public ApiResponse<Map<String, Long>> limits() {
        Map<String, Long> data = new LinkedHashMap<>();
        data.put("photoMaxBytes", photoMaxBytes);
        data.put("resumeMaxBytes", resumeMaxBytes);
        data.put("docMaxBytes", docMaxBytes);
        data.put("settlementBatchMaxBytes", settlementBatchMaxBytes);
        data.put("supplierMaxImages", (long) com.jshz.expert.service.SupplierImageService.MAX_IMAGES);
        data.put("supplierMaxSingleBytes", com.jshz.expert.service.SupplierStorageService.MAX_SINGLE_SIZE);
        data.put("supplierMaxTotalBytes", com.jshz.expert.service.SupplierImageService.MAX_TOTAL_SIZE);
        return ApiResponse.ok(data);
    }
}
