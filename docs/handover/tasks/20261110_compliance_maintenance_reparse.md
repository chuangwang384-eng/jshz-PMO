# 合规事项维护与文档重解析交接

2026-11-10 · 主责 Codex · 生产基线 1.0.37.37 / a417c51

## 范围与交付

维护看板固定按文档折叠，搜索与新增放在顶部。新增先选已上传文档。展示事项标题和内容，组内保留编辑、撤下和清空。解析区可主动解析、修改或删除候选并保存发布。清空重解析确认显示文档名和有效条数；解析成功后撤下旧发布并替换候选，失败保留现有事项。规范文档永久删除沿用现有事项清理，项目快照查询返回来源已删除状态。

## 文件与风险

MaintenanceBoard.vue 和 EnterpriseCompliance.vue 调整页面；ComplianceItemController 与 ComplianceService 新增解析及撤下操作。永久删除与事项写入通过文档行锁协调。项目待办 Mapper 补充来源存在性判断，ProjectDetail.vue 展示标记。沿用现有维护权限、OCR、本地 LLM 和数据结构。工作区在本轮开始前已有未提交改动。无新增数据库迁移。

下一步使用隔离浏览器上传真实文档后验证编辑、保存、清空重解析与项目来源标记。生产发布需独立安排。应用回滚恢复上一版制品，已撤下事项保留审计历史。

## 验证

- 在独立构建目录运行 Maven 的 ComplianceMaintenanceTest、ComplianceLlmMarkTest、ComplianceAssignmentRegressionTest、ComplianceV4EditingRegressionTest，共 12 项，最终退出 0。覆盖空解析保留旧事项、成功重置撤下旧事项并创建候选。
- ComplianceNotebookAcceptanceIT 使用本机隔离 MySQL 13306/acceptance 执行 2 项，均通过。覆盖撤下及来源文档删除后项目待办快照和来源标记。
- npm --prefix frontend run build -- --outDir <LOCAL_PATH> 通过，git diff --check 通过。真实浏览器上传、主动解析和看板交互尚未执行。
- 未部署生产，未操作生产数据。

## 隔离浏览器与生产发布结果

- frontend/tests/compliance-maintenance-live.mjs 在真实 Edge 中上传真实 PDF，经独立 MySQL、Spring 和 Vite 预览完成主动解析、编辑保存、项目推送、清空重解析与文档永久删除。删除后看板和员工记事本的来源条目消失，项目待办逐字段保持原样并新增 sourceDeleted 标记。退出 0。日志 logs/compliance-maintenance-edge-20261110.log。
- 1.0.37.38 后端蓝绿与前端平滑切换完成，提权发布入口退出 0。直连和 Nginx 均返回 1.0.37.38 UP。合规页 HTTP 200，未登录重解析预览 HTTP 401。线上首页、EnterpriseCompliance-CfrI77wD.js 和 ProjectDetail-xQi-uXN8.js 哈希匹配候选。
- 无生产迁移，业务写入和永久删除只在隔离环境执行。发布日志 logs/compliance-13738-production.log。1.0.37.37 后端制品和前端快照保留用于回退。

## PDF 解析覆盖修复

- 基线 1.0.37.38，主责 Codex。旧解析以模型的任意非空结果作为完整候选，正文足够长时跳过结构化 PDF 提取。新解析按页面原文及表格行生成候选，保留完整内容，模型只兜底。结构化结果不完整时使用已有正文补充并提示。
- 独立 Maven 构建目录，使用真实基金会 PDF 和本地 OCR。ComplianceLlmExtractTest、ComplianceMaintenanceTest、KnowledgeDocStreamingParseTest 共 9 项通过。真实 PDF 产生 33 条候选，测试逐项命中系列会立项限制和 70%、50%、30% 三条上限。候选仍需维护人核对。日志 logs/compliance-pdf-coverage-20261110.log。
- 仅本地代码与文档，未重新解析生产文档或发布。下一步在隔离浏览器复验后再发布，生产由维护人主动重解析该文档。无需数据迁移；应用回退恢复旧制品。

### 段落粒度回归

- 原 PDF 正文同段行距约 31 点，段间约 62 点。结构化提取使用 PDF 文字坐标与字号识别段落边界，统一换行后保留段内换行。表格按整行生成候选。
- 真实样本验证四条立项与比例限制合为一项；劳务专家与听众、线上回放、专家讲课费、主持费用各独立一项。9 项相关测试退出 0。日志 logs/compliance-pdf-paragraphs-retest-20261110.log。
- 部分表格比较符号仍需对照原件人工核对。未部署、无迁移。

- 1.0.37.39 后端候选已构建且预演通过，与 1.0.37.38 的运行时差异仅解析类、PDF 提取类及版本资源。自动执行策略拒绝启动管理员发布进程，生产仍为 1.0.37.38 UP。管理员入口 temp/apply-compliance-13739.ps1；无迁移，回退使用 1.0.37.38 制品。

- 管理员已执行 1.0.37.39 后端蓝绿发布，脚本退出 0。独立复核直连与 Nginx 均为 1.0.37.39 UP，运行 JAR 哈希匹配候选。前端继续使用 1.0.37.38 制品，无数据库迁移。日志 logs/deploy-backend.20261110_190853.log；回退制品 backend/releases/1.0.37.39-20261110_190933/previous-expert-system.jar。

### 2026-11-11 标题与表格 OCR 质量

- 基线 1.0.38.1。标题由 ComplianceParseService 按段落主题与义务关键词生成，保留原段落分组。ComplianceOcrTable 对疑似乱码单元格重识别，失败则标注核对原文。沿用本地 Tesseract。
- 验收覆盖真实基金会 PDF 的五项分组、专家与听众比例和线上会议回放的主题标题，以及截图乱码的过滤与有效金额比较符号保留。
- 无迁移、未部署。上线前在隔离浏览器重新解析实际文档，复核标题、金额与原件。回退使用上一版后端制品。

- 复测退出 0。ComplianceLlmExtractTest、ComplianceMaintenanceTest、KnowledgeDocStreamingParseTest 共 10 项通过。真实基金会 PDF 仍生成 17 条候选，截图乱码回归保留正常比较符号并标记不可辨行。证据 logs/compliance-ocr-quality-clean-20261111.log，使用独立目录 clean test 确认新增用例确实执行。

- 1.0.38.2 已完成生产后端蓝绿发布，直连和 Nginx 健康均返回 UP，线上运行 JAR 哈希匹配。前端使用 1.0.38.1 制品。无迁移。真实 PDF 验证在本地完成。发布日志 logs/deploy-backend.20261111_103330.log，回退制品 backend/releases/1.0.38.2-20261111_103415/previous-expert-system.jar。
