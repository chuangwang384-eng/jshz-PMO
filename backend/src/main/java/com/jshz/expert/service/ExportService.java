package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 导入导出服务（正式版 V1.001，接口全集对齐 demo）。
 * CSV（UTF-8 BOM）导出/模板/导入、ZIP（CSV+三类照片）、Markdown 下载说明、下载留痕。
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    /** CSV 表头（接口中文键，18 字段英文列名的中文对应） */
    public static final List<String> CSV_HEADERS = List.of(
            "id", "姓名", "单位", "科室", "职称", "研究方向", "擅长领域", "联系方式", "简介",
            "身份证号", "银行卡号", "银行卡开户行", "照片", "身份证正面", "身份证反面",
            "创建时间", "修改时间", "修改人id");

    /** 导入模板列（12 列，不含照片与时间字段） */
    public static final List<String> TEMPLATE_COLUMNS = List.of(
            "姓名", "单位", "科室", "职称", "研究方向", "擅长领域", "联系方式", "简介",
            "身份证号", "银行卡号", "银行卡开户行");

    private final ExpertService expertService;
    private final PhotoStorageService photoStorageService;
    private final DownloadAuditService downloadAuditService;

    // ==================== CSV ====================

    /**
     * 将专家列表转为 CSV 字节（UTF-8 带 BOM），full=false 时敏感字段脱敏。
     */
    public byte[] expertsToCsv(List<Expert> experts, boolean full) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM
        sb.append(String.join(",", CSV_HEADERS)).append("\r\n");
        for (Expert e : experts) {
            List<String> row = new ArrayList<>();
            row.add(e.getId() == null ? "" : String.valueOf(e.getId()));
            row.add(nvl(e.getName()));
            row.add(nvl(e.getUnit()));
            row.add(nvl(e.getDepartment()));
            row.add(nvl(e.getTitle()));
            row.add(nvl(e.getResearchDirection()));
            row.add(nvl(e.getExpertiseArea()));
            row.add(nvl(e.getContact()));
            row.add(nvl(e.getProfile()));
            row.add(full ? nvl(e.getIdCard()) : ExpertService.maskSensitive(e.getIdCard()));
            row.add(full ? nvl(e.getBankCard()) : ExpertService.maskSensitive(e.getBankCard()));
            row.add(nvl(e.getBankBranch()));
            row.add(nvl(e.getPhoto()));
            row.add(nvl(e.getIdCardFront()));
            row.add(nvl(e.getIdCardBack()));
            row.add(e.getCreateTime() == null ? "" : e.getCreateTime().toString());
            row.add(e.getUpdateTime() == null ? "" : e.getUpdateTime().toString());
            row.add(nvl(e.getUpdateBy()));
            sb.append(row.stream().map(ExportService::csvEscape).reduce((a, b) -> a + "," + b).orElse(""))
                    .append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成导入模板 CSV（表头 + 示例行）。
     */
    public byte[] templateCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(String.join(",", TEMPLATE_COLUMNS)).append("\r\n");
        sb.append(csvLine("张三", "示例单位", "示例科室", "主任医师", "心血管", "冠心病诊疗",
                "13800138000", "示例简介，可删除此行", "110101199001011234", "6222001234567890123", "示例银行支行"))
                .append("\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 导入 CSV（UTF-8-sig / UTF-8 / GBK 自动识别），逐行校验并记录失败明细。
     * 返回 Map: success / failed（List<Map: line,name,reason>） / total。
     */
    public Map<String, Object> importCsv(MultipartFile file, AuthUser user) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("导入文件不能为空");
        }
        List<byte[]> rawLines;
        try {
            rawLines = readLines(file);
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }
        if (rawLines.isEmpty()) {
            throw new BusinessException("文件内容为空");
        }
        String[] headers = parseLine(rawLines.get(0));
        List<String> headerList = new ArrayList<>();
        for (String h : headers) {
            headerList.add(h == null ? "" : h.trim());
        }
        if (!(headerList.contains("姓名") && headerList.contains("身份证号")
                && headerList.contains("银行卡号") && headerList.contains("联系方式"))) {
            throw new BusinessException("CSV 表头必须包含：姓名、身份证号、银行卡号、联系方式");
        }

        int success = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        List<Expert> toInsert = new ArrayList<>();
        for (int i = 1; i < rawLines.size(); i++) {
            String[] cells = parseLine(rawLines.get(i));
            if (cells == null || cells.length == 0 || isBlankRow(cells)) {
                continue;
            }
            int lineNo = i + 1;
            Expert expert = new Expert();
            String name = cell(headers, cells, headerList.indexOf("姓名"));
            String reason = null;
            if (!StringUtils.hasText(name)) {
                reason = "姓名为空";
            }
            expert.setName(name);
            expert.setUnit(cell(headers, cells, headerList.indexOf("单位")));
            expert.setDepartment(cell(headers, cells, headerList.indexOf("科室")));
            expert.setTitle(cell(headers, cells, headerList.indexOf("职称")));
            expert.setResearchDirection(cell(headers, cells, headerList.indexOf("研究方向")));
            expert.setExpertiseArea(cell(headers, cells, headerList.indexOf("擅长领域")));
            expert.setContact(cell(headers, cells, headerList.indexOf("联系方式")));
            expert.setProfile(cell(headers, cells, headerList.indexOf("简介")));
            expert.setIdCard(cell(headers, cells, headerList.indexOf("身份证号")));
            expert.setBankCard(cell(headers, cells, headerList.indexOf("银行卡号")));
            expert.setBankBranch(cell(headers, cells, headerList.indexOf("银行卡开户行")));
            expert.setUpdateBy(user == null ? null : user.getUsername());

            if (reason == null && StringUtils.hasText(expert.getIdCard())
                    && !expert.getIdCard().matches("^\\d{17}[\\dXx]$")) {
                reason = "身份证号格式不正确";
            }
            if (reason == null && StringUtils.hasText(expert.getBankCard())
                    && !expert.getBankCard().matches("^\\d{13,19}$")) {
                reason = "银行卡号格式不正确";
            }
            if (reason == null && StringUtils.hasText(expert.getContact())
                    && !expert.getContact().matches("^1\\d{10}$|^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                reason = "联系方式格式不正确";
            }
            if (reason != null) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("line", lineNo);
                f.put("name", nvl(name));
                f.put("reason", reason);
                failed.add(f);
                continue;
            }
            boolean nameExists = expertService.list(
                    new LambdaQueryWrapper<Expert>().eq(Expert::getName, name)).stream()
                    .anyMatch(x -> name.equals(x.getName()));
            if (nameExists) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("line", lineNo);
                f.put("name", nvl(name));
                f.put("reason", "姓名重复");
                failed.add(f);
                continue;
            }
            toInsert.add(expert);
        }
        if (!toInsert.isEmpty()) {
            expertService.saveBatch(toInsert);
        }
        success = toInsert.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("total", rawLines.size() - 1);
        return result;
    }

    // ==================== ZIP ====================

    /**
     * 打包 ZIP：experts.csv（完整字段）+ photos/ 下三类照片（命名：姓名+单位+科室+类型）。
     *
     * @param experts 专家列表
     * @param full    是否完整（false 时 CSV 脱敏）
     * @param auditType 审计类型（selected_zip / full_zip）
     */
    public byte[] buildZip(List<Expert> experts, boolean full, String auditType, AuthUser user,
                           HttpServletRequest request) {
        if (experts == null || experts.isEmpty()) {
            throw new BusinessException("没有可导出的专家数据");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            byte[] csv = expertsToCsv(experts, full);
            ZipEntry csvEntry = new ZipEntry("experts.csv");
            zos.putNextEntry(csvEntry);
            zos.write(csv);
            zos.closeEntry();

            List<Expert> audited = new ArrayList<>();
            for (Expert e : experts) {
                List<String[]> photos = new ArrayList<>();
                if (StringUtils.hasText(e.getPhoto())) {
                    photos.add(new String[]{"证件照", e.getPhoto()});
                }
                if (StringUtils.hasText(e.getIdCardFront())) {
                    photos.add(new String[]{"身份证正面", e.getIdCardFront()});
                }
                if (StringUtils.hasText(e.getIdCardBack())) {
                    photos.add(new String[]{"身份证反面", e.getIdCardBack()});
                }
                for (String[] p : photos) {
                    String fileName = photoZipName(e, p[0], p[1]);
                    try {
                        byte[] data = Files.readAllBytes(photoStorageService.resolvePhoto(p[1]));
                        ZipEntry entry = new ZipEntry("photos/" + fileName);
                        zos.putNextEntry(entry);
                        zos.write(data);
                        zos.closeEntry();
                    } catch (IOException ex) {
                        // 单个照片缺失不阻断整包导出
                    }
                }
                audited.add(e);
            }
            audit(audited, auditType, user, request);
        } catch (IOException e) {
            throw new BusinessException("ZIP 打包失败: " + e.getMessage());
        }
        return bos.toByteArray();
    }

    // ==================== Markdown ====================

    /**
     * 生成 Markdown 下载说明（含字段索引 / 图片命名规则 / AI 下载指引；full=1 完整版）。
     */
    public byte[] buildMarkdown(List<Expert> experts, boolean full, AuthUser user, HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 专家库数据下载说明\n\n");
        sb.append("生成时间：").append(java.time.LocalDateTime.now()).append("\n\n");
        sb.append("## 字段索引\n\n");
        sb.append("| 序号 | 字段名 | 说明 |\n|---|---|---|\n");
        int idx = 1;
        for (String h : CSV_HEADERS) {
            sb.append("| ").append(idx++).append(" | ").append(h).append(" | 见设计文档专家字段定义 |\n");
        }
        sb.append("\n## 图片命名规则\n\n");
        sb.append("ZIP 内照片位于 `photos/` 目录，命名格式：`姓名+单位+科室+类型`（类型：证件照 / 身份证正面 / 身份证反面）。\n\n");
        sb.append("## 数据规模\n\n");
        sb.append("本次导出专家 ").append(experts == null ? 0 : experts.size()).append(" 人。\n\n");
        if (full) {
            sb.append("## AI 下载指引（完整版）\n\n");
            sb.append("1. 完整字段含身份证号、银行卡号等敏感信息，请按最小化原则使用并妥善保存。\n");
            sb.append("2. 照片文件与 CSV 中 `照片 / 身份证正面 / 身份证反面` 字段对应，字段值为照片文件名。\n");
            sb.append("3. 如需人工核对，请以 `id` 字段关联 CSV 与照片（照片文件名含专家 id 与时间戳）。\n");
        }
        audit(experts, full ? "md_full" : "md", user, request);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ==================== 审计 ====================

    /**
     * 下载留痕：selected_* 逐专家一条；全量 / md 一条（expert_id=null）。
     */
    public void audit(List<Expert> experts, String downloadType, AuthUser user, HttpServletRequest request) {
        boolean perExpert = downloadType.startsWith("selected");
        if (!perExpert) {
            // 全量 / zip / md：无论多少专家只记一条（expert_id=null）
            downloadAuditService.record(user, null, null, downloadType, request);
            return;
        }
        if (experts == null || experts.isEmpty()) {
            downloadAuditService.record(user, null, null, downloadType, request);
            return;
        }
        for (Expert e : experts) {
            downloadAuditService.record(user, e.getId(), e.getName(), downloadType, request);
        }
    }

    // ==================== 私有工具 ====================

    private List<byte[]> readLines(MultipartFile file) throws IOException {
        byte[] all = file.getBytes();
        // 去 BOM
        if (all.length >= 3 && (all[0] & 0xFF) == 0xEF && (all[1] & 0xFF) == 0xBB && (all[2] & 0xFF) == 0xBF) {
            byte[] tmp = new byte[all.length - 3];
            System.arraycopy(all, 3, tmp, 0, tmp.length);
            all = tmp;
        }
        Charset charset = detectCharset(all);
        String text = new String(all, charset);
        String[] lines = text.split("\r\n|\n|\r");
        List<byte[]> result = new ArrayList<>();
        for (String line : lines) {
            result.add(line.getBytes(StandardCharsets.UTF_8));
        }
        return result;
    }

    private Charset detectCharset(byte[] data) {
        // GBK 常见中文字节模式探测（简化：若可被 UTF-8 严格解码则视为 UTF-8，否则 GBK）
        try {
            String s = new String(data, StandardCharsets.UTF_8);
            // 用 CharsetDecoder 严格校验
            java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            decoder.decode(java.nio.ByteBuffer.wrap(data));
            return StandardCharsets.UTF_8;
        } catch (Exception e) {
            try {
                return Charset.forName("GBK");
            } catch (Exception ex) {
                return StandardCharsets.UTF_8;
            }
        }
    }

    private String[] parseLine(byte[] lineUtf8) {
        String line = new String(lineUtf8, StandardCharsets.UTF_8).trim();
        if (line.isEmpty()) {
            return new String[0];
        }
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    cells.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        cells.add(cur.toString());
        return cells.toArray(new String[0]);
    }

    private boolean isBlankRow(String[] cells) {
        for (String c : cells) {
            if (StringUtils.hasText(c)) {
                return false;
            }
        }
        return true;
    }

    private String cell(String[] headers, String[] cells, int idx) {
        if (idx < 0 || idx >= headers.length || idx >= cells.length) {
            return null;
        }
        String v = cells[idx];
        return v == null ? null : v.trim();
    }

    private String csvLine(String... values) {
        List<String> escaped = new ArrayList<>();
        for (String v : values) {
            escaped.add(csvEscape(v == null ? "" : v));
        }
        return String.join(",", escaped);
    }

    private static String csvEscape(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }

    /**
     * ZIP 内照片命名：姓名+单位+科室+类型，去掉非法文件名字符。
     */
    private String photoZipName(Expert e, String type, String photoName) {
        String base = nvl(e.getName()) + "+" + nvl(e.getUnit()) + "+" + nvl(e.getDepartment()) + "+" + type;
        base = base.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        int dot = photoName.lastIndexOf('.');
        String ext = dot >= 0 ? photoName.substring(dot) : ".jpg";
        return base + ext;
    }
}
