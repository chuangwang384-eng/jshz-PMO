# 项目建档简历解析一致性生产发布

2026-11-16 09:07（上海时间）发布前端快照 `1.0.38.24-project-resume-parity-20261116`。项目单人及批量建档使用专家库默认解析模式，包含文件名补姓名，提交时仍校验姓名是否匹配所选参会专家。

用线上 `1.0.38.24-batch-resume-ui-20261113` 对应源码构建，核验线上已有快照的 80 个制品哈希。前端源码差异仅有 `BatchResumeDialog.vue`。生产候选构建成功，项目建档和专家库批量简历的 Edge 模拟接口回归通过。

管理员执行 `temp/apply-project-resume-parity-20261116.ps1 -Apply`。Nginx 平滑重载，master PID 维持 8036。线上 `/experts`、`/project/55` 与相关 JS/CSS 均返回 HTTP 200，SHA-256 与制品一致。后端经 Nginx 健康检查为 UP，版本 `1.0.38.29`。无后端发布、数据库迁移或权限调整。日志 `logs/project-resume-parity-production-20261116.log` 记录了 `PRODUCTION_VERIFIED`。真实简历上传和落库回绑待业务验收。

回滚使用 `frontend/release-metadata/1.0.38.24-project-resume-parity-20261116.rollback.conf.txt`。经负责人批准后用管理员 PowerShell 恢复该 Nginx 配置并执行 `scripts/invoke-nginx-graceful.ps1`，核对线上首页哈希及 `/api/health`。回滚目录包含上一版入口与新旧哈希资产，无数据库回滚步骤。
