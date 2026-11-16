# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261106-approval-tx
- 任务名称：审批流写入口事务边界修复（审查缺陷 H1）
- 主责 Agent：WorkBuddy（codex/personal-dashboard-v11 分支工作区）
- 交接时间：2026-11-06 14:50
- 当前状态：已完成待验收
- 优先级：P0

## 2. 目标与验收条件
- 业务目标：审批流的提交/通过/驳回/退回/撤回/转办/加签在单次操作内涉及的多处写入必须原子提交或整体回滚，不允许半提交脏数据。
- 本次范围：`ApplicationService` 审批流写入口加事务；照片归档/删除等文件 IO 移到事务提交后。
- 不在本次范围：审批并发双批（H2，状态条件更新/行锁）、权限 fail-open（H3）、明文导出（H4）、前端缺陷。
- 验收条件：
  - [x] 所有审批流写入口声明 `@Transactional(rollbackFor = Exception.class)`。
  - [x] 单次操作内 5 类写入（申请单 / 合同 status+approval_ref / project_expert 快照 / approval_record / operation_log）在同一事务内。
  - [x] 照片归档、旧照片删除、临时照片清理不在事务内执行，改为提交后执行。
  - [x] 编译通过、后端全量测试通过。
  - [ ] 真实 MySQL 环境跑通一次「提交→审批通过」「提交→驳回」「提交→撤回」全链路（需运行环境，未执行）。

## 3. 项目基线
- 分支：`codex/personal-dashboard-v11`
- 当前提交号：`b221ad4`（工作区含他人未提交改动，本任务未提交、未还原任何他人改动）
- 工作区状态：脏（含本次 3 个文件改动 + 既有未提交改动）
- 后端：Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.7；MySQL 8（expert_system）
- 以上信息核实时间：2026-11-06 14:40

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据或说明 |
| --- | --- | --- | --- |
| 写入口事务注解 | `service/ApplicationService.java` | 完成 | 14 个方法/重载声明 `@Transactional(rollbackFor = Exception.class)` |
| 照片归档两段式 | `service/PhotoStorageService.java` | 完成 | 新增 `buildArchivedName`（只命名）/ `archiveTempPhoto`（实际拷贝），`moveTempPhoto` 保留为组合调用 |
| 事务提交后执行 IO | `service/ApplicationService.java` | 完成 | `afterCommit` + `TransactionSynchronization.afterCommit`；归档/删除/清理均走该通道 |
| 合同联动失败不再静默 | `service/ApplicationService.java` | 完成 | `syncContractByApproval` 去掉整体 try-catch；快照联动改为 warn 日志 |
| 回归测试 | `src/test/.../ApplicationTransactionBoundaryTest.java` | 完成 | 注解反射校验 + 归档两段式文件断言 |

## 5. 实际变更
- 新增文件：`backend/src/test/java/com/jshz/expert/service/ApplicationTransactionBoundaryTest.java`
- 修改文件：
  - `backend/src/main/java/com/jshz/expert/service/ApplicationService.java`（imports、`@Slf4j`、事务注解、照片两段式、`afterCommit`/`archiveTempPhotos` 辅助方法、合同联动异常策略）
  - `backend/src/main/java/com/jshz/expert/service/PhotoStorageService.java`（`buildArchivedName` / `archiveTempPhoto` / `moveTempPhoto` 重构）
  - `docs/architecture.md`（新增「审批流事务边界」章节）
  - `docs/handover/README.md`（最新交接摘要新增一条）
- 数据库/迁移：无
- API/接口契约：无变化（HTTP 路径与返回结构不变）
- 页面/路由/权限：无变化
- 配置/环境变量：无变化
- 依赖变更：无

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 证据/备注 |
| --- | --- | --- | --- |
| 编译 | `cd backend && mvn -q -DskipTests compile` | 通过 | exit 0 |
| 新增回归测试 | `mvn -q -Dtest=ApplicationTransactionBoundaryTest test` | 通过 | exit 0 |
| 全量单元测试 | `JWT_SECRET=<测试值> MYSQL_PASSWORD=dummy mvn -q test` | 通过 | 75 项、0 失败、0 错误、9 跳过 |
| 无 JWT_SECRET 时 contextLoads | `mvn -q test` | 失败（环境依赖，非本次引入） | `JwtTokenProvider` 构造器 `Keys.hmacShaKeyFor` 对空密钥抛异常，属既有 fail-fast 设计 |
| 真实 MySQL 全链路 | 提交→通过 / 驳回 / 撤回 | 未执行 | 需连接生产或隔离库，本次未触碰数据库 |
| 部署健康检查 | — | 不适用 | 未发布 |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 真实库全链路验证 | 本会话未连接数据库；生产当前另有登录 500 未恢复事件在跟踪 | 事务行为只在单测层面验证 | 隔离库执行三次链路并核对 application/approval_record/operation_log/project_contract 一致性 | 后端 |
| H2 并发双批 | 未在本次范围 | 两个审批人并发仍可能重复通过 | 改 `UPDATE ... WHERE id=? AND status=0` 或加行锁 | 后端 |
| 提交后 IO 失败的补偿 | 数据库与文件系统无共享事务 | 归档失败时数据库已指向新文件名但文件未落盘 | 按 error 日志人工补齐；如需自动化可加补偿任务 | 后端 |

## 8. 风险与回滚
- 已知问题：提交后照片归档失败会在 `application`/`expert_info` 留下指向不存在文件的文件名，日志含专家 ID、临时文件名、目标文件名。
- 数据风险：事务边界扩大后，原先被静默吞掉的合同联动异常会使审批请求失败（500）而非"部分成功"，属预期的原子语义变化，需业务知悉。
- 兼容性风险：`moveTempPhoto` 仍保留，外部调用方（当前仅 ApplicationService）行为不变。
- 回滚步骤：`git checkout -- backend/src/main/java/com/jshz/expert/service/ApplicationService.java backend/src/main/java/com/jshz/expert/service/PhotoStorageService.java`，并删除新增测试文件；无数据库变更需回滚。
- 回滚限制：回滚后重新引入 H1 脏数据风险。

## 9. 文档同步
- [x] 架构文档（`docs/architecture.md` 新增审批流事务边界章节）
- [x] 交接摘要（`docs/handover/README.md`）
- [x] 测试说明（本文件第 6 节）
- [ ] 部署文档（无部署变更，不适用）
- [ ] 接口文档（无接口变更，不适用）
- [ ] 发布说明（未发布）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status` 确认工作区改动范围；`git diff -- backend/src/main/java/com/jshz/expert/service/ApplicationService.java` 复核 14 处事务注解与 `afterCommit` 调用点。
2. 然后执行：在隔离 MySQL 库跑「提交→审批通过（含照片）」「提交→驳回」「提交→撤回」，核对 `application.status`、`project_contract.status`、`approval_record`、`operation_log`、`project_expert.contract_status` 是否一致；再构造中途失败（如合同行临时加约束冲突）验证整体回滚。
3. 验收标准：失败场景下 5 类写入均无残留；成功场景下照片文件存在于 `照片库/`、临时目录无残留。
4. 未经确认禁止：连接生产库执行验证；直接发布。
5. 需要负责人决策：H2 并发双批是否纳入下一批修复。
