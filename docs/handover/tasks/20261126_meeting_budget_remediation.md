# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261126-MEETING-BUDGET-REMEDIATION
- 任务名称：会议预算制作阶段审查整改（P1×2 + P2/P3 清单落地）
- 主责 Agent：AutoClaw（本会话）
- 交接时间：2026-11-26
- 当前状态：已完成待验收（未发布，未执行生产库迁移）
- 优先级：P1
- 前置输入：[会议预算阶段代码审查报告](20261126_meeting_budget_review.md)（28 条发现 + 6 条新需求建议，全部经独立复核）

## 2. 目标与验收条件
- 业务目标：按负责人批准的整改清单修复会议预算制作阶段的代码缺陷、死代码、硬编码与边界问题。
- 本次范围：见第 5 节文件清单（仅会议预算阶段相关前后端 + 数据库迁移 + 文档）。
- 不在本次范围：
  - P2-⑦ 成本单导出权限口径（需负责人确认成本数据可见口径）；
  - 其他模块任何改动。
  - （P2-⑥ 收费标准权限收紧已于 2026-11-26 第二轮在本次提交内完成，见第 4/5 节追加行。）
- 验收条件：
  - [x] P1-① 空点选确认链路前后端双层封死（单测覆盖归档空明细拒绝）；
  - [x] P1-②/P2-③ 报价单孤儿死代码删除，后端 3 端点下线；
  - [x] 费率、状态字典、类目字典、下载逻辑单一真源化；
  - [x] 乐观锁 + 降级确认 + 操作日志 + 各项边界 UX；
  - [ ] 生产环境验证（发布后另行验收）。

## 3. 项目基线
- 分支：main；起点提交 1d96200（工作区起点干净，仅审查报告未跟踪）。
- 前端/后端/数据库版本：version.txt revision=1.0.158（2026-11-26 第二轮 bump，frontend/package.json 同步 1.0.158；pom ${revision} 由构建流程从 version.txt 派生，未手改）。
- 运行环境：本机生产 <LOCAL_PATH> 8080 / Nginx 前端）；本次未发布、未重启服务。
- 以上信息核实时间：2026-11-26（git status / 编译 / 构建输出）。

## 4. 已完成工作（对照审查报告编号）
| 发现 | 内容 | 状态 | 证据 |
| --- | --- | --- | --- |
| P1-①/P2-⑤ | 前端确认按钮改 hasPositiveItems；后端 save confirm 分支 + archiveConfirmed 空明细拒绝；mapList 抛业务异常 | 完成 | 单测 archiveConfirmedRejectsEmptyItemsEvenWithExporter；mvn test 通过 |
| P1-②/P2-③ | 删 ProjectBudgetQuotes.vue、5 个 API 封装；后端下线 generate/customer-confirmation/restore；archiveConfirmed 返回 boolean；workbook 等死代码清除 | 完成 | rg 全库无残留引用；QuoteController 注释注明存档用途 |
| P2-④ | 汇总链费率改读 budget.serviceRate/taxRate（0.06 仅兜底），模板文案动态百分比 | 完成 | Panel L255-258 |
| P2-⑧ | ONLINE 也持久化 estimate_inputs（null 不回擦旧值） | 完成 | ProjectBudgetService save |
| P2-⑨ | 已确认单 save(false)：前端弹确认框，后端写 ACTION_BUDGET_SAVE 操作日志 | 完成 | Panel save()；Service demotion 日志 |
| P2-⑩ | project_budget.version 乐观锁：前端携带加载时 version，后端不一致 409 | 完成 | 迁移 20261126_budget_optimistic_lock.sql + init.sql 并列 |
| P2-11 | removeSection 前 ElMessageBox.confirm，明示行数 | 完成 | Panel removeSection |
| P2-12 | 单价 0 行：行内红点 + 保存/确认前警示确认 | 完成 | Panel isZeroPrice/zero-price-dot |
| P2-13 | libraryRows 过滤 EST_ 前缀（后端 activeFeeStandards 不过滤——估算面板依赖 EST_* 读值，豁免理由见第 8 节） | 完成 | Panel libraryAll |
| P3-14 | estimate_inputs JSON.parse try/catch 降级 + 提示 | 完成 | Panel applySaved |
| P3-15 | 库弹窗显示总数与截断提示 | 完成 | Panel libraryTotal |
| P3-16 | doExport 改 downloadBlob；CATALOGS 收口 utils/dict.js BUDGET_FEE_CATALOGS（两页共用） | 完成 | dict.js；BudgetFeeConfig 引用 |
| P3-17 | 删除 canRecord/documents props 与 generated emit | 完成 | Panel defineProps/defineEmits |
| P3-18 | confirm 端点保留（幂等修复用途），Controller 注释 + 本记录注明无 UI 入口 | 完成 | 注记 |
| P3-19 | ESTIMATE_RULES 确立为唯一内置默认值真源（注释改写）；basis 回退时标注「按内置默认值估算」 | 完成 | budgetEstimate.js；7 个既有单测全过 |
| P3-20 | 删除不可达 watch(projectId)，保留 :key 重建 | 完成 | Panel |
| P3-21 | 后端 ProjectBudgetStatus 枚举 + 前端 BUDGET_STATUS_TEXT 成对登记（17.7） | 完成 | 两文件同次修改 |
| P3-22 | 前端提交前过滤异类分节行；后端分节报错文案带费用项名 | 完成 | Panel save；parseItem |
| P3-23 | 非预算阶段重确认：warnIfNotArchived 写操作日志 + 响应 warn + 前端 ElMessage.warning | 完成 | Controller |
| P3-24 | 前端保存前长度预检；后端报错带具体字数 | 完成 | 两侧 |
| P3-26 | parseItem 上限（数量≤1e6、单价/成本≤1e8、数量小数≤2）+ 文案带费用项名 | 完成 | parseItem |
| P3-27 | restore 路径整体删除（P1-② 决策①），冗余写与误导文案随之消除 | 完成 | QuoteService |
| P3-28 | 删 materialQuantityItem 与无参 activeFeeStandards() | 完成 | Service |

