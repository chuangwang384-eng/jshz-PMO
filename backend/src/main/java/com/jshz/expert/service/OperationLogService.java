package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.entity.OperationLog;
import com.jshz.expert.mapper.OperationLogMapper;
import com.jshz.expert.common.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户敏感操作日志服务（用户操作日志改版）。
 * 职责：
 * 1. 统一日志写入入口 record()：各类敏感操作（下载/导出、专家/文档写操作、登录/登出/改密/角色权限变更）埋点调用；
 * 2. 分页查询（管理员用户操作日志页面）：用户名模糊 / 操作类型 / 时间范围过滤。
 * 写入失败仅记录 warn 日志，不影响主业务流程（try-catch 降级）。
 */
@Slf4j
@Service
public class OperationLogService extends ServiceImpl<OperationLogMapper, OperationLog> {

    // ---------------- 操作类型枚举（action_type） ----------------

    /** 数据导出 / 下载（含历史 download_log 迁移行） */
    public static final String ACTION_DOWNLOAD = "DOWNLOAD";
    /** 登录 */
    public static final String ACTION_LOGIN = "LOGIN";
    /** 登出 */
    public static final String ACTION_LOGOUT = "LOGOUT";
    /** 修改本人密码 */
    public static final String ACTION_CHANGE_PASSWORD = "CHANGE_PASSWORD";
    /** 管理员重置用户密码 */
    public static final String ACTION_RESET_PASSWORD = "RESET_PASSWORD";
    /** 新增用户 */
    public static final String ACTION_CREATE_USER = "CREATE_USER";
    /** 修改用户信息 */
    public static final String ACTION_UPDATE_USER = "UPDATE_USER";
    /** 删除用户 */
    public static final String ACTION_DELETE_USER = "DELETE_USER";
    /** 为用户分配角色 */
    public static final String ACTION_ASSIGN_ROLES = "ASSIGN_ROLES";
    /** 调整角色权限绑定 */
    public static final String ACTION_ROLE_PERMISSION = "ROLE_PERMISSION";
    /** 新增专家 */
    public static final String ACTION_CREATE_EXPERT = "CREATE_EXPERT";
    /** 修改专家 */
    public static final String ACTION_UPDATE_EXPERT = "UPDATE_EXPERT";
    /** 删除专家 */
    public static final String ACTION_DELETE_EXPERT = "DELETE_EXPERT";
    /** 导入专家 Excel */
    public static final String ACTION_IMPORT_EXPERT = "IMPORT_EXPERT";
    /** 上传知识文档 */
    public static final String ACTION_CREATE_DOC = "CREATE_DOC";
    /** 更新知识文档（新版本） */
    public static final String ACTION_UPDATE_DOC = "UPDATE_DOC";
    /** 下架知识文档 */
    public static final String ACTION_OFF_SHELF_DOC = "OFF_SHELF_DOC";
    /** 永久删除知识文档 */
    public static final String ACTION_DELETE_DOC = "DELETE_DOC";
    /** 上架知识文档 */
    public static final String ACTION_RELIST_DOC = "RELIST_DOC";
    /** 触发企微通讯录同步（手动/定时/DryRun） */
    public static final String ACTION_WECOM_SYNC = "WECOM_SYNC";
    /** 企微成员人工指认绑定 */
    public static final String ACTION_WECOM_BIND = "WECOM_BIND";
    // ---------------- 项目管理模块（target_type=PROJECT，时间线同源） ----------------
    /** 新建项目 */
    public static final String ACTION_CREATE_PROJECT = "CREATE_PROJECT";
    /** 编辑项目 */
    public static final String ACTION_UPDATE_PROJECT = "UPDATE_PROJECT";
    /** 软删项目 */
    public static final String ACTION_DELETE_PROJECT = "DELETE_PROJECT";
    /** 取消项目（必填原因） */
    public static final String ACTION_CANCEL_PROJECT = "CANCEL_PROJECT";
    /** 阶段推进/回退 */
    public static final String ACTION_CHANGE_PHASE = "CHANGE_PHASE";
    /** 调派专家（项目关联） */
    public static final String ACTION_ASSIGN_EXPERT = "ASSIGN_EXPERT";
    /** 移出专家（项目关联） */
    public static final String ACTION_REMOVE_EXPERT = "REMOVE_EXPERT";
    /** 关联供应商 */
    public static final String ACTION_ASSIGN_SUPPLIER = "ASSIGN_SUPPLIER";
    /** 移出供应商 */
    public static final String ACTION_REMOVE_SUPPLIER = "REMOVE_SUPPLIER";
    /** 上传分阶段资料 */
    public static final String ACTION_UPLOAD_DOC = "UPLOAD_DOC";
    /** 登记专家合同 */
    public static final String ACTION_CREATE_CONTRACT = "CREATE_CONTRACT";
    /** 更新合同（含上传合同文件/修改金额等） */
    public static final String ACTION_UPDATE_CONTRACT = "UPDATE_CONTRACT";
    /** 合同敏感字段同步回写专家库（身份证/银行卡） */
    public static final String ACTION_SYNC_EXPERT_SENSITIVE = "SYNC_EXPERT_SENSITIVE";
    /** 新建模板类型 */
    public static final String ACTION_CREATE_TEMPLATE = "CREATE_TEMPLATE";
    /** 修改模板类型 */
    public static final String ACTION_UPDATE_TEMPLATE = "UPDATE_TEMPLATE";
    /** 删除模板类型 */
    public static final String ACTION_DELETE_TEMPLATE = "DELETE_TEMPLATE";
    /** 登记项目预算 */
    public static final String ACTION_REGISTER_BUDGET = "REGISTER_BUDGET";
    /** 合同发起审批（复用 approval 通道） */
    public static final String ACTION_SUBMIT_CONTRACT_APPROVAL = "SUBMIT_CONTRACT_APPROVAL";
    /** 系统公告：增删改/上下架/置顶 */
    public static final String ACTION_CREATE_ANNOUNCEMENT = "CREATE_ANNOUNCEMENT";
    public static final String ACTION_UPDATE_ANNOUNCEMENT = "UPDATE_ANNOUNCEMENT";
    public static final String ACTION_DELETE_ANNOUNCEMENT = "DELETE_ANNOUNCEMENT";
    public static final String ACTION_ONLINE_ANNOUNCEMENT = "ONLINE_ANNOUNCEMENT";
    public static final String ACTION_OFFLINE_ANNOUNCEMENT = "OFFLINE_ANNOUNCEMENT";
    public static final String ACTION_TOP_ANNOUNCEMENT = "TOP_ANNOUNCEMENT";

