# 项目会前财务、报价归档与旧区块清理 1.0.99

- 生产切换于 2026-11-20 17:57 完成。制品提交 4eba8fa，基线归档提交 e47bcdb。
- 后端直连和 Nginx 代理均返回 UP / 1.0.99 / 4eba8fa，Nginx 两个后端转发均指向正式端口 8080。前端快照为 1.0.99-project-finance-quote-20261120。
- 首页、项目列表、项目 56 入口响应为 200，与候选 index 完全一致。匿名读取项目预算和阶段表单返回 401。新旧前后端制品 SHA-256 均已与候选和回滚基线分别核对。
- 发布前后端 34 项定向回归通过，Edge 隔离数据验收通过。登录生产后使用真实项目保存财务资料和确认新报价未执行。数据库迁移不适用。
- 初次发布的 Nginx 切回超时已恢复。恢复使用核对过的发布前配置并分别执行优雅重载和前端切换。证据见 logs/project-finance-primary-reload-1.0.99.log、logs/project-finance-resume-1.0.99.log。

## 制品与回滚

- 新版为 builds/1.0.99-build-20261120_172334 下的 expert-system.jar 与 dist。
- 后端回滚包为 backend/releases/1.0.99-20261120_173211/previous-expert-system.jar，使用既有蓝绿发布脚本回退 1.0.98。
- 前端回退用 frontend/release-metadata/1.0.99-project-finance-quote-20261120.rollback.conf.txt，配合既有 Nginx 优雅重载及入口校验。回滚快照 index 已与原 1.0.98 核对。回滚无需修改项目历史数据。
- [交接记录](../handover/tasks/20261120_project_finance_quote_cleanup.md)

8082 的本机影子进程已退出生产路由。普通权限清理被系统拒绝，自动审批也拦截了提权清理命令，最后核对仍由 Java PID 44220 监听。
