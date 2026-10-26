package com.jshz.expert.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * 知识文档正文抽取（C1 内容检索）。
 * pdf → PDFBox；doc → HWPF(WordExtractor)；docx → XWPF；图片等返回 null（不入内容检索）。
 * 抽取失败返回 null，不阻断上传/更新主流程。
 */
@Component
public class KnowledgeDocTextExtractor {

    /** 入库正文长度上限（字符）：大文档截断，兼顾 MEDIUMTEXT 容量与检索意义 */
    private static final int MAX_CHARS = 200_000;

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String ext = extensionOf(file.getOriginalFilename());
        try {
            String text = switch (ext) {
                case "pdf" -> extractPdf(file.getBytes());
                case "doc" -> extractDoc(file.getBytes());
                case "docx" -> extractDocx(file.getBytes());
                default -> null;
            };
            if (!StringUtils.hasText(text)) {
                return null;
            }
            String normalized = text.replaceAll("\\s+", " ").trim();
            return normalized.length() > MAX_CHARS ? normalized.substring(0, MAX_CHARS) : normalized;
        } catch (Exception e) {
            // 抽取失败不阻断主流程：该文档仅不支持内容检索
            return null;
        }
    }

    private String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument doc = PDDocument.load(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String extractDoc(byte[] bytes) throws Exception {
        try (InputStream in = new ByteArrayInputStream(bytes);
             WordExtractor extractor = new WordExtractor(in)) {
            return extractor.getText();
        }
    }

    private String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
