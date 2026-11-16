# 项目详情右侧卡片吸顶前端发布

2026-11-16 10:15（上海时间）发布 `1.0.38.31-project-sidebar-sticky-20261116`。双栏滚动时右侧卡片组贴齐顶栏下缘，窄屏单栏正常滚动。

工作区八个已有变更文件提交为 `8b8b139`。以线上前端 `1.0.38.31-project-compliance-views-20261116` 对应源码构建，核对线上资源清单。发布源码差异只涉及 `App.vue` 和 `ProjectDetail.vue`。前端构建、项目详情和专家库批量上传两组 Edge 模拟接口回归通过。1191 × 675 下测得吸顶偏差不超过 1 CSS px，1024 × 675 下验证侧栏为正常文档流。

管理员执行 `temp/apply-project-sidebar-sticky-20261116.ps1 -Apply`。Nginx 主进程 PID 保持 8036。生产 `/project/55`、`/experts`、入口和项目详情 JS/CSS 均返回 HTTP 200，SHA-256 与候选一致。日志 `logs/project-sidebar-sticky-production-20261116.log` 记录 `PRODUCTION_VERIFIED`。线上后端保持 `1.0.38.31` UP，提交 `e90d143`。无数据库迁移、API 或权限变更。

回滚时恢复 `frontend/release-metadata/1.0.38.31-project-sidebar-sticky-20261116.rollback.conf.txt`，由管理员执行 `scripts/invoke-nginx-graceful.ps1`，之后核对上一版入口哈希及 `/api/health`。
