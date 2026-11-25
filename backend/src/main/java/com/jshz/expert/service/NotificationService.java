package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.entity.Notification;
import com.jshz.expert.entity.NotificationMute;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.NotificationMapper;
import com.jshz.expert.mapper.NotificationMuteMapper;
import com.jshz.expert.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 通用消息通知服务（消息通知中心）。
 * 提供事件埋点写入（notify）、按用户名反查接收人 id（userIdOf）与本人消息列表 / 未读数 / 已读标记查询。
 * 仅依赖 notification / sys_user 表，不依赖其他业务服务，供 ApplicationService / UserService /
 * KnowledgeDocumentService 等埋点调用，避免循环依赖。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService extends ServiceImpl<NotificationMapper, Notification> {

    private final SysUserMapper sysUserMapper;
    private final NotificationMuteMapper notificationMuteMapper;
    private final NotificationSseService notificationSseService;

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

    /** 企业合规：事项发布（推收藏用户+维护人） */
    public static final String TYPE_COMPLIANCE_PUBLISHED = "compliance_published";
    /** 企业合规：事项更新（推收藏用户+维护人） */
    public static final String TYPE_COMPLIANCE_UPDATED = "compliance_updated";
    /** 企业合规：到期提醒（个人提醒与全局责任人提醒共用） */
    public static final String TYPE_COMPLIANCE_DUE = "compliance_due";
    /** 企业合规：责任人指派通知 */
    public static final String TYPE_COMPLIANCE_ASSIGNED = "compliance_assigned";
    /** 企业合规：责任人全部完成提醒维护人评估 */
    public static final String TYPE_COMPLIANCE_DONE_NOTIFY = "compliance_done_notify";

    /**
     * 全部合法通知类型注册表（2026-11-25，需求 D）：偏好保存与列表筛选的 type 参数以此为白名单，
     * 非法值按 400 拒绝；前端 utils/notificationTypes.js 为展示层副本，新增类型须同一次提交成对修改。
     */
    public static final Set<String> KNOWN_TYPES = Set.of(
            TYPE_APPROVAL_RESULT, TYPE_APPROVAL_TODO, TYPE_APPROVAL_WITHDRAWN,
            TYPE_APPROVAL_TRANSFERRED, TYPE_APPROVAL_ASSIGNED,
            TYPE_ROLE_CHANGED, TYPE_PASSWORD_RESET,
            TYPE_DOC_OFF_SHELF, TYPE_DOC_DELETED, TYPE_DOC_UPDATED, TYPE_DOC_RELISTED,
            TYPE_COMPLIANCE_PUBLISHED, TYPE_COMPLIANCE_UPDATED, TYPE_COMPLIANCE_DUE,
            TYPE_COMPLIANCE_ASSIGNED, TYPE_COMPLIANCE_DONE_NOTIFY);

    /** 允许免打扰的类型：账号安全类（password_reset）强制送达，不可关闭。 */
    public static final Set<String> MUTABLE_TYPES = KNOWN_TYPES.stream()
            .filter(t -> !TYPE_PASSWORD_RESET.equals(t))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /**
     * 写入一条通知（未读）。写入前按接收人免打扰偏好过滤（需求 D，2026-11-25）：
     * 该用户对此 type 静音时不落库、不推送（password_reset 不可静音，MUTABLE_TYPES 保证偏好里不会有它）。
     * 写入成功后经 SSE 向接收人在线连接推刷新信号（需求 E；事务回滚导致的多余信号由前端拉取时空读消化）。
     */
    public void notify(Long userId, String type, String title, String content, String jumpPath) {
        if (userId == null) {
            return;
        }
        if (isMuted(userId, type)) {
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
        notificationSseService.pushRefresh(userId);
    }

    /** 该用户对该类型是否已开启免打扰；查询失败按未静音处理（宁可多送不可丢通知）。 */
    private boolean isMuted(Long userId, String type) {
        if (userId == null || type == null) {
            return false;
        }
        try {
            return notificationMuteMapper.selectCount(new LambdaQueryWrapper<NotificationMute>()
                    .eq(NotificationMute::getUserId, userId)
                    .eq(NotificationMute::getType, type)) > 0;
        } catch (Exception e) {
            log.warn("[消息中心] 免打扰偏好查询失败，按未静音处理：user={}，type={}", userId, type, e);
            return false;
        }
    }

    // ==================== 免打扰偏好（需求 D，2026-11-25） ====================

    /** 本人免打扰类型列表（按类型名排序，前端全量展示用）。 */
    public List<String> mutedTypes(Long userId) {
        List<NotificationMute> rows = notificationMuteMapper.selectList(
                new LambdaQueryWrapper<NotificationMute>()
                        .eq(NotificationMute::getUserId, userId)
                        .orderByAsc(NotificationMute::getType));
        List<String> result = new ArrayList<>();
        for (NotificationMute row : rows) {
            result.add(row.getType());
        }
        return result;
    }

    /**
     * 全量覆盖本人免打扰类型（参照 approval_config_user 的保存口径）：
     * 类型必须在 MUTABLE_TYPES 白名单内（未知类型与 password_reset 按 400 拒绝）。
     */
    public void saveMutedTypes(Long userId, List<String> types) {
        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        if (types != null) {
            for (String raw : types) {
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                String type = raw.trim();
                if (!MUTABLE_TYPES.contains(type)) {
                    throw new com.jshz.expert.common.BusinessException(400, "通知类型「" + type + "」不存在或不允许免打扰");
                }
                uniq.add(type);
            }
        }
        notificationMuteMapper.delete(new LambdaQueryWrapper<NotificationMute>()
                .eq(NotificationMute::getUserId, userId));
        for (String type : uniq) {
            NotificationMute row = new NotificationMute();
            row.setUserId(userId);
            row.setType(type);
            row.setCreateTime(LocalDateTime.now());
            notificationMuteMapper.insert(row);
        }
    }

    // ==================== 保留期清理（需求 A，2026-11-25） ====================

    /**
     * 物理删除「已读且创建时间早于保留期」的通知，返回删除条数；未读永不清理。
     * 供 {@code NotificationRetentionCleanTask} 每日调用；days 钳制到 ≥1，防止误传 0/负数全量清空。
     */
    public int deleteReadBefore(int days) {
        int safeDays = Math.max(1, days);
        LocalDateTime threshold = LocalDateTime.now().minusDays(safeDays);
        return baseMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getIsRead, 1)
                .lt(Notification::getCreateTime, threshold));
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
            if (!KNOWN_TYPES.contains(type.trim())) {
                throw new com.jshz.expert.common.BusinessException(400, "消息类型筛选值不合法");
            }
            wrapper.eq(Notification::getType, type.trim());
        }
        // 同秒消息按 id 兜底，避免翻页重复/遗漏（与「我的申请」列表口径一致）
        wrapper.orderByDesc(Notification::getCreateTime).orderByDesc(Notification::getId);
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
     * 单条标记已读（校验归属本人）。条件更新只写 is_read 列（2026-11-25，P3-13：
     * 此前 getById+updateById 回写全实体，并发下可能用旧值覆盖其他列）。
     */
    public void markRead(Long userId, Long id) {
        if (id == null) {
            return;
        }
        this.baseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
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
