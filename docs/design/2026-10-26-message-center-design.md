# 消息中心 设计与实现文档

- 日期：2026-10-26
- 状态：已实现（feature/my-applications 分支，随 1.005 发布列车）；后端 API 已影子实测通过，浏览器 UI 验收随 1.005 部署后统一进行
- 关联：`docs/superpowers/specs/2026-10-26-my-applications-unified-design.md`（同一发布列车）

## 1. 目标

升级现有通知中心（铃铛弹层）为完整消息中心：全体员工在个人中心集中查看本人全部通知，支持 已读/未读/类型 组合筛选、批量已读、全部已读、单条/批量删除（物理删除）、点击已读+跳转关联单据；未读计数全程准确。

## 2. 数据模型

**零迁移**：notification 表结构不变（type/jump_path/is_read 齐全）；无新增权限码（/api/notifications 无 permission 规则=登录即可，新端点同样）。

## 3. 接口（全部仅作用于本人消息）

| 接口 | 说明 |
| --- | --- |
| GET /api/notifications?page=&pageSize=&isRead=&type= | 列表扩展：isRead(0/1) 与 type 组合筛选（旧调用不传参数行为不变，铃铛兼容） |
| POST /api/notifications/read-batch {ids} | 批量已读，返回实际置读条数（仅未读条目生效，幂等） |
| DELETE /api/notifications/{id} | 单条物理删除（非本人静默跳过） |
| DELETE /api/notifications/batch {ids} | 批量物理删除，返回实际删除条数 |

已有：列表/未读数/单条已读/全部已读。未读数实时按 DB 计数（is_read=0），删除/已读后前端统一 loadUnread 刷新，天然准确。

## 4. 前端

- 新增 `views/MessageCenter.vue`（/profile/notifications，个人中心菜单）：Tab 全部/未读（含未读徽标，与铃铛红点同源）/已读 + 类型下拉 + 工具栏（批量已读/全部已读/批量删除）+ 表格（复选框、类型 tag、标题未读红点加粗、内容、时间、行内删除）+ 分页；行点击=已读+按 jump_path 跳转（点复选框列不触发，column.type==='selection' 守卫）；
- NotificationBell 弹层底部新增「查看全部 »」（受控 visible，跳转前收起弹层）；
- api/notification.js + store/notification.js 新增 readBatch/removeOne/removeBatch（未读数一律服务端刷新为准）；
- 路由：/profile children 插入 notifications（无权限码，全员可见）。

## 5. 与设计文档的偏差（"可调整"范围内，均已向用户说明）

1. **类型下拉 10 类而非 6 类**：库里现有 10 个 type（文档 6 类 + 当日上午 B2 新增 approval_transferred/approval_assigned + 既有 approval_withdrawn/doc_updated），少列会导致新类型消息筛不出；
2. **approval_todo 跳转保持 `/profile/approval/manage`**（审批管理页）：待办通知接收人是审批人，跳"我的申请"语义错误；文档默认值 /profile/my-approvals?status=0 未采纳；
3. **MyApproval.vue 的 status query 解析无需新增**：「我的申请」改造中已实现（读取 + watch），文档 §3.5 的补充项已满足。

## 6. 影子实测记录（8081，2026-10-26 晚）

| # | 验证项 | 结果 |
| --- | --- | --- |
| 1 | 通知产生：王创提交 84/85 号单并审批 → 未读 5→7（精确 +2 条 approval_result） | ✅ |
| 2 | 组合筛选 isRead=0+type=approval_result（total=4 含 2 条新）、isRead=1+type（total=5） | ✅ |
| 3 | 批量已读 [71,72] → 返回 2、未读 7→5；重复调用返回 0（幂等） | ✅ |
| 4 | 越权：张三批量已读王创消息 → 0 条生效；张三删王创消息（id70）→ HTTP 200 但不删除（total 核查 70 存活） | ✅ |
| 5 | 单删 71（200，未读不变）、批删 72（返回 1）；终态 approval_result total 9→7、未读回基线 5 | ✅ |
| 6 | 铃铛兼容：无参列表调用正常（total=22） | ✅ |
| 7 | 空数组容错：{ids:[]} 各操作返回 0 | ✅ |

测试数据说明：84/85 号申请为测试单（已通过，留审计轨迹）；71/72 通知为本次测试产生后删除；王创真实历史通知未动，未读数与基线一致。
