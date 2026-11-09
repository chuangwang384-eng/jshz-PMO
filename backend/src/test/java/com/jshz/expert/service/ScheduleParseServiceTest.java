package com.jshz.expert.service;

import com.jshz.expert.entity.ProjectSpeaker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日程解析规则打底单测（2026-11-09 流程重塑）：
 * fixture 按 Owner 提供的示例日程（10.23-日程.pptx，表格：时间/主题/讲者/主持，页脚大会主席）复刻。
 * 覆盖：主席致辞=大会主席、主持列=大会主持、单讲者=大会讲者、多人同段=讨论嘉宾、
 * 全角空格姓名归一化、页脚「大会主席」不误升「大会总结」讲者。
 */
class ScheduleParseServiceTest {

    private final ScheduleParseService service = new ScheduleParseService(
            null, null, null, null, null, null, null, null);

    private ScheduleParseService.Extracted sampleSchedule() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("时 间", "主 题", "讲 者", "主\n持"));
        rows.add(List.of("19:00-19:05", "主席致辞", "成慧君 教授", ""));
        rows.add(List.of("19:05-19:25", "规范剂量 优化获益", "谭　波 教授", "马　媛 教授"));
        rows.add(List.of("19:25-19:45", "CSCO 指南 2026 版解读", "肖　艳 教授", ""));
        rows.add(List.of("19:45-20:10", "大咖论道", "于　晓 教授\n侯艺芳 教授\n杨　柳 教授\n罗　辉 教授\n崔庆丽 教授", ""));
        rows.add(List.of("20:10-20:15", "大会总结", "成慧君 教授", ""));
        String text = "会议时间：2026 年 10 月 23 日 19:00-20:15\n腾讯会议：272-108-236\n大会主席：成慧君 教授，";
        return new ScheduleParseService.Extracted(text, rows);
    }

    @Test
    void ruleParseRolesMatchSample() {
        List<ProjectSpeaker> result = service.ruleParse(sampleSchedule());
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "成慧君", "主席致辞").getRole(), "主席致辞→大会主席");
        assertEquals(ProjectSpeaker.ROLE_HOST, find(result, "马媛", null).getRole(), "主持列→大会主持");
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "肖艳", null).getRole(), "单讲者→大会讲者");
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "谭波", null).getRole());
        for (String n : List.of("于晓", "侯艺芳", "杨柳", "罗辉", "崔庆丽")) {
            assertEquals(ProjectSpeaker.ROLE_DISCUSSANT, find(result, n, null).getRole(), "多人同段→讨论嘉宾: " + n);
        }
        // 大会总结的成慧君保持讲者（页脚主席只升格致辞行）
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "成慧君", "大会总结").getRole());
        // 全角空格姓名归一化后精确匹配成功（于晓）
        assertTrue(result.stream().anyMatch(sp -> "于晓".equals(sp.getExpertName())));
    }

    @Test
    void footerChairAddsWhenMissing() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("时间", "主题", "讲者", "主持"));
        rows.add(List.of("19:00-19:05", "开场", "张三 教授", ""));
        String text = "大会主席：李四 教授，";
        List<ProjectSpeaker> result = service.ruleParse(new ScheduleParseService.Extracted(text, rows));
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "李四", null).getRole(), "页脚主席兜底补一条");
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "张三", null).getRole());
    }

    @Test
    void nameNormalizationAndGuards() {
        assertTrue(service.isPlausibleName("马媛"));
        assertTrue(service.isPlausibleName("马　媛".replace("\u3000", "")));
        assertTrue(!service.isPlausibleName("规范剂量"), "议题词不算人名");
        assertTrue(!service.isPlausibleName("腾讯会议"), "会议特征词不算人名");
        assertEquals("谭波", service.normalizeName("谭　波"));
        List<String> names = service.extractNames("于晓 教授 侯艺芳 教授");
        assertEquals(List.of("于晓", "侯艺芳"), names, "单行多人提取");
    }

    private ProjectSpeaker find(List<ProjectSpeaker> list, String name, String segment) {
        return list.stream()
                .filter(sp -> name.equals(sp.getExpertName())
                        && (segment == null || (sp.getSegmentTitle() != null && sp.getSegmentTitle().contains(segment))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到: " + name + " @ " + segment));
    }
}
