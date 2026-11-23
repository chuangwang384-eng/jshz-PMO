package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.ParamUtils;
import com.jshz.expert.common.KnowledgeParseStatus;
import com.jshz.expert.entity.ComplianceItem;
import com.jshz.expert.entity.ComplianceItemAssignee;
import com.jshz.expert.entity.ComplianceItemHistory;
import com.jshz.expert.entity.ComplianceUserProgress;
import com.jshz.expert.entity.ComplianceReminderLog;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.mapper.ComplianceItemAssigneeMapper;
import com.jshz.expert.mapper.ComplianceItemHistoryMapper;
import com.jshz.expert.mapper.ComplianceItemMapper;
import com.jshz.expert.mapper.ComplianceUserProgressMapper;
import com.jshz.expert.mapper.ComplianceReminderLogMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.mapper.KnowledgeFavoriteMapper;
import com.jshz.expert.entity.KnowledgeFavorite;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.Set;

/**
 * 企业合规事项服务（REQ/DES-20261102 v2/v3）：
 * - 全局事项 CRUD / 草稿确认发布（仅 knowledge:maintain）；
 * - 我的待办 = 收藏文档的已发布事项 ∪ 责任人是我的事项（去重）；
 * - 个人进度/提醒/备注（仅本人可读写）；
 * - 跨版本稳定标识 item_code 与匹配（完全一致自动继承 / 归一化或相似度≥80% 待确认 / 其余新增停用）；
 * - 事项修改历史（字段变更 + 审计动作）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final ComplianceRolloutService rollout;
    private final ComplianceItemMapper itemMapper;
    private final ComplianceItemAssigneeMapper assigneeMapper;
    private final ComplianceUserProgressMapper progressMapper;
    private final ComplianceItemHistoryMapper historyMapper;
    private final KnowledgeFavoriteMapper favoriteMapper;
    private final OllamaService ollamaService;
    private final KnowledgeDocumentMapper documentMapper;
    private final NotificationService notificationService;
    private final ComplianceParseService parseService;
    private final KnowledgeDocStorageService storageService;

    public static final List<String> STAGES = List.of("INITIATION", "FINANCE", "EXECUTION", "VERIFICATION", "SPECIAL", "OTHER");
    public static final List<String> GLOBAL_STATUS = List.of("PENDING", "IN_PROGRESS", "CONFIRMING", "COMPLETED");

    // ==================== 全局事项（维护人） ====================

    /** 全局事项列表（登录即可读；维护人可看草稿）。 */
    public Map<String, Object> listItems(Long documentId, String stage, String status, String priority,
                                         String keyword, boolean includeDraft, long page, long size) {
        LambdaQueryWrapper<ComplianceItem> qw = new LambdaQueryWrapper<>();
        if (documentId != null) qw.eq(ComplianceItem::getDocumentId, documentId);
        if (stage != null && !stage.isBlank()) qw.eq(ComplianceItem::getStage, stage);
        if (status != null && !status.isBlank()) qw.eq(ComplianceItem::getStatus, status);
        if (priority != null && !priority.isBlank()) qw.eq(ComplianceItem::getPriority, priority);
        if (keyword != null && !keyword.isBlank()) {
            // 关键词按字面子串匹配：转义 LIKE 通配符（\ % _），避免特殊字符被当通配符导致误匹配（Owner 2026-11-11）
            String kw = keyword.trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            qw.and(w -> w.like(ComplianceItem::getTitle, kw).or().like(ComplianceItem::getContent, kw));
        }
        qw.and(w -> w.isNull(ComplianceItem::getWithdrawn).or().eq(ComplianceItem::getWithdrawn, 0));
        if (!includeDraft) {
            qw.eq(ComplianceItem::getIsDraft, 0);
        }
        qw.orderByAsc(ComplianceItem::getDocumentId).orderByAsc(ComplianceItem::getId);
        List<ComplianceItem> all = itemMapper.selectList(qw);
        // 文档名批量填充（Owner 2026-11-10：模板维护按来源文档名分组展示，非文档编号）
        Set<Long> docIds = all.stream().map(ComplianceItem::getDocumentId).collect(java.util.stream.Collectors.toSet());
        Map<Long, String> docTitles = new LinkedHashMap<>();
        if (!docIds.isEmpty()) {
            for (KnowledgeDocument d : documentMapper.selectBatchIds(docIds)) {
                docTitles.put(d.getId(), d.getTitle());
            }
        }
        // 内存分页（事项量级可控：单文档 ≤ 数百条）
        int from = (int) Math.max(0, (page - 1) * size);
        from = Math.min(from, all.size());
        int to = (int) Math.min(all.size(), from + size);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ComplianceItem it : all.subList(from, to)) {
            Map<String, Object> m = toMap(it);
            m.put("documentTitle", docTitles.get(it.getDocumentId()));
            rows.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("items", rows);
        return m;
    }

    /** 手动新增事项（维护人）：直接已发布。 */
    @Transactional(rollbackFor = Exception.class)
    public ComplianceItem createItem(Map<String, Object> body, AuthUser user) {
        ComplianceItem it = new ComplianceItem();
        // 手动新增：documentId 必填（事项归属文档）
        Long documentId = ParamUtils.parseLong(body.get("documentId"), null);
        if (documentId == null) throw new BusinessException("documentId 不能为空（事项须归属文档）");
        requireComplianceDocument(documentId, true);
        it.setDocumentId(documentId);
        applyFields(it, body);
        it.setItemCode(nextItemCode());
        it.setIsDraft(0);
        it.setCreateByName(ParamUtils.usernameOf(user));
        it.setUpdateByName(ParamUtils.usernameOf(user));
        validateItem(it);
        itemMapper.insert(it);
        saveAssignees(it.getId(), body.get("assigneeIds"), user, true);
        history(it.getId(), "手动新增", null, it.getTitle(), user, null);
        return it;
    }

    /** 编辑事项（维护人全字段；关键字段变更写历史）。 */
    @Transactional(rollbackFor = Exception.class)
    public ComplianceItem updateItem(Long id, Map<String, Object> body, AuthUser user) {
        ComplianceItem it = itemMapper.selectById(id);
        if (it == null) throw new BusinessException("事项不存在");
        requireComplianceDocument(it.getDocumentId(), true);
        it = itemMapper.selectById(id);
        if (it == null || Integer.valueOf(1).equals(it.getWithdrawn())) throw new BusinessException("事项已删除或撤下");
        String oldTitle = it.getTitle();
        String oldStatus = it.getStatus();
        String oldDue = it.getDueDate() == null ? null : it.getDueDate().toString();
        applyFields(it, body);
        validateItem(it);
        it.setUpdateByName(ParamUtils.usernameOf(user));
        itemMapper.updateById(it);
        if (body.containsKey("assigneeIds")) {
            List<Long> oldAssignees = assigneeIdsOf(id);
            saveAssignees(id, body.get("assigneeIds"), user, false);
            List<Long> newAssignees = assigneeIdsOf(id);
            // 责任人变更：写历史 + 通知新责任人（旧责任人保留不通知）
            for (Long uid : newAssignees) {
                if (!oldAssignees.contains(uid)) {
                    notifyAssignee(uid, it);
                    history(id, "责任人", null, String.valueOf(uid), user, null);
                }
            }
        }
        if (!oldTitle.equals(it.getTitle())) history(id, "标题", oldTitle, it.getTitle(), user, null);
        if (!oldStatus.equals(it.getStatus())) history(id, "全局状态", oldStatus, it.getStatus(), user, null);
        String newDue = it.getDueDate() == null ? null : it.getDueDate().toString();
        if (oldDue != null && !oldDue.equals(newDue)) {
            history(id, "到期日期", oldDue, newDue, user, null);
            // 到期日期修改：旧提醒节点置 SKIPPED（按新日期重算）
            skipPendingReminders(id);
        }
        // 全部责任人已完成 → 提醒维护人评估（不自动改全局）
        checkAllAssigneesCompleted(it);
        return it;
    }

    /** 删除事项（维护人）：删除事项与责任人；个人进度保留（孤儿数据不展示）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id, AuthUser user) {
        ComplianceItem it = itemMapper.selectById(id);
        if (it == null) throw new BusinessException("事项不存在");
        requireComplianceDocument(it.getDocumentId(), true);
        if (itemMapper.deleteById(id) == 0) return;
        assigneeMapper.delete(new LambdaQueryWrapper<ComplianceItemAssignee>().eq(ComplianceItemAssignee::getItemId, id));
        history(id, "删除", it.getTitle(), null, user, null);
    }

    private KnowledgeDocument requireComplianceDocument(Long id, boolean lock) {
        var query = new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getId, id);
        if (lock) query.last("FOR UPDATE");
        KnowledgeDocument doc = documentMapper.selectOne(query);
        if (doc == null || !"enterprise-compliance".equals(doc.getCategory())) {
            throw new BusinessException("请选择已上传的企业合规文档");
        }
        return doc;
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> reparse(Long documentId, boolean reset, Long expectedCount, AuthUser user) {
        KnowledgeDocument source = requireComplianceDocument(documentId, false);
        checkUploadParsing(source);
        if (source.getFilePath() == null || source.getFilePath().isBlank()) {
            throw new BusinessException("文档原件不可用");
        }
        var path = storageService.resolveDocument(source.getFilePath());
        if (!java.nio.file.Files.isRegularFile(path)) throw new BusinessException("文档原件不可用");
        var parsed = parseService.parse(source.getFileName(), source.getFileType(), path, source.getContent());
        if (parsed.items().isEmpty()) throw new BusinessException(parsed.message());
        KnowledgeDocument current = requireComplianceDocument(documentId, true);
        checkUploadParsing(current);
        if (!java.util.Objects.equals(source.getVersion(), current.getVersion())) {
            throw new BusinessException(409, "文档版本已更新，请重新解析");
        }
        var active = itemMapper.selectList(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getDocumentId, documentId)
                .and(w -> w.isNull(ComplianceItem::getWithdrawn).or().eq(ComplianceItem::getWithdrawn, 0)));
        if (reset && (expectedCount == null || active.size() != expectedCount)) {
            throw new BusinessException(409, "事项数量已变化，请重新确认");
        }
        for (ComplianceItem item : active) {
            if (Integer.valueOf(1).equals(item.getIsDraft())) {
                deleteItem(item.getId(), user);
            } else if (reset) {
                markWithdrawn(item, user);
            }
        }
        var version = versionMapper.selectOne(new LambdaQueryWrapper<com.jshz.expert.entity.KnowledgeDocVersion>()
                .eq(com.jshz.expert.entity.KnowledgeDocVersion::getDocId, documentId)
                .eq(com.jshz.expert.entity.KnowledgeDocVersion::getVersion, current.getVersion())
                .last("LIMIT 1"));
        for (ComplianceItem item : parsed.items()) {
            item.setDocumentId(documentId);
            if (version != null) item.setDocumentVersionId(version.getId());
            item.setItemCode(nextItemCode());
            item.setIsDraft(1);
            item.setPendingConfirm(0);
            item.setCreateByName(ParamUtils.usernameOf(user));
            itemMapper.insert(item);
        }
        return Map.of("parsed", parsed.items().size(), "message", parsed.message());
    }

    private void checkUploadParsing(KnowledgeDocument doc) {
        if (KnowledgeParseStatus.PENDING.equals(doc.getParseStatus()) || KnowledgeParseStatus.RUNNING.equals(doc.getParseStatus())) {
            throw new BusinessException(409, "文档正在执行上传解析，请完成后重试");
        }
    }

    public Map<String, Object> resetPreview(Long documentId) {
        var doc = requireComplianceDocument(documentId, false);
        Long count = itemMapper.selectCount(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getDocumentId, documentId)
                .and(w -> w.isNull(ComplianceItem::getWithdrawn).or().eq(ComplianceItem::getWithdrawn, 0)));
        return Map.of("documentTitle", doc.getTitle(), "count", count);
    }

    /** 候选事项清空预览（维护端：文档标题 + 全部草稿条数）。 */
    public Map<String, Object> clearDraftsPreview(Long documentId) {
        var doc = requireComplianceDocument(documentId, false);
        Long count = itemMapper.selectCount(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getDocumentId, documentId)
                .eq(ComplianceItem::getIsDraft, 1)
                .and(w -> w.isNull(ComplianceItem::getWithdrawn).or().eq(ComplianceItem::getWithdrawn, 0)));
        return Map.of("documentTitle", doc.getTitle(), "count", count);
    }

    /** 清空候选事项（维护人）：一次性删除指定文档全部草稿，expectedCount 集合版本校验。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> clearDrafts(Long documentId, Long expectedCount, AuthUser user) {
        var doc = requireComplianceDocument(documentId, false);
        List<ComplianceItem> drafts = itemMapper.selectList(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getDocumentId, documentId)
                .eq(ComplianceItem::getIsDraft, 1)
                .and(w -> w.isNull(ComplianceItem::getWithdrawn).or().eq(ComplianceItem::getWithdrawn, 0)));
        if (expectedCount != null && drafts.size() != expectedCount) {
            throw new BusinessException(409, "候选事项数量已变化，请刷新后重试");
        }
        for (ComplianceItem item : drafts) {
            deleteItem(item.getId(), user);
        }
        return Map.of("deleted", drafts.size(), "documentTitle", doc.getTitle());
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdrawItem(Long id, AuthUser user) {
        var item = itemMapper.selectById(id);
        if (item == null || !Integer.valueOf(0).equals(item.getIsDraft())) throw new BusinessException("已发布事项不存在");
        requireComplianceDocument(item.getDocumentId(), true);
        item = itemMapper.selectById(id);
        if (item == null) throw new BusinessException("已发布事项不存在");
        if (!Integer.valueOf(1).equals(item.getWithdrawn())) markWithdrawn(item, user);
    }

    private void markWithdrawn(ComplianceItem item, AuthUser user) {
        item.setWithdrawn(1);
        item.setWithdrawnBy(ParamUtils.usernameOf(user));
        item.setWithdrawnTime(java.time.LocalDateTime.now());
        itemMapper.updateById(item);
        history(item.getId(), "撤下", "已发布", "已撤下", user, null);
    }

    /** 批量确认草稿发布（解析产物 → 已发布），发布后通知收藏用户 + 责任人。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmDrafts(Long documentId, List<Long> itemIds, AuthUser user) {
        LambdaQueryWrapper<ComplianceItem> qw = new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getDocumentId, documentId)
                .eq(ComplianceItem::getIsDraft, 1);
        if (itemIds != null && !itemIds.isEmpty()) {
            qw.in(ComplianceItem::getId, itemIds);
        }
        requireComplianceDocument(documentId, true);
        List<ComplianceItem> drafts = itemMapper.selectList(qw);
        int published = 0;
        for (ComplianceItem it : drafts) {
            it.setIsDraft(0);
            it.setUpdateByName(ParamUtils.usernameOf(user));
            itemMapper.updateById(it);
            history(it.getId(), "草稿发布", null, it.getTitle(), user, null);
            published++;
        }
        // 通知收藏该文档的用户 + 责任人
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        String docName = doc == null ? String.valueOf(documentId) : doc.getTitle();
        List<Long> favoriters = favoriteMapper.selectList(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getDocId, documentId))
                .stream().map(KnowledgeFavorite::getUserId).distinct().toList();
        for (Long uid : new HashSet<>(favoriters)) {
            notificationService.notify(uid, NotificationService.TYPE_COMPLIANCE_PUBLISHED,
                    "合规事项发布", "《" + docName + "》已发布 " + published + " 条合规事项，已同步到你的待办",
                    "/knowledge-planet/enterprise-compliance");
        }
        for (ComplianceItem it : drafts) {
            for (Long uid : assigneeIdsOf(it.getId())) {
                notifyAssignee(uid, it);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("published", published);
        m.put("notifiedUsers", new HashSet<>(favoriters).size());
        return m;
    }

    // ==================== 跨版本解析与匹配 ====================

    /**
     * 文档上传/更新后解析生成草稿（维护人触发）。
     * 跨版本匹配：与该文档当前已发布事项比对——标题完全一致→继承 item_code（个人记录按 item_code 迁移）；
     * 归一化相同或相似度≥80%→草稿标 pendingConfirm=1 待维护人确认；其余→新增。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> parseAndCreateDrafts(Long documentId, Long documentVersionId, String fileName,
                                                    String fileType, byte[] bytes, String textContent, AuthUser user) {
        return parseAndCreateDrafts(documentId, documentVersionId, fileName, fileType, textContent, user,
                () -> parseService.parse(fileName, fileType, bytes, textContent));
    }

    /**
     * P2-7：从磁盘路径解析（异步线程调用），xlsx/xls 不再整文件读入调用方 byte[]。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> parseAndCreateDrafts(Long documentId, Long documentVersionId, String fileName,
                                                    String fileType, Path path, String textContent, AuthUser user) {
        return parseAndCreateDrafts(documentId, documentVersionId, fileName, fileType, textContent, user,
                () -> parseService.parse(fileName, fileType, path, textContent));
    }

    private Map<String, Object> parseAndCreateDrafts(Long documentId, Long documentVersionId, String fileName,
                                                     String fileType, String textContent, AuthUser user,
                                                     Supplier<ComplianceParseService.ParseResult> parser) {
        if (documentId == null) {
            log.warn("合规解析跳过：documentId 为空（fileName={}）", fileName);
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("parsed", 0);
            empty.put("message", "documentId 为空，解析跳过");
            return empty;
        }
        ComplianceParseService.ParseResult pr = parser.get();
        List<ComplianceItem> parsed = pr.items();
        markImportantByLlm(parsed, user);
        requireComplianceDocument(documentId, true);
        List<ComplianceItem> oldPublished = itemMapper.selectList(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getDocumentId, documentId).eq(ComplianceItem::getIsDraft, 0)
                .and(w -> w.isNull(ComplianceItem::getWithdrawn).or().eq(ComplianceItem::getWithdrawn, 0)));
        Map<String, ComplianceItem> oldByTitle = new LinkedHashMap<>();
        for (ComplianceItem o : oldPublished) oldByTitle.put(o.getTitle(), o);

        int inherited = 0;
        int pending = 0;
        for (ComplianceItem it : parsed) {
            it.setDocumentId(documentId);
            it.setDocumentVersionId(documentVersionId);
            it.setCreateByName(ParamUtils.usernameOf(user));
            ComplianceItem exact = oldByTitle.get(it.getTitle());
            if (exact != null) {
                // 第一档：标题完全一致 → 自动匹配：先落库新事项，再迁移个人进度，最后物理删除旧事项
                // （释放 item_code 避免唯一键冲突；已提交资料/个人记录不丢失，E1）
                it.setItemCode(exact.getItemCode());
                it.setPreviousItemId(exact.getId());
                it.setChangeType("不变");
                itemMapper.deleteById(exact.getId()); // 先物理删除旧事项，释放 item_code（唯一键）
                itemMapper.insert(it);
                migrateProgress(exact.getId(), it.getId());
                inherited++;
            } else {
                it.setItemCode(nextItemCode());
                it.setChangeType("新增");
                ComplianceItem fuzzy = findFuzzyMatch(oldPublished, it.getTitle());
                if (fuzzy != null) {
                    // 第二档：归一化相同或相似度≥80% → 待维护人确认
                    it.setPendingConfirm(1);
                    it.setPreviousItemId(fuzzy.getId());
                    it.setChangeType("修改");
                    pending++;
                }
                itemMapper.insert(it);
            }
        }
        // 旧事项中未被任何新事项引用 → 标记停用（change_type=删除，待办隐藏，个人记录保留）
        Set<String> usedTitles = new HashSet<>();
        for (ComplianceItem it : parsed) usedTitles.add(it.getTitle());
        int removed = 0;
        for (ComplianceItem o : oldPublished) {
            if (!usedTitles.contains(o.getTitle())) {
                o.setChangeType("删除");
                o.setUpdateByName(ParamUtils.usernameOf(user));
                itemMapper.updateById(o);
                history(o.getId(), "停用（跨版本替换）", o.getTitle(), null, user, null);
                removed++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parsed", parsed.size());
        m.put("inherited", inherited);      // 自动匹配（继承个人记录）
        m.put("pendingConfirm", pending);   // 待确认匹配
        m.put("added", parsed.size() - inherited - pending); // 新增
        m.put("removed", removed);          // 停用
        m.put("message", pr.message());
        return m;
    }

    /**
     * LLM 重要事项标记（Owner 2026-11-10）：解析草稿经本地 Ollama 判定，重要者 priority 置 HIGH
     * 并在 remark 追加「LLM标记：重要」（维护人确认草稿时可改）。不可用/失败静默回退（保持 MEDIUM），
     * 异常不阻断解析主流程；日志只记条数不打印事项原文。
     */
    void markImportantByLlm(List<ComplianceItem> drafts, AuthUser user) {
        if (drafts == null || drafts.isEmpty() || ollamaService == null || !ollamaService.available()) {
            return;
        }
        try {
            List<String> titles = drafts.stream().map(ComplianceItem::getTitle).collect(java.util.stream.Collectors.toList());
            boolean[] flags = ollamaService.complianceImportance(titles);
            if (flags == null) {
                log.info("合规 LLM 重要性标记不可用（回退默认 MEDIUM）count={}", titles.size());
                return;
            }
            int marked = 0;
            for (int i = 0; i < drafts.size() && i < flags.length; i++) {
                if (flags[i]) {
                    ComplianceItem it = drafts.get(i);
                    it.setPriority("HIGH");
                    String remark = it.getRemark() == null ? "" : it.getRemark();
                    it.setRemark(remark.isBlank() ? "LLM标记：重要" : remark + "；LLM标记：重要");
                    marked++;
                }
            }
            log.info("合规 LLM 重要性标记完成 重要={}/{} by={}", marked, titles.size(), ParamUtils.usernameOf(user));
        } catch (Exception e) {
            log.warn("合规 LLM 重要性标记失败（不影响解析）：{}", e.getMessage());
        }
    }

    /** 归一化相同或相似度≥80% 匹配（排除标题完全一致场景）。 */
    private ComplianceItem findFuzzyMatch(List<ComplianceItem> oldItems, String title) {
        String norm = normalize(title);
        ComplianceItem best = null;
        double bestScore = 0;
        for (ComplianceItem o : oldItems) {
            if (normalize(o.getTitle()).equals(norm)) return o;
            double sim = similarity(o.getTitle(), title);
            if (sim >= 0.8 && sim > bestScore) {
                best = o;
                bestScore = sim;
            }
        }
        return best;
    }

    private String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\p{Punct}　，。；：、「」（）()【】\\[\\]]+", "")
                .replaceAll("^(关于|通知|要求|事项)", "").replaceAll("(试行|草案|通知)$", "");
    }

    /** 编辑距离相似度（0~1） */
    private double similarity(String a, String b) {
        if (a == null || b == null) return 0;
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(dp[i - 1][j] + 1, Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost));
            }
        }
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 1 : 1.0 - (double) dp[a.length()][b.length()] / max;
    }

    /** 匹配确认（维护人）：将草稿/新事项与旧事项建立血缘并迁移个人记录。 */
    @Transactional(rollbackFor = Exception.class)
    public void confirmMatch(Long newItemId, Long oldItemId, AuthUser user) {
        ComplianceItem it = itemMapper.selectById(newItemId);
        ComplianceItem old = itemMapper.selectById(oldItemId);
        if (it == null || old == null) throw new BusinessException("事项不存在");
        it.setItemCode(old.getItemCode());
        it.setPreviousItemId(old.getId());
        it.setPendingConfirm(0);
        it.setChangeType("修改");
        it.setUpdateByName(ParamUtils.usernameOf(user));
        itemMapper.updateById(it);
        migrateProgress(old.getId(), it.getId());
        history(newItemId, "匹配确认", String.valueOf(oldItemId), String.valueOf(newItemId), user, null);
    }

    /** 个人进度按 item_code 迁移（自动匹配/匹配确认场景：不丢个人记录）。 */
    private void migrateProgress(Long oldItemId, Long newItemId) {
        List<ComplianceUserProgress> rows = progressMapper.selectList(new LambdaQueryWrapper<ComplianceUserProgress>()
                .eq(ComplianceUserProgress::getItemId, oldItemId));
        for (ComplianceUserProgress p : rows) {
            if (progressMapper.selectCount(new LambdaQueryWrapper<ComplianceUserProgress>()
                    .eq(ComplianceUserProgress::getItemId, newItemId)
                    .eq(ComplianceUserProgress::getUserId, p.getUserId())) > 0) {
                continue;
            }
            ComplianceUserProgress np = new ComplianceUserProgress();
            np.setItemId(newItemId);
            np.setUserId(p.getUserId());
            np.setProgressStatus(p.getProgressStatus());
            np.setBlockReason(p.getBlockReason());
            np.setRemark(p.getRemark());
            np.setRemindEnabled(p.getRemindEnabled());
            np.setRemindDays(p.getRemindDays());
            np.setLastDueNotified(p.getLastDueNotified());
            progressMapper.insert(np);
        }
        // 旧事项标记删除（个人记录保留在旧行，不再展示）
        ComplianceItem old = itemMapper.selectById(oldItemId);
        if (old != null) {
            old.setChangeType("删除");
            itemMapper.updateById(old);
        }
    }

    // ==================== 我的待办（收藏 ∪ 责任人） ====================

    /** 我的待办：我收藏文档的已发布事项 ∪ 责任人是我的事项（去重）；统计 + 排序（高优置顶、到期升序）。 */
    public Map<String, Object> myTodo(Long userId) {
        Set<Long> docIds = favoriteMapper.selectList(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getUserId, userId))
                .stream().map(KnowledgeFavorite::getDocId).collect(java.util.stream.Collectors.toSet());
        Set<Long> assignedItemIds = assigneeMapper.selectList(new LambdaQueryWrapper<ComplianceItemAssignee>()
                .eq(ComplianceItemAssignee::getUserId, userId))
                .stream().map(ComplianceItemAssignee::getItemId).collect(java.util.stream.Collectors.toSet());

        LambdaQueryWrapper<ComplianceItem> qw = new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getIsDraft, 0)
                .ne(ComplianceItem::getStatus, "COMPLETED")
                // 20261123 修复：撤下事项不得进入我的待办（与 listItems 口径对齐）
                .and(w -> w.isNull(ComplianceItem::getWithdrawn).or().eq(ComplianceItem::getWithdrawn, 0));
        if (!docIds.isEmpty() && !assignedItemIds.isEmpty()) {
            qw.and(w -> w.in(ComplianceItem::getDocumentId, docIds).or().in(ComplianceItem::getId, assignedItemIds));
        } else if (!docIds.isEmpty()) {
            qw.in(ComplianceItem::getDocumentId, docIds);
        } else if (!assignedItemIds.isEmpty()) {
            qw.in(ComplianceItem::getId, assignedItemIds);
        } else {
            qw.eq(ComplianceItem::getId, -1L);
        }
        List<ComplianceItem> items = itemMapper.selectList(qw);
        // 来源文档批量读取：需求21a 起不再按下架隐藏，而是标注来源状态（ACTIVE/OFFLINE/MISSING）
        Map<Long, KnowledgeDocument> docs = new LinkedHashMap<>();
        Set<Long> allDocIds = items.stream().map(ComplianceItem::getDocumentId).collect(java.util.stream.Collectors.toSet());
        if (!allDocIds.isEmpty()) {
            for (KnowledgeDocument d : documentMapper.selectBatchIds(allDocIds)) docs.put(d.getId(), d);
        }

        LocalDate today = LocalDate.now();
        int near = 0;
        int overdue = 0;
        int unavailable = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ComplianceItem it : items) {
            KnowledgeDocument d = docs.get(it.getDocumentId());
            // 需求21a：来源下架/删除不再静默隐藏，返回来源状态供页面显式提示，避免误以为仍可执行
            String sourceStatus = d == null ? "MISSING" : (d.getStatus() != null && d.getStatus() == 1 ? "ACTIVE" : "OFFLINE");
            boolean actionable = "ACTIVE".equals(sourceStatus);
            if (!actionable) unavailable++;
            ComplianceUserProgress p = progressMapper.selectOne(new LambdaQueryWrapper<ComplianceUserProgress>()
                    .eq(ComplianceUserProgress::getItemId, it.getId())
                    .eq(ComplianceUserProgress::getUserId, userId));
            String progress = p == null ? "UNPROCESSED" : p.getProgressStatus();
            boolean done = "COMPLETED".equals(progress);
            LocalDate due = it.getDueDate();
            boolean isOverdue = due != null && due.isBefore(today) && !done && actionable;
            boolean isNear = due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(3)) && !done && actionable;
            if (!done && actionable) {
                if (isOverdue) overdue++;
                else if (isNear) near++;
            }
            Map<String, Object> m = toMap(it);
            m.put("documentTitle", d == null ? "来源文档已删除（#" + it.getDocumentId() + "）" : d.getTitle());
            m.put("source", (docIds.contains(it.getDocumentId()) ? "收藏" : "") + (assignedItemIds.contains(it.getId()) ? "责任人" : ""));
            m.put("sourceStatus", sourceStatus);
            m.put("sourceNotice", switch (sourceStatus) {
                case "MISSING" -> "来源文档已删除，仅供历史参考";
                case "OFFLINE" -> "来源文档已下架，仅供历史参考";
                default -> null;
            });
            m.put("actionable", actionable);
            m.put("myProgress", progress);
            m.put("myRemark", p == null ? null : p.getRemark());
            m.put("blockReason", p == null ? null : p.getBlockReason());
            m.put("remindEnabled", p == null ? 1 : p.getRemindEnabled());
            m.put("remindDays", p == null ? 3 : p.getRemindDays());
            m.put("overdue", isOverdue);
            m.put("nearDue", isNear);
            rows.add(m);
        }
        // 排序：可执行优先（来源失效项置底）→ 高优置顶 → 到期升序（无到期排后）
        rows.sort((a, b) -> {
            boolean aa = Boolean.TRUE.equals(a.get("actionable"));
            boolean bb = Boolean.TRUE.equals(b.get("actionable"));
            if (aa != bb) return aa ? -1 : 1;
            int pa = prioRank((String) a.get("priority"));
            int pb = prioRank((String) b.get("priority"));
            if (pa != pb) return pa - pb;
            LocalDate da = (LocalDate) a.get("dueDate");
            LocalDate db = (LocalDate) b.get("dueDate");
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return da.compareTo(db);
        });
        int todoCount = (int) rows.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("actionable")))
                .filter(r -> !"COMPLETED".equals(r.get("myProgress"))).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("todoCount", todoCount);
        m.put("nearDueCount", near);
        m.put("overdueCount", overdue);
        // 来源失效（下架/删除）条数：单独统计，不计入可执行待办，避免虚增工作量
        m.put("sourceUnavailableCount", unavailable);
        m.put("items", rows);
        return m;
    }

    private int prioRank(String p) {
        return switch (p == null ? "" : p) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    // ==================== 个人进度 / 提醒（仅本人） ====================

    /** 更新个人进度/备注/阻塞原因（仅本人；INSERT ON DUPLICATE 语义）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateMyProgress(Long userId, Long itemId, String progressStatus, String remark, String blockReason) {
        rollout.requireV3(userId);
        ComplianceItem it = itemMapper.selectById(itemId);
        // 20261123 修复：已撤下事项不可继续更新个人进度
        if (it == null || it.getIsDraft() == 1 || Integer.valueOf(1).equals(it.getWithdrawn())) throw new BusinessException("事项不存在、未发布或已撤下");
        // 需求21a：来源文档下架/删除后仅供历史参考，禁止继续更新进度（与待办 sourceNotice 一致）
        requireSourceAvailable(it.getDocumentId());
        if (progressStatus != null && !List.of("UNPROCESSED", "PROCESSING", "COMPLETED").contains(progressStatus)) {
            throw new BusinessException("非法的个人进度值");
        }
        ComplianceUserProgress p = progressMapper.selectOne(new LambdaQueryWrapper<ComplianceUserProgress>()
                .eq(ComplianceUserProgress::getItemId, itemId)
                .eq(ComplianceUserProgress::getUserId, userId));
        if (p == null) {
            p = new ComplianceUserProgress();
            p.setItemId(itemId);
            p.setUserId(userId);
            p.setProgressStatus(progressStatus == null ? "UNPROCESSED" : progressStatus);
            p.setRemindEnabled(1);
            p.setRemindDays(3);
        } else if (progressStatus != null) {
            p.setProgressStatus(progressStatus);
        }
        if (remark != null) p.setRemark(remark);
        if (blockReason != null) p.setBlockReason(blockReason);
        if (progressMapper.selectCount(new LambdaQueryWrapper<ComplianceUserProgress>()
                .eq(ComplianceUserProgress::getItemId, itemId)
                .eq(ComplianceUserProgress::getUserId, userId)) > 0) {
            progressMapper.update(p, new LambdaQueryWrapper<ComplianceUserProgress>()
                    .eq(ComplianceUserProgress::getItemId, itemId)
                    .eq(ComplianceUserProgress::getUserId, userId));
        } else {
            progressMapper.insert(p);
        }
        // 完成联动：所有责任人个人进度均已完成 → 提醒维护人评估（不自动改全局）
        if ("COMPLETED".equals(p.getProgressStatus())) {
            checkAllAssigneesCompleted(it);
        }
        return toProgressMap(p);
    }

    /** 提醒设置（仅本人）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateMyReminder(Long userId, Long itemId, Integer remindEnabled, Integer remindDays) {
        rollout.requireV3(userId);
        if (remindDays != null && (remindDays < 1 || remindDays > 30)) {
            throw new BusinessException("提醒提前天数须在 1-30 之间");
        }
        ComplianceUserProgress p = progressMapper.selectOne(new LambdaQueryWrapper<ComplianceUserProgress>()
                .eq(ComplianceUserProgress::getItemId, itemId)
                .eq(ComplianceUserProgress::getUserId, userId));
        if (p == null) {
            p = new ComplianceUserProgress();
            p.setItemId(itemId);
            p.setUserId(userId);
            p.setProgressStatus("UNPROCESSED");
            p.setRemindEnabled(remindEnabled == null ? 1 : remindEnabled);
            p.setRemindDays(remindDays == null ? 3 : remindDays);
            progressMapper.insert(p);
        } else {
            if (remindEnabled != null) p.setRemindEnabled(remindEnabled);
            if (remindDays != null) p.setRemindDays(remindDays);
            progressMapper.update(p, new LambdaQueryWrapper<ComplianceUserProgress>()
                    .eq(ComplianceUserProgress::getItemId, itemId)
                    .eq(ComplianceUserProgress::getUserId, userId));
        }
        return toProgressMap(p);
    }

    /** 某事项全部责任人个人进度（维护人只读）。 */
    public List<Map<String, Object>> assigneeProgress(Long itemId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ComplianceItemAssignee a : assigneeMapper.selectList(new LambdaQueryWrapper<ComplianceItemAssignee>()
                .eq(ComplianceItemAssignee::getItemId, itemId))) {
            ComplianceUserProgress p = progressMapper.selectOne(new LambdaQueryWrapper<ComplianceUserProgress>()
                    .eq(ComplianceUserProgress::getItemId, itemId)
                    .eq(ComplianceUserProgress::getUserId, a.getUserId()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", a.getUserId());
            m.put("progressStatus", p == null ? "UNPROCESSED" : p.getProgressStatus());
            m.put("blockReason", p == null ? null : p.getBlockReason());
            // 个人备注不可暴露给维护人（ADR-020 只读进度：备注不返回）
            m.put("updateTime", p == null ? null : p.getUpdateTime());
            rows.add(m);
        }
        return rows;
    }

    /** 某责任人全部事项个人进度聚合（维护人只读）。 */
    public List<Map<String, Object>> userProgress(Long userId) {
        List<ComplianceUserProgress> rows = progressMapper.selectList(new LambdaQueryWrapper<ComplianceUserProgress>()
                .eq(ComplianceUserProgress::getUserId, userId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ComplianceUserProgress p : rows) {
            ComplianceItem it = itemMapper.selectById(p.getItemId());
            if (it == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", p.getItemId());
            m.put("itemTitle", it.getTitle());
            m.put("documentId", it.getDocumentId());
            m.put("progressStatus", p.getProgressStatus());
            m.put("blockReason", p.getBlockReason());
            m.put("updateTime", p.getUpdateTime());
            result.add(m);
        }
        return result;
    }

    /** 事项修改历史。 */
    public List<ComplianceItemHistory> historyOf(Long itemId) {
        return historyMapper.selectList(new LambdaQueryWrapper<ComplianceItemHistory>()
                .eq(ComplianceItemHistory::getItemId, itemId)
                .orderByDesc(ComplianceItemHistory::getOperationTime));
    }

    // ==================== 提醒记录查询（维护人，幂等核对） ====================

    /** 单页上限：避免维护人一次拉全量提醒记录（需求21b）。 */
    public static final int REMINDER_LOG_MAX_PAGE_SIZE = 200;

    /**
     * 提醒记录分页查询（需求21b）：按事项 + 可选时间范围（按计划提醒时间过滤），
     * 服务端分页，默认 20 条/页，上限 {@value #REMINDER_LOG_MAX_PAGE_SIZE} 条。
     *
     * @param from 起始时间（包含，支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss），空=不限
     * @param to   结束时间（包含；仅日期时含当日全天），空=不限
     */
    public Map<String, Object> reminderLogs(Long itemId, String from, String to, long page, long size) {
        if (itemId == null) throw new BusinessException("itemId 不能为空");
        if (page < 1) throw new BusinessException("分页页码须从 1 开始");
        if (size < 1 || size > REMINDER_LOG_MAX_PAGE_SIZE) {
            throw new BusinessException("每页条数须在 1 至 " + REMINDER_LOG_MAX_PAGE_SIZE + " 之间");
        }
        LocalDateTime start = parseTimeBound(from, false);
        LocalDateTime end = parseTimeBound(to, true);
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException("时间范围起点不能晚于终点");
        }
        LambdaQueryWrapper<ComplianceReminderLog> qw = new LambdaQueryWrapper<ComplianceReminderLog>()
                .eq(ComplianceReminderLog::getItemId, itemId);
        if (start != null) qw.ge(ComplianceReminderLog::getScheduledTime, start);
        if (end != null) qw.le(ComplianceReminderLog::getScheduledTime, end);
        qw.orderByDesc(ComplianceReminderLog::getScheduledTime).orderByDesc(ComplianceReminderLog::getId);
        var result = reminderLogMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), qw);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", result.getTotal());
        m.put("page", page);
        m.put("size", size);
        m.put("items", result.getRecords());
        m.put("from", from == null || from.isBlank() ? null : from.trim());
        m.put("to", to == null || to.isBlank() ? null : to.trim());
        return m;
    }

    /** 解析查询时间边界：日期按当日 00:00:00 起（含当日时按次日 00:00:00 前一瞬）。 */
    private LocalDateTime parseTimeBound(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        try {
            if (text.length() <= 10) {
                LocalDate date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
                return endOfDay ? date.atTime(23, 59, 59, 999_000_000) : date.atStartOfDay();
            }
            String normalized = text.replace('T', ' ');
            return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException("时间格式不正确（应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）：" + text);
        }
    }

    /** 来源文档可用性校验：下架/删除后事项仅供历史参考（需求21a）。 */
    private void requireSourceAvailable(Long documentId) {
        KnowledgeDocument doc = documentId == null ? null : documentMapper.selectById(documentId);
        if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) {
            throw new BusinessException("来源文档已下架或删除，仅供历史参考，不能更新进度");
        }
    }

    /** 文档永久删除联动：删除该文档事项与责任人；个人进度保留（孤儿数据不展示）。 */
    @Transactional(rollbackFor = Exception.class)
    public void onDocumentDeleted(Long documentId) {
        List<ComplianceItem> items = itemMapper.selectList(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getDocumentId, documentId));
        for (ComplianceItem it : items) {
            assigneeMapper.delete(new LambdaQueryWrapper<ComplianceItemAssignee>()
                    .eq(ComplianceItemAssignee::getItemId, it.getId()));
            itemMapper.deleteById(it.getId());
        }
    }

    // ==================== 私有辅助 ====================

    private void applyFields(ComplianceItem it, Map<String, Object> body) {
        Object title = body.get("title");
        if (title != null) it.setTitle(String.valueOf(title).trim());
        if (body.containsKey("content")) it.setContent((String) body.get("content"));
        if (body.containsKey("stage")) it.setStage((String) body.get("stage"));
        if (body.containsKey("timeNote")) it.setTimeNote((String) body.get("timeNote"));
        Object due = body.get("dueDate");
        if (due != null && !String.valueOf(due).isBlank()) {
            it.setDueDate(LocalDate.parse(String.valueOf(due), DateTimeFormatter.ISO_LOCAL_DATE));
        } else if (body.containsKey("dueDate")) {
            it.setDueDate(null);
        }
        if (body.containsKey("priority")) it.setPriority((String) body.get("priority"));
        if (body.containsKey("status")) {
            String st = (String) body.get("status");
            if (!GLOBAL_STATUS.contains(st)) throw new BusinessException("非法的全局状态：" + st);
            it.setStatus(st);
        }
        if (body.containsKey("remark")) it.setRemark((String) body.get("remark"));
        Object projectId = body.get("projectId");
        if (projectId != null) it.setProjectId(ParamUtils.parseLong(projectId, null));
    }

    private void validateItem(ComplianceItem it) {
        if (it.getTitle() == null || it.getTitle().isBlank()) throw new BusinessException("事项标题不能为空");
        if (it.getStage() != null && !STAGES.contains(it.getStage())) throw new BusinessException("非法阶段：" + it.getStage());
        if (it.getPriority() != null && !List.of("HIGH", "MEDIUM", "LOW").contains(it.getPriority())) {
            throw new BusinessException("非法优先级：" + it.getPriority());
        }
    }

    private void saveAssignees(Long itemId, Object assigneeIds, AuthUser user, boolean notify) {
        assigneeMapper.delete(new LambdaQueryWrapper<ComplianceItemAssignee>().eq(ComplianceItemAssignee::getItemId, itemId));
        if (assigneeIds instanceof List<?> list) {
            for (Object o : list) {
                Long uid = ParamUtils.parseLong(o, null);
                if (uid == null) continue;
                if (assigneeMapper.selectCount(new LambdaQueryWrapper<ComplianceItemAssignee>()
                        .eq(ComplianceItemAssignee::getItemId, itemId)
                        .eq(ComplianceItemAssignee::getUserId, uid)) > 0) continue;
                ComplianceItemAssignee a = new ComplianceItemAssignee();
                a.setItemId(itemId);
                a.setUserId(uid);
                assigneeMapper.insert(a);
                if (notify) notifyAssignee(uid, itemMapper.selectById(itemId));
            }
        }
    }

    private List<Long> assigneeIdsOf(Long itemId) {
        return assigneeMapper.selectList(new LambdaQueryWrapper<ComplianceItemAssignee>()
                .eq(ComplianceItemAssignee::getItemId, itemId))
                .stream().map(ComplianceItemAssignee::getUserId).toList();
    }

    private void notifyAssignee(Long userId, ComplianceItem it) {
        if (it == null) return;
        notificationService.notify(userId, NotificationService.TYPE_COMPLIANCE_ASSIGNED,
                "合规事项责任人", "你被设为事项「" + it.getTitle() + "」的责任人，已加入你的待办",
                "/knowledge-planet/enterprise-compliance");
    }

    /** 所有责任人个人进度均已完成 → 提醒维护人评估（不自动改全局）。 */
    private void checkAllAssigneesCompleted(ComplianceItem it) {
        List<Long> assignees = assigneeIdsOf(it.getId());
        if (assignees.isEmpty()) return;
        boolean allDone = true;
        for (Long uid : assignees) {
            ComplianceUserProgress p = progressMapper.selectOne(new LambdaQueryWrapper<ComplianceUserProgress>()
                    .eq(ComplianceUserProgress::getItemId, it.getId())
                    .eq(ComplianceUserProgress::getUserId, uid));
            if (p == null || !"COMPLETED".equals(p.getProgressStatus())) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            // 维护人（具备 knowledge:maintain 的用户）：通知 createdBy 或广播给维护角色用户——此处通知全部维护人
            List<Long> maintainers = maintainerUserIds();
            for (Long uid : maintainers) {
                notificationService.notify(uid, NotificationService.TYPE_COMPLIANCE_DONE_NOTIFY,
                        "事项责任人已完成", "事项「" + it.getTitle() + "」所有责任人已完成个人进度，请评估是否更新全局状态",
                        "/knowledge-planet/enterprise-compliance");
            }
        }
    }

    private List<Long> maintainerUserIds() {
        // 维护人判定依赖权限码（knowledge:maintain）；此处经 role_permission 关联查询由 PermissionService 提供。
        return permissionUserIds("knowledge:maintain");
    }

    private final com.jshz.expert.mapper.UserRoleMapper userRoleMapper;
    private final com.jshz.expert.mapper.RolePermissionMapper rolePermissionMapper;

    /** 按权限码查持有该权限的用户（role_permission → user_role）。 */
    private List<Long> permissionUserIds(String code) {
        com.jshz.expert.entity.Role role = null;
        List<Long> ids = new ArrayList<>();
        var perms = rolePermissionMapper.selectList(new LambdaQueryWrapper<com.jshz.expert.entity.RolePermission>()
                .eq(com.jshz.expert.entity.RolePermission::getPermissionCode, code));
        Set<Long> roleIds = perms.stream().map(com.jshz.expert.entity.RolePermission::getRoleId).collect(java.util.stream.Collectors.toSet());
        if (roleIds.isEmpty()) return ids;
        for (var ur : userRoleMapper.selectList(new LambdaQueryWrapper<com.jshz.expert.entity.UserRole>()
                .in(com.jshz.expert.entity.UserRole::getRoleId, roleIds))) {
            if (!ids.contains(ur.getUserId())) ids.add(ur.getUserId());
        }
        return ids;
    }

    private void history(Long itemId, String field, String oldV, String newV, AuthUser user, String reason) {
        ComplianceItemHistory h = new ComplianceItemHistory();
        h.setItemId(itemId);
        h.setFieldName(field);
        h.setOldValue(oldV);
        h.setNewValue(newV);
        h.setOperatorId(ParamUtils.userIdOf(user));
        h.setOperatorName(ParamUtils.usernameOf(user));
        h.setChangeReason(reason);
        historyMapper.insert(h);
    }

    private void skipPendingReminders(Long itemId) {
        // 到期日期修改：PENDING 提醒置 SKIPPED（按新日期重算，不重发旧节点）
        ComplianceReminderLog patch = new ComplianceReminderLog();
        patch.setSendStatus("SKIPPED");
        reminderLogMapper.update(patch, new LambdaQueryWrapper<ComplianceReminderLog>()
                .eq(ComplianceReminderLog::getItemId, itemId)
                .eq(ComplianceReminderLog::getSendStatus, "PENDING"));
    }

    private final ComplianceReminderLogMapper reminderLogMapper;
    private final com.jshz.expert.mapper.KnowledgeDocVersionMapper versionMapper;

    private Map<String, Object> toMap(ComplianceItem it) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", it.getId());
        m.put("itemCode", it.getItemCode());
        m.put("documentId", it.getDocumentId());
        m.put("title", it.getTitle());
        m.put("content", it.getContent());
        m.put("stage", it.getStage());
        m.put("timeNote", it.getTimeNote());
        m.put("dueDate", it.getDueDate());
        m.put("priority", it.getPriority());
        m.put("status", it.getStatus());
        m.put("isDraft", it.getIsDraft());
        m.put("pendingConfirm", it.getPendingConfirm());
        m.put("changeType", it.getChangeType());
        m.put("remark", it.getRemark());
        m.put("updateByName", it.getUpdateByName());
        m.put("assigneeIds", assigneeIdsOf(it.getId()));
        return m;
    }

    private Map<String, Object> toProgressMap(ComplianceUserProgress p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId", p.getItemId());
        m.put("progressStatus", p.getProgressStatus());
        m.put("remark", p.getRemark());
        m.put("blockReason", p.getBlockReason());
        m.put("remindEnabled", p.getRemindEnabled());
        m.put("remindDays", p.getRemindDays());
        return m;
    }

    /** 生成跨版本稳定 item_code：EC-yyyy-序号（查当日最大序号）。 */
    private String nextItemCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
        String prefix = "EC-" + datePart + "-";
        Long maxSeq = itemMapper.selectList(new LambdaQueryWrapper<ComplianceItem>()
                .likeRight(ComplianceItem::getItemCode, prefix)
                .orderByDesc(ComplianceItem::getItemCode)
                .last("LIMIT 1"))
                .stream().findFirst()
                .map(c -> {
                    try {
                        return Long.parseLong(c.getItemCode().substring(prefix.length()));
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                }).orElse(0L);
        return prefix + String.format("%04d", maxSeq + 1);
    }
}
