package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectSchedule;
import com.jshz.expert.entity.ProjectSpeaker;
import com.jshz.expert.mapper.ProjectScheduleMapper;
import com.jshz.expert.mapper.ProjectSpeakerMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 项目日程上传与参会专家解析（2026-11-09 流程重塑第一阶段，设计文档 §4.1）。
 * 链路：文件提取（pptx/docx 表格逐单元格，pdf/图片走 PDFBox/OCR）→ 规则打底（列头关键词 + 多人同段=讨论嘉宾
 * + 页脚「大会主席」）→ Ollama 语义兜底（未配置/失败自动跳过）→ 合并去重（规则优先）→ 专家库精确匹配
 * → 全部落为 confirmed=0 候选，必须人工确认（AI 输出=候选原则）。
 * 日志只记文件名/大小/耗时/结果数，不打印日程原文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleParseService {

    private final ProjectScheduleMapper scheduleMapper;
    private final ProjectSpeakerMapper speakerMapper;
    private final ProjectService projectService;
    private final ExpertService expertService;
    private final OllamaService ollamaService;
    private final ResumeParseService resumeParseService;
    private final com.jshz.expert.mapper.ProjectExpertMapper expertRelMapper;
    private final com.jshz.expert.mapper.ProjectDocMapper docMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final List<String> ALLOWED_EXTENSIONS =
            List.of("pdf", "doc", "docx", "ppt", "pptx", "jpg", "jpeg", "png");

    /** 头衔后缀（清洗姓名用；命中即从词条尾部剥离）。 */
    private static final List<String> TITLE_SUFFIXES = List.of(
            "教授", "副教授", "主任医师", "副主任医师", "主治医师", "住院医师",
            "主任药师", "副主任药师", "主管药师", "药师",
            "主任技师", "副主任技师", "主管技师", "技师",
            "主任护师", "副主任护师", "主管护师", "护师",
            "研究员", "副研究员", "助理研究员", "讲师", "博士", "书记",
            "院长", "副院长", "主任", "副主任", "部长", "秘书长");

    private static final Pattern CHINESE_NAME = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,4}$");
    /** 名字+头衔序列提取（「于晓 教授 侯艺芳 教授」），人名内全角空格在行级先剔除。 */
    private static final Pattern NAME_WITH_TITLE = Pattern.compile("([\\u4e00-\\u9fa5·]{2,4})\\s*(教授|副教授|主任医师|副主任医师|主治医师|研究员|副研究员|讲师|博士)?");

    // ==================== 上传 / 查询 ====================

    /** 上传日程（新 current 份，旧份置 0，清该项目未确认候选）。 */
    public ProjectSchedule upload(Long projectId, MultipartFile file, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("日程文件不能为空");
        }
        String ext = extension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的日程格式（支持 pdf/doc/docx/ppt/pptx/jpg/png）");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("日程文件超过 20MB 限制");
        }
        String stored = projectService.storageSave(file);
        // 旧生效份置 0
        for (ProjectSchedule old : scheduleMapper.selectList(new LambdaQueryWrapper<ProjectSchedule>()
                .eq(ProjectSchedule::getProjectId, projectId)
                .eq(ProjectSchedule::getIsCurrent, 1))) {
            old.setIsCurrent(0);
            scheduleMapper.updateById(old);
        }
        // 清该项目所有未确认候选（人工成果不受影响）
        speakerMapper.delete(new LambdaQueryWrapper<ProjectSpeaker>()
                .eq(ProjectSpeaker::getProjectId, projectId)
                .eq(ProjectSpeaker::getConfirmed, 0));

        ProjectSchedule s = new ProjectSchedule();
        s.setProjectId(projectId);
        s.setFileName(file.getOriginalFilename());
        s.setFilePath(stored);
        s.setFileSize(file.getSize());
        s.setMimeType(file.getContentType());
        s.setParseStatus("PENDING");
        s.setIsCurrent(1);
        s.setUploadedBy(user == null ? null : user.getUserId());
        scheduleMapper.insert(s);

        // 自动挂载到当前阶段资料（Owner 2026-11-09：上传日程即完成「阶段资料」+ 自动解析）
        attachToPhaseDoc(projectId, project.getPhaseId(), s, user);

        // 自动解析（失败不阻断上传，仅记录 FAILED 供人工重试/补录）
        try {
            parse(projectId, s.getId(), user);
        } catch (Exception e) {
            log.warn("日程上传后自动解析未成功 project={} file={}：{}", projectId, s.getFileName(), e.getMessage());
        }
        return scheduleMapper.selectById(s.getId());
    }

    /** 把日程文件挂到指定阶段的资料区（项目资料库同一份文件，不重复落盘）。 */
    private void attachToPhaseDoc(Long projectId, Long phaseId, ProjectSchedule s, AuthUser user) {
        if (phaseId == null || docMapper == null) {
            return;
        }
        com.jshz.expert.entity.ProjectDoc doc = new com.jshz.expert.entity.ProjectDoc();
        doc.setProjectId(projectId);
        doc.setPhaseId(phaseId);
        doc.setFileName(s.getFileName());
        doc.setFilePath(s.getFilePath());
        doc.setUploadBy(user == null ? null : user.getUsername());
        doc.setIsDeleted(0);
        docMapper.insert(doc);
    }

    /** 当前生效日程（无则 null）。 */
    public ProjectSchedule currentSchedule(Long projectId) {
        List<ProjectSchedule> list = scheduleMapper.selectList(new LambdaQueryWrapper<ProjectSchedule>()
                .eq(ProjectSchedule::getProjectId, projectId)
                .eq(ProjectSchedule::getIsCurrent, 1)
                .orderByDesc(ProjectSchedule::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 日程与参会专家汇总（详情页日程卡）。 */
    public Map<String, Object> overview(Long projectId, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireProjectReadable(project, user);
        Map<String, Object> m = new LinkedHashMap<>();
        ProjectSchedule current = currentSchedule(projectId);
        m.put("schedule", current);
        m.put("candidates", current == null ? List.of()
                : speakerMapper.selectList(new LambdaQueryWrapper<ProjectSpeaker>()
                        .eq(ProjectSpeaker::getProjectId, projectId)
                        .eq(ProjectSpeaker::getScheduleId, current.getId())
                        .eq(ProjectSpeaker::getConfirmed, 0)
                        .orderByAsc(ProjectSpeaker::getId)));
        m.put("speakers", speakerMapper.selectList(new LambdaQueryWrapper<ProjectSpeaker>()
                .eq(ProjectSpeaker::getProjectId, projectId)
                .eq(ProjectSpeaker::getConfirmed, 1)
                .orderByAsc(ProjectSpeaker::getId)));
        // 候选/正式记录补专家档案姓名单位（库外专家前端标记）
        attachExpertInfo(m.get("candidates"));
        attachExpertInfo(m.get("speakers"));
        return m;
    }

    @SuppressWarnings("unchecked")
    private void attachExpertInfo(Object listObj) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) return;
        Set<Long> ids = new LinkedHashSet<>();
        for (Object o : list) {
            if (o instanceof ProjectSpeaker sp && sp.getExpertId() != null) ids.add(sp.getExpertId());
        }
        if (ids.isEmpty()) return;
        Map<Long, Expert> experts = new LinkedHashMap<>();
        for (Long id : ids) {
            Expert e = expertService.getById(id);
            if (e != null) experts.put(e.getId(), e);
        }
        for (Object o : list) {
            if (o instanceof ProjectSpeaker sp && sp.getExpertId() != null && experts.containsKey(sp.getExpertId())) {
                Expert e = experts.get(sp.getExpertId());
                sp.setExpertUnit(e.getUnit());
                sp.setExpertTitle(e.getTitle());
            }
        }
    }

    // ==================== 解析 ====================

    /** 触发解析（同步执行；Ollama 未配置/超时自动降级为规则结果）。 */
    public List<ProjectSpeaker> parse(Long projectId, Long scheduleId, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        ProjectSchedule s = scheduleMapper.selectById(scheduleId);
        if (s == null || !projectId.equals(s.getProjectId())) {
            throw new BusinessException("日程不存在");
        }
        if (!"PENDING".equals(s.getParseStatus()) && !"FAILED".equals(s.getParseStatus()) && !"DONE".equals(s.getParseStatus())) {
            throw new BusinessException("日程正在解析中，请稍后");
        }
        s.setParseStatus("PARSING");
        scheduleMapper.updateById(s);
        long begin = System.currentTimeMillis();
        try {
            byte[] bytes = projectService.storageRead(s.getFilePath());
            if (bytes == null) {
                throw new BusinessException("日程文件已不存在，请重新上传");
            }
            Extracted ext = extract(s.getFileName(), bytes);
            List<ProjectSpeaker> result = parseInternal(s, ext);
            s.setParseStatus("DONE");
            s.setParseMessage("解析到 " + result.size() + " 位参会专家（候选，请人工确认）");
            try {
                s.setRawResult(json.writeValueAsString(Map.of(
                        "count", result.size(),
                        "tableRows", ext.tableRows.size(),
                        "textChars", ext.plainText == null ? 0 : ext.plainText.length())));
            } catch (Exception ignored) {
            }
            scheduleMapper.updateById(s);
            // 替换本日程未确认候选
            speakerMapper.delete(new LambdaQueryWrapper<ProjectSpeaker>()
                    .eq(ProjectSpeaker::getScheduleId, s.getId())
                    .eq(ProjectSpeaker::getConfirmed, 0));
            for (ProjectSpeaker sp : result) {
                sp.setProjectId(projectId);
                sp.setScheduleId(s.getId());
                sp.setConfirmed(0);
                sp.setCreatedBy(user == null ? null : user.getUserId());
                speakerMapper.insert(sp);
            }
            log.info("日程解析完成 project={} file={} 耗时={}ms 候选={}人",
                    projectId, s.getFileName(), System.currentTimeMillis() - begin, result.size());
            return speakerMapper.selectList(new LambdaQueryWrapper<ProjectSpeaker>()
                    .eq(ProjectSpeaker::getScheduleId, s.getId())
                    .eq(ProjectSpeaker::getConfirmed, 0)
                    .orderByAsc(ProjectSpeaker::getId));
        } catch (BusinessException e) {
            s.setParseStatus("FAILED");
            s.setParseMessage(e.getMessage());
            scheduleMapper.updateById(s);
            throw e;
        } catch (Exception e) {
            log.warn("日程解析失败 project={} file={}：{}", projectId, s.getFileName(), e.getMessage());
            s.setParseStatus("FAILED");
            s.setParseMessage("解析失败（" + e.getClass().getSimpleName() + "），可重试或手动录入参会专家");
            scheduleMapper.updateById(s);
            throw new BusinessException("日程解析失败，可重试或手动录入参会专家");
        }
    }

    /** 解析主流程：规则打底 → LLM 兜底 → 合并去重 → 专家库精确匹配。 */
    List<ProjectSpeaker> parseInternal(ProjectSchedule s, Extracted ext) {
        List<ProjectSpeaker> merged = new ArrayList<>(ruleParse(ext));
        if (merged.isEmpty() || merged.size() < 2) {
            // 表格缺失/过薄时 LLM 兜底（未配置 Ollama 返回 null 自动跳过）
            List<ProjectSpeaker> semantic = ollamaService.extractScheduleSpeakers(ext.plainText);
            if (semantic != null && !semantic.isEmpty()) {
                Set<String> seen = new LinkedHashSet<>();
                for (ProjectSpeaker sp : merged) seen.add(normalizeName(sp.getExpertName()));
                for (ProjectSpeaker sp : semantic) {
                    if (seen.add(normalizeName(sp.getExpertName()))) {
                        merged.add(sp);
                    }
                }
            }
        }
        if (merged.isEmpty()) {
            throw new BusinessException("未能从日程中识别出参会专家，请手动录入");
        }
        matchExperts(merged);
        return merged;
    }

    // ==================== 规则打底 ====================

    /** 规则解析：先定位表头行确定列映射，再逐数据行提取；多人同段=讨论嘉宾；页脚「大会主席」兜底。 */
    List<ProjectSpeaker> ruleParse(Extracted ext) {
        List<ProjectSpeaker> result = new ArrayList<>();
        int timeIdx = -1, topicIdx = -1, speakerIdx = -1, hostIdx = -1;
        for (List<String> row : ext.tableRows) {
            if (row == null || row.isEmpty()) continue;
            List<String> cells = row.stream().map(c -> c == null ? "" : c.trim()).toList();
            String compact = String.join("", cells).replaceAll("\\s", "");
            // 表头行：确定列映射（时间/主题/讲者/主持；示例日程中"主 持"拆两行，逐字符匹配）
            if (compact.contains("讲者") || compact.contains("主持") || compact.contains("主题")) {
                for (int i = 0; i < cells.size(); i++) {
                    String c = cells.get(i).replaceAll("\\s", "");
                    if (timeIdx < 0 && c.contains("时") && !c.contains("时场")) timeIdx = i;
                    if (topicIdx < 0 && (c.contains("主题") || c.contains("题目") || c.contains("议题"))) topicIdx = i;
                    if (speakerIdx < 0 && c.contains("讲")) speakerIdx = i;
                    if (hostIdx < 0 && c.contains("主") && !c.contains("主席") && !c.contains("主题")) hostIdx = i;
                }
                continue;
            }
            if (timeIdx < 0 && speakerIdx < 0) continue;   // 尚未定位到表头，跳过
            String time = timeIdx >= 0 && timeIdx < cells.size() ? cells.get(timeIdx) : "";
            String topic = topicIdx >= 0 && topicIdx < cells.size() ? cells.get(topicIdx) : "";
            boolean chairRow = topic.contains("主席致辞") || topic.contains("大会主席");
            if (speakerIdx >= 0 && speakerIdx < cells.size()) {
                List<String> names = extractNames(cells.get(speakerIdx));
                String role = names.size() > 1 ? ProjectSpeaker.ROLE_DISCUSSANT
                        : (chairRow ? ProjectSpeaker.ROLE_CHAIR : ProjectSpeaker.ROLE_SPEAKER);
                for (String n : names) {
                    result.add(speaker(n, role, topic, time));
                }
            }
            if (hostIdx >= 0 && hostIdx < cells.size()) {
                for (String n : extractNames(cells.get(hostIdx))) {
                    result.add(speaker(n, ProjectSpeaker.ROLE_HOST, topic, time));
                }
            }
        }
        // 页脚/正文「大会主席：X」兜底：已识别的同名主席/讲者升格 CHAIR，未识别的补一条
        if (ext.plainText != null) {
            Matcher m = Pattern.compile("大会主席[:：]\\s*([\\u4e00-\\u9fa5·\\s]{2,12}?)\\s*(教授|主任医师|研究员|博士)?[，,。;；\\n]").matcher(ext.plainText);
            while (m.find()) {
                String name = normalizeName(m.group(1));
                if (!isPlausibleName(name)) continue;
                // 仅升格致辞/主席段落的同名讲者，避免把「大会总结」等普通发言也升为主席
                boolean existed = false;
                for (ProjectSpeaker sp : result) {
                    if (normalizeName(sp.getExpertName()).equals(name)
                            && sp.getSegmentTitle() != null
                            && (sp.getSegmentTitle().contains("致辞") || sp.getSegmentTitle().contains("主席"))) {
                        sp.setRole(ProjectSpeaker.ROLE_CHAIR);
                        existed = true;
                    }
                }
                if (!existed) {
                    result.add(speaker(name, ProjectSpeaker.ROLE_CHAIR, "大会主席", ""));
                }
            }
        }
        return result;
    }

    private ProjectSpeaker speaker(String name, String role, String topic, String time) {
        ProjectSpeaker sp = new ProjectSpeaker();
        sp.setExpertName(name);
        sp.setRole(role);
        sp.setSegmentTitle(blankToNull(topic));
        sp.setSegmentTime(blankToNull(time));
        return sp;
    }

    /** 从单元格文本提取人名列表：换行切块，名字+头衔正则提取，剥头衔、归一化全角空格。 */
    List<String> extractNames(String cell) {
        List<String> names = new ArrayList<>();
        if (cell == null || cell.isBlank()) return names;
        for (String line : cell.split("\\R")) {
            // 全角/不换行空格是人名内部间隔（如「马　媛」），直接剔除；ASCII 空格保留为人名间分隔
            String cleaned = line.replace("\u3000", "").replace("\u00a0", "").replace("|", " ").trim();
            if (cleaned.isEmpty()) continue;
            // 单行多人（空格分隔的名字+头衔序列）
            Matcher m = NAME_WITH_TITLE.matcher(cleaned);
            List<String> lineNames = new ArrayList<>();
            while (m.find()) {
                String name = normalizeName(m.group(1));
                if (isPlausibleName(name)) lineNames.add(name);
            }
            if (lineNames.isEmpty()) {
                String stripped = stripTitles(cleaned);
                if (isPlausibleName(stripped)) lineNames.add(stripped);
            }
            for (String n : lineNames) {
                if (!names.contains(n)) names.add(n);
            }
        }
        return names;
    }

    /** 剥离头衔后缀。 */
    String stripTitles(String v) {
        String s = v.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String t : TITLE_SUFFIXES) {
                if (s.endsWith(t)) {
                    s = s.substring(0, s.length() - t.length()).trim();
                    changed = true;
                }
            }
        }
        return s;
    }

    /** 归一化：去全部空白（含全角空格）、统一中间点。 */
    String normalizeName(String v) {
        return v == null ? "" : v.replaceAll("[\\u3000\\u00a0\\s]+", "").replace('・', '·');
    }

    /** 像人名：2-4 个汉字（允许 ·），且不含机构/议题特征词。 */
    boolean isPlausibleName(String v) {
        if (v == null) return false;
        String s = stripTitles(v);
        if (!CHINESE_NAME.matcher(s).matches()) return false;
        for (String w : List.of("医院", "大学", "大会", "会议", "规范", "优化", "解读", "指南", "论道", "致辞", "总结", "腾讯", "时间")) {
            if (s.contains(w)) return false;
        }
        return true;
    }

    // ==================== 专家库精确匹配 ====================

    /** 精确等值匹配（一次 IN 查询）；未命中 expert_id 保持 NULL=库外。 */
    void matchExperts(List<ProjectSpeaker> speakers) {
        Set<String> names = new LinkedHashSet<>();
        for (ProjectSpeaker sp : speakers) names.add(sp.getExpertName());
        if (names.isEmpty()) return;
        List<Expert> hits = expertService.list(new LambdaQueryWrapper<Expert>().in(Expert::getName, names));
        Map<String, Long> byName = new LinkedHashMap<>();
        for (Expert e : hits) {
            byName.putIfAbsent(normalizeName(e.getName()), e.getId());   // 重名取先入库者，人工可改绑
        }
        for (ProjectSpeaker sp : speakers) {
            sp.setExpertId(byName.get(normalizeName(sp.getExpertName())));
        }
    }

    /**
     * 批量简历建档（Owner 2026-11-09 需求：库外专家随批入档）：
     * 多份简历 → 复用 ResumeParseService 提取 姓名/单位/职称/简介 → 复用 ProjectService.createExpertCard
     * 建档并自动关联项目专家名单 → 按解析姓名回绑本项目参会专家（含候选）中未关联的同名行。
     * 单文件失败不影响其余；每文件结果逐条返回。
     */
    public List<Map<String, Object>> archiveByResumes(Long projectId, List<org.springframework.web.multipart.MultipartFile> files, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请选择至少一份简历文件");
        }
        if (files.size() > 10) {
            throw new BusinessException("单次最多 10 份简历");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (org.springframework.web.multipart.MultipartFile file : files) {
            Map<String, Object> r = new LinkedHashMap<>();
            String fileName = file.getOriginalFilename();
            r.put("fileName", fileName);
            try {
                byte[] bytes = file.getBytes();
                ResumeParseService.ResumeParseResult parsed =
                        resumeParseService.parse(fileName == null ? "" : fileName, bytes);
                Map<String, String> fields = parsed.fields();
                String name = fields.get("姓名");
                if (name == null || name.isBlank()) {
                    r.put("ok", false);
                    r.put("error", "未能从简历识别出姓名，请手动建档");
                    results.add(r);
                    continue;
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("name", name);
                body.put("unit", orDefault(fields.get("单位"), "待补充"));
                body.put("title", orDefault(fields.get("职称"), "待补充"));
                body.put("profile", orDefault(fields.get("简介"), "来自项目日程批量简历建档"));
                body.put("contact", fields.get("联系方式"));
                Expert saved = createExpertForProject(project, body, user);
                r.put("ok", true);
                r.put("expertId", saved.getId());
                r.put("expertName", saved.getName());
                r.put("boundSpeakers", bindSpeakersByNativeName(projectId, saved.getId(), saved.getName()));
            } catch (Exception e) {
                r.put("ok", false);
                r.put("error", "建档失败：" + e.getMessage());
            }
            results.add(r);
        }
        return results;
    }

    /**
     * 项目内建档（成员级，Owner 2026-11-09「库外专家随时建档」口径）：
     * 与 ProjectService.createExpertCard 的区别——权限为项目成员而非 project:manage，
     * 且**只接受 姓名/单位/职称/简介/联系方式**，不接受身份证/银行卡等敏感字段
     * （敏感字段仍须在专家库模块按既有管控维护）。
     */
    private Expert createExpertForProject(Project project, Map<String, Object> body, AuthUser user) {
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
        if (name.isEmpty()) {
            throw new BusinessException("姓名不能为空");
        }
        Expert expert = new Expert();
        expert.setName(name);
        expert.setUnit(strOf(body.get("unit")));
        expert.setTitle(strOf(body.get("title")));
        expert.setProfile(strOf(body.get("profile")));
        expert.setContact(strOf(body.get("contact")));
        Expert saved = expertService.create(expert, user);

        com.jshz.expert.entity.ProjectExpert rel = new com.jshz.expert.entity.ProjectExpert();
        rel.setProjectId(project.getId());
        rel.setExpertId(saved.getId());
        rel.setRole("嘉宾");
        rel.setCreateBy(com.jshz.expert.common.ParamUtils.usernameOf(user));
        expertRelMapper.insert(rel);

        projectService.operationRecord(user, "项目「" + project.getName() + "」新建专家卡「" + saved.getName() + "」（日程库外专家建档）",
                "PROJECT", project.getId());
        return saved;
    }

    private String strOf(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** 手动建档单条（成员级）：建档 + 按姓名回绑参会专家（含候选）。 */
    public Map<String, Object> archiveManual(Long projectId, Map<String, Object> body, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        String name = body == null || body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
        if (name.isEmpty()) {
            throw new BusinessException("姓名不能为空");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("name", name);
        normalized.put("unit", strOf(body.get("unit")).isEmpty() ? "待补充" : strOf(body.get("unit")));
        normalized.put("title", strOf(body.get("title")).isEmpty() ? "待补充" : strOf(body.get("title")));
        normalized.put("profile", strOf(body.get("profile")).isEmpty() ? "来自项目日程建档" : strOf(body.get("profile")));
        normalized.put("contact", strOf(body.get("contact")));
        Expert saved = createExpertForProject(project, normalized, user);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("fileName", "手动录入");
        r.put("expertId", saved.getId());
        r.put("expertName", saved.getName());
        r.put("boundSpeakers", bindSpeakersByNativeName(projectId, saved.getId(), saved.getName()));
        return r;
    }

    /** 按姓名回绑本项目参会专家（候选与正式，expert_id IS NULL），返回绑定行数。 */
    private int bindSpeakersByNativeName(Long projectId, Long expertId, String expertName) {
        List<ProjectSpeaker> targets = speakerMapper.selectList(new LambdaQueryWrapper<ProjectSpeaker>()
                .eq(ProjectSpeaker::getProjectId, projectId)
                .isNull(ProjectSpeaker::getExpertId)
                .eq(ProjectSpeaker::getExpertName, expertName));
        for (ProjectSpeaker sp : targets) {
            sp.setExpertId(expertId);
            speakerMapper.updateById(sp);
        }
        return targets.size();
    }

    private String orDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    // ==================== 确认 / 手动补录 / 删除 ====================

    /** 人工确认保存：整表覆盖式提交正式参会专家。 */
    public List<ProjectSpeaker> confirm(Long projectId, List<Map<String, Object>> items, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        if (items == null) {
            throw new BusinessException("确认内容不能为空");
        }
        List<ProjectSpeaker> keep = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String name = item.get("expertName") == null ? "" : String.valueOf(item.get("expertName")).trim();
            if (name.isEmpty()) {
                throw new BusinessException("专家姓名不能为空");
            }
            String role = String.valueOf(item.getOrDefault("role", ""));
            validateRole(role);
            ProjectSpeaker sp = new ProjectSpeaker();
            sp.setProjectId(projectId);
            sp.setExpertName(name);
            sp.setRole(role);
            sp.setExpertId(parseNullableId(item.get("expertId")));
            if (sp.getExpertId() != null && expertService.getById(sp.getExpertId()) == null) {
                throw new BusinessException("关联的专家档案不存在");
            }
            sp.setSegmentTitle(blankToNull(strOrNull(item.get("segmentTitle"))));
            sp.setSegmentTime(blankToNull(strOrNull(item.get("segmentTime"))));
            Object sid = item.get("scheduleId");
            sp.setScheduleId(sid == null ? null : parseNullableId(sid));
            sp.setConfirmed(1);
            keep.add(sp);
        }
        speakerMapper.delete(new LambdaQueryWrapper<ProjectSpeaker>()
                .eq(ProjectSpeaker::getProjectId, projectId)
                .eq(ProjectSpeaker::getConfirmed, 1));
        for (ProjectSpeaker sp : keep) {
            sp.setCreatedBy(user == null ? null : user.getUserId());
            speakerMapper.insert(sp);
        }
        projectService.operationRecord(user, "确认项目「" + project.getName() + "」参会专家 " + keep.size() + " 人",
                "PROJECT", projectId);
        return speakerMapper.selectList(new LambdaQueryWrapper<ProjectSpeaker>()
                .eq(ProjectSpeaker::getProjectId, projectId)
                .eq(ProjectSpeaker::getConfirmed, 1)
                .orderByAsc(ProjectSpeaker::getId));
    }

    /** 手动补录一条（解析失败/遗漏兜底）。 */
    public ProjectSpeaker addSpeaker(Long projectId, Map<String, Object> body, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        String name = body == null || body.get("expertName") == null ? "" : String.valueOf(body.get("expertName")).trim();
        if (name.isEmpty()) {
            throw new BusinessException("专家姓名不能为空");
        }
        String role = body == null ? "" : String.valueOf(body.getOrDefault("role", ""));
        validateRole(role);
        ProjectSpeaker sp = new ProjectSpeaker();
        sp.setProjectId(projectId);
        sp.setExpertName(name);
        sp.setRole(role);
        sp.setExpertId(parseNullableId(body.get("expertId")));
        if (sp.getExpertId() != null && expertService.getById(sp.getExpertId()) == null) {
            throw new BusinessException("关联的专家档案不存在");
        }
        sp.setSegmentTitle(blankToNull(strOrNull(body.get("segmentTitle"))));
        sp.setSegmentTime(blankToNull(strOrNull(body.get("segmentTime"))));
        sp.setConfirmed(1);
        sp.setCreatedBy(user == null ? null : user.getUserId());
        speakerMapper.insert(sp);
        return sp;
    }

    /** 删除一条参会专家记录（候选或正式均可，前端明示）。 */
    public void deleteSpeaker(Long projectId, Long speakerId, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        ProjectSpeaker sp = speakerMapper.selectById(speakerId);
        if (sp == null || !projectId.equals(sp.getProjectId())) {
            throw new BusinessException("记录不存在");
        }
        speakerMapper.deleteById(speakerId);
    }

    private void validateRole(String role) {
        if (!List.of(ProjectSpeaker.ROLE_CHAIR, ProjectSpeaker.ROLE_HOST,
                ProjectSpeaker.ROLE_SPEAKER, ProjectSpeaker.ROLE_DISCUSSANT).contains(role)) {
            throw new BusinessException("会议角色必须为 大会主席/大会主持/大会讲者/讨论嘉宾");
        }
    }

    // ==================== 文件提取 ====================

    /** 提取结果：plainText 全文（LLM 兜底/页脚扫描），tableRows 表格行（规则打底）。 */
    public record Extracted(String plainText, List<List<String>> tableRows) {}

    Extracted extract(String fileName, byte[] bytes) throws IOException {
        String ext = extension(fileName);
        return switch (ext) {
            case "ppt", "pptx" -> extractPpt(bytes);
            case "docx" -> extractDocx(bytes);
            case "doc" -> new Extracted(docText(bytes), List.of());
            case "pdf" -> extractPdf(bytes);
            case "jpg", "jpeg", "png" -> extractImage(bytes);
            default -> throw new BusinessException("不支持的日程格式");
        };
    }

    private Extracted extractPpt(byte[] bytes) throws IOException {
        StringBuilder text = new StringBuilder();
        List<List<String>> rows = new ArrayList<>();
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            for (var slide : ppt.getSlides()) {
                for (XSLFShape shape : slide) {
                    collectShape(shape, text, rows);
                }
            }
        }
        return new Extracted(text.toString(), rows);
    }

    /** 递归收集：表格逐行逐单元格（保结构），文本形状逐行。 */
    private void collectShape(XSLFShape shape, StringBuilder text, List<List<String>> rows) {
        if (shape instanceof org.apache.poi.xslf.usermodel.XSLFGroupShape group) {
            for (var child : group) {
                collectShape(child, text, rows);
            }
        } else if (shape instanceof XSLFTable table) {
            for (var row : table) {
                List<String> cells = new ArrayList<>();
                for (var cell : row) {
                    String v = cell.getText();
                    cells.add(v == null ? "" : v.trim());
                    if (v != null && !v.isBlank()) text.append(v.trim()).append('\n');
                }
                rows.add(cells);
            }
        } else if (shape instanceof XSLFTextShape ts) {
            String v = ts.getText();
            if (v != null && !v.isBlank()) text.append(v.trim()).append('\n');
        }
    }

    private Extracted extractDocx(byte[] bytes) throws IOException {
        StringBuilder text = new StringBuilder();
        List<List<String>> rows = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            text.append(extractor.getText());
            for (XWPFTable table : doc.getTables()) {
                for (var row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (var cell : row.getTableCells()) {
                        String v = cell.getText();
                        cells.add(v == null ? "" : v.trim());
                    }
                    rows.add(cells);
                }
            }
        }
        return new Extracted(text.toString(), rows);
    }

    private String docText(byte[] bytes) throws IOException {
        org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(new ByteArrayInputStream(bytes));
        try {
            return new WordExtractor(doc).getText();
        } finally {
            try { doc.close(); } catch (Exception ignored) {}
        }
    }

    private Extracted extractPdf(byte[] bytes) throws IOException {
        String text;
        try (PDDocument doc = PDDocument.load(bytes)) {
            text = new PDFTextStripper().getText(doc);
            // 扫描件回退 OCR（复用简历解析口径：前 3 页）
            if (text.trim().length() < Math.max(20L, doc.getNumberOfPages() * 20L)) {
                var renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
                int pages = Math.min(3, doc.getNumberOfPages());
                StringBuilder ocr = new StringBuilder();
                for (int i = 0; i < pages; i++) {
                    BufferedImage raster = renderer.renderImageWithDPI(i, 150);
                    ocr.append(KnowledgeDocTextExtractor.ocrPage(raster, false)).append('\n');
                }
                if (ocr.length() > text.trim().length()) {
                    text = ocr.toString();
                }
            }
        }
        return new Extracted(text, List.of());
    }

    private Extracted extractImage(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new BusinessException("无法读取图片内容");
        }
        return new Extracted(KnowledgeDocTextExtractor.ocrPage(image, false), List.of());
    }

    // ==================== 小工具 ====================

    private String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private String strOrNull(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Long parseNullableId(Object v) {
        if (v == null || String.valueOf(v).isBlank() || "null".equalsIgnoreCase(String.valueOf(v))) return null;
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new BusinessException("非法的ID参数");
        }
    }
}
