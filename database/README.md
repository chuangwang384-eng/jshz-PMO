# database —— 数据库结构与版本管理

本目录是 `expert_system`（MySQL 8.x）的**唯一结构事实源**，分三块：

```text
database\
├─ init.sql          全量基线：81 张业务表 + 字典/权限种子 + 初始管理员（新环境第一步）
├─ migration\        增量迁移：60 个 <yyyyMMdd>_<描述>.sql，按文件名顺序演进
└─ testing\          测试夹具：隔离测试库专用建表快照（禁止对生产执行）
```

## 1. init.sql（全量基线）

- 2026-11-12 按生产库实际结构重建：表、列、索引、约束、字符集逐项与生产一致
  （校验方式：临时库执行后与生产对比 **81 表 / 721 列 / 313 索引**，差异 0）。
- 只含 `CREATE TABLE IF NOT EXISTS`，**无 DROP**、无 `AUTO_INCREMENT` 计数器，可重复执行。
- 种子数据（`INSERT IGNORE`，共 604 行）：`role` 6、`permission` 117、`role_permission` 53、
  `supplier_city` 349、`project_type` 6、`project_phase` 7、`project_type_phase_rel` 35、
  `project_phase_template_rel` 9、`project_template` 6、`supplier_type` 7、
  `customer_relation_type` 7、`approval_config` 1；另加初始管理员 `admin`（密码 `111111`，部署后立即改密）。
- **不含**：业务数据（专家/项目/客户/文档/日志）、用户口令哈希、`*_bak_*` 历史备份表、`mig_*` 迁移标记表。

## 2. migration（增量迁移）

- 命名：`<yyyyMMdd>_<动词>_<对象>.sql`，例：`20261112_add_storage_orphan_scopes_permission.sql`。
- 必须**幂等**：`CREATE TABLE IF NOT EXISTS`、`INSERT IGNORE`、ALTER 前先查 `information_schema`。
- 一个发布对应一个脚本；脚本一旦执行过就不要再改内容（改动会被 `db-migrate` 判为 DRIFT）。

## 3. testing（测试夹具）

- `dashboard_baseline.sql`、`project_config_baseline.sql`：给 `scripts\test-dashboard.ps1` /
  `test-project-config.ps1` 在隔离库（`pc_acceptance` / 独立 datadir / 13317 端口）建表用。
- **禁止**指向生产库：文件内无 `USE`、无 `DROP`、无业务数据。

## 4. 版本表与命令行

`schema_migration`（位于 `expert_system` 库）：`script`（唯一）、`checksum`（SHA256）、
`status`（APPLIED / BASELINE / FAILED）、`applied_at`、`applied_by`、`note`。

```powershell
.\scripts\db-migrate.ps1 -Mode Status      # 已登记 / 待执行 / 漂移
.\scripts\db-migrate.ps1 -Mode Apply       # 执行所有待迁移并登记（有漂移则中止）
.\scripts\db-migrate.ps1 -Mode Bootstrap   # 接管历史库：现有脚本 + init.sql 登记为已执行
```

当前状态（2026-11-12）：`APPLIED` 60 + `BASELINE` 1，待执行 0、漂移 0。

## 5. 铁律

1. 任何结构/权限/字典变更 → 先写 `migration\` 脚本，再 `db-migrate -Mode Apply`，禁止直接改生产库不留脚本。
2. 新环境：`mysql < database/init.sql` → `.\scripts\db-migrate.ps1 -Mode Bootstrap`（**不要**用 Apply，否则 60 个历史脚本会重放）。
3. 生产命令一律经 `scripts\apply-migration-*.ps1` 或 `db-migrate.ps1`（带凭据注入、日志、失败即停）。
