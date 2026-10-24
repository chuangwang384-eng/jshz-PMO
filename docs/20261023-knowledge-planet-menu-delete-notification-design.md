---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_5696def0cf4411f1814c525400cd780f
    ReservedCode1: m1kTmmaNcUiXwJ1ybkaLqpgioP7BXJ9179U4uwv8sKkdCBYs0Tl1c1N3YcPtyrFz15ylCBk1j+Hj9xz9VOnWn028JcLb6zvXp2k4M6N8RqcD0XdQ2n3PcJrimfBwzJfIAoycCTbcgXHeghnbbYOihX9U3Kde+B+ENrW3QChXOXBXujtB3l+UWJE3jQo=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_5696def0cf4411f1814c525400cd780f
    ReservedCode2: m1kTmmaNcUiXwJ1ybkaLqpgioP7BXJ9179U4uwv8sKkdCBYs0Tl1c1N3YcPtyrFz15ylCBk1j+Hj9xz9VOnWn028JcLb6zvXp2k4M6N8RqcD0XdQ2n3PcJrimfBwzJfIAoycCTbcgXHeghnbbYOihX9U3Kde+B+ENrW3QChXOXBXujtB3l+UWJE3jQo=
---

# 知识星球菜单交互 + 永久删除 + 消息通知中心 设计文档

- 日期：2026-10-23
- 状态：已确认，待实施
- 技术栈：Java17 + Spring Boot3 + Vue3 + Element Plus + MySQL8 + MyBatis Plus
- 涉及服务：ExpertSystemBackend（8080）、ExpertSystemNginx（80）、MySQL26

## 一、背景与目标

内网专家库系统（jshz-PMO）二期已交付。本次新增三项需求：

1. **菜单交互优化**：点击侧边栏"知识星球"标题直接进入上传中心（父页 = 上传中心 + 全部分类文档总览）；点击右侧下拉箭头才展开二级菜单，每个二级菜单只展示本分类文件。
2. **永久删除**：管理员界面新增"删除"按钮，永久删除文档（主表记录 + 全部版本历史 + 磁盘物理文件），不可恢复；现有"下架"（软删除）保留。
3. **消息通知中心**：通用可扩展的通知模块，覆盖审批结果 / 新申请待审 / 角色调整 / 密码重置 / 文档下架与永久删除五类事件；顶栏铃铛 + 未读红点 + 消息列表 + 30 秒轮询 + 新消息轻提示；点击通知跳转到对应内容页。

## 二、现状核查结论

| 项 | 现状 |
|---|---|
| 菜单 | `frontend/src/components/SideMenuItem.vue` 将带 children 的菜单渲染为 el-sub-menu，点击标题只展开子菜单、不跳转父路由；父路由 `/knowledge-planet` 已挂 `KnowledgePlanetHome.vue` 且子路由正常，父页内容（上传中心+总览）已存在 |
| 下架 | `DELETE /api/knowledge/docs/{id}` = 下架（status=0），权限 `knowledge:manage`；`KnowledgeDocumentService.offShelf()` 只改状态，不删文件 |
| 存储 | `KnowledgeDocStorageService` 统一管理上传目录文件（storedName）；版本文件也在同一目录，storedName 全局唯一 |
| 通知 | 无任何通知表/接口/组件 |
| 埋点 | `ApprovalService`（提交/通过/驳回）、`UserService.assignRoles`、`UserService.resetPassword`、`KnowledgeDocumentService.offShelf` 为天然触发点 |
| 跳转页 | 我的审批 `/profile/my-approvals`、审批管理 `/approval/manage`、修改密码 `/profile/change-password`、个人中心 `/profile`、知识星球分类子页 `/knowledge-planet/{category}` |

## 三、设计

### 3.1 菜单交互（纯前端）

- 修改 `frontend/src/components/SideMenuItem.vue`（通用组件，所有带子菜单的项统一生效）：
  - 保留 el-sub-menu 结构用于展开/收起二级菜单；
  - 标题区改为自定义 template：内层 span 包裹标题文本，`@click.stop.prevent` 后 `router.push(item.path)` 跳父路由，阻止冒泡避免触发子菜单默认展开；
  - 右侧箭头保留 el-sub-menu 默认 toggle 行为（点击箭头展开/收起二级菜单）。
- 父页 `KnowledgePlanetHome.vue` 保持"上传中心 + 全部文档总览"不变。
- 高亮行为：进入父路由时父菜单项呈选中态且子菜单不自动展开；进入子路由时子菜单自动展开高亮（el-menu 默认行为）。

### 3.2 永久删除（前后端）

**后端**

- 新增 `DELETE /api/knowledge/docs/{id}/permanent`，权限 `knowledge:manage`（DbPermissionFilter + 业务层双重校验，参照 offShelf）。
- `KnowledgeDocumentService.permanentDelete(Long id, AuthUser user)`：
  1. 校验登录、权限、文档存在；
  2. 查版本表该 docId 的全部 filePath（storedName 列表），并取主表 filePath；
  3. 事务内删除版本表记录 + 主表记录；
  4. 事务提交后 best-effort 物理删除磁盘文件（含各版本 + 当前版本），失败仅告警不阻断（日志记录）；
  5. 通知上传人（doc_deleted 事件，见 3.3）。
- `KnowledgeDocStorageService` 新增物理删除方法：按 storedName 删除上传目录文件（含不存在容错）。

**前端**

