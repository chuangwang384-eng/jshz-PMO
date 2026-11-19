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
}
