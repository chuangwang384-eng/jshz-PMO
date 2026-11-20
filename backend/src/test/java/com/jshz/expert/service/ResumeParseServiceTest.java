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
            // 2026-11-19 单位文件名绝对第一优先：内容单位与文件名不一致时取文件名（王俊生案例）
            assertEquals("郑州市人民医院", service.parse("郑州市人民医院-谷培利.docx", output.toByteArray()).fields().get("单位"));
        }
    }

    @Test void profileTruncatesAt2000Chars() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 250; i++) longText.append("这是一段很长的未归位简介内容。\n");
        String profile = service.collectProfileLines(longText.toString(), Map.of());
        assertTrue(profile.length() <= 2000);
    }
    @Test void filenameIdentityWinsOverContentWithComparison() throws Exception {
        // 2026-11-19 文件名第一优先（Owner）：文件名候选经原文比对后落定，姓名垃圾候选被机构内嵌检测剔除
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("姓名：华中科技大学同济医学院医学博士\n工作单位：湖北省肿瘤医院");
            document.write(output);
            var fields = service.parse("副本李宏简介-湖北省肿瘤医院 1.docx", output.toByteArray()).fields();
            assertEquals("李宏", fields.get("姓名"));
            assertEquals("湖北省肿瘤医院", fields.get("单位"));
            var contentOnly = service.parse("副本李宏简介-湖北省肿瘤医院 1.docx", output.toByteArray(), false).fields();
            assertFalse(contentOnly.containsKey("姓名"));
            assertEquals("湖北省肿瘤医院", contentOnly.get("单位"));
        }
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

    @Test void pptxPrefersImagesPlacedOnTheSlideOverUnusedMedia() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var presentation = new XMLSlideShow()) {
            var slide = presentation.createSlide();
            for (int width : new int[]{120, 130, 140, 160}) {
                var png = new ByteArrayOutputStream();
                ImageIO.write(new BufferedImage(width, 200, BufferedImage.TYPE_INT_RGB), "png", png);
                var picture = presentation.addPicture(png.toByteArray(), PictureData.PictureType.PNG);
                if (width == 160) slide.createPicture(picture);
            }
            presentation.write(output);
        }
        var parser = new ResumeParseService(null, null);
        var result = parser.parse("简历.pptx", output.toByteArray(), false);
        assertEquals(1, result.photoCandidates().size());
        assertEquals(result.photoCandidates(), parser.parse("简历.ppt", output.toByteArray(), false).photoCandidates());
        byte[] bytes = java.util.Base64.getDecoder().decode(result.photoCandidates().get(0).base64());
        assertEquals(160, ImageIO.read(new java.io.ByteArrayInputStream(bytes)).getWidth());
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

    @Test void educationLineIsNotUnit() {
        // 2026-11-18 孙明亮原件回归：教育经历行含"大学"但不是任职单位
        Map<String, String> fields = parse("孙明亮，主治医师，医学硕士，\n2018年毕业于哈尔滨医科大学肿瘤放射治疗学专业，\n洛阳市肿瘤化疗委员会委员，\n洛阳市抗癌协会委员等。");
        assertNull(fields.get("单位"));
    }

    @Test void unitNamePrefixStrippedAfterNameResolved() throws Exception {
        // 2026-11-18 卫利民原件回归：姓名+单位拼接，姓名落定后二次剥离（现于 buildResult 内联执行）
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("卫利民河南科技大学第一附属医院肿瘤内科主治医师");
            document.write(output);
            var fields = service.parse("卫利民简介.docx", output.toByteArray()).fields();
            assertEquals("卫利民", fields.get("姓名"));
            assertEquals("河南科技大学第一附属医院", fields.get("单位"));
        }
    }

    @Test void shortUnitNamePrefixNotStripped() throws Exception {
        // 防"华山"→"医院"类短单位误伤：剩余部分不足 6 字符不剥离
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("华山医院");
            document.write(output);
            var fields = service.parse("华山简介.docx", output.toByteArray()).fields();
            assertEquals("华山", fields.get("姓名"));
            assertEquals("华山医院", fields.get("单位"));
        }
    }

    @Test void optionalJuaaiThreeOriginals() throws java.io.IOException {
        // 2026-11-18 三份真实原件探针（E:\王创\....24聚艾简介 目录）
        String dir = System.getenv("RESUME_JUAAI_DIR");
        org.junit.jupiter.api.Assumptions.assumeTrue(dir != null && !dir.isBlank());
        var parser = new ResumeParseService(new OllamaService() {
            @Override public Map<String, String> extractResumeFields(String text) {
                return Map.of();
            }
        }, null);
        var lushaobei = parser.parse("鲁少贝简介.pptx", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(dir, "鲁少贝简介.pptx")));
        assertEquals("鲁少贝", lushaobei.fields().get("姓名"));
        assertEquals("濮阳市人民医院", lushaobei.fields().get("单位"));
        var sunmingliang = parser.parse("孙明亮个人简介.pptx", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(dir, "孙明亮个人简介.pptx")));
        assertEquals("孙明亮", sunmingliang.fields().get("姓名"));
        String sunUnit = sunmingliang.fields().get("单位");
        assertTrue(sunUnit == null || !sunUnit.contains("毕业"), "单位不得包含教育经历行: " + sunUnit);
        var weilimin = parser.parse("卫利民简介.pptx", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(dir, "卫利民简介.pptx")));
        assertEquals("卫利民", weilimin.fields().get("姓名"));
        String weiUnit = weilimin.fields().get("单位");
        assertNotNull(weiUnit);
        assertFalse(weiUnit.startsWith("卫利民"), "单位不得混入姓名: " + weiUnit);
        assertTrue(weiUnit.contains("河南科技大学第一附属医院"), "单位应为河南科技大学第一附属医院: " + weiUnit);
    }

    @Test void strongOrgCandidateBeatsWeakTruncation() {
        // 2026-11-18 郭双双原件回归：弱候选"河南省医学会"不得压过强候选表格标签行
        // 2026-11-19 单位优先级重构：散行扫描候选改为返回值，由调用方按"组合行>标签行>文件名>扫描"排序
        Map<String, String> fields = new LinkedHashMap<>();
        String scanned = service.extractFields("郭双双\n副主任医师 肿瘤学博士 副教授 硕导\n洛阳市抗癌协会肺癌专业委员会副主委\n河南省医学会青年委员\n医院    河南科技大学第一附属医院\n科室    呼吸肿瘤内科二病区 副主任", fields);
        assertEquals("河南科技大学第一附属医院", scanned);
    }

    @Test void titlePrefixStrippedFromUnit() {
        // 2026-11-18 李泉江原件回归："主治医师河南科技大学第一附属医院放疗科"（返回值已净化职称前缀）
        Map<String, String> fields = new LinkedHashMap<>();
        String scanned = service.extractFields("李泉江\n医学博士  毕业于原解放军第二军医大学，\n主治医师  河南科技大学第一附属医院 放疗科\n中华医学会会员", fields);
        assertEquals("河南科技大学第一附属医院", scanned);
    }

    @Test void datedEducationLinesAreNotUnit() {
        // 2026-11-18 许鹏程原件回归："2013/09-2018/07南方医科大学"年份开头行不得作为单位
        Map<String, String> fields = parse("个 人 简 历\n姓名：许鹏程；年龄：30岁；\n2013/09-2018/07  南方医科大学   医学博士\n2019/01—2021/06  中国人民解放军南部战区总医院  医学博士后\n获得青年医师优秀论文奖2项。");
        assertNull(fields.get("单位"));
    }

    @Test void optionalJuaaiSixOriginals() throws java.io.IOException {
        // 2026-11-18 第二批六份真实原件探针：郭双双/胡晓辰/李泉江/吕红琼/任婧/许鹏程
        String dir = System.getenv("RESUME_JUAAI_DIR");
        org.junit.jupiter.api.Assumptions.assumeTrue(dir != null && !dir.isBlank());
        var parser = new ResumeParseService(new OllamaService() {
            @Override public Map<String, String> extractResumeFields(String text) {
                return Map.of();
            }
        }, null);
        var guo = parser.parse("郭双双 专家简介.pptx", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(dir, "郭双双 专家简介.pptx")));
        assertEquals("郭双双", guo.fields().get("姓名"));
        assertEquals("河南科技大学第一附属医院", guo.fields().get("单位"));
        var hu = parser.parse("胡晓辰简介.pptx", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(dir, "胡晓辰简介.pptx")));
        assertEquals("胡晓辰", hu.fields().get("姓名"));
        assertEquals("河南科技大学第一附属医院", hu.fields().get("单位"));
        var li = parser.parse("李泉江个人简介.pptx", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(dir, "李泉江个人简介.pptx")));
        assertEquals("河南科技大学第一附属医院", li.fields().get("单位"));
        assertEquals("放疗科", li.fields().get("科室"));
        var lv = parser.parse("吕红琼简介最新.pptx", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(dir, "吕红琼简介最新.pptx")));
        assertEquals("吕红琼", lv.fields().get("姓名"));
        assertEquals("洛阳市中心医院", lv.fields().get("单位"));
        var ren = parser.parse("任婧简介.ppt", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(dir, "任婧简介.ppt")));
        assertEquals("任婧", ren.fields().get("姓名"));
        assertEquals("肿瘤内科", ren.fields().get("科室"));
        var xu = parser.parse("许鹏程-个人简历.docx", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(dir, "许鹏程-个人简历.docx")));
        assertEquals("许鹏程", xu.fields().get("姓名"));
        assertNull(xu.fields().get("单位"));
    }

    @Test void semanticHallucinatedFieldsAreRejected() throws Exception {
        // 2026-11-18 任婧/卫利民原件回归：Ollama 臆造占位手机号 13800001234、虚构职称/科室必须被拒；
        // 原文真实座机（含 +86 前缀）应被采纳
        byte[] bytes;
        try (var presentation = new XMLSlideShow(); var output = new ByteArrayOutputStream()) {
            var slide = presentation.createSlide();
            var box = slide.createTextBox();
            box.setText("任婧\n河南科技大学第一附属医院肿瘤内科\n主治医师、医学硕士电话：+86 379 64830609");
            presentation.write(output);
            bytes = output.toByteArray();
        }
        var hallucinated = new ResumeParseService(new OllamaService() {
            @Override public Map<String, String> extractResumeFields(String text) {
                return Map.of("姓名", "任婧", "单位", "河南科技大学第一附属医院",
                        "联系方式", "13800001234", "职称", "院士", "科室", "消化内科");
            }
        }, null);
        var r1 = hallucinated.parse("任婧简介.pptx", bytes, false);
        assertFalse(r1.fields().containsKey("联系方式"));
        assertNotEquals("院士", r1.fields().get("职称"));
        assertNotEquals("消化内科", r1.fields().get("科室"));
        var grounded = new ResumeParseService(new OllamaService() {
            @Override public Map<String, String> extractResumeFields(String text) {
                return Map.of("姓名", "任婧", "单位", "河南科技大学第一附属医院",
                        "联系方式", "+86 379 64830609", "职称", "主治医师", "科室", "肿瘤内科");
            }
        }, null);
        var r2 = grounded.parse("任婧简介.pptx", bytes, false);
        assertTrue(r2.fields().get("联系方式").replaceAll("[\s+]", "").contains("37964830609"));
        assertEquals("主治医师", r2.fields().get("职称"));
        assertEquals("肿瘤内科", r2.fields().get("科室"));
    }

    @Test void optionalYueyouYangchenTextboxDocx() throws java.io.IOException {
        // 2026-11-18 杨晨原件回归：docx 文本框（w:txbxContent）正文须被抽取
        String dir = System.getenv("RESUME_YUEYOU_DIR");
        org.junit.jupiter.api.Assumptions.assumeTrue(dir != null && !dir.isBlank());
        var parser = new ResumeParseService(new OllamaService() {
            @Override public Map<String, String> extractResumeFields(String text) {
                return Map.of();
            }
        }, null);
        var result = parser.parse("杨晨简介.docx", java.nio.file.Files.readAllBytes(java.nio.file.Path.of(dir, "杨晨简介.docx")));
        assertEquals("信阳市肿瘤医院", result.fields().get("单位"));
        assertEquals("副主任医师", result.fields().get("职称"));
    }

    @Test
    void extractsHospitalAndDepartmentFromSplitRolesInLongParagraph() {
        var fields = service.buildResult("个人简介.docx", "示例，副主任医师，肿瘤学硕士，某市中心医院肿瘤科副主任。"
                + "省抗癌协会委员会常务委员；发表核心期刊论文十余篇。", java.util.List.of(), true).fields();
        assertEquals("某市中心医院", fields.get("单位"));
        assertEquals("肿瘤科", fields.get("科室"));
        assertEquals("副主任医师", fields.get("职称"));
    }

    @Test
    void extractsClinicalTitleBeforeAdditionalRole() {
        var fields = parse("某市中心医院 呼吸与危重症医学科 副主任医师 肺癌学组组长\n"
                + "海外大学医院访问学者");
        assertEquals("某市中心医院", fields.get("单位"));
        assertEquals("呼吸与危重症医学科", fields.get("科室"));
        assertEquals("副主任医师", fields.get("职称"));
    }

    @Test
    void corroboratesNameEndingInKeWithStandaloneHeading() {
        var fields = service.buildResult("王文科简介.pptx",
                "王文科\n某市人民医院胸心外科行政副主任、主任医师", java.util.List.of(), true).fields();
        assertEquals("王文科", fields.get("姓名"));
        assertEquals("胸心外科", fields.get("科室"));
        assertEquals("主任医师", fields.get("职称"));
    }

    @Test
    void imageOnlySlideUsesOcrWhileOfficeTextAndSmallPicturesDoNot() throws Exception {
        var parser = new ResumeParseService(org.mockito.Mockito.mock(OllamaService.class), null);
        try (var ocr = org.mockito.Mockito.mockStatic(KnowledgeDocTextExtractor.class)) {
            ocr.when(() -> KnowledgeDocTextExtractor.ppOcrLines(org.mockito.ArgumentMatchers.any()))
                    .thenReturn("姓名：王小明\n某市中心医院肿瘤科主任医师\n发表论文十余篇");
            var imageOnly = parser.parse("个人简介.pptx", resumeSlideWithImage(true, false), false);
            assertEquals("王小明", imageOnly.fields().get("姓名"));
            assertEquals("某市中心医院", imageOnly.fields().get("单位"));
            assertEquals("肿瘤科", imageOnly.fields().get("科室"));
            assertEquals("主任医师", imageOnly.fields().get("职称"));
            assertTrue(imageOnly.fields().get("简介").contains("发表论文十余篇"));
            assertFalse(imageOnly.photoCandidates().isEmpty());

            var nativeText = parser.parse("个人简介.pptx", resumeSlideWithImage(true, true), false);
            assertEquals("张晓丽", nativeText.fields().get("姓名"));
            assertTrue(parser.parse("个人简介.pptx", resumeSlideWithImage(false, false), false)
                    .fields().isEmpty());
            ocr.verify(() -> KnowledgeDocTextExtractor.ppOcrLines(org.mockito.ArgumentMatchers.any()));
        }
    }

    private byte[] resumeSlideWithImage(boolean fullPage, boolean hasText) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var presentation = new XMLSlideShow()) {
            var slide = presentation.createSlide();
            var png = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB), "png", png);
            var picture = slide.createPicture(presentation.addPicture(png.toByteArray(), PictureData.PictureType.PNG));
            var size = presentation.getPageSize();
            picture.setAnchor(new java.awt.geom.Rectangle2D.Double(0, 0,
                    fullPage ? size.width : size.width * 0.2,
                    fullPage ? size.height : size.height * 0.2));
            if (hasText) slide.createTextBox().setText("姓名：张晓丽");
            presentation.write(output);
        }
        return output.toByteArray();
    }
}
