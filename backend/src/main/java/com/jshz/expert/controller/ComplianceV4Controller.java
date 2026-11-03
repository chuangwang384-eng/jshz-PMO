package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ComplianceV4Service;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 企业合规 v4 控制器（REQ/DES-COMPLIANCE-V4，/api/compliance/v4/*）。
 * 权限边界：执行对象 owner=当前登录用户；模板草稿 owner/维护人；发布需 knowledge:maintain；
 * 不信任请求 body 中的 ownerId/projectId/snapshot。
 */
@RestController
@RequestMapping("/api/compliance/v4")
@RequiredArgsConstructor
public class ComplianceV4Controller {

    private final ComplianceV4Service complianceV4Service;

    private void requireUser(AuthUser user) {
        if (user == null) throw new BusinessException(401, "未登录");
    }

    private void requireMaintainer(AuthUser user) {
        if (user == null || !user.hasPermission("knowledge:maintain")) {
            throw new BusinessException("该操作需要维护人权限（knowledge:maintain）");
        }
    }

    // ==================== 模板 ====================

    /** 某文档已发布模板列表（登录）。 */
    @GetMapping("/documents/{documentId}/templates")
    public ApiResponse<List<Map<String, Object>>> listTemplates(@PathVariable Long documentId) {
        return ApiResponse.ok(complianceV4Service.listPublishedTemplates(documentId));
    }

    /** 上传者创建自己的模板草稿（可从已发布版本复制）。 */
    @PostMapping("/documents/{documentId}/templates/drafts")
    public ApiResponse<Map<String, Object>> createDraft(@PathVariable Long documentId,
                                                        @RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        Long copyFrom = com.jshz.expert.common.ParamUtils.parseLong(body.get("copyFromVersionId"), null);
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        return ApiResponse.ok(complianceV4Service.createDraft(documentId, copyFrom, title, user));
    }

    /** 模板完整草稿树。 */
    @GetMapping("/templates/{templateVersionId}")
    public ApiResponse<Map<String, Object>> templateDetail(@PathVariable Long templateVersionId,
                                                           @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(complianceV4Service.templateDetail(templateVersionId, user));
    }

    /** 编辑草稿（元数据 + 整树替换；rowVersion 乐观锁）。 */
    @PatchMapping("/templates/{templateVersionId}")
    public ApiResponse<Map<String, Object>> patchTemplate(@PathVariable Long templateVersionId,
                                                          @RequestBody Map<String, Object> body,
                                                          @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(complianceV4Service.patchTemplate(templateVersionId, body, user));
    }

    /** 发布（维护人；原子；树校验）。 */
    @PostMapping("/templates/{templateVersionId}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable Long templateVersionId,
                                                    @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        return ApiResponse.ok(complianceV4Service.publish(templateVersionId, user));
    }

    // ==================== 执行清单 ====================

    /** 创建执行清单（幂等键防重复；DRAFT 未确认不入待办）。 */
    @PostMapping("/executions")
    public ApiResponse<Map<String, Object>> createExecution(@RequestBody Map<String, Object> body,
                                                            @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(complianceV4Service.createExecution(body, user));
    }

    /** 草稿确认开始（进入待办并建立提醒）。 */
    @PostMapping("/executions/{id}/start")
    public ApiResponse<Map<String, Object>> startExecution(@PathVariable Long id,
                                                           @RequestBody(required = false) Map<String, Object> body,
                                                           @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(complianceV4Service.startExecution(id, body, user));
    }

    /** 本人清单分页。 */
    @GetMapping("/executions")
    public ApiResponse<Map<String, Object>> listMyExecutions(@RequestParam(required = false) String state,
                                                             @RequestParam(required = false) Long projectId,
                                                             @RequestParam(required = false) Long documentId,
                                                             @RequestParam(required = false) String search,
                                                             @RequestParam(defaultValue = "1") long page,
                                                             @RequestParam(defaultValue = "20") long size,
                                                             @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(complianceV4Service.listMyExecutions(user.getUserId(), state, projectId, documentId, search, page, size));
    }

    /** 清单详情（含任务；来源可用性标注）。 */
    @GetMapping("/executions/{id}")
    public ApiResponse<Map<String, Object>> executionDetail(@PathVariable Long id,
                                                            @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(complianceV4Service.executionDetail(id, user));
    }

    /** 再次使用（新清单；不复制状态/日期/备注/提醒）。 */
    @PostMapping("/executions/{id}/reuse")
    public ApiResponse<Map<String, Object>> reuse(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                  @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(complianceV4Service.reuseExecution(id, body, user));
    }

    /** 暂停（显式；取消未发送提醒）。 */
    @PostMapping("/executions/{id}/pause")
    public ApiResponse<Void> pause(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        complianceV4Service.pauseOrArchive(id, "pause", user);
        return ApiResponse.ok(null);
    }

    /** 归档（显式；取消未发送提醒）。 */
    @PostMapping("/executions/{id}/archive")
    public ApiResponse<Void> archive(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        complianceV4Service.pauseOrArchive(id, "archive", user);
        return ApiResponse.ok(null);
    }

    // ==================== 任务 ====================

    /** 任务更新（rowVersion 乐观锁；answer/dueDate/priority/progress/note/waiting 白名单语义）。 */
    @PatchMapping("/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> patchTask(@PathVariable Long taskId,
                                                      @RequestBody Map<String, Object> body,
                                                      @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(complianceV4Service.patchTask(taskId, body, user));
    }

    /** 我的任务列表（statusScope=ACTIVE/COMPLETED；已完成服务端过滤）。 */
    @GetMapping("/my-tasks")
    public ApiResponse<Map<String, Object>> myTasks(@RequestParam(defaultValue = "ACTIVE") String statusScope,
                                                    @RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) Long executionId,
                                                    @RequestParam(required = false) Long documentId,
                                                    @RequestParam(required = false) String search,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "50") long size,
                                                    @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(complianceV4Service.myTasks(user, statusScope, projectId, executionId, documentId, search, page, size));
    }

    /** 任务私人事件（仅本人）。 */
    @GetMapping("/tasks/{taskId}/events")
    public ApiResponse<List<Map<String, Object>>> taskEvents(@PathVariable Long taskId,
                                                             @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(complianceV4Service.taskEvents(taskId, user));
    }

    // ==================== 维护人迁移辅助 ====================

    /**
     * 从 v3 旧事项生成 v4 模板草稿（迁移辅助，须维护人+人工确认结构化定义后再发布；不做自动数据搬移）。
     * body: { documentId, title? }
     */
    @PostMapping("/migration/templates/from-v3")
    public ApiResponse<Map<String, Object>> createDraftFromV3(@RequestBody Map<String, Object> body,
                                                              @AuthenticationPrincipal AuthUser user) {
        requireMaintainer(user);
        Long documentId = com.jshz.expert.common.ParamUtils.parseLong(body.get("documentId"), null);
        if (documentId == null) throw new BusinessException("documentId 不能为空");
        String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
        return ApiResponse.ok(complianceV4Service.createDraftFromV3Items(documentId, title, user));
    }
}
