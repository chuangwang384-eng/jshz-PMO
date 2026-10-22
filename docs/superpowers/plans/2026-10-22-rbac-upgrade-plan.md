# RBAC 权限体系升级与网页标题调整实施计划

- 日期：2026-10-22
- 设计文档：docs/superpowers/specs/2026-10-22-rbac-upgrade-design.md
- 分支：feature/rbac-upgrade
- 目标版本：v1.1.0

## 阶段总览

| 阶段 | 内容 | 产出 |
|---|---|---|
| 0 | 标题修改 | index.html + router afterEach |
| 1 | 后端 RBAC 改造 | 迁移 SQL、实体/Mapper、权限服务、DbPermissionFilter、SecurityConfig、JWT、审批逻辑、Role/Permission Controller |
| 2 | 前端权限改造 | store/router/menu/v-permission、按钮、UserManage、ApprovalConfig、RoleManage.vue |
| 3 | 构建部署 | mvn package、npm run build、重启后端、Nginx dist 更新 |
| 4 | 验收与收口 | 验收清单逐条、文档同步、发布说明 v1.1.0、merge main + tag |

## 阶段 0：标题修改（先行，独立小步）

- [ ] `frontend/index.html` title → 景帅会展内部管理系统
- [ ] `router/index.js afterEach` 后缀与兜底 → 景帅会展内部管理系统
- 验收：浏览器标签页显示新标题

## 阶段 1：后端 RBAC 改造

### 1.1 数据库迁移（幂等脚本 database/migration/20261022_add_rbac_tables.sql）

- [ ] 建 4 表：role、permission、role_permission、user_role（含索引与 UNIQUE 约束，按设计文档 3.1）
- [ ] 插入内置角色 4 个（SUPER_ADMIN/ADMIN/APPROVER/EMPLOYEE，builtin=1）
- [ ] 插入权限点：6 菜单 + 13 接口码（expert:export 含 4 条路径行）
- [ ] 插入默认绑定（按设计 4.3）
- [ ] user_role：admin → SUPER_ADMIN；张三 → EMPLOYEE
- [ ] 脚本可重复执行（INSERT IGNORE / ON DUPLICATE KEY 或先查存在）

### 1.2 后端实体与数据访问

- [ ] 新增实体：Role、Permission、UserRole（RolePermission 按需）
- [ ] 新增 Mapper：RoleMapper、PermissionMapper、UserRoleMapper（MyBatis）
- [ ] 新表 CRUD SQL 与权限点查询 SQL（用户权限码集合：user_role → role_permission → permission）

### 1.3 权限服务与缓存

- [ ] PermissionService：权限点规则加载（method+path→code，Ant 匹配，进程内缓存）
- [ ] 用户权限码集合查询（按 userId 查角色 → 权限码并集，进程内缓存）
- [ ] 角色/权限写操作后立即清除相关缓存（立即生效）

### 1.4 鉴权链路改造

- [ ] JwtTokenProvider：claims 增加 roles
- [ ] JwtAuthenticationFilter / AuthUser：解析 roles，getAuthorities 返回 ROLE_<code> + 权限码
- [ ] 新增 DbPermissionFilter：加载规则 → 每请求 Ant 匹配 method+path → 命中校验权限码，无权限 403
- [ ] SecurityConfig：移除全部 hasRole("ADMIN")，只留白名单 + authenticated（逐一核对 10 条原规则已迁移到 permission 数据）
- [ ] 登录/me 响应：新增 roles、permissions 字段，保留 isAdmin

### 1.5 审批语义改造

- [ ] ApprovalService.submitRequest/submitCreateRequest：isAdmin 硬拒 → 拥有 expert:edit 权限判定
- [ ] 审批接口权限由 approval:audit 权限码接管（approve/reject/GET requests）
- [ ] getConfig() 默认 approverRole 保持可配置，不再强制 ADMIN 语义（默认值仍可 'ADMIN' 兼容）

### 1.6 新后端接口

- [ ] RoleController：GET /api/roles（列表，含启用角色用于审批配置下拉）、POST/PUT/DELETE（内置角色禁删禁改 code）、角色启停
- [ ] PermissionController：GET /api/permissions（菜单/接口分组，供权限树）
- [ ] RolePermission 配置接口：PUT /api/roles/{id}/permissions（绑定权限码集合）
- [ ] UserRole 分配接口：PUT /api/users/{id}/roles（分配角色集合）
- [ ] 鉴权规则：/api/roles/**、/api/permissions/**、/api/users/*/roles 由 role:manage / user:manage 权限码控制（permission 数据驱动）

### 阶段 1 验收

- mvn package 通过；启动后 /health UP；迁移脚本执行成功且幂等
- admin 登录 JWT 含 roles；me 返回 roles/permissions
- 未认证请求受保护接口 401；无权限请求命中权限码返回 403
- 审批接口权限跟随 approval:audit；改 approver_role 后生效

## 阶段 2：前端权限改造

- [ ] store/auth.js：存 roles/permissions；保留 isAdmin getter
- [ ] router/index.js：meta.permissions 替换 requiresAdmin；守卫按权限码；新增角色管理路由；afterEach 标题（阶段 0）
- [ ] utils/menu.js：按权限码过滤
- [ ] directives/permission.js：全局 v-permission
- [ ] ExpertList.vue：按钮权限码化；员工入口改「无 expert:edit」判断
- [ ] ApprovalManage.vue：审批按钮 v-permission="approval:audit"
- [ ] UserManage.vue：「分配角色」弹窗（多选角色，调 PUT /api/users/{id}/roles），移除设为/取消管理员
- [ ] ApprovalConfig.vue：角色下拉动态加载 /api/roles
- [ ] RoleManage.vue：角色 CRUD + 权限树勾选 + 启停（内置角色禁删禁改 code、SUPER_ADMIN 权限锁定）
- 验收：npm run build 通过；模板真实接线（按钮/@click/弹窗均在 template，历史踩坑点）

## 阶段 3：构建部署

- [ ] 停旧 java 进程 → 用 start-backend.bat 重启后端（MYSQL_PASSWORD=111111，禁裸 java -jar）
- [ ] npm run build → 更新 Nginx dist（<LOCAL_PATH> Restart-Service ExpertSystemNginx
- 验收：http://<PRIVATE_IP>/ 访问正常、/api/health UP、admin 登录成功

## 阶段 4：验收与收口

- [ ] 按设计文档第 8 节验收清单逐条执行（含自定义角色端到端 + 审批人切换 + 立即生效 + 回归）
- [ ] 文档同步铁律：architecture.md 更新为 10 表 10 Controller；相关 docs 同步
- [ ] release-notes-v1.1.0.md：变更清单 + 回滚方案（SQL 回滚脚本 + dist 回滚）
- [ ] Git：分批 commit → 合并 main → tag v1.1.0
- [ ] 清理测试数据

## 风险规避（执行时强制）

1. 子代理改前端必须核对 template 真实接线，build 后主代理抽查按钮/事件绑定存在
2. 派发 file-agent 必须要求逐字段核对后端返回结构，禁止臆造接口与字段
3. 后端启动一律走 start-backend.bat；mvn package 前先停旧进程
4. SecurityConfig 放宽后逐条核对原 10 条规则已入 permission 数据
5. 前端新逻辑一律用 permissions，实施后全项目扫描 isAdmin 残留误判点
