package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.FileNames;
import com.jshz.expert.common.UploadGuard;
import com.jshz.expert.entity.ExpertDocument;
import com.jshz.expert.mapper.ExpertDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 专家资料文件服务（expert_document，2026-11-20）。
 * 医生证等资质资料：上传（登记+落盘）/ 列表 / 删除（登记+物理文件）。
 * 存储目录 {STORAGE_ROOT}\专家资料库（expert.document.storage-dir）；大小上限复用
 * expert.upload.doc-max-bytes 单一真源（资料含 pdf，不走照片 10MB 限额）。
 * 通用清洗/校验复用 common.FileNames / common.UploadGuard；禁止复制本地副本。
 */
@Service
@Slf4j
public class ExpertDocumentService extends ServiceImpl<ExpertDocumentMapper, ExpertDocument> {

    /** 资质资料扩展名白名单：图片 + pdf（医生证扫描件常见形态）。 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "pdf");

    private final Path storageDir;
    private final long maxBytes;

    public ExpertDocumentService(@Value("${expert.document.storage-dir}") String storageDir,
                                 @Value("${expert.upload.doc-max-bytes:104857600}") long maxBytes) {
        this.storageDir = FileNames.normalizeDir(storageDir);
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("专家资料目录创建失败: " + this.storageDir, e);
        }
        this.maxBytes = maxBytes;
    }

    /** 按专家列出资料（create_time 升序，稳定展示顺序）。 */
    public List<ExpertDocument> listByExpert(Long expertId) {
        return list(new LambdaQueryWrapper<ExpertDocument>()
                .eq(ExpertDocument::getExpertId, expertId)
                .orderByAsc(ExpertDocument::getId));
    }

    /**
     * 上传并登记一条资料。
     *
     * @return 登记行（含 /api/files-doc/ 访问 url 所需的 storedName）
     */
    @Transactional
    public ExpertDocument upload(Long expertId, MultipartFile file, Long userId) {
        if (expertId == null) {
            throw new BusinessException("缺少专家 id，无法上传资料");
        }
        UploadGuard.requireValid(file, maxBytes, ALLOWED_EXT,
                "资料仅支持图片（jpg/jpeg/png/gif/webp/bmp）或 pdf 文件");
        String ext = FileNames.extension(file.getOriginalFilename());
        // 同专家同毫秒并发上传时避免文件名碰撞（2026-11-23 P1 修复）
        long stamp = Instant.now().toEpochMilli();
        String storedName = "expertdoc_" + expertId + "_" + stamp + "." + ext;
        while (Files.exists(this.storageDir.resolve(storedName))) {
            stamp++;
            storedName = "expertdoc_" + expertId + "_" + stamp + "." + ext;
        }
        try {
            Files.copy(file.getInputStream(), this.storageDir.resolve(storedName));
        } catch (IOException e) {
            throw new BusinessException("资料保存失败: " + e.getMessage());
        }
        // 登记失败回滚时清理已落盘文件，避免孤儿
        final String savedName = storedName;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        deleteFileQuietly(savedName);
                    }
                }
            });
        }
        ExpertDocument doc = new ExpertDocument();
        doc.setExpertId(expertId);
        doc.setFileName(FileNames.sanitize(file.getOriginalFilename()));
        doc.setStoredName(storedName);
        doc.setFileSize(file.getSize());
        doc.setCreatedBy(userId);
        try {
            save(doc);
        } catch (RuntimeException e) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                deleteFileQuietly(savedName);
            }
            throw e;
        }
        return doc;
    }

    /** 删除一条资料（事务内删登记行，提交后删物理文件；文件删除失败记日志不阻断）。 */
    @Transactional
    public void deleteDocument(Long expertId, Long docId) {
        ExpertDocument doc = getById(docId);
        if (doc == null || !doc.getExpertId().equals(expertId)) {
            throw new BusinessException("资料不存在");
        }
        removeById(docId);
        afterCommit(() -> deleteFileQuietly(doc.getStoredName()));
    }

    /** 删除专家全部资料（专家档案删除时级联调用；登记行事务内删，物理文件提交后删）。 */
    @Transactional
    public void deleteByExpert(Long expertId) {
        List<ExpertDocument> docs = listByExpert(expertId);
        if (docs.isEmpty()) {
            return;
        }
        removeByIds(docs.stream().map(ExpertDocument::getId).toList());
        afterCommit(() -> docs.forEach(doc -> deleteFileQuietly(doc.getStoredName())));
    }

    /** 物理文件删除失败仅记 warn（登记行已删，残留文件留痕便于人工清理），不阻断响应。 */
    private void deleteFileQuietly(String storedName) {
        try {
            Files.deleteIfExists(this.storageDir.resolve(storedName));
        } catch (IOException e) {
            log.warn("专家资料物理文件删除失败 storedName={} err={}", storedName, e.getMessage());
        }
    }

    /** 事务提交后执行；无事务上下文（如隔离测试直调）时立即执行。 */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
