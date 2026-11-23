# 1.0.130 工作台模块加固（bug 修复 + 死代码清理）（2026-11-23）

## 范围
仅工作台模块（前端 dashboard 相关 + 后端 Dashboard* 服务）。无数据库迁移。

## 后端修复
- `DashboardReadService`：去掉自建 `new JdbcTemplate` + 写死 1 秒超时，改用注入模板，超时经 `expert.dashboard.query-timeout-seconds`（env `DASHBOARD_QUERY_TIMEOUT_SECONDS`，默认 3 秒）配置；「我的项目」风险计数与列表下推 SQL（原全量拉取内存分页）；删除 `(Date)` 硬转与未用的 `configuration` 字段；`approvals/projects` 分页重载随端点删除一并移除。
- `DashboardTasks`：去掉 `Math.min` 超时截断（调用方 2000ms 曾被静默截成 1500ms）。
- `QuickAccessService.load`：按布局卡片白名单过滤已失去权限的入口（与保存口径对称）。
- `DashboardLayoutService`：保存比对与 snapshot 同口径按用户可见卡片计算 revision，权限回收后可收敛；注释 23→24 与代码对齐。

## 端点下线（前端零调用，全库 grep 核实）
- `GET/PUT /api/dashboard/layout`（v1 旧布局）
- `GET /api/dashboard/my-approvals`、`GET /api/dashboard/my-projects`
（未知路径返回统一 500 是既有全局异常行为，非本次引入。）

## 后端死代码删除
- `DashboardRules.completion()` 及对应测试（依赖已停用的配置快照）。

## 前端修复
- 统计卡点击无「我的项目」面板时降级跳 `/project`（原静默无响应）。
- 键盘调布局夹紧 12 列边界（原越界保存后被静默重排）。
- grid 工厂 `CARD_CATALOG[id]` 判空（原残留 DOM 会 TypeError 退出编辑模式）。
- 搜索框 Enter 失败时重新展开下拉显示错误。
- 快捷栏拖拽补 `@dragend` 重置 `dragIndex`。

## 前端死代码删除
- `DashboardView.vue`：`titles` 变量、`.risk-banner` 系列 CSS；`utils/dashboard.js`：`UNKNOWN_COMPLETION` 文案（后端已不再下发）。
- 保留：`retiredPaletteIds` 两张 stat 卡仍在默认布局渲染，属半退役而非死代码，未动。

## 验证
- `mvn test`：234 通过 / 0 失败 / 16 跳过（JWT_SECRET 注入 ≥32 字节）。
- `npm run build` 通过，is-not-exported 告警 0。
- 生产核验：后端 1.0.130（gitShort a7457ab）蓝绿 + 前端 graceful（`1.0.130-dashboard-hardening`）；
  overview 各分区 success/empty 正常、myProjects total 与 items 一致（曾发现计数占位符顺序错误致 total=0，已修复并二次发布）、projectRisk 计数与库内 SQL 直查一致、layout/v2、quick-access、全局搜索均正常；已删端点行为与未知路径一致。
- 浏览器 UI 端到端回归：未执行（建议工作台加载/编辑布局/保存/快捷栏各过一遍）。

## 已知行为变化
- 工作台查询超时 1s→3s（可配）；已登录会话首次保存布局可能触发一次 409 重载（revision 口径变化），属一次性收敛。

## 回滚
后端回上一归档版本 + 前端 `rollback-frontend.ps1` 回 `1.0.129-show-title-sync`。
