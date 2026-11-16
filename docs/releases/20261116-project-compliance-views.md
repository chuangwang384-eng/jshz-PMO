# 项目合规事项视图前端发布

2026-11-16 10:03（上海时间），前端快照 `1.0.38.31-project-compliance-views-20261116` 已上线。项目详情的合规要求注意事项默认显示待完成。完成或重开后按服务端状态进入对应视图，包括待完成列表为空时的提示。

使用上一线上快照 `1.0.38.31-project-resume-required-20261116` 对应的源码隔离构建，核对上一快照的制品清单。发布源码只改 `ProjectDetail.vue`。前端构建及项目详情、专家库两组 Edge 模拟接口回归通过。在 `1191 × 675` 视口验证默认筛选、完成、重开及待完成空列表，验证状态写入携带正确的 `rowVersion`。

发布预演通过后执行 `temp/apply-project-compliance-views-20261116.ps1 -Apply`。Nginx 平滑切换，master PID 保持 8036。生产 `/project/55`、`/experts` 和关联 JS/CSS 返回 HTTP 200，SHA-256 与候选一致。线上后端健康为 UP，版本 `1.0.38.31`、提交 `e90d143`。生产日志 `logs/project-compliance-views-production-20261116.log` 已记录 `PRODUCTION_VERIFIED`。无后端发布、数据库迁移、接口或权限变更。发布验证期间未更新生产事项状态。

回滚使用 `frontend/release-metadata/1.0.38.31-project-compliance-views-20261116.rollback.conf.txt`，由管理员恢复配置并运行 `scripts/invoke-nginx-graceful.ps1`。核对上一版入口哈希及 `/api/health`。
