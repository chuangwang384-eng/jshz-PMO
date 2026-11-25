package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.ApplicationType;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ApprovalConfig;
import com.jshz.expert.entity.ApprovalConfigUser;
import com.jshz.expert.entity.ApprovalRecord;
import com.jshz.expert.entity.Application;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.ProjectContract;
import com.jshz.expert.entity.ProjectExpert;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.entity.UserRole;
import com.jshz.expert.mapper.ApprovalConfigMapper;
import com.jshz.expert.mapper.ApprovalConfigUserMapper;
import com.jshz.expert.mapper.ApprovalRecordMapper;
import com.jshz.expert.mapper.ApplicationMapper;
import com.jshz.expert.mapper.ProjectContractMapper;
import com.jshz.expert.mapper.ProjectExpertMapper;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.mapper.UserRoleMapper;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.common.ParamUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通用申请服务（「我的申请」改版，由 ApprovalService 演化）。
 * 通用申请主表 application + biz_data JSON 承载业务差异；一期类型 EXPERT_MODIFY / EXPERT_CREATE。
 * 员工提交 -> 审批人处理（approve/reject/转办/加签）；通过时把白名单字段写入 expert_info。
 * 接口路径沿用 /api/approval/*（permission 表路径规则不变），HTTP 层见 ApplicationController。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationService extends ServiceImpl<ApplicationMapper, Application> {

    /** 审批流流程键（专家信息修改/新增共用 EXPERT；合同审批 CONTRACT） */
    public static final String FLOW_KEY = "EXPERT";
    public static final String FLOW_KEY_CONTRACT = "CONTRACT";

    /** 审批处理权限码（转办/加签目标用户与当前审批人须持有） */
    public static final String PERM_APPROVAL_AUDIT = "approval:audit";

    /** 字段白名单（排除照片类、id、时间类、下载次数；前端新增/编辑表单已移除研究方向、擅长领域，后端白名单保留两字段以兼容历史数据，符合设计文档 3.3） */
    public static final List<String> WHITELIST_FIELDS = List.of(
            "姓名", "单位", "科室", "职称", "研究方向", "擅长领域",
            "联系方式", "简介", "身份证号", "银行卡号", "银行卡开户行", "身份证有效期");

    /** 照片字段白名单（照片变更随审批流一并生效） */
    public static final List<String> PHOTO_FIELDS = List.of("照片", "身份证正面", "身份证反面");

    /** 临时照片值前缀 */
    private static final String TMP_PREFIX = "TMP:";

    /** 临时照片值格式：TMP:文件名 */
    private static final Pattern TMP_PATTERN = Pattern.compile("^TMP:[A-Za-z0-9._-]+$");

    /**
     * 单字段文本上限（前后端同源：前端 utils/approvalFields.js MAX_TEXT_LENGTH）。
     * 提交链路此前不校验，导致超长值可通过提交但无法重提（重提侧限制 5000）。
     */
    public static final int MAX_TEXT_LENGTH = 5000;

    /**
     * 申请原因上限（前后端同源：前端 utils/approvalFields.js MAX_REASON_LENGTH）。
     * 提交链路此前不校验，历史超长原因会导致退回后无法重提。
     */
    public static final int MAX_REASON_LENGTH = 1000;

    /** 单页最大条数（与 MybatisPlusConfig.maxLimit、前端 page-sizes 上限一致）。 */
    public static final long MAX_PAGE_SIZE = 100L;

    /** 状态：0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤回 / 4 退回修改 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;
    private static final int STATUS_WITHDRAWN = 3;
    private static final int STATUS_RETURNED = 4;

    /** 新增专家必填文本字段（审批通过后写入 expert_info；模板管理接口对外暴露该清单） */
    // Owner 2026-11-07：新增专家必填仅 姓名+单位，其余可选（DB 除 name 外均可空）
    public static final List<String> CREATE_REQUIRED_FIELDS = List.of(
            "姓名", "单位");

    /** 姓名正则：2-20 个汉字，可含间隔号 */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,20}$");

    /** 身份证号正则：18 位，末位可为 X（与 ExpertService.validateRequired 对齐） */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");

    /** 银行卡号正则：13-19 位数字（与 ExpertService.validateRequired 对齐） */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{13,19}$");

    /** 联系方式正则：11 位手机号或邮箱（与 ExpertService.validateRequired 对齐） */
    private static final Pattern CONTACT_PATTERN = Pattern.compile("^1\\d{10}$|^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    /** 敏感格式字段（仅在本次变更涉及且值非空时校验） */
    private static final List<String> SENSITIVE_FIELDS = List.of("身份证号", "银行卡号", "联系方式");

    /** 审批管理页面路由（菜单已迁移至个人中心） */
    private static final String MANAGE_PATH = "/profile/approval/manage";

    /** 「我的申请」页面路由（审批结果/退回/转办通知跳转用；带 status 参数） */
    private static final String MY_APPLICATIONS_PATH = "/profile/my-approvals";

    /** 合同申请允许变更的字段（提交与重提同源；前端 utils/approvalFields.js CONTRACT_FIELDS 为展示层副本） */
    private static final List<String> CONTRACT_FIELDS = List.of("身份证号", "银行卡号");

    private final ApprovalConfigMapper approvalConfigMapper;
    private final ApprovalConfigUserMapper approvalConfigUserMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ExpertService expertService;
    private final PhotoStorageService photoStorageService;
    private final RoleMapper roleMapper;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final SysUserMapper sysUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionService permissionService;
    private final ProjectContractMapper projectContractMapper;
    private final ProjectExpertMapper projectExpertMapper;
    private final OperationLogService operationLogService;

    // ==================== 审批流配置 ====================

    /**
     * 读取指定流程的审批流配置；不存在则初始化默认配置（启用、审批人 APPROVER）。
     * 多流程：按 flowKey 区分（EXPERT / CONTRACT），存量 EXPERT_MODIFY 已迁移为 EXPERT。
     */
    public ApprovalConfig getConfig(String flowKey) {
        String key = StringUtils.hasText(flowKey) ? flowKey.trim() : FLOW_KEY;
        ApprovalConfig config = findConfigByFlowKey(key);
        if (config == null) {
            config = new ApprovalConfig();
            config.setFlowKey(key);
            config.setFlowName(defaultFlowName(key));
            config.setEnabled(1);
            config.setApproverRole("APPROVER");
            approvalConfigMapper.insert(config);
        }
        return config;
    }

    /**
     * 更新指定流程的审批流配置（按 flowKey 更新对应行；无行则先自建）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalConfig updateConfig(String flowKey, Integer enabled, String approverRole) {
        String key = StringUtils.hasText(flowKey) ? flowKey.trim() : FLOW_KEY;
        if (!StringUtils.hasText(approverRole)) {
            throw new BusinessException("审批人角色不能为空");
        }
        // RBAC 阶段1：approver_role 可配置为任意已存在的角色（如 APPROVER / ADMIN），
        // 不在代码中硬解；实际审批动作由 approval:audit 权限码在 permission 数据中控制。
        if (roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getCode, approverRole.trim())) == 0) {
            throw new BusinessException("审批人角色「" + approverRole.trim() + "」不存在，请在角色管理中先创建");
        }
        ApprovalConfig config = getConfig(key);
        if (enabled != null) {
            if (enabled != 0 && enabled != 1) {
                throw new BusinessException("启用开关取值不合法");
            }
            config.setEnabled(enabled);
        }
        config.setApproverRole(approverRole.trim());
        approvalConfigMapper.updateById(config);
        return getConfig(key);
    }

    /** 流程键 -> 默认流程名称（自建默认行时使用）。 */
    private String defaultFlowName(String flowKey) {
        if (FLOW_KEY_CONTRACT.equals(flowKey)) {
            return "项目合同审批";
        }
        return "专家信息修改审批";
    }

    // ==================== 临时照片上传（员工） ====================

    /**
     * 审批前临时照片上传：校验与正式照片一致，文件保存到临时目录，返回临时文件名。
     */
    public String uploadTempPhoto(MultipartFile file) {
        return photoStorageService.saveTempPhoto(file);
    }

    // ==================== 提交申请（员工） ====================

    /**
     * 提交专家信息修改申请（application_type=EXPERT_MODIFY）：
     * - 拥有 expert:edit 权限的用户不允许走审批流（提示直接编辑）；
     * - 审批流未启用时拒绝；
     * - changes 非空且字段必须在白名单；
     * - 生成申请单(status=0)并写一条 SUBMIT 记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public Application submitRequest(Long expertId, Map<String, Object> changes, String reason,
                                     AuthUser user) {
        checkSubmitEligible(user, "编辑", FLOW_KEY);
        if (expertId == null) {
            throw new BusinessException("专家ID不能为空");
        }
        Expert expert = expertService.getById(expertId);
        if (expert == null) {
            throw new BusinessException("专家不存在");
        }
        checkChanges(changes);
        // 2026-11-24（Q4）：修改申请此前只校验敏感字段格式，姓名/单位可被写成空值或非法值，
        // 与新增申请口径不对称；统一走 validateModifyChanges。
        validateModifyChanges(changes);
        validateReason(reason);
        Application app = new Application();
        app.setApplicantId(user.getUserId());
        app.setApplicant(user.getUsername());
        app.setApplicationType(ApplicationType.EXPERT_MODIFY.name());
        app.setFlowKey(FLOW_KEY);
        app.setStatus(STATUS_PENDING);
        // 提交时落专家全量旧值快照：审批通过更新 expert_info 后仍可还原「变更前」
        app.setBizData(writeBizData(expertId, expert.getName(), changes, reason,
                buildOldSnapshot(expert)));
        // 提交时按流程配置生成具体审批人快照（R2/R3 或签）
        app.setApproverSnapshot(buildApproverSnapshot(getConfig(FLOW_KEY)));
        app.setCreateTime(LocalDateTime.now());
        this.save(app);

        addRecord(app.getId(), "提交修改申请", "SUBMIT", user.getUsername(),
                StringUtils.hasText(reason) ? reason : "提交专家信息修改申请");
        // 与新增申请/重新提交一致：通知审批人新增待办（2026-11-24 补齐，此前修改申请提交后审批人无待办通知）
        notifyApprovalTodo(app);
        return app;
    }

    /**
     * 提交新增专家申请（application_type=EXPERT_CREATE）：
     * - 校验与 submitRequest 一致（管理员拒绝、审批流启用、changes 非空、字段白名单、照片 TMP: 与临时文件存在）；
     * - 额外校验新增必填项（姓名、单位等，姓名须满足 ^[一-龥·]{2,20}$）；
     * - expertId 为空，审批通过后创建专家并回填 biz_data.expertId。
     */
    @Transactional(rollbackFor = Exception.class)
    public Application submitCreateRequest(Map<String, Object> changes, String reason,
                                           AuthUser user) {
        checkSubmitEligible(user, "新增", FLOW_KEY);
        checkChanges(changes);
        validateCreateChanges(changes);
        validateReason(reason);

        Application app = new Application();
        app.setApplicantId(user.getUserId());
        app.setApplicant(user.getUsername());
        app.setApplicationType(ApplicationType.EXPERT_CREATE.name());
        app.setFlowKey(FLOW_KEY);
        app.setStatus(STATUS_PENDING);
        String expertName = String.valueOf(changes.get("姓名")).trim();
        // 新增申请无「变更前」，oldData 置空
        app.setBizData(writeBizData(null, expertName, changes, reason, null));
        app.setApproverSnapshot(buildApproverSnapshot(getConfig(FLOW_KEY)));
        app.setCreateTime(LocalDateTime.now());
        this.save(app);

        addRecord(app.getId(), "提交新增专家申请", "SUBMIT", user.getUsername(),
                StringUtils.hasText(reason) ? reason : "提交新增专家申请");
        notifyApprovalTodo(app);
        return app;
    }

    /**
     * 提交资格共用前导：未登录拒绝；拥有 expert:edit 的用户走直编通道拒绝；审批流未启用拒绝。
     * action 仅用于提示文案（"编辑"/"新增"）。
     */
    private void checkSubmitEligible(AuthUser user, String action, String flowKey) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.hasPermission("expert:edit")) {
            throw new BusinessException("您已拥有专家编辑权限，请直接在专家管理中" + action + "专家，无需提交审批");
        }
        ApprovalConfig config = getConfig(flowKey);
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("审批流未启用，暂不支持提交" + action + "申请");
        }
    }

    /**
     * 提交字段校验：changes 非空、白名单（文本+照片）、照片值须为 TMP: 前缀且临时文件存在。
     */
    private void checkChanges(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new BusinessException("变更内容不能为空");
        }
        for (String key : changes.keySet()) {
            boolean textField = WHITELIST_FIELDS.contains(key);
            boolean photoField = PHOTO_FIELDS.contains(key);
            if (!textField && !photoField) {
                throw new BusinessException("字段「" + key + "」不在可修改白名单内");
            }
            if (photoField) {
                String value = changes.get(key) == null ? null : String.valueOf(changes.get(key)).trim();
                if (!StringUtils.hasText(value) || !TMP_PATTERN.matcher(value).matches()) {
                    throw new BusinessException("照片字段「" + key + "」的值不合法，请重新上传照片");
                }
                if (!photoStorageService.existsTempPhoto(value.substring(TMP_PREFIX.length()))) {
                    throw new BusinessException("照片字段「" + key + "」对应的临时文件不存在，请重新上传");
                }
            } else {
                // 2026-11-24（Q10）：值必须是字符串，此前任意 JSON 类型会被 String.valueOf 写进专家字段
                Object raw = changes.get(key);
                if (raw != null && !(raw instanceof String)) {
                    throw new BusinessException("字段「" + key + "」的值必须为文本");
                }
                if (raw instanceof String text && text.length() > MAX_TEXT_LENGTH) {
                    throw new BusinessException("字段「" + key + "」内容过长（上限 " + MAX_TEXT_LENGTH + " 字）");
                }
            }
        }
    }

    /**
     * 修改申请字段校验（2026-11-24，Q4）：与新增申请口径对齐，避免审批通过后写坏专家档案。
     * - 姓名：非空且满足 2-20 个汉字（可含间隔号）；
     * - 单位：非空；
     * - 身份证号/银行卡号/联系方式：非空时校验格式（清空不在此拦截，由业务确认后另行放开）。
     */
    private void validateModifyChanges(Map<String, Object> changes) {
        validateModifyChanges(changes, changes == null ? Set.of() : changes.keySet());
    }

    /**
     * 修改申请字段校验（限定键）：只校验 {@code touched} 中的字段。
     * 重新提交时传本次 patch 的键集，使历史单中已存在但不合规的存量值不会永久阻塞重提。
     */
    private void validateModifyChanges(Map<String, Object> changes, Set<String> touched) {
        if (changes == null) {
            return;
        }
        if (touched.contains("姓名")) {
            Object raw = changes.get("姓名");
            String name = String.valueOf(raw == null ? "" : raw).trim();
            if (!StringUtils.hasText(name)) {
                throw new BusinessException("姓名不能为空");
            }
            if (!NAME_PATTERN.matcher(name).matches()) {
                throw new BusinessException("姓名格式不正确（2-20 个汉字，可含间隔号）");
            }
        }
        if (touched.contains("单位")) {
            Object unit = changes.get("单位");
            if (unit == null || !StringUtils.hasText(String.valueOf(unit).trim())) {
                throw new BusinessException("单位不能为空");
            }
        }
        validateSensitiveFields(changes, touched);
    }

    /**
     * 申请原因长度校验（2026-11-24，Q8）：提交与重提同源（{@link #MAX_REASON_LENGTH}），
     * 避免"提交不限长、重提限 1000"导致历史单退回后无法重提。
     */
    private void validateReason(String reason) {
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new BusinessException("申请原因过长（上限 " + MAX_REASON_LENGTH + " 字）");
        }
    }

    /**
     * 新增专家必填校验：姓名、单位、科室、职称、联系方式必填（2026-10-30 对齐卡片展示字段，移除研究方向、擅长领域），
     * 姓名须满足 2-20 个汉字（可含间隔号）。
     */
    private void validateCreateChanges(Map<String, Object> changes) {
        for (String key : CREATE_REQUIRED_FIELDS) {
            Object value = changes.get(key);
            if (value == null || !StringUtils.hasText(String.valueOf(value).trim())) {
                throw new BusinessException("新增专家必填字段「" + key + "」不能为空");
            }
        }
        String name = String.valueOf(changes.get("姓名")).trim();
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new BusinessException("姓名格式不正确（2-20 个汉字，可含间隔号）");
        }
        // 敏感字段格式兜底校验（身份证/银行卡/联系方式）
        validateSensitiveFields(changes);
    }

    // ==================== 我的申请（员工） ====================

    /**
     * 「我的申请」列表筛选条件（R1，2026-11-24）：状态 / 申请类型 / 关键词 / 提交日期区间。
     * 全部字段可空；日期为 yyyy-MM-dd（含当日）。
     */
    public record MyApplicationFilter(Integer status, String type, String keyword, String startDate, String endDate) {
    }

    /**
     * 我的申请分页列表（my-applications；旧 my-requests 兼容路径已于 2026-11-24 下线）：
     * 按 applicant_id / applicant 双条件过滤本人（兼容存量 applicant_id 为空的迁移行），
     * 可按状态/类型/关键词/提交日期区间过滤（全部为空查全部），附带 biz_data 解析展示字段与进度记录。
     *
     * <p>2026-11-24（Q2）：page/size 在服务层再钳制一次（1..100），防止绕过 Controller 直接调用时
     * 触发 MyBatis-Plus「size&lt;0 即不分页」的全表查询；进度记录改为按页内 id 集合一次 IN 查询回填，
     * 消除逐行 recordsOf 的 N+1（此前 size=100 单请求约 101 条 SQL）。</p>
     */
    public Page<Application> myApplications(long page, long size, MyApplicationFilter filter, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<Application>()
                .and(w -> w.eq(Application::getApplicantId, user.getUserId())
                        .or().eq(Application::getApplicant, user.getUsername()));
        MyApplicationFilter f = filter == null ? new MyApplicationFilter(null, null, null, null, null) : filter;
        if (f.status() != null) {
            wrapper.eq(Application::getStatus, f.status());
        }
        if (StringUtils.hasText(f.type())) {
            wrapper.eq(Application::getApplicationType, f.type().trim());
        }
        if (StringUtils.hasText(f.keyword())) {
            String kw = f.keyword().trim();
            // 关键词覆盖：专家姓名（biz_data.expertName）/ 申请原因（biz_data.reason）。
            // 不再匹配 applicant：本人列表里申请人恒为当前用户，该条件只会让命中自己用户名的关键词返回全量。
            // 通配符转义：关键词里的 % _ 按字面匹配（ESCAPE '/'）。
            String esc = escapeLike(kw);
            wrapper.and(w -> w.apply("JSON_UNQUOTE(JSON_EXTRACT(biz_data, '$.expertName')) LIKE CONCAT('%', {0}, '%') ESCAPE '/'", esc)
                    .or().apply("JSON_UNQUOTE(JSON_EXTRACT(biz_data, '$.reason')) LIKE CONCAT('%', {0}, '%') ESCAPE '/'", esc));
        }
        applyDateRange(wrapper, f.startDate(), f.endDate());
        // 同秒提交的单据按 id 兜底，避免翻页重复/遗漏
        wrapper.orderByDesc(Application::getCreateTime).orderByDesc(Application::getId);
        Page<Application> result = this.page(new Page<>(ParamUtils.clamp(page, 1, Long.MAX_VALUE),
                ParamUtils.clamp(size, 1, MAX_PAGE_SIZE)), wrapper);
        fillViews(result.getRecords());
        return result;
    }

    /** LIKE 通配符转义（配合 SQL 中的 ESCAPE '/'）：先转义转义符本身，再转义 % 与 _。 */
    private static String escapeLike(String value) {
        return value.replace("/", "//").replace("%", "/%").replace("_", "/_");
    }

    /** Authorized single-record lookup for dashboard deep links; no action permissions changed. */
    public Application dashboardFocus(Long id, boolean audit, AuthUser user) {
        if (user == null) throw new BusinessException(401, "未登录");
        Application app = getById(id);
        if (app == null) throw new BusinessException(404, "记录不可用");
        if (!audit) {
            if (!java.util.Objects.equals(app.getApplicantId(), user.getUserId()) && !java.util.Objects.equals(app.getApplicant(), user.getUsername()))
                throw new BusinessException(403, "记录不可用");
        } else {
            if (!user.hasPermission(PERM_APPROVAL_AUDIT) || !java.util.Objects.equals(app.getStatus(), STATUS_PENDING))
                throw new BusinessException(403, "记录不可处理，请刷新待办");
            checkAssignee(app,user);
            String snapshot=app.getApproverSnapshot();
            if (org.springframework.util.StringUtils.hasText(snapshot)
                    && !java.util.Objects.equals(app.getCurrentAssignee(),user.getUsername())
                    && !java.util.Objects.equals(app.getExtraApprover(),user.getUsername())) {
                try {
                    java.util.List<java.util.Map<String,Object>> members=objectMapper.readValue(snapshot,new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String,Object>>>(){});
                    if(members.stream().noneMatch(m->user.getUsername().equals(m.get("username")))) throw new BusinessException(403,"记录不可用");
                } catch (BusinessException ex) { throw ex; }
                catch (Exception ex) { throw new BusinessException(403,"记录不可用"); }
            }
        }
        return fillView(app);
    }

    // ==================== 全部申请（审批管理） ====================

    /**
     * 全部申请单分页（多状态）：statuses 为空查全部；keyword 模糊匹配申请人 / 专家姓名。
     * 已处理列表（status=1,2,3）统一在此分页，避免前端分页语义不稳定。
     * page/size 由服务层钳制（1..{@link #MAX_PAGE_SIZE}），进度记录按页批量回填（Q2）。
     */
    public Page<Application> listRequests(List<Integer> statuses, String keyword, long page, long size, AuthUser viewer) {
        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(Application::getStatus, statuses);
            // 待办列表（含 status=0）按审批人快照过滤：快照为空（存量/任意审批人）放行；
            // 快照非空时仅快照内审批人、被转办人、被加签人可见（R2/R3 或签）。
            if (statuses.contains(STATUS_PENDING) && viewer != null) {
                wrapper.and(w -> w.isNull(Application::getApproverSnapshot)
                        .or().eq(Application::getApproverSnapshot, "")
                        .or().apply("JSON_CONTAINS(approver_snapshot, JSON_OBJECT('username', {0}))", viewer.getUsername())
                        .or().eq(Application::getCurrentAssignee, viewer.getUsername())
                        .or().eq(Application::getExtraApprover, viewer.getUsername()));
            }
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Application::getApplicant, kw)
                    .or().apply("JSON_UNQUOTE(JSON_EXTRACT(biz_data, '$.expertName')) LIKE CONCAT('%', {0}, '%')", kw));
        }
        wrapper.orderByDesc(Application::getCreateTime);
        Page<Application> result = this.page(new Page<>(ParamUtils.clamp(page, 1, Long.MAX_VALUE),
                ParamUtils.clamp(size, 1, MAX_PAGE_SIZE)), wrapper);
        fillViews(result.getRecords());
        return result;
    }

    // ==================== 审批（审批管理） ====================

    /**
     * 提交专家合同审批（application_type=CONTRACT）：
     * - 审批流未启用时拒绝；
     * - 合同必须存在且当前为 DRAFT 或 REJECTED（可重新发起），PENDING 不可重复发起；
     * - changes 仅含敏感字段（身份证号/银行卡号），发起时校验格式；
     * - 生成申请单(status=0)并置合同 status=PENDING、approval_ref=申请单id，写 operation_log 留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public Application submitContractApproval(Long contractId, Long projectId, Map<String, Object> changes,
                                              String reason, AuthUser user) {
        ApprovalConfig config = getConfig(FLOW_KEY_CONTRACT);
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("审批流未启用，无法发起合同审批");
        }
        if (contractId == null) {
            throw new BusinessException("合同ID不能为空");
        }
        ProjectContract contract = projectContractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        if (!"DRAFT".equals(contract.getStatus()) && !"REJECTED".equals(contract.getStatus())) {
            throw new BusinessException("仅草稿或被驳回的合同可发起审批");
        }
        // 2026-11-24（Q3）：同一合同只允许一张在审单（status 0 待审批 / 4 退回修改），
        // 否则重提旧单与再次发起新单会并存，导致合同被连续置为 APPROVED、审批流水失真。
        ensureNoOtherOpenContractApplication(contractId, null);
        if (changes == null || changes.isEmpty()) {
            throw new BusinessException("请先填写身份证号/银行卡号再发起审批");
        }
        validateSensitiveFields(changes);

        Application app = new Application();
        app.setApplicantId(user.getUserId());
        app.setApplicant(user.getUsername());
        app.setApplicationType(ApplicationType.CONTRACT.name());
        app.setFlowKey(FLOW_KEY_CONTRACT);
        app.setStatus(STATUS_PENDING);
        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("contractId", contractId);
        biz.put("projectId", projectId);
        biz.put("expertId", contract.getExpertId());
        biz.put("expertName", contract.getExpertName());
        biz.put("changes", changes);
        biz.put("reason", reason);
        app.setBizData(writeBiz(biz));
        app.setApproverSnapshot(buildApproverSnapshot(config));
        app.setCreateTime(LocalDateTime.now());
        this.save(app);

        contract.setStatus("PENDING");
        contract.setApprovalRef(app.getId());
        projectContractMapper.updateById(contract);
        syncProjectExpertSnapshot(projectId, contract.getExpertId(), "PENDING");
        addRecord(app.getId(), "提交合同审批", "SUBMIT", user.getUsername(),
                StringUtils.hasText(reason) ? reason : "发起专家合同审批");
        operationLogService.record(user.getUserId(), user.getUsername(),
                OperationLogService.ACTION_SUBMIT_CONTRACT_APPROVAL,
                "发起合同审批：专家「" + contract.getExpertName() + "」合同（金额 " + contract.getFee() + "）",
                "PROJECT", projectId, null);
        return app;
    }

    /**
     * 审批通过：按申请类型分支处理。
     * - EXPERT_CREATE 新增专家申请：先创建 Expert 再归档照片回填，最后回填 biz_data.expertId；
     * - CONTRACT 专家合同审批：合同生效（status=APPROVED）；
     * - EXPERT_MODIFY 修改申请：走原修改逻辑。
     */
    @Transactional(rollbackFor = Exception.class)
    public Application approve(Long id, String comment, AuthUser user) {
        Application app = getPending(id);
        checkAssignee(app, user);
        // H2 并发抢占：以 status=0 为条件原子更新，影响行数为 0 说明已被其他审批人处理。
        // 必须在建专家 / 合同生效 / 写审批记录等任何副作用之前执行，保证同一申请只被通过一次。
        claimPending(app, STATUS_APPROVED);
        Application result;
        if (isCreateRequest(app)) {
            result = approveCreate(app, comment, user);
        } else if (ApplicationType.parse(app.getApplicationType()) == ApplicationType.CONTRACT) {
            result = approveContract(app, comment, user);
        } else {
            result = approveModify(app, comment, user);
        }
        notifyApprovalResult(result, user);
        return result;
    }

    /**
     * 审批通过（专家合同审批）：同步合同生效（status=APPROVED）。
     * 2026-11-24（Q1）：biz_data 里取不到 contractId 说明申请单内容已损坏，
     * 此时必须阻断，否则会出现「审批通过但合同状态没变」的静默失败。
     */
    private Application approveContract(Application app, String comment, AuthUser user) {
        if (ParamUtils.parseLong(readBiz(app).get("contractId"), null) == null) {
            throw new BusinessException("申请内容缺失或已损坏（缺少合同标识），无法审批，请联系管理员核查申请单 #" + app.getId());
        }
        syncContractByApproval(app, "APPROVED", user);
        app.setStatus(STATUS_APPROVED);
        app.setApproveBy(user == null ? null : user.getUsername());
        app.setApproveComment(comment);
        app.setApproveTime(LocalDateTime.now());
        this.updateById(app);

        addRecord(app.getId(), "管理员审批通过", "APPROVE", user == null ? null : user.getUsername(),
                StringUtils.hasText(comment) ? comment : "同意合同审批");
        return fillView(app);
    }

    private boolean isCreateRequest(Application app) {
        return ApplicationType.parse(app.getApplicationType()) == ApplicationType.EXPERT_CREATE;
    }

    /**
     * 审批通过（新增专家申请）：
     * 先从 changes 提取文本字段创建 Expert（update_by=审批人），
     * 再归档照片回填 photo/idCardFront/idCardBack，最后回填 biz_data 的 expertId / expertName。
     */
    private Application approveCreate(Application app, String comment, AuthUser user) {
        Map<String, Object> changes = readChanges(app);
        // 防御性校验：提交后数据不应变化，审批时兜底校验一次
        validateCreateChanges(changes);

        // 1. 创建专家（仅文本字段，update_by=审批人）
        Expert expert = new Expert();
        applyChanges(expert, changes);
        expertService.create(expert, user);

        // 2. 计算照片归档文件名并回填（只做命名与临时文件校验，物理拷贝延后到事务提交后）
        Map<String, String> photoApplied = new LinkedHashMap<>();
        Map<String, String> pendingArchives = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            if (!PHOTO_FIELDS.contains(key)) {
                continue;
            }
            String value = entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim();
            if (!StringUtils.hasText(value) || !value.startsWith(TMP_PREFIX)) {
                continue;
            }
            String tempName = value.substring(TMP_PREFIX.length());
            String newName = photoStorageService.buildArchivedName(tempName, expert.getId(), photoTypeOf(key));
            pendingArchives.put(tempName, newName);
            photoApplied.put(key, newName);
            switch (key) {
                case "照片" -> expert.setPhoto(newName);
                case "身份证正面" -> expert.setIdCardFront(newName);
                default -> expert.setIdCardBack(newName);
            }
        }
        // 方案A：审批通过时 update_by 记申请人（发起新增的人），而非审批人
        expert.setUpdateBy(app.getApplicant());
        expertService.update(expert.getId(), expert, null);

        // 3. 回填 biz_data 的 expertId / expertName，并把照片 TMP: 值替换为归档文件名（生效后可预览）
        Map<String, Object> biz = readBiz(app);
        biz.put("expertId", expert.getId());
        biz.put("expertName", expert.getName());
        applyPhotoArchivedNames(biz, photoApplied);
        app.setBizData(writeBiz(biz));

        app.setStatus(STATUS_APPROVED);
        app.setApproveBy(user == null ? null : user.getUsername());
        app.setApproveComment(comment);
        app.setApproveTime(LocalDateTime.now());
        this.updateById(app);

        addRecord(app.getId(), "管理员审批通过", "APPROVE", user == null ? null : user.getUsername(),
                StringUtils.hasText(comment) ? comment : "同意新增专家");

        // 4. 事务提交后再做物理归档：提交失败则不会产生孤儿文件，提交成功才落盘（失败仅记日志，不影响已通过的审批结果）
        if (!pendingArchives.isEmpty()) {
            afterCommit("新增专家照片归档", () -> {
                archiveTempPhotos(pendingArchives, expert.getId());
                // 反面归档后自动提取有效期、正面归档后自动提取身份证号（2026-11-23，提交后独立更新）
                expertService.autoFillIdCardExpiry(expert.getId(), photoApplied.get("身份证反面"));
                expertService.autoFillIdCardNumber(expert.getId(), photoApplied.get("身份证正面"));
            });
        }
        return fillView(app);
    }

    /**
     * 审批通过（修改申请）：校验待审批状态，将白名单字段写入 expert_info（update_by=申请人），
     * 状态置为已通过，写 APPROVE 记录。
     *
     * <p>2026-11-24（Q1）：读取 changes 后若为空（biz_data 缺失/损坏/被写坏），必须终止审批。
     * 此前会以空变更「空转」置为已通过并留痕「同意修改」，导致审批通过但专家字段一个字都没更新，
     * 申请人、审批人、日志三方都无法发现。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Application approveModify(Application app, String comment, AuthUser user) {
        Map<String, Object> changes = readChanges(app);
        if (changes.isEmpty()) {
            throw new BusinessException("申请内容缺失或已损坏（biz_data 无法解析），无法审批，请联系管理员核查申请单 #" + app.getId());
        }
        // 敏感字段格式兜底校验（身份证/银行卡/联系方式）
        validateSensitiveFields(changes);

        Expert existing = expertService.getById(app.getExpertId());
        Expert patch = new Expert();
        Map<String, String> oldPhotos = new HashMap<>();
        Map<String, String> photoApplied = new LinkedHashMap<>();
        Map<String, String> pendingArchives = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            if (!PHOTO_FIELDS.contains(key)) {
                continue;
            }
            String value = entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim();
            if (!StringUtils.hasText(value) || !value.startsWith(TMP_PREFIX)) {
                continue;
            }
            String tempName = value.substring(TMP_PREFIX.length());
            String newName = photoStorageService.buildArchivedName(tempName, app.getExpertId(), photoTypeOf(key));
            pendingArchives.put(tempName, newName);
            photoApplied.put(key, newName);
            switch (key) {
                case "照片" -> {
                    if (existing != null) {
                        oldPhotos.put(key, existing.getPhoto());
                    }
                    patch.setPhoto(newName);
                }
                case "身份证正面" -> {
                    if (existing != null) {
                        oldPhotos.put(key, existing.getIdCardFront());
                    }
                    patch.setIdCardFront(newName);
                }
                default -> {
                    if (existing != null) {
                        oldPhotos.put(key, existing.getIdCardBack());
                    }
                    patch.setIdCardBack(newName);
                }
            }
        }
        applyChanges(patch, changes);
        // 方案A：审批通过时 update_by 记申请人（发起修改的人），而非审批人
        patch.setUpdateBy(app.getApplicant());
        expertService.update(app.getExpertId(), patch, null);

        // 旧照片处理：oldData 快照存在时保留旧文件（对比弹窗随时可回看「变更前」）；
        // 无快照的历史单维持原有物理删除行为（仅照片键有变更时删）。
        // 物理删除、临时文件清理与照片归档统一延后到事务提交后执行，避免文件 IO 占用数据库事务。
        boolean deleteOldPhotos = readOldData(app).isEmpty();

        // 照片生效后把 biz_data.changes 的 TMP: 值替换为归档文件名（生效后可预览），随状态更新一并落库
        if (!photoApplied.isEmpty()) {
            Map<String, Object> biz = readBiz(app);
            applyPhotoArchivedNames(biz, photoApplied);
            app.setBizData(writeBiz(biz));
        }

        app.setStatus(STATUS_APPROVED);
        app.setApproveBy(user == null ? null : user.getUsername());
        app.setApproveComment(comment);
        app.setApproveTime(LocalDateTime.now());
        this.updateById(app);

        addRecord(app.getId(), "管理员审批通过", "APPROVE", user == null ? null : user.getUsername(),
                StringUtils.hasText(comment) ? comment : "同意修改");

        // 事务提交后执行文件操作：先归档新照片，再按需删除旧照片，最后清理残留临时文件
        afterCommit("修改申请照片归档", () -> {
            archiveTempPhotos(pendingArchives, app.getExpertId());
            // 反面归档后自动提取有效期、正面归档后自动提取身份证号（2026-11-23，提交后独立更新）
            expertService.autoFillIdCardExpiry(app.getExpertId(), photoApplied.get("身份证反面"));
            expertService.autoFillIdCardNumber(app.getExpertId(), photoApplied.get("身份证正面"));
            if (deleteOldPhotos) {
                for (String oldName : oldPhotos.values()) {
                    if (StringUtils.hasText(oldName)) {
                        photoStorageService.deletePhoto(oldName);
                    }
                }
            }
            cleanTempPhotos(app);
        });
        return fillView(app);
    }

    /**
     * 审批驳回：校验待审批状态与意见必填，状态置为已驳回，写 REJECT 记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public Application reject(Long id, String comment, AuthUser user) {
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException("驳回意见不能为空");
        }
        Application app = getPending(id);
        checkAssignee(app, user);
        // H2 并发抢占：驳回同样以 status=0 为条件，避免与并发通过/驳回重复处理同一申请。
        claimPending(app, STATUS_REJECTED);
        // 驳回：提交后清理该申请关联的所有临时照片（与撤回共用 cleanTempPhotos）；事务失败时保留临时文件
        app.setStatus(STATUS_REJECTED);
        app.setApproveBy(user == null ? null : user.getUsername());
        app.setApproveComment(comment);
        app.setApproveTime(LocalDateTime.now());
        this.updateById(app);

        addRecord(app.getId(), "管理员驳回", "REJECT", user == null ? null : user.getUsername(), comment.trim());
        // CONTRACT 类型：同步合同驳回
        syncContractByApproval(app, "REJECTED", user);
        notifyApprovalResult(app, user);
        // 临时照片清理（文件 IO）延后到事务提交后
        afterCommit("驳回清理临时照片", () -> cleanTempPhotos(app));
        return fillView(app);
    }

    // ==================== 退回修改 / 重新提交（R4） ====================

    /**
     * 退回修改：审批人将待审批单退回申请人修改。
     * - status 0 → 4（退回修改），写 RETURN 记录，不清临时照片（区别于 reject）；
     * - 记录 return_comment 与 approve_comment；
     * - CONTRACT 类型同步合同恢复 DRAFT；
     * - 通知申请人。
     */
    @Transactional(rollbackFor = Exception.class)
    public Application returnRequest(Long id, String comment, AuthUser user) {
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException("退回意见不能为空");
        }
        Application app = getPending(id);
        checkAssignee(app, user);
        // H2 并发抢占：退回与通过/驳回互斥，抢占失败即说明该单已被处理。
        claimPending(app, STATUS_RETURNED);
        // 退回修改：保留临时照片（区别于 reject 的 cleanTempPhotos），供申请人修改后重新提交
        app.setStatus(STATUS_RETURNED);
        app.setApproveBy(user == null ? null : user.getUsername());
        app.setApproveComment(comment.trim());
        app.setApproveTime(LocalDateTime.now());
        app.setReturnComment(comment.trim());
        this.updateById(app);

        addRecord(app.getId(), "审批退回修改", "RETURN", user == null ? null : user.getUsername(), comment.trim());
        // CONTRACT 类型：同步合同恢复草稿（可修改后重新发起）
        syncContractByApproval(app, "DRAFT", user);
        notifyReturned(app, user);
        return fillView(app);
    }

    /**
     * 重新提交：仅被退回修改（status=4）的申请可重提，仅申请人本人可操作。
     * - status 4 → 0，写 RESUBMIT 记录；
     * - 重置审批人字段（approveBy/approveComment/approveTime/returnComment/currentAssignee/extraApprover）；
     * - 按当前流程配置刷新审批人快照（配置可能已变更）；
     * - patch 可含照片字段（值须为 TMP: 临时文件，2026-11-24 放开：退回修改后 status=4 不可撤回，
     *   此前申请人没有更换照片的途径）；被替换的旧临时照片在事务提交后清理；合同申请仍仅限身份证号/银行卡号；
     * - 通知快照内审批人新增待办。
     */
    @Transactional(rollbackFor = Exception.class)
    public Application resubmit(Long id, Map<String,Object> patch, String reason, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (id == null) {
            throw new BusinessException("申请单ID不能为空");
        }
        Application app = getById(id);
        if (app == null) {
            throw new BusinessException("申请单不存在");
        }
        if (app.getStatus() == null || app.getStatus() != STATUS_RETURNED) {
            throw new BusinessException("仅被退回修改的申请可重新提交");
        }
        if (!isApplicant(app, user)) {
            throw new BusinessException("仅申请人本人可重新提交");
        }
        fillBiz(app);
        ApprovalConfig config = getConfig(flowKeyOf(app));
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("审批流未启用，暂不支持重新提交");
        }
        List<String> replacedTempPhotos = new ArrayList<>();
        Map<String,Object> biz = readBiz(app);
        Map<String,Object> changes = new LinkedHashMap<>(asStringKeyMap(biz.get("changes")));
        boolean contract = ApplicationType.parse(app.getApplicationType()) == ApplicationType.CONTRACT;
        boolean bizChanged = false;
        if (patch != null && !patch.isEmpty()) {
            for (Map.Entry<String,Object> entry : patch.entrySet()) {
                String field = entry.getKey(); Object value = entry.getValue();
                boolean photoField = PHOTO_FIELDS.contains(field);
                if (contract && !CONTRACT_FIELDS.contains(field)) throw new BusinessException("合同申请仅支持修改身份与银行卡字段");
                if (!contract && !WHITELIST_FIELDS.contains(field) && !photoField) throw new BusinessException("字段不在可修改范围内");
                if (value != null && (!(value instanceof String) || value.toString().length() > MAX_TEXT_LENGTH)) throw new BusinessException("字段内容格式不正确或过长");
                // 清空文本字段 = 撤回该字段的变更（2026-11-25）：修改申请带旧值快照时，空白值不再写入 changes，
                // 否则审批通过会把专家档案对应字段覆盖成空串。新增申请/合同申请无旧值快照，维持原语义；
                // 必填字段（姓名/单位）不适用，清空仍按「不能为空」拒绝。
                boolean hasOldSnapshot = biz.get("oldData") instanceof Map<?, ?> old && old.containsKey(field);
                if (!photoField && !CREATE_REQUIRED_FIELDS.contains(field) && hasOldSnapshot
                        && value != null && value.toString().isBlank()) {
                    changes.remove(field);
                    continue;
                }
                // 照片被替换：记下旧临时文件名，事务提交后清理（格式与临时文件存在性由 checkChanges 校验）
                if (photoField && value != null && changes.get(field) instanceof String prev
                        && prev.startsWith(TMP_PREFIX) && !prev.equals(value)) {
                    replacedTempPhotos.add(prev.substring(TMP_PREFIX.length()));
                }
                changes.put(field,value);
            }
            bizChanged = true;
        }
        // 2026-11-24（Q9）：未被本次重传、且临时文件已丢失的照片键自动降级剔除——
        // 此前会以「临时文件不存在」硬拒，status=4 不可撤回，单据永久滞留退回修改。
        List<String> droppedPhotoFields = new ArrayList<>();
        for (String key : PHOTO_FIELDS) {
            if (!(changes.get(key) instanceof String raw) || !raw.startsWith(TMP_PREFIX)) {
                continue;
            }
            String tempName = raw.substring(TMP_PREFIX.length());
            if (photoStorageService.existsTempPhoto(tempName)) {
                continue;
            }
            changes.remove(key);
            droppedPhotoFields.add(key);
            bizChanged = true;
            log.warn("[审批流] 重新提交：照片临时文件已失效，已从变更中剔除（需重新上传）：申请单={}，字段={}，文件={}",
                    id, key, tempName);
        }
        checkChanges(changes);
        // 校验口径（Q4）：姓名/单位等文本字段只校验本次实际改动的键，避免历史单因存量值不合规被永久卡住
        Set<String> touched = patch == null ? Set.of() : patch.keySet();
        if (isCreateRequest(app)) validateCreateChanges(changes); else validateModifyChanges(changes, touched);
        biz.put("changes", changes);
        if (changes.get("姓名") != null) biz.put("expertName", changes.get("姓名"));
        if (reason != null) {
            validateReason(reason);
            biz.put("reason", reason);
            bizChanged = true;
        }
        if (bizChanged) {
            app.setBizData(writeBiz(biz));
            fillBiz(app);
        }
        // CONTRACT 类型：重提必须把合同重新置回在审（Q3），否则同一合同可再发起第二张在审单
        Long contractId = contract ? ParamUtils.parseLong(biz.get("contractId"), null) : null;
        if (contract && contractId == null) {
            throw new BusinessException("申请内容缺失合同标识，无法重新提交，请联系管理员");
        }
        if (contract) {
            ensureNoOtherOpenContractApplication(contractId, app.getId());
        }
        app.setStatus(STATUS_PENDING);
        app.setApproveBy(null);
        app.setApproveComment(null);
        app.setApproveTime(null);
        app.setReturnComment(null);
        // 转办 / 加签重置：回到原审批人待办
        app.setCurrentAssignee(null);
        app.setExtraApprover(null);
        // 按当前流程配置刷新审批人快照
        app.setApproverSnapshot(buildApproverSnapshot(config));
        boolean changed = this.lambdaUpdate().eq(Application::getId,id).eq(Application::getStatus,STATUS_RETURNED)
                .set(Application::getStatus,STATUS_PENDING).set(Application::getBizData,app.getBizData())
                .set(Application::getApproveBy,null).set(Application::getApproveComment,null).set(Application::getApproveTime,null)
                .set(Application::getReturnComment,null).set(Application::getCurrentAssignee,null).set(Application::getExtraApprover,null)
                .set(Application::getApproverSnapshot,app.getApproverSnapshot()).update();
        if(!changed) throw new BusinessException(409,"申请状态已变化，请刷新后重试");
        if (contract) {
            // 合同重新进入审批中：status=PENDING 且 approval_ref 指回本单（Q3/Q17）
            syncContractByApproval(app, "PENDING", user);
        }

        addRecord(app.getId(), "申请人重新提交", "RESUBMIT", user.getUsername(),
                StringUtils.hasText(app.getReason()) ? app.getReason() : "重新提交申请");
        notifyApprovalTodo(app);
        if (!replacedTempPhotos.isEmpty()) {
            afterCommit("重新提交清理被替换的临时照片", () -> {
                for (String tempName : replacedTempPhotos) {
                    try {
                        photoStorageService.deleteTempPhoto(tempName);
                    } catch (Exception e) {
                        log.warn("[审批流] 被替换的临时照片清理失败：{}", tempName, e);
                    }
                }
            });
        }
        Application result = fillView(app);
        // 被剔除的照片键回传前端提示：静默剔除会让申请人误以为照片仍会随审批生效
        result.setDroppedPhotoFields(droppedPhotoFields);
        return result;
    }

    // ==================== 审批人管理（R2 指定具体审批人） ====================

    /**
     * 查询指定流程已配置的具体审批人（[{id, username}]，按配置顺序）。
     */
    public List<Map<String, Object>> listConfigUsers(String flowKey) {
        ApprovalConfig config = getConfig(flowKey);
        List<ApprovalConfigUser> users = approvalConfigUserMapper.selectList(
                new LambdaQueryWrapper<ApprovalConfigUser>()
                        .eq(ApprovalConfigUser::getConfigId, config.getId())
                        .orderByAsc(ApprovalConfigUser::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ApprovalConfigUser u : users) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", u.getUserId());
            item.put("username", u.getUsername());
            result.add(item);
        }
        return result;
    }

    /**
     * 保存指定流程的具体审批人（全量覆盖式更新，事务内先删后插）。
     * 校验：用户名存在、未停用、拥有 approval:audit。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveConfigUsers(String flowKey, List<String> usernames) {
        ApprovalConfig config = getConfig(flowKey);
        if (usernames == null) {
            usernames = new ArrayList<>();
        }
        List<String> uniq = usernames.stream().filter(StringUtils::hasText)
                .map(String::trim).distinct().toList();
        List<SysUser> targets = new ArrayList<>();
        for (String name : uniq) {
            targets.add(checkTargetApprover(name));
        }
        approvalConfigUserMapper.delete(new LambdaQueryWrapper<ApprovalConfigUser>()
                .eq(ApprovalConfigUser::getConfigId, config.getId()));
        for (SysUser target : targets) {
            ApprovalConfigUser row = new ApprovalConfigUser();
            row.setConfigId(config.getId());
            row.setUserId(target.getId());
            row.setUsername(target.getUsername());
            row.setCreateTime(LocalDateTime.now());
            approvalConfigUserMapper.insert(row);
        }
    }

    /**
     * 审批流配置列表（多流程）：返回各流程的 flowKey/flowName/enabled/approverRole/具体审批人。
     */
    public List<Map<String, Object>> listConfigs() {
        List<ApprovalConfig> configs = approvalConfigMapper.selectList(
                new LambdaQueryWrapper<ApprovalConfig>().orderByAsc(ApprovalConfig::getFlowKey));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ApprovalConfig c : configs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("flowKey", c.getFlowKey());
            item.put("flowName", c.getFlowName());
            item.put("enabled", c.getEnabled());
            item.put("approverRole", c.getApproverRole());
            item.put("users", listConfigUsers(c.getFlowKey()));
            result.add(item);
        }
        return result;
    }

    // ==================== 撤回申请（申请人） ====================

    /**
     * 撤回申请：仅申请人本人可撤，status=0 才可撤。
     * 撤回后状态置为已撤回(3)，清理该单全部临时照片，写 WITHDRAW 记录，通知审批人角色下用户。
     */
    @Transactional(rollbackFor = Exception.class)
    public Application withdraw(Long id, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (id == null) {
            throw new BusinessException("申请单ID不能为空");
        }
        Application app = getById(id);
        if (app == null) {
            throw new BusinessException("申请单不存在");
        }
        if (app.getStatus() == null || app.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该申请单已处理，不能撤回");
        }
        if (!isApplicant(app, user)) {
            throw new BusinessException("仅申请人本人可撤回申请");
        }
        fillBiz(app);
        // H2 并发抢占：撤回与并发审批互斥——抢占失败说明审批人已先一步处理，不能再撤回。
        claimPending(app, STATUS_WITHDRAWN);
        // 临时照片清理延后到事务提交后（与合同状态回退、状态更新同事务，撤销失败不删文件）
        // CONTRACT 类型：同步合同恢复草稿
        syncContractByApproval(app, "DRAFT", user);
        app.setStatus(STATUS_WITHDRAWN);
        this.updateById(app);

        addRecord(app.getId(), "申请人撤回", "WITHDRAW", user.getUsername(), "撤回本人提交的申请");
        notifyApprovalWithdrawn(app);
        afterCommit("撤回清理临时照片", () -> cleanTempPhotos(app));
        return fillView(app);
    }

    // ==================== 删除申请（申请人，2026-11-24 新增） ====================

    /**
     * 删除申请单：申请人清理本人「已驳回 / 已撤回」的历史单据。
     *
     * <p>业务边界（不扩大影响面）：</p>
     * <ul>
     *     <li>仅申请人本人（applicant_id / applicant 双条件，与列表口径一致）；</li>
     *     <li>仅终态且无后续处理可能的 status∈{2 已驳回, 3 已撤回}；待审批(0)须先撤回、退回修改(4)须先重提或撤回，</li>
     *     <li>已通过(1)不可删除（专家档案已按该单变更生效，删除会丢失变更留痕）；</li>
     *     <li>同事务删除该单的审批进度记录，并以条件更新（status 仍为原终态）防止与并发操作竞争；</li>
     *     <li>删除前写 operation_log（DELETE_APPLICATION）保留审计痕迹，事务提交后清理临时照片文件。</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteApplication(Long id, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (id == null) {
            throw new BusinessException("申请单ID不能为空");
        }
        Application app = getById(id);
        if (app == null) {
            throw new BusinessException("申请单不存在或已删除");
        }
        if (!isApplicant(app, user)) {
            throw new BusinessException("仅申请人本人可删除申请单");
        }
        Integer status = app.getStatus();
        if (status == null || (status != STATUS_REJECTED && status != STATUS_WITHDRAWN)) {
            throw new BusinessException("仅「已驳回」或「已撤回」的申请可删除；待审批请先撤回，退回修改的申请先重新提交");
        }
        deleteApplication(app, user);
    }

    /** 删除单据本体（调用方已完成本人与状态校验）。 */
    private void deleteApplication(Application app, AuthUser user) {
        Long id = app.getId();
        Integer status = app.getStatus();
        fillBiz(app);
        String statusText = statusText(status);
        // 条件删除：以原状态为条件，避免与并发重提/审批交错（并发下影响行数为 0 即拒绝）
        boolean removed = this.lambdaUpdate()
                .eq(Application::getId, id)
                .eq(Application::getStatus, status)
                .remove();
        if (!removed) {
            throw new BusinessException(409, "申请状态已变化，请刷新后重试");
        }
        approvalRecordMapper.delete(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getApplicationId, id));
        operationLogService.record(user.getUserId(), user.getUsername(),
                OperationLogService.ACTION_DELETE_APPLICATION,
                "删除本人申请单：专家「" + (app.getExpertName() == null ? "-" : app.getExpertName())
                        + "」" + typeTextOf(app) + "（状态 " + statusText + "）",
                "APPLICATION", id, null);
        afterCommit("删除申请清理临时照片", () -> cleanTempPhotos(app));
    }

    /**
     * 批量删除申请单（2026-11-25，参照消息中心批量删除）：申请人一次清理多条本人「已驳回 / 已撤回」单据。
     * 逐条沿用 {@link #deleteApplication(Long, AuthUser)} 的口径；不满足条件的条目（非本人、非终态、不存在）
     * 静默跳过而不中断整批，返回实际删除条数，由前端提示「已删除 N 条」。
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteApplications(List<Long> ids, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        int removed = 0;
        for (Long id : new java.util.LinkedHashSet<>(ids)) {
            Application app = getById(id);
            if (app == null || !isApplicant(app, user)) {
                continue;
            }
            Integer status = app.getStatus();
            if (status == null || (status != STATUS_REJECTED && status != STATUS_WITHDRAWN)) {
                continue;
            }
            deleteApplication(app, user);
            removed++;
        }
        return removed;
    }

    /**
     * 收集仍被「待审批 / 退回修改」申请单引用的临时照片文件名（TMP: 值），
     * 供临时照片保留期清理任务保护在途单据（定时任务调用）。
     */
    public Set<String> referencedTempPhotos() {
        Set<String> result = new java.util.LinkedHashSet<>();
        List<Application> open = this.lambdaQuery()
                .select(Application::getId, Application::getBizData)
                .in(Application::getStatus, List.of(STATUS_PENDING, STATUS_RETURNED))
                .list();
        if (open == null) {
            return result;
        }
        for (Application app : open) {
            for (Object value : readChanges(app).values()) {
                if (value instanceof String text && text.startsWith(TMP_PREFIX)) {
                    result.add(text.substring(TMP_PREFIX.length()));
                }
            }
        }
        return result;
    }

    /**
     * 清理申请单关联的全部临时照片（TMP: 前缀值对应的临时文件），供驳回 / 撤回复用。
     */
    private void cleanTempPhotos(Application app) {
        Map<String, Object> changes = readChanges(app);
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            if (PHOTO_FIELDS.contains(entry.getKey()) && entry.getValue() != null
                    && String.valueOf(entry.getValue()).startsWith(TMP_PREFIX)) {
                photoStorageService.deleteTempPhoto(
                        String.valueOf(entry.getValue()).substring(TMP_PREFIX.length()));
            }
        }
    }

    // ==================== 事务提交后的文件 IO（照片归档 / 删除） ====================

    /**
     * 注册「事务提交后执行」的文件 IO 任务：照片归档、旧照片删除、临时文件清理统一走此通道。
     * 目的：数据库写入在同一事务内原子提交，文件 IO 不占用事务与数据库连接。
     * 无事务上下文时（如测试直接调用）立即执行。
     * 说明：数据库与文件系统无法共享事务，IO 失败只记录 error 日志，不回滚已提交的审批结果。
     */
    private void afterCommit(String scene, Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runAfterCommitSafely(scene, task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runAfterCommitSafely(scene, task);
            }
        });
    }

    private void runAfterCommitSafely(String scene, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("[审批流] 事务提交后文件操作失败（数据已提交，需人工核查照片文件）：{}", scene, e);
        }
    }

    /**
     * 批量归档临时照片：单张失败不阻断其余照片，逐张记录 error 日志便于人工补偿。
     */
    private void archiveTempPhotos(Map<String, String> pendingArchives, Long expertId) {
        for (Map.Entry<String, String> entry : pendingArchives.entrySet()) {
            try {
                photoStorageService.archiveTempPhoto(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("[审批流] 照片归档失败：专家ID={}，临时文件={}，目标文件={}",
                        expertId, entry.getKey(), entry.getValue(), e);
            }
        }
    }

    /**
     * 审批联动：同步项目-专家关联的合同状态快照（project_expert.contract_status）。
     */
    private void syncProjectExpertSnapshot(Long projectId, Long expertId, String status) {
        try {
            if (projectId == null || expertId == null) {
                return;
            }
            ProjectExpert rel = projectExpertMapper.selectOne(new LambdaQueryWrapper<ProjectExpert>()
                    .eq(ProjectExpert::getProjectId, projectId)
                    .eq(ProjectExpert::getExpertId, expertId)
                    .last("LIMIT 1"));
            if (rel == null) {
                return;
            }
            rel.setContractStatus(status);
            projectExpertMapper.updateById(rel);
        } catch (Exception e) {
            // 项目-专家合同状态属派生快照，失败不回滚主流程，但必须留痕便于核查
            log.warn("[审批流] 项目专家合同状态快照同步失败：projectId={}，expertId={}，status={}",
                    projectId, expertId, status, e);
        }
    }

    /**
     * 审批联动：同步合同状态（在审 PENDING / 通过 APPROVED / 驳回 REJECTED / 撤回或退回 DRAFT），并写 operation_log 留痕。
     * 合同 status 是审批的业务结果，写入失败必须向上抛出以触发整体回滚；
     * 仅「无合同关联」等前置条件不满足时直接返回，项目专家快照降级为告警日志。
     *
     * <p>approval_ref 维护（2026-11-24，Q3/Q17）：进入在审时指向当前申请单；离开在审（驳回/撤回/退回）
     * 时置空，避免合同行长期保留指向已终结单据的引用，后续若以该字段判定「是否在审」会误判。</p>
     */
    private void syncContractByApproval(Application app, String targetStatus, AuthUser user) {
        Map<String, Object> biz = readBiz(app);
        Object contractIdObj = biz.get("contractId");
        if (contractIdObj == null) {
            return;
        }
        Long contractId = ParamUtils.parseLong(contractIdObj, null);
        if (contractId == null) {
            return;
        }
        ProjectContract contract = projectContractMapper.selectById(contractId);
        if (contract == null) {
            log.warn("[审批流] 申请单 {} 关联的合同 {} 不存在，跳过合同状态联动", app.getId(), contractId);
            return;
        }
        contract.setStatus(targetStatus);
        contract.setApprovalRef("PENDING".equals(targetStatus) ? app.getId() : null);
        projectContractMapper.updateById(contract);
        if (contract.getProjectId() != null && contract.getExpertId() != null) {
            syncProjectExpertSnapshot(contract.getProjectId(), contract.getExpertId(), targetStatus);
        }
        operationLogService.record(user == null ? null : user.getUserId(),
                user == null ? "system" : user.getUsername(),
                OperationLogService.ACTION_UPDATE_CONTRACT,
                "审批联动：专家「" + contract.getExpertName() + "」合同状态更新为 " + targetStatus,
                "PROJECT", contract.getProjectId(), null);
    }

    /**
     * 合同在审单唯一性校验（Q3）：同一合同不允许同时存在多张 status∈{0 待审批, 4 退回修改} 的申请单。
     *
     * @param contractId  合同 id
     * @param excludeAppId 需要排除的申请单 id（重新提交时为当前单自身），无则传 null
     */
    private void ensureNoOtherOpenContractApplication(Long contractId, Long excludeAppId) {
        if (contractId == null) {
            return;
        }
        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<Application>()
                .eq(Application::getApplicationType, ApplicationType.CONTRACT.name())
                .in(Application::getStatus, List.of(STATUS_PENDING, STATUS_RETURNED))
                .apply("JSON_UNQUOTE(JSON_EXTRACT(biz_data, '$.contractId')) = {0}", String.valueOf(contractId));
        if (excludeAppId != null) {
            wrapper.ne(Application::getId, excludeAppId);
        }
        Long open = this.count(wrapper);
        if (open != null && open > 0) {
            throw new BusinessException("该合同已存在待审批或退回修改的申请单，请先在「我的申请」处理，避免重复在审");
        }
    }

    // ==================== 转办 / 加签（B2） ====================

    /**
     * 审批转办：当前审批人将待审批单移交指定用户。
     * - 仅 status=0 待审批可转；
     * - 当前用户须有 approval:audit，且满足 current_assignee 为空或等于本人；
     * - 目标用户必须存在且拥有 approval:audit 权限码；
     * - 转办后仅被转办人可审批（approve/reject 经 checkAssignee 校验）；
     * - 写 TRANSFER 记录并通知被转办人与申请人。
     */
    @Transactional(rollbackFor = Exception.class)
    public Application transfer(Long id, String targetUser, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (!user.hasPermission(PERM_APPROVAL_AUDIT)) {
            throw new BusinessException("无审批权限，不能执行转办");
        }
        if (!StringUtils.hasText(targetUser)) {
            throw new BusinessException("目标用户不能为空");
        }
        Application app = getPending(id);
        checkAssignee(app, user);
        SysUser target = checkTargetApprover(targetUser.trim());
        if (target.getUsername().equals(user.getUsername())) {
            throw new BusinessException("不能转办给自己");
        }
        app.setCurrentAssignee(target.getUsername());
        this.updateById(app);

        addRecord(app.getId(), "审批转办", "TRANSFER", user.getUsername(),
                "转给【" + target.getUsername() + "】");
        notifyTransferred(app, target.getUsername(), user);
        return fillView(app);
    }

    /**
     * 审批加签（或签语义）：当前审批人追加一个指定用户共同审批。
     * - 或签：原审批人与被加签人任一 approve 即通过、任一 reject 即驳回（不改变状态机）；
     * - 已加签不可重复加签；不能加签给自己；
     * - extra_approver 置为目标用户，写 ADD_APPROVER 记录并通知被加签人。
     */
    @Transactional(rollbackFor = Exception.class)
    public Application addApprover(Long id, String targetUser, String comment, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (!user.hasPermission(PERM_APPROVAL_AUDIT)) {
            throw new BusinessException("无审批权限，不能执行加签");
        }
        if (!StringUtils.hasText(targetUser)) {
            throw new BusinessException("目标用户不能为空");
        }
        Application app = getPending(id);
        checkAssignee(app, user);
        if (StringUtils.hasText(app.getExtraApprover())) {
            throw new BusinessException("该申请单已加签，不能重复加签");
        }
        SysUser target = checkTargetApprover(targetUser.trim());
        if (target.getUsername().equals(user.getUsername())) {
            throw new BusinessException("不能加签给自己");
        }
        app.setExtraApprover(target.getUsername());
        this.updateById(app);

        addRecord(app.getId(), "审批加签", "ADD_APPROVER", user.getUsername(),
                StringUtils.hasText(comment) ? comment.trim() : "加签【" + target.getUsername() + "】共同审批");
        notifyAssigned(app, target.getUsername());
        return fillView(app);
    }

    /**
     * 可转办/加签用户列表：启用用户中拥有 approval:audit 权限码的用户（仅返回 id + 用户名）。
     */
    public List<Map<String, Object>> assignableUsers() {
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, 1)
                        .orderByAsc(SysUser::getUsername));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser u : users) {
            if (!permissionService.hasPermission(u.getId(), PERM_APPROVAL_AUDIT)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            result.add(item);
        }
        return result;
    }

    /**
     * 申请人本人判定：applicant_id 或 applicant 用户名任一匹配（与 my-applications 列表口径一致；
     * 存量迁移行 applicant 可能为空，用户改名后 applicant_id 仍可识别，避免 NPE）。
     */
    private boolean isApplicant(Application app, AuthUser user) {
        return java.util.Objects.equals(app.getApplicantId(), user.getUserId())
                || java.util.Objects.equals(app.getApplicant(), user.getUsername());
    }

    /**
     * 审批权校验（B2）：current_assignee 非空时仅被转办人本人可审批；为空维持现状（任何有审批权限的用户可审）。
     */
    private void checkAssignee(Application app, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        String username = user.getUsername();
        String assignee = app.getCurrentAssignee();
        String extra = app.getExtraApprover();
        // 或签：被加签人（extraApprover）与当前审批人任一可审批
        if (StringUtils.hasText(extra) && extra.equals(username)) {
            return;
        }
        if (StringUtils.hasText(assignee) && !assignee.equals(username)) {
            throw new BusinessException("该申请单已转办给 " + assignee + "，仅被转办人可审批");
        }
    }

    /**
     * 转办/加签目标用户校验：用户存在、未停用、拥有 approval:audit 权限码（与现有审批权限体系一致）。
     */
    private SysUser checkTargetApprover(String targetUser) {
        SysUser target = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, targetUser).last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException("目标用户「" + targetUser + "」不存在");
        }
        if (target.getStatus() != null && target.getStatus() != 1) {
            throw new BusinessException("目标用户「" + targetUser + "」已停用");
        }
        if (!permissionService.hasPermission(target.getId(), PERM_APPROVAL_AUDIT)) {
            throw new BusinessException("目标用户「" + targetUser + "」没有审批权限，不能被转办/加签");
        }
        return target;
    }

    // ==================== 通知 ====================

    /**
     * 广播通知给审批流配置角色下的全部用户（todo/withdrawn 共用骨架）。
     */
    private void notifyApproverRole(Application app, String type, String title, String content) {
        try {
            // 多流程：按流程配置通知；快照非空时优先通知快照内具体审批人（R2/R3）
            List<Long> userIds = snapshotUserIds(app);
            if (!userIds.isEmpty()) {
                for (Long uid : userIds) {
                    notificationService.notify(uid, type, title, content, MANAGE_PATH);
                }
                return;
            }
            ApprovalConfig config = getConfig(flowKeyOf(app));
            String roleCode = config.getApproverRole();
            if (!StringUtils.hasText(roleCode)) {
                return;
            }
            Role role = roleMapper.selectOne(
                    new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode).last("LIMIT 1"));
            if (role == null) {
                return;
            }
            List<UserRole> bindings = userRoleMapper.selectList(
                    new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, role.getId()));
            for (UserRole ur : bindings) {
                notificationService.notify(ur.getUserId(), type, title, content, MANAGE_PATH);
            }
        } catch (Exception e) {
            // 通知失败不影响主流程，但留痕便于核查（2026-11-24 补日志）
            log.warn("[审批流] 通知发送失败：申请单={}", app.getId(), e);
        }
    }

    /**
     * 新申请待审通知（approval_todo）：通知审批人角色下全部用户。
     */
    private void notifyApprovalTodo(Application app) {
        String typeText = typeTextOf(app);
        notifyApproverRole(app, NotificationService.TYPE_APPROVAL_TODO,
                "新的" + typeText + "待审批",
                "申请人 " + app.getApplicant() + " 提交了新的申请，请及时处理");
    }

    /**
     * 撤回通知（approval_withdrawn）：通知审批人角色下全部用户「申请已撤回」。
     */
    private void notifyApprovalWithdrawn(Application app) {
        String typeText = typeTextOf(app);
        notifyApproverRole(app, NotificationService.TYPE_APPROVAL_WITHDRAWN,
                "申请已撤回：" + typeText,
                "申请人 " + app.getApplicant() + " 撤回了" + typeText + "，无需再处理");
    }

    /**
     * 审批结果通知（approval_result）：通知申请人审批通过 / 驳回结果。
     */
    private void notifyApprovalResult(Application app, AuthUser user) {
        try {
            Long applicantId = app.getApplicantId() != null ? app.getApplicantId() : notificationService.userIdOf(app.getApplicant());
            if (applicantId == null) {
                return;
            }
            boolean approved = app.getStatus() != null && app.getStatus() == STATUS_APPROVED;
            String typeText = typeTextOf(app);
            String operator = user == null ? "管理员" : user.getUsername();
            String content = operator + (approved ? "已通过您的" : "已驳回您的") + typeText;
            if (StringUtils.hasText(app.getApproveComment())) {
                content += "，意见：" + app.getApproveComment();
            }
            String jump = approved ? MY_APPLICATIONS_PATH + "?status=1" : MY_APPLICATIONS_PATH + "?status=2";
            notificationService.notify(applicantId, NotificationService.TYPE_APPROVAL_RESULT,
                    (approved ? "审批通过：" : "审批驳回：") + typeText, content, jump);
        } catch (Exception e) {
            // 通知失败不影响主流程，但留痕便于核查（2026-11-24 补日志）
            log.warn("[审批流] 通知发送失败：申请单={}", app.getId(), e);
        }
    }

    /**
     * 转办通知：通知被转办人新增待办（approval_transferred）；申请人可收到转办提醒。
     */
    private void notifyTransferred(Application app, String targetUser, AuthUser operator) {
        try {
            String typeText = typeTextOf(app);
            Long targetId = notificationService.userIdOf(targetUser);
            if (targetId != null) {
                notificationService.notify(targetId, NotificationService.TYPE_APPROVAL_TRANSFERRED,
                        "转办待办：" + typeText,
                        "「" + app.getExpertName() + "」的" + typeText + "已由 "
                                + operator.getUsername() + " 转办给您，请及时处理",
                        MANAGE_PATH);
            }
            Long applicantId = app.getApplicantId() != null ? app.getApplicantId() : notificationService.userIdOf(app.getApplicant());
            if (applicantId != null) {
                notificationService.notify(applicantId, NotificationService.TYPE_APPROVAL_TRANSFERRED,
                        "您的申请已转办",
                        "您的「" + app.getExpertName() + "」" + typeText + "已转办给 " + targetUser + " 审批",
                        MY_APPLICATIONS_PATH);
            }
        } catch (Exception e) {
            // 通知失败不影响主流程，但留痕便于核查（2026-11-24 补日志）
            log.warn("[审批流] 通知发送失败：申请单={}", app.getId(), e);
        }
    }

    /**
     * 加签通知（approval_assigned）：通知被加签人新增待办。
     */
    private void notifyAssigned(Application app, String targetUser) {
        try {
            Long targetId = notificationService.userIdOf(targetUser);
            if (targetId == null) {
                return;
            }
            String typeText = typeTextOf(app);
            notificationService.notify(targetId, NotificationService.TYPE_APPROVAL_ASSIGNED,
                    "加签待办：" + typeText,
                    "「" + app.getExpertName() + "」的" + typeText + "已加签您共同审批（任一人审批即生效），请及时处理",
                    MANAGE_PATH);
        } catch (Exception e) {
            // 通知失败不影响主流程，但留痕便于核查（2026-11-24 补日志）
            log.warn("[审批流] 通知发送失败：申请单={}", app.getId(), e);
        }
    }

    /**
     * 审批类型文案（通知用）。
     */
    private String typeTextOf(Application app) {
        ApplicationType type = ApplicationType.parse(app.getApplicationType());
        if (type == ApplicationType.EXPERT_CREATE) {
            return "新增专家申请";
        }
        if (type == ApplicationType.CONTRACT) {
            return "专家合同审批申请";
        }
        return "专家信息修改申请";
    }

    // ==================== 私有方法 ====================

    private ApprovalConfig findConfigByFlowKey(String flowKey) {
        return approvalConfigMapper.selectOne(
                new LambdaQueryWrapper<ApprovalConfig>().eq(ApprovalConfig::getFlowKey, flowKey).last("LIMIT 1"));
    }

    /** 申请单 -> 流程键（存量单 flow_key 为空时按类型兜底 EXPERT）。 */
    private String flowKeyOf(Application app) {
        if (app != null && StringUtils.hasText(app.getFlowKey())) {
            return app.getFlowKey().trim();
        }
        if (app != null && ApplicationType.parse(app.getApplicationType()) == ApplicationType.CONTRACT) {
            return FLOW_KEY_CONTRACT;
        }
        return FLOW_KEY;
    }

    /**
     * 按流程配置生成审批人快照 JSON 数组 [{id, username}]：
     * 优先取具体审批人（approval_config_user）；未配置时回退 approver_role 角色下启用用户；
     * 仍为空返回 "[]"（空数组=任意有审批权限的用户可审，兼容旧语义）。
     */
    private String buildApproverSnapshot(ApprovalConfig config) {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            List<ApprovalConfigUser> users = approvalConfigUserMapper.selectList(
                    new LambdaQueryWrapper<ApprovalConfigUser>()
                            .eq(ApprovalConfigUser::getConfigId, config.getId())
                            .orderByAsc(ApprovalConfigUser::getId));
            if (!users.isEmpty()) {
                for (ApprovalConfigUser u : users) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", u.getUserId());
                    item.put("username", u.getUsername());
                    items.add(item);
                }
            } else if (StringUtils.hasText(config.getApproverRole())) {
                Role role = roleMapper.selectOne(
                        new LambdaQueryWrapper<Role>().eq(Role::getCode, config.getApproverRole()).last("LIMIT 1"));
                if (role != null) {
                    List<UserRole> bindings = userRoleMapper.selectList(
                            new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, role.getId()));
                    List<Long> userIds = bindings.stream().map(UserRole::getUserId).toList();
                    if (!userIds.isEmpty()) {
                        List<SysUser> roleUsers = sysUserMapper.selectList(
                                new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds).eq(SysUser::getStatus, 1));
                        for (SysUser u : roleUsers) {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("id", u.getId());
                            item.put("username", u.getUsername());
                            items.add(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 快照生成失败不影响提交主流程（返回空数组=任意审批人可审），但语义被放宽必须留痕（Q11）。
            log.warn("[审批流] 审批人快照生成失败，本次申请按「任意有审批权限的用户可审」处理：flowKey={}",
                    config == null ? null : config.getFlowKey(), e);
            items.clear();
        }
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 从审批人快照 JSON 提取 userId 列表（通知用；解析失败或空快照返回空列表）。 */
    private List<Long> snapshotUserIds(Application app) {
        List<Long> result = new ArrayList<>();
        if (app == null || !StringUtils.hasText(app.getApproverSnapshot())) {
            return result;
        }
        try {
            List<Map<String, Object>> items = objectMapper.readValue(
                    app.getApproverSnapshot(), new TypeReference<List<Map<String, Object>>>() {
                    });
            for (Map<String, Object> item : items) {
                Object id = item.get("id");
                if (id != null) {
                    result.add(ParamUtils.parseLong(id, null));
                }
            }
        } catch (Exception e) {
            // 解析失败视为无快照（回退角色通知）
            log.warn("[审批流] 审批人快照解析失败：申请单={}", app == null ? null : app.getId(), e);
        }
        return result;
    }

    /** 退回修改通知（approval_result）：通知申请人修改后重新提交。 */
    private void notifyReturned(Application app, AuthUser user) {
        try {
            Long applicantId = app.getApplicantId() != null ? app.getApplicantId() : notificationService.userIdOf(app.getApplicant());
            if (applicantId == null) {
                return;
            }
            String typeText = typeTextOf(app);
            String operator = user == null ? "管理员" : user.getUsername();
            String content = operator + "已退回您的" + typeText + "，请修改后重新提交";
            if (StringUtils.hasText(app.getApproveComment())) {
                content += "，意见：" + app.getApproveComment();
            }
            notificationService.notify(applicantId, NotificationService.TYPE_APPROVAL_RESULT,
                    "退回修改：" + typeText, content, MY_APPLICATIONS_PATH + "?status=4");
        } catch (Exception e) {
            // 通知失败不影响主流程，但留痕便于核查（2026-11-24 补日志）
            log.warn("[审批流] 通知发送失败：申请单={}", app.getId(), e);
        }
    }

    /**
     * 取待审批申请单；不存在或已处理时报错。
     * 返回前先解析 biz_data 公共字段（expertId/expertName 等不再是数据库列，裸实体需补齐供审批链路使用）。
     */
    private Application getPending(Long id) {
        if (id == null) {
            throw new BusinessException("申请单ID不能为空");
        }
        Application app = getById(id);
        if (app == null) {
            throw new BusinessException("申请单不存在");
        }
        if (app.getStatus() == null || app.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该申请单已处理，不能重复审批");
        }
        fillBiz(app);
        return app;
    }

    /**
     * H2 并发抢占：把待审批单以 {@code status=0} 为条件原子推进到目标状态。
     *
     * <p>并发场景：两名审批人同时点击「通过」，双方 {@link #getPending(Long)} 都会读到 status=0；
     * 只有一方能在条件更新中拿到影响行数 1，另一方拿到 0 并立即抛 409 回滚，
     * 因此「同一申请重复审批通过」「重复创建专家」「合同重复生效」不会发生。</p>
     *
     * <p>必须在任何业务副作用（建专家、合同生效、写审批记录、发通知）之前调用；
     * 后续步骤抛异常时由 {@code @Transactional} 整体回滚，状态自然恢复为 0，不留抢占痕迹。</p>
     */
    private void claimPending(Application app, int targetStatus) {
        int affected = getBaseMapper().updateStatusIfCurrent(app.getId(), STATUS_PENDING, targetStatus);
        if (affected != 1) {
            throw new BusinessException(409, "该申请单已被其他审批人处理，请刷新后重试");
        }
        app.setStatus(targetStatus);
    }

    /**
     * 写一条审批流记录（application_id 为准；request_id 同值保留满足列非空约束与历史兼容）。
     */
    private void addRecord(Long applicationId, String nodeName, String action, String operator, String comment) {
        ApprovalRecord record = new ApprovalRecord();
        record.setRequestId(applicationId);
        record.setApplicationId(applicationId);
        record.setNodeName(nodeName);
        record.setAction(action);
        record.setOperator(operator);
        record.setComment(comment);
        record.setCreateTime(LocalDateTime.now());
        approvalRecordMapper.insert(record);
    }

    /**
     * 查询申请单全部进度记录（按时间升序，关联 application_id；存量行已按同 id 回填）。
     */
    private List<ApprovalRecord> recordsOf(Long applicationId) {
        return approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getApplicationId, applicationId)
                        .orderByAsc(ApprovalRecord::getCreateTime));
    }

    /**
     * 单条填充展示字段（详情/审批响应使用）：biz_data 解析（expertId/expertName/reason/changes/oldData/photoChanges）、
     * requestType/requestTypeText、statusText、records、当前处理人与停留时长（R2）。
     *
     * <p>脱敏口径（2026-11-24，Q5）：oldData 与 changes 一律返回明文。此前仅 oldData 对非管理员
     * 前3后4、changes 明文，同一弹窗出现「变更前打码 / 变更后明文」的错位展示，而 ExpertService.toView
     * 早已改为全员明文，脱敏事实失效却误导维护者。申请单可见范围仅「本人 + 审批人」，审批又必须核对原文。</p>
     */
    private Application fillView(Application app) {
        return fillView(app, recordsOf(app.getId()));
    }

    /**
     * 填充展示字段（进度记录已由调用方批量加载，避免逐行查询造成 N+1）。
     */
    private Application fillView(Application app, List<ApprovalRecord> records) {
        fillBiz(app);
        Map<String, Object> biz = readBiz(app);
        Map<String, Object> rawChanges = asStringKeyMap(biz.get("changes"));
        app.setChanges(friendlyChanges(rawChanges));
        app.setPhotoChanges(photoChanges(rawChanges));
        app.setOldData(asStringKeyMap(biz.get("oldData")));
        ApplicationType type = ApplicationType.parse(app.getApplicationType());
        app.setRequestType(type.requestType());
        app.setRequestTypeText(type.text());
        app.setStatusText(statusText(app.getStatus()));
        app.setRecords(records);
        fillTimeliness(app, records);
        fillExpiredPhotos(app, rawChanges);
        return app;
    }

    /**
     * 退回修改单的失效照片键（2026-11-25）：status=4 且照片值仍为 TMP: 临时文件而文件已不存在
     * （被保留期清理任务删除）。前端据此提示「需重新上传」，避免申请人以为不重传也能生效。
     * 其余状态不探测文件系统。
     */
    private void fillExpiredPhotos(Application app, Map<String, Object> rawChanges) {
        if (!Integer.valueOf(STATUS_RETURNED).equals(app.getStatus()) || rawChanges == null || rawChanges.isEmpty()) {
            return;
        }
        List<String> expired = new ArrayList<>();
        for (String key : PHOTO_FIELDS) {
            if (rawChanges.get(key) instanceof String raw && raw.startsWith(TMP_PREFIX)
                    && !photoStorageService.existsTempPhoto(raw.substring(TMP_PREFIX.length()))) {
                expired.add(key);
            }
        }
        if (!expired.isEmpty()) {
            app.setExpiredPhotoFields(expired);
        }
    }

    /**
     * 批量填充展示字段（列表使用）：按页内 id 集合一次 IN 查询进度记录后分组回填，
     * 把「每行一次 recordsOf」的 N+1 收敛为固定 1 次查询（Q2）。
     */
    private void fillViews(List<Application> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(Application::getId).filter(java.util.Objects::nonNull).toList();
        Map<Long, List<ApprovalRecord>> byApplication = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            List<ApprovalRecord> records = approvalRecordMapper.selectList(
                    new LambdaQueryWrapper<ApprovalRecord>()
                            .in(ApprovalRecord::getApplicationId, ids)
                            .orderByAsc(ApprovalRecord::getCreateTime));
            for (ApprovalRecord record : records) {
                byApplication.computeIfAbsent(record.getApplicationId(), k -> new ArrayList<>()).add(record);
            }
        }
        for (Application row : rows) {
            fillView(row, byApplication.getOrDefault(row.getId(), List.of()));
        }
    }

    /**
     * 派生「当前处理人 + 待审批停留时长」（R2，只读，不改状态机）。
     *
     * <p>处理人优先级：转办人 current_assignee → 加签人 extra_approver → 审批人快照
     * （单人直接显示，多人显示「首位 等 N 人」，空快照为「任意审批人」）；退回修改单固定「待申请人修改」。
     * 停留时长取最近一条 SUBMIT/RESUBMIT 记录时间（重提不更新 create_time，用创建时间会高估），
     * 均无记录时回退 create_time，仅 status=0 计算。</p>
     */
    private void fillTimeliness(Application app, List<ApprovalRecord> records) {
        Integer status = app.getStatus();
        if (status == null) {
            return;
        }
        if (status == STATUS_RETURNED) {
            app.setCurrentAssigneeName("待申请人修改");
            return;
        }
        if (status != STATUS_PENDING) {
            return;
        }
        app.setCurrentAssigneeName(resolveAssigneeName(app));
        LocalDateTime since = app.getCreateTime();
        if (records != null) {
            for (ApprovalRecord record : records) {
                boolean submitted = "SUBMIT".equals(record.getAction()) || "RESUBMIT".equals(record.getAction());
                if (submitted && record.getCreateTime() != null) {
                    since = record.getCreateTime();
                }
            }
        }
        if (since != null) {
            long hours = java.time.temporal.ChronoUnit.HOURS.between(since, LocalDateTime.now());
            app.setPendingHours(Math.max(0, hours));
        }
    }

    /** 当前处理人文案：转办人 → 加签人 → 审批人快照（单人/多人/空快照）。 */
    private String resolveAssigneeName(Application app) {
        if (StringUtils.hasText(app.getCurrentAssignee())) {
            return app.getCurrentAssignee();
        }
        if (StringUtils.hasText(app.getExtraApprover())) {
            return app.getExtraApprover();
        }
        if (!StringUtils.hasText(app.getApproverSnapshot())) {
            return "任意审批人";
        }
        try {
            List<Map<String, Object>> members = objectMapper.readValue(app.getApproverSnapshot(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            List<String> names = new ArrayList<>();
            for (Map<String, Object> member : members) {
                Object username = member.get("username");
                if (username != null && StringUtils.hasText(String.valueOf(username))) {
                    names.add(String.valueOf(username));
                }
            }
            if (names.isEmpty()) {
                return "任意审批人";
            }
            return names.size() == 1 ? names.get(0) : names.get(0) + " 等 " + names.size() + " 人";
        } catch (Exception e) {
            log.warn("[审批流] 审批人快照解析失败（当前处理人降级为任意审批人）：申请单={}", app.getId(), e);
            return "任意审批人";
        }
    }

    /**
     * 仅解析 biz_data 公共字段（不加进度记录等重查询），供审批/撤回等内部链路取 expertId/expertName。
     */
    private void fillBiz(Application app) {
        Map<String, Object> biz = readBiz(app);
        app.setExpertId(ParamUtils.toLong(biz.get("expertId")));
        Object expertName = biz.get("expertName");
        app.setExpertName(expertName == null ? null : String.valueOf(expertName));
        Object reason = biz.get("reason");
        app.setReason(reason == null ? null : String.valueOf(reason));
    }

    /**
     * 敏感字段格式兜底校验（与 ExpertService.validateRequired 对齐）：
     * 仅对本次变更涉及且值非空的身份证号/银行卡号/联系方式做格式校验。
     */
    private void validateSensitiveFields(Map<String, Object> changes) {
        validateSensitiveFields(changes, null);
    }

    /**
     * 敏感字段格式校验（限定键）：touched 非空时只校验 touched 内的敏感字段——
     * 重新提交时历史单里未改动的存量值（如旧格式的联系方式）不应永久阻塞重提。
     */
    private void validateSensitiveFields(Map<String, Object> changes, Set<String> touched) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        for (String key : SENSITIVE_FIELDS) {
            if (touched != null && !touched.contains(key)) {
                continue;
            }
            Object raw = changes.get(key);
            if (raw == null || !StringUtils.hasText(String.valueOf(raw).trim())) {
                continue;
            }
            String value = String.valueOf(raw).trim();
            switch (key) {
                case "身份证号" -> {
                    if (!ID_CARD_PATTERN.matcher(value).matches()) {
                        throw new BusinessException("身份证号格式不正确（18 位，末位可为 X）");
                    }
                }
                case "银行卡号" -> {
                    if (!BANK_CARD_PATTERN.matcher(value).matches()) {
                        throw new BusinessException("银行卡号格式不正确（13-19 位数字）");
                    }
                }
                default -> {
                    if (!CONTACT_PATTERN.matcher(value).matches()) {
                        throw new BusinessException("联系方式格式不正确（11 位手机号或邮箱）");
                    }
                }
            }
        }
    }

    /**
     * 照片字段展示友好化：TMP: 临时文件名与生效后回填的归档文件名统一替换为「已更换新照片」，
     * 避免审批列表裸显文件名（原始文件名经 photoChanges 字段供前端预览，不出现在 changes 中）。
     */
    private Map<String, Object> friendlyChanges(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            return changes;
        }
        Map<String, Object> result = new HashMap<>(changes);
        for (String key : PHOTO_FIELDS) {
            Object value = result.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                result.put(key, "已更换新照片");
            }
        }
        return result;
    }

    /**
     * 照片字段的原始值视图（TMP: 临时名或生效后回填的归档文件名），供对比弹窗加载「变更后」照片缩略图。
     * 仅含照片文件名，不含任何敏感原文。
     */
    private Map<String, Object> photoChanges(Map<String, Object> rawChanges) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (rawChanges == null) {
            return result;
        }
        for (String key : PHOTO_FIELDS) {
            if (rawChanges.containsKey(key)) {
                result.put(key, rawChanges.get(key));
            }
        }
        return result;
    }

    /**
     * 「我的申请」提交日期区间条件（R1）：startDate/endDate 为 yyyy-MM-dd，均含当日；
     * 格式非法时按 400 拒绝（避免静默忽略筛选条件导致用户误判数据）。空值不参与过滤。
     */
    private void applyDateRange(LambdaQueryWrapper<Application> wrapper, String startDate, String endDate) {
        java.time.LocalDate start = parseDate(startDate, "开始日期");
        java.time.LocalDate end = parseDate(endDate, "结束日期");
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessException(400, "开始日期不能晚于结束日期");
        }
        if (start != null) {
            wrapper.ge(Application::getCreateTime, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.lt(Application::getCreateTime, end.plusDays(1).atStartOfDay());
        }
    }

    private java.time.LocalDate parseDate(String value, String label) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BusinessException(400, label + "格式不正确（应为 yyyy-MM-dd）");
        }
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case STATUS_APPROVED -> "已通过";
            case STATUS_REJECTED -> "已驳回";
            case STATUS_WITHDRAWN -> "已撤回";
            case STATUS_RETURNED -> "退回修改";
            default -> "待审批";
        };
    }

    /**
     * 把白名单字段的中文键 -> 新值映射写入 Expert 实体（仅设置存在的键）。
     * 键集合与 {@link #WHITELIST_FIELDS} 一一对应，白名单内不存在"接受但不落库"的字段
     * （2026-11-24 补「身份证有效期」映射：此前白名单放行但 applyChanges 无对应分支，值被静默丢弃）。
     */
    private void applyChanges(Expert patch, Map<String, Object> changes) {
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            if (!WHITELIST_FIELDS.contains(key)) {
                continue;
            }
            String value = entry.getValue() == null ? null : String.valueOf(entry.getValue()).trim();
            switch (key) {
                case "姓名" -> patch.setName(value);
                case "单位" -> patch.setUnit(value);
                case "科室" -> patch.setDepartment(value);
                case "职称" -> patch.setTitle(value);
                case "研究方向" -> patch.setResearchDirection(value);
                case "擅长领域" -> patch.setExpertiseArea(value);
                case "联系方式" -> patch.setContact(value);
                case "简介" -> patch.setProfile(value);
                case "身份证号" -> patch.setIdCard(value);
                case "银行卡号" -> patch.setBankCard(value);
                case "银行卡开户行" -> patch.setBankBranch(value);
                case "身份证有效期" -> patch.setIdCardExpiry(value);
                default -> {
                    // 白名单外的键已在提交时拦截，此处兜底忽略
                }
            }
        }
    }

    /**
     * 照片字段 -> 存储类型。
     */
    private String photoTypeOf(String key) {
        return switch (key) {
            case "照片" -> "photo";
            case "身份证正面" -> "idcard_front";
            default -> "idcard_back";
        };
    }

    // ==================== biz_data 读写 ====================

    private String writeBizData(Long expertId, String expertName, Map<String, Object> changes,
                                String reason, Map<String, Object> oldData) {
        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("expertId", expertId);
        biz.put("expertName", expertName);
        biz.put("changes", changes);
        biz.put("reason", reason);
        if (oldData != null) {
            biz.put("oldData", oldData);
        }
        return writeBiz(biz);
    }

    /**
     * 专家当前全量快照（中文键 -> 旧值，11 个文本字段 + 3 个照片文件名，键序与白名单一致）。
     * 提交修改申请时写入 biz_data.oldData，保证审批通过更新 expert_info 后仍可还原「变更前」。
     */
    private Map<String, Object> buildOldSnapshot(Expert expert) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("姓名", expert.getName());
        snapshot.put("单位", expert.getUnit());
        snapshot.put("科室", expert.getDepartment());
        snapshot.put("职称", expert.getTitle());
        snapshot.put("研究方向", expert.getResearchDirection());
        snapshot.put("擅长领域", expert.getExpertiseArea());
        snapshot.put("联系方式", expert.getContact());
        snapshot.put("简介", expert.getProfile());
        snapshot.put("身份证号", expert.getIdCard());
        snapshot.put("银行卡号", expert.getBankCard());
        snapshot.put("银行卡开户行", expert.getBankBranch());
        snapshot.put("照片", expert.getPhoto());
        snapshot.put("身份证正面", expert.getIdCardFront());
        snapshot.put("身份证反面", expert.getIdCardBack());
        snapshot.put("身份证有效期", expert.getIdCardExpiry());
        return snapshot;
    }

    /** 读取 biz_data.oldData（提交时的专家旧值快照；无则空 Map）。 */
    private Map<String, Object> readOldData(Application app) {
        return asStringKeyMap(readBiz(app).get("oldData"));
    }

    /**
     * 把 biz_data.changes 中已生效照片键的 TMP: 值替换为归档文件名（审批通过后回填，
     * 使已通过单的「变更后」照片仍可预览）。
     */
    private void applyPhotoArchivedNames(Map<String, Object> biz, Map<String, String> photoApplied) {
        if (photoApplied.isEmpty()) {
            return;
        }
        Map<String, Object> changes = asStringKeyMap(biz.get("changes"));
        for (Map.Entry<String, String> entry : photoApplied.entrySet()) {
            changes.put(entry.getKey(), entry.getValue());
        }
        biz.put("changes", changes);
    }

    private String writeBiz(Map<String, Object> biz) {
        try {
            return objectMapper.writeValueAsString(biz);
        } catch (Exception e) {
            throw new BusinessException("申请内容序列化失败: " + e.getMessage());
        }
    }

    /**
     * 解析 biz_data（失败返回空 Map，由调用方决定是否阻断）。
     *
     * <p>2026-11-24（Q1）：解析失败必须留痕。此前空 catch 静默返回空 Map，被审批链路当成
     * 「无变更」继续执行，形成「审批通过但字段未更新」的不可见故障。</p>
     */
    private Map<String, Object> readBiz(Application app) {
        if (!StringUtils.hasText(app.getBizData())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(app.getBizData(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.error("[审批流] biz_data 解析失败（申请单 {}）：{}，请核查该行 JSON 是否损坏",
                    app.getId(), e.getMessage());
            return new HashMap<>();
        }
    }

    /** 任意 JSON 对象转 String 键 Map（保持键序；非 Map 或 null 返回空 Map）。 */
    private Map<String, Object> asStringKeyMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 读取 biz_data.changes（照片 TMP: 值原样返回，展示层再友好化）。
     */
    private Map<String, Object> readChanges(Application app) {
        return asStringKeyMap(readBiz(app).get("changes"));
    }
}
