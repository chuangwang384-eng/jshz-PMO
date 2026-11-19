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

# jshz-PMO 系统架构说明

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
- 数据库存放专家信息（expert_info）、登录账号（sys_user）、下载审计（download_log）、用户操作日志（operation_log，B2）、审批流配置（approval_config）、通用申请主表（application，「我的申请」改版）、审批流转记录（approval_record）、审批申请字段模板（approval_template，R-B4）、知识星球文档（knowledge_document / knowledge_doc_version）、文档收藏（knowledge_favorite，C3）、消息通知（notification）、供应商管理（supplier / supplier_type / supplier_city / supplier_price_item / supplier_review / supplier_hotel_print_rel，2026-10-26）、企微通讯录同步（wecom_department / wecom_user_department / wecom_sync_log，2026-10-27）以及 RBAC 权限体系（role / permission / role_permission / user_role）等数据，共 **101 张表**（**85 张业务/结构表 + 10 张历史备份表 + 6 张迁移标记表**，2026-11-16 由 `information_schema` 实测）。备份表如 `approval_config_bak_20261102` / `download_log_customer_backup_20261029` / `knowledge_doc_version_customer_backup_20261029` / `knowledge_document_customer_backup_20261029` / `knowledge_favorite_customer_backup_20261029` / `wecom_migration_uk_username_backup`。`expert_change_request` 虽为历史表但仍参与读取，计入业务表（存量 31 条已迁移至 application，新写入全部走 application，旧表保留不动）。

> 2026-11-16 实测：库中 **101 张表**（**85 张业务/结构表** + 10 张 `*_bak_*` 备份表 + 6 张 `mig_*` 迁移标记表）。
>
> **DDL 单一可信来源**：`database\init.sql`（全量基线，85 张业务表 + 字典/权限种子，**新环境唯一建表来源**）+ `database\migration\*`（63 个增量，仅服务既有库升级）+ `schema_migration`（迁移版本台账）+ `scripts\db-migrate.ps1`（`-Mode Status/Apply/Bootstrap`）。三方一致性由 `scripts\check-schema-parity.ps1`（只读）强制校验，未通过不得 Bootstrap。

## 2. 后端分层

