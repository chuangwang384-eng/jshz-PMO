---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_06d601abd41011f19101525400de85a5
    ReservedCode1: KTSM9SZzNhxE+RS7Mt3EDF2ZQasLRIltcuZSWS2qAo6YsuUYJhPEsRCRZrZ7eWYCSVHWbG1dQAI742OcntXhofwnXvw3gzcLgR5d4ZoJPCQvRYZtvI0qf1L4J0xbafJSZSGvAqo+PBhebiKuMHeC6X+jQrhWgbiuhJE6s8Nd0s8trbnCzROP4oLNpa4=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_06d601abd41011f19101525400de85a5
    ReservedCode2: KTSM9SZzNhxE+RS7Mt3EDF2ZQasLRIltcuZSWS2qAo6YsuUYJhPEsRCRZrZ7eWYCSVHWbG1dQAI742OcntXhofwnXvw3gzcLgR5d4ZoJPCQvRYZtvI0qf1L4J0xbafJSZSGvAqo+PBhebiKuMHeC6X+jQrhWgbiuhJE6s8Nd0s8trbnCzROP4oLNpa4=
---

# jshz-PMO 消息通知与公告入口合并 —— 需求文档

- 日期：2026-10-30
- 项目：jshz-PMO 专家库系统（内网）
- 技术栈：Vue 3.4 + Element Plus 2.7.5 + Pinia + Vite（前端）；Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL（后端）
- 需求来源：用户提出（附目标效果图：图1；现状图：图2）

---

## 一、需求概述

### 1.1 背景

当前系统右上角有**两个铃铛入口**，分别承载「公告」与「系统消息」，均为 el-popover 弹出面板（非抽屉）：

| 入口 | 组件 | 实现方式 | 数据 |
|---|---|---|---|
| 公告铃铛 | `AnnouncementBell.vue` | el-popover 弹出面板，380px，点击触发 | 打开时调 `getEffectiveAnnouncements(10)`（后端 effective 接口最多返回 5 条生效公告，content 置空）；未读数 = 前端 computed 对列表里 `read===false` 计数 |
| 系统消息铃铛 | `NotificationBell.vue` | el-popover 弹出面板，380px，点击触发 | store 30 秒轮询未读数与列表，打开面板 `refresh()` 展示最近 20 条；新消息弹 ElNotification 轻提示 |

两个入口数据体系完全独立：公告（announcement + announcement_read 表）与系统消息（notification 表）的接口、已读机制、未读计数方式均不同。

### 1.2 目标

- 将右上角两个铃铛**合并为一个入口**（单一铃铛图标 + 总未读红点角标）；
- 点击入口打开**右侧消息抽屉**（el-drawer）；
- 抽屉内提供三个 Tab：**全部 / 系统消息 / 公告**（Tab 顺序与图 1 一致）；
- 抽屉承载「轻量查看 + 已读管理」，完整管理能力仍由独立页面承接。

### 1.3 非目标（明确不做）

- 不重构公告/系统消息两套数据体系的后端存储；
- 不做抽屉内详情承载（详情仍跳独立页面）；
- 不新增后端统一聚合接口；
- 不新增"新公告轻提示"。

---

## 二、现状清单（只读核查结论，2026-10-30）

### 2.1 前端现状

| 项目 | 现状 | 位置 |
|---|---|---|
| 铃铛入口 | 两个铃铛并列在顶栏 `user-area` | `frontend/src/App.vue` 第 30-31 行：`<NotificationBell /> <AnnouncementBell />` |
| 公告铃铛 | el-popover 弹出面板，206 行 | `frontend/src/components/AnnouncementBell.vue` |
| 系统消息铃铛 | el-popover 弹出面板，193 行 | `frontend/src/components/NotificationBell.vue` |
| 消息中心页面 | el-tabs 全部/未读/已读 + 类型筛选 + el-table + 批量已读/删除 | `frontend/src/views/MessageCenter.vue`（路由 `/profile/notifications`） |
| 公告列表/详情/管理 | 独立页面 | `views/announcement/AnnouncementList.vue`、`AnnouncementDetail.vue`、`AnnouncementManage.vue` |
| 抽屉组件 | 全项目 el-drawer 仅 1 处（文档预览） | `frontend/src/components/KnowledgeDocList.vue`（size=72%），**无消息抽屉现成组件** |
| 全局未读公告弹窗 | `checkUnreadAnnouncements()` 用 ElMessageBox 弹未读公告列表 | `App.vue` |

