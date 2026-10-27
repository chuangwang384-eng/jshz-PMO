package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.ApplicationType;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ApprovalConfig;
import com.jshz.expert.entity.ApprovalRecord;
import com.jshz.expert.entity.Application;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.Role;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.entity.UserRole;
import com.jshz.expert.mapper.ApprovalConfigMapper;
import com.jshz.expert.mapper.ApprovalRecordMapper;
import com.jshz.expert.mapper.ApplicationMapper;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.mapper.UserRoleMapper;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.common.ParamUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 通用申请服务（「我的申请」改版，由 ApprovalService 演化）。
 * 通用申请主表 application + biz_data JSON 承载业务差异；一期类型 EXPERT_MODIFY / EXPERT_CREATE。
 * 员工提交 -> 审批人处理（approve/reject/转办/加签）；通过时把白名单字段写入 expert_info。
 * 接口路径沿用 /api/approval/*（permission 表路径规则不变），HTTP 层见 ApplicationController。
 */
@Service
@RequiredArgsConstructor
public class ApplicationService extends ServiceImpl<ApplicationMapper, Application> {

    /** 审批流流程键 */
    public static final String FLOW_KEY = "EXPERT_MODIFY";

    /** 审批处理权限码（转办/加签目标用户与当前审批人须持有） */
    public static final String PERM_APPROVAL_AUDIT = "approval:audit";

    /** 字段白名单（排除照片类、id、时间类、下载次数） */
    public static final List<String> WHITELIST_FIELDS = List.of(
            "姓名", "单位", "科室", "职称", "研究方向", "擅长领域",
            "联系方式", "简介", "身份证号", "银行卡号", "银行卡开户行");

    /** 照片字段白名单（照片变更随审批流一并生效） */
    public static final List<String> PHOTO_FIELDS = List.of("照片", "身份证正面", "身份证反面");

    /** 临时照片值前缀 */
    private static final String TMP_PREFIX = "TMP:";

    /** 临时照片值格式：TMP:文件名 */
    private static final Pattern TMP_PATTERN = Pattern.compile("^TMP:[A-Za-z0-9._-]+$");

    /** 状态：0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤回 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;
    private static final int STATUS_WITHDRAWN = 3;

    /** 新增专家必填文本字段（审批通过后写入 expert_info；模板管理接口对外暴露该清单） */
    public static final List<String> CREATE_REQUIRED_FIELDS = List.of(
            "姓名", "单位", "科室", "职称", "研究方向", "擅长领域", "联系方式");

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

    private final ApprovalConfigMapper approvalConfigMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ApprovalTemplateService approvalTemplateService;
    private final ExpertService expertService;
    private final PhotoStorageService photoStorageService;
    private final RoleMapper roleMapper;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final SysUserMapper sysUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionService permissionService;

    // ==================== 审批流配置 ====================

    /**
     * 读取审批流配置；不存在则初始化默认配置（启用、审批人 ADMIN）。
     */
    public ApprovalConfig getConfig() {
        ApprovalConfig config = findConfig();
        if (config == null) {
            config = new ApprovalConfig();
            config.setFlowKey(FLOW_KEY);
            config.setFlowName("专家信息修改审批");
            config.setEnabled(1);
            config.setApproverRole("ADMIN");
            approvalConfigMapper.insert(config);
        }
        return config;
    }

