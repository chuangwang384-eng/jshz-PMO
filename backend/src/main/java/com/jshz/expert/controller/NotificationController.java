package com.jshz.expert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.entity.Notification;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ApplicationService;
import com.jshz.expert.service.NotificationService;
import com.jshz.expert.service.NotificationSseService;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;

import com.jshz.expert.common.ParamUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用消息通知接口（消息中心）。登录即可，所有操作仅作用于本人消息。
 * 列表（支持已读/类型组合筛选）/ 未读数 / 单条已读 / 批量已读 / 全部已读 / 单条删除 / 批量删除（物理删除）/
 * SSE 实时推送（需求 E）/ 免打扰偏好查询与保存（需求 D）。
 * 2026-11-25（P3-9/10）：page/pageSize 双重钳制（1..100），isRead 只接受 0/1，type 只接受已知类型，非法值 400。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;

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
        if (isRead != null && isRead != 0 && isRead != 1) {
            throw new BusinessException(400, "已读筛选值不合法（应为 0 或 1）");
        }
        Long userId = requireUserId(authUser);
        Page<Notification> p = notificationService.pageByUser(userId,
                ParamUtils.clamp(page, 1, Long.MAX_VALUE),
                ParamUtils.clamp(pageSize, 1, ApplicationService.MAX_PAGE_SIZE),
                isRead, type);
        return ApiResponse.ok(PageResult.of(p));
    }

    /**
     * 消息实时推送（SSE，需求 E，2026-11-25）：GET /api/notifications/sse（登录即可，text/event-stream）。
     * 只推「refresh」信号与心跳注释，不携带业务数据；前端收到信号后自行拉取未读数与列表，
     * 30 秒轮询保留为连接中断期的兜底。X-Accel-Buffering:no 关闭 Nginx 响应缓冲。
     */
    @GetMapping(value = "/sse", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(@AuthenticationPrincipal AuthUser authUser, HttpServletResponse response) {
        Long userId = requireUserId(authUser);
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return notificationSseService.register(userId);
    }

    /**
     * 本人免打扰类型列表：GET /api/notifications/mutes（需求 D）。返回 ["type1","type2"…]。
     */
    @GetMapping("/mutes")
    public ApiResponse<List<String>> mutes(@AuthenticationPrincipal AuthUser authUser) {
        Long userId = requireUserId(authUser);
        return ApiResponse.ok(notificationService.mutedTypes(userId));
    }

    /**
     * 全量保存本人免打扰类型：PUT /api/notifications/mutes，body {types:[…]}（需求 D）。
     * 类型须在 MUTABLE_TYPES 白名单内（password_reset 与未知类型 400 拒绝）；空数组=全部恢复接收。
     */
    @org.springframework.web.bind.annotation.PutMapping("/mutes")
    public ApiResponse<Void> saveMutes(@AuthenticationPrincipal AuthUser authUser,
                                       @RequestBody(required = false) Map<String, Object> body) {
        Long userId = requireUserId(authUser);
        Object raw = body == null ? null : body.get("types");
        if (raw != null && !(raw instanceof List)) {
            throw new BusinessException(400, "types 须为类型数组");
        }
        List<String> types = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                types.add(item == null ? null : String.valueOf(item));
            }
        }
        notificationService.saveMutedTypes(userId, types);
        return ApiResponse.ok();
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
                Long v = ParamUtils.toLong(o);
                if (v != null) {
                    ids.add(v);
                }
                // 非法 id 忽略（REQ-20261102 加固：消除裸 Long.valueOf）
            }
        }
        return ids;
    }
}
