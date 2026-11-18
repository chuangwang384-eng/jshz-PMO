package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 简历字段多策略解析：标签行/机构词表/职称词表/正则；畸形输入不抛错。 */
class ResumeParseServiceTest {
    private final ResumeParseService service = new ResumeParseService(new OllamaService(), new PhotoMatteService());

    private Map<String, String> parse(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        service.extractFields(text, fields);
        return fields;
    }

    @Test void labelLinesAreExtracted() {
        Map<String, String> fields = parse("个人简历\n姓名：张晓丽\n工作单位：山东省立第三医院\n科室：教育培训部\n职称：主任医师\n联系电话：13812345678\n");
        assertEquals("张晓丽", fields.get("姓名"));
        assertEquals("山东省立第三医院", fields.get("单位"));
        assertEquals("教育培训部", fields.get("科室"));
        assertEquals("主任医师", fields.get("职称"));
        assertEquals("13812345678", fields.get("联系方式"));
    }

    @Test void contextFallbackWithoutLabels() {
        Map<String, String> fields = parse("王建平\n苏州大学附属第一医院\n肿瘤科\n主任医师\n手机：13999998888\n");
        assertEquals("王建平", fields.get("姓名"));
        assertEquals("苏州大学附属第一医院", fields.get("单位"));
        assertEquals("肿瘤科", fields.get("科室"));
        assertEquals("主任医师", fields.get("职称"));
        assertEquals("13999998888", fields.get("联系方式"));
    }

    @Test void firstNameLineNotTreatedAsNameWhenGeneric() {
        Map<String, String> fields = parse("个人简历\n某医院\n");
        assertFalse(fields.containsKey("姓名"));
    }

    @Test void titleWordBoundaryAvoidsSubstring() {
        // “主管护师”中包含“护师”，词边界判断应命中更长的职称或跳过子串
        Map<String, String> fields = parse("李四\n某医院\n主管护师\n");
        assertNotEquals("护师", fields.get("职称"));
    }

    @Test void emailFallbackForContact() {
        Map<String, String> fields = parse("姓名：王小明\n邮箱：xiaoming@hospital.cn\n");
        assertEquals("xiaoming@hospital.cn", fields.get("联系方式"));
    }

    @Test void malformedTextReturnsEmptyWithoutThrowing() {
        Map<String, String> fields = parse("\n\n   \n\t\t\n");
        assertTrue(fields.isEmpty());
    }

    @Test void parseRejectsBadExtensionAndEmpty() {
        assertThrows(BusinessException.class, () -> service.parse("a.exe", new byte[]{1}));
        assertThrows(BusinessException.class, () -> service.parse("a.pdf", new byte[0]));
        assertThrows(BusinessException.class, () -> service.parse("a.pdf", new byte[30 * 1024 * 1024 + 1]));
    }

    @Test void filenameFallbackSplitsJoinedHospitalAndStripsPersonalIntroduction() {
        assertEquals("徐亮", service.nameFromFileName("安阳市肿瘤医院徐亮.pptx"));
        assertEquals("刘起鹏", service.nameFromFileName("刘起鹏个人简介.pptx"));
        assertNull(service.nameFromFileName("安阳市肿瘤医院.pptx"));
    }

