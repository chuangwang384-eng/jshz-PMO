# PowerPoint 简历照片候选 1.0.68 发布

2026-11-18 17:02 CST 前后端生产发布完成，主责 Codex。功能提交 `6559742`，构建提交 `90fd15a`，制品目录 `builds/1.0.68-build-20261118_165755/`。两份原件均复测为第一候选是证件照。徐征姓名由现有规则回填，排除了幻灯片中的「讨论嘉宾」。本次无数据库迁移。

- 34 项定向测试通过，5 项可选原件测试跳过。前后端构建通过。
- 后端用 8082 影子实例进行蓝绿验证，Nginx 平滑回切正式 8080。影子启动日志记录 Java 25.0.5，正式 NSSM 服务启动日志记录 Java 17.0.20.1。影子通过启动和健康检查。正式 JVM 继续由 NSSM 固定路径指定。
- 直连与代理健康均为 `UP / 1.0.68 / 90fd15a`。首页、专家库、登录页与新旧 JS 入口均返回 HTTP 200，匿名简历解析返回 HTTP 401。生产登录后单传、批量上传和选照待业务验收。
- 前端 root 为 `frontend/releases/1.0.68-resume-visible-portrait-20261118/`，Nginx 主进程保持 7696。
- 后端回滚包 `backend/releases/1.0.68-20261118_170029/previous-expert-system.jar` 的 SHA-256 与 1.0.67 已发布制品一致。前端回滚使用 [Nginx 回滚配置](../../frontend/release-metadata/1.0.68-resume-visible-portrait-20261118.rollback.conf.txt)，再平滑重载 Nginx。
- 构建、部署与核验日志位于 `logs/`，前缀为 `20261118-resume-photos-1.0.68-`。复测步骤见 [任务交接](../handover/tasks/20261118_resume_visible_portrait.md)。
