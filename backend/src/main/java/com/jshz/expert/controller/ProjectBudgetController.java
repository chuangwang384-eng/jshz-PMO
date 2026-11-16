package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
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
        return ApiResponse.ok(budgetService.save(id, list, remark, confirm, user));
    }

    /** 确认报价（等同 confirm=true 的保存，供已填草稿直接确认）。 */
    @PostMapping("/{id}/meeting-budget/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthUser user) {
        Map<String, Object> current = budgetService.overview(id, user);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = ((List<?>) current.get("items")).stream()
                .filter(o -> o instanceof Map).map(o -> {
                    Map<?, ?> m = (Map<?, ?>) o;
                    Map<String, Object> r = new java.util.LinkedHashMap<>();
                    m.forEach((k, v) -> r.put(String.valueOf(k), v));
                    return r;
                }).toList();
        Object budget = current.get("budget");
        String remark = budget instanceof Map<?, ?> b && b.get("remark") != null ? String.valueOf(b.get("remark")) : null;
        return ApiResponse.ok(budgetService.save(id, items, remark, true, user));
    }

    // ==================== 收费标准（管理员设置-项目配置） ====================

    @GetMapping("/config/budget-fee-standards")
    public ApiResponse<List<ProjectBudgetFeeStandard>> listFeeStandards() {
        return ApiResponse.ok(budgetService.listFeeStandards());
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
