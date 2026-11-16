# 发布记录：模板维护搜索修复 + 候选事项清空（1.0.38.8）

- 日期：2026-11-12 08:19–08:25
- 发布人：CodeBuddy（Claude），经 Owner 批准施工
- 任务：docs/handover/tasks/20261111_compliance_maintenance_search_clear.md
- 基线：HEAD a417c51 + 工作区未提交改动（与 1.0.38.7 发布基线相同，叠加本任务增量与版本号提升）

## 变更内容
1. **后端**（蓝绿平滑发布，ExpertSystemBackend @8080）
   - `GET /api/compliance/items` keyword 改为字面子串匹配：转义 `\ % _` LIKE 通配符 + trim（行为修正）。
   - 新增 `GET /api/compliance/documents/{id}/drafts-clear-preview`、`POST /api/compliance/documents/{id}/drafts-clear`（knowledge:maintain，expectedCount 数量校验 409，逐条复用 deleteItem 留修改历史）。
   - 同时随基线生效：`GlobalExceptionHandler` DuplicateKeyException 友好提示（20261111 审批自增修复遗留项）等工作区既有已暂存改动。
2. **前端**（快照切换，无 dist 替换、无服务重启）
   - `MaintenanceBoard.vue`：候选分组「清空候选事项」按钮（预览+确认）；候选/已发布空态区分"搜索无结果"。
   - 版本号：version.txt / frontend/package.json → 1.0.38.8。
3. **数据库**：无迁移。

## 构建与制品
- 构建：`scripts\build-all.ps1`（隔离目录），日志 `logs/build-search-clear-1.0.38.8-20261112.log`。
- 制品：`builds/1.0.38.8-build-20261112_081946/`（expert-system.jar + dist），build-info version=1.0.38.8 gitShort=a417c51。
- 后端发布日志：`logs/deploy-backend-1.0.38.8-20261112.log`（11 阶段全部 OK，GracefulSwitch）。

## 发布后核验（实测）
| 项目 | 结果 |
| --- | --- |
| `/api/health` | UP，version=1.0.38.8，buildTime=2026-11-12 08:21:16，gitShort=a417c51 |
| SPA 路由 `/`、`/login`、`/enterprise-compliance` | 均 HTTP 200 |
| 前端快照 | `frontend/releases/1.0.38.8-maintenance-search-20261112`，index hash 与快照一致；上一 root=`1.0.38.7-maintenance-ui-20261111`；Nginx 配置备份 `frontend/release-metadata/1.0.38.8-maintenance-search-20261112.nginx-before.txt` |
| 新功能代码入快照 | `compliance-BkO0bv3K.js` 含 drafts-clear-preview；`EnterpriseCompliance-Du9Mv_5g.js` 含「无匹配候选」文案 |
| 新接口注册 | 未登录请求 drafts-clear-preview 返回 401（非 404，路由已生效） |
| deployment.md | deploy-backend.ps1 阶段 11 已自动追加发布表格行 |

## 回滚
- 后端：旧 jar 归档于 `backend/releases/1.0.38.8-20261112_082408/previous-expert-system.jar`；Nginx 配置备份 `<LOCAL_PATH>`。
- 前端：`frontend/releases/1.0.38.8-maintenance-search-20261112.rollback/` + `frontend/release-metadata/1.0.38.8-maintenance-search-20261112.rollback.conf.txt`。

## 待办
- 业务验收：搜索"讲费"/"70%"/"_"字面匹配；候选清空全流程；清空后列表与计数同步（涉及真实数据写入，留给 Owner 在验收环境执行）。
