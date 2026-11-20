# 1.0.94 发布记录：导出报价单即对客报价 + 允许编辑已确认费用明细

- 日期：2026-11-21
- 提交：`29f9452`（main）
- 制品：`builds/1.0.94-build-20261120_142315/`
- 类型：后端蓝绿 + 前端 graceful，**零数据库迁移**

## 1. 需求（Owner）

1. 「导出的报价单就是对客报价，删除下方对客报价的内容」——导出 Excel 即对客报价，移除独立的「对客报价」生成/客户确认/另存草稿区块。
2. 「允许用户编辑已保存的费用明细」——已确认（CONFIRMED）预算的费用明细也要能编辑。

## 2. 改动

| 模块 | 内容 |
| --- | --- |
| 前端 ProjectBudgetPanel.vue | ① 移除 `<ProjectBudgetQuotes>` 区块及 import（对客报价生成/客户确认/另存草稿）；② `editingLocked` 去掉 CONFIRMED 条件，仅 readonly/加载失败锁定 → 已确认明细可编辑 |
| 后端 ProjectBudgetService.save() | 移除「报价单已确认…新建草稿」拒写守卫；confirm=true 时调用 `overrideBudgetAmount` 同步项目财务登记金额（与 confirm() 一致，编辑后重新确认同样同步） |
| 后端 ProjectService.missingRequiredTemplates() | **移除预算阶段「生成报价文件」推进门禁**（导出即对客报价，不再要求单独生成报价文件）；保留 changePhase 的「预算 CONFIRMED」门禁 |
| 测试 ProjectBudgetServiceTest | `confirmedBudgetRejectsDraftOverwrite` 改写为 `confirmedBudgetAllowsEditAndReconfirm`（验证新契约：已确认可编辑、重新确认仍 CONFIRMED 并同步金额），非删除测试 |

## 3. 关键决策与影响（须负责人知悉）

- **推进门禁变化**：预算阶段推进原需「CONFIRMED 预算 + 已生成报价文件」两道；现仅保留「CONFIRMED 预算」。这是删除对客报价区块的必要配套（否则推进会因「缺少 生成报价文件」卡死）。导出报价单/成本单按钮随时可用，作为对客报价产出。
- **确认语义保留**：「确认报价」按钮仍置 CONFIRMED（推进门禁需要）；「保存草稿」置 DRAFT。编辑已确认单后若保存草稿会回到 DRAFT，需重新确认才能推进。
- 后端报价版本相关端点（project_budget_quote）保留未删，仅前端不再调用；历史报价文件/客户确认数据保留。

## 4. 验证证据（实测）

| 项 | 结果 |
| --- | --- |
| 后端编译 | 通过 |
| 预算测试 | ProjectBudgetServiceTest 12 + QuoteServiceTest 4 = 16/16 通过 |
| 前端构建 | 通过（is-not-exported 0） |
| 后端蓝绿 | 18 项 [OK]，/health=1.0.94/29f9452 UP |
| 前端 graceful（1.0.94-budget-edit-quote） | bundle 含 v1.0.94，探针 200×3 |
| 线上验收（项目 57，已确认预算，王创会话） | 对客报价区块已消失；导出报价单按钮保留；已确认状态下费用明细为可编辑输入框；保存草稿/确认报价按钮均可用；7 分节「从收费标准库添加」可用 |

## 5. 回滚

- 前端：`1.0.94-budget-edit-quote.nginx-before.txt` 存旧 root（1.0.93-config-ux），改回 reload。
- 后端：蓝绿归档旧实例按既有流程回退。
- 数据库：无结构变更；门禁/守卫为代码逻辑，回滚代码即恢复旧行为。
