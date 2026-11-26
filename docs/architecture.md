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
- 数据库存放专家信息（expert_info）、登录账号（sys_user）、下载审计（download_log）、用户操作日志（operation_log，B2）、审批流配置（approval_config）、通用申请主表（application，「我的申请」改版）、审批流转记录（approval_record）、审批申请字段模板（approval_template，R-B4）、知识星球文档（knowledge_document / knowledge_doc_version）、文档收藏（knowledge_favorite，C3）、消息通知（notification）、供应商管理（supplier / supplier_type / supplier_city / supplier_price_item / supplier_review / supplier_hotel_print_rel，2026-10-26）、企微通讯录同步（wecom_department / wecom_user_department / wecom_sync_log，2026-10-27）专家资料文件（expert_document，2026-11-20：医生证等资质资料，文件存专家资料库、经 /api/files-doc/** 登录后访问；上传/删除仅登录鉴权、刻意不校验 expert:edit，内网登录即信任，见 docs/decisions/2026-11-23-专家库权限信任模型-架构决策记录.md）以及 RBAC 权限体系（role / permission / role_permission / user_role）等数据，共 **108 张表**（**92 张业务/结构表 + 10 张历史备份表 + 6 张迁移标记表**，2026-11-20 项目类型迁移后由 `information_schema` 实测）。备份表如 `approval_config_bak_20261102` / `download_log_customer_backup_20261029` / `knowledge_doc_version_customer_backup_20261029` / `knowledge_document_customer_backup_20261029` / `knowledge_favorite_customer_backup_20261029` / `wecom_migration_uk_username_backup`。`expert_change_request` 虽为历史表但仍参与读取，计入业务表（存量 31 条已迁移至 application，新写入全部走 application，旧表保留不动）。

> 2026-11-25 实测（notification_mute 上线后现查；此前 2026-11-20 项目类型迁移后为 104/88）：库中 **108 张表**（**92 张业务/结构表** + 10 张 `*_bak_*` 备份表 + 6 张 `mig_*` 迁移标记表）。
>
> **DDL 单一可信来源**：`database\init.sql`（全量基线，92 张业务表 + 字典/权限种子，**新环境唯一建表来源**）+ `database\migration\*`（64 个增量，仅服务既有库升级）+ `schema_migration`（迁移版本台账）+ `scripts\db-migrate.ps1`（`-Mode Status/Apply/Bootstrap`）。三方一致性由 `scripts\check-schema-parity.ps1`（只读）强制校验，未通过不得 Bootstrap。

## 2. 后端分层

| 层 | 职责 | 示例 |
| --- | --- | --- |
| Controller | 接收请求、参数校验、返回结果 | `AuthController` / `ExpertController` / `ApplicationController`（通用申请全套，URL 沿用 /api/approval/*：配置、修改/新增申请、照片上传、审批、转办/加签/可转办用户列表、my-applications 我的申请；旧 my-requests 兼容路径已于 2026-11-24 下线） / `UploadController` / `ExportCompatController` / `UserController` / `AuditController`（下载留痕 + 通用用户操作日志） / `HealthController` / `SupplierController`（供应商管理：档案/行价/评价/图片全员登录即可，类型/城市字典管理 supplier:dict，酒店-打印店多对多绑定） / `RoleController`（RBAC 角色 CRUD/启停） / `PermissionController`（RBAC 权限点查询） / `KnowledgeController`（知识星球：文档上传/分页检索（category 可选=全分类、按权限过滤下架文档）/版本更新/下架/永久删除/版本历史/文件下载预览） / `NotificationController`（消息中心：本人列表（isRead/type 组合筛选）/ 未读数 / 单条已读 / 批量已读 / 全部已读 / 单条删除 / 批量删除，全部仅作用于本人消息） / `ApprovalTemplateController`（R-B4 审批申请字段模板：启用列表登录可用 / 全部列表+增删改启停 approval:config / 字段清单） / `WecomContactController`（企微通讯录同步管理：触发同步（dryRun 试算）/同步状态/最近记录/部门树/未绑定清单/人工指认绑定，权限 wecom:sync，2026-10-27） / `WecomDirectoryController`（普通员工个人通讯录：GET /api/wecom/directory/mine 返回本人根到本部门链路+本部门成员，登录即可，不暴露全量架构，2026-10-27）（共 **31 个**，2026-11-16 核实） |
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

数据库：`expert_system`（MySQL 8，utf8mb4），共 **108 张表**（92 业务/结构表 + 10 历史备份表 + 6 迁移标记表，2026-11-25 实测，与上文口径一致），含 RBAC 四表（role / permission / role_permission / user_role）、供应商七表（supplier / supplier_type / supplier_city / supplier_price_item / supplier_review / supplier_hotel_print_rel / supplier_image）、企微通讯录同步三表（wecom_department / wecom_user_department / wecom_sync_log，2026-10-27）、合规 17 表、项目 16 表、客户 4 表、知识 3 表、消息通知表（notification）、审批申请字段模板表（approval_template，R-B4）、知识文档收藏表（knowledge_favorite，C3）、用户操作日志表（operation_log，B2）与通用申请主表（application，「我的申请」改版）。

下表仅详列**早期核心表**（25 张）的字段说明；合规 / 项目 / 供应商扩展 / 客户等后续模块请直接查看 `backend/src/main/java/com/jshz/expert/entity/` 与 `database/migration/` 对应脚本。

### 我的主页：手动组织与本人账号活动

手动组织采用 profile_department 部门树和 profile_membership 主部门及岗位，可由超级管理员在我的主页维护。2026-11-24 起组织树以「总经理」为唯一根节点（共 20 节点），每个节点可设一名负责人（profile_department.leader_user_id，可空缺，一人可负责多个部门）；根节点负责人即总经理，任命时绑定 GENERAL_MANAGER 角色并清除其部门归属，换人直接顶替。组织树由超级管理员与总经理维护；部门负责人可查看本部门及下级的成员。普通用户只读本人组织信息。详见 [组织负责人交接记录](handover/tasks/20261124_organization_leaders.md)。账号活动按稳定账号 ID 查询近 30 天成功登录、退出、改密和被重置事件。旧改密链接转到我的主页并打开改密入口。详见 [主页交接记录](handover/tasks/20261124_profile_overview.md)。

### 客户档案与关系

客户列表在分页前按单位和有效关系筛选。关系图定位使用全部有效档案的姓名或单位搜索，并打开所选客户的一度关系圈。新增档案前可按姓名和单位查询疑似重复。客户任职履历存于 customer_employment，每条记录归属一个客户，单位、职务及年月独立于档案基本资料。履历读取要求登录，维护要求 customer:manage。详见 [客户信息四项需求交接](handover/tasks/20261124_customer_info_features.md)。


客户资料更新将显式提交的空字符串或纯空白保存为数据库空值，省略的可选字段保留原值。列表关系摘要与中心关系圈仅展示两端档案均有效的关系。已删除档案在维护人的回收站提供只读摘要和恢复。表单上限由客户接口提供，新增档案附带的关系在档案创建事务内校验和写入。接口和验证步骤见 [客户信息修复交接](handover/tasks/20261124_customer_info_fixes.md)。


| 表 | 说明 | 关键字段 |
| --- | --- | --- |
| `expert_info` | 专家主表（18 字段） | id、name、unit、department、title、research_direction、expertise_area、contact、profile、id_card、bank_card、photo、bank_branch、id_card_front、id_card_back、create_time、update_time、update_by |
| `sys_user` | 登录账号 | username=姓名、password（BCrypt）、is_admin（管理员名单兼容标记）、must_change_password（强制改密）、wecom_userid（企微唯一身份锚点）、mobile（手机号登录标识，2026-10-27 新增）、position（职务，2026-10-27 新增）、status、token_version（Token 版本号，2026-11-06 新增，停用/删除/改密/改角色时自增）；唯一索引 uk_wecom_userid（wecom_userid）/ uk_mobile（mobile）/ uk_username（已于 2026-10-27 删除，改由应用层保证未绑定通道内姓名唯一，企微同步可产生重名用户） |
| `download_log` | 下载审计 | 下载人、专家、时间、类型、IP；selected 导出逐专家一条、全量/md 单条；C4 起知识文档下载（download_type=knowledge_doc）记 doc_id 与文档标题；B2 起每次写入同时双写 operation_log（download_log 保留，下载次数统计仍依赖本表） |
| `approval_config` | 审批流配置 | flow_key（UNIQUE，EXPERT 专家信息修改/新增、CONTRACT 合同审批）、flow_name、enabled、approver_role；具体审批人见 `approval_config_user` |
| `application` | 通用申请主表（「我的申请」改版） | applicant_id、applicant、application_type（EXPERT_MODIFY/EXPERT_CREATE/CONTRACT）、flow_key（EXPERT/CONTRACT，存量空）、status（0待审/1通过/2驳回/3撤回/4退回修改，五态）、biz_data（JSON 业务详情：专家申请 {expertId, expertName, changes, reason, oldData?}，合同申请 {contractId, projectId, expertId, expertName, changes, reason}，照片 TMP: 语义保留；oldData 为修改申请提交时的专家旧值快照，审批前后对比用）、approve_by、approve_comment、approve_time、return_comment、current_assignee（B2）、extra_approver（B2）、approver_snapshot（提交时按流程配置生成的审批人快照 JSON）；索引 idx_applicant/idx_applicant_id/idx_status/idx_application_type |
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

> RBAC 说明：内置角色含 SUPER_ADMIN 代码内全权锁定、GENERAL_MANAGER 总经理（代码内计算：全部权限码减去 role:manage/audit:view/wecom:sync/storage:manage；只能经组织树根节点任命，角色管理页不可分配、不可改绑定、不可停用，新增仅限超管的权限码须登记到 PermissionService.GENERAL_MANAGER_EXCLUDED）、ADMIN 业务全权、APPROVER 审批、EMPLOYEE 无绑定等；permission.code 允许重复（一码多路径），UNIQUE(method,path) 约束接口唯一；鉴权链路为 JWT claims.roles + DbPermissionFilter 按 method+path 匹配权限码（命中校验、未命中放行）。知识星球上传/更新/版本/文件下载接口登录即可（不设权限码），但文件流端点会按存储文件名反查文档并校验状态：文档不存在/已永久删除返回业务码 404，已下架且非 `knowledge:maintain` 返回业务码 403，避免下架后旧链接继续可下载（P1-5）；列表 GET /api/knowledge/docs 登录即可且 category 可选（不传=全分类），并按权限过滤 status：具备 `knowledge:maintain`（知识星球维护人/超管）返回上架+下架，普通员工仅上架；下架接口 DELETE /api/knowledge/docs/{id} 与永久删除接口 DELETE /api/knowledge/docs/{id}/permanent 均需接口权限码 `knowledge:maintain`（已绑定 KNOWLEDGE_MAINTAINER），业务层 AuthUser.hasPermission 二次校验；永久删除同时删除主记录、全部版本历史与磁盘物理文件，不可恢复。

> 知识文档异步解析（2026-11-06，P2-7）：上传/更新请求只流式落盘并写入 `parse_status=PENDING`，随后提交 `knowledgeParseExecutor` 异步任务；正文抽取（PDFBox/POI）与企业合规 Excel 草稿解析均在线程池内完成，状态/正文更新带 `id+version` 条件，旧任务不会覆盖新版本。解析失败只记录 `FAILED` 与原因，不回滚已上传文件；PDF/DOC/DOCX 抽取为空按失败处理（可能损坏、加密或为扫描件），其余不支持正文的格式标记成功但不参与正文检索；线程池拒绝时同步标记失败，上传本身仍成功。线程池参数见 `expert.knowledge.parse.*`，队列满时采用 AbortPolicy。历史数据 `parse_status=NULL`（接口原样返回，不做批量回填）。

> 知识星球增强（2026-10-26，C1/C3/C4）：① C1 内容检索——knowledge_document.content 存上传/更新时抽取的正文（PDFBox/POI，截断 200k 字符），GET /api/knowledge/docs 的 keyword 扩展为 title/file_name/content 三列 LIKE；前端 placeholder「按标题 / 内容关键字检索」。② C3 收藏——knowledge_favorite 表（UNIQUE(doc_id,user_id)），接口 POST/DELETE/GET /api/knowledge/favorites[/{docId}]（登录即可，userId 取当前用户防越权；路径避开 permission 表 DELETE /api/knowledge/docs/** 通配规则），PUT /api/knowledge/docs/{id} 更新成功后向全部收藏者（排除操作人）写 doc_updated 通知；前端列表/总览操作列收藏按钮即时切换，知识星球父页「全部文档/我的收藏」切换。③ C4 下载次数——download_log 加 doc_id 列，附件下载（download=1，预览不计）写 download_type=knowledge_doc 记录，列表接口单次 GROUP BY 聚合返回 downloadCount，前端「下载次数」列仅维护人（`knowledge:maintain`）可见数值，他人显示 —。

> R-B4 审批申请字段模板说明：表 `approval_template`（template_name 唯一、fields 为中文键 JSON 数组、enabled 启停）；员工端 GET /api/approval/templates 返回启用模板（登录即可），管理端 GET /api/approval/templates/all 与 POST/PUT/DELETE /api/approval/templates[/{id}] 复用权限码 `approval:config`（permission 表一码多路径）；提交申请（POST /api/approval/requests、/api/approval/create-request）带可选 templateId：传入时校验 changes 字段集合 ⊆ 模板字段且模板启用，并落库 application.biz_data.template_id；不传走原逻辑，历史申请兼容。模板字段必须 ⊆ ApplicationService.WHITELIST_FIELDS + PHOTO_FIELDS（服务端校验）；「专家新增」种子模板覆盖 CREATE_REQUIRED_FIELDS 全部必填项；前端 ExpertList 修改/新增弹窗提供模板下拉，选择后仅渲染并提交模板字段，新增弹窗仅展示覆盖必填项的模板；管理员设置菜单新增「审批模板管理」页（/admin/approval/templates）。

> B2 审批转办/加签（2026-10-26，语义已确认：加签=或签，目标用户须有 approval:audit）：expert_change_request 新增 current_assignee / extra_approver 两列（可空，历史单兼容）。转办 POST /api/approval/requests/{id}/transfer（body {targetUser}，权限码 approval:audit）：仅 status=0 可转，操作者须有 approval:audit 且满足 current_assignee 为空或等于本人；转办后仅被转办人可审批（approve/reject 前经 checkAssignee 校验），写 approval_record（action=TRANSFER，nodeName=审批转办，comment=转给【XX】）并通知被转办人（approval_transferred）与申请人。加签 POST /api/approval/requests/{id}/add-approver（body {targetUser, comment?}）：或签语义——原审批人与被加签人任一 approve 即通过、任一 reject 即驳回（不改状态机），已加签不可重复加签，写 ADD_APPROVER 记录并通知被加签人（approval_assigned）。可转办/加签用户列表 GET /api/approval/assignable-users（approval:audit）：返回启用且拥有 approval:audit 的用户 [{id,username}]。前端 ApprovalManage 待审批行新增「转办/加签」按钮（行归属他人时隐藏）与选人弹窗，「当前审批人」列展示归属；MyApproval 进度时间线支持 TRANSFER/ADD_APPROVER 节点。

> 用户操作日志（B2 改版）：新表 operation_log 统一记录敏感操作——下载/导出（DownloadAuditService 双写，download_log 保留兼容，历史数据已由迁移脚本以 action_type=DOWNLOAD 并入，source_id+UNIQUE 防重复迁移）、专家新增/修改/删除/导入、知识文档上传/更新/下架/永久删除、登录/登出/修改密码/重置密码、用户增改删/分配角色/角色权限变更。统一写入入口 OperationLogService.record()（try-catch 降级，日志失败不影响主业务）；查询接口 GET /api/audit/operations?username=&actionType=&startTime=&endTime=&page=&size（audit:view，时间支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，纯日期结束取当日 23:59:59）。前端管理员设置二级菜单「下载留痕」更名为「用户操作日志」（路由 /admin/audit/downloads、组件、audit:view 权限码均不变），页面支持用户名模糊/操作类型/时间范围组合筛选，操作类型 el-tag 着色。

> 企微通讯录同步与账号体系改造（2026-10-27，评审通过版实施）：① 同步链路——`WeComApiClient`（纯出站 HTTP，token 缓存+40014/42001 自动重取、user/list_id 分页、department/list_id 或退回 department/list、user/get 逐人节流；日志零 secret/零 access_token，手机号 138****1234 脱敏）→ `ContactSyncService.run`（DryRun 只写变更清单不落业务数据；diff 绑定/新建/停用；wecom_sync_log RUNNING 并发护栏 409；手动同步异步执行返回 logId）→ 落库 wecom_department / wecom_user_department / wecom_sync_log。触发器：手动 POST /api/admin/wecom/sync + 定时 @Scheduled 每日 02:30（WecomSyncScheduler，sync-cron-enabled=false 默认关闭，开启且 corp-id/secret 为空 fail-fast）。② 登录标识三模式——AuthService.login 按 expert.wecom.login-mode（${AUTH_LOGIN_MODE:NAME}）分流：NAME 完全等同现状（姓名+自动注册，回滚档）；COMPAT 手机号格式 `^1[3-9]\d{9}$` 走 mobile 查、否则按姓名查未绑定用户（wecom_userid IS NULL）且姓名自动注册仅对未绑定用户保留；MOBILE 仅手机号+密码、关闭自动注册（账号只来自同步或管理员创建）。is_admin=1 管理员登录豁免（本期配置 AUTH_LOGIN_MODE=MOBILE 时管理员仍可登录）。停用检查/BCrypt/JWT/cookie/强制改密流程均未改动。前端 Login.vue 调 GET /api/auth/wecom/config（白名单，登录前可访问）按 loginMode 切换姓名/姓名或手机号/手机号文案。③ 管理接口（WecomContactController，/api/admin/wecom，权限 wecom:sync，permission 表一码多路径 6 条绑 ADMIN）：POST /sync（{dryRun}，返回 logId）、GET /sync/status（logId 可选）、GET /sync/recent、GET /departments（部门树含人数）、GET /unbound、POST /users/{id}/bind-wecom（{wecomUserId} 人工指认）；手动同步/人工绑定写 operation_log（WECOM_SYNC/WECOM_BIND）。④ 个人通讯录——WecomDirectoryController GET /api/wecom/directory/mine（登录即可，DbPermissionFilter 未命中规则放行），返回本人"根到本部门"链路与本部门成员（手机号脱敏），不暴露全量架构；前端个人中心「通讯录」页 /profile/directory。⑤ 存量绑定策略——自动：企微姓名↔系统未绑定用户姓名双方唯一才自动绑定，歧义进人工指认；人工：管理页指认；不绑定不阻塞登录（COMPAT 下未绑定用户仍姓名登录）。⑥ 迁移脚本 database/migration/20261026_add_wecom_contact_sync.sql（幂等，测试库已执行 3 遍通过）：3 新表 + sys_user.mobile/position 列 + uk_wecom_userid/uk_mobile + 删 uk_username 前备份表 wecom_migration_uk_username_backup + wecom:sync 权限种子。

> 审批管理 1.0.143 增强（2026-11-25；审查 `docs/handover/tasks/20261125_approval_manage_audit.md`）：
>
> **缺陷修复**：① 启用「退回修改」前端入口——`ApprovalTable` 待审操作列补退回按钮（此前后端 returnRequest/permission 行/对比弹窗 return 模式/事件管线全就绪但操作列从未渲染按钮，整条退回链路自上线不可达，库中 status=4 恒为 0）；② 快速审批并发 409 自动推进——`ApprovalDiffDialog` 捕获 409 上抛 `stale`，父组件移出队列并切下一条（此前坏行停留反复报错）；③ 列表跟随全局通知轮询静默刷新（`store.lastPollAt`，新申请提交后无需手动刷新）；④ 列表加载失败错误态+重试、分页越界回退、深链 `applicationId` 打开后清参、转办/加签下拉排除本人、可转办人加载失败提示、快速审批阈值提示歧义修正、`size=100` 改取 `PAGE_SIZES` 上限。
>
> **审批筛选增强（需求 A）**：待审/已处理两 Tab 增加关键词（专家姓名/申请人，后端 `listRequests.keyword` 现成）与申请类型筛选；已处理 Tab 增加状态筛选（已通过/已驳回/已撤回/退回修改）；`GET /api/approval/requests` 新增 `type` 参数（`ApplicationController.normalizeType` 归一化，非法 400）；筛选条件写回 URL。
>
> **工作台审批统计面板（需求 D）**：新增 `panel.approvalStats` 卡片（`approval:audit` 可见），`DashboardReadService.approvalStats` 聚合待审总数/滞留≥24h/≥72h/今日已处理/近30天平均耗时（口径与待办面板的或签/转办谓词一致，滞留自最近 SUBMIT/RESUBMIT 起算）；`DashboardController.SECTIONS` 与 `DashboardLayoutService.APPROVAL_CARDS` 同步登记。
>
> **审批滞留催办（需求 E）**：`ApprovalUrgeReminderTask` 每小时 :15 扫描待审批单，滞留达 `APPROVAL_URGE_FIRST_HOURS`(24)/`APPROVAL_URGE_SECOND_HOURS`(72) 分别发 URGE_24/URGE_72 催办通知（复用待办通知链路 + 补发转办/加签人），以审批进度记录 action 为去重凭据同级别只催一次，不建新表；状态守卫防并发已处理单被催。
>
> 无数据库结构变更（催办复用 approval_record）；新增 3 个环境变量已登记 deployment.md 与 AGENTS 18.4。

> 消息中心 1.0.142 增强（2026-11-25）：① SSE 实时推送 `GET /api/notifications/sse`（登录即可，text/event-stream；只发 refresh 信号+25s 心跳，X-Accel-Buffering:no 关代理缓冲，30s 轮询保留为兜底；连接注册表为单机内存实现，多实例部署需改造；2026-11-25 发射器改有限超时 30 分钟定期回收，配合 nginx worker_shutdown_timeout 60s 解决优雅重载排空阻塞）；② 按类型免打扰 `notification_mute` 表（迁移 20261125_add_notification_mute.sql；GET/PUT `/api/notifications/mutes` 全量覆盖保存；notify 落库前过滤，password_reset 强制送达不可静音，偏好查询失败按未静音放行）；③ 保留期清理任务 `NotificationRetentionCleanTask`（每日 03:40 物理删除「已读且超期」通知，未读永不清理，env `NOTIFICATION_READ_RETENTION_DAYS`/`NOTIFICATION_RETENTION_CLEAN_CRON`）；④ 类型白名单注册表 `NotificationService.KNOWN_TYPES`（列表筛选与偏好保存的 type 参数校验，前端展示层副本 `utils/notificationTypes.js`）；⑤ Controller 分页钳制与 markRead 条件更新。调查记录：`notification` 表 2026-11-19 后无新消息非缺陷，详见 `docs/reviews/20261125-notification-silence-investigation.md`（合规在办到期事项=0、无新申请；11-21 修改类申请受 1.0.138 前「提交不通知」旧缺陷影响）。
>
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

> 「我的申请」通用化改版（2026-10-26）：新表 `application` 为通用申请主表（applicant_id/applicant/application_type/status 五态/biz_data JSON/approve_by/approve_comment/approve_time + B2 转办加签两列），业务差异存 biz_data（专家申请 {expertId, expertName, changes, reason, oldData?}，照片 TMP: 语义保留；`templateId` 已于 2026-10-31 随审批模板功能下线，不再写入）；类型为代码枚举 ApplicationType（EXPERT_MODIFY/EXPERT_CREATE/CONTRACT）。`expert_change_request` 存量 31 条已一次性迁移（保留原 id，approval_record.application_id 按同 id 回填 65/65），旧表保留作历史备份、新写入停用。HTTP 层 ApplicationController（URL 沿用 /api/approval/*，permission 路径规则与前端 api/approval.js 不变）：新接口 GET /api/approval/my-applications（登录即可，applicant_id/applicant 双条件仅本人），GET my-requests 旧路径已于 2026-11-24 下线（前端零调用，仅保留 my-applications 入口）；提交/审批/驳回/撤回/转办/加签全链路读写 application；审批通过 update_by 记申请人语义不变。approval_record 新链路写 application_id（request_id 同值保留满足非空约束）。前端：MyApproval.vue 升级为「我的申请」（新增审批人/审批时间两列，列表刷新跟随全局通知 store 轮询（store.lastPollAt），页面不自建定时器）；「审批管理」菜单从管理员设置迁移至个人中心（/profile/approval/manage，permissions: approval:audit，普通员工不可见），/admin 父路由权限并集缩为 5 码；存量通知 jump_path=/admin/approval/manage 已由迁移脚本批量改为 /profile/approval/manage；跳转 /profile/my-approvals?status=1|2 语义不变。

> 「我的申请」审查修复（2026-11-24，1.0.138）：修改申请提交后补发审批人待办通知（此前仅新增/重提通知）；撤回与重新提交的本人判定改为 applicant_id/applicant 双条件；oldData 视图增加联系方式脱敏（1.0.139 起按下方「脱敏口径统一」改为一律明文）；重新提交（status=4）放开照片字段（仅接受 TMP: 临时文件名，被替换的旧临时照片事务提交后清理，合同申请仍仅身份证号/银行卡号）；前端重新编辑弹窗支持重传照片、列表刷新改为跟随全局通知 store 轮询（store.lastPollAt，页面不再自建定时器）；对比弹窗修正合同申请被误判「历史数据」的提示。删除无调用代码：`GET /api/approval/my-requests`、`ApplicationService.resubmit(id,user)`、`listRequests(Integer,...)`、`getConfig()`。无数据库/权限/配置变更。

> 审批「申请前后变化」对比（2026-10-27）：修改申请提交时 `ApplicationService.submitRequest` 从 expert_info 读全量字段写 biz_data.oldData 旧值快照（14 键：11 文本 + 3 照片文件名，新增申请置空）；查询接口返回项增加 `oldData`（脱敏口径见下方 2026-11-24 脱敏统一说明）与 `photoChanges`（照片原始文件名供前端预览）；审批通过时把 biz_data.changes 照片键 TMP: 值回填为归档文件名（生效后可预览），且 oldData 快照存在时跳过旧照片物理删除（保证历史单随时可还原「变更前」）。`WebConfig` 新增 /api/files-tmp/** 映射审批临时照片目录（待审批单新照片预览）。前端新增公共组件 `ApprovalDiffDialog.vue`（审批管理 + 我的申请共用，mode=view/approve/reject/return），审批管理与我的申请操作列加「查看对比」、通过/驳回整合进对比弹窗、摘要列升级「旧值 → 新值」；移除原展开行实时查专家表逻辑（oldValuesMap/handleExpand）。存量迁移 `database/migration/20261027_backfill_application_old_data.sql`：EXPERT_MODIFY 且 status∈{0,2,3} 的存量单用 expert_info 当前值回填 oldData（幂等、无 DDL），status=1 已通过单保持空（前端显示历史单提示）。

> 「我的申请」删除口径放宽（2026-11-25，1.0.141，负责人决策）：单条与批量删除（`DELETE /api/approval/requests/{id}` 与 `/batch`）的状态条件由 {2 已驳回, 3 已撤回} 放宽为 {1 已通过, 2 已驳回, 3 已撤回}；已通过单删除后专家档案保持已生效状态，变更留痕由 operation_log（DELETE_APPLICATION）保留；待审批(0)须先撤回、退回修改(4)须先重新提交的限制不变。「我的申请」列表去掉「变更摘要」列（移动端卡片同步），变更内容在详情抽屉查看。

> 「我的申请」第二轮审查 P2/P3 修复 + 详情抽屉 + 批量删除（2026-11-25，1.0.140；交接 `docs/handover/tasks/20261125_my_applications_p2p3_detail_drawer.md`）：
>
> **接口**：新增 `DELETE /api/approval/requests/batch`（body `{ids:[…]}`，≤100，登录即可 + Service 本人校验，口径同单条删除：仅已驳回/已撤回；不可删条目跳过，返回实际删除条数）。`Application` 响应新增 `expiredPhotoFields`（退回修改单里临时文件已被清理的照片键）与重提响应的 `droppedPhotoFields`（本次被剔除的失效照片键）；`bizData`、`approverSnapshot` 不再序列化（解析后的展示字段已承载）。提交修改/新增/合同接口的 `changes`/`reason` 类型错误按 400 拒绝（此前强转抛 500）。
>
> **业务规则**：① 重提时清空非必填文本字段 = 撤回该字段的变更（仅对带 `oldData` 快照的修改申请生效；必填字段清空仍拒绝；新增/合同申请维持原语义），避免审批通过把专家字段覆盖成空串；② 重提的敏感字段格式校验限定到本次改动的键，存量旧格式值不再阻塞重提；③ 列表关键词去掉恒真的 `applicant` 条件并转义 LIKE 通配符，排序追加 `id` 兜底；④ 合同申请的待办/撤回通知文案改用 `typeTextOf`。
>
> **前端**：`MyApproval.vue` 的「查看进度」「查看对比」合并为详情抽屉（`ApprovalDiffDialog` 新增 `variant=drawer`、`drawerSize`、`extra` 插槽，默认值不变，审批管理页不受影响）；列表多选 + 批量删除；30 秒轮询改静默刷新并按 id 更新已打开的详情；弹窗/抽屉按屏宽自适应；深链打开后移除 URL 的 `applicationId`；`approvalFields.js` 新增 `CREATE_REQUIRED_FIELDS`、`NAME_PATTERN`，移除零调用的 `applicationTypeText`。对比弹窗修正退回修改单的临时照片被误标「已清理」（退回时实际保留）。
>
> 未做（待负责人决策）：P1 退回修改单可撤回；合同发起提交的待办通知。无数据库/权限/配置变更。

> 「我的申请」审查修复与增强（2026-11-24，1.0.139；审查报告 `docs/reviews/20261124-我的申请-代码审查.md`）：
>
> **缺陷修复（P1/P2/P3 共 18 项）**：① `readBiz` 解析失败记 ERROR 日志，`approveModify` 遇空 changes、`approveContract` 遇缺 `contractId` 直接阻断（此前会「空审批通过」：状态置已通过但专家字段一条未更新）；② 分页参数在 Controller 与服务层双重钳制（page≥1、1≤size≤100），`MybatisPlusConfig` 设 `maxLimit=100` 兜底，列表进度记录改为按页内 id 集合一次 `IN` 查询回填（消除逐行 `recordsOf` 的 N+1）；③ 合同重提回写 `contract.status=PENDING` 与 `approval_ref=申请单id`，撤回/驳回/退回时清空 `approval_ref`，并新增「同一合同只允许一张在审单（status∈{0,4}）」校验；④ 修改申请与重提增加姓名/单位非空与姓名格式校验（只校验本次实际改动的键，避免历史值卡死重提），文本值必须为字符串、单字段≤5000 字，申请原因提交与重提统一≤1000 字；⑤ 重提时已失效的临时照片键自动剔除并记 WARN（不再硬拒导致 status=4 永久滞留），新增 `ApprovalTempPhotoCleanTask` 按保留期清理临时照片（默认 7 天，保护待审批/退回修改单据引用的文件）；⑥ 前端列表请求加序号防乱序覆盖、失败态可重试、页码越界自动回退、改变每页条数重置页码，URL `status`/`type` 白名单校验；⑦ `applyChanges` 补「身份证有效期」映射（此前白名单放行但无落库分支，值被静默丢弃）；⑧ 清理悬挂 javadoc、`applyChanges` 恒假条件、`maskedOldData` 与 `isFullSensitive`。
>
> **脱敏口径统一（2026-11-24）**：`oldData` 与 `changes` **一律明文**，与 `ExpertService.toView`（全员明文）一致。此前 `oldData` 对非管理员前3后4、`changes` 明文，同一对比弹窗出现「变更前打码 / 变更后明文」，且注释声称与 `/api/experts/{id}` 一致（该接口早已改为明文）。申请单可见范围仅「本人（applicant_id/applicant 双条件）+ 持有 approval:audit 的审批人」，审批必须核对原文。
>
> **新需求**：R1 列表筛选增强（`GET /api/approval/my-applications` 增 `type`/`keyword`/`startDate`/`endDate`，关键词覆盖专家姓名、申请原因、申请人；前端筛选条写回 URL query，仍强制本人过滤）；R2 列表新增「当前处理人」「已等待」两列（`fillView` 派生 `currentAssigneeName`/`pendingHours`，只读、不改状态机，停留时长自最近一条 SUBMIT/RESUBMIT 记录起算）；R5 移动端卡片视图（≤768px 替代宽表格，保留进度/对比/撤回/重提/删除全部入口）；新增**删除申请**（`DELETE /api/approval/requests/{id}`，仅申请人本人且 status∈{2 已驳回, 3 已撤回}，同事务删除进度记录并以条件删除防并发，写 `operation_log` `DELETE_APPLICATION` 留痕，事务提交后清理临时照片；待审批须先撤回、已通过不可删）。
>
> **前端常量收口**：新增 `frontend/src/utils/approvalFields.js`（文本/照片/敏感/合同字段、长度上限、状态与类型选项、page-sizes），`MyApproval.vue`、`ApprovalDiffDialog.vue`、`utils/format.js` 不再各写一份字面量。
>
> **CONTRACT 类型现状（Q12 结论：保留待启用）**：后端 `POST /api/approval/contract-request`、状态流转、合同联动与 `MyApproval`/`ApprovalDiffDialog` 的合同分支均已实现，但**前端无发起入口**（全库零调用），属「待启用」而非已上线功能；启用前需在项目管理结算合同页补发起入口并验收。
>
> 无数据库结构、权限码、配置项语义变更；新增可比环境变量 `APPROVAL_TEMP_PHOTO_RETENTION_DAYS` / `APPROVAL_TEMP_PHOTO_CLEAN_CRON`（默认值等于当前行为）。
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

### 合规复用答案、待办来源状态与提醒记录分页（2026-11-23，1.0.134）

对应需求 19/20/21（21a/21b）。三处改动均不改变既有权限边界：写入仍限本人，维护人接口仍要求 `knowledge:maintain`。

- **需求19 复用答案待确认**：`compliance_execution_task` 新增 `answer_unconfirmed`（1=待确认）与 `answer_unconfirmed_source`（来源说明，如「复用清单#12 的答案」）。`reuseExecution(copyAnswers=true)` 复制答案时置位并写入来源；`patchTask` 提交 `answer`（本人手工填写）或 `confirmAnswer=true` 即清除标记并记 `ANSWER_CONFIRM` 事件；`progress=COMPLETED` 时若仍待确认则拒绝完成（同一请求内填写/确认放行）。`executionDetail` / `myTasks` 的每个任务返回 `answerUnconfirmed`、`answerUnconfirmedSource`、`answerUnconfirmedNotice`，供界面渲染醒目提示。空答案不进入待确认态，避免影响正常手工填写。
- **需求20 OCR 表格非 4 列**：`ComplianceOcrTable.grid` 不再要求恰好 4 条竖线，接受 3~7 条（2~6 个内容列）。列语义由表头判读：含「项目/事项」的单元格为标题列，含「标准/内容/要求」的为正文列；4 竖线标准格式在表头不可判读时回退原列位，保持既有识别结果。非标准格式且表头无法判读、整行空白、标题或正文缺失时按既有规则返回空/PAGE 标记，不生成错误事项草稿。`blocks()` 返回已判读列位的栅格，`refineRows` 复用同一列映射。
- **需求21a 待办来源状态**：`GET /api/compliance/my-todo` 不再静默隐藏来源下架/删除事项，改为返回 `sourceStatus`（ACTIVE/OFFLINE/MISSING）、`sourceNotice`（如「来源文档已下架，仅供历史参考」）与 `actionable`；可执行项排前，统计只计 `actionable` 项并新增 `sourceUnavailableCount`。`PUT /api/compliance/my-progress/{itemId}` 对来源失效事项直接拒绝，防止误更新。
- **需求21b 提醒记录分页**：`GET /api/compliance/reminder-logs` 由「按 itemId 全量返回」改为服务端分页（`page`/`size`，默认 1/20，上限 200）并支持 `from`/`to` 按 `scheduled_time` 过滤（支持 `yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm:ss`；仅日期时含当日全天）。返回 `{total,page,size,from,to,items}`；非法分页或起点晚于终点返回业务错误。

- 合规 PDF 候选识别独立版面标题，主题标题覆盖适用对象与段内义务。本地模型仅在无法生成原文候选时兜底，其提示包含封面排除、金额保真及 OCR 核对规则。

简历解析在候选后端 1.0.38.27 中并行运行文字抽取和照片处理。每个后端进程复用本机 U²-Net 工作进程，启动时预热，抠图失败时回退原图 JPEG。见 [交接](handover/tasks/20261113_resume_speed.md)。

对原图不透明、顶部为纯色且 U²-Net 掩码提前结束的证件照，工作进程从顶边识别连续背景。只有与已有主体重合且延伸至原图底部中央的区域才用于恢复肩部和衣服。专家库新增、编辑及批量上传的候选缩略图按原比例完整显示。规则和复测步骤见 [证件照肩部恢复](handover/tasks/20261118_resume_portrait_shoulders.md)。

`POST /api/experts/resume/parse` 对旧版二进制 PPT 使用 POI HSLF。PPTX 使用 POI 只读文件模式按 ZIP 中央目录读取条目，避免流式读取带数据描述符的未压缩图片时误报解压比。系统临时文件在图文抽取后删除，POI ZIP 限制继续生效。 如果整个 PPT/PPTX 缺少可读取的正文，且有直接放置在幻灯片上的大幅图片，则取第一张宽、高分别达到幻灯片宽、高 60% 的图片，对较小截图做有界放大，复用图片简历的本机 OCR，再进入字段和简介提取。专家库新增、编辑和批量上传复用图片查看器，用户可点开候选和已选图片，批量候选的选用按钮独立于图片预览。验证步骤见 [PPT 解析和照片预览](handover/tasks/20261118_resume_ppt_preview.md)。

简历解析优先读取正文明确姓名，排除章节标题，并支持简介新等文件名后缀。大尺寸竖版照片参与候选筛选，在 U²-Net 推理前缩放。

简历解析支持从正文分句提取医院和科室，职称可来自同一任职描述或独立分句。简历文件名中的机构候选沿用既有优先级。默认解析在文件名与正文独立标题一致时可识别以「科」等科室后缀结尾的人名，并从科室候选中排除已识别姓名。contentNameOnly 仅限制姓名来源。图片简历优先使用本机 PP-OCRv5 的置信度过滤文本行，无法取到文本时回退本机 Tesseract。

2026-11-17 抠图与单位字段修复。证件照抠图工作脚本（`photomatte-worker.py`）在 U²-Net 背景分割后增加精修链：小图（最长边 <640px）先 LANCZOS 放大提升掩码细节 → gamma 硬化软 alpha（身体由半透明变实）→ 形态学闭运算/填洞/保留最大连通域提取人物主体（scipy 缺省时降级为纯 alpha 硬化）→ 按前景 bbox+3% 边距裁剪。修复"仅识别到人头/人像发虚"，输出为清晰、边界准确、完整保留人物有效区域的透明 PNG；READY/OK/ERROR 协议与 Java 侧不变。单位字段新增 `sanitizeUnit`：剥离混入的姓名前缀（兼容"姓名，单位"与直接拼接），按分隔符取以机构后缀结尾的机构段，启发式与本地 LLM 语义两条路径共用，修复"李向东，汝州市第一人民医院"应为"汝州市第一人民医院"。职称兜底改为"尾边界 + 最长匹配"（`extractTitleByLongestMatch`）：保留尾边界排除"护士长"中的"护士"子串，最长匹配避免"副主任医师"被"主任医师"抢先，修复"外科副主任医师"（前接汉字"科"）被旧前后非汉字边界误拒。基准实测职称扫描约 1.2µs→1.5µs（+0.3µs），完整字段解析约 50µs 不变，对整体解析速度无实质影响。见 [交接](handover/tasks/20261117_resume_matte_unit_fix.md)。

## 会议预算制作阶段

项目默认生命周期依次为会议预算制作、会前财务资料上传、日程上传、串场制作、结算资料上传、会议费用结算及项目结项。默认流程使用启用的 `project_phase` 字典，现有预算及日程项目通过原阶段 ID 继续定位。结算资料上传按项目类型展示分组资料清单，费用结算及项目结项仍按既有阶段流程展示。进入末阶段仅记录阶段进度。项目详情及阶段推进接口沿用现有契约，历史项目如有配置快照则按快照读取。见 [六阶段交接](handover/tasks/20261118_project_lifecycle_stages.md)。

项目详情的结算资料区基于现有资料接口展示必填进度及缺项。批量上传先在浏览器按文件名建议归类，经确认后按细项发起现有提交请求，有文件的细项追加，失败项保留供重试。酒店发票合计由当前批次中具有明确人民币金额标记的文件名计算，仅供页面参考。见 [结算资料页面交接](handover/tasks/20261126_settlement_stage_ui.md)。

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

专家简历按文件签名识别内容为 OOXML 的 `.ppt`，并交给现有 PPTX 解析器。已有透明人像沿用原透明通道并裁剪画布空白。若透明通道符合规则的圆形或椭圆形排版裁切，则先清除外圈装饰，再在原裁切范围内用本地 MODNet 或 U²-Net 分割人像。MODNet 对独立且远离主要人像的前景做连通区域清理，保留主区域软边。见 [圆形模板抠图回归](handover/tasks/20261119_framed_portrait_matte.md)。

项目会前财务资料以项目主数据同步会议日期、会议/项目名称和适用的我方负责人。确认会议报价复用预算行锁、项目文档和报价版本表完成同事务归档，按报价内容去重，项目文件按最新挂载顺序提供预览与下载。设计及兼容边界见 [会前财务资料与报价归档设计](design/2026-11-20-project-finance-quote-cleanup.md)。


### 项目阶段交付物（1.0.100）

- 项目详情的 missingRequiredTemplates 与推进接口共用交付物校验。预算阶段校验该项目该阶段已关联报价版本的 Excel，财务阶段校验合规立项 Excel，串场阶段校验归档的 PPT/PPTX 或 HTML。
- PUT /api/project/{id}/stage-form 复用既有阶段表单。财务按类型填充原 Excel 模板，提交成功后替换上一次归档。串场使用同一接口保存项目草稿。
- GET /api/project/{id}/show/agenda 从项目当前原始日程提取环节，按现有项目阅读权限提供；编辑器可补录和排序。POST /api/project/{id}/show/ppt 校验 PPT/PPTX 格式，POST /api/project/{id}/show/html 将结构化页面渲染为离线 HTML。归档均要求当前阶段与现有项目操作权限。POST /api/project/{id}/show/preview 将未归档草稿渲染为离线 HTML 字符串返回（不落盘、不写操作记录），与 show/html 共用同一渲染模板，仅需项目阅读权限。
- 项目详情「日程上传」参会专家表按已确认记录的专家 ID 区分已关联与库外。证件风险仅决定已关联行的风险标记。
- 串场阶段的「下载专家资料」调用 `GET /api/project/{id}/show/expert-materials`。接口要求登录、项目可读且调用者为项目成员；没有已匹配的已确认参会专家时返回业务错误 404。服务按专家 ID 去重，查询姓名、头像和简介，并从专家库照片存储读取头像。成功响应为 `application/zip` 附件且禁止缓存，内含 `专家资料.md` 和 `头像/`。档案、头像或简介缺失时在文档注明。ZIP 按请求生成，无项目文件归档、数据库写入或外部模型调用。弹窗显示可复制的鉴权链接、资料内容及 AI 提示词。
- 结算资料通过 `GET /api/project/{id}/settlement-materials/{phaseId}` 读取适用上传口、当前批次、历史批次、可写状态和预算来源。项目成员使用 `POST /api/project/{id}/settlement-materials/{phaseId}/{slot}` 提交 multipart `files`、`reason` 或 `sourceDocId` 中的一项。读取沿用项目可读规则，写入并校验项目阶段、类型和成员，只有平台 U18 在后续阶段可补传。
- 结算资料复用 `project_doc` 和项目资料库，服务生成 `project_settlement_<slot>_<batch>_...` 用于归类。替换批次软删前批，文件下载检查有效文档与项目读取权限，普通删除入口保护管理中的结算资料。同一项目内资料提交与阶段推进通过项目行锁串行，事务回滚清理本次新文件。推进依据适用口最新批次和物理文件，历史普通附件不自动计入。无 DDL、配置或权限码改动。详见 [结算资料需求](requirements/2026-11-25-settlement-materials.md)。
- 结算上传支持 multipart `append=true`，在项目锁内校验追加后批次的合计总量。文件数量不再限制（2026-11-25 取消原「每批最多 20 个文件」），单文件上限取 `expert.upload.doc-max-bytes`（100 MB），单批次合计上限取 `expert.upload.settlement-batch-max-bytes`（500 MB），两者经 `GET /api/meta/limits` 同步给前端做预校验；请求体上限由 `spring.servlet.multipart.max-request-size`（520 MB）与 Nginx `client_max_body_size`（520m）承接。项目成员可在适用必填齐全时调用 `POST /api/project/{id}/settlement-materials/{phaseId}/archive`，以当前各细项最新批次写入固定 ZIP，同次事务登记为项目文件并替换旧包。ZIP 流式写盘，服务端复核必填与源文件，失败时保留上次归档。项目文件栏为所有资料计算带项目名前缀的显示和下载名。
- `DELETE /api/project/{id}/settlement-materials/{phaseId}/{slot}/{docId}` 按项目成员、阶段和适用上传口校验，仅软删当前批次指定文件并留痕。最后一个文件删除后，空批次仍是当前，历史不会参与推进或打包。新 ZIP 按「业务小节/资料细项/文件」组织，已归档 ZIP 保持原内容。无数据迁移。
- 成本单制作（U08，2026-11-25）改为填表生成：`GET /api/project/{id}/cost-sheet/{phaseId}` 返回费用项目录、表头默认值与上次保存值；`POST` 同路径保存表单并生成 xlsx。目录与校验的唯一实现在 `ProjectCostSheetWorkbook`（8 个费用分节、11 列），金额口径为 应付金额=单价×数量、退款金额=结算金额−应付金额，空表拒绝。生成的文件经 `saveSettlement` 落项目资料库并以「会议成本单.xlsx」登记为 U08 当前版本，历史批次保留；表单 JSON 存 `project_stage_form` 的 `costSheet` 键，仅覆盖该键以保留同阶段其它表单。无 DDL 与权限码变更。
- 成本单 Excel 版式（2026-11-25 负责人要求）：11 列按中文宽度分配，表头信息区改为「标签格 + 合并值区」三行，分节、小计与合计分层配色，金额右对齐、明细零值显示「-」，表尾说明独占整行；A4 横向、页宽适应一页、表头行重复、冻结前 5 行。数据值与公式口径不变：明细 `F=C×E`、`H=G−F`，分节 `SUM`，总计为各分节小计单元格的 `SUM`（不再写服务端静态值），并置 `fullCalcOnLoad` 保证打开即重算。
- 成本单表单在前端按细项状态收起：`ProjectSettlementMaterials` 把 `item.complete` 传给 `ProjectCostSheetForm`，已提交时只显示「已提交 + 合计摘要 + 展开修改」，重新生成成功后自动收起；细项回到未提交（如删除当前批次）时自动展开。收起只影响展示，不改变已生成文件与表单数据。
- 项目详情右侧「当前待办」的结算资料细项入口（2026-11-25）不再统一滚到阶段资料顶部：按细项名称或编码定位到 `settlement-slot-<code>` 卡片，`scrollIntoView` 居中并高亮 3 秒，可写上传类细项同时展开文件选择区（成本单定位到在线填表区）；组件未挂载或名称未命中时回退原有「阶段资料」定位。组件 `load()` 复用进行中的请求，避免跳转与首屏加载并发时把刚打开的编辑器冲掉。
- 劳务费用资料包（2026-11-25）拆为「劳务协议」（U07，必填，沿用原编码与既有文件）与「会议资料」（U07B，选填）两个细项，同组归档路径随之分层；推进必填校验只认劳务协议。
- 编辑器路由为 /project/:id/show。主 K 和配图支持 PNG、JPEG、WebP，离线 HTML 内嵌图片并强制附件下载。财务、串场制品按项目阅读权限访问，沿用原数据库和文件目录。项目详情「PPT 制作」在当前项目中打开四步向导（模板与封面 → 专家信息 → 日程与角色 → 预览演示），也可访问独立向导路由 /project/:id/show-wizard。向导与编辑器共用 stage-form 草稿。
- 同一日程段及过场成员集合内，专家简介页的前后顺序同步到过场头像和文字。旧草稿缺少简介时，可从对应过场卡片手动补齐。第三步保留页面编辑，第四步为只读演示，支持左侧真实页面缩略图导航、保存草稿和归档。归档 HTML 的展示和下载文件名为「项目名-串场制作-YYYY-MM-DD.html」，日期取服务器归档日。预览和重新归档的 HTML 共用播放器，有位置调整的页面按文本框的最终排版从上到下、同一行从左到右播放已有出场动画。
- 专家头像通过本地人脸检测和肩部留量生成初始裁剪，手动可在固定尺寸的圆框内平移和缩放原图。输出沿用 400×400 圆形透明图片。
- 指针位于裁剪区时，可滚轮缩放原图，手动缩放不设业务比例上下限。保留放大、缩小按钮和方向键移动。
- 串场阶段共用一份有效成品。上传 PPT/PPTX 或归档 HTML 时，同一项目阶段内的旧串场成品在同一事务中软删除，新文件登记为有效资料。替换按项目加锁，旧文件保留用于恢复。两个入口的权限沿用项目现行规则。
- 项目详情右侧优先展示知识星球推送的「合规要求注意事项」。右侧「项目成员」展示卡已移除，项目基础资料仍展示负责人并沿用协助人配置。

#### 扩展串场主题与动效

1. 在 `backend/src/main/java/com/jshz/expert/service/ProjectShowService.java` 参照 `GOLD` 或 `TECH` 新增 `Theme(id, name, css, particles)`，并加入 `THEMES`。主题 ID 使用稳定的小写英文、数字和连字符。主题 CSS 复用 `--bg`、`--deco`、`--ink`、`--accent`、`--rule`、`--flow` 和字体变量。可展开现有主题的完整变量表作为起点。
2. 用 `body.t-主题ID` 限制主题样式的范围，给 `@keyframes` 使用主题前缀。跨页进场和元素揭示分别沿用 `.slide.active` 和 `[data-step]`，保留既有页面类、`data-step` 和 `data-edit`。要调整按键、揭示节奏或粒子脚本时，从同文件的 `deckTail` 扩展并按主题启用。图像和字体应随离线 HTML 内嵌，程序和样式不依赖网络；用 `prefers-reduced-motion` 给新增动效提供静态样式。
3. 在 `frontend/src/views/project/ShowWizard.vue` 的 `templates` 增加相同的 `id`、`name`、`desc` 和 `preview`。如果需要将该主题保存为共享封面模板，同步扩展 `backend/src/main/java/com/jshz/expert/service/ProjectShowTemplateService.java` 的主题校验列表，并检查独立编辑器的主题选项。
4. 共享封面模板目录 `<expert.project.storage-dir>/_show_templates/` 存放应用生成的 JSON 封面设计。当前渲染器从 Java 主题注册表加载程序。用新主题检查四步向导的封面、缩略图、逐页播放、预览键盘导航和断网打开归档 HTML。

例如给 `id=forum-fade` 的主题增加仅作用于页标题的动效：

```css
body.t-forum-fade #deck > .slide.active .sec-title {
  animation: forum-fade-in 500ms ease-out both;
}
@keyframes forum-fade-in {
  from { opacity: 0; translate: 0 12px; }
  to { opacity: 1; translate: 0 0; }
}
@media (prefers-reduced-motion: reduce) {
  body.t-forum-fade #deck > .slide.active .sec-title { animation: none; }
}
```
- 离线串场 HTML 自 2026-11-21 起内置四套模板（Deck.template：gold 红金盛典 / tech 深蓝科技 / paper 商务简白 / bamboo 青竹学术，缺失或未知回退 gold）与 Deck.fontScale 字号（0.8~1.4，默认 1.0），含流光标题、对话逐条步进（data-step）、16:9 自适应缩放与键盘翻页；主题仅差异在 CSS 变量层，结构与动画引擎为公共层（ProjectShowService.BASE_CSS）。见 [串场模板交接](handover/tasks/20261121_show_deck_themes.md)。


串场向导按日程议题及时间段分组，第三、四步共用页面清单。过场页的专家排列保存在既有草稿 persons 数组中，预览及离线归档共用服务端 HTML 渲染。见 [串场向导交接](handover/tasks/20261121_show_wizard_refinement.md)。

串场单页预览使用既有 HTML 预览接口与独立沙箱。向导将日程时间用于分组和排序，生成稿件省略会议时间。见 [串场交接](handover/tasks/20261121_show_wizard_page_preview.md)。

串场公司共享模板使用项目可读/可操作校验和项目资料库下的独立目录。封面和头像框扩展既有串场 deck，预览与归档共用渲染。见 [接口与交接](handover/tasks/20261121_show_cover_design.md)。

串场封面布局记录字段尺寸、字号与字体；离线归档按字符范围内嵌本地授权字体。装饰头像框与头像独立定位，项目草稿记录对齐参数。共享模板入口暂隐藏。见 [串场封面交接](handover/tasks/20261121_show_cover_controls.md)。

串场第三步单页编辑复用现有预览、草稿和项目权限。页面布局扩展在 deck.slides[].layout 中，按元素编号保存字号/比例、字体和位移。见 [单页编辑交接](handover/tasks/20261123_show_inline_editor.md)。

项目详情原位打开串场向导。专家简介段落批量样式、拖动坐标和删除状态随现有项目草稿及离线 HTML 保存。见 [交接记录](handover/tasks/20261123_show_resume_editing.md)。
