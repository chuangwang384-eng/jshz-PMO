# 合规扫描表格 PP-OCRv5 升级交接

2026-11-11 · 主责 Codex · 实施中

## 目标与验收

用户要求将 PP-OCRv5 模型下载到 E:\AI助手开发，将其接入合规 PDF 表格解析，并在真实样本验收后发布生产。验收使用《北京融和医学发展基金会合规要求》，对照原件核查人员档位、比较符号、金额、单位和条目归属。正文原有分段、表格行分组与维护人确认流程均需回归。

## 当前状态

- 分支 codex/personal-dashboard-v11，生产基线 a417c51。工作区存在多项既有暂存和未暂存改动。发布前须比较候选 JAR 与当前线上 JAR 的运行时条目，避免带入意外改动。
- 11:01 左右实测生产 /api/health 返回 1.0.38.2 UP，version.txt 同为 1.0.38.2。候选版本 1.0.38.3，尚未启动生产发布。
- E:\AI助手开发\pp-ocrv5 下已有 Python 3.12.16、RapidOCR 3.10.0、ONNX Runtime 1.31.1、脚本及两个模型。上游清单存 models/upstream-models.yaml，两份文件 SHA-256 均已实查。
  - ch_PP-OCRv5_det_server.onnx 为 0F8846B1D4BBA223A2A2F9D9B44022FBC22CC019051A602B41A7FDA9667E4CAD。
  - ch_PP-OCRv5_rec_server.onnx 为 E09385400EAAAEF34CEFF54AEB7C4F0F1FE014C27FA8B9905D4709B65746562A。
- 独立构建目录由 temp/compliance-release-1383-stage.txt 指向。复跑 clean test package 完成，Surefire 三组共 10 项通过，真实 PDF 生成 15 条候选，候选 JAR 已生成。续做时核对 logs/ppocr-integration-retest-20261111.log、Surefire 报告与候选包哈希。

## 文件与实现

| 文件 | 作用 |
| --- | --- |
| scripts/compliance-ppocr-table.py | 使用本地 ONNX 模型生成带坐标的 TSV，已复制到 E 盘模型目录 |
| backend/src/main/java/com/jshz/expert/service/LocalTableOcr.java | 调用本地 Python，单并发、75 秒超时。默认从 Program Files/JSHZ/pp-ocrv5 读取运行时，测试可设置 COMPLIANCE_PPOCR_ROOT。 |
| backend/src/main/java/com/jshz/expert/service/KnowledgeDocTextExtractor.java | 检测到有线表格后先试 PP-OCRv5；无有效表格或缺行时回退 Tesseract。 |
| backend/src/main/java/com/jshz/expert/service/ComplianceParseService.java | 调整独立标题规则，兼容 PP-OCRv5 识别出的「一、劳务：」。 |
| backend/src/test/java/com/jshz/expert/service/ComplianceLlmExtractTest.java | 真实样本测试。COMPLIANCE_PPOCR_EXPECTED=true 时额外验证讲课费四档符号和 PPT 计费单位。 |

## 证据与已知失败

- 真实 PDF 第 2 页以 250 DPI 渲染，PP-OCRv5 测得讲课费为 <5000、≤3000、≤2000、≤1000 元/次。PPT 行识别出 200元/页，科普视频行识别出 ≤500元。现有表格定位逻辑得到 12 个块、0 个缺列块。证据为 temp/ppocr-page2.tsv 和 logs/ppocr-page2-benchmark-20261111.log。
- 主持费用副高行仍识别成「≤2000元次」，斜杠缺失。对照原件才能确认单位。首次整份 PDF 回归中单样本耗时约 55 秒，后续需观察冷启动和长文档耗时。
- RapidOCR 3.10.0 的模型版本和大小参数需要枚举。首次传字符串报 “The value of Det.ocr_version must be Enum Type.”，已改为 OCRVersion.PPOCRV5 和 ModelType.SERVER。
- 首轮集成共 10 项，1 项失败。PP-OCRv5 把章节标题读为「一、劳务：」，旧标题规则未跳过。已加可选编号和冒号，独立目录复跑通过。

## 后续执行顺序

1. 先读取复跑日志及 Surefire 报告，确认 10 项退出 0，真实 PDF 的四档金额比较符号、PPT 单位和五项分组均通过。若 JAR 未构建，从独立目录重跑原命令。环境变量 COMPLIANCE_PPOCR_ROOT 指向 E:/AI助手开发/pp-ocrv5，COMPLIANCE_SAMPLE_PDF 指向 E:/王创/2026-11-4/北京融和医学发展基金会合规要求.pdf，COMPLIANCE_PPOCR_EXPECTED=true。
2. 准备生产运行时安装脚本。ExpertSystemBackend 实测以 LocalSystem 运行，E 盘目录继承 Authenticated Users Modify。生产从管理员保护的 <LOCAL_PATH> Files\JSHZ\pp-ocrv5 加载。复制已核验的模型、脚本、runtime 和实体 Python 基础目录，重写 runtime/pyvenv.cfg 的 home 指向 Program Files 内的实体 Python，核对目录权限、重解析点和模型哈希。受保护副本尚未安装。
3. 在受保护副本中以 python -I 运行真实样本推理，确认 TSV、模型使用路径和耗时。再用隔离后端验证合规解析和 Tesseract 回退，尤其检查服务账号可读取该副本。模型文件留存 E 盘供管理。
4. 核对当前生产健康与 JAR 差异，执行 release-backend.ps1 的 DryRun，再用管理员 PowerShell 蓝绿发布。发布后验证 8080 与 Nginx 均为 1.0.38.3 UP、运行 JAR 哈希一致及生产解析路径可用，随后同步 version.txt、frontend/package.json 和发布记录。
5. 本次预计只发布后端，无迁移。回退重新部署 1.0.38.2 后端制品。独立测试和上线核验完成前，已有生产事项不应批量重解析或清空。

