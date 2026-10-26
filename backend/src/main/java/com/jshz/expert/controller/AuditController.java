package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.DownloadLog;
import com.jshz.expert.entity.OperationLog;
import com.jshz.expert.service.DownloadAuditService;
import com.jshz.expert.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 审计接口（正式版 V1.001，仅 ADMIN）。
 * - GET /api/audit/downloads：历史下载留痕（保留兼容，数据仍在 download_log 双写）；
 * - GET /api/audit/operations：通用用户操作日志（用户操作日志改版，支持用户名模糊 / 操作类型 / 时间范围过滤）。
 * 两接口均由 permission 表 audit:view（GET /api/audit/**）控制。
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DownloadAuditService downloadAuditService;
    private final OperationLogService operationLogService;

    /**
     * GET /api/audit/downloads?expertId=&username=&page=&size=
     */
    @GetMapping("/downloads")
    public ApiResponse<PageResult<DownloadLog>> downloads(@RequestParam(required = false) Long expertId,
                                                          @RequestParam(required = false) String username,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size) {
        Page<DownloadLog> result = downloadAuditService.pageQuery(expertId, username, (int) page, (int) size);
        return ApiResponse.ok(PageResult.of(result));
    }

    /**
     * GET /api/audit/operations?username=&actionType=&startTime=&endTime=&page=&size=
     * startTime/endTime 支持 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd；
     * endTime 为纯日期时取当日 23:59:59（边界含当日）。
     */
    @GetMapping("/operations")
    public ApiResponse<PageResult<OperationLog>> operations(@RequestParam(required = false) String username,
                                                            @RequestParam(required = false) String actionType,
                                                            @RequestParam(required = false) String startTime,
                                                            @RequestParam(required = false) String endTime,
                                                            @RequestParam(defaultValue = "1") long page,
                                                            @RequestParam(defaultValue = "20") long size) {
        LocalDateTime start = parseTime(startTime, false);
        LocalDateTime end = parseTime(endTime, true);
        Page<OperationLog> result = operationLogService.pageQuery(
                username, actionType, start, end, (int) page, (int) size);
        return ApiResponse.ok(PageResult.of(result));
    }

    /**
     * 时间参数解析：支持日期（yyyy-MM-dd）与日期时间（yyyy-MM-dd HH:mm:ss）两种格式；
     * endOnly 为真且入参为纯日期时返回当日 23:59:59（闭区间右边界），纯日期开始则取 00:00:00。
     */
    private LocalDateTime parseTime(String value, boolean endOnly) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
                return endOnly ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(trimmed, DATETIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BusinessException("时间格式不正确（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）");
        }
    }
}
