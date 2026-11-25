# 1.0.142 发布记录：消息中心 P2/P3 修复 + 免打扰/保留期清理/SSE 推送

## 内容

- 修复审查 P2×4（类型字典收口、已读消息再弹提示、分页越界回退、行点击多余刷新）与 P3×10（请求序号、深链清参、失败空态、多条文案、后端钳制与白名单、markRead 条件更新、DDL 注释、样式登记、删除无留痕豁免登记）。
- 需求 D 按类型免打扰：新表 `notification_mute`（迁移 20261125_add_notification_mute.sql），`GET/PUT /api/notifications/mutes`，notify 落库前过滤；password_reset 强制送达。
- 需求 A 保留期清理：`NotificationRetentionCleanTask` 每日 03:40 物理删除「已读且超期」通知（默认 90 天，env 可配），未读永不清理。
- 需求 E SSE 推送：`GET /api/notifications/sse`（refresh 信号 + 25s 心跳，30s 轮询保留为兜底）；前端 EventSource 接入。
- 通知断流调查（负责人交办）：**非缺陷**，报告 `docs/reviews/20261125-notification-silence-investigation.md`。
- 详见 [任务交接](../handover/tasks/20261125_message_center_p2p3_features.md)。

## 数据库

- 新表 `notification_mute`（92 张业务表 / 108 张总表，2026-11-25 `information_schema` 现查；schema parity 三方一致通过）。
- 迁移经 `db-migrate.ps1 -Mode Apply` 执行并登记台账（10:19：执行 + 登记 OK）；预检曾以 PENDING 正确拦截过一次「直接 mysql 执行未登记」的操作，改走台账后通过。
- 无权限码变更；2 个新环境变量（`NOTIFICATION_READ_RETENTION_DAYS` / `NOTIFICATION_RETENTION_CLEAN_CRON`）已登记 deployment.md 与 AGENTS 18.4。

## 构建与测试

- 后端全量单测（隔离目录 + `LOG_DIR` 隔离）：**328 例 0 失败**（新增 `NotificationPreferenceTest` 7 例：免打扰过滤/失败放行/保存白名单/保留期钳制/类型注册表），16 例环境门控跳过。
- 前端构建通过（临时目录验证 + 发布构建），导出缺失类告警 0 条。
- 全栈联调（影子 8081 + 预览 5199，测试账号）：SSE 建连成功（`sseConnected=true`）；免打扰保存→API 回读 `["compliance_due"]`→清空恢复；分组筛选 4 组正常；深链 `?notificationId=` 弹内容→清参→跳转关联事项 ✓。
- SSE 端点 curl 实测（本地影子与线上均测）：`:connected` 立即返回、`:ping` 每 25s，经 Nginx 代理无缓冲拦截。
- 正式制品：`builds/1.0.142-build-*/expert-system.jar`（version=1.0.142，gitShort=d77cda6；commit d77cda6）。

## 发布过程

- 10:18 预检第一次 FAIL（迁移 PENDING，台账未登记）→ 改走 `db-migrate Apply` 后 10:19 预检通过。
- UAC 提权执行 `temp/publish-1.0.142.ps1`，10:19:52–10:23:5x，`logs/release-state.json` status=SUCCESS step=verified，退出码 0，0 FAIL。
- 全量备份 + 后端蓝绿（旧包归档 `backend/releases/1.0.142-20261125_102307/previous-expert-system.jar`）+ 前端 root → `1.0.142-message-center`。
- 发布日志：`logs/release-full-20261125_101952.log`。

## 技术冒烟与登录后验证

- 直连与经 Nginx `/health`：均 1.0.142 / d77cda6；8081 无残留。
- **线上浏览器实测（测试账号）**：页面 v1.0.142；「通知偏好」按钮在；表头为新版；**线上 SSE `sseConnected=true`**。
- 线上接口：`GET /mutes`=200；`type=BAD` 筛选 400「消息类型筛选值不合法」；`pageSize=500` 被钳制正常返回。

## 未实测项（如实登记）

- SSE 端到端「接收人实时收到信号」：测试账号无可自助触达的通知事件路径，`notify→pushRefresh` 由单测覆盖、SSE 通道已实测连通；建议负责人用两个账号验收（A 提交申请 → B 的铃铛在秒级变红）。
- 相对时间「<7 天」分支无消息样本（测试账号最新消息 11-11，均 ≥7 天回退绝对时间），纯计算逻辑未单测覆盖。
- 保留期清理任务要到 03:40 才首次运行（可临时把 cron 调近验证，未做以免改配置）。

## 回滚

- 前端 `release-full.ps1 -Mode Rollback -Yes`；后端 previous jar（1.0.141）。`notification_mute` 表保留无害（旧代码不感知）。

## 业务验收（待完成）

- [ ] 双账号：A 提交专家申请 → B（审批人）铃铛秒级变红（SSE 生效）；B 处理后 A 秒级收到结果。
- [ ] 「通知偏好」勾选某类型保存后，触发该类型事件不再产生新通知；取消勾选恢复。
- [ ] 消息中心列表：类型筛选带分组标题；时间列显示相对时间（7 天内的消息）。
- [ ] 明日 03:40 后日志出现 `[NotificationRetentionCleanTask]` 清理记录（有可清理数据时）。
