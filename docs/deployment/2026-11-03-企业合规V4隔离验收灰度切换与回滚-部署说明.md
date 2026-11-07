# 企业合规 v4：隔离验收、灰度切换与回滚

本文件记录 2026-11-03 企业合规 v4 首期实现；已于21:44生产蓝绿上线并完成技术验证，业务体验待复核，见 [生产记录](releases/20261103-compliance-v4-production.md)。当前部署身份以提交/哈希及实际配置为准，历史 R-1 文字和旧 PID 不能作为现状。

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
3. 运行 `scripts/test-compliance-v4.ps1`。脚本先核实实际 DATABASE、端口、datadir，拒绝其他端点；项目配置快照、V4表和个人模式迁移各运行两遍，再执行服务回归和真实 MySQL 验收。项目配置快照迁移是为了让现有项目列表真实服务可读取验收项目；它不是本次规范解析功能新增的生产迁移。测试会清理该隔离库中指定 fixture 表，严禁换成生产连接。报告位于 `backend/target/surefire-reports`。
4. 独立复制 `backend/pom.xml`、`backend/src` 和父级 `version.txt` 到忽略构建目录；`mvn -DskipTests package`。不要占用或覆盖运行中的正式 JAR。
5. 启动候选 JAR，固定上述隔离连接和文件目录，随机 `JWT_SECRET`，启用 V4，关闭 v3/v4 定时调度与企微同步；健康检查通过后启动 Vite，`VITE_API_PROXY_TARGET=http://127.0.0.1:18086`。勿因测试端口改变而放宽生产 CORS 来源。
6. `LIVE_V4_ACCEPTANCE=acceptance_v4` 且 `V4_TEST_PASSWORD` 就绪后运行 `node frontend/tests/compliance-v4-live.mjs`（从 frontend 目录执行时为 `node tests/compliance-v4-live.mjs`）。可用 `PLAYWRIGHT_MODULE=file:///.../playwright/index.mjs` 指向本机安装。此脚本无 API 拦截，真实 UI/登录→Spring→MySQL；包含上传真实 PDF、生成并重复打开待确认草稿、核对页面原文引用及服务端 sourceAnchor、项目关联、个人执行与跨用户拒绝。不要复用已被其他任务改写的 fixture，先跑步骤 3 重建基线。
7. 旧夹具回归仍用 15173：`tests/compliance-v4-browser.mjs`、`tests/project-config-browser.mjs`。这两项仅作为前端回归，不能替代上一步。

## 6. 发布前验证与回滚限制

必须另行核对目标生产元数据、实际版本、配置差异、备份/恢复、候选健康和权限；完成员工业务复核后审批不停机蓝绿切换。现有 `scripts/deploy-backend.ps1 -GracefulSwitch` 和前端平滑切换脚本只作为后续工具，不能将未执行描述为成功。

回滚优先关闭新版写入/调度并切回兼容入口，保留 `compliance_user_mode`、历史快照、所有执行和提醒记录；不能删除个人模式行来“恢复旧版”。早于本轮、不了解用户模式的旧二进制会重新发送 V3 提醒，**不能直接回滚到该二进制且继续开启旧调度**。如必须回旧二进制，需先停止两套合规调度并保留只读能力，另行批准旧个人写入恢复/数据补偿方案。数据库有业务数据时禁止 DROP 新表。

验收证据与当前交接见 [发布阻断闭环](handover/tasks/20261103_compliance_v4_release_blockers.md)。生产发布、用户逐人切换数量和全员业务验收不在本次“隔离通过”结论中。

## 7. 生产发布准备更新（2026-11-03）

最初 UAC 被取消及 WinPS5.1 HTTP 预检兼容问题已处理，21:44完成蓝绿/前端切流；两个 V4 生产开关均为true，个人mode行数仍为0，员工自行明确启用。部署影子同时关闭两套调度，使用 `-GracefulSwitch -DisableComplianceSchedulersOnRollback`；后者只在自动回退路径关闭两套调度。真实发布、登录态只读验证与局限见 [生产记录](releases/20261103-compliance-v4-production.md)，不能把 DryRun 文案当成上线证据。

