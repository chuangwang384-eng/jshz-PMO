# 发布记录：已删除资料文件回收 1.0.38.18（2026-11-12）

## 1. 问题与根因
Owner 反馈"清理孤儿文件后 `<LOCAL_PATH>` 仍有大量文件"。核查结论：**删除机制正常，是"孤儿"口径把软删记录算作引用**。

- 磁盘：28 个文件 / 110.7 MB；`project_doc` 28 行，其中 **27 行 `is_deleted=1`**（带 `deleted_by/deleted_time` 留痕）。
- 仅被软删行引用、无任何未删引用的文件：**27 个 / 109.2 MB**（全部仍在磁盘）；被未删行引用的仅 1 个。
- 原口径 `refs = project_doc ∪ schedule ∪ contract（全表含软删）` → 这 27 个文件"有记录指向"，故不算孤儿；历史清理日志显示 15:47 确实删过 10 个真孤儿。

## 2. 修复方案（v3）
新增第 7 个可清理范围 `project-deleted`（展示名「项目资料库（已删除资料）」）：
- 候选：`project_doc.file_path` 中**所有行均软删**、且**全库无未删引用**（未删资料行 ∪ 日程 ∪ 合同）的路径；
- `is_deleted IS NULL` 的历史行按未删处理（保守保留）；
- 只删磁盘文件，**保留数据库行与操作日志**（审计留痕不受影响）；
- 与普通孤儿共用 `GET /api/storage-admin/orphans?scope=…`、`POST /orphans/purge`（expectedCount/expectedBytes 校验、防穿越删除），**无需新增权限行与数据库迁移**。

## 3. 发布
| 步骤 | 结果 |
| --- | --- |
| 构建 | `builds/1.0.38.18-build-20261112_161236/`，三项制品校验通过 |
| 后端 | 11 阶段蓝绿通过；`/api/health` = UP 1.0.38.18（buildTime 16:13:26） |
| 前端 | 快照 `frontend/releases/1.0.38.18-reclaim-deleted-docs-20261112` 平滑切换 |
| 编排日志 | `logs/release-reclaim-20261112.log` → `RELEASE_OK`，EXIT=0 |

版本号：`version.txt` / `frontend/package.json` → 1.0.38.18。

## 4. 发布后核验
- `/api/health` → UP 1.0.38.18（gitShort 56c31ed）。
- Nginx root 指向新快照；`/`、`/login`、`/admin/storage` 均 200。
- 线上 `assets/StorageManage-B07gWTTG.js` HTTP 200（10,800 B），含「已删除资料」文案与新增说明。
- `/api/storage-admin/orphans?scope=project-deleted` 未登录 401（端点已注册且鉴权）。

## 5. 测试
`StorageAdminServiceTest` 16 用例通过，其中新增：
- `softDeletedDocFilesAreReclaimableButSharedFilesAreKept`：软删独占文件可回收；与未删行共享的文件、日程引用的文件均保留；普通口径仍为 0。
- `softDeletedReclaimIgnoresMissingAndNullIsDeletedRows`：`is_deleted IS NULL` 按未删处理，不误清。

## 6. 使用方式（Owner）
「管理员设置 → 存储管理 → 扫描全部目录」→ 表格新增行「项目资料库（已删除资料）」→ 展开核对 → 点「清理」输入「清理」。当前预期释放约 **109.2 MB / 27 个文件**。

## 7. 回滚
- 后端：`backend/releases/1.0.38.18-20261112_161xxx/previous-expert-system.jar`
- 前端：`frontend/release-metadata/1.0.38.18-reclaim-deleted-docs-20261112.rollback.conf.txt`
- 无数据库变更，无需回滚 SQL。

## 8. 风险说明
- 回收后这些文件**不可恢复**（系统本来也没有资料恢复入口）；数据库行、删除人/时间与操作日志仍保留，可追溯"谁在何时删除了哪份资料"。
- 若未来新增资料恢复功能，需先确保不对 `project-deleted` 范围执行清理。
