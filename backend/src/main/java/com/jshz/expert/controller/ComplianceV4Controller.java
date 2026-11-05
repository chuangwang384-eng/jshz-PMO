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
    private final com.jshz.expert.service.ComplianceRolloutService rollout;
    private final com.jshz.expert.service.ComplianceLegacyService legacy;
    private final com.jshz.expert.service.KnowledgeDocStorageService docStorage;
    private final com.jshz.expert.service.KnowledgeDocTextExtractor textExtractor;
    private final com.jshz.expert.mapper.KnowledgeDocumentMapper documentMapper;

    @GetMapping("/mode")
    public ApiResponse<Map<String,Object>> mode(@AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ApiResponse.ok(Map.of("enabled", rollout.isEnabled(), "mode", rollout.isV4(user.getUserId()) ? "V4" : "V3"));
    }
    @PostMapping("/mode/enable")
    public ApiResponse<Void> enable(@RequestBody Map<String,Object> body, @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        if (!Boolean.TRUE.equals(body.get("confirmed"))) throw new BusinessException("请确认旧版将转为只读历史");
        rollout.enable(user.getUserId()); return ApiResponse.ok(null);
    }
    @GetMapping("/legacy-history")
    public ApiResponse<List<Map<String,Object>>> legacyHistory(@AuthenticationPrincipal AuthUser user) {
        requireUser(user); return ApiResponse.ok(legacy.history(user.getUserId()));
    }
    @GetMapping("/documents/{documentId}/drafts")
    public ApiResponse<List<Map<String,Object>>> drafts(@PathVariable Long documentId, @AuthenticationPrincipal AuthUser user) {
        requireUser(user); return ApiResponse.ok(complianceV4Service.listDrafts(documentId, user));
    }

    @PostMapping("/documents/{documentId}/templates/generate-draft")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ApiResponse<Map<String,Object>> generateDraft(@PathVariable Long documentId, @AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        // Match the template service lock order; the document lock serializes
        // generation across users and application instances until commit.
        rollout.lockUser(user.getUserId());
        var doc = documentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jshz.expert.entity.KnowledgeDocument>()
                .eq(com.jshz.expert.entity.KnowledgeDocument::getId, documentId).last("FOR UPDATE"));
        if (doc == null || !"enterprise-compliance".equals(doc.getCategory()) || !Integer.valueOf(1).equals(doc.getStatus()))
            throw new BusinessException(404, "来源文档不可用");
        if (!user.hasPermission("knowledge:maintain") && !user.getUsername().equals(doc.getUploadBy()))
            throw new BusinessException(403, "仅上传者或维护人可生成规范草稿");
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(docStorage.resolveDocument(doc.getFilePath()));
            var extracted = textExtractor.extractStructured(bytes, doc.getFileName());
            if (extracted.blocks().isEmpty()) throw new BusinessException("未提取到可确认内容，请核对文件格式");
            List<Map<String,Object>> nodes = new java.util.ArrayList<>();
            int index = 0;
            for (var block : extracted.blocks()) {
                for (String line : block.text().split("\\R")) {
                    String candidate = line.trim();
                    if (candidate.length() < 8 || candidate.length() > 500 || candidate.matches("^[\\d\\s.、，；;：:()（）-]+$")) continue;
                    Map<String,Object> node = new java.util.LinkedHashMap<>();
                    node.put("nodeCode", "extract-" + (++index));
                    node.put("title", candidate.length() > 120 ? candidate.substring(0, 120) : candidate);
                    node.put("inputType", "CHECK"); node.put("required", false);
                    node.put("acceptance", candidate);
                    node.put("sourceAnchor", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("anchor", block.anchor(), "excerpt", candidate, "kind", block.kind(), "needsReview", true)).toString());
                    nodes.add(node);
                    if (nodes.size() >= 200) break;
                }
                if (nodes.size() >= 200) break;
            }
            if (nodes.isEmpty()) throw new BusinessException("未识别到候选条目，请人工核对原文");
            String draftTitle = doc.getTitle() + "（第" + doc.getVersion() + "版解析待确认）";
            for (Map<String,Object> existing : complianceV4Service.listDrafts(documentId, user)) {
                if (draftTitle.equals(existing.get("title"))) {
                    Map<String,Object> reused = complianceV4Service.templateDetail(((Number) existing.get("id")).longValue(), user);
                    reused.put("extractionIncomplete", extracted.incomplete()); reused.put("extractionWarning", "该文档版本已有解析草稿，已为你打开现有草稿"); reused.put("candidateCount", nodes.size());
                    return ApiResponse.ok(reused);
                }
            }
            Map<String,Object> draft = complianceV4Service.createDraft(documentId, null, draftTitle, user);
            Map<String,Object> patch = new java.util.LinkedHashMap<>(); patch.put("rowVersion", draft.get("rowVersion")); patch.put("nodes", nodes);
            Map<String,Object> saved = complianceV4Service.patchTemplate(((Number)draft.get("id")).longValue(), patch, user);
            saved.put("extractionIncomplete", extracted.incomplete()); saved.put("extractionWarning", extracted.warning()); saved.put("candidateCount", nodes.size());
            return ApiResponse.ok(saved);
        } catch (java.io.IOException e) { throw new BusinessException("读取来源文件失败，请重新上传"); }
    }

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
    public ApiResponse<List<Map<String, Object>>> listTemplates(@PathVariable Long documentId, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(complianceV4Service.listPublishedTemplates(documentId, user));
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
        return ApiResponse.ok(complianceV4Service.listMyExecutions(user, state, projectId, documentId, search, page, size));
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