    @Test void pptxWithUnsignedShapeIdStillExtractsTextAndPhoto() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var presentation = new XMLSlideShow()) {
            var slide = presentation.createSlide();
            var box = slide.createTextBox();
            box.setText("姓名：王小明\n工作单位：苏州大学附属第一医院");
            var png = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(120, 160, BufferedImage.TYPE_INT_RGB), "png", png);
            slide.createPicture(presentation.addPicture(png.toByteArray(), PictureData.PictureType.PNG));
            ((CTShape) box.getXmlObject()).getNvSpPr().getCNvPr().setId(2147484674L);
            presentation.write(output);
        }
        var parser = new ResumeParseService(new OllamaService() {
            @Override public Map<String, String> extractResumeFields(String text) {
                return Map.of();
            }
        }, null);
        var result = parser.parse("简历.pptx", output.toByteArray(), false);
        assertEquals("王小明", result.fields().get("姓名"));
        assertEquals("苏州大学附属第一医院", result.fields().get("单位"));
        assertEquals(1, result.photoCandidates().size());
    }

    @Test void contentOnlyParsingDoesNotUseFilenameForMissingName() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("工作单位：示例医院");
            document.write(output);
            byte[] bytes = output.toByteArray();
            assertEquals("张晓丽", service.parse("张晓丽简历.docx", bytes).fields().get("姓名"));
            assertFalse(service.parse("张晓丽简历.docx", bytes, false).fields().containsKey("姓名"));
        }
    }

    @Test void leftoverLinesCollectedIntoProfile() {
        String text = "何展鹏\n广州市荔湾中心医院\n主任医师\n广东省医院协会常务理事\n擅长内分泌代谢病的临床与科研工作\n";
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("姓名", "何展鹏");
        fields.put("单位", "广州市荔湾中心医院");
        fields.put("职称", "主任医师");
        String profile = service.collectProfileLines(text, fields);
        assertTrue(profile.contains("广东省医院协会常务理事"));
        assertTrue(profile.contains("擅长内分泌代谢病的临床与科研工作"));
        assertTrue(profile.contains("\n"));
        // 已归位字段取值不得重复出现在简介中
        assertFalse(profile.contains("何展鹏"));
        assertFalse(profile.contains("广州市荔湾中心医院"));
        assertFalse(profile.contains("主任医师"));
    }

    @Test void profileExcludesFieldLabelLines() {
        String profile = service.collectProfileLines(
                "姓名：张三\n单位：某医院\n电话：13812345678\n兼任某学会委员\n",
                Map.of("姓名", "张三", "单位", "某医院", "联系方式", "13812345678"));
        assertFalse(profile.contains("姓名"));
        assertFalse(profile.contains("某医院"));
        assertFalse(profile.contains("13812345678"));
        assertTrue(profile.contains("兼任某学会委员"));
    }

    @Test void nameFallbackFromFileName() {
        assertEquals("何展鹏", service.nameFromFileName("1 广州荔湾中心医院 何展鹏.pptx"));
        assertEquals("何展鹏", service.nameFromFileName("何展鹏-广州市荔湾中心医院.pdf"));
        assertEquals("李测试", service.nameFromFileName("3、李测试_某某医院.docx"));
        assertEquals("张三", service.nameFromFileName("张三简历.docx"));
        assertEquals("陈珂", service.nameFromFileName("河南省肿瘤医院-陈珂简介新.pptx"));
        assertNull(service.nameFromFileName("1 广州荔湾中心医院.pptx"));
        assertNull(service.nameFromFileName("个人简历.docx"));
        assertNull(service.nameFromFileName(null));
    }

    @Test void spacedNameBeforePersonalIntroductionBeatsSectionHeading() {
        assertEquals("陈珂", parse("基本情况\n副主任护师\n学术任职\n  陈  珂\n个人简介\n").get("姓名"));
    }

    @Test void filenameUnitOnlyFillsMissingUnitInBothNameModes() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("姓名：谷培利");
            document.write(output);
            assertEquals("郑州市人民医院", service.parse("郑州市人民医院-谷培利.docx", output.toByteArray(), false).fields().get("单位"));
        }
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("单位：测试医院");
            document.write(output);
            assertEquals("测试医院", service.parse("郑州市人民医院-谷培利.docx", output.toByteArray()).fields().get("单位"));
        }
    }

    @Test void profileTruncatesAt2000Chars() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 250; i++) longText.append("这是一段很长的未归位简介内容。\n");
        String profile = service.collectProfileLines(longText.toString(), Map.of());
        assertTrue(profile.length() <= 2000);
    }
    @Test void invalidNameFallsBackToDecoratedFilenameOnlyWhenAllowed() {
        String filename = "副本李宏简介-湖北省肿瘤医院 1.pptx";
        var result = new ResumeParseService.ResumeParseResult(
                Map.of("姓名", "华中科技大学同济医学院医学博士", "单位", "湖北省肿瘤医院"),
                java.util.List.of(), 2, "");
        assertEquals(Map.of("姓名", "李宏", "单位", "湖北省肿瘤医院"),
                service.resolveName(result, filename, true).fields());
        var contentOnly = service.resolveName(result, filename, false);
        assertEquals(Map.of("单位", "湖北省肿瘤医院"), contentOnly.fields());
        assertEquals(1, contentOnly.hitCount());
    }

    @Test void namePrefixedUnitLineIsSeparated() {
        // 2026-11-17 报告案例：姓名与单位同行，单位不得混入姓名
        Map<String, String> fields = parse("李向东，汝州市第一人民医院甲状腺乳腺外科副主任医师\n");
        assertEquals("汝州市第一人民医院", fields.get("单位"));
        assertFalse(fields.get("单位").contains("李向东"));
        assertEquals("副主任医师", fields.get("职称"));
    }

    @Test void embeddedTitleAfterDepartmentExtracted() {
        // 2026-11-17 词边界修复：科室+职称同行拼接应命中职称
        assertEquals("副主任医师", parse("甲状腺乳腺外科副主任医师\n").get("职称"));
        assertEquals("主任医师", parse("肿瘤科主任医师\n").get("职称"));
        assertEquals("主治医师", parse("肿瘤科主治医师\n").get("职称"));
    }

    @Test void longestTitleWinsOverSubstring() {
        // 最长匹配：副主任医师不得被子串"主任医师"抢先
        assertEquals("副主任医师", parse("李四\n某医院\n副主任医师\n").get("职称"));
        assertEquals("主管护师", parse("李四\n某医院\n主管护师\n").get("职称"));
    }

    @Test void nurseHeadNotMatchedAsNurse() {
        // 尾边界保留：护士长不得命中子串"护士"
        assertNull(parse("李四\n某医院\n护士长\n").get("职称"));
    }

    @Test void sanitizeUnitStripsNameAndKeepsOrg() {
        assertEquals("汝州市第一人民医院", service.sanitizeUnit("李向东，汝州市第一人民医院", "李向东"));
        assertEquals("汝州市第一人民医院", service.sanitizeUnit("李向东汝州市第一人民医院", "李向东"));
        assertEquals("汝州市第一人民医院", service.sanitizeUnit("李向东，汝州市第一人民医院", null));
        assertEquals("山东省立第三医院", service.sanitizeUnit("山东省立第三医院", null));
        assertEquals("汝州市第一人民医院", service.sanitizeUnit("李向东、汝州市第一人民医院、主任医师", "李向东"));
        assertNull(service.sanitizeUnit(null, null));
        assertNull(service.sanitizeUnit("   ", "李向东"));
    }

    @Test void legacyPptExtractsGroupedTextAndPhoto() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var presentation = new HSLFSlideShow()) {
            var slide = presentation.createSlide();
            var group = slide.createGroup();
            group.createTextBox().setText("姓名：王小明");
            slide.createTable(1, 1).getCell(0, 0).setText("工作单位：苏州大学附属第一医院");
            var png = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(120, 160, BufferedImage.TYPE_INT_RGB), "png", png);
            slide.createPicture(presentation.addPicture(png.toByteArray(), PictureData.PictureType.PNG));
            presentation.write(output);
        }

        var parser = new ResumeParseService(new OllamaService() {
            @Override public Map<String, String> extractResumeFields(String text) {
                return Map.of();
            }
        }, null);
        var result = parser.parse("简历.ppt", output.toByteArray(), false);
        assertEquals("王小明", result.fields().get("姓名"));
        assertEquals("苏州大学附属第一医院", result.fields().get("单位"));
        assertEquals(1, result.photoCandidates().size());
    }

    @Test void optionalLocalPptxRegression() throws Exception {
        String sample = System.getenv("RESUME_NAME_TEST_PPTX");
        org.junit.jupiter.api.Assumptions.assumeTrue(sample != null && !sample.isBlank());
        var path = java.nio.file.Path.of(sample);
        var parser = new ResumeParseService(new OllamaService() {
            @Override public Map<String, String> extractResumeFields(String text) {
                return Map.of("姓名", "华中科技大学同济医学院医学博士", "单位", "湖北省肿瘤医院");
            }
        }, null);
        byte[] bytes = java.nio.file.Files.readAllBytes(path);
        var result = parser.parse(path.getFileName().toString(), bytes);
        var fields = result.fields();
        assertEquals("李宏", fields.get("姓名"));
        assertTrue(fields.get("简介").contains("华中科技大学同济医学院医学博士"));
        assertTrue(result.photoCandidates().stream().anyMatch(photo ->
                "image/jpeg".equals(photo.mime()) && !photo.base64().isBlank()));
        assertFalse(parser.parse(path.getFileName().toString(), bytes, false).fields().containsKey("姓名"));
    }

    @Test void optionalPptxWithStoredImageDataDescriptors() throws Exception {
        String sample = System.getenv("RESUME_STORED_IMAGE_TEST_PPTX");
        org.junit.jupiter.api.Assumptions.assumeTrue(sample != null && !sample.isBlank());
        var path = java.nio.file.Path.of(sample);
        var parser = new ResumeParseService(new OllamaService() {
            @Override public Map<String, String> extractResumeFields(String text) {
                return Map.of();
            }
        }, null);

        var result = parser.parse(path.getFileName().toString(), java.nio.file.Files.readAllBytes(path), false);
        assertNotNull(result.fields().get("单位"));
        assertFalse(result.photoCandidates().isEmpty());
    }
}
