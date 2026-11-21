package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.controller.ProjectController;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.Project;
import com.jshz.expert.mapper.ProjectExpertMapper;
import com.jshz.expert.mapper.ProjectSpeakerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectResumeAvatarTest {
    private static final String PATH = "/api/project/55/speakers/archive-manual";
    private static final String BODY = "{\"name\":\"张医生\",\"requirePendingSpeaker\":true}";
    private final MockMultipartFile photo = new MockMultipartFile(
            "photo", "resume-photo.png", "image/png", new byte[]{1});

    @Test
    void projectArchiveAcceptsPhotoAndRetainsJsonContract() throws Exception {
        ScheduleParseService schedule = mock(ScheduleParseService.class);
        when(schedule.archiveManual(eq(55L), any(), any(), isNull())).thenReturn(Map.of("ok", true));
        var controller = new ProjectController(mock(ProjectService.class), null, schedule, null, null, null);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        var data = new MockMultipartFile("data", "blob", "application/json", BODY.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart(PATH).file(data).file(photo)).andExpect(status().isOk());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());

        Map<String, Object> fields = Map.of("name", "张医生", "requirePendingSpeaker", true);
        verify(schedule).archiveManual(55L, fields, photo, null);
        verify(schedule).archiveManual(55L, fields, null, null);
    }

    @Test
    void projectPermissionIsCheckedBeforeCreatingOrSavingPhoto() {
        ProjectService projects = mock(ProjectService.class);
        ExpertService experts = mock(ExpertService.class);
        Project project = new Project();
        when(projects.requireProject(55L)).thenReturn(project);
        doThrow(new BusinessException("无权限")).when(projects).requireOperable(project, null);
        var schedule = new ScheduleParseService(null, null, projects, experts, null, null, null, null, null, null);

        assertThrows(BusinessException.class, () -> schedule.archiveManual(55L, Map.of("name", "张医生"), photo, null));
        verifyNoInteractions(experts);
    }

    @Test
    void projectArchiveRequiresUnitAndAcceptsAbsentOptionalFields() {
        ProjectService projects = mock(ProjectService.class);
        Project project = new Project();
        project.setId(55L);
        when(projects.requireProject(55L)).thenReturn(project);
        ExpertService experts = spy(new ExpertService(null, null, null));
        doAnswer(invocation -> {
            Expert expert = invocation.getArgument(0);
            assertEquals("测试医院", expert.getUnit());
            assertEquals("心内科", expert.getDepartment());
            assertNull(expert.getContact());
            assertNull(expert.getTitle());
            assertNull(expert.getProfile());
            expert.setId(42L);
            return true;
        }).when(experts).save(any(Expert.class));
        ProjectSpeakerMapper speakers = mock(ProjectSpeakerMapper.class);
        when(speakers.selectList(any())).thenReturn(List.of());
        var schedule = new ScheduleParseService(null, speakers, projects, experts, null,
                null, mock(ProjectExpertMapper.class), null, null, null);

        assertEquals("单位不能为空", assertThrows(BusinessException.class,
                () -> schedule.archiveManual(55L, Map.of("name", "张医生", "unit", " "), null, null)).getMessage());
        assertThrows(BusinessException.class, () -> schedule.archiveManual(55L,
                Map.of("name", "张医生", "unit", "测试医院", "contact", "错误号码"), null, null));
        assertEquals(42L, schedule.archiveManual(55L,
                Map.of("name", "张医生", "unit", "测试医院", "department", " 心内科 ", "contact", " 无 "), null, null).get("expertId"));
        verify(experts, times(1)).save(any(Expert.class));
    }

    @Test
    void linksOnlyAnExactExistingArchiveAndKeepsItsFieldsAndPhoto() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "projectResumeFixture"),
                com.jshz.expert.entity.ProjectSpeaker.class);
        ProjectService projects = mock(ProjectService.class);
        when(projects.requireProject(55L)).thenReturn(new Project());
        ExpertService experts = mock(ExpertService.class);
        Expert existing = new Expert();
        existing.setId(42L);
        existing.setName("张医生");
        existing.setUnit("测试医院");
        existing.setDepartment("原科室");
        existing.setPhoto("original.png");
        when(experts.getById(42L)).thenReturn(existing);
        ProjectSpeakerMapper speakers = mock(ProjectSpeakerMapper.class);
        com.jshz.expert.entity.ProjectSpeaker pending = new com.jshz.expert.entity.ProjectSpeaker();
        pending.setId(7L);
        when(speakers.selectList(any())).thenReturn(List.of(pending));
        ProjectExpertMapper relations = mock(ProjectExpertMapper.class);
        when(relations.selectCount(any())).thenReturn(0L);
        var schedule = new ScheduleParseService(null, speakers, projects, experts, null, null, relations, null, null, null);

        assertThrows(BusinessException.class, () -> schedule.archiveManual(55L,
                Map.of("name", "张医生", "unit", "其他医院", "existingExpertId", 42L), null, null));
        var result = schedule.archiveManual(55L,
                Map.of("name", "张医生", "unit", "测试医院", "department", "新科室", "existingExpertId", 42L), photo, null);
        assertEquals(42L, result.get("expertId"));
        assertEquals(42L, pending.getExpertId());
        assertEquals("原科室", existing.getDepartment());
        assertEquals("original.png", existing.getPhoto());
        verify(experts, never()).create(any(), any());
        verify(experts, never()).attachNewAvatar(any(), any());
    }

    @Test
    void rollbackDeletesNewAvatarIfExpertUpdateFails() {
        PhotoStorageService storage = mock(PhotoStorageService.class);
        ExpertService experts = spy(new ExpertService(storage, null, null));
        Expert expert = new Expert();
        expert.setId(42L);
        when(storage.savePhoto(photo, 42L, "photo")).thenReturn("avatar_42_123.png");
        doReturn(false).when(experts).updateById(expert);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThrows(BusinessException.class, () -> experts.attachNewAvatar(expert, photo));
            assertEquals("avatar_42_123.png", expert.getPhoto());
            for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(storage).deletePhoto("avatar_42_123.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
