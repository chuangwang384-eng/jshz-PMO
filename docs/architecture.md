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
- 数据库存放专家信息（expert_info）、登录账号（sys_user）、下载审计（download_log）、用户操作日志（operation_log，B2）、审批流配置（approval_config）、通用申请主表（application，「我的申请」改版）、审批流转记录（approval_record）、审批申请字段模板（approval_template，R-B4）、知识星球文档（knowledge_document / knowledge_doc_version）、文档收藏（knowledge_favorite，C3）、消息通知（notification）以及 RBAC 权限体系（role / permission / role_permission / user_role）等数据，共 **17 张表**（expert_change_request 为历史备份表：存量 31 条已迁移至 application，新写入全部走 application，旧表保留不动）。

## 2. 后端分层

| 层 | 职责 | 示例 |
| --- | --- | --- |
| Controller | 接收请求、参数校验、返回结果 | `AuthController` / `ExpertController` / `ApplicationController`（通用申请全套，URL 沿用 /api/approval/*：配置、修改/新增申请、照片上传、审批、转办/加签/可转办用户列表、my-applications 我的申请、my-requests 旧路径兼容） / `UploadController` / `ExportCompatController` / `UserController` / `AuditController`（下载留痕 + 通用用户操作日志） / `HealthController` / `RoleController`（RBAC 角色 CRUD/启停） / `PermissionController`（RBAC 权限点查询） / `KnowledgeController`（知识星球：文档上传/分页检索（category 可选=全分类、按权限过滤下架文档）/版本更新/下架/永久删除/版本历史/文件下载预览） / `NotificationController`（消息中心：本人列表（isRead/type 组合筛选）/ 未读数 / 单条已读 / 批量已读 / 全部已读 / 单条删除 / 批量删除，全部仅作用于本人消息） / `ApprovalTemplateController`（R-B4 审批申请字段模板：启用列表登录可用 / 全部列表+增删改启停 approval:config / 字段清单）（共 **13 个**） |
| Service | 业务逻辑、事务控制 | `AuthService` / `ExpertService` / `ApplicationService`（通用申请核心：类型枚举分发、biz_data JSON 读写、白名单字段校验、TMP 临时照片通道、修改/新增分支审批、转办/加签与 assignee 审批权校验、审批/提交通知埋点） / `ExportService` / `PhotoStorageService` / `DownloadAuditService`（下载留痕，双写 operation_log） / `OperationLogService`（B2 通用敏感操作日志：统一写入（try-catch 降级）+ 分页查询） / `UserService`（含角色调整/密码重置通知埋点） / `KnowledgeDocumentService`（知识星球业务，含下架/永久删除通知埋点） / `KnowledgeDocStorageService`（文档落盘与校验，含物理删除） / `NotificationService`（通用消息通知：写入事件、本人列表/未读数/已读标记） |
| Security | 认证鉴权 | `JwtTokenProvider` / `JwtAuthenticationFilter` / `AuthUser` |
| Mapper | 数据访问（MyBatis-Plus，共 16 个） | `ExpertMapper` / `SysUserMapper` / `DownloadLogMapper` / `OperationLogMapper`（B2）/ `ApprovalConfigMapper` / `ApplicationMapper`（通用申请）/ `ApprovalRecordMapper` / `ApprovalTemplateMapper`（R-B4） / `RoleMapper` / `PermissionMapper` / `RolePermissionMapper` / `UserRoleMapper` / `KnowledgeDocumentMapper` / `KnowledgeDocVersionMapper` / `KnowledgeFavoriteMapper`（C3） / `NotificationMapper` |
| Entity | 数据库实体映射（共 16 个） | `Expert` / `SysUser` / `DownloadLog`（含 docId）/ `OperationLog`（B2）/ `ApprovalConfig` / `Application`（通用申请，含 biz_data 解析展示字段 currentAssignee/extraApprover/records 等）/ `ApprovalRecord`（含 applicationId）/ `ApprovalTemplate`（R-B4，含 fields 解析展示）/ `Role` / `Permission` / `RolePermission` / `UserRole` / `KnowledgeDocument`（含 content 正文/downloadCount/isFavorite）/ `KnowledgeDocVersion` / `KnowledgeFavorite`（C3）/ `Notification` |
| Config | 安全、跨域、全局配置 | `SecurityConfig` / `WebConfig` / `GlobalExceptionHandler` / `MybatisPlusConfig` |

## 3. 前端结构

| 目录 | 职责 |
| --- | --- |
| `src/api` | Axios 请求封装（request/auth/expert/user/audit/approval/knowledge/notification，withCredentials=true，401 拦截跳登录） |
| `src/router` | 路由配置与守卫（未登录跳登录、meta.permissions 权限码控制路由访问与菜单过滤；一级菜单：专家库/知识星球/个人中心/供应商管理/项目管理/管理员设置等；知识星球父路由 /knowledge-planet 挂父页组件（KnowledgePlanetHome 内含 <router-view/> 渲染四个分类子页；父路由显示上传中心+全分类总览，子路由仅渲染本分类列表），点击标题跳父路由、点击箭头展开二级菜单；个人中心 /profile 四子路由：my-approvals（我的申请，「我的申请」改版由「我的审批」更名，路由路径保留供通知跳转）、notifications（消息中心，登录即可）、approval/manage（审批管理，permissions: approval:audit——已从管理员设置迁移至此，普通员工不可见）、change-password（修改密码）；管理员设置父路由 /admin（redirect /admin/experts/manage，meta.permissions 为 5 码并集：expert:manage/audit:view/approval:config/user:manage/role:manage），6 个子路由：experts/manage（专家管理）、audit/downloads（用户操作日志）、approval/config（审批流配置）、approval/templates（审批模板管理，permissions: approval:config）、users（用户管理）、roles/manage（角色管理）） |
| `src/views` | 页面组件（Login/ExpertList/ExpertManage/DownloadAudit（B2 改造为通用用户操作日志页：用户名模糊/操作类型/时间范围筛选，含历史下载记录）/UserManage/MyApproval（「我的申请」：调 /api/approval/my-applications 仅本人数据，含审批人/审批时间列，30s 轮询刷新）/ApprovalManage（含转办/加签按钮与选人弹窗，读写 application 主表）/MessageCenter（消息中心：Tab 全部/未读/已读 + 10 类类型筛选组合、复选框批量已读/批量删除、单条删除、点击已读+按 jump_path 跳转、分页）/ApprovalConfig/ApprovalTemplateManage（R-B4 审批模板管理）/ChangePassword/KnowledgePlanetHome（知识星球父页：上传中心+全分类总览，管理员含永久删除）/MeetingExecution/AssociationCompliance/EnterpriseCompliance/CustomerPreferences 等） |
| `src/stores` | Pinia 状态管理（auth、notification（30 秒轮询未读数+新消息轻提示）） |
| `src/components` | 通用组件（ChangePasswordDialog/PhotoImage/SideMenuItem/KnowledgeDocList（知识文档列表，allowUpload=false 隐藏上传按钮供子页复用，管理员含永久删除）/NotificationBell（顶栏铃铛+未读红点+消息列表）） |

## 4. 数据模型

数据库：`expert_system`（MySQL 8，utf8mb4），共 **17 张表**，含 RBAC 四表（role / permission / role_permission / user_role）、消息通知表（notification）、审批申请字段模板表（approval_template，R-B4）、知识文档收藏表（knowledge_favorite，C3）、用户操作日志表（operation_log，B2）与通用申请主表（application，「我的申请」改版）：

| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| `expert_info` | 专家主表（18 字段） | id、name、unit、department、title、research_direction、expertise_area、contact、profile、id_card、bank_card、photo、bank_branch、id_card_front、id_card_back、create_time、update_time、update_by |
| `sys_user` | 登录账号 | username=姓名、password（BCrypt）、is_admin（管理员名单兼容标记）、must_change_password（强制改密）、wecom_userid（预留）、status |
| `download_log` | 下载审计 | 下载人、专家、时间、类型、IP；selected 导出逐专家一条、全量/md 单条；C4 起知识文档下载（download_type=knowledge_doc）记 doc_id 与文档标题；B2 起每次写入同时双写 operation_log（download_log 保留，下载次数统计仍依赖本表） |
| `approval_config` | 审批流配置 | flow_key（UNIQUE，EXPERT_MODIFY）、flow_name、enabled、approver_role |
| `application` | 通用申请主表（「我的申请」改版） | applicant_id、applicant、application_type（EXPERT_MODIFY/EXPERT_CREATE，代码枚举可扩展）、status（0待审/1通过/2驳回/3撤回，四态通用）、biz_data（JSON 业务详情：专家申请 {expertId, expertName, changes, reason, templateId}，照片 TMP: 语义保留）、approve_by、approve_comment、approve_time、current_assignee（B2）、extra_approver（B2）；索引 idx_applicant/idx_applicant_id/idx_status/idx_application_type |
| `expert_change_request` | 审批申请单（**历史备份表**：「我的申请」改版后存量已迁移 application，新写入停用，保留不动） | expert_id、expert_name、applicant、change_data（JSON）、reason、status、request_type、template_id、approve_by、current_assignee（B2）、extra_approver（B2）、approve_comment |
| `approval_record` | 审批流转记录 | request_id（存量=旧表 id 同值保留）、application_id（关联 application.id，新链路写入，存量按同 id 回填）、node_name、action（SUBMIT/APPROVE/REJECT/WITHDRAW/TRANSFER/ADD_APPROVER）、operator、comment、create_time |
| `approval_template` | 审批申请字段模板（R-B4） | template_name（UNIQUE）、fields（JSON 中文键数组，⊆ 可编辑白名单+照片字段）、enabled（1启用/0停用，停用不可用于新申请）、create_time、update_time |
| `role` | RBAC 角色表 | id、code（UNIQUE）、name、description、status、builtin（1=内置禁删禁改；SUPER_ADMIN 代码内全权锁定不可改）、create_time、update_time |
| `permission` | RBAC 权限点表 | id、code（可重复，一码多路径）、name、type（1菜单/2接口）、method、path、parent_code；UNIQUE(method,path) |
| `role_permission` | 角色-权限关联表 | id、role_id、permission_code；UNIQUE(role_id,permission_code) |
| `user_role` | 用户-角色关联表 | id、user_id、role_id；UNIQUE(user_id,role_id) |
| `knowledge_document` | 知识星球文档主表 | id、title、category、file_name、file_path、file_type、file_size、content（C1 正文，抽取截断存储）、version、upload_by、status（1上架/0下架）、create_time、update_time |
| `knowledge_doc_version` | 知识星球文档版本历史 | id、doc_id、version、file_name、file_path、file_type、file_size、upload_by、create_time |
| `knowledge_favorite` | 知识文档收藏（C3） | id、doc_id、user_id、create_time；UNIQUE(doc_id,user_id) |
| `notification` | 消息通知中心 | id、user_id（接收人）、type（approval_result / approval_todo / approval_withdrawn / approval_transferred / approval_assigned / role_changed / password_reset / doc_off_shelf / doc_deleted / doc_updated）、title、content、jump_path（点击跳转前端路由，可为空）、is_read（0未读/1已读）、create_time |
| `operation_log` | 用户敏感操作日志（B2） | user_id、username、action_type（DOWNLOAD/LOGIN/LOGOUT/CHANGE_PASSWORD/RESET_PASSWORD/CREATE_USER/UPDATE_USER/DELETE_USER/ASSIGN_ROLES/ROLE_PERMISSION/CREATE_EXPERT/UPDATE_EXPERT/DELETE_EXPERT/IMPORT_EXPERT/CREATE_DOC/UPDATE_DOC/OFF_SHELF_DOC/DELETE_DOC）、action_desc、target_type、target_id、ip、source_id（download_log.id 历史迁移标记，UNIQUE(action_type,source_id) 防重复迁移）、create_time |

> `expert_info.update_by` 写入语义：直接编辑（ExpertService.update，由超管/具备 expert:edit 权限用户调用）记操作者本人；审批通过（ApprovalService.approveModify / approveCreate）记申请单申请人 `expert_change_request.applicant`（发起修改/新增的人），审批人仅记录于 `approve_by`。`update_time` 由数据库 `ON UPDATE CURRENT_TIMESTAMP` 自动维护。

> RBAC 说明：内置角色 4 个（SUPER_ADMIN 代码内全权锁定 / ADMIN 业务全权 / APPROVER 审批 / EMPLOYEE 无绑定）；permission.code 允许重复（一码多路径），UNIQUE(method,path) 约束接口唯一；鉴权链路为 JWT claims.roles + DbPermissionFilter 按 method+path 匹配权限码（命中校验、未命中放行）。知识星球上传/更新/版本/文件下载接口登录即可（不设权限码）；列表 GET /api/knowledge/docs 登录即可且 category 可选（不传=全分类），并按权限过滤 status：具备 `knowledge:manage`（ADMIN/超管）返回上架+下架，普通员工仅上架；下架接口 DELETE /api/knowledge/docs/{id} 与永久删除接口 DELETE /api/knowledge/docs/{id}/permanent 均需接口权限码 `knowledge:manage`（已绑定 ADMIN），业务层 AuthUser.hasPermission 二次校验；永久删除同时删除主记录、全部版本历史与磁盘物理文件，不可恢复。

> 知识星球增强（2026-10-26，C1/C3/C4）：① C1 内容检索——knowledge_document.content 存上传/更新时抽取的正文（PDFBox/POI，截断 200k 字符），GET /api/knowledge/docs 的 keyword 扩展为 title/file_name/content 三列 LIKE；前端 placeholder「按标题 / 内容关键字检索」。② C3 收藏——knowledge_favorite 表（UNIQUE(doc_id,user_id)），接口 POST/DELETE/GET /api/knowledge/favorites[/{docId}]（登录即可，userId 取当前用户防越权；路径避开 permission 表 DELETE /api/knowledge/docs/** 通配规则），PUT /api/knowledge/docs/{id} 更新成功后向全部收藏者（排除操作人）写 doc_updated 通知；前端列表/总览操作列收藏按钮即时切换，知识星球父页「全部文档/我的收藏」切换。③ C4 下载次数——download_log 加 doc_id 列，附件下载（download=1，预览不计）写 download_type=knowledge_doc 记录，列表接口单次 GROUP BY 聚合返回 downloadCount，前端「下载次数」列仅上传人本人与管理员（knowledge:manage）可见数值，他人显示 —。

> R-B4 审批申请字段模板说明：表 `approval_template`（template_name 唯一、fields 为中文键 JSON 数组、enabled 启停）；员工端 GET /api/approval/templates 返回启用模板（登录即可），管理端 GET /api/approval/templates/all 与 POST/PUT/DELETE /api/approval/templates[/{id}] 复用权限码 `approval:config`（permission 表一码多路径）；提交申请（POST /api/approval/requests、/api/approval/create-request）带可选 templateId：传入时校验 changes 字段集合 ⊆ 模板字段且模板启用，并落库 expert_change_request.template_id；不传走原逻辑，历史申请兼容（template_id NULL）。模板字段必须 ⊆ ApprovalService.WHITELIST_FIELDS + PHOTO_FIELDS（服务端校验）；「专家新增」种子模板覆盖 CREATE_REQUIRED_FIELDS 全部必填项；前端 ExpertList 修改/新增弹窗提供模板下拉，选择后仅渲染并提交模板字段，新增弹窗仅展示覆盖必填项的模板；管理员设置菜单新增「审批模板管理」页（/admin/approval/templates）。

> B2 审批转办/加签（2026-10-26，语义已确认：加签=或签，目标用户须有 approval:audit）：expert_change_request 新增 current_assignee / extra_approver 两列（可空，历史单兼容）。转办 POST /api/approval/requests/{id}/transfer（body {targetUser}，权限码 approval:audit）：仅 status=0 可转，操作者须有 approval:audit 且满足 current_assignee 为空或等于本人；转办后仅被转办人可审批（approve/reject 前经 checkAssignee 校验），写 approval_record（action=TRANSFER，nodeName=审批转办，comment=转给【XX】）并通知被转办人（approval_transferred）与申请人。加签 POST /api/approval/requests/{id}/add-approver（body {targetUser, comment?}）：或签语义——原审批人与被加签人任一 approve 即通过、任一 reject 即驳回（不改状态机），已加签不可重复加签，写 ADD_APPROVER 记录并通知被加签人（approval_assigned）。可转办/加签用户列表 GET /api/approval/assignable-users（approval:audit）：返回启用且拥有 approval:audit 的用户 [{id,username}]。前端 ApprovalManage 待审批行新增「转办/加签」按钮（行归属他人时隐藏）与选人弹窗，「当前审批人」列展示归属；MyApproval 进度时间线支持 TRANSFER/ADD_APPROVER 节点。

> 用户操作日志（B2 改版）：新表 operation_log 统一记录敏感操作——下载/导出（DownloadAuditService 双写，download_log 保留兼容，历史数据已由迁移脚本以 action_type=DOWNLOAD 并入，source_id+UNIQUE 防重复迁移）、专家新增/修改/删除/导入、知识文档上传/更新/下架/永久删除、登录/登出/修改密码/重置密码、用户增改删/分配角色/角色权限变更。统一写入入口 OperationLogService.record()（try-catch 降级，日志失败不影响主业务）；查询接口 GET /api/audit/operations?username=&actionType=&startTime=&endTime=&page=&size（audit:view，时间支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，纯日期结束取当日 23:59:59）。前端管理员设置二级菜单「下载留痕」更名为「用户操作日志」（路由 /admin/audit/downloads、组件、audit:view 权限码均不变），页面支持用户名模糊/操作类型/时间范围组合筛选，操作类型 el-tag 着色。

> 消息中心说明（2026-10-26 升级）：表 `notification`（结构不变，无迁移），接口 GET /api/notifications（本人分页列表，isRead 0/1 与 type 组合筛选）、GET /api/notifications/unread-count、POST /api/notifications/{id}/read（单条已读）、POST /api/notifications/read-batch（body {ids}，返回实际已读条数）、POST /api/notifications/read-all、DELETE /api/notifications/{id}（单条物理删除）、DELETE /api/notifications/batch（body {ids}，返回删除条数），全部登录即可且仅作用于本人消息（越权操作静默无效/返回 0）；五类事件埋点：approval_result（审批结果→申请人）、approval_todo（新申请待审→审批角色用户）、role_changed（角色调整→被调整用户）、password_reset（密码重置→被重置用户）、doc_off_shelf / doc_deleted（文档下架/永久删除→上传人）；前端双入口：顶栏 NotificationBell 铃铛弹层（底部「查看全部」进消息中心页）+ 个人中心「消息中心」页（/profile/notifications，MessageCenter.vue）；store 30 秒轮询未读数并在新增未读时轻提示，store 30 秒轮询未读数并在新增未读时轻提示，点击通知按 jump_path 跳转（审批结果跳 /profile/my-approvals?status=1|2，新申请待审跳 /admin/approval/manage，角色调整跳 /profile，文档事件跳 /knowledge-planet，密码重置跳 /profile/change-password；后端 ApprovalService.notifyApprovalTodo 埋点已同步下发 `/admin/approval/manage`）。

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

> 「我的申请」通用化改版（2026-10-26）：新表 `application` 为通用申请主表（applicant_id/applicant/application_type/status 四态/biz_data JSON/approve_by/approve_comment/approve_time + B2 转办加签两列），业务差异存 biz_data（专家申请 {expertId, expertName, changes, reason, templateId}，照片 TMP: 语义保留）；类型为代码枚举 ApplicationType（一期 EXPERT_MODIFY/EXPERT_CREATE，未来加枚举+处理器+前端表单扩展请假等业务）。`expert_change_request` 存量 31 条已一次性迁移（保留原 id，approval_record.application_id 按同 id 回填 65/65），旧表保留作历史备份、新写入停用。HTTP 层 ApplicationController（URL 沿用 /api/approval/*，permission 路径规则与前端 api/approval.js 不变）：新接口 GET /api/approval/my-applications（登录即可，applicant_id/applicant 双条件仅本人），GET my-requests 旧路径兼容；提交/审批/驳回/撤回/转办/加签全链路读写 application；审批通过 update_by 记申请人语义不变。approval_record 新链路写 application_id（request_id 同值保留满足非空约束）。前端：MyApproval.vue 升级为「我的申请」（新增审批人/审批时间两列，30s 轮询刷新不重置分页，通知驱动为主轮询兜底）；「审批管理」菜单从管理员设置迁移至个人中心（/profile/approval/manage，permissions: approval:audit，普通员工不可见），/admin 父路由权限并集缩为 5 码；存量通知 jump_path=/admin/approval/manage 已由迁移脚本批量改为 /profile/approval/manage；跳转 /profile/my-approvals?status=1|2 语义不变。
