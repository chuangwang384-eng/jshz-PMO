# 1.0.145 结算资料上传发布记录

2026-11-25，生产全栈发布 `SUCCESS / verified`。业务代码 `866811f` 来自本地 main，正式发布命令为 `release-full.ps1 -Mode Publish -Version 1.0.145 -Slug settlement-upload -Yes -SkipVersionBump`。

- 12:43 启动，生产预检无待执行迁移或漂移。备份 `backup/backup_20261125_124338`，制品 `builds/1.0.145-build-20261125_124405`，日志 `logs/release-full-20261125_124324.log`。
- 后端直连和 Nginx 健康均为 `UP / 1.0.145 / 866811f`。首页连续三次 200 且哈希与正式快照一致。线上 `index-Daa783WW.js` 返回 200 且含目标版本。
- 未登录请求结算资料查询与提交端点均返回 HTTP 401。后端定向测试 33 项通过，前端构建通过，正式前端制品经隔离 Edge 验证。真实项目登录后上传、推进及补件待业务验收。
- 本次无数据库迁移或配置变更。授权回滚用 `release-full.ps1 -Mode Rollback -ReleaseId 1.0.145-settlement-upload -IncludeBackend -Yes`。回滚旧制品为 `backend/releases/1.0.145-20261125_124757/previous-expert-system.jar`。结算资料记录和文件保留。旧后端以普通附件处理这一新路径，若生产已开始上传，应先核对回滚期的文件访问控制。
- [需求和后续排期](../requirements/2026-11-25-settlement-materials.md) · [任务交接](../handover/tasks/20261125_settlement_upload_requirements.md)
