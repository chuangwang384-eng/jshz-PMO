# 本次发布范围说明（H1 审批流事务边界）

日期：2026-11-06
状态：**范围待确认，未发布、未迁移、未改动生产**
用途：供负责人确认发布范围后再进入蓝绿发布执行。

---

## 0. 前置更正：先前对"待部署增量"的判断有误

前一轮以 `git status`／`git diff`（对比 HEAD `b221ad4`）为依据，得出"139 个改动待上线"。该结论不成立：

线上 1.0.31 制品 `.build-dashboard-layout-production-20261106/candidate.jar` 是从**未提交的工作区**构建的，因此工作区里绝大多数改动**已经在生产运行**。

证据（对线上制品做类探测与字段反编译）：

```
BOOT-INF/classes/com/jshz/expert/common/KnowledgeParseStatus.class
BOOT-INF/classes/com/jshz/expert/config/KnowledgeAsyncConfig.class
BOOT-INF/classes/com/jshz/expert/service/KnowledgeParseTaskService.class
BOOT-INF/classes/com/jshz/expert/service/UserAuthStateService.class
BOOT-INF/classes/com/jshz/expert/service/DashboardLayoutService.class
javap SysUser -> private java.lang.Integer tokenVersion;
```

线上制品**不含** H1：`ApplicationService` 无 `afterCommit`/`archiveTempPhotos`，`PhotoStorageService` 仅有 `moveTempPhoto`，无 `buildArchivedName`/`archiveTempPhoto`。

以修改时间界定 13:47（线上构建时刻）之后的真实增量：

```
14:35  backend/src/main/java/com/jshz/expert/service/PhotoStorageService.java
14:39  backend/src/test/java/com/jshz/expert/service/ApplicationTransactionBoundaryTest.java
14:40  backend/src/main/java/com/jshz/expert/service/ApplicationService.java
```

13:47 之后无任何迁移脚本、配置文件改动。

---

## 1. 整包发布的真实内容

构建自当前工作区时，产物相对线上 1.0.31 的**业务差异仅有 H1**，涉及 2 个生产源码文件 + 1 个新增测试。

| 文件 | 改动 | 作用 |
| --- | --- | --- |
| `service/ApplicationService.java` | 14 个写入口统一加 `@Transactional(rollbackFor = Exception.class)`；新增 `afterCommit(scene, task)` 基于 `TransactionSynchronization.afterCommit`；`syncContractByApproval` 取消整体吞异常；类补 `@Slf4j` | 审批流的申请单、合同状态、项目专家快照、审批记录、操作日志在同一事务内原子提交，任一失败整体回滚；照片归档等文件 IO 移到提交之后 |
| `service/PhotoStorageService.java` | 拆为 `buildArchivedName`（事务内只校验+命名）、`archiveTempPhoto`（物理拷贝+删临时文件）；`moveTempPhoto` 保留为组合调用 | 事务内不做磁盘写，避免长事务占用连接 |
| `test/.../ApplicationTransactionBoundaryTest.java` | 新增 | 反射校验 14 个入口注解 + 断言两段式归档行为 |

事务注解入口行号：`updateConfig` 152、`submitRequest` 201、`submitCreateRequest` 239、`submitContractApproval` 426、`approve` 485、`approveModify` 589、`reject` 678、`returnRequest` 710、`resubmit` 739/744、`saveConfigUsers` 832、`withdraw` 881、`transfer` 1035、`addApprover` 1067。

> 说明：`approveCreate` / `approveContract` / `approveModify` 三个私有分支由 `approve` 的代理事务统一包裹；自调用不经过代理，故仅在 `approve` 与 public 的 `approveModify` 上声明，避免出现"以为开了新事务实际没有"的误导注解。

---

## 2. 已在线上、不属于本次增量的部分

以下内容同在工作区未提交，但已随 12:15 的 1.0.31 与 13:47 的工作台发布进入生产，本次不重复变更：

| 项 | 内容 | 线上状态 |
| --- | --- | --- |
| P1-2 | JWT `tv` claim 与 `sys_user.token_version` 比对、查库异常 fail-closed、改密回写 Cookie | 已在线上（`SysUser.tokenVersion` 存在于线上制品） |
| P1-5 | 知识文档流入口统一 `requireDownloadable` | 已在线上 |
| P2-7 | 上传流式落盘 + `PENDING` 异步解析 + 解析线程池 | 已在线上（`KnowledgeParseTaskService` 存在于线上制品） |
| P2-9 | 移除零引用 `spring-boot-starter-validation` | 已在线上 |
| 工作台二维布局 | `DashboardLayoutService` + `DashboardController` 布局接口 | 已在线上（13:47 发布） |
| 前端 | `1.0.31-dashboard-layout-ui-fix-20261106` | 已在线上（Nginx root 指向该目录） |
| 文档整理 | 72 个 `docs/*.md` 删除、`docs/design/` 现有 42 个同名文件 | 搬移归类，不参与运行时构建 |

