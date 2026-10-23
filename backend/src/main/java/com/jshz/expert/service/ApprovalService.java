package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ApprovalConfig;
import com.jshz.expert.entity.ApprovalRecord;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.ExpertChangeRequest;
import com.jshz.expert.entity.Role;
import com.jshz.expert.mapper.ApprovalConfigMapper;
import com.jshz.expert.mapper.ApprovalRecordMapper;
import com.jshz.expert.mapper.ExpertChangeRequestMapper;
import com.jshz.expert.mapper.RoleMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 专家信息修改审批流服务（需求5）。
 * 员工提交变更申请 -> 管理员审批通过/驳回；审批通过时把白名单字段写入 expert_info。
 */
@Service
@RequiredArgsConstructor
public class ApprovalService extends ServiceImpl<ExpertChangeRequestMapper, ExpertChangeRequest> {

    /** 审批流流程键 */
    public static final String FLOW_KEY = "EXPERT_MODIFY";

    /** 字段白名单（排除照片类、id、时间类、下载次数） */
    public static final List<String> WHITELIST_FIELDS = List.of(
            "姓名", "单位", "科室", "职称", "研究方向", "擅长领域",
            "联系方式", "简介", "身份证号", "银行卡号", "银行卡开户行");

    /** 照片字段白名单（需求3：照片变更随审批流一并生效） */
    public static final List<String> PHOTO_FIELDS = List.of("照片", "身份证正面", "身份证反面");

    /** 临时照片值前缀 */
    private static final String TMP_PREFIX = "TMP:";

    /** 临时照片值格式：TMP:文件名 */
    private static final Pattern TMP_PATTERN = Pattern.compile("^TMP:[A-Za-z0-9._-]+$");

    /** 状态：0 待审批 / 1 已通过 / 2 已驳回 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    /** 申请类型：0=修改申请 / 1=新增专家申请 */
    private static final int REQUEST_TYPE_MODIFY = 0;
    private static final int REQUEST_TYPE_CREATE = 1;

    /** 新增专家必填文本字段（审批通过后写入 expert_info） */
    private static final List<String> CREATE_REQUIRED_FIELDS = List.of(
            "姓名", "单位", "科室", "职称", "研究方向", "擅长领域", "联系方式");

