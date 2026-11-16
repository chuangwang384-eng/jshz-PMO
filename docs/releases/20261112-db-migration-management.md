# 运维变更记录：数据库迁移版本管理 + init.sql 基线重建（2026-11-12）

> 仅数据库结构基线与运维脚本，**无应用代码变更、不涉及版本发布**（`version.txt` 仍为 1.0.38.18）。

## 1. 变更前的问题
| # | 问题 | 证据 |
| --- | --- | --- |
| 1 | 没有迁移版本表：哪个脚本在哪个环境执行过、执行到第几步，全靠人工记录 | 库中无 `schema_migration`；pom 无 Flyway/Liquibase |
| 2 | `init.sql` 与现状严重脱节：只有 6 张表（2026-10-20 基线），生产实际 81 张业务表 | `init.sql` 151 行 / 6 个 `CREATE TABLE` |
| 3 | 文档口径漂移：`deployment.md` 验收项写 6 表 / 50 脚本 / 74 表，与实测不符 | 实测 94 表（81 业务 + 10 `*_bak_*` + 3 `mig_*`） |

## 2. 变更内容
| 项 | 说明 |
| --- | --- |
| `schema_migration` 版本表（库 + init.sql 均含） | `script`（唯一）/ `checksum`(SHA256) / `status`(APPLIED·BASELINE·FAILED) / `applied_at` / `applied_by` / `note` |
| `scripts\db-migrate.ps1`（新增） | `-Mode Status` 看已登记/待执行/漂移；`-Mode Apply` 顺序执行并登记（有漂移即中止）；`-Mode Bootstrap` 接管历史库/新环境回填台账。凭据取环境变量或 `start-backend.bat`，日志 `logs\db-migrate-*.log` |
| `database\init.sql`（重建） | 按生产库导出：81 张表全量结构 + 12 张字典/权限种子（604 行）+ 初始管理员 admin；`IF NOT EXISTS`、无 DROP、无 AUTO_INCREMENT 计数器；排除 `*_bak_*` 与 `mig_*` |
| `database\README.md`（新增） | 三块目录职责、幂等要求、`db-migrate` 用法、铁律（含"新环境用 Bootstrap 不用 Apply"） |
| 文档同步 | `docs/deployment.md` 第 2 节重写（新环境/接管/日常变更/版本表）+ 验收项改为 81 表与台账校验；`docs/architecture.md` 补库表实测口径 |

## 3. 台账回填结果（生产库 `expert_system`）
- `APPLIED` **60** 条（`database\migration\` 全部历史脚本）+ `BASELINE` **1** 条（`init.sql`）；
- 其中 7 条 `applied_at` 取自真实执行日志：`20261109_supplier_price_quote_date`(11-09 10:41)、`supplier_review_open`(10:55)、`project_basic_fields`(11:30)、`project_schedule_stage`(12:57)、`compliance_notebook`(11-10 17:15)、`storage_manage_permission`(11-12 15:42)、`storage_orphan_scopes_permission`(16:03)；
- 其余 53 条为回填：时间取脚本落盘时间，`note='回填：接管历史库…'` 已标注非精确执行时刻；
- `-Mode Status` 复核：**已登记 60 / 待执行 0 / 漂移 0**。

## 4. 结构一致性验证（临时库）
将 `init.sql` 在临时库 `expert_system_initcheck` 执行（去除建库语句、改 `USE` 目标），与生产库逐项比对：

| 维度 | 生产 | 临时库 | 差异 |
| --- | --- | --- | --- |
| 业务表 | 81 | 81 | 0 |
| 列定义（类型/可空/默认值） | 721 | 721 | 0 |
| 索引与键（列序/唯一性/类型） | 313 | 313 | 0 |
| 种子 | permission 117 / role 6 / role_permission 53 | 同 | 0 |

验证后临时库已删除；生产库仅新增 `schema_migration` 一张表（DDL `IF NOT EXISTS`）、无数据变更。

## 5. 风险与回滚
- `init.sql` 重建只影响新环境建库；生产未执行任何 DROP/ALTER（新增的 `schema_migration` 为纯新增表）。
- 回滚方式：`DROP TABLE schema_migration;` 并回退 `scripts\db-migrate.ps1`、`database\init.sql` 至上一提交；旧 `init.sql`（6 表）可从 git 历史取回。
- 已知取舍：`init.sql` **不含业务数据与用户口令哈希**，仅含 admin 与字典种子；需要完整数据时用 `backup\` 的 SQL（见 `docs/guides/2026-10-26-备份与恢复-演练文档.md`）。

## 6. 后续约定（已写入 database\README.md 与 deployment.md）
1. 变更先写 `database\migration\<yyyyMMdd>_<描述>.sql`（幂等），再 `db-migrate.ps1 -Mode Apply`；
2. 已执行脚本禁止再改内容（改动=DRIFT，Apply 会中止）；
3. 新环境：`mysql < database\init.sql` → `db-migrate.ps1 -Mode Bootstrap`（不要用 Apply）；
4. 每季度建议跑一次 `-Mode Status` 巡检，确认台账与库结构一致。
