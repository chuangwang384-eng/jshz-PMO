package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新增专家·简历解析（Owner 2026-11-07 确认口径）：
 * PDF/Word/PPT/图片 → 文本（扫描件回退本机 Tesseract OCR，复用 KnowledgeDocTextExtractor.ocrPage）
 * → 多策略本地字段解析（标签行 + 机构/科室/职称词表 + 上下文推断）→ 候选值；
 * 头像 = 内嵌图片启发式候选（最多 3 张），必须经用户确认；
 * 简历原文不落盘不留存，临时文件即用即删。
 */
@Service
@RequiredArgsConstructor
public class ResumeParseService {

    private final OllamaService ollamaService;

    public static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "doc", "docx", "ppt", "pptx", "jpg", "jpeg", "png");

    /** 职称词表（精确匹配，优先级从高到低）。 */
    private static final List<String> TITLE_WORDS = List.of(
            "主任医师", "副主任医师", "主治医师", "住院医师",
            "主任药师", "副主任药师", "主管药师", "药师",
            "主任技师", "副主任技师", "主管技师", "技师",
            "主任护师", "副主任护师", "主管护师", "护师", "护士",
            "研究员", "副研究员", "助理研究员", "研究实习员",
            "教授", "副教授", "讲师",
            "高级工程师", "工程师", "助理工程师",
            "高级经济师", "经济师");

    /** 机构后缀词表。 */
    private static final List<String> ORG_SUFFIXES = List.of(
            "医院", "研究院", "研究所", "大学", "学院", "中心", "公司", "卫生院", "疾控中心", "医学会");

    /** 科室特征词。 */
    private static final List<String> DEPT_SUFFIXES = List.of("科", "病房", "研究室", "办公室", "中心", "部", "组");

    /** 姓名启发式排除词（首行含这些词时不视为姓名）。 */
    private static final List<String> NAME_EXCLUDE_WORDS = List.of(
            "简历", "个人", "求职", "专家", "简介", "resume", "curriculum", "个人简历");

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CHINESE_NAME = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,4}$");

    /** 解析结果：fields 仅含命中的字段（键与专家表单中文名一致）。 */
    public record ResumeParseResult(Map<String, String> fields, List<String> photoCandidates,
                                    int hitCount, String message) {}

    /** 解析入口：按扩展名分发；非法格式/超限直接 400。 */
    public ResumeParseResult parse(String fileName, byte[] bytes) {
        String ext = extension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(400, "不支持的简历格式（支持 pdf/doc/docx/ppt/pptx/jpg/png）");
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(400, "简历文件为空");
        }
        if (bytes.length > MAX_SIZE) {
            throw new BusinessException(400, "简历文件超过 20MB 限制");
        }
        try {
            return switch (ext) {
                case "pdf" -> parsePdf(bytes);
                case "doc" -> parseDoc(bytes);
                case "docx" -> parseDocx(bytes);
                case "ppt", "pptx" -> parsePpt(bytes);
                case "jpg", "jpeg", "png" -> parseImage(bytes);
                default -> throw new BusinessException(400, "不支持的简历格式");
            };
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return new ResumeParseResult(Map.of(), List.of(), 0,
                    "解析失败（" + e.getClass().getSimpleName() + "），请手动填写");
        }
    }

    // ==================== 格式分发 ====================

    private ResumeParseResult parsePdf(byte[] bytes) throws IOException {
        List<String> photoCandidates;
        String text;
        try (PDDocument doc = PDDocument.load(bytes)) {
            photoCandidates = extractPdfImages(doc);
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            // 扫描件回退：每页平均文本过少时栅格化前 3 页 OCR
            if (text.trim().length() < Math.max(20L, doc.getNumberOfPages() * 20L)) {
                PDFRenderer renderer = new PDFRenderer(doc);
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
        return buildResult(text, photoCandidates);
    }

    private ResumeParseResult parseDoc(byte[] bytes) throws IOException {
        String text;
        try (WordExtractor extractor = new WordExtractor(new ByteArrayInputStream(bytes))) {
            text = extractor.getText();
        }
        return buildResult(text, List.of());
    }

    private ResumeParseResult parseDocx(byte[] bytes) throws IOException {
        List<String> photoCandidates = new ArrayList<>();
        String text;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            text = extractor.getText();
            doc.getAllPictures().forEach(pic -> addCandidate(photoCandidates, pic.getData()));
        }
        return buildResult(text, photoCandidates);
    }

    private ResumeParseResult parsePpt(byte[] bytes) throws IOException {
        List<String> photoCandidates = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            for (XSLFSlide slide : ppt.getSlides()) {
                // 递归展开组合形状：简历版式中姓名/单位常嵌套在 grpSp 内
                for (var shape : slide) {
                    collectShapeText(shape, text);
                }
            }
            for (XSLFPictureData pic : ppt.getPictureData()) {
                addCandidate(photoCandidates, pic.getData());
            }
        }
        return buildResult(text.toString(), photoCandidates);
    }

    /** 递归收集形状文本：组合形状逐层展开，表格按单元格拼接。 */
    private void collectShapeText(org.apache.poi.xslf.usermodel.XSLFShape shape, StringBuilder text) {
        if (shape instanceof org.apache.poi.xslf.usermodel.XSLFGroupShape group) {
            for (var child : group) {
                collectShapeText(child, text);
            }
        } else if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape ts) {
            text.append(ts.getText()).append('\n');
        } else if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTable table) {
            for (var row : table) {
                for (var cell : row) {
                    String v = cell.getText();
                    if (v != null && !v.isBlank()) text.append(v.trim()).append('\n');
                }
            }
        }
    }

    private ResumeParseResult parseImage(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new BusinessException(400, "无法读取图片内容");
        }
        List<String> photoCandidates = new ArrayList<>();
        addCandidate(photoCandidates, bytes);
        String text = KnowledgeDocTextExtractor.ocrPage(image, false);
        return buildResult(text, photoCandidates);
    }

    // ==================== 字段解析 ====================

    private ResumeParseResult buildResult(String text, List<String> photoCandidates) {
        Map<String, String> fields = new LinkedHashMap<>();
        String message = "";
        if (text == null || text.isBlank()) {
            message = "未识别到有效文本（可能为扫描件或图片质量不佳），请手动填写";
        } else {
            // 策略一：多策略本地启发式打底；策略二：本地 LLM 语义抽取（未配置 Ollama 时自动跳过）
            extractFields(text, fields);
            Map<String, String> semantic = ollamaService.extractResumeFields(text);
            if (semantic != null && !semantic.isEmpty()) {
                fields.putAll(semantic);   // 语义结果优先覆盖，启发式仅补缺
                if (!fields.isEmpty()) message = "已结合本地语义模型解析";
            }
            if (fields.isEmpty()) {
                message = "已识别文本但未命中可填充字段，请手动填写";
            }
        }
        return new ResumeParseResult(fields, photoCandidates, fields.size(), message);
    }

    /** 多策略字段解析：标签行优先，词表/上下文推断次之；首个可信命中即停。 */
    void extractFields(String text, Map<String, String> fields) {
        String[] lines = text.split("\\R");
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            String v = line == null ? "" : line.replaceAll("[ \\t]+", " ").trim();
            if (v.isEmpty()) continue;
            // open-resume 式段落清洗：丢弃页码/页眉页脚噪声（对 OCR 文本尤其重要）
            if (v.matches("\\d{1,3}") || v.matches("-?\\d+页?") || v.matches("第\\d+页.*共\\d+页")) continue;
            cleaned.add(v);
        }
        String full = text.replaceAll("[ \\t]", "");

        String name = extractByLabel(cleaned, "姓名", "Name", 2, 20);
        if (name == null) {
            // 无标签简历：前 3 行内寻找 2~4 汉字人名（首行常为"个人简历"类标题）
            for (int i = 0; i < Math.min(3, cleaned.size()) && name == null; i++) {
                String line = cleaned.get(i);
                if (CHINESE_NAME.matcher(line).matches()
                        && NAME_EXCLUDE_WORDS.stream().noneMatch(w -> line.toLowerCase(Locale.ROOT).contains(w.toLowerCase()))
                        && ORG_SUFFIXES.stream().noneMatch(line::endsWith)
                        && DEPT_SUFFIXES.stream().noneMatch(line::endsWith)) {
                    name = line;
                }
            }
        }
        if (name != null) fields.put("姓名", name);

        String unit = extractByLabel(cleaned, "单位", "Unit", 2, 40);
        if (unit == null) {
            for (String line : cleaned) {
                String compact = line.replace(" ", "");
                if (compact.length() > 40 || compact.contains("简历")) continue;
                // 设计稿常把职称/头衔接在单位后（"…医院院长"），按机构后缀截断
                for (String suffix : ORG_SUFFIXES) {
                    int idx = compact.indexOf(suffix);
                    if (idx >= 0) {
                        unit = compact.substring(0, idx + suffix.length());
                        break;
                    }
                }
                if (unit != null) break;
            }
        }
        if (unit != null) fields.put("单位", unit);

        String dept = extractByLabel(cleaned, "科室", "Department", 2, 20);
        if (dept == null) {
            for (String line : cleaned) {
                String compact = line.replace(" ", "");
                if (compact.length() > 20 || compact.contains("简历")) continue;
                if (unit != null && (compact.equals(unit) || unit.contains(compact))) continue;
                if (DEPT_SUFFIXES.stream().anyMatch(compact::endsWith)
                        && ORG_SUFFIXES.stream().noneMatch(compact::endsWith)) {
                    dept = compact;
                    break;
                }
            }
        }
        if (dept != null) fields.put("科室", dept);

        String title = extractByLabel(cleaned, "职称", "Title", 2, 20);
        if (title == null) {
            for (String line : cleaned) {
                for (String word : TITLE_WORDS) {
                    int idx = line.indexOf(word);
                    if (idx >= 0 && (idx == 0 || !isChineseWordChar(line.charAt(Math.max(0, idx - 1))))
                            && (idx + word.length() >= line.length()
                                || !isChineseWordChar(line.charAt(idx + word.length())))) {
                        title = word;
                        break;
                    }
                }
                if (title != null) break;
            }
        }
        if (title != null) fields.put("职称", title);

        Matcher phone = PHONE.matcher(full);
        if (phone.find()) fields.put("联系方式", phone.group());
        else {
            Matcher email = EMAIL.matcher(text);
            if (email.find()) fields.put("联系方式", email.group());
        }
    }

    /** 标签行解析：同行取分隔符后内容；单独标签行取下一个非空行。 */
    private String extractByLabel(List<String> lines, String cnLabel, String enLabel, int minLen, int maxLen) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int idx = indexOfLabel(line, cnLabel, enLabel);
            if (idx < 0) continue;
            String rest = line.substring(idx).replaceFirst("^(" + cnLabel + "|" + enLabel + ")[:：\\s]*", "").trim();
            if (rest.isEmpty()) {
                for (int j = i + 1; j < lines.size() && j <= i + 2; j++) {
                    if (!lines.get(j).isEmpty()) {
                        rest = lines.get(j).trim();
                        break;
                    }
                }
            }
            rest = rest.replaceAll("^[|｜\\-—]+", "").trim();
            if (rest.length() >= minLen && rest.length() <= maxLen) {
                return rest;
            }
        }
        return null;
    }

    private int indexOfLabel(String line, String cnLabel, String enLabel) {
        int cn = line.indexOf(cnLabel);
        int en = enLabel == null ? -1 : line.toUpperCase(Locale.ROOT).indexOf(enLabel.toUpperCase(Locale.ROOT));
        if (cn >= 0 && en >= 0) return Math.min(cn, en);
        return Math.max(cn, en);
    }

    private boolean isChineseWordChar(char c) {
        return c >= 0x4e00 && c <= 0x9fa5;
    }

    // ==================== 头像候选 ====================

    private void addCandidate(List<String> candidates, byte[] data) {
        if (candidates.size() >= 3 || data == null || data.length < 5 * 1024) return;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) return;
            int w = img.getWidth(), h = img.getHeight();
            // 高清简历图普遍超过 1500px（如 3508×2283 设计稿），上限放宽到 4000px
            if (w < 80 || h < 80 || w > 4000 || h > 4000) return;
            double ratio = (double) w / h;
            if (ratio < 0.5 || ratio > 1.8) return;
            candidates.add(toBase64Jpeg(img));
        } catch (Exception ignored) {
            // 单张候选失败不影响整体解析
        }
    }

    /** 候选图统一缩到最长边 600px 的 JPEG base64。 */
    private String toBase64Jpeg(BufferedImage img) throws IOException {
        int w = img.getWidth(), h = img.getHeight();
        int maxSide = Math.max(w, h);
        BufferedImage out = img;
        if (maxSide > 600) {
            double scale = 600.0 / maxSide;
            out = new BufferedImage((int) (w * scale), (int) (h * scale), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, out.getWidth(), out.getHeight(), null);
            g.dispose();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bos);
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    private List<String> extractPdfImages(PDDocument doc) throws IOException {
        List<String> candidates = new ArrayList<>();
        int pageIndex = 0;
        for (PDPage page : doc.getPages()) {
            if (pageIndex >= 2 || candidates.size() >= 3) break;
            PDResources resources = page.getResources();
            if (resources == null) continue;
            for (var name : resources.getXObjectNames()) {
                try {
                    if (resources.getXObject(name) instanceof PDImageXObject image) {
                        addCandidate(candidates, image.getImage() != null
                                ? toJpegBytes(image.getImage()) : null);
                    }
                } catch (Exception ignored) {
                    // 单个图片对象损坏不影响其余候选
                }
            }
            pageIndex++;
        }
        return candidates;
    }

    private byte[] toJpegBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

    private String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
