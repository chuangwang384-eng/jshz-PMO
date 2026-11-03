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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** 按文件类型分发解析（fileType 形如 xlsx/docx/pdf） */
    public ParseResult parse(String fileName, String fileType, byte[] bytes, String textContent) {
        try {
            String ft = fileType == null ? "" : fileType.toLowerCase();
            if (ft.equals("xlsx") || ft.equals("xls")) {
                return parseExcel(bytes);
            }
            // PDF/DOCX：优先使用已抽取的正文文本（knowledge_document.content 已有正文抽取链路）
            String text = textContent;
            if (text == null || text.isBlank()) {
                return new ParseResult(new ArrayList<>(), "未能抽取文本内容，请维护人手动录入事项");
            }
            return parseTextLines(text);
        } catch (Exception e) {
            log.warn("合规文档解析失败（fileName={}）：{}", fileName, e.getMessage());
            return new ParseResult(new ArrayList<>(), "自动解析失败（" + e.getClass().getSimpleName() + "），请手动录入事项");
        }
    }

    /** xlsx/xls 表头映射解析：时间节点→timeNote、项目→title、内容→content、备注→remark */
    private ParseResult parseExcel(byte[] bytes) {
        List<ComplianceItem> items = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
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
    private ParseResult parseTextLines(String text) {
        List<ComplianceItem> items = new ArrayList<>();
        for (String line : text.split("\r?\n")) {
            String t = line == null ? "" : line.trim();
            if (t.length() < 4 || t.length() > 190) continue;
            // 跳过明显非事项行（页码、目录、纯符号）
            if (t.matches("^[\\d\\s.。、-]+$") || t.startsWith("目录") || t.startsWith("第页")) continue;
            ComplianceItem it = new ComplianceItem();
            it.setTitle(t.length() > 60 ? t.substring(0, 60) : t);
            it.setContent(t);
            it.setStage(guessStage(t));
            it.setPriority("MEDIUM");
            it.setIsDraft(1);
            items.add(it);
            if (items.size() >= 200) break;
        }
        String msg = items.isEmpty() ? "文本未解析出事项，请手动录入" : "已解析 " + items.size() + " 条事项（文本规则），请确认后发布";
        return new ParseResult(items, msg);
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
