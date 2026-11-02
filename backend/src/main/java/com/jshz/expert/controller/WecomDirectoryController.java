package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ContactSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 普通员工通讯录接口（个人中心-通讯录页）。
 * 登录即可访问（DbPermissionFilter 未命中权限规则 → 放行），
 * 仅返回本人"根到本部门"链路与本部门成员（手机号脱敏），不暴露全量组织架构。
 */
@RestController
@RequestMapping("/api/wecom/directory")
@RequiredArgsConstructor
public class WecomDirectoryController {

    private final ContactSyncService contactSyncService;

    @GetMapping("/mine")
    public ApiResponse<Map<String, Object>> mine(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(contactSyncService.getMyDirectory(authUser == null ? null : authUser.getUserId()));
    }
}
