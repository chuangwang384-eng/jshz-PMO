package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ComplianceExecution;
import com.jshz.expert.entity.ComplianceItem;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectDoc;
import com.jshz.expert.entity.ProjectSchedule;
import com.jshz.expert.mapper.ComplianceExecutionEventMapper;
import com.jshz.expert.mapper.ComplianceExecutionMapper;
import com.jshz.expert.mapper.ComplianceExecutionReminderMapper;
import com.jshz.expert.mapper.ComplianceExecutionTaskMapper;
import com.jshz.expert.mapper.ComplianceItemAssigneeMapper;
import com.jshz.expert.mapper.ComplianceItemHideMapper;
import com.jshz.expert.mapper.ComplianceItemHistoryMapper;
import com.jshz.expert.mapper.ComplianceItemMapper;
import com.jshz.expert.mapper.ComplianceProjectDocBindingMapper;
import com.jshz.expert.mapper.ComplianceProjectTodoMapper;
import com.jshz.expert.mapper.ComplianceUserProgressMapper;
import com.jshz.expert.mapper.ApplicationMapper;
import com.jshz.expert.mapper.CustomerAttachmentMapper;
import com.jshz.expert.mapper.ExpertMapper;
import com.jshz.expert.mapper.KnowledgeDocVersionMapper;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import com.jshz.expert.mapper.SupplierImageMapper;
import com.jshz.expert.mapper.SupplierMapper;
import com.jshz.expert.mapper.ProjectAssistantMapper;
import com.jshz.expert.mapper.ProjectContractMapper;
import com.jshz.expert.mapper.ProjectDocMapper;
import com.jshz.expert.mapper.ProjectExecutorMapper;
import com.jshz.expert.mapper.ProjectExpertMapper;
import com.jshz.expert.mapper.ProjectMapper;
import com.jshz.expert.mapper.ProjectPhaseHistoryMapper;
import com.jshz.expert.mapper.ProjectScheduleMapper;
import com.jshz.expert.mapper.ProjectSpeakerMapper;
import com.jshz.expert.mapper.ProjectSupplierMapper;
import com.jshz.expert.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 存储管理与硬删除工具（Owner 2026-11-12，防存储爆炸）。
 * 四个能力：存储占用统计与预警、项目资料库孤儿文件清理、预览缓存清理、已取消/已删除项目的关联数据硬删除。
 * 设计文档：docs/design/2026-11-12-存储管理与硬删除工具-设计文档.md。
 * 硬删除不可恢复：项目硬删仅限 CANCELED/DELETED 状态 + 完整项目名确认；文件删除均带全库引用复核。
 */
@Service
public class StorageAdminService {

