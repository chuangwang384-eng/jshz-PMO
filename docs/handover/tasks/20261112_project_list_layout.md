# 2026-11-12 项目列表布局优化

主责 Codex。前端页面 ProjectList.vue 已发布生产。该任务以现有项目列表为基础，沿用搜索、查询参数、权限、项目操作及分页逻辑。

- 常用条件包括关键词、团队、会议类型和当前阶段。会议城市、负责人、协助人及两组日期收进「更多筛选」，使用数量显示隐藏条件。链接携带高级筛选时会自动展开。
- 按会议、当前阶段、时间安排、团队与人员、更新时间组织列表。详情直接可访问，编辑、取消、恢复和删除使用「更多」。操作列取消固定，窄屏只在表格内横向滚动。
- 无 API、数据库、配置、路由及权限码变更。恢复旧前端快照即可回滚。
- 在独立输出目录执行 npm --prefix frontend run build，构建通过。执行 node temp/project-list-layout-edge.mjs，以隔离 API 响应验证真实 Edge 下 1532、1024、480px、筛选回显和重置、阶段展示、取消弹窗和详情跳转。git diff --check 通过。日志位于 logs/project-list-layout-build.log 和 logs/project-list-layout-edge.log。生产发布见下节。

## 生产发布

2026-11-12，前端平滑切换至 1.0.38.15-project-list-layout-20261112。相对上一快照的内容差异仅为项目列表 JS/CSS。线上入口和新列表 JS/CSS 与验收包哈希一致，/project 返回 200，切换监测 84 次、0 失败。后端仍为 1.0.38.15 UP。仅发布前端，无数据库迁移。

上一快照为 frontend/releases/1.0.38.15-dashboard-palette-20261112，回切配置为 frontend/release-metadata/1.0.38.15-project-list-layout-20261112.rollback.conf.txt。连续探测见 logs/project-list-layout-production-probe.log。
