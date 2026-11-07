package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 简历字段多策略解析：标签行/机构词表/职称词表/正则；畸形输入不抛错。 */
class ResumeParseServiceTest {
    private final ResumeParseService service = new ResumeParseService(new OllamaService());

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
        assertThrows(BusinessException.class, () -> service.parse("a.pdf", new byte[21 * 1024 * 1024]));
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

    @Test void profileTruncatesAt2000Chars() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 250; i++) longText.append("这是一段很长的未归位简介内容。\n");
        String profile = service.collectProfileLines(longText.toString(), Map.of());
        assertTrue(profile.length() <= 2000);
    }
}
