package com.jshz.expert.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 本地 PP-OCRv5 venv 布局的唯一实现（2026-11-13 复用治理收敛）。
 * 既有两个消费方（抠图 PhotoMatteService、表格识别 LocalTableOcr），此前各自维护
 * "root/runtime/Scripts/python.exe" 相对路径与存在性探测；统一到本类后，
 * venv 目录结构变更只需改 PYTHON_RELATIVE 一处。
 * 注意：两个消费方的根目录环境变量语义不同（PHOTOMATTE_* 三级探测 vs COMPLIANCE_PPOCR_ROOT 单根），
 * 探测候选列表由各服务自行组装，本类只承载布局常量与扫描逻辑。
 */
public final class PpOcrRuntime {

    /** venv 内 Python 解释器相对路径（独立 venv 布局约定）。 */
    public static final String PYTHON_RELATIVE = "runtime/Scripts/python.exe";

    private PpOcrRuntime() {
    }

    /** 指定根目录下的 Python 解释器路径。 */
    public static Path pythonUnder(Path root) {
        return root.resolve(PYTHON_RELATIVE);
    }

    /** 指定根目录下的脚本路径。 */
    public static Path scriptUnder(Path root, String scriptName) {
        return root.resolve(scriptName);
    }

    /** 解释器与脚本同时存在才算运行时就绪。 */
    public static boolean ready(Path python, Path script) {
        return Files.isRegularFile(python) && Files.isRegularFile(script);
    }

    /** 按顺序返回第一个存在的候选路径；全部缺失返回 null。 */
    public static Path firstExisting(List<Path> candidates) {
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }
}
