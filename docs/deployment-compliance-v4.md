# 企业合规 v4：隔离验收、灰度切换与回滚

本文件记录 2026-11-03 开发分支的候选实现；**尚未生产发布**。生产环境当前进程、实际表结构及前任 R-1 发布文字未经本轮核实，不能沿用历史 PID、端口或表数作为现状。

## 1. 变更与迁移顺序

1. 在目标库读取 `SELECT DATABASE(), VERSION()` 及八张相关表元数据，核对备份和审批。不要根据隔离库 65 张表推断生产表数（隔离结构含历史备份表）。
2. 获批后先执行 `database/migration/20261103_add_compliance_v4_tables.sql`（七表及 sent_time 补列），再执行 `database/migration/20261103_compliance_v4_user_mode.sql`（用户开关和只读历史 JSON 快照）。两脚本均由调用方显式选择数据库，不再内置 `USE expert_system`。不能只创建实体或只执行其中一个脚本。
3. 这两份脚本为 MySQL 8.x 及以上的增量迁移；可重复执行。已有同名表时仍须对照字段/索引核实，`IF NOT EXISTS` 不会修复任意旧结构。本轮实测 MySQL 26.7.0。
4. 没有批量搬移、删除或重写 v3 业务数据。新表与旧代码可并存；旧版切换由员工逐人明确确认，事务内捕获其既有个人进度及责任事项的只读快照，不把收藏自动转换成执行。
5. 为候选后端配置下述开关，使用现有严格蓝绿流程发布候选实例、做健康/登录/资源授权验证，再切前端；生产步骤必须另获审批。本轮未运行发布脚本。

## 2. 配置及互斥

| 配置 | 默认值 | 行为 |
| --- | --- | --- |
| `COMPLIANCE_V4_ENABLED` / `expert.compliance.v4-enabled` | false | 是否允许启用/写入新版；需迁移表已存在，新进程才能识别 |
| `COMPLIANCE_V4_REMINDER_ENABLED` / `expert.compliance.v4-reminder-enabled` | false | 是否运行新版每分钟扫描；只处理已切换用户的 ACTIVE 清单 |
| `expert.compliance.due-enabled` | 原配置 true | 旧到期提醒仅处理未切换用户；切换用户即使新版开关关闭也不恢复旧提醒 |
| `expert.compliance.v4-reminder-cron` | `0 * * * * ?` | 新版每分钟扫描到期 PENDING 节点，每批最多 200 |

缺用户开关行为 V3。切换事务锁定 `sys_user` 行；旧写入、旧到期发送、新任务写入、新提醒发送按同一用户锁串行，避免切换和发送并发。切换后旧个人写接口返回业务 409；旧进度只读快照不受后续旧公共事项编辑影响。关闭全局新版开关仅暂停新写入/新发送，不反向切换个人模式，也不清空待发送节点。恢复发送后沿既有节点幂等处理。

`hasModeTable` 在进程内缓存元数据检测结果；必须先迁移，再启动候选实例。不能在已缓存“无表”的进程中执行迁移后直接认为功能已开放。

## 3. 权限与接口契约

| 资源/操作 | 服务端约束 |
| --- | --- |
| 创建/编辑模板草稿 | 企业合规文档的上传者或 `knowledge:maintain`；草稿本人或维护人；使用当前账号状态和资源权限 |
| 发布模板 | `knowledge:maintain`，来源须上架；发布版本不可原地修改 |
| 新建/复用执行 | 本人已确认启用 V4，来源上架，关联项目可读且未删除；不接受外部 owner/snapshot |
| 执行、任务、私人事件 | 本人且来源仍存在于合规分类；关联项目每次复核；维护人权限不豁免个人所有权 |
| 普通下架 | 不允许新使用；已有 v4 执行保留且提示停用；旧历史沿原规则隐藏来源内容 |
| 永久删除/移出合规分类/项目撤权 | 不返回任务快照或私人事件；清单列表仅受限占位；发送前再次拒绝 |
| 账号停用、项目取消/删除 | 账号停用拒绝资源读取/写入；项目取消停止该项目执行提醒，删除/撤权限制读取 |

现有 CORS 来源名单保持不变，方法名单补 `PATCH`，用于 v4 编辑。新增 HTTP 路径：

- `GET /api/compliance/v4/mode`：`{enabled,mode}`；未开放时保留 V3 入口。
- `POST /api/compliance/v4/mode/enable`：必须 `{confirmed:true}`；不可作为自动批量迁移调用。
- `GET /api/compliance/v4/legacy-history`：本人旧历史；切换后使用快照并动态限制来源读取。
- `GET /api/compliance/v4/documents/{id}/drafts`：该文档本人草稿或维护人可见草稿。
- 其余模板/清单/任务接口沿用 `/api/compliance/v4/*`；`start` 支持 DRAFT/PAUSED，归档后只读；任务 PATCH 增加 `remindEnabled:0|1`、`remindDays:1..30`，`waiting:null` 清除等待。
- 日期/完成/等待/个人提醒改变，统一更新任务计划 revision，失效旧 PENDING，按最终字段重建新节点；无日期不发到期节点，未来复查日可独立生成 `review_due`。创建/改期不补发早于当前时刻的节点。
- 任务更新 `id + row_version` 条件写；冲突返回业务 409，事务回滚事件/提醒变更。业务码与 HTTP 码遵循现有 `ApiResponse`，不能只判断 HTTP 200。

