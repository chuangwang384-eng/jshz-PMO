# 项目建档简历头像生产发布

2026-11-16 09:24（上海时间）完成后端 `1.0.38.30` 蓝绿发布及前端快照 `1.0.38.30-project-resume-avatar-20261116` 切换。项目单人及批量建档共享专家库的简历头像候选，选中后随建档保存头像和回绑参会记录。

候选基于生产后端 `1.0.38.29` 和前端 `1.0.38.24-project-resume-parity-20261116` 对应的源码构建。只复制本任务涉及的六个生产源码文件。后端 `mvn -q -Drevision=1.0.38.30 -DgitShort=1fa4cf2 package` 成功，140 项中 130 通过、10 跳过、0 失败。前端构建、项目建档及专家库批量简历两组 Edge 模拟接口回归、前后端发布预演均通过。

管理员运行 `temp/apply-project-resume-avatar-20261116.ps1`。先在 8081 验证新版后端，再接回正式端口 8080，最后切换前端。Nginx master PID 保持 8036。直连和通过 Nginx 的后端健康接口为 UP，生产 JAR 哈希和候选一致。未登录建档请求返回 HTTP 401。线上 `/experts`、`/project/55` 与相关 JS/CSS 均返回 HTTP 200，SHA-256 与候选一致。总发布日志 `logs/project-resume-avatar-orchestration-20261116.log` 已记录 `FULL_PRODUCTION_VERIFIED`。

现有建档 JSON 请求兼容，选中头像时支持 multipart。无数据库迁移、权限规则或业务运行配置改动。真实生产简历上传及头像落盘、参会记录回绑待业务验收。

回滚先由管理员恢复 `frontend/release-metadata/1.0.38.30-project-resume-avatar-20261116.rollback.conf.txt`，执行 `scripts/invoke-nginx-graceful.ps1` 并核验上一前端快照。若后端也需回滚，使用经 SHA-256 核实的 `backend/releases/1.0.38.30-20261116_092326/previous-expert-system.jar`（`1.0.38.29`），按既有蓝绿路径验证后切换。无需数据库回滚。
