package com.jshz.expert.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2-7：知识文档从磁盘路径流式抽取/解析的单元测试。
 * 使用临时文件，不连接数据库、不启动 Spring 上下文。
 */
class KnowledgeDocStreamingParseTest {

    @Test
    void extractDocxFromPathWithoutByteArray(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sample.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(file)) {
            doc.createParagraph().createRun().setText("这是一段用于验证流式抽取的正文");
            doc.write(out);
        }

        String text = new KnowledgeDocTextExtractor().extract(file, "sample.docx");

        assertThat(text).isNotNull();
        assertThat(text).contains("流式抽取");
    }

    @Test
    void parseXlsxFromPathWithoutByteArray(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("compliance.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(file)) {
            var sheet = wb.createSheet("合规");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("项目");
            header.createCell(1).setCellValue("内容");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("测试事项");
            row.createCell(1).setCellValue("测试内容");
            wb.write(out);
        }

        ComplianceParseService.ParseResult result =
                new ComplianceParseService(null, null).parse("compliance.xlsx", "xlsx", file, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getTitle()).isEqualTo("测试事项");
    }

    @Test
    void missingPathIsReportedAsFailureNotException(@TempDir Path dir) {
        ComplianceParseService.ParseResult result =
                new ComplianceParseService(null, null).parse("missing.xlsx", "xlsx", dir.resolve("missing.xlsx"), null);

        assertThat(result.items()).isEmpty();
        assertThat(result.message()).contains("失败");
    }
}
