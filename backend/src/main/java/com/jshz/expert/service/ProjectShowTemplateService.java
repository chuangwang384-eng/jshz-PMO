package com.jshz.expert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectShowTemplateService {
    public record Design(String name, String theme, String background, List<ProjectShowService.CoverField> fields) {}
    public record Entry(UUID id, String name) {}

    private final ProjectService projects;
    private final ObjectMapper json;
    private final Path directory;

    public ProjectShowTemplateService(ProjectService projects, ObjectMapper json,
            @Value("${expert.project.storage-dir}") String storage) {
        this.projects = projects;
        this.json = json;
        this.directory = Path.of(storage).resolve("_show_templates");
    }

    public List<Entry> list(long projectId, AuthUser user) throws IOException {
        requireReadable(projectId, user);
        if (!Files.isDirectory(directory)) return List.of();
        List<Entry> entries = new ArrayList<>();
        try (var paths = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : paths) {
                String name = path.getFileName().toString();
                UUID id = UUID.fromString(name.substring(0, name.length() - 5));
                entries.add(new Entry(id, read(id).name()));
            }
        }
        entries.sort(Comparator.comparing(Entry::name).thenComparing(Entry::id));
        return entries;
    }

    public Design get(long projectId, UUID id, AuthUser user) throws IOException {
        requireReadable(projectId, user);
        return read(id);
    }

    public synchronized Entry save(long projectId, UUID id, Design design, AuthUser user) throws IOException {
        projects.requireOperable(projects.requireProject(projectId), user);
        if (design == null || design.name() == null || design.name().isBlank() || design.name().length() > 60
                || design.theme() == null || !Set.of("gold", "tech").contains(design.theme())
                || design.fields() == null || design.fields().isEmpty()) {
            throw new BusinessException("请填写模板名称并设置封面字段");
        }
        ProjectShowService.validateCoverDesign(design.background(), design.fields());
        var keys = new java.util.HashSet<String>();
        for (var field : design.fields()) {
            if (field.key() == null || !field.key().matches("[a-zA-Z0-9_-]{1,64}") || !keys.add(field.key())
                    || field.label() == null || field.label().isBlank() || field.label().length() > 40) {
                throw new BusinessException("模板字段名称或编号无效");
            }
        }
        var fields = design.fields().stream()
                .map(field -> new ProjectShowService.CoverField(field.key(), field.label(), "", field.x(), field.y()))
                .toList();
        Files.createDirectories(directory);
        Path target = directory.resolve(id + ".json");
        Path temp = Files.createTempFile(directory, "design-", ".tmp");
        try {
            json.writeValue(temp.toFile(), new Design(design.name().strip(), design.theme(), design.background(), fields));
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        projects.operationRecord(user, "保存共享串场模板", "PROJECT", projectId);
        return new Entry(id, design.name().strip());
    }

    public synchronized void remove(long projectId, UUID id, AuthUser user) throws IOException {
        projects.requireOperable(projects.requireProject(projectId), user);
        if (Files.deleteIfExists(directory.resolve(id + ".json"))) {
            projects.operationRecord(user, "下架共享串场模板", "PROJECT", projectId);
        }
    }

    private void requireReadable(long projectId, AuthUser user) {
        projects.requireProjectReadable(projects.requireProject(projectId), user);
    }

    private Design read(UUID id) throws IOException {
        Path path = directory.resolve(id + ".json");
        if (!Files.isRegularFile(path)) throw new BusinessException("共享模板已下架");
        return json.readValue(path.toFile(), Design.class);
    }
}
