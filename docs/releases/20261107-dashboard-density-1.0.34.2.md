# 2026-11-07 工作台信息密度优化发布（1.0.34.2-dashboard-density-r2-20261107）

状态：**COMPLETE**（部署与线上验证通过，纯前端变更，无迁移）。

## 变更内容
按 [需求文档 v1.1](../requirements/2026-11-07-个人工作台信息密度优化-需求文档.md)（Owner 2026-11-07 决策：空面板折叠不做、内联审批与 Ctrl+K 不纳入）实施，仅 `frontend/src/views/dashboard/DashboardView.vue`：
1. **头部压缩**：问候语 26px→20px 与副标题同一行，问候区 margin 18px→10px；日期与刷新按钮保持右上。
2. **KPI 卡降噪**：删除「待处理/待补充/已超期」重复副标题（到期提醒的"今天/七天内"说明保留）；0 值应用 `.is-zero` 静音样式；仅已逾期>0 保留 danger 高亮（caution 黄底删除）。
3. **项目行结构化**：`.project-row` 改三列网格（名称+角色 / 四个对齐属性列：阶段·负责人·结束日期·最近推进/回退 / 详情→）；`warning==='OVERDUE'` 行加 2px 顶边线。
4. **时间格式化**：新增 `fmtTime()`（M月D日 HH:mm，跨年附年份，非法输入原样返回），应用于最近推进、审批/退回时间、收藏/公告/通知时间；`endDate` 保持 YYYY-MM-DD。
5. **颜色降噪**：新增 `--tone-danger-quiet/--tone-warning-quiet` 等变量，danger 底色改用极淡变量值。

## 发布过程
- 版本 1.0.34.2（version.txt + package.json；后端不动，/health 仍 1.0.34）。
- 构建先出 `temp/dist-verify-1.0.34.2` 验证，后正式构建 dist。
- 第一版发布后发现属性列在窄面板溢出（横向滚动条+列重叠），收紧列宽（4em/4.5em/6.6em/7.6em、gap 14px→固定、overflow:hidden）后以新 ReleaseId `1.0.34.2-dashboard-density-r2-20261107` 二次发布；`1.0.34.2-dashboard-density-20261107` 为废弃快照保留可回滚。
- 两次发布均走 `deploy-frontend-graceful.ps1 -Apply`（UAC 提权），nginx 优雅重载 master PID 39504 不变，root 现指向 r2。

## 验证
- 静态：模板无裸 ISO 时间渲染路径（grep 核对）。
- HTTP：`/` 200、入口标识「正式版v1.0.34.2」、`/health` 1.0.34 UP、新 chunk `DashboardView-*.js` 200。
- 真实页面（ZCode 内置浏览器，测试账号）：KPI 0 值无色底无副标题；项目行四属性列对齐显示、时间已格式化（11月02日 15:51）；收藏/公告时间格式化；空面板形态与改造前一致；横向滚动条消失。截图存会话工件。
- 布局编辑逻辑零改动（`CARD_CATALOG`/拖拽/持久化未触碰）；连续性切流探针未执行（纯静态资源切换）。

## 回滚
`frontend/release-metadata/1.0.34.2-dashboard-density-r2-20261107.rollback.conf.txt` 的 root 行写回 jshz-pmo.conf 并优雅重载。
