# 1.0.96 发布记录：收费项类目可编辑/新建 + 参考单价列 + 全字段可编辑

- 日期：2026-11-21
- 提交：`18688bb`（BudgetFeeConfig.vue + 1.0.96 版本号）
- 类型：纯前端 graceful（ReleaseId `1.0.96-fee-edit-catalog`，后端保持 1.0.95）

## 1. 需求（Owner）

1. 新增收费项处可以新增类目（不止选已有 6 类）。
2. 可编辑表格中画框内容（类目列、用途与默认值列）。
3. 每条收费项都可以被编辑。
4. 「用途与默认值」列直接改为「参考单价」。

## 2. 改动（frontend/src/views/admin/BudgetFeeConfig.vue）

| 项 | 内容 |
| --- | --- |
| 类目列 | 由只读文本改为可编辑下拉（el-select filterable allow-create default-first-option），可选已有类目或输入新建类目 |
| 新增弹窗类目 | 同样改为 allow-create 下拉，placeholder「选择已有类目，或输入新类目名称」 |
| 类目选项 | 新增 catalogOptions 计算属性 = 内置 6 类 + 数据中已出现的自定义类目（新建类目后自动进入选项与筛选） |
| 「用途与默认值」列 | 改名「参考单价」，去掉用途徽标（数量增量/估算参数/参考单价 badge），直接编辑参考单价输入框 |
| 单位列 | 去掉 quantityRules/estimateRuleMeta/STAFF_DAY 特判（这些无类目键已在 1.0.95 删除），恒为可编辑输入框 |
| 弹窗「默认值(元)」 | 标签改「参考单价(元)」 |
| 清理 | 移除已不用的 quantityRules/estimateRuleMeta 导入与 .fee-default 样式；更新页头/页签文案 |

后端无需改动：saveFeeStandards 早已接受任意 catalog 字符串（1.0.88 扩列），自定义类目直接落库。

## 3. 验证证据（实测，王创管理员会话）

| 项 | 结果 |
| --- | --- |
| 前端构建 | 通过（is-not-exported 0） |
| graceful 发布 | bundle v1.0.96，探针 200×3 |
| 列改名 | 「参考单价」列存在、「用途与默认值」消失 |
| 类目可编辑 | 数据行类目列为 combobox（首行显示「电子物料」可改） |
| 新建类目端到端 | 弹窗输入新类目「测试新类目」+ 项「验收测试项XYZ」(100/元场) → 确认置顶（141→142）→ 保存入库（DB 实查 id205 catalog=测试新类目）→ 删除清理（DB 恢复 170 条 0 自定义） |
| 删除复验 | 删除确认弹窗正常、删除成功 |

## 4. 回滚

- 前端：`1.0.96-fee-edit-catalog.nginx-before.txt` 存旧 root（1.0.95-fee-cleanup-skeleton），改回 reload。
- 无数据库变更。
