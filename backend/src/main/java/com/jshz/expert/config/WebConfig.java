package com.jshz.expert.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Web 配置（正式版 V1.001）。
 * 将照片库目录映射为 /api/files/** 静态资源（带鉴权，登录后可访问照片）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${expert.photo.storage-dir}")
    private String storageDir;

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
    }
}
