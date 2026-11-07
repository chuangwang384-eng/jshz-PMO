package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.KnowledgeParseStatus;
import com.jshz.expert.entity.KnowledgeDocVersion;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.mapper.KnowledgeDocVersionMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 知识文档异步解析任务（P2-7）。
 *
 * <p>上传/更新请求只负责流式落盘并提交本任务；正文抽取、合规草稿生成在
 * {@code knowledgeParseExecutor} 线程池中执行，状态写入 knowledge_document.parse_status。</p>
 *
 * <p>版本一致性：所有状态与正文更新都带 {@code id + version} 条件。用户在上一次解析期间
 * 上传了新版本时，旧任务不会覆盖新版本的状态/正文，也不会为旧版本生成合规草稿。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeParseTaskService {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocVersionMapper versionMapper;
    private final KnowledgeDocStorageService storageService;
    private final KnowledgeDocTextExtractor textExtractor;
    private final ComplianceService complianceService;

    /**
     * 异步解析指定文档版本。
     *
     * @param docId        文档 id
     * @param version      文档版本号（乐观并发条件）
     * @param storedName   知识文档库内的存储文件名
     * @param originalName 原始文件名（决定扩展名/抽取器）
     * @param fileType     扩展名（小写）
     * @param category     分类；enterprise-compliance 触发合规草稿解析
     * @param user         触发人（写入合规事项 createByName）
     */
    @Async("knowledgeParseExecutor")
    public void parseAsync(Long docId, Integer version, String storedName, String originalName,
                           String fileType, String category, AuthUser user) {
        if (docId == null || version == null) {
            return;
        }
        if (!isCurrentVersion(docId, version)) {
            log.info("跳过知识文档解析：版本已变化 docId={}, taskVersion={}", docId, version);
            return;
        }
        updateStatus(docId, version, KnowledgeParseStatus.RUNNING, "解析中");
        try {
            Path path = storageService.resolveDocument(storedName);
            if (!Files.isRegularFile(path)) {
                throw new BusinessException("源文件不存在或不可读");
            }
            String content = textExtractor.extract(path, originalName == null ? storedName : originalName);
            // PDF/DOC/DOCX 明确期望有正文；抽取器为避免阻断主流程会吞掉异常，这里把“空结果”显式标为失败
            if (content == null && expectsText(fileType)) {
                throw new BusinessException("未提取到正文（文件可能损坏、加密或为扫描件）");
            }
            if (!updateContent(docId, version, content)) {
                log.info("跳过过期解析结果：docId={}, taskVersion={}", docId, version);
                return;
            }

            String message = content == null ? "该格式不参与正文检索" : "正文提取完成";
            if ("enterprise-compliance".equals(category)) {
                if (!isCurrentVersion(docId, version)) {
                    log.info("跳过过期合规解析：docId={}, taskVersion={}", docId, version);
                    return;
                }
                Long versionId = versionIdOf(docId, version);
                Map<String, Object> result = complianceService.parseAndCreateDrafts(
                        docId, versionId, originalName, fileType, path, content, user);
                message = String.valueOf(result.getOrDefault("message", "合规解析完成"));
            }
            updateStatus(docId, version, KnowledgeParseStatus.SUCCESS, message);
        } catch (Exception e) {
            log.error("知识文档异步解析失败：docId={}, version={}", docId, version, e);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            updateStatus(docId, version, KnowledgeParseStatus.FAILED, reason);
        }
    }

    /**
     * 线程池拒绝时由调用方同步标记失败，保证状态可观测且不阻塞上传请求。
     */
    public void markFailed(Long docId, Integer version, String message) {
        updateStatus(docId, version, KnowledgeParseStatus.FAILED, message);
    }

    private boolean expectsText(String fileType) {
        if (fileType == null) {
            return false;
        }
        String ft = fileType.toLowerCase(java.util.Locale.ROOT);
        return "pdf".equals(ft) || "doc".equals(ft) || "docx".equals(ft);
    }

    private boolean isCurrentVersion(Long docId, Integer version) {
        KnowledgeDocument doc = documentMapper.selectById(docId);
        return doc != null && Objects.equals(doc.getVersion(), version);
    }

    private Long versionIdOf(Long docId, Integer version) {
        KnowledgeDocVersion row = versionMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocVersion>()
                .eq(KnowledgeDocVersion::getDocId, docId)
                .eq(KnowledgeDocVersion::getVersion, version)
                .last("LIMIT 1"));
        return row == null ? null : row.getId();
    }

    /** 仅在 id + version 仍匹配时写入正文，避免旧任务覆盖新版本。 */
    private boolean updateContent(Long docId, Integer version, String content) {
        return documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, docId)
                .eq(KnowledgeDocument::getVersion, version)
                .set(KnowledgeDocument::getContent, content)) > 0;
    }

    /** 仅在 id + version 仍匹配时写入状态，避免旧任务把新版本标记为已完成/失败。 */
    private void updateStatus(Long docId, Integer version, String status, String message) {
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, docId)
                .eq(KnowledgeDocument::getVersion, version)
                .set(KnowledgeDocument::getParseStatus, status)
                .set(KnowledgeDocument::getParseMessage, truncate(message))
                // RUNNING 不是完成时间；清空旧版本时间，终态再写当前时间
                .set(KnowledgeDocument::getParseTime,
                        KnowledgeParseStatus.RUNNING.equals(status) ? null : LocalDateTime.now()));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
    }
}
