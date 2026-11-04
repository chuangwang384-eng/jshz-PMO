# 项目配置 v3：接口、数据字典、隔离验证与发布步骤

PC-20261104，2026-11-04。开发及隔离技术验收已完成，待业务验收；生产未迁移、未部署。业务规则以[需求](20261104-项目配置易用性与版本隔离-需求文档.md)为准，方案以[设计](20261104-项目配置易用性与版本隔离-设计文档.md)为准。

## 1. 实际实现与兼容范围

`ProjectConfigurationService` 负责配置聚合、预览、排序、幂等及快照；项目服务的详情/列表名称、阶段推进/回退、资料阶段校验和导出读取项目快照。前端图形和表格视图共用三步向导；排序维护提供拖动和上下移动。项目编辑不能换型或绕过推进接口改变阶段；预算 0 在页面完成提示和服务端判断均算已登记。

配置模式 INHERIT / CUSTOM / NONE 明确区分，CUSTOM 全选填不会应用通用必填。类型定义保存模式、顺序及资料要求；创建项目解析当时公共阶段/材料元数据和通用要求，再冻结名称、类别、顺序和必填规则。普通运行读取不回查字典覆盖这些字段。

旧类型 POST/PUT、类型流程 PUT、类型专属绑定 PUT 通过兼容适配层转换为完整定义的新版本，复用同一事务锁、排序与权限校验。旧创建请求将当时启用阶段固化为显式流程（不会跟随以后新增阶段）；旧名称/位置更新保留阶段和三模式；非空材料绑定明确转 CUSTOM。空流程、空专属绑定无法表达新含义，返回引导升级的业务错误，不再隐式回退。sort=0 的旧创建默认末位，旧更新映射第1位；其他位置按新规则校验。

旧客户端每个接口调用原子提交，但原先多次调用组合不具备新版向导整套原子性和客户端幂等保障，仍建议迁移新版聚合接口。所有可见类型编辑入口已统一向导；通用材料维护、阶段/模板字典维护保留。全部配置写入在服务内明确执行原 project:manage/管理员规则，不依赖缺失数据库接口规则时的默认放行。

## 2. API 与权限

前缀 `/api/project`；返回沿用 ApiResponse。冲突以业务 code=409 表达，HTTP 状态遵守全局异常处理；客户端不可仅看 HTTP 200。非法参数为业务拒绝，鉴权失败 code=403。

| 方法及路径 | 输入 / 输出 | 权限 |
| --- | --- | --- |
| GET /config/definition-context | epoch、当前类型顺序、通用要求、阶段引用数量 | 服务内显式 project:manage（沿用现有管理员豁免） |
| GET /config/types/{id}/definition | 当前定义、展示位置、epoch、解析效果 | 同上 |
| POST /config/definitions/preview | name、position、expectedEpoch、phases；返回有效清单，不落库 | 同上 |
| POST /config/definitions | 上述字段加 requestId，创建完整类型及新公共阶段 | 同上 |
| PUT /config/types/{id}/definition | 上述字段；发布新版本 | 同上 |
| PUT /config/types/order | ids 全集、expectedEpoch；原子排序 | 同上 |
| GET /{id}/configuration | 项目固定快照 | 登录且既有项目级读取权限，独立于管理权限 |

phases 每项：phaseId（新增时 null）、name（新增时必填）、mode、requirements。新阶段 name 不超过实际字段长度 50；CUSTOM requirements 至少一项，其他模式须为空。requirements 每项 templateId、required=0/1，顺序就是数组顺序。类型 position 为 1～N；新建 N 为创建后的类型总数。

同一 requestId、相同语义请求可以重试并返回原结果；JSON 字段顺序不影响摘要。旧读响应保留关系id、typeId、phaseId等兼容字段；项目快照冻结其来源信息，业务关联仍使用phaseId/templateId。同一键不同请求拒绝。epoch 过期不覆盖已有改动。幂等记录首期保留，不自动删除；后续如需清理须制定重试有效期与受控维护策略。

## 3. 数据字典与锁

四张新表均 InnoDB / utf8mb4：

| 表 | 主键/唯一约束 | 关键字段 |
| --- | --- | --- |
| project_config_state | id=1 | epoch：所有配置写入和新建项目快照使用同一行锁 |
| project_type_config_revision | id；唯一(type_id,revision) | schema_version=1，definition JSON，不可变发布版本，created_by/at |
| project_config_snapshot | project_id | source_revision_id、schema_version、effective_config JSON、config_hash SHA-256、origin=CREATED/LEGACY_BASELINE |
| project_config_request | request_id | payload_hash、result JSON、created_at，用于持久化重试 |

project_type 新增 active_config_revision_id，sort 最终非空且唯一（uk_project_type_position）。版本和快照不级联删除，以保留历史；阶段/模板删除检查历史引用。项目创建使用 READ_COMMITTED 事务，取得配置锁后解析并写入项目与快照，避免先读类型建立旧一致性视图后等待锁的问题。