模板/执行初版仍以平铺细项和固定版本快照为范围；条件分支、自动跨版本升级、团队共享不是本次验收能力。备注正文不进入公共日志，消息只包含通用提醒和本人任务深链。

## 4. 提醒事务与故障恢复

`ComplianceV4ReminderService` 每节点 REQUIRES_NEW + READ_COMMITTED：按用户→清单→任务→提醒行加锁，发送前检查用户模式/状态、执行状态、来源/项目、适用性、进度、开关和 revision。站内通知 INSERT 与 SENT 更新同事务提交；任何失败整体回滚，再单独持久化尝试次数，最多 3 次置 FAILED。进程在事务中崩溃时数据库回滚，不遗留已提交 SENDING。不同实例争抢同节点最终只有一条站内通知。

外部消息发送不在当前实现中；不得将该数据库事务保证套用于未来企业微信等外部副作用。FAILED 不自动无限重发，运维需核实失败原因后另行受控补偿。

## 5. 真实隔离环境复现

本轮：独立 MySQL 26.7.0（127.0.0.1:13316 / acceptance_v4）、后端 127.0.0.1:18086、Vite 127.0.0.1:5173（浏览器访问允许来源 `http://localhost:5173`）。所有文件目录位于忽略的 `.build-compliance-v4-acceptance/`，企业微信配置为空，定时调度关闭。无生产数据复制，只有历史结构快照和人工 fixture；后端源文件与隔离构建副本逐文件哈希对比。

1. 创建新的独立 datadir，仅监听本机 13316，禁用 mysqlx。初始化 root 后立即设置随机临时口令；创建 `acceptance_v4`，导入经核实的纯结构快照。不要把生产数据导出作为 fixture。
2. 配置进程环境 `V4_ACCEPTANCE_DB=acceptance_v4`、`V4_DB_PASSWORD=<临时口令>`、`V4_TEST_PASSWORD=<随机测试账号口令>`。不要将口令放在命令参数、提交或交接记录中。
3. 运行 `scripts/test-compliance-v4.ps1`。脚本先核实实际 DATABASE、端口、datadir，拒绝其他端点；两份迁移各运行两遍，再执行服务回归和真实 MySQL 验收。测试会清理该隔离库中指定 fixture 表，严禁换成生产连接。报告位于 `backend/target/surefire-reports`。
4. 独立复制 `backend/pom.xml`、`backend/src` 和父级 `version.txt` 到忽略构建目录；`mvn -DskipTests package`。不要占用或覆盖运行中的正式 JAR。
5. 启动候选 JAR，固定上述隔离连接和文件目录，随机 `JWT_SECRET`，启用 V4，关闭 v3/v4 定时调度与企微同步；健康检查通过后启动 Vite，`VITE_API_PROXY_TARGET=http://127.0.0.1:18086`。勿因测试端口改变而放宽生产 CORS 来源。
6. `LIVE_V4_ACCEPTANCE=acceptance_v4` 且 `V4_TEST_PASSWORD` 就绪后运行 `node frontend/tests/compliance-v4-live.mjs`（从 frontend 目录执行时为 `node tests/compliance-v4-live.mjs`）。可用 `PLAYWRIGHT_MODULE=file:///.../playwright/index.mjs` 指向本机安装。此脚本无 API 拦截，真实 UI/登录→Spring→MySQL；不要复用已被其他任务改写的 fixture，先跑步骤 3 重建基线。
7. 旧夹具回归仍用 15173：`tests/compliance-v4-browser.mjs`、`tests/project-config-browser.mjs`。这两项仅作为前端回归，不能替代上一步。

## 6. 发布前验证与回滚限制

必须另行核对目标生产元数据、实际版本、配置差异、备份/恢复、候选健康和权限；完成员工业务复核后审批不停机蓝绿切换。现有 `scripts/deploy-backend.ps1 -GracefulSwitch` 和前端平滑切换脚本只作为后续工具，不能将未执行描述为成功。

回滚优先关闭新版写入/调度并切回兼容入口，保留 `compliance_user_mode`、历史快照、所有执行和提醒记录；不能删除个人模式行来“恢复旧版”。早于本轮、不了解用户模式的旧二进制会重新发送 V3 提醒，**不能直接回滚到该二进制且继续开启旧调度**。如必须回旧二进制，需先停止两套合规调度并保留只读能力，另行批准旧个人写入恢复/数据补偿方案。数据库有业务数据时禁止 DROP 新表。

验收证据与当前交接见 [发布阻断闭环](handover/tasks/20261103_compliance_v4_release_blockers.md)。生产发布、用户逐人切换数量和全员业务验收不在本次“隔离通过”结论中。
