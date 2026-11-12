# 发布记录：合规扫描表格 PP-OCRv5 升级（1.0.38.3）

- 日期：2026-11-11
- 需求来源：Owner 要求 PP-OCRv5 模型接入合规 PDF 表格解析，真实样本验收后发布
- 交接前置：`docs/handover/tasks/20261111_ppocrv5_compliance_rollout.md`（Codex 实施中 → ZCode 接手完成安装/验证/发布）
- 状态：**已发布生产（11:18 后端 R-1 蓝绿，仅后端，无迁移），发布后核验通过**
- 类型：解析引擎升级；**无数据库迁移、无前端制品变更**

## 1. 内容
- 解析 OCR 引擎升级：合规 PDF 检测到有线表格后优先使用 **PP-OCRv5 server 版 ONNX 模型**（本地 RapidOCR 3.10 + ONNX Runtime，经 `LocalTableOcr` 调用本地 Python，单并发、75s 超时）；无有效表格或缺行时回退 Tesseract（原引擎）。
- PP-OCRv5 模型与运行时安装于受保护目录 `<LOCAL_PATH> Files\JSHZ\pp-ocrv5`（服务以 LocalSystem 运行可读）；E:\AI助手开发\pp-ocrv5 保留源副本供管理。
- 独立标题规则兼容 PP-OCRv5 识别形态（如「一、劳务：」带编号与冒号）。

## 2. 真实样本验收（交接内完成 + 本次复核）
- 样本《北京融和医学发展基金会合规要求》第 2 页 250 DPI：讲课费四档 <5000/≤3000/≤2000/≤1000 元/次比较符号正确；PPT 行 200元/页、科普视频行 ≤500元 正确；12 表格块、0 缺列块（`temp/ppocr-page2.tsv`、`logs/ppocr-page2-benchmark-20261111.log`）。
- 隔离构建 Surefire 10 项 0 失败；真实 PDF 15 条候选（`logs/ppocr-integration-retest-20261111.log`）。

## 3. 本次接手执行（核验→安装→验证→发布）
1. 事实核验：候选 jar SHA256=3bd13df825e0…9261、Surefire 10 项 0 失败、stage 目录完整。
2. 运行时安装 `scripts/install-ppocr-runtime-20261111.ps1`：/XJ 解引用 junction、排除 cache；实体 Python（cpython-3.12.16）物理拷贝；pyvenv.cfg home 重写至 Program Files；两模型 SHA-256 与交接一致；目标内重解析点=0。
3. 双通道推理验证：交互会话 + SYSTEM 计划任务（LocalSystem）各一次 `python -I`，均退出 0、模型自 Program Files 加载、识别坐标/置信度一致。
4. DryRun 11 阶段通过（退出码 0）→ 蓝绿发布 `logs/deploy-backend.20261111_111656.log` R-1 发布完成。

## 4. 发布后核验（11:19 实测）
- 8080 直连与经 Nginx `/api/health` 均 **1.0.38.3 UP**（buildTime 11:00:51 与候选一致）。
- 运行 jar（backend/target/expert-system.jar）SHA256 = 候选 jar（3bd13df8…）。
- SPA 200；version.txt 与 frontend/package.json 已同步 1.0.38.3。

## 5. 回滚
重新部署 1.0.38.2 后端制品（backend/releases 留存 previous jar）；运行时目录可保留（旧版代码不读取）。

## 6. 遗留与注意事项
- 生产已有事项**不批量重解析**；Owner 挑样本重解析验收 OCR 质量（对照原件核查人员档位/比较符号/金额/单位/条目归属）。
- 已知识别缺陷：主持费用副高行「≤2000元次」斜杠缺失，待对照原件复核。
- 冷启动/长文档耗时：首样本约 55s，需持续观察。
