package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.KnowledgeParseStatus;
import com.jshz.expert.entity.KnowledgeDocVersion;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.KnowledgeDocVersionMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 知识文档业务服务（知识星球）。
 * 上传 / 分页检索 / 版本更新（保留历史）/ 下架 / 上架 / 永久删除 / 版本历史。
 * 管理操作（上传/更新/下架/上架/永久删除/版本历史）均需权限码 knowledge:maintain。
 * 消息通知：下架通知上传人与收藏者；上架通知全部收藏者；更新通知收藏者。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentService extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument> {

    private static final Set<String> CATEGORIES = Set.of(
            "meeting-execution", "association-compliance", "enterprise-compliance");

    private final KnowledgeDocStorageService storageService;
    private final ComplianceService complianceService;
    private final KnowledgeFavoriteService favoriteService;
    private final DownloadAuditService downloadAuditService;
    private final KnowledgeDocVersionMapper versionMapper;
    private final NotificationService notificationService;
    private final SysUserMapper sysUserMapper;
    private final DocPreviewService docPreviewService;

    /**
     * 上传新文档：写主表 version=1，并写版本表第一条记录。仅维护人调用（控制器已校验）。
     * P2-7：请求线程只做流式落盘与入库，正文抽取/合规解析由知识库异步任务执行。
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
        doc.setContent(null);
        doc.setParseStatus(KnowledgeParseStatus.PENDING);
        doc.setParseMessage("等待解析");
        doc.setUploadBy(userName);
        doc.setUpdateBy(userName);
        doc.setStatus(1);
        this.save(doc);

        saveVersion(doc.getId(), 1, file.getOriginalFilename(), storedName,
                doc.getFileType(), file.getSize(), userName);
        return doc;
    }

    private String getDocCategory(Long docId) {
        KnowledgeDocument d = getById(docId);
        return d == null ? null : d.getCategory();
    }

    /**
     * 分页列表：category 可选（不传=全部分类）；fileType 对 file_type 等值过滤（image 特判为
     * jpg/jpeg/png 集合，供前端「图片」筛选标签使用）；keyword 对 title / file_name / content 检索（C1 内容检索）。
     * 按权限过滤 status：维护人（knowledge:maintain）返回上架+下架，普通用户仅上架（status=1）。
     * 结果批量填充 downloadCount（C4，仅维护人可见）与 isFavorite（C3，当前用户收藏状态）。
     */
    public Page<KnowledgeDocument> pageList(String category, String keyword, String fileType,
                                            long page, long pageSize, AuthUser user, boolean favoritesOnly) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        if (favoritesOnly) {
            if (user == null) throw new BusinessException("未登录");
            wrapper.apply("EXISTS (SELECT 1 FROM knowledge_favorite f WHERE f.doc_id = knowledge_document.id AND f.user_id = {0})",
                    user.getUserId());
        }
        if (StringUtils.hasText(category)) {
            if (!CATEGORIES.contains(category)) {
                throw new BusinessException("分类不合法");
            }
            wrapper.eq(KnowledgeDocument::getCategory, category);
        }
        if (StringUtils.hasText(fileType)) {
            String ft = fileType.trim().toLowerCase(Locale.ROOT);
            if ("image".equals(ft)) {
                wrapper.in(KnowledgeDocument::getFileType, "jpg", "jpeg", "png");
            } else {
                wrapper.eq(KnowledgeDocument::getFileType, ft);
            }
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(KnowledgeDocument::getTitle, kw)
                    .or().like(KnowledgeDocument::getFileName, kw)
                    .or().like(KnowledgeDocument::getContent, kw));
        }
        boolean isMaintainer = user != null && user.hasPermission("knowledge:maintain");
        if (!isMaintainer) {
            wrapper.eq(KnowledgeDocument::getStatus, 1);
        }
        wrapper.orderByDesc(KnowledgeDocument::getUpdateTime);
        Page<KnowledgeDocument> result = this.page(new Page<>(page, pageSize), wrapper);
        fillListExtras(result.getRecords(), user, isMaintainer);
        return result;
    }

    /**
     * 列表附加信息填充：downloadCount（download_log 聚合一次查询，仅维护人可见）+ isFavorite（当前用户批量查询）。
     */
    private void fillListExtras(List<KnowledgeDocument> docs, AuthUser user, boolean isMaintainer) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        List<Long> ids = docs.stream().map(KnowledgeDocument::getId).toList();
        Map<Long, Long> counts = isMaintainer ? downloadAuditService.countGroupByDoc() : Map.of();
        Set<Long> favoriteIds = user == null ? Set.of()
                : favoriteService.favoriteDocIds(user.getUserId(), ids);
        for (KnowledgeDocument doc : docs) {
            if (isMaintainer) {
                doc.setDownloadCount(counts.getOrDefault(doc.getId(), 0L));
            } else {
                doc.setDownloadCount(null);
            }
            doc.setIsFavorite(favoriteIds.contains(doc.getId()));
        }
    }

    /**
     * 更新文档：上传新文件覆盖，主表 version+1、更新文件字段与 update_by/update_time，
     * 并向版本表追加一条历史记录。仅维护人调用；正文抽取/合规解析由异步任务执行（P2-7）。
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument updateVersion(Long id, String title, MultipartFile file, AuthUser user) {
        // 业务层二次校验（类头约定：过滤器 + 业务层双重校验；20261123 修复更新接口漏校验）
        if (user == null || !user.hasPermission("knowledge:maintain")) {
            throw new BusinessException("该操作需要知识星球维护人权限（knowledge:maintain）");
        }
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
        doc.setContent(null);
        doc.setParseStatus(KnowledgeParseStatus.PENDING);
        doc.setParseMessage("等待解析");
        doc.setUpdateBy(userName);
        // update_time 由数据库 ON UPDATE CURRENT_TIMESTAMP 自动维护
        this.updateById(doc);
        // MyBatis-Plus 默认忽略 null 字段：显式清空旧正文，避免新版本解析完成前仍按旧内容命中
        this.update(new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, id)
                .set(KnowledgeDocument::getContent, null));

        saveVersion(id, newVersion, file.getOriginalFilename(), storedName,
                doc.getFileType(), file.getSize(), userName);

        // 格式展示：旧版本预览缓存失效，按文档 id 清理（新版本尚未产生缓存）
        docPreviewService.clearPreviews(id);

        // C3：更新成功后通知全部收藏者（排除操作人本人）
        notifyFavoriters(doc.getId(), doc.getTitle(), newVersion, userName);

        return getById(id);
    }

    /**
     * 更新通知（C3，doc_updated）：通知该文档全部收藏者「已更新至 vN」，
     * 排除执行本次更新的操作人；通知失败不影响主流程。
     */
    private void notifyFavoriters(Long docId, String title, int newVersion, String operatorName) {
        try {
            Long operatorId = operatorName == null ? null : notificationService.userIdOf(operatorName);
            for (Long userId : favoriteService.userIdsOfDoc(docId)) {
                if (operatorId != null && operatorId.equals(userId)) {
                    continue;
                }
                notificationService.notify(userId, NotificationService.TYPE_DOC_UPDATED,
                        "您收藏的文档《" + title + "》已更新至 v" + newVersion,
                        "文档《" + title + "》有新版本（v" + newVersion + "），可前往知识星球查看",
                        "/knowledge-planet");
            }
        } catch (Exception e) {
            // 通知失败不影响主流程
        }
    }


    /**
     * 收藏列表结果填充（C3）：为 [{doc, favoriteTime}] 中的文档填充 downloadCount 并置 isFavorite=true。
     * 由 Controller 编排调用（避免 KnowledgeFavoriteService 反向依赖本服务形成循环）。
     */
    public void enrichFavorites(List<Map<String, Object>> favorites) {
        if (favorites == null || favorites.isEmpty()) {
            return;
        }
        List<KnowledgeDocument> docs = favorites.stream()
                .map(item -> (KnowledgeDocument) item.get("doc"))
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, Long> counts = downloadAuditService.countGroupByDoc();
        for (KnowledgeDocument doc : docs) {
            doc.setDownloadCount(counts.getOrDefault(doc.getId(), 0L));
            doc.setIsFavorite(true);
        }
    }

    /**
     * 按存储文件名查文档（C4：文件下载端点记录下载次数用）。
     */
    public KnowledgeDocument findByFilePath(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getFilePath, storedName).last("LIMIT 1"));
    }

    /**
     * 文件是否可访问（P1-5）：上架文档登录即可；下架文档仅维护人可访问。
     * 纯函数，便于单元测试与控制器/服务复用。
     */
    public static boolean canAccessFile(KnowledgeDocument doc, AuthUser user) {
        if (doc == null) {
            return false;
        }
        if (user != null && user.hasPermission("knowledge:maintain")) {
            return true;
        }
        return Integer.valueOf(1).equals(doc.getStatus());
    }

    /**
     * 文件流访问前的统一授权（P1-5）。
     * 文档不存在/已永久删除 → 业务码 404；已下架且非维护人 → 业务码 403；
     * 否则返回文档供审计/预览复用（HTTP 状态沿用项目统一的 200 + body.code 约定）。
     * 这样下架或删除后旧链接立即失效，不再依赖“只有列表隐藏”。
     */
    public KnowledgeDocument requireDownloadable(String storedName, AuthUser user) {
        KnowledgeDocument doc = findByFilePath(storedName);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在或已删除");
        }
        if (!canAccessFile(doc, user)) {
            throw new BusinessException(403, "文档已下架，暂不可访问");
        }
        return doc;
    }

    /**
     * 下架（status=0）：需权限码 knowledge:maintain（业务层校验，参照 ApplicationService）。
     * 通知：收藏者（新）+ 上传人（现有保留）。
     */
    public void offShelf(Long id, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (!user.hasPermission("knowledge:maintain")) {
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
        // 上传人通知（现有保留）
        notifyDocEvent(NotificationService.TYPE_DOC_OFF_SHELF, doc,
                "文档「" + doc.getTitle() + "」已被下架", "您上传的文档已被下架，内容不再对全员可见");
        // 收藏者通知（新）
        notifyAllFavoriters(NotificationService.TYPE_DOC_OFF_SHELF, doc,
                "您收藏的文档《" + doc.getTitle() + "》已下架", "文档《" + doc.getTitle() + "》已下架，收藏列表中将自动隐藏，上架后恢复显示");
    }

    /**
     * 上架（status=1）：仅维护人（knowledge:maintain）。普通用户立即可见，收藏列表自动恢复。
     * 通知全部收藏者（新）。
     */
    public void relist(Long id, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (!user.hasPermission("knowledge:maintain")) {
            throw new BusinessException("无权限执行该操作");
        }
        KnowledgeDocument doc = getById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        if (doc.getStatus() != null && doc.getStatus() == 1) {
            throw new BusinessException("文档已上架");
        }
        doc.setStatus(1);
        this.updateById(doc);
        notifyAllFavoriters(NotificationService.TYPE_DOC_RELISTED, doc,
                "您收藏的文档《" + doc.getTitle() + "》已恢复上架", "文档《" + doc.getTitle() + "》已重新上架，可前往知识星球查看");
    }

    /**
     * 收藏者通知（doc_off_shelf / doc_relisted 共用）：通知该文档全部收藏者；通知失败不影响主流程。
     */
    private void notifyAllFavoriters(String type, KnowledgeDocument doc, String title, String content) {
        try {
            for (Long userId : favoriteService.userIdsOfDoc(doc.getId())) {
                notificationService.notify(userId, type, title, content, "/knowledge-planet");
            }
        } catch (Exception e) {
            // 通知失败不影响主流程
        }
    }

    /**
     * 永久删除（knowledge:maintain）：删除主记录 + 全部版本历史 + 磁盘文件（含各版本文件），不可恢复。
     * 需权限码 knowledge:maintain（业务层校验）。删除后通知上传人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void permanentDelete(Long id, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (!user.hasPermission("knowledge:maintain")) {
            throw new BusinessException("无权限执行该操作");
        }
        KnowledgeDocument doc = getOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, id).last("FOR UPDATE"));
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        // 1. 收集物理文件（当前版本 + 各历史版本），先删除记录后物理删除
        List<KnowledgeDocVersion> versions = versions(id);
        // 2. 删除版本历史记录
        versionMapper.delete(new LambdaQueryWrapper<KnowledgeDocVersion>()
                .eq(KnowledgeDocVersion::getDocId, id));
        // 3. 删除主记录
        this.removeById(id);
        // 3.5 清理该文档全部预览缓存（LibreOffice 转换产物）
        docPreviewService.clearPreviews(id);
        // 4. 物理删除磁盘文件（当前 + 历史版本，容错不阻断）
        if (StringUtils.hasText(doc.getFilePath())) {
            storageService.deleteDocument(doc.getFilePath());
        }
        for (KnowledgeDocVersion v : versions) {
            if (v.getFilePath() != null && StringUtils.hasText(v.getFilePath())) {
                storageService.deleteDocument(v.getFilePath());
            }
        }
        // 5. 通知上传人
        notifyDocEvent(NotificationService.TYPE_DOC_DELETED, doc,
                "文档「" + doc.getTitle() + "」已被永久删除",
                "您上传的文档已被管理员永久删除（含全部版本历史与磁盘文件），不可恢复");
     // 企业合规联动：永久删除文档时删除事项与责任人；个人进度保留（孤儿数据不展示）
    if (complianceService != null) {
        complianceService.onDocumentDeleted(id);
    }
}


    /**
     * 文档事件通知：按 uploadBy 反查接收人，通知到用户。
     */
    private void notifyDocEvent(String type, KnowledgeDocument doc, String title, String content) {
        if (doc.getUploadBy() == null || !StringUtils.hasText(doc.getUploadBy())) {
            return;
        }
        SysUser uploader = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, doc.getUploadBy()).last("LIMIT 1"));
        if (uploader == null) {
            return;
        }
        notificationService.notify(uploader.getId(), type, title, content, "/knowledge-planet");
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
        return com.jshz.expert.common.FileNames.extension(fileName);
    }
}
