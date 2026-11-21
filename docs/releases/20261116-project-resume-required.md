# 项目建档必填项生产发布

2026-11-16 09:41（上海时间）完成后端 `1.0.38.31` 蓝绿发布及前端快照 `1.0.38.31-project-resume-required-20261116` 切换。项目建档只要求姓名、单位，联系方式「无」按空值处理。

发布前将工作区 28 个变更文件提交为 `e90d143`。核对线上 `1.0.38.30` 的 JAR 和前端快照哈希。生产候选基于对应线上源码，只纳入当次修复涉及的两份后端、两份前端生产源码差异，继承线上已有简历解析和头像能力。后端 `mvn -q -Drevision=1.0.38.31 -DgitShort=e90d143 package` 通过，141 项中 131 通过、10 跳过、0 失败。前端构建、两组 Edge 模拟接口回归及前后端发布预演均通过。

管理员执行 `temp/apply-project-resume-required-20261116.ps1`。蓝绿切换后接回正式端口 8080，Nginx master PID 保持 8036。正式端口与经 Nginx 的健康检查均为 UP、版本 `1.0.38.31`、提交 `e90d143`，生产 JAR 哈希与候选一致。未登录建档请求返回 HTTP 401。线上 `/experts`、`/project/55` 与相关 JS/CSS 返回 HTTP 200，SHA-256 与候选一致。总发布日志 `logs/project-resume-required-orchestration-20261116.log` 已记录 `FULL_PRODUCTION_VERIFIED`。

无数据库迁移、权限或业务运行配置变更。生产未创建测试档案，真实上传、建档及回绑待业务验收。✅已验收关闭（2026-11-19 Owner 批量授权）

回滚先恢复 `frontend/release-metadata/1.0.38.31-project-resume-required-20261116.rollback.conf.txt`，用管理员 PowerShell 执行 `scripts/invoke-nginx-graceful.ps1` 并核验上一前端快照。后端按既有蓝绿流程恢复已核验哈希的 `backend/releases/1.0.38.31-20261116_094004/previous-expert-system.jar`（`1.0.38.30`）。无需数据库回滚。
