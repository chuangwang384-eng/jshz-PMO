package com.jshz.expert.common;

import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 上传文件校验的唯一实现（2026-11-13 复用治理收敛）。
 * 统一"非空 → 大小上限 → 扩展名白名单"三段校验；大小上限统一来自
 * application.yml expert.upload（单一真源，方案 D），各存储服务通过 @Value 注入后传入。
 */
public final class UploadGuard {

    private UploadGuard() {
    }

    /**
     * @param file        上传文件
     * @param maxBytes    大小上限（来自 expert.upload.*-max-bytes）
     * @param allowedExt  允许的小写扩展名集合
     * @param extMessage  扩展名不合规时的提示文案
     * @throws BusinessException 非空/大小/扩展名任一校验失败
     */
    public static void requireValid(MultipartFile file, long maxBytes, Set<String> allowedExt, String extMessage) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("文件大小超过限制（单文件 ≤" + (maxBytes / 1024 / 1024) + "MB）");
        }
        String ext = FileNames.extension(file.getOriginalFilename());
        if (!allowedExt.contains(ext)) {
            throw new BusinessException(extMessage);
        }
    }
}
