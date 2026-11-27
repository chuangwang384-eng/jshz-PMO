package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.entity.ProjectBudgetFeeStandard;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ProjectBudgetExportService;
import com.jshz.expert.service.ProjectBudgetService;
import com.jshz.expert.service.ProjectBudgetQuoteService;
import com.jshz.expert.service.OperationLogService;
import org.springframework.transaction.annotation.Transactional;
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
 * 收费标准端点（/api/project/config/budget-fee-standards*）自 2026-11-26 起按精确路径
 * 绑定 project:manage（见 database/migration/20261126_budget_fee_permission.sql），
 * config/phases、config/types 字典读取保持登录即可（20261029 迁移的放开规则不受影响）。
 */
@RestController
@RequestMapping("/api/project")
@RequiredArgsConstructor
public class ProjectBudgetController {

    private final ProjectBudgetService budgetService;
    private final ProjectBudgetExportService exportService;
    private final ProjectBudgetQuoteService quoteService;
    private final ObjectMapper objectMapper;
    private final OperationLogService operationLogService;

    /** 预算单总览（不存在返回骨架草稿，不落库）。 */
    @GetMapping("/{id}/meeting-budget")
    public ApiResponse<Map<String, Object>> overview(@PathVariable Long id,
                                                     @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(budgetService.overview(id, user));
    }

    /** 整单保存（confirm=false 草稿 / true 确认报价）。body.version 为乐观锁版本号（加载时 budget.version）。 */
    @Transactional
    @PutMapping("/{id}/meeting-budget")
    public ApiResponse<Map<String, Object>> save(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> body,
                                                 @AuthenticationPrincipal AuthUser user) {
        Object items = body == null ? null : body.get("items");
        List<Map<String, Object>> list = mapList(items, "明细");
        String remark = body == null || body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        boolean confirm = body != null && Boolean.parseBoolean(String.valueOf(body.get("confirm")));
        Integer expectedVersion = null;
        if (body != null && body.get("version") instanceof Number n) {
            expectedVersion = n.intValue();
        }
        String estimateInputs = null;
        if (body != null && body.get("estimateInputs") != null) {
            if (!(body.get("estimateInputs") instanceof Map<?, ?>)) {
                throw new BusinessException("估算参数格式不正确");
            }
            estimateInputs = objectMapper.valueToTree(body.get("estimateInputs")).toString();
            if (estimateInputs.length() > 1000) {
                // P3-24（2026-11-26）：报错带具体字数，前端保存前亦有预检
                throw new BusinessException("估算参数过长（当前 " + estimateInputs.length() + " 字符，上限 1000）");
            }
        }
        var budget = budgetService.save(id, list, remark, confirm, estimateInputs, expectedVersion, user);
        if (confirm) {
            warnIfNotArchived(id, user, budget);
        }
        return ApiResponse.ok(budget);
    }

    /**
     * 确认已保存的报价草稿（幂等修复用途，无 UI 入口：前端确认走 save(confirm=true)，2026-11-26 注记）。
     */
    @Transactional
    @PostMapping("/{id}/meeting-budget/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthUser user) {
        var budget = budgetService.confirm(id, user);
        warnIfNotArchived(id, user, budget);
        return ApiResponse.ok(budget);
    }

    /**
     * P3-23（2026-11-26）：非预算阶段重确认时不归档新报价——写操作日志并在响应中带 warn，前端提示。
     */
    private void warnIfNotArchived(Long id, AuthUser user, Map<String, Object> budget) {
        boolean archived = quoteService.archiveConfirmed(id, user, exportService);
        if (!archived) {
            String warn = "本次确认未归档新报价（项目已不在「会议预算制作」阶段），项目资料中仍是旧版报价";
            budget.put("warn", warn);
            operationLogService.record(user == null ? null : user.getUserId(),
                    user == null ? null : user.getUsername(), OperationLogService.ACTION_BUDGET_CONFIRM,
                    warn + "，project=" + id, "PROJECT", id, null);
        }
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
        List<Map<String, Object>> list = mapList(rows, "收费标准");
        return ApiResponse.ok(budgetService.saveFeeStandards(list, user));
    }

    /** 删除收费项（2026-11-20）：被预算明细引用时 400 并提示条数。 */
    @DeleteMapping("/config/budget-fee-standards/{feeKey}")
    public ApiResponse<Void> deleteFeeStandard(@PathVariable String feeKey,
                                               @AuthenticationPrincipal AuthUser user) {
        budgetService.deleteFeeStandard(feeKey, user);
        return ApiResponse.ok(null);
    }

    /**
     * 请求体中 List 元素到 Map 的类型窄化（P2-⑤ 2026-11-26 口径对齐：非 List 或元素非 Map 抛业务异常，
     * 不再静默丢弃——与 estimateInputs 的严格校验一致）。
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object raw, String label) {
        if (!(raw instanceof List<?> l)) {
            throw new BusinessException(label + "格式不正确（应为数组）");
        }
        return l.stream().map(o -> {
            if (!(o instanceof Map)) {
                throw new BusinessException(label + "格式不正确（存在非对象元素）");
            }
            return (Map<String, Object>) o;
        }).toList();
    }
}
