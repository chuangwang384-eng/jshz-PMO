# PC-20261104 项目配置 v3 施工交接

## 1. 任务身份与状态

- 主责：Codex /root；时间：2026-11-04，Asia/Shanghai。
- 状态：已完成待验收。已实现并完成隔离技术验收；首次使用者的业务易用性复核尚未执行；未部署生产。
- 需求人已明确批准方案和施工；本任务不含生产迁移、生产发布、外部 AI 或复杂审批流。

## 2. 目标与验收入口

- [需求](../../20261104-项目配置易用性与版本隔离-需求文档.md)：D1～D4 为用户确认规则，A1～A9 为验收条件。
- [设计](../../20261104-项目配置易用性与版本隔离-设计文档.md)：快照、三模式、排序、并发与兼容。
- [实施/API/数据字典/迁移/回滚说明](../../deployment-project-config-v3.md)。
- [自动化证据目录](../../testing/evidence/20261104-project-config-v3/)：后端统计、真实浏览器、空库结构引导、离线迁移重跑、清理结果、制品信息和页面截图。

| 验收项 | 当前结论 |
| --- | --- |
| A1 就地新增与完整保存/取消 | 真实 Edge→Vite→Spring→MySQL 通过；没有阶段独立 POST 或半成品记录。 |
| A2 新阶段不影响其他类型 | 隐式流程迁移测试及浏览器基线类型阶段数量检查通过。 |
| A3 连续唯一排序 | 数据库唯一约束、插队/删除/并发与真实页面移动通过。 |
| A4 三模式/全选填不回退 | 真实数据库及页面重开校验通过。 |
| A5 首次使用引导 | 自动化完成配置/示例/预览操作；首次使用者人工业务复核未执行。 |
| A6 新旧项目隔离 | 公共名称/停用/通用要求/类型改动的数据库测试通过；浏览器旧快照与新项目顺序对照通过。 |
| A7 页面和后台一致 | 当前阶段材料、预算0、附件/文本分流、固定顺序推进/回退通过；页面推进后自动显示新阶段。 |
| A8 失败/重试/权限 | 原子回滚、并发、幂等、初始化/预览时序、普通用户与匿名接口拒绝通过。 |
| A9 存量迁移 | 数据回填重复执行、异常阻断、规则差异复核、旧快照 hash 保持及空库结构验证通过；生产数据未预检。 |

## 3. 项目基线与交付状态

- 工作分支：codex/fix-review-and-config-takeover；施工源基线：8c49755。
- 实现提交：b189a7017d4cad46731a587955e9a95fb1554c9a（b189a70，含代码、测试和文档）；后续交接补充提交以git log核实。
- 接手前已有 docs/deployment.md 的4条 R-1 历史追加，以及未跟踪 .bdv4b.txt / tv.txt，均保留。仅将本任务的 deployment 增量纳入交付，不混入上述历史改动。
- 前端现有 Vue/Vite、后端 Java17/Spring Boot3.2.5；本次未增加运行依赖。未提前修改生产版本号。
- 隔离 MySQL 26.7.0，13317 / pc_acceptance（最终 datadir=mysql-data-final，完全用仓库结构基线引导）；后端18087、前端5173，仅回环监听。运行的后端为本次隔离 Maven 打包的可执行 JAR，含72个 BOOT-INF/lib 依赖。
- 生产 backend/target/expert-system.jar 哈希仍为 DDB6D4EB914747EEE322AD8465BA4EE6D0EC1F65A63ECC63AF6C2D58C1F0E301，未改生产文件或配置。

## 4. 实际变更

| 模块 | 文件及作用 |
| --- | --- |
| 后端配置 | ProjectConfigurationService：原子定义、模式解析、管理权限、唯一排序、持久幂等、项目快照及校验。 |
| 项目运行 | ProjectService：项目创建同时冻结配置；详情/列表/资料/推进回退/导出使用快照；普通编辑禁止换型和绕过推进改阶段。 |
| HTTP | ProjectConfigurationController及ProjectController旧入口适配/管理校验：定义上下文、编辑、预览、聚合保存、排序、资源级授权快照读取。 |
| 迁移 | ProjectConfigurationMigration、tools/ProjectConfigurationMigrationMain：离线预检和事务回填，无自动启动迁移/HTTP迁移入口。 |
| SQL | 20261104_project_config_snapshots.sql、20261104_project_config_finalize.sql；另有纯结构隔离测试基线 database/testing/project_config_baseline.sql。 |
| 前端 | StepWizard、ProjectConfig/store/图形组件：就地新建、逐阶段确认、三模式、员工预览、唯一排序和统一编辑入口；ProjectEdit/Detail：固定项目规则、材料完成提示、推进后阶段展示。 |
| HTTP兼容 | projectConfigRequestId.js 使用 getRandomValues 生成32位请求标识，内网HTTP不依赖仅安全上下文提供的 randomUUID。 |
| 构建 | backend/pom.xml 明确应用 mainClass，新增离线主类不改变正常可执行包入口。 |
| 验证 | ProjectConfigurationAcceptanceIT、前端组件回归/真实浏览器脚本、scripts/test-project-config.ps1。 |

