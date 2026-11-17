# 报价文件待办与估算预览修复

任务 20261117-budget-quote-phase-gate，主责 Codex，2026-11-17。状态为已完成待验收。开发分支 `codex/budget-quote-phase-gate`，基线 `main@feb0db3`，开工时工作区干净。

- 预览列表原先继承 Element Plus 复选框组的零行高。现为费用名称、调整值和依据设置独立行距和换行，滚动容器限制总高度。
- 线上和线下项目在预算阶段都有「生成报价文件」待办。点击待办进入对客报价区。内部确认后生成 Excel，挂到当前阶段资料。至少一个属于本项目且能读取的报价文件可完成待办。普通附件、单独内部确认、文件找不到时均无法由预算阶段推进。客户确认继续独立登记。
- `ProjectDetail.vue` 和报价组件展示待办并定位入口。后端 `ProjectService` 共用详情和阶段推进校验，`ProjectDocMapper` 查询报价版本对应的活动阶段资料。无数据库 DDL、迁移、权限、路由或配置变更。接口 `missingRequiredTemplates` 增加一个业务项，字段结构沿用原契约。
- 验收时打开预算阶段，确认「生成报价文件」待办与推进禁用同时出现。完成内部确认，生成报价 Excel，检查待办和推进按钮更新。直接调用推进接口，在文件生成前应收到业务拒绝，生成后应进入日程上传。用长标题产品行在窄屏预览，检查三行文字不相交。
- 回滚可恢复此前的前后端部署制品，无数据库变更。当前未发布至生产。
- 2026-11-17 ZCode 复核：代码审查通过（SQL 字段与 `project_budget_quote`/`project_doc` 表结构核对一致；阶段名硬编码沿用 `ProjectBudgetQuoteService:184` 既有约定）。实测 `mvn test -Dtest=ProjectAccessRegressionTest`（注入 JWT_SECRET、跳过 build-info）通过；`npm run build` 通过，`is-not-exported` 告警 0 条。浏览器人工验收与生产发布未执行。
