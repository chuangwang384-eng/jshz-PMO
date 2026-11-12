# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261111-approval-record-autoincrement
- 任务名称：专家库提交申请报 Duplicate entry（approval_record 自增值不同步）排查与修复
- 主责 Agent：CodeBuddy
- 交接时间：2026-11-11 16:10
- 当前状态：已完成待验收
- 优先级：P0

## 2. 目标与验收条件
- 业务目标：解析简历后提交专家申请不再报"服务器内部错误：Duplicate entry ... approval_record.PRIMARY"，上传/提交流程正常完成。
- 本次范围：定位故障点、修复数据库自增值、加固后端异常提示、沉淀迁移脚本与交接记录。
- 不在本次范围：排查自增值被重置的具体操作来源；发布后端改动；其他表的自增巡检自动化。
- 验收条件：
  - [x] 故障点定位到具体 SQL 与代码行（ApplicationService.addRecord:1460，submitCreateRequest:260）。
  - [x] 根因确认：表自增值落后于存量主键（非文件大小/格式/编码/必填字段/超时问题）。
  - [x] 数据库自增值已校正并实测验证（试插入分配 152，回滚无残留）。
  - [x] 后端编译通过；DuplicateKeyException 友好提示 + 兜底 500 脱敏。
  - [ ] 用户在页面实际重新提交一次申请（需用户操作，未验证）。
  - [ ] 后端异常处理改动随下次发布生效（当前生产仍运行 1.0.38.7 旧 jar）。

## 3. 项目基线
- 分支：master（`git rev-parse --short HEAD` = a417c51）
- 当前提交号：a417c51；工作区脏（含本任务 GlobalExceptionHandler 改动 + 他人既有未提交改动，本任务未触碰他人文件）
- 生产后端版本：1.0.38.7（NSSM ExpertSystemBackend，AppDirectory=<LOCAL_PATH>
- 数据库：MySQL Server 26.7（服务 MySQL26），expert_system
- 关键事实核实时间：2026-11-11 15:30~16:00（只读 SQL + logs/expert-app.log）

## 4. 排查结论（证据链）
1. 前端截图/日志：报错为 MyBatis 原始 SQL 错误透出，`Duplicate entry '138'→'139'→'140' for key 'approval_record.PRIMARY'`，连续三次提交分别撞上三个已存在主键 → 说明是自增计数器落后，且每次失败后计数器仍在前进。
2. 日志定位：`logs/expert-app.log` 行 4592/4593/4731；堆栈 `ApplicationService.addRecord(ApplicationService.java:1460) ← submitCreateRequest(:260)`；SQL 为 `INSERT INTO approval_record (request_id, application_id, node_name, action, operator, comment, create_time) VALUES (?,?,?,?,?,?,?)`（id 不在列清单中，由表 AUTO_INCREMENT 分配）。
3. 实测库（只读）：
   - `approval_record`：AUTO_INCREMENT=141，MAX(id)=151，id 138/139/140 均已存在（COUNT=3）。
   - id 141~151 为 2026-11-07 王创的合法业务数据（SUBMIT/APPROVE/REJECT）。
   - `application`（MAX=103）、`expert_info`、`notification`、`operation_log`、`approval_config` 自增值均正常，仅 approval_record 不同步。
4. 结论：**错误发生在"数据入库"阶段的审批进度记录写入，根因是 approval_record 自增计数器被重置（141）落后于存量最大主键（151）**。与文件上传、简历解析、文件大小/格式/编码、必填字段、超时均无关。自增值被重置的具体操作来源未查明（疑似 11-07 前后的人工数据恢复/手工 ALTER），需后续排查。
5. 数据完整性：失败请求因 20261106 事务边界修复（@Transactional）已整体回滚，application 最大 id 保持 103，无脏数据。

## 5. 实际变更
- 数据库（生产已执行）：
  - `ALTER TABLE expert_system.approval_record AUTO_INCREMENT = 152`（通过动态 `MAX(id)+1` 计算后执行；仅元数据，无数据增删改，天然可逆/幂等）。
- 迁移脚本：
  - 新增 `database/migration/20261111_fix_approval_record_auto_increment.sql`（幂等，含执行记录与验证语句）。
