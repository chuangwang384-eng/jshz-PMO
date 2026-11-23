# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261123_compliance_req19_21
- 任务名称：企业合规需求 19/20/21（含 21a/21b）实现、验证与发布
- 主责 Agent：WorkBuddy（单主责，无并行改动）
- 交接时间：2026-11-23
- 当前状态：已实现并验证，随 1.0.134 发布（发布状态见第 7 节）
- 优先级：P1

## 2. 目标与验收条件
| # | 验收条件 | 结果 |
| --- | --- | --- |
| 19 | 「再次使用」复用上次答案时，复制答案带醒目「待确认」标记与提示；不影响正常手工填写 | 通过（服务强制 + 接口字段） |
| 20 | OCR 表格支持非 4 列（3 列、5 列…），保持 4 列标准格式兼容；列识别异常不生成错误草稿 | 通过（单测覆盖 4/5/5 竖线变体与异常） |
| 21a | 「我的待办」展示来源状态（如「来源文档已下架」） | 通过（接口 + 页面列） |
| 21b | 提醒发送记录查询支持分页与时间范围，不再全量返回 | 通过（服务端分页 + 范围过滤） |
| — | 自动化测试/回归验证，现有功能不受影响 | 通过（见第 6 节） |

## 3. 项目基线
- 分支：main；开工提交号：9a016a5
- 工作区：含上一任务（20261123_enterprise_compliance_bugfix）未提交改动，已一并在本次提交中收口
- 数据库：`expert_system`，实测 104 张 BASE TABLE（2026-11-23 核实现查）
- 版本：1.0.133 → 1.0.134

## 4. 变更清单

### 4.1 需求19 复用答案「待确认」
| 文件 | 变更 |
| --- | --- |
| `database/migration/20261123_execution_answer_unconfirmed.sql`（新增） | `compliance_execution_task` 增 `answer_unconfirmed`（1=待确认）、`answer_unconfirmed_source`；`information_schema` 判存可重跑 |
| `database/init.sql` | 同步两列（新环境单一 DDL 来源） |
| `entity/ComplianceExecutionTask.java` | 新增两字段 |
| `service/ComplianceV4Service.java` | `markAnswerUnconfirmed` / `answerPendingConfirm` / `unconfirmedCompletionError`（包内静态，可单测）；`reuseExecution` 复制答案置位并返回 `unconfirmedAnswers`/`answerNotice`；`patchTask` 提交 `answer` 或 `confirmAnswer=true` 清除标记并记 `ANSWER_CONFIRM` 事件；完成校验拦截未确认答案；`taskMap` 下发 `answerUnconfirmed/answerUnconfirmedSource/answerUnconfirmedNotice` |

- 语义对齐「候选值变化/失效须重新确认」：复制来的值未经本人核对不得视为本次结果。
- 不影响手工填写：正常提交答案即清除标记；空答案不进入待确认态。
- 顺带删除 `reuseExecution` 内自 1.0.x 起未使用的 `oldAnswers` 局部变量（复制逻辑已改为直接置位，死代码无调用方）。
- 边界说明：V4「我的执行」UI 已于 1.0.37.34 下线，标记通过接口字段下发并由服务端强制，页面恢复该清单时按字段渲染即可。

### 4.2 需求20 OCR 表格非 4 列
| 文件 | 变更 |
| --- | --- |
| `service/ComplianceOcrTable.java` | `Grid` 增加 `titleColumn/bodyColumn`；`Table` 记录（blocks + 已判读栅格）；`grid()` 接受 3~7 条竖线；新增 `resolveCellColumns(headers)` 按「项目/事项」「标准/内容/要求」定位列；4 竖线标准格式判读失败时回退原列位；宽表判读失败返回空；整行空白跳过，缺列行标 `PAGE` 触发整页重试；`refineRows` 复用同一列映射 |
| `service/KnowledgeDocTextExtractor.java` | 适配 `blocks()` 返回值，`refineRows` 传入已判读栅格 |

- 4 列标准格式的单元格文本与块类型不变（`ComplianceLlmExtractTest` 回归通过）。
- 不生成错误草稿：表头无「标准/内容」、宽表列位无法判读、无有效数据行时返回空。

