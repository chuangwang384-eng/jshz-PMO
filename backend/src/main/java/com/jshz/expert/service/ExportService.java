package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Expert;
import com.jshz.expert.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 导入导出服务（正式版 V1.002，CSV 全面升级为 Excel）。
 * Excel（.xlsx）导出/模板/导入、ZIP（XLSX+三类照片）、Markdown 下载说明、下载留痕。
 * 说明：后续以 Excel（xlsx）为准，所有数字/日期列均以字符串形式写入单元格，保持与旧 CSV 一致的宽松导入体验。
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    /** Excel 表头（接口中文键，18 字段英文列名的中文对应） */
    public static final List<String> XLSX_HEADERS = List.of(
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

    // ==================== Excel ====================

    /**
     * 将专家列表转为 Excel（.xlsx）字节，全部单元格以字符串写入（数字/日期列保持字符串）。
     * full=false 时敏感字段脱敏。
     */
    public byte[] expertsToXlsx(List<Expert> experts, boolean full) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("专家数据");
            XSSFRow header = sheet.createRow(0);
            for (int c = 0; c < XLSX_HEADERS.size(); c++) {
                header.createCell(c).setCellValue(XLSX_HEADERS.get(c));
            }
            int r = 1;
            for (Expert e : experts) {
                XSSFRow row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.getId() == null ? "" : String.valueOf(e.getId()));
                row.createCell(1).setCellValue(nvl(e.getName()));
                row.createCell(2).setCellValue(nvl(e.getUnit()));
                row.createCell(3).setCellValue(nvl(e.getDepartment()));
                row.createCell(4).setCellValue(nvl(e.getTitle()));
                row.createCell(5).setCellValue(nvl(e.getResearchDirection()));
                row.createCell(6).setCellValue(nvl(e.getExpertiseArea()));
                row.createCell(7).setCellValue(nvl(e.getContact()));
                row.createCell(8).setCellValue(nvl(e.getProfile()));
                row.createCell(9).setCellValue(full ? nvl(e.getIdCard()) : ExpertService.maskSensitive(e.getIdCard()));
                row.createCell(10).setCellValue(full ? nvl(e.getBankCard()) : ExpertService.maskSensitive(e.getBankCard()));
                row.createCell(11).setCellValue(nvl(e.getBankBranch()));
                row.createCell(12).setCellValue(nvl(e.getPhoto()));
                row.createCell(13).setCellValue(nvl(e.getIdCardFront()));
                row.createCell(14).setCellValue(nvl(e.getIdCardBack()));
                row.createCell(15).setCellValue(e.getCreateTime() == null ? "" : e.getCreateTime().toString());
                row.createCell(16).setCellValue(e.getUpdateTime() == null ? "" : e.getUpdateTime().toString());
                row.createCell(17).setCellValue(nvl(e.getUpdateBy()));
            }
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Excel 导出失败: " + e.getMessage());
        }
    }

    /**
     * 生成导入模板 Excel（表头 + 示例行）。
     */
    public byte[] templateXlsx() {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("导入模板");
            XSSFRow header = sheet.createRow(0);
            for (int c = 0; c < TEMPLATE_COLUMNS.size(); c++) {
                header.createCell(c).setCellValue(TEMPLATE_COLUMNS.get(c));
            }
            String[] sample = new String[]{"张三", "示例单位", "示例科室", "主任医师", "心血管", "冠心病诊疗",
                    "13800138000", "示例简介，可删除此行", "110101199001011234", "6222001234567890123", "示例银行支行"};
            XSSFRow row = sheet.createRow(1);
            for (int c = 0; c < sample.length; c++) {
                row.createCell(c).setCellValue(sample[c]);
            }
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("模板生成失败: " + e.getMessage());
        }
    }

    /**
     * 导入 Excel（.xlsx）：读取第一个工作表，逐行校验并记录失败明细。
     * 校验逻辑与旧 CSV 导入完全一致（表头必含姓名/身份证号/银行卡号/联系方式；
     * 姓名重复拒绝、setUpdateBy 保留），全部列按字符串宽松读取。
     * 返回 Map: success / failed（List<Map: line,name,reason>） / total。
     */
    public Map<String, Object> importXlsx(MultipartFile file, AuthUser user) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("导入文件不能为空");
        }
        List<Map<Integer, String>> rows = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            XSSFSheet sheet = wb.getSheetAt(0);
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                XSSFRow row = sheet.getRow(i);
                Map<Integer, String> cells = new LinkedHashMap<>();
                if (row != null) {
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        cells.put(c, cellString(row.getCell(c)));
                    }
                }
                rows.add(cells);
            }
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BusinessException("文件内容为空");
        }
        Map<Integer, String> headerRow = rows.get(0);
        List<String> headerList = new ArrayList<>();
        for (int c = 0; c < headerRow.size(); c++) {
            String h = headerRow.get(c);
            headerList.add(h == null ? "" : h.trim());
        }
        if (!(headerList.contains("姓名") && headerList.contains("身份证号")
                && headerList.contains("银行卡号") && headerList.contains("联系方式"))) {
            throw new BusinessException("Excel 表头必须包含：姓名、身份证号、银行卡号、联系方式");
        }

        int success = 0;
        List<Map<String, Object>> failed = new ArrayList<>();
        List<Expert> toInsert = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            Map<Integer, String> cells = rows.get(i);
            if (cells.isEmpty() || isBlankRow(cells)) {
                continue;
            }
            int lineNo = i + 1;
            Expert expert = new Expert();
            String name = cellAt(cells, headerList, "姓名");
            String reason = null;
            if (!StringUtils.hasText(name)) {
                reason = "姓名为空";
            }
            expert.setName(name);
            expert.setUnit(cellAt(cells, headerList, "单位"));
            expert.setDepartment(cellAt(cells, headerList, "科室"));
            expert.setTitle(cellAt(cells, headerList, "职称"));
            expert.setResearchDirection(cellAt(cells, headerList, "研究方向"));
            expert.setExpertiseArea(cellAt(cells, headerList, "擅长领域"));
            expert.setContact(cellAt(cells, headerList, "联系方式"));
            expert.setProfile(cellAt(cells, headerList, "简介"));
            expert.setIdCard(cellAt(cells, headerList, "身份证号"));
            expert.setBankCard(cellAt(cells, headerList, "银行卡号"));
            expert.setBankBranch(cellAt(cells, headerList, "银行卡开户行"));
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
        result.put("total", Math.max(0, rows.size() - 1));
        return result;
    }

    // ==================== ZIP ====================

    /**
     * 打包 ZIP：experts.xlsx（完整字段）+ photos/ 下三类照片（命名：姓名+单位+科室+类型）。
     *
     * @param experts 专家列表
     * @param full    是否完整（false 时 Excel 脱敏）
     * @param auditType 审计类型（selected_zip / full_zip）
     */
    public byte[] buildZip(List<Expert> experts, boolean full, String auditType, AuthUser user,
                           HttpServletRequest request) {
        if (experts == null || experts.isEmpty()) {
            throw new BusinessException("没有可导出的专家数据");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            byte[] xlsx = expertsToXlsx(experts, full);
            ZipEntry xlsxEntry = new ZipEntry("experts.xlsx");
            zos.putNextEntry(xlsxEntry);
            zos.write(xlsx);
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
        for (String h : XLSX_HEADERS) {
            sb.append("| ").append(idx++).append(" | ").append(h).append(" | 见设计文档专家字段定义 |\n");
        }
        sb.append("\n## 图片命名规则\n\n");
        sb.append("ZIP 内照片位于 `photos/` 目录，命名格式：`姓名+单位+科室+类型`（类型：证件照 / 身份证正面 / 身份证反面）。\n\n");
        sb.append("## 数据规模\n\n");
        sb.append("本次导出专家 ").append(experts == null ? 0 : experts.size()).append(" 人。\n\n");
        if (full) {
            sb.append("## AI 下载指引（完整版）\n\n");
            sb.append("1. 完整字段含身份证号、银行卡号等敏感信息，请按最小化原则使用并妥善保存。\n");
            sb.append("2. 照片文件与 Excel 中 `照片 / 身份证正面 / 身份证反面` 字段对应，字段值为照片文件名。\n");
            sb.append("3. 如需人工核对，请以 `id` 字段关联 Excel 与照片（照片文件名含专家 id 与时间戳）。\n");
        }
        audit(experts, full ? "md_full" : "md", user, request);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ==================== 审计 ====================

    /**
     * 下载留痕：selected_* 逐专家一条；全量 / md 一条（expert_id=null）。
     * download_type 取值：selected_xlsx / selected_zip / full_xlsx / full_zip / md / template_xlsx 等。
     */
    public void audit(List<Expert> experts, String downloadType, AuthUser user, HttpServletRequest request) {
        boolean perExpert = downloadType.startsWith("selected");
        if (!perExpert) {
            // 全量 / zip / md / template：无论多少专家只记一条（expert_id=null）
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

    /**
     * 单元格统一按字符串读取：数字型整数（身份证/银行卡/手机号等）还原为不带小数/科学计数的原文，
     * 保持与旧 CSV 宽松导入一致。
     */
    private String cellString(XSSFCell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        switch (type) {
            case STRING:
                return cell.getStringCellValue() == null ? "" : cell.getStringCellValue().trim();
            case NUMERIC:
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    return BigDecimal.valueOf(v).toBigInteger().toString();
                }
                return String.valueOf(v);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * 按表头名定位列索引取值（找不到返回 null）。
     */
    private String cellAt(Map<Integer, String> cells, List<String> headerList, String headerName) {
        int idx = headerList.indexOf(headerName);
        if (idx < 0) {
            return null;
        }
        String v = cells.get(idx);
        return v == null ? null : v.trim();
    }

    private boolean isBlankRow(Map<Integer, String> cells) {
        for (String v : cells.values()) {
            if (StringUtils.hasText(v)) {
                return false;
            }
        }
        return true;
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
