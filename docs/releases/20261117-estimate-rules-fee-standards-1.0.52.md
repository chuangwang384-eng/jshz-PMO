# 发布记录：1.0.52 估算规则并入收费标准配置

| 项 | 内容 |
|---|---|
| 版本 | 1.0.52（version.txt 唯一真源，commit `58c5966`） |
| 发布日期 | 2026-11-17 |
| 类型 | 前后端 + 数据库种子迁移 |
| 决策来源 | 挂起决策 A1 / 需求提案 D1：负责人 2026-11-17 批准「估算金额并入 feeStandards」 |
| 迁移 | `database/migration/20261117_estimate_rules_fee_standards.sql`（幂等 INSERT IGNORE，5 行 EST_* 种子，无 DDL） |

## 1. 变更内容

对客报价估算规则从前端硬编码常量迁入管理员「收费标准配置」（project_budget_fee_standard）：

1. **数据库**：新增 5 个收费键（scene=OFFLINE）：EST_TEA_RATIO（茶歇比例 70%）、EST_FLIGHT_FARE（机票 2000 元/人）、EST_PLATFORM_FIRST（一线城市 200 元/人）、EST_PLATFORM_OTHER（其他城市 100 元/人）、EST_HOTEL_ROOMS（默认预留 2 间）。`init.sql` 基线同步并入。
2. **前端估算逻辑**（`budgetEstimate.js`）：估算改读 feeStandards 的 EST_* 配置，`ESTIMATE_RULES` 常量降级为接口缺失/未启用时的兜底（值与种子一致）；茶歇改用百分比直算（×70%/500 向上取 5），管理员可调 50–99（含小数时按 Math.ceil 取整行为不变）。
3. **管理页**（`BudgetFeeConfig.vue`）：EST_* 行显示「估算参数」徽标与固定单位，与「参考单价/数量增量」区分。
4. **测试**：`budgetEstimate.test.js` 新增配置覆盖+兜底回退用例（7/7 通过）。

后端零代码改动（收费标准接口/表结构本就通用）。

## 2. 迁移执行

- 正式执行：2026-11-17 14:57，幂等脚本直连执行（mysql 客户端，凭据经 common-credentials.ps1）。
- **台账说明**：`db-migrate -Mode Apply` 因历史漂移 18 项（挂起问题 A2，与本次无关）中止，按 1.0.47 发布先例手工执行并登记 `schema_migration`（status=APPLIED，checksum 与文件一致，applied_by=manual）。
- 执行后核验：5 行 EST_* 在库（name/value/unit/scene/is_active 逐项核对）；`check-schema-parity.ps1` 全部通过（init.sql ↔ 迁移 ↔ 生产库三方一致）。

## 3. 发布过程

- 构建：`build-all.ps1`（隔离目录 `builds\1.0.52-build-20261117_145421`，build-info 校验 version=1.0.52 gitShort=58c5966）。
- 后端：`release-backend.ps1 -JarPath <候选包>`，DryRun 11 阶段预演通过后 UAC 提权正式执行，蓝绿切换完成（日志 `logs/deploy-backend.20261117_145924.log`，退出码 0）。
- 前端：`deploy-frontend-graceful.ps1 -Apply`，快照 `frontend/releases/1.0.52-estimate-rules-fee-standards-20261117`，graceful root 切换，旧 assets 并入保留。
- 回滚点：`backend/releases/1.0.52-20261117_150002/previous-expert-system.jar`（1.0.51）；前端 `frontend/release-metadata/1.0.52-estimate-rules-fee-standards-20261117.rollback.conf.txt`（指回 1.0.50-code-review-refactor-20261117）。

## 4. 发布后核验（全部实测，2026-11-17 15:0x）

| 项 | 结果 |
|---|---|
| `/health` 8080 直连 | UP，1.0.52，gitShort=58c5966 ✓ |
| `/api/health` 经 Nginx | UP，1.0.52，与直连一致 ✓ |
| Nginx root | `frontend/releases/1.0.52-estimate-rules-fee-standards-20261117` ✓ |
| 线上 index 入口 | 引用 `index-COXwXdWb.js`，含 `v1.0.52` ✓ |
| 功能 chunk 线上可达 | `budgetEstimate-CS856ft5.js`（含 EST_TEA_RATIO/EST_FLIGHT_FARE/EST_HOTEL_ROOMS/EST_PLATFORM_FIRST）200 ✓；`BudgetFeeConfig-Bf_RVYl1.js`（含「估算参数」标签）200 ✓ |
| 旧 chunk 兜底 | 1.0.50 主 bundle 与旧 budgetEstimate chunk 均 200（已打开页面不断链）✓ |
| SPA 路由 | /project、/expert、/admin/budget-fee 均 200 ✓ |
| 接口鉴权 | `/api/project/config/budget-fee-standards` 未登录 401 ✓ |
| 前端测试 | node --test 7/7 通过 ✓ |
| 构建 | vite build 通过（26.36s）✓；后端为纯配置种子无代码变更，未跑 mvn test（构建校验通过）|

## 5. 待业务验收

- [ ] 管理员登录「收费标准配置」页（线下页签）：应看到 5 个「估算参数」行，修改保存后生效。
- [ ] 项目预算估算预览：管理员调整 EST_TEA_RATIO（如 80%）后，估算依据文案应显示「× 80%」且人数随之变化；停用 EST_FLIGHT_FARE 后机票估算回退 2000。
- [ ] 线上项目（无 EST 配置的旧数据路径）估算行为与 1.0.51 一致（兜底常量）。

## 6. 风险与回滚

- 风险低：估算属候选建议（人工确认后才入单），值与种子兜底一致，行为零变化起步。
- 回滚：后端重发 1.0.51 jar；前端按 rollback.conf 指回旧 root；EST_* 种子可保留（无害，旧前端不读取）。

## 7. 遗留

- 台账漂移 18 项（A2）依旧挂起，本次沿用手工登记先例，建议尽快批准重登记。
