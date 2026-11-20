# 1.0.97 发布记录：收费项明细（detail）可编辑 + 新增弹窗补明细字段

- 日期：2026-11-21
- 提交：`e74f6c9`（BudgetFeeConfig.vue + 1.0.97 版本号）
- 类型：纯前端 graceful（ReleaseId `1.0.97-fee-detail-edit`，后端保持 1.0.95）

## 1. 需求（Owner）

「收费项明细部分还是无法编辑，新增时也没有收费项明细部分」——指收费项的 detail（详细内容，原为名称下方只读提示文字）。

## 2. 改动（frontend/src/views/admin/BudgetFeeConfig.vue）

| 项 | 内容 |
| --- | --- |
| 表格明细 | 名称列下方原只读 hint-text（row.detail）改为可编辑 textarea（autosize 1~4 行，placeholder「收费项明细（详细内容）」）；列名改「收费项名称 / 明细」 |
| 新增弹窗 | 增加「收费项明细」表单项（textarea autosize 2~5 行，maxlength 500，可空） |
| 脚本 | openAddDialog 初始化 detail:''；confirmAdd 行数据带 detail；stripped() 早已含 detail（1.0.88） |

后端无需改动：saveFeeStandards 自 1.0.88 起 setDetail(str(row.get("detail")))，明细编辑直接落库。

## 3. 验证证据（实测，王创管理员会话）

| 项 | 结果 |
| --- | --- |
| 前端构建 | 通过（is-not-exported 0） |
| graceful 发布 | bundle v1.0.97，探针 200×3 |
| 表格明细可编辑 | 首行明细为 textbox「收费项明细（详细内容）」（值「资深设计原创设计及延展」可改） |
| 新增弹窗明细字段 | 弹窗含「收费项明细」textarea |
| 端到端 | 带明细新增「明细验收项ABC」(detail=明细测试内容ABC/100) → 保存 → DB 实查 id206 detail 正确 → 删除清理（DB 恢复 170） |

## 4. 回滚

- 前端：`1.0.97-fee-detail-edit.nginx-before.txt` 存旧 root（1.0.96-fee-edit-catalog），改回 reload。
- 无数据库变更。