## 5. 实际变更
- 新增文件：`backend/.../entity/ProjectBudgetStatus.java`、`database/migration/20261126_budget_optimistic_lock.sql`、`database/migration/20261126_budget_fee_permission.sql`、本记录、审查报告（前次任务）。
- 修改文件：
  - 后端：ProjectBudgetController / ProjectBudgetQuoteController / ProjectBudgetService / ProjectBudgetQuoteService / ProjectBudget（加 version）/ OperationLogService（ACTION_BUDGET_SAVE/CONFIRM）；
  - 测试：ProjectBudgetServiceTest（注入 OperationLogService mock）、ProjectBudgetQuoteServiceTest（按新契约重写：删 generate/confirm 用例，增非预算阶段 false 与空明细拒绝用例）；
  - 前端：ProjectBudgetPanel.vue / api/project.js / utils/dict.js（BUDGET_FEE_CATALOGS、BUDGET_STATUS_TEXT）/ utils 无其它 / BudgetFeeConfig.vue / budgetEstimate.js；
  - 数据库：init.sql（project_budget.version 列；权限段 1480-1482 行新增 id 280-282 收费标准三规则，与迁移 20261126_budget_fee_permission.sql 成对）；
  - 文档：docs/architecture.md（2026-11-26 整改段）、docs/deployment.md（迁移小节 + 业务表数 88→92 实测修正）。
