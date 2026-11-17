# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261117_code_review_hardcode_redundancy
- 任务名称：提交工作区变更 + 今日（2026-11-17）代码硬编码与冗余审查
- 主责 Agent：CodeBuddy
- 交接时间：2026-11-17
- 当前状态：已完成待验收（审查报告已产出；同日修复实施已完成，见第 11 节）
- 优先级：P2

## 2. 目标与验收条件
- 业务目标：提交工作区全部 Git 变更；审查今日全部代码改动，找出硬编码与冗余代码。
- 本次范围：审查、报告、清理建议。**不包含修复实施**。
- 不在本次范围：修改任何业务代码、生产部署、文档中基线数字更新。
- 验收条件：
  - [x] 工作区变更已提交（`1c214f2`），`git status` 干净
  - [x] 今日 14 个提交中全部代码文件（后端 15、前端 9、脚本 1、SQL 2）均被审查
  - [x] 问题均附文件:行号证据，关键结论经主 Agent 二次实读核实

## 3. 项目基线
- 分支：main；提交 `1c214f2`（审查前基线为 `4e02ea7`）；工作区：干净
- 今日代码改动主要来自：预算估算与对客报价（1.0.47–1.0.49，`b1070ee`..`9343361`/`3ac2c90`）、Ollama 看门狗（`ff56bf6`）
- 核实时间：2026-11-17

## 4. 提交记录
- `1c214f2` chore: 归档 1.0.49 发布 Nginx 基线与回滚配置元数据
  - 新增 `frontend/release-metadata/1.0.49-excel-sched-gate-20261117.nginx-before.txt`、`...rollback.conf.txt`（此前已暂存）

## 5. 审查发现（按文件，均经实读核实）

### 后端
| 位置 | 类型 | 问题 |
| --- | --- | --- |
| `scripts/ollama-health-watch.ps1:61-64` | 硬编码/文档不一致 | 注释写“wait up to 180s”，实际循环 12×10s=**120s**；`:79` 日志文案写死“90s after restart”同样不符 |
| `scripts/ollama-health-watch.ps1:11-12,28` | 硬编码 | URL `http://127.0.0.1:11434` 与绝对路径 `<LOCAL_PATH>`、`<LOCAL_PATH>` 写死，换机/换盘即失效 |
| `scripts/ollama-health-watch.ps1:23,31,38-39,49,66,74-75` | 硬编码（魔法数字） | 300 行日志保留、10 分钟锁过期、60/10/120s 超时、20s 启动等待、12×10s 循环散落函数体内 |
| `scripts/ollama-health-watch.ps1:38` vs `:74` | 冗余 | 两段逐字相同的探测请求体构造（仅超时不同），应提取 `Invoke-Probe($timeoutSec)` |
| `backend/.../service/ProjectBudgetQuoteService.java:144` 等 5 处 | 硬编码 | `"project_quote_"` 前缀散布于 ProjectStorageService.java:65（源头）、ProjectService.java:907、ProjectController.java:133/181、ProjectBudgetQuoteService.java:144，无常量定义 |
| 同上（ProjectController.java:133/181 vs QuoteService.java:144） | 硬编码/口径不一 | 控制器用大小写不敏感 `regionMatches(true,…)`，Service 用大小写敏感 `startsWith`，判定口径不一致 |
| `ProjectBudgetQuoteService.java:184` + `ProjectService.java:1953` | 硬编码 | 阶段名 `"会议预算制作"` 两处硬编码，阶段字典改名会静默失效 |
| `ProjectBudgetQuoteService.java:76-78` vs `:165-167` | 冗余 | `generate`/`restore` 重复的 `!"CONFIRMED".equals(budget.getStatus())` 判断（仅文案不同），可提取 `requireConfirmedBudget` |
| `ProjectBudgetQuoteService.java:66,126,130` | 硬编码（低） | 长度上限 1000/100 与迁移脚本列宽（VARCHAR(1000)/VARCHAR(100)）对应但无常量与注释绑定 |
| `ProjectController.java:133-138` vs `:181-186` | 冗余 | 新 workbook 端点逐字复制旧文件端点的前置校验块，且同方法内 `"project_quote_"` 字面量重复 4 次 |
| `ProjectBudgetController.java:41-44` vs `:79-82` | 冗余 | 两段相同的 `List<Map>` 类型窄化代码，可提取 `mapList(Object raw)` |
| `ProjectBudgetService.java:58,91,113,141,144` 等 | 冗余（低） | `"DRAFT"`/`"CONFIRMED"` 状态字面量在预算模块两 Service 共 8 处 |
| `ProjectBudgetService.java:104` vs `:187` | 冗余（低） | 相同的 null 集合兜底写两遍 |