部署 GET 共用 `scripts/deployment-http.ps1` 的本机 HTTP/1.0 探测，规避已复现的 WinPS5.1 HTTP/1.1 读取失败；不改变浏览器/业务协议。`deployment-artifact.ps1` 验证候选及回滚包具有启动清单和Boot依赖，坏包拒绝发布；`-RollbackJarPath` 用于指定经审批的可启动基线。本次发现归档的旧磁盘小包不可启动，已标为禁止回滚并额外保存完整已验证基线；日常回退优先保留当前兼容后端，关闭新版/切回兼容前端。禁止在正式 NSSM 的 `backend/target` 执行 `mvn package/clean`，必须隔离构建。

运维回归：`powershell.exe -NoProfile -File scripts/tests/deployment-http-regression.ps1`（只读 health/匿名/前端）；`scripts/tests/deployment-artifact-regression.ps1 -NonExecutableJar <经核实的坏包路径>`（包结构接受/拒绝）；完整 DryRun 与坏回滚源拒绝均已实测。上述护栏补正没有再次切流。

WinPS5.1执行SQL原生管道前必须将 `$OutputEncoding` 设为UTF-8，`--default-character-set=utf8mb4` 不能替代上游管道编码；本次新表注释曾出现问号，已按源迁移修正并回读。隔离迁移脚本已设置并在结束时恢复编码；生产执行器同步处理。备份脚本通过mysqldump原生`-r`写文件，不受此管道问题影响。


## 4. 规范文件生成草稿（2026-11-05）

新增 `POST /api/compliance/v4/documents/{documentId}/templates/generate-draft`，仅接受已上架的 `enterprise-compliance` 文档；上传者或 `knowledge:maintain` 维护人可生成。接口从知识文档存储读取原件，以 V4 草稿版本保存候选节点，不发布。每条节点的 `sourceAnchor` JSON 保存页/段/表格位置、原文摘录、内容类型及 `needsReview=true`。响应含 `candidateCount`、`extractionIncomplete`、`extractionWarning`。前端模板维护页显示警告及原文摘录，维护人仍须人工核对并手动发布。