- 修改文件：
  - `backend/src/main/java/com/jshz/expert/config/GlobalExceptionHandler.java`：
    ① 新增 `DuplicateKeyException` 处理器，前端返回"数据保存冲突，请稍后重试…"，不再透出原始 SQL；② 兜底 500 改为返回通用提示（"服务器内部错误，请稍后重试…"），完整堆栈只写日志，避免泄露 SQL/表结构细节。
- API/接口契约：无变化（仅 500 时 message 文案变化）。
- 页面/路由/权限：无变化。
- 配置/环境变量/依赖：无变化。

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 证据/备注 |
| --- | --- | --- | --- |
| 自增值校正 | 动态 ALTER（PREPARE/EXECUTE）后 `SHOW TABLE STATUS`、事务内试插入 `SELECT LAST_INSERT_ID()` | 通过 | 分配 id=152；`ROLLBACK` 后 `COUNT(*) WHERE node_name='__AUTOINC_VERIFY__'`=0（无残留） |
| 其余表自增巡检 | 对 application/expert_info/notification/operation_log/approval_config 逐一比对 AUTO_INCREMENT 与 MAX(id) | 通过 | 均无不同步 |
| 后端编译 | `mvn -q -DskipTests -Drevision=1.0.38.7 -DgitShort=a417c51 compile`（在 backend/，未打包，生产 jar 未受影响） | 通过 | exit 0 |
| 单元测试 | 全量 mvn test | 未执行 | 本次改动仅为异常文案与 DB 元数据，无逻辑分支变化；如需可后续补跑 |
| 页面重新提交申请 | 用户在浏览器重试"提交申请" | 未执行（待用户验收） | 数据库层面已消除冲突来源 |
| 新异常文案生效 | 需重新构建发布后端 | 未执行 | 当前生产仍运行 1.0.38.7 旧 jar |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 自增值被重置的来源未查明 | 无审计线索，141~151 写入于 11-07 | 若有维护脚本/人工恢复流程在改库，可能复发或波及其他表 | 排查 11-07~11-11 的手工 SQL/备份恢复操作；建议对核心表增加"自增值 vs MAX(id)"巡检 | 后端/运维 |
| 后端异常处理改动未发布 | 未执行发布流程 | 生产 500 仍可能透出原始 SQL | 随下次后端发布一并生效 | 项目负责人批准后发布 |
| 用户页面验收 | 需用户操作 | — | 用户重试一次提交申请确认成功 | 用户/Owner |

## 8. 风险与回滚
- 已知问题：自增分配在冲突期间产生空洞（138~152 间有跳号），不影响业务语义。
- 数据风险：无（本次仅调整自增元数据，未改动任何行）。
- 兼容性风险：兜底 500 文案变化，若前端有对原始错误文案的字符串匹配需检查（当前未见）。
- 回滚步骤：数据库无需回滚（自增值只会随业务前进）；代码回滚 `git checkout -- backend/src/main/java/com/jshz/expert/config/GlobalExceptionHandler.java`，删除迁移脚本与相关文档即可。
- 回滚限制：回滚异常处理改动会恢复"原始 SQL 错误透出前端"的现状。

## 9. 文档同步
- [x] 迁移脚本（database/migration/20261111_fix_approval_record_auto_increment.sql）
- [x] 最新交接摘要（docs/handover/README.md 顶部新增）
- [x] 本任务交接记录（docs/handover/tasks/20261111_approval_record_auto_increment_fix.md）
- [ ] 发布说明（后端改动未发布，发布时补）
- [ ] 架构/部署文档（无结构性变更，不适用）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`SHOW TABLE STATUS FROM expert_system LIKE 'approval_record';` 确认自增值 ≥ 当前 MAX(id)+1；`git status` 确认 GlobalExceptionHandler 改动仍在工作区。
2. 然后执行：随下次后端发布携带 GlobalExceptionHandler 改动；发布后验证 500 文案不再含 SQL 细节。
3. 验收标准：用户页面提交申请成功；`approval_record` 新记录 id 从 152 起连续分配。
4. 未经确认禁止：对生产库执行任何手工 ALTER/恢复类操作前不设自增校验；直接发布。
5. 需要负责人决策：是否安排排查 11-07 前后的改库操作来源，以及是否为核心表增加自增巡检脚本。
