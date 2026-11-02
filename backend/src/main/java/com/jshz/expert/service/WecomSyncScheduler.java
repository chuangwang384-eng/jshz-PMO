package com.jshz.expert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 企微每日定时同步（首次引入 @Scheduled）。
 * 读取 sync-cron-enabled 开关，默认 false（上线稳定后由运维打开）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WecomSyncScheduler {

    private final ContactSyncService contactSyncService;

    @Scheduled(cron = "${expert.wecom.sync-cron:0 30 2 * * ?}")
    public void scheduledSync() {
        if (!contactSyncService.isSyncCronEnabled()) {
            log.debug("企微定时同步开关关闭（sync-cron-enabled=false），跳过");
            return;
        }
        log.info("企微定时同步触发（SCHEDULED）");
        try {
            contactSyncService.start("SCHEDULED", "system", false);
        } catch (Exception e) {
            log.warn("企微定时同步启动失败：{}", e.getMessage());
        }
    }
}
