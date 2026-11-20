# 1.0.84 发布记录：日程候选移除修复 + 手动解析按钮

- 日期：2026-11-20
- 提交：`1606575`（main）
- 制品：`builds/1.0.84-build-20261120_092429/`
- 类型：常规发布（后端蓝绿 + 前端 graceful，零数据库迁移）

## 1. 变更内容

| 内容 | 文件 |
| --- | --- |
| 日程解析候选逐条「移除」同步丢弃后端记录（原仅删本地草稿，待办永不消失） | `ScheduleParseService.java` confirm()、`ProjectDetail.vue` removeCandidateRow() |
| `PUT /project/{id}/speakers` 新增语义：candidateId + 空 speakers = 仅丢弃该候选 | `ScheduleParseService.java` |
| 参会专家标题旁新增「手动解析」按钮（复用 doParse） | `ProjectDetail.vue` |

详见 [任务交接](../handover/tasks/20261120_schedule_candidate_remove_fix.md)。

## 2. 发布与验证证据（实测）

| 步骤 | 结果 |
| --- | --- |
| `build-all.ps1` 全量构建 | 通过；build-info 校验 version=1.0.84 gitShort=1606575；前端 is-not-exported 警告 0 |
| `release-backend.ps1`（提权 temp 包装，日志 `logs/deploy-backend.1.0.84.agent.log`） | EXITCODE=0；蓝绿切换、Nginx reload、日志轮转阶段全 [OK] |
| 后端 `/health` | `UP, version=1.0.84, buildTime=2026-11-20 09:25:23, gitShort=1606575` |
| `deploy-frontend-graceful.ps1 -Apply`（ReleaseId `1.0.84-sched-candidate-fix`） | 切换成功；Nginx root → `frontend/releases/1.0.84-sched-candidate-fix`（conf 第 26 行实测）；graceful reload master PID 未变 |
| 线上主 bundle `index-D1YK-THx.js` | 含 `v1.0.84` 标记 |
| 连续性探针 `GET /` ×3 | 200/200/200；`GET /api/health` 经 Nginx 返回 1.0.84 UP |

## 3. 待验收（业务侧，未执行）

- [ ] 误解析候选点「移除」→ 该行消失、右侧「待保存专家信息」条数减少、清零后待办消失、徽标变「已完成」
- [ ] 参会专家标题旁「手动解析」可触发重新解析并刷新候选
- [ ] 移除某行后其余行已编辑内容保留
- 建议用测试账号（测试/111111）在含日程附件的项目验证。

## 4. 回滚

- 前端：`frontend/release-metadata/1.0.84-sched-candidate-fix.nginx-before.txt` 存有旧 root（`1.0.81-budget-gate-20261119`），改回 conf 后 `nginx -s reload` 即回滚。
- 后端：蓝绿归档保留旧版实例，按 deploy-backend 既有回滚流程。
- 数据库：无结构变更，无需数据回滚；已丢弃的候选可用「手动解析」重新生成。
