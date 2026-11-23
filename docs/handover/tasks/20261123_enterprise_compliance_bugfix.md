# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261123_enterprise_compliance_bugfix
- 任务名称：知识星球「企业合规要求」模块 Bug 修复、健壮性维护与残留代码清理
- 主责 Agent：AutoClaw（主控）+ 审阅者×4（只读审查，报告存 `.zwork/runs/0f96e9ac/`）
- 交接时间：2026-11-23
- 当前状态：已完成待验收
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：修复审查发现的 7 项 bug；修复健壮性/一致性问题；清理无引用残留代码；验证功能未受影响。
- 本次范围：仅「企业合规要求」页面直接使用的代码（EnterpriseCompliance.vue 及其 4 个页签的前后端实现）。
- 不在本次范围：V4Service 5 个已下线端点对应的死方法（**保留**，理由见第 7 节）、V4 内存分页/N+1 性能重构、item_code 并发取号加锁、`mode` 默认值 V3→V4（需负责人确认业务是否已全面 V4）、`reminder-logs` 分页（按 itemId 查询、单条目量小，暂不改）。
- 验收条件：
  - [x] 7 项 bug 全部修复并定位到行
  - [x] 健壮性问题按清单修复（见第 4 节）
  - [x] 残留代码删除且回归通过
  - [x] `mvn compile` + 相关单测 + `npm run build` 通过

## 3. 项目基线
- 分支：main；当前提交号：9a016a5（开工时）
- 工作区状态：含他人未提交修改（AGENTS.md、docs/deployment.md）与本任务 14 个文件改动；未提交
- 版本：version.txt revision=1.0.133（未发布，改动待随下次发布上线）
- 以上信息核实时间：2026-11-23

## 4. 已完成工作

### 4.1 Bug 修复（P0）
| # | 内容 | 文件 | 说明 |
| --- | --- | --- | --- |
| 1 | 模板编辑器永不渲染 | frontend/src/views/EnterpriseCompliance.vue | 删除未定义变量 `enabled` 的 `v-if`（页签本身已由 `knowledge:maintain` 门控） |
| 2 | 文档更新接口缺权限校验 | backend/.../controller/KnowledgeController.java、service/KnowledgeDocumentService.java | `PUT /api/knowledge/docs/{id}` 控制器补 `requireMaintain` + service `updateVersion` 补业务层二次校验（与上传对齐，落实 2026-10-27 权限设计文档既有约定） |
| 3 | 撤下事项泄漏到我的待办 | backend/.../service/ComplianceService.java | `myTodo` 查询补 `withdrawn=0/NULL` 过滤；`updateMyProgress` 拒绝对已撤下事项更新进度 |
| 4 | 乐观锁更新静默失败 | backend/.../service/ComplianceNotebookService.java | `handleTodo` 检查条件更新影响行数，0 时抛 `BusinessException(409)`（对齐 V4Service patchTask 口径） |
| 5 | byte[] 解析路径 NPE | backend/.../service/ComplianceParseService.java | `candidates.isEmpty() || structured.incomplete()` → 补 `structured != null` 判断 |
| 6 | LLM 兜底失效 | 同上（与 #5 同一修复） | NPE 被外层 catch 吞掉导致 `llmExtract` 兜底永不执行，#5 修复后恢复 |
| 7 | OCR 表格行错位/越界 | backend/.../service/ComplianceOcrTable.java | `refineRows` 行号改为从锚点「表格第 N 项」解析真实行号（原对每个含锚点块递增导致 PAGE/TABLE_ROW 混排错位）；取单元格坐标前先判 `row+1 < rows.size()`、`cell+2 < columns.size()` 与 `grid != null` |

