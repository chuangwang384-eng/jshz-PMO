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
    void slideLayoutSurvivesPortraitResolutionAndEscapesKeys() {
        var adjustment = new ProjectShowService.ElementLayout("avatar-0", 125.0, -30.0, 130.0, "");
        var title = new ProjectShowService.ElementLayout("title", 25.0, 15.0, 110.0, "kai");
        var person = new ProjectShowService.Person("大会主席", "测试专家", "教授", "", "ph:0");
        var slide = new ProjectShowService.Slide("", "开场", "", "", "section", List.of("01"),
                List.of(person), null, 0, List.of(adjustment, title));
        String photo = "data:image/png;base64,AAAA";
        var deck = new ProjectShowService.Deck("", List.of(slide), "gold", null, List.of(photo), null, photo);
        String html = ProjectShowService.render(deck);
        assertTrue(html.contains("data-placement=\"avatar-0\" data-x=\"125.0\""));
        assertTrue(html.contains("data-font=\"kai\""));
        assertTrue(html.contains(".sec-p-avatar > .avatar-frame{border:0"));
        assertThrows(BusinessException.class, () -> ProjectShowService.render(new ProjectShowService.Deck("",
                List.of(new ProjectShowService.Slide("", "开场", "", "", "section", List.of("01"),
                        List.of(person), null, 0, List.of(new ProjectShowService.ElementLayout(
                                "title\" injected", 0.0, 0.0, 100.0, "")))), "gold", null, List.of(photo))));
    }

    @Test
    void exportsResizedCoverWithOfflineFontAndAdjustableAvatarFrame() {
        var cover = new ProjectShowService.Slide("", "封面", "", "", "cover", null, null, null);
        var field = new ProjectShowService.CoverField("title", "主标题", "会议", 50.0, 42.0,
                37.5, 9.0, 120.0, "ma-shan");
        var deck = new ProjectShowService.Deck("", List.of(cover), "gold", null, null, List.of(field),
                "data:image/png;base64,AAAA", new ProjectShowService.FrameFit(74.0, 3.0, -5.0));
        String html = ProjectShowService.render(deck);
        assertTrue(html.contains(";width:37.5%;height:9.0%;font-size:"));
        assertTrue(html.contains("font-family:'PMO Ma Shan'"));
        assertTrue(html.contains("font-src data:"));
        assertTrue(html.contains("src:url(data:font/woff2;base64,"));
        assertTrue(html.contains("--frame-size:74.0%;--frame-x:53.0%;--frame-y:45.0%"));
        assertTrue(html.contains("SIL OPEN FONT LICENSE") || html.contains("SIL Open Font License"));
    }

    @Test
    void customCoverCoordinatesAndPortraitFrameAreRenderedSafely() {
        var cover = new ProjectShowService.Slide("", "封面", "", "", "cover", null, null, null);
        var section = new ProjectShowService.Slide("", "开场", "", "", "section", List.of("01"),
                List.of(new ProjectShowService.Person("主席", "测试专家", "教授", "", "ph:0")), null);
        String image = "data:image/png;base64,AAAA";
        var field = new ProjectShowService.CoverField("title", "主标题", "<会议>", 31.5, 22.0);
        String html = ProjectShowService.render(new ProjectShowService.Deck("", List.of(cover, section),
                "gold", null, List.of(image), List.of(field), image));
        assertTrue(html.contains("left:31.5%;top:22.0%"));
        assertTrue(html.contains("&lt;会议&gt;"));
        assertTrue(html.contains("id=\"avatar-frame\""));
        assertTrue(html.contains(".persons[0].title\">教授</small>"));
        var invalid = new ProjectShowService.CoverField("title", "主标题", "会议", Double.NaN, 22.0);
        assertThrows(BusinessException.class, () -> ProjectShowService.validateCoverDesign("", List.of(invalid)));
    }

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
        var slide = new ProjectShowService.Slide("讲题", "<img src=x onerror=alert(1)>", "主题", "", null, null, null, null);
        var deck = new ProjectShowService.Deck("data:image/png;base64,AAAA", List.of(slide), null, null, null);
        String html = ProjectShowService.render(deck);
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertFalse(html.contains("<img src=x"));
        assertTrue(html.contains("ArrowRight"));
        assertThrows(BusinessException.class, () -> ProjectShowService.render(
                new ProjectShowService.Deck("data:image/svg+xml;base64,AAAA", List.of(slide), null, null, null)));
    }

    @Test
    void deckRendersThemeVariantsStepsAndClampsFontScale() {
        var slide = new ProjectShowService.Slide("会议议程", "议程", "09:00 开幕\n09:10 讲题一", "", null, null, null, null);
        String html = ProjectShowService.render(
                new ProjectShowService.Deck("data:image/png;base64,AAAA", List.of(slide), "tech", 2.0, null));
        assertTrue(html.contains("t-tech"));
        assertTrue(html.contains("k-on"));
        assertTrue(html.contains("data-step"));
        assertTrue(html.contains("--fs:1.4"));
        var fallback = new ProjectShowService.Deck(null, List.of(slide), "nope", null, null);
        assertTrue(ProjectShowService.render(fallback).contains("t-gold"));
        assertTrue(ProjectShowService.render(fallback).contains("k-agenda"));
    }

    @Test
    void structuredPageKindsRenderAndLegacyDeckStaysCompatible() {
        var png = "data:image/png;base64,AAAA";
        var person = new ProjectShowService.Person("大会主席", "齐秀恒", "教授", "河北中石油中心医院", png);
        var cover = new ProjectShowService.Slide("中国医药教育协会", "聚例辉煌", "临床应用研讨 · 多学科病例讨论项目", "",
                "cover", List.of("会议时间|2026-11-18", "会议地点|郑州", "大会主席|齐秀恒 教授"), null, null);
        var section = new ProjectShowService.Slide("", "主席致辞", "", "", "section", List.of("01"), List.of(person), null);
        var bio = new ProjectShowService.Slide("", "齐秀恒 教授", "", "", "bio", null, List.of(person), null);
        var resume = new ProjectShowService.Slide("个人简历", "齐秀恒 教授", "", "",
                "resume", List.of("河北省抗癌协会理事", "肿瘤中心主任委员"), List.of(person), null);
        var panel = new ProjectShowService.Slide("多学科讨论", "多学科讨论", "", "", "panel",
                null, List.of(person, new ProjectShowService.Person("讨论嘉宾", "董桂兰", "教授", "唐山市人民医院", "")), null);
        var legacy = new ProjectShowService.Slide("会议环节", "旧结构页", "正文行", "", null, null, null, null);
        String html = ProjectShowService.render(
                new ProjectShowService.Deck("", List.of(cover, section, bio, resume, panel, legacy), "gold", null, null));
        assertTrue(html.contains("k-cover"));
        assertTrue(html.contains("cover-title"));
        assertTrue(html.contains("ci-k"));
        assertTrue(html.contains("sec-num"));
        assertTrue(html.contains("sec-wm"));
        assertTrue(html.contains("photo-card"));
        assertTrue(html.contains("role-badge"));
        assertTrue(html.contains("rp2-list"));
        assertTrue(html.contains("rp2-avatar"));
        assertTrue(html.contains("sec-people"));
        assertTrue(html.contains("data-edit=\"p0.title\""));
        assertTrue(html.contains("data-edit=\"p3.items[0]\""));
        assertTrue(html.contains("p-card"));
        assertTrue(html.contains("p-avatar-word"));
        assertTrue(html.contains("k-content"));
        assertFalse(html.contains("k-section-crumb"));
        // 旧扁平结构（无 kind）回退启发式渲染：单张 deck 的首页按启发式归为封面
        String legacyOnly = ProjectShowService.render(new ProjectShowService.Deck("", List.of(legacy), "gold", null, null));
        assertTrue(legacyOnly.contains("k-cover"));
    }

    @Test
    void photoPoolReferencesResolveToSingleEmbeddedImage() {
        var png = "data:image/png;base64,AAAA";
        var person = new ProjectShowService.Person("大会主席", "齐秀恒", "教授", "河北中石油中心医院", "ph:0");
        var bio = new ProjectShowService.Slide("", "齐秀恒 教授", "", "", "bio", null, List.of(person), null);
        var resume = new ProjectShowService.Slide("个人简历", "齐秀恒 教授", "", "",
                "resume", List.of("河北省抗癌协会理事"), List.of(person), null);
        var deck = new ProjectShowService.Deck("", List.of(bio, resume), "gold", null, List.of(png));
        var resume2 = new ProjectShowService.Slide("个人简历", "齐秀恒 教授", "", "", "resume", List.of("条目一"), List.of(person), 1.2);
        var deck2 = new ProjectShowService.Deck("", List.of(resume2), "gold", null, List.of(png));
        assertTrue(ProjectShowService.render(deck2).contains("--rfs:1.2"));
        String html = ProjectShowService.render(deck);
        // 池中照片解析后注入页面：出场页与简历页共享同一张池图，共出现两次
        assertEquals(2, html.split("data:image/png;base64,AAAA", -1).length - 1);
        assertThrows(BusinessException.class, () -> ProjectShowService.render(
                new ProjectShowService.Deck("", List.of(bio), "gold", null,
                        List.of("data:image/svg+xml;base64,AAAA"))));
    }
}
