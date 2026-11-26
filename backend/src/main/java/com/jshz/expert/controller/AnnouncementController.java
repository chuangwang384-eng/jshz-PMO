package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.dto.AnnouncementForm;
import com.jshz.expert.entity.Announcement;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.AnnouncementService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.Map;

/**
 * 系统公告接口：/api/announcement
 * 列表/详情/标记已读登录即可；创建/编辑/删除/上下架/置顶/已读统计需 announcement:manage
 * （DbPermissionFilter 按 method+path 规则拦截，Service 层业务校验兜底）。
 */
@RestController
@RequestMapping("/api/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** 分页列表：管理视角含已读进度；员工视角仅 ONLINE；scope=effective 追加生效中过滤 */
    @GetMapping
    public ApiResponse<PageResult<Announcement>> pageQuery(@AuthenticationPrincipal AuthUser user,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "10") int size,
                                                           @RequestParam(required = false) String scope) {
        return ApiResponse.ok(announcementService.pageQuery(user, keyword, status, page, size, scope));
    }

    /** 当前用户生效中公告未读数（登录即可，与 effectiveList 同一生效口径） */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal AuthUser user) {
        if (user == null || user.getUserId() == null) {
            return ApiResponse.ok(0L);
        }
        return ApiResponse.ok(announcementService.unreadCount(user.getUserId()));
    }

    /** 当前用户把全部生效中公告标记已读（幂等：差集插入 announcement_read） */
    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal AuthUser user) {
        if (user != null && user.getUserId() != null) {
            announcementService.markAllEffectiveRead(user.getUserId());
        }
        return ApiResponse.ok();
    }

    /** 详情 */
    @GetMapping("/{id}")
    public ApiResponse<Announcement> detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ApiResponse.ok(announcementService.detail(user, id));
    }

    /** 创建（announcement:manage） */
    @PostMapping
    public ApiResponse<Announcement> create(@AuthenticationPrincipal AuthUser user,
                                            @RequestBody AnnouncementForm form,
                                            HttpServletRequest request) {
        return ApiResponse.ok(announcementService.create(user, form, request));
    }

    /** 编辑（announcement:manage） */
    @PutMapping("/{id}")
    public ApiResponse<Announcement> update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                            @RequestBody AnnouncementForm form,
                                            HttpServletRequest request) {
        return ApiResponse.ok(announcementService.update(user, id, form, request));
    }

    /** 删除（announcement:manage） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    HttpServletRequest request) {
        announcementService.delete(user, id, request);
        return ApiResponse.ok();
    }

    /** 上架/下架（announcement:manage），status=ONLINE|OFFLINE */
    @PutMapping("/{id}/status")
    public ApiResponse<Announcement> changeStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                                  @RequestParam String status, HttpServletRequest request) {
        return ApiResponse.ok(announcementService.changeStatus(user, id, status, request));
    }

    /** 置顶/取消置顶（announcement:manage），top=true|false */
    @PutMapping("/{id}/top")
    public ApiResponse<Announcement> toggleTop(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                               @RequestParam boolean top, HttpServletRequest request) {
        return ApiResponse.ok(announcementService.toggleTop(user, id, top, request));
    }

    /** 置顶组内上移/下移（需求四 2026-11-26，announcement:manage），dir=up|down */
    @PostMapping("/{id}/move")
    public ApiResponse<Announcement> moveTop(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                             @RequestParam String dir, HttpServletRequest request) {
        return ApiResponse.ok(announcementService.moveTop(user, id, dir, request));
    }

    /** 标记已读（登录即可，幂等，不入操作日志） */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        announcementService.markRead(user, id);
        return ApiResponse.ok();
    }

    /** 已读进度与已读/未读名单（announcement:manage） */
    @GetMapping("/{id}/read-stats")
    public ApiResponse<Map<String, Object>> readStats(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ApiResponse.ok(announcementService.readStats(user, id));
    }
}
