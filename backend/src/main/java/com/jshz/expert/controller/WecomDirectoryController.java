package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ContactSyncService;
import com.jshz.expert.service.ProfileOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 普通员工通讯录接口（个人中心-通讯录页）。
 * 登录即可访问（DbPermissionFilter 未命中权限规则 → 放行），
 * 仅返回本人"根到本部门"链路与本部门成员（含停用成员，status 原样返回；手机号脱敏），
 * 不暴露全量组织架构。2026-11-25：响应新增 truncated 标记（上级部门缺失/父指针成环时为 true）。
 * 2026-11-25 热修复：企微侧无部门数据（未绑定/未同步）时回退读取手动组织树
 * （profile_membership 链路 + 同部门成员，source=profile），避免"明明有部门和岗位却看到未绑定空态"。
 */
@RestController
@RequestMapping("/api/wecom/directory")
@RequiredArgsConstructor
public class WecomDirectoryController {

    private final ContactSyncService contactSyncService;
    private final ProfileOverviewService profileOverviewService;

    @GetMapping("/mine")
    public ApiResponse<Map<String, Object>> mine(@AuthenticationPrincipal AuthUser authUser) {
        Long userId = authUser == null ? null : authUser.getUserId();
        Map<String, Object> result = contactSyncService.getMyDirectory(userId);
        result.put("source", "wecom");
        List<?> ancestors = (List<?>) result.get("ancestors");
        List<?> members = (List<?>) result.get("members");
        boolean wecomEmpty = (ancestors == null || ancestors.isEmpty()) && (members == null || members.isEmpty());
        if (userId != null && wecomEmpty) {
            Map<String, Object> fallback = profileOverviewService.directoryFallback(userId);
            List<?> fa = (List<?>) fallback.get("ancestors");
            List<?> fm = (List<?>) fallback.get("members");
            if ((fa != null && !fa.isEmpty()) || (fm != null && !fm.isEmpty())) {
                return ApiResponse.ok(fallback);
            }
        }
        return ApiResponse.ok(result);
    }
}
