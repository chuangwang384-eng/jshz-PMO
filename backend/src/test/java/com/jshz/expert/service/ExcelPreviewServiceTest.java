package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelPreviewServiceTest {
    @TempDir Path directory;

    @Test
    void previewsVisibleSheetsAndMergesAcrossPagesForBothFormats() throws Exception {
        var service = new ExcelPreviewService();
        for (String extension : List.of("xls", "xlsx")) {
            Path path = directory.resolve("preview." + extension);
            try (var workbook = extension.equals("xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
                var sheet = workbook.createSheet("合规要求");
                var firstRow = sheet.createRow(0);
                firstRow.createCell(0).setCellValue("立项要求");
                firstRow.createCell(1).setCellValue("第一行\n第二行");
                firstRow.createCell(39).setCellValue("跨列合并");
                firstRow.createCell(41).setCellValue("右侧内容");
                sheet.addMergedRegion(new CellRangeAddress(0, 101, 0, 0));
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 39, 40));
                sheet.createRow(1).setZeroHeight(true);
                sheet.setColumnHidden(2, true);
                workbook.createSheet("隐藏");
                workbook.setSheetHidden(1, true);
                workbook.createSheet("第二张表");
                try (var output = Files.newOutputStream(path)) { workbook.write(output); }
            }
            byte[] original = Files.readAllBytes(path);
            var initial = service.read(path, null, 0, 0);
            assertEquals(List.of("合规要求", "第二张表"), initial.sheets().stream().map(ExcelPreviewService.Worksheet::name).toList());
            assertEquals(2, initial.rowPages());
            assertEquals(2, initial.columnPages());
            assertEquals(new ExcelPreviewService.Cell("立项要求", 99, 1), initial.rows().get(0).cells().get(0));
            assertEquals("第一行\n第二行", initial.rows().get(0).cells().get(1).text());
            assertFalse(initial.columns().stream().anyMatch(column -> column.label().equals("C")));
            assertEquals(new ExcelPreviewService.Cell("立项要求", 2, 1), service.read(path, 0, 1, 0).rows().get(0).cells().get(0));
            assertEquals(List.of("跨列合并", "右侧内容"), service.read(path, 0, 0, 1).rows().get(0).cells().stream()
                    .map(ExcelPreviewService.Cell::text).toList());
            assertThrows(BusinessException.class, () -> service.read(path, 1, 0, 0));
            assertArrayEquals(original, Files.readAllBytes(path));
        }
    }
}
