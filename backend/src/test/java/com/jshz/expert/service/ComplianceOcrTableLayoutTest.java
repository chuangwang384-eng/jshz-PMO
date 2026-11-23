package com.jshz.expert.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 需求20 回归：OCR 表格不再写死 columns.size()==4。
 * 3 列（4 竖线）标准格式保持兼容；5 列等宽表按表头语义识别「项目/标准」列；
 * 表头判读失败且非标准格式时返回空，避免生成错误事项草稿。
 */
class ComplianceOcrTableLayoutTest {

    /** PP-OCR TSV 行：block/par/line 用于同行合并，x/y 为框中心点。 */
    private static String word(int line, int x, int y, String text) {
        return "5\t1\t1\t1\t" + line + "\t1\t" + (x - 5) + "\t" + (y - 5) + "\t10\t10\t95.00\t" + text;
    }

    private static String tsv(List<String> rows) {
        return String.join("\n", rows);
    }

    @Test
    void standardFourLineTableStillProducesRows() {
        var grid = new ComplianceOcrTable.Grid(List.of(0, 20, 40, 60), List.of(0, 50, 150, 250));
        String tsv = tsv(List.of(
                word(1, 20, 10, "序号"), word(2, 100, 10, "项目"), word(3, 200, 10, "标准"),
                word(4, 20, 30, "1"), word(5, 100, 30, "缴纳保证金"), word(6, 200, 30, "合同签订前"),
                word(7, 20, 50, "2"), word(8, 100, 50, "现场照片"), word(9, 200, 50, "每场留存")));
        var table = ComplianceOcrTable.blocks(grid, tsv, "PDF第1页");
        var rows = table.blocks().stream().filter(b -> "TABLE_ROW".equals(b.kind())).toList();
        assertEquals(2, rows.size());
        assertEquals("缴纳保证金 | 合同签订前", rows.get(0).text());
        assertEquals("现场照片 | 每场留存", rows.get(1).text());
        assertEquals(1, table.grid().titleColumn());
        assertEquals(2, table.grid().bodyColumn());
    }

    @Test
    void fiveColumnTableUsesHeaderSemantics() {
        // 序号|类别|项目|标准|备注（6 竖线，原逻辑直接判为无表格）
        var grid = new ComplianceOcrTable.Grid(List.of(0, 20, 40, 60),
                List.of(0, 40, 100, 160, 220, 280));
        String tsv = tsv(List.of(
                word(1, 20, 10, "序号"), word(2, 70, 10, "类别"), word(3, 130, 10, "项目"),
                word(4, 190, 10, "标准"), word(5, 250, 10, "备注"),
                word(6, 20, 30, "1"), word(7, 70, 30, "会务"), word(8, 130, 30, "场地租金"),
                word(9, 190, 30, "每场≤5000元"), word(10, 250, 30, "含税"),
                word(11, 20, 50, "2"), word(12, 70, 50, "交通"), word(13, 130, 50, "市内交通"),
                word(14, 190, 50, "据实报销"), word(15, 250, 50, "")));
        var table = ComplianceOcrTable.blocks(grid, tsv, "PDF第2页");
        var rows = table.blocks().stream().filter(b -> "TABLE_ROW".equals(b.kind())).toList();
        assertEquals(2, rows.size(), "5 列表格应识别出数据行");
        assertEquals("场地租金 | 每场≤5000元", rows.get(0).text());
        assertEquals("市内交通 | 据实报销", rows.get(1).text());
        assertEquals(2, table.grid().titleColumn());
        assertEquals(3, table.grid().bodyColumn());
    }

    @Test
    void threeColumnTableVariantIsSupported() {
        // 序号|项目|标准|备注（5 竖线）：标题列 1、正文列 2
        var grid = new ComplianceOcrTable.Grid(List.of(0, 20, 40, 60), List.of(0, 40, 120, 200, 280));
        String tsv = tsv(List.of(
                word(1, 20, 10, "序号"), word(2, 80, 10, "项目"), word(3, 160, 10, "标准"), word(4, 240, 10, "备注"),
                word(5, 20, 30, "1"), word(6, 80, 30, "劳务标准"), word(7, 160, 30, "院士≤5000元/次"),
                word(8, 240, 30, "")));
        var table = ComplianceOcrTable.blocks(grid, tsv, "PDF第3页");
        var rows = table.blocks().stream().filter(b -> "TABLE_ROW".equals(b.kind())).toList();
        assertEquals(1, rows.size());
        assertEquals("劳务标准 | 院士≤5000元/次", rows.get(0).text());
    }

    @Test
    void unresolvableWideTableProducesNoDrafts() {
        var grid = new ComplianceOcrTable.Grid(List.of(0, 20, 40, 60), List.of(0, 40, 100, 160, 220, 280));
        String tsv = tsv(List.of(
                word(1, 20, 10, "序号"), word(2, 70, 10, "甲"), word(3, 130, 10, "乙"),
                word(4, 190, 10, "丙"), word(5, 250, 10, "丁"),
                word(6, 20, 30, "1"), word(7, 70, 30, "值一"), word(8, 130, 30, "值二"),
                word(9, 190, 30, "值三"), word(10, 250, 30, "值四")));
        var table = ComplianceOcrTable.blocks(grid, tsv, "PDF第4页");
        assertTrue(table.isEmpty(), "表头无法判读时不得按固定列位生成草稿");
    }

    @Test
    void cellColumnResolution() {
        assertArrayEquals(new int[]{1, 2}, ComplianceOcrTable.resolveCellColumns(List.of("序号", "项目", "标准")));
        assertArrayEquals(new int[]{1, 2}, ComplianceOcrTable.resolveCellColumns(List.of("序号", "项目", "标准", "备注")));
        assertArrayEquals(new int[]{2, 3}, ComplianceOcrTable.resolveCellColumns(List.of("序号", "类别", "项目", "标准")));
        assertArrayEquals(new int[]{1, 2}, ComplianceOcrTable.resolveCellColumns(List.of("序号", "事项", "内容要求", "备注")));
        assertNull(ComplianceOcrTable.resolveCellColumns(List.of("序号", "甲", "乙", "丙")));
        assertNull(ComplianceOcrTable.resolveCellColumns(List.of("序号")));
    }

    @Test
    void blankRowsAreSkippedAndPartialRowsFlagged() {
        var grid = new ComplianceOcrTable.Grid(List.of(0, 20, 40, 60, 80), List.of(0, 50, 150, 250));
        List<String> rows = new ArrayList<>(List.of(
                word(1, 20, 10, "序号"), word(2, 100, 10, "项目"), word(3, 200, 10, "标准"),
                // 第 1 行完整
                word(4, 20, 30, "1"), word(5, 100, 30, "缴纳保证金"), word(6, 200, 30, "合同签订前"),
                // 第 2 行整行空白（应跳过，不触发整页重跑）
                // 第 3 行缺正文（应标 PAGE 等待整页重试）
                word(7, 20, 70, "3"), word(8, 100, 70, "现场照片")));
        var table = ComplianceOcrTable.blocks(grid, tsv(rows), "PDF第5页");
        assertEquals(1, table.blocks().stream().filter(b -> "TABLE_ROW".equals(b.kind())).count());
        assertEquals(1, table.blocks().stream().filter(b -> "PAGE".equals(b.kind())).count());
        assertEquals(0, table.blocks().stream().filter(b -> "PDF_PAGE".equals(b.kind())).count());
    }
}
