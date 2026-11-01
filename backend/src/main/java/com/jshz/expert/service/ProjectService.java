package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.ParamUtils;
import com.jshz.expert.entity.Application;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.OperationLog;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectContract;
import com.jshz.expert.entity.ProjectDoc;
import com.jshz.expert.entity.ProjectExecutor;
import com.jshz.expert.entity.ProjectExpert;
import com.jshz.expert.entity.ProjectPhase;
import com.jshz.expert.entity.ProjectPhaseTemplateRel;
import com.jshz.expert.entity.ProjectSupplier;
import com.jshz.expert.entity.ProjectTemplate;
import com.jshz.expert.entity.ProjectType;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.Supplier;
import com.jshz.expert.entity.SupplierType;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.entity.UserRole;
import com.jshz.expert.mapper.OperationLogMapper;
import com.jshz.expert.mapper.ProjectContractMapper;
import com.jshz.expert.mapper.ProjectDocMapper;
import com.jshz.expert.mapper.ProjectExecutorMapper;
import com.jshz.expert.mapper.ProjectExpertMapper;
import com.jshz.expert.mapper.ProjectMapper;
import com.jshz.expert.mapper.ProjectPhaseMapper;
import com.jshz.expert.mapper.ProjectPhaseTemplateRelMapper;
import com.jshz.expert.mapper.ProjectSupplierMapper;
import com.jshz.expert.mapper.ProjectTemplateMapper;
import com.jshz.expert.mapper.ProjectTypeMapper;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.SupplierMapper;
import com.jshz.expert.mapper.SupplierTypeMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.mapper.UserRoleMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目管理服务（需求文档 2026-10-27 项目管理模块）：
 * - 项目 CRUD（软删）、流程阶段推进/回退（必填模板校验）、取消（必填原因）；
 * - 专家/供应商关联（留痕 + 新建专家卡）、分阶段资料、预算、富文本；
 * - 结算合同（敏感字段带出/回写留痕/审批复用）、导出 xlsx（DOWNLOAD 留痕）；
 * - 类型字典 / 阶段 / 模板配置（project:manage 由 Controller 层校验）。
 * 时间线 = operation_log target_type=PROJECT 同源。
 */
@Service
@RequiredArgsConstructor
public class ProjectService extends ServiceImpl<ProjectMapper, Project> {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_DELETED = "DELETED";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectTypeMapper typeMapper;
    private final ProjectPhaseMapper phaseMapper;
    private final ProjectTemplateMapper templateMapper;
    private final ProjectPhaseTemplateRelMapper relMapper;
    private final ProjectExecutorMapper executorMapper;
    private final ProjectExpertMapper expertRelMapper;
    private final ProjectSupplierMapper supplierRelMapper;
    private final ProjectDocMapper docMapper;
    private final ProjectContractMapper contractMapper;
    private final SupplierTypeMapper supplierTypeMapper;
    private final SupplierMapper supplierMapper;
    private final ExpertService expertService;
    private final OperationLogService operationLogService;
    private final OperationLogMapper operationLogMapper;
    private final ApplicationService applicationService;
    private final ProjectStorageService storageService;
    private final SysUserMapper sysUserMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final ObjectMapper objectMapper;

    // ==================== 项目 CRUD ====================

    /**
     * 分页列表：状态(当前阶段)/开始时间范围/结束时间范围/负责人/大类/类型/执行人 筛选。
     */
    public Page<Map<String, Object>> pageProjects(String status, String startBegin, String startEnd,
                                                  String endBegin, String endEnd, Long ownerId,
                                                  String category, Long typeId, Long executorId,
                                                  long page, long size, AuthUser user) {
        // 软删隐藏：仅过滤 status != DELETED
        LambdaQueryWrapper<Project> qw = new LambdaQueryWrapper<Project>()
                .ne(Project::getStatus, STATUS_DELETED);
        if (StringUtils.hasText(status)) {
            if ("CANCELED".equals(status)) {
                qw.eq(Project::getStatus, STATUS_CANCELED);
            } else if ("ACTIVE".equals(status)) {
                qw.ne(Project::getStatus, STATUS_DELETED);
            } else {
                Long phaseId = parseLong(status);
                if (phaseId != null) {
                    qw.eq(Project::getPhaseId, phaseId);
                }
            }
        }
        if (StringUtils.hasText(startBegin)) {
            qw.ge(Project::getStartDate, parseDate(startBegin));
        }
        if (StringUtils.hasText(startEnd)) {
            qw.le(Project::getStartDate, parseDate(startEnd));
        }
        if (StringUtils.hasText(endBegin)) {
            qw.ge(Project::getEndDate, parseDate(endBegin));
        }
        if (StringUtils.hasText(endEnd)) {
            qw.le(Project::getEndDate, parseDate(endEnd));
        }
        if (ownerId != null) {
            qw.eq(Project::getOwnerId, ownerId);
        }
        if (StringUtils.hasText(category)) {
            qw.eq(Project::getCategory, category);
        }
        if (typeId != null) {
            qw.eq(Project::getTypeId, typeId);
        }
        if (executorId != null) {
            List<Long> projectIds = executorMapper.selectList(new LambdaQueryWrapper<ProjectExecutor>()
                            .eq(ProjectExecutor::getUserId, executorId))
                    .stream().map(ProjectExecutor::getProjectId).distinct().collect(Collectors.toList());
            if (projectIds.isEmpty()) {
                qw.eq(Project::getId, -1L);
            } else {
                qw.in(Project::getId, projectIds);
            }
        }
        // 行级数据权限：非豁免用户仅见本人负责或执行的项目
        if (!canViewAllProjects(user)) {
            appendRowLevelCondition(qw, user);
        }
        qw.orderByDesc(Project::getCreateTime);
        Page<Project> p = this.page(new Page<>(page, size), qw);
        List<Map<String, Object>> items = p.getRecords().stream()
                .map(project -> {
                    Map<String, Object> m = toBriefMap(project);
                    // 项目级操作授权（与详情页 canAdvance 同义：负责人/执行人/超管），供列表"取消项目"按钮显隐
                    m.put("canAdvance", isOwnerOrExecutor(project, user));
                    return m;
                }).collect(Collectors.toList());
        return new Page<Map<String, Object>>(p.getCurrent(), p.getSize(), p.getTotal()).setRecords(items);
    }

    private Map<String, Object> toBriefMap(Project project) {
        Map<String, Object> m = toMap(project);
        m.put("typeName", nameOfType(project.getTypeId()));
        m.put("phaseName", nameOfPhase(project.getPhaseId()));
        m.put("ownerName", usernameOf(project.getOwnerId()));
        m.put("creatorName", usernameOf(project.getCreatorId()));
        m.put("executorNames", executorNamesOf(project.getId()));
        m.put("executorIds", executorIdsOf(project.getId()));
        return m;
    }

    /**
     * 详情：项目 + 关联专家/供应商/资料/合同 + 预算汇总 + 阶段模板 + 时间线。
     */
    public Map<String, Object> detail(Long id, AuthUser user) {
        Project project = requireProject(id);
        requireProjectReadable(project, user);
        Map<String, Object> m = toBriefMap(project);
        boolean fullSensitive = isOwnerOrExecutor(project, user);
        m.put("experts", listExperts(id, user));
        m.put("suppliers", listSuppliers(id, user));
        m.put("docs", listDocs(id, null, user));
        m.put("contracts", listContracts(id, user));
        m.put("budget", budgetOf(project));
        m.put("phases", phasesWithTemplates());
        m.put("timeline", timeline(id));
        m.put("canManage", user != null && (user.isAdmin() || user.hasPermission("project:manage")));
        m.put("canAdvance", fullSensitive);
        m.put("missingRequiredTemplates", missingRequiredTemplates(project, project.getPhaseId()));
        return m;
    }

