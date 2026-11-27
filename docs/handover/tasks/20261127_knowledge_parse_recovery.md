# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261127-knowledge-parse-recovery
- 任务名称：知识文档解析链路缺陷修复（崩溃恢复缺失）+ parse_status 双口径评估
- 主责 Agent：CodeBuddy
- 交接时间：2026-11-27
- 当前状态：已完成待验收
- 优先级：P1（RUNNING 卡死导致合规上传永久 409，无自愈路径）

## 2. 目标与验收条件
- 业务目标：①评估 parse_status 双口径是否需调整；②修复解析链路崩溃恢复缺失；③梳理并修复链路上其他真实缺陷。
- 本次范围：解析链路 4 个后端文件。
- 验收条件：
  - [x] 编译通过
  - [x] 静态复查通过（含自调用事务陷阱自查修正）
  - [x] 调用方影响面核实
  - [ ] 运行时验证（需重启场景，未发布故未执行）

## 3. 项目基线（2026-11-27 核实）
- 分支：main；最新提交 `8758976`（revision=1.0.159）
- 工作区状态：不干净（同日另有操作日志切面/企微客户端/FileNames/IpUtils 修复及他人未提交的 1.0.159 财务改动）。本任务改 4 个文件，未触碰他人改动。
- 运行环境：生产 1.0.158，本改动未发布。

## 4. 已完成工作

### 4.1 parse_status 双口径评估结论（只评估，未改代码）
**结论：保持两套口径，不合并。**
- 依据①：业务语义不同——`knowledge_document`（本常量类 PENDING/RUNNING/SUCCESS/FAILED）是**异步**正文提取，任务可崩溃/重启，需要 RUNNING 中间态与恢复语义；`project_schedule_stage`（PENDING/PARSING/DONE/FAILED）是**请求内同步**解析（`ScheduleParseService` 乐观锁 `eq(parseStatus, 当前值)→set PARSING`，:364-367），无重启中断问题，PARSING/DONE 口径与前端按钮态（ProjectDetail.vue:168 `!== 'PARSING'`）耦合。
- 依据②：前端已显式隔离——`frontend/src/utils/dict.js:4` 注释"枚举域不同的字典（如日程 parseStatus vs 合规文档解析状态）不得合并"，两套映射各自维护。
- 依据③：迁移脚本列注释分别固化（20261106/20261109），合并须双表 DDL + 前端映射 + 后端状态机三处联动，收益为零。
- 依据④：已核实无任何交叉误用（ScheduleParseService 全程字面量、知识链路全程常量类）。

### 4.2 修复清单

| 缺陷 | 根因 | 修复 | 文件 | 状态 |
| --- | --- | --- | --- | --- |
| RUNNING 卡死 → 合规上传永久 409 死锁 | 任务只在上传请求里提交一次，无恢复机制；checkUploadParsing(:243) 对 RUNNING 拒绝 | 新建启动恢复：ApplicationReadyEvent 时全量 RUNNING→FAILED（message 注明重启中断） | service/KnowledgeParseRecoveryService.java（新建） | 完成 |
| PENDING 卡死（提交前重启） | 同上 | 启动恢复：在架（status=1）PENDING 文档逐个重触发 parseAsync（复用 id+version 乐观校验），线程池拒绝→markFailed | 同上 | 完成 |
| 下架文档卡 PENDING 的 relist 盲区 | 启动恢复跳过下架文档；relist 只置 status=1 不触发 | relist 时若 PENDING 补触发一次 | controller/KnowledgeController.java | 完成 |
| 优雅停机打断在跑任务 | ThreadPoolTaskExecutor 默认 shutdownNow | setWaitForTasksToCompleteOnShutdown(true) + awaitTermination 30s；强杀仍靠启动恢复兜底 | config/KnowledgeAsyncConfig.java | 完成 |

### 4.3 梳理后判定不修的项（记录留痕）
- `versionIdOf` 返回 null 传给 parseAndCreateDrafts：version 与文档同事务创建，理论不可达，且 parseAndCreateDrafts 对 null 容忍；
- checkUploadParsing 的 TOCTOU 窗口：合规上传的是新文件本身而非知识文档正文，竞态无实际危害；
- ScheduleParseService 字面量硬编码：与既有实现一致，属风格问题，随双口径"不合并"结论维持现状。