### 4.2 健壮性维护（P1）
| 内容 | 文件 | 说明 |
| --- | --- | --- |
| Notebook 接口补登录校验 | backend/.../controller/ComplianceNotebookController.java | 全部读端点补 `requireUser`（401），对齐 ComplianceV4Controller；原 null user 在 service 层 NPE 变 500 |
| withdraw 并发加固 | backend/.../service/ComplianceNotebookService.java | 逐条撤下前再核对 `items.size() == expectedCount`（收窄 TOCTOU 窗口） |
| Legacy 历史页 NPE | backend/.../service/ComplianceLegacyService.java | `document_id` 为 NULL 时视为不可读，不再 NPE |
| 撤下/恢复/推送异常处理 | frontend/.../compliance/ComplianceNotebook.vue | doHide/doUnhide/openPush/doPush 补 catch，消除 unhandled rejection（拦截器已有 toast） |
| 列表/上传异常处理 | frontend/src/components/KnowledgeDocList.vue | `fetchList` 失败清空列表；`submitUpload` 失败保留弹窗便于重试；均补 catch |
| 预览 blob URL 泄漏 | 同上 | `handlePreview` 重开抽屉时先 `revokeObjectURL` 旧 blob |
| 维护看板异常处理 | frontend/.../compliance/MaintenanceBoard.vue | perform/clearPublished/clearDraftGroups/parse 补 catch；parse 的 ElLoading 改为 finally 兜底关闭 |
| 撤销误置 dirty | frontend/.../compliance/ComplianceTemplateEditor.vue | `undo()` 用 hydrating 标志包住 content 替换，撤销不再误亮「等待保存」 |
| 409 判断兼容 HTTP 状态 | 同上 | `error.code===409 \|\| error.response?.status===409`（现行后端为信封 409，属防御性兼容） |
| 硬编码色值 | frontend/.../compliance/ComplianceNotebook.vue | inline `#f56c6c` → `var(--ui-danger)`（AGENTS.md 17.2） |

### 4.3 残留代码清理（P2）
| 内容 | 文件 | 依据 |
| --- | --- | --- |
| 删除 6 参 `pageList` 重载 | backend/.../service/KnowledgeDocumentService.java | 全库无调用方（控制器用 7 参版） |
| 删除 `latestVersionIdOf` | backend/.../service/ComplianceService.java | 全库无调用方 |
| 删除 `extract(MultipartFile)`、`extractStructured(MultipartFile)` 与重复 import（Files/Path 各 import 两次） | backend/.../service/KnowledgeDocTextExtractor.java | 无调用方（全链路已走 Path/byte[]） |
| 删除恒空 else 分支 `result.addAll(fragments)` | backend/.../service/ComplianceParseService.java | else 分支 fragments 必为空（注意：if 分支内的 addAll 是 LLM 拆分失败回退，**非**死代码，已保留） |

