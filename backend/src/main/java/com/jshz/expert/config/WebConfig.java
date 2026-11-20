package com.jshz.expert.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Web 配置（正式版 V1.001）。
 * 将照片库目录映射为 /api/files/** 静态资源（带鉴权，登录后可访问照片）。
 * 临时照片目录（审批流预上传）映射为 /api/files-tmp/**（同为登录后可访问），
 * 供审批对比弹窗预览待审批单的「变更后」新照片。
 * 专家资料目录（医生证等资质资料，2026-11-20）映射为 /api/files-doc/**（登录后可访问）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${expert.photo.storage-dir}")
    private String storageDir;

    @Value("${expert.photo.temp-dir}")
    private String tempDir;

    @Value("${expert.document.storage-dir}")
    private String documentDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String normalized = storageDir.replace('\\', '/');
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        // 确保目录存在
        File dir = new File(normalized);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        registry.addResourceHandler("/api/files/**")
                .addResourceLocations("file:" + normalized);

        String normalizedTmp = tempDir.replace('\\', '/');
        if (!normalizedTmp.endsWith("/")) {
            normalizedTmp = normalizedTmp + "/";
        }
        File tmpDir = new File(normalizedTmp);
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        registry.addResourceHandler("/api/files-tmp/**")
                .addResourceLocations("file:" + normalizedTmp);

        String normalizedDoc = documentDir.replace('\\', '/');
        if (!normalizedDoc.endsWith("/")) {
            normalizedDoc = normalizedDoc + "/";
        }
        File docDir = new File(normalizedDoc);
        if (!docDir.exists()) {
            docDir.mkdirs();
        }
        registry.addResourceHandler("/api/files-doc/**")
                .addResourceLocations("file:" + normalizedDoc);
    }
}
