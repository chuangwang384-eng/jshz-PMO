package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.dto.DepartmentNodeVo;
import com.jshz.expert.dto.UnboundDataVo;
import com.jshz.expert.dto.WecomBindRequest;
import com.jshz.expert.dto.WecomSyncRequest;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.entity.WecomSyncLog;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ContactSyncService;
import com.jshz.expert.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企微通讯录同步管理接口（2026-10-27 评审通过，权限 wecom:sync）。
 * POST /sync（dryRun=true 试算）→ 返回 logId；GET /sync/status 轮询；
 * GET /departments 部门树；GET /unbound 未绑定清单；POST /users/{id}/bind-wecom 人工指认。
 * 手动同步 / 人工绑定写入 operation_log 审计。
 */
@RestController
@RequestMapping("/api/admin/wecom")
@RequiredArgsConstructor
public class WecomContactController {

    private final ContactSyncService contactSyncService;
    private final OperationLogService operationLogService;

    /**
     * 触发同步（异步执行，立即返回 logId；dryRun=true 只试算变更清单，不落业务数据）。
     */
    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> sync(@RequestBody(required = false) WecomSyncRequest request,
                                                 @AuthenticationPrincipal AuthUser authUser,
                                                 HttpServletRequest httpRequest) {
        boolean dryRun = request != null && Boolean.TRUE.equals(request.getDryRun());
        String operator = authUser == null ? "system" : authUser.getUsername();
        Long logId = contactSyncService.start(dryRun ? "DRYRUN" : "MANUAL", operator, dryRun);
        operationLogService.record(authUser == null ? null : authUser.getUserId(), operator,
                OperationLogService.ACTION_WECOM_SYNC,
                (dryRun ? "企微同步试算(DryRun)启动" : "企微同步实跑启动") + "，logId=" + logId,
                "WECOM", String.valueOf(logId), httpRequest);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logId", logId);
        data.put("dryRun", dryRun);
        return ApiResponse.ok(data);
    }

    /**
     * 同步状态：传入 logId 查指定记录；不传返回最近一次（含运行中进度）。
     */
    @GetMapping("/sync/status")
    public ApiResponse<WecomSyncLog> syncStatus(@RequestParam(required = false) Long logId) {
        return ApiResponse.ok(contactSyncService.getSyncStatus(logId));
    }

    /**
     * 最近同步记录（管理页表格数据源）。
     */
    @GetMapping("/sync/recent")
    public ApiResponse<List<WecomSyncLog>> recent(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(contactSyncService.recentLogs(limit));
    }

    /**
     * 组织架构树（部门节点带主部门成员数与成员卡）。
     */
    @GetMapping("/departments")
    public ApiResponse<List<DepartmentNodeVo>> departments() {
        return ApiResponse.ok(contactSyncService.getDepartmentTree());
    }

    /**
     * 未绑定清单：系统未绑定存量用户 + 企微侧未匹配成员（人工指认数据源）。
     */
    @GetMapping("/unbound")
    public ApiResponse<UnboundDataVo> unbound() {
        return ApiResponse.ok(contactSyncService.getUnboundList());
    }

    /**
     * 管理员人工指认绑定（重名/改名场景兜底）。
     */
    @PostMapping("/users/{id}/bind-wecom")
    public ApiResponse<SysUser> bindWecom(@PathVariable Long id,
                                          @RequestBody WecomBindRequest request,
                                          @AuthenticationPrincipal AuthUser authUser,
                                          HttpServletRequest httpRequest) {
        SysUser user = contactSyncService.bindWecom(id, request.getWecomUserId());
        String operator = authUser == null ? "system" : authUser.getUsername();
        operationLogService.record(authUser == null ? null : authUser.getUserId(), operator,
                OperationLogService.ACTION_WECOM_BIND,
                "人工指认绑定：用户id=" + id + "，wecomUserId=" + request.getWecomUserId(),
                "USER", String.valueOf(id), httpRequest);
        return ApiResponse.ok(user);
    }
}
