# 1.0.146 结算多文件与资料包发布记录

2026-11-25，生产发布 `SUCCESS / verified`。本地 main 业务代码为 `989b58b`，包含上一版本的结算资料上传功能。13:56 启动管理员发布，聊天中断后发布进程继续完成，未发起重复切换。

- 生产预检无待执行迁移或漂移。备份 `backup/backup_20261125_135617`，制品 `builds/1.0.146-build-20261125_135644`，日志 `logs/release-full-20261125_135604.log`。
- 后端经 Nginx 健康响应为 `UP / 1.0.146 / 989b58b`。Nginx root 为 `frontend/releases/1.0.146-settlement-package`。首页三次 200 且哈希一致，线上主包 `index-DQAPwC3T.js` 返回 200 且核对版本和哈希通过。匿名调用归档接口返回 401。
- JDK 17 定向测试 35 项通过，前端构建通过。隔离 Edge 对正式制品验证逐次选文件、追加、手动归档、历史和新版文件名以及实际 ZIP 下载名。真实项目登录后上传和归档待业务验收。
- 无数据库迁移、配置或权限码变更。授权回滚命令为 `release-full.ps1 -Mode Rollback -ReleaseId 1.0.146-settlement-package -IncludeBackend -Yes`。后端旧制品为 `backend/releases/1.0.146-20261125_140101/previous-expert-system.jar`。回滚不删除结算资料或 ZIP。
- [任务交接](../handover/tasks/20261125_settlement_package.md)