| 层 | 职责 | 示例 |
| --- | --- | --- |
| Controller | 接收请求、参数校验、返回结果 | `AuthController` / `ExpertController` / `ApplicationController`（通用申请全套，URL 沿用 /api/approval/*：配置、修改/新增申请、照片上传、审批、转办/加签/可转办用户列表、my-applications 我的申请、my-requests 旧路径兼容） / `UploadController` / `ExportCompatController` / `UserController` / `AuditController`（下载留痕 + 通用用户操作日志） / `HealthController` / `SupplierController`（供应商管理：档案/行价/评价/图片全员登录即可，类型/城市字典管理 supplier:dict，酒店-打印店多对多绑定） / `RoleController`（RBAC 角色 CRUD/启停） / `PermissionController`（RBAC 权限点查询） / `KnowledgeController`（知识星球：文档上传/分页检索（category 可选=全分类、按权限过滤下架文档）/版本更新/下架/永久删除/版本历史/文件下载预览） / `NotificationController`（消息中心：本人列表（isRead/type 组合筛选）/ 未读数 / 单条已读 / 批量已读 / 全部已读 / 单条删除 / 批量删除，全部仅作用于本人消息） / `ApprovalTemplateController`（R-B4 审批申请字段模板：启用列表登录可用 / 全部列表+增删改启停 approval:config / 字段清单） / `WecomContactController`（企微通讯录同步管理：触发同步（dryRun 试算）/同步状态/最近记录/部门树/未绑定清单/人工指认绑定，权限 wecom:sync，2026-10-27） / `WecomDirectoryController`（普通员工个人通讯录：GET /api/wecom/directory/mine 返回本人根到本部门链路+本部门成员，登录即可，不暴露全量架构，2026-10-27）（共 **31 个**，2026-11-16 核实） |
| Service | 业务逻辑、事务控制 | `AuthService` / `ExpertService` / `ApplicationService`（通用申请核心：类型枚举分发、biz_data JSON 读写（含 oldData 旧值快照写入与按查看者脱敏返回）、白名单字段校验、TMP 临时照片通道、修改/新增分支审批、转办/加签与 assignee 审批权校验、审批/提交通知埋点） / `ExportService` / `PhotoStorageService` / `DownloadAuditService`（下载留痕，双写 operation_log） / `OperationLogService`（B2 通用敏感操作日志：统一写入（try-catch 降级）+ 分页查询，2026-10-27 新增 WECOM_SYNC / WECOM_BIND 审计类型） / `UserService`（含角色调整/密码重置通知埋点） / `KnowledgeDocumentService`（知识星球业务，含下架/永久删除通知埋点） / `KnowledgeDocStorageService`（文档落盘与校验，含物理删除） / `NotificationService`（通用消息通知：写入事件、本人列表/未读数/已读标记） / `ContactSyncService`（企微通讯录同步核心：DryRun 试算不写业务数据、diff 绑定/新建/停用、并发护栏 409、部门树/未绑定清单/人工指认绑定/个人通讯录 getMyDirectory，2026-10-27） / `WecomSyncScheduler`（@Scheduled 每日 02:30，受 sync-cron-enabled 开关控制，首次引入定时任务） / `WecomSyncTrigger`（手动/定时同步统一入口，异步执行） |
| Security | 认证鉴权 | `JwtTokenProvider` / `JwtAuthenticationFilter` / `AuthUser` |
| Mapper | 数据访问（MyBatis-Plus，共 67 个，2026-11-16 核实） | `ExpertMapper` / `SysUserMapper` / `DownloadLogMapper` / `OperationLogMapper`（B2）/ `ApprovalConfigMapper` / `ApplicationMapper`（通用申请）/ `ApprovalRecordMapper` / `ApprovalTemplateMapper`（R-B4） / `RoleMapper` / `PermissionMapper` / `RolePermissionMapper` / `UserRoleMapper` / `KnowledgeDocumentMapper` / `KnowledgeDocVersionMapper` / `KnowledgeFavoriteMapper`（C3） / `NotificationMapper` / `SupplierMapper` / `SupplierTypeMapper` / `SupplierCityMapper` / `SupplierPriceItemMapper` / `SupplierReviewMapper` / `SupplierHotelPrintRelMapper` / `WecomDepartmentMapper` / `WecomUserDepartmentMapper` / `WecomSyncLogMapper`（企微三表，2026-10-27）等（此处仅列早期核心 Mapper，合规 17 表与项目 16 表的 Mapper 见源码目录） |
| Entity | 数据库实体映射（共 67 个，与 Mapper 一一对应，2026-11-16 核实） | `Expert` / `SysUser` / `DownloadLog`（含 docId）/ `OperationLog`（B2）/ `ApprovalConfig` / `Application`（通用申请，含 biz_data 解析展示字段 currentAssignee/extraApprover/records/oldData/photoChanges 等）/ `ApprovalRecord`（含 applicationId）/ `ApprovalTemplate`（R-B4，含 fields 解析展示）/ `Role` / `Permission` / `RolePermission` / `UserRole` / `KnowledgeDocument`（含 content 正文/downloadCount/isFavorite）/ `KnowledgeDocVersion` / `KnowledgeFavorite`（C3）/ `Notification` / `Supplier`（含类型/城市/评分展示字段）/ `SupplierType`（含 bizKey）/ `SupplierCity` / `SupplierPriceItem` / `SupplierReview` / `SupplierHotelPrintRel` / `WecomDepartment` / `WecomUserDepartment` / `WecomSyncLog`（企微三表，2026-10-27）等 |
| Config | 安全、跨域、全局配置 | `SecurityConfig` / `WebConfig`（/api/files/** 正式照片库、/api/files-tmp/** 审批临时照片库，均登录可读）/ `GlobalExceptionHandler` / `MybatisPlusConfig` / `WecomProperties`（企微配置绑定，2026-10-27）/ `WecomAsyncConfig`（异步同步线程池，2026-10-27） |

## 3. 前端结构

| 目录 | 职责 |
| --- | --- |
| `src/api` | Axios 请求封装（request/auth/expert/user/audit/approval/knowledge/notification/supplier/wecom，withCredentials=true，401 拦截跳登录；wecom 于 2026-10-27 新增：triggerSync/getSyncStatus/getRecentSyncLogs/getDepartments/getUnbound/bindWecom/getWecomConfig/getMyDirectory） |
| `src/router` | 路由配置与守卫（未登录跳登录、meta.permissions 权限码控制路由访问与菜单过滤；一级菜单：专家库/知识星球/个人中心/供应商管理（SupplierList 列表/卡片双视图 + 名称联系人搜索 + 类型/城市/仅看已评价筛选 + 按名称/更新时间排序，2026-11-09；/supplier/:id 详情页，menu:false 不进菜单）/项目管理/管理员设置等；知识星球父路由 /knowledge-planet 挂父页组件（KnowledgePlanetHome 内含 <router-view/> 渲染四个分类子页；父路由显示上传中心+全分类总览，子路由仅渲染本分类列表），点击标题跳父路由、点击箭头展开二级菜单；个人中心 /profile 父路由挂 ProfileHome（内含 <router-view/>，父路径 /profile 渲染 ProfileCard 信息卡主页；2026-10-30 修复入口回归：cda0d27 改造曾删除主页信息卡与修改密码入口）；子路由：overview（我的主页，ProfileCard 信息卡：用户名/角色（roleNames 中文优先回退编码）/账号ID/注册时间 + 修改密码按钮复用 ChangePasswordDialog）、my-approvals（我的申请，「我的申请」改版由「我的审批」更名，路由路径保留供通知跳转）、notifications（消息中心，登录即可）、approval/manage（审批管理，permissions: approval:audit——已从管理员设置迁移至此，普通员工不可见）、directory（通讯录，登录即可，个人通讯录页）、announcements / announcements/manage（公告与公告管理，权限码 announcement:manage；announcement-detail menu:false 不进菜单）；管理员设置父路由 /admin（redirect /admin/experts/manage，meta.permissions 为 5 码并集：expert:manage/audit:view/approval:config/user:manage/role:manage），7 个子路由：experts/manage（专家管理）、audit/downloads（用户操作日志）、approval/config（审批流配置）、approval/templates（审批模板管理，permissions: approval:config）、users（用户管理）、roles/manage（角色管理）、wecom（企微通讯录同步，permissions: wecom:sync）） |
| `src/views` | 页面组件（Login（2026-10-27 起按 /api/auth/wecom/config 的 loginMode 自适应姓名/姓名或手机号/手机号文案）/ExpertList/ExpertManage/DownloadAudit（B2 改造为通用用户操作日志页：用户名模糊/操作类型/时间范围筛选，含历史下载记录）/UserManage（2026-10-27 增手机号/部门/企微绑定列与绑定操作）/MyApproval（「我的申请」：调 /api/approval/my-applications 仅本人数据，含审批人/审批时间列，30s 轮询刷新）/ApprovalManage（含转办/加签按钮与选人弹窗，读写 application 主表）/MessageCenter（消息中心：Tab 全部/未读/已读 + 10 类类型筛选组合、复选框批量已读/批量删除、单条删除、点击已读+按 jump_path 跳转、分页）/ApprovalConfig/ApprovalTemplateManage（R-B4 审批模板管理）/KnowledgePlanetHome（知识星球父页：上传中心+全分类总览，管理员含永久删除）/MeetingExecution/AssociationCompliance/EnterpriseCompliance/CustomerPreferences/WecomSyncManage（企微通讯录同步管理：立即同步+DryRun 试算变更清单+确认实跑、同步结果卡片、最近记录表、组织架构树 el-tree、未绑定清单+人工绑定，2026-10-27）/profile/ProfileHome（个人中心父页：<router-view/> + 父路径渲染 ProfileCard 信息卡主页，2026-10-30 修复入口回归）/profile/ProfileCard（个人中心信息卡：用户名/角色/账号ID/注册时间 + 修改密码弹窗，2026-10-30 新增，由 ProfileHome 与 /profile/overview 子路由复用）/profile/Directory（个人中心通讯录：根到本部门链路+本部门成员，手机号脱敏，2026-10-27）等） |
| `src/stores` | Pinia 状态管理（auth、notification（30 秒轮询未读数+新消息轻提示）） |
| `src/components` | 通用组件（ChangePasswordDialog（普通模式可关闭、force-mode 强制改密警示）/PhotoImage/SideMenuItem/KnowledgeDocList（知识文档列表，allowUpload=false 隐藏上传按钮供子页复用，管理员含永久删除）/NotificationBell（顶栏铃铛+未读红点+消息列表）） |

## 4. 数据模型

数据库：`expert_system`（MySQL 8，utf8mb4），共 **101 张表**（85 业务/结构表 + 10 历史备份表 + 6 迁移标记表，2026-11-16 实测），含 RBAC 四表（role / permission / role_permission / user_role）、供应商七表（supplier / supplier_type / supplier_city / supplier_price_item / supplier_review / supplier_hotel_print_rel / supplier_image）、企微通讯录同步三表（wecom_department / wecom_user_department / wecom_sync_log，2026-10-27）、合规 17 表、项目 16 表、客户 4 表、知识 3 表、消息通知表（notification）、审批申请字段模板表（approval_template，R-B4）、知识文档收藏表（knowledge_favorite，C3）、用户操作日志表（operation_log，B2）与通用申请主表（application，「我的申请」改版）。

下表仅详列**早期核心表**（25 张）的字段说明；合规 / 项目 / 供应商扩展 / 客户等后续模块请直接查看 `backend/src/main/java/com/jshz/expert/entity/` 与 `database/migration/` 对应脚本。

| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| `expert_info` | 专家主表（18 字段） | id、name、unit、department、title、research_direction、expertise_area、contact、profile、id_card、bank_card、photo、bank_branch、id_card_front、id_card_back、create_time、update_time、update_by |
| `sys_user` | 登录账号 | username=姓名、password（BCrypt）、is_admin（管理员名单兼容标记）、must_change_password（强制改密）、wecom_userid（企微唯一身份锚点）、mobile（手机号登录标识，2026-10-27 新增）、position（职务，2026-10-27 新增）、status、token_version（Token 版本号，2026-11-06 新增，停用/删除/改密/改角色时自增）；唯一索引 uk_wecom_userid（wecom_userid）/ uk_mobile（mobile）/ uk_username（已于 2026-10-27 删除，改由应用层保证未绑定通道内姓名唯一，企微同步可产生重名用户） |
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
| `knowledge_document` | 知识星球文档主表 | id、title、category、file_name、file_path、file_type、file_size、content（C1 正文，抽取截断存储）、version、upload_by、status（1上架/0下架）、parse_status（PENDING/RUNNING/SUCCESS/FAILED，NULL=历史数据）、parse_message、parse_time（P2-7 异步解析状态，2026-11-06 新增）、create_time、update_time |
| `knowledge_doc_version` | 知识星球文档版本历史 | id、doc_id、version、file_name、file_path、file_type、file_size、upload_by、create_time |
| `knowledge_favorite` | 知识文档收藏（C3） | id、doc_id、user_id、create_time；UNIQUE(doc_id,user_id) |
| `notification` | 消息通知中心 | id、user_id（接收人）、type（approval_result / approval_todo / approval_withdrawn / approval_transferred / approval_assigned / role_changed / password_reset / doc_off_shelf / doc_deleted / doc_updated）、title、content、jump_path（点击跳转前端路由，可为空）、is_read（0未读/1已读）、create_time |
| `operation_log` | 用户敏感操作日志（B2） | user_id、username、action_type（DOWNLOAD/LOGIN/LOGOUT/CHANGE_PASSWORD/RESET_PASSWORD/CREATE_USER/UPDATE_USER/DELETE_USER/ASSIGN_ROLES/ROLE_PERMISSION/CREATE_EXPERT/UPDATE_EXPERT/DELETE_EXPERT/IMPORT_EXPERT/CREATE_DOC/UPDATE_DOC/OFF_SHELF_DOC/DELETE_DOC/WECOM_SYNC/WECOM_BIND）、action_desc、target_type、target_id、ip、source_id（download_log.id 历史迁移标记，UNIQUE(action_type,source_id) 防重复迁移）、create_time |
| `wecom_department` | 企微部门快照（2026-10-27，不回写企微） | dept_id（企微部门 id，根=1，UNIQUE uk_dept_id）、name、parent_dept_id、sort_order、status（1正常/0企微已删除，保留快照）、create_time、update_time |
| `wecom_user_department` | 成员-部门归属（2026-10-27，企微一人可属多部门） | user_id（sys_user.id）、dept_id（企微部门 id）、is_main（1主部门）；UNIQUE(user_id,dept_id)，KEY idx_dept |
| `wecom_sync_log` | 同步审计日志（2026-10-27，每次手动/定时同步一条） | trigger_type（MANUAL/SCHEDULED/DRYRUN）、operator、status（RUNNING/SUCCESS/FAILED）、fetched_users/created_users/updated_users/disabled_users/bound_users/unbound_left、error_msg、detail（DryRun 变更清单 JSON）、start_time/end_time |

> `expert_info.update_by` 写入语义：直接编辑（ExpertService.update，由超管/具备 expert:edit 权限用户调用）记操作者本人；审批通过（ApplicationService.approveModify / approveCreate）记申请单申请人 `application.applicant`（发起修改/新增的人），审批人仅记录于 `approve_by`。`update_time` 由数据库 `ON UPDATE CURRENT_TIMESTAMP` 自动维护。

简历解析在收集简介之前校验姓名候选，排除学历、职称、机构和占位文本。未被有效字段占用的学历原文可以进入简介。默认模式下，正文姓名缺失或无效时允许从文件名提取，并去除「副本」「简介」等前后缀。接口保留可选的 `contentNameOnly=true` 参数，供需要限定姓名来源的旧客户端使用。

专家库批量上传简历由前端依次调用已有单份解析接口，每个文件生成一张可编辑候选卡。提交前在本批选中项和库内分页结果中匹配姓名与单位，命中后展示两卡详情。覆盖只提交本次确认的非空字段及选定的证件照。新增按 `expert:add`、覆盖按 `expert:edit` 判断直写权限，否则分别提交新增和修改申请。查重在本次前端流程内执行，独立会话和待审批申请之间不提供唯一性保证。

单份简历上限为 30 MiB，经 `GET /api/meta/limits` 提供给共有的单独和批量上传界面。正文缺名时允许从「机构名紧接姓名」和「姓名＋个人简介」文件名提取候选姓名。PPTX 形状 ID 导致 POI 无法枚举形状时，读取该页的 OOXML 文本段落，并继续提取嵌入图片。抠图后使用有色纯背景与前景连通性判断能否补回明显漏分的肩部，继续使用本地 U²-Net。

项目详情与专家库共用默认批量简历解析和证件照选择弹窗。「一键上传」最多添加 60 份待处理简历。默认解析可从文件名补姓名，解析后正文或文件名所得姓名可自动匹配待建档名单。姓名不一致或无法匹配时保留解析资料和照片，可搜索选择参会专家，提交前确认按所选姓名回绑。科室支持解析回填、编辑和保存。

项目 `POST /api/project/{id}/speakers/archive-manual` 支持 `department`、`matchByNameAndUnit` 和 `existingExpertId`。选择同名同单位档案后，后端复核待建档状态和姓名、单位，仅建立项目关联及回绑参会记录。无同名同单位档案时可创建并保存所选照片。沿用单文件大小限制、项目成员权限和现有数据库结构。

项目建档也展示同一解析响应中的头像候选，由用户预览并选择。未选择时沿用 JSON 提交。选中头像时，同一建档地址收取 `multipart/form-data`，`data` 为原有字段的 JSON 部件，`photo` 为选中的照片文件。服务端校验项目操作权限和参会记录条件后，按专家库头像的文件大小、格式白名单、存储与命名规则入档。建档、项目关联和回绑沿用同一事务，回滚时清理此次保存的头像。

项目单人、批量及手动建档均要求姓名和单位，职称、简介、联系方式及头像可空。联系方式「无」按空值处理，其他非空联系方式沿用专家库的手机号或邮箱校验。旧批量简历建档接口遇到缺少姓名或单位的简历时逐条返回失败。

项目详情右侧的「合规要求注意事项」默认显示待完成事项，可切换查看已完成事项。完成或重开后重新读取项目详情，事项按服务端状态出现在对应视图。沿用原事项写入权限和 `rowVersion` 冲突检查。

项目详情双栏布局滚动时，右侧卡片组吸附在主内容滚动区顶部，紧贴顶栏下缘。单栏布局中卡片随页面正常滚动。

> RBAC 说明：内置角色 4 个（SUPER_ADMIN 代码内全权锁定 / ADMIN 业务全权 / APPROVER 审批 / EMPLOYEE 无绑定）；permission.code 允许重复（一码多路径），UNIQUE(method,path) 约束接口唯一；鉴权链路为 JWT claims.roles + DbPermissionFilter 按 method+path 匹配权限码（命中校验、未命中放行）。知识星球上传/更新/版本/文件下载接口登录即可（不设权限码），但文件流端点会按存储文件名反查文档并校验状态：文档不存在/已永久删除返回业务码 404，已下架且非 `knowledge:maintain` 返回业务码 403，避免下架后旧链接继续可下载（P1-5）；列表 GET /api/knowledge/docs 登录即可且 category 可选（不传=全分类），并按权限过滤 status：具备 `knowledge:maintain`（知识星球维护人/超管）返回上架+下架，普通员工仅上架；下架接口 DELETE /api/knowledge/docs/{id} 与永久删除接口 DELETE /api/knowledge/docs/{id}/permanent 均需接口权限码 `knowledge:maintain`（已绑定 KNOWLEDGE_MAINTAINER），业务层 AuthUser.hasPermission 二次校验；永久删除同时删除主记录、全部版本历史与磁盘物理文件，不可恢复。

> 知识文档异步解析（2026-11-06，P2-7）：上传/更新请求只流式落盘并写入 `parse_status=PENDING`，随后提交 `knowledgeParseExecutor` 异步任务；正文抽取（PDFBox/POI）与企业合规 Excel 草稿解析均在线程池内完成，状态/正文更新带 `id+version` 条件，旧任务不会覆盖新版本。解析失败只记录 `FAILED` 与原因，不回滚已上传文件；PDF/DOC/DOCX 抽取为空按失败处理（可能损坏、加密或为扫描件），其余不支持正文的格式标记成功但不参与正文检索；线程池拒绝时同步标记失败，上传本身仍成功。线程池参数见 `expert.knowledge.parse.*`，队列满时采用 AbortPolicy。历史数据 `parse_status=NULL`（接口原样返回，不做批量回填）。

> 知识星球增强（2026-10-26，C1/C3/C4）：① C1 内容检索——knowledge_document.content 存上传/更新时抽取的正文（PDFBox/POI，截断 200k 字符），GET /api/knowledge/docs 的 keyword 扩展为 title/file_name/content 三列 LIKE；前端 placeholder「按标题 / 内容关键字检索」。② C3 收藏——knowledge_favorite 表（UNIQUE(doc_id,user_id)），接口 POST/DELETE/GET /api/knowledge/favorites[/{docId}]（登录即可，userId 取当前用户防越权；路径避开 permission 表 DELETE /api/knowledge/docs/** 通配规则），PUT /api/knowledge/docs/{id} 更新成功后向全部收藏者（排除操作人）写 doc_updated 通知；前端列表/总览操作列收藏按钮即时切换，知识星球父页「全部文档/我的收藏」切换。③ C4 下载次数——download_log 加 doc_id 列，附件下载（download=1，预览不计）写 download_type=knowledge_doc 记录，列表接口单次 GROUP BY 聚合返回 downloadCount，前端「下载次数」列仅维护人（`knowledge:maintain`）可见数值，他人显示 —。

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
- Token 吊销（P1-2，2026-11-06）：JWT 增加 `tv` claim，与 `sys_user.token_version` 比对；过滤器每次请求校验用户存在、`status=1`、版本一致，任一不满足不建立认证。停用/删除/改密/改角色时自增版本并失效本地缓存，单实例下一次请求即生效、多实例最迟一个 TTL（`expert.auth.state-cache-seconds`，默认 60 秒）。升级前签发、不含 `tv` 的存量 Token 一律拒绝，用户需重新登录一次；查库异常按 fail-closed 处理。
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
- 日程解析保留 PPTX 文本框边界，与原有表格解析合并。明确标注的文本框人员按姓名、角色、议题和时间匹配出场，同人跨角色保留，未确认时间留空。规则结果充足时也可对含未解析角色或姓名信号的独立文本调用本地 Ollama。
- PPTX 的表头在独立文本框、数据表没有内嵌表头时，首列有效时间范围与人员列显式角色可共同确认列布局。按表格行关联议题和时间，按角色标签读取讲者及跨段落的主持名单，沿用已有候选确认和专家库匹配流程。
- 解析成功时与本项目已确认且来源于日程的参会记录逐场比较。已保存的同场同角色从候选隐藏，新增场次进入候选；可唯一匹配的变更和减少的场次存入当前日程的 `raw_result`，等待用户核对。手动补录保留，移除只作用于用户勾选的当前项目参会记录。
- `GET /api/project/{id}/schedule` 补充 `parseRevision` 和可空的 `comparison`。`PUT /api/project/{id}/speakers` 附带 `scheduleId` 和 `parseRevision` 时校验当前解析批次，按出场追加保存并清理本批候选。项目可操作人员通过 `POST /api/project/{id}/schedule/{scheduleId}/comparison` 提交 `{token, accepted}`，其中 `accepted` 是同意更新或移除的已确认参会记录 ID；空数组保留原记录并结束本批核对。
- 单条保存通过 `PUT /api/project/{id}/speakers?candidateId=…` 只提交一条并消费对应候选，其余候选及本地编辑可继续处理。清空已保存专家经页面二次确认后调用 `DELETE /api/project/{id}/speakers/confirmed`，删除本项目已确认参会记录并清理针对旧名单的待确认差异。候选、专家库档案和项目专家名单关联均保留，操作计入项目日志。
- PPTX 日程候选按幻灯片顺序、文本框与表格行的视觉坐标输出。同一视觉行从左到右，保留姓名在原行中的顺序。无表头的文本日程以原文时间和栏间空白划分议题与人员，无法可靠定位的议题留空。
- 日程表头支持发言人、发言者和报告人。开场致辞和总结优先使用同一份日程中的主席身份。缺少主席证据时，明确的开场致辞作为主席候选信号。明确主持保留主持角色。
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

完整规格见 [需求](requirements/2026-11-03-企业合规可复用规范与个人执行清单-需求文档.md) 与 [技术设计](design/2026-11-03-企业合规可复用清单-设计文档.md)。已补资源权限、个人提醒、逐节点事务与旧版切换；14 项真实 MySQL、22 项服务回归及真实浏览器通过，见 [发布阻断闭环](handover/tasks/20261103_compliance_v4_release_blockers.md)。首期生产上线与技术只读验收已完成，员工业务体验待复核。

V4 由两个默认关闭的环境开关及个人确认控制；新增第八张 `compliance_user_mode` 表（user_id 主键、mode、legacy_snapshot JSON、switched_at、updated_at）。缺行仍是 V3；切换锁定用户并保存个人旧进度快照，新版与旧个人写/到期发送互斥。关闭 V4 不清除个人切换标记，不恢复旧写入/旧提醒。七表迁移及用户开关迁移先经独立库验证，再核实生产元数据并按本次授权执行；不得据此跳过后续环境核查。

资源策略在 V4 内实施：本人执行/事件必须再检来源和项目，维护人不豁免个人隐私；模板由上传者/维护人维护、维护人发布。提醒使用 READ_COMMITTED、每节点 REQUIRES_NEW 与用户/执行/任务/提醒行锁；站内通知和 SENT 原子提交，失败回滚后独立计次，3 次上限。CORS 仅补 PATCH 方法，不改变允许来源。接口、权限、数据字典和发布/回滚限制见 [部署说明](deployment/2026-11-03-企业合规V4隔离验收灰度切换与回滚-部署说明.md)。

2026-11-03 21:44生产更新：八张v4相关表已核实，用户切换表新增；两个V4开关均已开启，个人mode行数0，保持员工显式切换。正式8080 Java进程18436，Nginx主进程39504未重启，前端root为 `frontend/releases/1.0.22-compliance-v4-c24add5-20261103`；运行JAR SHA256 DDB6D4EB914747EEE322AD8465BA4EE6D0EC1F65A63ECC63AF6C2D58C1F0E301。前述"未经本轮核实/尚未执行"是开发阶段历史基线，当前事实见 [生产记录](releases/20261103-compliance-v4-production.md)，后续应重新实测，不把PID当常量。

### 规范文件生成模板草稿

2026-11-05 在知识文档提取层保留 PDF 页、Word 段落/表格行、演示文稿页和电子表格行的来源锚点；短文本 PDF 页触发扫描页提示，并可调用本机 Tesseract 简体中文/英文 OCR。V4 新增生成待确认模板草稿接口，候选节点保留页/段/行锚点、原文摘录和需人工复核标记；前端逐项展示引用，草稿不会自动发布。权限沿用文档上传者/维护人和模板发布权限，不新增表或权限。扫描文件可能漏识别或错误识别，人工核对原件是发布前置条件。Windows 服务账号必须能读取 `chi_sim.traineddata`；当前测试模型在交互用户 LocalAppData，正式后端以 LocalSystem 运行且尚无 `TESSDATA_DIR`，生产 OCR 可用性尚未就绪。接口和隔离验收步骤见 [企业合规 V4 部署说明](deployment/2026-11-03-企业合规V4隔离验收灰度切换与回滚-部署说明.md)。


## 项目配置 v3（PC-20261104，2026-11-04 已完成开发待业务验收，尚未部署）

新增配置聚合服务、不可变类型版本和项目运行快照。项目详情/列表名称、阶段推进回退、资料阶段校验与导出使用项目快照；三种要求模式先解析再判断必填。配置写操作共同锁定 project_config_state，类型展示位置为唯一连续 1～N。项目创建在 READ_COMMITTED 事务中解析配置并冻结，公共字典维护不回写旧项目。

新增表 project_config_state / project_type_config_revision / project_config_snapshot / project_config_request，project_type 增加 active_config_revision_id 与唯一 sort。数据字典、接口、权限和迁移步骤见 [项目配置 v3 实施说明](deployment/2026-11-04-项目配置V3接口数据字典与发布步骤-部署说明.md)。旧分步类型写入适配为完整配置版本，仅拒绝含义不明确的空流程/空专属绑定；现有页面已收口新版向导，兼容细则见该说明。新增管理 API 服务内显式验证 project:manage；项目快照读取保留原项目级可读校验。原项目权限范围不扩大。

本节为本地施工增量，生产仍以已发布版本和目标库实际元数据为准，不能据此认定表或接口已上线。

## 个人工作台与全局搜索（2026-11-04 本地实施，未发布）

新增 `/dashboard`，默认登录落地且保留合法原目标；固定首屏展示待我审批/退回修改与项目风险，其后本人项目、收藏、公告和消息。新增只读聚合、六类搜索、共用规格的完整结果列表与授权单条定位；原模块路由保留，query只承载定位/筛选，不参与授权。

后端为DashboardController/DashboardFocusController/GlobalSearchController、DashboardReadService、DashboardTasks、ModuleSearcher/GlobalSearchModules及DashboardRules。前端容器DashboardView、GlobalSearchBar、GlobalModuleResults、DashboardWorkList，原目标页补query定位。聚合查询按原权限规则和资源过滤输出最小DTO，卡片错误不伪装成空；8线程/128有界队列、查询/模块/请求超时与失效响应保护。

完成判定仅复用已批准的历史LEGACY_BASELINE快照中的唯一"已完成"阶段；新类型及无法确认的流程为UNKNOWN，不修改生命周期或数据。退回申请可在原页面编辑文本并重提，原bodyless调用兼容；本人/status=4校验保持，变更/状态/指派清理/记录/通知同事务。

无新增业务表、权限码、生产环境变量或外部服务；新增测试schema仅供隔离验证，不能当作迁移。接口/权限矩阵、目标路径、故障与验证边界见[工作台说明](deployment/2026-11-04-个人工作台V1.1.1接口与隔离验证-部署说明.md)，原需求与设计已同步为V1.1.1。生产快照前置条件及实际部署状态本次未验证。

工作台生产状态更新（2026-11-04）：1.0.24已部署，业务源码1980a66，API只读验证通过；严格切流连续性有1次连接重置，后续稳态复核通过，保留为待排查问题，不作全程无故障或业务验收结论。见[发布记录](releases/20261104-dashboard-production.md)。

2026-11-05生产更新：并发生成事务护栏及ProgramData OCR路径随新后端上线；生产只读技术检查通过，切流连续性有2次连接重置，业务验收待复核。详见releases/20261105-compliance-template-production.md；此前并发窗口/未部署文字为历史状态。

## 可视化模板维护施工增量（2026-11-05，未完成/未部署）
新增共享工作区服务与GET/PUT接口，迁移脚本20261105_compliance_template_workspace.sql尚未执行。工作区内容与当前生效模板分开，保存使用row_version条件更新，不写员工执行快照。生效、收口及前端尚待施工，不属于现网可用能力。见handover/tasks/20261105_compliance_visual_editor_implementation.md。

可视化维护阶段二：新增共享工作区存量选择与原子生效事务，以工作区active_template_version_id为唯一生效指针；保留不可变模板及员工快照。前端改为阶段卡片和自动保存。增量迁移尚未在生产执行，候选提取合并仍未接入，见施工交接。

## 可视化模板维护阶段三（未部署）
新增ComplianceWorkspaceSourceService与持久化候选、来源复核。提取在事务外读取授权文件，保存前复查来源版本；合并按候选摘要去重，明确新增/替换，不覆盖已有内容。生效校验服务端核对摘要；文档版本变化拒绝旧候选。阶段管理/结构撤销、候选对照及持久化漏提取提示已接入。workspace_request记录actor，extraction/review记录来源版本与核对时间；四表数据字典和测试边界见施工交接阶段三。源格式行候选不是语义完整识别，大文件同步OCR及Office精确定位有局限。生产新表尚不存在，本节不能视为现网状态。

### OCR 事项分组、收藏筛选与历史入口

模板维护优先按 PDF 表格线与 OCR 词坐标归并三列表格，提取同一行的事项标题和多行操作要求，保留页码、行号和人工核对。普通编号条款按条款边界合并折行。企业合规规范文档库的 `GET /api/knowledge/docs` 支持可选 `favoritesOnly=true`，按登录用户收藏记录过滤后分页，可与格式和关键词一起使用。V4 页面移除旧版只读历史入口，底层历史记录保留。验证记录见 [可视化维护施工交接](handover/tasks/20261105_compliance_visual_editor_implementation.md)。

### 工作台二维布局

静态工作台保留现有业务查询和跳转。编辑器按需加载 GridStack 14.1.0，Vue 保留卡片内容渲染，宽屏静态视图用相同的 12 列坐标展示，窄屏按坐标顺序单列阅读。布局通过认证账号隔离。旧 layout 与可空 layout_v2 并行存储，旧版回退读取原列，迁移和并发语义见 [接口与部署说明](deployment/2026-11-06-工作台二维布局-接口部署与验收.md)。本轮生产未发布。

> 历史状态说明（2026-11-09 核实）：上文「本轮生产未发布」「GridStack 14.1.0」为 2026-11-06 记录；后续布局编辑器已改为零依赖原生拖拽（`frontend/src/utils/dashboardGrid.js`）并已上线，1.0.37.28 起不再依赖构建期开关。

### 工作台备选卡片去重（2026-11-12）

备选区过滤已放置的卡片、与当前快捷访问草稿相同的页面入口，以及已由现有面板覆盖的页面入口。待我审批和退回待修改的旧统计卡不再作为备选，已保存布局及默认布局仍可渲染。新增「我的申请进度」与「规范文档库」两张可选卡，分别沿用我的申请和知识星球权限。后端布局白名单接受两个新标识。规范文档库深链只接受企业合规页的 tab=docs。审批标题映射为中文业务名称。

### 工作台与实际功能对齐（2026-11-09，1.0.37.28 待发布）

工作台的项目数据口径改为与现行项目流程一致：阶段名取 `project_phase` 字典（配置快照、历史完成阶段判定与阶段推进/回退已停用），到期风险仅按 `end_date` 计算，成员范围=负责人/创建人/协助人（兼容历史执行人）；全局搜索项目分组采用同一范围，辅助信息取阶段名而非配置快照。卡片目录补 3 个实际菜单入口（协会合规要求、企业合规要求、我的主页），前端卡片目录/快捷访问/图标/深链白名单与后端 `DashboardLayoutService` 白名单同步；移除失效的 `/project?mine=1` 深链与「项目状态待核实」横幅。**无数据库迁移、无接口契约变更、无权限码变更**。详见 [设计文档](design/2026-11-09-工作台与实际功能对齐-设计文档.md) 与 [发布记录](releases/20261109-dashboard-align-1.0.37.28.md)。

## 审批流事务边界（2026-11-06，开发完成未发布）

`ApplicationService` 的审批流写入口统一声明 `@Transactional(rollbackFor = Exception.class)`：`updateConfig` / `submitRequest` / `submitCreateRequest` / `submitContractApproval` / `approve`（其内部 `approveContract`、`approveCreate`、`approveModify` 分支共享该事务）/ `approveModify` / `reject` / `returnRequest` / `resubmit`（两个重载）/ `withdraw` / `transfer` / `addApprover` / `saveConfigUsers`。单次操作中的申请单写入、合同 status 与 approval_ref、项目专家合同快照、审批记录、操作日志在同一事务内原子提交，任一失败整体回滚。

文件 IO 与事务解耦：`PhotoStorageService.buildArchivedName` 只校验临时文件并计算归档文件名（事务内调用），`archiveTempPhoto` 负责实际拷贝与删除；两者由 `ApplicationService.afterCommit`（`TransactionSynchronization.afterCommit`）在事务提交后执行。驳回/撤回的临时照片清理、修改审批的旧照片物理删除、兼容历史单的旧照片删除同样延后到提交后。数据库与文件系统不共享事务，提交后 IO 失败只记录 error 日志（含专家 ID、临时文件、目标文件名），不回滚已提交的审批结果，需按日志人工补偿。

审批联动 `syncContractByApproval` 取消整体吞异常：合同 status 写入失败向上抛出以触发回滚；`syncProjectExpertSnapshot`（项目-专家合同状态派生快照）失败仍降级，但改为 warn 日志留痕。并发重复审批（状态条件更新/行锁缺失）本次未处理，仍为已知风险。验证：`ApplicationTransactionBoundaryTest`（注解与归档两段式）+ 后端全量测试 75 项 0 失败 0 错误 9 跳过。

## 合规模板单次保存

模板维护按规范文档进入，候选事项与模板事项宽屏并排，窄屏切换。编辑只保存在页面中。GET /api/compliance/v4/documents/{documentId}/template-workspace/extractions/latest 读取当前文档版本的候选。POST /api/compliance/v4/documents/{documentId}/template-workspace/save-and-activate 在同一事务中验证编辑序号、保存内容并生效，沿用维护人权限及请求幂等校验。每份文档只有一个工作区，历史生效版本供既有清单引用。无新增迁移。

### 清空当前文档的模板事项

维护人可在模板事项区确认清空当前文档的全部模板事项，并在保存前使用结构撤销。保存空事项列表时，工作区事务更新内容及编辑序号，并将当前生效版本指针置空。现有执行清单仍引用历史版本。员工不能再用旧模板新建清单，直到维护人重新添加事项并保存生效。提取候选、原文件及其他文档独立保留。无新增迁移。

### 合规事项维护与文档主动解析

企业合规「模板维护」的情况事项看板按规范文档折叠，事项数据展示标题和内容。新增要求选择已有企业合规文档。保留既有阶段、状态和优先级字段用于兼容历史数据及接口。

- GET /api/compliance/documents/{id}/reset-preview 返回文档名称和候选加已发布的有效事项数。
- POST /api/compliance/documents/{id}/reparse 复用本地解析链。普通解析替换未发布候选。reset=true 时校验确认条数，解析成功后在同一事务中替换候选并撤下旧已发布事项。空结果或解析失败保持旧数据，文档版本变化拒绝写入。候选继续通过现有确认发布接口保存。
- POST /api/compliance/items/{id}/withdraw 撤下单条已发布事项并记录历史。按文档清空继续使用记事本批量撤下接口。所有写入沿用 knowledge:maintain。
- GET /api/compliance/documents/{id}/drafts-clear-preview 返回文档名称和该文档全部草稿候选数（维护人，2026-11-11 新增）。
- POST /api/compliance/documents/{id}/drafts-clear 一次性删除该文档全部草稿候选，expectedCount 集合版本校验（数量不符返回 409），逐条复用单条删除逻辑留修改历史；已发布事项不受影响（维护人，2026-11-11 新增）。
- GET /api/compliance/items 的 keyword 为字面子串匹配标题或内容：服务端转义 `%`、`_`、`\` LIKE 通配符后按 `%kw%` 查询，特殊字符不再被当作通配符（2026-11-11 行为修正）。
- 文档永久删除沿用事项清理，项目待办快照保留。项目详情和待办查询基于来源文档是否存在返回 sourceDeleted，无数据库迁移。

### 存储管理与硬删除（2026-11-12，已发布 1.0.38.18）

补充第 7 个范围 `project-deleted`（「项目资料库（已删除资料）」）：`project_doc` 行全部软删且无任何未删引用（未删资料 ∪ 日程 ∪ 合同）的物理文件可回收，`is_deleted IS NULL` 按未删处理；只删磁盘文件、保留 DB 留痕，无迁移（复用 `/orphans` 与 `/orphans/purge`）。解决"删除资料后磁盘文件永久驻留"（实测 27 个 / 109.2 MB）。

孤儿清理已从项目资料库扩展到 6 个可清理目录（v2）：GET `/api/storage-admin/orphans?scope=all|project|knowledge|photo|photo-tmp|customer|supplier` + POST `/orphans/purge`。引用集合：project=doc∪schedule∪contract；knowledge=knowledge_document∪knowledge_doc_version（排除 `_preview/`）；photo=expert.photo/idCardFront/idCardBack（排除 `richtext_*` 内嵌图）；photo-tmp=application.biz_data 中 `TMP:<文件名>`；customer=customer_attachment.storage_name；supplier=supplier_image.file_name ∪ 兼容 supplier.images JSON。统一 `OrphanSpec` 扫描、expectedCount/expectedBytes 校验、`deleteWithin` 防穿越删除，前端按目录展示孤儿数并逐个清理。


管理员存储管理工具（权限码 storage:manage，绑定 ADMIN；菜单「管理员设置→存储管理」）：① GET /api/storage-admin/stats 各存储目录（项目资料库/知识文档库/预览缓存/照片库/照片库临时/客户附件库/供应商图库）占用与文件数，单目录阈值 expert.storage.warn/critical-threshold-bytes（10GB/20GB）判 NONE/WARNING/CRITICAL，另报磁盘剩余；② GET /project-orphans + POST /project-orphans/purge 项目资料库孤儿文件（引用集=project_doc∪project_schedule∪project_contract 全表 filePath 含软删）expectedCount/expectedBytes 校验后物理删除；③ POST /preview-cache/purge 清空预览缓存（可再生成）；④ GET/POST /projects/{id}/purge-preview|purge 已取消/已删除项目关联数据硬删除（21 张表级联：project_doc/schedule/speaker/expert/supplier/contract/phase_history/executor/assistant、compliance_project_todo/doc_binding/execution 及 task/event/reminder、compliance_item 及 assignee/hide/history/user_progress、project 主行），confirmName=完整项目名确认，物理文件 afterCommit 全库引用复核后删除，操作日志记 STORAGE_PURGE_*。审批单/操作日志/知识文档/主数据明确保留。无新增业务表。

### 合规 PDF 候选覆盖

合规事项解析优先复用本地结构化提取，按 PDF 正文段落和表格行保留原文候选，使用行距与字号区分正文段落，保留段内换行，按规范化文字去重。页面提取不完整时补充已缓存正文，并提示人工核对。模型提取作为无法形成原文候选时的兜底。表格文件沿用既有入口。无需引入依赖或迁移。

### 规范文档库 Excel 直接预览

GET /api/knowledge/docs/workbooks/{fileName} 复用文档下载的登录、上架和来源文件校验，读取 xls 或 xlsx，返回可见工作表、列宽、显示值和合并范围。可选参数 sheet、rowPage、columnPage 均从 0 开始，未指定工作表时返回首张可见表。每次返回至多 100 行和 40 列，隐藏行列和工作表按工作簿设置处理，跨组的合并区域保留可见部分及原锚点内容。公式使用文件内保存的结果。预览界面为只读，沿用现有下载入口。PDF 和 PPT 仍使用既有文件预览链路。

### 合规候选标题与表格识别质量

正文按段落保留内容，标题根据段落主题及义务关键词生成。表格优先使用项目名称作为标题。表格坐标识别发现明显乱码时，对对应单元格裁剪后使用本地 Tesseract 重识别，每页最多补识别六个单元格。无法确认的行标注需核对原文，其他行及有效比例、金额、比较符号按原识别结果保留。无新增依赖、接口或数据库迁移。

- 合规 PDF 候选识别独立版面标题，主题标题覆盖适用对象与段内义务。本地模型仅在无法生成原文候选时兜底，其提示包含封面排除、金额保真及 OCR 核对规则。

简历解析在候选后端 1.0.38.27 中并行运行文字抽取和照片处理。每个后端进程复用本机 U²-Net 工作进程，启动时预热，抠图失败时回退原图 JPEG。见 [交接](handover/tasks/20261113_resume_speed.md)。

对原图不透明、顶部为纯色且 U²-Net 掩码提前结束的证件照，工作进程从顶边识别连续背景。只有与已有主体重合且延伸至原图底部中央的区域才用于恢复肩部和衣服。专家库新增、编辑及批量上传的候选缩略图按原比例完整显示。规则和复测步骤见 [证件照肩部恢复](handover/tasks/20261118_resume_portrait_shoulders.md)。

`POST /api/experts/resume/parse` 对旧版二进制 PPT 使用 POI HSLF。PPTX 使用 POI 只读文件模式按 ZIP 中央目录读取条目，避免流式读取带数据描述符的未压缩图片时误报解压比。系统临时文件在图文抽取后删除，POI ZIP 限制继续生效。专家库新增、编辑和批量上传复用图片查看器，用户可点开候选和已选图片，批量候选的选用按钮独立于图片预览。验证步骤见 [PPT 解析和照片预览](handover/tasks/20261118_resume_ppt_preview.md)。

简历解析优先读取正文明确姓名，排除章节标题，并支持简介新等文件名后缀。大尺寸竖版照片参与候选筛选，在 U²-Net 推理前缩放。

简历解析保留正文单位，缺失时可从文件名提取机构词段。contentNameOnly 仅限制姓名来源。

2026-11-17 抠图与单位字段修复。证件照抠图工作脚本（`photomatte-worker.py`）在 U²-Net 背景分割后增加精修链：小图（最长边 <640px）先 LANCZOS 放大提升掩码细节 → gamma 硬化软 alpha（身体由半透明变实）→ 形态学闭运算/填洞/保留最大连通域提取人物主体（scipy 缺省时降级为纯 alpha 硬化）→ 按前景 bbox+3% 边距裁剪。修复"仅识别到人头/人像发虚"，输出为清晰、边界准确、完整保留人物有效区域的透明 PNG；READY/OK/ERROR 协议与 Java 侧不变。单位字段新增 `sanitizeUnit`：剥离混入的姓名前缀（兼容"姓名，单位"与直接拼接），按分隔符取以机构后缀结尾的机构段，启发式与本地 LLM 语义两条路径共用，修复"李向东，汝州市第一人民医院"应为"汝州市第一人民医院"。职称兜底改为"尾边界 + 最长匹配"（`extractTitleByLongestMatch`）：保留尾边界排除"护士长"中的"护士"子串，最长匹配避免"副主任医师"被"主任医师"抢先，修复"外科副主任医师"（前接汉字"科"）被旧前后非汉字边界误拒。基准实测职称扫描约 1.2µs→1.5µs（+0.3µs），完整字段解析约 50µs 不变，对整体解析速度无实质影响。见 [交接](handover/tasks/20261117_resume_matte_unit_fix.md)。

## 会议预算制作阶段

项目默认生命周期依次为会议预算制作、会前财务资料上传、日程上传、串场制作、结算资料上传、会议费用结算及项目结项。默认流程使用启用的 `project_phase` 字典，现有预算及日程项目通过原阶段 ID 继续定位。会前财务、串场、结算资料及费用结算与结项为占位阶段，详情展示「暂未配置」，可按相邻顺序推进。进入末阶段仅记录阶段进度。项目详情及阶段推进接口沿用现有契约，历史项目如有配置快照则按快照读取。见 [六阶段交接](handover/tasks/20261118_project_lifecycle_stages.md)。

线下估算使用参会总人数、外地参会人数、场次、城市档位、工作人员和天数，另选大交通与地面交通方式。外地参会人数用于估算住宿和大交通，可独立填写。各费用只读取已有的估算参数，缺少依据的数量或单价留待补录。页面预览数量、计量单位和参考单价，用户按行应用。估算输入和已应用行的依据以 JSON 保存在 `project_budget.estimate_inputs`。日程、台卡的收费标准值用于计算数量增量，展架标准值用于每场件数。草稿明细保存后继续由服务端计算金额。

线上和线下共用对客报价版本。内部确认后生成 XLSX，`project_budget_quote` 保存项目内版本号、关联资料 ID、内部明细快照和客户确认记录。生成的文件登记在 `project_doc`，属于该项目的「会议预算制作」阶段。该类文件走项目阅读权限，报价生成、另存草稿、客户确认按现有项目成员操作权限校验。报价工作簿只包含对客说明和报价明细。快照和 Excel 随版本保留，另存草稿时从快照恢复，客户确认记录只属于所选版本。接口和验收见 [估算与对客报价交接](handover/tasks/20261117_budget_estimate_quote.md)。

「会议预算制作」推进前须完成「生成报价文件」待办。项目详情通过 `missingRequiredTemplates` 返回待办，推进接口复用同一校验。完成条件是本项目至少一份报价版本关联到当前预算阶段的有效资料，且文件可读取。内部确认是生成报价的前提，客户确认可在推进后登记。报价生成成功后详情刷新，推进按钮随待办更新。见 [报价待办与预览交接](handover/tasks/20261117_budget_quote_phase_gate.md)。

「日程上传」推进前须完成日程上传与专家匹配。当前日程候选及从日程保留下来的已确认记录均参与库外专家统计。详情与推进接口共用校验，回退不受限。前端当前待办有候选时提供「去保存」入口，候选清空后提示未建档专家「一键上传」。

项目文件预览中的 Excel（xls/xlsx，含报价文件）改用知识星球表格预览方案（2026-11-17）：新端点 `GET /api/project/files/{fileName}/workbook`（参数 sheet/rowPage/columnPage 与知识库一致），后端复用同一 `ExcelPreviewService`（POI 100行×40列分页），前端复用 `KnowledgeWorkbookPreview` 组件（新增可选 `fetcher` prop 注入 `fetchProjectWorkbook`，默认仍走知识库 API，无第二份渲染逻辑）。权限与 `GET /files/{fileName}` 同口径：报价文件（`project_quote_` 前缀）走项目阅读权限，非 Excel 返回业务错误。PDF/图片/其余 Office 预览链路不变。

2026-11-17 开发分支修复。线上标准项勾选后可编辑正整数数量，取消勾选后数量为零。独立确认接口 `POST /api/project/{id}/meeting-budget/confirm` 无请求体，沿用项目写权限，仅确认已保存草稿并登记更新人。响应继续包含 `budget` 和 `items`，金额、备注、明细及其 ID 保留。重复确认返回当前已确认单。尚未保存草稿时返回业务错误。保存和确认在事务内锁定同一预算记录。页面继续使用原有 PUT 整单保存接口。详见 [流程对照与验收记录](handover/tasks/20261117_budget_workflow_fixes.md)。

「会议预算制作」为默认第一阶段。进入该阶段时，项目详情页只展示生命周期与阶段资料。按项目大类分流：线上（ONLINE）为简化报价单（模板点选形式待定）；线下（OFFLINE）为七节费用明细（酒店=场地/住宿/用餐/茶歇；交通=大交通按专家人数预估+地面交通车队专车/平台专车；搭建=非必需；物料=设计费+公司收取串场/沟通函及延展+日程/台卡上浮+展架+条幅+其他打印+讲台花+桌花；媒体=非必需；礼品=需与客户确认；人员=固定费用+据实报销）。金额=数量×单价由服务端计算，整单覆盖式保存（DRAFT/CONFIRMED，确认后草稿不可再覆盖）。

新增三张表（迁移 `database/migration/20261116_budget_stage.sql`，幂等）：`project_budget`（一项目一份，category 快照）、`project_budget_item`（分节明细行，item_key 关联收费标准）、`project_budget_fee_standard`（收费标准默认值，fee_key 唯一，区间值存"低,高"取低值预填，种子 13 项含串场500/场、沟通函及延展500/场、人员费500/人/天、礼品200-300等）。旧接口 `PUT /api/project/{id}/budget`（登记项目预算金额）不变；新接口为 `GET/PUT /api/project/{id}/meeting-budget`、`POST /{id}/meeting-budget/confirm`、`GET/PUT /api/project/config/budget-fee-standards`（ProjectBudgetController，写权限绑定 project:manage 数据驱动）。前端：ProjectBudgetPanel 组件嵌入 ProjectDetail 阶段资料区；「管理员设置→项目配置」（/admin/project/config，路由恢复）承载收费标准编辑（BudgetFeeConfig，仅新增/改值/启停，无删除）。

## AI 助手（2026-11-16，1.0.43 已上线）

系统使用问答+操作指引卡片。新表 `ai_assistant_entry`（entry_key uk/title/questions 问法样例/answer/link_url/link_label/steps/perm_code/sort/is_active），种子 20 条核心操作；权限码 `ai:manage`（菜单+条目管理接口，绑定 ADMIN）。链路：`POST /api/ai-assistant/ask`（登录即可用，不注册 permission 规则）→ AiAssistantService 关键词 bigram 打分取 Top5 候选（按 perm_code 过滤）→ 候选不唯一时 OllamaService.completeJson(prompt, 20s) 在候选内选键（候选外键无效）→ 返回条目卡片；Ollama 不可用/超时回退关键词头部。条目管理 `GET/PUT /api/ai-assistant/entries`（ai:manage），前端「管理员设置→AI 助手知识库」(/admin/ai-assistant) 表格+弹窗编辑保存即生效；全局悬浮抽屉 AiAssistantDrawer（App.vue，登录页除外）。纯只读问答：模型不生成链接/步骤、不代执行操作。completeJson 新增双参超时重载，解析链原超时不变。

### PowerPoint 简历的照片候选

PPT/PPTX 优先取幻灯片的图片形状，组合形状会递归读取。只有幻灯片没有符合现有照片尺寸筛选的图片，或 PPTX 形状树触发兼容降级时，才扫描文件包内媒体。候选仍通过现有抠图和原图回退链路。母版和未引用图片不再占用正常幻灯片的候选名额。见 [照片候选回归](handover/tasks/20261118_resume_visible_portrait.md)。

### PowerPoint 已透明照片与单主体抠图

专家简历按文件签名识别内容为 OOXML 的 `.ppt`，并交给现有 PPTX 解析器。照片具有明显透明区域时，沿用原透明通道并裁剪画布空白。MODNet 对独立且远离主要人像的前景做连通区域清理，保留主区域软边。设计和原件验证见 [三份简历照片回归](handover/tasks/20261118_three_portrait_fixes.md)。
