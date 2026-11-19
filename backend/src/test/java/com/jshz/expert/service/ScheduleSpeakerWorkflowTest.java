package com.jshz.expert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectSchedule;
import com.jshz.expert.entity.ProjectSpeaker;
import com.jshz.expert.mapper.ProjectExpertMapper;
import com.jshz.expert.mapper.ProjectScheduleMapper;
import com.jshz.expert.mapper.ProjectSpeakerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class ScheduleSpeakerWorkflowTest {
    @BeforeAll
    static void mapperMetadata() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "scheduleFixture"), ProjectSpeaker.class);
    }

    private final ProjectScheduleMapper schedules = mock(ProjectScheduleMapper.class);
    private final ProjectSpeakerMapper speakers = mock(ProjectSpeakerMapper.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final ExpertService experts = mock(ExpertService.class);
    private final ResumeParseService resumes = mock(ResumeParseService.class);
    private final ScheduleParseService service = new ScheduleParseService(schedules, speakers, projects, experts,
            null, resumes, mock(ProjectExpertMapper.class), null, null, new ObjectMapper());
    private final ProjectSchedule schedule = new ProjectSchedule();

    @BeforeEach
    void prepareProject() {
        Project project = new Project();
        project.setId(5L);
        when(projects.requireProject(5L)).thenReturn(project);
        schedule.setId(8L);
        schedule.setParseStatus("DONE");
        schedule.setRawResult("{\"revision\":\"current\"}");
        when(schedules.selectOne(any())).thenReturn(schedule);
    }

    @Test
    void savesOnlyNewAppearancesAndRejectsStaleDraft() {
        ProjectSpeaker existing = row(10L, "何振", "SPEAKER", "09:00");
        when(speakers.selectList(any())).thenReturn(List.of(existing));
        var input = List.<Map<String, Object>>of(
                Map.of("expertName", "何振", "role", "SPEAKER", "segmentTime", "09:00"),
                Map.of("expertName", "何振", "role", "SPEAKER", "segmentTime", "10:00"));
        assertThrows(BusinessException.class, () -> service.confirm(5L, 8L, "old", input, null));
        verify(speakers, never()).insert(any(ProjectSpeaker.class));
        service.confirm(5L, 8L, "current", input, null);
        var inserted = org.mockito.ArgumentCaptor.forClass(ProjectSpeaker.class);
        verify(speakers).insert(inserted.capture());
        assertEquals("10:00", inserted.getValue().getSegmentTime());
        assertEquals(1, inserted.getValue().getConfirmed());
        assertEquals(10L, existing.getId());
    }

    @Test
    void appliesSelectedRoleChangeAndPreservesUnselectedRemoval() {
        schedule.setRawResult("""
                {"revision":"current","comparison":{"token":"current","changes":[
                  {"before":{"id":10,"scheduleId":7,"role":"SPEAKER"},"after":{"role":"HOST","segmentTime":"09:00"}},
                  {"before":{"id":11,"scheduleId":7,"role":"HOST"},"after":null}
                ]}}
                """);
        when(speakers.update(isNull(), any())).thenReturn(1);
        assertThrows(BusinessException.class, () -> service.applyComparison(5L, 8L, "old", List.of(10L), null));
        service.applyComparison(5L, 8L, "current", List.of(10L), null);
        verify(speakers).update(isNull(), any());
        verify(speakers, never()).delete(any());
        assertEquals("{\"revision\":\"current\"}", schedule.getRawResult());
    }

    @Test
    void confirmsRemovalWithoutDeletingExpertArchive() {
        schedule.setRawResult("""
                {"comparison":{"token":"current","changes":[
                  {"before":{"id":11,"scheduleId":7,"role":"HOST","expertId":99},"after":null}
                ]}}
                """);
        when(speakers.delete(any())).thenReturn(1);
        service.applyComparison(5L, 8L, "current", List.of(11L), null);
        verify(speakers).delete(any());
        verifyNoInteractions(experts);
    }

    @Test
    void automaticArchiveSkipsUnmatchedAndConflictingResumesAndContinues() {
        when(schedules.selectList(any())).thenReturn(List.of(schedule));
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(call -> {
            Consumer<org.springframework.transaction.TransactionStatus> action = call.getArgument(0);
            action.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactions).executeWithoutResult(any());
        ReflectionTestUtils.setField(service, "transactions", transactions);
        var pending = row(10L, "何振", "SPEAKER", "09:00");
        var conflict = row(11L, "王军", "HOST", "10:00");
        conflict.setExpertId(99L);
        when(speakers.selectList(any())).thenReturn(List.of(pending, conflict), List.of(),
                List.of(pending, conflict), List.of(), List.of(pending, conflict), List.of());
        when(experts.create(any(Expert.class), isNull())).thenAnswer(call -> {
            Expert saved = call.getArgument(0);
            saved.setId(42L);
            return saved;
        });
        var files = List.of(resume("unmatched.pptx", "陌生人"), resume("conflict.pptx", "王军"),
                resume("matched.pptx", "何振"));
        var result = service.archiveByResumes(5L, List.copyOf(files), null);
        assertEquals(List.of(false, false, true), result.stream().map(item -> item.get("ok")).toList());
        assertEquals(42L, pending.getExpertId());
        verify(experts).create(any(Expert.class), isNull());
        verify(speakers).updateById(pending);
    }

    private MockMultipartFile resume(String fileName, String name) {
        when(resumes.parse(eq(fileName), any(byte[].class), eq(false))).thenReturn(
                new ResumeParseService.ResumeParseResult(Map.of("姓名", name, "单位", "测试医院"), List.of(), 2, ""));
        return new MockMultipartFile("files", fileName, "application/octet-stream", new byte[]{1});
    }

    private ProjectSpeaker row(Long id, String name, String role, String time) {
        var row = new ProjectSpeaker();
        row.setId(id);
        row.setScheduleId(7L);
        row.setExpertName(name);
        row.setRole(role);
        row.setSegmentTime(time);
        return row;
    }
}
