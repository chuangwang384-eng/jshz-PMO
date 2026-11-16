package com.jshz.expert.service;

import com.jshz.expert.entity.ComplianceItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 合规 OCR+LLM 事项提取（Owner 2026-11-10）：Ollama 不可用时 llmExtract 返回 null
 * （调用方回退规则切分），且不回退路径不得抛异常。规则回退行为不变。
 */
class ComplianceLlmExtractTest {

    private final ComplianceParseService service = new ComplianceParseService(null, null);

    @Test
    void llmExtractReturnsNullWhenOllamaUnavailable() {
        assertNull(service.llmExtract("某合规文档正文：必须缴纳保证金，签订安全协议。"));
        assertNull(service.llmExtract(null));
        assertNull(service.llmExtract("  "));
    }

    @Test
    void ruleFallbackStillProducesDraftItems() {
        String text = "缴纳会议保证金并及时签署合同\n现场需要准备签到表与照片留存\n会后完成核销归档";
        ComplianceParseService.ParseResult r = service.parse("doc.pdf", "pdf",
                text.getBytes(java.nio.charset.StandardCharsets.UTF_8), text);
        assertFalse(r.items().isEmpty(), "规则回退应产出事项");
        assertTrue(r.items().stream().allMatch(i -> Integer.valueOf(1).equals(i.getIsDraft())), "解析产物必须为草稿");
        assertTrue(r.items().stream().allMatch(i -> "MEDIUM".equals(i.getPriority())));
    }

    @Test
    void blankTextReportsManualEntry() {
        ComplianceParseService.ParseResult r = service.parse("empty.pdf", "pdf", "   ".getBytes(java.nio.charset.StandardCharsets.UTF_8), "   ");
        assertTrue(r.items().isEmpty());
        assertTrue(r.message().contains("手动录入"));
    }

    @Test
    void unreadableTableLineIsFlaggedWithoutRemovingValidAmounts() {
        var raster = new java.awt.image.BufferedImage(5, 5, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var grid = new ComplianceOcrTable.Grid(List.of(0, 1, 2), List.of(0, 1, 2, 3));
        var row = new KnowledgeDocTextExtractor.SourceBlock("PDF第2页表格第1项",
                "专家讲课费 | 院士：<5000元/次\nSEV RUE:<10000/*", "TABLE_ROW");
        String result = ComplianceOcrTable.refineRows(raster, grid, List.of(row)).get(0).text();
        assertTrue(result.contains("<5000元/次"));
        assertTrue(result.contains("请核对原文"));
        assertFalse(result.contains("10000"));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "COMPLIANCE_SAMPLE_PDF", matches = ".+")
    void realPdfRetainsMeetingRestrictions() {
        var file = java.nio.file.Path.of(System.getenv("COMPLIANCE_SAMPLE_PDF"));
        var parser = new ComplianceParseService(null, new KnowledgeDocTextExtractor());
        var result = parser.parse(file.getFileName().toString(), "pdf", file, null);
        var lines = result.items().stream().map(ComplianceItem::getContent)
                .map(text -> text.replaceAll("\\s+", "")).toList();
        assertEquals(1, lines.stream().filter(line -> line.contains("不予立项") && line.contains("线下")
                && line.contains("70%") && line.contains("50%") && line.contains("30%")).count());
        assertEquals(1, lines.stream().filter(line -> line.contains("1:5") && line.contains("劳务标准要求")).count());
        assertEquals(1, lines.stream().filter(line -> line.contains("线上回放") && line.contains("商品名称")).count());
        assertEquals(1, lines.stream().filter(line -> line.contains("专家讲课费")
                && line.contains("5000") && line.contains("3000") && line.contains("2000") && line.contains("1000")).count());
        assertEquals(1, lines.stream().filter(line -> line.contains("主持费用")
                && line.contains("5000") && line.contains("3000") && line.contains("2000")).count());
        assertTrue(result.items().stream().anyMatch(item -> item.getTitle().equals("劳务专家与听众比例及劳务标准")));
        assertTrue(result.items().stream().anyMatch(item -> item.getTitle().equals("线上会议回放及课件要求")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("SEVRUE") || line.contains("/*")));
        assertTrue(result.items().stream().noneMatch(item -> item.getTitle().startsWith("北京融和医学发展基金会")));
        assertTrue(result.items().stream().anyMatch(item -> item.getTitle().equals("同天同场会议劳务费计发规则")));
        if ("true".equals(System.getenv("COMPLIANCE_PPOCR_EXPECTED"))) {
            assertTrue(lines.stream().anyMatch(line -> line.contains("专家讲课费")
                    && line.contains("<5000元/次") && line.contains("≤3000元/次")
                    && line.contains("≤2000元/次") && line.contains("≤1000元/次")));
            assertTrue(lines.stream().anyMatch(line -> line.contains("PPT设计制作") && line.contains("200元/页")));
        }
        System.out.println("Actual PDF candidate count: " + lines.size());
    }
}
