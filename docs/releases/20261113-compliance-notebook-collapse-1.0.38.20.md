# 发布记录：合规记事本折叠与收藏关联说明（前端 1.0.38.20）

- 发布时间：2026-11-13 10:30 左右
- 发布方式：`scripts/deploy-frontend-graceful.ps1 -Apply`（提权 UAC 通过，graceful Nginx root 切换，无服务重启）
- ReleaseId：`1.0.38.20-compliance-notebook-collapse-20261113`
- 回滚：`frontend/release-metadata/1.0.38.20-compliance-notebook-collapse-20261113.nginx-before.txt` 恢复 root 指针到 `1.0.38.19-project-exact-match-20261113`（rollback 目录已由脚本生成）

## 变更内容（仅前端）
- `frontend/src/components/compliance/ComplianceNotebook.vue`：
  1. 事项正文/阶段/时间/来源默认折叠，点击标题（支持键盘 Enter/空格）展开；无详情的条目不显示展开箭头。
  2. 页签顶部新增常驻说明：收藏规范文档自动关联进记事本、取消收藏移除分组、勾选推送项目、隐藏仅对自己生效。
- 版本：`version.txt` / `frontend/package.json` 1.0.38.18 → **1.0.38.20**（1.0.38.19 仅后端线上版本号，当日早间发布漏回写版本真源，本次统一）。

## 验证
- 构建门禁：`vite build` 通过，rollup `is-not-exported` 警告数 = 0。
- 线上核验：Nginx root 已指向新 ReleaseId；`http://127.0.0.1/` 200 且 index 与发布目录一致；bundle 内确认含折叠/说明文案与 `v1.0.38.20` 版本号；后端 `/health` UP（1.0.38.19，本次未动后端）。
- 页面手工点击展开/收起：未逐条人工验收，待用户在浏览器复核（测试/111111）。

## 风险
- 无接口/权限/数据库变更；纯展示层。回滚只回前端 root，无数据影响。
