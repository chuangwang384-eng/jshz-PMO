# 工作台二维布局生产发布

日期 2026-11-06。发布标识 1.0.31-dashboard-layout-20261106，应用版本 1.0.31。主责 Codex。状态为已发布待生产业务验收。基线为 codex/personal-dashboard-v11 / b221ad4，制品来自已通过隔离 Edge 和 MySQL 验收的构建目录。

- 先执行数据库及文件备份，校验通过。备份目录 backup/backup_20261106_134251。
- 生产 expert_system:3306 已执行 20261106_dashboard_layout_v2.sql。user_dashboard_layout.layout_v2 为可空 JSON，迁移前后旧布局均为 2 条，指纹一致。
- 后端通过 deploy-backend.ps1 -GracefulSwitch 先切到 8081，再切回 8080。后端 SHA256 EA531A91F2019BC33282A264C203FA9D0F362573B61D37240E23D2047883366B。与上版比较，业务制品差异仅为 DashboardController 及 DashboardLayoutService 类。
- 前端通过 deploy-frontend-graceful.ps1 -Apply 切换至 frontend/releases/1.0.31-dashboard-layout-20261106，新布局已开启。Nginx 主进程 PID 39504 保持。正式首页和编辑器分块回读哈希均匹配。健康检查 UP，虚构账号登录返回预期业务码 401，布局接口未登录请求 HTTP 401。
- 连续性探针从首次切流准备期间开始采样，连接复用 1942 次、失败 0，新建连接 1934 次、失败 1。13:46:23 的 /api/health 新建连接出现 ECONNRESET，后续采样恢复，严格零失败门槛未通过。
- 生产浏览器连接超时且调试器未连接。实际业务账号登录后的编辑、拖拽、保存、刷新未在生产验收。下一步使用业务账号打开正式工作台完成一轮实际操作。
- 回退使用 frontend/release-metadata/1.0.31-dashboard-layout-20261106.rollback.conf.txt 平滑切换前端，后端旧包在 backend/releases/1.0.31-dashboard-layout-20261106-20261106_134639/previous-expert-system.jar。回退保留新增数据库列及旧投影。发布证据在 .build-dashboard-layout-production-20261106。

## 14:00 前端显示修复

已发布 1.0.31-dashboard-layout-ui-fix-20261106，待生产账号界面验收。复用隔离 Edge 验收制品，执行 deploy-frontend-graceful.ps1 的预演及正式切换。Nginx 主进程 PID 39504 保持，正式首页和 7 个相关资源哈希匹配。后端健康 UP，正式后端包 SHA256 保持一致。本次没有执行数据库迁移。

切流观测共 1522 次请求，复用连接 762 次、新建连接 760 次，均为零失败。以正式发布目录制品再次运行 dashboard-layout-render.mjs，真实 Edge 在固定接口数据下验证编辑前、编辑中、保存后、刷新后卡片位置尺寸一致，页脚正常、文字居中。生产账号实际保存流程未执行。

回退使用 frontend/release-metadata/1.0.31-dashboard-layout-ui-fix-20261106.rollback.conf.txt，恢复上一前端版本并保留新旧资源。执行证据位于 .build-dashboard-layout-ui-fix-20261106。