## 5. 实际变更
- 新建：`backend/src/main/java/com/jshz/expert/service/KnowledgeParseRecoveryService.java`
- 修改：`backend/src/main/java/com/jshz/expert/config/KnowledgeAsyncConfig.java`（+4 行）
- 修改：`backend/src/main/java/com/jshz/expert/controller/KnowledgeController.java`（relist 端点 +5 行）
- 未修改（评估留痕）：`KnowledgeParseStatus.java`、`ScheduleParseService.java`
- 数据库/API 签名/权限/环境变量：无（启动恢复无新端点）

## 6. 测试与验证

| 验证项 | 执行命令/步骤 | 结果 | 备注 |
| --- | --- | --- | --- |
| 编译 | `mvn -q -DskipTests "-Drevision=1.0.159" "-DgitShort=8758976" compile` | 通过 EXIT=0 | — |
| IDE 检查 | read_lints 三个文件 | 无错误 | 新增 warning 均为仓库既有 MyBatis-Plus lambda 空值分析风格；KnowledgeController:13 未使用 import 为既有问题未动 |
| 自查修正 | 复查发现 recoverOnStartup→this.recoverRunning() 自调用致 @Transactional 无效 | 已修正 | 单条 UPDATE 无原子性需求，去掉注解并注释说明 |
| user=null 容忍 | 走查 parseAndCreateDrafts→markImportantByLlm/ParamUtils.usernameOf | 通过 | 恢复触发场景无登录用户，全链 null 安全 |
| 运行时验证 | 未执行 | 未执行 | 需"上传→重启→观察恢复"场景，随发布后冒烟 |

## 7. 未完成事项与阻塞

| 事项 | 原因 | 影响 | 下一步 | 负责人决策点 |
| --- | --- | --- | --- | --- |
| 运行时恢复验证 | 未发布 | — | 发布后冒烟：上传文档→立即重启→确认日志"崩溃恢复完成"且状态 FAILED/重新解析 | 项目负责人 |
| 正式单测 | 仓库无相关测试基线 | 回归保护弱 | 建议 RecoveryService 抽纯函数补测 | 项目负责人 |
| 随 1.0.159 发布 | 未获提交授权；工作区含他人改动 | 修复未上线 | 与同批次修复一并提交 | 项目负责人 |

## 8. 风险与回滚
- 已知问题：**单实例前提**——恢复会把 RUNNING 全量置 FAILED；多实例部署时会误标其他实例在跑任务（该任务完成时按 id+version 写回 SUCCESS，最终一致但状态抖动）。类注释已声明，多实例演进须改心跳/租约判定。
- 数据风险：低。RUNNING→FAILED 仅改状态字段，正文 content 不动；PENDING 重触发复用现有异步链路。
- 兼容性风险：低。relist 补触发仅在 PENDING 时生效，正常文档零变化；优雅停机使停机耗时最多增加 30s（NSSM 停机超时需确认>30s，start-backend.bat 停脚本需留意）。
- 回滚步骤：删除新建文件 + 还原另两个文件即可。
- 回滚限制：无。

## 9. 文档同步
- [x] 本任务记录；[x] README 摘要
- [x] docs/architecture.md：无需修改（无表结构/接口变更；恢复属实现细节）
- [x] 历史设计文档（2026-10-30 P2-7）不追溯

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status backend/src/main/java/com/jshz/expert/{service/KnowledgeParseRecoveryService.java,config/KnowledgeAsyncConfig.java,controller/KnowledgeController.java}` 与第 5 节一致。
2. 然后执行：随 1.0.159 发布前重跑编译；发布冒烟按第 7 节第一条。
3. 验收标准：编译通过；重启场景日志出现"知识文档解析崩溃恢复完成"；卡 RUNNING 文档重启后可正常发起合规上传。
4. 未经确认禁止：不得把恢复改为 @Scheduled 周期扫描（当前"启动一次"是刻意最小方案，改周期须负责人确认）；不得合并两套 parse_status 口径；不得替他人提交 1.0.159 改动。
5. 需要负责人决策：NSSM 停机超时是否 ≥30s（优雅停机等待）；是否补单测。
