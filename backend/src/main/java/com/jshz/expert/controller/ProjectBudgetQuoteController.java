package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.entity.ProjectBudgetQuote;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ProjectBudgetQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 会议预算报价版本（仅存档查看，2026-11-26 审查整改）：
 * 手动 generate / customer-confirmation / restore 端点已随「导出报价单即对客报价」终局决策下线，
 * 归档仅由确认报价（save confirm=true）自动触发；本控制器保留 list/file 供历史版本查看与下载。
 */
@RestController
@RequestMapping("/api/project/{id}/meeting-budget/quotes")
@RequiredArgsConstructor
public class ProjectBudgetQuoteController {
    private final ProjectBudgetQuoteService service;

    @GetMapping
    public ApiResponse<List<ProjectBudgetQuote>> list(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.list(id, user));
    }

    @GetMapping("/{versionId}/file")
    public ResponseEntity<FileSystemResource> file(@PathVariable Long id, @PathVariable Long versionId,
            @AuthenticationPrincipal AuthUser user) {
        var file = service.download(id, versionId, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new FileSystemResource(file.path()));
    }
}