    private static final Logger log = LoggerFactory.getLogger(StorageAdminService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectMapper projects;
    private final ProjectDocMapper docMapper;
    private final ProjectScheduleMapper scheduleMapper;
    private final ProjectContractMapper contractMapper;
    private final ProjectSpeakerMapper speakerMapper;
    private final ProjectExpertMapper expertRelMapper;
    private final ProjectSupplierMapper supplierRelMapper;
    private final ProjectPhaseHistoryMapper phaseHistoryMapper;
    private final ProjectExecutorMapper executorMapper;
    private final ProjectAssistantMapper assistantMapper;
    private final ComplianceProjectTodoMapper todoMapper;
    private final ComplianceProjectDocBindingMapper bindingMapper;
    private final ComplianceExecutionMapper executionMapper;
    private final ComplianceExecutionTaskMapper executionTaskMapper;
    private final ComplianceExecutionEventMapper executionEventMapper;
    private final ComplianceExecutionReminderMapper executionReminderMapper;
    private final ComplianceItemMapper itemMapper;
    private final ComplianceItemAssigneeMapper itemAssigneeMapper;
    private final ComplianceItemHideMapper itemHideMapper;
    private final ComplianceItemHistoryMapper itemHistoryMapper;
    private final ComplianceUserProgressMapper userProgressMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDocVersionMapper knowledgeDocVersionMapper;
    private final ExpertMapper expertMapper;
    private final ApplicationMapper applicationMapper;
    private final CustomerAttachmentMapper customerAttachmentMapper;
    private final SupplierImageMapper supplierImageMapper;
    private final SupplierMapper supplierMapper;
    private final ProjectStorageService storage;
    private final OperationLogService opLog;

    @Value("${expert.project.storage-dir}")
    private String projectDir;
    @Value("${expert.knowledge.storage-dir}")
    private String knowledgeDir;
    @Value("${expert.knowledge.preview-dir}")
    private String previewDir;
    @Value("${expert.photo.storage-dir}")
    private String photoDir;
    @Value("${expert.photo.temp-dir}")
    private String photoTmpDir;
    @Value("${expert.customer.storage-dir}")
    private String customerDir;
    @Value("${expert.supplier.storage-dir}")
    private String supplierDir;
    @Value("${expert.storage.warn-threshold-bytes:10737418240}")
    private long warnThresholdBytes;
    @Value("${expert.storage.critical-threshold-bytes:21474836480}")
    private long criticalThresholdBytes;

    public StorageAdminService(ProjectMapper projects, ProjectDocMapper docMapper,
                               ProjectScheduleMapper scheduleMapper, ProjectContractMapper contractMapper,
                               ProjectSpeakerMapper speakerMapper, ProjectExpertMapper expertRelMapper,
                               ProjectSupplierMapper supplierRelMapper, ProjectPhaseHistoryMapper phaseHistoryMapper,
                               ProjectExecutorMapper executorMapper, ProjectAssistantMapper assistantMapper,
                               ComplianceProjectTodoMapper todoMapper, ComplianceProjectDocBindingMapper bindingMapper,
                               ComplianceExecutionMapper executionMapper, ComplianceExecutionTaskMapper executionTaskMapper,
                               ComplianceExecutionEventMapper executionEventMapper,
                               ComplianceExecutionReminderMapper executionReminderMapper,
                               ComplianceItemMapper itemMapper, ComplianceItemAssigneeMapper itemAssigneeMapper,
                               ComplianceItemHideMapper itemHideMapper, ComplianceItemHistoryMapper itemHistoryMapper,
                               ComplianceUserProgressMapper userProgressMapper,
                               KnowledgeDocumentMapper knowledgeDocumentMapper,
                               KnowledgeDocVersionMapper knowledgeDocVersionMapper, ExpertMapper expertMapper,
                               ApplicationMapper applicationMapper, CustomerAttachmentMapper customerAttachmentMapper,
                               SupplierImageMapper supplierImageMapper, SupplierMapper supplierMapper,
                               ProjectStorageService storage, OperationLogService opLog) {
        this.projects = projects;
        this.docMapper = docMapper;
        this.scheduleMapper = scheduleMapper;
        this.contractMapper = contractMapper;
        this.speakerMapper = speakerMapper;
        this.expertRelMapper = expertRelMapper;
        this.supplierRelMapper = supplierRelMapper;
        this.phaseHistoryMapper = phaseHistoryMapper;
        this.executorMapper = executorMapper;
        this.assistantMapper = assistantMapper;
        this.todoMapper = todoMapper;
        this.bindingMapper = bindingMapper;
        this.executionMapper = executionMapper;
        this.executionTaskMapper = executionTaskMapper;
        this.executionEventMapper = executionEventMapper;
        this.executionReminderMapper = executionReminderMapper;
        this.itemMapper = itemMapper;
        this.itemAssigneeMapper = itemAssigneeMapper;
        this.itemHideMapper = itemHideMapper;
        this.itemHistoryMapper = itemHistoryMapper;
        this.userProgressMapper = userProgressMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeDocVersionMapper = knowledgeDocVersionMapper;
        this.expertMapper = expertMapper;
        this.applicationMapper = applicationMapper;
        this.customerAttachmentMapper = customerAttachmentMapper;
        this.supplierImageMapper = supplierImageMapper;
        this.supplierMapper = supplierMapper;
        this.storage = storage;
        this.opLog = opLog;
    }

    // ==================== 存储占用统计与预警 ====================

    /** 各存储目录占用 + 阈值预警级别 + 磁盘剩余空间。 */
    public Map<String, Object> stats() {
        List<Map<String, Object>> dirs = new ArrayList<>();
        String worst = "NONE";
        worst = worst(dirs, "项目资料库", projectDir, worst);
        worst = worst(dirs, "知识文档库", knowledgeDir, worst);
        worst = worst(dirs, "预览缓存", previewDir, worst);
        worst = worst(dirs, "照片库", photoDir, worst);
        worst = worst(dirs, "照片库临时", photoTmpDir, worst);
        worst = worst(dirs, "客户附件库", customerDir, worst);
        worst = worst(dirs, "供应商图库", supplierDir, worst);

        long totalBytes = 0;
        long totalFiles = 0;
        for (Map<String, Object> d : dirs) {
            totalBytes += (Long) d.get("bytes");
            totalFiles += (Long) d.get("files");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dirs", dirs);
        result.put("totalBytes", totalBytes);
        result.put("totalFiles", totalFiles);
        result.put("level", worst);
        result.put("warnThresholdBytes", warnThresholdBytes);
        result.put("criticalThresholdBytes", criticalThresholdBytes);
        result.put("diskUsableBytes", new File(projectDir).getUsableSpace());
        result.put("checkedAt", LocalDateTime.now().format(TS));
        return result;
    }

    private String worst(List<Map<String, Object>> dirs, String name, String path, String currentWorst) {
        Map<String, Object> stat = dirStat(name, path);
        dirs.add(stat);
        String level = (String) stat.get("level");
        return "CRITICAL".equals(currentWorst) || "CRITICAL".equals(level) ? "CRITICAL"
                : "WARNING".equals(currentWorst) || "WARNING".equals(level) ? "WARNING" : currentWorst;
    }

    private Map<String, Object> dirStat(String name, String path) {
        long[] size = dirSize(Paths.get(path));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("path", path);
        m.put("bytes", size[0]);
        m.put("files", size[1]);
        m.put("level", size[0] >= criticalThresholdBytes ? "CRITICAL" : size[0] >= warnThresholdBytes ? "WARNING" : "NONE");
        m.put("exists", size[1] >= 0);
        return m;
    }

    /** 递归统计目录字节数与文件数；目录不存在返回 {-1,-1}（exists=false）。 */
    private long[] dirSize(Path dir) {
        if (!Files.isDirectory(dir)) {
            return new long[]{-1, -1};
        }
        long bytes = 0;
        long files = 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (Files.isRegularFile(p)) {
                    try {
                        bytes += Files.size(p);
                        files++;
                    } catch (IOException ignored) {
                        // 单文件统计失败不阻断
                    }
                }
            }
        } catch (IOException e) {
            log.warn("存储目录统计失败 dir={}：{}", dir, e.getMessage());
            return new long[]{-1, -1};
        }
        return new long[]{bytes, files};
    }

