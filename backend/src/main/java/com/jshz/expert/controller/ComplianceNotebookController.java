package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.entity.ComplianceProjectTodo;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ComplianceNotebookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 企业合规记事本（Owner 2026-11-10）：员工=收藏∩已发布∩上架 的记事本、个人隐藏/恢复、
 * 批量推送参与项目、项目待办处理；维护人=按文档撤下（预览计数+版本校验）。
 * 权限：读=登录；隐藏/推送/处理=本人真实参与或本人收藏口径；撤下=knowledge:maintain。
 */
@RestController
@RequestMapping("/api/compliance/notebook")
public class ComplianceNotebookController {

    @Autowired
    private ComplianceNotebookService notebookService;

    /** 记事本查询（收藏文档分组；view=hidden 返回已隐藏条目）。 */
    @GetMapping("/items")
    public ApiResponse<List<Map<String, Object>>> myItems(@RequestParam(required = false) Long documentId,
                                                          @RequestParam(defaultValue = "all") String view,
                                                          @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(notebookService.myItems(user, documentId, view));
    }

    /** 隐藏一条（仅影响本人记事本）。 */
    @PutMapping("/items/{itemId}/hide")
    public ApiResponse<Void> hide(@PathVariable Long itemId, @AuthenticationPrincipal AuthUser user) {
        notebookService.hide(itemId, user);
        return ApiResponse.ok(null);
    }

    /** 恢复显示（幂等）。 */
    @PutMapping("/items/{itemId}/unhide")
    public ApiResponse<Void> unhide(@PathVariable Long itemId, @AuthenticationPrincipal AuthUser user) {
        notebookService.unhide(itemId, user);
        return ApiResponse.ok(null);
    }

    /** 本人真实参与且文档绑定兼容的项目（推送选择器）。 */
    @GetMapping("/pushable-projects")
    public ApiResponse<List<Map<String, Object>>> pushableProjects(@RequestParam Long documentId,
                                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(notebookService.pushableProjects(documentId, user));
    }

    /** 批量推送（同一文档；首次绑定、快照去重；批次失败整批回滚）。 */
    @PostMapping("/push")
    public ApiResponse<Map<String, Object>> push(@RequestBody Map<String, Object> body,
                                                 @AuthenticationPrincipal AuthUser user) {
        Long projectId = body.get("projectId") == null ? null
                : Long.valueOf(String.valueOf(body.get("projectId")));
        Object ids = body.get("itemIds");
        List<Long> itemIds = ids instanceof List<?> l
                ? l.stream().filter(o -> o != null).map(o -> Long.valueOf(String.valueOf(o))).distinct().toList()
                : List.of();
        return ApiResponse.ok(notebookService.push(projectId, itemIds, user));
    }

    /** 项目合规待办列表（项目可读）。 */
    @GetMapping("/projects/{projectId}/todos")
    public ApiResponse<List<ComplianceProjectTodo>> projectTodos(@PathVariable Long projectId,
                                                                 @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(notebookService.projectTodos(projectId, user));
    }

    /** 处理项目待办（真实参与人；row_version 乐观锁）。 */
    @PutMapping("/todos/{todoId}")
    public ApiResponse<Void> handleTodo(@PathVariable Long todoId, @RequestBody Map<String, Object> body,
                                        @AuthenticationPrincipal AuthUser user) {
        String status = body.get("status") == null ? null : String.valueOf(body.get("status"));
        Integer rowVersion = body.get("rowVersion") == null ? null
                : Integer.valueOf(String.valueOf(body.get("rowVersion")));
        notebookService.handleTodo(todoId, status, rowVersion, user);
        return ApiResponse.ok(null);
    }

    // ==================== 维护端（knowledge:maintain） ====================

    /** 撤下预览：文档标题 + 全量已发布条目实际数量。 */
    @PostMapping("/documents/{documentId}/withdraw-preview")
    public ApiResponse<Map<String, Object>> withdrawPreview(@PathVariable Long documentId,
                                                            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(notebookService.withdrawPreview(documentId, user));
    }

    /** 撤下提交：expectedCount 与当前全量不符时冲突返回。 */
    @PostMapping("/documents/{documentId}/withdraw")
    public ApiResponse<Map<String, Object>> withdraw(@PathVariable Long documentId,
                                                     @RequestBody Map<String, Object> body,
                                                     @AuthenticationPrincipal AuthUser user) {
        Integer expectedCount = body.get("expectedCount") == null ? null
                : Integer.valueOf(String.valueOf(body.get("expectedCount")));
        return ApiResponse.ok(notebookService.withdraw(documentId, expectedCount, user));
    }
}
