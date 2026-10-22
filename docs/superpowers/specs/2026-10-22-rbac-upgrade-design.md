# RBAC 权限体系升级与网页标题调整设计文档

- 日期：2026-10-22
- 项目：jshz-PMO 景帅会展内部管理系统（专家库子系统）
- 阶段：设计（brainstorming 产物）
- 分支：feature/rbac-upgrade

## 1. 背景与目标

### 1.1 现状问题

- `sys_user.is_admin` 名单制两级权限（0 员工 / 1 管理员），无角色表
- 后端 `SecurityConfig.java` 硬编码 `hasRole("ADMIN")` 拦截管理接口，新增权限必须改代码
- `approval_config.approver_role` 字段只写入、从未被读取，审批人实际由安全层写死为 ADMIN
- 前端靠 `isAdmin + meta.requiresAdmin` 过滤菜单、路由与按钮
- 网页标题为「专家库系统」（`index.html` 与路由动态标题后缀）

### 1.2 目标

1. 网页标题改为「景帅会展内部管理系统」
2. 引入角色表 + 权限点，支持「超级管理员 / 管理员 / 审批人 / 普通员工」细分
3. `approval_config.approver_role` 改为真正生效的可配置角色
4. **验收标准：新增角色无需改代码即可配置菜单与接口权限**

## 2. 关键决策（brainstorming 已确认）

| # | 决策点 | 结论 |
|---|---|---|
| 1 | 权限配置入口 | 新建「角色管理」前端页面（超级管理员专属），页面内建角色、勾选权限、分配用户 |
| 2 | 接口权限点粒度 | 细粒度：一个接口一个权限码（method + path → code） |
| 3 | 前端权限控制范围 | 菜单 + 按钮双控制（路由 meta.permissions + v-permission 指令） |
| 4 | 超级管理员定位 | 内置 SUPER_ADMIN 默认全权、不可修改；审批人角色可配置，审批接口动态校验 |
| 5 | 旧字段迁移 | 保留 is_admin 但不再作为鉴权依据；admin → SUPER_ADMIN，张三 → EMPLOYEE |
| 6 | 权限生效时机 | 立即生效：JWT 只带 roles，权限码走 DB + 进程内缓存 |
| 7 | 鉴权架构 | 权限表驱动过滤器（DbPermissionFilter），SecurityConfig 只保留白名单 + authenticated |

## 3. 数据模型

### 3.1 新增 4 张表

**role（角色表）**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| code | VARCHAR(50) UNIQUE | 角色标识：SUPER_ADMIN / ADMIN / APPROVER / EMPLOYEE 等 |
| name | VARCHAR(50) | 显示名 |
| description | VARCHAR(255) | 描述 |
| builtin | TINYINT(1) | 内置角色不可删除（SUPER_ADMIN 强制 builtin） |
| status | TINYINT(1) | 1 启用 / 0 停用 |
| create_time / update_time | DATETIME | |

