package com.jshz.expert.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * 简历解析字段逻辑单元测试（不起 Spring 上下文，Ollama 用 Mockito mock）。
 * 2026-11-19 李宏案例回归：单位优先级改为"组合行（单位+科室+职称）> 文件名 > 散行扫描"。
 */
class ResumeParseServiceUnitTest {

    private ResumeParseService service() {
        return new ResumeParseService(mock(OllamaService.class), null);
    }

    /** 李宏 PPT 原文片段：经历行"日本上尾中央综合医院访问学者"不得压过任职行。 */
    private static final String LI_HONG_TEXT = """
            华中科技大学同济医学院医学博士
            湖北省肿瘤医院微创
            介入科副主任医师
            武田医学奖学金获得者（国家级）
            日本上尾中央综合医院访问学者
            《肝癌的治疗与康复》主编
            擅长肝胆胰复杂肿瘤切除术
            """;

    @Test
    void comboLineBeatsExperienceLine() {
        Map<String, String> fields = new LinkedHashMap<>();
        String scanned = service().extractFields(LI_HONG_TEXT, fields);
        // 组合行直接落定单位，不再返回散行扫描候选
        assertNull(scanned);
        assertEquals("湖北省肿瘤医院", fields.get("单位"));
        assertEquals("微创介入科", fields.get("科室"));
        assertEquals("副主任医师", fields.get("职称"));
    }

    @Test
    void scannedUnitReturnedWithoutComboLine() {
        // 无组合行：散行扫描候选以返回值交给调用方（排在文件名之后兜底），不直接写入 fields
        String text = "个人简历\n张三\n日本上尾中央综合医院访问学者\n";
        Map<String, String> fields = new LinkedHashMap<>();
        String scanned = service().extractFields(text, fields);
        assertEquals("日本上尾中央综合医院", scanned);
        assertNull(fields.get("单位"));
    }

    @Test
    void comboGuards() {
        ResumeParseService svc = service();
        // 经历行/学位行/非医院单位不得判为任职组合行
        assertNull(svc.matchUnitDeptTitle("日本上尾中央综合医院访问学者"));
        assertNull(svc.matchUnitDeptTitle("美国加州大学旧金山分校医学中心助理教授"));
        assertNull(svc.matchUnitDeptTitle("华中科技大学同济医学院医学博士"));
        assertNotNull(svc.matchUnitDeptTitle("洛阳市中心医院呼吸内科主治医师"));
    }

    @Test
    void unitFromFileName() {
        ResumeParseService svc = service();
        assertEquals("湖北省肿瘤医院", svc.unitFromFileName("副本李宏简介-湖北省肿瘤医院 1.pptx"));
        assertEquals("广州荔湾中心医院", svc.unitFromFileName("1 广州荔湾中心医院 何展鹏.pptx"));
        assertNull(svc.unitFromFileName("何展鹏-某医院.pdf"));
        assertNull(svc.unitFromFileName("简历.pdf"));
    }

    // ==================== 职称：词表顺序优先（Owner 2026-11-19 定稿） ====================

    private String titleOf(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        service().extractFields(text, fields);
        return fields.get("职称");
    }

    /** Owner 原例：主任医师、教授同行，取顺序靠前的主任医师。 */
    @Test
    void orderPriorityBeatsLaterGroups() {
        assertEquals("主任医师", titleOf("李四\n某医院\n主任医师、教授\n"));
        assertEquals("主任医师", titleOf("李四\n某医院\n教授、主任医师\n"));
        assertEquals("主任医师", titleOf("李四\n某医院\n研究员\n主任医师\n"));
    }

    /** 长词覆盖：子串命中不得抢在整体前面（"医师"在词表顺序上先于"中医师"）。 */
    @Test
    void longerWordCoversSubstring() {
        assertEquals("副主任中医师", titleOf("李四\n某医院\n副主任中医师\n"));
        assertEquals("中医师", titleOf("李四\n某医院\n中医师\n"));
        assertEquals("主管药师", titleOf("李四\n某医院\n主管药师\n"));
    }

