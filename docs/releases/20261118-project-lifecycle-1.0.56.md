# 项目管理六阶段 1.0.56 生产发布

2026-11-18 09:27 CST 发布完成，主责 Codex。功能提交为 `main@5281cbf`，前端构建提交为 `4d3d767`，制品位于 `builds/1.0.56-build-20261118_092430/dist/`。后端版本为 1.0.55（`de6b319`）。

## 发布与验证

1. 发布前迁移台账仅待执行 `20261118_project_lifecycle_stages.sql`，漂移为 0，`check-schema-parity.ps1` 通过。备份目录为 `backup/project-lifecycle-1.0.56-20261118_092608/`，含阶段字典、助手条目、SQL 备份 SHA-256 和迁移前项目进度。
2. 09:26:16 使用 `scripts/db-migrate.ps1 -Mode Apply` 执行并登记迁移。六阶段排序为 98–103，预算 ID 10、日程 ID 8 沿用。7 条既有项目记录的 ID、阶段 ID 和状态在迁移前后完全一致，含软删除历史记录。上线后迁移台账已登记 65 项，待执行 0，漂移 0。
3. 09:27:21 使用 `deploy-frontend-graceful.ps1 -Apply` 切换到 `frontend/releases/1.0.56-project-lifecycle-20261118/`。Nginx 主进程 PID 7696 保持。生产首页、项目列表、项目详情路由和登录页均 HTTP 200，入口 HTML 与发布快照一致。新旧主入口资源均 HTTP 200。
4. 生产后端经 Nginx 返回 `UP / 1.0.55 / de6b319`，匿名项目详情 API 返回 401。前端构建、隔离数据库迁移和模拟 API 的 Edge 流程回归通过。生产登录后的阶段推进和回退留待业务验收。

发布日志为 `logs/20261118-project-lifecycle-1.0.56-deploy.log`，迁移日志为 `logs/db-migrate-20261118_092611.log`。

## 回滚

- 前端使用 [Nginx 回滚配置](../../frontend/release-metadata/1.0.56-project-lifecycle-20261118.rollback.conf.txt) 指向保留两版哈希静态资源的回滚快照，平滑重载 Nginx。
- 数据补偿依据发布前备份。回退阶段字典前须核对新增阶段的项目、资料、历史引用。发生引用时保留相应阶段 ID，再确认补偿方式。后端无需版本回滚。
- 验收步骤见 [六阶段交接](../handover/tasks/20261118_project_lifecycle_stages.md)。
