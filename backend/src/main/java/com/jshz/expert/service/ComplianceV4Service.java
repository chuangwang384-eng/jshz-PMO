package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.ParamUtils;
import com.jshz.expert.entity.ComplianceExecution;
import com.jshz.expert.entity.ComplianceExecutionEvent;
import com.jshz.expert.entity.ComplianceExecutionReminder;
import com.jshz.expert.entity.ComplianceExecutionTask;
import com.jshz.expert.entity.ComplianceTemplateNode;
import com.jshz.expert.entity.ComplianceTemplateOption;
import com.jshz.expert.entity.ComplianceTemplateVersion;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.entity.Project;
import com.jshz.expert.mapper.ComplianceExecutionEventMapper;
import com.jshz.expert.mapper.ComplianceExecutionMapper;
import com.jshz.expert.mapper.ComplianceExecutionReminderMapper;
import com.jshz.expert.mapper.ComplianceExecutionTaskMapper;
import com.jshz.expert.mapper.ComplianceTemplateNodeMapper;
import com.jshz.expert.mapper.ComplianceTemplateOptionMapper;
import com.jshz.expert.mapper.ComplianceTemplateVersionMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.mapper.ProjectMapper;
import com.jshz.expert.security.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 企业合规 v4 服务：可复用模板 + 个人执行清单/任务（REQ/DES-COMPLIANCE-V4）。
 * 隔离边界：执行对象 owner_user_id = 当前登录用户；每个 taskId 先联 execution 校验所有权，
 * 再检来源文档与项目可读；不信任请求 body 中的 ownerId/projectId/snapshot。
 */
@Service
@RequiredArgsConstructor
public class ComplianceV4Service {

    private final ComplianceTemplateVersionMapper templateVersionMapper;
    private final ComplianceTemplateNodeMapper templateNodeMapper;
    private final ComplianceTemplateOptionMapper templateOptionMapper;
    private final ComplianceExecutionMapper executionMapper;
    private final ComplianceExecutionTaskMapper taskMapper;
    private final ComplianceExecutionEventMapper eventMapper;
    private final ComplianceExecutionReminderMapper reminderMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final ProjectMapper projectMapper;
    private final ProjectService projectService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public static final List<String> NODE_TYPES = List.of("CHECK", "SINGLE", "MULTI", "TEXT");
    public static final List<String> PRIORITIES = List.of("HIGH", "MEDIUM", "LOW");
    public static final List<String> PROGRESS_STATES = List.of("UNPROCESSED", "PROCESSING", "COMPLETED");

    // ==================== 模板 ====================

