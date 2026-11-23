package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.ParamUtils;
import com.jshz.expert.entity.Application;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.OperationLog;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectAssistant;
import com.jshz.expert.entity.ProjectBudget;
import com.jshz.expert.entity.ProjectContract;
import com.jshz.expert.entity.ProjectDoc;
import com.jshz.expert.entity.ProjectExecutor;
import com.jshz.expert.entity.ProjectExpert;
import com.jshz.expert.entity.ProjectPhase;
import com.jshz.expert.entity.ProjectPhaseTemplateRel;
import com.jshz.expert.entity.ProjectSupplier;
import com.jshz.expert.entity.ProjectTemplate;
import com.jshz.expert.entity.ProjectType;
import com.jshz.expert.entity.ProjectTypePhaseRel;
import com.jshz.expert.entity.ProjectPhaseHistory;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.Supplier;
import com.jshz.expert.entity.SupplierCity;
import com.jshz.expert.entity.SupplierType;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.entity.UserRole;
import com.jshz.expert.mapper.OperationLogMapper;
import com.jshz.expert.mapper.ProjectAssistantMapper;
import com.jshz.expert.mapper.ProjectContractMapper;
import com.jshz.expert.mapper.ProjectDocMapper;
import com.jshz.expert.mapper.ProjectExecutorMapper;
import com.jshz.expert.mapper.ProjectExpertMapper;
import com.jshz.expert.mapper.ProjectMapper;
import com.jshz.expert.mapper.ProjectPhaseMapper;
import com.jshz.expert.mapper.ProjectPhaseTemplateRelMapper;
import com.jshz.expert.entity.ProjectSchedule;
import com.jshz.expert.entity.ProjectSpeaker;
import com.jshz.expert.entity.ProjectStageForm;
import com.jshz.expert.mapper.ProjectScheduleMapper;
import com.jshz.expert.mapper.ProjectSpeakerMapper;
import com.jshz.expert.mapper.ProjectStageFormMapper;
import com.jshz.expert.mapper.ProjectSupplierMapper;
import com.jshz.expert.mapper.ProjectTemplateMapper;
import com.jshz.expert.mapper.ProjectTypeMapper;
import com.jshz.expert.mapper.ProjectTypePhaseRelMapper;
import com.jshz.expert.mapper.ProjectPhaseHistoryMapper;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.SupplierCityMapper;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 会议预算制作阶段名（阶段字典 data-driven；missingRequiredTemplates 推进校验与报价服务共用）。 */
    public static final String PHASE_BUDGET_MAKING = "会议预算制作";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 团队字典（2026-11-09 项目基本信息改版，固定三个部） */
    private static final Map<String, String> TEAM_LABELS = Map.of(
            "DEPT1", "项目一部",
            "DEPT2", "项目二部",
            "DEPT3", "项目三部");

    /** 项目类型合法值（2026-11-20 新增，固定三类；前端展示名见 PROJECT_TYPE_LABELS） */
    public static final Set<String> PROJECT_TYPES = Set.of("PLATFORM", "DIRECT_PAY", "KEY");

    /** 项目类型展示名 */
    private static final Map<String, String> PROJECT_TYPE_LABELS = Map.of(
            "PLATFORM", "平台类项目",
            "DIRECT_PAY", "直付类项目",
            "KEY", "重点类项目");

    String projectTypeLabelOf(String projectType) {
        return projectType == null ? "" : PROJECT_TYPE_LABELS.getOrDefault(projectType, projectType);
    }

    private final ProjectTypeMapper typeMapper;
    private final ProjectTypePhaseRelMapper typePhaseRelMapper;
    private final ProjectPhaseHistoryMapper phaseHistoryMapper;
    private final ProjectPhaseMapper phaseMapper;
    private final ProjectTemplateMapper templateMapper;
    private final ProjectPhaseTemplateRelMapper relMapper;
    private final ProjectExecutorMapper executorMapper;
    private final ProjectAssistantMapper assistantMapper;
    private final SupplierCityMapper supplierCityMapper;
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
    private final ProjectConfigurationService configuration;
    private final ProjectScheduleMapper scheduleMapper;
    private final ProjectSpeakerMapper speakerMapper;
    private final ProjectStageFormMapper stageFormMapper;
    private final com.jshz.expert.mapper.ComplianceProjectTodoMapper complianceProjectTodoMapper;
    private final com.jshz.expert.mapper.ComplianceProjectDocBindingMapper complianceProjectDocBindingMapper;

    // ==================== 项目 CRUD ====================

    /**
     * 分页列表：关键字(会议名称/客户单位/客户姓名)/状态(当前阶段)/起止时间范围/负责人/大类/类型/执行人/
     * 团队/会议城市/协助人 筛选。
     */
    public Page<Map<String, Object>> pageProjects(String keyword, String status, String startBegin, String startEnd,
                                                  String endBegin, String endEnd, Long ownerId,
                                                  String category,
                                                  String team, Long cityId, Long assistantId,
                                                  long page, long size, AuthUser user) {
        // 软删隐藏：仅过滤 status != DELETED
        LambdaQueryWrapper<Project> qw = new LambdaQueryWrapper<Project>()
                .ne(Project::getStatus, STATUS_DELETED);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(Project::getName, kw)
                    .or().like(Project::getClientCompany, kw)
                    .or().like(Project::getClientName, kw));
        }
        if (StringUtils.hasText(team)) {
            qw.eq(Project::getTeam, team);
        }
        if (cityId != null) {
            qw.eq(Project::getCityId, cityId);
        }
        if (assistantId != null) {
            List<Long> projectIds = assistantMapper.selectList(new LambdaQueryWrapper<ProjectAssistant>()
                            .eq(ProjectAssistant::getUserId, assistantId))
                    .stream().map(ProjectAssistant::getProjectId).distinct().collect(Collectors.toList());
            if (projectIds.isEmpty()) {
                qw.eq(Project::getId, -1L);
            } else {
                qw.in(Project::getId, projectIds);
            }
        }
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
                    m.put("canAdvance", isOwnerOrCreator(project, user));
                    return m;
                }).collect(Collectors.toList());
        return new Page<Map<String, Object>>(p.getCurrent(), p.getSize(), p.getTotal()).setRecords(items);
    }

    /** 新项目（无配置快照）判定：2026-11-09 流程重塑后创建的项目不再绑定类型/快照。 */
    private Map<String, Object> snapshotOrNull(Project project) {
        try {
            return configuration.snapshot(project.getId());
        } catch (Exception e) {
            return null;
        }
    }

    /** 阶段名：快照项目走快照口径，新项目直接查阶段字典。 */
    String phaseNameOf(Project project) {
        if (project.getPhaseId() == null) {
            return "";
        }
        Map<String, Object> rules = snapshotOrNull(project);
        if (rules != null) {
            try {
                return configuration.phase(rules, project.getPhaseId()).getName();
            } catch (Exception ignored) {
            }
        }
        ProjectPhase p = phaseMapper.selectById(project.getPhaseId());
        return p == null ? "" : p.getName();
    }

    /**
     * 项目起始阶段：激活阶段字典中 sort 最小者（2026-11-16 起=「会议预算制作」；
     * 2026-11-09~15 期间曾固定为「日程上传」，新预算阶段插入其后导致新项目跳过预算，改为按 sort 取首）。
     */
    private ProjectPhase firstActivePhase() {
        List<ProjectPhase> phases = phaseMapper.selectList(new LambdaQueryWrapper<ProjectPhase>()
                .eq(ProjectPhase::getIsActive, 1).orderByAsc(ProjectPhase::getSort).last("LIMIT 1"));
        if (!phases.isEmpty()) {
            return phases.get(0);
        }
        throw new BusinessException("project_phase 无激活阶段，请先执行阶段迁移脚本");
    }

    private Map<String, Object> toBriefMap(Project project) {
        Map<String, Object> m = toMap(project);
        m.put("phaseName", phaseNameOf(project));
        m.put("ownerName", usernameOf(project.getOwnerId()));
        m.put("creatorName", usernameOf(project.getCreatorId()));
        // 2026-11-09 流程重塑：类型/执行人概念删除，改为日程上传阶段状态
        ProjectSchedule schedule = scheduleMapper.selectOne(new LambdaQueryWrapper<ProjectSchedule>()
                .eq(ProjectSchedule::getProjectId, project.getId())
                .eq(ProjectSchedule::getIsCurrent, 1)
                .orderByDesc(ProjectSchedule::getId)
                .last("LIMIT 1"));
        m.put("scheduleStatus", schedule == null ? "NONE" : schedule.getParseStatus());
        Long confirmedCnt = speakerMapper.selectCount(new LambdaQueryWrapper<ProjectSpeaker>()
                .eq(ProjectSpeaker::getProjectId, project.getId())
                .eq(ProjectSpeaker::getConfirmed, 1));
        m.put("speakerConfirmedCnt", confirmedCnt == null ? 0 : confirmedCnt);
        m.put("teamLabel", teamLabelOf(project.getTeam()));
        m.put("cityName", cityNameOf(project.getCityId()));
        m.put("assistantNames", assistantNamesOf(project.getId()));
        m.put("assistantIds", assistantIdsOf(project.getId()));
        return m;
    }

    /**
     * 详情：项目 + 关联专家/供应商/资料/合同 + 预算汇总 + 阶段模板 + 时间线。
     */
    public Map<String, Object> detail(Long id, AuthUser user) {
        Project project = requireProject(id);
        requireProjectReadable(project, user);
        Map<String, Object> m = toBriefMap(project);
        boolean fullSensitive = isProjectMember(project, user);
        m.put("experts", listExperts(id, user));
        m.put("suppliers", listSuppliers(id, user));
        m.put("docs", listDocs(id, null, user));
        m.put("contracts", listContracts(id, user));
        m.put("budget", budgetOf(project));
        Map<String, Object> rules = snapshotOrNull(project);
        if (rules != null) {
            m.put("phases", configuration.display(rules));
        } else {
            // 2026-11-09 流程重塑：新项目无快照，阶段=激活阶段字典（本期仅「日程上传」），无模板要求
            List<Map<String, Object>> phaseList = new ArrayList<>();
            for (ProjectPhase p : phaseMapper.selectList(new LambdaQueryWrapper<ProjectPhase>()
                    .eq(ProjectPhase::getIsActive, 1).orderByAsc(ProjectPhase::getSort).orderByAsc(ProjectPhase::getId))) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("phaseId", p.getId());
                item.put("id", p.getId());
                item.put("name", p.getName());
                item.put("sort", p.getSort());
                item.put("isActive", p.getIsActive());
                item.put("templates", List.of());
                phaseList.add(item);
            }
            m.put("phases", phaseList);
        }
        m.put("timeline", timeline(id, user));
        m.put("canManage", user != null && (user.isAdmin() || user.hasPermission("project:manage")));
        m.put("canAdvance", isOwnerOrCreator(project, user));
        m.put("canOperate", fullSensitive);
        // REQ-20261102 需求三：回退权限独立字段（2026-11-09 起与推进一致=负责人+创建人）
        m.put("canRollback", isOwnerOrCreator(project, user));
        // 恢复（与取消对称，Owner 2026-11-10）：仅已取消项目且负责人/创建人可见
        m.put("canRestore", STATUS_CANCELED.equals(project.getStatus()) && isOwnerOrCreator(project, user));
        // 基础信息编辑（Owner 2026-11-10 放宽）：管理员/project:manage 或项目成员均可编辑
        m.put("canEditBasic", user != null && (user.isAdmin() || user.hasPermission("project:manage")
                || isProjectMember(project, user)));

        // 合规要求注意事项（Owner 2026-11-10 记事本需求规则5）：绑定文档名+推送待办（推送时快照）
        var complianceBinding = complianceProjectDocBindingMapper.selectById(id);
        m.put("complianceBoundDocTitle", complianceBinding == null ? null : complianceBinding.getDocTitle());
        m.put("complianceTodos", complianceProjectTodoMapper.selectForProject(id));
        // 阶段变更历史（最近 20 条，倒序）
        List<ProjectPhaseHistory> hists = phaseHistoryMapper.selectList(new LambdaQueryWrapper<ProjectPhaseHistory>()
                .eq(ProjectPhaseHistory::getProjectId, id)
                .orderByDesc(ProjectPhaseHistory::getCreateTime)
                .last("LIMIT 20"));
        m.put("phaseHistory", hists);
        m.put("missingRequiredTemplates", missingRequiredTemplates(project, project.getPhaseId(), (String) m.get("phaseName")));
        return m;
    }

    public Map<String,Object> projectConfiguration(Long id, AuthUser user) {
        requireProjectReadable(requireProject(id), user);
        return configuration.snapshot(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Project create(Map<String, Object> body, AuthUser user) {
        requireCreate(user);
        String name = str(body.get("name"));
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("会议名称不能为空");
        }
        String category = str(body.get("category"));
        if (!"ONLINE".equals(category) && !"OFFLINE".equals(category)) {
            throw new BusinessException("地点必须为线上(ONLINE)或线下(OFFLINE)");
        }
        // 项目类型（2026-11-20 新增，必填，固定三类）
        String projectType = str(body.get("projectType"));
        if (!PROJECT_TYPES.contains(projectType)) {
            throw new BusinessException("项目类型必须为平台类(PLATFORM)/直付类(DIRECT_PAY)/重点类(KEY)");
        }
        // 团队（2026-11-09 新增，必填，固定三个部）
        String team = str(body.get("team"));
        if (!StringUtils.hasText(team)) {
            throw new BusinessException("团队不能为空（项目一部/项目二部/项目三部）");
        }
        validateTeam(team);
        // 会议城市（非必填；校验存在于供应商城市字典）
        Long cityId = toLong(body.get("cityId"));
        validateCity(cityId);
        // 项目起止时间（必填）
        LocalDate startDate = parseDateNullable(str(body.get("startDate")));
        LocalDate endDate = parseDateNullable(str(body.get("endDate")));
        if (startDate == null || endDate == null) {
            throw new BusinessException("项目起止时间必填");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("项目结束时间不能早于开始时间");
        }
        // 会议举办时间（非必填；单日=起止同日）
        LocalDate meetingStart = parseDateNullable(str(body.get("meetingStartDate")));
        LocalDate meetingEnd = parseDateNullable(str(body.get("meetingEndDate")));
        validateMeetingRange(meetingStart, meetingEnd);
        // 总负责人：默认创建人（2026-11-09 起全员可选，不再限定 PROJECT_OWNER 角色）
        Long ownerId = toLong(body.get("ownerId"));
        if (ownerId == null) {
            ownerId = user == null ? null : user.getUserId();
        }
        if (ownerId == null) {
            throw new BusinessException("总负责人不能为空（默认创建人）");
        }
        // 2026-11-09 流程重塑：项目类型概念删除；新建不可选阶段，固定落第一阶段「日程上传」
        ProjectPhase phase = firstActivePhase();

        Project project = new Project();
        project.setName(name.trim());
        project.setCategory(category);
        project.setProjectType(projectType);
        project.setTeam(team);
        project.setCityId(cityId);
        project.setMeetingStartDate(meetingStart);
        project.setMeetingEndDate(meetingEnd);
        String clientCompany = str(body.get("clientCompany"));
        project.setClientCompany(clientCompany == null ? null : clientCompany.trim());
        String clientName = str(body.get("clientName"));
        project.setClientName(clientName == null ? null : clientName.trim());
        String remark = str(body.get("remark"));
        project.setRemark(remark == null ? null : remark.trim());
        project.setTypeId(null);   // 类型已删除概念，列保留停用
        project.setStartDate(startDate);
        project.setEndDate(endDate);
        project.setOwnerId(ownerId);
        project.setCreatorId(user == null ? null : user.getUserId());
        project.setPhaseId(phase.getId());
        project.setStatus(STATUS_ACTIVE);
        this.save(project);

        // 协助人（多选，全员可选，非必填）；执行人概念已删除，不再写入
        saveAssistants(project.getId(), toLongList(body.get("assistantIds")));

        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_CREATE_PROJECT, "创建项目「" + project.getName() + "」",
                "PROJECT", project.getId(), null);
        return project;
    }

    /**
     * 编辑项目基础信息（Owner 2026-11-10 放宽口径：项目成员可编辑，与日程上传 requireOperable 同口径；
     * 原 requireManage（admin/project:manage）保留为人员结构守卫——携带 ownerId/assistantIds 时仍须
     * 负责人/创建人/admin，防协助人越权调整项目负责人结构）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Project update(Long id, Map<String, Object> body, AuthUser user) {
        Project project = requireProject(id);
        requireOperable(project, user);
        boolean personnelCarrier = body.containsKey("ownerId") || body.containsKey("assistantIds");
        if (personnelCarrier && !(user != null && (user.isAdmin() || isOwnerOrCreator(project, user)))) {
            throw new BusinessException("仅负责人/创建人可修改项目人员（负责人/协助人）");
        }
        if (StringUtils.hasText(str(body.get("name")))) {
            project.setName(str(body.get("name")).trim());
        }
        String category = str(body.get("category"));
        if (StringUtils.hasText(category)) {
            if (!"ONLINE".equals(category) && !"OFFLINE".equals(category)) {
                throw new BusinessException("地点必须为线上(ONLINE)或线下(OFFLINE)");
            }
            project.setCategory(category);
        }
        // 项目类型（2026-11-20 新增；编辑时携带才校验，存量项目为空可补填）
        if (body.containsKey("projectType")) {
            String projectType = str(body.get("projectType"));
            if (!PROJECT_TYPES.contains(projectType)) {
                throw new BusinessException("项目类型必须为平台类(PLATFORM)/直付类(DIRECT_PAY)/重点类(KEY)");
            }
            project.setProjectType(projectType);
        }
        String team = str(body.get("team"));
        if (StringUtils.hasText(team)) {
            validateTeam(team);
            project.setTeam(team);
        }
        Long cityId = toLong(body.get("cityId"));
        if (body.containsKey("cityId")) {
            validateCity(cityId);
            project.setCityId(cityId);
        }
        if (str(body.get("meetingStartDate")) != null) {
            LocalDate ms = parseDateNullable(str(body.get("meetingStartDate")));
            LocalDate me = body.containsKey("meetingEndDate") && str(body.get("meetingEndDate")) != null
                    ? parseDateNullable(str(body.get("meetingEndDate"))) : project.getMeetingEndDate();
            validateMeetingRange(ms, me);
            project.setMeetingStartDate(ms);
        }
        if (str(body.get("meetingEndDate")) != null) {
            LocalDate me = parseDateNullable(str(body.get("meetingEndDate")));
            LocalDate ms = body.containsKey("meetingStartDate") && str(body.get("meetingStartDate")) != null
                    ? parseDateNullable(str(body.get("meetingStartDate"))) : project.getMeetingStartDate();
            validateMeetingRange(ms, me);
            project.setMeetingEndDate(me);
        }
        if (body.containsKey("clientCompany")) {
            String v = str(body.get("clientCompany"));
            project.setClientCompany(v == null ? null : v.trim());
        }
        if (body.containsKey("clientName")) {
            String v = str(body.get("clientName"));
            project.setClientName(v == null ? null : v.trim());
        }
        if (body.containsKey("remark")) {
            String v = str(body.get("remark"));
            project.setRemark(v == null ? null : v.trim());
        }
        // 2026-11-09 流程重塑：类型/阶段/执行人均不可通过编辑修改（阶段只能走推进接口）
        if (toLong(body.get("phaseId")) != null
                && !toLong(body.get("phaseId")).equals(project.getPhaseId())) {
            throw new BusinessException("请通过阶段推进或回退操作修改项目阶段");
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
        this.updateById(project);

        // 协助人：显式携带 assistantIds（数组可为空=清空协助人）时全量替换；执行人概念已删除，传入忽略
        if (body.containsKey("assistantIds")) {
            saveAssistants(id, toLongList(body.get("assistantIds")));
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
     * 推进/回退阶段（REQ-20261102 需求三增强）：
     * - 推进：仅相邻（target.sort==current.sort+1）且必填材料校验；
     * - 回退：相邻与跨级均允许（target.sort<current.sort），reason 必填；
     * - 权限不变（负责人/执行人/admin）；同阶段切换（取消恢复复用）跳过校验；
     * - 每次变更写 project_phase_history（ADVANCE/ROLLBACK），事务保证与项目更新原子性。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePhase(Long id, Long phaseId, String reason, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrCreator(project, user)) {
            throw new BusinessException("仅项目总负责人或创建人可推进/回退阶段");
        }
        Map<String, Object> rules = snapshotOrNull(project);
        ProjectPhase target;
        ProjectPhase current;
        if (rules == null) {
            // 2026-11-09 流程重塑：新项目不绑定类型/快照，阶段直接以字典为准（当前仅「日程上传」）
            target = phaseId == null ? null : phaseMapper.selectById(phaseId);
            current = project.getPhaseId() == null ? null : phaseMapper.selectById(project.getPhaseId());
            if (target == null) {
                throw new BusinessException("目标阶段不存在");
            }
            if (target.getIsActive() != null && target.getIsActive() == 0) {
                throw new BusinessException("目标阶段已停用");
            }
        } else {
            target = phaseId == null ? null : configuration.phase(rules, phaseId);
            if (target == null) {
                throw new BusinessException("目标阶段不存在");
            }
            if (target.getIsActive() != null && target.getIsActive() == 0) {
                throw new BusinessException("目标阶段已停用");
            }
            current = configuration.phase(rules, project.getPhaseId());
        }
        if (current == null) {
            throw new BusinessException("当前阶段不存在");
        }
        boolean samePhase = target.getId().equals(current.getId());
        String actionType = null;
        if (!samePhase && current.getSort() != null && target.getSort() != null) {
            if (target.getSort() > current.getSort()) {
                // 推进：仅相邻 + 必填材料校验
                if (target.getSort() != current.getSort() + 1) {
                    throw new BusinessException("仅支持相邻阶段推进（" + current.getName() + " → " + target.getName() + "）");
                }
                List<String> missing = missingRequiredTemplates(project, current.getId(), current.getName());
                if (!missing.isEmpty()) {
                    throw new BusinessException("当前阶段「" + current.getName() + "」必填材料未提交，无法推进：缺少 " + String.join("、", missing));
                }
                actionType = ProjectPhaseHistory.ACTION_ADVANCE;
            } else {
                // 回退：相邻与跨级均允许，原因必填
                if (!StringUtils.hasText(reason)) {
                    throw new BusinessException("回退原因不能为空");
                }
                actionType = ProjectPhaseHistory.ACTION_ROLLBACK;
            }
        }
        String before = current.getName();
        project.setPhaseId(target.getId());
        if (STATUS_CANCELED.equals(project.getStatus())) {
            project.setStatus(STATUS_ACTIVE);
        }
        this.updateById(project);
        // 阶段历史（推进/回退均记录；同阶段切换不记录）
        if (actionType != null) {
            ProjectPhaseHistory hist = new ProjectPhaseHistory();
            hist.setProjectId(id);
            hist.setFromPhaseId(current.getId());
            hist.setToPhaseId(target.getId());
            hist.setActionType(actionType);
            hist.setReason(StringUtils.hasText(reason) ? reason.trim() : null);
            hist.setOperatorId(ParamUtils.userIdOf(user));
            hist.setOperatorName(ParamUtils.usernameOf(user));
            phaseHistoryMapper.insert(hist);
        }
        String reasonSuffix = StringUtils.hasText(reason) ? "（原因：" + reason.trim() + "）" : "";
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_CHANGE_PHASE,
                "项目「" + project.getName() + "」阶段变更：" + before + " → " + target.getName() + reasonSuffix,
                "PROJECT", id, null);
    }

    /** A16/D3：预算确认后以会议预算合计覆盖项目财务登记金额（project.budget_amount），仅限本用途调用。 */
    public void overrideBudgetAmount(Long projectId, java.math.BigDecimal amount) {
        Project patch = new Project();
        patch.setId(projectId);
        patch.setBudgetAmount(amount);
        updateById(patch);
    }

    /** 取消（必填原因）；取消后仍可回退（changePhase 已支持从 CANCELED 恢复）。 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrCreator(project, user)) {
            throw new BusinessException("仅项目总负责人或创建人可取消项目");
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

    /**
     * 恢复已取消项目（Owner 2026-11-10：取消的对称操作）：仅 CANCELED 可恢复，权限与取消一致（负责人/创建人）。
     * 恢复后 status=ACTIVE、阶段保持不变；cancelReason/cancelTime 保留作历史留痕，不回写阶段变更记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id, AuthUser user) {
        Project project = requireProject(id);
        if (!isOwnerOrCreator(project, user)) {
            throw new BusinessException("仅项目总负责人或创建人可恢复项目");
        }
        if (!STATUS_CANCELED.equals(project.getStatus())) {
            throw new BusinessException("仅已取消的项目可恢复");
        }
        project.setStatus(STATUS_ACTIVE);
        this.updateById(project);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPDATE_PROJECT,
                "恢复项目「" + project.getName() + "」（原取消原因：" + project.getCancelReason() + "）",
                "PROJECT", id, null);
    }

    /** 时间线：operation_log target_type=PROJECT 倒序（与后台日志同源）。 */
    public List<OperationLog> timeline(Long id, AuthUser user) {
        requireProjectReadable(requireProject(id), user);
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
        boolean full = user != null && (isProjectMember(requireProject(id), user));
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
            // 身份证有效期与过期风险（2026-11-23：引入专家后前端据此自动提示）
            m.put("idCardExpiry", expert.getIdCardExpiry());
            m.put("idCardExpiryRisk", IdCardExpiryExtractor.risk(expert.getIdCardExpiry(), LocalDate.now(DashboardReadService.ZONE)));
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
            boolean missing = supplier == null;
            if (missing) {
                supplier = new Supplier();
                supplier.setId(rel.getSupplierId());
                supplier.setName("已删除供应商#" + rel.getSupplierId());
                supplier.setTypeName("历史关联");
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("missing", missing);
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
        Supplier supplier = supplierId == null ? null : supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getId, supplierId).last("FOR UPDATE"));
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
                "项目「" + project.getName() + "」移出供应商「" + (supplierMapper.selectById(supplierId) == null ? "已删除供应商#" + supplierId : supplierMapper.selectById(supplierId).getName()) + "」",
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
            m.put("phaseName", docPhaseName(id, doc.getPhaseId()));
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
        if (!isProjectMember(project, user)) {
            throw new BusinessException("仅项目成员（负责人/创建人/协助人）可上传资料");
        }
        if (phaseId == null) {
            throw new BusinessException("阶段不存在");
        }
        validatePhaseForProject(project, phaseId);
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
                "项目「" + project.getName() + "」上传资料《" + doc.getFileName() + "》（阶段：" + phaseNameOf(requireProject(id)) + "）",
                "PROJECT", id, null);
        return doc;
    }

    /** 删除分阶段资料（软删留痕；富文本同时清理物理文件）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDoc(Long id, Long docId, AuthUser user) {
        Project project = requireProject(id);
        if (!isProjectMember(project, user)) {
            throw new BusinessException("仅项目成员（负责人/创建人/协助人）可删除资料");
        }
        ProjectDoc doc = docMapper.selectById(docId);
        if (doc == null || !doc.getProjectId().equals(id)) {
            throw new BusinessException("资料不存在");
        }
        if (doc.getFilePath() != null && doc.getFilePath().startsWith(ProjectStorageService.QUOTE_FILE_PREFIX)) {
            throw new BusinessException("已生成的对客报价随版本保留");
        }
        doc.setIsDeleted(1);
        doc.setDeletedBy(ParamUtils.usernameOf(user));
        doc.setDeletedTime(LocalDateTime.now());
        docMapper.updateById(doc);
        if (doc.getFilePath() != null && doc.getFilePath().startsWith("_richtext/")) {
            if (docMapper.selectCount(new LambdaQueryWrapper<ProjectDoc>()
                    .eq(ProjectDoc::getFilePath, doc.getFilePath()).eq(ProjectDoc::getIsDeleted, 0)) == 0) {
                if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override public void afterCommit() { storageService.deleteFile(doc.getFilePath()); }
                        });
                } else {
                    storageService.deleteFile(doc.getFilePath());
                }
            }
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
        if (!isProjectMember(project, user)) {
            throw new BusinessException("仅项目成员（负责人/创建人/协助人）可维护阶段内容");
        }
        if (phaseId == null) {
            throw new BusinessException("阶段不存在");
        }
        validatePhaseForProject(project, phaseId);
        if (snapshotOrNull(project) == null) {
            // 2026-11-09 流程重塑：新流程项目无模板体系，不支持富文本阶段内容
            throw new BusinessException("当前项目未配置富文本资料要求");
        }
        Map<String,Object> requirement = configuration.requirements(id,phaseId).stream()
                .filter(r -> java.util.Objects.equals(ProjectConfigurationService.number(r.get("templateId")),templateId) && "RICH_TEXT".equals(r.get("templateType")))
                .findFirst().orElseThrow(() -> new BusinessException("此阶段未配置该富文本资料要求"));
        ProjectTemplate template = new ProjectTemplate(); template.setId(templateId); template.setName(requirement.get("name").toString());
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
                "项目「" + project.getName() + "」保存阶段内容《" + template.getName() + "》（阶段：" + phaseNameOf(requireProject(id)) + "）",
                "PROJECT", id, null);
    }

    // ==================== 项目阶段表单（2026-11-20：会前财务资料上传按项目类型差异化填写） ====================

    /**
     * 读取某项目某阶段的表单数据（无记录时 formData 返回 null）。
     * 权限：项目可读者即可查看（与阶段资料同口径）。
     */
    private void applyFinanceProjectValues(Project project, Long phaseId, Map<String, Object> fields) {
        if (!"会前财务资料上传".equals(docPhaseName(project, phaseId))) return;
        fields.put("meetingName", project.getName());
        LocalDate start = project.getMeetingStartDate();
        LocalDate end = project.getMeetingEndDate();
        String meetingDate = start != null && end != null && !start.equals(end)
                ? start + " 至 " + end : java.util.Objects.toString(start != null ? start : end, "");
        fields.put("meetingDate", meetingDate);
        if ("PLATFORM".equals(project.getProjectType()) || "DIRECT_PAY".equals(project.getProjectType())) {
            fields.put("ourOwner", usernameOf(project.getOwnerId()));
        }
    }

    public Map<String, Object> getStageForm(Long id, Long phaseId, AuthUser user) {
        Project project = requireProject(id);
        requireProjectReadable(project, user);
        if (phaseId == null) {
            throw new BusinessException("阶段不能为空");
        }
        ProjectStageForm form = stageFormMapper.selectOne(new LambdaQueryWrapper<ProjectStageForm>()
                .eq(ProjectStageForm::getProjectId, id)
                .eq(ProjectStageForm::getPhaseId, phaseId)
                .orderByDesc(ProjectStageForm::getId)
                .last("LIMIT 1"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectId", id);
        m.put("phaseId", phaseId);
        if (form == null) {
            m.put("formData", null);
            m.put("updateBy", null);
            m.put("updateTime", null);
            return m;
        }
        Map<String, Object> fields = new LinkedHashMap<>(parseStageFormData(form.getFormData()));
        applyFinanceProjectValues(project, phaseId, fields);
        m.put("formData", fields);
        m.put("updateBy", form.getUpdateBy() == null ? null : usernameOf(form.getUpdateBy()));
        m.put("updateTime", form.getUpdateTime() == null ? null : form.getUpdateTime().format(DT_FMT));
        return m;
    }

    /**
     * 保存某项目某阶段的表单数据（整包覆盖 upsert，不做历史版本）。
     * body: {phaseId, formData:{...}}；权限与阶段资料上传同口径（requireOperable）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveStageForm(Long id, Map<String, Object> body, AuthUser user) {
        Project project = requireProject(id);
        requireOperable(project, user);
        Long phaseId = toLong(body.get("phaseId"));
        if (phaseId == null) {
            throw new BusinessException("阶段不能为空");
        }
        validatePhaseForProject(project, phaseId);
        ProjectPhase savedPhase = phaseMapper.selectById(phaseId);
        String savedPhaseName = savedPhase == null ? "" : savedPhase.getName();
        if ("串场制作".equals(savedPhaseName) || "会前财务资料上传".equals(savedPhaseName)) {
            project = baseMapper.selectOne(new LambdaQueryWrapper<Project>().eq(Project::getId, id).last("FOR UPDATE"));
            if (project == null || !phaseId.equals(project.getPhaseId())) throw new BusinessException("只可保存当前阶段");
            requireOperable(project, user);
        }
        Object raw = body.get("formData");
        Map<String, Object> formData;
        try {
            formData = objectMapper.convertValue(raw,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("表单数据必须为 JSON 对象");
        }
        String json;
        try {
            if (formData == null) formData = new LinkedHashMap<>();
            applyFinanceProjectValues(project, phaseId, formData);
            json = objectMapper.writeValueAsString(formData);
        } catch (Exception e) {
            throw new BusinessException("表单数据序列化失败");
        }
        if ("串场制作".equals(savedPhaseName)) {
            ProjectShowService.validateDraft(objectMapper.convertValue(formData, ProjectShowService.Deck.class));
        }
        if ("会前财务资料上传".equals(savedPhaseName)) {
            byte[] workbook = ProjectFinanceWorkbook.generate(project.getProjectType(), formData);
            archiveStageDocument(id, phaseId, "project_finance_", "xlsx", "合规立项申请表.xlsx", workbook, user, true);
        }
        Long userId = ParamUtils.userIdOf(user);
        ProjectStageForm form = stageFormMapper.selectOne(new LambdaQueryWrapper<ProjectStageForm>()
                .eq(ProjectStageForm::getProjectId, id)
                .eq(ProjectStageForm::getPhaseId, phaseId)
                .orderByDesc(ProjectStageForm::getId)
                .last("LIMIT 1"));
        if (form == null) {
            form = new ProjectStageForm();
            form.setProjectId(id);
            form.setPhaseId(phaseId);
            form.setFormData(json);
            form.setCreateBy(userId);
            form.setUpdateBy(userId);
            stageFormMapper.insert(form);
        } else {
            form.setFormData(json);
            form.setUpdateBy(userId);
            stageFormMapper.updateById(form);
        }
        operationLogService.record(userId, ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPLOAD_DOC,
                "项目「" + project.getName() + "」保存阶段表单（阶段：" + phaseNameOf(project) + "）",
                "PROJECT", id, null);
        return getStageForm(id, phaseId, user);
    }

    /** 阶段表单 JSON 解析（脏数据兜底返回空 Map，不抛异常阻断详情页）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseStageFormData(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> fields = objectMapper.readValue(json, Map.class);
            return fields == null ? Map.of() : fields;
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ==================== 预算 ====================

    /** 登记预算金额（BUDGET 模板内容）。 */
    @Transactional(rollbackFor = Exception.class)
    public void registerBudget(Long id, BigDecimal amount, AuthUser user) {
        Project project = requireProject(id);
        if (!isProjectMember(project, user)) {
            throw new BusinessException("仅项目成员（负责人/创建人/协助人）可登记预算");
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
        boolean full = user != null && isProjectMember(requireProject(id), user);
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
        if (!isProjectMember(project, user)) {
            throw new BusinessException("仅项目成员（负责人/创建人/协助人）可登记合同");
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
        if (!isProjectMember(project, user)) {
            throw new BusinessException("仅项目成员（负责人/创建人/协助人）可上传合同文件");
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
        if (!isProjectMember(project, user)) {
            throw new BusinessException("仅项目成员（负责人/创建人/协助人）可修改合同敏感字段");
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
        if (!isProjectMember(project, user)) {
            throw new BusinessException("仅项目成员（负责人/创建人/协助人）可发起合同审批");
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
    public Workbook buildExportWorkbook(String keyword, String status, String startBegin, String startEnd,
                                        String endBegin, String endEnd, Long ownerId,
                                        String category,
                                        String team, Long cityId, Long assistantId, AuthUser user) {
        List<Project> projects = collectProjects(keyword, status, startBegin, startEnd, endBegin, endEnd, ownerId, category, team, cityId, assistantId, user);
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("项目列表");
        String[] headers = {"项目ID", "会议名称", "项目类型", "地点", "团队", "会议城市", "项目开始时间", "项目结束时间", "举办开始", "举办结束", "总负责人", "协助人", "客户单位", "客户姓名", "当前阶段", "状态", "预算金额", "备注", "创建时间"};
        Row head = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            head.createCell(i).setCellValue(headers[i]);
        }
        int r = 1;
        for (Project p : projects) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(p.getId() == null ? "" : String.valueOf(p.getId()));
            row.createCell(1).setCellValue(p.getName() == null ? "" : p.getName());
            row.createCell(2).setCellValue(projectTypeLabelOf(p.getProjectType()));
            row.createCell(3).setCellValue("ONLINE".equals(p.getCategory()) ? "线上" : ("OFFLINE".equals(p.getCategory()) ? "线下" : ""));
            row.createCell(4).setCellValue(teamLabelOf(p.getTeam()));
            row.createCell(5).setCellValue(cityNameOf(p.getCityId()));
            row.createCell(6).setCellValue(p.getStartDate() == null ? "" : p.getStartDate().format(DATE_FMT));
            row.createCell(7).setCellValue(p.getEndDate() == null ? "" : p.getEndDate().format(DATE_FMT));
            row.createCell(8).setCellValue(p.getMeetingStartDate() == null ? "" : p.getMeetingStartDate().format(DATE_FMT));
            row.createCell(9).setCellValue(p.getMeetingEndDate() == null ? "" : p.getMeetingEndDate().format(DATE_FMT));
            row.createCell(10).setCellValue(usernameOf(p.getOwnerId()));
            row.createCell(11).setCellValue(String.join("、", assistantNamesOf(p.getId())));
            row.createCell(12).setCellValue(p.getClientCompany() == null ? "" : p.getClientCompany());
            row.createCell(13).setCellValue(p.getClientName() == null ? "" : p.getClientName());
            row.createCell(14).setCellValue(phaseNameOf(p));
            row.createCell(15).setCellValue(statusText(p));
            row.createCell(16).setCellValue(p.getBudgetAmount() == null ? "" : p.getBudgetAmount().toPlainString());
            row.createCell(17).setCellValue(p.getRemark() == null ? "" : p.getRemark());
            row.createCell(18).setCellValue(p.getCreateTime() == null ? "" : p.getCreateTime().format(DT_FMT));
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

    private List<Project> collectProjects(String keyword, String status, String startBegin, String startEnd,
                                          String endBegin, String endEnd, Long ownerId,
                                          String category,
                                          String team, Long cityId, Long assistantId, AuthUser user) {
        LambdaQueryWrapper<Project> qw = new LambdaQueryWrapper<Project>().ne(Project::getStatus, STATUS_DELETED);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(Project::getName, kw)
                    .or().like(Project::getClientCompany, kw)
                    .or().like(Project::getClientName, kw));
        }
        if (StringUtils.hasText(team)) {
            qw.eq(Project::getTeam, team);
        }
        if (cityId != null) {
            qw.eq(Project::getCityId, cityId);
        }
        if (assistantId != null) {
            List<Long> projectIds = assistantMapper.selectList(new LambdaQueryWrapper<ProjectAssistant>()
                            .eq(ProjectAssistant::getUserId, assistantId))
                    .stream().map(ProjectAssistant::getProjectId).distinct().collect(Collectors.toList());
            if (projectIds.isEmpty()) {
                qw.eq(Project::getId, -1L);
            } else {
                qw.in(Project::getId, projectIds);
            }
        }
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

    @Transactional(rollbackFor = Exception.class)
    public ProjectType createType(String name, Integer sort, AuthUser user) {
        Map<String,Object> result=configuration.legacyCreate(name,sort,user);
        return typeMapper.selectById(ProjectConfigurationService.number(result.get("id")));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectType updateType(Long id, String name, Integer sort, AuthUser user) {
        configuration.legacyUpdate(id,name,sort,user);
        return typeMapper.selectById(id);
    }

    /** 删除类型：有项目引用则拦截；无引用级联清理类型阶段序列与类型专属绑定（全局兜底不受影响）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id, AuthUser user) {
        configuration.requireManager(user);
        configuration.lock(); configuration.changed();
        if (id == null || typeMapper.selectById(id) == null) {
            throw new BusinessException("类型不存在");
        }
        Long used = this.count(new LambdaQueryWrapper<Project>().eq(Project::getTypeId, id).ne(Project::getStatus, STATUS_DELETED));
        if (used != null && used > 0) {
            throw new BusinessException("该类型下仍有 " + used + " 个项目，不能删除");
        }
        typePhaseRelMapper.delete(new LambdaQueryWrapper<ProjectTypePhaseRel>().eq(ProjectTypePhaseRel::getTypeId, id));
        relMapper.delete(new LambdaQueryWrapper<ProjectPhaseTemplateRel>().eq(ProjectPhaseTemplateRel::getTypeId, id));
        typeMapper.deleteById(id);
        configuration.compact();
    }

    public List<ProjectPhase> listPhases() {
        return phaseMapper.selectList(new LambdaQueryWrapper<ProjectPhase>()
                .orderByAsc(ProjectPhase::getSort).orderByAsc(ProjectPhase::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectPhase createPhase(String name, Integer sort, AuthUser user) {
        configuration.requireManager(user);
        configuration.lock(); configuration.changed();
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

    @Transactional(rollbackFor = Exception.class)
    public ProjectPhase updatePhase(Long id, String name, Integer sort, Integer isActive, AuthUser user) {
        configuration.requireManager(user);
        configuration.lock(); configuration.changed();
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

    /** 删除阶段：被类型流程引用或仍有项目处于该阶段则拦截；无引用级联删除全部（含全局与类型专属）模板绑定。 */
    @Transactional(rollbackFor = Exception.class)
    public void deletePhase(Long id, AuthUser user) {
        configuration.requireManager(user);
        configuration.lock(); configuration.changed();
        configuration.protectReference("phaseId",id);
        ProjectPhase p = phaseMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("阶段不存在");
        }
        Long used = this.count(new LambdaQueryWrapper<Project>().eq(Project::getPhaseId, id).ne(Project::getStatus, STATUS_DELETED));
        if (used != null && used > 0) {
            throw new BusinessException("该阶段下仍有 " + used + " 个项目，不能删除");
        }
        Long flowUsed = typePhaseRelMapper.selectCount(new LambdaQueryWrapper<ProjectTypePhaseRel>().eq(ProjectTypePhaseRel::getPhaseId, id));
        if (flowUsed != null && flowUsed > 0) {
            throw new BusinessException("该阶段已被 " + flowUsed + " 个类型流程引用，请先在类型流程配置中移除后再删除");
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
    @Transactional(rollbackFor = Exception.class)
    public ProjectTemplate createTemplate(String name, String templateType, AuthUser user) {
        configuration.requireManager(user);
        configuration.lock(); configuration.changed();
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

    /** 阶段-模板绑定（required 必填标记 + 排序），全量替换；type_id=0 全局兜底（不触碰类型专属行）。 */
    @Transactional(rollbackFor = Exception.class)
    public void savePhaseTemplates(Long phaseId, List<Map<String, Object>> binds, AuthUser user) {
        configuration.requireManager(user);
        configuration.lock(); configuration.changed();
        saveTypePhaseTemplates(0L, phaseId, binds, user);
    }

    /** 阶段模板列表（全局兜底 type_id=0；含必填标记与类型）。 */
    public List<Map<String, Object>> listPhaseTemplates(Long phaseId) {
        List<ProjectPhaseTemplateRel> rels = relMapper.selectList(new LambdaQueryWrapper<ProjectPhaseTemplateRel>()
                .eq(ProjectPhaseTemplateRel::getTypeId, 0L)
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

    // ==================== 配置 v2（20261102）：类型流程 + 类型专属绑定 + 模板编辑删除 ====================

    /** 某类型的阶段序列（按 sort 升序，join 阶段完整信息）。 */
    public List<Map<String, Object>> listTypePhases(Long typeId) {
        if (typeId == null || typeMapper.selectById(typeId) == null) {
            throw new BusinessException("类型不存在");
        }
        List<ProjectTypePhaseRel> rels = typePhaseRelMapper.selectList(new LambdaQueryWrapper<ProjectTypePhaseRel>()
                .eq(ProjectTypePhaseRel::getTypeId, typeId).orderByAsc(ProjectTypePhaseRel::getSort));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectTypePhaseRel rel : rels) {
            ProjectPhase p = phaseMapper.selectById(rel.getPhaseId());
            if (p == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rel.getId());
            m.put("typeId", rel.getTypeId());
            m.put("phaseId", p.getId());
            m.put("phaseName", p.getName());
            m.put("phaseSort", p.getSort());
            m.put("isActive", p.getIsActive());
            m.put("sort", rel.getSort());
            result.add(m);
        }
        return result;
    }

    /** 全量保存类型阶段序列（先删后插，按入参顺序赋 sort；阶段须存在且启用）。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveTypePhases(Long typeId, List<Long> phaseIds, AuthUser user) {
        configuration.requireManager(user);
        configuration.legacyPhases(typeId,phaseIds,user);
    }

    /** 某类型某阶段模板绑定：优先类型专属，无专属回退全局兜底（meta.fallback=true 标识来源）。 */
    public List<Map<String, Object>> listTypePhaseTemplates(Long typeId, Long phaseId) {
        Map<String,Object> effective = configuration.effective(configuration.definition(typeId));
        return configuration.display(effective).stream().filter(p -> ProjectConfigurationService.number(p.get("id")) == phaseId)
                .map(p -> ProjectConfigurationService.rows(p.get("templates"))).findFirst().orElse(List.of());
    }

    /** 全量保存某类型某阶段模板绑定（typeId=0 走全局兜底；typeId>0 写类型专属行）。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveTypePhaseTemplates(Long typeId, Long phaseId, List<Map<String, Object>> binds, AuthUser user) {
        configuration.requireManager(user);
        if (typeId == null) throw new BusinessException("类型不能为空");
        if (typeId != 0L) { configuration.legacyBindings(typeId,phaseId,binds,user); return; }
        configuration.lock(); configuration.changed();
        if (typeId == null || (typeId != 0L && typeMapper.selectById(typeId) == null)) {
            throw new BusinessException("类型不存在");
        }
        if (phaseId == null || phaseMapper.selectById(phaseId) == null) {
            throw new BusinessException("阶段不存在");
        }
        relMapper.delete(new LambdaQueryWrapper<ProjectPhaseTemplateRel>()
                .eq(ProjectPhaseTemplateRel::getTypeId, typeId)
                .eq(ProjectPhaseTemplateRel::getPhaseId, phaseId));
        int sort = 0;
        if (binds != null) {
            for (Map<String, Object> b : binds) {
                Long templateId = toLong(b.get("templateId"));
                if (templateId == null || templateMapper.selectById(templateId) == null) {
                    continue;
                }
                ProjectPhaseTemplateRel rel = new ProjectPhaseTemplateRel();
                rel.setTypeId(typeId);
                rel.setPhaseId(phaseId);
                rel.setTemplateId(templateId);
                rel.setRequired(toLong(b.get("required")) != null && toLong(b.get("required")) == 1 ? 1 : 0);
                rel.setSort(sort++);
                relMapper.insert(rel);
            }
        }
        String scope = typeId == 0L ? "全局兜底" : "类型「" + typeMapper.selectById(typeId).getName() + "」专属";
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPDATE_PROJECT,
                "调整" + scope + "阶段「" + nameOfPhase(phaseId) + "」模板绑定（" + (binds == null ? 0 : binds.size()) + " 项）",
                "PROJECT", null, null);
    }

    /** 修改模板（名称唯一校验 + 类型枚举校验）。 */
    @Transactional(rollbackFor = Exception.class)
    public ProjectTemplate updateTemplate(Long id, String name, String templateType, AuthUser user) {
        configuration.requireManager(user);
        configuration.lock(); configuration.changed();
        ProjectTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException("模板不存在");
        }
        if (StringUtils.hasText(name)) {
            String trimmed = name.trim();
            Long dup = templateMapper.selectCount(new LambdaQueryWrapper<ProjectTemplate>()
                    .eq(ProjectTemplate::getName, trimmed).ne(ProjectTemplate::getId, id));
            if (dup != null && dup > 0) {
                throw new BusinessException("模板名称已存在");
            }
            t.setName(trimmed);
        }
        if (StringUtils.hasText(templateType)) {
            String type = templateType.trim().toUpperCase();
            if (!List.of("FILE", "EXPERT", "SUPPLIER", "BUDGET", "RICH_TEXT").contains(type)) {
                throw new BusinessException("不支持的模板类型：" + templateType);
            }
            t.setTemplateType(type);
        }
        templateMapper.updateById(t);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPDATE_TEMPLATE,
                "修改项目配置模板「" + t.getName() + "」（类型：" + t.getTemplateType() + "）",
                "PROJECT", null, null);
        return t;
    }

    /** 删除模板：被任意绑定（全局或类型专属）引用则拦截。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id, AuthUser user) {
        configuration.requireManager(user);
        configuration.lock(); configuration.changed();
        configuration.protectReference("templateId",id);
        ProjectTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException("模板不存在");
        }
        Long used = relMapper.selectCount(new LambdaQueryWrapper<ProjectPhaseTemplateRel>().eq(ProjectPhaseTemplateRel::getTemplateId, id));
        if (used != null && used > 0) {
            throw new BusinessException("该模板已被 " + used + " 处绑定引用，请先解除绑定");
        }
        templateMapper.deleteById(id);
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_DELETE_TEMPLATE,
                "删除项目配置模板「" + t.getName() + "」",
                "PROJECT", null, null);
    }

    /** 校验阶段是否属于类型流程（类型未配置流程时不限制）；非法抛业务异常。 */


    private List<Map<String, Object>> relsToMaps(List<ProjectPhaseTemplateRel> rels, boolean fallback) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectPhaseTemplateRel rel : rels) {
            ProjectTemplate t = templateMapper.selectById(rel.getTemplateId());
            if (t == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rel.getId());
            m.put("typeId", rel.getTypeId());
            m.put("phaseId", rel.getPhaseId());
            m.put("templateId", t.getId());
            m.put("templateName", t.getName());
            m.put("templateType", t.getTemplateType());
            m.put("required", rel.getRequired());
            m.put("sort", rel.getSort());
            m.put("fallback", fallback);
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

    // 执行人候选接口已随执行人概念删除（2026-11-09 流程重塑）。

    /** 激活阶段字典（阶段筛选/详情展示用；非配置管理）。 */
    public List<ProjectPhase> listActivePhases() {
        return phaseMapper.selectList(new LambdaQueryWrapper<ProjectPhase>()
                .eq(ProjectPhase::getIsActive, 1).orderByAsc(ProjectPhase::getSort).orderByAsc(ProjectPhase::getId));
    }

    /** 用户选项：全部在职用户（仅 id/姓名；负责人/协助人筛选与选择）。 */
    public List<Map<String, Object>> listUserOptions() {
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

    /** 项目存在且未删除（2026-11-09 改 public：日程解析等协作服务复用）。 */
    public Project requireProject(Long id) {
        Project p = id == null ? null : this.getById(id);
        if (p == null || STATUS_DELETED.equals(p.getStatus())) {
            throw new BusinessException("项目不存在");
        }
        return p;
    }

    /** 项目成员级授权（2026-11-09 执行人概念删除后）：负责人/创建人/协助人；超级管理员全权。 */
    private boolean isProjectMember(Project project, AuthUser user) {
        if (user != null && user.isAdmin()) {
            return true;
        }
        return isRealProjectMember(project, user);
    }

    /**
     * 真实参与关系判定（Owner 2026-11-10 合规记事本推送口径）：负责人/创建人/协助人/历史执行人，
     * **不含管理员快捷放行**——项目全量查看豁免仅用于读取，不扩大推送/处理范围（设计文档 §接口与事务）。
     * 供合规推送与项目选择器共用。
     */
    public boolean isRealProjectMember(Project project, AuthUser user) {
        if (user == null) {
            return false;
        }
        Long uid = user.getUserId();
        if ((project.getOwnerId() != null && project.getOwnerId().equals(uid))
                || (project.getCreatorId() != null && project.getCreatorId().equals(uid))) {
            return true;
        }
        // 兼容历史项目的执行人授权（执行人表已停用写入，老数据仍生效）
        Long cnt = executorMapper.selectCount(new LambdaQueryWrapper<ProjectExecutor>()
                .eq(ProjectExecutor::getProjectId, project.getId()).eq(ProjectExecutor::getUserId, uid));
        if (cnt != null && cnt > 0) {
            return true;
        }
        Long acnt = assistantMapper.selectCount(new LambdaQueryWrapper<ProjectAssistant>()
                .eq(ProjectAssistant::getProjectId, project.getId()).eq(ProjectAssistant::getUserId, uid));
        return acnt != null && acnt > 0;
    }

    /**
     * 本人真实参与的项目列表（Owner 2026-11-10 合规记事本项目选择器）：
     * 负责人/创建人/协助人/历史执行人，不含全量查看豁免；排除已删除项目。
     */
    public List<Project> myParticipatedProjects(AuthUser user) {
        Long uid = user == null ? null : user.getUserId();
        if (uid == null) {
            return List.of();
        }
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        for (Project p : this.list(new LambdaQueryWrapper<Project>()
                .eq(Project::getOwnerId, uid).ne(Project::getStatus, STATUS_DELETED))) {
            ids.add(p.getId());
        }
        for (Project p : this.list(new LambdaQueryWrapper<Project>()
                .eq(Project::getCreatorId, uid).ne(Project::getStatus, STATUS_DELETED))) {
            ids.add(p.getId());
        }
        for (ProjectAssistant a : assistantMapper.selectList(new LambdaQueryWrapper<ProjectAssistant>()
                .eq(ProjectAssistant::getUserId, uid))) {
            ids.add(a.getProjectId());
        }
        for (ProjectExecutor e : executorMapper.selectList(new LambdaQueryWrapper<ProjectExecutor>()
                .eq(ProjectExecutor::getUserId, uid))) {
            ids.add(e.getProjectId());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<Project>()
                .in(Project::getId, ids).ne(Project::getStatus, STATUS_DELETED)
                .orderByDesc(Project::getUpdateTime));
    }

    /** 推进/回退/取消级授权（2026-11-09 Owner 口径）：仅负责人+创建人（执行人概念已删除）。 */
    private boolean isOwnerOrCreator(Project project, AuthUser user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        Long uid = user.getUserId();
        return (project.getOwnerId() != null && project.getOwnerId().equals(uid))
                || (project.getCreatorId() != null && project.getCreatorId().equals(uid));
    }

    /** 供日程解析等协作服务复用：校验当前用户可操作该项目（成员级）。 */
    public void requireOperable(Project project, AuthUser user) {
        if (!isProjectMember(project, user)) {
            throw new BusinessException(403, "仅项目成员（负责人/创建人/协助人）可执行该操作");
        }
    }

    /** 供日程解析等协作服务复用：文件存入项目资料库（调用方负责格式/大小校验）。 */
    public String storageSave(org.springframework.web.multipart.MultipartFile file) {
        return storageService.saveFile(file);
    }

    /** 供日程解析等协作服务复用：按存储相对路径读取文件字节（防穿越；不存在返回 null）。 */
    /** 项目资料库文件解析（防穿越，供文件流端点；Owner 2026-11-12 在线预览）。 */
    public java.nio.file.Path resolveStoredFile(String filePath) {
        return storageService.resolveFile(filePath);
    }

    public void requireArchivedFileReadable(String filePath, AuthUser user) {
        ProjectDoc doc = docMapper.selectOne(new LambdaQueryWrapper<ProjectDoc>()
                .eq(ProjectDoc::getFilePath, filePath)
                .eq(ProjectDoc::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (doc == null) {
            throw new BusinessException(404, "项目文件不存在");
        }
        requireProjectReadable(requireProject(doc.getProjectId()), user);
    }

    public byte[] storageRead(String filePath) {
        java.nio.file.Path p = storageService.resolveFile(filePath);
        if (p == null) return null;
        try {
            return java.nio.file.Files.readAllBytes(p);
        } catch (IOException e) {
            return null;
        }
    }

    /** 供日程解析等协作服务复用：统一操作留痕入口。 */
    public void operationRecord(AuthUser user, String desc, String targetType, Long targetId) {
        operationLogService.record(ParamUtils.userIdOf(user), ParamUtils.usernameOf(user),
                OperationLogService.ACTION_UPDATE_PROJECT, desc, targetType, targetId, null);
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
        return canViewAllProjects(user) || isProjectMember(project, user);
    }

    /** 读出口校验：非豁免用户越权访问返回 403。 */
    /** 读出口校验：非豁免用户越权访问返回 403。（20261103 改 public：合规 v4 清单项目关联复用） */
    public void requireProjectReadable(Project project, AuthUser user) {
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

    /** 校验项目下阶段合法性：快照项目走配置口径，新项目（无快照）要求阶段存在且启用。 */
    private void validatePhaseForProject(Project project, Long phaseId) {
        Map<String, Object> rules = snapshotOrNull(project);
        if (rules != null) {
            configuration.phase(rules, phaseId);
            return;
        }
        ProjectPhase p = phaseMapper.selectById(phaseId);
        if (p == null || (p.getIsActive() != null && p.getIsActive() == 0)) {
            throw new BusinessException("阶段不存在或已停用");
        }
    }

    /** 资料所属阶段名（新项目直接查字典，快照项目走配置口径）。 */
    private String docPhaseName(Long projectId, Long phaseId) {
        if (phaseId == null) return "";
        return docPhaseName(requireProject(projectId), phaseId);
    }

    private String docPhaseName(Project project, Long phaseId) {
        Map<String, Object> rules = snapshotOrNull(project);
        if (rules != null) {
            try {
                return configuration.phase(rules, phaseId).getName();
            } catch (Exception ignored) {
            }
        }
        ProjectPhase p = phaseMapper.selectById(phaseId);
        return p == null ? "" : p.getName();
    }

    private List<ProjectDoc> phaseDocuments(Long projectId, Long phaseId) {
        return docMapper.selectList(new LambdaQueryWrapper<ProjectDoc>()
                .eq(ProjectDoc::getProjectId, projectId).eq(ProjectDoc::getPhaseId, phaseId)
                .eq(ProjectDoc::getIsDeleted, 0));
    }

    private boolean validArchive(ProjectDoc doc, String prefix) {
        return Integer.valueOf(0).equals(doc.getIsDeleted()) && doc.getFilePath() != null
                && doc.getFilePath().startsWith(prefix) && storageService.resolveFile(doc.getFilePath()) != null;
    }

    /** Serialize replacements across the PPT and HTML entry points. Retain superseded files for recovery. */
    void archiveShowDocument(Long projectId, Long phaseId, String extension, String name, byte[] content, AuthUser user) {
        Project locked = baseMapper.selectOne(new LambdaQueryWrapper<Project>().eq(Project::getId, projectId).last("FOR UPDATE"));
        if (locked == null || !phaseId.equals(locked.getPhaseId())) {
            throw new BusinessException("只可在串场制作阶段归档");
        }
        requireOperable(locked, user);
        String prefix = ProjectShowService.FILE_PREFIX;
        String path = storageService.saveGenerated(prefix, extension, content);
        docMapper.update(null, new LambdaUpdateWrapper<ProjectDoc>()
                .eq(ProjectDoc::getProjectId, projectId).eq(ProjectDoc::getPhaseId, phaseId)
                .eq(ProjectDoc::getIsDeleted, 0).apply("LEFT(file_path, {0}) = {1}", prefix.length(), prefix)
                .set(ProjectDoc::getIsDeleted, 1).set(ProjectDoc::getDeletedBy, user.getUsername())
                .set(ProjectDoc::getDeletedTime, LocalDateTime.now()));

        ProjectDoc doc = new ProjectDoc();
        doc.setProjectId(projectId);
        doc.setPhaseId(phaseId);
        doc.setFileName(name);
        doc.setFilePath(path);
        doc.setUploadBy(user.getUsername());
        doc.setIsDeleted(0);
        docMapper.insert(doc);
    }

    void archiveStageDocument(Long projectId, Long phaseId, String prefix, String extension,
                              String name, byte[] content, AuthUser user, boolean replace) {
        ProjectDoc doc = replace ? phaseDocuments(projectId, phaseId).stream()
                .filter(item -> item.getFilePath() != null && item.getFilePath().startsWith(prefix)).findFirst().orElse(null) : null;
        String path = storageService.saveGenerated(prefix, extension, content);
        if (doc == null) {
            doc = new ProjectDoc();
            doc.setProjectId(projectId);
            doc.setPhaseId(phaseId);
            doc.setIsDeleted(0);
        } else {
            String previous = doc.getFilePath();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { storageService.deleteFile(previous); }
                    });
        }
        doc.setFileName(name);
        doc.setFilePath(path);
        doc.setUploadBy(user.getUsername());
        if (doc.getId() == null) docMapper.insert(doc);
        else docMapper.updateById(doc);
    }

    /** 返回当前阶段缺少的业务材料及必填模板。 */
    private List<String> missingRequiredTemplates(Project project, Long phaseId, String phaseName) {
        List<String> missing = new ArrayList<>();
        if (PHASE_BUDGET_MAKING.equals(phaseName)) {
            boolean archived = docMapper.listBudgetQuotePaths(project.getId(), phaseId).stream()
                    .anyMatch(path -> path != null && path.startsWith(ProjectStorageService.QUOTE_FILE_PREFIX)
                            && storageService.resolveFile(path) != null);
            if (!archived) missing.add("保存报价单");
        }
        if ("会前财务资料上传".equals(phaseName) && phaseDocuments(project.getId(), phaseId).stream()
                .noneMatch(doc -> validArchive(doc, "project_finance_"))) {
            missing.add("保存阶段表单");
        }
        if ("串场制作".equals(phaseName) && phaseDocuments(project.getId(), phaseId).stream()
                .noneMatch(doc -> validArchive(doc, ProjectShowService.FILE_PREFIX))) {
            missing.add("上传串场 PPT 或归档网页幻灯片");
        }
        // 2026-11-17 需求：日程上传阶段须上传日程且全部参会专家匹配到专家库（project_speaker.expert_id 非空）
        if ("日程上传".equals(phaseName)) {
            ProjectSchedule schedule = scheduleMapper.selectOne(new LambdaQueryWrapper<ProjectSchedule>()
                    .eq(ProjectSchedule::getProjectId, project.getId())
                    .eq(ProjectSchedule::getIsCurrent, 1)
                    .orderByDesc(ProjectSchedule::getId)
                    .last("LIMIT 1"));
            if (schedule == null) {
                missing.add("上传日程");
            } else {
                Long unmatched = speakerMapper.selectCount(new LambdaQueryWrapper<ProjectSpeaker>()
                        .eq(ProjectSpeaker::getProjectId, project.getId())
                        .and(rows -> rows.eq(ProjectSpeaker::getScheduleId, schedule.getId())
                                .or(saved -> saved.eq(ProjectSpeaker::getConfirmed, 1).isNotNull(ProjectSpeaker::getScheduleId)))
                        .isNull(ProjectSpeaker::getExpertId));
                if (unmatched != null && unmatched > 0) {
                    missing.add("专家匹配入库（" + unmatched + " 位库外专家）");
                }
            }
        }
        // 新流程项目无快照模板，仍执行阶段业务材料校验。
        if (snapshotOrNull(project) == null) {
            return missing;
        }
        // 类型专属绑定优先，无专属回退全局兜底（type_id=0）
        List<Map<String,Object>> rels = configuration.requirements(project.getId(),phaseId).stream()
                .filter(r -> ProjectConfigurationService.number(r.get("required")) == 1).toList();
        if (rels.isEmpty()) {
            return missing;
        }
        Long expertCnt = expertRelMapper.selectCount(new LambdaQueryWrapper<ProjectExpert>().eq(ProjectExpert::getProjectId, project.getId()));
        Long supplierCnt = supplierRelMapper.selectCount(new LambdaQueryWrapper<ProjectSupplier>().eq(ProjectSupplier::getProjectId, project.getId()));
        // 20261102 修复：FILE 与 RICH_TEXT 必填判定分流——富文本落盘路径以 _richtext/ 开头，
        // 否则上传过附件会误判自定义文本已交、文本必填不拦截推进
        Long docCnt = docMapper.selectCount(new LambdaQueryWrapper<ProjectDoc>()
                .eq(ProjectDoc::getProjectId, project.getId())
                .eq(ProjectDoc::getPhaseId, phaseId)
                .eq(ProjectDoc::getIsDeleted, 0)
                .notLike(ProjectDoc::getFilePath, "_richtext/%"));
        Long richDocCnt = docMapper.selectCount(new LambdaQueryWrapper<ProjectDoc>()
                .eq(ProjectDoc::getProjectId, project.getId())
                .eq(ProjectDoc::getPhaseId, phaseId)
                .eq(ProjectDoc::getIsDeleted, 0)
                .likeRight(ProjectDoc::getFilePath, "_richtext/"));
        boolean hasBudget = project.getBudgetAmount() != null;
        for (Map<String,Object> rel : rels) {
            ProjectTemplate t = new ProjectTemplate(); t.setName(rel.get("name").toString());
            String type = rel.get("templateType").toString();
            if ("FILE".equals(type) && (docCnt == null || docCnt == 0)) {
                missing.add("资料附件（" + t.getName() + "）");
            } else if ("EXPERT".equals(type) && (expertCnt == null || expertCnt == 0)) {
                missing.add("专家（" + t.getName() + "）");
            } else if ("SUPPLIER".equals(type) && (supplierCnt == null || supplierCnt == 0)) {
                missing.add("供应商（" + t.getName() + "）");
            } else if ("BUDGET".equals(type) && !hasBudget) {
                missing.add("预算（" + t.getName() + "）");
            } else if ("RICH_TEXT".equals(type) && (richDocCnt == null || richDocCnt == 0)) {
                missing.add("自定义文本（" + t.getName() + "）");
            }
        }
        return missing;
    }

    /** 各阶段及其模板绑定（详情页阶段页签用）。 */


    private Map<String, Object> toMap(Project project) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", project.getId());
        m.put("name", project.getName());
        m.put("category", project.getCategory());
        m.put("projectType", project.getProjectType());
        m.put("team", project.getTeam());
        m.put("cityId", project.getCityId());
        m.put("meetingStartDate", project.getMeetingStartDate() == null ? null : project.getMeetingStartDate().toString());
        m.put("meetingEndDate", project.getMeetingEndDate() == null ? null : project.getMeetingEndDate().toString());
        m.put("clientCompany", project.getClientCompany());
        m.put("clientName", project.getClientName());
        m.put("remark", project.getRemark());
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

    // ==================== 团队 / 会议城市 / 协助人（2026-11-09 基本信息改版） ====================

    private void validateTeam(String team) {
        if (!TEAM_LABELS.containsKey(team)) {
            throw new BusinessException("团队必须为项目一部(DEPT1)/项目二部(DEPT2)/项目三部(DEPT3)");
        }
    }

    private String teamLabelOf(String team) {
        return team == null ? "" : TEAM_LABELS.getOrDefault(team, team);
    }

    private void validateCity(Long cityId) {
        if (cityId != null && supplierCityMapper.selectById(cityId) == null) {
            throw new BusinessException("会议城市不存在（请从城市字典中选择）");
        }
    }

    private String cityNameOf(Long cityId) {
        if (cityId == null) {
            return "";
        }
        SupplierCity c = supplierCityMapper.selectById(cityId);
        if (c == null) {
            return "";
        }
        return "北京市天津市上海市重庆市".contains(c.getName()) ? c.getName() : c.getProvince() + c.getName();
    }

    private void validateMeetingRange(LocalDate meetingStart, LocalDate meetingEnd) {
        if (meetingStart != null && meetingEnd != null && meetingEnd.isBefore(meetingStart)) {
            throw new BusinessException("会议举办结束日期不能早于开始日期");
        }
    }

    /** 全量替换协助人（可传空=清空）。 */
    private void saveAssistants(Long projectId, List<Long> userIds) {
        assistantMapper.delete(new LambdaQueryWrapper<ProjectAssistant>().eq(ProjectAssistant::getProjectId, projectId));
        for (Long uid : userIds) {
            ProjectAssistant pa = new ProjectAssistant();
            pa.setProjectId(projectId);
            pa.setUserId(uid);
            assistantMapper.insert(pa);
        }
    }

    private List<String> assistantNamesOf(Long projectId) {
        return assistantMapper.selectList(new LambdaQueryWrapper<ProjectAssistant>()
                        .eq(ProjectAssistant::getProjectId, projectId).orderByAsc(ProjectAssistant::getId))
                .stream().map(ProjectAssistant::getUserId).map(this::usernameOf).collect(Collectors.toList());
    }

    private List<Long> assistantIdsOf(Long projectId) {
        return assistantMapper.selectList(new LambdaQueryWrapper<ProjectAssistant>()
                        .eq(ProjectAssistant::getProjectId, projectId).orderByAsc(ProjectAssistant::getId))
                .stream().map(ProjectAssistant::getUserId).collect(Collectors.toList());
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
        String phase = phaseNameOf(p);
        return StringUtils.hasText(phase) ? phase : p.getStatus();
    }



    /** 类型流程内第一个阶段；类型未配置流程返回 null。 */


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
