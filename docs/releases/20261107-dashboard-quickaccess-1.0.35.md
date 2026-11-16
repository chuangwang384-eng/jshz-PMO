# 2026-11-07 工作台导航整合发布（1.0.35，后端+前端）

状态：**COMPLETE**（部署与真机验证通过；含 1 张新表迁移）。

## 变更内容
按 [需求 v1.1](../requirements/2026-11-07-工作台导航与快捷访问整合-需求文档.md)（Owner 决策：页头改造不做、图标沿用现有库、Ctrl K 仅工作台页、「更多」/编辑态按概念图）：

**后端**（1.0.35，蓝绿发布）：
- 新表 `user_quick_access`（迁移 `20261107_add_user_quick_access.sql`，幂等，先经临时库两轮验证再上生产）。
- `GET/PUT /api/dashboard/quick-access`：按账号全量覆盖保存；白名单复用 `DashboardLayoutService.allowed`（改为 public）；非法/重复/超限拒绝整单（400/403）。
- 单测 `QuickAccessServiceTest` 4 用例通过。

**前端**（1.0.35-dashboard-quickaccess-20261107）：
- 合并卡片 `.nav-card`：GlobalSearchBar（去标题/去搜索按钮/回车即搜/`Ctrl K` 徽标）+ 新组件 QuickAccessBar。
- QuickAccessBar：图标+名称（element-plus icons）、≤6 平铺 + 「更多 ▾」、编辑态（点击增删、原生拖拽排序、恢复默认、保存失败保留内容）；候选集 = CARD_CATALOG page.* 经 canShow 过滤；配置持久化按账号。
- Ctrl K 仅工作台页（document keydown，输入法组合态忽略，卸载时移除监听）。
- 设计变量落 `.dashboard`（#2563EB/#F8FAFC/#0F172A/#64748B/#E2E8F0/圆角/阴影）。
- App.vue 页头零改动（红线）。

## 发布过程
- 构建：build-all.ps1 隔离构建（jar 69 依赖）；前端 `VITE_DASHBOARD_LAYOUT_V2=true` 构建（开关教训已吸取）。
- 迁移：先临时库 qa_migration_check 双跑验证幂等并核结构，后应用生产 expert_system（加表，无既有对象改动）。
- 后端：logs/deploy-1.0.35-wrapper.ps1（UAC 提权，凭据运行时解析）→ deploy-backend.ps1 全 11 阶段 OK，/health=1.0.35 UP，JWT 未变（用户免重登）。
- 前端：deploy-frontend-graceful.ps1（UAC），root → `releases/1.0.35-dashboard-quickaccess-20261107`，master PID 39504 不变。

## 真机验证（测试账号，ZCode 浏览器）
- 入口「正式版v1.0.35」；合并卡片渲染正常：搜索框+Ctrl K 徽标 + 6 个图标入口 + 更多 + 编辑；页头未变。
- 编辑快捷访问：移除「知识星球」→ 保存提示成功 → 刷新后仍生效（服务端持久化验证）；「恢复默认」→ 保存 → 入口还原。
- 编辑态「暂无可添加候选」（10 候选全启用时正确）；删除入口后候选出现。
- 搜索回归：输入「验收」结果面板正常展开（期间发现面板会遮住下方编辑按钮，Esc/清空后恢复，属面板层级既有表现）。
- Ctrl K：本验证环境无法合成可信键盘事件（CUA/Playwright press 均不产生 keydown），改用 JS 派发 ctrl+k 事件验证处理器：聚焦成功（activeElement=global-keyword）。**真实键盘行为建议人工按一次确认**。
- 布局编辑回归：进入编辑（保存/恢复默认/取消/手柄）→ 取消退出，全部正常。

## 代码入库说明（披露）
本次提交 0795991 除本发布文件外，补齐了 1.0.31–1.0.34 期间已部署但未入库的布局编辑依赖（DashboardLayoutService、dashboardLayout.js、两个布局迁移、pom 构建参数），否则仓库无法复现本 jar。知识库解析、UserAuthState 等其他未入库文件非本发布依赖，保持原状待责任会话补交。

## 回滚
- 前端：`release-metadata/1.0.35-dashboard-quickaccess-20261107.rollback.conf.txt` 写回 + 优雅重载。
- 后端：回滚 jar 由 deploy-backend 归档（KeepVersions=5）；`user_quick_access` 表保留无害。
