# 存储管理与硬删除工具（防存储爆炸）

## 1. 基本信息
- 任务编号：20261112_storage_admin_and_purge
- 任务名称：存储占用统计预警 + 孤儿文件清理 + 预览缓存清理 + 项目关联数据硬删除
- 主责 Agent：CodeBuddy（Claude）
- 交接时间：2026-11-12（2026-11-12 15:44 已发布生产 1.0.38.16）
- 当前状态：已发布，待业务验收（未在生产执行任何真实硬删）
- 优先级：P1

## 1.3 v3：已删除资料文件回收（2026-11-12 16:17 已发布 1.0.38.18）
- 问题：清理孤儿后项目资料库仍有 27 个文件 / 109.2 MB，因其 `project_doc` 行仅被软删（仍算引用）。
- 方案：新增 `project-deleted` 范围（「项目资料库（已删除资料）」），仅回收"全部行软删且无任何未删引用"的文件；`is_deleted IS NULL` 按未删处理；只删文件、保留 DB 行与日志；无迁移。
- 发布：后端蓝绿 + 前端快照 `1.0.38.18-reclaim-deleted-docs-20261112`，`logs/release-reclaim-20261112.log` = RELEASE_OK，`/health`=1.0.38.18。
- 测试：StorageAdminServiceTest 16 用例（新增 2 组：软删独占可回收/共享保留、NULL 行不误清）。
- 待办：Owner 在生产点该行"清理"释放约 109 MB（不可恢复，DB 留痕保留）。

## 1.2 v2 扩展：孤儿清理覆盖全目录（2026-11-12 16:06 已发布 1.0.38.17）
- 新增端点：GET `/api/storage-admin/orphans`（scope=all/project/knowledge/photo/photo-tmp/customer/supplier）、POST `/api/storage-admin/orphans/purge`（scope+expectedCount+expectedBytes）。
- 引用集与排除：knowledge=knowledge_document∪knowledge_doc_version（排除 `_preview/`）；photo=expert.photo/idCardFront/idCardBack（排除 `richtext_*`）；photo-tmp=application.biz_data 的 `TMP:<name>`；customer=customer_attachment.storage_name；supplier=supplier_image.file_name∪supplier.images JSON。
- 迁移：`scripts/apply-migration-20261112-storage-orphan-scopes.ps1`（库内实测 menu=1 api=8 orphan_paths=2 admin_bind=1）。
- 发布：`deploy-backend.ps1 -GracefulSwitch`（/health=1.0.38.17）→ 前端快照 `1.0.38.17-orphans-all-20261112`；`logs/release-orphans-all-20261112.log` = RELEASE_OK。
- 测试：StorageAdminServiceTest 14 用例（新增 5 组全目录场景）+ 回归 2 组通过。
- 待办：Owner 在生产点「扫描全部目录」核对孤儿数后再决定清理；`richtext_*` 内嵌图按既定策略不参与清理。

## 1.1 发布结果（2026-11-12 15:44，经 Owner 批准，顺序：迁移→后端→前端）
- 构建：`builds/1.0.38.16-build-20261112_153926/`（三项制品校验通过；jar 含 StorageAdmin 类，dist 含 StorageManage chunk）。
- 迁移：`scripts/apply-migration-20261112-storage-manage-permission.ps1`（提权执行，MIGRATION_OK；库内实测 menu=1 api=6 admin_bind=1）。
- 后端：`deploy-backend.ps1 -GracefulSwitch` 11 阶段通过，`/api/health` = UP 1.0.38.16（buildTime 15:40:26，gitShort 56c31ed）；deployment.md 已记录 15:44:03 成功。
- 前端：快照 `frontend/releases/1.0.38.16-storage-admin-20261112` 平滑切换，上一 root=`1.0.38.15-project-list-layout-20261112` 保留。
- 核验：SPA `/`、`/login`、`/admin/storage` 均 200；线上 `StorageManage-DZTbyFuy.js`（9,714 B）含页面代码；5 个新端点未登录 401；SUPER_ADMIN 动态全权、ADMIN 已绑定。
- 发布日志：`logs/build-storage-admin-1.0.38.16-20261112.log`、`logs/release-storage-admin-20261112.log`。
- 回滚：后端 `backend/releases/1.0.38.16-20261112_154326/previous-expert-system.jar`；前端 `frontend/release-metadata/1.0.38.16-storage-admin-20261112.rollback.conf.txt`；权限回滚语句在迁移 SQL 注释。
- 待办：Owner 验收统计/预警与孤儿扫描（只读）；孤儿清理与项目硬删建议先用测试项目演练。