    /** 某文档已发布模板列表（登录即可读）。 */
    public List<Map<String, Object>> listPublishedTemplates(Long documentId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ComplianceTemplateVersion t : templateVersionMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateVersion>()
                .eq(ComplianceTemplateVersion::getDocumentId, documentId)
                .eq(ComplianceTemplateVersion::getState, "PUBLISHED")
                .orderByDesc(ComplianceTemplateVersion::getRevision))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("revision", t.getRevision());
            m.put("title", t.getTitle());
            m.put("applicability", t.getApplicability());
            m.put("publishedAt", t.getPublishedAt());
            m.put("nodeCount", templateNodeMapper.selectCount(new LambdaQueryWrapper<ComplianceTemplateNode>()
                    .eq(ComplianceTemplateNode::getVersionId, t.getId())
                    .isNotNull(ComplianceTemplateNode::getInputType)));
            rows.add(m);
        }
        return rows;
    }

    /** 上传者创建自己的模板草稿（从指定已发布版本复制或空白）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createDraft(Long documentId, Long copyFromVersionId, String title, AuthUser user) {
        requireUser(user);
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) throw new BusinessException("来源文档不存在");
        ComplianceTemplateVersion t = new ComplianceTemplateVersion();
        t.setDocumentId(documentId);
        t.setSourceDocumentVersionId(copyFromVersionId);
        t.setOwnerUserId(user.getUserId());
        t.setTitle(title == null || title.isBlank() ? doc.getTitle() + " 执行规范" : title.trim());
        // revision = 同文档最大 revision + 1
        Integer maxRev = templateVersionMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateVersion>()
                .eq(ComplianceTemplateVersion::getDocumentId, documentId)
                .orderByDesc(ComplianceTemplateVersion::getRevision).last("LIMIT 1"))
                .stream().findFirst().map(ComplianceTemplateVersion::getRevision).orElse(0);
        t.setRevision(maxRev + 1);
        t.setState("DRAFT");
        if (copyFromVersionId != null) {
            ComplianceTemplateVersion src = templateVersionMapper.selectById(copyFromVersionId);
            if (src == null || !src.getDocumentId().equals(documentId)) throw new BusinessException("复制来源版本不存在");
            if (!"PUBLISHED".equals(src.getState())) throw new BusinessException("只能从已发布版本复制");
            t.setApplicability(src.getApplicability());
            if (title == null || title.isBlank()) t.setTitle(src.getTitle() + "（新修订草稿）");
        }
        templateVersionMapper.insert(t);
        if (copyFromVersionId != null) {
            // 复制节点树与选项（草稿可改，不影响旧版本）
            for (ComplianceTemplateNode n : templateNodeMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateNode>()
                    .eq(ComplianceTemplateNode::getVersionId, copyFromVersionId).orderByAsc(ComplianceTemplateNode::getId))) {
                ComplianceTemplateNode nn = new ComplianceTemplateNode();
                nn.setVersionId(t.getId());
                nn.setParentId(n.getParentId());
                nn.setNodeCode(n.getNodeCode());
                nn.setTitle(n.getTitle());
                nn.setStage(n.getStage());
                nn.setInputType(n.getInputType());
                nn.setInstruction(n.getInstruction());
                nn.setMaterials(n.getMaterials());
                nn.setAcceptance(n.getAcceptance());
                nn.setTimeRule(n.getTimeRule());
                nn.setRequired(n.getRequired());
                nn.setSourceAnchor(n.getSourceAnchor());
                nn.setSort(n.getSort());
                templateNodeMapper.insert(nn);
                for (ComplianceTemplateOption o : templateOptionMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateOption>()
                        .eq(ComplianceTemplateOption::getNodeId, n.getId()).orderByAsc(ComplianceTemplateOption::getSort))) {
                    ComplianceTemplateOption no = new ComplianceTemplateOption();
                    no.setNodeId(nn.getId());
                    no.setOptionCode(o.getOptionCode());
                    no.setLabel(o.getLabel());
                    no.setGuidance(o.getGuidance());
                    no.setSort(o.getSort());
                    templateOptionMapper.insert(no);
                }
            }
        }
        return templateDetail(t.getId(), user);
    }

    /** 模板完整草稿树（nodes+options）。权限：owner 本人或维护人可读草稿；已发布版本登录可读结构。 */
    public Map<String, Object> templateDetail(Long templateVersionId, AuthUser user) {
        ComplianceTemplateVersion t = templateVersionMapper.selectById(templateVersionId);
        if (t == null) throw new BusinessException("模板版本不存在");
        boolean owner = t.getOwnerUserId().equals(ParamUtils.userIdOf(user));
        boolean maintainer = user != null && user.hasPermission("knowledge:maintain");
        if ("DRAFT".equals(t.getState()) && !owner && !maintainer) {
            throw new BusinessException("无权查看该草稿");
        }
        List<ComplianceTemplateNode> nodes = templateNodeMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateNode>()
                .eq(ComplianceTemplateNode::getVersionId, templateVersionId).orderByAsc(ComplianceTemplateNode::getSort));
        List<Map<String, Object>> nodeRows = new ArrayList<>();
        for (ComplianceTemplateNode n : nodes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("parentId", n.getParentId());
            m.put("nodeCode", n.getNodeCode());
            m.put("title", n.getTitle());
            m.put("stage", n.getStage());
            m.put("inputType", n.getInputType());
            m.put("instruction", n.getInstruction());
            m.put("materials", n.getMaterials());
            m.put("acceptance", n.getAcceptance());
            m.put("timeRule", n.getTimeRule());
            m.put("required", n.getRequired());
            m.put("sourceAnchor", n.getSourceAnchor());
            m.put("conditionOptionId", n.getConditionOptionId());
            m.put("sort", n.getSort());
            if ("SINGLE".equals(n.getInputType()) || "MULTI".equals(n.getInputType())) {
                List<Map<String, Object>> opts = new ArrayList<>();
                for (ComplianceTemplateOption o : templateOptionMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateOption>()
                        .eq(ComplianceTemplateOption::getNodeId, n.getId()).orderByAsc(ComplianceTemplateOption::getSort))) {
                    Map<String, Object> om = new LinkedHashMap<>();
                    om.put("id", o.getId());
                    om.put("optionCode", o.getOptionCode());
                    om.put("label", o.getLabel());
                    om.put("guidance", o.getGuidance());
                    om.put("sort", o.getSort());
                    opts.add(om);
                }
                m.put("options", opts);
            }
            nodeRows.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", t.getId());
        result.put("documentId", t.getDocumentId());
        result.put("revision", t.getRevision());
        result.put("state", t.getState());
        result.put("title", t.getTitle());
        result.put("applicability", t.getApplicability());
        result.put("rowVersion", t.getRowVersion());
        result.put("ownerUserId", t.getOwnerUserId());
        result.put("nodes", nodeRows);
        return result;
    }

    /**
     * 编辑草稿（owner 或维护人）：白名单元数据 + 整树替换（草稿可改，发布后不可原地改写）。
     * 树校验：node_code 同版本唯一、两级、condition 引用同版本前置单/多选选项、无自环。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> patchTemplate(Long templateVersionId, Map<String, Object> body, AuthUser user) {
        ComplianceTemplateVersion t = templateVersionMapper.selectById(templateVersionId);
        if (t == null) throw new BusinessException("模板版本不存在");
        boolean owner = t.getOwnerUserId().equals(ParamUtils.userIdOf(user));
        boolean maintainer = user != null && user.hasPermission("knowledge:maintain");
        if (!owner && !maintainer) throw new BusinessException("无权编辑该模板");
        if (!"DRAFT".equals(t.getState())) throw new BusinessException("已发布版本不可原地修改，请复制新修订草稿");
        Integer clientRowVersion = ParamUtils.parseLong(body.get("rowVersion"), null) == null ? null : ParamUtils.parseLong(body.get("rowVersion"), null).intValue();
        if (clientRowVersion != null && !clientRowVersion.equals(t.getRowVersion())) {
            throw new BusinessException(409, "模板已被他人修改，请刷新后比较");
        }
        if (body.containsKey("title")) {
            String title = String.valueOf(body.get("title"));
            if (!title.isBlank()) t.setTitle(title.trim());
        }
        if (body.containsKey("applicability")) t.setApplicability((String) body.get("applicability"));
        t.setRowVersion((t.getRowVersion() == null ? 0 : t.getRowVersion()) + 1);
        templateVersionMapper.updateById(t);

        // 整树替换
        if (body.get("nodes") instanceof List<?> nodesIn) {
            List<Long> oldNodeIds = templateNodeMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateNode>()
                    .eq(ComplianceTemplateNode::getVersionId, templateVersionId))
                    .stream().map(ComplianceTemplateNode::getId).toList();
            if (!oldNodeIds.isEmpty()) {
                templateOptionMapper.delete(new LambdaQueryWrapper<ComplianceTemplateOption>()
                        .in(ComplianceTemplateOption::getNodeId, oldNodeIds));
                templateNodeMapper.delete(new LambdaQueryWrapper<ComplianceTemplateNode>()
                        .eq(ComplianceTemplateNode::getVersionId, templateVersionId));
            }
            Set<String> codes = new HashSet<>();
            Map<String, Long> codeToId = new HashMap<>();
            int sort = 0;
            for (Object o : nodesIn) {
                if (!(o instanceof Map<?, ?> n)) continue;
                String code = str(n.get("nodeCode"));
                String title = str(n.get("title"));
                String type = str(n.get("inputType"));
                if (code == null || code.isBlank() || title == null || title.isBlank()) continue;
                if (!codes.add(code)) throw new BusinessException("节点标识重复：" + code);
                if (!NODE_TYPES.contains(type)) throw new BusinessException("非法输入类型：" + type);
                boolean isGroup = "GROUP".equals(type);
                ComplianceTemplateNode node = new ComplianceTemplateNode();
                node.setVersionId(templateVersionId);
                node.setNodeCode(code);
                node.setTitle(title.trim());
                node.setStage(str(n.get("stage")));
                node.setInputType(isGroup ? "GROUP" : type);
                node.setInstruction(str(n.get("instruction")));
                node.setMaterials(str(n.get("materials")));
                node.setAcceptance(str(n.get("acceptance")));
                node.setTimeRule(str(n.get("timeRule")));
                node.setRequired(Boolean.TRUE.equals(n.get("required")) || "1".equals(String.valueOf(n.get("required"))) ? 1 : 0);
                node.setSourceAnchor(str(n.get("sourceAnchor")));
                node.setSort(sort++);
                templateNodeMapper.insert(node);
                codeToId.put(code, node.getId());
                // 选项
                if (n.get("options") instanceof List<?> optsIn) {
                    if (!"SINGLE".equals(type) && !"MULTI".equals(type)) {
                        throw new BusinessException("仅单选/多选节点可配置选项");
                    }
                    int os = 0;
                    Set<String> optCodes = new HashSet<>();
                    for (Object oo : optsIn) {
                        if (!(oo instanceof Map<?, ?> om)) continue;
                        String oc = str(om.get("optionCode"));
                        String ol = str(om.get("label"));
                        if (oc == null || oc.isBlank() || ol == null || ol.isBlank()) continue;
                        if (!optCodes.add(oc)) throw new BusinessException("选项标识重复：" + oc);
                        ComplianceTemplateOption opt = new ComplianceTemplateOption();
                        opt.setNodeId(node.getId());
                        opt.setOptionCode(oc);
                        opt.setLabel(ol.trim());
                        opt.setGuidance(str(om.get("guidance")));
                        opt.setSort(os++);
                        templateOptionMapper.insert(opt);
                    }
                }
            }
            // 两级：parent 归组（GROUP 节点的 children 通过 parentId=分组 id 表达，前端按 parentId 提交）
            for (Object o : nodesIn) {
                if (!(o instanceof Map<?, ?> n)) continue;
                String code = str(n.get("nodeCode"));
                Object parentCode = n.get("parentCode");
                if (parentCode != null && !String.valueOf(parentCode).isBlank()) {
                    Long pid = codeToId.get(String.valueOf(parentCode));
                    Long selfId = codeToId.get(code);
                    if (pid != null && selfId != null && !pid.equals(selfId)) {
                        ComplianceTemplateNode node = templateNodeMapper.selectById(selfId);
                        node.setParentId(pid);
                        templateNodeMapper.updateById(node);
                    }
                }
            }
            // condition_option_id 校验：引用同版本前置单/多选节点选项
            for (ComplianceTemplateNode n : templateNodeMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateNode>()
                    .eq(ComplianceTemplateNode::getVersionId, templateVersionId))) {
                if (n.getConditionOptionId() == null) continue;
                ComplianceTemplateOption opt = templateOptionMapper.selectById(n.getConditionOptionId());
                if (opt == null) throw new BusinessException("分支引用的选项不存在");
                ComplianceTemplateNode owner2 = templateNodeMapper.selectById(opt.getNodeId());
                if (owner2 == null || !owner2.getVersionId().equals(templateVersionId)
                        || !("SINGLE".equals(owner2.getInputType()) || "MULTI".equals(owner2.getInputType()))
                        || owner2.getId().equals(n.getId())) {
                    throw new BusinessException("分支条件只能引用同版本前置单选/多选节点");
                }
            }
        }
        return templateDetail(templateVersionId, user);
    }

    /** 发布（维护权限；原子发布；树校验；必做任务不可为空集）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publish(Long templateVersionId, AuthUser user) {
        requireMaintainer(user);
        ComplianceTemplateVersion t = templateVersionMapper.selectById(templateVersionId);
        if (t == null) throw new BusinessException("模板版本不存在");
        if (!"DRAFT".equals(t.getState())) throw new BusinessException("仅草稿可发布");
        List<ComplianceTemplateNode> nodes = templateNodeMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateNode>()
                .eq(ComplianceTemplateNode::getVersionId, templateVersionId));
        long leafCount = nodes.stream().filter(n -> !"GROUP".equals(n.getInputType())).count();
        if (leafCount == 0) throw new BusinessException("模板至少需要一个执行细项");
        t.setState("PUBLISHED");
        t.setPublishedBy(user == null ? null : user.getUserId());
        t.setPublishedAt(java.time.LocalDateTime.now());
        templateVersionMapper.updateById(t);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("published", true);
        m.put("templateVersionId", t.getId());
        m.put("revision", t.getRevision());
        return m;
    }

    // ==================== 执行清单 ====================

    /** 创建执行清单（幂等键防重复；快照任务生成；DRAFT 未确认不入待办）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createExecution(Map<String, Object> body, AuthUser user) {
        requireUser(user);
        Long templateVersionId = ParamUtils.parseLong(body.get("templateVersionId"), null);
        if (templateVersionId == null) throw new BusinessException("templateVersionId 不能为空");
        ComplianceTemplateVersion t = templateVersionMapper.selectById(templateVersionId);
        if (t == null || !"PUBLISHED".equals(t.getState())) throw new BusinessException("模板不存在或未发布");
        Long projectId = ParamUtils.parseLong(body.get("projectId"), null);
        if (projectId != null) {
            Project project = projectMapper.selectById(projectId);
            if (project == null) throw new BusinessException("关联项目不存在");
            projectService.requireProjectReadable(project, user); // 无项目权限不能挂靠项目（AC11）
        }
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) throw new BusinessException("清单名称必填（无项目时名称必填）");
        String idem = str(body.get("idempotencyKey"));
        if (idem == null || idem.isBlank()) throw new BusinessException("idempotencyKey 不能为空（防重复提交）");

        ComplianceExecution e = new ComplianceExecution();
        e.setOwnerUserId(user.getUserId());
        e.setProjectId(projectId);
        e.setTemplateVersionId(templateVersionId);
        e.setSourceDocumentId(t.getDocumentId());
        e.setName(name.trim());
        e.setState("DRAFT");
        e.setSourceKind("TEMPLATE");
        e.setIdempotencyKey(idem.trim());
        try {
            executionMapper.insert(e);
        } catch (DuplicateKeyException dup) {
            // 同幂等键并发：返回已存在清单（AC09）
            ComplianceExecution exist = executionMapper.selectOne(new LambdaQueryWrapper<ComplianceExecution>()
                    .eq(ComplianceExecution::getOwnerUserId, user.getUserId())
                    .eq(ComplianceExecution::getIdempotencyKey, idem.trim()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("executionId", exist.getId());
            m.put("alreadyExists", true);
            return m;
        }
        event(e.getId(), null, user.getUserId(), "CREATE", "{\"name\":\"" + escape(name.trim()) + "\"}");

        // 任务快照：selectedNodeCodes 为空则全部适用细项
        List<String> selected = new ArrayList<>();
        if (body.get("selectedNodeCodes") instanceof List<?> selIn) {
            for (Object o : selIn) selected.add(String.valueOf(o));
        }
        List<ComplianceTemplateNode> leaves = templateNodeMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateNode>()
                .eq(ComplianceTemplateNode::getVersionId, templateVersionId)
                .isNotNull(ComplianceTemplateNode::getInputType)
                .ne(ComplianceTemplateNode::getInputType, "GROUP")
                .orderByAsc(ComplianceTemplateNode::getSort));
        for (ComplianceTemplateNode n : leaves) {
            if (!selected.isEmpty() && !selected.contains(n.getNodeCode())) continue;
            ComplianceExecutionTask task = new ComplianceExecutionTask();
            task.setExecutionId(e.getId());
            task.setSourceNodeCode(n.getNodeCode());
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("nodeCode", n.getNodeCode());
            snap.put("title", n.getTitle());
            snap.put("inputType", n.getInputType());
            snap.put("instruction", n.getInstruction());
            snap.put("materials", n.getMaterials());
            snap.put("acceptance", n.getAcceptance());
            snap.put("timeRule", n.getTimeRule());
            snap.put("required", n.getRequired());
            snap.put("stage", n.getStage());
            if ("SINGLE".equals(n.getInputType()) || "MULTI".equals(n.getInputType())) {
                List<Map<String, Object>> opts = new ArrayList<>();
                for (ComplianceTemplateOption o : templateOptionMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateOption>()
                        .eq(ComplianceTemplateOption::getNodeId, n.getId()).orderByAsc(ComplianceTemplateOption::getSort))) {
                    Map<String, Object> om = new LinkedHashMap<>();
                    om.put("optionCode", o.getOptionCode());
                    om.put("label", o.getLabel());
                    om.put("guidance", o.getGuidance());
                    opts.add(om);
                }
                snap.put("options", opts);
            }
            try {
                task.setSnapshot(objectMapper.writeValueAsString(snap));
            } catch (com.fasterxml.jackson.core.JsonProcessingException jpe) {
                task.setSnapshot(snap.toString());
            }
            task.setApplicability("APPLICABLE");
            task.setPriority("MEDIUM");
            task.setProgress("UNPROCESSED");
            task.setRemindEnabled(1);
            task.setRemindDays(3);
            task.setScheduleRevision(0);
            taskMapper.insert(task);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("executionId", e.getId());
        m.put("taskCount", taskMapper.selectCount(new LambdaQueryWrapper<ComplianceExecutionTask>()
                .eq(ComplianceExecutionTask::getExecutionId, e.getId())));
        return m;
    }

    /** 草稿确认开始（DRAFT→ACTIVE；进入待办并按 due_date 建立提醒节点）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startExecution(Long executionId, Map<String, Object> body, AuthUser user) {
        ComplianceExecution e = ownedExecution(executionId, user);
        if (!"DRAFT".equals(e.getState())) throw new BusinessException("仅草稿可开始");
        e.setState("ACTIVE");
        e.setStartAt(java.time.LocalDateTime.now());
        executionMapper.updateById(e);
        event(e.getId(), null, user.getUserId(), "START", null);
        // 为已设置 due_date 的任务生成提醒节点
        int reminders = 0;
        for (ComplianceExecutionTask task : tasksOf(e.getId())) {
            if (task.getDueDate() != null && task.getRemindEnabled() != null && task.getRemindEnabled() == 1) {
                reminders += scheduleTaskReminders(task, e);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("started", true);
        m.put("remindersScheduled", reminders);
        return m;
    }

    /** 本人清单分页（state/projectId/documentId/search 筛选）。 */
    public Map<String, Object> listMyExecutions(Long userId, String state, Long projectId, Long documentId,
                                                String search, long page, long size) {
        if (page < 1 || size < 1 || size > 200) throw new BusinessException("分页参数超出范围");
        LambdaQueryWrapper<ComplianceExecution> qw = new LambdaQueryWrapper<ComplianceExecution>()
                .eq(ComplianceExecution::getOwnerUserId, userId);
        if (state != null && !state.isBlank()) qw.eq(ComplianceExecution::getState, state);
        if (projectId != null) qw.eq(ComplianceExecution::getProjectId, projectId);
        if (documentId != null) qw.eq(ComplianceExecution::getSourceDocumentId, documentId);
        if (search != null && !search.isBlank()) qw.like(ComplianceExecution::getName, search);
        qw.orderByDesc(ComplianceExecution::getUpdatedAt);
        List<ComplianceExecution> all = executionMapper.selectList(qw);
        int from = page > (all.size() / size) + 1 ? all.size() : (int) Math.min(all.size(), (page - 1) * size);
        int to = (int) Math.min(all.size(), from + size);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ComplianceExecution e : all.subList(from, to)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("name", e.getName());
            m.put("state", e.getState());
            m.put("projectId", e.getProjectId());
            m.put("templateVersionId", e.getTemplateVersionId());
            m.put("sourceDocumentId", e.getSourceDocumentId());
            m.put("updatedAt", e.getUpdatedAt());
            long total = taskMapper.selectCount(new LambdaQueryWrapper<ComplianceExecutionTask>()
                    .eq(ComplianceExecutionTask::getExecutionId, e.getId())
                    .eq(ComplianceExecutionTask::getApplicability, "APPLICABLE"));
            long done = taskMapper.selectCount(new LambdaQueryWrapper<ComplianceExecutionTask>()
                    .eq(ComplianceExecutionTask::getExecutionId, e.getId())
                    .eq(ComplianceExecutionTask::getApplicability, "APPLICABLE")
                    .eq(ComplianceExecutionTask::getProgress, "COMPLETED"));
            m.put("taskTotal", total);
            m.put("taskDone", done);
            rows.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("items", rows);
        return result;
    }

    /** 本人清单详情（含任务列表；来源可用性标注）。 */
    public Map<String, Object> executionDetail(Long executionId, AuthUser user) {
        ComplianceExecution e = ownedExecution(executionId, user);
        KnowledgeDocument doc = documentMapper.selectById(e.getSourceDocumentId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("state", e.getState());
        m.put("projectId", e.getProjectId());
        m.put("templateVersionId", e.getTemplateVersionId());
        m.put("sourceDocumentId", e.getSourceDocumentId());
        m.put("sourceDocumentTitle", doc == null ? null : doc.getTitle());
        // 来源可用性：普通停用→可用性降级提示；永久删除→来源不可用（FR09/AC08）
        boolean docAlive = doc != null && doc.getStatus() != null && doc.getStatus() == 1;
        m.put("sourceAvailable", docAlive);
        if (!docAlive) m.put("sourceNotice", "来源文档已停用或删除，请核对该清单依据（历史保留）");
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (ComplianceExecutionTask t : tasksOf(e.getId())) {
            tasks.add(taskMap(t));
        }
        m.put("tasks", tasks);
        return m;
    }

    /** 再次使用：新清单，不复制状态/日期/备注/提醒；copyAnswers 明确选择才复制答案。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reuseExecution(Long executionId, Map<String, Object> body, AuthUser user) {
        ComplianceExecution src = ownedExecution(executionId, user);
        String idem = str(body.get("idempotencyKey"));
        if (idem == null || idem.isBlank()) throw new BusinessException("idempotencyKey 不能为空");
        Long projectId = ParamUtils.parseLong(body.get("projectId"), null);
        if (projectId != null) {
            Project project = projectMapper.selectById(projectId);
            if (project == null) throw new BusinessException("关联项目不存在");
            projectService.requireProjectReadable(project, user);
        }
        String name = str(body.get("name"));
        boolean copyAnswers = Boolean.TRUE.equals(body.get("copyAnswers"));
        ComplianceExecution e = new ComplianceExecution();
        e.setOwnerUserId(user.getUserId());
        e.setProjectId(projectId);
        e.setTemplateVersionId(src.getTemplateVersionId());
        e.setSourceDocumentId(src.getSourceDocumentId());
        e.setName(name == null || name.isBlank() ? src.getName() + "（再次使用）" : name.trim());
        e.setState("DRAFT");
        e.setSourceKind("TEMPLATE");
        e.setIdempotencyKey(idem.trim());
        try {
            executionMapper.insert(e);
        } catch (DuplicateKeyException dup) {
            ComplianceExecution exist = executionMapper.selectOne(new LambdaQueryWrapper<ComplianceExecution>()
                    .eq(ComplianceExecution::getOwnerUserId, user.getUserId())
                    .eq(ComplianceExecution::getIdempotencyKey, idem.trim()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("executionId", exist.getId());
            m.put("alreadyExists", true);
            return m;
        }
        // 复制快照任务：默认只继承适用项选择；答案可选；日期/进度/备注/提醒重置
        Map<String, String> oldAnswers = new HashMap<>();
        if (copyAnswers) {
            for (ComplianceExecutionTask t : tasksOf(src.getId())) {
                if (t.getAnswer() != null) oldAnswers.put(String.valueOf(t.getId()), t.getAnswer());
            }
        }
        int copied = 0;
        for (ComplianceExecutionTask t : tasksOf(src.getId())) {
            if (!"APPLICABLE".equals(t.getApplicability())) continue;
            ComplianceExecutionTask nt = new ComplianceExecutionTask();
            nt.setExecutionId(e.getId());
            nt.setSourceNodeCode(t.getSourceNodeCode());
            nt.setSnapshot(t.getSnapshot());
            if (copyAnswers && t.getAnswer() != null) {
                nt.setAnswer(t.getAnswer());
                // 候选值变化/失效须重新确认：复制答案时标记待确认（前端提示重新核对）
            }
            nt.setApplicability("APPLICABLE");
            nt.setPriority("MEDIUM");
            nt.setProgress("UNPROCESSED");
            nt.setRemindEnabled(1);
            nt.setRemindDays(3);
            nt.setScheduleRevision(0);
            taskMapper.insert(nt);
            copied++;
        }
        event(e.getId(), null, user.getUserId(), "REUSE", "{\"sourceExecutionId\":" + src.getId() + ",\"copiedTasks\":" + copied + "}");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("executionId", e.getId());
        m.put("copiedTasks", copied);
        return m;
    }

    // ==================== 任务更新 ====================

    /**
     * 任务更新（仅本人）：rowVersion 乐观锁（冲突 409）；answer 白名单校验；
     * 完成校验必填答案；completed_at 同事务；due/等待变更联动提醒 revision。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> patchTask(Long taskId, Map<String, Object> body, AuthUser user) {
        requireUser(user);
        ComplianceExecutionTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        ComplianceExecution e = ownedExecution(task.getExecutionId(), user);
        Integer rv = ParamUtils.parseLong(body.get("rowVersion"), null) == null ? null : ParamUtils.parseLong(body.get("rowVersion"), null).intValue();
        if (rv != null && !rv.equals(task.getRowVersion())) {
            throw new BusinessException(409, "任务已被修改，请刷新比较后重试");
        }
        List<String> changes = new ArrayList<>();

        // answer（JSON 白名单校验：按 snapshot.input_type）
        if (body.containsKey("answer")) {
            Object answer = body.get("answer");
            String snap = task.getSnapshot();
            String err = validateAnswer(snap, answer);
            if (err != null) throw new BusinessException(err);
            String oldAnswer = task.getAnswer();
            task.setAnswer(answer == null ? null : jsonStr(answer));
            changes.add("answer");
            if (!"COMPLETED".equals(task.getProgress())) {
                // 填写后视为处理中（可选）
            }
            event(e.getId(), task.getId(), ParamUtils.userIdOf(user), "ANSWER",
                    "{\"old\":" + (oldAnswer == null ? "null" : oldAnswer) + ",\"new\":" + (answer == null ? "null" : jsonStr(answer)) + "}");
        }

        // dueDate：null 明确清空；missing 不改
        if (body.containsKey("dueDate")) {
            Object due = body.get("dueDate");
            LocalDate oldDue = task.getDueDate();
            if (due == null || String.valueOf(due).isBlank()) {
                task.setDueDate(null);
            } else {
                task.setDueDate(LocalDate.parse(String.valueOf(due), DateTimeFormatter.ISO_LOCAL_DATE));
            }
            if (!java.util.Objects.equals(oldDue, task.getDueDate())) {
                changes.add("dueDate");
                bumpRevision(task);
                event(e.getId(), task.getId(), ParamUtils.userIdOf(user), "DUE",
                        "{\"old\":\"" + oldDue + "\",\"new\":\"" + task.getDueDate() + "\"}");
                // 改期：按新日期重算提醒节点（旧节点已由 bumpRevision 置 CANCELLED，不重发旧节点）
                if (task.getDueDate() != null && task.getRemindEnabled() != null && task.getRemindEnabled() == 1
                        && !"COMPLETED".equals(task.getProgress()) && "ACTIVE".equals(e.getState())) {
                    scheduleTaskReminders(task, e);
                }
            }
        }

        if (body.containsKey("priority")) {
            String p = str(body.get("priority"));
            if (p != null && !PRIORITIES.contains(p)) throw new BusinessException("非法优先级");
            if (p != null && !p.equals(task.getPriority())) {
                task.setPriority(p);
                changes.add("priority");
                event(e.getId(), task.getId(), ParamUtils.userIdOf(user), "PRIORITY", "{\"new\":\"" + p + "\"}");
            }
        }

        // progress：完成校验必填答案；重开清 completed_at；完成/重开联动提醒
        if (body.containsKey("progress")) {
            String progress = str(body.get("progress"));
            if (!PROGRESS_STATES.contains(progress)) throw new BusinessException("非法进度值");
            if ("COMPLETED".equals(progress)) {
                // 必填缺失不能完成；answer 非空判断用已提交答案
                String missingErr = requiredMissingError(task);
                if (missingErr != null) throw new BusinessException(missingErr);
            }
            String oldProgress = task.getProgress();
            if (!oldProgress.equals(progress)) {
                task.setProgress(progress);
                if ("COMPLETED".equals(progress)) {
                    task.setCompletedAt(java.time.LocalDateTime.now());
                    task.setWaitingReason(null);
                    task.setNextAction(null);
                    task.setReviewDate(null);
                    bumpRevision(task); // 完成/清除等待取消未发送节点
                } else {
                    task.setCompletedAt(null);
                    if ("COMPLETED".equals(oldProgress)) {
                        bumpRevision(task); // 重开重建提醒
                    }
                }
                changes.add("progress");
                event(e.getId(), task.getId(), ParamUtils.userIdOf(user),
                        "COMPLETED".equals(progress) ? "COMPLETE" : "PROGRESS",
                        "{\"old\":\"" + oldProgress + "\",\"new\":\"" + progress + "\"}");
                // 完成后重新生成提醒（重开场景）；完成则无新节点
                if (!"COMPLETED".equals(progress) && task.getDueDate() != null
                        && task.getRemindEnabled() != null && task.getRemindEnabled() == 1) {
                    scheduleTaskReminders(task, e);
                }
            }
        }

        // note（≤2000 字，超限拒绝不静默截断）
        if (body.containsKey("note")) {
            Object note = body.get("note");
            String noteStr = note == null ? null : String.valueOf(note);
            if (noteStr != null && noteStr.length() > 2000) throw new BusinessException("备注不能超过 2000 字");
            String oldNote = task.getNote();
            task.setNote(noteStr);
            if (!java.util.Objects.equals(oldNote, noteStr)) {
                changes.add("note");
                event(e.getId(), task.getId(), ParamUtils.userIdOf(user), "NOTE",
                        "{\"old\":" + (oldNote == null ? "null" : jsonStr(oldNote)) + ",\"new\":" + (noteStr == null ? "null" : jsonStr(noteStr)) + "}");
            }
        }

        // 等待跟进（FR07）：进度保持 PROCESSING；完成/清除取消 review 节点
        if (body.containsKey("waiting")) {
            Object w = body.get("waiting");
            if (w == null) {
                // 明确清除等待
                task.setWaitingReason(null);
                task.setNextAction(null);
                task.setReviewDate(null);
                bumpRevision(task);
                changes.add("waiting-clear");
                event(e.getId(), task.getId(), ParamUtils.userIdOf(user), "WAIT_CLEAR", null);
            } else if (w instanceof Map<?, ?> wm) {
                String reason = str(wm.get("reason"));
                String next = str(wm.get("nextAction"));
                Object review = wm.get("reviewDate");
                if ((reason == null || reason.isBlank()) && (next == null || next.isBlank())) {
                    throw new BusinessException("请填写“在等什么”或“下一步行动”");
                }
                if ("COMPLETED".equals(task.getProgress())) throw new BusinessException("已完成任务无需等待跟进");
                task.setWaitingReason(reason);
                task.setNextAction(next);
                if (review != null && !String.valueOf(review).isBlank()) {
                    task.setReviewDate(LocalDate.parse(String.valueOf(review), DateTimeFormatter.ISO_LOCAL_DATE));
                } else {
                    task.setReviewDate(null);
                }
                if (!"PROCESSING".equals(task.getProgress())) task.setProgress("PROCESSING");
                changes.add("waiting");
                event(e.getId(), task.getId(), ParamUtils.userIdOf(user), "WAITING",
                        jsonStr(Map.of("reason", nullToEmpty(reason), "next", nullToEmpty(next), "review", nullToEmpty(String.valueOf(review)))));
                // review 节点由 Scheduler 扫描 review_date=今天 生成/发送
            }
        }

        if (changes.isEmpty() && rv == null) {
            // 无有效变更且未带版本：仅返回现状
        }
        int previousVersion = task.getRowVersion();
        task.setRowVersion(previousVersion + 1);
        // 显式 set 全部可变字段：MyBatis-Plus updateById 默认忽略 null，无法清空答案/备注/等待/日期
        int updated = taskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ComplianceExecutionTask>()
                .eq(ComplianceExecutionTask::getId, task.getId())
                .eq(ComplianceExecutionTask::getRowVersion, previousVersion)
                .set(ComplianceExecutionTask::getAnswer, task.getAnswer())
                .set(ComplianceExecutionTask::getDueDate, task.getDueDate())
                .set(ComplianceExecutionTask::getPriority, task.getPriority())
                .set(ComplianceExecutionTask::getProgress, task.getProgress())
                .set(ComplianceExecutionTask::getNote, task.getNote())
                .set(ComplianceExecutionTask::getWaitingReason, task.getWaitingReason())
                .set(ComplianceExecutionTask::getNextAction, task.getNextAction())
                .set(ComplianceExecutionTask::getReviewDate, task.getReviewDate())
                .set(ComplianceExecutionTask::getScheduleRevision, task.getScheduleRevision())
                .set(ComplianceExecutionTask::getCompletedAt, task.getCompletedAt())
                .set(ComplianceExecutionTask::getApplicability, task.getApplicability())
                .set(ComplianceExecutionTask::getRemindEnabled, task.getRemindEnabled())
                .set(ComplianceExecutionTask::getRemindDays, task.getRemindDays())
                .set(ComplianceExecutionTask::getRowVersion, task.getRowVersion()));
        if (updated != 1) throw new BusinessException(409, "任务已被修改，请刷新比较后重试");
        if (!changes.isEmpty()) {
            event(e.getId(), task.getId(), ParamUtils.userIdOf(user), "UPDATE", "{\"fields\":" + jsonStr(changes) + "}");
        }
        return taskMap(taskMapper.selectById(taskId));
    }

    // ==================== 查询 ====================

    /** 我的任务列表（statusScope=ACTIVE/COMPLETED；已完成服务端过滤，不占默认列表）。 */
    public Map<String, Object> myTasks(AuthUser user, String statusScope, Long projectId, Long executionId,
                                       Long documentId, String search, long page, long size) {
        requireUser(user);
        if (page < 1 || size < 1 || size > 200) throw new BusinessException("分页参数超出范围");
        String scope = statusScope == null || statusScope.isBlank() ? "ACTIVE" : statusScope;
        if (!List.of("ACTIVE", "COMPLETED").contains(scope)) throw new BusinessException("非法任务范围");
        // 本人清单（含 ACTIVE/PAUSED 活跃清单的任务；COMPLETED 页签含 COMPLETED 清单任务）
        List<Long> execIds = new ArrayList<>();
        Map<Long, ComplianceExecution> execMap = new LinkedHashMap<>();
        List<String> states = "COMPLETED".equals(scope)
                ? List.of("ACTIVE", "PAUSED", "COMPLETED")  // 完成记录：只要任务完成即可见（不要求整单完成）
                : List.of("ACTIVE", "PAUSED");
        for (ComplianceExecution e : executionMapper.selectList(new LambdaQueryWrapper<ComplianceExecution>()
                .eq(ComplianceExecution::getOwnerUserId, user.getUserId())
                .in(ComplianceExecution::getState, states))) {
            execIds.add(e.getId());
            execMap.put(e.getId(), e);
        }
        if (executionId != null) {
            execIds = execIds.contains(executionId) ? List.of(executionId) : new ArrayList<>();
        }
        List<Map<String, Object>> all = new ArrayList<>();
        if (!execIds.isEmpty()) {
            LambdaQueryWrapper<ComplianceExecutionTask> qw = new LambdaQueryWrapper<ComplianceExecutionTask>()
                    .in(ComplianceExecutionTask::getExecutionId, execIds)
                    .eq(ComplianceExecutionTask::getApplicability, "APPLICABLE");
            if ("ACTIVE".equals(scope)) qw.ne(ComplianceExecutionTask::getProgress, "COMPLETED");
            else qw.eq(ComplianceExecutionTask::getProgress, "COMPLETED");
            if (search != null && !search.isBlank()) {
                qw.like(ComplianceExecutionTask::getSnapshot, search);
            }
            List<ComplianceExecutionTask> tasks = taskMapper.selectList(qw);
            // projectId/documentId 过滤（经 execution 冗余字段）
            for (ComplianceExecutionTask t : tasks) {
                ComplianceExecution e = execMap.get(t.getExecutionId());
                if (projectId != null && !projectId.equals(e.getProjectId())) continue;
                if (documentId != null && !documentId.equals(e.getSourceDocumentId())) continue;
                Map<String, Object> m = taskMap(t);
                m.put("executionId", e.getId());
                m.put("executionName", e.getName());
                m.put("executionState", e.getState());
                m.put("projectId", e.getProjectId());
                m.put("sourceDocumentId", e.getSourceDocumentId());
                all.add(m);
            }
            // FR08 排序：逾期 → 今天 → 3 天内 → 已排期 → 未排期；组内高优、到期升序
            all.sort((a, b) -> {
                int ga = sortGroup((LocalDate) a.get("dueDate"));
                int gb = sortGroup((LocalDate) b.get("dueDate"));
                if (ga != gb) return ga - gb;
                int pa = prioRank((String) a.get("priority"));
                int pb = prioRank((String) b.get("priority"));
                if (pa != pb) return pa - pb;
                LocalDate da = (LocalDate) a.get("dueDate");
                LocalDate db = (LocalDate) b.get("dueDate");
                if (da == null || db == null) return 0;
                return da.compareTo(db);
            });
        }
        // 统计（所有本人活跃任务，不随分页）
        int todoN = 0, nearN = 0, overdueN = 0, unscheduledN = 0;
        LocalDate today = LocalDate.now();
        for (Map<String, Object> row : all) {
            LocalDate due = (LocalDate) row.get("dueDate");
            String prog = (String) row.get("progress");
            boolean done = "COMPLETED".equals(prog);
            if (done) continue;
            if (due == null) unscheduledN++;
            else if (due.isBefore(today)) overdueN++;
            else if (!due.isAfter(today.plusDays(3))) nearN++;
            todoN++;
        }
        int from = page > (all.size() / size) + 1 ? all.size() : (int) Math.min(all.size(), (page - 1) * size);
        int to = (int) Math.min(all.size(), from + size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("stats", Map.of("todoCount", todoN, "nearDueCount", nearN, "overdueCount", overdueN, "unscheduledCount", unscheduledN));
        result.put("items", all.subList(from, to));
        return result;
    }

    private int sortGroup(LocalDate due) {
        if (due == null) return 5;
        LocalDate today = LocalDate.now();
        if (due.isBefore(today)) return 0;
        if (due.isEqual(today)) return 1;
        if (!due.isAfter(today.plusDays(3))) return 2;
        return 3;
    }

    private int prioRank(String p) {
        return switch (p == null ? "" : p) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    // ==================== 暂停 / 归档 / 事件 ====================

    @Transactional(rollbackFor = Exception.class)
    public void pauseOrArchive(Long executionId, String action, AuthUser user) {
        ComplianceExecution e = ownedExecution(executionId, user);
        String target = "pause".equals(action) ? "PAUSED" : "ARCHIVED";
        if (!"ACTIVE".equals(e.getState())) throw new BusinessException("仅进行中清单可" + ("pause".equals(action) ? "暂停" : "归档"));
        e.setState(target);
        e.setClosedAt("ARCHIVED".equals(target) ? java.time.LocalDateTime.now() : null);
        executionMapper.updateById(e);
        bumpAllRevisions(e.getId()); // 取消未发送提醒（恢复时重新生成）
        event(e.getId(), null, ParamUtils.userIdOf(user), target.equals("PAUSED") ? "PAUSE" : "ARCHIVE", null);
    }

    /** 私人事件（仅本人；不允许维护人凭 id 读取他人记录）。 */
    public List<Map<String, Object>> taskEvents(Long taskId, AuthUser user) {
        requireUser(user);
        ComplianceExecutionTask task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("任务不存在");
        ownedExecution(task.getExecutionId(), user);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ComplianceExecutionEvent ev : eventMapper.selectList(new LambdaQueryWrapper<ComplianceExecutionEvent>()
                .eq(ComplianceExecutionEvent::getTaskId, taskId)
                .orderByDesc(ComplianceExecutionEvent::getId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ev.getId());
            m.put("eventType", ev.getEventType());
            m.put("payload", ev.getPayload());
            m.put("createTime", ev.getCreatedAt());
            rows.add(m);
        }
        return rows;
    }

    /**
     * 迁移辅助：从 v3 旧事项生成 v4 模板草稿（保留旧公共内容为节点；人工补结构化定义后经维护人发布；
     * 不搬移个人进度/不推断完成状态——设计 §8.2.3/8.2.4）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createDraftFromV3Items(Long documentId, String title, AuthUser user) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) throw new BusinessException("来源文档不存在");
        ComplianceTemplateVersion t = new ComplianceTemplateVersion();
        t.setDocumentId(documentId);
        t.setOwnerUserId(user == null ? null : user.getUserId());
        t.setTitle(title == null || title.isBlank() ? doc.getTitle() + " 执行规范（v3 迁移草稿）" : title.trim());
        Integer maxRev = templateVersionMapper.selectList(new LambdaQueryWrapper<ComplianceTemplateVersion>()
                .eq(ComplianceTemplateVersion::getDocumentId, documentId)
                .orderByDesc(ComplianceTemplateVersion::getRevision).last("LIMIT 1"))
                .stream().findFirst().map(ComplianceTemplateVersion::getRevision).orElse(0);
        t.setRevision(maxRev + 1);
        t.setState("DRAFT");
        templateVersionMapper.insert(t);
        // v3 已发布事项 → CHECK 型节点（跳过草稿与停用行）
        List<com.jshz.expert.entity.ComplianceItem> v3Items = v3ItemMapper.selectList(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceItem>()
                .eq(com.jshz.expert.entity.ComplianceItem::getDocumentId, documentId)
                .eq(com.jshz.expert.entity.ComplianceItem::getIsDraft, 0)
                .orderByAsc(com.jshz.expert.entity.ComplianceItem::getId));
        int sort = 0;
        int seq = 1;
        for (com.jshz.expert.entity.ComplianceItem item : v3Items) {
            ComplianceTemplateNode n = new ComplianceTemplateNode();
            n.setVersionId(t.getId());
            n.setNodeCode("V3-" + item.getId());
            n.setTitle(item.getTitle());
            n.setStage(item.getStage());
            n.setInputType("CHECK");
            n.setInstruction(item.getContent());
            n.setTimeRule(item.getTimeNote());
            n.setRequired(0); // 旧公共 required 语义不自动推断为个人必做（设计 §8.2.3）
            n.setSort(sort++);
            templateNodeMapper.insert(n);
            seq++;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("templateVersionId", t.getId());
        m.put("nodesCreated", sort);
        return m;
    }

    private final com.jshz.expert.mapper.ComplianceItemMapper v3ItemMapper;

    // ==================== 私有辅助 ====================

    private void requireUser(AuthUser user) {
        if (user == null) throw new BusinessException(401, "未登录");
    }

    private void requireMaintainer(AuthUser user) {
        if (user == null || !user.hasPermission("knowledge:maintain")) {
            throw new BusinessException("该操作需要维护人权限（knowledge:maintain）");
        }
    }

    private ComplianceExecution ownedExecution(Long executionId, AuthUser user) {
        requireUser(user);
        ComplianceExecution e = executionMapper.selectById(executionId);
        if (e == null) throw new BusinessException("清单不存在");
        // 统一 404 语义，不暴露存在性（设计 §4）
        if (!e.getOwnerUserId().equals(user.getUserId())) throw new BusinessException(404, "清单不存在");
        return e;
    }

    private List<ComplianceExecutionTask> tasksOf(Long executionId) {
        return taskMapper.selectList(new LambdaQueryWrapper<ComplianceExecutionTask>()
                .eq(ComplianceExecutionTask::getExecutionId, executionId).orderByAsc(ComplianceExecutionTask::getId));
    }

    private Map<String, Object> taskMap(ComplianceExecutionTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("executionId", t.getExecutionId());
        m.put("sourceNodeCode", t.getSourceNodeCode());
        m.put("snapshot", parseJsonSafe(t.getSnapshot()));
        m.put("answer", parseJsonSafe(t.getAnswer()));
        m.put("applicability", t.getApplicability());
        m.put("dueDate", t.getDueDate());
        m.put("priority", t.getPriority());
        m.put("progress", t.getProgress());
        m.put("note", t.getNote());
        m.put("waitingReason", t.getWaitingReason());
        m.put("nextAction", t.getNextAction());
        m.put("reviewDate", t.getReviewDate());
        m.put("remindEnabled", t.getRemindEnabled());
        m.put("remindDays", t.getRemindDays());
        m.put("scheduleRevision", t.getScheduleRevision());
        m.put("completedAt", t.getCompletedAt());
        m.put("rowVersion", t.getRowVersion());
        return m;
    }

    private Object parseJsonSafe(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return json;
        }
    }

    private String jsonStr(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "null";
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String escape(String s) {
        return s.replace("\"", "\\\"");
    }

    private void event(Long executionId, Long taskId, Long userId, String type, String payload) {
        ComplianceExecutionEvent ev = new ComplianceExecutionEvent();
        ev.setExecutionId(executionId);
        ev.setTaskId(taskId);
        ev.setActorUserId(userId);
        ev.setEventType(type);
        ev.setPayload(payload);
        eventMapper.insert(ev);
    }

    private void bumpRevision(ComplianceExecutionTask task) {
        task.setScheduleRevision(task.getScheduleRevision() == null ? 1 : task.getScheduleRevision() + 1);
        // 旧 PENDING 节点全部失效
        ComplianceExecutionReminder patch = new ComplianceExecutionReminder();
        patch.setState("CANCELLED");
        reminderMapper.update(patch, new LambdaQueryWrapper<ComplianceExecutionReminder>()
                .eq(ComplianceExecutionReminder::getTaskId, task.getId())
                .eq(ComplianceExecutionReminder::getState, "PENDING"));
    }

    private void bumpAllRevisions(Long executionId) {
        ComplianceExecutionReminder patch = new ComplianceExecutionReminder();
        patch.setState("CANCELLED");
        reminderMapper.update(patch, new LambdaQueryWrapper<ComplianceExecutionReminder>()
                .eq(ComplianceExecutionReminder::getExecutionId, executionId)
                .eq(ComplianceExecutionReminder::getState, "PENDING"));
    }

    /** 为任务 due_date 生成 提前N天 + 当天 节点（过去时间点跳过；revision 幂等）。 */
    public int scheduleTaskReminders(ComplianceExecutionTask task, ComplianceExecution e) {
        LocalDate due = task.getDueDate();
        if (due == null) return 0;
        int days = task.getRemindDays() == null ? 3 : task.getRemindDays();
        LocalDate today = LocalDate.now();
        int n = 0;
        LocalDate before = due.minusDays(days);
        if (!before.isBefore(today)) {
            n += trySchedule(e, task, "due_before", before.atTime(8, 30));
        }
        if (!due.isBefore(today)) {
            n += trySchedule(e, task, "due_today", due.atTime(8, 30));
        }
        return n;
    }

    private int trySchedule(ComplianceExecution e, ComplianceExecutionTask task, String kind, LocalDateTime at) {
        ComplianceExecutionReminder r = new ComplianceExecutionReminder();
        r.setExecutionId(e.getId());
        r.setTaskId(task.getId());
        r.setRecipientUserId(e.getOwnerUserId());
        r.setKind(kind);
        r.setScheduledAt(at);
        r.setScheduleRevision(task.getScheduleRevision() == null ? 0 : task.getScheduleRevision());
        r.setState("PENDING");
        try {
            reminderMapper.insert(r);
            return 1;
        } catch (DuplicateKeyException dup) {
            return 0; // 同节点已计划（幂等）
        }
    }

    /** 必填校验：必做且 input_type=CHECK/TEXT 时答案非空；SINGLE/MULTI 必须选择。返回错误消息或 null。 */
    private String requiredMissingError(ComplianceExecutionTask task) {
        Map<String, Object> snap = (Map<String, Object>) parseJsonSafe(task.getSnapshot());
        if (snap == null) return null;
        Object req = snap.get("required");
        if (req == null || !"1".equals(String.valueOf(req)) && !Boolean.TRUE.equals(req)) return null;
        Object answer = parseJsonSafe(task.getAnswer());
        if (answer == null) return "必做任务「" + snap.get("title") + "」尚未填写，不能完成";
        String type = String.valueOf(snap.get("inputType"));
        if (!(answer instanceof Map<?, ?> values)) return "必做任务答案格式不正确";
        if ("MULTI".equals(type) && (!(values.get("optionCodes") instanceof List<?> codes) || codes.isEmpty())) {
            return "必做任务「" + snap.get("title") + "」请选择至少一个选项";
        }
        if ("SINGLE".equals(type) && (values.get("optionCode") == null || String.valueOf(values.get("optionCode")).isBlank())) {
            return "必做任务「" + snap.get("title") + "」请选择一个选项";
        }
        if ("CHECK".equals(type) || "TEXT".equals(type)) {
            if (answer instanceof Map<?, ?> m) {
                Object v = m.get("text");
                if (v == null || String.valueOf(v).isBlank()) return "必做任务「" + snap.get("title") + "」尚未填写，不能完成";
            }
        }
        return null;
    }

    /** answer 白名单校验：按 snapshot.input_type 与选项 code。 */
    private String validateAnswer(String snapshotJson, Object answer) {
        if (answer == null) return null;
        Map<String, Object> snap = (Map<String, Object>) parseJsonSafe(snapshotJson);
        if (snap == null) return null;
        String type = String.valueOf(snap.get("inputType"));
        if (!("SINGLE".equals(type) || "MULTI".equals(type) || "TEXT".equals(type) || "CHECK".equals(type))) {
            return "该节点不支持填写";
        }
        if ("SINGLE".equals(type)) {
            if (!(answer instanceof Map<?, ?> m) || m.get("optionCode") == null) return "请选择一个选项";
            String code = String.valueOf(m.get("optionCode"));
            if (!optionCodeExists(snap, code)) return "选项已失效，请重新选择";
        } else if ("MULTI".equals(type)) {
            if (!(answer instanceof Map<?, ?> m) || !(m.get("optionCodes") instanceof List<?> codes)) return "请选择选项";
            for (Object c : codes) {
                if (!optionCodeExists(snap, String.valueOf(c))) return "选项已失效，请重新选择";
            }
        }
        return null;
    }

    private boolean optionCodeExists(Map<String, Object> snap, String code) {
        if (snap.get("options") instanceof List<?> opts) {
            for (Object o : opts) {
                if (o instanceof Map<?, ?> m && code.equals(String.valueOf(m.get("optionCode")))) return true;
            }
        }
        return false;
    }
}