    // ---------------- 供应商模块（target_type=SUPPLIER，AOP 注解埋点） ----------------
    /** 新增供应商 */
    public static final String ACTION_SUPPLIER_CREATE = "SUPPLIER_CREATE";
    /** 修改供应商 */
    public static final String ACTION_SUPPLIER_UPDATE = "SUPPLIER_UPDATE";
    /** 删除供应商 */
    public static final String ACTION_SUPPLIER_DELETE = "SUPPLIER_DELETE";
    /** 保存行价（全量替换） */
    public static final String ACTION_SAVE_SUPPLIER_PRICE = "SAVE_SUPPLIER_PRICE";
    /** 建立酒店-打印店绑定 */
    public static final String ACTION_BIND_SUPPLIER = "BIND_SUPPLIER";
    /** 解绑酒店-打印店绑定 */
    public static final String ACTION_UNBIND_SUPPLIER = "UNBIND_SUPPLIER";

    // ---------------- 客户模块（target_type=CUSTOMER） ----------------
    /** 下载客户附件 */
    public static final String ACTION_DOWNLOAD_CUSTOMER_ATTACH = "DOWNLOAD_CUSTOMER_ATTACH";

    /**
     * 统一写入一条操作日志；任何异常仅记 warn，不向上抛（日志失败不影响主业务）。
     *
     * @param userId     操作人 id（可空）
     * @param username   操作人用户名（可空）
     * @param actionType 操作类型（OperationLogService.ACTION_* 常量）
     * @param actionDesc 操作描述（含对象中文名）
     * @param targetType 对象类型（USER/ROLE/EXPERT/DOC，可空）
     * @param targetId   对象标识（id 或用户名，可空）
     * @param request    当前请求（取 IP，可空）
     */
    public void record(Long userId, String username, String actionType, String actionDesc,
                       String targetType, Object targetId, HttpServletRequest request) {
        try {
            OperationLog entry = new OperationLog();
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setActionType(actionType);
            entry.setActionDesc(actionDesc);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId == null ? null : String.valueOf(targetId));
            entry.setIp(IpUtils.clientIp(request));
            entry.setCreateTime(LocalDateTime.now());
            this.save(entry);
        } catch (Exception e) {
            log.warn("操作日志写入失败（不影响主业务）: actionType={}, username={}, err={}",
                    actionType, username, e.getMessage());
        }
    }

    /**
     * 操作日志分页查询：用户名模糊 / 操作类型精确 / 排除操作类型 / 时间范围（闭区间）过滤，按时间倒序。
     *
     * @param excludeActionTypes 需排除的操作类型（如 LOGIN/LOGOUT），为空不排除
     */
    public Page<OperationLog> pageQuery(String username, String actionType,
                                        LocalDateTime startTime, LocalDateTime endTime,
                                        List<String> excludeActionTypes,
                                        int page, int size) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(OperationLog::getUsername, username.trim());
        }
        if (StringUtils.hasText(actionType)) {
            wrapper.eq(OperationLog::getActionType, actionType.trim());
        }
        if (excludeActionTypes != null && !excludeActionTypes.isEmpty()) {
            wrapper.notIn(OperationLog::getActionType, excludeActionTypes);
        }
        if (startTime != null) {
            wrapper.ge(OperationLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(OperationLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return this.page(new Page<>(page, size), wrapper);
    }

}
