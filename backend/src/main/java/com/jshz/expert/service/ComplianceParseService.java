package com.jshz.expert.service;

import com.jshz.expert.entity.ComplianceItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 企业合规文档解析服务（REQ/DES-20261102 v1.0 保留）。
 * xlsx/xls：Apache POI 按表头映射解析（时间节点/项目/内容/备注）+ 阶段归类关键词；
 * PDF/DOCX：文本抽取后按行规则解析（允许部分失败）；其他格式：不解析（返回空，提示手动录入）。
 * 解析失败静默跳过（返回空列表 + 提示信息），允许维护人手动录入兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceParseService {

    /** 阶段归类关键词（设计 §2.6） */
    private static final Map<String, List<String>> STAGE_KEYWORDS = new LinkedHashMap<>();
    static {
        STAGE_KEYWORDS.put("INITIATION", Arrays.asList("立项", "审批", "招商"));
        STAGE_KEYWORDS.put("FINANCE", Arrays.asList("财务", "合同", "付款", "发票", "签署", "结算"));
        STAGE_KEYWORDS.put("EXECUTION", Arrays.asList("执行", "日程", "劳务", "讲题", "人员", "布置"));
        STAGE_KEYWORDS.put("VERIFICATION", Arrays.asList("核销", "照片", "签到", "盖章", "归档", "会后"));
        STAGE_KEYWORDS.put("SPECIAL", Arrays.asList("特殊", "区域", "账户", "固定劳务"));
    }

    /** 解析结果：事项列表 + 提示信息 */
    public record ParseResult(List<ComplianceItem> items, String message) {
    }

    private final OllamaService ollamaService;
    private final KnowledgeDocTextExtractor textExtractor;
    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** 按文件类型分发解析（fileType 形如 xlsx/docx/pdf）；byte[] 入口保留兼容（无 OCR 路径文件）。 */
    public ParseResult parse(String fileName, String fileType, byte[] bytes, String textContent) {
        return parse(fileName, fileType, textContent, () -> parseExcel(new ByteArrayInputStream(bytes)), null);
    }

    /**
     * P2-7：从磁盘路径解析，xlsx/xls 直接从文件流读取，避免请求线程/调用方持有整文件 byte[]。
     * 非表格且文本过薄/缺失时走 extractStructured 的 OCR 回退 + LLM 提取（Owner 2026-11-10）。
     */
    public ParseResult parse(String fileName, String fileType, Path path, String textContent) {
        return parse(fileName, fileType, textContent, () -> {
            try (InputStream in = Files.newInputStream(path)) {
                return parseExcel(in);
            }
        }, path);
    }

    private ParseResult parse(String fileName, String fileType, String textContent, Callable<ParseResult> excelParser,
                              Path path) {
        try {
            String ft = fileType == null ? "" : fileType.toLowerCase();
            if (ft.equals("xlsx") || ft.equals("xls")) {
                return excelParser.call();
            }
            var structured = path == null || textExtractor == null
                    ? null : textExtractor.extractStructured(path, fileName);
            var candidates = new LinkedHashMap<String, ComplianceItem>();
            if (structured != null) {
                for (var block : structured.blocks()) {
                    if ("TABLE_ROW".equals(block.kind()) || "PARAGRAPH".equals(block.kind())) {
                        addCandidate(candidates, block.text());
                    } else {
                        addParagraphs(candidates, block.text(), "PDF_PAGE".equals(block.kind()));
                    }
                }
            }
            if (candidates.isEmpty() || structured.incomplete()) {
                if (textContent != null) {
                    addParagraphs(candidates, textContent, false);
                }
            }
            if (!candidates.isEmpty()) {
                List<ComplianceItem> ruleItems = new ArrayList<>(candidates.values());
                String warning = structured != null && structured.incomplete() ? "；部分页面需人工核对" : "";
                // LLM 结构化拆分（Owner 2026-11-11）：仅把「类别/项目/标准」表格碎片送 LLM 拆分
                // （标题=项目+类别，如「飞机交通费」；内容=标准原文），其余候选原样直通——保证零丢失。
                List<ComplianceItem> result = new ArrayList<>();
                List<ComplianceItem> fragments = new ArrayList<>();
                String sourceText = "";
                for (ComplianceItem it : ruleItems) {
                    String content = it.getContent() == null ? "" : it.getContent();
                    long lines = content.lines().count();
                    boolean tableFragment = (content.contains("类别") || content.contains("标准") || content.contains("序号"))
                            && (lines >= 3 || content.contains("项目"));
                    if (tableFragment) {
                        fragments.add(it);
                        sourceText += nullToEmpty(it.getTitle()) + "\n" + content + "\n";
                    } else {
                        result.add(it);
                    }
                }
                if (!fragments.isEmpty()) {
                    List<ComplianceItem> split = llmRestructure(fragments, sourceText);
                    if (split != null && split.size() >= fragments.size()) {
                        result.addAll(split);
                        return new ParseResult(result,
                                "已解析 " + result.size() + " 条事项（OCR+LLM 结构化），请核对后发布" + warning);
                    }
                    result.addAll(fragments);
                } else {
                    result.addAll(fragments);
                }
                return new ParseResult(result,
                        "已解析 " + result.size() + " 条原文候选，请核对后发布" + warning);
            }
            List<ComplianceItem> llm = llmExtract(textContent);
            if (llm != null && !llm.isEmpty()) return new ParseResult(llm, "已提取候选，请核对后发布");
            return new ParseResult(List.of(), "未能提取有效事项，请核对原文或手动录入");
        } catch (Exception e) {
            log.warn("合规文档解析失败（fileName={}）：{}", fileName, e.getMessage());
            return new ParseResult(new ArrayList<>(), "自动解析失败（" + e.getClass().getSimpleName() + "），请手动录入事项");
        }
    }

    /**
     * LLM 事项提取（Owner 2026-11-10）：OCR/文本层内容 → 本地大模型抽取「事项标题+要点内容」结构化草稿。
     * 失败/不可用/产出空返回 null（调用方回退规则切分）；不编造、按文档顺序、上限 100 条。
     */
    List<ComplianceItem> llmExtract(String text) {
        if (ollamaService == null || !ollamaService.available() || text == null || text.isBlank()) return null;
        String prompt = """
                你是合规事项提取助手。从以下合规文档文本中提取"合规事项"，严格输出 JSON 对象（不要输出任何其他文字）：
                {"items":[{"title":"","content":""}]}
                规则：title=事项标题，不超过 30 字，动宾结构（如"缴纳保证金""签订安全协议""现场照片留存"）；
                标题要体现适用对象与主要义务，同一段落的多项并列要求不能只取第一句。
                content=原文支持的执行要求，保留适用条件和数字；模糊的金额或比较符号注明需核对原文。
                硬性约束：输出内容必须逐字取自文档原文；金额、比较符号（≤/<）与单位（元/次 等）保持原文写法；
                禁止出现文档文本中没有的数字串、英文单词或乱码符号；宁缺毋滥，不确定的条目直接跳过。
                跳过封面、章节名、表头、页码、目录及联系方式；最多 100 条。文档文本：
                """ + text.substring(0, Math.min(text.length(), 12000));
        try {
            String resp = ollamaService.completeJson(prompt);
            if (resp == null) return null;
            com.fasterxml.jackson.databind.JsonNode arr = jsonMapper.readTree(resp).path("items");
            if (!arr.isArray() || arr.isEmpty()) return null;
            // 幻觉过滤（Owner 2026-11-11）：OCR 原文干净而 LLM 输出偶发乱码（如 hex:、(220444 Ht K)），
            // 标题在归一化原文中找不到的整条丢弃；证据见 temp/garbage.log 取证。
            String normSource = text.replaceAll("[\\s\\u3000\\u00a0]", "");
            List<ComplianceItem> items = new ArrayList<>();
            int dropped = 0;
            for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                String title = n.path("title").asText("").trim();
                if (title.isEmpty()) continue;
                String content = n.path("content").asText("").trim();
                // 标题字符在归一化原文中的顺序覆盖率过低 → 判为幻觉（LLM 编造，非原文内容）
                if (coverageRatio(normSource, title) < 80) {
                    dropped++;
                    log.info("合规 LLM 事项疑似幻觉丢弃 title={}", title.length() > 40 ? title.substring(0, 40) : title);
                    continue;
                }
                ComplianceItem it = new ComplianceItem();
                it.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
                it.setContent(content.isEmpty() ? null : content);
                it.setStage(guessStage(title + " " + content));
                it.setPriority("MEDIUM");
                it.setIsDraft(1);
                items.add(it);
                if (items.size() >= 100) break;
            }
            if (dropped > 0) {
                log.info("合规 LLM 事项提取完成 items={} 疑似幻觉丢弃={}", items.size(), dropped);
            } else {
                log.info("合规 LLM 事项提取完成 items={}", items.size());
            }
            return items;
        } catch (Exception e) {
            log.warn("合规 LLM 事项提取失败（回退规则切分）：{}", e.getMessage());
            return null;
        }
    }

    /**
     * LLM 结构化拆分（Owner 2026-11-11）：「类别/项目/标准」表格碎片 → 独立事项
     * （标题=项目+类别，如「飞机交通费」；内容=标准列原文逐字保留）。
     * 幻觉过滤：标题+内容对候选合集覆盖率 <80% 的整条丢弃；返回 null 或条目少于输入时调用方保留原候选。
     */
    List<ComplianceItem> llmRestructure(List<ComplianceItem> input, String sourceText) {
        if (ollamaService == null || !ollamaService.available() || input == null || input.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.size(); i++) {
            var it = input.get(i);
            sb.append('[').append(i + 1).append("] 标题：").append(nullToEmpty(it.getTitle()))
                    .append("\n内容：").append(nullToEmpty(it.getContent())).append('\n');
        }
        String prompt = """
                你是合规文档整理助手。下面是 OCR 从"类别/项目/标准"表格提取的文本碎片，请整理为独立的合规事项，
                严格输出 JSON 对象（不要输出任何其他文字）：
                {"items":[{"title":"","content":""}]}
                规则：一条事项=表格中的一个「项目」（如 飞机/高铁/市内交通/早餐/午餐、晚餐/茶歇/住宿费）；
                title=项目名+类别，不超过 20 字（如「飞机交通费」「午餐餐费」「茶歇费用」「住宿费」）；
                content=该项目对应的标准列原文，逐字保留金额、比较符号（≤/<）、单位（元/次、元/人/天 等）与括号说明，
                同一项目跨行的说明合并为一段；[n] 编号、页眉与表头行（序号/类别/项目/标准）不要作为事项；
                非表格碎片的完整事项保持原样输出；禁止编造文档中没有的内容。候选文本：
                """ + sb;
        try {
            String resp = ollamaService.completeJson(prompt);
            if (resp == null) return null;
            com.fasterxml.jackson.databind.JsonNode arr = jsonMapper.readTree(resp).path("items");
            if (!arr.isArray() || arr.isEmpty()) return null;
            String normSource = sourceText.replaceAll("[\\s\\u3000\\u00a0]", "");
            List<ComplianceItem> items = new ArrayList<>();
            int dropped = 0;
            for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                String title = n.path("title").asText("").trim();
                String content = n.path("content").asText("").trim();
                if (title.isEmpty()) continue;
                // 幻觉判定基于「内容列原文」（标题是 项目+类别 的合法组合词，逐字覆盖必然偏低——如「飞机交通费」）：
                // 内容覆盖率达标，或（内容为空时）标题覆盖率达标，才认为有据
                if (coverageRatio(normSource, content) < 80 && coverageRatio(normSource, title) < 80) {
                    dropped++;
                    log.info("合规 LLM 结构化疑似幻觉丢弃 title={}", title.length() > 40 ? title.substring(0, 40) : title);
                    continue;
                }
                ComplianceItem it = new ComplianceItem();
                it.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
                it.setContent(content.isEmpty() ? null : content);
                it.setStage(guessStage(title + " " + content));
                it.setPriority("MEDIUM");
                it.setIsDraft(1);
                items.add(it);
                if (items.size() >= 100) break;
            }
            log.info("合规 LLM 结构化拆分完成 in={} out={} 幻觉丢弃={}", input.size(), items.size(), dropped);
            return items;
        } catch (Exception e) {
            log.warn("合规 LLM 结构化拆分失败（保留原候选）：{}", e.getMessage());
            return null;
        }
    }

    /** 样本字符在归一化原文中的顺序覆盖率（百分比）；空白/标点差异不影响。 */
    private int coverageRatio(String normSource, String sample) {
        String norm = sample.replaceAll("[\\s\\u3000\\u00a0｜|/／、：:（）()（）-]", "");
        if (norm.isEmpty()) return 100;
        int hit = 0;
        String rest = normSource;
        for (int ci = 0; ci < norm.length(); ci++) {
            int idx = rest.indexOf(norm.charAt(ci));
            if (idx >= 0) { hit++; rest = rest.substring(idx + 1); }
        }
        return hit * 100 / norm.length();
    }

    /** xlsx/xls 表头映射解析：时间节点→timeNote、项目→title、内容→content、备注→remark */
    private ParseResult parseExcel(InputStream in) {
        List<ComplianceItem> items = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return new ParseResult(items, "表格无表头行，请手动录入事项");
            }
            Map<String, Integer> col = new LinkedHashMap<>();
            for (Cell c : header) {
                String h = str(c);
                if (h == null || h.isBlank()) continue;
                if (h.contains("时间")) col.put("time", c.getColumnIndex());
                else if (h.contains("项目") || h.contains("事项")) col.put("title", c.getColumnIndex());
                else if (h.contains("内容") || h.contains("要求")) col.put("content", c.getColumnIndex());
                else if (h.contains("备注")) col.put("remark", c.getColumnIndex());
            }
            if (!col.containsKey("title")) {
                return new ParseResult(items, "未识别到「项目/事项」表头列，请手动录入事项");
            }
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String title = col.containsKey("title") ? str(row.getCell(col.get("title"))) : null;
                if (title == null || title.isBlank()) continue;
                ComplianceItem it = new ComplianceItem();
                it.setTitle(title.trim());
                if (col.containsKey("time")) it.setTimeNote(str(row.getCell(col.get("time"))));
                if (col.containsKey("content")) it.setContent(str(row.getCell(col.get("content"))));
                if (col.containsKey("remark")) it.setRemark(str(row.getCell(col.get("remark"))));
                it.setStage(guessStage(title + " " + nullToEmpty(it.getContent()) + " " + nullToEmpty(it.getTimeNote())));
                it.setPriority("MEDIUM");
                it.setIsDraft(1);
                items.add(it);
            }
            String msg = items.isEmpty() ? "表格未解析出事项，请手动录入" : "已解析 " + items.size() + " 条事项，请确认后发布";
            return new ParseResult(items, msg);
        } catch (Exception e) {
            log.warn("xlsx 解析异常：{}", e.getMessage());
            return new ParseResult(items, "xlsx 解析异常，请手动录入事项");
        }
    }

    /** PDF/DOCX 文本行规则解析：非空行即一条事项（允许部分失败，规则随样本持续维护） */
    private void addParagraphs(Map<String, ComplianceItem> candidates, String text, boolean pdfPage) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String paragraph : normalized.split("\\n(?:[ \\t]*\\n)+")) {
            if (pdfPage && isDocumentHeading(paragraph.strip())) continue;
            // OCR 噪声行过滤（Owner 2026-11-11）：整页 OCR 输出里不含中文的行（如「(220444 Ht K)|」「xyes aN」）
            // 与好行同段合并会产生乱码候选——先逐行剔除再合并
            StringBuilder kept = new StringBuilder();
            for (String line : paragraph.split("\n")) {
                String t = line.strip();
                if (t.isEmpty()) { kept.append('\n'); continue; }
                long cjk = t.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
                if (cjk == 0) continue;
                kept.append(line).append('\n');
            }
            String filtered = kept.toString().strip();
            if (filtered.isEmpty()) continue;
            addCandidate(candidates, filtered);
        }
    }

    private boolean isDocumentHeading(String paragraph) {
        return paragraph.matches("[^\\n，。；]{2,60}(?:基金会|标准试行)\\n[ \\t]*(?:[一二三四五六七八九十]+、)?(?:会议项目|劳务)[：:]?");
    }

    private void addCandidate(Map<String, ComplianceItem> candidates, String block) {
        String text = block.trim();
        if (text.length() < 4 || text.matches("^[\\d\\s.。、-]+$")
                || text.startsWith("目录") || text.matches("^第\\s*\\d+\\s*页$")) return;
        // OCR 噪声门槛（Owner 2026-11-11）：合规事项必含中文，纯 ASCII/数字噪声行
        // （如「(220444 Ht K)|」「hex:」「7/2」「T/x」）直接丢弃，不再进入候选
        long cjk = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
        if (cjk < 2) return;
        String key = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).replaceAll("\\s+", "");
        if (candidates.containsKey(key)) return;
        var item = new ComplianceItem();
        String title = candidateTitle(text);
        item.setTitle(title.substring(0, Math.min(60, title.length())));
        item.setContent(text);
        item.setStage(guessStage(text));
        item.setPriority("MEDIUM");
        item.setIsDraft(1);
        candidates.put(key, item);
    }

    private String candidateTitle(String text) {
        String first = text.split("\\R| \\| ", 2)[0].trim();
        if (text.contains(" | ")) return first;
        if (first.contains("劳务专家") && first.contains("听众")) {
            return text.contains("时长") || text.contains("标准") ? "劳务专家与听众比例及劳务标准" : "劳务专家与听众比例要求";
        }
        if (first.contains("线上回放")) {
            return text.contains("PPT") || text.contains("课件") ? "线上会议回放及课件要求" : "线上会议回放要求";
        }
        if (first.contains("不予立项") && text.contains("劳务占比")) return "系列会立项及劳务占比要求";
        if (first.contains("同天同场") && first.contains("劳务费") && text.contains("按一次计算")) {
            return "同天同场会议劳务费计发规则";
        }
        String subject = first.split("[（(]|必须|不得|严禁|应当|需要", 2)[0].strip();
        return subject.length() >= 4 && subject.length() < first.length() ? subject + "要求" : first;
    }

    /** 阶段归类：按关键词命中，默认 OTHER */
    public String guessStage(String text) {
        String t = text == null ? "" : text;
        for (Map.Entry<String, List<String>> e : STAGE_KEYWORDS.entrySet()) {
            for (String k : e.getValue()) {
                if (t.contains(k)) return e.getKey();
            }
        }
        return "OTHER";
    }

    private String str(Cell c) {
        if (c == null) return null;
        if (c.getCellType() == CellType.STRING) return c.getStringCellValue().trim();
        if (c.getCellType() == CellType.NUMERIC) {
            double d = c.getNumericCellValue();
            if (d == Math.floor(d)) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        if (c.getCellType() == CellType.BOOLEAN) return String.valueOf(c.getBooleanCellValue());
        return null;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
