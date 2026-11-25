# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261125_message_center_audit
- 任务名称：「消息中心」板块只读代码审查（bug / 未启用 / 死代码 / 硬编码 / 边界 + 新需求建议）
- 主责 Agent：ZCode
- 交接时间：2026-11-25
- 当前状态：已完成（纯只读审查，未改任何代码/数据/配置）
- 优先级：P2

## 2. 目标与范围
- 审查范围：`views/MessageCenter.vue`、`components/MessageDrawerBell.vue`、`store/notification.js`、`api/notification.js`、后端 `NotificationController` / `NotificationService` / `Notification` 实体、`database/init.sql` notification 表、permission 表相关行、生产库 notification 表实测（487 行，2026-11-25 只读查库）。
- 不在范围：公告管理页、合规调度器、知识库文档通知的产生链路（仅在交叉处说明）。

## 3. 项目基线
- 分支 main，提交 1f599e1（1.0.141 已发布）；工作区有他人未提交 docs 改动，未触碰。核实时间 2026-11-25 09:40 前后。

## 4. 审查发现（详见对话汇报，按优先级）

### P2（4 项）
| # | 问题 | 位置 |
| --- | --- | --- |
| 1 | 类型字典两份副本已漂移：消息中心缺 `doc_relisted`（显示英文），铃铛缺 5 类 `compliance_*`；注释「共 10 类」失真（实 16 类常量）。第 3 处复制收口规则已触发 | MessageCenter.vue:101-118、MessageDrawerBell.vue:175-187 |
| 2 | 已读消息仍弹轻提示：openDrawer 不推进 store.baselineIds，抽屉里看过的新消息下轮 poll 再弹一次 | MessageDrawerBell.vue:283-288、store/notification.js:64-81 |
| 3 | 分页无越界回退：删除当前页最后一条后停留空白页（MyApproval Q7 同款问题本页未修） | MessageCenter.vue:219-257 |
| 4 | 行点击已读后多余 fetchList()：丢勾选状态 + 多一次请求（本地已改 isRead，无需刷新） | MessageCenter.vue:169-186 |

### P3（10 项）
请求竞态无序号（MessageCenter.vue:142-156）；深链 notificationId 参数残留（:264-269）；铃铛首屏失败显示「暂无消息」（MessageDrawerBell.vue:213-235）；多条新消息只弹第一条（store:68-78）；后端 page/pageSize 未显式钳制（NotificationController.java:41-50，依赖全局 maxLimit 兜底）；isRead/type 无白名单（同文件:45-46）；DDL type 列注释只列 6 类（init.sql:713-716）；msg-scroll calc 魔法值与字面色值（Bell:448-500）；markRead 全实体 updateById（Service:140-150）；消息删除无 operation_log（建议登记豁免理由）。

### 已核实无问题
- 权限：permission 表 0 行匹配 /api/notifications → 全部「登录即可」；所有读写 Service 强制 `eq(userId)` 归属，无越权路径。
- 登出后轮询停止（登录页只渲染 router-view，铃铛卸载触发 stopPolling）。
- 硬编码：无盘符/IP/密钥。
- 索引：idx_user_read 覆盖未读数查询，数据量 487 行健康。
- store 全部已读/批量已读未读数以服务端刷新，口径正确。

### 数据观察（非缺陷，待确认）
notification 表最新消息停在 2026-11-19 15:57（审查时 6 天前），期间合规提醒调度器在运行——可能是无到期事项属正常，建议负责人核实合规提醒任务是否正常产出（产生链路属合规模块，不在本次范围）。

## 5. 新需求候选
A. 消息保留期清理任务（已读超 N 天物理清理，未读保留）——表只增不减，approval_todo 占 305/487 增长最快；
B. 消息中心体验小改：相对时间、类型筛选分组、深链直达；
C. 「全部」Tab 的合并视图落地（现「查看全部」跳系统消息页，两路合并视图只在铃铛里存在，需产品确认 S8 口径）；
D. 按类型通知免打扰偏好（需表+设置入口，中等规模）；
E. SSE 推送替代 30s 轮询（低优先）。

## 6. 测试与验证
| 验证项 | 命令/步骤 | 结果 |
| --- | --- | --- |
| 调用面与死代码 | grep TYPE_DOC_RELISTED / doc_relisted / notify( 全库 | 通过（结论见第 4 节） |
| 权限行 | grep api/notifications database/init.sql | 0 行（登录即可，符合注释） |
| 生产数据 | 只读查库：行数/类型分布/索引/时间范围 | 通过（487 行，观察项见上） |
| 编译/构建 | 未执行（未改代码） | 不适用 |

## 7. 下一位 Agent 的接手指令
1. 修复排期由负责人定；P2-1/P2-2 是体验缺陷、P2-3/P2-4 是小改，均无 DB 变更。
2. 类型字典收口按 AGENTS 17.7/19：新建 utils 单一来源，同一次提交同步两处消费方。
3. 未经确认禁止：改通知产生链路（各业务 notify 调用点）、删 notification 表数据、引入 SSE。
