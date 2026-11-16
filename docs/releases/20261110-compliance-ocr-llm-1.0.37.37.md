# 发布记录：合规解析 OCR+LLM 提取 + 新增弹窗精简（1.0.37.37）

- 日期：2026-11-10
- 状态：**构建与验证完成，待发布**（`temp\deploy-13737.cmd`，无迁移）
- 类型：功能迭代；**无数据库迁移**

## 1. Owner 需求
1. 「新增事项」弹窗只保留 事项标题 + 要点内容；
2. 用 **OCR + LLM** 解析文档提取「事项标题/要点内容」（参考 GitHub 常见 OCR→LLM 结构化抽取范式）；
3. 解析产物可编辑、可删除、可保存；
4. **已保存（发布）的内容才在合规记事本展示**。

## 2. 实际变更

### 后端 `ComplianceParseService`
- 注入 `OllamaService` 与 `KnowledgeDocTextExtractor`。
- 非 Excel 链路重构：
  1. 文本来源：`textContent`（doc/docx/pdf 文本层）→ 为空/过薄（<40 字）时 **OCR 回退**：`textExtractor.extractStructured(path, fileName)` 拼接 blocks（覆盖 PDF 扫描件/表格页 OCR，此前该链路完全不用 OCR，图片/扫描件解析零产出）；
  2. **LLM 提取**：`ollamaService.completeJson` + 严格 JSON 契约 `{"items":[{"title","content"}]}`，标题 ≤30 字动宾结构、要点内容可空、按文档顺序、不编造、上限 100 条；
  3. **回退链**：Ollama 不可用/失败/产出空 → 原 `parseTextLines`（行为不变）；Excel 仍走 `parseExcel` 不变；
  4. 日志只记条数。
- `KnowledgeDocTextExtractor` 新增 `extractStructured(Path, String)` 重载（异步链直接读盘）。

### 前端 `MaintenanceBoard.vue`
- 新增/编辑弹窗**精简为两字段**（事项标题 + 要点内容），保存 payload 仅 `{title, content}`（后端 applyFields 部分更新，其余字段保持原值）。
- 草稿行新增：「编辑」「**保存发布**」（`confirmComplianceDrafts({documentId, itemIds:[id]})` 单条发布），保留删除/确认匹配/全部确认发布。
- 记事本展示条件已满足（查询过滤 `isDraft=0 && withdrawn=0`）——**未保存草稿不出现**。

## 3. 验证（实测）

| 验证项 | 结果 |
| --- | --- |
| 真实合规 PDF（`北京融和医学发展基金会合规要求.pdf`，525KB，文本层空→触发 OCR） | **LLM 从 OCR 文本提取 8 条**，标题为动宾结构、要点内容完整准确（如「线上会议回放要求 / 线上会议必须提供线上回放（讲者PPT严禁出现企业名称、药品商品名称）」「线下会劳务占比上限 50%」「住宿费标准 委800元/人北上广深…」），阶段自动归类 EXECUTION/OTHER 合理 |
| 新增单测 `ComplianceLlmExtractTest` | 3 用例通过（Ollama 不可用→null 回退；规则回退产物 isDraft=1/MEDIUM；空文本提示手动录入） |
| 后端全量 mvn test | **102 通过 / 0 失败 / 9 跳过** |
| 前端构建 | 29.7s，is-not-exported=0 |
| 制品 | jar build.version=1.0.37.37；前端 index-DUU-pIhK.js 含版本号、EnterpriseCompliance chunk 含「保存发布」 |

## 4. 已知限制
- qwen2.5:3b（本机）抽取质量尚可但条目可能偏细，草稿可人工编辑/删除后再发布。
- **纯图片文档（jpg/png 上传为知识文档）仍不走 OCR**（extractStructured 不含图片分支，既有行为）；如需要下轮加 ImageIO→ocrPage。
- 上次全量测试出现一次 `DashboardRulesTest.partialFailureAndDeadlineDoNotBecomeEmpty` 时序 flaky（单独运行与重跑全量均通过，与本改动无关，仅测 DashboardTasks 并发超时）。

## 5. 回滚
revert 提交；无迁移、无配置变更。
