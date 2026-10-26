# 设计文档：未分配角色用户默认普通员工（EMPLOYEE）

- 日期：2026-10-26
- 项目：jshz-PMO（景帅会展专家库系统，Java17 + Spring Boot3 + Vue3 + MySQL8）
- 状态：已批准，待实现

## 一、背景与目标

系统当前无任何"默认角色"逻辑：管理员创建用户（`UserService.createUser`）与自动注册（`AuthService.autoRegisterAndLogin`）均不写入 `user_role` 关联表，导致"未分配角色"在系统中表现为角色空集——用户可正常登录、浏览公开内容，但不具备任何角色权限。

目标：所有未分配角色的用户一律默认为"普通员工"（`EMPLOYEE`），保证任何用户都不会长期处于无角色状态。

## 二、现状要点（只读核查结论）

| 项 | 现状 | 位置 |
|---|---|---|
| 用户表 | `sys_user` 无 role 字段，RBAC 经 `user_role(user_id, role_id)` 关联 | `SysUser.java` L1-69 |
| 角色取值 | `SUPER_ADMIN(1) / ADMIN(2) / APPROVER(3) / EMPLOYEE(4)`，均 builtin=1 | role 表 / `20261022_add_rbac_tables.sql` |
| 创建用户 | 仅写 username/password/isAdmin 等，不写 user_role，无默认角色 | `UserService.java` L44-66 |
| 自动注册 | 只建 sys_user，不绑定角色 | `AuthService.java` L82-118 |
| 角色分配 | `PUT /api/users/{id}/roles` 覆盖式分配，前端新建用户不选则不落库 | `UserController.java` L78-84 |
| 存量无角色用户 | 宋庆鑫(154)、测试(156)、张三(205)，共 3 人 | 真实库 user_role 核对 |

## 三、改动范围

| # | 文件 | 改动 |
|---|------|------|
| 1 | `backend/src/main/java/com/jshz/expert/service/UserService.java` | `createUser` 中按 `role.code='EMPLOYEE'` 查库取 roleId，幂等写入 `user_role` |
| 2 | `backend/src/main/java/com/jshz/expert/service/AuthService.java` | `autoRegisterAndLogin` 中同样绑定 EMPLOYEE |
| 3 | Role 查询能力 | 在 RoleService / RoleMapper 增加（或复用）按 code 查询角色，**禁止硬编码 roleId=4** |
| 4 | `database/migration/20261026_add_default_employee_role.sql` | 一次性迁移，存量 3 个无角色用户补绑 EMPLOYEE |

前端零改动：创建用户入参与交互不变，仍保持"创建后通过现有分配角色功能调整"的流程。

## 四、关键行为约定

1. **幂等保护**：写入 `user_role` 前先检查是否已存在 `(user_id, role_id)` 记录，存在则跳过，避免重复绑定与唯一键冲突。
2. **角色缺失兜底**：若 `EMPLOYEE` 角色在库中不存在（理论不发生，builtin 角色），记录告警日志并继续完成用户创建，不因角色异常阻断主流程。
3. **接口契约不变**：`POST /api/users` 与自动注册入参不变；`UserRequest` DTO 不改；前端无需改动。
4. **迁移脚本幂等**：使用 `INSERT ... SELECT ... WHERE NOT EXISTS` 形式，可重复执行不报错、不产生重复绑定。
5. **事务边界**：创建用户与绑定默认角色在同一事务内，失败整体回滚，不产生"有用户无角色"的中间态。

## 五、验收标准

1. 管理员创建新用户（不分配角色）→ 查 `user_role` 存在 EMPLOYEE 绑定；登录后拥有员工权限（可浏览专家库、提交审批申请，无管理入口）。
2. 自动注册新用户 → `user_role` 同样存在 EMPLOYEE 绑定。
3. 执行迁移脚本后 → 宋庆鑫(154)、测试(156)、张三(205) 均绑定 EMPLOYEE。
4. 既有已分配角色用户（admin、已绑定专家/审批人角色的用户）不受影响，角色数据不变。
5. 数据层核查：浏览器实测 + 直接查库核对 `user_role` 真实绑定，防止"代码对、数据脏"的越权类问题。

## 六、影响面与风险

- 无破坏性变更；仅新增默认绑定逻辑与一次性迁移。
- 风险点：`EMPLOYEE` 角色若被改名/停用，需同步调整查询条件；迁移脚本依赖 3 个存量用户 ID 存在，脚本已按 `WHERE NOT EXISTS` 幂等处理。
