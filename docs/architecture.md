---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_06aa5f84cb8011f18078525400de85a5
    ReservedCode1: 7KZhQW6RdkOa2yxFwTSCW6rIs1belFXgwferFD5EHeeW5SKe8eEQ0lo6/Fc0X4ZWQfHmizHabzzoui31JIcVw8DCEF6z0WeOVbC503RMX7jpAvN2AXyVZvfKFyfk047AB/pwYAxjoPo52ZjiAZ5vvaYc6HUX9hLiUd3I+E8TMngZ0rZIQao1Hgp58zY=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_06aa5f84cb8011f18078525400de85a5
    ReservedCode2: 7KZhQW6RdkOa2yxFwTSCW6rIs1belFXgwferFD5EHeeW5SKe8eEQ0lo6/Fc0X4ZWQfHmizHabzzoui31JIcVw8DCEF6z0WeOVbC503RMX7jpAvN2AXyVZvfKFyfk047AB/pwYAxjoPo52ZjiAZ5vvaYc6HUX9hLiUd3I+E8TMngZ0rZIQao1Hgp58zY=
---

# 架构说明

## 1. 总体架构

专家库系统采用**前后端分离**架构，整体依赖公司内网部署：

```
┌────────────────────┐      ┌────────────────────┐      ┌────────────────────┐
│   前端 Vue 3       │ HTTP │   后端 Spring Boot │ JDBC │      MySQL 8.x      │
│  (frontend/)       ├─────►│  (backend/)        ├─────►│   (database/)      │
│  Nginx 静态托管    │  /api │  8080 端口         │      │   expert_system 库 │
└────────────────────┘      └────────────────────┘      └────────────────────┘
```

- 前端为 SPA 应用，构建产物由 Nginx 托管，通过反向代理将 `/api` 请求转发至后端。
- 后端提供 RESTful 接口，采用分层架构（Controller / Service / Mapper / Entity）。
- 数据库存放专家信息（expert_info）、登录账号（sys_user）、下载审计（download_log）、审批流配置（approval_config）、审批申请单（expert_change_request）、审批流转记录（approval_record）以及 RBAC 权限体系（role / permission / role_permission / user_role）等数据，共 **10 张表**。

## 2. 后端分层

| 层 | 职责 | 示例 |
| --- | --- | --- |
| Controller | 接收请求、参数校验、返回结果 | `AuthController` / `ExpertController` / `ApprovalController`（审批流全套：配置、修改/新增申请、照片上传、审批） / `UploadController` / `ExportCompatController` / `UserController` / `AuditController` / `HealthController` / `RoleController`（RBAC 角色 CRUD/启停） / `PermissionController`（RBAC 权限点查询）（共 **10 个**） |
| Service | 业务逻辑、事务控制 | `AuthService` / `ExpertService` / `ApprovalService`（审批核心：白名单字段校验、TMP 临时照片通道、修改/新增分支审批） / `ExportService` / `PhotoStorageService` / `DownloadAuditService` / `UserService` |
| Security | 认证鉴权 | `JwtTokenProvider` / `JwtAuthenticationFilter` / `AuthUser` |
| Mapper | 数据访问（MyBatis-Plus，共 10 个） | `ExpertMapper` / `SysUserMapper` / `DownloadLogMapper` / `ApprovalConfigMapper` / `ExpertChangeRequestMapper` / `ApprovalRecordMapper` / `RoleMapper` / `PermissionMapper` / `RolePermissionMapper` / `UserRoleMapper` |
| Entity | 数据库实体映射（共 10 个） | `Expert` / `SysUser` / `DownloadLog` / `ApprovalConfig` / `ExpertChangeRequest`（含 requestType/requestTypeText）/ `ApprovalRecord` / `Role` / `Permission` / `RolePermission` / `UserRole` |
| Config | 安全、跨域、全局配置 | `SecurityConfig` / `WebConfig` / `GlobalExceptionHandler` / `MybatisPlusConfig` |

## 3. 前端结构

