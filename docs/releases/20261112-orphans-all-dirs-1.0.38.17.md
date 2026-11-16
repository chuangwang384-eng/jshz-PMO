# 发布记录：孤儿清理全目录扩展 1.0.38.17（2026-11-12）

## 1. 发布概要
- 版本：`1.0.38.17`（version.txt / frontend/package.json 同源）
- 批准：Owner（追加需求："正常的（其他目录）孤儿文件也要能清理"）
- 时间：2026-11-12 16:02–16:06，顺序：权限迁移 → 后端蓝绿 → 健康核验 → 前端快照
- 构建：`builds/1.0.38.17-build-20261112_160006/`（三项制品校验通过）
- 基线：HEAD 56c31ed（叠加于已发布 1.0.38.16 之上）

## 2. 变更内容
### 后端
- `StorageAdminService`：新增 `OrphanSpec` + `orphansOf/allOrphans/purgeOrphans/deleteWithin`，覆盖 6 个目录；新增 Mapper 依赖（知识文档/版本、专家、审批单、客户附件、供应商图片/主表）。
- `StorageAdminController`：新增 `GET /api/storage-admin/orphans`（scope 缺省 all）与 `POST /api/storage-admin/orphans/purge`；保留旧 `project-orphans` 兼容端点。
### 前端
- `StorageManage.vue`：孤儿卡片升级为「孤儿文件清理（全目录）」，一次扫描 6 目录，可展开明细，逐目录清理（固定确认词「清理」+ expected 校验）。
### 数据库
- `database/migration/20261112_add_storage_orphan_scopes_permission.sql`：2 条 `storage:manage` 接口权限，绑定 ADMIN，幂等。
- 执行脚本：`scripts/apply-migration-20261112-storage-orphan-scopes.ps1`（dry-run + 结果校验）。

## 3. 孤儿判定与安全排除
| scope | 引用集合 | 排除 |
| --- | --- | --- |
| project | project_doc/schedule/contract.filePath | 无 |
| knowledge | knowledge_document + knowledge_doc_version.filePath | `_preview/` |
| photo | expert.photo/idCardFront/idCardBack | `richtext_*` 富文本内嵌图 |
| photo-tmp | application.biz_data 的 `TMP:<name>` | 无 |
| customer | customer_attachment.storage_name | 无 |
| supplier | supplier_image.file_name ∪ supplier.images JSON | 无 |

## 4. 执行与结果
| 步骤 | 结果 |
| --- | --- |
| 迁移 | `MIGRATION_OK`；库内实测 `menu=1 api=8 orphan_paths=2 admin_bind=1` |
| 后端 | 11 阶段通过；`/api/health` = UP 1.0.38.17（buildTime 16:01:02）；deployment.md 记录 16:05:03 成功 |
| 前端 | 快照 `frontend/releases/1.0.38.17-orphans-all-20261112` 平滑切换，上一 root `1.0.38.16-storage-admin-20261112` 保留 |
| 编排日志 | `logs/release-orphans-all-20261112.log` → `RELEASE_OK`，EXIT=0 |

## 5. 发布后核验
- `/api/health` → UP 1.0.38.17（gitShort 56c31ed）。
- Nginx root 指向新快照；`/`、`/login`、`/admin/storage` 均 200。
- 线上 `assets/StorageManage-CGay6uLn.js` HTTP 200（10,467 B），含 `storage-admin/orphans` 与「孤儿文件清理（全目录）」文案。
- 部署 jar 含 `StorageAdminService$OrphanSpec`；`/api/storage-admin/orphans` 未登录 401（已注册且鉴权）。
- 权限：`menu=1 api=8 orphan_paths=2 admin_bind=1`；SUPER_ADMIN 动态全权。

## 6. 测试
- `StorageAdminServiceTest` 14 用例通过（含新增：知识库排除 _preview 且保留历史版本、照片库排除 richtext_、临时照片按 TMP: 保留、客户/供应商引用、all 聚合与未知 scope 400）。
- 回归：`ProjectAccessRegressionTest`、`ComplianceMaintenanceTest` 通过；前端 `npm run build` 通过。

## 7. 回滚
- 后端：`backend/releases/1.0.38.17-20261112_160xxx/previous-expert-system.jar`。
- 前端：`frontend/release-metadata/1.0.38.17-orphans-all-20261112.rollback.conf.txt`（上一快照 `1.0.38.16-storage-admin-20261112` 保留）。
- 数据库：`DELETE FROM permission WHERE code='storage:manage' AND path LIKE '/api/storage-admin/orphans%';`（无表结构变更）。

## 8. 风险与未验收项
- 未在生产实际执行任一目录的孤儿清理；扫描为只读，建议 Owner 先扫描核对数量再决定清理。
- 富文本内嵌图（`richtext_*`）不参与孤儿判定，属已知保守策略；如需清理需另行解析正文引用。
