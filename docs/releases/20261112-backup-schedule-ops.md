# 运维变更记录：自动备份任务恢复 + 备份保留策略修复（2026-11-12）

> 仅运维脚本与计划任务，**无应用代码/制品变更，不涉及版本发布**（`version.txt` 保持 1.0.38.18）。

## 1. 发现的问题
| # | 问题 | 证据 |
| --- | --- | --- |
| 1 | 文档记载的周备份任务 `jshzPMOBackupWeekly` **实际不存在** | `schtasks /query /tn jshzPMOBackupWeekly` → `ERROR: The system cannot find the file specified` |
| 2 | 最近备份停在 2026-11-06 13:42（6 天无备份） | `backup\` 下最旧/最新均 11-05~11-06 |
| 3 | 保留策略未收敛，`backup_*` 有 6 份（上限 5） | 11-06 12:06 日志：`DeleteDirectory ... 无法找到指定文件` ×9；11-06 13:42 那次以 `Keep=1000` 运行 |
| 4 | 两份手工备份目录不在任何策略内 | `backup\frontend_release_1.0.29_before_dashboard_fix_20261105`（77.6 MB）、`backup\frontend`（3.2 MB） |

## 2. 变更内容
| 项 | 说明 |
| --- | --- |
| `scripts\backup.ps1` 保留策略加固 | 只匹配 `backup_yyyyMMdd_HHmmss`；删除前 `Test-Path` 判存在；删除后复核是否真的消失；未超上限时输出“现有 N 份，未超过上限”；`DeleteDirectory` 对已不存在目录不再记成失败 |
| `scripts\register-backup-schedule.ps1`（新增） | 注册/删除 `jshzPMOBackupWeekly`：每周日 02:00，SYSTEM，`backup.ps1 -Mode Backup -Keep 5`；支持 `-DryRun` / `-Unregister` / `-Keep` / `-Time` |
| 计划任务重新注册 | `STATE=Ready`，`NEXT=2026-11-15 02:00`，`USER=SYSTEM`，`RUNLEVEL=Highest` |
| 手工目录归档 | 上述两份移入 `builds\archive\legacy-manual-backups-20261112\`（archive 不被任何清理脚本删除，可随时人工处理） |
| 立即补一次备份 | `backup_20261112_170431`，产物 4 个（SQL/照片库/照片库_tmp/知识文档库），保留策略把最旧 2 份（11-05 两份）移入回收站，现为 5 份 |

## 3. 执行记录
```text
[2026-11-12 17:04:31] 备份目录: backup\backup_20261112_170431
[2026-11-12 17:04:39] manifest.txt 已写入；耗时 7.3 秒，产物 4 个
[2026-11-12 17:04:40] 保留策略: 已移入回收站 backup_20261105_162337
[2026-11-12 17:04:41] 保留策略: 已移入回收站 backup_20261105_153153
[2026-11-12 17:04:41] Backup 完成
```
`-Mode Verify`：SQL、照片库.zip、照片库_tmp.zip、知识文档库.zip 四项 SHA256/CRC 全部通过。

## 4. 当前状态
| 项 | 值 |
| --- | --- |
| `backup_*` 份数 | **5**（170431 / 120546 / 101945 / 083522 / 134251） |
| 备份任务 | Ready，NextRun 2026-11-15 02:00（SYSTEM） |
| `backup\` 手工残留 | 仅 3 个散落文件：`expert_system_full_20261029_094000.sql`、`sys_user_backup_20261021.csv/.xlsx`（早期手工导出，未纳入策略） |
| 归档区 | `builds\archive\legacy-manual-backups-20261112\`（80.8 MB，非删除，可人工处理） |

## 5. 回滚
- 停用周备份：`.\scripts\register-backup-schedule.ps1 -Unregister`（管理员）。
- 回退脚本改动：`scripts\backup.ps1` 的保留策略仅影响清理行为，回退不影响备份产物本身。
- 归档的手工备份如需恢复：移回 `backup\` 即可（`frontend`、`frontend_release_1.0.29_...`）。

## 6. 遗留与建议
1. 三份散落手工文件建议确认后归档/删除，使 `backup\` 只保留脚本产物（便于保留策略与巡检）。
2. 备份目前与数据同盘（D 盘），建议后续加一份异机/异地副本（当前 5 份约 150 MB，成本低）。
3. 建议每月清理任务（`jshzPMOPruneMonthly`）与周备份任务错开已满足（1 日 03:30 vs 每周日 02:00）；如遇日期重合，备份耗时仅数秒至数十秒，冲突风险低。
