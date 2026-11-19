package com.jshz.expert.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
