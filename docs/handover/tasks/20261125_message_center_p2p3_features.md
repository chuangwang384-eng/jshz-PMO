# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261125_message_center_p2p3_features
- 任务名称：「消息中心」P2×4 + P3×10 修复 + 通知断流调查 + 需求 A/B/D/E 实现 + 发布上线
- 主责 Agent：ZCode
- 交接时间：2026-11-25
- 当前状态：已完成待验收（发布状态见 `docs/releases/20261125-message-center-1.0.142.md`）
- 优先级：P1

## 2. 目标与验收条件
- 修复审查清单 P2（4 项）与 P3（10 项）；深入调查「最新消息停在 11-19」；实现需求 A（保留期清理）、B（体验小改）、D（按类型免打扰）、E（SSE 推送替代轮询）；发布上线并出具调查报告。
- 不在本次范围：SSE 替代公告未读链路（公告仍走 30s 轮询）、通知偏好按用户组批量配置、孤儿通知自动识别。

## 3. 项目基线
- 分支 main，起点 1f599e1（1.0.141 已发布）；工作区含他人未提交 docs 改动，未触碰。核实时间 2026-11-25 上午。

## 4. 实际变更
### 后端
- 新增：`entity/NotificationMute`、`mapper/NotificationMuteMapper`、`service/NotificationSseService`（SSE 注册表+心跳）、`task/NotificationRetentionCleanTask`（需求 A）、测试 `NotificationPreferenceTest`（7 例）。
- 修改：`NotificationService`（KNOWN_TYPES/MUTABLE_TYPES 注册表；notify 落库前免打扰过滤+成功后 SSE 推送；mutedTypes/saveMutedTypes；deleteReadBefore；markRead 改条件更新只写 is_read 列[P3-13]；pageByUser type 白名单+id 排序兜底[P3-10 部分]）、`NotificationController`（page/pageSize 钳制与 isRead 校验[P3-9/10]；SSE 端点 `/sse`；mutes GET/PUT）、`application.yml`（2 个新环境变量）。
- 数据库：新表 `notification_mute`（迁移 `database/migration/20261125_add_notification_mute.sql` + init.sql 基线同步 + 幂等脚本 `scripts/apply-migration-20261125-notification-mute.ps1`）；`notification.type` 列注释更正（P3-11）。**业务表 88→89、总表 104→105（迁移后以 information_schema 现查为准）**。

### 前端
- 新增：`utils/notificationTypes.js`（类型字典/分组/tag 颜色/相对时间，单一来源；对应后端 KNOWN_TYPES）。
- 修改：`MessageCenter.vue`（P2-3 分页越界回退、P2-4 行点击去多余刷新、P3-5 请求序号、P3-6 深链清参、分组筛选、相对时间列、免打扰偏好弹窗）、`MessageDrawerBell.vue`（P2-1 字典收口、P2-2 openDrawer 推进基线、P3-7 失败态空态区分、SSE 建连、P3-12 字面色值按 17.2 保留并旁注令牌）、`store/notification.js`（poll 防重入、多条新消息文案「等 N 条」[P3-8]、connectSSE/disconnectSSE，30s 轮询保留为兜底）、`api/notification.js`（mutes 接口）。

## 5. 关键设计决策
- **SSE 只发「refresh」信号不带业务数据**：notify 与推送不同事务，推送先于提交到达属可接受，前端收到信号自行拉取；30s 轮询保留为兜底（连接中断/丢失信号不影响正确性）。
- **免打扰在 notify 落库前过滤**（不产生未读、不推送）；password_reset 强制送达（MUTABLE_TYPES 排除）；偏好查询失败按未静音放行（宁可多送不可丢通知）。
- **保留期清理只删「已读且超期」**（默认 90 天，`NOTIFICATION_READ_RETENTION_DAYS` / `NOTIFICATION_RETENTION_CLEAN_CRON`，每日 03:40）；未读永不清理。
- P3-12/14 处理说明：字面色值按 17.2 属允许字面量（保留+旁注令牌）；消息删除无 operation_log 登记为豁免（低敏数据、本人操作）。

## 6. 测试与验证
| 验证项 | 命令/步骤 | 结果 |
| --- | --- | --- |
| 新增单测 | `NotificationPreferenceTest`（免打扰过滤/查询失败放行/保存白名单与去重/保留期钳制/注册表完整性 7 例） | 7 例全绿 |
| 后端全量单测 | 隔离目录 + LOG_DIR 隔离 | 见发布记录 |
| 前端构建 | vite build 临时目录 | 通过，导出告警 0 条 |
| 全栈联调 | 影子后端 8081 + 预览前端 5199（测试账号） | 见发布记录（SSE 建连、免打扰保存生效、相对时间、深链清参） |
| 生产冒烟 | 发布后核验 | 见发布记录 |

## 7. 调查结论（详见 `docs/reviews/20261125-notification-silence-investigation.md`）
- 通知断流非缺陷：合规在办且设到期日事项=0、11-19 后无新申请（11-21 那张修改类申请正赶上 1.0.138 前的「提交不通知」旧缺陷）、其余类型对应业务无操作。
- 连带发现：删除申请单/消息后历史通知成孤儿残留，本次保留期清理不覆盖孤儿（notification 无业务引用列），登记为已知限制。

## 8. 风险与回滚
- SSE 长连接经 Nginx：响应头 X-Accel-Buffering:no 关闭缓冲、25s 心跳低于 60s 读超时；若代理层仍断连，前端自动重连+轮询兜底，不影响功能。
- 免打扰为「不产生新通知」，不会丢既有未读；偏好查询失败按放行处理。
- 回滚：前端 Rollback；后端 previous jar；`notification_mute` 表保留无害（旧代码不感知）。迁移先于发布执行，回滚后新表空置即可。
- 限制：SSE 单机内存注册表，多实例部署需改广播（当前单机部署不适用）。

## 9. 文档同步
- [x] 架构文档（消息中心段 + 通知断流调查链接 + 表数现查）
- [x] 部署文档 + AGENTS 18.4（新增 2 个环境变量）
- [x] 数据字典（init.sql / migration）
- [x] 调查报告 `docs/reviews/20261125-notification-silence-investigation.md`
- [x] 交接摘要 / 发布记录

## 10. 下一位 Agent 的接手指令
1. `curl http://127.0.0.1:8080/health` 确认 1.0.142；查 `notification_mute` 表存在。
2. 浏览器验收：铃铛打开消息中心 → 通知偏好 → 勾选某类型保存 → 让另一账号触发该类型事件（如提交申请）→ 被静音账号铃铛不涨；取消勾选恢复。观察 Network 中 /sse 长连接与 refresh 事件。
3. 未经确认禁止：把 SSE 改为携带业务数据、放宽 password_reset 可静音、清理未读消息。
