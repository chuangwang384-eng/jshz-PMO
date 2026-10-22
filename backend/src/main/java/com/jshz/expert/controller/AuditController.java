package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.DownloadLog;
import com.jshz.expert.service.DownloadAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下载审计接口（正式版 V1.001，仅 ADMIN）。
 * 查询下载留痕：下载人 / 时间 / 类型 / 专家 / IP。
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final DownloadAuditService downloadAuditService;

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
}
