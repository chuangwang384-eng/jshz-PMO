# 发布记录：合规「情况事项」看板 + LLM 重要事项自动标记（1.0.37.33）

- 日期：2026-11-10
- 状态：**已发布生产（13:15，后端 R-1 蓝绿 + 前端 graceful，无迁移），发布后核验通过；页面真机验收待 Owner**
- 类型：功能迭代；**无数据库迁移**（priority 列 VARCHAR(16) 容纳 HIGH，无 DDL）

## 3.1 发布执行与发布后核验（13:14–13:16 实测）
- `temp\deploy-13733.cmd`：后端 R-1 11 阶段蓝绿（影子 8081 探测过 → 稳态化 → **R-1 发布完成**，日志 `logs/deploy-backend.20261110_131407.log`）；前端 graceful（ReleaseId `1.0.37.33-compliance-llm-situation-20261110`，DEPLOY_EXIT=0）。
- 核验：`/api/health` 经 Nginx **1.0.37.33 UP**（buildTime=13:03:26、gitShort=a417c51 与制品一致）；root 已切新 release 且线上 index 主 bundle=磁盘哈希（index-B_V8K3UJ.js）；SPA 200。

## 1. 需求口径（Owner 2026-11-10 澄清）
- 「情况事项」= 文档解析产生的合规待办事项 → 在「模板维护」页签提供其**查看/草稿确认/手动新增**入口。
- 解析强化 = **用本地 LLM（Ollama）自动识别重要事项并标记**。

## 2. 实际变更
### 后端
- `OllamaService`：新增通用 `completeJson(prompt)`（沿用现有 Ollama 调用模式，失败返回 null）与 `complianceImportance(List<String> titles)`——单次 prompt 列出全部草稿标题（截 80 字），判定口径写入 prompt：资金/付款/合同/法务/发票/时限截止/监管/处罚/证件/资质/审计/上报为重要，一般事务准备为普通；输出 `flags[]` 与入参等长，解析失败返回 null。
- `ComplianceService.parseAndCreateDrafts`：解析产物在入库匹配前调 `markImportantByLlm()`——重要者 `priority=MEDIUM→HIGH`、remark 追加「LLM标记：重要」；Ollama 未配置/失败/超时**静默回退**（全部保持 MEDIUM），异常不阻断解析主流程；日志只记条数。
### 前端
- `EnterpriseCompliance.vue`：维护页签（knowledge:maintain）在可视化模板下方挂载 **MaintenanceBoard**（合规事项（情况事项）卡片）——此前该看板组件功能完备但从未被任何页面挂载；手动新增（走 createItem 直接发布）、解析草稿确认、按文档分组、优先级筛选全部现成。
- `MaintenanceBoard.vue`：优先级 HIGH 渲染红色「重要」徽标（两处表格列）。

## 3. 验证（实测）
| 验证项 | 结果 |
| --- | --- |
| 回退回归单测（新增 ComplianceLlmMarkTest） | Ollama 不可用时草稿 priority/remark 零变化、不抛异常（2 用例通过） |
| 后端全量 mvn test | 全绿 EXIT=0（既有合规回归含 ComplianceAssignmentRegressionTest 均过——LLM 关闭时行为与现状一致） |
| 前端构建 | 25.8s，is-not-exported=0 |
| 制品 | jar build.version=1.0.37.33；前端主包 index-B_V8K3UJ.js 含版本号、EnterpriseCompliance chunk 含「合规事项（情况事项）」 |

## 4. 风险与回滚
- LLM 误判只影响 priority 标记（草稿确认时可人工改），不改事项内容；Ollama 停机时行为与现状零差异（qwen2.5:7b 质量有限，误标可人工修正，后续可换更大模型）。
- 回滚：revert 提交；无迁移、无配置变更。

## 5. 遗留
- V4 模板维护的原文提取候选（ComplianceWorkspaceSourceService）未接 LLM 分类（本轮口径只覆盖 V3 解析链）；模板节点/解析事项的独立"类别"字段未做（Owner 选择了轻量口径）。