    @Transactional(rollbackFor = Exception.class)
    public Project create(Map<String, Object> body, AuthUser user) {
        requireCreate(user);
        String name = str(body.get("name"));
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("项目名称不能为空");
        }
        String category = str(body.get("category"));
        if (!"ONLINE".equals(category) && !"OFFLINE".equals(category)) {
            throw new BusinessException("项目大类必须为线上(ONLINE)或线下(OFFLINE)");
        }
        Long typeId = toLong(body.get("typeId"));
        if (typeId == null || typeMapper.selectById(typeId) == null) {
            throw new BusinessException("项目类型不能为空或不存在");
        }
        LocalDate startDate = parseDateNullable(str(body.get("startDate")));
        LocalDate endDate = parseDateNullable(str(body.get("endDate")));
        Long ownerId = toLong(body.get("ownerId"));
        if (ownerId == null) {
            throw new BusinessException("负责人不能为空（立项必填）");
        }
        ProjectPhase phase = phaseMapper.selectById(toLong(body.get("phaseId")) == null ? firstPhaseId() : toLong(body.get("phaseId")));
        if (phase == null) {
            throw new BusinessException("当前阶段不存在");
        }

        Project project = new Project();
        project.setName(name.trim());
        project.setCategory(category);
        project.setTypeId(typeId);
        project.setStartDate(startDate);
        project.setEndDate(endDate);
        project.setOwnerId(ownerId);
        project.setCreatorId(user == null ? null : user.getUserId());
        project.setPhaseId(phase.getId());
        project.setStatus(STATUS_ACTIVE);
        this.save(project);