### 2.2 后端现状

| 链路 | 表 | 已有接口 | 缺口 |
|---|---|---|---|
| 公告 | `announcement`、`announcement_read`（联合键 announcement_id+user_id 幂等） | `GET /api/announcement/effective`（最多5条）、`GET /api/announcement`（分页）、`GET /{id}`、`POST /{id}/read`、管理侧 CRUD/status/top/read-stats | **无未读计数接口**；**无批量已读接口** |
| 系统消息 | `notification`（userId/type/title/content/jumpPath/isRead/createTime） | `GET /api/notifications`（isRead/type 筛选）、`GET /unread-count`、`POST /{id}/read`、`/read-batch`、`/read-all`、`DELETE /{id}`、`/batch` | 无（已完备） |

### 2.3 关键事实提示

1. 公告未读数现状不准确（仅统计生效列表前 5 条中的未读），需新增真实计数接口；
2. 系统消息 11 个类型常量：approval_result / approval_todo / approval_withdrawn / approval_transferred / approval_assigned / role_changed / password_reset / doc_off_shelf / doc_deleted / doc_updated / doc_relisted；
3. 系统消息所有操作仅限本人消息；公告已读按 announcement_read 表记录；
4. 路由均在 `/profile` 子路由下，权限：notifications / announcements 登录即可；announcements-manage 需 `announcement:manage` 权限；
5. 菜单由 `src/utils/menu.js` 从路由表生成（`meta.menu===false` 隐藏、`meta.permissions` 过滤）。

---

## 三、需求规格（9 项确认结论）

### 3.1 入口合并（S1）

- 右上角**移除** `<NotificationBell />` 与 `<AnnouncementBell />` 两个独立铃铛组件，替换为**一个合并铃铛入口**（新组件，建议命名 `MessageDrawerBell.vue`）；
- 铃铛图标右上角显示**总未读数红点角标**：`总未读数 = 系统消息未读数 + 公告未读数`；
- 点击铃铛 → 打开右侧消息抽屉。

### 3.2 抽屉结构（S2）

- 使用 `el-drawer`，`direction="rtl"`（右侧滑出），宽度 **420px**，窄屏自动缩至 90%；
- 带半透明遮罩，点击遮罩关闭；
- 标题栏「消息中心」右侧显示当前总未读数文本（图1 的"5条未读"样式）；
- Tab 横向排列于标题下方，顺序：**全部 / 系统消息 / 公告**；
- 每条消息展示：标题（粗体）、来源/类型标签、时间、未读状态（未读前缀橙色圆点，已读无标记）；
- 空数据时显示"暂无消息"占位；
- 抽屉关闭不重置滚动位置、不主动清未读（已读只由单条点击/「全部已读」触发）。

### 3.3 Tab 语义与数据聚合（S3）

- **「公告」Tab**：展示当前用户可见的公告列表，排序 = 生效中优先（置顶优先）+ 发布时间倒序；改用公告**分页接口** `GET /api/announcement`（生效中优先），**不再使用** 5 条 effective 接口；
- **「系统消息」Tab**：展示本人系统消息列表，沿用现有分页接口 `GET /api/notifications`（20 条/页）；
- **「全部」Tab**：公告 + 系统消息按发布时间倒序合并成一条时间流，每条保留各自类型标签；公告"置顶"逻辑**只在公告 Tab 内生效**，「全部」时间流内不置顶、纯按时间排；未读状态各自独立标记；
- **分页加载**：每个 Tab 首屏加载最近 20 条，抽屉内滚动到底自动加载下一页（`el-infinite-scroll`）；
- **「全部」Tab 合并策略**：首屏并发拉公告 + 系统消息各 20 条，前端按时间倒序合并、去重、排序；继续下翻时两路各自翻页、前端继续合并。**不新增后端聚合接口**。

### 3.4 未读角标与计数（S4）

