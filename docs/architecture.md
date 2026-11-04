---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_a6cf28bfd1fd11f1a301525400cd780f
    ReservedCode1: nLPLo4GCKePvYzweekjXf6qjRm5YspFoJ5vDxuBstLvRwKAGPdm572Bw9QJ32hY57bQ/GC2fOPIRpWG9yk5KeUijE2/PQOTtOvBZS6ZsQvnOPn5UQy6DDSeFGbZi+WFW2r27no+if8ilIPok8PxS+UbB6iWxFQ/uvraqWqvTqu3x6I18czkJSQpR92o=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_a6cf28bfd1fd11f1a301525400cd780f
    ReservedCode2: nLPLo4GCKePvYzweekjXf6qjRm5YspFoJ5vDxuBstLvRwKAGPdm572Bw9QJ32hY57bQ/GC2fOPIRpWG9yk5KeUijE2/PQOTtOvBZS6ZsQvnOPn5UQy6DDSeFGbZi+WFW2r27no+if8ilIPok8PxS+UbB6iWxFQ/uvraqWqvTqu3x6I18czkJSQpR92o=
---

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
- 数据库存放专家信息（expert_info）、登录账号（sys_user）、下载审计（download_log）、用户操作日志（operation_log，B2）、审批流配置（approval_config）、通用申请主表（application，「我的申请」改版）、审批流转记录（approval_record）、审批申请字段模板（approval_template，R-B4）、知识星球文档（knowledge_document / knowledge_doc_version）、文档收藏（knowledge_favorite，C3）、消息通知（notification）、供应商管理（supplier / supplier_type / supplier_city / supplier_price_item / supplier_review / supplier_hotel_print_rel，2026-10-26）、企微通讯录同步（wecom_department / wecom_user_department / wecom_sync_log，2026-10-27）以及 RBAC 权限体系（role / permission / role_permission / user_role）等数据，共 **26 张表**（expert_change_request 为历史备份表：存量 31 条已迁移至 application，新写入全部走 application，旧表保留不动）。

## 2. 后端分层