## 5. 实际变更
- 新增文件：docs/handover/tasks/20261123_enterprise_compliance_bugfix.md（本文件）
- 修改文件：上述 4.1–4.3 共 10 个 Java、5 个 Vue
- 删除文件：无
- 数据库/迁移：无结构变更（ withdrew 过滤仅查询条件，无 DDL）
- API/接口契约：`PUT /api/knowledge/docs/{id}` 由「登录即可」收紧为 `knowledge:maintain`（对齐 2026-10-27 权限设计文档第 22 行既有约定，属文档-代码不一致修复，**非行为变更**）；其余接口对外契约不变（仅错误路径新增 409/401）
- 页面/路由/权限：无路由变更；模板维护页签下编辑器恢复渲染
- 配置/环境变量：无
- 依赖变更：无

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 证据/备注 |
| --- | --- | --- | --- |
| 后端编译 | `mvn -q compile -DskipTests -Drevision=1.0.133 -DgitShort=<head>` | 通过（EXIT=0） | 需带 -Drevision/-DgitShort（pom 由构建脚本注入） |
| 后端单测（本次改动相关 8 个类） | `mvn test -Dtest=ComplianceLlmExtractTest,ComplianceLlmMarkTest,ComplianceMaintenanceTest,ComplianceReminderRegressionTest,ComplianceAssignmentRegressionTest,ComplianceV4EditingRegressionTest,KnowledgeDocAccessPolicyTest,KnowledgeDocStreamingParseTest` | 通过 | surefire 报告 0 失败：LlmExtract 5(1 skip)/LlmMark 2/Maintenance 4/Reminder 7/Assignment 1/V4Editing 7/AccessPolicy 4/StreamingParse 3 |
| 前端构建 | `cd frontend && npm run build` | 通过（38.07s） | 仅既有 chunk 体积警告 |
| 残留回归 grep | `latestVersionIdOf` / `v-if="enabled"` / `extractStructured(MultipartFile)` 全库检索 | 0 命中 | — |
| 样式残留 grep | 合规组件内 `#f56c6c|#409eff` | 0 命中 | — |
| 复制检测（19.5） | `rg -c 'private String (safeName\|extension)'` | 3 处既有命中（DocPreviewService/KnowledgeDocumentService/KnowledgeDocTextExtractor），非本次引入，属既有状态 | 未扩大处理（超本次范围） |
| 接口/页面真机验证 | 未执行 | 未执行 | 需运行环境与登录态，建议验收时按第 10 节步骤人工核验 |
| 生产部署健康检查 | 不适用 | 不适用 | 未发布 |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| V4Service 5 个死方法未删除 | 保留至 V4 执行清单彻底退役（ReminderScheduler 仍读相关表，且涉及 scheduleTaskReminders 级联依赖，删除属小型重构） | 无功能影响，仅代码整洁度 | V4 退役时随迁移一并删除 | 负责人决策排期 |
| `mode` 默认 'V3' 未改 | 需确认后端 `/compliance/v4/mode` 生产实际返回；改默认值属行为变更 | Legacy 页签在接口失败时仍可能显示 | 负责人确认后改 `'V4'` | 负责人 |
| V4 内存分页/N+1、item_code 取号并发、reminder-logs 分页 | 属性能/并发重构，超出最小必要改动 | 数据量百级前无感知 | 列入后续优化批次 | 负责人排期 |
| 交接前提交 Git | 规范要求主责 Agent 整合提交；本记录未自动提交 | — | 审阅后 `git add` 相关 14 文件 + 本文件，提交信息建议「fix(compliance): 企业合规要求模块 7 项 bug 修复与残留清理」 | 负责人/下一位 Agent |

## 8. 风险与回滚
- 已知问题：`PUT /api/knowledge/docs/{id}` 权限收紧后，若有前端以外脚本以普通用户身份调该接口将开始收到 403/400——检索前端仅 KnowledgeDocList `openUpdate`（维护人可见按钮）调用，风险低。
- 数据风险：无（无 DML/DDL）。
- 兼容性风险：`handleTodo` 冲突时前端将收到信封 code=409；前端 ComplianceNotebook 的 handleTodo 调用走拦截器统一提示，无需改动。
- 回滚步骤：`git checkout -- backend/src/main/java/com/jshz/expert/{controller,service}/... frontend/src/...`（按本记录第 5 节文件清单），或回退本次提交。
- 回滚限制：无数据库回滚需求。

## 9. 文档同步
- [x] 交接记录（本文件 + docs/handover/README.md 最新摘要）
- [x] 权限说明：PUT docs 收紧为落实 [2026-10-27 维护人权限设计文档](../design/2026-10-27-知识星球维护人角色与权限-设计文档.md) 既有约定，设计文档无需改
- [ ] 接口文档：仓库无独立接口文档目录，接口契约以代码注释为准（已在注释标注 20261123 修复）
- 未完成项及原因：architecture.md 未单列本次修复（bug 修复不改变架构）；如负责人要求可在架构文档安全节补一行

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`/`git diff` 核对第 5 节 14 文件清单；确认 AGENTS.md、docs/deployment.md 两处**他人未提交修改未被卷入提交**。
2. 然后执行：真机验证——① 维护人登录 → 模板维护页签确认模板编辑器正常渲染；② 普通用户调 PUT /api/knowledge/docs/{id} 应被拒；③ 收藏文档 → 撤下其中一条 → 我的待办不再出现且无法更新进度；④ 两个账号并发处理同一项目合规待办，后者收到「待办已被他人更新」。
3. 验收标准：以上 4 条全部符合预期；前端控制台无 `enabled is not defined`、无 unhandled rejection 新增。
4. 未经确认禁止：删除 ComplianceV4Service 5 个已下线方法；修改 `mode` 默认值；发布生产。
5. 需要负责人决策：第 7 节三项（V4 死方法删除时机、mode 默认值、性能优化排期）；以及本记录连同代码的提交授权。
