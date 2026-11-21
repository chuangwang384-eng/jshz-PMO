package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProjectStageDeliverableTest {
    @Test
    void financeWorkbookKeepsOnlyTheSelectedBusinessSheetAndDictionary() throws Exception {
        byte[] bytes = ProjectFinanceWorkbook.generate("PLATFORM", Map.of(
                "meetingName", "联调会议", "meetingDate", "2026-11-21", "ourCompany", "河南景帅",
                "subType", "单会立项", "sponsorAmount", 1200.50));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex("Sheet4")));
            var sheet = workbook.getSheet("平台类项目");
            assertTrue(sheet.getNumMergedRegions() > 0);
            assertEquals("联调会议", sheet.getRow(2).getCell(10).getStringCellValue());
            assertEquals(1200.50, sheet.getRow(2).getCell(11).getNumericCellValue());
            assertNull(workbook.getSheet("直付类项目"));
        }
    }

    @Test
    void otherFinanceLayoutsWriteTheProjectNameToTheirOwnColumns() throws Exception {
        Map<String, Object> fields = Map.of("meetingName", "联调会议", "meetingDate", "2026-11-21",
                "subType", "有合同", "ourCompany", "景帅", "companyName", "景帅", "province", "河南");
        for (String type : List.of("DIRECT_PAY", "KEY")) {
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(ProjectFinanceWorkbook.generate(type, fields)))) {
                var sheet = workbook.getSheetAt(0);
                int column = type.equals("KEY") ? 5 : 10;
                assertEquals("联调会议", sheet.getRow(2).getCell(column).getStringCellValue());
            }
        }
    }

    @Test
    void incompleteFinanceFormCannotGenerateDeliverable() {
        assertThrows(BusinessException.class, () -> ProjectFinanceWorkbook.generate("DIRECT_PAY", Map.of()));
    }

    @Test
    void htmlExportEmbedsOnlySupportedImagesAndEscapesSlideText() {
        var slide = new ProjectShowService.Slide("讲题", "<img src=x onerror=alert(1)>", "主题", "");
        var deck = new ProjectShowService.Deck("data:image/png;base64,AAAA", List.of(slide), null, null);
        String html = ProjectShowService.render(deck);
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertFalse(html.contains("<img src=x"));
        assertTrue(html.contains("ArrowRight"));
        assertThrows(BusinessException.class, () -> ProjectShowService.render(
                new ProjectShowService.Deck("data:image/svg+xml;base64,AAAA", List.of(slide), null, null)));
    }

    @Test
    void deckRendersThemeVariantsStepsAndClampsFontScale() {
        var slide = new ProjectShowService.Slide("会议议程", "议程", "09:00 开幕\n09:10 讲题一", "");
        String html = ProjectShowService.render(
                new ProjectShowService.Deck("data:image/png;base64,AAAA", List.of(slide), "tech", 2.0));
        assertTrue(html.contains("t-tech"));
        assertTrue(html.contains("k-on"));
        assertTrue(html.contains("data-step"));
        assertTrue(html.contains("--fs:1.4"));
        var fallback = new ProjectShowService.Deck(null, List.of(slide), "nope", null);
        assertTrue(ProjectShowService.render(fallback).contains("t-gold"));
        assertTrue(ProjectShowService.render(fallback).contains("k-agenda"));
    }
}
