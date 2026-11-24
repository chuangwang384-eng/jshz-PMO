# 我的主页 1.0.136 发布记录

- 发布标识 1.0.136-profile-overview，生产源码提交 453e18f，已快进合入本地 main。2026-11-24 10:29:15 完成发布，状态 SUCCESS / verified。发布日志 logs/release-full-20261124_102220.log。
- 迁移前全量备份 backup/backup_20261124_102141。20261124_profile_manual_organization.sql 已执行登记。生产只读检查有 73 项已登记、0 项待执行、0 项漂移。五个顶级部门已启用，账号归属由超管设置。
- 正式制品目录 builds/1.0.136-build-20261124_102237。后端直连和经 Nginx 返回 1.0.136 / 453e18f，静态目录 frontend/releases/1.0.136-profile-overview。主页及脚本 HTTP 200，脚本含 1.0.136。组织、活动、部门、成员四个匿名 API 均返回 401。
- JDK 17 后端 10 项测试通过，正式构建通过。隔离 Edge 回归覆盖普通改密、强制改密、旧链接与超管组织入口。隔离 MySQL 和真实后端验证迁移、层级、权限、账号活动和改密会话。未执行生产已登录账号业务操作。
- 原前端为 1.0.135-customer-info。回滚配置在 frontend/release-metadata/1.0.136-profile-overview.rollback.conf.txt。应用回滚时按发布流程恢复配套前后端并保留组织表。
- 业务验收用例见 [任务交接](../handover/tasks/20261124_profile_overview.md)。超级管理员可从我的主页的组织架构管理分配总经理岗位。
