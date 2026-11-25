package com.jshz.expert.task;

import com.jshz.expert.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审批滞留催办任务（2026-11-25 审批管理需求 E）。
 *
 * <p>待审批单滞留超过阈值（自最近一次提交/重新提交起算，与「已等待」同口径）时催办审批人：
 * 默认 24 小时发 URGE_24、72 小时再发 URGE_72，同级别只催一次（以审批进度记录为去重凭据，不建新表）。
 * 催办失败不影响业务，下个周期重试；重试不会绕过去重（已写记录即视为已催）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalUrgeReminderTask {

    private final ApplicationService applicationService;

    /** 一级催办阈值（application.yml expert.approval.urge-first-hours，默认 24 小时）。 */
    @Value("${expert.approval.urge-first-hours:24}")
    private int firstHours;

    /** 二级催办阈值（expert.approval.urge-second-hours，默认 72 小时）。 */
    @Value("${expert.approval.urge-second-hours:72}")
    private int secondHours;

    @Scheduled(cron = "${expert.approval.urge-cron:0 15 * * * ?}")
    public void urgeStalePending() {
        try {
            int sent = applicationService.urgeStalePending(firstHours, secondHours, LocalDateTime.now());
            if (sent > 0) {
                log.info("[ApprovalUrgeReminderTask] 本轮催办 {} 张", sent);
            }
        } catch (Exception e) {
            log.warn("[ApprovalUrgeReminderTask] 催办任务失败: {}", e.getMessage(), e);
        }
    }
}