---

## 3. 数据库迁移状态：已执行，无需再跑

按今日两份生产备份中的表结构比对：

| 备份 | `token_version` | `parse_status` / `parse_message` / `parse_time` |
| --- | --- | --- |
| `backup/backup_20261106_120546` | 0 处 | 无 |
| `backup/backup_20261106_134251` | 1 处 | 三项均在 |

结论：`20261106_add_user_token_version.sql` 与 `20261106_add_knowledge_parse_status.sql` 已在 12:05–13:42 之间执行完毕，生产库具备 H1 所需表结构。H1 本身不新增任何列，不依赖迁移。

> 若需复核，两个脚本均为幂等（`information_schema` 判定后 `PREPARE/EXECUTE`），重复执行只输出 `SELECT 1`，不改数据。

---

## 4. 本次发布的行为变化（业务可见）

1. **原子性**：审批流单次操作中 5 类写入要么全部成功、要么全部回滚，不再出现"申请单已通过、合同未生效"的半提交数据。
2. **合同联动异常改为请求失败**：`syncContractByApproval` 原实现整体 `catch(Exception){}`，合同 status 写失败被静默吞掉。现改为向上抛出触发回滚——合同联动异常时审批请求会返回失败，而不是部分成功。这是本次唯一对调用方可见的语义变化。
3. **项目-专家快照降级保留**：`syncProjectExpertSnapshot` 失败仍不阻塞主流程，但从空 catch 改为 `log.warn` 留痕。
4. **照片归档时机**：由审批事务内改为事务提交后执行。提交后 IO 失败只记 error 日志（含专家 ID、临时文件、目标文件名），不回滚已提交的审批结果。

无新增接口、无接口签名变更、无前端改动、无新增环境变量、无表结构变更。

---

## 5. 发布方式：沿用现有蓝绿 11 阶段

`scripts/deploy-backend.ps1 -GracefulSwitch`，流程为：预检 → 影子 8081 启动 → 影子健康+业务探测 → 备份 Nginx conf → 切流 8081 → `nginx -t` → SYSTEM 单次任务平滑重载 → 蓝验证 → 稳态化 NSSM（停服→归档旧 jar→替换→启服→8080 健康）→ 切回 8080 → 清理归档。

关键参数：

- 候选制品：独立目录构建，禁止在服务运行时于 `backend/` 直接 `mvn package`（2026-10-31 瘦 jar 事故根因）。构建后需校验 `BOOT-INF/lib` 项数 > 0。
- 回滚基线：`backend/releases/1.0.31-dashboard-layout-20261106-20261106_134639/previous-expert-system.jar`（58,800,754 B，`BOOT-INF/lib` 69 项，可启动包）。
- 版本号：`1.0.32`（`version.txt` 现为 1.0.31，且 1.0.31 已被 13:47 发布占用）。
- 凭据：会话内 `MYSQL_PASSWORD` / `JWT_SECRET` 必须与 NSSM `AppEnvironmentExtra` 完全一致，脚本第 1 阶段强制比对，不一致直接中止（JWT 不变则全员免重登）。
- 提权：`-GracefulSwitch` 与非提权会话互斥，第 1 阶段即 throw；停/启 NSSM 服务同样需要管理员。

---

## 6. 回滚

- 后端：`Copy-Item` 回滚基线 jar 覆盖 `backend/target/expert-system.jar` → `Start-Service ExpertSystemBackend`；或重跑脚本并以 `-RollbackJarPath` 指向基线。
- 无数据库变更需回退；H1 回滚后 `token_version` 等列保留，旧代码忽略。
- 回滚后重新引入的缺陷：审批流多表写无事务、合同联动异常静默吞掉、照片归档在事务内。

---

## 7. 剩余前置条件与未覆盖风险

**前置条件（唯一）**：提权。当前会话属 Administrators 组但为 UAC 过滤令牌（`只用于拒绝的组`），须由 UAC 授权后才能执行第 1 阶段。

**未覆盖风险（本次不修，需知悉）**：

1. **并发双批（H2）未修**：`getPending` 仍是"先查状态再更新"，无行锁与状态条件更新。两个审批人并发点击仍可能重复通过（重复建专家／重复生效合同）。加事务不解决该问题，需改为 `UPDATE ... WHERE id=? AND status=0`。
2. **提交后 IO 失败无自动补偿**：数据库与文件系统不共享事务，归档失败时 `expert_info` 会指向尚未落盘的文件名，靠 error 日志人工补齐。
3. **真实库全链路未验证**：本次未连生产库跑"提交→通过（含照片）／驳回／撤回"并核对 5 张表一致性，建议发布后用业务账号走一轮。
4. **严格零失败门槛**：今日 13:46 的连续性探针曾出现 1 次 `ECONNRESET`，非本次引入，但说明"严格不停机零失败"在现网尚未稳定成立。
