package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectPhase;
import com.jshz.expert.mapper.ProjectAssistantMapper;
import com.jshz.expert.mapper.ProjectMapper;
import com.jshz.expert.mapper.ProjectPhaseMapper;
import com.jshz.expert.security.AuthUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 会议日期必填与 team 派生回归（2026-11-26 项目日历需求 FR-04/FR-05）。
 */
@ExtendWith(MockitoExtension.class)
class ProjectMeetingDateTeamTest {

    @InjectMocks ProjectService service;
    @Mock ProjectMapper projects;
    @Mock ProjectPhaseMapper phases;
    @Mock ProjectAssistantMapper assistants;
    @Mock ProjectConfigurationService configuration;
    @Mock OperationLogService audit;
    @Mock ProjectTeamSyncService teamSync;
    @Mock com.jshz.expert.mapper.ProjectExecutorMapper executors;

    AuthUser admin() {
        return new AuthUser(9L, "fixture", true, false, Set.of(), Set.of());
    }

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "fixture"), Project.class);
        ReflectionTestUtils.setField(service, "baseMapper", projects);
    }

    private ProjectPhase phase() {
        ProjectPhase p = new ProjectPhase();
        p.setId(7L);
        p.setName("日程上传");
        p.setSort(1);
        p.setIsActive(1);
        return p;
    }

    private Map<String, Object> body(Map<String, Object> extra) {
        Map<String, Object> b = new java.util.HashMap<>();
        b.put("name", "测试会议");
        b.put("category", "ONLINE");
        b.put("projectType", "PLATFORM");
        b.put("startDate", "2026-12-01");
        b.put("endDate", "2026-12-02");
        b.putAll(extra);
        return b;
    }

    @Test
    void createRejectsMissingMeetingDates() {
        BusinessException e = assertThrows(BusinessException.class, () -> service.create(body(Map.of()), admin()));
        assertEquals("会议举办起止日期必填", e.getMessage());
    }

    @Test
    void createRejectsMeetingStartOnly() {
        assertThrows(BusinessException.class,
                () -> service.create(body(Map.of("meetingStartDate", "2026-12-01", "meetingEndDate", "")), admin()));
    }

    @Test
    void createDerivesTeamFromOwnerDepartment() {
        when(phases.selectList(any())).thenReturn(List.of(phase()));
        when(teamSync.deriveTeam(9L)).thenReturn("DEPT1");
        Project created = service.create(body(Map.of(
                "meetingStartDate", "2026-12-01", "meetingEndDate", "2026-12-02")), admin());
        assertEquals("DEPT1", created.getTeam());
        verify(teamSync).deriveTeam(9L);
    }

    @Test
    void createKeepsTeamNullWhenDepartmentUnmapped() {
        when(phases.selectList(any())).thenReturn(List.of(phase()));
        when(teamSync.deriveTeam(9L)).thenReturn(null);
        Project created = service.create(body(Map.of(
                "meetingStartDate", "2026-12-01", "meetingEndDate", "2026-12-01")), admin());
        assertNull(created.getTeam(), "映射不到时 team 留空（8.3 决策）");
    }

    @Test
    void updateRejectsClearingMeetingDates() {
        Project p = new Project();
        p.setId(1L);
        p.setOwnerId(9L);
        p.setStatus("ACTIVE");
        p.setMeetingStartDate(java.time.LocalDate.of(2026, 12, 1));
        p.setMeetingEndDate(java.time.LocalDate.of(2026, 12, 2));
        when(projects.selectOne(any())).thenReturn(p);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("meetingStartDate", null);   // Map.of 不接受 null 值，用 HashMap 模拟前端清空
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update(1L, body, admin()));
        assertEquals("会议举办日期不能清空", e.getMessage());
    }

    @Test
    void updateRejectsReversedProjectDates() {
        // P2-1（2026-11-26）：编辑起止倒挂与创建同口径拒绝
        Project p = new Project();
        p.setId(1L);
        p.setOwnerId(9L);
        p.setStatus("ACTIVE");
        p.setStartDate(java.time.LocalDate.of(2026, 12, 1));
        p.setEndDate(java.time.LocalDate.of(2026, 12, 5));
        when(projects.selectOne(any())).thenReturn(p);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.update(1L, Map.of("endDate", "2026-11-01"), admin()));
        assertEquals("项目结束时间不能早于开始时间", e.getMessage());
    }

    @Test
    void pageProjectsRejectsInvalidStatusFilter() {
        // P2-3（2026-11-26）：非法 status 不再静默返回全量
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.pageProjects(null, "abc", null, null, null, null, null, null, null, null, null, 1, 10, admin()));
        assertEquals("状态筛选参数不合法", e.getMessage());
    }

    @Test
    void escapeLikeEscapesWildcards() {
        // P3-3（2026-11-26）：LIKE 通配符转义
        assertEquals("100\\%\\_x\\\\y", com.jshz.expert.common.ParamUtils.escapeLike("100%_x\\y"));
        assertNull(com.jshz.expert.common.ParamUtils.escapeLike(null));
    }

    @Test
    void updateDerivesTeamOnOwnerChange() {
        Project p = new Project();
        p.setId(1L);
        p.setOwnerId(9L);
        p.setStatus("ACTIVE");
        p.setTeam("DEPT1");
        when(projects.selectOne(any())).thenReturn(p);
        when(teamSync.deriveTeam(8L)).thenReturn("DEPT2");
        when(projects.updateById(any(Project.class))).thenReturn(1);
        service.update(1L, Map.of("ownerId", 8), admin());
        assertEquals(8L, p.getOwnerId());
        assertEquals("DEPT2", p.getTeam());
    }
}
