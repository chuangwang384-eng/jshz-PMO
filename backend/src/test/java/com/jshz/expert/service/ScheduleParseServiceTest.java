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
 * 全角空格姓名归一化、已知主席在总结环节沿用主席身份。
 */
class ScheduleParseServiceTest {

    private final ScheduleParseService service = new ScheduleParseService(
            null, null, null, null, null, null, null, null, null, null);

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
        // 大会总结的成慧君沿用本场主席身份
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "成慧君", "大会总结").getRole());
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

    /**
     * 三门峡实文件结构回放（2026-11-10 取证）：表头「时间/内容/嘉宾」，无主持列；
     * 讨论段带「讨论：」标签多人；「第X环节」行=环节主持；人名与「教授」跨段拆分；页脚「会议主席」。
     */
    private ScheduleParseService.Extracted sanmenxiaSchedule() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("时间", "内容", "嘉宾"));
        rows.add(List.of("14:00-14:05", "主席致辞", "田春桃\n教授"));
        rows.add(List.of("14:05-14:30", "第一环节", "姜建华 教授"));
        rows.add(List.of("14:05-14:30", "CSCO指南 HER2阳性乳腺癌最新指南更新", "梁垲 教授"));
        rows.add(List.of("14:30-14:55", "讨论：", "王六林 教授、\n薛凌飞 教授、\n张毅 教授、\n邱丽倩 教授"));
        rows.add(List.of("", "第二环节", "王辉 教授"));
        rows.add(List.of("14:55-15:20", "CSCO指南 HR阳性乳腺癌最新指南更新", "李佳音 教授"));
        rows.add(List.of("15:20-15:45", "讨论：卢鑫怡 教授、徐静 教授", ""));
        rows.add(List.of("15:45-16:10", "HR病例分享", "张泽帅 教授"));
        rows.add(List.of("16:10-16:35", "讨论：郭芳芳 教授、朱世航 教授、秦智军 教授、章万勇 教授、刘桂兰 教授", ""));
        rows.add(List.of("16:35-16:40", "会议总结", "田春桃 教授\n姚丽鸽 教授"));
        String text = "会议主席：田春桃\n教授\n会议时间：2024年09月21日 14:00-16:40\n会议地点：三门峡天鹅城国际酒店";
        return new ScheduleParseService.Extracted(text, rows);
    }

    @Test
    void sanmenxiaHeaderAndRoles() {
        List<ProjectSpeaker> result = service.ruleParse(sanmenxiaSchedule());
        // 表头「时间/内容/嘉宾」可定位，讲者列解析出全部姓名
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "田春桃", "主席致辞").getRole(), "主席致辞→大会主席");
        assertEquals(ProjectSpeaker.ROLE_HOST, find(result, "姜建华", "第一环节").getRole(), "「第X环节」行→大会主持");
        assertEquals("14:05-14:30", find(result, "姜建华", "第一环节").getSegmentTime(), "合并单元格时间沿上一行");
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "梁垲", null).getRole(), "普通讲者→大会讲者");
        assertEquals(ProjectSpeaker.ROLE_DISCUSSANT, find(result, "王六林", null).getRole(), "讨论段（内容列）→讨论嘉宾");
        assertEquals(ProjectSpeaker.ROLE_DISCUSSANT, find(result, "邱丽倩", null).getRole());
        assertEquals(ProjectSpeaker.ROLE_DISCUSSANT, find(result, "刘桂兰", null).getRole(), "内容列多人讨论段全量提取");
        assertEquals("14:30-14:55", find(result, "王六林", null).getSegmentTime(), "讨论段带时间段");
        // 「讨论：」标签不被当成 2 字人名
        assertTrue(result.stream().noneMatch(sp -> "讨论".equals(sp.getExpertName())), "段首标签不算人名");
        assertTrue(result.stream().noneMatch(sp -> sp.getExpertName().contains("CSCO")), "含字母串不算人名");
        // 仅已知主席沿用主席身份，同段其他人保留原角色
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "田春桃", "会议总结").getRole());
        assertEquals(ProjectSpeaker.ROLE_DISCUSSANT, find(result, "姚丽鸽", "会议总结").getRole());
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "张泽帅", null).getRole());
    }

    @Test
    void headerlessTimeAnchoredLines() {
        String text = "19:00-19:05 主席致辞 李五 教授\n19:05-19:25 规范诊疗 王六 教授\n会议时间：2026年10月23日";
        List<ProjectSpeaker> result = service.ruleParse(new ScheduleParseService.Extracted(text, List.of()));
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "李五", null).getRole(), "无表头时间锚定+角色词");
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "王六", null).getRole());
        assertTrue(result.stream().noneMatch(sp -> sp.getSegmentTime() == null || sp.getSegmentTime().isBlank()),
                "时间锚定行应带时间段");
    }

    /**
     * 曲径通幽实文件结构回放（2026-11-10 第二轮取证）：表头「时间/讲题/讲者\ufF0f嘉宾」（讲题列极易误判为讲者列）；
     * 讲题含医学词（静脉曲张日间手术）不能当人名；表内整行「主持：X 教授 单位」；讨论嘉宾带单位后缀。
     */
    private ScheduleParseService.Extracted qujingSchedule() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("时间", "讲题", "讲者\n/\n嘉宾"));
        rows.add(List.of("主持：王雅 教授  南阳中心医院", "", ""));
        rows.add(List.of("19:00-19:10", "主席致辞", "马立人 教授 平顶山市中医院"));
        rows.add(List.of("19:10-19:30", "静脉曲张日间手术", "彭军路 教授 河北医科大学第一医院"));
        rows.add(List.of("19:30-19:45", "讨论", "彭喜涛 教授  河南省直第三人民医院\n王玉文 教授  山西医科大学第二医院\n任伟强 教授  邯郸市中心医院"));
        rows.add(List.of("主持：牛国浩 教授  平煤神马集团总医院", "", ""));
        rows.add(List.of("19:45-20:05", "射频联合硬化剂治疗下肢静脉曲张处理体会", "罗秀荣 教授 平顶山市中医院"));
        rows.add(List.of("20:05-20:20", "讨论", "郭强 教授  长治市人民医院\n苏威强 教授  河南理工大学第一附属医院\n李藏妥 教授  唐山市工人医院"));
        rows.add(List.of("20:20-20:30", "会议总结", "马立人 教授 平顶山市中医院"));
        String text = "大会主席：马立人 教授\n会议时间：10月21日（周一）19:00-20:30\n会议地点：腾讯会议：973-343-664\n扫描二维码参加会议";
        return new ScheduleParseService.Extracted(text, rows);
    }

    @Test
    void qujingHeaderTopicColumnAndHostRows() {
        List<ProjectSpeaker> result = service.ruleParse(qujingSchedule());
        // 「讲题」列不再被当成讲者列：医学讲题词不出现为人名
        for (String bad : List.of("静脉曲张", "日间手术", "射频联合", "硬化剂治", "疗下肢静", "处理体会")) {
            assertTrue(result.stream().noneMatch(sp -> bad.equals(sp.getExpertName())), "讲题词不算人名: " + bad);
        }
        // 真讲者全量提取，单位词被剔除
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "彭军路", "静脉曲张日间手术").getRole());
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "马立人", "主席致辞").getRole());
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "马立人", "会议总结").getRole());
        assertEquals(ProjectSpeaker.ROLE_DISCUSSANT, find(result, "彭喜涛", "讨论").getRole());
        assertEquals(ProjectSpeaker.ROLE_DISCUSSANT, find(result, "王玉文", "讨论").getRole());
        assertEquals(ProjectSpeaker.ROLE_DISCUSSANT, find(result, "李藏妥", "讨论").getRole());
        assertEquals("19:30-19:45", find(result, "彭喜涛", "讨论").getSegmentTime());
        // 表内整行「主持：X 教授 单位」→大会主持
        assertEquals(ProjectSpeaker.ROLE_HOST, find(result, "王雅", null).getRole());
        assertEquals(ProjectSpeaker.ROLE_HOST, find(result, "牛国浩", null).getRole());
        assertTrue(result.stream().noneMatch(sp -> sp.getExpertName().contains("医院") || sp.getExpertName().contains("中心")), "单位词不算人名");
    }

    /** 大医万里行 docx 形态回放（2026-11-10）：合并单元格致讲者列留空，名单（无头衔、顿号分隔）在内容列。 */
    @Test
    void docxMergedDiscussionNamesFromTopicColumn() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("时间", "内容", "讲者", "主持"));
        rows.add(List.of("08:30-08:40", "开场致辞", "贾红燕、王涛", "贾红燕、王涛"));
        rows.add(List.of("09:05-09:25", "真知灼见:冯利国、郝小强、牛海刚、郭俊斌", "", "杨璇"));
        rows.add(List.of("09:25-09:50", "HER2阳性早期乳腺癌治疗进展", "张亚芬", "罗飞"));
        rows.add(List.of("09:50-10:10", "静脉曲张日间手术", "", "杨璇"));
        List<ProjectSpeaker> result = service.ruleParse(new ScheduleParseService.Extracted("", rows));
        for (String n : List.of("冯利国", "郝小强", "牛海刚", "郭俊斌")) {
            assertEquals(ProjectSpeaker.ROLE_DISCUSSANT, find(result, n, "真知灼见").getRole(),
                    "合并单元格讨论名单（无头衔）应从内容列提取: " + n);
        }
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "张亚芬", null).getRole());
        assertTrue(result.stream().noneMatch(sp -> "静脉曲张".equals(sp.getExpertName())),
                "无名单无头衔的讲题不得当人名");
    }

    private ProjectSpeaker find(List<ProjectSpeaker> list, String name, String segment) {
        return list.stream()
                .filter(sp -> name.equals(sp.getExpertName())
                        && (segment == null || (sp.getSegmentTitle() != null && sp.getSegmentTitle().contains(segment))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到: " + name + " @ " + segment));
    }
    @Test
    void bindsTextBoxAppearancesAlongsideTableAndPreservesMultipleRoles() {
        var rows = List.of(List.of("时间", "议题", "讲者"),
                List.of("09:00", "专题报告", "李志峰 教授"));
        var extracted = new ScheduleParseService.Extracted("", rows, List.of(
                "09:30—10:00 大咖授课  主持：李志峰 教授",
                "百尺竿头  主持: 康厚香 教授",
                "跬步千里  主持：炕景超 教授",
                "跬步千里  主持：炕景超 教授"));
        var result = service.ruleParse(extracted);
        assertEquals(4, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getExpertName().equals("李志峰")
                && s.getRole().equals("SPEAKER") && s.getSegmentTime().equals("09:00")));
        assertTrue(result.stream().anyMatch(s -> s.getExpertName().equals("李志峰")
                && s.getRole().equals("HOST") && s.getSegmentTitle().equals("大咖授课")
                && s.getSegmentTime().equals("09:30—10:00")));
        assertTrue(result.stream().anyMatch(s -> s.getExpertName().equals("康厚香")
                && s.getRole().equals("HOST") && s.getSegmentTitle().equals("百尺竿头")
                && s.getSegmentTime() == null));
    }

    @Test
    void readsPptByVisualPositionAndPreservesFullTopic() throws Exception {
        try (var ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow();
             var bytes = new java.io.ByteArrayOutputStream()) {
            var slide = ppt.createSlide();
            var lower = slide.createTextBox();
            lower.setAnchor(new java.awt.geom.Rectangle2D.Double(25, 120, 450, 40));
            lower.setText("15:00-15:30     自体造血干细胞动员、采集与冻存              边志磊");
            var right = slide.createTextBox();
            right.setAnchor(new java.awt.geom.Rectangle2D.Double(250, 55, 200, 40));
            right.setText("第一环节 主持：李志峰");
            var left = slide.createTextBox();
            left.setAnchor(new java.awt.geom.Rectangle2D.Double(25, 57, 200, 40));
            left.setText("第一环节 讲者：徐   征 姚金晓");
            ppt.write(bytes);

            var result = service.ruleParse(service.extract("fixture.pptx", bytes.toByteArray()));
            assertEquals(List.of("徐征", "姚金晓", "李志峰", "边志磊"),
                    result.stream().map(ProjectSpeaker::getExpertName).toList());
            assertEquals("自体造血干细胞动员、采集与冻存", result.get(3).getSegmentTitle());
        }
    }

    @Test
    void readsSpeakerHeaderAliasesAndKeepsClosingChairRole() {
        for (String header : List.of("发言人", "发言者", "报告人")) {
            var rows = List.of(List.of("时 间", "主 题", header, "主 持"),
                    List.of("16:00-16:10", "大会主席致辞", "李海春 教授", ""),
                    List.of("16:10-16:30", "指南解读", "陈春龙 教授", "李修炜 教授"),
                    List.of("16:30-16:50", "讨论嘉宾", "丁小崇 教授\n郭　勇 教授", ""),
                    List.of("17:10-17:20", "会议总结", "李海春 教授", ""));
            var result = service.ruleParse(new ScheduleParseService.Extracted("大会主席：李海春 教授", rows));
            assertEquals(List.of("李海春", "陈春龙", "李修炜", "丁小崇", "郭勇", "李海春"),
                    result.stream().map(ProjectSpeaker::getExpertName).toList());
            assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "李海春", "会议总结").getRole());
        }
    }

    @Test
    void openingAddressUsesDeclaredChairsAndPreservesOtherRoles() {
        var rows = List.of(List.of("时间", "内容", "讲者", "主持人"),
                List.of("09:00-09:10", "开场致辞", "陈友国 张梦真", "王小明"),
                List.of("09:10-09:20", "开场致辞", "李海春", ""),
                List.of("09:20-09:30", "指南解读", "张梦真", ""));
        var result = service.ruleParse(new ScheduleParseService.Extracted("会议主席：陈友国 教授 张梦真 教授", rows));
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "陈友国", "开场致辞").getRole());
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(result, "张梦真", "开场致辞").getRole());
        assertEquals(ProjectSpeaker.ROLE_HOST, find(result, "王小明", "开场致辞").getRole());
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "李海春", "开场致辞").getRole());
        assertEquals(ProjectSpeaker.ROLE_SPEAKER, find(result, "张梦真", "指南解读").getRole());
        var noChairLabel = service.ruleParse(new ScheduleParseService.Extracted("", rows));
        assertEquals(ProjectSpeaker.ROLE_CHAIR, find(noChairLabel, "陈友国", "开场致辞").getRole());
    }

}
