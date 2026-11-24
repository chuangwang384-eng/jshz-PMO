package com.jshz.expert.task;

import com.jshz.expert.service.ApplicationService;
import com.jshz.expert.service.PhotoStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 审批临时照片保留期清理任务（2026-11-24，我的申请审查 Q9）。
 *
 * <p>背景：审批流临时目录此前「只增不减」——驳回/撤回/重提只清理本单照片键，其余文件长期滞留；
 * 人工清理或目录迁移会删掉仍被 status=4（退回修改）单据引用的临时照片，导致申请人重提被硬拒且
 * 该状态不可撤回，单据永久滞留。</p>
 *
 * <p>清理口径：仅删除「未被任何待审批/退回修改申请单引用」且最后修改时间早于保留期
 * （{@code expert.photo.temp-retention-days}，默认 7 天）的文件；在途单据引用的文件一律保留，
 * 避免审批通过时归档失败。每日 03:10 执行一次（cron 可经 {@code expert.photo.temp-clean-cron} 覆盖）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTempPhotoCleanTask {

    private final ApplicationService applicationService;
    private final PhotoStorageService photoStorageService;

    /** 保留天数（application.yml expert.photo.temp-retention-days，默认 7）。 */
    @Value("${expert.photo.temp-retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "${expert.photo.temp-clean-cron:0 10 3 * * ?}")
    public void cleanExpiredTempPhotos() {
        try {
            Set<String> inUse = applicationService.referencedTempPhotos();
            int removed = photoStorageService.cleanTempPhotos(inUse, retentionDays);
            if (removed > 0) {
                log.info("[ApprovalTempPhotoCleanTask] 已清理过期临时照片 {} 张（保留在途引用 {} 个，保留期 {} 天）",
                        removed, inUse.size(), retentionDays);
            }
        } catch (Exception e) {
            // 清理属兜底运维动作，失败不影响业务，下个周期重试
            log.warn("[ApprovalTempPhotoCleanTask] 临时照片清理失败: {}", e.getMessage(), e);
        }
    }
}
