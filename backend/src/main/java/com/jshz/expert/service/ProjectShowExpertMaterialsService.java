package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.common.FileNames;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.ProjectSpeaker;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class ProjectShowExpertMaterialsService {
    private static final Set<String> AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp");

    private final ProjectService projects;
    private final ScheduleParseService schedules;
    private final ExpertService experts;
    private final PhotoStorageService photos;

    public Archive prepare(Long projectId, AuthUser user) {
        var project = projects.requireProject(projectId);
        projects.requireProjectReadable(project, user);
        projects.requireProjectMember(project, user);

        Map<Long, ProjectSpeaker> speakers = new LinkedHashMap<>();
        for (var speaker : schedules.confirmedSpeakers(projectId)) {
            if (speaker.getExpertId() != null) {
                speakers.putIfAbsent(speaker.getExpertId(), speaker);
            }
        }
        if (speakers.isEmpty()) {
            throw new BusinessException(404, "暂无已确认且关联专家档案的参会专家");
        }

        Map<Long, Expert> byId = experts.list(new LambdaQueryWrapper<Expert>()
                        .select(Expert::getId, Expert::getName, Expert::getProfile, Expert::getPhoto)
                        .in(Expert::getId, speakers.keySet())).stream()
                .collect(Collectors.toMap(Expert::getId, Function.identity()));

        return output -> {
            try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                var markdown = new StringBuilder("# 串场专家资料\n\n");
                for (var entry : speakers.entrySet()) {
                    Long id = entry.getKey();
                    Expert expert = byId.get(id);
                    if (expert == null) {
                        markdown.append("## ").append(entry.getValue().getExpertName())
                                .append("（").append(id).append("）\n\n")
                                .append("专家档案不可用，头像和简介缺失。\n\n");
                        continue;
                    }
                    String avatar = writeAvatar(zip, expert);
                    markdown.append("## ").append(expert.getName()).append("（").append(id).append("）\n\n")
                            .append("头像：").append(avatar == null ? "缺失" : "`" + avatar + "`")
                            .append("\n\n简介：\n\n")
                            .append(StringUtils.hasText(expert.getProfile()) ? expert.getProfile() : "缺失")
                            .append("\n\n");
                }
                zip.putNextEntry(new ZipEntry("专家资料.md"));
                zip.write(markdown.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        };
    }

    private String writeAvatar(ZipOutputStream zip, Expert expert) throws IOException {
        String photo = expert.getPhoto();
        if (!StringUtils.hasText(photo) || !photo.equals(FileNames.sanitize(photo))) {
            return null;
        }
        String extension = FileNames.extension(photo).toLowerCase(Locale.ROOT);
        if (!AVATAR_EXTENSIONS.contains(extension)) {
            return null;
        }
        var path = photos.resolvePhoto(photo);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        String entryName = "头像/" + expert.getId() + "." + extension;
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(path, zip);
        zip.closeEntry();
        return entryName;
    }

    @FunctionalInterface
    public interface Archive {
        void write(OutputStream output) throws IOException;
    }
}