| 层 | 职责 | 示例 |
| --- | --- | --- |
| Controller | 接收请求、参数校验、返回结果 | `AuthController` / `ExpertController` / `ApplicationController`（通用申请全套，URL 沿用 /api/approval/*：配置、修改/新增申请、照片上传、审批、转办/加签/可转办用户列表、my-applications 我的申请、my-requests 旧路径兼容） / `UploadController` / `ExportCompatController` / `UserController` / `AuditController`（下载留痕 + 通用用户操作日志） / `HealthController` / `SupplierController`（供应商管理：档案/行价/评价/图片全员登录即可，类型/城市字典管理 supplier:dict，酒店-打印店多对多绑定） / `RoleController`（RBAC 角色 CRUD/启停） / `PermissionController`（RBAC 权限点查询） / `KnowledgeController`（知识星球：文档上传/分页检索（category 可选=全分类、按权限过滤下架文档）/版本更新/下架/永久删除/版本历史/文件下载预览） / `NotificationController`（消息中心：本人列表（isRead/type 组合筛选）/ 未读数 / 单条已读 / 批量已读 / 全部已读 / 单条删除 / 批量删除，全部仅作用于本人消息） / `ApprovalTemplateController`（R-B4 审批申请字段模板：启用列表登录可用 / 全部列表+增删改启停 approval:config / 字段清单） / `WecomContactController`（企微通讯录同步管理：触发同步（dryRun 试算）/同步状态/最近记录/部门树/未绑定清单/人工指认绑定，权限 wecom:sync，2026-10-27） / `WecomDirectoryController`（普通员工个人通讯录：GET /api/wecom/directory/mine 返回本人根到本部门链路+本部门成员，登录即可，不暴露全量架构，2026-10-27）（共 **16 个**） |
| Service | 业务逻辑、事务控制 | `AuthService` / `ExpertService` / `ApplicationService`（通用申请核心：类型枚举分发、biz_data JSON 读写（含 oldData 旧值快照写入与按查看者脱敏返回）、白名单字段校验、TMP 临时照片通道、修改/新增分支审批、转办/加签与 assignee 审批权校验、审批/提交通知埋点） / `ExportService` / `PhotoStorageService` / `DownloadAuditService`（下载留痕，双写 operation_log） / `OperationLogService`（B2 通用敏感操作日志：统一写入（try-catch 降级）+ 分页查询，2026-10-27 新增 WECOM_SYNC / WECOM_BIND 审计类型） / `UserService`（含角色调整/密码重置通知埋点） / `KnowledgeDocumentService`（知识星球业务，含下架/永久删除通知埋点） / `KnowledgeDocStorageService`（文档落盘与校验，含物理删除） / `NotificationService`（通用消息通知：写入事件、本人列表/未读数/已读标记） / `ContactSyncService`（企微通讯录同步核心：DryRun 试算不写业务数据、diff 绑定/新建/停用、并发护栏 409、部门树/未绑定清单/人工指认绑定/个人通讯录 getMyDirectory，2026-10-27） / `WecomSyncScheduler`（@Scheduled 每日 02:30，受 sync-cron-enabled 开关控制，首次引入定时任务） / `WecomSyncTrigger`（手动/定时同步统一入口，异步执行） |
| Security | 认证鉴权 | `JwtTokenProvider` / `JwtAuthenticationFilter` / `AuthUser` |
| Mapper | 数据访问（MyBatis-Plus，共 25 个） | `ExpertMapper` / `SysUserMapper` / `DownloadLogMapper` / `OperationLogMapper`（B2）/ `ApprovalConfigMapper` / `ApplicationMapper`（通用申请）/ `ApprovalRecordMapper` / `ApprovalTemplateMapper`（R-B4） / `RoleMapper` / `PermissionMapper` / `RolePermissionMapper` / `UserRoleMapper` / `KnowledgeDocumentMapper` / `KnowledgeDocVersionMapper` / `KnowledgeFavoriteMapper`（C3） / `NotificationMapper` / `SupplierMapper` / `SupplierTypeMapper` / `SupplierCityMapper` / `SupplierPriceItemMapper` / `SupplierReviewMapper` / `SupplierHotelPrintRelMapper` / `WecomDepartmentMapper` / `WecomUserDepartmentMapper` / `WecomSyncLogMapper`（企微三表，2026-10-27） |
| Entity | 数据库实体映射（共 25 个） | `Expert` / `SysUser` / `DownloadLog`（含 docId）/ `OperationLog`（B2）/ `ApprovalConfig` / `Application`（通用申请，含 biz_data 解析展示字段 currentAssignee/extraApprover/records/oldData/photoChanges 等）/ `ApprovalRecord`（含 applicationId）/ `ApprovalTemplate`（R-B4，含 fields 解析展示）/ `Role` / `Permission` / `RolePermission` / `UserRole` / `KnowledgeDocument`（含 content 正文/downloadCount/isFavorite）/ `KnowledgeDocVersion` / `KnowledgeFavorite`（C3）/ `Notification` / `Supplier`（含类型/城市/评分展示字段）/ `SupplierType`（含 bizKey）/ `SupplierCity` / `SupplierPriceItem` / `SupplierReview` / `SupplierHotelPrintRel` / `WecomDepartment` / `WecomUserDepartment` / `WecomSyncLog`（企微三表，2026-10-27） |
| Config | 安全、跨域、全局配置 | `SecurityConfig` / `WebConfig`（/api/files/** 正式照片库、/api/files-tmp/** 审批临时照片库，均登录可读）/ `GlobalExceptionHandler` / `MybatisPlusConfig` / `WecomProperties`（企微配置绑定，2026-10-27）/ `WecomAsyncConfig`（异步同步线程池，2026-10-27） |

## 3. 前端结构

| 目录 | 职责 |
| --- | --- |
| `src/api` | Axios 请求封装（request/auth/expert/user/audit/approval/knowledge/notification/supplier/wecom，withCredentials=true，401 拦截跳登录；wecom 于 2026-10-27 新增：triggerSync/getSyncStatus/getRecentSyncLogs/getDepartments/getUnbound/bindWecom/getWecomConfig/getMyDirectory） |
| `src/router` | 路由配置与守卫（未登录跳登录、meta.permissions 权限码控制路由访问与菜单过滤；一级菜单：专家库/知识星球/个人中心/供应商管理（SupplierList 卡片列表 + /supplier/:id 详情页，menu:false 不进菜单）/项目管理/管理员设置等；知识星球父路由 /knowledge-planet 挂父页组件（KnowledgePlanetHome 内含 <router-view/> 渲染四个分类子页；父路由显示上传中心+全分类总览，子路由仅渲染本分类列表），点击标题跳父路由、点击箭头展开二级菜单；个人中心 /profile 父路由挂 ProfileHome（内含 <router-view/>，父路径 /profile 渲染 ProfileCard 信息卡主页；2026-10-30 修复入口回归：cda0d27 改造曾删除主页信息卡与修改密码入口）；子路由：overview（我的主页，ProfileCard 信息卡：用户名/角色（roleNames 中文优先回退编码）/账号ID/注册时间 + 修改密码按钮复用 ChangePasswordDialog）、my-approvals（我的申请，「我的申请」改版由「我的审批」更名，路由路径保留供通知跳转）、notifications（消息中心，登录即可）、approval/manage（审批管理，permissions: approval:audit——已从管理员设置迁移至此，普通员工不可见）、directory（通讯录，登录即可，个人通讯录页）、announcements / announcements/manage（公告与公告管理，权限码 announcement:manage；announcement-detail menu:false 不进菜单）；管理员设置父路由 /admin（redirect /admin/experts/manage，meta.permissions 为 5 码并集：expert:manage/audit:view/approval:config/user:manage/role:manage），7 个子路由：experts/manage（专家管理）、audit/downloads（用户操作日志）、approval/config（审批流配置）、approval/templates（审批模板管理，permissions: approval:config）、users（用户管理）、roles/manage（角色管理）、wecom（企微通讯录同步，permissions: wecom:sync）） |
| `src/views` | 页面组件（Login（2026-10-27 起按 /api/auth/wecom/config 的 loginMode 自适应姓名/姓名或手机号/手机号文案）/ExpertList/ExpertManage/DownloadAudit（B2 改造为通用用户操作日志页：用户名模糊/操作类型/时间范围筛选，含历史下载记录）/UserManage（2026-10-27 增手机号/部门/企微绑定列与绑定操作）/MyApproval（「我的申请」：调 /api/approval/my-applications 仅本人数据，含审批人/审批时间列，30s 轮询刷新）/ApprovalManage（含转办/加签按钮与选人弹窗，读写 application 主表）/MessageCenter（消息中心：Tab 全部/未读/已读 + 10 类类型筛选组合、复选框批量已读/批量删除、单条删除、点击已读+按 jump_path 跳转、分页）/ApprovalConfig/ApprovalTemplateManage（R-B4 审批模板管理）/KnowledgePlanetHome（知识星球父页：上传中心+全分类总览，管理员含永久删除）/MeetingExecution/AssociationCompliance/EnterpriseCompliance/CustomerPreferences/WecomSyncManage（企微通讯录同步管理：立即同步+DryRun 试算变更清单+确认实跑、同步结果卡片、最近记录表、组织架构树 el-tree、未绑定清单+人工绑定，2026-10-27）/profile/ProfileHome（个人中心父页：<router-view/> + 父路径渲染 ProfileCard 信息卡主页，2026-10-30 修复入口回归）/profile/ProfileCard（个人中心信息卡：用户名/角色/账号ID/注册时间 + 修改密码弹窗，2026-10-30 新增，由 ProfileHome 与 /profile/overview 子路由复用）/profile/Directory（个人中心通讯录：根到本部门链路+本部门成员，手机号脱敏，2026-10-27）等） |
| `src/stores` | Pinia 状态管理（auth、notification（30 秒轮询未读数+新消息轻提示）） |
| `src/components` | 通用组件（ChangePasswordDialog（普通模式可关闭、force-mode 强制改密警示）/PhotoImage/SideMenuItem/KnowledgeDocList（知识文档列表，allowUpload=false 隐藏上传按钮供子页复用，管理员含永久删除）/NotificationBell（顶栏铃铛+未读红点+消息列表）） |

## 4. 数据模型

数据库：`expert_system`（MySQL 8，utf8mb4），共 **26 张表**，含 RBAC 四表、供应商六表（supplier / supplier_type / supplier_city / supplier_price_item / supplier_review / supplier_hotel_print_rel）（role / permission / role_permission / user_role）、企微通讯录同步三表（wecom_department / wecom_user_department / wecom_sync_log，2026-10-27）、消息通知表（notification）、审批申请字段模板表（approval_template，R-B4）、知识文档收藏表（knowledge_favorite，C3）、用户操作日志表（operation_log，B2）与通用申请主表（application，「我的申请」改版）：

| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| `expert_info` | 专家主表（18 字段） | id、name、unit、department、title、research_direction、expertise_area、contact、profile、id_card、bank_card、photo、bank_branch、id_card_front、id_card_back、create_time、update_time、update_by |
| `sys_user` | 登录账号 | username=姓名、password（BCrypt）、is_admin（管理员名单兼容标记）、must_change_password（强制改密）、wecom_userid（企微唯一身份锚点）、mobile（手机号登录标识，2026-10-27 新增）、position（职务，2026-10-27 新增）、status；唯一索引 uk_wecom_userid（wecom_userid）/ uk_mobile（mobile）/ uk_username（已于 2026-10-27 删除，改由应用层保证未绑定通道内姓名唯一，企微同步可产生重名用户） |
| `download_log` | 下载审计 | 下载人、专家、时间、类型、IP；selected 导出逐专家一条、全量/md 单条；C4 起知识文档下载（download_type=knowledge_doc）记 doc_id 与文档标题；B2 起每次写入同时双写 operation_log（download_log 保留，下载次数统计仍依赖本表） |
| `approval_config` | 审批流配置 | flow_key（UNIQUE，EXPERT_MODIFY）、flow_name、enabled、approver_role |
| `application` | 通用申请主表（「我的申请」改版） | applicant_id、applicant、application_type（EXPERT_MODIFY/EXPERT_CREATE，代码枚举可扩展）、status（0待审/1通过/2驳回/3撤回，四态通用）、biz_data（JSON 业务详情：专家申请 {expertId, expertName, changes, reason, templateId, oldData?}，照片 TMP: 语义保留；oldData 为修改申请提交时的专家旧值快照，审批前后对比用）、approve_by、approve_comment、approve_time、current_assignee（B2）、extra_approver（B2）；索引 idx_applicant/idx_applicant_id/idx_status/idx_application_type |
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
| `operation_log` | 用户敏感操作日志（B2） | user_id、username、action_type（DOWNLOAD/LOGIN/LOGOUT/CHANGE_PASSWORD/RESET_PASSWORD/CREATE_USER/UPDATE_USER/DELETE_USER/ASSIGN_ROLES/ROLE_PERMISSION/CREATE_EXPERT/UPDATE_EXPERT/DELETE_EXPERT/IMPORT_EXPERT/CREATE_DOC/UPDATE_DOC/OFF_SHELF_DOC/DELETE_DOC/WECOM_SYNC/WECOM_BIND）、action_desc、target_type、target_id、ip、source_id（download_log.id 历史迁移标记，UNIQUE(action_type,source_id) 防重复迁移）、create_time |
| `wecom_department` | 企微部门快照（2026-10-27，不回写企微） | dept_id（企微部门 id，根=1，UNIQUE uk_dept_id）、name、parent_dept_id、sort_order、status（1正常/0企微已删除，保留快照）、create_time、update_time |
| `wecom_user_department` | 成员-部门归属（2026-10-27，企微一人可属多部门） | user_id（sys_user.id）、dept_id（企微部门 id）、is_main（1主部门）；UNIQUE(user_id,dept_id)，KEY idx_dept |
| `wecom_sync_log` | 同步审计日志（2026-10-27，每次手动/定时同步一条） | trigger_type（MANUAL/SCHEDULED/DRYRUN）、operator、status（RUNNING/SUCCESS/FAILED）、fetched_users/created_users/updated_users/disabled_users/bound_users/unbound_left、error_msg、detail（DryRun 变更清单 JSON）、start_time/end_time |

> `expert_info.update_by` 写入语义：直接编辑（ExpertService.update，由超管/具备 expert:edit 权限用户调用）记操作者本人；审批通过（ApplicationService.approveModify / approveCreate）记申请单申请人 `application.applicant`（发起修改/新增的人），审批人仅记录于 `approve_by`。`update_time` 由数据库 `ON UPDATE CURRENT_TIMESTAMP` 自动维护。

> RBAC 说明：内置角色 4 个（SUPER_ADMIN 代码内全权锁定 / ADMIN 业务全权 / APPROVER 审批 / EMPLOYEE 无绑定）；permission.code 允许重复（一码多路径），UNIQUE(method,path) 约束接口唯一；鉴权链路为 JWT claims.roles + DbPermissionFilter 按 method+path 匹配权限码（命中校验、未命中放行）。知识星球上传/更新/版本/文件下载接口登录即可（不设权限码）；列表 GET /api/knowledge/docs 登录即可且 category 可选（不传=全分类），并按权限过滤 status：具备 `knowledge:manage`（ADMIN/超管）返回上架+下架，普通员工仅上架；下架接口 DELETE /api/knowledge/docs/{id} 与永久删除接口 DELETE /api/knowledge/docs/{id}/permanent 均需接口权限码 `knowledge:manage`（已绑定 ADMIN），业务层 AuthUser.hasPermission 二次校验；永久删除同时删除主记录、全部版本历史与磁盘物理文件，不可恢复。

> 知识星球增强（2026-10-26，C1/C3/C4）：① C1 内容检索——knowledge_document.content 存上传/更新时抽取的正文（PDFBox/POI，截断 200k 字符），GET /api/knowledge/docs 的 keyword 扩展为 title/file_name/content 三列 LIKE；前端 placeholder「按标题 / 内容关键字检索」。② C3 收藏——knowledge_favorite 表（UNIQUE(doc_id,user_id)），接口 POST/DELETE/GET /api/knowledge/favorites[/{docId}]（登录即可，userId 取当前用户防越权；路径避开 permission 表 DELETE /api/knowledge/docs/** 通配规则），PUT /api/knowledge/docs/{id} 更新成功后向全部收藏者（排除操作人）写 doc_updated 通知；前端列表/总览操作列收藏按钮即时切换，知识星球父页「全部文档/我的收藏」切换。③ C4 下载次数——download_log 加 doc_id 列，附件下载（download=1，预览不计）写 download_type=knowledge_doc 记录，列表接口单次 GROUP BY 聚合返回 downloadCount，前端「下载次数」列仅上传人本人与管理员（knowledge:manage）可见数值，他人显示 —。

> R-B4 审批申请字段模板说明：表 `approval_template`（template_name 唯一、fields 为中文键 JSON 数组、enabled 启停）；员工端 GET /api/approval/templates 返回启用模板（登录即可），管理端 GET /api/approval/templates/all 与 POST/PUT/DELETE /api/approval/templates[/{id}] 复用权限码 `approval:config`（permission 表一码多路径）；提交申请（POST /api/approval/requests、/api/approval/create-request）带可选 templateId：传入时校验 changes 字段集合 ⊆ 模板字段且模板启用，并落库 application.biz_data.template_id；不传走原逻辑，历史申请兼容。模板字段必须 ⊆ ApplicationService.WHITELIST_FIELDS + PHOTO_FIELDS（服务端校验）；「专家新增」种子模板覆盖 CREATE_REQUIRED_FIELDS 全部必填项；前端 ExpertList 修改/新增弹窗提供模板下拉，选择后仅渲染并提交模板字段，新增弹窗仅展示覆盖必填项的模板；管理员设置菜单新增「审批模板管理」页（/admin/approval/templates）。

> B2 审批转办/加签（2026-10-26，语义已确认：加签=或签，目标用户须有 approval:audit）：expert_change_request 新增 current_assignee / extra_approver 两列（可空，历史单兼容）。转办 POST /api/approval/requests/{id}/transfer（body {targetUser}，权限码 approval:audit）：仅 status=0 可转，操作者须有 approval:audit 且满足 current_assignee 为空或等于本人；转办后仅被转办人可审批（approve/reject 前经 checkAssignee 校验），写 approval_record（action=TRANSFER，nodeName=审批转办，comment=转给【XX】）并通知被转办人（approval_transferred）与申请人。加签 POST /api/approval/requests/{id}/add-approver（body {targetUser, comment?}）：或签语义——原审批人与被加签人任一 approve 即通过、任一 reject 即驳回（不改状态机），已加签不可重复加签，写 ADD_APPROVER 记录并通知被加签人（approval_assigned）。可转办/加签用户列表 GET /api/approval/assignable-users（approval:audit）：返回启用且拥有 approval:audit 的用户 [{id,username}]。前端 ApprovalManage 待审批行新增「转办/加签」按钮（行归属他人时隐藏）与选人弹窗，「当前审批人」列展示归属；MyApproval 进度时间线支持 TRANSFER/ADD_APPROVER 节点。

> 用户操作日志（B2 改版）：新表 operation_log 统一记录敏感操作——下载/导出（DownloadAuditService 双写，download_log 保留兼容，历史数据已由迁移脚本以 action_type=DOWNLOAD 并入，source_id+UNIQUE 防重复迁移）、专家新增/修改/删除/导入、知识文档上传/更新/下架/永久删除、登录/登出/修改密码/重置密码、用户增改删/分配角色/角色权限变更。统一写入入口 OperationLogService.record()（try-catch 降级，日志失败不影响主业务）；查询接口 GET /api/audit/operations?username=&actionType=&startTime=&endTime=&page=&size（audit:view，时间支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，纯日期结束取当日 23:59:59）。前端管理员设置二级菜单「下载留痕」更名为「用户操作日志」（路由 /admin/audit/downloads、组件、audit:view 权限码均不变），页面支持用户名模糊/操作类型/时间范围组合筛选，操作类型 el-tag 着色。

> 企微通讯录同步与账号体系改造（2026-10-27，评审通过版实施）：① 同步链路——`WeComApiClient`（纯出站 HTTP，token 缓存+40014/42001 自动重取、user/list_id 分页、department/list_id 或退回 department/list、user/get 逐人节流；日志零 secret/零 access_token，手机号 138****1234 脱敏）→ `ContactSyncService.run`（DryRun 只写变更清单不落业务数据；diff 绑定/新建/停用；wecom_sync_log RUNNING 并发护栏 409；手动同步异步执行返回 logId）→ 落库 wecom_department / wecom_user_department / wecom_sync_log。触发器：手动 POST /api/admin/wecom/sync + 定时 @Scheduled 每日 02:30（WecomSyncScheduler，sync-cron-enabled=false 默认关闭，开启且 corp-id/secret 为空 fail-fast）。② 登录标识三模式——AuthService.login 按 expert.wecom.login-mode（${AUTH_LOGIN_MODE:NAME}）分流：NAME 完全等同现状（姓名+自动注册，回滚档）；COMPAT 手机号格式 `^1[3-9]\d{9}$` 走 mobile 查、否则按姓名查未绑定用户（wecom_userid IS NULL）且姓名自动注册仅对未绑定用户保留；MOBILE 仅手机号+密码、关闭自动注册（账号只来自同步或管理员创建）。is_admin=1 管理员登录豁免（本期配置 AUTH_LOGIN_MODE=MOBILE 时管理员仍可登录）。停用检查/BCrypt/JWT/cookie/强制改密流程均未改动。前端 Login.vue 调 GET /api/auth/wecom/config（白名单，登录前可访问）按 loginMode 切换姓名/姓名或手机号/手机号文案。③ 管理接口（WecomContactController，/api/admin/wecom，权限 wecom:sync，permission 表一码多路径 6 条绑 ADMIN）：POST /sync（{dryRun}，返回 logId）、GET /sync/status（logId 可选）、GET /sync/recent、GET /departments（部门树含人数）、GET /unbound、POST /users/{id}/bind-wecom（{wecomUserId} 人工指认）；手动同步/人工绑定写 operation_log（WECOM_SYNC/WECOM_BIND）。④ 个人通讯录——WecomDirectoryController GET /api/wecom/directory/mine（登录即可，DbPermissionFilter 未命中规则放行），返回本人"根到本部门"链路与本部门成员（手机号脱敏），不暴露全量架构；前端个人中心「通讯录」页 /profile/directory。⑤ 存量绑定策略——自动：企微姓名↔系统未绑定用户姓名双方唯一才自动绑定，歧义进人工指认；人工：管理页指认；不绑定不阻塞登录（COMPAT 下未绑定用户仍姓名登录）。⑥ 迁移脚本 database/migration/20261026_add_wecom_contact_sync.sql（幂等，测试库已执行 3 遍通过）：3 新表 + sys_user.mobile/position 列 + uk_wecom_userid/uk_mobile + 删 uk_username 前备份表 wecom_migration_uk_username_backup + wecom:sync 权限种子。

> 消息中心说明（2026-10-26 升级）：表 `notification`（结构不变，无迁移），接口 GET /api/notifications（本人分页列表，isRead 0/1 与 type 组合筛选）、GET /api/notifications/unread-count、POST /api/notifications/{id}/read（单条已读）、POST /api/notifications/read-batch（body {ids}，返回实际已读条数）、POST /api/notifications/read-all、DELETE /api/notifications/{id}（单条物理删除）、DELETE /api/notifications/batch（body {ids}，返回删除条数），全部登录即可且仅作用于本人消息（越权操作静默无效/返回 0）；五类事件埋点：approval_result（审批结果→申请人）、approval_todo（新申请待审→审批角色用户）、role_changed（角色调整→被调整用户）、password_reset（密码重置→被重置用户）、doc_off_shelf / doc_deleted（文档下架/永久删除→上传人）；前端双入口：顶栏 NotificationBell 铃铛弹层（底部「查看全部」进消息中心页）+ 个人中心「消息中心」页（/profile/notifications，MessageCenter.vue）；store 30 秒轮询未读数并在新增未读时轻提示，store 30 秒轮询未读数并在新增未读时轻提示，点击通知按 jump_path 跳转（审批结果跳 /profile/my-approvals?status=1|2，新申请待审跳 /admin/approval/manage，角色调整跳 /profile，文档事件跳 /knowledge-planet，密码重置跳 /profile/change-password；后端 ApprovalService.notifyApprovalTodo 埋点已同步下发 `/admin/approval/manage`）。

> 项目管理页普通员工 403 修复（2026-10-29，1.009）：EMPLOYEE 等仅有 project:view/create 的用户打开项目管理页即弹"无权限执行该操作"，根因是列表页 onMounted 会读 GET /api/project/config/phases、/config/types（permission 表规则 GET /api/project/config/** → project:manage 拦截 403）和 GET /api/users（user:manage 拦截 403）。修复：① 删除 GET /api/project/config/** 读取规则（迁移脚本 database/migration/20261029_fix_project_page_read_permissions.sql，已在主库执行），config 字典读取登录即可，config 写操作（POST/PUT/DELETE）仍需 project:manage；② 新增 ProjectController GET /api/project/executors（登录即可，返回全部在职用户 id/username，复用 /owners 模式），前端 ProjectList/ProjectDetail 的执行人下拉从直接调 /users 改为调该接口。验收：测试账号（EMPLOYEE）登录后 /api/project、/config/phases、/config/types、/owners、/executors 全部 200；POST /api/project/config/types 仍 403（写权限保持）。

> 项目级操作授权对齐（2026-10-30，1.010）：项目"项目级操作"（推进/回退/取消/资料/预算/合同）业务层授权为 isOwnerOrExecutor（负责人/执行人/超管），与"专家/供应商关联/劳务费/编辑/删除/配置"的 requireManage（project:manage）是两套语义，但前端曾一律用 canManage、permission 表曾把资料/预算/合同接口误映射为 project:manage 规则。修复：① 前端按类拆分按钮条件（项目级操作用 canAdvance，专家供应商类用 canManage），列表页取消按钮用行级 canAdvance；② 后端 pageProjects 每行下发 canAdvance；③ 迁移脚本 20261030_fix_project_owner_executor_rules.sql 删除 6 条项目级操作接口的 project:manage 规则（budget/docs/docs/*/docs/richtext/contracts/contracts/*/sensitive），改由业务层 isOwnerOrExecutor 裁决（DbPermissionFilter 未命中规则即放行到业务层）。

## 5. 安全设计（内网）

- 系统仅监听内网地址，通过 Nginx 限制来源 IP 与访问路径。
- 认证鉴权已实现：Spring Security + JWT（jjwt 0.11.5），Token 存 httpOnly cookie（SameSite=Lax、无 Secure，适配内网 http），登录写入、登出清除；密码 BCrypt 哈希，初始密码强制改密。
- 无凭证访问受保护接口返回 401（配置 AuthenticationEntryPoint）；CORS 白名单 `http://<PRIVATE_IP>:5173` / `http://localhost:5173`，allowCredentials=true。
- 敏感字段脱敏：身份证号/银行卡号查看页默认前 3 后 4；下载行为写入 download_log 审计。
- 企微通讯录同步（2026-10-27）：CorpID / 通讯录同步助手只读 secret 仅由环境变量 `WECOM_CORP_ID` / `WECOM_CONTACT_SECRET` 注入（start-backend.bat + NSSM 服务环境，文档与日志只用占位符，禁止提交真实值）；系统只读企微、不回写；手机号展示与日志一律脱敏（138****1234）；个人通讯录接口仅返回本人部门链路与本部门成员，不暴露全量架构；管理接口全部走 wecom:sync 权限码。
- 数据库账号密码通过 `MYSQL_*` 环境变量注入，禁止提交到代码仓库。

## 6. 后续扩展方向

- 二期：企微扫码 / OTP 登录（2026-10-27 已落地通讯录同步与 userid 绑定基础，扫码登录需备案可信域名后接入，企微返回 userid 与既有绑定天然一致）
- 敏感字段数据补录（开户行、身份证正反面照片）
- Nginx 托管 dist 生产部署（后端已注册 Windows 服务 NSSM 开机自启）
- 项目管理 / 供应商管理模块开发（一级菜单占位）
*（内容由AI生成，仅供参考）*

> 「我的申请」通用化改版（2026-10-26）：新表 `application` 为通用申请主表（applicant_id/applicant/application_type/status 四态/biz_data JSON/approve_by/approve_comment/approve_time + B2 转办加签两列），业务差异存 biz_data（专家申请 {expertId, expertName, changes, reason, templateId}，照片 TMP: 语义保留）；类型为代码枚举 ApplicationType（一期 EXPERT_MODIFY/EXPERT_CREATE，未来加枚举+处理器+前端表单扩展请假等业务）。`expert_change_request` 存量 31 条已一次性迁移（保留原 id，approval_record.application_id 按同 id 回填 65/65），旧表保留作历史备份、新写入停用。HTTP 层 ApplicationController（URL 沿用 /api/approval/*，permission 路径规则与前端 api/approval.js 不变）：新接口 GET /api/approval/my-applications（登录即可，applicant_id/applicant 双条件仅本人），GET my-requests 旧路径兼容；提交/审批/驳回/撤回/转办/加签全链路读写 application；审批通过 update_by 记申请人语义不变。approval_record 新链路写 application_id（request_id 同值保留满足非空约束）。前端：MyApproval.vue 升级为「我的申请」（新增审批人/审批时间两列，30s 轮询刷新不重置分页，通知驱动为主轮询兜底）；「审批管理」菜单从管理员设置迁移至个人中心（/profile/approval/manage，permissions: approval:audit，普通员工不可见），/admin 父路由权限并集缩为 5 码；存量通知 jump_path=/admin/approval/manage 已由迁移脚本批量改为 /profile/approval/manage；跳转 /profile/my-approvals?status=1|2 语义不变。

> 审批「申请前后变化」对比（2026-10-27）：修改申请提交时 `ApplicationService.submitRequest` 从 expert_info 读全量字段写 biz_data.oldData 旧值快照（14 键：11 文本 + 3 照片文件名，新增申请置空）；查询接口返回项增加 `oldData`（身份证/银行卡按查看者脱敏：管理员全量、非管理员前3后4，与 /api/experts/{id} 规则一致）与 `photoChanges`（照片原始文件名供前端预览）；审批通过时把 biz_data.changes 照片键 TMP: 值回填为归档文件名（生效后可预览），且 oldData 快照存在时跳过旧照片物理删除（保证历史单随时可还原「变更前」）。`WebConfig` 新增 /api/files-tmp/** 映射审批临时照片目录（待审批单新照片预览）。前端新增公共组件 `ApprovalDiffDialog.vue`（审批管理 + 我的申请共用，mode=view/approve/reject），审批管理与我的申请操作列加「查看对比」、通过/驳回整合进对比弹窗、摘要列升级「旧值 → 新值」；移除原展开行实时查专家表逻辑（oldValuesMap/handleExpand）。存量迁移 `database/migration/20261027_backfill_application_old_data.sql`：EXPERT_MODIFY 且 status∈{0,2,3} 的存量单用 expert_info 当前值回填 oldData（幂等、无 DDL），status=1 已通过单保持空（前端显示历史单提示）。
*（内容由AI生成，仅供参考）*

## 2026-11-03 审查修复与项目配置接手（开发分支）
- 时间线 GET /api/project/{id}/timeline 现在与项目详情采用相同资源读取校验：project:view-all 或负责人/执行人；非成员返回业务403。
- 供应商删除在事务内锁定供应商行并检查 project_supplier，有任何项目引用则拒删；关联供应商采用相同行锁。历史悬空关联仍显示占位名称/费用，可由授权维护人正常移出，不自动清理历史费用。
- 富文本每次保存生成含UUID的新文件，作为独立记录内容；旧固定路径继续可读。删除记录后仅无其他有效引用才清理富文本文件，物理删除在事务提交后执行；新文件若事务回滚则清理。未迁移或恢复历史上已被覆盖的内容。
- 合规到期提醒由事项关联的收藏者与责任人集合产生：首次收藏无需先创建进度行；取消收藏且非责任人不再提醒；仍为责任人的用户继续保留责任提醒。个人完成或关闭提醒停止该用户提醒。收藏者默认提前3天，只有责任人时使用assignee-days；同用户同事项节点去重。
- 仅提前N天与当天生成提醒，非窗口每日发送；PENDING→SENDING→SENT/FAILED，失败最多3次同步发送尝试。每节点REQUIRES_NEW事务使本地notification及日志原子提交；单节点事务异常最多重试3次，不回滚其他节点。前提：通知与日志使用现有同一MySQL事务数据源。
- 责任人编辑仅通知新增者一次，保留者不重新通知。
- 项目配置图形化接手保留原表格入口及原有权限；新增Vue Flow依赖、统一Pinia配置状态和9个组件，修复阶段拖动、模板异步初始化/排序、兜底来源/影响面与新增部分保存重试。
- 开发验证及限制见 docs/handover/tasks/20261103_fix_takeover.md，尚未部署。

## 2026-11-03 隔离验收补充
项目配置修复watch初始化顺序与VueFlow slot节点参数传递，类型节点使用computed跟随数据更新。发布增加可选GracefulSwitch与不可变前端root切换，保留旧worker和新旧资产兼容；未部署。完整验证与待审批风险见 docs/releases/20261103-review-fixes-approval.md。

## 生产运行状态更新（2026-11-03 15:37）
本轮修复已获批准并以蓝绿方式上线。生产API稳定在8080，影子8081已停止；前端通过Nginx root指向frontend/releases/1.0.22-review-fixes-20261103，新旧hash资源并存；回滚配置元数据位于frontend/release-metadata（webroot外、排除版本控制）。发布证据及业务验收边界见docs/releases/20261103-review-fixes-production.md。

## 企业合规可复用清单 v4（首期已上线，业务体验待复核）
2026-11-03 已交付 `ComplianceV4Controller/Service`、七张执行/模板表及用户切换表实体与迁移，以及 `ComplianceV4ReminderScheduler`；前端企业合规页分为我的执行、规范文档库、模板维护和原有待办/只读历史，个人任务与模板分离。目标生产相关结构和运行版本已在本次发布核实。旧 v3 表/接口/调度保留，按个人模式与 V4 互斥，员工逐人明确确认切换。

页面使用 `/api/compliance/v4` 模板草稿/版本发布、个人清单创建/显式开始/再次使用与任务 PATCH；个人答案、日期、优先级和备注不写回模板。任务更新以 `id + row_version` 条件更新，冲突返回业务 409；列表超末页返回空集，分页限制见 [施工交接第6节](handover/tasks/20261103_compliance_v4_takeover.md)。本轮强化 V4 资源授权，公共账号/JWT/Cookie 语义不变。

完整规格见 [需求](20261103-企业合规可复用清单-需求文档.md) 与 [技术设计](20261103-企业合规可复用清单-技术设计.md)。已补资源权限、个人提醒、逐节点事务与旧版切换；14 项真实 MySQL、22 项服务回归及真实浏览器通过，见 [发布阻断闭环](handover/tasks/20261103_compliance_v4_release_blockers.md)。首期生产上线与技术只读验收已完成，员工业务体验待复核。

V4 由两个默认关闭的环境开关及个人确认控制；新增第八张 `compliance_user_mode` 表（user_id 主键、mode、legacy_snapshot JSON、switched_at、updated_at）。缺行仍是 V3；切换锁定用户并保存个人旧进度快照，新版与旧个人写/到期发送互斥。关闭 V4 不清除个人切换标记，不恢复旧写入/旧提醒。七表迁移及用户开关迁移先经独立库验证，再核实生产元数据并按本次授权执行；不得据此跳过后续环境核查。

资源策略在 V4 内实施：本人执行/事件必须再检来源和项目，维护人不豁免个人隐私；模板由上传者/维护人维护、维护人发布。提醒使用 READ_COMMITTED、每节点 REQUIRES_NEW 与用户/执行/任务/提醒行锁；站内通知和 SENT 原子提交，失败回滚后独立计次，3 次上限。CORS 仅补 PATCH 方法，不改变允许来源。接口、权限、数据字典和发布/回滚限制见 [部署说明](deployment-compliance-v4.md)。

2026-11-03 21:44生产更新：八张v4相关表已核实，用户切换表新增；两个V4开关均已开启，个人mode行数0，保持员工显式切换。正式8080 Java进程18436，Nginx主进程39504未重启，前端root为 `frontend/releases/1.0.22-compliance-v4-c24add5-20261103`；运行JAR SHA256 DDB6D4EB914747EEE322AD8465BA4EE6D0EC1F65A63ECC63AF6C2D58C1F0E301。前述“未经本轮核实/尚未执行”是开发阶段历史基线，当前事实见 [生产记录](releases/20261103-compliance-v4-production.md)，后续应重新实测，不把PID当常量。


## 项目配置 v3（PC-20261104，2026-11-04 已完成开发待业务验收，尚未部署）

新增配置聚合服务、不可变类型版本和项目运行快照。项目详情/列表名称、阶段推进回退、资料阶段校验与导出使用项目快照；三种要求模式先解析再判断必填。配置写操作共同锁定 project_config_state，类型展示位置为唯一连续 1～N。项目创建在 READ_COMMITTED 事务中解析配置并冻结，公共字典维护不回写旧项目。

新增表 project_config_state / project_type_config_revision / project_config_snapshot / project_config_request，project_type 增加 active_config_revision_id 与唯一 sort。数据字典、接口、权限和迁移步骤见 [项目配置 v3 实施说明](deployment-project-config-v3.md)。旧分步类型写入适配为完整配置版本，仅拒绝含义不明确的空流程/空专属绑定；现有页面已收口新版向导，兼容细则见该说明。新增管理 API 服务内显式验证 project:manage；项目快照读取保留原项目级可读校验。原项目权限范围不扩大。

本节为本地施工增量，生产仍以已发布版本和目标库实际元数据为准，不能据此认定表或接口已上线。

## 个人工作台与全局搜索（2026-11-04 本地实施，未发布）

新增 `/dashboard`，默认登录落地且保留合法原目标；固定首屏展示待我审批/退回修改与项目风险，其后本人项目、收藏、公告和消息。新增只读聚合、六类搜索、共用规格的完整结果列表与授权单条定位；原模块路由保留，query只承载定位/筛选，不参与授权。

后端为DashboardController/DashboardFocusController/GlobalSearchController、DashboardReadService、DashboardTasks、ModuleSearcher/GlobalSearchModules及DashboardRules。前端容器DashboardView、GlobalSearchBar、GlobalModuleResults、DashboardWorkList，原目标页补query定位。聚合查询按原权限规则和资源过滤输出最小DTO，卡片错误不伪装成空；8线程/128有界队列、查询/模块/请求超时与失效响应保护。

完成判定仅复用已批准的历史LEGACY_BASELINE快照中的唯一“已完成”阶段；新类型及无法确认的流程为UNKNOWN，不修改生命周期或数据。退回申请可在原页面编辑文本并重提，原bodyless调用兼容；本人/status=4校验保持，变更/状态/指派清理/记录/通知同事务。

无新增业务表、权限码、生产环境变量或外部服务；新增测试schema仅供隔离验证，不能当作迁移。接口/权限矩阵、目标路径、故障与验证边界见[工作台说明](deployment-dashboard-v11.md)，原需求与设计已同步为V1.1.1。生产快照前置条件及实际部署状态本次未验证。

工作台生产状态更新（2026-11-04）：1.0.24已部署，业务源码1980a66，API只读验证通过；严格切流连续性有1次连接重置，后续稳态复核通过，保留为待排查问题，不作全程无故障或业务验收结论。见[发布记录](releases/20261104-dashboard-production.md)。
