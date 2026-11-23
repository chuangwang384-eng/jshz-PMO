package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.entity.ComplianceItem;
import com.jshz.expert.entity.ComplianceItemHistory;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 企业合规事项控制器（REQ/DES-20261102，/api/compliance/*）。
 * 权限（ADR-004/011/014/020）：全局编辑类需 knowledge:maintain；个人维度接口强制本人；
 * 维护人查看责任人进度只读；不做数据越权放行。
 */
@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
public class ComplianceItemController {

    private final ComplianceService complianceService;

    private boolean isMaintainer(AuthUser user) {
        return user != null && user.hasPermission("knowledge:maintain");
    }

    private void requireMaintainer(AuthUser user) {
        if (!isMaintainer(user)) {
            throw new com.jshz.expert.common.BusinessException("该操作需要维护人权限（knowledge:maintain）");
        }
    }

    /** 全局事项列表（登录可读；维护人可看草稿）。 */
    @GetMapping("/items")
    public ApiResponse<Map<String, Object>> listItems(@RequestParam(required = false) Long documentId,
                                                      @RequestParam(required = false) String stage,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String priority,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "false") boolean includeDraft,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "50") long size,
                                                      @AuthenticationPrincipal AuthUser user) {
        boolean draft = includeDraft && isMaintainer(user);
        return ApiResponse.ok(complianceService.listItems(documentId, stage, status, priority, keyword, draft, page, size));
    }

    /** 手动新增事项（维护人）。 */
    @PostMapping("/items")
    public ApiResponse<ComplianceItem> createItem(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        return ApiResponse.ok(complianceService.createItem(body, user));
    }

    /** 编辑事项（维护人全字段；全局状态仅维护人可改）。 */
    @PutMapping("/items/{id}")
    public ApiResponse<ComplianceItem> updateItem(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                  @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        return ApiResponse.ok(complianceService.updateItem(id, body, user));
    }

    /** 删除事项（维护人）。 */
    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        complianceService.deleteItem(id, user);
        return ApiResponse.ok(null);
    }

    @GetMapping("/documents/{id}/reset-preview")
    public ApiResponse<Map<String, Object>> resetPreview(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        return ApiResponse.ok(complianceService.resetPreview(id));
    }

    /** 候选事项清空预览（维护人）。 */
    @GetMapping("/documents/{id}/drafts-clear-preview")
    public ApiResponse<Map<String, Object>> clearDraftsPreview(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        return ApiResponse.ok(complianceService.clearDraftsPreview(id));
    }

    /** 清空候选事项（维护人）：一次性删除该文档全部草稿候选。 */
    @PostMapping("/documents/{id}/drafts-clear")
    public ApiResponse<Map<String, Object>> clearDrafts(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        return ApiResponse.ok(complianceService.clearDrafts(id,
                com.jshz.expert.common.ParamUtils.parseLong(body.get("expectedCount"), null), user));
    }

    @PostMapping("/documents/{id}/reparse")
    public ApiResponse<Map<String, Object>> reparse(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                    @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        return ApiResponse.ok(complianceService.reparse(id, Boolean.TRUE.equals(body.get("reset")),
                com.jshz.expert.common.ParamUtils.parseLong(body.get("expectedCount"), null), user));
    }

    @PostMapping("/items/{id}/withdraw")
    public ApiResponse<Void> withdrawItem(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        complianceService.withdrawItem(id, user);
        return ApiResponse.ok(null);
    }

    /** 批量确认草稿发布（维护人）。 */
    @PostMapping("/items/confirm")
    public ApiResponse<Map<String, Object>> confirmDrafts(@RequestBody Map<String, Object> body,
                                                          @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        Long documentId = com.jshz.expert.common.ParamUtils.parseLong(body.get("documentId"), null);
        if (documentId == null) {
            throw new com.jshz.expert.common.BusinessException("documentId 不能为空");
        }
        @SuppressWarnings("unchecked")
        List<Long> itemIds = (List<Long>) body.get("itemIds");
        return ApiResponse.ok(complianceService.confirmDrafts(documentId, itemIds, user));
    }

    /** 跨版本匹配确认（维护人）。 */
    @PostMapping("/items/{itemId}/match-confirm")
    public ApiResponse<Void> confirmMatch(@PathVariable Long itemId, @RequestBody Map<String, Object> body,
                                          @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        Long oldItemId = com.jshz.expert.common.ParamUtils.parseLong(body.get("oldItemId"), null);
        if (oldItemId == null) {
            throw new com.jshz.expert.common.BusinessException("oldItemId 不能为空");
        }
        complianceService.confirmMatch(itemId, oldItemId, user);
        return ApiResponse.ok(null);
    }

    /** 事项修改历史（登录）。 */
    @GetMapping("/items/{itemId}/history")
    public ApiResponse<List<ComplianceItemHistory>> history(@PathVariable Long itemId) {
        return ApiResponse.ok(complianceService.historyOf(itemId));
    }

    /** 我的待办（收藏 ∪ 责任人；本人）。 */
    @GetMapping("/my-todo")
    public ApiResponse<Map<String, Object>> myTodo(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            throw new com.jshz.expert.common.BusinessException(401, "未登录");
        }
        return ApiResponse.ok(complianceService.myTodo(user.getUserId()));
    }

    /** 个人进度更新（本人）。 */
    @PutMapping("/my-progress/{itemId}")
    public ApiResponse<Map<String, Object>> updateMyProgress(@PathVariable Long itemId,
                                                             @RequestBody Map<String, Object> body,
                                                             @AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            throw new com.jshz.expert.common.BusinessException(401, "未登录");
        }
        String progress = body.get("progressStatus") == null ? null : String.valueOf(body.get("progressStatus"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        String block = body.get("blockReason") == null ? null : String.valueOf(body.get("blockReason"));
        return ApiResponse.ok(complianceService.updateMyProgress(user.getUserId(), itemId, progress, remark, block));
    }

    /** 提醒设置（本人）。 */
    @PutMapping("/my-reminder/{itemId}")
    public ApiResponse<Map<String, Object>> updateMyReminder(@PathVariable Long itemId,
                                                             @RequestBody Map<String, Object> body,
                                                             @AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            throw new com.jshz.expert.common.BusinessException(401, "未登录");
        }
        Integer enabled = com.jshz.expert.common.ParamUtils.parseLong(body.get("remindEnabled"), null) == null
                ? null : com.jshz.expert.common.ParamUtils.parseLong(body.get("remindEnabled"), null).intValue();
        Integer days = com.jshz.expert.common.ParamUtils.parseLong(body.get("remindDays"), null) == null
                ? null : com.jshz.expert.common.ParamUtils.parseLong(body.get("remindDays"), null).intValue();
        return ApiResponse.ok(complianceService.updateMyReminder(user.getUserId(), itemId, enabled, days));
    }

    /** 某事项全部责任人个人进度（维护人只读）。 */
    @GetMapping("/items/{itemId}/assignee-progress")
    public ApiResponse<List<Map<String, Object>>> assigneeProgress(@PathVariable Long itemId,
                                                                   @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        return ApiResponse.ok(complianceService.assigneeProgress(itemId));
    }

    /** 某责任人全部事项个人进度聚合（维护人只读）。 */
    @GetMapping("/users/{userId}/progress")
    public ApiResponse<List<Map<String, Object>>> userProgress(@PathVariable Long userId,
                                                               @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        return ApiResponse.ok(complianceService.userProgress(userId));
    }

    /**
     * 提醒记录查询（维护人，幂等核对）。需求21b：服务端分页 + 可选时间范围，
     * 不再一次性返回某事项全部记录；时间按计划提醒时间（scheduled_time）过滤。
     */
    @GetMapping("/reminder-logs")
    public ApiResponse<Map<String, Object>> reminderLogs(
            @RequestParam Long itemId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        return ApiResponse.ok(complianceService.reminderLogs(itemId, from, to, page, size));
    }
}