    // ==================== 孤儿文件（全目录：项目/知识/照片/照片临时/客户/供应商） ====================

    public static final String SCOPE_PROJECT = "project";
    /** 项目资料库-已删除资料：project_doc 记录已软删且全库无未删引用的物理文件（系统无资料恢复入口）。 */
    public static final String SCOPE_PROJECT_DELETED = "project-deleted";
    public static final String SCOPE_KNOWLEDGE = "knowledge";
    public static final String SCOPE_PHOTO = "photo";
    public static final String SCOPE_PHOTO_TMP = "photo-tmp";
    public static final String SCOPE_CUSTOMER = "customer";
    public static final String SCOPE_SUPPLIER = "supplier";
    public static final String SCOPE_ALL = "all";
    private static final List<String> PURGEABLE_SCOPES = List.of(
            SCOPE_PROJECT, SCOPE_PROJECT_DELETED, SCOPE_KNOWLEDGE, SCOPE_PHOTO, SCOPE_PHOTO_TMP,
            SCOPE_CUSTOMER, SCOPE_SUPPLIER);

    /**
     * 单个存储目录的孤儿判定规格。
     * skipPreviewDir：知识文档库需排除 _preview 子目录（预览缓存由专用清空接口处理）。
     * skipRichtextImages：照片库需排除 richtext_* 富文本内嵌图（引用存在于正文 HTML/JSON，无法按文件名反查）。
     * deletedDocsOnly：只清理「资料记录已软删且无任何未删引用」的物理文件（项目资料库回收），
     *                  此时 refs 必须是未删引用集合，避免把软删行自身当引用。
     */
    private record OrphanSpec(String scope, String name, Path root, Set<String> refs,
                              boolean skipPreviewDir, boolean skipRichtextImages, boolean deletedDocsOnly) {}

    private OrphanSpec specOf(String scope) {
        switch (scope == null ? "" : scope) {
            case SCOPE_PROJECT:
                return new OrphanSpec(SCOPE_PROJECT, "项目资料库", Paths.get(projectDir), referencedFilePaths(), false, false, false);
            case SCOPE_PROJECT_DELETED:
                return new OrphanSpec(SCOPE_PROJECT_DELETED, "项目资料库（已删除资料）", Paths.get(projectDir),
                        activeProjectFilePaths(), false, false, true);
            case SCOPE_KNOWLEDGE:
                return new OrphanSpec(SCOPE_KNOWLEDGE, "知识文档库", Paths.get(knowledgeDir), knowledgeRefs(), true, false, false);
            case SCOPE_PHOTO:
                return new OrphanSpec(SCOPE_PHOTO, "照片库", Paths.get(photoDir), photoRefs(), false, true, false);
            case SCOPE_PHOTO_TMP:
                return new OrphanSpec(SCOPE_PHOTO_TMP, "照片库临时", Paths.get(photoTmpDir), photoTmpRefs(), false, false, false);
            case SCOPE_CUSTOMER:
                return new OrphanSpec(SCOPE_CUSTOMER, "客户附件库", Paths.get(customerDir), customerRefs(), false, false, false);
            case SCOPE_SUPPLIER:
                return new OrphanSpec(SCOPE_SUPPLIER, "供应商图库", Paths.get(supplierDir), supplierRefs(), false, false, false);
            default:
                throw new BusinessException(400, "未知的存储范围: " + scope);
        }
    }

