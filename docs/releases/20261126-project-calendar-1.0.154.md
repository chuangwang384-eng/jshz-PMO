# 发布记录 — 项目日历 1.0.154

- 日期：2026-11-26 ｜ 任务：[交接记录](../handover/tasks/20261126_project_calendar.md) ｜ 状态：**已上线，待业务验收**

## 变更内容
项目日历：项目页列表/日历切换（个人月历 + 部门负责人员工排期，URL 可还原）、工作台「我的近期会议 / 本部门会议概览」卡片；立项会议日期必填、team 由负责人组织架构部门自动派生（调岗钩子 + 每日兜底 cron `expert.project.team-sync-cron`）；协助人纳入项目列表行级过滤（负责人已批 8.5）。新接口 `GET /api/project/calendar`（白名单字段）。迁移 `20261126_project_calendar_team_sync.sql`（team 回填 + 存量会议日期补齐 + idx_project_meeting 索引，幂等，db-migrate 台账已登记）。

## 发布过程（实录）
| 时间 | 动作 | 结果 |
| --- | --- | --- |
| 10:48 | db-migrate -Mode Apply | OK，1 个脚本执行并登记；回填验证：id60 team 按规则置空（负责人总经办无 DEPT 映射，8.3 决策）、id63 已 DELETED 不动、索引 3 列就位 |
| 10:49–10:56 | release-full Publish 1.0.154-project-calendar（预检/备份/构建/蓝绿/前端 graceful/发布后验证 5/5） | SUCCESS：/health 1.0.154、root=1.0.154-project-calendar、首页 hash×3 一致、bundle v1.0.154 |
| 10:58 | 线上冒烟：scope=me 200、scope=dept 403（非负责人）、超 62 天 400 | 符合设计；**发现 dashboard overview 500**：DashboardTasks.collect 对 deptMeetings 省略型 null 结果 NPE |
| 11:05 | 修复 DashboardTasks（null 结果跳过判错），build-all -BackendOnly 隔离构建 | 构建校验全过 |
| 11:07–11:11 | deploy-backend 蓝绿重发两次被预检拦下（影子进程需显式注入 MYSQL_PASSWORD/JWT_SECRET；未触碰生产），v3 注入后成功 | 生产 /health 1.0.154 gitShort=d80b151；8081 无遗留影子 |
| 11:38 | 复测 dashboard overview | myMeetings=empty 正常返回、deptMeetings 非负责人正确省略；layout 正常 |
| 11:40 | AI 助手知识条目 project_calendar 写入（cmd 字节流管道，HEX 校验 UTF-8 正确） | OK |

## 回滚
- 后端：`backend/releases/` previous jar 蓝绿换回（新列/索引加法迁移，旧包兼容）。
- 前端：`frontend/release-metadata/1.0.154-project-calendar.rollback.conf.txt` root 回指 + graceful reload。
- 数据：回填仅影响 2 个 ACTIVE 项目（id60 team DEPT3→NULL 按 8.3 决策；id63 已 DELETED 未动）；索引回滚 `ALTER TABLE project DROP KEY idx_project_meeting`。

## 验证与遗留
- 后端全量 mvn test 370 例 0 失败（16 跳过为既有环境门控；contextLoads 首跑失败系测试 JWT_SECRET 过短，换 ≥32 字节后通过，非代码问题）。
- 前端构建通过、is-not-exported 警告 0。
- **未执行**：浏览器人工回归（个人月历撞期渲染、部门排期弹窗、URL 还原、暗色/窄屏、负责人账号 dept 视图）——待业务/管理员账号验收；接口层已覆盖 200/403/400 与白名单。
