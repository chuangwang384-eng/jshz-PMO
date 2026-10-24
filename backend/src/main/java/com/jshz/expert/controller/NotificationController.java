package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.Notification;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用消息通知接口（消息通知中心）。登录即可。
 * 列表 / 未读数 / 单条已读 / 全部已读。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 本人消息分页列表：GET /api/notifications?page=&pageSize=。登录即可。
     */
    @GetMapping
    public ApiResponse<PageResult<Notification>> list(@AuthenticationPrincipal AuthUser authUser,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long pageSize) {
        Long userId = requireUserId(authUser);
        Page<Notification> p = notificationService.pageByUser(userId, page, pageSize);
        return ApiResponse.ok(PageResult.of(p));
    }

    /**
     * 本人未读消息数：GET /api/notifications/unread-count。登录即可。
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal AuthUser authUser) {
        Long userId = requireUserId(authUser);
        return ApiResponse.ok(notificationService.unreadCount(userId));
    }

    /**
     * 单条标记已读：POST /api/notifications/{id}/read。登录即可。
     */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal AuthUser authUser,
                                  @PathVariable Long id) {
        Long userId = requireUserId(authUser);
        notificationService.markRead(userId, id);
        return ApiResponse.ok();
    }

    /**
     * 全部标记已读：POST /api/notifications/read-all。登录即可。
     */
    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal AuthUser authUser) {
        Long userId = requireUserId(authUser);
        notificationService.markAllRead(userId);
        return ApiResponse.ok();
    }

    private Long requireUserId(AuthUser authUser) {
        if (authUser == null || authUser.getUserId() == null) {
            throw new BusinessException("未登录");
        }
        return authUser.getUserId();
    }
}
