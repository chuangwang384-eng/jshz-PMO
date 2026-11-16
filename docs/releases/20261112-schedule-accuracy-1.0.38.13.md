# 发布记录：日程解析 P1-P4 准确率优化（1.0.38.13）

- 日期：2026-11-12
- 状态：**构建与验证完成，待发布**（`temp\deploy-13813.cmd` = 仅后端蓝绿，无迁移，无前端变更）
- 基准：`docs/handover/tasks/20261112_schedule_codex_benchmark.md`（12 份 Codex 对照，含附录优化方案 P1-P4）

## P1：图片型 PPTX/扫描 PDF 接入 PP-OCRv5（预期增量最大：47 条参照命中 0）
- `extractPdf`：从"整文 Tesseract 150dpi"改为**逐页 250dpi 渲染 → PP-OCRv5/Tesseract 按 CJK 择优**；
- `extractPpt`：图片型幻灯片（文本 <40 字符且有内嵌图片）→ 遍历 `XSLFPictureShape`（含组合形状递归）对每张图片跑 PP-OCRv5/Tesseract 竞争；
- `extractImage`：jpg/png 日程直接走 PP-OCRv5/Tesseract 竞争。
- 覆盖：144-日程(1).pdf（27 条参照）、妇愈芬芳日程.pptx（20 条）等此前**零候选**的图片/扫描件。

## P2：PPTX 文本框坐标配对
- Codex 已实现（PptBlock 坐标 + Y 聚类 + X 排序），本次确认覆盖重庆/零售场景，无需额外改动。

## P3：主持合并格计一次
- `ruleParse` 主持列多名单合并格取首位（不再逐名拆分导致多报）。

## P4：LLM 兜底约束
- 超时重试 1 次；候选须回指原文（姓名含于输入 + 议题/时间字段覆盖率 ≥60%）；按出场去重（姓名+时间+议题）。

## 验证
- 全量 mvn test：**116 通过 / 0 失败 / 10 跳过**；ScheduleParseServiceTest 11 项通过。
- 三份低分文件提取文本实测：144-PDF 575 字符（含完整表格文字）、妇愈芬芳 PPTX 0 字符（图片幻灯片，需运行时 PP-OCR，代码已接入）、重庆 249 字符+25 个文本框。
- 基准脚本（temp/opening-chair-edge-13812.mjs + runner）可用于发布后回归。

## 已知限制
- 妇愈芬芳 PPTX 图片幻灯片 OCR 效果取决于 PP-OCRv5 对设计稿渲染质量（运行时已装）。
- LLM 覆盖率过滤可能丢弃部分合法改写条目（80% 门槛），候选可人工补录。
- 回滚：重发 1.0.38.12 制品。

## 发布执行与核验（Owner 手动执行 deploy-13813.cmd，12:29 完成）
- 后端 R-1 蓝绿全 11 阶段 [OK]，R-1 发布完成（`logs/deploy-backend.20261112_122442.log`）。
- 核验：8080 直连与经 Nginx `/api/health` 均 **1.0.38.13 UP**（buildTime=12:21:26 与制品一致）；**运行 jar SHA-256 = 候选 jar**（aba306d6…5afe8）；SPA 200；deployment.md 表行已由脚本写入（12:29:02 | 1.0.38.13 | 成功）。
- 真机验收项：上传「日程-6.15 与艾同行苏州站.pptx」（31 条回归）与「144-日程(1).pdf」/「妇愈芬芳日程.pptx」（图片/扫描件首次 PP-OCRv5 提取）。
- 回滚：重发 1.0.38.12 制品（`builds/1.0.38.12-build-20261112_105450/backend/target/expert-system.jar`）。
