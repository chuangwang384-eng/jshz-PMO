# 供应商「服务评价」权限放开至全员 — 发布记录 1.0.37.22

- 日期：2026-11-09
- 版本：`1.0.37.22`
- 类型：权限修复 + 前端小改（DB 数据变更 + 后端重启刷新缓存 + 前端 graceful）
- 需求来源：Owner 反馈「希望用户能在服务评价里对供应商进行评价」

## 1. 问题

详情页「服务评价」Tab 只有统计与列表，右上角没有「新增评价」入口；即便手工调接口也会 403。

根因不是前端漏写，而是**权限口径过窄**：

| 层 | 现象 | 原因 |
| --- | --- | --- |
| 后端 | `POST /api/supplier/*/reviews` 返回 403 | `permission` 表第 195 行把该接口绑在 `supplier:manage`，只有 `ADMIN` 绑定该码 |
| 前端 | 按钮不渲染 | 按钮用 `v-permission="'supplier:manage'"`，非 ADMIN 无该码 |

`supplier:manage` 同时覆盖「编辑/删除供应商、设主图、删图、跨城批量解绑」，不能整码下放给员工，否则会连带放开危险操作。

## 2. 改动

### 2.1 数据（迁移 `database/migration/20261109_supplier_review_open.sql`）

1. **规则改造**：`permission` 第 195 行 `POST /api/supplier/*/reviews` 的 `code` 由 `supplier:manage` 改为 `supplier:review`，名称改「供应商-服务评价」。
   - 必须原地改码而不是新增一行：`DbPermissionFilter` 遍历规则、**命中第一条即校验并 break**，若旧 `supplier:manage` 行还在，非 ADMIN 仍会先命中旧行 403。
2. 兜底 `INSERT IGNORE` 同一规则，防止行被误删。
3. `supplier:review` 绑定**全部非 SUPER_ADMIN 角色**（ADMIN / APPROVER / EMPLOYEE / KNOWLEDGE_MAINTAINER / PROJECT_OWNER）；SUPER_ADMIN 在代码里是 `*` 全权，无需绑定。
4. 迁移幂等：`DELETE-if-new-exists` + `UPDATE-if-new-missing` + `INSERT IGNORE` + 绑定 `NOT EXISTS`，已连跑两次验证（规则行恒为 1、绑定恒为 5）。

### 2.2 前端（`frontend/src/views/supplier/SupplierDetail.vue`）

- 新增 `canReview = computed(() => authStore.hasPermission('supplier:review'))`，两处入口改用 `v-if="canReview"`：
  - Tab 右上角「新增评价」；
  - 0 条评价空态新增**「写第一条评价」**按钮（原空态只有说明文字，用户看不到任何入口）。
- 不用 `v-permission` 指令的原因：该指令只在 `mounted` 判一次，而 `App.vue` 启动后 `fetchMe()` 才刷新权限，首次加载会误删按钮且不会重新挂载。
- 提交成功后仍走 `fetchDetail()` 刷新统计与列表，无需整页刷新。

## 3. 发布链路

`temp\deploy-13722.cmd`（管理员）三步顺序执行，任一步失败即停：

1. `scripts\apply-migration-20261109-supplier-review-open.ps1` — 迁移 + 校验（规则已切码、无残留 `supplier:manage` 行、5 个角色均绑定），日志 `logs\migration-20261109-supplier-review-open.log`
2. `scripts\release-backend.ps1` — 蓝绿发布 1.0.37.22，**目的是刷新 `PermissionService` 的规则/用户权限缓存**（代码零改动）
3. `temp\run-deploy-frontend-13722.ps1` — 前端 graceful 切 root 到 `1.0.37.22-supplier-review-open-20261109`

顺序不可调：迁移先落库是安全的（缓存未刷新前行为不变），后端重启后新规则才生效，前端最后上线。

## 4. 构建

| 项 | 结果 |
| --- | --- |
| 前端 | `builds/1.0.37.22-build-20261109/dist`，34.07s / 0 错误；`SupplierDetail-DM3MEusk.js` 含「写第一条评价」「新增评价」「supplier:review」；`编辑布局` 仍在 `DashboardView-BGNIlZ5I.js`；客户页「查看关联客户」仍在 |
| 后端 | `builds/1.0.37.22-build-20261109_105220/expert-system.jar`，version=1.0.37.22，含 `BOOT-INF/` 与 logback 校验通过 |

## 5. 回滚

- 权限回滚：
  ```sql
  UPDATE `permission` SET `code`='supplier:manage', `name`='供应商-添加评价'
   WHERE `code`='supplier:review' AND `method`='POST' AND `path`='/api/supplier/*/reviews';
  DELETE FROM `role_permission` WHERE `permission_code`='supplier:review';
  ```
  改完需重启后端刷新缓存。
- 前端：`scripts/rollback-frontend.ps1`，旧 release `1.0.37.21-supplier-detail-tabs-20261109` 完整保留。
- 后端：`backend/releases/` 下保留上一版 jar，可蓝绿回切。

## 6. 验收要点

1. 员工账号进详情页「服务评价」：右上角有「新增评价」，0 条评价时中间有「写第一条评价」。
2. 点开弹窗选星 + 填内容 → 提交成功，评分、条数、分布条、列表同步刷新。
3. 员工账号仍**看不到**编辑/删除供应商、设主图、删图、跨城清单（`supplier:manage` 未被放宽）。
4. 评价后列表页「仅看已评价」筛选能命中该供应商。
