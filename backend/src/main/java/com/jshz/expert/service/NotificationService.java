package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.entity.Notification;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.NotificationMapper;
import com.jshz.expert.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用消息通知服务（消息通知中心）。
 * 提供事件埋点写入（notify）、按用户名反查接收人 id（userIdOf）与本人消息列表 / 未读数 / 已读标记查询。
 * 仅依赖 notification / sys_user 表，不依赖其他业务服务，供 ApplicationService / UserService /
 * KnowledgeDocumentService 等埋点调用，避免循环依赖。
 */
@Service
@RequiredArgsConstructor
public class NotificationService extends ServiceImpl<NotificationMapper, Notification> {

    private final SysUserMapper sysUserMapper;

    /**
     * 按用户名反查用户ID（sys_user.username 唯一），供通知埋点定位接收人；查不到返回 null。
     */
    public Long userIdOf(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        SysUser u = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("LIMIT 1"));
        return u == null ? null : u.getId();
    }

    /** 事件类型：审批结果（我的审批） */
    public static final String TYPE_APPROVAL_RESULT = "approval_result";

    /** 事件类型：新申请待审（审批人） */
    public static final String TYPE_APPROVAL_TODO = "approval_todo";

    /** 事件类型：申请已撤回（审批人） */
    public static final String TYPE_APPROVAL_WITHDRAWN = "approval_withdrawn";

    /** 事件类型：审批转办（被转办人 + 申请人，B2） */
    public static final String TYPE_APPROVAL_TRANSFERRED = "approval_transferred";

    /** 事件类型：审批加签（被加签人，B2） */
    public static final String TYPE_APPROVAL_ASSIGNED = "approval_assigned";

    /** 事件类型：角色调整 */
    public static final String TYPE_ROLE_CHANGED = "role_changed";

    /** 事件类型：密码重置 */
    public static final String TYPE_PASSWORD_RESET = "password_reset";

    /** 事件类型：文档下架 */
    public static final String TYPE_DOC_OFF_SHELF = "doc_off_shelf";

    /** 事件类型：文档永久删除 */
    public static final String TYPE_DOC_DELETED = "doc_deleted";

    /** 事件类型：收藏文档已更新（C3：通知收藏者） */
    public static final String TYPE_DOC_UPDATED = "doc_updated";

    /** 事件类型：文档重新上架（维护人上架：通知收藏者） */
    public static final String TYPE_DOC_RELISTED = "doc_relisted";

    /**
     * 写入一条通知（未读）。
     */
    public void notify(Long userId, String type, String title, String content, String jumpPath) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setJumpPath(jumpPath);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        this.save(n);
    }

    /**
     * 本人消息分页列表（按时间倒序）。
     */
    public Page<Notification> pageByUser(Long userId, long page, long pageSize) {
        return pageByUser(userId, page, pageSize, null, null);
    }

    /**
     * 本人消息分页列表（消息中心）：isRead（0未读/1已读，空=全部）与 type（空=全部类型）可组合过滤。
     */
    public Page<Notification> pageByUser(Long userId, long page, long pageSize, Integer isRead, String type) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId);
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(Notification::getType, type.trim());
        }
        wrapper.orderByDesc(Notification::getCreateTime);
        return this.page(new Page<>(page, pageSize), wrapper);
    }

    /**
     * 本人未读消息数。
     */
    public long unreadCount(Long userId) {
        Long count = this.baseMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
        return count == null ? 0 : count;
    }

    /**
     * 单条标记已读（校验归属本人）。
     */
    public void markRead(Long userId, Long id) {
        if (id == null) {
            return;
        }
        Notification n = getById(id);
        if (n == null || n.getUserId() == null || !n.getUserId().equals(userId)) {
            return;
        }
        n.setIsRead(1);
        this.updateById(n);
    }

    /**
     * 全部标记已读，返回本次更新条数。
     */
    public int markAllRead(Long userId) {
        Notification patch = new Notification();
        patch.setIsRead(1);
        return this.baseMapper.update(patch,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
    }

    /**
     * 批量标记已读（仅本人消息，仅未读条目生效），返回实际置为已读的条数。
     */
    public int markReadBatch(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Notification patch = new Notification();
        patch.setIsRead(1);
        return baseMapper.update(patch,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .in(Notification::getId, ids)
                        .eq(Notification::getIsRead, 0));
    }

    /**
     * 单条删除（物理删除，仅本人消息；不存在或非本人时静默跳过，幂等）。
     */
    public void deleteOne(Long userId, Long id) {
        if (id == null) {
            return;
        }
        Notification n = getById(id);
        if (n == null || !userId.equals(n.getUserId())) {
            return;
        }
        this.removeById(id);
    }

    /**
     * 批量删除（物理删除，仅本人消息），返回实际删除条数。
     */
    public int deleteBatch(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return baseMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .in(Notification::getId, ids));
    }
}