原子排序先移到不冲突的临时正数区间，再写最终 1～N；事务提交前不会暴露中间序列。检查 INT 溢出。所有配置字典写入共同持锁并更新 epoch；项目普通运行不用该全局锁。

## 4. 迁移操作顺序（尚未用于生产）

1. 只读核实目标库元数据、配置调用方；备份数据库与应用、确认恢复演练。关闭配置维护及新建/换型写入口，停止旧版本后台写入；普通查询可继续。此兼容窗口不等于停机。
2. 执行 `database/migration/20261104_project_config_snapshots.sql`，只扩展结构，可重跑。
3. 在隔离构建副本生成类及依赖 classpath，使用离线入口 `com.jshz.expert.tools.ProjectConfigurationMigrationMain --preflight`。入口不启动 Spring Web，不由正常应用启动触发，不开放 HTTP 迁移接口。
4. 通过受控进程环境提供 PC_MIGRATION_URL、PC_MIGRATION_DATABASE、PC_MIGRATION_USER、PC_MIGRATION_PASSWORD。URL 指明真实目标，入口核对 SELECT DATABASE()；凭证不得放到命令行或文档。
5. 审阅报告 anomalies、optionalFallbackDifferences 和 flowOrderDifferences。异常阶段/悬空引用须先按批准方案修复；存量仅能冻结此时规则。确认执行时设置 PC_MIGRATION_APPROVED_DATABASE 与目标一致，运行 `--apply`；有全选填回退或公共顺序差异时，逐项核准后额外显式传 `--review-legacy-differences`。数据回填在一个事务内，失败全部回滚。此环境变量不是替代生产审批的授权。
6. 校验所有项目有快照、原始材料/负责人/日志不丢失、hash 一致、类型流程显式化、位置连续无重复后，执行 `20261104_project_config_finalize.sql` 加唯一索引。DDL 隐式提交，不能当作同一回填事务的一部分。
7. 切换快照兼容前后端；验证登录、权限、列表、旧/新项目、推进、回退、材料和排序；再开放维护及创建入口。记录最终提交号、制品 hash 和双路探针。发布及生产迁移需要另行批准。

离线运行 classpath 示例（均在隔离构建目录）：先 `mvn -DskipTests compile dependency:build-classpath -Dmdep.outputFile=classpath.txt`；再用 `java -cp` 指定 `target/classes` 与该文件列出的依赖，主类如上。Spring Boot 打包明确指定 ExpertSystemApplication 为应用主类，新增离线入口不改变正常启动入口。

## 5. 回滚与风险

保留支持快照的新后端，关闭维护入口是优先回退方案。旧后端不理解 NONE 和项目固定顺序，不能无条件恢复旧包继续写入。不能用“删新表”回滚已有项目；整库恢复会覆盖其他业务写入，须单独审批与补偿。发布前准备快照兼容回退构建并演练，未经验证不宣称蓝绿发布可无损退回旧代码。

存量非相邻公共 sort、全选填回退差异、已删除类型引用、历史材料阶段悬空均是必须盘点的重点；异常清单记录 ID 和原因即可，不导出材料正文。当前未核实生产数据是否存在这些异常。

## 6. 可复现隔离测试

本次独立实例：MySQL 127.0.0.1:13317 / pc_acceptance；初轮 datadir 为 `.build-project-config-acceptance/mysql-data`；最终兼容复验重新初始化 `.build-project-config-acceptance/mysql-data-final`，仅导入仓库测试结构基线和虚构数据，不导入真实业务行。后端 127.0.0.1:18087，Vite 127.0.0.1:5173；调度与外部联系均禁用，文件目录全部覆盖到隔离目录。

空实例先由本机管理员在仅回环监听的独立 datadir 初始化并设置临时随机凭证、创建 pc_acceptance 库（不要复制业务行或复用生产凭证）。测试基线结构已随仓库提供 database/testing/project_config_baseline.sql，无需依赖历史 .build 目录。

设置 PC_ACCEPTANCE_DB=pc_acceptance 和 PC_DB_PASSWORD 后执行 `scripts/test-project-config.ps1`：检查数据库名、端口和数据目录，导入测试结构基线，迁移脚本重跑两轮，在隔离构建副本运行 ProjectConfigurationAcceptanceIT 及既有三组回归。测试会清理该专用库的测试表，禁止改为生产连接。不要直接在生产使用的 backend/target 运行 Maven。

前端组件：在 frontend 执行 `node tests/project-config-regression.mjs`。真实浏览器：先生成 prepareBrowserFixture（PC_BROWSER_FIXTURE=yes、PC_TEST_PASSWORD 为临时随机值），启动以上隔离服务，再执行 `node frontend/tests/project-config-v3-live.mjs`，PLAYWRIGHT_MODULE 指向本机可用 Playwright。脚本不 mock API；实际使用 Edge→Vite→Spring→MySQL。

构建输出、原始日志与截图在忽略目录 `.build-project-config-acceptance/`；交付汇总证据另行归档到 docs 下。业务负责人易用性验收仍需首次使用者执行需求 A5，不用开发者自动化代替。
