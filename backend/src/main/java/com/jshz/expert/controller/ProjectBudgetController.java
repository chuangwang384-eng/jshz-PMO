package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.entity.ProjectBudgetFeeStandard;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ProjectBudgetExportService;
import com.jshz.expert.service.ProjectBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
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
    private final ProjectBudgetExportService exportService;
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
        List<Map<String, Object>> list = mapList(items);
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

    /**
     * 导出报价单/成本单 xlsx（2026-11-20，历史结算单共性结构）。
     * docType=QUOTE（缺省）报价单 / COST 成本单。
     */
    @GetMapping("/{id}/meeting-budget/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @RequestParam(defaultValue = "QUOTE") String docType,
                                         @AuthenticationPrincipal AuthUser user) throws IOException {
        byte[] bytes = exportService.export(id, docType, user);
        String filename = java.net.URLEncoder.encode(
                ("COST".equalsIgnoreCase(docType) ? "会议成本单" : "会议报价单") + ".xlsx",
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
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
        List<Map<String, Object>> list = mapList(rows);
        return ApiResponse.ok(budgetService.saveFeeStandards(list, user));
    }

    /** 删除收费项（2026-11-20）：被预算明细引用时 400 并提示条数。 */
    @DeleteMapping("/config/budget-fee-standards/{feeKey}")
    public ApiResponse<Void> deleteFeeStandard(@PathVariable String feeKey,
                                               @AuthenticationPrincipal AuthUser user) {
        budgetService.deleteFeeStandard(feeKey, user);
        return ApiResponse.ok(null);
    }

    /** 请求体中 List 元素到 Map 的类型窄化（save/saveFeeStandards 共用，非 List 或元素非 Map 一律丢弃）。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object raw) {
        return raw instanceof List<?> l
                ? l.stream().filter(o -> o instanceof Map).map(o -> (Map<String, Object>) o).toList()
                : List.of();
    }
}
