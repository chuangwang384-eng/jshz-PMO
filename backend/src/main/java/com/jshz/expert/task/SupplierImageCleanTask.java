package com.jshz.expert.task;

import com.jshz.expert.service.SupplierImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 未关联图片定时清理任务：扫描供应商图库目录，删除不在 supplier_image 表内
 * 且创建时间超过 30 分钟的文件（上传落盘后 DB 登记失败的孤儿文件兜底清理）。
 * 每小时执行一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierImageCleanTask {

    private final SupplierImageService supplierImageService;

    @Scheduled(cron = "0 0 * * * ?")
    public void cleanOrphanImages() {
        try {
            int cleaned = supplierImageService.cleanOrphanImages();
            if (cleaned > 0) {
                log.info("[SupplierImageCleanTask] 已清理未关联图片 {} 张", cleaned);
            }
        } catch (Exception e) {
            log.warn("[SupplierImageCleanTask] 清理未关联图片失败: {}", e.getMessage());
        }
    }
}
