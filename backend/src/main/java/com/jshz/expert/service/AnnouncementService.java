package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.PageResult;
import com.jshz.expert.dto.AnnouncementForm;
import com.jshz.expert.entity.Announcement;
import com.jshz.expert.entity.AnnouncementRead;
import com.jshz.expert.entity.SysUser;
import com.jshz.expert.mapper.AnnouncementMapper;
import com.jshz.expert.mapper.AnnouncementReadMapper;
import com.jshz.expert.mapper.SysUserMapper;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.util.XssCleanUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统公告服务。
 * 写操作（创建/编辑/删除/上下架/置顶）业务层校验 announcement:manage；
 * 列表/详情/标记已读登录即可；标记已读幂等且不入操作日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService extends ServiceImpl<AnnouncementMapper, Announcement> {

    public static final String PERMISSION_MANAGE = "announcement:manage";
    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String TARGET_TYPE = "ANNOUNCEMENT";
    private static final int MAX_EFFECTIVE_LIMIT = 5;

    private final AnnouncementReadMapper announcementReadMapper;
    private final OperationLogService operationLogService;
    private final SysUserMapper sysUserMapper;

    /** 业务层管理权限校验 */
    public void requireManage(AuthUser user) {
        if (user == null || !user.hasPermission(PERMISSION_MANAGE)) {
            throw new BusinessException("无权限：需要 " + PERMISSION_MANAGE + " 权限");
        }
    }

    /**
     * 分页查询：管理视角返回全部状态并附已读进度；员工视角仅 ONLINE。
     */
    public PageResult<Announcement> pageQuery(AuthUser user, String keyword, String status, int page, int size) {
        boolean manage = user != null && user.hasPermission(PERMISSION_MANAGE);
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        if (!manage) {
            wrapper.eq(Announcement::getStatus, STATUS_ONLINE);
        } else if (StringUtils.hasText(status)) {
            wrapper.eq(Announcement::getStatus, status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Announcement::getTitle, keyword.trim())
                    .or().like(Announcement::getContent, keyword.trim()));
        }
        wrapper.orderByDesc(Announcement::getIsTop).orderByDesc(Announcement::getCreateTime);
        Page<Announcement> result = this.page(new Page<>(Math.max(page, 1), Math.max(size, 1)), wrapper);
        fillAux(result.getRecords(), user, manage);
        return PageResult.of(result);
    }

    /**
     * 首页/生效列表：status=ONLINE AND 未过期 AND effective_time<=now，置顶优先 + 发布时间倒序，最多 limit 条。
     * 仅用于卡片栏展示，content 置空以减小传输。
     */
    public List<Announcement> effectiveList(AuthUser user, int limit) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Announcement::getStatus, STATUS_ONLINE)
                .le(Announcement::getEffectiveTime, now)
                .and(w -> w.isNull(Announcement::getExpireTime)
                        .or().gt(Announcement::getExpireTime, now))
                .orderByDesc(Announcement::getIsTop)
                .orderByDesc(Announcement::getCreateTime)
                .last("LIMIT " + Math.min(Math.max(limit, 1), MAX_EFFECTIVE_LIMIT));
        List<Announcement> list = this.list(wrapper);
        boolean manage = user != null && user.hasPermission(PERMISSION_MANAGE);
        for (Announcement a : list) {
            a.setContent(null);
            a.setRead(isRead(user, a.getId()));
            a.setExpired(false);
            a.setCanManage(manage);
        }
        return list;
    }

    /**
     * 详情：员工视角仅允许查看 ONLINE；管理员额外附带已读进度。
     */
    public Announcement detail(AuthUser user, Long id) {
        Announcement a = this.getById(id);
        if (a == null) {
            throw new BusinessException("公告不存在或已删除");
        }
        boolean manage = user != null && user.hasPermission(PERMISSION_MANAGE);
        if (!manage && !STATUS_ONLINE.equals(a.getStatus())) {
            throw new BusinessException("公告未上架，暂不可查看");
        }
        a.setRead(isRead(user, a.getId()));
        a.setExpired(isExpired(a));
        a.setCanManage(manage);
        if (manage) {
            a.setReadCount(readCount(id));
            a.setTotalCount(totalUserCount());
        }
        return a;
    }

    /** 创建公告（需 announcement:manage），富文本入库前 XSS 白名单清洗 */
    public Announcement create(AuthUser user, AnnouncementForm form, HttpServletRequest request) {
        requireManage(user);
        Announcement a = new Announcement();
        applyForm(a, form);
        a.setCreatorId(user.getUserId());
        a.setCreatorName(user.getUsername());
        if (a.getStatus() == null) {
            a.setStatus(STATUS_ONLINE);
        }
        if (a.getIsTop() == null) {
            a.setIsTop(0);
        }
        if (a.getEffectiveTime() == null) {
            a.setEffectiveTime(LocalDateTime.now());
        }
        LocalDateTime now = LocalDateTime.now();
        a.setCreateTime(now);
        a.setUpdateTime(now);
        this.save(a);
        operationLogService.record(user.getUserId(), user.getUsername(),
                OperationLogService.ACTION_CREATE_ANNOUNCEMENT,
                "发布公告「" + a.getTitle() + "」", TARGET_TYPE, a.getId(), request);
        return a;
    }

    /** 编辑公告（需 announcement:manage），富文本重新清洗 */
    public Announcement update(AuthUser user, Long id, AnnouncementForm form, HttpServletRequest request) {
        requireManage(user);
        Announcement a = this.getById(id);
        if (a == null) {
            throw new BusinessException("公告不存在或已删除");
        }
        applyForm(a, form);
        a.setUpdateTime(LocalDateTime.now());
        this.updateById(a);
        operationLogService.record(user.getUserId(), user.getUsername(),
                OperationLogService.ACTION_UPDATE_ANNOUNCEMENT,
                "编辑公告「" + a.getTitle() + "」", TARGET_TYPE, a.getId(), request);
        return a;
    }

    /** 删除公告（需 announcement:manage），同步清理已读记录 */
    public void delete(AuthUser user, Long id, HttpServletRequest request) {
        requireManage(user);
        Announcement a = this.getById(id);
        if (a == null) {
            throw new BusinessException("公告不存在或已删除");
        }
        String title = a.getTitle();
        this.removeById(id);
        announcementReadMapper.delete(new LambdaQueryWrapper<AnnouncementRead>()
                .eq(AnnouncementRead::getAnnouncementId, id));
        operationLogService.record(user.getUserId(), user.getUsername(),
                OperationLogService.ACTION_DELETE_ANNOUNCEMENT,
                "删除公告「" + title + "」", TARGET_TYPE, id, request);
    }

    /** 上架/下架（需 announcement:manage） */
    public Announcement changeStatus(AuthUser user, Long id, String status, HttpServletRequest request) {
        requireManage(user);
        if (!STATUS_ONLINE.equals(status) && !STATUS_OFFLINE.equals(status)) {
            throw new BusinessException("status 参数仅支持 ONLINE / OFFLINE");
        }
        Announcement a = this.getById(id);
        if (a == null) {
            throw new BusinessException("公告不存在或已删除");
        }
        a.setStatus(status);
        a.setUpdateTime(LocalDateTime.now());
        this.updateById(a);
        boolean online = STATUS_ONLINE.equals(status);
        operationLogService.record(user.getUserId(), user.getUsername(),
                online ? OperationLogService.ACTION_ONLINE_ANNOUNCEMENT
                        : OperationLogService.ACTION_OFFLINE_ANNOUNCEMENT,
                (online ? "上架公告「" : "下架公告「") + a.getTitle() + "」", TARGET_TYPE, a.getId(), request);
        return a;
    }

    /** 置顶/取消置顶（需 announcement:manage） */
    public Announcement toggleTop(AuthUser user, Long id, boolean top, HttpServletRequest request) {
        requireManage(user);
        Announcement a = this.getById(id);
        if (a == null) {
            throw new BusinessException("公告不存在或已删除");
        }
        a.setIsTop(top ? 1 : 0);
        a.setUpdateTime(LocalDateTime.now());
        this.updateById(a);
        operationLogService.record(user.getUserId(), user.getUsername(),
                OperationLogService.ACTION_TOP_ANNOUNCEMENT,
                (top ? "置顶公告「" : "取消置顶公告「") + a.getTitle() + "」", TARGET_TYPE, a.getId(), request);
        return a;
    }

    /**
     * 标记已读：幂等（唯一联合键 + 先查后插双保险），不入操作日志。
     */
    public void markRead(AuthUser user, Long id) {
        if (user == null) {
            return;
        }
        Announcement a = this.getById(id);
        if (a == null) {
            throw new BusinessException("公告不存在或已删除");
        }
        Long exist = announcementReadMapper.selectCount(new LambdaQueryWrapper<AnnouncementRead>()
                .eq(AnnouncementRead::getAnnouncementId, id)
                .eq(AnnouncementRead::getUserId, user.getUserId()));
        if (exist != null && exist > 0) {
            return;
        }
        AnnouncementRead ar = new AnnouncementRead();
        ar.setAnnouncementId(id);
        ar.setUserId(user.getUserId());
        ar.setReadTime(LocalDateTime.now());
        announcementReadMapper.insert(ar);
    }

    /**
     * 已读进度与已读/未读名单（需 announcement:manage）。
     */
    public Map<String, Object> readStats(AuthUser user, Long id) {
        requireManage(user);
        Announcement a = this.getById(id);
        if (a == null) {
            throw new BusinessException("公告不存在或已删除");
        }
        long total = totalUserCount();
        long read = readCount(id);
        List<AnnouncementRead> readList = announcementReadMapper.selectList(
                new LambdaQueryWrapper<AnnouncementRead>()
                        .eq(AnnouncementRead::getAnnouncementId, id)
                        .orderByAsc(AnnouncementRead::getReadTime));
        Set<Long> readIds = new HashSet<>();
        List<Map<String, Object>> readUsers = new ArrayList<>();
        for (AnnouncementRead ar : readList) {
            SysUser u = sysUserMapper.selectById(ar.getUserId());
            if (u == null) {
                continue;
            }
            readIds.add(u.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", u.getId());
            item.put("username", u.getUsername());
            item.put("readTime", ar.getReadTime());
            readUsers.add(item);
        }
        List<Map<String, Object>> unreadUsers = new ArrayList<>();
        List<SysUser> all = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1).orderByAsc(SysUser::getUsername));
        for (SysUser u : all) {
            if (readIds.contains(u.getId())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", u.getId());
            item.put("username", u.getUsername());
            unreadUsers.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", total);
        result.put("readCount", read);
        result.put("readUsers", readUsers);
        result.put("unreadUsers", unreadUsers);
        return result;
    }

    // ==================== 内部辅助 ====================

    private void applyForm(Announcement a, AnnouncementForm form) {
        if (form.getTitle() != null) {
            a.setTitle(form.getTitle().trim());
        }
        if (form.getContent() != null) {
            a.setContent(XssCleanUtil.clean(form.getContent()));
        }
        if (form.getEffectiveTime() != null) {
            a.setEffectiveTime(form.getEffectiveTime());
        }
        // expireTime 为空 = 永久有效（允许编辑时清空失效时间）
        a.setExpireTime(form.getExpireTime());
        if (form.getIsTop() != null) {
            a.setIsTop(form.getIsTop() == 1 ? 1 : 0);
        }
        if (form.getStatus() != null) {
            String s = form.getStatus().trim();
            if (STATUS_ONLINE.equals(s) || STATUS_OFFLINE.equals(s)) {
                a.setStatus(s);
            }
        }
    }

    private void fillAux(List<Announcement> list, AuthUser user, boolean manage) {
        LocalDateTime now = LocalDateTime.now();
        for (Announcement a : list) {
            a.setRead(isRead(user, a.getId()));
            a.setExpired(a.getExpireTime() != null && a.getExpireTime().isBefore(now));
            a.setCanManage(manage);
            if (manage) {
                a.setReadCount(readCount(a.getId()));
                a.setTotalCount(totalUserCount());
            }
        }
    }

    private boolean isRead(AuthUser user, Long announcementId) {
        if (user == null) {
            return false;
        }
        Long c = announcementReadMapper.selectCount(new LambdaQueryWrapper<AnnouncementRead>()
                .eq(AnnouncementRead::getAnnouncementId, announcementId)
                .eq(AnnouncementRead::getUserId, user.getUserId()));
        return c != null && c > 0;
    }

    private boolean isExpired(Announcement a) {
        return a.getExpireTime() != null && a.getExpireTime().isBefore(LocalDateTime.now());
    }

    private long totalUserCount() {
        Long c = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1));
        return c == null ? 0 : c;
    }

    private long readCount(Long announcementId) {
        Long c = announcementReadMapper.selectCount(new LambdaQueryWrapper<AnnouncementRead>()
                .eq(AnnouncementRead::getAnnouncementId, announcementId));
        return c == null ? 0 : c;
    }
}
