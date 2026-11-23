# 1.0.127 串场头像裁剪与只读预览发布记录

- 2026-11-23，发布标识 `1.0.127-show-preview-overview`，功能提交 `b5dafc4`，上一生产版 1.0.126。
- 管理员 PowerShell 运行 `scripts/release-full.ps1 -Mode Publish -Version 1.0.127 -Slug show-preview-overview -Yes`，完成全量备份、隔离构建、后端蓝绿切换、Nginx 前端切换和发布验证。
- 备份在 `backup/backup_20261123_095721`。日志 `logs/release-full-20261123_095706.log`，状态文件 `logs/release-state-20261123_095706.json` 为 SUCCESS。
- 后端 `/health` 为 UP、1.0.127、`b5dafc4`。经 Nginx 的 `/api/health` 为 200，未登录专家列表为 401。Nginx root 指向新版快照，首页三次 200 且哈希匹配，线上 `index-CJJOWAwZ.js` 含 `v1.0.127`。
- 本版无数据库迁移。前端通过标准发布脚本 Rollback 模式恢复 `1.0.126-show-sequence`，依据 `frontend/release-metadata/1.0.127-show-preview-overview.nginx-before.txt`。后端按部署文档使用归档 `backend/releases/1.0.127-20261123_100051/previous-expert-system.jar` 恢复 1.0.126，并核实两个线上版本。
- 真实项目保存、归档与下载待业务验收。功能验证及主题扩展说明见 [任务交接](../handover/tasks/20261123_show_preview_overview.md)。
