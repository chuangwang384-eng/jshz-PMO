package com.jshz.expert.task;

import com.jshz.expert.service.ProjectTeamSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 项目 team 每日兜底同步（2026-11-26 项目日历需求 FR-05）：
 * 调岗钩子之外的安全网（如手工改库、钩子异常）；时间点避开业务高峰。
 * cron 可经 expert.project.team-sync-cron 覆盖（AGENTS.md 18：出口化，默认值=当前生效值）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectTeamSyncTask {

    private final ProjectTeamSyncService teamSyncService;

    @Scheduled(cron = "${expert.project.team-sync-cron:0 30 4 * * ?}")
    public void resyncDaily() {
        try {
            teamSyncService.resyncAll();
        } catch (Exception e) {
            log.warn("项目团队兜底同步失败：{}", e.getMessage());
        }
    }
}
