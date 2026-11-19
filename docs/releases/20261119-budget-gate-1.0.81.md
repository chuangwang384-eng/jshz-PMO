# 发布记录：1.0.81 会议预算口径落地（A16/D3）

| 项 | 内容 |
|---|---|
| 版本 | 1.0.81（version.txt，commit `e3ba372`） |
| 发布日期 | 2026-11-19 |
| 类型 | 前后端（无数据库迁移） |
| 决策来源 | A16/D3：负责人 2026-11-17 拍板四项口径 |

## 1. 变更内容（实质 diff：8 文件 +48/-7）

1. **阶段推进预算门槛（全部项目类型）**：`ProjectService.changePhase` 推进分支在必填材料校验后新增「已确认预算单」校验（project_budget.status=CONFIRMED），未确认报「预算未确认：请先在『会议预算制作』阶段保存并确认预算单后再推进阶段」；**回退不拦截**（沿用既有规则）。
2. **确认即覆盖财务金额**：`ProjectBudgetService.confirm` 确认（含重复确认/已确认单再确认，幂等）后调用新增的 `ProjectService.overrideBudgetAmount`，以预算单 total_amount 覆盖 `project.budget_amount`。
3. **线上面板「需要主视觉设计」勾选**：`ProjectBudgetPanel.vue` 线上模式新增勾选（默认勾选=行为不变），取消后「设计/主视觉」类费用项自动取消点选且不可勾选；状态随 estimateInputs 持久化（线上亦持久化，原为 null）。
4. **过渡提示（重要）**：上线即生效——**所有存量未确认预算的项目均无法再推进阶段**，需先确认预算单；如某项目确属免预算，当前版本无豁免入口（口径未含），需出临时确认单或后续补「免预算」标记功能。

## 2. 验证
- 后端 `mvn test`：217 项，0 失败；唯一 1 个错误为既有环境性失败（测试环境未注入 JWT_SECRET，与本次无关，历史记录同）；ProjectAccessRegressionTest 10/10（新增预算桩）、ProjectBudgetServiceTest 含新增「确认覆盖财务金额」断言。
- 前端 `npm run build` 通过（32.37s）。
- 发布：蓝绿（logs/deploy-backend.20261119_160827.log，退出码 0，UAC 提权）+ 前端快照 `1.0.81-budget-gate-20261119`（graceful，master PID 未变）。
- 核验：/health=1.0.81 UP（gitShort=e3ba372）；线上入口含 1.0.81；`ProjectDetail-DdTEXEiG.js`（含主视觉勾选）线上 200；部署 jar 内含「预算未确认」拦截文案；四路由 200；回滚点齐备（backend/releases/1.0.81…/previous + rollback.conf）。

## 3. 待业务验收
- [ ] 新建项目走完 预算制作→确认→推进 全链（门槛生效、确认后项目财务金额被覆盖）；
- [ ] 未确认预算点推进：出现明确拦截文案；
- [ ] 线上项目勾选/取消「需要主视觉设计」：设计类费用项联动；
- [ ] 存量在途项目处置：按需补确认预算单。

## 4. 回滚
后端重发 1.0.80 jar；前端按 rollback.conf 指回 1.0.80-framed-portrait-20261119。门槛逻辑纯代码无数据迁移，回滚即恢复旧行为。
