package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.ApprovalConfig;
import com.jshz.expert.entity.Application;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用申请接口（「我的申请」改版，由 ApprovalController 演化）。
 * URL 沿用 /api/approval/*（permission 表路径规则与前端 api/approval.js 不变），业务读写 application 主表。
 * - GET/PUT /api/approval/config：审批流配置（PUT 仅 ADMIN）；
 * - POST /api/approval/requests：员工提交专家修改申请（EXPERT_MODIFY）；
 * - POST /api/approval/create-request：员工提交新增专家申请（EXPERT_CREATE）；
 * - GET /api/approval/my-applications：我的申请（新接口，登录即可，仅本人数据）；
 * - GET /api/approval/my-requests：旧路径兼容（同实现）；
 * - GET /api/approval/requests：审批管理全部申请（权限码 approval:audit）；
 * - POST /api/approval/requests/{id}/approve|reject|withdraw|transfer|add-approver；
 * - GET /api/approval/assignable-users：可转办/加签用户列表。
 */
@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    /**
     * 审批流配置：GET /api/approval/config（登录即可）。
     * 返回 {flowName, enabled, approverRole}。
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        ApprovalConfig config = applicationService.getConfig();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flowName", config.getFlowName());
        data.put("enabled", config.getEnabled());
        data.put("approverRole", config.getApproverRole());
        return ApiResponse.ok(data);
    }

    /**
     * 更新审批流配置：PUT /api/approval/config（仅 ADMIN）。
     * body {enabled, approverRole}。
     */
    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(@RequestBody(required = false) Map<String, Object> body) {
        Integer enabled = body == null ? null : toInteger(body.get("enabled"));
        String approverRole = body == null ? null : (String) body.get("approverRole");
        ApprovalConfig config = applicationService.updateConfig(enabled, approverRole);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flowName", config.getFlowName());
        data.put("enabled", config.getEnabled());
        data.put("approverRole", config.getApproverRole());
        return ApiResponse.ok(data);
    }

    /**
     * 提交修改申请：POST /api/approval/requests（登录即可，管理员拒绝）。
     * body {expertId, changes: {中文键: 新值}, reason, templateId 可选}。
     */
    @PostMapping("/requests")
    public ApiResponse<Application> submit(@RequestBody(required = false) Map<String, Object> body,
                                           @AuthenticationPrincipal AuthUser authUser) {
        if (body == null) {
            throw new com.jshz.expert.common.BusinessException("请求体不能为空");
        }
        Long expertId = toLong(body.get("expertId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> changes = (Map<String, Object>) body.get("changes");
        String reason = (String) body.get("reason");
        Long templateId = toLong(body.get("templateId"));
        return ApiResponse.ok(applicationService.submitRequest(expertId, changes, reason, templateId, authUser));
    }

    /**
     * 提交新增专家申请：POST /api/approval/create-request（登录即可，管理员拒绝）。
     * body {changes: {中文键: 新值}, reason, templateId 可选}，expertId 无需传。
     */
    @PostMapping("/create-request")
    public ApiResponse<Application> createRequest(@RequestBody(required = false) Map<String, Object> body,
                                                  @AuthenticationPrincipal AuthUser authUser) {
        if (body == null) {
            throw new com.jshz.expert.common.BusinessException("请求体不能为空");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> changes = (Map<String, Object>) body.get("changes");
        String reason = (String) body.get("reason");
        Long templateId = toLong(body.get("templateId"));
        return ApiResponse.ok(applicationService.submitCreateRequest(changes, reason, templateId, authUser));
    }

    /**
     * 临时照片上传：POST /api/approval/photo-upload（登录即可，multipart 字段 file）。
     * 返回 {fileName}，提交申请时以 TMP:fileName 作为照片字段值。
     */
    @PostMapping("/photo-upload")
    public ApiResponse<Map<String, Object>> photoUpload(@RequestParam("file") MultipartFile file) {
        String fileName = applicationService.uploadTempPhoto(file);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileName", fileName);
        return ApiResponse.ok(data);
    }

    /**
     * 我的申请（新接口）：GET /api/approval/my-applications?page=&size=&status=（登录即可）。
     * 仅返回本人申请（applicant_id/applicant 双条件过滤），含审批人/审批时间/审批意见/进度记录。
     */
    @GetMapping("/my-applications")
    public ApiResponse<PageResult<Application>> myApplications(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Integer status,
            @AuthenticationPrincipal AuthUser authUser) {
        Page<Application> p = applicationService.myApplications(page, size, status, authUser);
        return ApiResponse.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /**
     * 我的申请（旧路径兼容）：GET /api/approval/my-requests，实现与 my-applications 一致。
     */
    @GetMapping("/my-requests")
    public ApiResponse<PageResult<Application>> myRequests(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Integer status,
            @AuthenticationPrincipal AuthUser authUser) {
        Page<Application> p = applicationService.myApplications(page, size, status, authUser);
        return ApiResponse.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /**
     * 全部申请单：GET /api/approval/requests?status=&statuses=1,2&keyword=&page=&size=（权限码 approval:audit）。
     * status：单状态过滤；statuses：多状态过滤（逗号分隔，如 1,2,3 表示已处理），优先于 status。
     */
    @GetMapping("/requests")
    public ApiResponse<PageResult<Application>> listRequests(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String statuses,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        List<Integer> statusList = null;
        if (StringUtils.hasText(statuses)) {
            statusList = new ArrayList<>();
            for (String s : statuses.split(",")) {
                String t = s.trim();
                if (t.isEmpty()) {
                    continue;
                }
                statusList.add(Integer.valueOf(t));
            }
        } else if (status != null) {
            statusList = List.of(status);
        }
        Page<Application> p = applicationService.listRequests(statusList, keyword, page, size);
        return ApiResponse.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /**
     * 审批通过：POST /api/approval/requests/{id}/approve（权限码 approval:audit），body {comment 可空}。
     */
    @PostMapping("/requests/{id}/approve")
    public ApiResponse<Application> approve(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            @AuthenticationPrincipal AuthUser authUser) {
        String comment = body == null ? null : (String) body.get("comment");
        return ApiResponse.ok(applicationService.approve(id, comment, authUser));
    }

    /**
     * 审批驳回：POST /api/approval/requests/{id}/reject（权限码 approval:audit），body {comment 必填}。
     */
    @PostMapping("/requests/{id}/reject")
    public ApiResponse<Application> reject(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, Object> body,
                                           @AuthenticationPrincipal AuthUser authUser) {
        String comment = body == null ? null : (String) body.get("comment");
        return ApiResponse.ok(applicationService.reject(id, comment, authUser));
    }

    /**
     * 撤回申请：POST /api/approval/requests/{id}/withdraw（仅申请人本人，status=0 可撤）。
     */
    @PostMapping("/requests/{id}/withdraw")
    public ApiResponse<Application> withdraw(@PathVariable Long id,
                                             @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(applicationService.withdraw(id, authUser));
    }

    /**
     * 审批转办：POST /api/approval/requests/{id}/transfer（权限码 approval:audit）。
     * body {targetUser}；current_assignee 非空时仅被转办人本人可再转办。
     */
    @PostMapping("/requests/{id}/transfer")
    public ApiResponse<Application> transfer(@PathVariable Long id,
                                             @RequestBody(required = false) Map<String, Object> body,
                                             @AuthenticationPrincipal AuthUser authUser) {
        String targetUser = body == null ? null : (String) body.get("targetUser");
        return ApiResponse.ok(applicationService.transfer(id, targetUser, authUser));
    }

    /**
     * 审批加签（或签）：POST /api/approval/requests/{id}/add-approver（权限码 approval:audit）。
     * body {targetUser, comment?}；已加签不可重复加签。
     */
    @PostMapping("/requests/{id}/add-approver")
    public ApiResponse<Application> addApprover(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, Object> body,
                                                @AuthenticationPrincipal AuthUser authUser) {
        String targetUser = body == null ? null : (String) body.get("targetUser");
        String comment = body == null ? null : (String) body.get("comment");
        return ApiResponse.ok(applicationService.addApprover(id, targetUser, comment, authUser));
    }

    /**
     * 可转办/加签用户列表：GET /api/approval/assignable-users（权限码 approval:audit）。
     * 返回启用且拥有 approval:audit 权限码的用户 [{id, username}]。
     */
    @GetMapping("/assignable-users")
    public ApiResponse<List<Map<String, Object>>> assignableUsers() {
        return ApiResponse.ok(applicationService.assignableUsers());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(String.valueOf(value).trim());
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.valueOf(String.valueOf(value).trim());
    }
}
