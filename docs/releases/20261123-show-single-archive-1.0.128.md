# 1.0.128 串场成品与项目侧栏发布记录

- 2026-11-23 发布 `1.0.128-show-single-archive`，功能提交 `9afb0be`，基线 1.0.127。
- 管理员运行 `scripts/release-full.ps1 -Mode Publish -Version 1.0.128 -Slug show-single-archive -Yes`，依次完成生产备份、隔离构建、后端蓝绿切换和前端快照切换。发布状态 `logs/release-state-20261123_103009.json` 为 SUCCESS。
- 后端健康接口返回 UP、1.0.128、`9afb0be`。经 Nginx 的健康 API 返回 200，未登录专家列表返回 401，三次首页 HTTP 内容哈希匹配，线上主包 `index-C4iXaVp0.js` 含 `v1.0.128`。
- 生产库定点清理于 10:36:31 生效。仅软删除项目 57、阶段 12 的资料 93、94、95，审计标记 `show-dedupe-20261123`。最新资料 96 仍为有效状态，四份源文件 SHA-256 不变。全库有效串场成品重复组为 0。
- 数据库备份在 `backup/backup_20261123_103023`。本轮无结构迁移。需补偿清理时，在审批后用备份或清理前记录恢复这三个 ID 的删除字段，并复核当前有效资料，避免重建重复。旧文件仍在原路径。
- 前端回滚使用标准 Rollback 模式恢复 `1.0.127-show-preview-overview`，依据 `frontend/release-metadata/1.0.128-show-single-archive.nginx-before.txt`。后端按部署说明使用 `backend/releases/1.0.128-20261123_103333/previous-expert-system.jar`，再核对线上版本。
- 登录生产项目后的跨格式替换待验收。验证命令和结果见 [任务交接](../handover/tasks/20261123_show_single_archive.md)。
