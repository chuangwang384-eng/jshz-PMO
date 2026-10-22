package com.jshz.expert.controller;

import com.jshz.expert.entity.Expert;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.ExpertService;
import com.jshz.expert.service.ExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * demo 兼容导出接口（正式版 V1.001，对齐 demo /api/export 系列）。
 * /api/export 员工脱敏 ZIP；/api/export/full 管理员完整 ZIP；/api/export/md 下载说明 Markdown。
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportCompatController {

    private final ExpertService expertService;
    private final ExportService exportService;

    /**
     * 员工脱敏 ZIP：脱敏 CSV + 照片原图。
     */
    @GetMapping
    public void exportMaskedZip(@AuthenticationPrincipal AuthUser authUser,
                                HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        List<Expert> experts = expertService.list();
        byte[] zip = exportService.buildZip(experts, false, "employee_zip", authUser, request);
        writeAttachment(response, zip, "experts_masked_" + LocalDate.now() + ".zip",
                MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * 管理员完整 ZIP（完整 CSV + 三类照片原图，仅 ADMIN）。
     */
    @GetMapping("/full")
    public void exportFullZip(@AuthenticationPrincipal AuthUser authUser,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        List<Expert> experts = expertService.list();
        byte[] zip = exportService.buildZip(experts, true, "full_zip", authUser, request);
        writeAttachment(response, zip, "experts_full_" + LocalDate.now() + ".zip",
                MediaType.APPLICATION_OCTET_STREAM);
    }

    /**
     * 下载说明 Markdown：?full=1 完整版（含 AI 下载指引）。
     */
    @GetMapping("/md")
    public void exportMd(@RequestParam(required = false) String full,
                         @AuthenticationPrincipal AuthUser authUser,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        List<Expert> experts = expertService.list();
        byte[] md = exportService.buildMarkdown(experts, "1".equals(full), authUser, request);
        writeAttachment(response, md, "download_guide.md",
                MediaType.parseMediaType("text/markdown;charset=UTF-8"));
    }

    private void writeAttachment(HttpServletResponse response, byte[] data, String fileName,
                                 MediaType mediaType) throws IOException {
        response.setContentType(mediaType.toString());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded);
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }
}
