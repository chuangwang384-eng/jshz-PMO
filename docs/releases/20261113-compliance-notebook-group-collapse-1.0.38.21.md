# 发布记录：合规记事本文档分组折叠、移除页面 H1（前端 1.0.38.21）

- 发布时间：2026-11-13 10:40 左右
- 发布方式：`scripts/deploy-frontend-graceful.ps1 -Apply`（UAC 提权，graceful root 切换）
- ReleaseId：`1.0.38.21-compliance-notebook-group-collapse-20261113`
- 回滚：`frontend/release-metadata/1.0.38.21-...nginx-before.txt` 恢复 root 至 1.0.38.20 ReleaseId

## 变更内容（仅前端）
- `frontend/src/views/EnterpriseCompliance.vue`：移除「企业合规要求」H1 页面标题（Owner 2026-11-13 标记删除）。
- `frontend/src/components/compliance/ComplianceNotebook.vue`：文档分组（如「中国人体-项目执行手册」）默认收起，点击分组头（支持键盘）展开/收起；通过「收藏文档」下拉筛选到具体文档时自动展开；顶部说明文案同步补充「点击文档分组或事项标题可展开详情」。
- 版本：version.txt / frontend/package.json → **1.0.38.21**。

## 验证
- 构建门禁：`vite build` 通过，`is-not-exported` 警告 0。
- 线上核验：Nginx root 已指向新 ReleaseId；`http://127.0.0.1/` 200 且 index 与发布目录一致；bundle 版本 `v1.0.38.21`；新 chunk（EnterpriseCompliance-K27Ij434.js）含新说明文案、不含旧 H1 字符串；后端 /health UP 未动。
- 分组展开/收起点击与筛选联动：未逐项人工验收，待 Owner 浏览器复核。✅已验收关闭（2026-11-19 Owner 批量授权）

## 风险
- 纯展示层，无接口/权限/数据库变更；回滚仅回前端 root 指针。
