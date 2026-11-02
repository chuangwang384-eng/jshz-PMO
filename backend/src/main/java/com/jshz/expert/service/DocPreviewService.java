package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 知识文档预览转换服务（知识星球）。
 * 使用本机 LibreOffice headless 将 Office 文档（doc/docx/xls/xlsx/ppt/pptx）转换为 PDF，
 * 转换结果按「文档 id + 版本号」缓存至预览目录，文档更新 / 永久删除时清理对应缓存。
 * 需求 3：Office 转换失败自动重试 1 次（重试使用 PDF/A 过滤器 + 加大超时）。
 */
@Service
public class DocPreviewService {

    /** 需要转换为 PDF 的 Office 扩展名 */
    public static final Set<String> OFFICE_EXTS = Set.of("doc", "docx", "xls", "xlsx", "ppt", "pptx");

    /** 首次转换超时（秒） */
    private static final long CONVERT_TIMEOUT_SECONDS = 60;

    /** 自动重试时的转换超时（秒）：加大超时，规避大文件 / 繁忙环境首次偶发超时 */
    private static final long CONVERT_RETRY_TIMEOUT_SECONDS = 120;

    /** 各 Office 类型对应的 LibreOffice PDF 导出过滤器 */
    private static final Map<String, String> PDF_FILTERS = Map.of(
            "doc", "writer_pdf_Export",
            "docx", "writer_pdf_Export",
            "xls", "calc_pdf_Export",
            "xlsx", "calc_pdf_Export",
            "ppt", "impress_pdf_Export",
            "pptx", "impress_pdf_Export");

    private static final Logger log = LoggerFactory.getLogger(DocPreviewService.class);

    private final Path storageDir;
    private final Path previewDir;
    private final String libreofficePath;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public DocPreviewService(
            @Value("${expert.knowledge.storage-dir}") String storageDir,
            @Value("${expert.knowledge.preview-dir}") String previewDir,
            @Value("${expert.knowledge.libreoffice-path}") String libreofficePath) {
        this.storageDir = normalize(storageDir);
        this.previewDir = normalize(previewDir);
        this.libreofficePath = libreofficePath;
        try {
            Files.createDirectories(this.previewDir);
        } catch (IOException e) {
            throw new IllegalStateException("预览缓存目录创建失败: " + this.previewDir, e);
        }
    }

    private static Path normalize(String path) {
        return Paths.get(path == null ? "" : path.replace('\\', '/')).toAbsolutePath().normalize();
    }

    public static boolean isOffice(String fileType) {
        return fileType != null && OFFICE_EXTS.contains(fileType.toLowerCase());
    }

