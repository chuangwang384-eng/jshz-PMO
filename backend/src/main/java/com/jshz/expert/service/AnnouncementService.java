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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统公告服务。
 * 写操作（创建/编辑/删除/上下架/置顶/置顶排序）业务层校验 announcement:manage；
 * 列表/详情/标记已读登录即可；标记已读幂等且不入操作日志。
 * 2026-11-26 修复批次（审查 20261126_announcement_audit）：
 * - P2-1 生效/失效时间前后校验（expire 必须 > effective）；
 * - P2-2 员工列表与生效口径统一：员工视角一律只返回生效中公告（scope 参数保留兼容但不再区分）；
 * - P2-3 已读统计/进度按在职（sys_user.status=1）口径，读+未读=总数；
 * - P2-4 标记已读撞唯一键视为幂等成功（并发双开详情/全部已读竞态）；
 * - P3-2 关键词搜索按纯文本匹配（Jsoup 剥离 HTML 标签后 contains；当前公告量小，内存分页可接受）；
 * - P3-3 生效口径收敛到 applyEffective 单处实现；管理列表已读进度批量查询消除 N+1；
 * 需求四（2026-11-26）：置顶组内排序 top_sort（值小靠前），moveTop 上移/下移交换相邻排序值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService extends ServiceImpl<AnnouncementMapper, Announcement> {

    public static final String PERMISSION_MANAGE = "announcement:manage";
    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String TARGET_TYPE = "ANNOUNCEMENT";

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
     * 分页查询：管理视角返回全部状态并附已读进度；员工视角仅"生效中"公告（P2-2：
     * 与消息抽屉/未读角标同一口径，未来生效/已过期公告不再出现在员工列表）。
     * scope 参数保留兼容旧调用但已不产生分支（历史上一度用 scope=effective 区分）。
     */
    public PageResult<Announcement> pageQuery(AuthUser user, String keyword, String status, int page, int size, String scope) {
        boolean manage = user != null && user.hasPermission(PERMISSION_MANAGE);
        LambdaQueryWrapper<Announcement> base = new LambdaQueryWrapper<>();
        if (!manage) {
            applyEffective(base, LocalDateTime.now());
        } else if (StringUtils.hasText(status)) {
            base.eq(Announcement::getStatus, status.trim());
        }
        // 生效排序口径（需求四）：置顶组内 top_sort 升序，非置顶按创建时间倒序；id 倒序兜底稳定
        base.orderByDesc(Announcement::getIsTop)
                .orderByAsc(Announcement::getTopSort)
                .orderByDesc(Announcement::getCreateTime)
                .orderByDesc(Announcement::getId);
        if (!StringUtils.hasText(keyword)) {
            Page<Announcement> result = this.page(new Page<>(Math.max(page, 1), Math.max(size, 1)), base);
            fillAux(result.getRecords(), user, manage);
            return PageResult.of(result);
        }
        // P3-2：keyword 需匹配纯文本正文（content 存的是清洗后 HTML，LIKE 会命中标签名）。
        // 公告量小（数十条），先按 title LIKE 预筛 + 全量状态过滤，再在内存按纯文本过滤后分页。
        String kw = keyword.trim();
        base.and(w -> w.like(Announcement::getTitle, kw).or().like(Announcement::getContent, kw));
        List<Announcement> candidates = this.list(base);
        List<Announcement> matched = candidates.stream()
                .filter(a -> plainText(a).contains(kw))
                .collect(Collectors.toList());
        int from = Math.max((Math.max(page, 1) - 1) * Math.max(size, 1), 0);
        List<Announcement> rows = from >= matched.size()
                ? List.of()
                : matched.subList(from, Math.min(from + Math.max(size, 1), matched.size()));
        fillAux(new ArrayList<>(rows), user, manage);
        return PageResult.of(new Page<Announcement>(Math.max(page, 1), Math.max(size, 1), matched.size()).setRecords(rows));
    }

    /** 纯文本匹配素材：标题 + 正文剥标签后的文本（小写不敏感由调用方约定；中文场景保持原样即可） */
    private String plainText(Announcement a) {
        String text = a.getContent() == null ? "" : org.jsoup.Jsoup.parse(a.getContent()).text();
        return (a.getTitle() == null ? "" : a.getTitle()) + "\n" + text;
    }

    /**
     * 生效口径唯一实现（P3-3）：status=ONLINE 且 effective_time<=now 且（未过期 或 expire_time>now）。
     * pageQuery 员工分支 / unreadCount / markAllEffectiveRead 三处共用，禁止复制本逻辑。
     */
    private LambdaQueryWrapper<Announcement> applyEffective(LambdaQueryWrapper<Announcement> wrapper, LocalDateTime now) {
        return wrapper.eq(Announcement::getStatus, STATUS_ONLINE)
                .le(Announcement::getEffectiveTime, now)
                .and(w -> w.isNull(Announcement::getExpireTime)
                        .or().gt(Announcement::getExpireTime, now));
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
            Map<String, Object> stats = readStats(user, id);
            a.setReadCount((Long) stats.get("readCount"));
            a.setTotalCount((Long) stats.get("totalCount"));
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
        // P2-1：新建时失效时间不能已是过去时（编辑存量公告不拦，允许保留历史过期公告）
        if (a.getExpireTime() != null && a.getExpireTime().isBefore(now)) {
            throw new BusinessException("失效时间不能早于当前时间");
        }
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

    /** 置顶/取消置顶（需 announcement:manage）。置顶时若未排序则取当前最小 top_sort-1（排最前）。 */
    public Announcement toggleTop(AuthUser user, Long id, boolean top, HttpServletRequest request) {
        requireManage(user);
        Announcement a = this.getById(id);
        if (a == null) {
            throw new BusinessException("公告不存在或已删除");
        }
        a.setIsTop(top ? 1 : 0);
        if (top && (a.getTopSort() == null || a.getTopSort() == 0)) {
            a.setTopSort(nextTopSort());
        }
        a.setUpdateTime(LocalDateTime.now());
        this.updateById(a);
        operationLogService.record(user.getUserId(), user.getUsername(),
                OperationLogService.ACTION_TOP_ANNOUNCEMENT,
                (top ? "置顶公告「" : "取消置顶公告「") + a.getTitle() + "」", TARGET_TYPE, a.getId(), request);
        return a;
    }

    /**
     * 置顶组内上移/下移（需求四，需 announcement:manage）：dir=up|down。
     * 与相邻置顶公告交换 top_sort；取消置顶保留原值（重新置顶恢复原位置）。
     */
    public Announcement moveTop(AuthUser user, Long id, String dir, HttpServletRequest request) {
        requireManage(user);
        if (!"up".equals(dir) && !"down".equals(dir)) {
            throw new BusinessException("dir 参数仅支持 up / down");
        }
        Announcement a = this.getById(id);
        if (a == null) {
            throw new BusinessException("公告不存在或已删除");
        }
        if (a.getIsTop() == null || a.getIsTop() != 1) {
            throw new BusinessException("仅置顶公告支持排序调整");
        }
        // 当前展示顺序（与 pageQuery 一致）：top_sort 升序，id 倒序兜底
        List<Announcement> tops = this.list(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getIsTop, 1)
                .orderByAsc(Announcement::getTopSort)
                .orderByDesc(Announcement::getId));
        int idx = -1;
        for (int i = 0; i < tops.size(); i++) {
            if (tops.get(i).getId().equals(id)) {
                idx = i;
                break;
            }
        }
        int target = "up".equals(dir) ? idx - 1 : idx + 1;
        if (idx < 0 || target < 0 || target >= tops.size()) {
            throw new BusinessException("up".equals(dir) ? "已在最顶部" : "已在最底部");
        }
        Announcement other = tops.get(target);
        Integer tmp = a.getTopSort();
        a.setTopSort(other.getTopSort());
        other.setTopSort(tmp);
        LocalDateTime now = LocalDateTime.now();
        a.setUpdateTime(now);
        other.setUpdateTime(now);
        this.updateById(a);
        this.updateById(other);
        operationLogService.record(user.getUserId(), user.getUsername(),
                OperationLogService.ACTION_UPDATE_ANNOUNCEMENT,
                ("up".equals(dir) ? "上移" : "下移") + "公告「" + a.getTitle() + "」置顶顺序", TARGET_TYPE, a.getId(), request);
        return a;
    }

    /** 当前最小置顶排序值 - 1（新置顶排最前）；无置顶时从 1 开始。 */
    private Integer nextTopSort() {
        List<Announcement> tops = this.list(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getIsTop, 1)
                .orderByAsc(Announcement::getTopSort)
                .last("LIMIT 1"));
        Integer min = tops.isEmpty() ? null : tops.get(0).getTopSort();
        return (min == null || min <= 0) ? 1 : min - 1;
    }

    /**
     * 标记已读：幂等（唯一联合键兜底），不入操作日志。
     * P2-4：先查后插窗口内并发重复插入撞唯一键时视为已成功（如详情页与"全部已读"同时触发）。
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
        insertReadQuietly(id, user.getUserId(), LocalDateTime.now());
    }

    /** 插入已读记录；唯一键冲突（并发已插入）视为幂等成功。 */
    private void insertReadQuietly(Long announcementId, Long userId, LocalDateTime readTime) {
        AnnouncementRead ar = new AnnouncementRead();
        ar.setAnnouncementId(announcementId);
        ar.setUserId(userId);
        ar.setReadTime(readTime);
        try {
            announcementReadMapper.insert(ar);
        } catch (DuplicateKeyException e) {
            log.debug("公告已读幂等命中 uk_announcement_user：announcement={} user={}", announcementId, userId);
        }
    }

    /**
     * 当前用户生效中公告未读数（与员工列表/抽屉同一生效口径，P3-3 复用 applyEffective）。
     */
    public long unreadCount(Long userId) {
        if (userId == null) {
            return 0;
        }
        List<Long> ids = effectiveIds(LocalDateTime.now());
        if (ids.isEmpty()) {
            return 0;
        }
        Long read = announcementReadMapper.selectCount(new LambdaQueryWrapper<AnnouncementRead>()
                .eq(AnnouncementRead::getUserId, userId)
                .in(AnnouncementRead::getAnnouncementId, ids));
        return ids.size() - (read == null ? 0 : read);
    }

    /**
     * 当前用户把全部生效中公告标记已读（幂等：差集插入，撞唯一键视为已成功）。
     * 返回本次实际插入条数；已下架/已过期公告不在范围。
     */
    @Transactional
    public int markAllEffectiveRead(Long userId) {
        if (userId == null) {
            return 0;
        }
        List<Long> ids = effectiveIds(LocalDateTime.now());
        if (ids.isEmpty()) {
            return 0;
        }
        List<AnnouncementRead> already = announcementReadMapper.selectList(
                new LambdaQueryWrapper<AnnouncementRead>()
                        .eq(AnnouncementRead::getUserId, userId)
                        .in(AnnouncementRead::getAnnouncementId, ids));
        Set<Long> readIds = already.stream()
                .map(AnnouncementRead::getAnnouncementId).collect(Collectors.toSet());
        int count = 0;
        LocalDateTime t = LocalDateTime.now();
        for (Long id : ids) {
            if (readIds.contains(id)) {
                continue;
            }
            insertReadQuietly(id, userId, t);
            count++;
        }
        return count;
    }

    /** 生效中公告 id 列表（applyEffective 的轻量投影）。 */
    private List<Long> effectiveIds(LocalDateTime now) {
        return this.list(applyEffective(new LambdaQueryWrapper<Announcement>()
                .select(Announcement::getId), now))
                .stream().map(Announcement::getId).toList();
    }

    /**
     * 已读进度与已读/未读名单（需 announcement:manage）。
     * P2-3：口径统一为在职用户（sys_user.status=1）——读+未读=总数，进度不会超 100%；
     * 已停用账号的已读记录不计入。
     */
    public Map<String, Object> readStats(AuthUser user, Long id) {
        requireManage(user);
        Announcement a = this.getById(id);
        if (a == null) {
            throw new BusinessException("公告不存在或已删除");
        }
        Set<Long> activeIds = activeUserIds();
        Map<Long, LocalDateTime> activeReadTimes = new LinkedHashMap<>();
        for (AnnouncementRead ar : announcementReadMapper.selectList(
                new LambdaQueryWrapper<AnnouncementRead>()
                        .eq(AnnouncementRead::getAnnouncementId, id)
                        .orderByAsc(AnnouncementRead::getReadTime))) {
            if (activeIds.contains(ar.getUserId())) {
                activeReadTimes.putIfAbsent(ar.getUserId(), ar.getReadTime());
            }
        }
        Map<Long, String> names = usernameOf(activeIds);
        List<Map<String, Object>> readUsers = new ArrayList<>();
        for (Map.Entry<Long, LocalDateTime> e : activeReadTimes.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", e.getKey());
            item.put("username", names.getOrDefault(e.getKey(), String.valueOf(e.getKey())));
            item.put("readTime", e.getValue());
            readUsers.add(item);
        }
        List<Map<String, Object>> unreadUsers = new ArrayList<>();
        for (Long uid : activeIds) {
            if (activeReadTimes.containsKey(uid)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", uid);
            item.put("username", names.getOrDefault(uid, String.valueOf(uid)));
            unreadUsers.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", (long) activeIds.size());
        result.put("readCount", (long) readUsers.size());
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
        // P2-1：生效/失效时间前后校验（两者都填时失效必须晚于生效）
        if (a.getEffectiveTime() != null && a.getExpireTime() != null
                && !a.getExpireTime().isAfter(a.getEffectiveTime())) {
            throw new BusinessException("失效时间必须晚于生效时间");
        }
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

    /** 行级辅助字段填充（P3-3：总数/已读数批量查询，管理列表不再逐行 count）。 */
    private void fillAux(List<Announcement> list, AuthUser user, boolean manage) {
        LocalDateTime now = LocalDateTime.now();
        if (list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(Announcement::getId).toList();
        Set<Long> myReadIds = myReadIds(user, ids);
        Set<Long> activeIds = null;
        Map<Long, Long> readCounts = null;
        long total = 0;
        if (manage) {
            activeIds = activeUserIds();
            total = activeIds.size();
            readCounts = new LinkedHashMap<>();
            for (AnnouncementRead ar : announcementReadMapper.selectList(
                    new LambdaQueryWrapper<AnnouncementRead>().in(AnnouncementRead::getAnnouncementId, ids))) {
                if (activeIds.contains(ar.getUserId())) {
                    readCounts.merge(ar.getAnnouncementId(), 1L, Long::sum);
                }
            }
        }
        for (Announcement a : list) {
            a.setRead(myReadIds.contains(a.getId()));
            a.setExpired(a.getExpireTime() != null && a.getExpireTime().isBefore(now));
            a.setCanManage(manage);
            if (manage) {
                a.setReadCount(readCounts.getOrDefault(a.getId(), 0L));
                a.setTotalCount(total);
            }
        }
    }

    private boolean isRead(AuthUser user, Long announcementId) {
        return user != null && myReadIds(user, List.of(announcementId)).contains(announcementId);
    }

    /** 当前用户在给定公告集合中已读的 id 集合。 */
    private Set<Long> myReadIds(AuthUser user, List<Long> announcementIds) {
        if (user == null || announcementIds.isEmpty()) {
            return Set.of();
        }
        return announcementReadMapper.selectList(new LambdaQueryWrapper<AnnouncementRead>()
                        .eq(AnnouncementRead::getUserId, user.getUserId())
                        .in(AnnouncementRead::getAnnouncementId, announcementIds))
                .stream().map(AnnouncementRead::getAnnouncementId).collect(Collectors.toSet());
    }

    private boolean isExpired(Announcement a) {
        return a.getExpireTime() != null && a.getExpireTime().isBefore(LocalDateTime.now());
    }

    /** 在职用户 id 有序集合（P2-3 口径唯一来源）。 */
    private Set<Long> activeUserIds() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, 1).orderByAsc(SysUser::getId))
                .stream().map(SysUser::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 批量取用户名（一次查询），缺失回退字符串 id。 */
    private Map<Long, String> usernameOf(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds))
                .stream().collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (x, y) -> x));
    }

}
