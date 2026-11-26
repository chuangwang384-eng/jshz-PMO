# B2 审批转办/加签 + 用户操作日志改版 设计文档

- 日期：2026-10-26
- 状态：已实现（代码 + 迁移脚本），待执行迁移与发布
- 关联：`database/migration/20261026_add_b2_transfer_operation_log.sql`

## 1. 背景与目标

审批人原为「角色级」、无归属概念。本设计为申请单引入「当前审批人」，并交付：

1. **R-B2-1 审批转办**：当前审批人可将待审批单移交指定用户，转办后仅被转办人可审批；
2. **R-B2-2 审批加签**：追加一名用户共同审批；
3. **菜单更名**：管理员设置二级菜单「下载留痕」→「用户操作日志」（路由/组件/权限码不变）；
4. **敏感操作日志扩展**：新表 operation_log 统一记录下载/导出、专家/文档写操作、登录/登出/改密/角色权限变更；
5. **日志页改造**：用户名模糊 / 操作类型 / 时间范围组合筛选。

## 2. 已确认的业务决策（2026-10-26 用户拍板）

| 决策项 | 结论 |
| --- | --- |
| B2-2 加签语义 | **或签**：原审批人与被加签人任一 approve 即通过、任一 reject 即驳回；不引入中间态。会签留待独立迭代 |
| 转办/加签目标用户 | **必须拥有 approval:audit 权限码**（与现有审批权限体系一致），选人列表仅列出有该权限的启用用户 |
| B3 撤回通知 / B4 模板必填 | 不在本次范围（待确认项维持现状） |

## 3. 数据模型

### 3.1 expert_change_request 加列（幂等 migration，information_schema 判列）

- `current_assignee VARCHAR(64) NULL`：转办后有值；**为空 = 未转办，任何拥有 approval:audit 的用户可审批（向后兼容）**；
- `extra_approver VARCHAR(64) NULL`：加签审批人（或签）；已加签不可重复加签；未来多级加签可改 JSON。

### 3.2 新表 operation_log

id / user_id / username / action_type（统一枚举）/ action_desc / target_type / target_id / ip / source_id / create_time。

- `source_id`：历史迁移自 download_log 时记来源行 id，`UNIQUE(action_type, source_id)` + `INSERT IGNORE` 保证迁移幂等；新写入为 NULL（MySQL 唯一键多 NULL 允许）；
- 索引：idx_username / idx_action_type / idx_create_time；
- 历史 download_log 全量迁移为 action_type=DOWNLOAD（download_log 保留：下载次数统计仍依赖，双写不丢数）。

## 4. 接口设计

| 接口 | 权限 | 说明 |
| --- | --- | --- |
| POST /api/approval/requests/{id}/transfer | approval:audit（permission 表新增规则） | body {targetUser}；仅 status=0；操作者须有 approval:audit 且 current_assignee 为空或=本人；目标用户存在/启用/有 approval:audit；不能转给自己 |
| POST /api/approval/requests/{id}/add-approver | approval:audit | body {targetUser, comment?}；同上校验 + 已加签拒绝、不能加签给自己 |
| GET /api/approval/assignable-users | approval:audit | 启用且拥有 approval:audit 的用户 [{id, username}] |
| GET /api/audit/operations | audit:view（既有 GET /api/audit/** 规则覆盖） | username 模糊 / actionType 精确 / startTime / endTime（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，纯日期结束取 23:59:59）/ page / size |

动作记录：TRANSFER（nodeName=审批转办，comment=转给【XX】）、ADD_APPROVER（nodeName=审批加签）写入 approval_record。

通知：转办→被转办人（approval_transferred）+ 申请人提醒；加签→被加签人（approval_assigned）。

## 5. 埋点范围（OperationLogService.record，try-catch 降级）

- 下载/导出：DownloadAuditService.record / recordKnowledgeDoc 双写（覆盖全部既有下载路径）；
- 专家：新增 / 修改 / 删除 / 导入（ExpertController）；
- 文档：上传 / 更新 / 下架 / 永久删除（KnowledgeController）；
- 账号：登录 / 登出 / 修改本人密码（AuthController）、新增/修改/删除用户/重置密码/分配角色（UserController）、角色权限绑定变更（RoleController）。

## 6. 前端

- ApprovalManage.vue：待审批行新增「当前审批人」列、「转办/加签」按钮（行归属他人时隐藏）+ 选人弹窗（仅列出有审批权限用户）；或签语义在弹窗内提示；
- MyApproval.vue：进度时间线支持 TRANSFER（warning）/ ADD_APPROVER（primary）节点；
- router/index.js：audit/downloads meta.title 改「用户操作日志」；
- DownloadAudit.vue：改造为通用日志页（用户名输入 + datetimerange + 操作类型下拉 + 查询/重置；列：用户名 / 操作类型 el-tag / 描述 / 时间 / IP），历史下载记录并入展示。

## 7. 兼容与回滚

- 未转办/未加签单行为完全不变（current_assignee 为空走原逻辑）；
- GET /api/audit/downloads 保留；download_log 双写保留；
- 回滚：代码回退即可；DDL 仅加列/新表，无破坏性变更（回滚不必删列删表，保留不影响旧版本运行）。

## 8. 验收对照

| 验收项 | 状态 |
| --- | --- |
| ① 转办后被转办人可审批、原审批人被拒 | 代码实现，待运行验证 |
| ② 时间线可见转办/加签节点 | 代码实现，待运行验证 |
| ③ 未转办单任何审批角色仍可审（无回退） | 代码实现（checkAssignee 空值直通），待运行验证 |
| ④ 敏感操作落 operation_log（含 IP） | 代码实现，待运行验证 |
| ⑤ 历史下载记录可查 | 迁移脚本实现，待执行迁移验证 |
| ⑥ 日志写入失败不影响主业务 | record() try-catch 降级 |
| ⑦ 筛选组合 / 分页 / 重置 | 前端实现，npm run build 通过，待运行验证 |
| ⑧ 后端编译 / 前端构建 | mvn compile 通过 / npm run build 通过 |
