# 2026-11-07 工作台「我的项目」列表形态改版（1.0.34.1-dashboard-projectlist-20261107）

状态：**COMPLETE**（部署与线上验证通过，纯前端变更，无迁移）。

## 变更内容
应负责人要求，工作台「我的项目」卡片从双列卡片网格改为与其他板块（待我审批/我的收藏等）一致的列表行形态：
- 每行：项目名 + 到期风险标签 → 一行小字（阶段 · 负责人 · 结束日期 · 最近推进/回退）→ 我的角色 → 「详情 →」。
- 数据字段与接口完全不变；拖拽布局、持久化（user_dashboard_layout）、权限过滤逻辑未触碰。

## 实际改动
| 文件 | 说明 |
| --- | --- |
| frontend/src/views/dashboard/DashboardView.vue | `.project-grid/.project-item` 卡片结构改为复用 `.item-row`（`.project-row` 修饰类）；样式仅保留 3 个小补充 |
| version.txt / frontend/package.json | 1.0.34 → **1.0.34.1**（纯前端小版本，沿用 1.0.25.1/1.0.30.1 先例；后端未重建，/health 仍为 1.0.34） |

## 发布过程
- 构建：`npm run build` 通过（43s，仅原有 chunk 体积警告）。
- 执行器：`deploy-frontend-graceful.ps1 -Apply`，ReleaseId `1.0.34.1-dashboard-projectlist-20261107`；Agent 非管理员 shell 被拒，经 `Start-Process -Verb RunAs` UAC 提权成功（日志 `logs/release-1.0.34.1-frontend.log`）。
- nginx root 切换：`1.0.34-20261106_1705` → `1.0.34.1-dashboard-projectlist-20261107`，优雅重载 master PID 39504 不变；旧 root 保留，回滚 conf 存 `frontend/release-metadata/`。

## 验证
- HTTP：入口 `/` 200、入口页含「正式版v1.0.34.1」标识；`http://127.0.0.1:8080/health` 1.0.34 UP；`/api/auth/login` 可达。
- 制品：新 chunk `DashboardView-CEJSZkz9.js` 经 HTTP 200 提供、内容含 `project-row`。
- 真实页面（ZCode 内置浏览器，测试账号）：登录 → 工作台渲染正常，「我的项目」以列表行显示（项目名/阶段/负责人/结束日期/最近推进/我的角色/详情→），截图存会话工件。
- 连续性切流探针：**未执行**（纯前端静态文件切换，未动后端与 proxy）。

## 回滚
把 `frontend/release-metadata/1.0.34.1-dashboard-projectlist-20261107.rollback.conf.txt` 的 root 行写回 jshz-pmo.conf 并优雅重载（回滚目录含旧入口页 + 新旧 assets 并集）。
