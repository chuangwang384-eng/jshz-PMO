---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_a4695972d1fd11f18068525400de85a5
    ReservedCode1: 0W1pLG6C6mjHy4dJY9tAx1Cm1OPkFuB958oiCAC/C8esC+FDEokWqdQAm2lYbrfD9gGsmrvG0xHbOuq9PZaUQFZhH5TqKZdekk6WDi5/qdekEU9xu+iSPQ2so3QvuBPJKtuzfqF+6bwzxcqNIoS+T9L8DWJfN5idufAfAvMYM5t7Jv6pSh2jD1D6X9Y=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_a4695972d1fd11f18068525400de85a5
    ReservedCode2: 0W1pLG6C6mjHy4dJY9tAx1Cm1OPkFuB958oiCAC/C8esC+FDEokWqdQAm2lYbrfD9gGsmrvG0xHbOuq9PZaUQFZhH5TqKZdekk6WDi5/qdekEU9xu+iSPQ2so3QvuBPJKtuzfqF+6bwzxcqNIoS+T9L8DWJfN5idufAfAvMYM5t7Jv6pSh2jD1D6X9Y=
---

# v1.0.7 发布说明（2026-10-27，企微通讯录同步与账号体系改造）

> 构建版本：**1.007（预计）**——当前仓库 `backend/pom.xml` 与 `frontend/package.json` 仍为 **1.006**，升版操作随生产发布执行（避免开发期版本号漂移）。本版基于评审通过的《2026-10-27-企微通讯录同步与账号体系改造-施工说明书》实施，配套迁移脚本 `database/migration/20261026_add_wecom_contact_sync.sql`。
>
> ⚠️ **本版本尚未发布生产**：代码、迁移脚本、文档与部署注入点已完成；生产发布需负责人批准后按 docs/deployment.md「企微通讯录同步部署准备」执行。

## 新增功能

### 企微通讯录同步
- 新增 `WeComApiClient`（纯出站 HTTP）：access_token 缓存+提前 5 分钟刷新、40014/42001 自动重取重试、`user/list_id` 分页拉成员 ID、`department/list_id`（不可用退回 `department/list`）拉部门、`user/get` 逐人节流；日志零 secret/零 access_token，手机号一律 138\*\*\*\*1234 脱敏。
- 新增 `ContactSyncService`：DryRun 试算只写变更清单不落业务数据；按 wecom_userid diff 绑定/新建/停用；企微离职（status 2/5）与全量未出现已绑定用户自动停用（不删除，保历史外键）；并发护栏 409；手动同步异步执行返回 logId。
- 新增 `WecomSyncScheduler`：`@Scheduled` 每日 02:30 定时同步（首次引入定时任务），受 `sync-cron-enabled` 开关控制（默认 false，开启且 corp-id/secret 为空 fail-fast）。
- 新增 `WecomContactController`（/api/admin/wecom，权限 wecom:sync，permission 表一码多路径 6 条绑 ADMIN）：POST /sync（dryRun 试算）、GET /sync/status、GET /sync/recent、GET /departments（部门树含人数）、GET /unbound（未绑定清单）、POST /users/{id}/bind-wecom（人工指认绑定）。

### 账号体系改造（登录标识三模式）
- `AuthService.login` 按 `AUTH_LOGIN_MODE` 分流：NAME（现状回滚档）/ COMPAT（姓名或手机号过渡档）/ MOBILE（仅手机号，关闭自动注册）；`is_admin=1` 管理员登录豁免；停用检查/BCrypt/JWT/cookie/强制改密流程全部不动。
- 新增 `GET /api/auth/wecom/config`（白名单占位接口），前端按 loginMode 自适应；本期配置 `AUTH_LOGIN_MODE=MOBILE`（管理员豁免），上线建议 COMPAT 过渡。

### 前端
- 新增 `WecomSyncManage.vue`（路由 /admin/wecom，权限 wecom:sync）：立即同步（DryRun 试算变更清单 → 确认实跑）、同步结果卡片、最近同步记录表、组织架构树 el-tree（部门节点带人数、成员卡手机号脱敏/主部门标记）、未绑定清单+人工绑定下拉。
- 新增个人中心「通讯录」页（/profile/directory，登录即可）：本人根到本部门链路 + 本部门成员（手机号脱敏），不暴露全量架构。
- `UserManage.vue` 增强：列表新增手机号（脱敏）/部门/企微绑定列与绑定操作。
- `Login.vue` 按 `/api/auth/wecom/config` 的 loginMode 自适应姓名/姓名或手机号/手机号文案；新增 `api/wecom.js` 接口封装。

## 数据库变更

- 执行 `database/migration/20261026_add_wecom_contact_sync.sql`（幂等，已在本机测试库演练 3 遍通过）：
  - 新建 `wecom_department` / `wecom_user_department` / `wecom_sync_log` 三表；
  - `sys_user` 加 `mobile`（手机号登录标识）、`position`（职务）两列；
  - 建唯一索引 `uk_wecom_userid(wecom_userid)`、`uk_mobile(mobile)`；删除 `uk_username` 前先备份为 `wecom_migration_uk_username_backup`；
  - 写入 `wecom:sync` 权限码（6 条路径，绑定 ADMIN 角色）。

## 配置/环境变量变更

- `application.yml` 新增 `expert.wecom.*`：`corp-id=${WECOM_CORP_ID:}`、`contact-secret=${WECOM_CONTACT_SECRET:}`、`sync-cron="0 30 2 * * ?"`、`sync-cron-enabled: false`、`login-mode=${AUTH_LOGIN_MODE:NAME}`。
- `start-backend.bat` 已加入三变量注入点（占位，真实值负责人部署时填写）；NSSM 服务环境需在发布时（管理员/UAC）追加 `WECOM_CORP_ID` / `WECOM_CONTACT_SECRET` / `AUTH_LOGIN_MODE`。
- 企微后台前置：开启「通讯录同步助手」只读 secret、记录 CorpID、出口 IP 加入可信名单（负责人操作）。

## 验证情况（2026-10-27）

- 后端 `mvn compile` 通过（EXIT=0）。
- 前端 `npm run build` 通过（EXIT=0）。
- 迁移脚本测试库执行 3 遍幂等通过。
- **未执行**：测试环境 §7 验收清单（DryRun 不写库、实跑、重名双手机号登录、离职停用、权限拒绝、管理员豁免、未绑定清单）、生产发布与冒烟——待负责人批准与配置凭据后执行。

## 风险与回滚

- 删除 `uk_username` 后重名数据唯一性改由应用层保证（未绑定通道内姓名唯一）；回滚重建索引前需确认无重名数据。
- 回滚（按层）：① 登录回滚 `AUTH_LOGIN_MODE=NAME` 重启；② 功能回滚 `sync-cron-enabled=false`（已同步数据无害保留）；③ 结构回滚新表可保留或 DROP，`uk_username` 仅在无重名时可重建。
- 企微接口/字段政策再变化：端点封装在 `WeComApiClient` 单点，实施以实测响应为准。
- 服务器出口 IP 被企微 IP 可信拦截：同步报错不影响登录。

## 发布记录

- **未发布**（待负责人批准，按 docs/deployment.md「企微通讯录同步部署准备」的发布步骤执行）。

*（内容由AI生成，仅供参考）*
*（内容由AI生成，仅供参考）*
