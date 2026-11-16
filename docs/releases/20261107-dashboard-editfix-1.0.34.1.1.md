# 2026-11-07 工作台编辑布局功能恢复（1.0.34.1.1-dashboard-editfix-20261107）

状态：**COMPLETE**（部署与真机验证通过，纯前端变更，无迁移）。

## 故障与根因
Owner 反馈回滚后工作台「编辑布局」功能消失。根因：`VITE_DASHBOARD_LAYOUT_V2` 是**构建时固化**的功能开关（不写入 dashboard 时编辑器整个编译排除），且不在任何 `.env` 文件中，仅由发布构建过程以进程环境变量注入。本日 1.0.34.1 / 1.0.34.2 两次发布均由 Agent 直接 `npm run build` 构建，**未注入开关**，导致功能丢失。1.0.34 之前的官方发布均带开关（见 docs/deployment/2026-11-06-工作台二维布局 §2）。

## 修复
1. `git revert` 密度优化提交 1691c24（代码回到 1.0.34.1 状态，密度改动保留在 git 历史 a4c*可查）。
2. 版本号 1.0.34.1.1（version.txt + package.json）。
3. 构建：`VITE_DASHBOARD_LAYOUT_V2=true npm run build`，构建后 grep 确认产物含「编辑布局」。

## 发布
ReleaseId `1.0.34.1.1-dashboard-editfix-20261107`，deploy-frontend-graceful.ps1 -Apply（UAC 提权），nginx 优雅重载 master PID 39504 不变。服务端验证：入口 bundle `index-10Sk73kB.js`，其引用的 `DashboardView-8fI3TX-H.js` 含「编辑布局」。

## 真机验证（测试账号）
- 入口标识「正式版v1.0.34.1.1」；「编辑布局」按钮出现。
- 点击进入编辑模式：保存布局/恢复默认/取消工具条、卡片拖拽手柄与移除按钮、操作提示全部正常。
- 取消退出编辑模式正常，无报错。

## 教训（已记入交接）
- **前端功能开关必须进 .env 文件或写进发布脚本构建步骤**；靠会话环境变量注入不可复现。本次修复未改构建脚本（最小改动），后续应将 `VITE_DASHBOARD_LAYOUT_V2=true` 固化到 `frontend/.env.production` 或 deploy 脚本——待 Owner 确认后执行。

## 回滚
`frontend/release-metadata/1.0.34.1.1-dashboard-editfix-20261107.rollback.conf.txt`（回滚点为 1.0.34.1-dashboard-projectlist-20261107）。
