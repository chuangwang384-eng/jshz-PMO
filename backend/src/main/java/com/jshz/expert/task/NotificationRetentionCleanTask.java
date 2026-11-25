package com.jshz.expert.task;

import com.jshz.expert.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 消息保留期清理任务（需求 A，2026-11-25）：物理删除「已读且超过保留期」的本人通知，未读永不清理。
 *
 * <p>背景：notification 表只增不减——审批类通知增长最快（每次提交通知全部审批人），且删除申请单/消息
 * 后相关历史通知会成为孤儿残留。清理口径刻意保守：只动已读行，避免用户丢未读。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetentionCleanTask {

    private final NotificationService notificationService;

    /** 已读消息保留天数（application.yml expert.notification.read-retention-days，默认 90）。 */
    @Value("${expert.notification.read-retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "${expert.notification.retention-clean-cron:0 40 3 * * ?}")
    public void cleanExpiredReadNotifications() {
        try {
            int removed = notificationService.deleteReadBefore(retentionDays);
            if (removed > 0) {
                log.info("[NotificationRetentionCleanTask] 已清理过期已读消息 {} 条（保留期 {} 天）", removed, retentionDays);
            }
        } catch (Exception e) {
            // 清理属兜底运维动作，失败不影响业务，下个周期重试
            log.warn("[NotificationRetentionCleanTask] 消息保留期清理失败: {}", e.getMessage(), e);
        }
    }
}
