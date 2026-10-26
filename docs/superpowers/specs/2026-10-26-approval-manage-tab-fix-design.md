# 审批管理页面 Tab 切换数据加载修复设计

- 日期：2026-10-26
- 模块：管理员设置 - 审批管理（/admin/approval/manage）
- 状态：已批准，进入实施

## 背景与问题

审批管理页面（`frontend/src/views/ApprovalManage.vue`）包含"待处理"与"已处理"两个 Tab：

- 待处理 Tab 调 `GET /api/approval/requests?status=0`；
- 已处理 Tab 调 `GET /api/approval/requests?statuses=1,2,3`。

实测现象：**待处理为空**是因为数据库 `expert_change_request` 表 status=0 记录数为 0（27 条均为已处理状态），查询链路正常，非代码缺陷；**已处理为空**是前端缺陷：`el-tabs` 未绑定 `@tab-change`，且 `onMounted` 只调用 `fetchPending()`，首次切换到"已处理"Tab 时没有任何代码触发 `fetchDone()`，`doneList` 恒为空数组。

后端查询链路、接口路径、状态值映射、权限过滤均已排查无误。

## 修复方案（方案 A）

仅修改 `frontend/src/views/ApprovalManage.vue` 一个文件：

1. `el-tabs` 增加 `@tab-change="handleTabChange"` 事件绑定；
2. 新增 `handleTabChange(tabName)` 方法：`tabName === 'pending'` 调 `fetchPending()`，否则调 `fetchDone()`（与现有 `refresh()` 的按 Tab 刷新逻辑保持一致）；
3. `onMounted` 在 `fetchPending()` 之后追加一次 `fetchDone()` 预加载，保证首屏切换 Tab 时数据已在。

## 影响范围与风险

- 仅前端一个 Vue 组件，无后端接口契约变更、无数据库表结构变更，风险低；
- 复用现有 `fetchPending` / `fetchDone` 逻辑，不引入新依赖；
- 不处理"待处理为空"（数据库无 status=0 数据，属业务现状）。

## 验收标准

- `npm run build` 构建成功；
- 重启 `ExpertSystemNginx` 后，切到"已处理"Tab 显示 27 条 status∈{1,2,3} 记录；
- 待处理 Tab 仍为空（符合业务现状，无 status=0 数据）；
- 同步 release notes 并提交独立 git commit。
