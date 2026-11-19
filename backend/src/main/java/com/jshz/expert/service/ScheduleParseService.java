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
import org.springframework.beans.factory.annotation.Value;
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
    private final ProjectStorageService storageService;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    /** 日程单文件上限：单一真源 application.yml expert.upload.doc-max-bytes（Owner 2026-11-10：真实日程 pptx 内嵌图片多，20MB 撞 413；与 Spring multipart 全局 50MB 对齐）。 */
    @Value("${expert.upload.doc-max-bytes:52428800}")
    private long docMaxBytes;
    private static final List<String> ALLOWED_EXTENSIONS =
            List.of("pdf", "doc", "docx", "ppt", "pptx", "jpg", "jpeg", "png", "xlsx", "xls");

    /** 头衔后缀（清洗姓名用；命中即从词条尾部剥离）。 */
    private static final List<String> TITLE_SUFFIXES = List.of(
            "教授", "副教授", "主任医师", "副主任医师", "主治医师", "住院医师",
            "主任药师", "副主任药师", "主管药师", "药师",
            "主任技师", "副主任技师", "主管技师", "技师",
            "主任护师", "副主任护师", "主管护师", "护师",
            "研究员", "副研究员", "助理研究员", "讲师", "博士", "书记",
            "院长", "副院长", "主任", "副主任", "部长", "秘书长", "院士");

    private static final Pattern CHINESE_NAME = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,4}$");

    // ==================== 上传 / 查询 ====================

    /** 上传日程（替换式：硬删旧生效份与候选、软删旧附件，不做历史版本——Owner 2026-11-10 口径）。 */
    public ProjectSchedule upload(Long projectId, MultipartFile file, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("日程文件不能为空");
        }
        String ext = com.jshz.expert.common.FileNames.extension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的日程格式（支持 pdf/doc/docx/ppt/pptx/jpg/png/xlsx/xls）");
        }
        if (file.getSize() > docMaxBytes) {
            throw new BusinessException("日程文件超过 " + (docMaxBytes / 1024 / 1024) + "MB 限制");
        }
        String stored = projectService.storageSave(file);
        // 旧生效份：硬删日程行与未确认候选（人工确认成果不受影响），旧附件软删，旧文件无引用时物理删
        List<String> orphanFiles = new ArrayList<>();
        List<String> oldFiles = scheduleMapper.selectList(new LambdaQueryWrapper<ProjectSchedule>()
                .eq(ProjectSchedule::getProjectId, projectId)
                .eq(ProjectSchedule::getIsCurrent, 1))
                .stream().map(ProjectSchedule::getFilePath).filter(java.util.Objects::nonNull).toList();
        for (ProjectSchedule old : scheduleMapper.selectList(new LambdaQueryWrapper<ProjectSchedule>()
                .eq(ProjectSchedule::getProjectId, projectId)
                .eq(ProjectSchedule::getIsCurrent, 1))) {
            speakerMapper.delete(new LambdaQueryWrapper<ProjectSpeaker>()
                    .eq(ProjectSpeaker::getScheduleId, old.getId())
                    .eq(ProjectSpeaker::getConfirmed, 0));
            scheduleMapper.deleteById(old.getId());
        }
        for (String oldFile : oldFiles) {
            if (oldFile.equals(stored)) continue;
            // 旧日程附件软删，避免附件列表堆积重复
            docMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.jshz.expert.entity.ProjectDoc>()
                    .eq(com.jshz.expert.entity.ProjectDoc::getProjectId, projectId)
                    .eq(com.jshz.expert.entity.ProjectDoc::getFilePath, oldFile)
                    .eq(com.jshz.expert.entity.ProjectDoc::getIsDeleted, 0)
                    .set(com.jshz.expert.entity.ProjectDoc::getIsDeleted, 1));
            // 全库（含软删）无引用才物理删文件
            if (docMapper.selectCount(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectDoc>()
                    .eq(com.jshz.expert.entity.ProjectDoc::getFilePath, oldFile)) == 0) {
                orphanFiles.add(oldFile);
            }
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

        // 旧日程文件已无任何引用（含软删行），物理删除落盘文件
        for (String orphan : orphanFiles) {
            log.info("物理删除被替换的旧日程文件 project={} file={}", projectId, orphan);
            if (storageService != null) storageService.deleteFile(orphan);
        }

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

    /**
     * 删除日程阶段附件（Owner 2026-11-10 需求：附件可删）。
     * 软删 project_doc；若删的是当前生效日程文件，联动硬删该项目全部日程记录与未确认候选
     * （人工确认成果保留），文件无其他引用时物理删——项目回到「未上传日程」状态。
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void deleteScheduleDoc(Long projectId, Long docId, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        com.jshz.expert.entity.ProjectDoc doc = docMapper.selectById(docId);
        if (doc == null || !projectId.equals(doc.getProjectId()) || doc.getIsDeleted() != 0) {
            throw new BusinessException("附件不存在");
        }
        doc.setIsDeleted(1);
        doc.setDeletedBy(user == null ? null : user.getUsername());
        doc.setDeletedTime(java.time.LocalDateTime.now());
        docMapper.updateById(doc);

        ProjectSchedule current = currentSchedule(projectId);
        boolean clearedSchedule = current != null && doc.getFilePath() != null
                && doc.getFilePath().equals(current.getFilePath());
        if (clearedSchedule) {
            speakerMapper.delete(new LambdaQueryWrapper<ProjectSpeaker>()
                    .eq(ProjectSpeaker::getProjectId, projectId)
                    .eq(ProjectSpeaker::getConfirmed, 0));
            scheduleMapper.delete(new LambdaQueryWrapper<ProjectSchedule>()
                    .eq(ProjectSchedule::getProjectId, projectId));
        }
        // 全库（含软删行）无其他引用才物理删文件
        boolean physicalDelete = doc.getFilePath() != null
                && docMapper.selectCount(new LambdaQueryWrapper<com.jshz.expert.entity.ProjectDoc>()
                .eq(com.jshz.expert.entity.ProjectDoc::getFilePath, doc.getFilePath())) == 0;
        if (physicalDelete && storageService != null) {
            log.info("日程附件已无引用，物理删除 project={} file={}", projectId, doc.getFilePath());
            storageService.deleteFile(doc.getFilePath());
        }
        projectService.operationRecord(user, (clearedSchedule
                ? "删除项目「" + project.getName() + "」日程附件《" + doc.getFileName() + "》，已联动清除该日程的解析候选"
                : "删除项目「" + project.getName() + "」日程附件《" + doc.getFileName() + "》"),
                "PROJECT", projectId);
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
        List<String> unresolved = new ArrayList<>();
        for (String block : standaloneBlocks(ext)) {
            if ((ROLE_LABEL.matcher(block).find() && labeledAppearances(List.of(block)).isEmpty())
                    || (hasTitleWord(block) && extractNames(block).stream().anyMatch(name -> merged.stream()
                        .noneMatch(existing -> normalizeName(existing.getExpertName()).equals(name))))) {
                unresolved.add(block);
            }
        }
        String semanticInput = merged.size() < 2 ? ext.plainText : String.join("\n", unresolved);
        if (semanticInput != null && !semanticInput.isBlank()) {
            // P4（Owner 2026-11-12）：LLM 兜底约束——超时重试 1 次；候选须回指原文（姓名含于输入，
            // 议题/时间字段字符覆盖率 ≥60），按出场（姓名+时间+议题）去重，允许同人跨角色保留多条。
            List<ProjectSpeaker> semantic = ollamaService.extractScheduleSpeakers(semanticInput);
            if (semantic == null && !unresolved.isEmpty()) {
                semantic = ollamaService.extractScheduleSpeakers(semanticInput);
            }
            String normSemanticInput = normalizeName(semanticInput);
            if (semantic != null) {
                for (ProjectSpeaker candidate : semantic) {
                    String name = normalizeName(candidate.getExpertName());
                    boolean nameGrounded = isPlausibleName(name) && normSemanticInput.contains(name);
                    boolean fieldsGrounded = coverageRatio(semanticInput, candidate.getSegmentTitle()) >= 60
                            && coverageRatio(semanticInput, candidate.getSegmentTime()) >= 60;
                    if (nameGrounded && fieldsGrounded
                            && merged.stream().noneMatch(existing -> sameAppearance(existing, candidate))) {
                        merged.add(candidate);
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

    /** 规则解析：先定位表头行确定列映射（放宽为 ≥2 关键词命中，兼容「时间/内容/嘉宾」等列头），
     *  再逐数据行按角色词判定；无表头时用时间段锚定兜底；页脚「大会/会议主席」最后补齐。 */
    List<ProjectSpeaker> ruleParse(Extracted ext) {
        if (ext.pptBlocks != null) return parsePptBlocks(ext.pptBlocks);
        List<ProjectSpeaker> result = ruleParse(ext, null);
        applyCeremonyChairRoles(result);
        return result;
    }

    private List<ProjectSpeaker> ruleParse(Extracted ext,
                                           java.util.function.BiConsumer<Integer, ProjectSpeaker> rowCandidate) {
        List<ProjectSpeaker> result = new ArrayList<>();
        int timeIdx = -1, topicIdx = -1, speakerIdx = -1, hostIdx = -1;
        String prevTime = "";
        for (int rowIndex = 0; rowIndex < ext.tableRows.size(); rowIndex++) {
            List<String> row = ext.tableRows.get(rowIndex);
            int firstCandidate = result.size();
            if (row == null || row.isEmpty()) continue;
            List<String> cells = row.stream().map(c -> c == null ? "" : c.trim()).toList();
            // 表头行：≥2 个列头关键词命中（时间/主题/题目/议题/内容/讲题/讲者/嘉宾/主持/讲/专家）
            if (isHeaderRow(cells)) {
                for (int i = 0; i < cells.size(); i++) {
                    String c = compactCell(cells.get(i));
                    if (timeIdx < 0 && c.contains("时") && !c.contains("时场")) timeIdx = i;
                    // 讲题/内容等主题列头先判（「讲题」不能落进讲者列——2026-11-10 曲径通幽日程误抓根因）
                    if (topicIdx < 0 && (c.contains("主题") || c.contains("题目") || c.contains("议题")
                            || c.contains("内容") || c.contains("讲题"))) topicIdx = i;
                    if (speakerIdx < 0 && !c.contains("讲题")
                            && (c.contains("讲者") || c.contains("发言人") || c.contains("发言者") || c.contains("报告人") || c.contains("嘉宾") || c.contains("专家") || c.contains("讲"))) speakerIdx = i;
                    if (hostIdx < 0 && c.contains("主") && !c.contains("主席") && !c.contains("主题") && !c.contains("内容")) hostIdx = i;
                }
                continue;
            }
            if (timeIdx < 0 && speakerIdx < 0) continue;   // 尚未定位到表头，跳过
            String time = timeIdx >= 0 && timeIdx < cells.size() ? cells.get(timeIdx) : "";
            // 时间列校验：合并单元格留空或被非时间文字（如整行「主持：…」）占据时，沿用上一行有效时间
            if (TIME_VALUE.matcher(time).find()) {
                prevTime = time;
            } else {
                time = prevTime;
            }
            String topic = topicIdx >= 0 && topicIdx < cells.size() ? cells.get(topicIdx) : "";
            String speakerCell = speakerIdx >= 0 && speakerIdx < cells.size() ? cells.get(speakerIdx) : "";
            List<String> names = extractNames(speakerCell);
            if (names.isEmpty()) {
                // 姓名落在「内容」列的日程（如「讨论：A 教授、B 教授」「真知灼见:A、B、C」）：
                // 主题单元格带头衔词，或 token 判名 ≥2 人（顿号分隔名单强信号）才提取，
                // 防止「静脉曲张日间手术」这类无头衔讲题被当成多人名（2026-11-10 大医万里行 docx 实证补 ≥2 人门控）
                List<String> topicNames = extractNames(topic);
                if (!topicNames.isEmpty() && (hasTitleWord(topic) || topicNames.size() >= 2)) {
                    names = topicNames;
                    speakerCell = topic;
                    topic = topic.replaceFirst("^([\\u4e00-\\u9fa5]{1,6})[：:].*", "$1");
                }
            }
            if (!names.isEmpty()) {
                String role = roleFor(topic, speakerCell, names.size());
                for (String n : names) {
                    result.add(speaker(n, role, topic, time));
                }
            } else if (hostIdx < 0) {
                // 表内整行「主持：王雅 教授 某医院」（无时间/讲者列）：提取为主持行
                for (String c : cells) {
                    if (c.contains("主持") && extractNames(c).isEmpty() == false) {
                        for (String n : extractNames(c)) {
                            result.add(speaker(n, ProjectSpeaker.ROLE_HOST, "主持", time));
                        }
                        break;
                    }
                }
            }
            if (hostIdx >= 0 && hostIdx < cells.size()) {
                // P3（Owner 2026-11-12）：主持名单合并格计一次（取首位）；单人仍正常
                List<String> hostNames = extractNames(cells.get(hostIdx));
                if (!hostNames.isEmpty()) {
                    result.add(speaker(hostNames.get(0), ProjectSpeaker.ROLE_HOST, topic, time));
                }
            }
            if (rowCandidate != null) {
                for (int i = firstCandidate; i < result.size(); i++) rowCandidate.accept(rowIndex, result.get(i));
            }
        }
        // 无表头兜底：按时间段锚定文本行（含 tableRows 未命中表头/纯文本日程）
        if (result.isEmpty()) {
            result.addAll(headerlessParse(ext));
        }
        // 页脚/正文「大会主席：X」「会议主席：X」兜底：已识别的同名致辞/主席段讲者升格 CHAIR，未识别的补一条
        if (ext.plainText != null) {
            Matcher m = Pattern.compile("(?:大会|会议)主席[:：]\\s*([^\\n]{0,40})").matcher(ext.plainText);
            while (m.find()) {
                for (String name : extractNames(m.group(1))) {
                    // 同名主席已有致辞或主席段落时，沿用该条有议题与时间的记录。
                    boolean existed = false;
                    for (ProjectSpeaker sp : result) {
                        if (normalizeName(sp.getExpertName()).equals(name)
                                && sp.getSegmentTitle() != null
                                && !ProjectSpeaker.ROLE_HOST.equals(sp.getRole())
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
        }
        for (ProjectSpeaker candidate : labeledAppearances(standaloneBlocks(ext))) {
            if (result.stream().noneMatch(existing -> sameAppearance(existing, candidate))) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static final Pattern ROLE_LABEL = Pattern.compile(
            "(大会主持|会议主持|环节主持|主持人|主持|讲者|讲课嘉宾|主讲人|主讲|讨论嘉宾|讨论)\\s*[:：]");
    private static final Pattern LOCAL_TIME = Pattern.compile(
            "\\d{1,2}[:：]\\d{2}(?:\\s*[-—–~～至]\\s*\\d{1,2}[:：]\\d{2})?");

    private List<String> standaloneBlocks(Extracted ext) {
        if (ext.textBlocks != null) return ext.textBlocks;
        if (ext.plainText == null) return List.of();
        Set<String> cellLines = new LinkedHashSet<>();
        for (List<String> row : ext.tableRows) {
            if (row == null) continue;
            for (String cell : row) {
                if (cell != null) cell.lines().map(String::strip).forEach(cellLines::add);
            }
        }
        return ext.plainText.lines().filter(line -> !cellLines.contains(line.strip())).toList();
    }

    private List<ProjectSpeaker> labeledAppearances(List<String> blocks) {
        List<ProjectSpeaker> appearances = new ArrayList<>();
        for (String block : blocks) {
            for (String line : block.split("\\R")) {
                Matcher label = ROLE_LABEL.matcher(line);
                if (!label.find()) continue;
                String prefix = line.substring(0, label.start());
                Matcher clock = LOCAL_TIME.matcher(prefix);
                String time = clock.find() ? clock.group().replace('：', ':') : "";
                String topic = LOCAL_TIME.matcher(prefix).replaceAll("").strip()
                        .replaceAll("^[：:|｜·\\s]+|[：:|｜·\\s]+$", "");
                addLabeledPeople(appearances, line, label, topic, time);
            }
        }
        return appearances;
    }

    /** label 已定位到首个角色；保留下一角色前的全部名单，包括跨段落的姓名。 */
    private void addLabeledPeople(List<ProjectSpeaker> result, String text, Matcher label, String topic, String time) {
        boolean hasNextLabel;
        do {
            String roleLabel = label.group(1);
            int payloadStart = label.end();
            hasNextLabel = label.find();
            String role = roleLabel.contains("主持") ? ProjectSpeaker.ROLE_HOST
                    : roleLabel.contains("讨论") ? ProjectSpeaker.ROLE_DISCUSSANT : ProjectSpeaker.ROLE_SPEAKER;
            addAgendaPeople(result, text.substring(payloadStart, hasNextLabel ? label.start() : text.length()),
                    role, topic, time);
        } while (hasNextLabel);
    }

    private boolean sameAppearance(ProjectSpeaker left, ProjectSpeaker right) {
        return normalizeName(left.getExpertName()).equals(normalizeName(right.getExpertName()))
                && java.util.Objects.equals(left.getRole(), right.getRole())
                && java.util.Objects.equals(blankToNull(left.getSegmentTitle()), blankToNull(right.getSegmentTitle()))
                && java.util.Objects.equals(blankToNull(left.getSegmentTime()), blankToNull(right.getSegmentTime()));
    }

    /** 列头关键词（≥2 命中即认为是表头行）。 */
    private static final List<String> HEADER_KEYWORDS =
            List.of("时间", "主题", "题目", "议题", "内容", "讲者", "嘉宾", "主持", "专家", "讲");

    /** 单元格压缩空白：\\s 不含全角空格/不换行空格（「时 间」表头曾因此整表跳过——邯郸卫星会实证）。 */
    private String compactCell(String c) {
        return c == null ? "" : c.replace("\u3000", "").replace("\u00a0", "").replaceAll("\\s", "");
    }

    private boolean isHeaderRow(List<String> cells) {
        int hits = 0;
        for (String c : cells) {
            String v = compactCell(c);
            if (v.isEmpty() || v.length() > 6) continue;   // 数据单元格不会这么短还带列头词
            for (String k : HEADER_KEYWORDS) {
                if (v.contains(k)) { hits++; break; }
            }
        }
        return hits >= 2;
    }

    /** 单元格是否带头衔词（人名落的强信号，用于「姓名在内容列」的提取门控）。 */
    private static final List<String> TITLE_WORDS = List.of(
            "教授", "副教授", "主任医师", "副主任医师", "主治医师", "研究员", "副研究员", "讲师", "博士", "主任", "护师", "药师", "技师");

    private boolean hasTitleWord(String v) {
        if (v == null) return false;
        for (String t : TITLE_WORDS) {
            if (v.contains(t)) return true;
        }
        return false;
    }

    /** 行级角色判定：主席致辞→CHAIR；主持/环节行→HOST；讨论段或多人同段→DISCUSSANT；其余→SPEAKER。 */
    private String roleFor(String topic, String speakerCell, int nameCount) {
        String t = topic == null ? "" : topic;
        if (t.contains("主席致辞") || t.contains("大会主席") || t.contains("会议主席")) return ProjectSpeaker.ROLE_CHAIR;
        String cell = speakerCell == null ? "" : speakerCell;
        if (t.contains("主持") || cell.contains("主持")) return ProjectSpeaker.ROLE_HOST;
        if (t.contains("讨论") || cell.contains("讨论")) return ProjectSpeaker.ROLE_DISCUSSANT;
        if (t.contains("环节")) return ProjectSpeaker.ROLE_HOST;   // 「第一环节」行=环节主持
        return nameCount > 1 ? ProjectSpeaker.ROLE_DISCUSSANT : ProjectSpeaker.ROLE_SPEAKER;
    }

    /** 无表头兜底：逐行找时间段锚点，行内按启发式提取姓名（含角色词判定）。 */
    private List<ProjectSpeaker> headerlessParse(Extracted ext) {
        List<ProjectSpeaker> result = new ArrayList<>();
        Pattern TIME_RANGE = Pattern.compile("\\d{1,2}[:：]\\d{2}\\s*[-—~至]\\s*\\d{1,2}[:：]\\d{2}");
        List<String> lines = new ArrayList<>();
        if (ext.tableRows != null) {
            for (List<String> row : ext.tableRows) {
                if (row != null) lines.add(String.join(" | ", row));
            }
        }
        if (ext.plainText != null) {
            for (String line : ext.plainText.split("\\R")) lines.add(line);
        }
        // P1（Owner 2026-11-12）：纵向关联——OCR 文本里时间行与议题/姓名行是连续独立行，
        // 需要把「时间行 → 后续议题/姓名行」关联成组（144-日程 PDF 实证）
        List<String> associated = new ArrayList<>();
        StringBuilder group = new StringBuilder();
        for (String line : lines) {
            String t = line.strip();
            if (t.isEmpty()) continue;
            Matcher tm = TIME_RANGE.matcher(t);
            if (tm.find() && t.length() <= 20) {
                // 纯时间行：保存前一组，开始新组
                if (group.length() > 0) associated.add(group.toString());
                group = new StringBuilder(t);
            } else if (group.length() > 0 && t.length() >= 2) {
                group.append(' ').append(t);
            } else if (tm.find() && t.length() > 20) {
                // 时间+内容同行
                associated.add(t);
            } else if (t.length() >= 4 && containsName(t)) {
                // 无时间的独立姓名/议题行
                associated.add(t);
            }
        }
        if (group.length() > 0) associated.add(group.toString());
        for (String line : associated) {
            Matcher tm = TIME_RANGE.matcher(line);
            if (!tm.find()) continue;
            String time = tm.group().replace('：', ':');
            String rest = line.substring(tm.end()).trim();
            String lead = line.substring(0, tm.start()).trim();
            boolean timeLast = rest.isEmpty() || !containsName(rest);
            String payload = timeLast ? lead : rest;
            List<String> names = extractNames(payload);
            if (names.isEmpty()) continue;
            String role = roleFor(payload, payload, names.size());
            String topic = agendaColumns(payload).length > 1 ? agendaColumns(payload)[0] : "";
            for (String n : names) {
                result.add(speaker(n, role, blankToNull(topic), time));
            }
        }
        return result;
    }

    /** 文本中是否存在像人名的片段。 */
    private boolean containsName(String v) {
        return !extractNames(v).isEmpty();
    }

    private static final Pattern TIME_VALUE = Pattern.compile("\\d{1,2}[:：]\\d{2}");

    private ProjectSpeaker speaker(String name, String role, String topic, String time) {
        ProjectSpeaker sp = new ProjectSpeaker();
        sp.setExpertName(name);
        sp.setRole(role);
        sp.setSegmentTitle(blankToNull(topic == null ? null : topic.replaceAll("\\s+", " ").trim()));
        sp.setSegmentTime(blankToNull(time));
        return sp;
    }

    /**
     * 从单元格文本提取人名列表：换行切块，剥段首标签（讨论：/主持：），再按分隔符切 token 逐个判名。
     * token 判名（2026-11-10 曲径通幽/邯郸实证口径）：token 为纯 2-4 汉字（王军/刘铮/田春桃），
     * 或剥去头衔后缀后为 2-4 汉字（彭军路教授→彭军路）；长 token（单位如 平顶山市中医院）整体拒绝，
     * 避免 NAME_WITH_TITLE 任意滑窗把单位词截成人名。
     */
    List<String> extractNames(String cell) {
        List<String> names = new ArrayList<>();
        if (cell == null || cell.isBlank()) return names;
        for (String line : cell.split("\\R")) {
            // 全角/不换行空格是人名内部间隔（如「马　媛」），直接剔除；
            // PUA 私有区字符（\uE000-\uF8FF，嵌入字体的空白字形）与零宽字符一并剔除（邯郸卫星会「王\uF020军」实证）
            String cleaned = line.replace("\u3000", "").replace("\u00a0", "").replace("|", " ")
                    .replaceAll("[\\uE000-\\uF8FF\\u200b-\\u200f\\u2060\\ufeff]", "").trim();
            cleaned = cleaned.replaceFirst("^[\\u4e00-\\u9fa5]{1,6}[：:]\\s*", "");   // 「讨论：」「主持：」等段首标签
            if (cleaned.isEmpty()) continue;
            List<String> run = new ArrayList<>();
            List<String> tokens = new ArrayList<>();
            for (String token : cleaned.split("[\\s、，,；;。/]+")) {
                if (token.length() == 1 && token.matches("[\\u4e00-\\u9fa5·]")) {
                    run.add(token);
                } else {
                    flushSingleCharRun(run, tokens);
                    if (!token.isEmpty()) tokens.add(token);
                }
            }
            flushSingleCharRun(run, tokens);
            for (String token : tokens) {
                String candidate = stripTitles(token);
                if (CHINESE_NAME.matcher(candidate).matches() && isPlausibleName(candidate)
                        && !names.contains(candidate)) {
                    names.add(candidate);
                }
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

    /** 连续单字 token 两两合并为候选名（「王 军」→「王军」），奇数个时末位单字丢弃。 */
    private void flushSingleCharRun(java.util.List<String> run, java.util.List<String> out) {
        for (int i = 0; i + 1 < run.size(); i += 2) {
            out.add(run.get(i) + run.get(i + 1));
        }
        run.clear();
    }

    /** 归一化：去全部空白（含全角空格）、统一中间点。 */
    String normalizeName(String v) {
        return v == null ? "" : v.replaceAll("[\\u3000\\u00a0\\s]+", "").replace('・', '·');
    }

    /** 像人名：2-4 个汉字（允许 ·），不含数字/字母，且不含机构/议题/角色特征词。 */
    boolean isPlausibleName(String v) {
        if (v == null) return false;
        String s = stripTitles(v);
        if (!CHINESE_NAME.matcher(s).matches()) return false;
        if (s.matches(".*[0-9A-Za-z].*")) return false;
        for (String w : List.of("医院", "大学", "大会", "会议", "规范", "优化", "解读", "指南", "论道", "致辞", "总结", "腾讯", "时间",
                "讨论", "主持", "嘉宾", "环节", "病例", "扫码", "地点", "形式", "尊敬", "您好", "邀请", "分享",
                "手术", "体会", "治疗", "硬化", "射频", "下肢", "曲张", "药剂", "巡讲", "论坛", "专场", "指南更新", "医药")) {
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
                body.put("unit", fields.get("单位"));
                body.put("title", fields.get("职称"));
                body.put("profile", fields.get("简介"));
                body.put("contact", fields.get("联系方式"));
                Expert saved = createExpertForProject(project, body, null, user);
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
     * 接受姓名、单位、职称、简介、联系方式及可选头像，不接受身份证或银行卡字段
     * （敏感字段仍须在专家库模块按既有管控维护）。
     */
    private Expert createExpertForProject(Project project, Map<String, Object> body, MultipartFile photo, AuthUser user) {
        String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
        if (name.isEmpty()) {
            throw new BusinessException("姓名不能为空");
        }
        String unit = strOf(body.get("unit"));
        if (unit.isEmpty()) {
            throw new BusinessException("单位不能为空");
        }
        String contact = strOf(body.get("contact"));
        Expert expert = new Expert();
        expert.setName(name);
        expert.setUnit(unit);
        expert.setTitle(blankToNull(strOf(body.get("title"))));
        expert.setProfile(blankToNull(strOf(body.get("profile"))));
        expert.setContact("无".equals(contact) ? null : blankToNull(contact));
        Expert saved = expertService.create(expert, user);
        if (photo != null) {
            expertService.attachNewAvatar(saved, photo);
        }

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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Map<String, Object> archiveManual(Long projectId, Map<String, Object> body, MultipartFile photo, AuthUser user) {
        Project project = projectService.requireProject(projectId);
        projectService.requireOperable(project, user);
        String name = body == null || body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
        if (name.isEmpty()) {
            throw new BusinessException("姓名不能为空");
        }
        if (Boolean.TRUE.equals(body.get("requirePendingSpeaker"))
                && speakerMapper.selectList(new LambdaQueryWrapper<ProjectSpeaker>()
                        .select(ProjectSpeaker::getId)
                        .eq(ProjectSpeaker::getProjectId, projectId)
                        .eq(ProjectSpeaker::getExpertName, name)
                        .eq(ProjectSpeaker::getConfirmed, 1)
                        .isNull(ProjectSpeaker::getExpertId)
                        .last("FOR UPDATE")).isEmpty()) {
            throw new BusinessException("该参会专家已建档或不在待建档名单中，请刷新后重试");
        }
        Expert saved = createExpertForProject(project, body, photo, user);
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
    public record Extracted(String plainText, List<List<String>> tableRows, List<String> textBlocks,
                            List<PptBlock> pptBlocks) {
        public Extracted(String plainText, List<List<String>> tableRows, List<String> textBlocks) {
            this(plainText, tableRows, textBlocks, null);
        }
        public Extracted(String plainText, List<List<String>> tableRows) {
            this(plainText, tableRows, null);
        }
    }

    Extracted extract(String fileName, byte[] bytes) throws IOException {
        String ext = com.jshz.expert.common.FileNames.extension(fileName);
        return switch (ext) {
            case "ppt", "pptx" -> extractPpt(bytes);
            case "docx" -> extractDocx(bytes);
            case "doc" -> extractDoc(bytes);
            case "xlsx", "xls" -> extractExcel(bytes);
            case "pdf" -> extractPdf(bytes);
            case "jpg", "jpeg", "png" -> extractImage(bytes);
            default -> throw new BusinessException("不支持的日程格式");
        };
    }

    record PptBlock(int slide, double x, double y, String text, List<List<String>> rows,
                    List<Double> rowOffsets, boolean hostColumn) {}

    private record PptAppearance(int slide, double x, double y, ProjectSpeaker speaker) {}

    private static final Pattern AGENDA_RANGE =
            Pattern.compile("\\d{1,2}[:：]\\d{2}\\s*[-—–~～至]\\s*\\d{1,2}[:：]\\d{2}");
    private static final Pattern AGENDA_LINE = Pattern.compile("^(" + AGENDA_RANGE + ")\\s*(.*)$");
    private static final Pattern DISCUSSION_LINE = Pattern.compile("^讨论(?:嘉宾)?\\s*[:：]");
    private static final Pattern PPT_DISCUSSION_CELL = Pattern.compile("^(多学科对话)\\s*[:：]");
    private static final Pattern ADJACENT_PROFESSORS = Pattern.compile("(?<=教授)(?=[\\p{IsHan}])");

    private Extracted extractPpt(byte[] bytes) throws IOException {
        List<PptBlock> blocks = new ArrayList<>();
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            int slideIndex = 0;
            for (var slide : ppt.getSlides()) {
                boolean hostColumn = slide.getShapes().stream().anyMatch(shape ->
                        shape instanceof XSLFTextShape text && compactCell(text.getText()).equals("主持"));
                int before = blocks.size();
                long[] beforeCharsBox = {0};
                collectPptBlocks(slide.getShapes(), slideIndex, hostColumn, blocks);
                for (int i = before; i < blocks.size(); i++) {
                    PptBlock b = blocks.get(i);
                    if (b.text != null) beforeCharsBox[0] += b.text.length();
                    else b.rows.forEach(r -> r.forEach(c -> beforeCharsBox[0] += c.length()));
                }
                // P1（Owner 2026-11-12）：图片型幻灯片（文本过薄且有内嵌图片）→ PP-OCRv5/Tesseract 竞争识别
                if (beforeCharsBox[0] < 40) {
                    List<BufferedImage> pictures = new ArrayList<>();
                    collectSlidePictures(slide.getShapes(), pictures);
                    StringBuilder ocrText = new StringBuilder();
                    for (BufferedImage pic : pictures) {
                        if (pic == null || pic.getWidth() < 80 || pic.getHeight() < 80) continue;
                        String pageText = betterOcrPage(pic);
                        if (pageText != null && !pageText.isBlank()) ocrText.append(pageText.strip()).append('\n');
                    }
                    if (!ocrText.isEmpty()) {
                        blocks.add(new PptBlock(slideIndex, 0, 0, ocrText.toString(), List.of(), List.of(), hostColumn));
                    }
                }
                slideIndex++;
            }
        }
        List<String> textBlocks = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (PptBlock block : blocks) {
            if (block.text != null) {
                textBlocks.add(block.text);
                text.append(block.text).append('\n');
            } else {
                rows.addAll(block.rows);
                for (List<String> row : block.rows) text.append(String.join("\n", row)).append('\n');
            }
        }
        return new Extracted(text.toString(), rows, textBlocks, blocks);
    }

    private void collectPptBlocks(List<XSLFShape> shapes, int slide, boolean hostColumn, List<PptBlock> blocks) {
        for (XSLFShape shape : shapes) {
            var anchor = shape.getAnchor();
            if (shape instanceof org.apache.poi.xslf.usermodel.XSLFGroupShape group) {
                collectPptBlocks(group.getShapes(), slide, hostColumn, blocks);
            } else if (shape instanceof XSLFTable table) {
                List<List<String>> rows = new ArrayList<>();
                List<Double> offsets = new ArrayList<>();
                double offset = 0;
                for (var row : table) {
                    offsets.add(offset);
                    rows.add(row.getCells().stream().map(cell -> cell.getText().strip()).toList());
                    offset += row.getHeight();
                }
                blocks.add(new PptBlock(slide, anchor.getX(), anchor.getY(), null, rows, offsets, hostColumn));
            } else if (shape instanceof XSLFTextShape text && !text.getText().isBlank()) {
                blocks.add(new PptBlock(slide, anchor.getX(), anchor.getY(), text.getText().strip(),
                        List.of(), List.of(), hostColumn));
            }
        }
    }

    /** 递归收集幻灯片内嵌图片（含组合形状），供图片型幻灯片 OCR（P1）。 */
    private void collectSlidePictures(List<XSLFShape> shapes, List<BufferedImage> out) {
        for (XSLFShape shape : shapes) {
            if (shape instanceof org.apache.poi.xslf.usermodel.XSLFGroupShape group) {
                collectSlidePictures(group.getShapes(), out);
            } else if (shape instanceof org.apache.poi.xslf.usermodel.XSLFPictureShape pic) {
                try {
                    var data = pic.getPictureData();
                    if (data != null && data.getData() != null && data.getData().length > 1000) {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data.getData()));
                        if (img != null) out.add(img);
                    }
                } catch (Exception ignored) {
                    // 单张图片解码失败不影响其余
                }
            }
        }
    }

    private List<ProjectSpeaker> parsePptBlocks(List<PptBlock> blocks) {
        List<PptAppearance> appearances = new ArrayList<>();
        for (PptBlock block : blocks) {
            if (block.text != null) {
                List<ProjectSpeaker> candidates = new ArrayList<>();
                parseAgendaLines(block.text, block.hostColumn, candidates);
                for (ProjectSpeaker candidate : candidates) {
                    appearances.add(new PptAppearance(block.slide, block.x, block.y, candidate));
                }
                continue;
            }
            boolean hasHeader = block.rows.stream().anyMatch(this::isHeaderRow);
            if (!hasHeader && block.rows.stream().anyMatch(this::hasTimedPptRoleCells)) {
                for (int index = 0; index < block.rows.size(); index++) {
                    for (ProjectSpeaker candidate : parseTimedPptRow(block.rows.get(index))) {
                        appearances.add(new PptAppearance(block.slide, block.x,
                                block.y + block.rowOffsets.get(index), candidate));
                    }
                }
                continue;
            }
            var table = new Extracted("", block.rows, List.of());
            List<ProjectSpeaker> standard = ruleParse(table, (index, candidate) ->
                    appearances.add(new PptAppearance(block.slide, block.x,
                            block.y + block.rowOffsets.get(index), candidate)));
            if (hasHeader) continue;
            boolean expandedTable = false;
            for (int index = 0; index < block.rows.size(); index++) {
                String expanded = expandTimeColumn(block.rows.get(index));
                if (expanded == null) continue;
                expandedTable = true;
                List<ProjectSpeaker> candidates = new ArrayList<>();
                parseAgendaLines(expanded, block.hostColumn, candidates);
                for (ProjectSpeaker candidate : candidates) {
                    appearances.add(new PptAppearance(block.slide, block.x,
                            block.y + block.rowOffsets.get(index), candidate));
                }
            }
            if (!expandedTable) {
                for (ProjectSpeaker candidate : standard) {
                    appearances.add(new PptAppearance(block.slide, block.x, block.y, candidate));
                }
            }
        }
        appearances.sort(java.util.Comparator.comparingInt(PptAppearance::slide).thenComparingDouble(PptAppearance::y));
        List<ProjectSpeaker> result = new ArrayList<>();
        for (int start = 0; start < appearances.size();) {
            PptAppearance first = appearances.get(start);
            int end = start + 1;
            while (end < appearances.size() && appearances.get(end).slide == first.slide
                    && appearances.get(end).y - first.y <= 3) end++;
            // 近似同一视觉行时按左到右，表格行内维持姓名顺序。
            List<PptAppearance> line = appearances.subList(start, end);
            line.sort(java.util.Comparator.comparingDouble(PptAppearance::x));
            for (PptAppearance appearance : line) {
                ProjectSpeaker candidate = appearance.speaker;
                if (result.stream().noneMatch(existing -> sameAppearance(existing, candidate))) result.add(candidate);
            }
            start = end;
        }
        applyCeremonyChairRoles(result);
        Set<String> scheduledChairs = new LinkedHashSet<>();
        for (ProjectSpeaker candidate : result) {
            if (ProjectSpeaker.ROLE_CHAIR.equals(candidate.getRole()) && candidate.getSegmentTime() != null) {
                scheduledChairs.add(candidate.getExpertName());
            }
        }
        result.removeIf(candidate -> ProjectSpeaker.ROLE_CHAIR.equals(candidate.getRole())
                && "大会主席".equals(candidate.getSegmentTitle()) && candidate.getSegmentTime() == null
                && scheduledChairs.contains(candidate.getExpertName()));
        return result;
    }

    /** 无内嵌表头时，以时间范围和人员列中的显式角色共同确认列布局。 */
    private boolean hasTimedPptRoleCells(List<String> row) {
        return row.size() >= 3 && AGENDA_RANGE.matcher(row.get(0)).matches()
                && row.subList(2, row.size()).stream().anyMatch(cell -> ROLE_LABEL.matcher(cell).lookingAt());
    }

    private List<ProjectSpeaker> parseTimedPptRow(List<String> row) {
        List<ProjectSpeaker> result = new ArrayList<>();
        if (row.size() < 3 || !AGENDA_RANGE.matcher(row.get(0)).matches()) return result;
        String time = row.get(0).replace('：', ':');
        String topic = row.get(1).replaceAll("\\s*\\R\\s*", " ");
        Matcher discussion = PPT_DISCUSSION_CELL.matcher(topic);
        if (discussion.find()) {
            addAgendaPeople(result, splitAdjacentProfessors(topic.substring(discussion.end())),
                    ProjectSpeaker.ROLE_DISCUSSANT, discussion.group(1), time);
            topic = discussion.group(1);
        }
        for (String cell : row.subList(2, row.size())) {
            if (cell.isBlank() || cell.equals("全体")) continue;
            String people = splitAdjacentProfessors(cell);
            Matcher label = ROLE_LABEL.matcher(people);
            if (label.lookingAt()) {
                addLabeledPeople(result, people, label, topic, time);
            } else {
                List<String> names = extractNames(people);
                String role = roleFor(topic, cell, names.size());
                for (String name : names) result.add(speaker(name, role, topic, time));
            }
        }
        return result;
    }

    private String splitAdjacentProfessors(String people) {
        return ADJACENT_PROFESSORS.matcher(people).replaceAll(" ");
    }

    /** 开场和总结优先使用本场主席名单，明确主持保留原角色。 */
    private void applyCeremonyChairRoles(List<ProjectSpeaker> candidates) {
        Set<String> chairs = new LinkedHashSet<>();
        for (ProjectSpeaker candidate : candidates) {
            if (ProjectSpeaker.ROLE_CHAIR.equals(candidate.getRole())) {
                chairs.add(normalizeName(candidate.getExpertName()));
            }
        }
        for (ProjectSpeaker candidate : candidates) {
            String topic = candidate.getSegmentTitle();
            if (topic == null || ProjectSpeaker.ROLE_HOST.equals(candidate.getRole())) continue;
            boolean opening = isOpeningAddress(topic);
            boolean namedChair = chairs.contains(normalizeName(candidate.getExpertName()));
            if ((opening && (namedChair || chairs.isEmpty())) || (topic.contains("总结") && namedChair)) {
                candidate.setRole(ProjectSpeaker.ROLE_CHAIR);
            }
        }
    }

    private boolean isOpeningAddress(String topic) {
        return compactCell(topic).matches("^(?:大会|会议)?开场致辞(?:及.+)?$");
    }

    /** 兼容一行表格的时间列和内容列各自用多段文字表示多个日程行。 */
    private String expandTimeColumn(List<String> row) {
        if (row.size() != 2) return null;
        List<String> times = row.get(0).lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        List<String> content = row.get(1).lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (times.size() != 2 || !times.stream().allMatch(time -> AGENDA_RANGE.matcher(time).matches())
                || content.size() < 2 || !DISCUSSION_LINE.matcher(content.get(content.size() - 1)).find()) return null;
        int discussion = content.size() - 1;
        return times.get(0) + " " + String.join("\n", content.subList(0, discussion))
                + "\n" + times.get(1) + " " + content.get(discussion);
    }

    private void parseAgendaLines(String text, boolean hostColumn, List<ProjectSpeaker> result) {
        String time = "";
        String topic = "";
        for (String line : text.lines().map(String::strip).toList()) {
            if (line.isBlank()) continue;
            Matcher timed = AGENDA_LINE.matcher(line);
            if (timed.matches()) {
                time = timed.group(1).replace('：', ':');
                String body = timed.group(2).strip();
                if (DISCUSSION_LINE.matcher(body).find()) {
                    topic = "讨论";
                    addAgendaPeople(result, DISCUSSION_LINE.matcher(body).replaceFirst(""), ProjectSpeaker.ROLE_DISCUSSANT, topic, time);
                    time = "";
                    continue;
                }
                String[] columns = agendaColumns(body);
                topic = columns.length > 1 ? columns[0] : "";
                if (columns.length > 1) {
                    String role = roleFor(topic, "", 1);
                    addAgendaPeople(result, columns[1], role, topic, time);
                    for (int i = 2; i < columns.length; i++) {
                        addAgendaPeople(result, columns[i], hostColumn ? ProjectSpeaker.ROLE_HOST : role, topic, time);
                    }
                } else {
                    result.addAll(labeledAppearances(List.of(line)));
                    time = "";
                }
            } else if (line.matches("^(大会|会议)主席\\s*[:：].*")) {
                addAgendaPeople(result, line, ProjectSpeaker.ROLE_CHAIR, "大会主席", "");
                time = "";
            } else if (hostColumn && !time.isEmpty() && isNameList(line)) {
                addAgendaPeople(result, line, ProjectSpeaker.ROLE_HOST, topic, time);
            } else {
                result.addAll(labeledAppearances(List.of(line)));
                time = "";
            }
        }
    }

    private String[] agendaColumns(String text) {
        return text.split("(?:\\t+| {5,}|　{2,})");
    }

    private boolean isNameList(String text) {
        List<String> names = extractNames(text);
        return !names.isEmpty() && normalizeName(text).replaceAll("[、，,；;]", "")
                .equals(String.join("", names));
    }

    private void addAgendaPeople(List<ProjectSpeaker> result, String payload, String role, String topic, String time) {
        for (String name : extractNames(payload)) result.add(speaker(name, role, topic, time));
    }

    private Extracted extractDocx(byte[] bytes) throws IOException {
        StringBuilder text = new StringBuilder();
        List<List<String>> rows = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            text.append(extractor.getText());
            for (XWPFTable table : doc.getTables()) {
                collectDocxTable(table, text, rows);
            }
        }
        return new Extracted(text.toString(), rows);
    }

    /** 递归收集 docx 表格：顶层与嵌套表格（cell.getTables()）行均平铺进 tableRows。 */
    private void collectDocxTable(XWPFTable table, StringBuilder text, List<List<String>> rows) {
        for (var row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            boolean hasValue = false;
            for (var cell : row.getTableCells()) {
                String v = cell.getText();
                cells.add(v == null ? "" : v.trim());
                if (v != null && !v.isBlank()) {
                    hasValue = true;
                    text.append(v.trim()).append('\n');
                }
                for (XWPFTable nested : cell.getTables()) {
                    collectDocxTable(nested, text, rows);
                }
            }
            if (hasValue) rows.add(cells);
        }
    }

    /**
     * .doc 旧格式（Owner 2026-11-10 Word 提准）：优先提取 HWPF 表格（此前只取纯文本导致表格型 .doc 全漏），
     * 表格 API 失败或无表格时回退纯文本（段落式日程由 headerlessParse 时间锚定兜底）。
     */
    private Extracted extractDoc(byte[] bytes) throws IOException {
        org.apache.poi.hwpf.HWPFDocument doc =
                new org.apache.poi.hwpf.HWPFDocument(new ByteArrayInputStream(bytes));
        try {
            StringBuilder text = new StringBuilder();
            List<List<String>> rows = new ArrayList<>();
            org.apache.poi.hwpf.usermodel.Range range = doc.getRange();
            try {
                text.append(new WordExtractor(doc).getText());
            } catch (Exception ignored) {
            }
            try {
                org.apache.poi.hwpf.usermodel.TableIterator it =
                        new org.apache.poi.hwpf.usermodel.TableIterator(range);
                while (it.hasNext()) {
                    org.apache.poi.hwpf.usermodel.Table table = it.next();
                    for (int r = 0; r < table.numRows(); r++) {
                        var row = table.getRow(r);
                        List<String> cells = new ArrayList<>();
                        boolean hasValue = false;
                        for (int c = 0; c < row.numCells(); c++) {
                            String v = row.getCell(c).text().trim();
                            v = v.replace('\u0007', ' ').replaceAll("\\s+", " ");
                            cells.add(v);
                            if (!v.isBlank()) {
                                hasValue = true;
                                text.append(v).append('\n');
                            }
                        }
                        if (hasValue) rows.add(cells);
                    }
                }
            } catch (Exception e) {
                log.info(".doc 表格提取不可用，回退纯文本：{}", e.getMessage());
            }
            return new Extracted(text.toString(), rows);
        } finally {
            try { doc.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * xlsx/xls 日程（Owner 2026-11-10 新增）：WorkbookFactory 统一吃新旧格式；遍历全部 sheet；
     * 合并单元格展开（仅左上格有值→填充区域内每格），空行跳过；cell 值经 DataFormatter 格式化
     * （数字/日期保持显示值），行同时拼入 plainText 供 headerlessParse/Ollama 兜底。
     */
    private Extracted extractExcel(byte[] bytes) throws IOException {
        StringBuilder text = new StringBuilder();
        List<List<String>> rows = new ArrayList<>();
        try (var wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
            for (var sheet : wb) {
                // 合并区域展开策略：纵向合并（跨行）才把左上格值填充到区域内各格（时间列合并续行）；
                // 横向合并（单行横幅如「上半场：… 大会主席：胡毅 秦叔逵」）不复制——复制会把横幅文本灌进
                // 讲者/主持列产生假人名（2026-11-10 新长征 xlsx 实证「再谱华章」），横幅人名由 plainText 页脚兜底接管
                Map<Integer, String> filled = new LinkedHashMap<>();
                for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
                    var region = sheet.getMergedRegion(i);
                    if (region.getLastRow() == region.getFirstRow()) continue;
                    var src = sheet.getRow(region.getFirstRow());
                    if (src == null) continue;
                    var srcCell = src.getCell(region.getFirstColumn());
                    String v = srcCell == null ? "" : formatter.formatCellValue(srcCell).trim();
                    for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                        for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                            filled.put(r * 1000 + c, v);
                        }
                    }
                }
                for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
                    var row = sheet.getRow(r);
                    if (row == null) continue;
                    List<String> cells = new ArrayList<>();
                    boolean hasValue = false;
                    for (int c = 0; c < Math.max(row.getLastCellNum(), 1); c++) {
                        String v = filled.getOrDefault(r * 1000 + c, "");
                        if (v.isEmpty()) {
                            var cell = row.getCell(c);
                            if (cell != null && cell.getCellType() != org.apache.poi.ss.usermodel.CellType.BLANK) {
                                v = formatter.formatCellValue(cell).trim();
                            }
                        }
                        cells.add(v);
                        if (!v.isBlank()) {
                            hasValue = true;
                            text.append(v).append('\t');
                        }
                    }
                    if (hasValue) {
                        text.append('\n');
                        rows.add(cells);
                    }
                }
            }
        }
        return new Extracted(text.toString(), rows);
    }

    private Extracted extractPdf(byte[] bytes) throws IOException {
        StringBuilder text = new StringBuilder();
        try (PDDocument doc = PDDocument.load(bytes)) {
            // 扫描页回退 OCR：逐页在 文本层 与 PP-OCRv5/Tesseract 竞争结果 间按 CJK 量择优（P1，Owner 2026-11-12）
            var renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            int pages = Math.min(6, doc.getNumberOfPages());
            for (int i = 1; i <= pages; i++) {
                var stripper = new PDFTextStripper();
                stripper.setStartPage(i); stripper.setEndPage(i);
                String page = stripper.getText(doc);
                if (page.trim().length() < 40) {
                    BufferedImage raster = renderer.renderImageWithDPI(i - 1, 250);
                    page = betterOcrPage(raster);
                }
                text.append(page).append('\n');
            }
        }
        return new Extracted(text.toString(), List.of());
    }

    private Extracted extractImage(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new BusinessException("无法读取图片内容");
        }
        return new Extracted(betterOcrPage(image), List.of());
    }

    // ==================== PP-OCRv5 竞争（P1：图片/扫描件，Owner 2026-11-12） ====================

    /**
     * PP-OCRv5 与 Tesseract 整页识别竞争择优：LocalTableOcr TSV 逐行置信度 ≥60 过滤，
     * 与 Tesseract 结果按 CJK 字符量取优（复用合规解析已验证的口径）；运行时未安装返回 Tesseract 文本。
     */
    private String betterOcrPage(BufferedImage raster) {
        String tesseract = KnowledgeDocTextExtractor.ocrPage(raster, false);
        try {
            String tsv = LocalTableOcr.recognize(raster);
            if (tsv == null || tsv.isBlank()) return tesseract;
            StringBuilder sb = new StringBuilder();
            for (String line : tsv.split("\\R")) {
                String[] f = line.split("\t");
                if (f.length < 2) continue;
                String cell = f[f.length - 1].trim();
                double score;
                try { score = Double.parseDouble(f[f.length - 2]); } catch (NumberFormatException e) { continue; }
                if (cell.isEmpty() || score < 60) continue;
                sb.append(cell).append('\n');
            }
            String ppocr = sb.toString();
            return cjkCount(ppocr) > cjkCount(tesseract) ? ppocr : tesseract;
        } catch (Exception e) {
            log.info("PP-OCRv5 整页识别不可用，回退 Tesseract：{}", e.getMessage());
            return tesseract;
        }
    }

    /** 样本字符在归一化原文中的顺序覆盖率（百分比）；空白差异不影响（P4 LLM 兜底回指校验）。 */
    private int coverageRatio(String source, String sample) {
        String normSample = sample == null ? "" : sample.replaceAll("[\\s\\u3000\\u00a0｜|/／、：:（）()（）-]", "");
        if (normSample.isEmpty()) return 100;
        String normSource = normalizeName(source);
        int hit = 0;
        String rest = normSource;
        for (int ci = 0; ci < normSample.length(); ci++) {
            int idx = rest.indexOf(normSample.charAt(ci));
            if (idx >= 0) { hit++; rest = rest.substring(idx + 1); }
        }
        return hit * 100 / normSample.length();
    }

    private static long cjkCount(String s) {
        return s == null ? 0 : s.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
    }

    // ==================== 小工具 ====================

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