上游模型入口为 [RapidOCR 模型清单](https://github.com/RapidAI/RapidOCR/blob/main/python/rapidocr/default_models.yaml)。

## 2026-11-11 接手执行结果（ZCode）

- 事实核验：隔离构建 Surefire 10 项 0 失败（含真实 PDF 15 条候选）、候选 jar SHA256=3bd13df825e0...d78e9261、stage 目录 builds/1.0.38.3-build-20261111_105715（version.txt=1.0.38.3）。
- 运行时安装：`scripts/install-ppocr-runtime-20261111.ps1` 完成 E:→<LOCAL_PATH> Files\JSHZ\pp-ocrv5（/XJ 解引用 junction、排除 cache；实体 Python cpython-3.12.16 物理拷贝；pyvenv.cfg home 已重写）。校验：两模型 SHA-256 与交接一致（0F8846B1…/E09385400E…）、目标内重解析点=0。日志 logs/install-ppocr-runtime-20261111.log。
- 推理验证：交互会话与 SYSTEM（LocalSystem）计划任务双通道 `python -I` 各跑一次自测图，均退出 0、模型自 Program Files 加载、识别结果坐标/置信度一致（讲课费/5000元/次，99.79/95.85）。
- 隔离后端与发布：release-backend.ps1 DryRun 11 阶段通过（退出码 0）；蓝绿发布完成 `logs/deploy-backend.20261111_111656.log`。发布后核验：8080 与经 Nginx 均 **1.0.38.3 UP**（buildTime 11:00:51 与候选一致）、运行 jar 哈希=候选哈希（3bd13df8…）、SPA 200。
- 版本元数据：version.txt 与 frontend/package.json 均已同步 1.0.38.3。
- 遗留：生产已有事项不批量重解析（等 Owner 挑样本重解析验收 OCR 质量）；主持费用副高行「≤2000元次」斜杠缺失问题待对照原件复核；冷启动/长文档耗时持续观察（首样本约 55s，后续观察）。

## 2026-11-11 乱码候选定位与热修 1.0.38.4（ZCode）

- Owner 反馈候选出现乱码（(220444 Ht K)|、hex:、7/2、T/x 等）。
- 取证：对同路径 extractStructured 全 14 块文本逐块比对，乱码串**不存在于 OCR 文本**；结合流水线结构（候选非空时不调 LLM）判定乱码来自 **PP-OCRv5/Tesseract 对整页块的识别噪声行**，被 `addParagraphs/addCandidate`（仅要求 ≥4 字符）逐字带入候选——**OCR 层噪声 + 候选无质量门槛**，非 LLM 编造。
- 修复：`addCandidate` 增加中文门槛（CJK 字符 <2 的行丢弃）。真实 PDF 回归：候选 15 条全干净（此前含噪声）。
- 热修 1.0.38.4 后端蓝绿发布完成（11:44，builds/1.0.38.4-build-20261111_114142），运行 jar 哈希与候选一致，`/api/health`=1.0.38.4 UP。
- 遗留：内容行内嵌噪声片段（噪声与正文同行拼接）仍需维护人编辑；「≤2000元次」缺斜杠待对照原件。

## 2026-11-11 酒店段解析质量修复 + 解析中弹窗（1.0.38.5，ZCode，已发布）

- Owner 反馈：①二、酒店交通段候选混入 OCR 噪声行（1（tee、[ie Jowe…、xyes aN）且表格行丢失（飞机/高铁/早餐/午餐/茶歇等缺失）；②解析时需要"正在解析"弹窗。
- 取证：该页表格定位失败 → 回退 Tesseract 整页识别（噪声大且行丢失）。
- 修复（1.0.38.5）：
  1. `addParagraphs` 逐行剔除不含中文的 OCR 噪声行（此前仅整块门槛）；
  2. `KnowledgeDocTextExtractor` 表格定位失败的扫描页：**PP-OCRv5 整页行识别与 Tesseract 竞争择优**（PP-OCR TSV 逐行置信度 ≥60 过滤，按 CJK 字符量择优；新增 `ppOcrLines/cjkCount`）；
  3. 前端 MaintenanceBoard 解析动作加 `ElLoading` 全屏弹窗（"正在解析文档（OCR + 大模型识别，约需 1-2 分钟）"）。
- 实测（真实 PDF 全链路）：酒店段候选从"Tesseract 乱码+行丢失"变为 **PP-OCRv5 完整干净内容**（序号/类别/项目/标准表头 + 飞机/高铁/市内交通/早餐/午餐晚餐≤300元/人/天/茶歇≤80元/人/住宿费≤650·≤800元/人 全部在位），乱码 0；全量 mvn test 107 过 0 失败。
- 发布：12:09 后端 R-1 蓝绿 + 前端 graceful（ReleaseId 1.0.38.5-parse-modal-ppocr-page-20261111），核验 /api/health 经 Nginx =1.0.38.5 UP、root 已切、index 哈希一致、运行 jar=候选（8b2c2d49…）、SPA 200。
