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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用消息通知接口（消息中心）。登录即可，所有操作仅作用于本人消息。
 * 列表（支持已读/类型组合筛选）/ 未读数 / 单条已读 / 批量已读 / 全部已读 / 单条删除 / 批量删除（物理删除）。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 本人消息分页列表：GET /api/notifications?page=&pageSize=&isRead=&type=。
     * isRead（0未读/1已读）与 type 可组合过滤，均空=全部（铃铛弹层沿用此形态）。
     */
    @GetMapping
    public ApiResponse<PageResult<Notification>> list(@AuthenticationPrincipal AuthUser authUser,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(required = false) Integer isRead,
                                                      @RequestParam(required = false) String type) {
        Long userId = requireUserId(authUser);
        Page<Notification> p = notificationService.pageByUser(userId, page, pageSize, isRead, type);
        return ApiResponse.ok(PageResult.of(p));
    }

    /**
     * 本人未读消息数：GET /api/notifications/unread-count。
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal AuthUser authUser) {
        Long userId = requireUserId(authUser);
        return ApiResponse.ok(notificationService.unreadCount(userId));
    }

    /**
     * 单条标记已读：POST /api/notifications/{id}/read。
     */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@AuthenticationPrincipal AuthUser authUser,
                                  @PathVariable Long id) {
        Long userId = requireUserId(authUser);
        notificationService.markRead(userId, id);
        return ApiResponse.ok();
    }

    /**
     * 批量标记已读：POST /api/notifications/read-batch，body {ids: []}。
     * 返回实际置为已读的条数（前端据此与未读数核对）。
     */
    @PostMapping("/read-batch")
    public ApiResponse<Integer> readBatch(@AuthenticationPrincipal AuthUser authUser,
                                          @RequestBody(required = false) Map<String, Object> body) {
        Long userId = requireUserId(authUser);
        return ApiResponse.ok(notificationService.markReadBatch(userId, toLongIds(body)));
    }

    /**
     * 全部标记已读：POST /api/notifications/read-all。
     */
    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal AuthUser authUser) {
        Long userId = requireUserId(authUser);
        notificationService.markAllRead(userId);
        return ApiResponse.ok();
    }

    /**
     * 单条删除（物理删除）：DELETE /api/notifications/{id}。仅本人消息可删。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long id) {
        Long userId = requireUserId(authUser);
        notificationService.deleteOne(userId, id);
        return ApiResponse.ok();
    }

    /**
     * 批量删除（物理删除）：DELETE /api/notifications/batch，body {ids: []}。
     * 返回实际删除条数。
     */
    @DeleteMapping("/batch")
    public ApiResponse<Integer> deleteBatch(@AuthenticationPrincipal AuthUser authUser,
                                            @RequestBody(required = false) Map<String, Object> body) {
        Long userId = requireUserId(authUser);
        return ApiResponse.ok(notificationService.deleteBatch(userId, toLongIds(body)));
    }

    private Long requireUserId(AuthUser authUser) {
        if (authUser == null || authUser.getUserId() == null) {
            throw new BusinessException("未登录");
        }
        return authUser.getUserId();
    }

    /**
     * 解析 body.ids（元素可为 Number 或数字字符串），非法值忽略。
     */
    private List<Long> toLongIds(Map<String, Object> body) {
        if (body == null || !(body.get("ids") instanceof List<?> raw)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Number n) {
                ids.add(n.longValue());
            } else if (o != null) {
                try {
                    ids.add(Long.valueOf(String.valueOf(o).trim()));
                } catch (NumberFormatException ignored) {
                    // 非法 id 忽略
                }
            }
        }
        return ids;
    }
}