- 铃铛红点角标 = 系统消息未读数 + 公告未读数；
- **后端新增**公告未读计数接口：`GET /api/announcement/unread-count`，按 announcement_read 表对当前用户计数（生效中公告未读数），保证数字准确；
- 角标实时性：沿用现有 **30 秒轮询**节奏，每次轮询**同时**调公告 `unread-count` + 系统消息 `unread-count`，合成总未读数更新铃铛角标；
- 抽屉打开时强制刷新一次当前 Tab 数据；
- 系统消息"新消息弹 ElNotification 轻提示"**保留不动**；
- 公告**不新增**轻提示，只在角标体现（避免两套弹窗打架）。

### 3.5 点击条目行为（S5）

完全沿用现有跳转逻辑，**不改行为**：

- 公告条目点击 → 标记已读 → 跳公告详情页 `/profile/announcements/:id`（富文本全文）；
- 系统消息条目点击 → 标记已读 → 有 `jumpPath` 则跳转对应业务页面；无 `jumpPath` 则仅标已读、原地停留；
- 抽屉只做「列表 + 已读管理」，不做内容详情承载。

### 3.6 全部已读（S6）

- 抽屉顶部提供「全部已读」操作（图1 勾选图标样式），**只作用于当前激活 Tab**：
  - 「系统消息」Tab 下点击 → 调现有 `POST /api/notifications/read-all`；
  - 「公告」Tab 下点击 → **后端新增**公告批量已读接口 `POST /api/announcement/read-all`（按当前用户把生效公告全标已读）；
  - 「全部」Tab 下点击 → 两个接口都调。

### 3.7 全局未读公告弹窗（S7）

- **保留** `checkUnreadAnnouncements()` 弹窗，但节流为**首次登录当天弹一次**，后续靠铃铛角标 + 抽屉内公告 Tab 提醒。

### 3.8 独立页面保留与「查看全部」（S8）

- 三个独立页面**全部保留**：`/profile/notifications`（消息中心）、`/profile/announcements`（公告列表）、`/profile/announcements/:id`（公告详情）；公告管理后台 `/profile/announcements/manage` 不变；
- 抽屉每个 Tab 底部放「查看全部」：
  - 「公告」Tab → 跳 `/profile/announcements`；
  - 「系统消息」「全部」Tab → 跳 `/profile/notifications`；
- 原公告铃铛、系统消息铃铛 popover 里的「查看全部」随旧入口一并移除（旧铃铛已删除）。

### 3.9 状态与数据刷新汇总（S9）

| 数据 | 刷新机制 | 触发点 |
|---|---|---|
| 铃铛总未读角标 | 30 秒轮询（公告 unread-count + 系统消息 unread-count 合成） | 挂载即开始，与现有 notification 轮询合并节奏 |
| 抽屉各 Tab 数据 | 打开抽屉强制刷新当前 Tab；滚动加载下一页 | 打开、滚动到底 |
| 已读状态 | 单条点击标记 / 「全部已读」按钮 | 用户操作 |
| 系统消息轻提示 | 现有 ElNotification，保留 | 轮询发现新消息 |

---

## 四、改动清单

### 4.1 后端新增（2 个接口）

| 接口 | 说明 | 涉及位置（参考） |
|---|---|---|
| `GET /api/announcement/unread-count` | 当前用户生效中公告未读数（announcement_read 表计数） | `AnnouncementController` / `AnnouncementService` / `AnnouncementRead` Mapper |
| `POST /api/announcement/read-all` | 当前用户把生效公告全部标记已读（幂等，重复调用不报错） | 同上 |

### 4.2 前端改动

| 改动 | 说明 | 涉及文件（参考） |
|---|---|---|
| 新增合并铃铛组件 | 单一铃铛 + 总未读角标 + 点击打开抽屉，集成 30 秒轮询合成 | `frontend/src/components/MessageDrawerBell.vue`（新建） |
| 移除旧铃铛 | 删除两个旧组件引用；组件文件可保留或删除（建议删除） | `App.vue`（第 30-31 行）；`AnnouncementBell.vue`、`NotificationBell.vue` |
| 新增公告 API 封装 | unread-count / read-all | `frontend/src/api/announcement.js` |
| 扩展通知 store | 轮询合成总未读（或在抽屉组件内自行合成） | `frontend/src/store/notification.js` |
| 抽屉内公告列表 | 改用分页接口（生效中优先、时间倒序、20 条/页 + 滚动加载） | 抽屉组件内 |
| 「全部」Tab 合并 | 前端并发拉取 + 时间倒序合并去重排序 + 分页翻页合并 | 抽屉组件内 |
| 「全部已读」 | 按当前 Tab 调对应 read-all 接口 | 抽屉组件内 |
| 弹窗节流 | `checkUnreadAnnouncements()` 增加"首次登录当天弹一次"节流 | `App.vue` |