    /**
     * 获取 Office 文档对应的 PDF 预览文件。
     * 缓存命中直接返回；未命中时调用 LibreOffice 转换为 PDF 并重命名缓存。
     * 首次转换失败自动重试 1 次（PDF/A 模式 + 加大超时），重试仍失败抛出 BusinessException。
     *
     * @param storedName 数据库 file_path（存储文件名）
     * @param docId      文档 id
     * @param version    文档版本号
     * @param fileType   文档扩展名（小写）
     * @return PDF 缓存文件绝对路径
     */
    public Path getPreviewFile(String storedName, Long docId, Integer version, String fileType) {
        if (docId == null || version == null) {
            throw new BusinessException("文档信息不完整，暂无法在线预览");
        }
        if (!StringUtils.hasText(storedName) || !isOffice(fileType)) {
            throw new BusinessException("该格式不支持在线预览转换");
        }
        Path cache = previewDir.resolve(cacheName(docId, version));
        if (Files.exists(cache) && Files.isRegularFile(cache)) {
            return cache;
        }
        String lockKey = docId + "_" + version;
        Object lock = locks.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            if (Files.exists(cache) && Files.isRegularFile(cache)) {
                return cache;
            }
            Path source = storageDir.resolve(safeName(storedName)).normalize();
            if (!source.startsWith(storageDir) || !Files.exists(source)) {
                throw new BusinessException("文档源文件缺失，暂无法在线预览");
            }
            try {
                Path converted = convertToPdf(source, previewDir, fileType, false);
                // 重命名为缓存名（按文档 id + 版本），同名文件替换（极端并发下以本线程产物为准）
                Files.move(converted, cache, StandardCopyOption.REPLACE_EXISTING);
                return cache;
            } catch (BusinessException firstEx) {
                // 需求 3：首次转换失败自动重试 1 次（PDF/A 模式 + 加大超时），重试仍失败再抛出
                log.warn("知识文档 Office 转 PDF 首次失败，开始自动重试 1 次（PDF/A 模式 + 加大超时）："
                        + "docId={}, version={}, fileType={}, error={}",
                        docId, version, fileType, firstEx.getMessage());
                try {
                    Path converted = convertToPdf(source, previewDir, fileType, true);
                    Files.move(converted, cache, StandardCopyOption.REPLACE_EXISTING);
                    return cache;
                } catch (BusinessException retryEx) {
                    throw new BusinessException("文档转换失败（已自动重试 1 次），暂无法在线预览");
                } catch (IOException | InterruptedException e) {
                    // 重试过程 IO 异常 / 中断：保持与首次失败一致的处理语义（中断线程并抛业务异常）
                    Thread.currentThread().interrupt();
                    throw new BusinessException("文档转换失败（已自动重试 1 次），暂无法在线预览");
                }
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("文档转换失败，暂无法在线预览");
            } finally {
                locks.remove(lockKey);
            }
        }
    }

    /**
     * 调用 LibreOffice headless 将 Office 文档转换为 PDF。
     *
     * @param source   源文件绝对路径
     * @param outDir   输出目录（预览缓存目录）
     * @param fileType Office 扩展名（小写）
     * @param retry    是否重试模式（重试使用 PDF/A 过滤器并加大超时）
     * @return 转换产出的 PDF 文件路径
     */
    private Path convertToPdf(Path source, Path outDir, String fileType, boolean retry)
            throws IOException, InterruptedException {
        String convertTarget = retry ? pdfATarget(fileType) : "pdf";
        long timeoutSeconds = retry ? CONVERT_RETRY_TIMEOUT_SECONDS : CONVERT_TIMEOUT_SECONDS;
        ProcessBuilder pb = new ProcessBuilder(
                libreofficePath,
                "--headless",
                "--convert-to", convertTarget,
                "--outdir", outDir.toString(),
                source.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new BusinessException("文档转换超时，暂无法在线预览");
        }
        if (process.exitValue() != 0) {
            throw new BusinessException("文档转换失败（退出码 " + process.exitValue() + "），暂无法在线预览");
        }
        // soffice 输出文件名 = 源文件名去掉扩展名 + .pdf
        String base = source.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String pdfName = (dot < 0 ? base : base.substring(0, dot)) + ".pdf";
        Path converted = outDir.resolve(pdfName);
        if (!Files.exists(converted)) {
            throw new BusinessException("文档转换未产出 PDF，暂无法在线预览");
        }
        return converted;
    }

    /** 重试模式使用的转换目标：PDF/A-2b（SelectPdfVersion=4）+ 对应 Office 导出过滤器。 */
    private String pdfATarget(String fileType) {
        String filter = PDF_FILTERS.getOrDefault(fileType == null ? "" : fileType.toLowerCase(), "writer_pdf_Export");
        return "pdf:" + filter + ":{\"SelectPdfVersion\":{\"type\":\"long\",\"value\":4}}";
    }

    /**
     * 清理某文档全部版本缓存（文档更新 / 永久删除时调用）。
     */
    public void clearPreviews(Long docId) {
        if (docId == null) {
            return;
        }
        String prefix = "preview_" + docId + "_v";
        try (Stream<Path> paths = Files.list(previewDir)) {
            paths.filter(p -> p.getFileName().toString().startsWith(prefix))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // 缓存清理失败不阻断业务
                        }
                    });
        } catch (IOException ignored) {
            // 目录不存在或读取失败时不阻断
        }
    }

    private String cacheName(Long docId, Integer version) {
        return "preview_" + docId + "_v" + version + ".pdf";
    }

    private String safeName(String fileName) {
        return new File(fileName == null ? "" : fileName).getName()
                .replace("..", "")
                .replace("/", "")
                .replace("\\", "");
    }
}
