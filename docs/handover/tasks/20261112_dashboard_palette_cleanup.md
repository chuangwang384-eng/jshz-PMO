# 2026-11-12 工作台备选卡片去重

主责 Codex，HEAD 91a67f3，工作区包含既有变更。已发布生产 1.0.38.15。

- DashboardView.vue 将待我审批、退回待修改和申请进度的申请类型统一显示为中文。未知代码显示「其他申请」，已有中文标题沿用。申请时间去除 ISO 的 T。
- 只调整备选池。旧审批和退回统计卡停止提供为备选，已保存布局和默认布局继续渲染。QuickAccessBar.vue 提供快捷访问草稿标识，备选池去掉同一快捷入口以及已由放置的面板覆盖的入口。
- 新增可选的「我的申请进度」面板及「规范文档库」页面卡。EnterpriseCompliance.vue 支持文档库页签深链，dashboard.js 仅对该路径放行 tab=docs。DashboardLayoutService 新卡沿用原申请、知识模块权限。
- 无数据库迁移、API 请求或响应结构调整，也无需新增权限码。部署需要同步新布局白名单后端和前端。回滚可使用旧制品，已保存的新卡在旧前端中不会显示。
- 验证日志见 logs/dashboard-palette-unit.log、logs/dashboard-palette-build.log、logs/dashboard-palette-edge.log 和 logs/dashboard-palette-backend-test.log。前端单测 9 项、后端 DashboardRulesTest 4 项通过。真实 Edge 使用隔离接口响应验证布局和跳转，该 Edge 用例使用隔离响应，未执行真实数据库保存。

## 生产发布

2026-11-12，先部署后端 1.0.38.15 布局白名单，再平滑切换前端 1.0.38.15-dashboard-palette-20261112。直连与 Nginx 健康均为 1.0.38.15 UP，运行 JAR、工作台入口和相关在线资产哈希与候选一致，匿名布局接口 401。切换期间健康探测 110 次、0 失败。正式构建重新通过 Edge 隔离验收。版本元数据已同步。

- 后端日志为 logs/deploy-backend.20261112_144116.log。上一生产 JAR 为 backend/releases/1.0.38.15-20261112_144204/previous-expert-system.jar。
- 前端上一快照为 frontend/releases/1.0.38.14-projectfile-pan-20261112。回切配置为 frontend/release-metadata/1.0.38.15-dashboard-palette-20261112.rollback.conf.txt。先回切前端，再回滚后端可避免新卡保存触及旧白名单。
- 验收和连续探测日志为 logs/dashboard-13815-edge.log、logs/dashboard-13815-health-probe.log。无数据库迁移。
