package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.entity.ProjectBudgetFeeStandard;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ProjectBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会议预算制作（2026-11-16 新阶段）。
 * 权限同 ProjectController：数据驱动（DbPermissionFilter），写操作绑定 project:manage；
 * 收费标准端点同为 project:manage（管理员设置-项目配置页）。
 */
@RestController
@RequestMapping("/api/project")
@RequiredArgsConstructor
public class ProjectBudgetController {

    private final ProjectBudgetService budgetService;
    private final ObjectMapper objectMapper;

    /** 预算单总览（不存在返回骨架草稿，不落库）。 */
    @GetMapping("/{id}/meeting-budget")
    public ApiResponse<Map<String, Object>> overview(@PathVariable Long id,
                                                     @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(budgetService.overview(id, user));
    }

    /** 整单保存（confirm=false 草稿 / true 确认报价）。 */
    @PutMapping("/{id}/meeting-budget")
    public ApiResponse<Map<String, Object>> save(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> body,
                                                 @AuthenticationPrincipal AuthUser user) {
        Object items = body == null ? null : body.get("items");
        List<Map<String, Object>> list = items instanceof List<?> l
                ? l.stream().filter(o -> o instanceof Map).map(o -> (Map<String, Object>) o).toList()
                : List.of();
        String remark = body == null || body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        boolean confirm = body != null && Boolean.parseBoolean(String.valueOf(body.get("confirm")));
        String estimateInputs = null;
        if (body != null && body.get("estimateInputs") != null) {
            if (!(body.get("estimateInputs") instanceof Map<?, ?>)) {
                throw new BusinessException("估算参数格式不正确");
            }
            estimateInputs = objectMapper.valueToTree(body.get("estimateInputs")).toString();
            if (estimateInputs.length() > 1000) {
                throw new BusinessException("估算参数过长");
            }
        }
        return ApiResponse.ok(budgetService.save(id, list, remark, confirm, estimateInputs, user));
    }

    /** 确认已保存的报价草稿。 */
    @PostMapping("/{id}/meeting-budget/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(budgetService.confirm(id, user));
    }

    // ==================== 收费标准（管理员设置-项目配置） ====================

    /** scene 可选（ONLINE/OFFLINE），缺省全量。 */
    @GetMapping("/config/budget-fee-standards")
    public ApiResponse<List<ProjectBudgetFeeStandard>> listFeeStandards(
            @RequestParam(required = false) String scene) {
        return ApiResponse.ok(budgetService.listFeeStandards(scene));
    }

    @PutMapping("/config/budget-fee-standards")
    public ApiResponse<List<ProjectBudgetFeeStandard>> saveFeeStandards(
            @RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        Object rows = body == null ? null : body.get("rows");
        List<Map<String, Object>> list = rows instanceof List<?> l
                ? l.stream().filter(o -> o instanceof Map).map(o -> (Map<String, Object>) o).toList()
                : List.of();
        return ApiResponse.ok(budgetService.saveFeeStandards(list, user));
    }
}