    /** 未删引用集合：project_doc(is_deleted=0，NULL 视为未删) ∪ 日程 ∪ 合同。软删行不算引用。 */
    private Set<String> activeProjectFilePaths() {
        Set<String> refs = new HashSet<>();
        for (ProjectDoc d : docMapper.selectList(null)) {
            if (d.getFilePath() == null) continue;
            if (d.getIsDeleted() == null || d.getIsDeleted() == 0) refs.add(d.getFilePath());
        }
        for (ProjectSchedule s : scheduleMapper.selectList(null)) {
            if (s.getFilePath() != null) refs.add(s.getFilePath());
        }
        for (com.jshz.expert.entity.ProjectContract c : contractMapper.selectList(null)) {
            if (c.getFilePath() != null) refs.add(c.getFilePath());
        }
        return refs;
    }

    /** 仅被软删资料行引用（无任何未删引用）的文件路径集合。 */
    private Set<String> softDeletedDocPaths() {
        java.util.Map<String, int[]> stat = new java.util.HashMap<>();
        for (ProjectDoc d : docMapper.selectList(null)) {
            if (d.getFilePath() == null) continue;
            int[] s = stat.computeIfAbsent(d.getFilePath(), k -> new int[2]);
            if (d.getIsDeleted() != null && d.getIsDeleted() == 1) {
                s[1]++;
            } else {
                s[0]++;
            }
        }
        Set<String> active = activeProjectFilePaths();
        Set<String> result = new java.util.LinkedHashSet<>();
        for (java.util.Map.Entry<String, int[]> e : stat.entrySet()) {
            if (e.getValue()[0] == 0 && e.getValue()[1] > 0 && !active.contains(e.getKey())) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /** 知识文档库引用：主表当前文件 + 全部历史版本文件（只看主表会误删历史版本）。 */
    private Set<String> knowledgeRefs() {
        Set<String> refs = new HashSet<>();
        for (com.jshz.expert.entity.KnowledgeDocument d : knowledgeDocumentMapper.selectList(null)) {
            if (d.getFilePath() != null) refs.add(d.getFilePath());
        }
        for (com.jshz.expert.entity.KnowledgeDocVersion v : knowledgeDocVersionMapper.selectList(null)) {
            if (v.getFilePath() != null) refs.add(v.getFilePath());
        }
        return refs;
    }

    /** 照片库引用：专家照片/身份证正反面对应文件名。 */
    private Set<String> photoRefs() {
        Set<String> refs = new HashSet<>();
        for (com.jshz.expert.entity.Expert e : expertMapper.selectList(null)) {
            if (e.getPhoto() != null) refs.add(e.getPhoto());
            if (e.getIdCardFront() != null) refs.add(e.getIdCardFront());
            if (e.getIdCardBack() != null) refs.add(e.getIdCardBack());
        }
        return refs;
    }

    /** 临时照片引用：全部审批单 biz_data 中 "TMP:<文件名>"（含待审/已审，保守保留）。 */
    private Set<String> photoTmpRefs() {
        Set<String> refs = new HashSet<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("TMP:([^\"\\\\,\\}\\s]+)");
        for (com.jshz.expert.entity.Application a : applicationMapper.selectList(null)) {
            if (a.getBizData() == null) continue;
            java.util.regex.Matcher m = p.matcher(a.getBizData());
            while (m.find()) refs.add(m.group(1));
        }
        return refs;
    }

    /** 客户附件库引用：customer_attachment.storage_name。 */
    private Set<String> customerRefs() {
        Set<String> refs = new HashSet<>();
        for (com.jshz.expert.entity.CustomerAttachment a : customerAttachmentMapper.selectList(null)) {
            if (a.getStorageName() != null) refs.add(a.getStorageName());
        }
        return refs;
    }

    /** 供应商图库引用：supplier_image.file_name + 兼容历史 supplier.images JSON 数组。 */
    private Set<String> supplierRefs() {
        Set<String> refs = new HashSet<>();
        for (com.jshz.expert.entity.SupplierImage img : supplierImageMapper.selectList(null)) {
            if (img.getFileName() != null) refs.add(img.getFileName());
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "[A-Za-z0-9_\\-.]+(?i:\\.(?:jpg|jpeg|png|gif|webp|bmp))");
        for (com.jshz.expert.entity.Supplier s : supplierMapper.selectList(null)) {
            if (s.getImages() == null) continue;
            java.util.regex.Matcher m = p.matcher(s.getImages());
            while (m.find()) refs.add(m.group());
        }
        return refs;
    }

    /** 扫描指定目录的孤儿文件：目录内文件，存储键（相对路径 / 分隔）不在引用集合中。 */
    private List<Map<String, Object>> scanOrphans(OrphanSpec spec) {
        Path root = spec.root().toAbsolutePath().normalize();
        List<Map<String, Object>> orphans = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return orphans;
        }
        if (spec.deletedDocsOnly()) {
            // 回收模式：候选集=仅被软删资料行引用的路径，不再全目录遍历
            for (String key : softDeletedDocPaths()) {
                String norm = key.replace('\\', '/');
                Path p = root.resolve(norm).normalize();
                if (!p.startsWith(root) || !Files.isRegularFile(p)) continue;
                appendOrphan(root, p, norm, spec, orphans);
            }
        } else {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (!Files.isRegularFile(p)) continue;
                    appendOrphan(root, p, root.relativize(p).toString().replace('\\', '/'), spec, orphans);
                }
            } catch (IOException e) {
                throw new BusinessException("孤儿文件扫描失败: " + e.getMessage());
            }
        }
        orphans.sort((a, b) -> Long.compare((Long) b.get("bytes"), (Long) a.get("bytes")));
        return orphans;
    }

    /** 收集单个文件（排除规则 + 引用二次校验通过才计入；读取失败则跳过）。 */
    private void appendOrphan(Path root, Path p, String key, OrphanSpec spec, List<Map<String, Object>> orphans) {
        if (spec.skipPreviewDir() && (key.equals("_preview") || key.startsWith("_preview/"))) return;
        String simpleName = p.getFileName().toString();
        if (spec.skipRichtextImages() && simpleName.startsWith("richtext_")) return;
        if (spec.refs().contains(key) || spec.refs().contains(simpleName)) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", key);
        try {
            m.put("bytes", Files.size(p));
            m.put("lastModified", Files.getLastModifiedTime(p).toString().substring(0, 19));
        } catch (IOException e) {
            return;
        }
        orphans.add(m);
    }

    /** 单个目录孤儿清单 + 总计。 */
    public Map<String, Object> orphansOf(String scope) {
        OrphanSpec spec = specOf(scope);
        List<Map<String, Object>> orphans = scanOrphans(spec);
        long bytes = orphans.stream().mapToLong(m -> (Long) m.get("bytes")).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", spec.scope());
        result.put("name", spec.name());
        result.put("path", spec.root().toString());
        result.put("items", orphans);
        result.put("count", orphans.size());
        result.put("bytes", bytes);
        return result;
    }

    /** 全部可清理目录的孤儿清单（前端一次性展示）。 */
    public Map<String, Object> allOrphans() {
        List<Map<String, Object>> scopes = new ArrayList<>();
        long totalCount = 0;
        long totalBytes = 0;
        for (String scope : PURGEABLE_SCOPES) {
            Map<String, Object> one = orphansOf(scope);
            scopes.add(one);
            totalCount += ((Integer) one.get("count")).longValue();
            totalBytes += (Long) one.get("bytes");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scopes", scopes);
        result.put("totalCount", totalCount);
        result.put("totalBytes", totalBytes);
        return result;
    }

    /** 兼容旧接口：项目资料库孤儿清单。 */
    public Map<String, Object> projectOrphans() {
        return orphansOf(SCOPE_PROJECT);
    }

    /**
     * 孤儿文件硬删除（按目录范围；expectedCount+expectedBytes 集合版本校验，不可恢复）。
     * 孤儿定义保证文件已无任何业务引用，删除不影响在线功能。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> purgeOrphans(String scope, Long expectedCount, Long expectedBytes, AuthUser user) {
        OrphanSpec spec = specOf(scope);
        List<Map<String, Object>> orphans = scanOrphans(spec);
        long bytes = orphans.stream().mapToLong(m -> (Long) m.get("bytes")).sum();
        if (expectedCount != null && orphans.size() != expectedCount) {
            throw new BusinessException(409, spec.name() + "孤儿文件数量已变化（现 " + orphans.size() + " 个），请刷新后重试");
        }
        if (expectedBytes != null && bytes != expectedBytes) {
            throw new BusinessException(409, spec.name() + "孤儿文件总大小已变化，请刷新后重试");
        }
        int deleted = 0;
        long deletedBytes = 0;
        for (Map<String, Object> o : orphans) {
            if (deleteWithin(spec.root(), (String) o.get("path"))) {
                deleted++;
                deletedBytes += (Long) o.get("bytes");
            }
        }
        opLog.record(user == null ? null : user.getUserId(), user == null ? null : user.getUsername(),
                OperationLogService.ACTION_STORAGE_PURGE_ORPHANS,
                "清理" + spec.name() + "孤儿文件 " + deleted + " 个（" + deletedBytes + " 字节）",
                "STORAGE", spec.scope(), null);
        log.info("孤儿文件清理完成 scope={} count={} bytes={} operator={}", spec.scope(), deleted, deletedBytes,
                user == null ? "-" : user.getUsername());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", spec.scope());
        result.put("deleted", deleted);
        result.put("deletedBytes", deletedBytes);
        return result;
    }

    /** 兼容旧接口：项目资料库孤儿清理。 */
    public Map<String, Object> purgeProjectOrphans(Long expectedCount, Long expectedBytes, AuthUser user) {
        return purgeOrphans(SCOPE_PROJECT, expectedCount, expectedBytes, user);
    }

    /** 在指定根目录内删除文件（防穿越校验，失败静默返回 false）。 */
    private boolean deleteWithin(Path rootPath, String key) {
        try {
            Path root = rootPath.toAbsolutePath().normalize();
            Path p = root.resolve(key.replace('/', File.separatorChar)).normalize();
            if (!p.startsWith(root) || !Files.isRegularFile(p)) return false;
            Files.deleteIfExists(p);
            return true;
        } catch (Exception e) {
            log.warn("孤儿文件删除失败 key={}：{}", key, e.getMessage());
            return false;
        }
    }

    // ==================== 项目资料库文件引用 ====================

    /** 引用集合：project_doc ∪ project_schedule ∪ project_contract 的 filePath（含软删行）。 */
    private Set<String> referencedFilePaths() {
        Set<String> refs = new HashSet<>();
        for (ProjectDoc d : docMapper.selectList(null)) {
            if (d.getFilePath() != null) refs.add(d.getFilePath());
        }
        for (ProjectSchedule s : scheduleMapper.selectList(null)) {
            if (s.getFilePath() != null) refs.add(s.getFilePath());
        }
        for (com.jshz.expert.entity.ProjectContract c : contractMapper.selectList(null)) {
            if (c.getFilePath() != null) refs.add(c.getFilePath());
        }
        return refs;
    }

    // ==================== 预览缓存清理 ====================

    /** 清空预览缓存目录全部文件（按需再生成，零业务风险）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> clearPreviewCache(Long expectedCount, AuthUser user) {
        Path root = Paths.get(previewDir);
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (Files.isRegularFile(p)) files.add(p);
                }
            } catch (IOException e) {
                throw new BusinessException("预览缓存扫描失败: " + e.getMessage());
            }
        }
        if (expectedCount != null && files.size() != expectedCount) {
            throw new BusinessException(409, "缓存文件数量已变化（现 " + files.size() + " 个），请刷新后重试");
        }
        long bytes = 0;
        int deleted = 0;
        for (Path p : files) {
            try {
                bytes += Files.size(p);
                Files.deleteIfExists(p);
                deleted++;
            } catch (IOException e) {
                log.warn("预览缓存文件删除失败 {}：{}", p, e.getMessage());
            }
        }
        opLog.record(user == null ? null : user.getUserId(), user == null ? null : user.getUsername(),
                OperationLogService.ACTION_STORAGE_CLEAR_PREVIEW,
                "清空文档预览缓存 " + deleted + " 个（" + bytes + " 字节）",
                "STORAGE", "preview-cache", null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("deletedBytes", bytes);
        return result;
    }

    // ==================== 项目关联数据硬删除 ====================

    /** 收集项目关联物理文件（资料含软删 + 日程 + 合同），预览与硬删共用。 */
    private Set<String> collectProjectFiles(Long projectId) {
        Set<String> files = new HashSet<>();
        for (ProjectDoc d : docMapper.selectList(new LambdaQueryWrapper<ProjectDoc>()
                .eq(ProjectDoc::getProjectId, projectId))) {
            if (d.getFilePath() != null) files.add(d.getFilePath());
        }
        for (ProjectSchedule s : scheduleMapper.selectList(new LambdaQueryWrapper<ProjectSchedule>()
                .eq(ProjectSchedule::getProjectId, projectId))) {
            if (s.getFilePath() != null) files.add(s.getFilePath());
        }
        for (com.jshz.expert.entity.ProjectContract c : contractMapper.selectList(
                new LambdaQueryWrapper<com.jshz.expert.entity.ProjectContract>()
                        .eq(com.jshz.expert.entity.ProjectContract::getProjectId, projectId))) {
            if (c.getFilePath() != null) files.add(c.getFilePath());
        }
        return files;
    }

    /** 项目硬删预览：项目名/状态/可删性 + 各类关联行计数 + 物理文件数与字节。 */
    public Map<String, Object> purgeProjectPreview(Long projectId) {
        Project project = projects.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        boolean purgeable = ProjectService.STATUS_CANCELED.equals(project.getStatus())
                || ProjectService.STATUS_DELETED.equals(project.getStatus());
        Set<String> files = collectProjectFiles(projectId);
        long fileBytes = 0;
        for (String key : files) {
            Path p = storage.resolveFile(key);
            if (p != null) {
                try {
                    fileBytes += Files.size(p);
                } catch (IOException ignored) {
                    // 统计失败按 0 计
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("projectName", project.getName());
        result.put("status", project.getStatus());
        result.put("purgeable", purgeable);
        result.put("docs", docMapper.selectCount(new LambdaQueryWrapper<ProjectDoc>().eq(ProjectDoc::getProjectId, projectId)));
        result.put("schedules", scheduleMapper.selectCount(new LambdaQueryWrapper<ProjectSchedule>().eq(ProjectSchedule::getProjectId, projectId)));
        result.put("speakers", speakerMapper.selectCount(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectSpeaker>().eq(com.jshz.expert.entity.ProjectSpeaker::getProjectId, projectId)));
        result.put("expertRels", expertRelMapper.selectCount(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectExpert>().eq(com.jshz.expert.entity.ProjectExpert::getProjectId, projectId)));
        result.put("supplierRels", supplierRelMapper.selectCount(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectSupplier>().eq(com.jshz.expert.entity.ProjectSupplier::getProjectId, projectId)));
        result.put("contracts", contractMapper.selectCount(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectContract>().eq(com.jshz.expert.entity.ProjectContract::getProjectId, projectId)));
        result.put("phaseHistories", phaseHistoryMapper.selectCount(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectPhaseHistory>().eq(com.jshz.expert.entity.ProjectPhaseHistory::getProjectId, projectId)));
        result.put("complianceExecutions", executionMapper.selectCount(new LambdaQueryWrapper<ComplianceExecution>().eq(ComplianceExecution::getProjectId, projectId)));
        result.put("complianceItems", itemMapper.selectCount(new LambdaQueryWrapper<ComplianceItem>().eq(ComplianceItem::getProjectId, projectId)));
        result.put("complianceTodos", todoMapper.selectCount(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceProjectTodo>().eq(com.jshz.expert.entity.ComplianceProjectTodo::getProjectId, projectId)));
        result.put("files", files.size());
        result.put("fileBytes", fileBytes);
        return result;
    }


    /**
     * 硬删除项目全部关联数据（不可恢复）。仅限 status ∈ {CANCELED, DELETED}；
     * confirmName 必须与项目名完全一致。DB 行同一事务硬删，物理文件 afterCommit 引用复核后删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> purgeProject(Long projectId, String confirmName, AuthUser user) {
        Project project = projects.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (!ProjectService.STATUS_CANCELED.equals(project.getStatus())
                && !ProjectService.STATUS_DELETED.equals(project.getStatus())) {
            throw new BusinessException(409, "仅「已取消」或「已删除」状态的项目可硬删除，当前状态：" + project.getStatus());
        }
        if (confirmName == null || !confirmName.trim().equals(project.getName())) {
            throw new BusinessException(400, "项目名称确认不符，请输入完整项目名");
        }

        // 1. 收集物理文件（资料含软删 + 日程 + 合同）
        Set<String> files = collectProjectFiles(projectId);

        // 2. 合规执行清单级联（execution → task/event/reminder）
        List<ComplianceExecution> executions = executionMapper.selectList(
                new LambdaQueryWrapper<ComplianceExecution>().eq(ComplianceExecution::getProjectId, projectId));
        List<Long> executionIds = executions.stream().map(ComplianceExecution::getId).toList();
        int tasks = 0, events = 0, reminders = 0;
        if (!executionIds.isEmpty()) {
            tasks = executionTaskMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceExecutionTask>()
                    .in(com.jshz.expert.entity.ComplianceExecutionTask::getExecutionId, executionIds));
            events = executionEventMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceExecutionEvent>()
                    .in(com.jshz.expert.entity.ComplianceExecutionEvent::getExecutionId, executionIds));
            reminders = executionReminderMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceExecutionReminder>()
                    .in(com.jshz.expert.entity.ComplianceExecutionReminder::getExecutionId, executionIds));
        }
        int executionsDeleted = executionMapper.delete(new LambdaQueryWrapper<ComplianceExecution>()
                .eq(ComplianceExecution::getProjectId, projectId));

        // 3. 合规事项级联（item → assignee/hide/history/progress）
        List<ComplianceItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ComplianceItem>().eq(ComplianceItem::getProjectId, projectId));
        List<Long> itemIds = items.stream().map(ComplianceItem::getId).toList();
        int assignees = 0, hides = 0, itemHistories = 0, progresses = 0;
        if (!itemIds.isEmpty()) {
            assignees = itemAssigneeMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceItemAssignee>()
                    .in(com.jshz.expert.entity.ComplianceItemAssignee::getItemId, itemIds));
            hides = itemHideMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceItemHide>()
                    .in(com.jshz.expert.entity.ComplianceItemHide::getItemId, itemIds));
            itemHistories = itemHistoryMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceItemHistory>()
                    .in(com.jshz.expert.entity.ComplianceItemHistory::getItemId, itemIds));
            progresses = userProgressMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceUserProgress>()
                    .in(com.jshz.expert.entity.ComplianceUserProgress::getItemId, itemIds));
        }
        int itemsDeleted = itemMapper.delete(new LambdaQueryWrapper<ComplianceItem>()
                .eq(ComplianceItem::getProjectId, projectId));

        // 4. 项目业务行硬删
        int docs = docMapper.delete(new LambdaQueryWrapper<ProjectDoc>().eq(ProjectDoc::getProjectId, projectId));
        int schedules = scheduleMapper.delete(new LambdaQueryWrapper<ProjectSchedule>().eq(ProjectSchedule::getProjectId, projectId));
        int speakers = speakerMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectSpeaker>().eq(com.jshz.expert.entity.ProjectSpeaker::getProjectId, projectId));
        int expertRels = expertRelMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectExpert>().eq(com.jshz.expert.entity.ProjectExpert::getProjectId, projectId));
        int supplierRels = supplierRelMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectSupplier>().eq(com.jshz.expert.entity.ProjectSupplier::getProjectId, projectId));
        int contracts = contractMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectContract>().eq(com.jshz.expert.entity.ProjectContract::getProjectId, projectId));
        int phaseHistories = phaseHistoryMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectPhaseHistory>().eq(com.jshz.expert.entity.ProjectPhaseHistory::getProjectId, projectId));
        int executors = executorMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectExecutor>().eq(com.jshz.expert.entity.ProjectExecutor::getProjectId, projectId));
        int assistants = assistantMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectAssistant>().eq(com.jshz.expert.entity.ProjectAssistant::getProjectId, projectId));
        int todos = todoMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceProjectTodo>().eq(com.jshz.expert.entity.ComplianceProjectTodo::getProjectId, projectId));
        int bindings = bindingMapper.delete(new LambdaQueryWrapper<com.jshz.expert.entity.ComplianceProjectDocBinding>().eq(com.jshz.expert.entity.ComplianceProjectDocBinding::getProjectId, projectId));
        int projectRows = projects.deleteById(projectId);

        // 5. 物理文件：提交后删除；删除前复核全库引用（防共享路径误删）
        List<String> toDelete = new ArrayList<>(files);
        Runnable fileCleanup = () -> {
            int removed = 0;
            long freedBytes = 0;
            for (String key : toDelete) {
                if (stillReferenced(key)) {
                    log.warn("文件仍被其他记录引用，跳过物理删除 file={}", key);
                    continue;
                }
                Path p = storage.resolveFile(key);
                if (p != null) {
                    try {
                        freedBytes += Files.size(p);
                    } catch (IOException ignored) {
                        // 大小统计失败不影响删除
                    }
                }
                storage.deleteFile(key);
                removed++;
            }
            log.info("项目硬删物理文件清理 project={} removed={} freedBytes={}", projectId, removed, freedBytes);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { fileCleanup.run(); }
            });
        } else {
            fileCleanup.run();
        }

        opLog.record(user == null ? null : user.getUserId(), user == null ? null : user.getUsername(),
                OperationLogService.ACTION_STORAGE_PURGE_PROJECT,
                "硬删除项目「" + project.getName() + "」全部关联数据（资料" + docs + "、日程" + schedules + "、合同" + contracts
                        + "、合规事项" + itemsDeleted + "、执行清单" + executionsDeleted + "，物理文件 " + toDelete.size() + " 个待清理）",
                "PROJECT", projectId, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectName", project.getName());
        result.put("docs", docs);
        result.put("schedules", schedules);
        result.put("speakers", speakers);
        result.put("expertRels", expertRels);
        result.put("supplierRels", supplierRels);
        result.put("contracts", contracts);
        result.put("phaseHistories", phaseHistories);
        result.put("executors", executors);
        result.put("assistants", assistants);
        result.put("complianceTodos", todos);
        result.put("complianceBindings", bindings);
        result.put("complianceExecutions", executionsDeleted);
        result.put("complianceExecutionTasks", tasks);
        result.put("complianceExecutionEvents", events);
        result.put("complianceExecutionReminders", reminders);
        result.put("complianceItems", itemsDeleted);
        result.put("complianceItemAssignees", assignees);
        result.put("complianceItemHides", hides);
        result.put("complianceItemHistories", itemHistories);
        result.put("complianceUserProgress", progresses);
        result.put("projectRows", projectRows);
        result.put("files", toDelete.size());
        return result;
    }

    /** 物理删除前的全库引用复核（任意表仍引用则跳过）。 */
    private boolean stillReferenced(String filePath) {
        if (docMapper.selectCount(new LambdaQueryWrapper<ProjectDoc>().eq(ProjectDoc::getFilePath, filePath)) > 0) {
            return true;
        }
        if (scheduleMapper.selectCount(new LambdaQueryWrapper<ProjectSchedule>().eq(ProjectSchedule::getFilePath, filePath)) > 0) {
            return true;
        }
        return contractMapper.selectCount(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectContract>()
                .eq(com.jshz.expert.entity.ProjectContract::getFilePath, filePath)) > 0;
    }
}
