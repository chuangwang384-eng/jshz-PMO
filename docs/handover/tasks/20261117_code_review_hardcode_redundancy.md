# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261117_code_review_hardcode_redundancy
- 任务名称：提交工作区变更 + 今日（2026-11-17）代码硬编码与冗余审查
- 主责 Agent：CodeBuddy
- 交接时间：2026-11-17
- 当前状态：已完成待验收（审查报告已产出，问题修复未开始）
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
