package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.KnowledgeDocVersion;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.mapper.KnowledgeDocVersionMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 知识文档业务服务（知识星球）。
 * 上传 / 分页检索 / 版本更新（保留历史）/ 下架（knowledge:manage）/ 版本历史。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentService extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument> {

    private static final Set<String> CATEGORIES = Set.of(
            "meeting-execution", "association-compliance", "enterprise-compliance", "customer-preferences");

    private final KnowledgeDocStorageService storageService;
    private final KnowledgeDocVersionMapper versionMapper;

    /**
     * 上传新文档：写主表 version=1，并写版本表第一条记录。登录即可。
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument upload(String category, String title, MultipartFile file, AuthUser user) {
        if (!StringUtils.hasText(category) || !CATEGORIES.contains(category)) {
            throw new BusinessException("分类不合法");
        }
        if (!StringUtils.hasText(title)) {
            throw new BusinessException("文档标题不能为空");
        }
        String storedName = storageService.saveDocument(file);
        String userName = user == null ? null : user.getUsername();

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setCategory(category);
        doc.setTitle(title.trim());
        doc.setVersion(1);
        doc.setFileName(file.getOriginalFilename());
        doc.setFilePath(storedName);
        doc.setFileType(extensionOf(file.getOriginalFilename()));
        doc.setFileSize(file.getSize());
        doc.setUploadBy(userName);
        doc.setUpdateBy(userName);
        doc.setStatus(1);
        this.save(doc);

        saveVersion(doc.getId(), 1, file.getOriginalFilename(), storedName,
                doc.getFileType(), file.getSize(), userName);
        return doc;
    }

    /**
     * 分页列表：category 可选（不传=全部分类）；keyword 对 title / file_name LIKE 检索。
     * 按权限过滤 status：管理员（knowledge:manage）返回上架+下架，普通员工仅上架（status=1）。
     */
    public Page<KnowledgeDocument> pageList(String category, String keyword, long page, long pageSize, AuthUser user) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            if (!CATEGORIES.contains(category)) {
                throw new BusinessException("分类不合法");
            }
            wrapper.eq(KnowledgeDocument::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(KnowledgeDocument::getTitle, kw)
                    .or().like(KnowledgeDocument::getFileName, kw));
        }
        boolean isManager = user != null && user.hasPermission("knowledge:manage");
        if (!isManager) {
            wrapper.eq(KnowledgeDocument::getStatus, 1);
        }
        wrapper.orderByDesc(KnowledgeDocument::getUpdateTime);
        return this.page(new Page<>(page, pageSize), wrapper);
    }

    /**
     * 更新文档：上传新文件覆盖，主表 version+1、更新文件字段与 update_by/update_time，
     * 并向版本表追加一条历史记录。登录即可。
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument updateVersion(Long id, String title, MultipartFile file, AuthUser user) {
        KnowledgeDocument doc = getById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        String storedName = storageService.saveDocument(file);
        String userName = user == null ? null : user.getUsername();

        int newVersion = (doc.getVersion() == null ? 1 : doc.getVersion()) + 1;
        doc.setVersion(newVersion);
        doc.setTitle(StringUtils.hasText(title) ? title.trim() : doc.getTitle());
        doc.setFileName(file.getOriginalFilename());
        doc.setFilePath(storedName);
        doc.setFileType(extensionOf(file.getOriginalFilename()));
        doc.setFileSize(file.getSize());
        doc.setUpdateBy(userName);
        // update_time 由数据库 ON UPDATE CURRENT_TIMESTAMP 自动维护
        this.updateById(doc);

        saveVersion(id, newVersion, file.getOriginalFilename(), storedName,
                doc.getFileType(), file.getSize(), userName);
        return getById(id);
    }

    /**
     * 下架（status=0）：需权限码 knowledge:manage（业务层校验，参照 ApprovalService）。
     */
    public void offShelf(Long id, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (!user.hasPermission("knowledge:manage")) {
            throw new BusinessException("无权限执行该操作");
        }
        KnowledgeDocument doc = getById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        if (doc.getStatus() != null && doc.getStatus() == 0) {
            throw new BusinessException("文档已下架");
        }
        doc.setStatus(0);
        this.updateById(doc);
    }

    /**
     * 版本历史列表（按版本倒序）。
     */
    public List<KnowledgeDocVersion> versions(Long id) {
        LambdaQueryWrapper<KnowledgeDocVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocVersion::getDocId, id)
                .orderByDesc(KnowledgeDocVersion::getVersion);
        return versionMapper.selectList(wrapper);
    }

    private void saveVersion(Long docId, int version, String fileName, String storedName,
                             String fileType, Long fileSize, String uploadBy) {
        KnowledgeDocVersion v = new KnowledgeDocVersion();
        v.setDocId(docId);
        v.setVersion(version);
        v.setFileName(fileName);
        v.setFilePath(storedName);
        v.setFileType(fileType);
        v.setFileSize(fileSize);
        v.setUploadBy(uploadBy);
        versionMapper.insert(v);
    }

    private String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
