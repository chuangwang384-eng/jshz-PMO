package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * 公告板块审查修复回归（2026-11-26，1.0.155）：
 * P2-1 时间校验 / P2-2 员工生效口径 / P2-3 在职统计口径 / P2-4 幂等已读 /
 * P3-2 纯文本搜索 / 需求四 置顶排序交换。
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementServiceFixTest {

    @InjectMocks AnnouncementService service;
    @Mock AnnouncementMapper baseMapperMock;
    @Mock AnnouncementReadMapper reads;
    @Mock SysUserMapper users;
    @Mock OperationLogService audit;

    AuthUser manage() {
        return new AuthUser(9L, "管理员", false, false, Set.of(), Set.of(AnnouncementService.PERMISSION_MANAGE));
    }

    AuthUser employee() {
        return new AuthUser(7L, "员工", false, false, Set.of(), Set.of());
    }

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "fixture"), Announcement.class);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapperMock);
    }

    private static Announcement ann(Long id, String title, String content, Integer isTop, Integer topSort) {
        Announcement a = new Announcement();
        a.setId(id);
        a.setTitle(title);
        a.setContent(content);
        a.setIsTop(isTop);
        a.setTopSort(topSort);
        a.setStatus(AnnouncementService.STATUS_ONLINE);
        a.setCreateTime(LocalDateTime.of(2026, 11, 20, 9, 0));
        return a;
    }

    // ---------- P2-1 时间校验 ----------

    @Test
    void createRejectsExpireNotAfterEffective() {
        AnnouncementForm form = new AnnouncementForm();
        form.setTitle("t");
        form.setContent("<p>正文</p>");
        form.setEffectiveTime(LocalDateTime.of(2026, 12, 2, 9, 0));
        form.setExpireTime(LocalDateTime.of(2026, 12, 1, 9, 0));
        BusinessException e = assertThrows(BusinessException.class, () -> service.create(manage(), form, null));
        assertEquals("失效时间必须晚于生效时间", e.getMessage());
    }

    @Test
    void createRejectsExpireAlreadyPast() {
        AnnouncementForm form = new AnnouncementForm();
        form.setTitle("t");
        form.setContent("<p>正文</p>");
        form.setExpireTime(LocalDateTime.of(2020, 1, 1, 0, 0));
        BusinessException e = assertThrows(BusinessException.class, () -> service.create(manage(), form, null));
        assertEquals("失效时间不能早于当前时间", e.getMessage());
    }

    @Test
    void editKeepsHistoricalExpiredAnnouncementAllowed() {
        // 编辑已过期存量公告（只改标题，expireTime 不携带）不被"失效时间不能早于当前时间"误伤
        Announcement a = ann(1L, "旧公告", "<p>历史</p>", 0, 0);
        a.setEffectiveTime(LocalDateTime.of(2020, 1, 1, 0, 0));
        a.setExpireTime(LocalDateTime.of(2020, 2, 1, 0, 0));
        when(baseMapperMock.selectById(1L)).thenReturn(a);
        when(baseMapperMock.updateById(any(Announcement.class))).thenReturn(1);
        AnnouncementForm form = new AnnouncementForm();
        form.setTitle("改个标题");
        Announcement updated = service.update(manage(), 1L, form, null);
        assertEquals("改个标题", updated.getTitle());
        // 既有契约：编辑未携带 expireTime = 置为永久有效（允许清空失效时间，见 applyForm 注释）
        assertNull(updated.getExpireTime());
    }

    // ---------- P2-2 员工生效口径 ----------

    @Test
    void employeePageQueryAppliesEffectiveCondition() {
        when(baseMapperMock.selectPage(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Announcement>(1, 10, 0));
        service.pageQuery(employee(), null, null, 1, 10, null);
        ArgumentCaptor<LambdaQueryWrapper<Announcement>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(baseMapperMock).selectPage(any(), cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertTrue(sql.contains("status"), "员工视角必须带状态过滤");
        assertTrue(sql.contains("effective_time"), "员工视角必须带生效时间过滤（P2-2）");
        assertTrue(sql.contains("expire_time"), "员工视角必须带未过期过滤（P2-2）");
    }

    @Test
    void managerPageQueryWithoutStatusFilterReturnsAll() {
        when(baseMapperMock.selectPage(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Announcement>(1, 10, 0));
        service.pageQuery(manage(), null, null, 1, 10, null);
        ArgumentCaptor<LambdaQueryWrapper<Announcement>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(baseMapperMock).selectPage(any(), cap.capture());
        assertFalse(cap.getValue().getSqlSegment().contains("effective_time"), "管理视角不受生效口径限制");
    }

    // ---------- P2-4 幂等已读 ----------

    @Test
    void markReadTreatsDuplicateKeyAsSuccess() {
        Announcement a = ann(1L, "t", "<p>c</p>", 0, 0);
        when(baseMapperMock.selectById(1L)).thenReturn(a);
        when(reads.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_announcement_user"))
                .when(reads).insert(any(AnnouncementRead.class));
        assertDoesNotThrow(() -> service.markRead(employee(), 1L));
    }

    @Test
    void markAllEffectiveReadToleratesDuplicateInserts() {
        when(baseMapperMock.selectList(any())).thenReturn(List.of(ann(1L, "a", "x", 0, 0), ann(2L, "b", "y", 0, 0)));
        when(reads.selectList(any())).thenReturn(List.of());
        when(reads.insert(any(AnnouncementRead.class)))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenReturn(1);
        int n = service.markAllEffectiveRead(employee().getUserId());
        assertEquals(2, n, "撞键的插入视为已读成功，不中断整批");
    }

    // ---------- P2-3 在职口径 ----------

    @Test
    void readStatsCountsActiveUsersOnly() {
        when(baseMapperMock.selectById(1L)).thenReturn(ann(1L, "t", "c", 0, 0));
        SysUser u1 = new SysUser(); u1.setId(1L); u1.setUsername("甲");
        SysUser u2 = new SysUser(); u2.setId(2L); u2.setUsername("乙");
        // 第一次 selectList（在职集合）→ 甲/乙；第二次（usernames）→ 同一批
        when(users.selectList(any())).thenReturn(List.of(u1, u2), List.of(u1, u2));
        AnnouncementRead r1 = new AnnouncementRead(); r1.setAnnouncementId(1L); r1.setUserId(1L);
        r1.setReadTime(LocalDateTime.of(2026, 11, 21, 10, 0));
        AnnouncementRead r3 = new AnnouncementRead(); r3.setAnnouncementId(1L); r3.setUserId(3L); // 已停用用户
        r3.setReadTime(LocalDateTime.of(2026, 11, 21, 11, 0));
        when(reads.selectList(any())).thenReturn(List.of(r1, r3));

        Map<String, Object> stats = service.readStats(manage(), 1L);
        assertEquals(2L, stats.get("totalCount"), "总数=在职用户数");
        assertEquals(1L, stats.get("readCount"), "停用用户的已读不计入（P2-3）");
        assertEquals(1, ((List<?>) stats.get("readUsers")).size());
        assertEquals(1, ((List<?>) stats.get("unreadUsers")).size(), "读+未读=总数");
    }

    // ---------- P3-2 纯文本搜索 ----------

    @Test
    void keywordSearchDoesNotMatchHtmlMarkup() {
        Announcement htmlTagOnly = ann(2L, "节日安排", "<span style=\"x\">联欢会延期举行</span>", 0, 0);
        when(baseMapperMock.selectList(any())).thenReturn(List.of(htmlTagOnly));
        PageResult<Announcement> result = service.pageQuery(employee(), "span", null, 1, 10, null);
        assertEquals(0, result.getItems().size(), "关键词只出现在 HTML 标签中不得命中（P3-2）");

        Announcement textHit = ann(3L, "通知", "<p>机房维护安排</p>", 0, 0);
        when(baseMapperMock.selectList(any())).thenReturn(List.of(textHit));
        PageResult<Announcement> hit = service.pageQuery(employee(), "机房维护", null, 1, 10, null);
        assertEquals(1, hit.getItems().size(), "纯文本关键词正常命中");
    }

    // ---------- 需求四 置顶排序 ----------

    @Test
    void moveTopSwapsSortValuesWithNeighbor() {
        Announcement a = ann(1L, "A", "x", 1, 1);
        Announcement b = ann(2L, "B", "y", 1, 2);
        when(baseMapperMock.selectById(1L)).thenReturn(a);
        when(baseMapperMock.selectList(any())).thenReturn(List.of(a, b));
        service.moveTop(manage(), 1L, "down", null);
        ArgumentCaptor<Announcement> cap = ArgumentCaptor.forClass(Announcement.class);
        verify(baseMapperMock, times(2)).updateById(cap.capture());
        List<Announcement> updated = cap.getAllValues();
        Announcement ua = updated.stream().filter(x -> x.getId().equals(1L)).findFirst().orElseThrow();
        Announcement ub = updated.stream().filter(x -> x.getId().equals(2L)).findFirst().orElseThrow();
        assertEquals(2, ua.getTopSort(), "下移后 A 拿到 B 的排序值");
        assertEquals(1, ub.getTopSort(), "上移后 B 拿到 A 的排序值");
    }

    @Test
    void moveTopRejectsNonTopOrBoundary() {
        Announcement plain = ann(5L, "普通", "x", 0, 0);
        when(baseMapperMock.selectById(5L)).thenReturn(plain);
        assertThrows(BusinessException.class, () -> service.moveTop(manage(), 5L, "up", null), "非置顶不可排序");

        Announcement top = ann(1L, "A", "x", 1, 1);
        when(baseMapperMock.selectById(1L)).thenReturn(top);
        when(baseMapperMock.selectList(any())).thenReturn(List.of(top));
        BusinessException e = assertThrows(BusinessException.class, () -> service.moveTop(manage(), 1L, "up", null));
        assertEquals("已在最顶部", e.getMessage());
    }

    @Test
    void toggleTopAssignsSmallestSortValue() {
        Announcement a = ann(7L, "新置顶", "x", 0, 0);
        Announcement existing = ann(8L, "已置顶", "y", 1, 3);
        when(baseMapperMock.selectById(7L)).thenReturn(a);
        when(baseMapperMock.selectList(any())).thenReturn(List.of(existing));
        when(baseMapperMock.updateById(any(Announcement.class))).thenReturn(1);
        Announcement result = service.toggleTop(manage(), 7L, true, null);
        assertEquals(2, result.getTopSort(), "新置顶取最小值-1=2，排在已置顶(3)之前");
    }

    @Test
    void toggleTopFalseKeepsSortValueForReTop() {
        Announcement a = ann(1L, "A", "x", 1, 5);
        when(baseMapperMock.selectById(1L)).thenReturn(a);
        when(baseMapperMock.updateById(any(Announcement.class))).thenReturn(1);
        Announcement result = service.toggleTop(manage(), 1L, false, null);
        assertEquals(0, result.getIsTop());
        assertEquals(5, result.getTopSort(), "取消置顶保留排序值，重新置顶恢复原位置");
    }
}
