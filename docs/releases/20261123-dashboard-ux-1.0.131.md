# 1.0.131 工作台体验四项改进（2026-11-23）

## 范围
仅前端工作台模块（DashboardView.vue）。纯前端 graceful 发布，后端保持 1.0.130，无数据库迁移。

## 改动
1. **规范文档库深链（核验通过，零改动）**：`page.complianceDocs` → `query.tab=docs` → `safeTarget` 特判透传 → `EnterpriseCompliance.vue` watch `route.query.tab` 切换标签，链路本已完整；浏览器实测点击卡片直达「规范文档库」标签。
2. **窄屏简化编辑**：`!wide`（<900px）不再禁用布局编辑。窄屏进入简化模式：仅增删卡片（复用 palette 与卡片 × 按钮），不初始化 gridstack、不显示拖拽手柄；保存/恢复默认均可用。提示文案按宽窄分流。
3. **快捷访问候选派生**：`quickCandidates` 改为从 `CARD_CATALOG` 派生全部 `kind:'page'` 卡片；`QUICK_ACCESS_ORDER` 仅声明顺序，未登记的新卡片自动排末尾（按 id 字典序）。此前漏登记的 `page.complianceDocs` 现已自动出现在候选中。
4. **保存时等价卡去重**：`saveLayout` 保存前按 `equivalentPanels` 过滤——布局同时含某功能的 page 入口与 panel 面板时保留 panel，并提示「已自动移除与面板功能重复的页面入口卡片」。

## 验证（浏览器实测，窄屏 800×900，测试账号）
- 窄屏「自定义布局」可点击；编辑态有保存/备选卡片/移除按钮、无拖拽手柄、提示文案正确。
- 窄屏添加「规范文档库」卡片 → 保存成功。
- 点击「规范文档库」页面卡 → URL `/knowledge-planet/enterprise-compliance?tab=docs`，激活标签为「规范文档库」。
- 构造「公告页面卡 + 最新公告面板」共存 → 保存触发去重提示，页面卡被移除。
- 测试账号布局已恢复默认。
- `npm run build` 通过，is-not-exported 告警 0。

## 回滚
`scripts/rollback-frontend.ps1` 回 `1.0.130-dashboard-hardening`。
