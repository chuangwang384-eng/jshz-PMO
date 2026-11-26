# Agent 任务交接记录 — 项目日历

## 1. 基本信息
- 任务编号：20261126-project-calendar
- 任务名称：项目日历（个人月历/部门排期/工作台卡片/会议日期必填/team 自动同步）
- 主责 Agent：ZCode（GLM）
- 交接时间：2026-11-26
- 当前状态：已完成待验收（1.0.154 已上线）
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：按日期看清自己与部门成员的会议分布，不扩大项目详情查看范围（C6）。
- 本次范围：FR-01~FR-06 全部（三期并入本次：存量缺失仅 1 条，上线前随迁移补齐，必填校验直接启用）。
- 负责人已拍板（2026-11-26）：8.1 本部门+全部下级、总经理全员；8.3 team 映射不到留空；8.5 协助人一并纳入列表行级过滤；8.6 停用员工显示并标注；8.4 按当前部门不追溯。
- 验收条件：见需求文档第 7 节；本记录第 6 节逐项标注验证状态。

## 3. 项目基线
- 分支：main；基线提交：4ea7841（1.0.153 发布记录）
- 工作区状态：本次改动未提交（发布前按流程提交）
- 基线版本：1.0.153；本次目标版本：1.0.154
- 生产库实测（2026-11-26）：ACTIVE 项目 2（DEPT3×1、DEPT1×1），缺会议日期 1 条（id=63「发布冒烟-待删」）；组织树 20 节点，PROJECT_1/2/3 seed 齐备。
- 核实时间：2026-11-26

## 4. 已完成工作 / 5. 实际变更
| 内容 | 文件 | 说明 |
| --- | --- | --- |
| 新增 日历服务 | `backend/.../service/ProjectCalendarService.java` | scope=me/dept 取数、部门范围（负责人=本部门+下级、总经理全员）、字段白名单、62 天上限、卡片 section |
| 新增 接口 | `backend/.../controller/ProjectController.java` | `GET /api/project/calendar`（白名单序列化；权限=登录即可，与 GET /api/project 列表同口径；部门 scope 服务端 403） |
| 新增 team 同步 | `backend/.../service/ProjectTeamSyncService.java`、`task/ProjectTeamSyncTask.java` | 派生规则 seed PROJECT_1/2/3→DEPT1/2/3 沿 parent_id 向上；调岗重算 ACTIVE；每日兜底 cron `expert.project.team-sync-cron`（默认 0 30 4 * * ?） |
| ProjectService | create/update 校验、appendRowLevelCondition | 会议日期必填+清空拒绝；team 改派生（body.team 忽略，validateTeam 删除）；**行级过滤加入协助人（8.5 决策）** |
| ProfileOverviewService | saveMembership 调岗钩子 | afterCommit 调 teamSync.resyncForUser |
| Dashboard | `DashboardController/ProjectCalendarService` | SECTIONS+myMeetings/deptMeetings；非负责人 deptMeetings 省略 |
| DashboardLayoutService | 白名单 | +panel.myMeetings（公共）、panel.deptMeetings（负责人限定） |
| 迁移 | `database/migration/20261126_project_calendar_team_sync.sql` | team 回填（ACTIVE 派生、映射不到置 NULL）+存量会议日期补齐+索引 idx_project_meeting；幂等；init.sql 已同步（parity 通过） |
| 前端 工具 | `frontend/src/utils/projectCalendar.js` | 角色/日期展开/月历网格/撞期展示层单一来源 |
| 前端 组件 | `views/project/components/ProjectCalendar.vue`、`ProjectDeptSchedule.vue` | 个人月历、部门排期（员工×日期矩阵、! 撞期、停用标注、当天会议弹窗） |
| 前端 页面 | `ProjectList.vue` | 列表/日历切换 + month/scope URL 回存；本部门入口按 getOrganization 判定显隐 |
| 前端 表单 | `ProjectEdit.vue` | 会议日期必填；团队只读展示（"由负责人所在部门自动确定"） |
| 工作台 | `DashboardView.vue` | panel.myMeetings / panel.deptMeetings 两卡片（默认布局加 myMeetings） |
| 样式令牌 | `frontend/src/styles/ui-tokens.css` | +`--ui-role-owner/assist/exec` |
| API | `frontend/src/api/project.js` | getProjectCalendar |
| 测试 | `ProjectCalendarServiceTest`(9)、`ProjectMeetingDateTeamTest`(6) | 范围/角色/白名单/必填/派生；修复受构造变更影响的 `ProfileOrganizationRulesTest`、`ProjectResumeAvatarTest` |

- 数据库：无新表；team/会议日期数据回填（2 个 ACTIVE 项目）+1 索引。
- API 契约：新增 GET /api/project/calendar；GET /api/project 列表行级过滤口径变化（协助人可见自己的项目——行为变更，8.5 已获批准）。
- 权限：无新权限点（登录即可 + 服务端部门负责人判定）。
- 未实现（设计文档 §9 提示项）："有 N 个项目未设置会议日期"提示——必填上线后新数据不会缺失，存量随迁移补齐，故省略。

## 6. 测试与验证
| 验证项 | 命令/步骤 | 结果 |
| --- | --- | --- |
| 后端编译 | mvn compile（带 -DgitShort/-Drevision） | 通过 |
| 新增单测 | mvn test -Dtest=ProjectCalendarServiceTest,ProjectMeetingDateTeamTest | 15/15 通过 |
| 全量回归 | mvn test（LOG_DIR=temp 隔离） | 见发布前补记 |
| parity | check-schema-parity.ps1 | 通过（92 业务表） |
| 前端构建 | npm run build | 通过，is-not-exported 警告 0 |
| 接口冒烟（线上） | scope=me 200 空列表（测试账号无近期会议）/ dept 403 / 超62天 400 / dashboard overview 修复后 200 | 通过 |
| 浏览器人工回归 | 未执行（个人月历撞期渲染、部门排期弹窗、URL 还原、负责人账号 dept 视图） | 未执行 |

## 7. 风险与回滚
- 已知风险：日历可见范围大于详情（C6 明确接受）；列表口径变化（8.5 已批准，需回归协助人视角）。
- 数据风险：迁移回填 team/会议日期，仅 ACTIVE 2 条，幂等可重跑；回滚=手工恢复原值（交接记录留存原值 DEPT3/DEPT1、id63 日期回填为其 start/end）。
- 回滚步骤：release-full Rollback（前端 root 回指快照、后端换回旧 jar）；旧代码不感知新列/索引，可独立回滚。

## 8. 文档同步
- [x] 需求/设计文档落库 docs/requirements、docs/design
- [x] init.sql + migration 同步（parity 通过）
- [x] 交接记录 + 摘要（本文件与 README.md）
- [ ] docs/architecture.md 接口与 team 同步说明（随发布提交补）
- [ ] docs/repo-map.md / README.md 数量基线（随发布提交补）
- [ ] AI 助手知识条目「如何查看项目日历」（随发布 SQL 补）

## 9. 下一位 Agent 的接手指令
1. 检查：git 状态、`/health` 版本、迁移台账是否已登记 20261126。
2. 执行：db-migrate 执行迁移 → release-full 发布 1.0.154 → 健康检查 + 浏览器回归（需求第 7 节逐项）。
3. 验收标准：需求文档第 7 节全绿。
4. 未经确认禁止：改 team 派生规则/可见范围口径。
5. 需要负责人决策：无（8.1/8.3/8.5/8.6 已拍板）。