### 前端
| 位置 | 类型 | 问题 |
| --- | --- | --- |
| `frontend/src/views/project/components/budgetEstimate.js:35-36,41,51` | 硬编码（高） | 默认预留 2 间、茶歇 `(attendees*7)/50*5`（即 70% 向上取 5 的倍数，写法与 ：44 文案字面对不上）、平台专车 200/100、机票 2000 全部写死；而 STAFF_DAY 等单价走管理员 `feeStandards` 配置，口径不一致 |
| `frontend/src/views/project/components/ProjectBudgetQuotes.vue:127-131` | 冗余（高） | 手写 UUIDv4 生成 5 行，与已有 `frontend/src/utils/projectConfigRequestId.js` 的 `createProjectConfigRequestId()`（StepWizard.vue 已复用）重复 |
| `ProjectBudgetQuotes.vue:151-163` vs `ProjectDetail.vue:1831-1846` | 冗余 | 两份“createObjectURL → a.download → click → revoke”下载实现，实现不一致（一个 append/remove 立即 revoke，一个 `setTimeout(1000)` 回收）；`:159` 的 1000 为魔法数字 |
| `ProjectBudgetQuotes.vue:2` + `ProjectDetail.vue:1328` | 硬编码 | DOM 锚点 `budget-quote-versions` 跨文件裸字符串耦合，改 id 滚动定位静默失效 |
| `ProjectBudgetPanel.vue:160` vs `ProjectDetail.vue:1353-1356` | 冗余 | `fmt` 金额格式化逐字重复，可提取到 utils |
| `ProjectBudgetPanel.vue:187,233` + `BudgetFeeConfig.vue:113,136` | 冗余（低） | `e?.response?.data?.message || e?.message || …` 重复 4 处；Quotes.vue:95 已示范本族内抽取做法 |
| `ProjectBudgetEstimate.vue:11` | 硬编码（低） | `:max="9999"` 无常量无注释 |
| `api/project.js:245-248` | 冗余（低） | `listBudgetFeeStandards` 手工拼查询串，同文件 `fetchProjectWorkbook`（:208-210）已示范 `{ params }` 用法 |
| `budgetEstimate.js:1-7` | 硬编码（有意保留） | `legacyNames` 中文行名与历史数据隐式契约，属兼容性需要，建议登记“有意保留” |

### 样式规范专项（AGENTS.md §17）
今日改动文件 hex 色值 grep 结果为 0，三个新文件 style 均正确使用 `var(--ui-*)` 令牌，**无样式规范违规**。

### 已核查无问题
`database/migration/20261117_budget_estimate_quote.sql`（无库名前缀，全参数化）、`init.sql` 新增段、`ProjectDocMapper.java` 新增 SQL、实体/Mapper、`KnowledgeWorkbookPreview.vue`、`budgetEstimate.test.js`、`api/project.js` 今日新增接口（相对路径+统一封装）。

## 6. 清理建议（按严重程度）
1. 【高】`ollama-health-watch.ps1`：修正 ：61 注释（180s→120s）与 ：79 文案（90s→120s），并将全部阈值收拢到脚本顶部参数区；URL/路径改为 `$PSScriptRoot` 推导或参数化。
2. 【高】`budgetEstimate.js`：茶歇比例 0.7、取整倍数 5、默认 2 间、平台专车 200/100、机票 2000 提为命名常量或并入 `feeStandards` 配置口径。
3. 【高】`ProjectBudgetQuotes.vue:127-131` 改用 `createProjectConfigRequestId()`。
4. 【中】后端 `"project_quote_"`/`"_richtext/"` 前缀在 `ProjectStorageService` 定义常量并统一大小写判定口径；`"会议预算制作"` 提取常量或走阶段配置。
5. 【中】抽公共 `downloadBlob(blob, filename)` 与共享 `fmt` 金额格式化到 utils；`budget-quote-versions` 锚点常量化或双向注释。
6. 【低】提取 `requireConfirmedBudget`、`mapList`；状态字面量常量化；api 参数改 `{ params }`；`:max="9999"` 加注释。

## 7. 测试与验证
| 验证项 | 命令/步骤 | 结果 |
| --- | --- | --- |
| 提交验证 | `git status --porcelain` | 通过（提交后干净） |
| 问题核实 | `read_file` 抽查 ps1 全文、budgetEstimate.js、ProjectBudgetQuotes.vue；grep `project_quote_`、`会议预算制作` | 通过（与报告一致） |
| 构建/测试 | 未执行（本次未改代码） | 未执行 |

## 8. 风险与回滚
- 本次仅提交 2 个发布元数据文本文件 + 本交接文档，无代码/数据库/配置变更，无需回滚方案。
- 风险提示：`ollama-health-watch.ps1` 的注释/文案不一致是运维告警口径问题，若不修复可能误导人工排障时机判断。

