package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.OperationLogService;
import com.jshz.expert.service.PermissionService;
import com.jshz.expert.service.ProfileOverviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profile/overview")
@RequiredArgsConstructor
public class ProfileOverviewController {
    private final ProfileOverviewService service;
    private final OperationLogService logs;

    @GetMapping("/organization")
    public ApiResponse<ProfileOverviewService.Organization> organization(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.organization(user.getUserId()));
    }

    @GetMapping("/activity")
    public ApiResponse<ProfileOverviewService.ProfilePage<ProfileOverviewService.Activity>> activity(
            @AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "1") int page) {
        return ApiResponse.ok(service.activity(user.getUserId(), page));
    }

    @GetMapping("/departments")
    public ApiResponse<List<ProfileOverviewService.Department>> departments(@AuthenticationPrincipal AuthUser user) {
        requireOrganizationAdmin(user);
        return ApiResponse.ok(service.departments());
    }

    @PostMapping("/departments")
    public ApiResponse<Void> createDepartment(@AuthenticationPrincipal AuthUser user,
            @RequestBody ProfileOverviewService.DepartmentForm form, HttpServletRequest request) {
        requireOrganizationAdmin(user);
        String note = service.saveDepartment(null, form);
        logs.record(user.getUserId(), user.getUsername(), "PROFILE_ORG_DEPARTMENT",
                "新增组织部门「" + form.name() + "」" + (note == null ? "" : "，" + note),
                "PROFILE_DEPARTMENT", form.name(), request);
        return ApiResponse.ok();
    }

    @PutMapping("/departments/{id}")
    public ApiResponse<Void> updateDepartment(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
            @RequestBody ProfileOverviewService.DepartmentForm form, HttpServletRequest request) {
        requireOrganizationAdmin(user);
        String note = service.saveDepartment(id, form);
        logs.record(user.getUserId(), user.getUsername(), "PROFILE_ORG_DEPARTMENT",
                "更新组织部门「" + form.name() + "」" + (note == null ? "" : "，" + note),
                "PROFILE_DEPARTMENT", id, request);
        return ApiResponse.ok();
    }

    /** 成员列表：超管与总经理看全部；部门负责人仅看本人负责部门及其下级，其余账号 403。 */
    @GetMapping("/members")
    public ApiResponse<ProfileOverviewService.ProfilePage<ProfileOverviewService.Member>> members(
            @AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "1") int page) {
        return ApiResponse.ok(service.members(isOrganizationAdmin(user), user.getUserId(), keyword, page, departmentId));
    }

    @PutMapping("/members/{id}")
    public ApiResponse<Void> membership(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
            @RequestBody ProfileOverviewService.Membership form, HttpServletRequest request) {
        requireOrganizationAdmin(user);
        service.saveMembership(id, form);
        logs.record(user.getUserId(), user.getUsername(), "PROFILE_ORG_MEMBERSHIP", "调整账号部门及岗位", ProfileOverviewService.USER_ACCOUNT, id, request);
        return ApiResponse.ok();
    }

    private static boolean isOrganizationAdmin(AuthUser user) {
        return user != null && (user.getRoles().contains(PermissionService.SUPER_ADMIN_CODE)
                || user.getRoles().contains(PermissionService.GENERAL_MANAGER_CODE));
    }

    private static void requireOrganizationAdmin(AuthUser user) {
        if (!isOrganizationAdmin(user)) {
            throw new BusinessException(403, "仅超级管理员或总经理可维护组织架构");
        }
    }
}
