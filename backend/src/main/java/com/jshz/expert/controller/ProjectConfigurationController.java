package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ProjectConfigurationService;
import com.jshz.expert.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.Map;

@RestController
@RequestMapping("/api/project")
@RequiredArgsConstructor
public class ProjectConfigurationController {
    private final ProjectConfigurationService config;
    private final ProjectService projects;
    @GetMapping("/config/types/{id}/definition")
    public ApiResponse<Map<String,Object>> edit(@PathVariable long id,@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(config.edit(id,user));
    }
    @GetMapping("/config/definition-context")
    public ApiResponse<Map<String,Object>> context(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(config.context(user));
    }
    @PostMapping("/config/definitions/preview")
    public ApiResponse<Map<String,Object>> preview(@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(config.preview(body,user));
    }
    @PostMapping("/config/definitions")
    public ApiResponse<Map<String,Object>> create(@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(config.save(null,body,user));
    }
    @PutMapping("/config/types/{id}/definition")
    public ApiResponse<Map<String,Object>> save(@PathVariable long id,@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(config.save(id,body,user));
    }
    @PutMapping("/config/types/order")
    public ApiResponse<Void> order(@RequestBody Map<String,Object> body,@AuthenticationPrincipal AuthUser user) {
        config.order(body,user);return ApiResponse.ok(null);
    }
    @GetMapping("/{id}/configuration")
    public ApiResponse<Map<String,Object>> snapshot(@PathVariable long id,@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(projects.projectConfiguration(id,user));
    }
}
