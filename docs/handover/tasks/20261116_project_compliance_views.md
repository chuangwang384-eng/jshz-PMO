# 项目合规事项视图

2026-11-16，Codex，任务 `20261116-project-compliance-views`，已完成待验收。开工分支 `codex/personal-dashboard-v11`，提交 `d663523`，工作区干净。

`ProjectDetail.vue` 的合规要求注意事项默认显示待完成事项，也可切换到已完成。完成或重开后按服务端状态归入对应视图，当前视图保留。操作期间禁用卡片内的状态按钮，刷新项目详情时沿用当前参会专家草稿。原权限、接口、数据结构及运行配置均沿用，无迁移。

前端 `npm run build -- --outDir ../builds/project-compliance-views-build-20261116` 通过，Vite 报告部分 chunk 超过体积阈值。Edge 模拟接口回归 `node frontend/tests/project-resume-reuse-browser.mjs builds/project-compliance-views-build-20261116` 通过，覆盖默认过滤、完成后移入已完成、重开后移回待完成、空状态及原建档流程。已检索本地样式类重复和目标色值，新增样式为局部间距。 `git diff --check` 通过。

已同步架构文档与交接索引。2026-11-16 10:03 隔离验收通过后完成生产前端切换。线上页面及静态文件已核验，真实项目事项的完成和重开待业务验收。见 [生产发布记录](../../releases/20261116-project-compliance-views.md)。