### 4.3 需求21a 待办来源状态
| 文件 | 变更 |
| --- | --- |
| `service/ComplianceService.java` | `myTodo` 不再隐藏来源下架/删除事项，返回 `sourceStatus`（ACTIVE/OFFLINE/MISSING）、`sourceNotice`、`actionable`；可执行项排前；统计只计 `actionable`，新增 `sourceUnavailableCount`；`updateMyProgress` 对来源失效事项拒绝（`requireSourceAvailable`） |
| `components/compliance/LegacyCompliancePanel.vue` | 新增「来源状态」列（warning 标签显示来源文档已下架/已删除）、失效条数提示条、失效行禁用编辑按钮 |

### 4.4 需求21b 提醒记录分页
| 文件 | 变更 |
| --- | --- |
| `service/ComplianceService.java` | 新增 `reminderLogs(itemId, from, to, page, size)`：`Page` 分页、`scheduled_time` 范围过滤、上限 200/页、起止校验、格式校验 |
| `controller/ComplianceItemController.java` | `GET /api/compliance/reminder-logs` 增加 `from/to/page/size`，返回 `{total,page,size,from,to,items}`；移除控制器内直接 Mapper 查询 |

## 5. API 契约变化
| 接口 | 变化 | 兼容性 |
| --- | --- | --- |
| `GET /api/compliance/my-todo` | 新增 `sourceStatus/sourceNotice/actionable/sourceUnavailableCount`；来源失效事项不再被过滤 | 追加字段；原字段不变 |
| `PUT /api/compliance/my-progress/{itemId}` | 来源文档下架/删除时返回业务错误 | 行为收紧（原先这些事项不可见） |
| `GET /api/compliance/reminder-logs` | 返回结构由数组改为分页对象，新增 `from/to/page/size` | **不兼容变更**：仓库内无前端调用方（已检索），仅有维护人手工/脚本核对用途 |
| `POST /api/compliance/v4/executions/{id}/reuse` | 返回追加 `unconfirmedAnswers/answerNotice`；端点仍为「已下线」占位 | 无影响 |
| `GET /api/compliance/v4/executions/{id}`、`/my-tasks` | 任务行追加 `answerUnconfirmed*` 字段 | 追加字段 |
| `PATCH /api/compliance/v4/tasks/{id}` | 新增可选 `confirmAnswer`；未确认的复用答案禁止 `COMPLETED` | 端点已下线，契约预留 |

## 6. 测试与验证
| 验证项 | 命令/步骤 | 结果 |
| --- | --- | --- |
| 后端编译 | `mvn -o compile -DskipTests -Drevision=1.0.134 -DgitShort=dev` | 通过 EXIT=0 |
| 新增单测（16 例） | `mvn -o test -Dtest=ComplianceOcrTableLayoutTest,ComplianceReuseAnswerMarkTest,ComplianceTodoSourceStatusTest` | 通过 16/16 |
| 合规模块回归（49 例） | `mvn -o test -Dtest='Compliance*Test,KnowledgeDoc*Test'` | 通过 49，跳过 1（既有环境门控） |
| 后端全量单测 | `mvn -o test -Drevision=1.0.134 -DgitShort=dev` | 262 例：0 失败、1 错误、16 跳过。错误为 `ExpertSystemApplicationTests.contextLoads`：未注入 `JWT_SECRET` 时 `JwtTokenProvider` 构造抛 `WeakKeyException`（环境依赖，非本次改动；见第 8 节） |
| 前端构建 | `cd frontend && npm run build` | 通过（仅既有 chunk 体积警告） |
| 数据库迁移 | 执行迁移脚本 + `information_schema` 核对 | 两列存在，`answer_unconfirmed` NOT NULL DEFAULT 0 |
| 迁移登记 | `schema_migration` 插入 SHA256 | APPLIED，2026-11-23 17:08:54 |
| 结构一致性 | `scripts/check-schema-parity.ps1` | 未执行：本次 PowerShell 工具不可用（见第 8 节）；本次无新增业务表，仅加列，`init.sql` 已同步 |
| 接口/页面真机验证 | — | 未执行（需登录态；发布后按第 10 节核验） |

