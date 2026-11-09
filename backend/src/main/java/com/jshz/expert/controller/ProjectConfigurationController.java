package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import org.springframework.web.bind.annotation.*;

/**
 * 项目类型/阶段流程配置（配置 v2，PC-20261104）。
 * 2026-11-09 项目流程重塑：项目类型概念删除、类型决定阶段流程的配置体系整体停用，
 * 全部端点返回 410（表保留观察，下版物理删除）；前端配置页已同步移除。
 */
@RestController
@RequestMapping("/api/project")
public class ProjectConfigurationController {

    private ApiResponse<Object> retired() {
        throw new BusinessException(410, "项目类型/阶段流程配置已停用（2026-11-09 项目流程重塑）：类型概念已删除，项目固定走新流程");
    }

    @GetMapping("/config/types/{id}/definition")
    public ApiResponse<Object> edit(@PathVariable long id) { return retired(); }

    @GetMapping("/config/definition-context")
    public ApiResponse<Object> context() { return retired(); }

    @PostMapping("/config/definitions/preview")
    public ApiResponse<Object> preview() { return retired(); }

    @PostMapping("/config/definitions")
    public ApiResponse<Object> create() { return retired(); }

    @PutMapping("/config/types/{id}/definition")
    public ApiResponse<Object> save(@PathVariable long id) { return retired(); }

    @PutMapping("/config/types/order")
    public ApiResponse<Object> order() { return retired(); }

    @GetMapping("/{id}/configuration")
    public ApiResponse<Object> snapshot(@PathVariable long id) { return retired(); }
}