**permission（权限点表）**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| code | VARCHAR(100) | 权限码可重复：同一权限码可对应多行 method+path |
| name | VARCHAR(100) | 权限名 |
| type | TINYINT(1) | 1=菜单（路由级）；2=接口（方法+路径级） |
| method | VARCHAR(10) NULL | 接口权限：POST/PUT/DELETE/GET |
| path | VARCHAR(200) NULL | 接口权限：Ant 模式路径，如 /api/experts/** |
| module | VARCHAR(50) | 归属模块：expert/approval/audit/user/role/upload |
| sort | INT | 展示排序 |
| create_time / update_time | DATETIME | |
| UNIQUE(method, path) | | 同一方法+路径不重复 |

**role_permission（角色-权限关联）**：`id`、`role_id`、`permission_code`（按权限码关联，支持一码多路径），`UNIQUE(role_id, permission_code)`

**user_role（用户-角色关联）**：`id`、`user_id`、`role_id`，`UNIQUE(user_id, role_id)`，一个用户可挂多角色，权限取并集

### 3.2 既有表变更

- `sys_user`：`is_admin` 字段保留不动（兼容展示），鉴权不再读取
- `approval_config`：`approver_role` 字段不变，取值改为角色 code，审批动作实时校验

## 4. 后端鉴权链路

### 4.1 组件改造

| 组件 | 改动 |
|---|---|
| JWT claims | 新增 `roles`（角色 code 数组），其余 userId/username/isAdmin/mustChangePassword 保留 |
| AuthUser.getAuthorities() | 返回 ROLE_<code> + 权限码集合（DB 实时拉取，带进程内缓存） |
| 新增 DbPermissionFilter（JwtAuthenticationFilter 之后） | ① 加载 permission 表规则（method+path → code）到缓存；② 每请求按方法 + Ant 路径匹配：命中 → 校验用户权限码集合，无权限返回 403；未命中 → 登录即可放行 |
| SecurityConfig | 只保留白名单 permitAll（/health、/ready、POST /api/auth/login、/api/auth/logout、/api/auth/wecom/**），其余全部 authenticated()；原 hasRole("ADMIN") 规则全部移除 |

### 4.2 权限码初始化清单

**菜单权限（type=1）**

| code | 名称 | 对应路由 |
|---|---|---|
| expert:manage | 专家管理 | 原专家管理 |
| audit:manage | 下载留痕 | 下载留痕 |
| approval:manage | 审批管理 | 审批管理 |
| approval:config | 审批流配置 | 审批流配置 |
| user:manage | 用户管理 | 用户管理 |
| role:manage | 角色管理（新） | 新增菜单，仅超管可见 |

**接口权限（type=2）**

| code | method / path |
|---|---|
| expert:add | POST /api/experts |
| expert:edit | PUT /api/experts/** |
| expert:delete | DELETE /api/experts/** |
| expert:import | POST /api/experts/import |
| expert:template | GET /api/experts/template |
| expert:export | GET /api/experts/export、/api/experts/export/employee、/api/export/full、/api/experts/export/full |
| approval:audit | POST /api/approval/requests/*/approve、/*/reject；GET /api/approval/requests |
| approval:config | PUT /api/approval/config |
| audit:view | GET /api/audit/** |
| user:manage | /api/users/** |
| upload:manage | /api/upload/** |
| role:manage | /api/roles/**（新增后端接口） |

### 4.3 内置角色默认绑定

| 角色 | 绑定 |
|---|---|
| SUPER_ADMIN | 全部权限（代码内强制全权，不可修改） |
| ADMIN | 菜单 expert:manage/audit:manage/approval:manage/approval:config/user:manage；接口 expert 全套 + approval:audit/config + audit:view + user:manage + upload:manage（不含 role:manage） |
| APPROVER | 菜单 approval:manage；接口 approval:audit |
| EMPLOYEE | 无权限码（专家库主页登录即可见） |

### 4.4 审批语义

- `approve/reject` 由权限码 `approval:audit` 控制，`approver_role` 配置任意角色（默认 APPROVER 可选，迁移期保持 ADMIN 兼容），改配置即实时切换审批人
- `submitRequest / submitCreateRequest`：拥有 `expert:edit` 权限的用户直接编辑免审批；无该权限走审批流（原 isAdmin 硬拒逻辑移除）

## 5. 前端改动

| 组件 | 改动 |
|---|---|
| store/auth.js | 登录/me 响应新增 roles、permissions 入 store；保留 isAdmin getter 兼容 |
| router/index.js | 5 条路由 meta.requiresAdmin → meta.permissions；守卫按权限码校验；新增「角色管理」路由 meta.permissions=['role:manage']；afterEach 标题后缀改「景帅会展内部管理系统」 |
| utils/menu.js | buildMenuTree 按 meta.permissions 与用户权限码取交集过滤 |
| directives/permission.js（新增） | 全局 v-permission 指令，无权限自动移除元素 |
| ExpertList.vue | 新增专家 v-permission="expert:add"；编辑/删除 v-permission="expert:edit"/"expert:delete"；员工入口改按「无 expert:edit」判断（不再用 !isAdmin） |
| ApprovalManage.vue | 审批按钮 v-permission="approval:audit" |
| UserManage.vue | 去掉「设为/取消管理员」，改为「分配角色」弹窗（多选，调 /api/users/{id}/roles） |
| ApprovalConfig.vue | approverRole 下拉从 /api/roles 动态加载 |
| RoleManage.vue（新增） | 角色 CRUD（内置角色禁删禁改 code）+ 权限树勾选（菜单/接口）+ 状态启停 |

## 6. 标题修改

- `frontend/index.html`：`<title>专家库系统</title>` → `<title>景帅会展内部管理系统</title>`
- `router/index.js afterEach`：动态标题后缀 `- 专家库系统` → `- 景帅会展内部管理系统`；兜底默认值同步替换

## 7. 数据迁移

- 新增幂等脚本 `<LOCAL_PATH>`：
  1. 建 4 张新表
  2. 插入 4 个内置角色 + 权限点（6 菜单 + 13 接口码）及默认绑定
  3. `user_role`：admin → SUPER_ADMIN；张三 → EMPLOYEE
  4. `sys_user.is_admin` 保留不动
- `approval_config.approver_role` 迁移保持 ADMIN 不变（兼容），交付后可改为 APPROVER 或自定义角色验证

## 8. 验收标准

1. 浏览器标签页标题显示「景帅会展内部管理系统」
2. admin 登录为超级管理员：可见「角色管理」菜单、全权操作
3. 不写代码新建角色：创建「区域经理」→ 勾选 expert:manage + expert:add/expert:edit → 分配账号 → 该账号可见专家管理菜单、可新增/编辑，删除/导出不可见且 403
4. 审批人可配置：approver_role 改 APPROVER → APPROVER 账号可审批；改自定义角色 → 仅该角色可审批
5. EMPLOYEE 员工账号：无管理菜单/按钮，提交审批正常
6. 权限变更立即生效：取消某角色 expert:add 后，下一次请求即 403，无需重新登录
7. 回归：登录/退出、专家检索、下载导出、照片上传、审批全流程正常，/health UP

## 9. Git 与文档策略

- 独立分支 `feature/rbac-upgrade`；本设计文档单独 commit
- 实施后按功能点分批 commit；发布说明 v1.1.0（含回滚方案：SQL 回滚脚本 + 前端 dist 回滚）
- 合并 main 打 tag v1.1.0
- 文档同步铁律：architecture.md 等同步为 10 表、10 个 Controller

## 10. 风险与规避

| 风险 | 规避 |
|---|---|
| JWT 体积增大（cookie 4KB） | 只加 roles 数组，权限码不入 token |
| 权限变更不及时 | 角色/权限写操作后立即清进程内缓存 |
| SecurityConfig 放宽后漏拦 | 原 10 条硬编码规则逐一迁移为 permission 数据，验收逐条打点 |
| 前端 isAdmin 残留误判 | 保留 getter 过渡，新逻辑全用 permissions，实施后全项目扫描 |
| 内置角色误删 | builtin=1 禁删禁改 code；SUPER_ADMIN 权限强制锁定 |
