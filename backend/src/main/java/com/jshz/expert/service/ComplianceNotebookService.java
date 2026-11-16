package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ComplianceItem;
import com.jshz.expert.entity.ComplianceItemHide;
import com.jshz.expert.entity.ComplianceItemHistory;
import com.jshz.expert.entity.ComplianceProjectDocBinding;
import com.jshz.expert.entity.ComplianceProjectTodo;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.entity.Project;
import com.jshz.expert.mapper.ComplianceItemHideMapper;
import com.jshz.expert.mapper.ComplianceItemHistoryMapper;
import com.jshz.expert.mapper.ComplianceItemMapper;
import com.jshz.expert.mapper.ComplianceProjectDocBindingMapper;
import com.jshz.expert.mapper.ComplianceProjectTodoMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业合规记事本（Owner 2026-11-10 需求/设计文档）：
 * 员工=收藏文档 ∩ 已发布 ∩ 上架，个人隐藏/恢复，批量推送参与项目（首次绑定文档、快照去重）；
 * 维护人=按文档撤下（预览计数校验，撤下保留历史）。推送与撤下共用项目行锁顺序防竞态。
 * 项目待办为推送时快照，来源更新/撤下/取消收藏/个人隐藏均不影响快照与处理状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceNotebookService {

    private final ComplianceItemMapper itemMapper;
    private final ComplianceItemHideMapper hideMapper;
    private final ComplianceItemHistoryMapper historyMapper;
    private final ComplianceProjectDocBindingMapper bindingMapper;
    private final ComplianceProjectTodoMapper todoMapper;
    private final KnowledgeFavoriteService favoriteService;
    private final KnowledgeDocumentService documentService;
    private final ProjectService projectService;
    private final OperationLogService operationLogService;

    // ==================== 员工记事本 ====================

    /**
     * 记事本查询（会话用户）：收藏文档 ∩ 已发布未撤下 ∩ 上架（有权访问）；documentId 可选筛选；
     * view=hidden 返回当前用户已隐藏条目。返回按文档分组结构（真实文档名）。
     */
    public List<Map<String, Object>> myItems(AuthUser user, Long documentId, String view) {
        Long uid = user.getUserId();
        List<Long> favDocIds = favoriteService.myFavoriteDocIds(uid);
        if (documentId != null) {
            if (!favDocIds.contains(documentId)) return List.of();
            favDocIds = List.of(documentId);
        }
        if (favDocIds.isEmpty()) return List.of();
        boolean hiddenView = "hidden".equals(view);
        List<Long> hiddenIds = hiddenIdsOf(uid);
        List<ComplianceItem> items = itemMapper.selectList(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getIsDraft, 0)
                .eq(ComplianceItem::getWithdrawn, 0)
                .in(ComplianceItem::getDocumentId, favDocIds)
                .orderByDesc(ComplianceItem::getId));
        Map<Long, Map<String, Object>> groups = new LinkedHashMap<>();
        for (ComplianceItem it : items) {
            boolean hidden = hiddenIds.contains(it.getId());
            if (hiddenView != hidden) continue;
            KnowledgeDocument doc = documentService.getById(it.getDocumentId());
            if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) continue;   // 无权访问（下架/删除）不入记事本
            Map<String, Object> g = groups.computeIfAbsent(it.getDocumentId(), k -> {
                Map<String, Object> gm = new LinkedHashMap<>();
                gm.put("documentId", k);
                gm.put("documentTitle", doc.getTitle());
                gm.put("items", new ArrayList<Map<String, Object>>());
                return gm;
            });
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", it.getId());
            im.put("title", it.getTitle());
            im.put("content", it.getContent());
            im.put("stage", it.getStage());
            im.put("priority", it.getPriority());
            im.put("timeNote", it.getTimeNote());
            im.put("documentVersionId", it.getDocumentVersionId());
            im.put("hidden", hidden);
            im.put("sourceAnchor", it.getDocumentVersionId() == null
                    ? (doc.getVersion() == null ? null : "v" + doc.getVersion())
                    : "v" + it.getDocumentVersionId());
            ((List<Map<String, Object>>) g.get("items")).add(im);
        }
        return new ArrayList<>(groups.values());
    }

    /** 隐藏（幂等；仅限本人可见事项——收藏+已发布未撤下）。 */
    public void hide(Long itemId, AuthUser user) {
        requireVisibleItem(itemId, user);
        if (hideMapper.selectCount(new LambdaQueryWrapper<ComplianceItemHide>()
                .eq(ComplianceItemHide::getUserId, user.getUserId())
                .eq(ComplianceItemHide::getItemId, itemId)) > 0) {
            return;
        }
        ComplianceItemHide h = new ComplianceItemHide();
        h.setUserId(user.getUserId());
        h.setItemId(itemId);
        hideMapper.insert(h);
    }

    /** 恢复显示（幂等）。 */
    public void unhide(Long itemId, AuthUser user) {
        hideMapper.delete(new LambdaQueryWrapper<ComplianceItemHide>()
                .eq(ComplianceItemHide::getUserId, user.getUserId())
                .eq(ComplianceItemHide::getItemId, itemId));
    }

    /** 当前用户隐藏的事项 ID 集合。 */
    public List<Long> hiddenIdsOf(Long userId) {
        return hideMapper.selectList(new LambdaQueryWrapper<ComplianceItemHide>()
                .eq(ComplianceItemHide::getUserId, userId))
                .stream().map(ComplianceItemHide::getItemId).toList();
    }

    private void requireVisibleItem(Long itemId, AuthUser user) {
        ComplianceItem it = itemMapper.selectById(itemId);
        if (it == null || it.getIsDraft() != 0 || it.getWithdrawn() != 0) {
            throw new BusinessException("事项不存在或未发布");
        }
        if (!favoriteService.favoriteDocIds(user.getUserId(), List.of(it.getDocumentId())).contains(it.getDocumentId())) {
            throw new BusinessException("仅收藏文档的已发布事项可操作");
        }
    }

    // ==================== 维护端：按文档撤下 ====================

    /** 撤下预览：文档标题 + 全量已发布条目实际数量（确认窗展示用）。维护人权限（knowledge:maintain）。 */
    public Map<String, Object> withdrawPreview(Long documentId, AuthUser user) {
        requireMaintainer(user);
        long count = publishedCount(documentId);
        KnowledgeDocument doc = documentService.getById(documentId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("documentId", documentId);
        m.put("documentTitle", doc == null ? "来源已失效 #" + documentId : doc.getTitle());
        m.put("count", count);
        return m;
    }

    /**
     * 撤下提交：expectedCount 与当前全量不符 → 集合已变化冲突；事务内批量置 withdrawn + 逐条历史。
     * 原件、解析候选、收藏、模板、已推送项目待办均不受影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> withdraw(Long documentId, Integer expectedCount, AuthUser user) {
        requireMaintainer(user);
        long actual = publishedCount(documentId);
        if (expectedCount == null || expectedCount != actual) {
            throw new BusinessException("已发布条目集合已变化（当前 " + actual + " 条），请重新确认");
        }
        List<ComplianceItem> items = itemMapper.selectList(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getDocumentId, documentId)
                .eq(ComplianceItem::getIsDraft, 0)
                .eq(ComplianceItem::getWithdrawn, 0));
        for (ComplianceItem it : items) {
            it.setWithdrawn(1);
            it.setWithdrawnBy(user.getUsername());
            it.setWithdrawnTime(LocalDateTime.now());
            itemMapper.updateById(it);
            ComplianceItemHistory h = new ComplianceItemHistory();
            h.setItemId(it.getId());
            h.setFieldName("撤下");
            h.setOldValue("已发布");
            h.setNewValue("已撤下");
            h.setOperatorId(user.getUserId());
            h.setOperatorName(user.getUsername());
            h.setChangeReason("按文档清空已发布事项");
            historyMapper.insert(h);
        }
        operationLogService.record(user.getUserId(), user.getUsername(), "COMPLIANCE_WITHDRAW",
                "撤下文档#" + documentId + "已发布合规事项 " + items.size() + " 条", "KNOWLEDGE_DOC", documentId, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("withdrawn", items.size());
        return m;
    }

    private void requireMaintainer(AuthUser user) {
        if (user == null || !user.hasPermission("knowledge:maintain")) {
            throw new BusinessException("该操作需要维护人权限（knowledge:maintain）");
        }
    }

    private long publishedCount(Long documentId) {
        return itemMapper.selectCount(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getDocumentId, documentId)
                .eq(ComplianceItem::getIsDraft, 0)
                .eq(ComplianceItem::getWithdrawn, 0));
    }

    // ==================== 推送项目 ====================

    /**
     * 可推送项目：本人真实参与（负责人/创建人/协助人/历史执行人，不含全量查看豁免）
     * 且文档绑定兼容（无绑定或已绑定同一文档）。仅返回本人参与的项目（按行级数据权限口径）。
     */
    public List<Map<String, Object>> pushableProjects(Long documentId, AuthUser user) {
        List<Project> mine = projectService.myParticipatedProjects(user);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Project p : mine) {
            ComplianceProjectDocBinding b = bindingMapper.selectById(p.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", p.getId());
            m.put("name", p.getName());
            m.put("boundDocumentId", b == null ? null : b.getDocumentId());
            m.put("boundDocTitle", b == null ? null : b.getDocTitle());
            m.put("compatible", b == null || b.getDocumentId().equals(documentId));
            result.add(m);
        }
        return result;
    }

    /**
     * 推送事项到项目（设计文档 §推送事项）：事务内锁项目行 → 校验真实成员/来源权限/发布状态/同一文档
     * → 校验或建立绑定 → 逐条写快照（唯一约束去重计 skipped）。批次异常整批回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> push(Long projectId, List<Long> itemIds, AuthUser user) {
        if (projectId == null || itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException("请选择项目与至少一条事项");
        }
        todoMapper.lockProject(projectId);   // 与来源撤下一致锁顺序，串行化跨文档并发首次绑定
        Project project = projectService.requireProject(projectId);
        if (!projectService.isRealProjectMember(project, user)) {
            throw new BusinessException("仅项目真实参与人（负责人/创建人/协助人/历史执行人）可推送");
        }
        List<ComplianceItem> items = itemMapper.selectList(new LambdaQueryWrapper<ComplianceItem>()
                .in(ComplianceItem::getId, itemIds));
        if (items.size() != itemIds.stream().distinct().count()) {
            throw new BusinessException("存在无效或重复的事项");
        }
        Long documentId = null;
        for (ComplianceItem it : items) {
            if (it.getIsDraft() != 0 || it.getWithdrawn() != 0) {
                throw new BusinessException("仅已发布且未撤下的事项可推送");
            }
            KnowledgeDocument doc = documentService.getById(it.getDocumentId());
            if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) {
                throw new BusinessException("来源文档不可访问，无法推送");
            }
            if (documentId == null) {
                documentId = it.getDocumentId();
            } else if (!documentId.equals(it.getDocumentId())) {
                throw new BusinessException("仅支持推送同一文档下的事项");
            }
        }
        ComplianceProjectDocBinding binding = bindingMapper.selectById(projectId);
        if (binding == null) {
            binding = new ComplianceProjectDocBinding();
            binding.setProjectId(projectId);
            binding.setDocumentId(documentId);
            KnowledgeDocument doc = documentService.getById(documentId);
            binding.setDocTitle(doc.getTitle());
            binding.setBindBy(user.getUsername());
            binding.setBindTime(LocalDateTime.now());
            bindingMapper.insert(binding);
        } else if (!binding.getDocumentId().equals(documentId)) {
            throw new BusinessException("该项目已绑定其他来源文档（" + binding.getDocTitle() + "），本期不支持换绑");
        }
        KnowledgeDocument srcDoc = documentService.getById(documentId);
        int added = 0;
        int skipped = 0;
        for (ComplianceItem it : items) {
            ComplianceProjectTodo todo = new ComplianceProjectTodo();
            todo.setProjectId(projectId);
            todo.setSourceItemId(it.getId());
            todo.setDocumentId(documentId);
            todo.setDocumentVersionId(it.getDocumentVersionId());
            todo.setDocTitle(srcDoc.getTitle());
            todo.setTitle(it.getTitle());
            todo.setContent(it.getContent());
            todo.setSourceAnchor(srcDoc.getVersion() == null ? null : "v" + srcDoc.getVersion());
            todo.setStatus("PENDING");
            todo.setCreateBy(user.getUsername());
            try {
                todoMapper.insert(todo);
                added++;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                skipped++;   // 重复推送同一条目跳过
            }
        }
        operationLogService.record(user.getUserId(), user.getUsername(), "COMPLIANCE_PUSH",
                "推送合规事项到项目「" + project.getName() + "」：新增 " + added + " 条，跳过 " + skipped + " 条",
                "PROJECT", projectId, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("added", added);
        m.put("skipped", skipped);
        m.put("boundDocumentId", documentId);
        return m;
    }

    // ==================== 项目待办读写 ====================

    /** 项目待办列表（项目可读即可查看）。 */
    public List<ComplianceProjectTodo> projectTodos(Long projectId, AuthUser user) {
        projectService.requireProjectReadable(projectService.requireProject(projectId), user);
        return todoMapper.selectForProject(projectId);
    }

    /** 处理待办（真实参与人；row_version 乐观锁，不一致抛冲突）。 */
    @Transactional(rollbackFor = Exception.class)
    public void handleTodo(Long todoId, String status, Integer rowVersion, AuthUser user) {
        if (!"PENDING".equals(status) && !"DONE".equals(status)) {
            throw new BusinessException("处理状态仅支持 PENDING/DONE");
        }
        ComplianceProjectTodo todo = todoMapper.selectById(todoId);
        if (todo == null) {
            throw new BusinessException("待办不存在");
        }
        Project project = projectService.requireProject(todo.getProjectId());
        if (!projectService.isRealProjectMember(project, user)) {
            throw new BusinessException("仅项目真实参与人可处理合规待办");
        }
        if (rowVersion == null || !rowVersion.equals(todo.getRowVersion())) {
            throw new BusinessException("待办已被他人更新，请刷新后重试");
        }
        LambdaUpdateWrapper<ComplianceProjectTodo> uw = new LambdaUpdateWrapper<>();
        uw.eq(ComplianceProjectTodo::getId, todoId)
                .eq(ComplianceProjectTodo::getRowVersion, rowVersion)
                .set(ComplianceProjectTodo::getStatus, status)
                .set(ComplianceProjectTodo::getHandleBy, user.getUsername())
                .set(ComplianceProjectTodo::getHandleTime, LocalDateTime.now())
                .setSql("row_version = row_version + 1");
        todoMapper.update(null, uw);
    }
}
