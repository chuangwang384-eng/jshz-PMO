# 项目详情右侧卡片吸顶

2026-11-16，Codex，任务 `20261116-project-sidebar-sticky`，已完成待验收。开工分支 `codex/personal-dashboard-v11`，提交 `d663523`，工作区有前次合规事项视图与发布记录的未提交改动。线上后端 `1.0.38.31` UP，前端快照 `1.0.38.31-project-compliance-views-20261116`。

`App.vue` 通过 Element Plus 的 `--el-main-padding` 表示原有 16px 内边距，`ProjectDetail.vue` 的右侧卡片组吸顶时抵消该值。双栏视图滚动后紧贴顶栏下缘，初始留白及窄屏单栏正常滚动沿用既有布局。无数据库迁移、接口、权限或运行配置变更。

`npm run build -- --outDir ../builds/project-sidebar-flush-build-20261116`、`git diff --check` 和 `node frontend/tests/project-resume-reuse-browser.mjs builds/project-sidebar-flush-build-20261116` 通过。Edge 模拟接口测试在 1191 × 675 下测得旧快照吸顶空白为 88 CSS px，新版贴合误差不超过 1 CSS px，在 1024 × 675 下验证侧栏随单栏页面滚动。合规视图与项目建档回归随同通过。两处新增样式已核对作用域，未新增重复样式或色值。架构说明及交接索引已同步。

2026-11-16 10:15 已部署生产前端。以原前端快照为基线构建，通过隔离浏览器回归与线上文件哈希验证。真实项目登录态滚动待业务验收。发布和回滚路径见 [生产发布记录](../../releases/20261116-project-sidebar-sticky.md)。
