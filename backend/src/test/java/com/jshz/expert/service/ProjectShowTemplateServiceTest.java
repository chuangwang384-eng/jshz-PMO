package com.jshz.expert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectShowTemplateServiceTest {
    @TempDir Path storage;

    @Test
    void sharedTemplateReusesLayoutAcrossProjectsWithoutProjectText() throws Exception {
        ProjectService projects = mock(ProjectService.class);
        AuthUser user = mock(AuthUser.class);
        Project first = new Project();
        Project second = new Project();
        first.setId(1L);
        second.setId(2L);
        when(projects.requireProject(1L)).thenReturn(first);
        when(projects.requireProject(2L)).thenReturn(second);
        var library = new ProjectShowTemplateService(projects, new ObjectMapper(), storage.toString());
        var key = UUID.randomUUID();
        var field = new ProjectShowService.CoverField("title", "主标题", "项目专属文字", 55.0, 31.0);
        var design = new ProjectShowTemplateService.Design("临床会议", "gold", "", List.of(field));
        library.save(1L, key, design, user);
        verify(projects).requireOperable(first, user);

        var saved = library.get(2L, key, user);
        verify(projects).requireProjectReadable(second, user);
        assertEquals("", saved.fields().get(0).value());
        assertEquals(55.0, saved.fields().get(0).x());
        assertEquals(List.of(new ProjectShowTemplateService.Entry(key, "临床会议")), library.list(2L, user));

        doThrow(new BusinessException(403, "只读")).when(projects).requireOperable(second, user);
        assertThrows(BusinessException.class, () -> library.remove(2L, key, user));
        assertEquals(1, library.list(1L, user).size());
        library.remove(1L, key, user);
        assertTrue(library.list(2L, user).isEmpty());
    }
}
