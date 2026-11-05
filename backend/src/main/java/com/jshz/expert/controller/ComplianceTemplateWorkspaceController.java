package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ComplianceTemplateWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/compliance/v4/documents/{documentId}/template-workspace")
public class ComplianceTemplateWorkspaceController {
    private final ComplianceTemplateWorkspaceService workspace;
    private final com.jshz.expert.service.ComplianceWorkspaceSourceService sources;
    @GetMapping
    public ApiResponse<Map<String,Object>> open(@PathVariable long documentId,@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(workspace.open(documentId,user));
    }
    @PutMapping
    public ApiResponse<Map<String,Object>> save(@PathVariable long documentId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(workspace.save(documentId,body,user));
    }
    @GetMapping("/legacy-candidates")
    public ApiResponse<java.util.List<Map<String,Object>>> candidates(@PathVariable long documentId,@AuthenticationPrincipal AuthUser user) { return ApiResponse.ok(workspace.candidates(documentId,user)); }
    @PostMapping("/select-existing")
    public ApiResponse<Map<String,Object>> select(@PathVariable long documentId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user) { return ApiResponse.ok(workspace.select(documentId,body,user)); }
    @PostMapping("/activate")
    public ApiResponse<Map<String,Object>> activate(@PathVariable long documentId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user) { return ApiResponse.ok(workspace.activate(documentId,body,user)); }
    @PostMapping("/extract")
    public ApiResponse<Map<String,Object>> extract(@PathVariable long documentId,@AuthenticationPrincipal AuthUser user){return ApiResponse.ok(sources.extract(documentId,user));}
    @PostMapping("/merge-candidates")
    public ApiResponse<Map<String,Object>> merge(@PathVariable long documentId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ApiResponse.ok(workspace.merge(documentId,body,user));}
    @PostMapping("/review-node")
    public ApiResponse<Map<String,Object>> review(@PathVariable long documentId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ApiResponse.ok(workspace.review(documentId,body,user));}
    @PostMapping("/confirm-extraction")
    public ApiResponse<Map<String,Object>> confirm(@PathVariable long documentId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user){return ApiResponse.ok(workspace.confirmExtraction(documentId,body,user));}
    @GetMapping("/extractions/{extractionId}")
    public ApiResponse<Map<String,Object>> extraction(@PathVariable long documentId,@PathVariable String extractionId,@AuthenticationPrincipal AuthUser user){return ApiResponse.ok(sources.extraction(documentId,extractionId,user));}
}