## 2. 背景与目标
项目资料库软删留痕设计导致物理文件只增不减（普通附件删除永不物理删）。本工具为管理员提供：① 各存储目录占用统计与阈值预警；② 项目资料库孤儿文件扫描与清理（防爆炸核心）；③ 文档预览缓存清空（可再生成）；④ 已取消/已删除项目的全部关联数据硬删除。设计文档：docs/design/2026-11-12-存储管理与硬删除工具-设计文档.md。

## 3. 实际变更
新增文件：
- backend：`service/StorageAdminService.java`、`controller/StorageAdminController.java`、`test/.../StorageAdminServiceTest.java`
- frontend：`api/storageAdmin.js`、`views/admin/StorageManage.vue`
- `database/migration/20261112_add_storage_manage_permission.sql`（权限码 storage:manage：菜单 1 + 接口 6 路径，绑定 ADMIN；幂等 INSERT IGNORE，附回滚语句）
- 设计文档、本交接

修改文件：
- `OperationLogService.java`（新增 STORAGE_PURGE_ORPHANS / STORAGE_CLEAR_PREVIEW / STORAGE_PURGE_PROJECT 常量）
- `application.yml`（expert.storage.warn/critical-threshold-bytes：10GB/20GB）
- `frontend/src/router/index.js`（/admin/storage 路由 + 父路由权限数组）
- `docs/architecture.md`（存储管理章节，见下）

## 4. 硬删除范围（详见设计文档 §2）
- 随项目硬删：project_doc（含软删）、project_schedule、project_speaker、project_expert、project_supplier、project_contract、project_phase_history、project_executor、project_assistant、compliance_project_todo、compliance_project_doc_binding、compliance_execution（+task/event/reminder 级联）、compliance_item（+assignee/hide/history/user_progress 级联）、project 主行。
- 物理文件：doc∪schedule∪contract 的 filePath，事务提交后删除，删除前全库引用复核（共享路径跳过）。
- 明确保留：operation_log、download_log、application/approval_record（biz_data 留痕）、知识文档、专家/客户/供应商主数据、合规模板文档。

## 5. 护栏机制
- 权限：storage:manage（RBAC，绑定 ADMIN；SUPER_ADMIN 代码内全权），全部 6 端点逐一校验。
- 项目硬删：仅 CANCELED/DELETED 状态 + 删除前预览接口展示全部关联计数 + ElMessageBox 输入完整项目名确认 + 操作日志留痕。
- 孤儿/缓存清理：expectedCount/expectedBytes 服务端重扫描校验（409 拒绝过期操作）+ 固定确认词输入。
- 页面全程"永久删除，不可恢复"提示。

## 6. 验证
| 项目 | 结果 |
| --- | --- |
| StorageAdminServiceTest 9 用例（孤儿判定/409 校验/硬删护栏/级联 21 表/引用复核跳过） | 通过 |
| 回归 ComplianceMaintenanceTest + ProjectAccessRegressionTest | 通过（exit 0） |
| 前端构建 | 通过（42.4s） |

## 7. 未完成与发布依赖
- **发布顺序**：① 生产库执行迁移 SQL（或新权限码登录后前端不可见/接口 403）；② 后端蓝绿发布；③ 前端快照发布。后端必须先于前端（前端页面调用的接口旧后端 404）。
- 待 Owner 批准发布后执行；浏览器验收（统计页/孤儿扫描/硬删全流程）在验收环境执行。

## 8. 风险与回滚
- 项目硬删不可逆，唯一兜底为数据库备份（scripts/backup.ps1 惯例）；已用状态白名单+双重确认+权限收敛控制风险。
- 孤儿扫描引用集=project_doc∪project_schedule∪project_contract；**未来新增向项目资料库写文件的模块必须同步把 filePath 引用加入 scanOrphans 引用集合**（已在设计文档注明）。
- 回滚：撤代码；权限回滚语句见迁移 SQL 注释。