- `frontend/src/api/knowledge.js` 新增 `permanentDeleteKnowledgeDoc(id)`。
- `KnowledgePlanetHome.vue` 与 `KnowledgeDocList.vue` 操作列在 `canManage` 时新增红色"删除"按钮：
  - ElMessageBox.confirm 二次确认，文案明确"永久删除不可恢复，将同时删除全部历史版本与磁盘文件"；
  - 成功后刷新当前列表。
- 原"下架"按钮保留。

### 3.3 消息通知中心

**数据库（新表 notification）**

```sql
CREATE TABLE IF NOT EXISTS notification (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id      BIGINT       NOT NULL COMMENT '接收人用户ID',
  type         VARCHAR(50)  NOT NULL COMMENT '类型：approval_result/approval_todo/role_changed/password_reset/doc_off_shelf/doc_deleted',
  title        VARCHAR(200) NOT NULL COMMENT '标题',
  content      VARCHAR(500) NOT NULL COMMENT '内容',
  jump_path    VARCHAR(200) DEFAULT NULL COMMENT '点击跳转路由',
  is_read      TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_time    DATETIME     DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_user_read_time (user_id, is_read, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知';
```

- 新增实体 `Notification`、mapper `NotificationMapper`、service `NotificationService`（含 `notify(userId, type, title, content, jumpPath)` 通用写方法，作为后续事件扩展点）。

**后端接口（均登录即可，仅查本人数据）**

| 接口 | 说明 |
|---|---|
| `GET /api/notifications?page=&pageSize=` | 分页列表（按时间倒序） |
| `GET /api/notifications/unread-count` | 未读数 |
| `POST /api/notifications/{id}/read` | 单条标记已读 |
| `POST /api/notifications/read-all` | 全部标记已读 |

**业务埋点**

| 触发点 | 事件 type | 接收人 | title/content 要点 | jump_path |
|---|---|---|---|---|
| `ApprovalService.approve/reject` | approval_result | 申请人（request.create_by 对应 userId） | 审批通过/驳回 + 审批意见 | `/profile/my-approvals?status=1` 或 `?status=2` |
| `ApprovalService.submitRequest/submitCreateRequest` | approval_todo | approval_config.approver_role 角色下全部用户 | 有新申请待审批 | `/approval/manage?status=0` |
| `UserService.assignRoles` | role_changed | 被调整用户 | 角色已更新为 xxx | `/profile` |
| `UserService.resetPassword` | password_reset | 该用户 | 密码已重置，请及时修改 | `/profile/change-password` |
| `KnowledgeDocumentService.offShelf` | doc_off_shelf | 上传人（uploadBy 反查 userId，查不到则跳过） | 文档「xxx」已下架 | `/knowledge-planet/{category}` |
| `KnowledgeDocumentService.permanentDelete` | doc_deleted | 上传人（同上容错） | 文档「xxx」已被永久删除 | `/knowledge-planet/{category}` |

- 上传人字段为 username，需按 username 反查 SysUser.id；查无此用户时静默跳过（不抛异常）。
- approval_todo 接收人集合：查询该角色下全部用户；集合为空时跳过。

**前端**

- `frontend/src/api/notification.js`：列表 / 未读数 / 单条已读 / 全部已读。
- `frontend/src/store/notification.js`：unreadCount、fetchUnread、markRead、markAllRead、30 秒轮询 timer（登录后启动、退出清理）。
- 新组件 `frontend/src/components/NotificationBell.vue`：
  - 顶栏铃铛（el-badge 显示未读数红点），点击弹出消息列表（el-popover 或 el-drawer）；
  - 列表：未读置顶加粗 + 蓝点，时间展示，分页加载（每页 20）；
  - 单条点击 → `router.push(jump_path)` 并标记已读；底部"全部已读"。
- `App.vue`：header 的 user-area 内 username 前插入 `<NotificationBell />`；onMounted 启动轮询；未读数 0 → 非 0 时 `ElNotification` 右上角轻提示"您有一条新消息"；退出登录清理 timer。
- 跳转页状态过滤：实施前核查 `MyApproval.vue` / `ApprovalManage.vue` 是否支持 `status` query；不支持则补前端 query 解析小改。

## 四、验收要点

1. 菜单：点"知识星球"进上传中心+总览；点箭头展开四分类；子页仅展示本分类。
2. 永久删除：管理员可见红色删除按钮；确认弹窗文案正确；删除后列表消失、版本历史为空、磁盘文件不存在；下架功能不受影响。
3. 通知：五类事件均产生通知；未读数正确；30 秒轮询生效；新消息轻提示；点击跳转对应页面并标记已读；全部已读生效；普通用户仅见本人通知。
4. 文档同步：architecture.md、release notes、AGENTS.md 同步；git 提交。

## 五、部署步骤

1. 前端：`npm run build` → 重启 ExpertSystemNginx（勿用 `nginx -s reload`）。
2. 后端：先停 ExpertSystemBackend（NSSM 锁 jar）→ `mvn clean package -DskipTests` → 执行建表 SQL → 启动 ExpertSystemBackend。
3. 用户双账号（超管 jshz / 员工王创）自验。

## 六、风险与注意事项

- 后端改动涉及三处业务插桩 + 新表新接口，需停服打包重启闭环（computer-agent 执行）。
- SideMenuItem 为通用组件，个人中心等其它带子菜单项行为一并改变（预期内）。
- 删除磁盘文件为 best-effort，失败不阻断事务但须告警日志。
- 通知表仅存收件人维度，不存发件人（YAGNI，后续需要再加）。
*（内容由AI生成，仅供参考）*