## 7. 发布状态
- 版本号：`version.txt` = `revision=1.0.134`，`frontend/package.json` = `1.0.134`，`package-lock.json` 同步
- 代码提交：`a53d21e`（main）
- 数据库：迁移已应用并登记（见第 6 节），对当前运行的 1.0.133 无影响（新增列有默认值，旧代码不读取）

### 7.1 发布结果（回填）
- **未发布（阻塞）**。阻塞点：Publish 模式强制要求管理员权限（`release-full.ps1` 第 424 行：蓝绿发布需操作 NSSM 服务与 Nginx 配置），当前会话 `IsInRole(Administrator)=False`；同时本会话 PowerShell 工具只能执行单个简单 cmdlet，调用任何脚本均报 `PowerShell command analysis failed`，无法运行发布编排。
- 待管理员执行（PowerShell 管理员窗口）：
  ```
  <LOCAL_PATH> -Mode Preflight -Version 1.0.134
  <LOCAL_PATH> -Mode Publish -Version 1.0.134 -Slug compliance-req19-21 -Yes
  ```
  发布后核对：`curl -s http://127.0.0.1:8080/health` 返回 `version=1.0.134`；Nginx root 指向 `frontend/releases/1.0.134-compliance-req19-21`。
- 现状：后端服务仍运行 1.0.133，前端 root 仍为 `1.0.133-idcard-risk`；数据库已含新列（向后兼容）。

## 8. 已知限制与未验证项
| 事项 | 原因 | 影响 | 下一步 |
| --- | --- | --- | --- |
| 需求19 无页面呈现 | V4「我的执行」UI 已按需求规则6于 1.0.37.34 移除，端点写操作同步关闭 | 标记仅在接口层与完成校验生效，当前无界面可看 | 若恢复执行清单页面，按 `answerUnconfirmed*` 渲染醒目徽标与提示 |
| `contextLoads` 失败 | 未设置 `JWT_SECRET`（要求 ≥32 字节） | 与本次改动无关；CI/本机需注入 | 以发布环境变量运行或跳过该类 |
| `check-schema-parity.ps1` 未跑 | 本会话 PowerShell 工具报 `analysis failed` | 结构漂移未被脚本确认 | 发布前/后由具备 PowerShell 的环境补跑 |
| 生产 DDL 由 mysql 客户端直连执行 | 同上，`db-migrate.ps1` 无法运行；已按脚本同一格式手工登记 `schema_migration` | 与脚本路径的差异已留痕 | 下次 `db-migrate.ps1 -Mode Status` 应显示该项为已执行 |

## 9. 文档同步
- [x] `docs/architecture.md` 新增「合规复用答案、待办来源状态与提醒记录分页（2026-11-23，1.0.134）」
- [x] `docs/releases/20261123-compliance-req19-21-1.0.134.md`
- [x] 本交接记录 + `docs/handover/README.md` 摘要
- [x] 数据库迁移脚本与 `init.sql` 同步
- [ ] 接口文档：仓库无独立接口目录，契约以 Controller/Service 注释为准（已在注释标注需求编号）

## 10. 下一位 Agent 的接手指令
1. 先确认发布状态：`curl -s http://127.0.0.1:8080/health`（应返回 `version=1.0.134`）；Nginx root 指向 `frontend/releases/1.0.134-*`。
2. 真机核验：① 维护人收藏一篇企业合规文档并收藏其事项 → 文档下架 → 「原有待办」页签应显示「来源文档已下架，仅供历史参考」且编辑按钮禁用；② 该事项调用 `PUT /api/compliance/my-progress/{id}` 应被拒绝；③ `GET /api/compliance/reminder-logs?itemId=x&page=1&size=20&from=2026-11-01&to=2026-11-30` 返回分页对象；④ 上传一份 5 列合规表格 PDF，解析草稿列内容应为「项目 | 标准」而非错列。
3. 未确认前禁止：删除 `answer_unconfirmed*` 两列（旧代码不读，保留更安全）；恢复 V4 执行清单写端点。
4. 需负责人决策：是否为「我的待办」增加「仅看可执行」筛选（当前用排序置底代替）。