- 删除文件：`frontend/src/views/project/components/ProjectBudgetQuotes.vue`；api/project.js 5 个报价函数。
- 数据库/迁移：`20261126_budget_optimistic_lock.sql`（**未执行**，随发布 db-migrate 流程执行；先迁移后切后端，顺序见 deployment.md）。
- API 契约变更（不兼容删除，属授权决策①）：POST /quotes（手动生成）、POST /quotes/{versionId}/customer-confirmation、POST /quotes/{versionId}/restore 下线；GET /quotes、GET /quotes/{versionId}/file 保留。PUT meeting-budget 请求新增可选 `version` 字段；确认响应可能新增 `warn` 字段（向后兼容）。
- 权限（2026-11-26 第二轮）：新增 type=2 规则 3 行，GET/PUT `/api/project/config/budget-fee-standards` 与 DELETE `/api/project/config/budget-fee-standards/*` 统一绑定 `project:manage`（迁移 `20261126_budget_fee_permission.sql`，幂等 INSERT IGNORE，回滚见文末注释）；未用 `/api/project/config/**` 通配，config/phases、config/types 字典读取不受影响（20261029 放开规则保留）。`project:manage` 已经由 role_permission 授予角色 2/53，无需新增角色绑定；EMPLOYEE 收紧后应 403。同步更正了 ProjectBudgetController 失实的类注释。
- 配置/环境变量：无新增。
- 状态字典成对登记（17.7）：后端 `ProjectBudgetStatus`（entity）↔ 前端 `utils/dict.js BUDGET_STATUS_TEXT`，本记录第 5 节即为两侧文件成对清单。

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 备注 |
| --- | --- | --- | --- |
| 后端编译 | `mvn -q '-Dspring-boot.build-info.skip=true' -DskipTests compile`（backend/） | 通过（exit 0） | build-info 插件 gitShort 为 null 的既有环境问题，跳过该 goal |
| 估算单测 | `node --test frontend/src/views/project/components/budgetEstimate.test.js` | 7/7 通过 | 含 fallback 用例 |
| 后端预算单测 | `mvn ... -Dtest=ProjectBudgetQuoteServiceTest test` | 4/4 通过 | ProjectBudgetServiceTest 12/12 在全量跑中通过 |
| 后端全量测试（意外触发） | 首次 -Dtest 引号未加导致全量执行 | 除 QuoteServiceTest 1 个断言口径问题（已修复复跑通过）外全部通过 | 相当于全量回归证据 |
| 前端构建 | `npm run build` | 通过（34.48s） | chunk 体积警告为既有状态 |
| schema 校验 | `scripts/check-schema-parity.ps1` | 通过（init.sql=生产库=92 表，2026-11-26 第二轮复跑通过） | 该脚本只核表级，不核列/权限行；权限行以迁移执行后实测为准 |
| 收费标准权限行 grep | Select-String init.sql+migration 搜 `budget-fee-standards` | 迁移 1 文件 3 行 + init.sql 3 行成对 | 生产库 permission 行未连库实测（无凭据，且禁止执行生产库 SQL） |
| EMPLOYEE 403 实测 | — | 未执行 | 需发布后以 EMPLOYEE 账号实测三端点 403、config/phases与types 仍 200；PermissionService 规则有缓存（PermissionService.java:72-77），迁移后需刷新缓存或重启生效 |
| 残留 grep | ProjectBudgetQuotes / 5 个 API 名 / canRecord / generated / service.generate 等 | 仅剩注释级提及（历史说明） | 有意保留 |
| 0.06 字面量 | Panel 内 | 仅剩 `?? 0.06` 兜底与注释 | 有意保留（未保存草稿兜底，导出侧后端默认同值） |
| 生产库迁移执行 | — | 未执行 | 随发布流程，须先迁移后切后端 |
| 页面手工回归 | — | 未执行 | 需发布后验收：确认守卫、降级弹窗、移节确认、0 单价红点、库截断提示、导出下载 |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 发布 + 生产库迁移 | 未获发布授权 | 改动仅在代码库 | 走 release-full 流程，先 db-migrate 再切后端 | 负责人 |
| P2-⑦ 权限项 | 需口径决策 | 成本单导出可见口径未定 | 负责人拍板后另开任务 | 负责人 |
| P2-⑥ 收费标准权限 | 已完成（本轮实施，待发布验证） | 迁移未执行，EMPLOYEE 实测未跑 | 发布后按第 6 节实测 | 负责人 |
| P1-② 决策回溯 | 本轮按①删除；若业务要恢复客户确认入口 | 需重建 UI + 端点 | git 历史可回溯本次删除 | 负责人 |

## 8. 风险与回滚
- 已知问题：迁移非幂等（重复执行报 Duplicate column，人工忽略）；确认守卫会拒绝「全 0 数量」确认——线上骨架未点选即确认的旧行为被有意禁止（即 P1-① 修复目标）。
- 数据风险：低。version 列 DEFAULT 0，旧行为兼容；estimate_inputs ONLINE 持久化仅新增写入。
- 兼容风险：删除的 3 个端点若有外部脚本调用将 404（前端无消费方，已核实）；旧前端 + 新后端组合下保存不携带 version → 跳过乐观锁校验（兼容窗口内可接受）；新前端 + 旧库（未迁移）→ 保存报 SQL 错误，**必须先迁移**。
- 回滚步骤：代码 git revert 本次提交；数据库 `ALTER TABLE project_budget DROP COLUMN version`（列无外部引用，可逆）；权限回滚见迁移文末 DELETE 语句（按精确 path 删除 3 行，不影响其他规则）；操作日志记录不可回滚（无需回滚）。
- 豁免说明：后端 activeFeeStandards 不过滤 EST_（估算面板经 overview 读取 EST_* 配置，过滤会破坏估算参数读取；防线上限由前端库弹窗过滤承担）。

## 9. 文档同步
- [x] 架构文档（docs/architecture.md 2026-11-26 整改段）
- [x] 部署文档（docs/deployment.md 迁移小节、表数实测修正）
- [x] 数据库迁移（migration + init.sql 成对，parity 通过）
- [x] 测试说明（本记录第 6 节）
- [x] 最新交接摘要（docs/handover/README.md 已更新链接）
- [ ] 发布说明（随下次发布生成）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`（应只有本次整改 + 审查报告相关变更）；`mvn compile` 与 `npm run build` 均应通过。
2. 然后执行（获发布授权后）：release-full 流程，**先执行 20261126_budget_optimistic_lock.sql 再切后端**；健康检查后手工回归第 6 节页面清单。
3. 验收标准：空点选确认被拒（前端按钮禁用 + 后端 400）；两人并发保存后提交者收到「请刷新」409；移节/降级/0 单价三个弹窗行为符合文案。
4. 未经确认禁止：执行生产库迁移之外的任何 DDL；恢复已删除的报价端点；放宽收费标准权限规则。
5. 需要负责人决策：P2-⑦ 成本单导出可见口径；是否进入 1.0.158 发布（版本已 bump，未发布未 push）。