结构化提取保留 PDF 页、DOCX 正文段落/表格行、PPTX 幻灯片、XLS/XLSX 工作表行。PDF 页少于20个文字字符时提示可能为扫描/图片页，并尝试调用本地 Tesseract OCR（简体中文+英文，250 DPI）；OCR失败或结果仍过短时保留扫描页提示。配置 `TESSERACT_CMD`（缺省按 PATH/Program Files 查找）和可选 `TESSDATA_DIR`。识别文字是候选，仍须人工对照原页；不保证准确。Windows可从[UB Mannheim发行页](https://github.com/UB-Mannheim/tesseract/wiki)安装Tesseract；简体中文模型来自[tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast)，Apache-2.0。Windows当前开发机实测 Tesseract 5.4.0 与 chi_sim 模型安装于当前用户本地数据目录；Windows服务账号需要能读模型文件，部署时应把模型置于服务账号可读的本地目录并设置 `TESSDATA_DIR`。当前正式后端服务身份为 LocalSystem，交互用户 LocalAppData 模型不可被当作正式可用模型；本次尚未变更正式服务环境或模型文件。行/段候选是待确认草稿；单次最多200条候选。相同文档版本按草稿标题复用既有解析草稿以避免常规重复点击；并发请求尚无数据库唯一约束，仍需后续补原子幂等护栏。接口暂无异步任务。

## 7. 本机真实数据库与 Edge 验收记录（2026-11-05）

执行 `scripts/run-compliance-v4-e2e.ps1`：同一 PowerShell 控制进程为新隔离 datadir 生成随机 MySQL root、员工测试账号和 JWT 凭据，临时 init SQL 删除后再经子进程环境传递；schema 导入前核实 `127.0.0.1:13316/acceptance_v4` 与沙箱 datadir。V4/项目快照迁移重复执行、七组服务/数据库测试及真实 Edge → Vite → 候选 Spring → 隔离 MySQL 流程通过。浏览器上传了 `E:\王创\2026-11-4\北京融和医学发展基金会合规要求.pdf`，验证解析草稿可见页码/摘录引用、每个候选均有 sourceAnchor，重复生成复用同一草稿；随后验证新版清单编辑/发布、项目关联、用户隔离、完成/重用/旧历史。运行产物和截图位于忽略目录 `.build-compliance-v4-acceptance/`。本记录仅为本地隔离验收，不代表生产部署。

2026-11-05补充：并发生成事务护栏及真实Edge并发复验通过。管理员发布尝试已配置ProgramData OCR路径并完成备份，但Nginx SYSTEM平滑重载任务查询失败/超时，发布FAILED，未认定上线；正式8080和首页健康200。详见任务交接追加记录和.build-compliance-template-production-20261105证据。后端发布退出码门禁已补入专用编排。

生产最终记录见 [20261105发布记录](releases/20261105-compliance-template-production.md)：已切流但严格不停机探针失败，保留告警。并发生成已通过事务用户/文档行锁串行，替代此前无并发护栏描述。

可视化维护施工新增GET/PUT /api/compliance/v4/documents/{id}/template-workspace（尚未部署）：仅上传者/维护人，PUT需要content和expectedRowVersion，冲突409。增量迁移20261105_compliance_template_workspace.sql未执行；禁止在未迁移环境启用新入口。完整生效/迁移/回滚仍在施工，见施工交接。

### 可视化维护阶段二（未部署）
workspace新增GET legacy-candidates、POST select-existing与activate，后两者需要expectedRowVersion及requestId（16～64字母数字连字符）；select-existing另需selectedId。服务端检查维护权限，工作区保存与生效分离，旧执行快照不改。已收口文档的新建入口限制唯一active模板。前端阶段卡片/自动保存/原文核对和生效已接入；候选文件提取合并尚未接入新编辑器。具体边界与测试见施工交接，不能视作完整需求验收。

阶段二技术验证更新：V4_VISUAL_EDITOR=true运行run-compliance-v4-e2e.ps1退出0，真实隔离MySQL迁移/服务测试与Edge卡片编辑→自动保存刷新恢复→生效通过；旧执行快照不变用例通过。本轮仍未生产迁移/部署。证据与未完成范围见handover/tasks/20261105_compliance_visual_editor_implementation.md。

## 工作区候选与来源核对接口（设计施工已实现，尚未生产部署）
在/api/compliance/v4/documents/{id}/template-workspace下：POST extract提取当前原件，GET extractions/{extractionId}读取绑定版本结果；POST merge-candidates需要expectedRowVersion、requestId、extractionId、actions[{candidateId,targetCode可空}]；POST review-node需要expectedRowVersion和nodeCode，记录当前事项内容核对；POST confirm-extraction需要extractionId及confirmed:true，记录漏页人工补齐确认。权限均复用来源上传者/维护人；生效仍限维护人。旧来源版本409，无权来源拒绝；保存/生效校验重复ID、类型、长度、选项与真实来源候选。相同生效内容无操作返回，不生成重复历史。

迁移扩展为四张工作区/请求/提取/核对表；只在独立MySQL验收，生产未执行。文件目录必须通过启动参数显式覆盖至沙箱，不能仅隔离数据库。2026-11-05脚本已修正并完成一次包含真实上传的重跑；早期一次测试文件落正式文件目录的情况已记入交接，不清理未知文件。复现用V4_VISUAL_EDITOR=true运行scripts/run-compliance-v4-e2e.ps1，证据及剩余边界见施工交接阶段三。

## OCR 事项分组与规范文档库筛选

本轮复用现有本机 Tesseract 和简中/英文模型，不增加环境变量、OCR 服务或数据库迁移。受支持的 PDF 表格按行生成候选，仍需对照原件核对数值、比较符号及漏提取位置。发布后在隔离环境检查原文提取、旧版页签移除、收藏与关键词组合筛选及取消收藏后的分页刷新。

### 隔离验收复跑

在仓库根目录设置 `$env:V4_VISUAL_EDITOR='true'`，执行 `./scripts/run-compliance-v4-e2e.ps1`。脚本启动本地隔离 MySQL、Spring、Vite 与真实 Edge，凭据在同一进程生成并传递。新增断言覆盖基金会 PDF 第 2 页打开及表格引用持久化、两页收藏列表响应、浏览器筛选和取消收藏、员工启用新版后的页签列表。
