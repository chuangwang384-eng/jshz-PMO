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
import java.util.Map;

@RestController
@RequestMapping("/api/project/{id}/meeting-budget/quotes")
@RequiredArgsConstructor
public class ProjectBudgetQuoteController {
    private final ProjectBudgetQuoteService service;

    @GetMapping
    public ApiResponse<List<ProjectBudgetQuote>> list(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.list(id, user));
    }

    @PostMapping
    public ApiResponse<ProjectBudgetQuote> generate(@PathVariable Long id,
            @RequestBody ProjectBudgetQuoteService.Generate request, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.generate(id, request, user));
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

    @PostMapping("/{versionId}/customer-confirmation")
    public ApiResponse<ProjectBudgetQuote> confirm(@PathVariable Long id, @PathVariable Long versionId,
            @RequestBody ProjectBudgetQuoteService.Confirmation request, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.confirm(id, versionId, request, user));
    }

    @PostMapping("/{versionId}/restore")
    public ApiResponse<Map<String, Object>> restore(@PathVariable Long id, @PathVariable Long versionId,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.restore(id, versionId, user));
    }
}