| 目录 | 职责 |
| --- | --- |
| `src/api` | Axios 请求封装（request/auth/expert/user/audit/approval，withCredentials=true，401 拦截跳登录） |
| `src/router` | 路由配置与守卫（未登录跳登录、管理页按 is_admin 控制、requiresAdmin 自动过滤员工菜单） |
| `src/views` | 页面组件（Login/ExpertList/ExpertManage/DownloadAudit/UserManage/MyApproval/ApprovalManage/ApprovalConfig/ChangePassword 等） |
| `src/stores` | Pinia 状态管理（auth） |
| `src/components` | 通用组件（ChangePasswordDialog/PhotoImage/SideMenuItem） |

## 4. 数据模型

数据库：`expert_system`（MySQL 8，utf8mb4），共 **10 张表**，含 RBAC 四表（role / permission / role_permission / user_role）：

| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| `expert_info` | 专家主表（18 字段） | id、name、unit、department、title、research_direction、expertise_area、contact、profile、id_card、bank_card、photo、bank_branch、id_card_front、id_card_back、create_time、update_time、update_by |
| `sys_user` | 登录账号 | username=姓名、password（BCrypt）、is_admin（管理员名单兼容标记）、must_change_password（强制改密）、wecom_userid（预留）、status |
| `download_log` | 下载审计 | 下载人、专家、时间、类型、IP；selected 导出逐专家一条、全量/md 单条 |
| `approval_config` | 审批流配置 | flow_key（UNIQUE，EXPERT_MODIFY）、flow_name、enabled、approver_role |
| `expert_change_request` | 审批申请单 | expert_id（可空，新增申请通过后回填）、expert_name、applicant、change_data（JSON）、reason、status（0待审/1通过/2驳回）、request_type（0修改/1新增）、approve_by、approve_comment |
| `approval_record` | 审批流转记录 | request_id、node_name、action（SUBMIT/APPROVE/REJECT）、operator、comment、create_time |
| `role` | RBAC 角色表 | id、code（UNIQUE）、name、description、status、builtin（1=内置禁删禁改；SUPER_ADMIN 代码内全权锁定不可改）、create_time、update_time |
| `permission` | RBAC 权限点表 | id、code（可重复，一码多路径）、name、type（1菜单/2接口）、method、path、parent_code；UNIQUE(method,path) |
| `role_permission` | 角色-权限关联表 | id、role_id、permission_code；UNIQUE(role_id,permission_code) |
| `user_role` | 用户-角色关联表 | id、user_id、role_id；UNIQUE(user_id,role_id) |

> RBAC 说明：内置角色 4 个（SUPER_ADMIN 代码内全权锁定 / ADMIN 业务全权 / APPROVER 审批 / EMPLOYEE 无绑定）；permission.code 允许重复（一码多路径），UNIQUE(method,path) 约束接口唯一；鉴权链路为 JWT claims.roles + DbPermissionFilter 按 method+path 匹配权限码（命中校验、未命中放行）。

## 5. 安全设计（内网）

- 系统仅监听内网地址，通过 Nginx 限制来源 IP 与访问路径。
- 认证鉴权已实现：Spring Security + JWT（jjwt 0.11.5），Token 存 httpOnly cookie（SameSite=Lax、无 Secure，适配内网 http），登录写入、登出清除；密码 BCrypt 哈希，初始密码强制改密。
- 无凭证访问受保护接口返回 401（配置 AuthenticationEntryPoint）；CORS 白名单 `http://<PRIVATE_IP>:5173` / `http://localhost:5173`，allowCredentials=true。
- 敏感字段脱敏：身份证号/银行卡号查看页默认前 3 后 4；下载行为写入 download_log 审计。
- 数据库账号密码通过 `MYSQL_*` 环境变量注入，禁止提交到代码仓库。

## 6. 后续扩展方向

- 二期：企微扫码登录（接口已预留 wecom_userid 字段）
- 敏感字段数据补录（开户行、身份证正反面照片）
- Nginx 托管 dist 生产部署（后端已注册 Windows 服务 NSSM 开机自启）
- 项目管理 / 供应商管理模块开发（一级菜单占位）
*（内容由AI生成，仅供参考）*
