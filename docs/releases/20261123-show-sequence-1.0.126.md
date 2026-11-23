# 1.0.126 串场排序发布记录

- 2026-11-23，发布标识 `1.0.126-show-sequence`，功能提交 `33cb5a0`，基线生产版本 1.0.125。
- 管理员 PowerShell 执行 `scripts/release-full.ps1 -Mode Publish -Version 1.0.126 -Slug show-sequence -Yes`。正式发布先备份，再隔离构建，随后完成后端蓝绿切换与 Nginx 前端快照切换。
- 发布状态文件 `logs/release-state-20261123_092229.json` 返回 SUCCESS。发布日志 `logs/release-full-20261123_092229.log`。
- 后端 `/health` 返回 UP、1.0.126、`33cb5a0`。经 Nginx 的 `/api/health` 返回 200，未登录专家列表返回 401。站点首页三次返回 200 且哈希匹配新快照，线上主包 `index-BVMUt2LW.js` 包含 `v1.0.126`。
- 全量备份存放在 `backup/backup_20261123_092241`。本次发布没有数据库迁移。前端回滚由标准发布脚本的 Rollback 模式恢复至 1.0.125，使用 `frontend/release-metadata/1.0.126-show-sequence.nginx-before.txt`。后端回滚使用本轮蓝绿发布归档的上一版 JAR，按部署文档恢复并复查两个实际版本。
- 真实项目登录后编辑、保存与归档待验收。功能验证见 [串场排序交接](../handover/tasks/20261123_show_sequence.md)。