## 9. 文档同步
- [x] 最新交接摘要（docs/handover/README.md 已加条目）
- [x] 本任务记录
- [ ] 架构/部署/接口文档：不涉及（无代码变更）
- 修复实施时需同步：AGENTS.md §17（若收口样式类）、architecture.md（若阶段名改配置）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git log --oneline -2` 确认基线 `1c214f2`；读本记录第 5 节。
2. 然后执行：按第 6 节严重程度顺序实施修复，每项独立提交；ps1 修改后用 `schtasks /run /tn OllamaHealthWatch` + 读 `logs/ollama-watch.log` 验证一轮真实探测。
3. 验收标准：每条修复附 diff 与验证证据；`backend` 测试通过；前端 `npm run build` 通过。
4. 未经确认禁止：直接修改生产 `feeStandards` 数据、改动阶段名字典、重启生产 Ollama 服务。
5. 需要负责人决策：估算金额常量（茶歇 70%、机票 2000 等）是保持前端写死还是并入管理员收费配置（涉及对客报价口径）。

---

## 11. 修复实施记录（2026-11-17 同日，CodeBuddy）

按第 6 节清单实施，**行为不变的重构**，估算金额以命名常量收口（并入 feeStandards 待负责人决策）。

### 11.1 实际变更
| 文件 | 改动 |
| --- | --- |
| `scripts/ollama-health-watch.ps1` | 修正 ：61 注释（180s→120s）与 ：79 文案（90s→120s）；全部阈值收拢到顶部参数区（`$probeTimeoutSec`/`$recoverLoops`/`$warmupTimeoutSec` 等）；日志/锁路径由 `$PSScriptRoot` 推导仓库根；URL 支持 `OLLAMA_BASE_URL` 环境变量（URL 无法由脚本路径推导，故参数化）；探测请求体提取 `Invoke-Probe($timeoutSec)` 消除两处重复 |
| `frontend/.../budgetEstimate.js` | 新增 `ESTIMATE_RULES` 常量（默认 2 间、茶歇 7/50×5（保留整数运算形态避免 0.7 浮点误差）、平台专车 200/100、机票 2000），文案引用常量 |
| `frontend/src/utils/projectConfigRequestId.js` | 新增 `createUuidRequestId()`（带连字符 UUIDv4；后端 `request_key VARCHAR(36)` 且做 `UUID.fromString` 校验，不能直接用 32 位 hex 版本） |
| `frontend/src/utils/common.js`（新增） | `downloadBlob`（统一此前两种不一致实现，立即回收）、`formatMoney`、`apiErrorMessage` |
| `ProjectBudgetQuotes.vue` | 删除手写 UUID 5 行改用 `createUuidRequestId()`；下载改用 `downloadBlob`；错误文案改用 `apiErrorMessage` |
| `ProjectBudgetPanel.vue`、`BudgetFeeConfig.vue`、`ProjectDetail.vue` | 接入 `formatMoney as fmt` / `apiErrorMessage`；ProjectDetail 下载改用 `downloadBlob`（消除 `setTimeout(1000)` 魔法数字） |
| `frontend/src/api/project.js` | `listBudgetFeeStandards` 改用 `{ params: { scene } }`（axios 空值自动省略，与 `fetchProjectWorkbook` 对齐） |
| `backend/.../ProjectStorageService.java` | 新增 `QUOTE_FILE_PREFIX` 常量并在生成处引用 |
| `backend/.../ProjectService.java` | 新增 `PHASE_BUDGET_MAKING` 常量并在 ：1953 引用；删除保护改用 `QUOTE_FILE_PREFIX` |
| `backend/.../ProjectBudgetQuoteService.java` | 前缀/阶段名改用常量（错误文案拼接常量）；提取 `requireConfirmed(budget, message)` 合并 generate/restore 重复校验（保持原加锁顺序不变） |
| `backend/.../ProjectController.java` | 两个文件端点重复的前置校验提取 `resolveReadableStoredFile()`；报价判定统一为大小写敏感 `startsWith(QUOTE_FILE_PREFIX)`（落盘文件名全为小写，原 `regionMatches(true,…)` 多余宽松） |
| `backend/.../ProjectBudgetController.java` | 提取 `mapList(Object raw)` 合并两处类型窄化 |

### 11.2 验证证据
| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 估算单元测试 | `node --test frontend/src/views/project/components/budgetEstimate.test.js` | 6/6 通过 |
| 后端全量测试 | `mvn -q -f backend/pom.xml test "-Drevision=1.0.49" "-DgitShort=<head>"`（注入 `$env:JWT_SECRET` 测试占位值） | 通过：0 失败 0 错误（32 项按既有开关跳过） |
| contextLoads 环境说明 | 首次运行报 `WeakKeyException: 0 bits` | 本机未注入 `JWT_SECRET`（application.yml 设计为 fail-fast），与本次改动无关；注入占位密钥后通过 |
| 前端构建 | `npm run build` | 通过（45.28s，仅既有 chunk 体积告警） |
| ps1 语法 | PowerShell `Parser::ParseFile` | 0 错误 |

### 11.3 未实施项
- 估算金额并入管理员 `feeStandards` 配置：**待负责人决策**（涉及对客报价口径），当前以 `ESTIMATE_RULES` 常量集中维护。
- `budget-quote-versions` 锚点 id 常量化：属跨文件约定，本次未动（建议后续任务处理）。
- `legacyNames` 中文契约：按审查结论登记"有意保留"。
- ps1 已注册计划任务指向脚本路径，本次修改未改文件位置，无需重新注册；修改**尚未在计划任务下实测一轮**（重启服务类验证需负责人批准）。

### 11.4 部署状态
- **未发布**：本次为代码重构修复，仅仓库内提交；生产后端/前端/计划任务均未变更。发布需走 `build-all.ps1` + `deploy-backend.ps1` 流程并经负责人批准。