    /** 姓名正则：2-20 个汉字，可含间隔号 */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,20}$");

    /** 身份证号正则：18 位，末位可为 X（与 ExpertService.validateRequired 对齐） */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");

    /** 银行卡号正则：13-19 位数字（与 ExpertService.validateRequired 对齐） */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{13,19}$");

    /** 联系方式正则：11 位手机号或邮箱（与 ExpertService.validateRequired 对齐） */
    private static final Pattern CONTACT_PATTERN = Pattern.compile("^1\\d{10}$|^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    /** 敏感格式字段（仅在本次变更涉及且值非空时校验，与 ExpertService.validateRequired 的 hasText 逻辑对齐） */
    private static final List<String> SENSITIVE_FIELDS = List.of("身份证号", "银行卡号", "联系方式");

    private final ApprovalConfigMapper approvalConfigMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final ExpertService expertService;
    private final PhotoStorageService photoStorageService;
    private final RoleMapper roleMapper;
    private final ObjectMapper objectMapper;

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
     * 提交修改申请：
     * - 拥有 expert:edit 权限的用户不允许走审批流（提示直接编辑）；
     * - 审批流未启用时拒绝；
     * - changes 非空且字段必须在白名单；
     * - 生成申请单(status=0)并写一条 SUBMIT 记录。
     */
    public ExpertChangeRequest submitRequest(Long expertId, Map<String, Object> changes, String reason, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.hasPermission("expert:edit")) {
            throw new BusinessException("您已拥有专家编辑权限，请直接在专家管理中编辑专家信息，无需提交审批");
        }
        ApprovalConfig config = getConfig();
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("审批流未启用，暂不支持提交修改申请");
        }
        if (expertId == null) {
            throw new BusinessException("专家ID不能为空");
        }
        Expert expert = expertService.getById(expertId);
        if (expert == null) {
            throw new BusinessException("专家不存在");
        }
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
        // 敏感字段格式兜底校验（身份证/银行卡/联系方式，与 ExpertService.validateRequired 对齐）
        validateSensitiveFields(changes);

        ExpertChangeRequest request = new ExpertChangeRequest();
        request.setExpertId(expertId);
        request.setExpertName(expert.getName());
        request.setApplicant(user.getUsername());
        request.setChangeData(writeJson(changes));
        request.setReason(reason);
        request.setStatus(STATUS_PENDING);
        request.setCreateTime(LocalDateTime.now());
        this.save(request);

        addRecord(request.getId(), "提交修改申请", "SUBMIT", user.getUsername(),
                StringUtils.hasText(reason) ? reason : "提交专家信息修改申请");
        return request;
    }

    /**
     * 提交新增专家申请（需求4）：
     * - 校验与 submitRequest 一致（管理员拒绝、审批流启用、changes 非空、字段白名单、照片 TMP: 与临时文件存在）；
     * - 额外校验新增必填项（姓名、单位等，姓名须满足 ^[一-龥·]{2,20}$）；
     * - 生成 request_type=1 的申请单（expertId 为空，expertName 取 changes 里的姓名）。
     */
    public ExpertChangeRequest submitCreateRequest(Map<String, Object> changes, String reason, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.hasPermission("expert:edit")) {
            throw new BusinessException("您已拥有专家编辑权限，请直接在专家管理中新增专家，无需提交审批");
        }
        ApprovalConfig config = getConfig();
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("审批流未启用，暂不支持提交新增专家申请");
        }
        if (changes == null || changes.isEmpty()) {
            throw new BusinessException("变更内容不能为空");
        }
        for (String key : changes.keySet()) {
            boolean textField = WHITELIST_FIELDS.contains(key);
            boolean photoField = PHOTO_FIELDS.contains(key);
            if (!textField && !photoField) {
                throw new BusinessException("字段「" + key + "」不在可新增白名单内");
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
        validateCreateChanges(changes);

        ExpertChangeRequest request = new ExpertChangeRequest();
        request.setRequestType(REQUEST_TYPE_CREATE);
        request.setExpertId(null);
        request.setExpertName(String.valueOf(changes.get("姓名")).trim());
        request.setApplicant(user.getUsername());
        request.setChangeData(writeJson(changes));
        request.setReason(reason);
        request.setStatus(STATUS_PENDING);
        request.setCreateTime(LocalDateTime.now());
        this.save(request);

        addRecord(request.getId(), "提交新增专家申请", "SUBMIT", user.getUsername(),
                StringUtils.hasText(reason) ? reason : "提交新增专家申请");
        return request;
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
        // 敏感字段格式兜底校验（身份证/银行卡/联系方式，与 ExpertService.validateRequired 对齐）
        validateSensitiveFields(changes);
    }

    // ==================== 我的申请（员工） ====================

    /**
     * 我的申请分页列表：只查本人，附带变更内容、状态文本与审批流进度记录。
     */
    public Page<ExpertChangeRequest> myRequests(long page, long size, AuthUser user) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        LambdaQueryWrapper<ExpertChangeRequest> wrapper = new LambdaQueryWrapper<ExpertChangeRequest>()
                .eq(ExpertChangeRequest::getApplicant, user.getUsername())
                .orderByDesc(ExpertChangeRequest::getCreateTime);
        Page<ExpertChangeRequest> result = this.page(new Page<>(page, size), wrapper);
        for (ExpertChangeRequest r : result.getRecords()) {
            fillView(r);
        }
        return result;
    }

    // ==================== 全部申请（管理员） ====================

    /**
     * 全部申请单分页：可按状态过滤，keyword 模糊匹配申请人 / 专家姓名。
     */
    public Page<ExpertChangeRequest> listRequests(Integer status, String keyword, long page, long size) {
        return listRequests(status == null ? null : List.of(status), keyword, page, size);
    }

    /**
     * 全部申请单分页（多状态）：statuses 为空查全部；keyword 模糊匹配申请人 / 专家姓名。
     * 已处理列表（status=1,2）统一在此分页，避免前端分页语义不稳定。
     */
    public Page<ExpertChangeRequest> listRequests(List<Integer> statuses, String keyword, long page, long size) {
        LambdaQueryWrapper<ExpertChangeRequest> wrapper = new LambdaQueryWrapper<>();
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(ExpertChangeRequest::getStatus, statuses);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ExpertChangeRequest::getApplicant, kw)
                    .or().like(ExpertChangeRequest::getExpertName, kw));
        }
        wrapper.orderByDesc(ExpertChangeRequest::getCreateTime);
        Page<ExpertChangeRequest> result = this.page(new Page<>(page, size), wrapper);
        for (ExpertChangeRequest r : result.getRecords()) {
            fillView(r);
        }
        return result;
    }

    // ==================== 审批（管理员） ====================

    /**
     * 审批通过：按申请类型分支处理（需求4）。
     * - request_type=1 新增专家申请：先创建 Expert 再归档照片回填，最后回填申请单 expertId；
     * - request_type=0 修改申请：走原修改逻辑。
     */
    public ExpertChangeRequest approve(Long id, String comment, AuthUser user) {
        ExpertChangeRequest request = getPending(id);
        if (isCreateRequest(request)) {
            return approveCreate(request, comment, user);
        }
        return approveModify(request, comment, user);
    }

    private boolean isCreateRequest(ExpertChangeRequest request) {
        return request.getRequestType() != null && request.getRequestType() == REQUEST_TYPE_CREATE;
    }

    /**
     * 审批通过（新增专家申请，request_type=1）：
     * 先从 changes 提取文本字段创建 Expert（update_by=审批人），
     * 再归档照片回填 photo/idCardFront/idCardBack，最后回填申请单 expertId。
     */
    private ExpertChangeRequest approveCreate(ExpertChangeRequest request, String comment, AuthUser user) {
        Map<String, Object> changes = readChanges(request);
        // 防御性校验：提交后数据不应变化，审批时兜底校验一次
        validateCreateChanges(changes);

        // 1. 创建专家（仅文本字段，update_by=审批人）
        Expert expert = new Expert();
        applyChanges(expert, changes);
        expertService.create(expert, user);

        // 2. 归档照片并回填
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
            switch (key) {
                case "照片" -> expert.setPhoto(newName);
                case "身份证正面" -> expert.setIdCardFront(newName);
                default -> expert.setIdCardBack(newName);
            }
        }
        // 方案A：审批通过时 update_by 记申请人（发起新增的人），而非审批人
        expert.setUpdateBy(request.getApplicant());
        expertService.update(expert.getId(), expert, null);

        // 3. 回填申请单 expertId
        request.setExpertId(expert.getId());
        request.setExpertName(expert.getName());
        request.setStatus(STATUS_APPROVED);
        request.setApproveBy(user == null ? null : user.getUsername());
        request.setApproveComment(comment);
        request.setApproveTime(LocalDateTime.now());
        this.updateById(request);

        addRecord(request.getId(), "管理员审批通过", "APPROVE", user == null ? null : user.getUsername(),
                StringUtils.hasText(comment) ? comment : "同意新增专家");
        return fillView(request);
    }

    /**
     * 审批通过（修改申请，request_type=0）：校验待审批状态，将白名单字段写入 expert_info（update_by=审批人），
     * 状态置为已通过，写 APPROVE 记录。
     */
    public ExpertChangeRequest approveModify(ExpertChangeRequest request, String comment, AuthUser user) {
        Map<String, Object> changes = readChanges(request);
        // 敏感字段格式兜底校验（身份证/银行卡/联系方式，与 ExpertService.validateRequired 对齐）
        validateSensitiveFields(changes);

        Expert existing = expertService.getById(request.getExpertId());
        Expert patch = new Expert();
        Map<String, String> oldPhotos = new HashMap<>();
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
            String newName = photoStorageService.moveTempPhoto(tempName, request.getExpertId(), photoTypeOf(key));
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
        patch.setUpdateBy(request.getApplicant());
        expertService.update(request.getExpertId(), patch, null);

        // 照片已生效：物理删除旧照片（仅照片键有变更时）
        for (String oldName : oldPhotos.values()) {
            if (StringUtils.hasText(oldName)) {
                photoStorageService.deletePhoto(oldName);
            }
        }
        // 防御性清理临时文件（moveTempPhoto 已归档并删除，此处兜底）
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            if (PHOTO_FIELDS.contains(entry.getKey()) && entry.getValue() != null
                    && String.valueOf(entry.getValue()).startsWith(TMP_PREFIX)) {
                photoStorageService.deleteTempPhoto(
                        String.valueOf(entry.getValue()).substring(TMP_PREFIX.length()));
            }
        }

        request.setStatus(STATUS_APPROVED);
        request.setApproveBy(user == null ? null : user.getUsername());
        request.setApproveComment(comment);
        request.setApproveTime(LocalDateTime.now());
        this.updateById(request);

        addRecord(request.getId(), "管理员审批通过", "APPROVE", user == null ? null : user.getUsername(),
                StringUtils.hasText(comment) ? comment : "同意修改");
        return fillView(request);
    }

    /**
     * 审批驳回：校验待审批状态与意见必填，状态置为已驳回，写 REJECT 记录。
     */
    public ExpertChangeRequest reject(Long id, String comment, AuthUser user) {
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException("驳回意见不能为空");
        }
        ExpertChangeRequest request = getPending(id);
        // 驳回：清理该申请关联的所有临时照片
        Map<String, Object> changes = readChanges(request);
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            if (PHOTO_FIELDS.contains(entry.getKey()) && entry.getValue() != null
                    && String.valueOf(entry.getValue()).startsWith(TMP_PREFIX)) {
                photoStorageService.deleteTempPhoto(
                        String.valueOf(entry.getValue()).substring(TMP_PREFIX.length()));
            }
        }
        request.setStatus(STATUS_REJECTED);
        request.setApproveBy(user == null ? null : user.getUsername());
        request.setApproveComment(comment);
        request.setApproveTime(LocalDateTime.now());
        this.updateById(request);

        addRecord(request.getId(), "管理员驳回", "REJECT", user == null ? null : user.getUsername(), comment.trim());
        return fillView(request);
    }

    // ==================== 私有方法 ====================

    private ApprovalConfig findConfig() {
        return approvalConfigMapper.selectOne(
                new LambdaQueryWrapper<ApprovalConfig>().eq(ApprovalConfig::getFlowKey, FLOW_KEY));
    }

    /**
     * 取待审批申请单；不存在或已处理时报错。
     */
    private ExpertChangeRequest getPending(Long id) {
        if (id == null) {
            throw new BusinessException("申请单ID不能为空");
        }
        ExpertChangeRequest request = getById(id);
        if (request == null) {
            throw new BusinessException("申请单不存在");
        }
        if (request.getStatus() == null || request.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该申请单已处理，不能重复审批");
        }
        return request;
    }

    /**
     * 写一条审批流记录。
     */
    private void addRecord(Long requestId, String nodeName, String action, String operator, String comment) {
        ApprovalRecord record = new ApprovalRecord();
        record.setRequestId(requestId);
        record.setNodeName(nodeName);
        record.setAction(action);
        record.setOperator(operator);
        record.setComment(comment);
        record.setCreateTime(LocalDateTime.now());
        approvalRecordMapper.insert(record);
    }

    /**
     * 查询申请单全部进度记录（按时间升序）。
     */
    private List<ApprovalRecord> recordsOf(Long requestId) {
        return approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getRequestId, requestId)
                        .orderByAsc(ApprovalRecord::getCreateTime));
    }

    /**
     * 填充展示字段：changes（解析 JSON）、statusText、requestTypeText、records。
     */
    private ExpertChangeRequest fillView(ExpertChangeRequest request) {
        request.setChanges(friendlyChanges(readChanges(request)));
        request.setStatusText(statusText(request.getStatus()));
        request.setRequestTypeText(requestTypeText(request.getRequestType()));
        request.setRecords(recordsOf(request.getId()));
        return request;
    }

    /**
     * 申请类型文本：1=新增专家，其余（0/空）=修改申请。
     */
    private String requestTypeText(Integer requestType) {
        if (requestType == null || requestType == REQUEST_TYPE_MODIFY) {
            return "修改申请";
        }
        return "新增专家";
    }

    /**
     * 敏感字段格式兜底校验（与 ExpertService.validateRequired 对齐）：
     * 仅对本次变更涉及且值非空的身份证号/银行卡号/联系方式做格式校验；
     * 变更中未涉及或值为空的字段不校验（保持与新增通道一致的 hasText 语义）。
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
     * 照片字段展示友好化：TMP: 临时文件名替换为「已更换新照片」，避免审批列表裸显临时文件名。
     */
    private Map<String, Object> friendlyChanges(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            return changes;
        }
        Map<String, Object> result = new HashMap<>(changes);
        for (String key : PHOTO_FIELDS) {
            Object value = result.get(key);
            if (value != null && String.valueOf(value).startsWith(TMP_PREFIX)) {
                result.put(key, "已更换新照片");
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

    private String writeJson(Map<String, Object> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception e) {
            throw new BusinessException("变更内容序列化失败: " + e.getMessage());
        }
    }

    private Map<String, Object> readChanges(ExpertChangeRequest request) {
        if (!StringUtils.hasText(request.getChangeData())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(request.getChangeData(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
