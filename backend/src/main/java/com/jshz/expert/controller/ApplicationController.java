package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.ApplicationType;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.ApprovalConfig;
import com.jshz.expert.entity.Application;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ApplicationService;
import com.jshz.expert.common.ParamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * - GET /api/approval/my-applications：我的申请（登录即可，仅本人数据；支持状态/类型/关键词/日期筛选）；
 *   （旧兼容路径 /my-requests 已于 2026-11-24 下线：前端零调用，仅 my-applications 一个入口）
 * - GET /api/approval/requests：审批管理全部申请（权限码 approval:audit）；
 * - POST /api/approval/requests/{id}/approve|reject|withdraw|transfer|add-approver|return|resubmit；
 * - DELETE /api/approval/requests/{id}：申请人删除本人已驳回/已撤回的申请单（2026-11-24 新增）；
 * - GET /api/approval/assignable-users：可转办/加签用户列表。
 */
@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    /**
     * 审批流配置：GET /api/approval/config?flowKey=（登录即可；缺省默认 EXPERT）。
     * 返回 {flowKey, flowName, enabled, approverRole}。
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(@RequestParam(required = false) String flowKey) {
        ApprovalConfig config = applicationService.getConfig(flowKey);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flowKey", config.getFlowKey());
        data.put("flowName", config.getFlowName());
        data.put("enabled", config.getEnabled());
        data.put("approverRole", config.getApproverRole());
        return ApiResponse.ok(data);
    }

    /**
     * 审批流配置列表（多流程）：GET /api/approval/config/list（登录即可）。
     * 返回各流程 {flowKey, flowName, enabled, approverRole, users}。
     */
    @GetMapping("/config/list")
    public ApiResponse<List<Map<String, Object>>> configList() {
        return ApiResponse.ok(applicationService.listConfigs());
    }

    /**
     * 更新审批流配置：PUT /api/approval/config（仅 ADMIN）。
     * body {flowKey?, enabled, approverRole}，flowKey 缺省默认 EXPERT。
     */
    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(@RequestBody(required = false) Map<String, Object> body) {
        String flowKey = body == null ? null : (String) body.get("flowKey");
        Integer enabled = body == null ? null : ParamUtils.toInteger(body.get("enabled"));
        String approverRole = body == null ? null : (String) body.get("approverRole");
        ApprovalConfig config = applicationService.updateConfig(flowKey, enabled, approverRole);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flowKey", config.getFlowKey());
        data.put("flowName", config.getFlowName());
        data.put("enabled", config.getEnabled());
        data.put("approverRole", config.getApproverRole());
        return ApiResponse.ok(data);
    }

    /**
     * 提交修改申请：POST /api/approval/requests（登录即可，管理员拒绝）。
     * body {expertId, changes: {中文键: 新值}, reason}。
     */
    @PostMapping("/requests")
    public ApiResponse<Application> submit(@RequestBody(required = false) Map<String, Object> body,
                                           @AuthenticationPrincipal AuthUser authUser) {
        if (body == null) {
            throw new com.jshz.expert.common.BusinessException("请求体不能为空");
        }
        Long expertId = ParamUtils.toLong(body.get("expertId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> changes = (Map<String, Object>) body.get("changes");
        String reason = (String) body.get("reason");
        return ApiResponse.ok(applicationService.submitRequest(expertId, changes, reason, authUser));
    }

    /**
     * 提交新增专家申请：POST /api/approval/create-request（登录即可，管理员拒绝）。
     * body {changes: {中文键: 新值}, reason}，expertId 无需传。
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
        return ApiResponse.ok(applicationService.submitCreateRequest(changes, reason, authUser));
    }

    /**
     * 提交专家合同审批：POST /api/approval/contract-request（项目管理模块结算合同）。
     * body {contractId, projectId, changes: {身份证号, 银行卡号}, reason}；
     * 使用 CONTRACT 流程配置（approval_config flow_key='CONTRACT'），通过后合同生效。
     */
    @PostMapping("/contract-request")
    public ApiResponse<Application> contractRequest(@RequestBody(required = false) Map<String, Object> body,
                                                    @AuthenticationPrincipal AuthUser authUser) {
        if (body == null) {
            throw new com.jshz.expert.common.BusinessException("请求体不能为空");
        }
        Long contractId = ParamUtils.toLong(body.get("contractId"));
        Long projectId = ParamUtils.toLong(body.get("projectId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> changes = (Map<String, Object>) body.get("changes");
        String reason = (String) body.get("reason");
        return ApiResponse.ok(applicationService.submitContractApproval(contractId, projectId, changes, reason, authUser));
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
     * 我的申请（新接口）：GET /api/approval/my-applications?page=&size=&status=&type=&keyword=&startDate=&endDate=
     * （登录即可）。
     * 仅返回本人申请（applicant_id/applicant 双条件过滤），含审批人/审批时间/审批意见/进度记录/
     * 当前处理人与待审批停留时长（R2）。
     *
     * <p>2026-11-24（Q2）：page/size 在此钳制为 page≥1、1≤size≤100。MyBatis-Plus 分页插件在
     * {@code size<0} 且未设 maxLimit 时不构造分页 SQL（等于返回全表），必须由入口收敛。
     * status 只接受 0–4，type 只接受 ApplicationType 枚举名，日期为 yyyy-MM-dd（R1）。</p>
     */
    @GetMapping("/my-applications")
    public ApiResponse<PageResult<Application>> myApplications(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal AuthUser authUser) {
        if (status != null && (status < 0 || status > 4)) {
            throw new BusinessException(400, "状态筛选值不合法（应为 0-4）");
        }
        ApplicationService.MyApplicationFilter filter = new ApplicationService.MyApplicationFilter(
                status, normalizeType(type), keyword, startDate, endDate);
        Page<Application> p = applicationService.myApplications(
                ParamUtils.clamp(page, 1, Long.MAX_VALUE), ParamUtils.clamp(size, 1, ApplicationService.MAX_PAGE_SIZE),
                filter, authUser);
        return ApiResponse.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 申请类型参数归一化：只接受枚举名（大小写不敏感），非法值按 400 拒绝而不是静默忽略。 */
    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String value = type.trim().toUpperCase(java.util.Locale.ROOT);
        for (ApplicationType candidate : ApplicationType.values()) {
            if (candidate.name().equals(value)) {
                return candidate.name();
            }
        }
        throw new BusinessException(400, "申请类型筛选值不合法");
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
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser authUser) {
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
        Page<Application> p = applicationService.listRequests(statusList, keyword,
                ParamUtils.clamp(page, 1, Long.MAX_VALUE), ParamUtils.clamp(size, 1, ApplicationService.MAX_PAGE_SIZE),
                authUser);
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
     * 删除申请单：DELETE /api/approval/requests/{id}（仅申请人本人，status∈{2 已驳回, 3 已撤回}）。
     * 同事务删除进度记录并写 operation_log（DELETE_APPLICATION）留痕；权限沿用「登录即可 + Service 本人校验」，
     * 与 withdraw / resubmit 一致，不新增权限码。
     */
    @DeleteMapping("/requests/{id}")
    public ApiResponse<Void> deleteRequest(@PathVariable Long id,
                                           @AuthenticationPrincipal AuthUser authUser) {
        applicationService.deleteApplication(id, authUser);
        return ApiResponse.ok(null);
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
     * 审批退回修改：POST /api/approval/requests/{id}/return（权限码 approval:audit）。
     * body {comment 必填}；status 0→4，保留临时照片，写 RETURN 记录。
     */
    @PostMapping("/requests/{id}/return")
    public ApiResponse<Application> returnRequest(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body,
                                                  @AuthenticationPrincipal AuthUser authUser) {
        String comment = body == null ? null : (String) body.get("comment");
        return ApiResponse.ok(applicationService.returnRequest(id, comment, authUser));
    }

    /**
     * 重新提交：POST /api/approval/requests/{id}/resubmit（仅申请人本人，status=4 可重提）。
     * body {changes: {中文键: 新值}, reason?}；changes 可含照片字段（TMP: 临时文件名，退回修改后可重传，2026-11-24 放开）。
     * 值必须为字符串或 null（2026-11-24，Q10：此前任意 JSON 类型都会被 String.valueOf 写进专家字段）。
     * status 4→0，写 RESUBMIT 记录，重置转办/加签并刷新审批人快照；CONTRACT 单据同时把合同置回在审。
     */
    @PostMapping("/requests/{id}/resubmit")
    public ApiResponse<Application> resubmit(@PathVariable Long id,
                                             @RequestBody(required=false) Map<String,Object> body,
                                             @AuthenticationPrincipal AuthUser authUser) {
        Map<String,Object> patch = null;
        String reason = null;
        if(body != null) {
            Object changes = body.get("changes");
            if(!(changes instanceof Map<?,?> raw)) throw new BusinessException(400,"changes须为字段对象（文本为字符串，照片为 TMP: 临时文件名）");
            patch = new LinkedHashMap<>();
            for(var entry:raw.entrySet()) {
                if(!(entry.getKey() instanceof String key)) throw new BusinessException(400,"字段名称无效");
                Object value = entry.getValue();
                if (value != null && !(value instanceof String)) throw new BusinessException(400,"字段「" + key + "」的值必须为文本");
                patch.put(key,value);
            }
            Object value=body.get("reason");
            if(value!=null && !(value instanceof String)) throw new BusinessException(400,"申请原因格式无效");
            reason=(String)value;
        }
        return ApiResponse.ok(applicationService.resubmit(id,patch,reason,authUser));
    }

    /**
     * 查询流程具体审批人：GET /api/approval/config/{flowKey}/users（登录即可）。
     * 返回 [{id, username}]。
     */
    @GetMapping("/config/{flowKey}/users")
    public ApiResponse<List<Map<String, Object>>> configUsers(@PathVariable String flowKey) {
        return ApiResponse.ok(applicationService.listConfigUsers(flowKey));
    }

    /**
     * 保存流程具体审批人：PUT /api/approval/config/{flowKey}/users（权限码 approval:config）。
     * body {usernames: ["u1", "u2"]}，全量覆盖。
     */
    @PutMapping("/config/{flowKey}/users")
    public ApiResponse<Void> saveConfigUsers(@PathVariable String flowKey,
                                             @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> usernames = body == null ? null : (List<String>) body.get("usernames");
        applicationService.saveConfigUsers(flowKey, usernames);
        return ApiResponse.ok(null);
    }

    /**
     * 可转办/加签用户列表：GET /api/approval/assignable-users（权限码 approval:audit）。
     * 返回启用且拥有 approval:audit 权限码的用户 [{id, username}]。
     */
    @GetMapping("/assignable-users")
    public ApiResponse<List<Map<String, Object>>> assignableUsers() {
        return ApiResponse.ok(applicationService.assignableUsers());
    }


}
