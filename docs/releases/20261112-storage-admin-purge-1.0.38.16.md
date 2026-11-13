# 发布记录：存储管理与硬删除工具 1.0.38.16（2026-11-12）

## 1. 发布概要
- 版本：`1.0.38.16`（version.txt / frontend/package.json 同一真源）
- 批准：Owner（本次会话，按"迁移 SQL → 后端蓝绿 → 前端快照"顺序）
- 时间：2026-11-12 15:42–15:45
- 基线：HEAD 56c31ed（并行会话 1.0.38.15 之上叠加本任务增量）
- 构建：`builds/1.0.38.16-build-20261112_153926/`（build-info 校验 version/gitShort、BOOT-INF、logback 编码三项通过）

## 2. 变更内容
### 后端（新增，无表结构变更）
- `StorageAdminService`：存储目录统计/阈值预警、孤儿文件扫描与清理、预览缓存清理、项目关联数据硬删除（21 张表级联）。
- `StorageAdminController`：`/api/storage-admin/stats`、`/project-orphans`、`/project-orphans/purge`、`/preview-cache/purge`、`/projects/{id}/purge-preview`、`/projects/{id}/purge`；全部要求 `storage:manage`。
- `OperationLogService`：新增 `STORAGE_PURGE_ORPHANS` / `STORAGE_CLEAR_PREVIEW` / `STORAGE_PURGE_PROJECT`。
- `application.yml`：`expert.storage.warn-threshold-bytes=10GB`、`critical-threshold-bytes=20GB`。
### 前端
- 新增「管理员设置 → 存储管理」页面（统计卡/孤儿清理/缓存清理/项目硬删，含不可恢复提示与二次确认）。
### 数据库
- 迁移 `20261112_add_storage_manage_permission.sql`：permission 菜单 1 行 + 接口 6 行，绑定 ADMIN；幂等 INSERT IGNORE。执行脚本 `scripts/apply-migration-20261112-storage-manage-permission.ps1`（自带 dry-run 与结果校验）。

## 3. 执行步骤与结果
| 步骤 | 命令/产物 | 结果 |
| --- | --- | --- |
| 构建 | `scripts/build-all.ps1` | EXIT=0，日志 `logs/build-storage-admin-1.0.38.16-20261112.log` |
| 迁移 | 提权编排内执行 apply-migration（先校验后执行） | `MIGRATION_OK`；库内实测 `menu=1 api=6 admin_bind=1` |
| 后端 | `deploy-backend.ps1 -JarPath builds/1.0.38.16-build-20261112_153926/expert-system.jar -Version 1.0.38.16 -GracefulSwitch` | 11 阶段全部通过，`/api/health` = UP 1.0.38.16（buildTime 15:40:26） |
| 健康核验 | 版本轮询 | version=1.0.38.16、status=UP |
| 前端 | `deploy-frontend-graceful.ps1 -StagedDist ...\dist -ReleaseId 1.0.38.16-storage-admin-20261112 -Apply` | 平滑切换成功，上一 root 保留 |
| 编排日志 | `logs/release-storage-admin-20261112.log` | `RELEASE_OK`，EXIT=0 |

## 4. 发布后核验（实测）
- `/api/health` → `UP, version=1.0.38.16, gitShort=56c31ed`。
- Nginx root → `frontend/releases/1.0.38.16-storage-admin-20261112`；SPA `/`、`/login`、`/admin/storage` 均 200。
- 线上 chunk `assets/StorageManage-DZTbyFuy.js` HTTP 200（9,714 B），含页面文案与 `storage-admin/stats` 调用。
- 部署 jar 实测含 `StorageAdminController`/`StorageAdminService` 类；5 个新端点未登录均 401（鉴权生效）。
- 权限落地：`permission` 菜单 1 + 接口 6、`role_permission(ADMIN)=1`；SUPER_ADMIN 代码内动态全权（重启后缓存为空，立即生效），ADMIN 角色用户需刷新登录态（后端重启已强制重载）。

## 5. 回滚
- 后端：`backend/releases/1.0.38.16-20261112_1542xx/previous-expert-system.jar`（deploy 脚本自动保留目录，实际目录名以 releases 列表为准）→ 按 `deploy-backend.ps1` 回投。
- 前端：`frontend/release-metadata/1.0.38.16-storage-admin-20261112.rollback.conf.txt` 或 `rollback-frontend.ps1`；上一快照 `1.0.38.15-project-list-layout-20261112` 保留。
- 数据库：权限回滚语句见迁移 SQL 注释（DELETE role_permission / permission where code='storage:manage'）；无表结构变更，无需回滚 DDL。

## 6. 风险与未验收项
- 项目硬删除为不可恢复操作，本次未在生产执行任何真实硬删；仅验证接口鉴权与页面可用。
- 业务验收待 Owner：进入「管理员设置→存储管理」查看统计/预警、扫描孤儿文件（只读）、预览缓存清空（可再生成）。孤儿清理与项目硬删建议先由 Owner 指定测试项目演练后再动用。

## 7. 相关文档
- 设计：`docs/design/2026-11-12-存储管理与硬删除工具-设计文档.md`
- 任务交接：`docs/handover/tasks/20261112_storage_admin_and_purge.md`
- 架构：`docs/architecture.md`（存储管理章节）