        // 执行人（默认创建人；可传多人 userIds）
        List<Long> executors = toLongList(body.get("executorIds"));
        if (executors.isEmpty()) {
            executors.add(user == null ? ownerId : user.getUserId());
        }
        if (!executors.contains(ownerId)) {
            executors.add(0, ownerId);
        }
        for (Long uid : executors) {
            ProjectExecutor pe = new ProjectExecutor();
            pe.setProjectId(project.getId());
            pe.setUserId(uid);
            executorMapper.insert(pe);
        }

        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_CREATE_PROJECT, "创建项目「" + project.getName() + "」",
                "PROJECT", project.getId(), null);
        return project;
    }

    @Transactional(rollbackFor = Exception.class)
    public Project update(Long id, Map<String, Object> body, AuthUser user) {
        Project project = requireProject(id);
        requireManage(user);
        if (StringUtils.hasText(str(body.get("name")))) {
            project.setName(str(body.get("name")).trim());
        }
        String category = str(body.get("category"));
        if (StringUtils.hasText(category)) {
            if (!"ONLINE".equals(category) && !"OFFLINE".equals(category)) {
                throw new BusinessException("项目大类必须为线上(ONLINE)或线下(OFFLINE)");
            }
            project.setCategory(category);
        }
        Long typeId = toLong(body.get("typeId"));
        if (typeId != null) {
            if (typeMapper.selectById(typeId) == null) {
                throw new BusinessException("项目类型不存在");
            }
            project.setTypeId(typeId);
        }
        if (str(body.get("startDate")) != null) {
            project.setStartDate(parseDateNullable(str(body.get("startDate"))));
        }
        if (str(body.get("endDate")) != null) {
            project.setEndDate(parseDateNullable(str(body.get("endDate"))));
        }
        if (toLong(body.get("ownerId")) != null) {
            project.setOwnerId(toLong(body.get("ownerId")));
        }
        if (toLong(body.get("phaseId")) != null) {
            ProjectPhase phase = phaseMapper.selectById(toLong(body.get("phaseId")));
            if (phase == null) {
                throw new BusinessException("当前阶段不存在");
            }
            project.setPhaseId(phase.getId());
        }
        this.updateById(project);

        List<Long> executors = toLongList(body.get("executorIds"));
        if (!executors.isEmpty()) {
            executorMapper.delete(new LambdaQueryWrapper<ProjectExecutor>().eq(ProjectExecutor::getProjectId, id));
            for (Long uid : executors) {
                ProjectExecutor pe = new ProjectExecutor();
                pe.setProjectId(id);
                pe.setUserId(uid);
                executorMapper.insert(pe);
            }
        }
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPDATE_PROJECT, "修改项目「" + project.getName() + "」",
                "PROJECT", id, null);
        return project;
    }

    /** 软删除：列表隐藏、数据保留、日志保留。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSoft(Long id, AuthUser user) {
        Project project = requireProject(id);
        requireManage(user);
        project.setStatus(STATUS_DELETED);
        this.updateById(project);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_DELETE_PROJECT, "删除项目「" + project.getName() + "」",
                "PROJECT", id, null);
    }

    // ==================== 阶段推进 / 回退 / 取消 ====================

    /**
     * 推进/回退阶段（严格逐级）：推进 target.sort==current.sort+1、回退 target.sort==current.sort-1；
     * 推进方向校验当前阶段必填模板已提交（回退不做材料校验）；仅项目负责人/执行人可操作。
     * 同阶段切换（如取消项目恢复 ACTIVE 的复用场景）跳过顺序与材料校验。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePhase(Long id, Long phaseId, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrExecutor(project, user)) {
            throw new BusinessException("仅项目负责人或执行人可推进/回退阶段");
        }
        ProjectPhase target = phaseId == null ? null : phaseMapper.selectById(phaseId);
        if (target == null) {
            throw new BusinessException("目标阶段不存在");
        }
        if (target.getIsActive() != null && target.getIsActive() == 0) {
            throw new BusinessException("目标阶段已停用");
        }
        ProjectPhase current = phaseMapper.selectById(project.getPhaseId());
        if (current == null) {
            throw new BusinessException("当前阶段不存在");
        }
        boolean samePhase = target.getId().equals(current.getId());
        if (!samePhase && current.getSort() != null && target.getSort() != null) {
            boolean adjacent = target.getSort() == current.getSort() + 1 || target.getSort() == current.getSort() - 1;
            if (!adjacent) {
                throw new BusinessException("仅支持相邻阶段推进或回退（" + current.getName() + " → " + target.getName() + "）");
            }
            if (target.getSort() > current.getSort()) {
                List<String> missing = missingRequiredTemplates(project, current.getId());
                if (!missing.isEmpty()) {
                    throw new BusinessException("当前阶段「" + current.getName() + "」必填材料未提交，无法推进：缺少 " + String.join("、", missing));
                }
            }
        }
        String before = nameOfPhase(project.getPhaseId());
        project.setPhaseId(target.getId());
        if (STATUS_CANCELED.equals(project.getStatus())) {
            project.setStatus(STATUS_ACTIVE);
        }
        this.updateById(project);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_CHANGE_PHASE,
                "项目「" + project.getName() + "」阶段变更：" + before + " → " + target.getName(),
                "PROJECT", id, null);
    }

    /** 取消（必填原因）；取消后仍可回退（changePhase 已支持从 CANCELED 恢复）。 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrExecutor(project, user)) {
            throw new BusinessException("仅项目负责人或执行人可取消项目");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("取消原因不能为空");
        }
        project.setStatus(STATUS_CANCELED);
        project.setCancelReason(reason.trim());
        project.setCancelTime(LocalDateTime.now());
        this.updateById(project);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_CANCEL_PROJECT,
                "取消项目「" + project.getName() + "」，原因：" + reason.trim(),
                "PROJECT", id, null);
    }

    /** 时间线：operation_log target_type=PROJECT 倒序（与后台日志同源）。 */
    public List<OperationLog> timeline(Long id) {
        return operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getTargetType, "PROJECT")
                .eq(OperationLog::getTargetId, String.valueOf(id))
                .orderByDesc(OperationLog::getCreateTime));
    }

    // ==================== 专家关联 ====================

    public List<Map<String, Object>> listExperts(Long id, AuthUser user) {
        requireProjectReadable(requireProject(id), user);
        List<ProjectExpert> rels = expertRelMapper.selectList(new LambdaQueryWrapper<ProjectExpert>()
                .eq(ProjectExpert::getProjectId, id).orderByAsc(ProjectExpert::getId));
        boolean full = user != null && (isOwnerOrExecutor(requireProject(id), user));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectExpert rel : rels) {
            Expert expert = expertService.getById(rel.getExpertId());
            if (expert == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rel.getId());
            m.put("expertId", expert.getId());
            m.put("name", expert.getName());
            m.put("unit", expert.getUnit());
            m.put("title", expert.getTitle());
            m.put("profile", expert.getProfile());
            m.put("role", rel.getRole());
            m.put("fee", rel.getFee());
            m.put("contractStatus", rel.getContractStatus());
            if (full) {
                m.put("idCard", expert.getIdCard());
                m.put("bankCard", expert.getBankCard());
                m.put("bankBranch", expert.getBankBranch());
            } else {
                m.put("idCard", ExpertService.maskSensitive(expert.getIdCard()));
                m.put("bankCard", ExpertService.maskSensitive(expert.getBankCard()));
                m.put("bankBranch", expert.getBankBranch());
            }
            result.add(m);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void addExpert(Long id, Long expertId, String role, AuthUser user) {
        Project project = requireProject(id);
        requireManage(user);
        if (expertId == null || expertService.getById(expertId) == null) {
            throw new BusinessException("专家不存在");
        }
        Long dup = expertRelMapper.selectCount(new LambdaQueryWrapper<ProjectExpert>()
                .eq(ProjectExpert::getProjectId, id).eq(ProjectExpert::getExpertId, expertId));
        if (dup != null && dup > 0) {
            throw new BusinessException("该专家已在项目名单中");
        }
        ProjectExpert rel = new ProjectExpert();
        rel.setProjectId(id);
        rel.setExpertId(expertId);
        rel.setRole(StringUtils.hasText(role) ? role.trim() : "嘉宾");
        rel.setCreateBy(ParamUtils.usernameOf(user));
        expertRelMapper.insert(rel);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_ASSIGN_EXPERT,
                "项目「" + project.getName() + "」调派专家「" + expertService.getById(expertId).getName() + "」（角色：" + rel.getRole() + "）",
                "PROJECT", id, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeExpert(Long id, Long expertId, AuthUser user) {
        Project project = requireProject(id);
        requireManage(user);
        ProjectExpert rel = expertRelMapper.selectOne(new LambdaQueryWrapper<ProjectExpert>()
                .eq(ProjectExpert::getProjectId, id).eq(ProjectExpert::getExpertId, expertId));
        if (rel == null) {
            throw new BusinessException("该专家不在项目名单中");
        }
        expertRelMapper.deleteById(rel.getId());
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_REMOVE_EXPERT,
                "项目「" + project.getName() + "」移出专家「" + expertService.getById(expertId).getName() + "」",
                "PROJECT", id, null);
    }

    /**
     * 新建专家卡（必填：姓名/单位/职称/简介），保存后自动关联到项目；留痕 CREATE_EXPERT(target=PROJECT)。
     */
    @Transactional(rollbackFor = Exception.class)
    public Expert createExpertCard(Long id, Map<String, Object> body, AuthUser user) {
        Project project = requireProject(id);
        requireManage(user);
        String name = str(body.get("name"));
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("姓名不能为空");
        }
        if (!StringUtils.hasText(str(body.get("unit")))) {
            throw new BusinessException("单位不能为空");
        }
        if (!StringUtils.hasText(str(body.get("title")))) {
            throw new BusinessException("职称不能为空");
        }
        if (!StringUtils.hasText(str(body.get("profile")))) {
            throw new BusinessException("简介不能为空");
        }
        Expert expert = new Expert();
        expert.setName(name.trim());
        expert.setUnit(str(body.get("unit")).trim());
        expert.setTitle(str(body.get("title")).trim());
        expert.setProfile(str(body.get("profile")).trim());
        expert.setContact(str(body.get("contact")));
        expert.setIdCard(str(body.get("idCard")));
        expert.setBankCard(str(body.get("bankCard")));
        expert.setBankBranch(str(body.get("bankBranch")));
        Expert saved = expertService.create(expert, user);

        ProjectExpert rel = new ProjectExpert();
        rel.setProjectId(id);
        rel.setExpertId(saved.getId());
        rel.setRole(StringUtils.hasText(str(body.get("role"))) ? str(body.get("role")).trim() : "嘉宾");
        rel.setCreateBy(ParamUtils.usernameOf(user));
        expertRelMapper.insert(rel);

        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_CREATE_EXPERT,
                "项目「" + project.getName() + "」新建专家卡「" + saved.getName() + "」并关联",
                "PROJECT", id, null);
        return saved;
    }

    // ==================== 供应商关联 ====================

    public List<Map<String, Object>> listSuppliers(Long id, AuthUser user) {
        requireProjectReadable(requireProject(id), user);
        List<ProjectSupplier> rels = supplierRelMapper.selectList(new LambdaQueryWrapper<ProjectSupplier>()
                .eq(ProjectSupplier::getProjectId, id).orderByAsc(ProjectSupplier::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectSupplier rel : rels) {
            Supplier supplier = supplierMapper.selectById(rel.getSupplierId());
            if (supplier == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rel.getId());
            m.put("supplierId", supplier.getId());
            m.put("name", supplier.getName());
            m.put("typeName", supplier.getTypeName() == null ? nameOfSupplierType(supplier.getTypeId()) : supplier.getTypeName());
            m.put("contact", supplier.getContact());
            m.put("phone", supplier.getPhone());
            m.put("purpose", rel.getPurpose());
            m.put("fee", rel.getFee());
            result.add(m);
        }
        return result;
    }

    /** 关联供应商：用途按供应商类型自动带出（如酒店/打印/车队等），不手动填。 */
    @Transactional(rollbackFor = Exception.class)
    public void addSupplier(Long id, Long supplierId, AuthUser user) {
        Project project = requireProject(id);
        requireManage(user);
        Supplier supplier = supplierId == null ? null : supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new BusinessException("供应商不存在");
        }
        Long dup = supplierRelMapper.selectCount(new LambdaQueryWrapper<ProjectSupplier>()
                .eq(ProjectSupplier::getProjectId, id).eq(ProjectSupplier::getSupplierId, supplierId));
        if (dup != null && dup > 0) {
            throw new BusinessException("该供应商已在项目名单中");
        }
        ProjectSupplier rel = new ProjectSupplier();
        rel.setProjectId(id);
        rel.setSupplierId(supplierId);
        rel.setPurpose(purposeOf(supplier));
        rel.setCreateBy(ParamUtils.usernameOf(user));
        supplierRelMapper.insert(rel);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_ASSIGN_SUPPLIER,
                "项目「" + project.getName() + "」关联供应商「" + supplier.getName() + "」（用途：" + rel.getPurpose() + "）",
                "PROJECT", id, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeSupplier(Long id, Long supplierId, AuthUser user) {
        Project project = requireProject(id);
        requireManage(user);
        ProjectSupplier rel = supplierRelMapper.selectOne(new LambdaQueryWrapper<ProjectSupplier>()
                .eq(ProjectSupplier::getProjectId, id).eq(ProjectSupplier::getSupplierId, supplierId));
        if (rel == null) {
            throw new BusinessException("该供应商不在项目名单中");
        }
        supplierRelMapper.deleteById(rel.getId());
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_REMOVE_SUPPLIER,
                "项目「" + project.getName() + "」移出供应商「" + supplierMapper.selectById(supplierId).getName() + "」",
                "PROJECT", id, null);
    }

    // ==================== 分阶段资料 / 富文本 ====================

    public List<Map<String, Object>> listDocs(Long id, Long phaseId, AuthUser user) {
        requireProjectReadable(requireProject(id), user);
        LambdaQueryWrapper<ProjectDoc> qw = new LambdaQueryWrapper<ProjectDoc>()
                .eq(ProjectDoc::getProjectId, id)
                .eq(ProjectDoc::getIsDeleted, 0)
                .orderByAsc(ProjectDoc::getCreateTime);
        if (phaseId != null) {
            qw.eq(ProjectDoc::getPhaseId, phaseId);
        }
        List<ProjectDoc> docs = docMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectDoc doc : docs) {
            Map<String, Object> m = toMap(doc);
            m.put("phaseName", nameOfPhase(doc.getPhaseId()));
            m.put("isRichText", doc.getFilePath() != null && doc.getFilePath().startsWith("_richtext/"));
            if (m.get("isRichText") == Boolean.TRUE) {
                m.put("content", storageService.readRichText(doc.getFilePath()));
            }
            result.add(m);
        }
        return result;
    }

    /** 上传分阶段资料（无类型/大小限制）；留痕。 */
    @Transactional(rollbackFor = Exception.class)
    public ProjectDoc uploadDoc(Long id, Long phaseId, MultipartFile file, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrExecutor(project, user)) {
            throw new BusinessException("仅项目负责人或执行人可上传资料");
        }
        if (phaseId == null || phaseMapper.selectById(phaseId) == null) {
            throw new BusinessException("阶段不存在");
        }
        String fileName = storageService.saveFile(file);
        ProjectDoc doc = new ProjectDoc();
        doc.setProjectId(id);
        doc.setPhaseId(phaseId);
        doc.setFileName(file.getOriginalFilename() == null ? fileName : file.getOriginalFilename());
        doc.setFilePath(fileName);
        doc.setUploadBy(ParamUtils.usernameOf(user));
        doc.setIsDeleted(0);
        docMapper.insert(doc);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPLOAD_DOC,
                "项目「" + project.getName() + "」上传资料《" + doc.getFileName() + "》（阶段：" + nameOfPhase(phaseId) + "）",
                "PROJECT", id, null);
        return doc;
    }

    /** 删除分阶段资料（软删留痕；富文本同时清理物理文件）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDoc(Long id, Long docId, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrExecutor(project, user)) {
            throw new BusinessException("仅项目负责人或执行人可删除资料");
        }
        ProjectDoc doc = docMapper.selectById(docId);
        if (doc == null || !doc.getProjectId().equals(id)) {
            throw new BusinessException("资料不存在");
        }
        doc.setIsDeleted(1);
        doc.setDeletedBy(ParamUtils.usernameOf(user));
        doc.setDeletedTime(LocalDateTime.now());
        docMapper.updateById(doc);
        if (doc.getFilePath() != null && doc.getFilePath().startsWith("_richtext/")) {
            storageService.deleteFile(doc.getFilePath());
        }
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_DELETE_DOC,
                "项目「" + project.getName() + "」删除资料《" + doc.getFileName() + "》",
                "PROJECT", id, null);
    }

    /** 保存富文本（自定义文本模板内容），以约定文件落盘并登记 project_doc。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveRichText(Long id, Long phaseId, Long templateId, String content, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrExecutor(project, user)) {
            throw new BusinessException("仅项目负责人或执行人可维护阶段内容");
        }
        if (phaseId == null || phaseMapper.selectById(phaseId) == null) {
            throw new BusinessException("阶段不存在");
        }
        ProjectTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        String filePath = storageService.saveRichText(id, phaseId, templateId, content == null ? "" : content);
        ProjectDoc doc = new ProjectDoc();
        doc.setProjectId(id);
        doc.setPhaseId(phaseId);
        doc.setFileName(template.getName() + "（富文本）");
        doc.setFilePath(filePath);
        doc.setUploadBy(ParamUtils.usernameOf(user));
        doc.setIsDeleted(0);
        docMapper.insert(doc);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPLOAD_DOC,
                "项目「" + project.getName() + "」保存阶段内容《" + template.getName() + "》（阶段：" + nameOfPhase(phaseId) + "）",
                "PROJECT", id, null);
    }

    // ==================== 预算 ====================

    /** 登记预算金额（BUDGET 模板内容）。 */
    @Transactional(rollbackFor = Exception.class)
    public void registerBudget(Long id, BigDecimal amount, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrExecutor(project, user)) {
            throw new BusinessException("仅项目负责人或执行人可登记预算");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("预算金额不能为空且不能为负");
        }
        project.setBudgetAmount(amount);
        this.updateById(project);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_REGISTER_BUDGET,
                "项目「" + project.getName() + "」登记预算：" + amount.toPlainString() + " 元",
                "PROJECT", id, null);
    }

    private Map<String, Object> budgetOf(Project project) {
        BigDecimal expertFee = expertRelMapper.selectList(new LambdaQueryWrapper<ProjectExpert>()
                        .eq(ProjectExpert::getProjectId, project.getId()))
                .stream().map(ProjectExpert::getFee).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal supplierFee = supplierRelMapper.selectList(new LambdaQueryWrapper<ProjectSupplier>()
                        .eq(ProjectSupplier::getProjectId, project.getId()))
                .stream().map(ProjectSupplier::getFee).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budgetAmount", project.getBudgetAmount() == null ? BigDecimal.ZERO : project.getBudgetAmount());
        m.put("expertFee", expertFee);
        m.put("supplierFee", supplierFee);
        m.put("actualTotal", expertFee.add(supplierFee));
        return m;
    }

    // ==================== 结算合同 ====================

    public List<Map<String, Object>> listContracts(Long id, AuthUser user) {
        requireProjectReadable(requireProject(id), user);
        List<ProjectContract> contracts = contractMapper.selectList(new LambdaQueryWrapper<ProjectContract>()
                .eq(ProjectContract::getProjectId, id).orderByDesc(ProjectContract::getId));
        boolean full = user != null && isOwnerOrExecutor(requireProject(id), user);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectContract c : contracts) {
            Map<String, Object> m = toMap(c);
            if (full) {
                m.put("idCard", c.getIdCard());
                m.put("bankCard", c.getBankCard());
            } else {
                m.put("idCard", ExpertService.maskSensitive(c.getIdCard()));
                m.put("bankCard", ExpertService.maskSensitive(c.getBankCard()));
            }
            result.add(m);
        }
        return result;
    }

    /**
     * 登记合同：自动带出专家库身份证/银行卡（项目负责人/执行人可见明文）；
     * 若带出为空或登记时修改，同步回写 expert_info 并写 SYNC_EXPERT_SENSITIVE 留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectContract createContract(Long id, Long expertId, BigDecimal fee, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrExecutor(project, user)) {
            throw new BusinessException("仅项目负责人或执行人可登记合同");
        }
        ProjectExpert rel = expertRelMapper.selectOne(new LambdaQueryWrapper<ProjectExpert>()
                .eq(ProjectExpert::getProjectId, id).eq(ProjectExpert::getExpertId, expertId));
        if (rel == null) {
            throw new BusinessException("该专家不在项目名单中，请先关联专家");
        }
        Expert expert = expertService.getById(expertId);
        if (expert == null) {
            throw new BusinessException("专家不存在");
        }
        if (fee == null || fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("合同金额不能为空且不能为负");
        }
        ProjectContract contract = new ProjectContract();
        contract.setProjectId(id);
        contract.setExpertId(expertId);
        contract.setExpertName(expert.getName());
        contract.setIdCard(expert.getIdCard());
        contract.setBankCard(expert.getBankCard());
        contract.setFee(fee);
        contract.setStatus("DRAFT");
        contract.setCreateBy(ParamUtils.usernameOf(user));
        contractMapper.insert(contract);

        // 登记即回填劳务费快照
        rel.setFee(fee);
        rel.setContractStatus("DRAFT");
        expertRelMapper.updateById(rel);

        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_CREATE_CONTRACT,
                "项目「" + project.getName() + "」登记专家「" + expert.getName() + "」合同（金额 " + fee.toPlainString() + " 元）",
                "PROJECT", id, null);
        return contract;
    }

    /** 上传合同文件（A 信息 + B 文件 都要）。 */
    @Transactional(rollbackFor = Exception.class)
    public void uploadContractFile(Long id, Long contractId, MultipartFile file, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrExecutor(project, user)) {
            throw new BusinessException("仅项目负责人或执行人可上传合同文件");
        }
        ProjectContract contract = contractMapper.selectById(contractId);
        if (contract == null || !contract.getProjectId().equals(id)) {
            throw new BusinessException("合同不存在");
        }
        String oldFile = contract.getFilePath();
        contract.setFilePath(storageService.saveFile(file));
        contractMapper.updateById(contract);
        if (StringUtils.hasText(oldFile)) {
            storageService.deleteFile(oldFile);
        }
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPDATE_CONTRACT,
                "项目「" + project.getName() + "」上传合同文件《" + (file.getOriginalFilename() == null ? "合同" : file.getOriginalFilename()) + "》（专家「" + contract.getExpertName() + "」）",
                "PROJECT", id, null);
    }

    /**
     * 登记时修改敏感字段（身份证/银行卡）：保存后同步回写专家库档案 + 留痕；再发起审批。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateContractSensitive(Long id, Long contractId, Map<String, Object> changes, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrExecutor(project, user)) {
            throw new BusinessException("仅项目负责人或执行人可修改合同敏感字段");
        }
        ProjectContract contract = contractMapper.selectById(contractId);
        if (contract == null || !contract.getProjectId().equals(id)) {
            throw new BusinessException("合同不存在");
        }
        Expert expert = expertService.getById(contract.getExpertId());
        if (expert == null) {
            throw new BusinessException("专家不存在");
        }
        String idCard = str(changes.get("idCard"));
        String bankCard = str(changes.get("bankCard"));
        boolean changed = false;
        StringBuilder desc = new StringBuilder("项目「" + project.getName() + "」回写专家「" + expert.getName() + "」敏感字段：");
        if (StringUtils.hasText(idCard)) {
            if (!idCard.matches("^\\d{17}[\\dXx]$")) {
                throw new BusinessException("身份证号格式不正确（18 位，末位可为 X）");
            }
            if (!idCard.equals(expert.getIdCard())) {
                desc.append("身份证号由[").append(expert.getIdCard() == null ? "空" : "已存在").append("]改为[").append(maskTail(idCard)).append("]；");
                expert.setIdCard(idCard);
                contract.setIdCard(idCard);
                changed = true;
            }
        }
        if (StringUtils.hasText(bankCard)) {
            if (!bankCard.matches("^\\d{13,19}$")) {
                throw new BusinessException("银行卡号格式不正确（13-19 位数字）");
            }
            if (!bankCard.equals(expert.getBankCard())) {
                desc.append("银行卡号由[").append(expert.getBankCard() == null ? "空" : "已存在").append("]改为[").append(maskTail(bankCard)).append("]；");
                expert.setBankCard(bankCard);
                contract.setBankCard(bankCard);
                changed = true;
            }
        }
        if (changed) {
            expertService.update(expert.getId(), expert, user);
            contractMapper.updateById(contract);
            operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                    OperationLogService.ACTION_SYNC_EXPERT_SENSITIVE, desc.toString(),
                    "PROJECT", id, null);
        }
    }

    /** 发起合同审批（复用现有审批流；通过后合同生效）。 */
    public Application submitContractApproval(Long id, Long contractId, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrExecutor(project, user)) {
            throw new BusinessException("仅项目负责人或执行人可发起合同审批");
        }
        ProjectContract contract = contractMapper.selectById(contractId);
        if (contract == null || !contract.getProjectId().equals(id)) {
            throw new BusinessException("合同不存在");
        }
        Map<String, Object> changes = new HashMap<>();
        changes.put("身份证号", contract.getIdCard());
        changes.put("银行卡号", contract.getBankCard());
        return applicationService.submitContractApproval(contractId, id, changes, "专家合同审批", user);
    }

    private String maskTail(String value) {
        if (value == null || value.length() < 5) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    // ==================== 导出 xlsx ====================

    /**
     * 导出项目列表 xlsx（含专家/供应商明细，三 sheet）；写 DOWNLOAD 留痕。
     */
    public Workbook buildExportWorkbook(String status, String startBegin, String startEnd,
                                        String endBegin, String endEnd, Long ownerId,
                                        String category, Long typeId, Long executorId, AuthUser user) {
        List<Project> projects = collectProjects(status, startBegin, startEnd, endBegin, endEnd, ownerId, category, typeId, executorId, user);
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("项目列表");
        String[] headers = {"项目ID", "名称", "大类", "类型", "开始时间", "结束时间", "负责人", "执行人", "当前阶段", "状态", "预算金额", "创建时间"};
        Row head = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            head.createCell(i).setCellValue(headers[i]);
        }
        int r = 1;
        for (Project p : projects) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(p.getId() == null ? "" : String.valueOf(p.getId()));
            row.createCell(1).setCellValue(p.getName() == null ? "" : p.getName());
            row.createCell(2).setCellValue("ONLINE".equals(p.getCategory()) ? "线上" : ("OFFLINE".equals(p.getCategory()) ? "线下" : ""));
            row.createCell(3).setCellValue(nameOfType(p.getTypeId()));
            row.createCell(4).setCellValue(p.getStartDate() == null ? "" : p.getStartDate().format(DATE_FMT));
            row.createCell(5).setCellValue(p.getEndDate() == null ? "" : p.getEndDate().format(DATE_FMT));
            row.createCell(6).setCellValue(usernameOf(p.getOwnerId()));
            row.createCell(7).setCellValue(String.join("、", executorNamesOf(p.getId())));
            row.createCell(8).setCellValue(nameOfPhase(p.getPhaseId()));
            row.createCell(9).setCellValue(statusText(p));
            row.createCell(10).setCellValue(p.getBudgetAmount() == null ? "" : p.getBudgetAmount().toPlainString());
            row.createCell(11).setCellValue(p.getCreateTime() == null ? "" : p.getCreateTime().format(DT_FMT));
        }

        Sheet expertSheet = wb.createSheet("项目专家明细");
        String[] expHeaders = {"项目ID", "项目名称", "专家姓名", "单位", "职称", "角色分工", "劳务费", "合同状态"};
        Row eh = expertSheet.createRow(0);
        for (int i = 0; i < expHeaders.length; i++) {
            eh.createCell(i).setCellValue(expHeaders[i]);
        }
        r = 1;
        for (Project p : projects) {
            for (ProjectExpert rel : expertRelMapper.selectList(new LambdaQueryWrapper<ProjectExpert>()
                    .eq(ProjectExpert::getProjectId, p.getId()).orderByAsc(ProjectExpert::getId))) {
                Expert expert = expertService.getById(rel.getExpertId());
                if (expert == null) {
                    continue;
                }
                Row row = expertSheet.createRow(r++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getName());
                row.createCell(2).setCellValue(expert.getName());
                row.createCell(3).setCellValue(expert.getUnit() == null ? "" : expert.getUnit());
                row.createCell(4).setCellValue(expert.getTitle() == null ? "" : expert.getTitle());
                row.createCell(5).setCellValue(rel.getRole() == null ? "" : rel.getRole());
                row.createCell(6).setCellValue(rel.getFee() == null ? "" : rel.getFee().toPlainString());
                row.createCell(7).setCellValue(rel.getContractStatus() == null ? "" : rel.getContractStatus());
            }
        }

        Sheet supplierSheet = wb.createSheet("项目供应商明细");
        String[] supHeaders = {"项目ID", "项目名称", "供应商名称", "供应商类型", "用途", "费用"};
        Row sh = supplierSheet.createRow(0);
        for (int i = 0; i < supHeaders.length; i++) {
            sh.createCell(i).setCellValue(supHeaders[i]);
        }
        r = 1;
        for (Project p : projects) {
            for (ProjectSupplier rel : supplierRelMapper.selectList(new LambdaQueryWrapper<ProjectSupplier>()
                    .eq(ProjectSupplier::getProjectId, p.getId()).orderByAsc(ProjectSupplier::getId))) {
                Supplier supplier = supplierMapper.selectById(rel.getSupplierId());
                if (supplier == null) {
                    continue;
                }
                Row row = supplierSheet.createRow(r++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getName());
                row.createCell(2).setCellValue(supplier.getName());
                row.createCell(3).setCellValue(nameOfSupplierType(supplier.getTypeId()));
                row.createCell(4).setCellValue(rel.getPurpose() == null ? "" : rel.getPurpose());
                row.createCell(5).setCellValue(rel.getFee() == null ? "" : rel.getFee().toPlainString());
            }
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        for (int i = 0; i < expHeaders.length; i++) {
            expertSheet.autoSizeColumn(i);
        }
        for (int i = 0; i < supHeaders.length; i++) {
            supplierSheet.autoSizeColumn(i);
        }
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_DOWNLOAD, "导出项目列表 xlsx（共 " + projects.size() + " 个项目）",
                "PROJECT", null, null);
        return wb;
    }

    private List<Project> collectProjects(String status, String startBegin, String startEnd,
                                          String endBegin, String endEnd, Long ownerId,
                                          String category, Long typeId, Long executorId, AuthUser user) {
        LambdaQueryWrapper<Project> qw = new LambdaQueryWrapper<Project>().ne(Project::getStatus, STATUS_DELETED);
        if (StringUtils.hasText(status)) {
            if ("CANCELED".equals(status)) {
                qw.eq(Project::getStatus, STATUS_CANCELED);
            } else {
                Long phaseId = parseLong(status);
                if (phaseId != null) {
                    qw.eq(Project::getPhaseId, phaseId);
                }
            }
        }
        if (StringUtils.hasText(startBegin)) {
            qw.ge(Project::getStartDate, parseDate(startBegin));
        }
        if (StringUtils.hasText(startEnd)) {
            qw.le(Project::getStartDate, parseDate(startEnd));
        }
        if (StringUtils.hasText(endBegin)) {
            qw.ge(Project::getEndDate, parseDate(endBegin));
        }
        if (StringUtils.hasText(endEnd)) {
            qw.le(Project::getEndDate, parseDate(endEnd));
        }
        if (ownerId != null) {
            qw.eq(Project::getOwnerId, ownerId);
        }
        if (StringUtils.hasText(category)) {
            qw.eq(Project::getCategory, category);
        }
        if (typeId != null) {
            qw.eq(Project::getTypeId, typeId);
        }
        if (executorId != null) {
            List<Long> projectIds = executorMapper.selectList(new LambdaQueryWrapper<ProjectExecutor>()
                            .eq(ProjectExecutor::getUserId, executorId))
                    .stream().map(ProjectExecutor::getProjectId).distinct().collect(Collectors.toList());
            if (projectIds.isEmpty()) {
                qw.eq(Project::getId, -1L);
            } else {
                qw.in(Project::getId, projectIds);
            }
        }
        // 行级数据权限：非豁免用户仅见本人负责或执行的项目
        if (!canViewAllProjects(user)) {
            appendRowLevelCondition(qw, user);
        }
        qw.orderByDesc(Project::getCreateTime);
        return this.list(qw);
    }

    // ==================== 配置管理（project:manage 由 Controller 校验） ====================

    public List<ProjectType> listTypes() {
        return typeMapper.selectList(new LambdaQueryWrapper<ProjectType>()
                .orderByAsc(ProjectType::getSort).orderByAsc(ProjectType::getId));
    }

    public ProjectType createType(String name, Integer sort) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("类型名称不能为空");
        }
        String trimmed = name.trim();
        if (typeMapper.selectCount(new LambdaQueryWrapper<ProjectType>().eq(ProjectType::getName, trimmed)) > 0) {
            throw new BusinessException("类型名称已存在");
        }
        ProjectType t = new ProjectType();
        t.setName(trimmed);
        t.setSort(sort == null ? 0 : sort);
        typeMapper.insert(t);
        return t;
    }

    public ProjectType updateType(Long id, String name, Integer sort) {
        ProjectType t = typeMapper.selectById(id);
        if (t == null) {
            throw new BusinessException("类型不存在");
        }
        if (StringUtils.hasText(name)) {
            String trimmed = name.trim();
            Long dup = typeMapper.selectCount(new LambdaQueryWrapper<ProjectType>()
                    .eq(ProjectType::getName, trimmed).ne(ProjectType::getId, id));
            if (dup != null && dup > 0) {
                throw new BusinessException("类型名称已存在");
            }
            t.setName(trimmed);
        }
        if (sort != null) {
            t.setSort(sort);
        }
        typeMapper.updateById(t);
        return t;
    }

    public void deleteType(Long id) {
        if (id == null || typeMapper.selectById(id) == null) {
            throw new BusinessException("类型不存在");
        }
        Long used = this.count(new LambdaQueryWrapper<Project>().eq(Project::getTypeId, id).ne(Project::getStatus, STATUS_DELETED));
        if (used != null && used > 0) {
            throw new BusinessException("该类型下仍有 " + used + " 个项目，不能删除");
        }
        typeMapper.deleteById(id);
    }

    public List<ProjectPhase> listPhases() {
        return phaseMapper.selectList(new LambdaQueryWrapper<ProjectPhase>()
                .orderByAsc(ProjectPhase::getSort).orderByAsc(ProjectPhase::getId));
    }

    public ProjectPhase createPhase(String name, Integer sort) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("阶段名称不能为空");
        }
        String trimmed = name.trim();
        if (phaseMapper.selectCount(new LambdaQueryWrapper<ProjectPhase>().eq(ProjectPhase::getName, trimmed)) > 0) {
            throw new BusinessException("阶段名称已存在");
        }
        ProjectPhase p = new ProjectPhase();
        p.setName(trimmed);
        p.setSort(sort == null ? 0 : sort);
        p.setIsActive(1);
        phaseMapper.insert(p);
        return p;
    }

    public ProjectPhase updatePhase(Long id, String name, Integer sort, Integer isActive) {
        ProjectPhase p = phaseMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("阶段不存在");
        }
        if (StringUtils.hasText(name)) {
            String trimmed = name.trim();
            Long dup = phaseMapper.selectCount(new LambdaQueryWrapper<ProjectPhase>()
                    .eq(ProjectPhase::getName, trimmed).ne(ProjectPhase::getId, id));
            if (dup != null && dup > 0) {
                throw new BusinessException("阶段名称已存在");
            }
            p.setName(trimmed);
        }
        if (sort != null) {
            p.setSort(sort);
        }
        if (isActive != null) {
            p.setIsActive(isActive);
        }
        phaseMapper.updateById(p);
        return p;
    }

    public void deletePhase(Long id) {
        ProjectPhase p = phaseMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("阶段不存在");
        }
        Long used = this.count(new LambdaQueryWrapper<Project>().eq(Project::getPhaseId, id).ne(Project::getStatus, STATUS_DELETED));
        if (used != null && used > 0) {
            throw new BusinessException("该阶段下仍有 " + used + " 个项目，不能删除");
        }
        relMapper.delete(new LambdaQueryWrapper<ProjectPhaseTemplateRel>().eq(ProjectPhaseTemplateRel::getPhaseId, id));
        phaseMapper.deleteById(id);
    }

    /** 模板列表（含内置 5 类与管理员新建）。 */
    public List<Map<String, Object>> listTemplates() {
        List<ProjectTemplate> list = templateMapper.selectList(new LambdaQueryWrapper<ProjectTemplate>()
                .orderByAsc(ProjectTemplate::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectTemplate t : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("templateType", t.getTemplateType());
            m.put("createBy", t.getCreateBy());
            m.put("createTime", t.getCreateTime());
            result.add(m);
        }
        return result;
    }

    /** 新建模板（管理员/项目负责人可建；留痕 CREATE_TEMPLATE）。 */
    public ProjectTemplate createTemplate(String name, String templateType, AuthUser user) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("模板名称不能为空");
        }
        if (!StringUtils.hasText(templateType)) {
            throw new BusinessException("模板类型不能为空");
        }
        String type = templateType.trim().toUpperCase();
        if (!List.of("FILE", "EXPERT", "SUPPLIER", "BUDGET", "RICH_TEXT").contains(type)) {
            throw new BusinessException("不支持的模板类型：" + templateType);
        }
        String trimmed = name.trim();
        if (templateMapper.selectCount(new LambdaQueryWrapper<ProjectTemplate>().eq(ProjectTemplate::getName, trimmed)) > 0) {
            throw new BusinessException("模板名称已存在");
        }
        ProjectTemplate t = new ProjectTemplate();
        t.setName(trimmed);
        t.setTemplateType(type);
        t.setCreateBy(ParamUtils.usernameOf(user));
        templateMapper.insert(t);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_CREATE_TEMPLATE,
                "新建项目配置模板「" + t.getName() + "」（类型：" + type + "）",
                "PROJECT", null, null);
        return t;
    }

    /** 阶段-模板绑定（required 必填标记 + 排序），全量替换。 */
    public void savePhaseTemplates(Long phaseId, List<Map<String, Object>> binds, AuthUser user) {
        if (phaseId == null || phaseMapper.selectById(phaseId) == null) {
            throw new BusinessException("阶段不存在");
        }
        relMapper.delete(new LambdaQueryWrapper<ProjectPhaseTemplateRel>().eq(ProjectPhaseTemplateRel::getPhaseId, phaseId));
        int sort = 0;
        if (binds != null) {
            for (Map<String, Object> b : binds) {
                Long templateId = toLong(b.get("templateId"));
                if (templateId == null || templateMapper.selectById(templateId) == null) {
                    continue;
                }
                ProjectPhaseTemplateRel rel = new ProjectPhaseTemplateRel();
                rel.setPhaseId(phaseId);
                rel.setTemplateId(templateId);
                rel.setRequired(toLong(b.get("required")) != null && toLong(b.get("required")) == 1 ? 1 : 0);
                rel.setSort(sort++);
                relMapper.insert(rel);
            }
        }
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPDATE_PROJECT,
                "调整阶段「" + nameOfPhase(phaseId) + "」模板绑定（" + (binds == null ? 0 : binds.size()) + " 项）",
                "PROJECT", null, null);
    }

    /** 阶段模板列表（含必填标记与类型）。 */
    public List<Map<String, Object>> listPhaseTemplates(Long phaseId) {
        List<ProjectPhaseTemplateRel> rels = relMapper.selectList(new LambdaQueryWrapper<ProjectPhaseTemplateRel>()
                .eq(ProjectPhaseTemplateRel::getPhaseId, phaseId).orderByAsc(ProjectPhaseTemplateRel::getSort));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectPhaseTemplateRel rel : rels) {
            ProjectTemplate t = templateMapper.selectById(rel.getTemplateId());
            if (t == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rel.getId());
            m.put("phaseId", rel.getPhaseId());
            m.put("templateId", t.getId());
            m.put("templateName", t.getName());
            m.put("templateType", t.getTemplateType());
            m.put("required", rel.getRequired());
            m.put("sort", rel.getSort());
            result.add(m);
        }
        return result;
    }

    /** 负责人候选：拥有 PROJECT_OWNER 角色的用户 + 超级管理员（SUPER_ADMIN 自动获得）。 */
    public List<Map<String, Object>> listProjectOwners() {
        Role ownerRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "PROJECT_OWNER"));
        List<Long> userIds = new ArrayList<>();
        if (ownerRole != null) {
            userIds.addAll(userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, ownerRole.getId()))
                    .stream().map(UserRole::getUserId).distinct().collect(Collectors.toList()));
        }
        List<Long> admins = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getIsAdmin, true))
                .stream().map(SysUser::getId).collect(Collectors.toList());
        for (Long a : admins) {
            if (!userIds.contains(a)) {
                userIds.add(a);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        if (!userIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .in(SysUser::getId, userIds).eq(SysUser::getStatus, 1));
            for (SysUser u : users) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", u.getId());
                m.put("username", u.getUsername());
                m.put("mobile", u.getMobile());
                result.add(m);
            }
        }
        return result;
    }

    /** 执行人候选（全部在职用户，仅 id/姓名，登录即可访问，供项目页执行人筛选与选择）。 */
    public List<Map<String, Object>> listProjectExecutors() {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1).orderByAsc(SysUser::getUsername));
        List<Map<String, Object>> result = new ArrayList<>(users.size());
        for (SysUser u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            result.add(m);
        }
        return result;
    }

    // ==================== 私有辅助 ====================

    private Project requireProject(Long id) {
        Project p = id == null ? null : this.getById(id);
        if (p == null || STATUS_DELETED.equals(p.getStatus())) {
            throw new BusinessException("项目不存在");
        }
        return p;
    }

    /** 项目级授权：负责人/执行人；超级管理员全权。 */
    private boolean isOwnerOrExecutor(Project project, AuthUser user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        if (project.getOwnerId() != null && project.getOwnerId().equals(user.getUserId())) {
            return true;
        }
        Long cnt = executorMapper.selectCount(new LambdaQueryWrapper<ProjectExecutor>()
                .eq(ProjectExecutor::getProjectId, project.getId()).eq(ProjectExecutor::getUserId, user.getUserId()));
        return cnt != null && cnt > 0;
    }

    private void requireManage(AuthUser user) {
        if (user == null || (!user.isAdmin() && !user.hasPermission("project:manage"))) {
            throw new BusinessException("无权限：需要 project:manage 权限");
        }
    }

    /** 全员可新建：校验 project:create（ADMIN/SUPER_ADMIN 兜底豁免；角色绑定见迁移脚本）。 */
    private void requireCreate(AuthUser user) {
        if (user == null || (!user.isAdmin() && !user.hasPermission("project:create"))) {
            throw new BusinessException("无权限：需要 project:create 权限");
        }
    }

    /** 行级数据权限豁免：拥有 project:view-all（仅 ADMIN 绑定；SUPER_ADMIN 恒全权自动豁免）。 */
    private boolean canViewAllProjects(AuthUser user) {
        return user != null && user.hasPermission("project:view-all");
    }

    /** 项目可读：豁免用户全量可见；否则仅负责人/执行人。 */
    private boolean canViewProject(Project project, AuthUser user) {
        return canViewAllProjects(user) || isOwnerOrExecutor(project, user);
    }

    /** 读出口校验：非豁免用户越权访问返回 403。 */
    private void requireProjectReadable(Project project, AuthUser user) {
        if (!canViewProject(project, user)) {
            throw new BusinessException(403, "无权限访问该项目");
        }
    }

    /** 行级过滤条件：owner_id = 当前用户 OR id IN (project_executor.user_id = 当前用户)。 */
    private void appendRowLevelCondition(LambdaQueryWrapper<Project> qw, AuthUser user) {
        Long uid = user == null ? null : user.getUserId();
        if (uid == null) {
            qw.eq(Project::getId, -1L);
            return;
        }
        List<Long> mine = executorMapper.selectList(new LambdaQueryWrapper<ProjectExecutor>()
                        .eq(ProjectExecutor::getUserId, uid))
                .stream().map(ProjectExecutor::getProjectId).distinct().collect(Collectors.toList());
        if (mine.isEmpty()) {
            qw.eq(Project::getOwnerId, uid);
        } else {
            qw.and(w -> w.eq(Project::getOwnerId, uid).or().in(Project::getId, mine));
        }
    }

    /** 校验目标阶段必填模板是否配齐；返回缺失模板类型中文列表。 */
    private List<String> missingRequiredTemplates(Project project, Long phaseId) {
        List<String> missing = new ArrayList<>();
        List<ProjectPhaseTemplateRel> rels = relMapper.selectList(new LambdaQueryWrapper<ProjectPhaseTemplateRel>()
                .eq(ProjectPhaseTemplateRel::getPhaseId, phaseId).eq(ProjectPhaseTemplateRel::getRequired, 1));
        if (rels.isEmpty()) {
            return missing;
        }
        Long expertCnt = expertRelMapper.selectCount(new LambdaQueryWrapper<ProjectExpert>().eq(ProjectExpert::getProjectId, project.getId()));
        Long supplierCnt = supplierRelMapper.selectCount(new LambdaQueryWrapper<ProjectSupplier>().eq(ProjectSupplier::getProjectId, project.getId()));
        Long docCnt = docMapper.selectCount(new LambdaQueryWrapper<ProjectDoc>()
                .eq(ProjectDoc::getProjectId, project.getId())
                .eq(ProjectDoc::getPhaseId, phaseId)
                .eq(ProjectDoc::getIsDeleted, 0));
        boolean hasBudget = project.getBudgetAmount() != null;
        for (ProjectPhaseTemplateRel rel : rels) {
            ProjectTemplate t = templateMapper.selectById(rel.getTemplateId());
            if (t == null) {
                continue;
            }
            String type = t.getTemplateType();
            if ("FILE".equals(type) && (docCnt == null || docCnt == 0)) {
                missing.add("资料附件（" + t.getName() + "）");
            } else if ("EXPERT".equals(type) && (expertCnt == null || expertCnt == 0)) {
                missing.add("专家（" + t.getName() + "）");
            } else if ("SUPPLIER".equals(type) && (supplierCnt == null || supplierCnt == 0)) {
                missing.add("供应商（" + t.getName() + "）");
            } else if ("BUDGET".equals(type) && !hasBudget) {
                missing.add("预算（" + t.getName() + "）");
            } else if ("RICH_TEXT".equals(type) && (docCnt == null || docCnt == 0)) {
                missing.add("自定义文本（" + t.getName() + "）");
            }
        }
        return missing;
    }

    /** 各阶段及其模板绑定（详情页阶段页签用）。 */
    private List<Map<String, Object>> phasesWithTemplates() {
        List<ProjectPhase> phases = listPhases();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectPhase p : phases) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("sort", p.getSort());
            m.put("isActive", p.getIsActive());
            m.put("templates", listPhaseTemplates(p.getId()));
            result.add(m);
        }
        return result;
    }

    private Map<String, Object> toMap(Project project) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", project.getId());
        m.put("name", project.getName());
        m.put("category", project.getCategory());
        m.put("typeId", project.getTypeId());
        m.put("startDate", project.getStartDate() == null ? null : project.getStartDate().toString());
        m.put("endDate", project.getEndDate() == null ? null : project.getEndDate().toString());
        m.put("ownerId", project.getOwnerId());
        m.put("creatorId", project.getCreatorId());
        m.put("phaseId", project.getPhaseId());
        m.put("status", project.getStatus());
        m.put("cancelReason", project.getCancelReason());
        m.put("cancelTime", project.getCancelTime() == null ? null : project.getCancelTime().format(DT_FMT));
        m.put("budgetAmount", project.getBudgetAmount());
        m.put("createTime", project.getCreateTime() == null ? null : project.getCreateTime().format(DT_FMT));
        m.put("updateTime", project.getUpdateTime() == null ? null : project.getUpdateTime().format(DT_FMT));
        return m;
    }

    private Map<String, Object> toMap(ProjectDoc doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getId());
        m.put("projectId", doc.getProjectId());
        m.put("phaseId", doc.getPhaseId());
        m.put("fileName", doc.getFileName());
        m.put("filePath", doc.getFilePath());
        m.put("uploadBy", doc.getUploadBy());
        m.put("createTime", doc.getCreateTime() == null ? null : doc.getCreateTime().format(DT_FMT));
        return m;
    }

    private Map<String, Object> toMap(ProjectContract c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("projectId", c.getProjectId());
        m.put("expertId", c.getExpertId());
        m.put("expertName", c.getExpertName());
        m.put("idCard", c.getIdCard());
        m.put("bankCard", c.getBankCard());
        m.put("fee", c.getFee());
        m.put("filePath", c.getFilePath());
        m.put("status", c.getStatus());
        m.put("approvalRef", c.getApprovalRef());
        m.put("createBy", c.getCreateBy());
        m.put("createTime", c.getCreateTime() == null ? null : c.getCreateTime().format(DT_FMT));
        return m;
    }

    private List<String> executorNamesOf(Long projectId) {
        List<ProjectExecutor> rels = executorMapper.selectList(new LambdaQueryWrapper<ProjectExecutor>()
                .eq(ProjectExecutor::getProjectId, projectId));
        return rels.stream().map(ProjectExecutor::getUserId).map(this::usernameOf).collect(Collectors.toList());
    }

    /** 项目执行人 userIds（编辑回显/提交用）。 */
    private List<Long> executorIdsOf(Long projectId) {
        return executorMapper.selectList(new LambdaQueryWrapper<ProjectExecutor>()
                .eq(ProjectExecutor::getProjectId, projectId))
                .stream().map(ProjectExecutor::getUserId).collect(Collectors.toList());
    }

    private String nameOfType(Long typeId) {
        if (typeId == null) {
            return "";
        }
        ProjectType t = typeMapper.selectById(typeId);
        return t == null ? "" : t.getName();
    }

    private String nameOfPhase(Long phaseId) {
        if (phaseId == null) {
            return "";
        }
        ProjectPhase p = phaseMapper.selectById(phaseId);
        return p == null ? "" : p.getName();
    }

    private String nameOfSupplierType(Long typeId) {
        if (typeId == null) {
            return "";
        }
        SupplierType t = supplierTypeMapper.selectById(typeId);
        return t == null ? "" : t.getName();
    }

    private String usernameOf(Long userId) {
        if (userId == null) {
            return "";
        }
        SysUser u = sysUserMapper.selectById(userId);
        return u == null ? "" : u.getUsername();
    }

    private String purposeOf(Supplier supplier) {
        String typeName = nameOfSupplierType(supplier.getTypeId());
        String bizKey = supplier.getTypeBizKey();
        if (StringUtils.hasText(bizKey)) {
            return typeName + "（" + bizKey + "）";
        }
        return StringUtils.hasText(typeName) ? typeName : "供应商服务";
    }

    private String statusText(Project p) {
        if (STATUS_CANCELED.equals(p.getStatus())) {
            return "已取消";
        }
        String phase = nameOfPhase(p.getPhaseId());
        return StringUtils.hasText(phase) ? phase : p.getStatus();
    }

    private Long firstPhaseId() {
        ProjectPhase p = phaseMapper.selectOne(new LambdaQueryWrapper<ProjectPhase>()
                .orderByAsc(ProjectPhase::getSort).last("LIMIT 1"));
        return p == null ? null : p.getId();
    }

    private Long parseLong(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Long> toLongList(Object o) {
        List<Long> result = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object item : list) {
                Long v = toLong(item);
                if (v != null && !result.contains(v)) {
                    result.add(v);
                }
            }
        }
        return result;
    }

    private LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim(), DATE_FMT);
        } catch (Exception e) {
            throw new BusinessException("日期格式不正确：" + s + "（应为 yyyy-MM-dd）");
        }
    }

    private LocalDate parseDateNullable(String s) {
        return StringUtils.hasText(s) ? parseDate(s) : null;
    }

    private String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    // ==================== 费用登记 ====================

    /** 登记专家劳务费（结算阶段），写 operation_log 留痕。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveExpertFee(Long id, Long expertId, Double fee, AuthUser user) {
        Project project = requireProject(id);
        requireManage(user);
        ProjectExpert rel = expertRelMapper.selectOne(new LambdaQueryWrapper<ProjectExpert>()
                .eq(ProjectExpert::getProjectId, id)
                .eq(ProjectExpert::getExpertId, expertId));
        if (rel == null) {
            throw new BusinessException("该项目未关联该专家");
        }
        rel.setFee(fee == null ? null : BigDecimal.valueOf(fee));
        expertRelMapper.updateById(rel);
        Expert expert = expertService.getById(expertId);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPDATE_PROJECT,
                "登记项目「" + project.getName() + "」专家「" + (expert == null ? String.valueOf(expertId) : expert.getName())
                        + "」劳务费 ¥" + (fee == null ? "0" : fee),
                "PROJECT", project.getId(), null);
    }

    /** 填写供应商费用，写 operation_log 留痕。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveSupplierFee(Long id, Long supplierId, Double fee, AuthUser user) {
        Project project = requireProject(id);
        requireManage(user);
        ProjectSupplier rel = supplierRelMapper.selectOne(new LambdaQueryWrapper<ProjectSupplier>()
                .eq(ProjectSupplier::getProjectId, id)
                .eq(ProjectSupplier::getSupplierId, supplierId));
        if (rel == null) {
            throw new BusinessException("该项目未关联该供应商");
        }
        rel.setFee(fee == null ? null : BigDecimal.valueOf(fee));
        supplierRelMapper.updateById(rel);
        Supplier supplier = supplierMapper.selectById(supplierId);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPDATE_PROJECT,
                "登记项目「" + project.getName() + "」供应商「" + (supplier == null ? String.valueOf(supplierId) : supplier.getName())
                        + "」费用 ¥" + (fee == null ? "0" : fee),
                "PROJECT", project.getId(), null);
    }
}
