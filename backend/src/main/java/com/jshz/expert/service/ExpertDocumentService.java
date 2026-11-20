package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.FileNames;
import com.jshz.expert.common.UploadGuard;
import com.jshz.expert.entity.ExpertDocument;
import com.jshz.expert.mapper.ExpertDocumentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
public class ExpertDocumentService extends ServiceImpl<ExpertDocumentMapper, ExpertDocument> {

    /** 资质资料扩展名白名单：图片 + pdf（医生证扫描件常见形态）。 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "pdf");

    private final Path storageDir;
    private final long maxBytes;

    public ExpertDocumentService(@Value("${expert.document.storage-dir}") String storageDir,
                                 @Value("${expert.upload.doc-max-bytes:52428800}") long maxBytes) {
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
    public ExpertDocument upload(Long expertId, MultipartFile file, Long userId) {
        if (expertId == null) {
            throw new BusinessException("缺少专家 id，无法上传资料");
        }
        UploadGuard.requireValid(file, maxBytes, ALLOWED_EXT,
                "资料仅支持图片（jpg/jpeg/png/gif/webp/bmp）或 pdf 文件");
        String ext = FileNames.extension(file.getOriginalFilename());
        String storedName = "expertdoc_" + expertId + "_" + Instant.now().toEpochMilli() + "." + ext;
        try {
            Files.copy(file.getInputStream(), this.storageDir.resolve(storedName));
        } catch (IOException e) {
            throw new BusinessException("资料保存失败: " + e.getMessage());
        }
        ExpertDocument doc = new ExpertDocument();
        doc.setExpertId(expertId);
        doc.setFileName(FileNames.sanitize(file.getOriginalFilename()));
        doc.setStoredName(storedName);
        doc.setFileSize(file.getSize());
        doc.setCreatedBy(userId);
        save(doc);
        return doc;
    }

    /** 删除一条资料（登记行 + 物理文件；文件不存在忽略）。 */
    public void deleteDocument(Long expertId, Long docId) {
        ExpertDocument doc = getById(docId);
        if (doc == null || !doc.getExpertId().equals(expertId)) {
            throw new BusinessException("资料不存在");
        }
        removeById(docId);
        try {
            Files.deleteIfExists(this.storageDir.resolve(doc.getStoredName()));
        } catch (IOException e) {
            // 登记行已删；物理残留可由存储管理孤儿扫描兜底，不阻断响应
        }
    }

    /** 删除专家全部资料（专家档案删除时级联调用；单个文件失败不阻断）。 */
    public void deleteByExpert(Long expertId) {
        for (ExpertDocument doc : listByExpert(expertId)) {
            removeById(doc.getId());
            try {
                Files.deleteIfExists(this.storageDir.resolve(doc.getStoredName()));
            } catch (IOException ignored) {
                // 同 deleteDocument：残留交孤儿扫描兜底
            }
        }
    }
}