    /** 新词与全组覆盖：医士/药士/技士/助教/研究实习员。 */
    @Test
    void allSixGroupsRecognized() {
        assertEquals("医士", titleOf("李四\n某医院\n放射科医士\n"));
        assertEquals("药士", titleOf("李四\n某医院\n药士\n"));
        assertEquals("技士", titleOf("李四\n某医院\nCT室技士\n"));
        assertEquals("助教", titleOf("李四\n某医院\n助教\n"));
        assertEquals("研究实习员", titleOf("李四\n某医院\n研究实习员\n"));
    }

    /** 模糊匹配："主任"→主任医师、"主治"→主治医师；科室行"副主任"不得误判。 */
    @Test
    void fuzzyAliasMatching() {
        assertEquals("主任医师", titleOf("李四\n某医院\n职称：主任\n"));
        assertEquals("主任医师", titleOf("李四\n某医院\n主任\n"));
        assertEquals("主治医师", titleOf("李四\n某医院\n主治\n"));
        // 科室特征行模糊禁用：无精确职称词时"病区副主任"不当作职称
        assertNull(titleOf("李四\n某医院\n呼吸内科病区 副主任\n"));
        // 归一入口：标签/规范候选
        assertEquals("主任医师", service().canonicalTitle("主任"));
        assertEquals("副主任医师", service().canonicalTitle("副主任"));
        assertEquals("医师", service().canonicalTitle("住院医师"));
        assertEquals("医师", service().canonicalTitle("执 业 医 师"));
        assertNull(service().canonicalTitle("院长"));
    }

    // ==================== 王俊生案例：任职行不是单位；Ollama 不覆盖本地职称；科室 LLM 辅助 ====================

    /** 任职行（委员会/学会）不得作为单位候选——单位留给文件名兜底。 */
    @Test
    void membershipLinesAreNotUnit() {
        Map<String, String> fields = new LinkedHashMap<>();
        String scanned = service().extractFields(
                "河南省抗癌协会化疗专业委员会副主任委员\n安阳市医学会肿瘤内科专业委员会主任委员\n", fields);
        assertNull(scanned);
        assertNull(fields.get("单位"));
    }

    /** 含科字候选收集：机构名之后截取；任职行（含委员等）不参与。 */
    @Test
    void deptCandidatesCollected() {
        var cands = service().collectDeptCandidates("许昌中心医院乳腺甲状腺外科，\n肿瘤内科专业委员会\n");
        assertTrue(cands.contains("乳腺甲状腺外科"));
        assertFalse(cands.contains("肿瘤内科"));
        assertFalse(cands.contains("心医院乳腺甲状腺外科"));
    }

    /** Ollama 判定为科室时填入；判定不出不填。 */
    @Test
    void deptLlmAssistFillsWhenLocalMisses() {
        OllamaService ollama = mock(OllamaService.class);
        org.mockito.Mockito.when(ollama.available()).thenReturn(true);
        org.mockito.Mockito.when(ollama.pickResumeDepartment(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("乳腺甲状腺外科");
        ResumeParseService svc = new ResumeParseService(ollama, null);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("单位", "许昌市中心医院");
        fields.put("姓名", "李晓龙");
        svc.buildResult("李晓龙-个人简介.docx", "许昌中心医院乳腺甲状腺外科，\n李晓龙 教授\n", java.util.List.of(), true);
        // 直接经 parse 校验更真实：
        try (var baos = new java.io.ByteArrayOutputStream()) {
            var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
            document.createParagraph().createRun().setText("许昌中心医院乳腺甲状腺外科，\n李晓龙 教授");
            document.write(baos);
            var r = svc.parse("李晓龙-个人简介.docx", baos.toByteArray());
            assertEquals("乳腺甲状腺外科", r.fields().get("科室"));
            document.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 本地已解析职称不得被 LLM 覆盖（主治医师不被教授覆盖）。 */
    @Test
    void localTitleNotOverriddenByLlm() {
        OllamaService ollama = mock(OllamaService.class);
        org.mockito.Mockito.when(ollama.extractResumeFields(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Map.of("职称", "教授"));
        ResumeParseService svc = new ResumeParseService(ollama, null);
        Map<String, String> fields = new LinkedHashMap<>();
        svc.extractFields("王五\n某医院\n主治医师\n", fields);
        assertEquals("主治医师", fields.get("职称"));
    }
}