---

## 五、验收标准

1. 右上角仅剩**一个铃铛入口**，红点角标数字 = 系统消息未读 + 公告未读，30 秒内随轮询自动更新；
2. 点击铃铛从右侧滑出抽屉（420px，遮罩可关闭），标题栏显示总未读数文本；
3. 抽屉三个 Tab（全部 / 系统消息 / 公告）均可正常切换；
4. 「公告」Tab 按生效中优先 + 发布时间倒序展示，首屏 20 条，滚动到底自动加载下一页；不再出现"只有 5 条公告"的限制；
5. 「全部」Tab 中公告与系统消息按时间倒序混合排列，公告不置顶，未读状态各自正确；
6. 点击公告条目 → 标记已读并跳公告详情页；点击系统消息条目 → 标记已读并按 jumpPath 跳转（无 jumpPath 则原地标已读）；
7. 「全部已读」仅在当前 Tab 生效：系统消息 Tab 只清通知、公告 Tab 只清公告、全部 Tab 两个都清；公告批量已读幂等；
8. 登录后未读公告弹窗一天最多弹一次；
9. 抽屉各 Tab 底部「查看全部」分别跳转 `/profile/announcements` 与 `/profile/notifications`，独立页面功能不受影响；
10. 系统消息轻提示（ElNotification）行为与改造前一致；
11. 改造前后端接口均只读兼容旧页面：消息中心、公告列表/详情/管理页面无回归。

---

## 六、实现提示（面向 Agent）

1. **先做后端**：新增 `GET /api/announcement/unread-count` 与 `POST /api/announcement/read-all`，可参考 `notification` 的 unread-count / read-all 写法（`NotificationController` / `NotificationService`），公告已读表为 `announcement_read`（联合键 announcement_id+user_id，幂等插入）；
2. **抽屉组件**：`el-drawer` 参考 `KnowledgeDocList.vue` 用法（本项目已有先例）；Tab 用 `el-tabs`；滚动加载用 `el-infinite-scroll`；
3. **「全部」Tab 合并**：用 Map 以 id+来源类型 去重（公告 id 与通知 id 不冲突，仍需按类型区分，建议 key = `${type}-${id}`），按 `createTime`/`publishTime` 倒序排序；
4. **轮询合成**：在 `notification.js` store 现有轮询回调里同时请求公告 unread-count，或由抽屉组件统一管理两个轮询；注意只保留一套定时器，避免重复轮询；
5. **「公告」Tab 分页接口**：确认 `GET /api/announcement` 的排序参数支持"生效中优先 + 置顶 + 发布时间倒序"；若现有分页接口不满足排序，需在后端补排序参数（低风险，需在开发时核对）；
6. **弹窗节流**：用 localStorage 记录 `announcementPopupDate`（yyyy-MM-dd），当日已弹则跳过；
7. **删除旧组件**：删除 `AnnouncementBell.vue`、`NotificationBell.vue` 及 `App.vue` 引用时，注意确认无其他页面引用这两个组件；
8. **菜单/路由**：不动现有路由与菜单；新增抽屉不产生新路由；
9. **回归重点**：消息中心页（/profile/notifications）、公告列表/详情/管理页、知识库相关埋点通知（doc_updated 等）不回归。

---

## 附：图1（目标）与图2（现状）说明

- 图1：目标界面 —— 单铃铛入口、Tab（全部/系统消息/公告）、未读橙色圆点、总未读数文本（"5条未读"）、「全部已读」勾选、底部"↓"加载更多；
- 图2：现状 —— 专家库管理界面右上角两个铃铛入口。
*（内容由AI生成，仅供参考）*