    /**
     * 更新审批流配置（仅 flow_key='EXPERT_MODIFY' 行）。
     */
    public ApprovalConfig updateConfig(Integer enabled, String approverRole) {
        if (!StringUtils.hasText(approverRole)) {
            throw new BusinessException("审批人角色不能为空");
        }
        // RBAC 阶段1：approver_role 可配置为任意已存在的角色（如 APPROVER / ADMIN），
        // 不在代码中硬解；实际审批动作由 approval:audit 权限码在 permission 数据中控制。
        if (roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getCode, approverRole.trim())) == 0) {
            throw new BusinessException("审批人角色「" + approverRole.trim() + "」不存在，请在角色管理中先创建");
        }
        ApprovalConfig config = getConfig();
        if (enabled != null) {
            if (enabled != 0 && enabled != 1) {
                throw new BusinessException("启用开关取值不合法");
            }
            config.setEnabled(enabled);
        }
        config.setApproverRole(approverRole.trim());
        approvalConfigMapper.updateById(config);
        return getConfig();
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
    public Application submitRequest(Long expertId, Map<String, Object> changes, String reason,
                                     Long templateId, AuthUser user) {
        checkSubmitEligible(user, "编辑");
        if (expertId == null) {
            throw new BusinessException("专家ID不能为空");
        }
        Expert expert = expertService.getById(expertId);
        if (expert == null) {
            throw new BusinessException("专家不存在");
        }
        checkChanges(changes);
        validateSensitiveFields(changes);
        // 模板校验（R-B4）：传了 templateId 时字段集合必须 ⊆ 模板字段（且模板存在并启用）
        if (templateId != null) {
            approvalTemplateService.checkTemplateForSubmit(templateId, changes.keySet());
        }

        Application app = new Application();
        app.setApplicantId(user.getUserId());
        app.setApplicant(user.getUsername());
        app.setApplicationType(ApplicationType.EXPERT_MODIFY.name());
        app.setStatus(STATUS_PENDING);
        // 提交时落专家全量旧值快照：审批通过更新 expert_info 后仍可还原「变更前」
        app.setBizData(writeBizData(expertId, expert.getName(), changes, reason, templateId,
                buildOldSnapshot(expert)));
        app.setCreateTime(LocalDateTime.now());
        this.save(app);

        addRecord(app.getId(), "提交修改申请", "SUBMIT", user.getUsername(),
                StringUtils.hasText(reason) ? reason : "提交专家信息修改申请");
        return app;
    }

    /**
     * 提交新增专家申请（application_type=EXPERT_CREATE）：
     * - 校验与 submitRequest 一致（管理员拒绝、审批流启用、changes 非空、字段白名单、照片 TMP: 与临时文件存在）；
     * - 额外校验新增必填项（姓名、单位等，姓名须满足 ^[一-龥·]{2,20}$）；
     * - expertId 为空，审批通过后创建专家并回填 biz_data.expertId。
     */
    public Application submitCreateRequest(Map<String, Object> changes, String reason,
                                           Long templateId, AuthUser user) {
        checkSubmitEligible(user, "新增");
        checkChanges(changes);
        validateCreateChanges(changes);

        Application app = new Application();
        app.setApplicantId(user.getUserId());
        app.setApplicant(user.getUsername());
        app.setApplicationType(ApplicationType.EXPERT_CREATE.name());
        app.setStatus(STATUS_PENDING);
        String expertName = String.valueOf(changes.get("姓名")).trim();
        // 模板校验（R-B4）：传了 templateId 时字段集合必须 ⊆ 模板字段（且模板存在并启用）
        if (templateId != null) {
            approvalTemplateService.checkTemplateForSubmit(templateId, changes.keySet());
        }
        // 新增申请无「变更前」，oldData 置空
        app.setBizData(writeBizData(null, expertName, changes, reason, templateId, null));
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
    private void checkSubmitEligible(AuthUser user, String action) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.hasPermission("expert:edit")) {
            throw new BusinessException("您已拥有专家编辑权限，请直接在专家管理中" + action + "专家，无需提交审批");
        }
        ApprovalConfig config = getConfig();
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
            }
        }
    }

    /**
     * 新增专家必填校验：姓名、单位、科室、职称、研究方向、擅长领域、联系方式必填，
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
     * 我的申请分页列表（新接口 my-applications；旧 my-requests 同实现兼容保留）：
     * 按 applicant_id / applicant 双条件过滤本人（兼容存量 applicant_id 为空的迁移行），
     * 可按状态过滤（status 为空查全部），附带 biz_data 解析展示字段与进度记录。
     */
    public Page<Application> myApplications(long page, long size, Integer status, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<Application>()
                .and(w -> w.eq(Application::getApplicantId, user.getUserId())
                        .or().eq(Application::getApplicant, user.getUsername()));
        if (status != null) {
            wrapper.eq(Application::getStatus, status);
        }
        wrapper.orderByDesc(Application::getCreateTime);
        Page<Application> result = this.page(new Page<>(page, size), wrapper);
        for (Application r : result.getRecords()) {
            fillView(r, isFullSensitive(user));
        }
        return result;
    }

    // ==================== 全部申请（审批管理） ====================

    /**
     * 全部申请单分页：可按状态过滤，keyword 模糊匹配申请人 / 专家姓名（biz_data.expertName JSON 提取）。
     */
    public Page<Application> listRequests(Integer status, String keyword, long page, long size, AuthUser viewer) {
        return listRequests(status == null ? null : List.of(status), keyword, page, size, viewer);
    }

    /**
     * 全部申请单分页（多状态）：statuses 为空查全部；keyword 模糊匹配申请人 / 专家姓名。
     * 已处理列表（status=1,2,3）统一在此分页，避免前端分页语义不稳定。
     */
    public Page<Application> listRequests(List<Integer> statuses, String keyword, long page, long size, AuthUser viewer) {
        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(Application::getStatus, statuses);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Application::getApplicant, kw)
                    .or().apply("JSON_UNQUOTE(JSON_EXTRACT(biz_data, '$.expertName')) LIKE CONCAT('%', {0}, '%')", kw));
        }
        wrapper.orderByDesc(Application::getCreateTime);
        Page<Application> result = this.page(new Page<>(page, size), wrapper);
        for (Application r : result.getRecords()) {
            fillView(r, isFullSensitive(viewer));
        }
        return result;
    }

    // ==================== 审批（审批管理） ====================

    /**
     * 审批通过：按申请类型分支处理。
     * - EXPERT_CREATE 新增专家申请：先创建 Expert 再归档照片回填，最后回填 biz_data.expertId；
     * - EXPERT_MODIFY 修改申请：走原修改逻辑。
     */
    public Application approve(Long id, String comment, AuthUser user) {
        Application app = getPending(id);
        checkAssignee(app, user);
        Application result;
        if (isCreateRequest(app)) {
            result = approveCreate(app, comment, user);
        } else {
            result = approveModify(app, comment, user);
        }
        notifyApprovalResult(result, user);
        return result;
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

        // 2. 归档照片并回填
        Map<String, String> photoApplied = new LinkedHashMap<>();
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
            String newName = photoStorageService.moveTempPhoto(tempName, expert.getId(), photoTypeOf(key));
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
        return fillView(app, isFullSensitive(user));
    }

    /**
     * 审批通过（修改申请）：校验待审批状态，将白名单字段写入 expert_info（update_by=申请人），
     * 状态置为已通过，写 APPROVE 记录。
     */
    public Application approveModify(Application app, String comment, AuthUser user) {
        Map<String, Object> changes = readChanges(app);
        // 敏感字段格式兜底校验（身份证/银行卡/联系方式）
        validateSensitiveFields(changes);

        Expert existing = expertService.getById(app.getExpertId());
        Expert patch = new Expert();
        Map<String, String> oldPhotos = new HashMap<>();
        Map<String, String> photoApplied = new LinkedHashMap<>();
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
            String newName = photoStorageService.moveTempPhoto(tempName, app.getExpertId(), photoTypeOf(key));
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
        // 无快照的历史单维持原有物理删除行为（仅照片键有变更时删）
        if (readOldData(app).isEmpty()) {
            for (String oldName : oldPhotos.values()) {
                if (StringUtils.hasText(oldName)) {
                    photoStorageService.deletePhoto(oldName);
                }
            }
        }
        // 防御性清理临时文件（moveTempPhoto 已归档并删除，此处兜底）
        cleanTempPhotos(app);

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
        return fillView(app, isFullSensitive(user));
    }

    /**
     * 审批驳回：校验待审批状态与意见必填，状态置为已驳回，写 REJECT 记录。
     */
    public Application reject(Long id, String comment, AuthUser user) {
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException("驳回意见不能为空");
        }
        Application app = getPending(id);
        checkAssignee(app, user);
        // 驳回：清理该申请关联的所有临时照片（与撤回共用 cleanTempPhotos）
        cleanTempPhotos(app);
        app.setStatus(STATUS_REJECTED);
        app.setApproveBy(user == null ? null : user.getUsername());
        app.setApproveComment(comment);
        app.setApproveTime(LocalDateTime.now());
        this.updateById(app);

        addRecord(app.getId(), "管理员驳回", "REJECT", user == null ? null : user.getUsername(), comment.trim());
        notifyApprovalResult(app, user);
        return fillView(app, isFullSensitive(user));
    }

    // ==================== 撤回申请（申请人） ====================

    /**
     * 撤回申请：仅申请人本人可撤，status=0 才可撤。
     * 撤回后状态置为已撤回(3)，清理该单全部临时照片，写 WITHDRAW 记录，通知审批人角色下用户。
     */
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
        if (!app.getApplicant().equals(user.getUsername())) {
            throw new BusinessException("仅申请人本人可撤回申请");
        }
        fillBiz(app);
        cleanTempPhotos(app);
        app.setStatus(STATUS_WITHDRAWN);
        this.updateById(app);

        addRecord(app.getId(), "申请人撤回", "WITHDRAW", user.getUsername(), "撤回本人提交的申请");
        notifyApprovalWithdrawn(app);
        return fillView(app, isFullSensitive(user));
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

    // ==================== 转办 / 加签（B2） ====================

    /**
     * 审批转办：当前审批人将待审批单移交指定用户。
     * - 仅 status=0 待审批可转；
     * - 当前用户须有 approval:audit，且满足 current_assignee 为空或等于本人；
     * - 目标用户必须存在且拥有 approval:audit 权限码；
     * - 转办后仅被转办人可审批（approve/reject 经 checkAssignee 校验）；
     * - 写 TRANSFER 记录并通知被转办人与申请人。
     */
    public Application transfer(Long id, String targetUser, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
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
        return fillView(app, isFullSensitive(user));
    }

    /**
     * 审批加签（或签语义）：当前审批人追加一个指定用户共同审批。
     * - 或签：原审批人与被加签人任一 approve 即通过、任一 reject 即驳回（不改变状态机）；
     * - 已加签不可重复加签；不能加签给自己；
     * - extra_approver 置为目标用户，写 ADD_APPROVER 记录并通知被加签人。
     */
    public Application addApprover(Long id, String targetUser, String comment, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
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
        return fillView(app, isFullSensitive(user));
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
     * 审批权校验（B2）：current_assignee 非空时仅被转办人本人可审批；为空维持现状（任何有审批权限的用户可审）。
     */
    private void checkAssignee(Application app, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        String assignee = app.getCurrentAssignee();
        if (StringUtils.hasText(assignee) && !assignee.equals(user.getUsername())) {
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
            ApprovalConfig config = getConfig();
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
            // 通知失败不影响主流程
        }
    }

    /**
     * 新申请待审通知（approval_todo）：通知审批人角色下全部用户。
     */
    private void notifyApprovalTodo(Application app) {
        String typeText = isCreateRequest(app) ? "新增专家申请" : "专家信息修改申请";
        notifyApproverRole(app, NotificationService.TYPE_APPROVAL_TODO,
                "新的" + typeText + "待审批",
                "申请人 " + app.getApplicant() + " 提交了新的申请，请及时处理");
    }

    /**
     * 撤回通知（approval_withdrawn）：通知审批人角色下全部用户「申请已撤回」。
     */
    private void notifyApprovalWithdrawn(Application app) {
        String typeText = isCreateRequest(app) ? "新增专家申请" : "专家信息修改申请";
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
            String typeText = isCreateRequest(app) ? "新增专家申请" : "专家信息修改申请";
            String operator = user == null ? "管理员" : user.getUsername();
            String content = operator + (approved ? "已通过您的" : "已驳回您的") + typeText;
            if (StringUtils.hasText(app.getApproveComment())) {
                content += "，意见：" + app.getApproveComment();
            }
            String jump = approved ? "/profile/my-approvals?status=1" : "/profile/my-approvals?status=2";
            notificationService.notify(applicantId, NotificationService.TYPE_APPROVAL_RESULT,
                    (approved ? "审批通过：" : "审批驳回：") + typeText, content, jump);
        } catch (Exception e) {
            // 通知失败不影响主流程
        }
    }

    /**
     * 转办通知：通知被转办人新增待办（approval_transferred）；申请人可收到转办提醒。
     */
    private void notifyTransferred(Application app, String targetUser, AuthUser operator) {
        try {
            String typeText = isCreateRequest(app) ? "新增专家申请" : "专家信息修改申请";
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
                        "/profile/my-approvals");
            }
        } catch (Exception e) {
            // 通知失败不影响主流程
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
            String typeText = isCreateRequest(app) ? "新增专家申请" : "专家信息修改申请";
            notificationService.notify(targetId, NotificationService.TYPE_APPROVAL_ASSIGNED,
                    "加签待办：" + typeText,
                    "「" + app.getExpertName() + "」的" + typeText + "已加签您共同审批（任一人审批即生效），请及时处理",
                    MANAGE_PATH);
        } catch (Exception e) {
            // 通知失败不影响主流程
        }
    }

    // ==================== 私有方法 ====================


    private ApprovalConfig findConfig() {
        return approvalConfigMapper.selectOne(
                new LambdaQueryWrapper<ApprovalConfig>().eq(ApprovalConfig::getFlowKey, FLOW_KEY));
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
     * 填充展示字段：biz_data 解析（expertId/expertName/reason/templateId/changes/oldData/photoChanges）、
     * requestType/requestTypeText、statusText、records。
     * fullSensitive=false 时 oldData 的身份证号/银行卡号按前3后4脱敏（与 /api/experts/{id} 非管理员视图一致，不扩大可见范围）。
     */
    private Application fillView(Application app, boolean fullSensitive) {
        fillBiz(app);
        Map<String, Object> biz = readBiz(app);
        Map<String, Object> rawChanges = asStringKeyMap(biz.get("changes"));
        app.setChanges(friendlyChanges(rawChanges));
        app.setPhotoChanges(photoChanges(rawChanges));
        app.setOldData(maskedOldData(asStringKeyMap(biz.get("oldData")), fullSensitive));
        ApplicationType type = ApplicationType.parse(app.getApplicationType());
        app.setRequestType(type.requestType());
        app.setRequestTypeText(type.text());
        app.setStatusText(statusText(app.getStatus()));
        app.setRecords(recordsOf(app.getId()));
        return app;
    }

    /** 查看者是否可见敏感原文（管理员全量，其余脱敏；与 /api/experts/{id}?sensitive=1 规则一致）。 */
    private boolean isFullSensitive(AuthUser user) {
        return user != null && user.isAdmin();
    }

    /**
     * 仅解析 biz_data 公共字段（不加进度记录等重查询），供审批/撤回等内部链路取 expertId/expertName。
     */
    private void fillBiz(Application app) {
        Map<String, Object> biz = readBiz(app);
        app.setExpertId(ParamUtils.toLong(biz.get("expertId")));
        Object expertName = biz.get("expertName");
        app.setExpertName(expertName == null ? null : String.valueOf(expertName));
        app.setTemplateId(ParamUtils.toLong(biz.get("templateId")));
        Object reason = biz.get("reason");
        app.setReason(reason == null ? null : String.valueOf(reason));
    }


    /**
     * 敏感字段格式兜底校验（与 ExpertService.validateRequired 对齐）：
     * 仅对本次变更涉及且值非空的身份证号/银行卡号/联系方式做格式校验。
     */
    private void validateSensitiveFields(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        for (String key : SENSITIVE_FIELDS) {
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
     * 旧值快照脱敏视图：非管理员查看时身份证号/银行卡号前3后4（复用 ExpertService.maskSensitive），
     * 其余字段与照片文件名不脱敏。
     */
    private Map<String, Object> maskedOldData(Map<String, Object> oldData, boolean fullSensitive) {
        if (oldData.isEmpty() || fullSensitive) {
            return oldData;
        }
        Map<String, Object> result = new LinkedHashMap<>(oldData);
        for (String key : List.of("身份证号", "银行卡号")) {
            Object value = result.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                result.put(key, ExpertService.maskSensitive(String.valueOf(value)));
            }
        }
        return result;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case STATUS_APPROVED -> "已通过";
            case STATUS_REJECTED -> "已驳回";
            case STATUS_WITHDRAWN -> "已撤回";
            default -> "待审批";
        };
    }

    /**
     * 把白名单字段的中文键 -> 新值映射写入 Expert 实体（仅设置存在的键）。
     */
    private void applyChanges(Expert patch, Map<String, Object> changes) {
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            if (!WHITELIST_FIELDS.contains(key) || PHOTO_FIELDS.contains(key)) {
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
                                String reason, Long templateId, Map<String, Object> oldData) {
        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("expertId", expertId);
        biz.put("expertName", expertName);
        biz.put("changes", changes);
        biz.put("reason", reason);
        biz.put("templateId", templateId);
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

    private Map<String, Object> readBiz(Application app) {
        if (!StringUtils.hasText(app.getBizData())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(app.getBizData(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
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
