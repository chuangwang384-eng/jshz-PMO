# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261123-dashboard-hardening
- 任务名称：工作台模块 bug 修复 + 死代码/零调用端点清理
- 主责 Agent：ZCode（GLM）
- 交接时间：2026-11-23 12:10
- 当前状态：已完成待验收
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：修复工作台前后端审查发现的 bug；删除 4 个零调用端点与死代码；测试功能完整性。
- 不在本次范围：工作台以外的模块；retiredPaletteIds 半退役卡的最终去留（涉及默认布局变更，待负责人决策）；窄屏编辑、docs 深链等新需求（审查报告第三批）。
- 验收条件：后端测试全绿；前端构建零 is-not-exported；生产 overview/布局/快捷访问/全局搜索正常；已删端点不可达。

## 3. 项目基线
- 分支：main；提交 bea122e → a7457ab；版本 1.0.130（后端蓝绿 + 前端 graceful `1.0.130-dashboard-hardening`）。
- 核实时间：2026-11-23（/health version=1.0.130, gitShort=a7457ab）。

## 4-5. 已完成工作与实际变更
见 [发布记录](../../releases/20261123-dashboard-hardening-1.0.130.md)。要点：
- 后端：DashboardReadService（超时可配/SQL 分页下推/类型转换）、DashboardTasks（超时截断）、QuickAccessService（读路径权限过滤）、DashboardLayoutService（revision 可见集口径）；删除 v1 layout 双端点、my-approvals/my-projects 端点、DashboardRules.completion() 及测试；application.yml 新增 expert.dashboard.query-timeout-seconds。
- 前端：DashboardView/dashboardGrid/GlobalSearchBar/QuickAccessBar 五处修复；删 titles、risk-banner CSS、UNKNOWN_COMPLETION 文案。

## 6. 测试与验证
| 验证项 | 命令/步骤 | 结果 |
| --- | --- | --- |
| 后端单测 | `mvn test`（JWT_SECRET≥32B） | 234 通过/0 失败/16 跳过 |
| 后端构建 | build-all.ps1 -BackendOnly | 通过（build-info/BOOT-INF 校验过） |
| 前端构建 | npm run build | 通过，is-not-exported=0 |
| 生产接口 | overview/layout v2/quick-access/search 实测 | 全部正常 |
| 计数修复复验 | 库内 SQL 直查 vs API | 一致（total=1, dueSoon=1） |
| 浏览器 UI 回归 | — | 未执行 |

## 7. 未完成事项
| 事项 | 原因 | 下一步 |
| --- | --- | --- |
| 浏览器端工作台回归 | 需人工/界面操作 | 负责人过一遍：加载、编辑布局保存、快捷栏拖拽、统计卡点击 |
| 409 一次性收敛观察 | revision 口径变化 | 已登录用户首次保存布局会重载一次，属预期 |

## 8. 风险与回滚
- 行为变化：查询超时 1s→3s；未知路径统一 500（既有行为）。
- 回滚：后端回上一归档版本；前端 rollback-frontend.ps1 回 1.0.129。

## 9. 文档同步
- [x] 发布记录、部署文档（新增 DASHBOARD_QUERY_TIMEOUT_SECONDS）
- [ ] AGENTS.md 18.4 环境变量清单未追加：该文件存在他人未提交改动（工作区 ' M'），避免覆盖，待负责人合并时补一行。

## 10. 下一位 Agent 的接手指令
1. `curl http://127.0.0.1:8080/health` 确认 1.0.130/a7457ab。
2. 浏览器回归工作台（见上）。
3. 新需求施工见审查报告第三批（docs 深链核验 → 窄屏简化编辑 → QUICK_ACCESS_ORDER 派生）。
4. 未经确认禁止：删除 retiredPaletteIds 卡、改动 AGENTS.md。

---

## 附：1.0.131 后续（2026-11-23）
审查第三批新需求已实施并发布（纯前端 graceful `1.0.131-dashboard-ux`）：规范文档库深链核验通过（零改动）、窄屏简化编辑、快捷访问候选由 CARD_CATALOG 派生、保存时等价卡去重。浏览器窄屏实测四项均通过，测试账号布局已恢复默认。详见 [发布记录](../../releases/20261123-dashboard-ux-1.0.131.md)。审查报告其余建议（retired stat 卡去留等）仍待负责人决策。
