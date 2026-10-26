# v1.0.6 发布说明（2026-10-26）

> 构建版本 **1.002**（后端 pom / 前端 package.json 同步升版）。本版包含 v1.0.5 之后的三批变更：审批申请撤回、安全整改（凭据治理）、R-B4 审批申请字段模板。

## 新增功能

### R-B4 常用申请类型字段模板
- 管理员可预设申请模板（「联系方式变更」「职称变更」「专家新增」3 个种子模板已随迁移写入）；管理入口：管理员设置 → 审批模板管理（/admin/approval/templates，权限码 approval:config）。
- 员工在专家库修改申请弹窗与新增专家申请弹窗顶部可选择模板，选择后表单仅展示模板字段、提交仅限模板字段；不选模板保持原全字段流程。
- 提交带 templateId 时服务端校验 changes 字段集合 ⊆ 模板字段且模板启用；`expert_change_request` 新增 `template_id` 列记录来源（历史申请 NULL 兼容）。
- 新表 `approval_template`（14 张表），新增 Controller `ApprovalTemplateController`（13 个），接口权限复用 `approval:config`。

### 审批申请撤回（v1.0.5 后补充）
- 员工对「待审批」状态的本人申请可撤回（POST /api/approval/requests/{id}/withdraw）：状态置为已撤回(3)、清理临时照片、写 WITHDRAW 时间线、通知审批人；撤回单不可再审批。

## 安全整改

- 仓库内所有明文数据库密码与 JWT 密钥清除：`application.yml` JWT 密钥改 `${JWT_SECRET:}` 注入（未注入启动失败属预期 fail-fast）；deploy-backend.ps1 凭据改会话环境变量并在预检强校验；文档全部改占位符描述。
- 生产数据库密码经 MySQL 双密码机制零中断轮换；NSSM 服务环境变量升级为 `MYSQL_PASSWORD + JWT_SECRET`（rotate-backend-credentials.ps1 执行）。**本版发布后新 JWT 密钥生效，全员需重新登录。**
- 剩余动作：观察稳定后执行 `rotate-backend-credentials.ps1 -DiscardOldPassword` 弃用旧数据库密码。

## 修复与优化

- 侧栏父菜单箭头并入 el-sub-menu 原生展开热区，修复一级菜单点击错位/热区过小问题（SideMenuItem 重构）。
- README 修正"init.sql 含 13 表"误述（实际 init.sql 6 表 + migration 7 表 = 13，现 14）；事实基线同步为 14 表 / 13 Controller。
- AGENTS.md 规范文件定名入库（完善版替代原版），修复文档断链。

## 数据库变更

- 执行 `database/migration/20261026_add_approval_template.sql`（幂等）：建 `approval_template` 表、`expert_change_request` 加 `template_id` 列、4 条模板接口权限码、3 个种子模板。

## 发布记录

- 后端：2026-10-26 10:46 R-1 蓝绿发布 1.002 成功（回滚点 `backend/releases/1.002-20261026_104551`）。
- 前端：2026-10-26 10:51 R-2 原子发布 1.002 成功（回滚点 `frontend/releases/1.002-20261026_105127`）。

## 验证情况（2026-10-26 发布冒烟）

- mvn compile / vite build 通过；8080 与经 Nginx /api/health 均 200；登录链路正常（新 JWT 密钥签发）。
- GET /api/approval/templates 返回 3 个种子模板；GET /api/approval/template-fields 返回字段清单。
- 验收③实测：用「联系方式变更」模板提交「简介」字段被拒（"字段「简介」不在模板范围内"）；模板内字段仍走原敏感格式校验。
- 不传 templateId 的旧提交路径逻辑零改动（代码路径 templateId=null 直接跳过模板校验）。

*（内容由AI生成，仅供参考）*
