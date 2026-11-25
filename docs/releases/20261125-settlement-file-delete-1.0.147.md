# 1.0.147 结算文件删除和资料包目录热修复

2026-11-25，生产全栈 `SUCCESS / verified`。本地 main 代码提交 `62b3c0e`。14:13 启动 `release-full.ps1 -Mode Publish -Version 1.0.147 -Slug settlement-file-delete -Yes -SkipVersionBump`。

- 发布前预检无待执行迁移或漂移。备份 `backup/backup_20261125_141359`，正式制品 `builds/1.0.147-build-20261125_141421`，发布日志 `logs/release-full-20261125_141347.log`。
- 后端直连及 Nginx 健康均为 `UP / 1.0.147 / 62b3c0e`。前端为 `1.0.147-settlement-file-delete`，首页连续三次 200，主资源 `index-aRjjrQ3x.js` 版本和哈希校验通过。未登录 DELETE 返回 401。
- JDK 17 定向测试 36 项通过，正式制品隔离 Edge 验证取消和确认删除、当前批次状态变化及旧 ZIP 可下载。真实项目的登录后删除和重新归档仍待业务验收。
- 无数据库迁移、配置或权限码变更。经授权回滚用 `release-full.ps1 -Mode Rollback -ReleaseId 1.0.147-settlement-file-delete -IncludeBackend -Yes`，后端旧制品在 `backend/releases/1.0.147-20261125_141814/previous-expert-system.jar`。已软删除文件和已生成 ZIP 状态保留。
- [任务交接](../handover/tasks/20261125_settlement_file_delete.md)
