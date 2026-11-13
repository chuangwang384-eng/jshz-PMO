# 运维变更记录：统一空间保留策略 + 月度计划任务（2026-11-12）

> 本次仅运维脚本与计划任务，**无应用代码/制品变更，不涉及版本发布**（`version.txt` 保持 1.0.38.18）。

## 1. 背景（实测）
| 目录 | 清理机制（变更前） | 实测占用 |
| --- | --- | --- |
| `frontend\releases` | 有手动脚本（默认保留 3），从未定时执行 | 136 个目录 / **31.2 GB** |
| `builds\` | 无 | 108 个目录 / **16.5 GB**（archive 另 10.5 GB） |
| `temp\` | 无 | 273 项 / 571 MB |
| `logs\` | `prune-logs.ps1` 由后端发布阶段 11 调用 | 正常 |
| `backend\releases` | `deploy-backend.ps1 -KeepVersions`（默认 5） | 5 个，正常 |

## 2. 变更内容
| 项 | 说明 |
| --- | --- |
| `scripts\prune-builds.ps1`（新增） | builds 保留最近 N（默认 5）个构建目录；`archive\` 永不删除；路径越界拒绝 |
| `scripts\prune-temp.ps1`（新增） | temp 按 目录/jar/script/log/other 分类各保留最近 N（默认 5）；2 天内修改跳过；文件名被 `docs\`/`scripts\` 引用则跳过 |
| `scripts\prune-all.ps1`（新增） | 统一入口：前端快照 → builds → temp → logs，日志 `logs\prune-<时间戳>.log`，任一失败退出码 1 |
| `scripts\register-prune-schedule.ps1`（新增） | 注册/删除月度计划任务（`schtasks /SC MONTHLY`；PS 5.1 无 Monthly 触发器） |
| `scripts\prune-frontend-releases.ps1` | 默认 `-KeepReleases` 由 3 调整为 **5**（与统一策略一致） |
| 计划任务 `jshzPMOPruneMonthly` | 每月 1 日 03:30，SYSTEM，`prune-all.ps1 -Keep 5` |

## 3. 安全护栏
- 前端：Nginx 当前 root、当前 root 的 `.rollback`、最近 5 个正式快照、路径必须在 `frontend\` 下。
- builds：`archive\` 跳过；`smoke-password.dpapi` 所在目录受 archive 保护。
- temp：近期文件（默认 2 天）与被 docs/scripts 引用的文件跳过。
- 所有脚本先可 `-DryRun` 预演；删除前校验路径前缀，拒绝越界。

## 4. 验证
| 项 | 结果 |
| --- | --- |
| 语法 | 4 个脚本 `Parser::ParseFile` 0 错误（UTF-8 BOM） |
| prune-builds 沙箱 | 8 个假构建目录 → 保留 5 个，`archive` 内 dpapi 文件未动 |
| prune-temp 沙箱 | 42 项 → 各类保留 5、近期文件保留、`prune-temp.ps1` 因被引用跳过 |
| prune-all 预演 | EXIT=0，四段均执行，日志落 `logs\prune-20261112_162721.log` |
| 计划任务 | `jshzPMOPruneMonthly` State=Ready，NextRunTime=2026-12-01 03:30:30 |

## 5. 首次清理执行结果（2026-11-12 16:32，经 Owner 批准，`prune-all.ps1 -Keep 5`）

| 环节 | 结果 | 释放 |
| --- | --- | --- |
| 前端快照 | 删除 130 个目录，剩余 6（5 个正式快照 + 当前 root 的 `.rollback`） | 29,112.7 MB |
| builds | 删除 102 个目录，剩余 5 + `archive\`（archive 与 `smoke-password.dpapi` 未动） | 15,897.5 MB |
| temp | 删除 99 项，27 项因被 docs/scripts 引用跳过，剩余 174 项（多为 2 天内新文件） | 169.1 MB |
| logs | 未超上限（NSSM 各 10、发布日志 20） | 0 |
| **合计** | 日志 `logs\prune-first-run-20261112.log`，EXIT=0 | **≈ 45.2 GB** |

清理后实测：`frontend/releases` 31.2 GB → **2.06 GB / 6 个**；`builds`（不含 archive）16.5 GB → **0.6 GB / 5 个**；
`temp` 570.9 MB → **401.8 MB / 174 项**；D 盘可用 **150.78 GB**。

线上核验：`/api/health` = UP 1.0.38.18；Nginx root =
`frontend/releases/1.0.38.18-reclaim-deleted-docs-20261112`（保留清单内）；`/`、`/login`、`/admin/storage` 均 200。

## 6. 回滚
- 删除计划任务：`.\scripts\register-prune-schedule.ps1 -Unregister`（管理员）。
- 脚本文件删除即可恢复原状；已删除的 frontend 快照不可恢复（回滚能力缩减到最近 5 个版本 + 当前回滚副本），
  builds/temp 均可重建。

## 7. 注意事项
- 前端旧快照删除后，`release-metadata\*.rollback.conf.txt` 指向更早版本的记录将失效；如需长期保留某版本，发布后手动把该快照移出 `releases\` 或调大 `-Keep 5`。
- `archive\` 仍为 10.5 GB，确认无回溯需求后可整体清理（其中 `.build-compliance-v4-acceptance` 7.3 GB 为可重建 e2e 沙箱）。
