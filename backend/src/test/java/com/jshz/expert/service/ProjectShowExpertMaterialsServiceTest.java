package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectSpeaker;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

class ProjectShowExpertMaterialsServiceTest {
    private final ProjectService projects = mock(ProjectService.class);
    private final ScheduleParseService schedules = mock(ScheduleParseService.class);
    private final ExpertService experts = mock(ExpertService.class);
    private final PhotoStorageService photos = mock(PhotoStorageService.class);
    private final ProjectShowExpertMaterialsService service =
            new ProjectShowExpertMaterialsService(projects, schedules, experts, photos);
    private final AuthUser user = mock(AuthUser.class);
    private final Project project = new Project();

    @BeforeAll
    static void initializeExpertMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Expert.class);
    }

    @TempDir
    Path temp;

    @Test
    void exportsOnlyMatchedExpertsDeduplicatedById() throws Exception {
        when(projects.requireProject(10L)).thenReturn(project);
        var matched = new ProjectSpeaker();
        matched.setExpertId(1L);
        var missing = new ProjectSpeaker();
        missing.setExpertId(2L);
        missing.setExpertName("缺档专家");
        when(schedules.confirmedSpeakers(10L)).thenReturn(List.of(matched, matched, new ProjectSpeaker(), missing));
        var expert = new Expert();
        expert.setId(1L);
        expert.setName("专家甲");
        expert.setProfile("正式简介");
        expert.setPhoto("avatar.png");
        expert.setContact("不得导出的联系方式");
        when(experts.list(isA(LambdaQueryWrapper.class))).thenReturn(List.of(expert));
        Path avatar = Files.write(temp.resolve("avatar.png"), new byte[]{1, 2, 3});
        when(photos.resolvePhoto("avatar.png")).thenReturn(avatar);

        var output = new ByteArrayOutputStream();
        service.prepare(10L, user).write(output);

        try (var zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8)) {
            assertEquals("头像/1.png", zip.getNextEntry().getName());
            assertArrayEquals(new byte[]{1, 2, 3}, zip.readAllBytes());
            assertEquals("专家资料.md", zip.getNextEntry().getName());
            var markdown = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(markdown.contains("## 专家甲（1）"));
            assertTrue(markdown.contains("正式简介"));
            assertTrue(markdown.contains("头像/1.png"));
            assertTrue(markdown.contains("缺档专家"));
            assertFalse(markdown.contains("不得导出的联系方式"));
            assertEquals(1, markdown.lines().filter(line -> line.equals("## 专家甲（1）")).count());
            assertNull(zip.getNextEntry());
        }
    }

    @Test
    void rejectsNonMembersBeforeReadingSpeakerOrExpertData() {
        when(projects.requireProject(10L)).thenReturn(project);
        doThrow(new BusinessException(403, "无权下载")).when(projects).requireOperable(project, user);
        assertThrows(BusinessException.class, () -> service.prepare(10L, user));
        verifyNoInteractions(schedules, experts, photos);
    }

    @Test
    void rejectsProjectsWithoutMatchedExperts() {
        when(projects.requireProject(10L)).thenReturn(project);
        when(schedules.confirmedSpeakers(10L)).thenReturn(List.of(new ProjectSpeaker()));
        assertThrows(BusinessException.class, () -> service.prepare(10L, user));
        verifyNoInteractions(experts, photos);
    }
}