旧分步类型写接口适配为完整定义的新版本；仅空流程/空专属绑定明确引导新版选择。通用阶段/模板维护继续可用；删除增加历史引用保护。详细接口和兼容限制见实施说明。

## 5. 真实执行的验证

| 命令/步骤 | 结果与证据 |
| --- | --- |
| scripts/test-project-config.ps1（PC_ACCEPTANCE_DB=pc_acceptance，凭证通过临时环境注入） | 19项本轮真实MySQL回归 + 7项既有回归通过，0失败；1个仅供浏览器准备数据的测试按设计跳过。backend-tests.json。 |
| Maven -Dtest=ProjectConfigurationAcceptanceIT#prepareBrowserFixture test | 单独准备虚构账号/RBAC通过，没有复用生产账号或密码。 |
| Maven package dependency:build-classpath（隔离副本） | 通过；可执行入口与72个依赖检查通过；实际java -jar启动及 /ready=200。 |
| node frontend/tests/project-config-regression.mjs | 图形/绑定状态、NONE/全选填、初始化、过期预览、响应丢失重试键及HTTP随机标识通过。 |
| npm run build -- --outDir ../.build-project-config-acceptance/frontend-dist-final-reviewed | 通过；保留既有大chunk提示，无构建错误。 |
| node frontend/tests/project-config-v3-live.mjs | 真实Edge、无API mock；登录、新增/取消、三模式/预览/保存/重开、排序、真实上传和两次推进、新旧项目规则隔离、无权限直调、表格视图通过；pageErrors=[]。 |
| ProjectConfigurationMigrationMain --preflight / --apply | 离线入口通过；对已版本化数据重跑后快照hash不变。migration-cli.json。 |
| 空库引导 | 纯结构基线 + 两个DDL脚本在新的隔离空库执行通过，28表219列的类型/可空/默认值与验收库一致，无业务数据导入。bootstrap.json。 |
| git diff --check、文档相对链接检查 | 通过；CRLF转换提示不是校验失败。 |

前几轮发现并修正向导初始化覆盖输入、过期预览响应可重新显示、内网HTTP随机标识兼容问题；最终测试没有跳过业务断言。JSON幂等测试比较改为忽略对象字段顺序，并额外验证不同字段顺序下同键可重试。

## 6. 数据库/API/权限/配置影响

- 四张新表、一个类型版本指针、sort非空唯一约束；必须先完成离线预检/回填，再加索引和切换运行链路。
- 旧隐式全阶段流程显式化；迁移只能冻结迁移当时规则，不能伪造项目初建配置。公共sort/全选填差异要逐项人工复核，异常项目阻断迁移。
- 新管理接口服务内校验project:manage；项目快照接口使用现有项目级可读权限。没有扩大原项目可见范围或更改公共认证机制。
- 正常启动不新增迁移环境变量要求；仅离线命令使用PC_MIGRATION_*，见实施说明。

## 7. 未完成、风险与回滚

- 人工业务复核：负责人按需求A5进行首次使用者验收。
- 发布前：只读盘点生产元数据、历史异常和旧接口调用方；准备备份与支持快照的兼容回退构建，并另行申请迁移/蓝绿发布审批。
- 旧客户端的空流程/空专属绑定请求会明确失败；原多请求组合没有新版向导的整套原子性和客户端幂等保障，发布前仍须盘点并建议升级。
- 旧后端无法解释NONE/快照流程，不可直接无损回滚旧包；保留快照兼容后端关闭维护入口为优先回退方向，具体演练仍未执行。
- 未执行生产迁移、生产健康验收、生产切流或生产数据修复。
- 源码可用本次Git提交回退；数据库已写入新规则后不能以删除新表代替数据补偿。

## 8. 文档同步

已同步需求、设计补充、既有术语表/ADR-006、architecture、deployment（仅本任务增量）、独立API/数据字典/迁移/测试说明、原设计交接入口和最新交接摘要。无单独生产发布说明，因为尚未申请或执行发布。

## 9. 环境清理

三个测试监听（13317/18087/5173）均已停止，数据库使用mysqladmin在确认身份后平滑关闭；临时数据库/账号密码文件已删除，框架生成但未使用的默认密码已从原始日志脱敏。忽略目录保留构建、虚构资料、原始日志和测试datadir作证据。临时凭证不再可用，下次复现请重新初始化专用隔离实例并注入新的随机凭证，不得复用生产凭证。详见teardown.json。

## 10. 下一位 Agent 的第一步

1. 阅读根AGENTS、本记录、需求/设计/实施说明；运行git status和git log核对实现提交与其他人未提交改动。
2. 从docs/testing/evidence/20261104-project-config-v3核对实际技术验收结果；先由负责人开展业务易用性复核。
3. 获得后续发布任务时，先做目标库只读元数据与迁移预检，记录异常/差异和预计影响；不因本轮“方案通过”直接发布。
4. 重新验收时使用新的本地隔离实例、仓库测试结构基线和test-project-config.ps1；不能在生产backend/target打包。

## 11. 禁止事项与待确认

禁止覆盖接手前R-1历史改动、.bdv4b.txt、tv.txt或其他人新出现的工作；禁止未经批准执行生产DDL、回填、切流、回滚或旧包恢复。生产存量异常和公共顺序差异如何修复须由负责人结合预检报告决策，不得自动插入阶段或改写业务历史。
