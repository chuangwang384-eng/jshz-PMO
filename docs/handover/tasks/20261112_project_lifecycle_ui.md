# 2026-11-12 项目生命周期界面与日程解析评估

主责 Codex。状态为已完成待验收。基线分支为 codex/personal-dashboard-v11，HEAD 为 a417c51，工作区已有其他未提交改动。

- 项目详情移除阶段任务独立卡片。阶段推进、回退仍通过项目页头现有入口操作。生命周期使用可键盘操作的阶段按钮，区分当前、已完成、未开始与正在查看，宽度不足时支持横向滚动。
- 改动集中在 frontend/src/views/project/ProjectDetail.vue。此次无需数据库迁移、接口或运行时配置调整。
- 执行 npm --prefix frontend run build -- --outDir <独立产物目录>，构建通过，耗时 42.18 秒，有 Vite 大分块提示。git diff --check 通过。浏览器复验、生产发布和日程解析性能压测未执行。
- 本机 Ryzen 7 3750H、GTX 1650 4 GiB、约 13.9 GiB 可见内存。ScheduleParseService 依赖文本层与规则，弱文本 PDF 才 OCR，最多前 3 页。规则候选少于 2 个才走 Ollama，日程提示词最多接收前 4000 字符。混合 PDF、多页扫描与规则部分命中都有漏项风险。
- 日程评估建议用 RapidOCR/ONNX Runtime 在 CPU 对比 PP-OCRv5 mobile 与 Tesseract，再用短片段对比现用模型与 Qwen3-4B Q4。分别记录专家姓名及角色精确率、召回率、冷暖耗时与资源峰值。详见 [RapidOCR](https://github.com/RapidAI/RapidOCR)、[PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR)、[Qwen3](https://github.com/QwenLM/Qwen3)。
- 接手先对项目详情做单阶段、多阶段和窄屏复验，检查阶段切换及页头推进、回退入口。回滚时撤回本次 ProjectDetail.vue 差异。若随后发布，保留上一前端快照用于回切。

## 生产发布与真实日程复测

- 执行 node temp/project-lifecycle-edge.mjs。真实 Edge 加载候选构建，隔离 API 数据覆盖桌面、480px、单阶段、多阶段、键盘切换和页头推进、回退入口。通过。
- 生产前端快照 1.0.38.8-project-lifecycle-20261112 已经 Nginx 平滑切换，master PID 7696 保持。线上 index 哈希与快照一致，/project/55 和 ProjectDetail-hQwm3t7Z.js 返回 200，/api/health 为 1.0.38.8 UP。旧快照为 1.0.38.8-maintenance-no-search-20261112，回滚配置见 frontend/release-metadata/1.0.38.8-project-lifecycle-20261112.rollback.conf.txt。发布日志见 logs/deploy-project-lifecycle-20261112.log。
- 真实样本为「10.26【日程】聚艾同行-乳腺癌肿瘤-濮阳会议日程(1).pptx」。通过与生产 jar SHA-256 一致的 1.0.38.8 制品离线调用 ScheduleParseService.extract 和 ruleParse。共提取 12 条表格行、542 个文本字符，规则返回 25 名候选。炕景超、李志峰、康厚香均出现在原生文本的环节主持行，均未进入规则候选。候选数达到兜底阈值，所以实际链路不会调用 Ollama。离线探针不涉及专家档案匹配、日程上传写入或生产数据。
- 下一步在正文文本补提明确标注的环节主持，将其按现有 HOST 角色入候选，并用原文件验证三人补齐和原有候选保留。本次没有修改或发布解析器。
