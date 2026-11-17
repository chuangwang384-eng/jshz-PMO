# 会议预算页面 UI 调整

任务 20261117-budget-ui-polish，主责 Codex，2026-11-17。状态为已发布待业务验收。分支 `codex/budget-ui-polish` 从 `main@b5d876d` 开始，发布提交为 `32b6f65`，生产后端 1.0.47。

- 项目详情的会议预算面板使用响应式估算表单、分组费用表格、独立合计栏和报价版本卡。原「外埠人数」显示为「外地参会人数」，用于住宿和大交通。无外地参会人员时填零，人数待定时可留空。
- 估算预览允许只填写已有信息，去掉两个人数字段间的比较。缺少参数的费用数量或参考单价留待补录，显式填写外地人数零仍按原规则预留两间。回归覆盖参会总人数 8、外地人数 8 和 10 的部分估算。
- 管理员「项目配置」收费标准页使用统一卡片与表格分组，以标签区分参考单价、数量增量和每场数量，显示保存状态。
- 修改 `ProjectBudgetEstimate.vue`、`ProjectBudgetPanel.vue`、`ProjectBudgetQuotes.vue`、`BudgetFeeConfig.vue` 和 `budgetEstimate.js`。复用现有全局令牌和卡片类。费用公式及报价流程沿用原口径，API、权限、路由、数据库和配置无变更。同步了架构说明和交接首页。
- `node --test frontend/src/views/project/components/budgetEstimate.test.js` 6 项通过。用本地模拟接口在 Edge 分别以 1200 px 和 430 px 渲染两页，验证无页面级横向溢出，估算表单从四列切到两列。两种宽度均实测只填参会总人数 8、外地人数分别为 8 和 10，可以打开预览并显示对应房间数量。截图保存在 `temp/budget-ui-*.png`。色值及新增样式类使用面检查和 `git diff --check` 通过。
- `npm run build -- --outDir ../builds/1.0.47-build-20261117092638-budget-estimate-validation` 通过，有包体积提示。
- 生产已切换到前端发布点 `1.0.47-budget-ui-20261117`。已通过正式站点静态资源核验，生产登录后的业务回归待做。下一步可在测试项目核对预算预览和项目配置切换。见 [前端发布记录](../../releases/20261117-budget-ui.md)。
