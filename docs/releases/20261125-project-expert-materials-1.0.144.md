# 1.0.144 发布记录

2026-11-25，串场专家资料下载和日程匹配状态修复。发布制品来自本地 `main` 的 `c638aa5`。发布脚本 `release-full.ps1 -Mode Publish -Version 1.0.144 -Slug project-expert-materials -Yes -SkipVersionBump` 于 12:00 启动，最终状态为 `SUCCESS / verified`。

- 生产库只读预检通过，无待执行迁移、结构漂移或本次配置变更。
- 全量备份位于 `backup/backup_20261125_120019`，构建位于 `builds/1.0.144-build-20261125_120049`，日志位于 `logs/release-full-20261125_120003.log`。
- 后端直连与 Nginx `/api/health` 均返回 `UP / 1.0.144 / c638aa5`。Nginx 正式后端为 8080，发布脚本已回收影子进程。
- 前端 Nginx root 为 `frontend/releases/1.0.144-project-expert-materials`。首页连续三次 200 且哈希吻合，线上主包 `index-CWYx6BKq.js` 返回 200 且版本为 1.0.144。
- 未登录请求 `GET /api/project/37/show/expert-materials` 返回 HTTP 401。真实生产项目中的已登录页面与 ZIP 内容待业务验收。隔离 Edge 和 JDK 17 定向测试证据见 [任务交接](../handover/tasks/20261125_project_expert_materials.md)。
- 回滚由授权管理员使用 `release-full.ps1 -Mode Rollback -ReleaseId 1.0.144-project-expert-materials -IncludeBackend -Yes`。前端回滚配置位于 `frontend/release-metadata/1.0.144-project-expert-materials.rollback.conf.txt`，后端旧制品位于 `backend/releases/1.0.144-20261125_120528/previous-expert-system.jar`，无需数据库回滚。
