package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.sl.usermodel.GroupShape;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.SlideShow;
import org.apache.poi.sl.usermodel.TableShape;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新增专家·简历解析（Owner 2026-11-07 确认口径）：
 * PDF/Word/PPT/图片 → 文本（扫描件回退本机 Tesseract OCR，复用 KnowledgeDocTextExtractor.ocrPage）
 * → 多策略本地字段解析（标签行 + 机构/科室/职称词表 + 上下文推断）→ 候选值；
 * 头像 = 内嵌图片启发式候选（最多 3 张），必须经用户确认；
 * 简历原文不留存，解析用临时文件即用即删。
 */
@Service
@RequiredArgsConstructor
public class ResumeParseService {

    private final OllamaService ollamaService;
    private final PhotoMatteService photoMatteService;

    /** 简历单文件上限：单一真源 application.yml expert.upload.resume-max-bytes。 */
    @Value("${expert.upload.resume-max-bytes}")
    private long resumeMaxBytes = 30L * 1024 * 1024;
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
            "简历", "个人", "求职", "专家", "简介", "resume", "curriculum", "个人简历", "某某", "基本情况", "学术任职", "学术成就");

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CHINESE_NAME = Pattern.compile("^[\\u4e00-\\u9fa5]{2,4}$|^(?=.{3,20}$)[\\u4e00-\\u9fa5]+(?:·[\\u4e00-\\u9fa5]+)+$");

    /** 证件照候选：base64 + mime + 是否已抠透明背景（rembg 成功=true，回退白底 JPG=false） */
    public record PhotoCandidate(String base64, String mime, boolean transparent) {}

    /** 解析结果：fields 仅含命中的字段（键与专家表单中文名一致）。 */
    public record ResumeParseResult(Map<String, String> fields, List<PhotoCandidate> photoCandidates,
                                    int hitCount, String message) {}

    /** 解析入口：按扩展名分发；非法格式/超限直接 400。 */
    public ResumeParseResult parse(String fileName, byte[] bytes) {
        return parse(fileName, bytes, true);
    }

    public ResumeParseResult parse(String fileName, byte[] bytes, boolean allowFilenameFallback) {
        String ext = com.jshz.expert.common.FileNames.extension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(400, "不支持的简历格式（支持 pdf/doc/docx/ppt/pptx/jpg/png）");
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(400, "简历文件为空");
        }
        if (bytes.length > resumeMaxBytes) {
            throw new BusinessException(400, "简历文件超过 " + (resumeMaxBytes / 1024 / 1024) + "MB 限制");
        }
        try {
            ResumeParseResult result = switch (ext) {
                case "pdf" -> parsePdf(bytes);
                case "doc" -> parseDoc(bytes);
                case "docx" -> parseDocx(bytes);
                case "ppt" -> parsePpt(bytes);
                case "pptx" -> parsePptx(bytes);
                case "jpg", "jpeg", "png" -> parseImage(bytes);
                default -> throw new BusinessException(400, "不支持的简历格式");
            };
            result = resolveName(result, fileName, allowFilenameFallback);
            result = resolveUnit(result, fileName);
            return sanitizeUnitAgainstName(result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return new ResumeParseResult(Map.of(), List.of(), 0,
                    "解析失败（" + e.getClass().getSimpleName() + "），请手动填写");
        }
    }

    /**
     * 姓名兜底（Owner 2026-11-07）：内容解析未命中姓名时，从文件名分词中提取。
     * 兼容"1 广州荔湾中心医院 何展鹏.pptx"（序号+单位+姓名）与"何展鹏-某医院.pdf"等命名。
     */
    ResumeParseResult resolveName(ResumeParseResult result, String fileName, boolean allowFilenameFallback) {
        String parsedName = result.fields().get("姓名");
        if (isCandidateName(parsedName)) return result;
        Map<String, String> fields = new LinkedHashMap<>(result.fields());
        fields.remove("姓名");
        String fallback = allowFilenameFallback ? nameFromFileName(fileName) : null;
        if (fallback != null) fields.put("姓名", fallback);
        return new ResumeParseResult(fields, result.photoCandidates(), fields.size(), result.message());
    }

    private boolean isCandidateName(String value) {
        return value != null && CHINESE_NAME.matcher(value).matches()
                && NAME_EXCLUDE_WORDS.stream().noneMatch(value::contains)
                && ORG_SUFFIXES.stream().noneMatch(value::endsWith)
                && DEPT_SUFFIXES.stream().noneMatch(value::endsWith)
                && !value.matches(".*(博士|硕士|学士|医师|教授|主任)$");
    }

    /** 简历类后缀词（可带"最新/新版"变体与纯数字版次，如"简历2""简介2024"）。 */
    private static final String RESUME_SUFFIX_PATTERN = "(?:个人简介|专家简介|的简历|简历|简介)+(?:最新版|新版|更新版|最新|新)?\\d*";

    String nameFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        String base = fileName.replaceFirst("(?i)\\.(pdf|docx?|pptx?|jpe?g|png)$", "").trim();
        // 去掉常见后缀词后再分词
        base = base.replaceAll(RESUME_SUFFIX_PATTERN + "$", "").trim();
        String[] tokens = base.split("[\\s\\-_.（）()、，,]+");
        for (String token : tokens) {
            String t = token.replaceFirst("^(?:副本)+", "")
                    .replaceFirst(RESUME_SUFFIX_PATTERN + "$", "");
            if (isCandidateName(t)) return t;
        }
        for (String token : tokens) {
            for (String suffix : ORG_SUFFIXES) {
                int start = token.lastIndexOf(suffix);
                if (start < 2) continue;
                String tail = token.substring(start + suffix.length());
                if (isCandidateName(tail)) return tail;
            }
        }
        return null;
    }

    private ResumeParseResult resolveUnit(ResumeParseResult result, String fileName) {
        String unit = result.fields().get("单位");
        if ((unit != null && !unit.isBlank()) || fileName == null) return result;
        String base = fileName.replaceFirst("(?i)\\.(pdf|docx?|pptx?|jpe?g|png)$", "").trim();
        for (String token : base.split("[\\s\\-_.、，,]+")) {
            String candidate = token.replaceFirst("^(?:副本|[0-9]+)+", "");
            if (candidate.length() < 4 || candidate.length() > 40
                    || candidate.contains("某") || NAME_EXCLUDE_WORDS.stream().anyMatch(candidate::contains)
                    || ORG_SUFFIXES.stream().noneMatch(candidate::endsWith)) continue;
            Map<String, String> fields = new LinkedHashMap<>(result.fields());
            fields.put("单位", candidate);
            return new ResumeParseResult(fields, result.photoCandidates(), fields.size(), result.message());
        }
        return result;
    }

    /**
     * 单位最终净化（2026-11-18 修复"姓名混入单位"第二形态）：
     * PPT 同一文本框内姓名与单位直接拼接（"卫利民河南科技大学第一附属医院…"）时，
     * 内容解析阶段姓名尚未确定（文件名兜底在 resolveName），当时的 sanitizeUnit 无名可剥。
     * 此处在姓名落定后二次剥离：仅当剩余部分足够长（≥6 字符，防误伤"华山"→"医院"类短单位）才执行。
     */
    ResumeParseResult sanitizeUnitAgainstName(ResumeParseResult result) {
        String name = result.fields().get("姓名");
        String unit = result.fields().get("单位");
        if (name == null || name.isBlank() || unit == null || !unit.startsWith(name)
                || unit.length() <= name.length()) {
            return result;
        }
        String remaining = unit.substring(name.length()).replaceFirst("^[，,、；;：:\\s／/|]+", "");
        if (remaining.length() < 6) return result;
        String sanitized = sanitizeUnit(unit, name);
        if (sanitized == null || sanitized.equals(unit)) return result;
        Map<String, String> fields = new LinkedHashMap<>(result.fields());
        fields.put("单位", sanitized);
        return new ResumeParseResult(fields, result.photoCandidates(), fields.size(), result.message());
    }

    // ==================== 格式分发 ====================

    private ResumeParseResult parsePdf(byte[] bytes) throws IOException {
        List<BufferedImage> images;
        String text;
        try (PDDocument doc = PDDocument.load(bytes)) {
            images = extractPdfImages(doc);
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
        return buildResult(text, images);
    }

    /**
     * 老版 .doc：文本 HWPF 抽取；文档内所有图片（含证件照）通过 PicturesTable 抽出作为头像候选
     * （Owner 2026-11-07：doc 内所有图片都可能是头像）。
     */
    private ResumeParseResult parseDoc(byte[] bytes) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        String text;
        org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(new ByteArrayInputStream(bytes));
        try {
            text = new WordExtractor(doc).getText();
            for (org.apache.poi.hwpf.usermodel.Picture pic : doc.getPicturesTable().getAllPictures()) {
                collectImage(images, pic.getContent());
            }
        } finally {
            try { doc.close(); } catch (Exception ignored) {}
        }
        return buildResult(text, images);
    }

    private ResumeParseResult parseDocx(byte[] bytes) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        String text;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            text = extractor.getText();
            doc.getAllPictures().forEach(pic -> collectImage(images, pic.getData()));
        }
        return buildResult(text, images);
    }

    private record SlideContent(String text, List<BufferedImage> images) {}

    private ResumeParseResult parsePpt(byte[] bytes) throws IOException {
        SlideContent content;
        try (HSLFSlideShow ppt = new HSLFSlideShow(new ByteArrayInputStream(bytes))) {
            content = extractSlideContent(ppt);
        }
        return buildResult(content.text(), content.images());
    }

    private ResumeParseResult parsePptx(byte[] bytes) throws IOException, InvalidFormatException {
        // 走 ZIP 中央目录读取条目大小，避免流式读取带数据描述符的条目时误报解压比。
        // 临时文件使用系统分配的路径，POI 以只读方式打开并保留 ZIP 安全检查。
        Path temporary = Files.createTempFile("resume-slides-", ".pptx");
        SlideContent content;
        try {
            Files.write(temporary, bytes);
            try (OPCPackage pkg = OPCPackage.open(temporary.toFile(), PackageAccess.READ);
                 XMLSlideShow ppt = new XMLSlideShow(pkg)) {
                content = extractSlideContent(ppt);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return buildResult(content.text(), content.images());
    }

    private SlideContent extractSlideContent(SlideShow<?, ?> ppt) {
        List<BufferedImage> images = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (var slide : ppt.getSlides()) {
            List<? extends Shape<?, ?>> shapes;
            try {
                shapes = slide.getShapes();
            } catch (IndexOutOfBoundsException e) {
                if (!(slide instanceof XSLFSlide xslf)) throw e;
                // OOXML 的形状 ID 是无符号整数，POI 构造形状树时可能将它转成负数。
                collectOoxmlParagraphs(xslf, text);
                continue;
            }
            for (var shape : shapes) {
                collectShapeText(shape, text);
            }
        }
        ppt.getPictureData().forEach(picture -> collectImage(images, picture.getData()));
        return new SlideContent(text.toString(), images);
    }

    private void collectOoxmlParagraphs(XSLFSlide slide, StringBuilder text) {
        String drawingNs = "declare namespace a='http://schemas.openxmlformats.org/drawingml/2006/main'; ";
        for (var paragraph : slide.getXmlObject().getCSld().getSpTree().selectPath(drawingNs + ".//a:p")) {
            for (var run : paragraph.selectPath(drawingNs + ".//a:t | .//a:br")) {
                try (var cursor = run.newCursor()) {
                    text.append("br".equals(cursor.getName().getLocalPart()) ? "\n" : cursor.getTextValue());
                }
            }
            text.append('\n');
        }
    }

    /** 递归收集形状文本：组合形状逐层展开，表格按单元格拼接。 */
    private void collectShapeText(Shape<?, ?> shape, StringBuilder text) {
        if (shape instanceof TableShape<?, ?> table) {
            for (int row = 0; row < table.getNumberOfRows(); row++) {
                for (int column = 0; column < table.getNumberOfColumns(); column++) {
                    var cell = table.getCell(row, column);
                    String value = cell == null ? null : cell.getText();
                    if (value != null && !value.isBlank()) text.append(value.trim()).append('\n');
                }
            }
        } else if (shape instanceof GroupShape<?, ?> group) {
            for (var child : group) {
                collectShapeText(child, text);
            }
        } else if (shape instanceof TextShape<?, ?> ts) {
            text.append(ts.getText()).append('\n');
        }
    }

    private ResumeParseResult parseImage(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new BusinessException(400, "无法读取图片内容");
        }
        String text = KnowledgeDocTextExtractor.ocrPage(image, false);
        return buildResult(text, List.of(image));
    }

    // ==================== 字段解析 ====================

    private ResumeParseResult buildResult(String text, List<BufferedImage> images) {
        var photos = photoMatteService == null || images == null || images.isEmpty() ? null
                : photoMatteService.candidates(() -> buildPhotoCandidates(images, true));
        Map<String, String> fields = new LinkedHashMap<>();
        String message = "";
        if (text == null || text.isBlank()) {
            message = "未识别到有效文本（可能为扫描件或图片质量不佳），请手动填写";
        } else {
            // 策略一：多策略本地启发式打底；策略二：本地 LLM 语义抽取（未配置 Ollama 时自动跳过）
            extractFields(text, fields);
            Map<String, String> semantic = ollamaService.extractResumeFields(text);
            if (semantic != null && !semantic.isEmpty()) {
                String normalizedText = text.replaceAll("\\s+", "");
                semantic.forEach((key, value) -> {
                    // 单位先净化（剥离混入的姓名），再走机构后缀/原文包含校验
                    String val = "单位".equals(key) ? sanitizeUnit(value, fields.get("姓名")) : value;
                    if ("单位".equals(key) && (fields.containsKey(key) || val == null
                            || ORG_SUFFIXES.stream().noneMatch(val.strip()::endsWith)
                            || !normalizedText.contains(val.replaceAll("\\s+", "")))) return;
                    // 2026-11-18 联系方式/职称/科室防幻觉（Ollama 曾臆造占位手机号 13800001234）：
                    // 三者必须能在原文（去空白）中找到才采纳；联系方式另有格式约束
                    if ("联系方式".equals(key)) {
                        if (val == null || val.isBlank()) return;
                        if (val.contains("@")) {
                            if (!EMAIL.matcher(val).matches()
                                    || !normalizedText.contains(val.replaceAll("\\s+", ""))) return;
                        } else {
                            String digits = val.replaceAll("\\D", "");
                            if (digits.startsWith("86") && digits.length() > 11) digits = digits.substring(2);
                            String textDigits = normalizedText.replaceAll("\\D", "");
                            if (digits.length() < 7 || !textDigits.contains(digits)) return;
                        }
                    }
                    if ("职称".equals(key) && (val == null || !TITLE_WORDS.contains(val.strip())
                            || !normalizedText.contains(val.replaceAll("\\s+", "")))) return;
                    if ("科室".equals(key) && (val == null
                            || DEPT_SUFFIXES.stream().noneMatch(val.strip()::endsWith)
                            || !normalizedText.contains(val.replaceAll("\\s+", "")))) return;
                    if (!"姓名".equals(key) || (!isCandidateName(fields.get("姓名"))
                            && isCandidateName(val) && normalizedText.contains(val))) {
                        fields.put(key, val);
                    }
                });
                if (!fields.isEmpty()) message = "已结合本地语义模型解析";
            }
            // 无效姓名不算已归入结构化字段，原文仍应进入简介。
            if (!isCandidateName(fields.get("姓名"))) fields.remove("姓名");
            if (fields.isEmpty()) {
                message = "已识别文本但未命中可填充字段，请手动填写";
            }
        }
        // 未归入五个字段的简历文本统一收入简介（保留换行；仅在简介为空时填充）
        if (!fields.containsKey("简介")) {
            String profile = collectProfileLines(text, fields);
            if (!profile.isEmpty()) {
                fields.put("简介", profile);
            }
        }
        List<PhotoCandidate> photoCandidates = photos == null ? List.of() : photos.join();
        if (photoCandidates.isEmpty()) photoCandidates = buildPhotoCandidates(images, false);
        return new ResumeParseResult(fields, photoCandidates, fields.size(), message);
    }

    private List<PhotoCandidate> buildPhotoCandidates(List<BufferedImage> images, boolean matte) {
        // 头像候选：先取"头像特征"过滤结果；若全部被过滤，回退为文档内所有图片
        //（Owner 2026-11-07：doc 等格式内所有图片都可能是头像）
        List<PhotoCandidate> photoCandidates = new ArrayList<>();
        List<BufferedImage> safeImages = images == null ? List.of() : images;
        List<BufferedImage> pool = safeImages.stream().filter(this::isAvatarLike)
                .sorted(java.util.Comparator.comparing(img -> img.getWidth() > img.getHeight()))
                .toList();
        if (pool.isEmpty()) pool = safeImages;
        for (BufferedImage img : pool) {
            if (photoCandidates.size() >= 3) break;
            if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) continue;
            try {
                // Owner 2026-11-11：先转无损 PNG 保留原始像素 → rembg 抠图 → 透明证件照；
                // 抠图失败（运行时未装/超时）回退白底 JPG（原行为）
                byte[] matted = matte ? photoMatteService.removeBackground(toPngBytes(resizePortraitInput(img))) : null;
                if (matted != null) {
                    // 抠图后按最长边 600px 缩放（保持透明通道；避免文档大图 base64 过大）
                    byte[] scaled = downscalePng(matted, 600);
                    photoCandidates.add(new PhotoCandidate(
                            Base64.getEncoder().encodeToString(scaled), "image/png", true));
                } else {
                    String candidate = toBase64Jpeg(img);
                    if (!candidate.isEmpty()) {
                        photoCandidates.add(new PhotoCandidate(candidate, "image/jpeg", false));
                    }
                }
            } catch (Exception ignored) {
                // 单张候选失败不影响其余
            }
        }
        return photoCandidates;
    }

    /** 头像特征：最短边 80px、宽高比 0.5~1.8。大图在推理前缩放。 */
    private boolean isAvatarLike(BufferedImage img) {
        if (img == null) return false;
        int w = img.getWidth(), h = img.getHeight();
        if (w < 80 || h < 80) return false;
        double ratio = (double) w / h;
        return ratio >= 0.5 && ratio <= 1.8;
    }

    private BufferedImage resizePortraitInput(BufferedImage image) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= 1200) return image;
        double scale = 1200.0 / longest;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    /** 解码图片字节并收集（解码失败或 0 尺寸异常图跳过）。 */
    private void collectImage(List<BufferedImage> images, byte[] data) {
        if (data == null || data.length == 0) return;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null && img.getWidth() > 0 && img.getHeight() > 0) images.add(img);
        } catch (Exception ignored) {
        }
    }

    /**
     * 收集未归入 姓名/单位/科室/职称/联系方式 的文本行进简介，保留原始顺序与换行。
     * 排除：已抽取字段的取值行、这些字段的标签行、空行。超长截断到 2000 字符。
     */
    String collectProfileLines(String text, Map<String, String> fields) {
        List<String> leftover = new ArrayList<>();
        Set<String> values = new HashSet<>();
        for (String v : List.of("姓名", "单位", "科室", "职称", "联系方式")) {
            String value = fields.get(v);
            if (value != null && !value.isBlank()) values.add(value.trim());
        }
        for (String line : text.split("\\R")) {
            String v = line == null ? "" : line.replaceAll("[ \\t]+", " ").trim();
            if (v.isEmpty() || v.matches("\\d{1,3}") || v.matches("-?\\d+页?")
                    || v.matches("第\\d+页.*共\\d+页")) continue;
            if (values.contains(v)) continue;
            if (CONSUMED_LABELS.stream().anyMatch(v::contains)) continue;
            if (!leftover.contains(v)) leftover.add(v);
        }
        String joined = String.join("\n", leftover);
        return joined.length() > 2000 ? joined.substring(0, 2000) : joined;
    }

    /** 已被结构化字段消费的标签关键词（命中即视为该行信息已归位）。 */
    private static final List<String> CONSUMED_LABELS = List.of(
            "姓名", "单位", "科室", "职称", "联系方式", "联系电话", "电话", "手机", "邮箱", "Email", "E-mail");

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
        if (!isCandidateName(name)) {
            name = null;
            for (int i = 1; i < cleaned.size(); i++) {
                if (!cleaned.get(i).matches("个人简介|个人简历")) continue;
                String preceding = cleaned.get(i - 1).replaceAll("\\s+", "");
                if (isCandidateName(preceding)) {
                    name = preceding;
                    break;
                }
            }
        }
        if (name == null) {
            // 无标签简历：前 3 行内寻找 2~4 汉字人名（首行常为"个人简历"类标题）
            for (int i = 0; i < Math.min(3, cleaned.size()) && name == null; i++) {
                String line = cleaned.get(i);
                if (isCandidateName(line)) {
                    name = line;
                }
            }
        }
        if (name != null) fields.put("姓名", name);

        String unit = extractByLabel(cleaned, "单位", "Unit", 2, 40);
        if (unit == null) {
            // 2026-11-18 改为强/弱候选分级取最优：
            // 强=整行以机构后缀结尾（"洛阳市中心医院"、"医院河南科技大学第一附属医院"表格标签行）；
            // 弱=行中截断到机构后缀（"河南省医学会青年委员"→"河南省医学会"、"主治医师XX医院放疗科"→"主治医师XX医院"）。
            // 跳过：教育经历行（毕业/年份开头），防"2018年毕业于XX大学""2013/09-2018/07南方医科大学"误抓。
            String strong = null;
            String weak = null;
            for (String line : cleaned) {
                String compact = line.replace(" ", "");
                if (compact.length() > 40 || compact.contains("简历")) continue;
                if (compact.contains("毕业")) continue;
                if (compact.matches("\\d{4}.*")) continue;
                String lineStrong = null;
                String lineWeak = null;
                for (String suffix : ORG_SUFFIXES) {
                    if (compact.endsWith(suffix)) {
                        String cand = stripLeadingOrgEcho(compact);
                        if (cand.length() >= 4 && (lineStrong == null || betterOrgCandidate(cand, lineStrong))) {
                            lineStrong = cand;
                        }
                        break;
                    }
                    int idx = compact.indexOf(suffix);
                    if (idx >= 0) {
                        String cand = compact.substring(0, idx + suffix.length());
                        if (cand.length() >= 4 && (lineWeak == null || betterOrgCandidate(cand, lineWeak))) {
                            lineWeak = cand;
                        }
                    }
                }
                if (lineStrong != null && (strong == null || betterOrgCandidate(lineStrong, strong))) strong = lineStrong;
                if (lineWeak != null && (weak == null || betterOrgCandidate(lineWeak, weak))) weak = lineWeak;
            }
            unit = strong != null ? strong : weak;
        }
        unit = sanitizeUnit(unit, name);
        if (unit != null) fields.put("单位", unit);

        String dept = extractByLabel(cleaned, "科室", "Department", 2, 20);
        if (dept == null) {
            for (String line : cleaned) {
                String compact = line.replace(" ", "");
                if (compact.length() > 20 || compact.contains("简历")) continue;
                if (unit != null && (compact.equals(unit) || unit.contains(compact))) continue;
                if (DEPT_SUFFIXES.stream().anyMatch(compact::endsWith)
                        && ORG_SUFFIXES.stream().noneMatch(compact::endsWith)) {
                    // 2026-11-18：行内含机构后缀时取其后尾段（"河南科技大学第一附属医院肿瘤内科"→"肿瘤内科"），
                    // 防止"主治医师XX医院放疗科"整行混入科室
                    String tail = compact;
                    for (String suffix : ORG_SUFFIXES) {
                        int idx = tail.indexOf(suffix);
                        if (idx >= 0) {
                            tail = tail.substring(idx + suffix.length());
                            break;
                        }
                    }
                    if (tail.length() >= 2 && tail.length() <= 10
                            && DEPT_SUFFIXES.stream().anyMatch(tail::endsWith)
                            && TITLE_WORDS.stream().noneMatch(tail::contains)) {
                        dept = tail;
                        break;
                    }
                }
            }
        }
        if (dept != null) fields.put("科室", dept);

        String title = extractByLabel(cleaned, "职称", "Title", 2, 20);
        if (title == null) title = extractTitleByLongestMatch(cleaned);
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

    /**
     * 职称兜底（2026-11-17 修复词边界误拒）：取行内最长匹配职称词。
     * 旧规则要求职称词前后均非汉字，会把"外科副主任医师"（前接"科"）这类同行拼接误拒；
     * 改为保留尾边界（避免"护士长"命中子串"护士"）+ 最长匹配（避免"副主任医师"被更短子串
     * "主任医师"抢先），等长保留 TITLE_WORDS 中更高优先级者。对外契约不变：返回职称词或 null。
     */
    private String extractTitleByLongestMatch(List<String> lines) {
        for (String line : lines) {
            String best = null;
            for (String word : TITLE_WORDS) {
                int idx = line.indexOf(word);
                if (idx < 0) continue;
                // 尾边界：职称词后不能紧接汉字（排除"护士长"中的"护士"等子串）
                if (idx + word.length() < line.length() && isChineseWordChar(line.charAt(idx + word.length()))) continue;
                if (best == null || word.length() > best.length()) best = word;
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * 剥除行首机构后缀回声（2026-11-18）：表格标签行"医院 河南科技大学第一附属医院"压缩后
     * 以"医院"开头，仅当剩余部分仍以机构后缀结尾时剥除标签，防整体误剥。
     */
    String stripLeadingOrgEcho(String compact) {
        for (String suffix : ORG_SUFFIXES) {
            if (compact.startsWith(suffix) && compact.length() > suffix.length()) {
                String rest = compact.substring(suffix.length());
                if (ORG_SUFFIXES.stream().anyMatch(rest::endsWith)) return rest;
            }
        }
        return compact;
    }

    /**
     * 机构候选排序（2026-11-18 王新帅原件回归）：医疗简历语境下"医院"后缀（含卫生院）
     * 优先于大学/中心/公司等——访问学者行"美国加州大学…医学中心"不得压过任职医院行；
     * 同后缀取更长候选。
     */
    boolean betterOrgCandidate(String candidate, String current) {
        boolean candHospital = candidate.endsWith("医院") || candidate.endsWith("卫生院");
        boolean curHospital = current.endsWith("医院") || current.endsWith("卫生院");
        if (candHospital != curHospital) return candHospital;
        return candidate.length() > current.length();
    }

    /**
     * 单位字段净化（2026-11-17 修复"姓名混入单位"；2026-11-18 增加职称前缀剥离"主治医师XX医院"）：
     * 设计稿常把姓名与单位写在同一行（"李向东，汝州市第一人民医院…"），按机构后缀截断时
     * 会把前置姓名一并带入。此处剥离已知姓名前缀，并按分隔符取以机构后缀结尾的机构段，
     * 丢弃姓名/职称段。对外契约不变：返回净化后的单位字符串，无机构段时返回原值（交由上层校验）。
     */
    String sanitizeUnit(String raw, String knownName) {
        if (raw == null || raw.isBlank()) return null;
        String unit = raw.trim();
        // 剥离已解析出的姓名前缀（含其后分隔符，兼容"姓名，单位"与"姓名单位"直接拼接）
        if (knownName != null && !knownName.isBlank() && unit.startsWith(knownName)) {
            unit = unit.substring(knownName.length()).replaceFirst("^[，,、；;：:\\s／/|]+", "").trim();
        }
        // 剥除职称前缀（Ollama/截断候选常见"主治医师XX医院"），最长职称词优先
        for (String title : TITLE_WORDS.stream().sorted((a, b) -> b.length() - a.length()).toList()) {
            if (unit.startsWith(title) && unit.length() > title.length()) {
                String rest = unit.substring(title.length()).replaceFirst("^[，,、；;：:\\s／/|]+", "").trim();
                if (rest.contains("医院") || rest.contains("大学") || rest.contains("学院")
                        || ORG_SUFFIXES.stream().anyMatch(rest::endsWith)) {
                    unit = rest;
                }
                break;
            }
        }
        // 按分隔符切分，取以机构后缀结尾的最长段（姓名/职称段被丢弃）
        String best = null;
        for (String part : unit.split("[，,、；;／/|\\s]+")) {
            String p = part.trim();
            if (p.length() >= 4 && ORG_SUFFIXES.stream().anyMatch(p::endsWith)
                    && (best == null || p.length() > best.length())) {
                best = p;
            }
        }
        if (best != null) return best;
        return unit.isEmpty() ? null : unit;
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

    /** 抠图结果缩放（保留 Alpha）：最长边超限时等比缩小并重编码 PNG。 */
    private byte[] downscalePng(byte[] png, int maxSide) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        if (src == null) throw new IOException("PNG 解析失败");
        int w = src.getWidth(), h = src.getHeight();
        int limit = Math.max(w, h);
        if (limit <= maxSide) return png;
        double scale = (double) maxSide / limit;
        BufferedImage out = new BufferedImage(Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!ImageIO.write(out, "png", bos)) throw new IOException("PNG 编码失败");
        return bos.toByteArray();
    }

    /** 候选原图转无损 PNG bytes（保留像素，供 rembg 抠图）。 */
    private byte[] toPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "png", bos) || bos.size() == 0) throw new IOException("PNG 编码失败");
        return bos.toByteArray();
    }

    /** 候选图统一重绘到 TYPE_INT_RGB 白底（JPEG 编码器不支持带 Alpha 的颜色模型）并缩到最长边 600px。 */
    private String toBase64Jpeg(BufferedImage img) throws IOException {
        int w = img.getWidth(), h = img.getHeight();
        int maxSide = Math.max(w, h);
        double scale = maxSide > 600 ? 600.0 / maxSide : 1.0;
        BufferedImage out = new BufferedImage((int) (w * scale), (int) (h * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(img, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!ImageIO.write(out, "jpg", bos)) return "";   // 无编码器时返回空串，由调用方过滤
        byte[] encoded = bos.toByteArray();
        return encoded.length == 0 ? "" : Base64.getEncoder().encodeToString(encoded);
    }

    private List<BufferedImage> extractPdfImages(PDDocument doc) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        int pageIndex = 0;
        for (PDPage page : doc.getPages()) {
            if (pageIndex >= 2 || images.size() >= 10) break;
            PDResources resources = page.getResources();
            if (resources == null) continue;
            for (var name : resources.getXObjectNames()) {
                try {
                    if (resources.getXObject(name) instanceof PDImageXObject image) {
                        collectImage(images, toJpegBytes(image.getImage()));
                    }
                } catch (Exception ignored) {
                    // 单个图片对象损坏不影响其余候选
                }
            }
            pageIndex++;
        }
        return images;
    }

    private byte[] toJpegBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

}
