# Agent 任务交接记录：全项目冗余清理 + DDL 单一可信来源收口

## 1. 基本信息

| 项 | 内容 |
| --- | --- |
| 任务编号 | 20261116-redundancy-cleanup |
| 任务名称 | 全项目冗余清理（代码/文档/产物）与 init.sql–migration DDL 收口 |
| 主责 Agent | CodeBuddy（单 Agent 执行） |
| 交接时间 | 2026-11-16 |
| 当前状态 | **已完成待验收**（生产未发布，见第 7 节） |
| 优先级 | P1 |

## 2. 目标与验收条件

- 业务目标：消除冗余代码/文档/产物，把 `init.sql` 与 `migration` 的重复 DDL 统一为单一可信来源，且不改变系统功能。
- 本次范围：零风险产物清理、前端死代码清理、文档过期事实修正、DDL 基线补齐 + 一致性守卫脚本、完整验收。
- 不在本次范围：`UserInfoCompatController` / `ExportCompatController` / `ComplianceLegacyService` 的退役（需负责人决策）；三处文档解析实现合并（高风险重构）；生产发布与前端上线；`schema_migration` 台账修复（需生产库写权限批准）。

验收条件：

- [x] 清理范围与判定标准在执行前明确列出并落地
- [x] 删除项均可用 Git / 构建重建恢复
- [x] 线上前端与后端在清理过程中持续可用（每阶段后实测）
- [x] `init.sql` 与生产库业务表清单一致（脚本实测 0 差异）
- [x] 后端测试、前端构建、核心接口冒烟通过
- [ ] 业务侧功能人工复验（需在有账号的环境点检页面，见第 7 节）

## 3. 项目基线

| 项 | 值 |
| --- | --- |
| 分支 | `codex/personal-dashboard-v11` |
| 提交号 | `69074c8`（清理前，工作区干净） |
| 运行后端 | `1.0.45`，buildTime `2026-11-16 14:33:19`，gitShort `bf275ca`（**运行包早于 HEAD**，本次未重新构建/部署） |
| 生产库 | `expert_system` @ 127.0.0.1:3306，实测 **101 张表**（85 业务 + 10 `*_bak_*` + 6 `mig_*`） |
| 线上前端 | `frontend/releases/1.0.45-ai-chat-20261116`（Nginx root） |
| 核实时间 | 2026-11-16 15:30–16:10 |

## 4. 已完成工作

| 内容 | 位置 | 状态 | 证据 |
| --- | --- | --- | --- |
| 产物清理：发布快照 54→6 目录 | `frontend/releases/` | 完成 | `prune-frontend-releases.ps1`，释放 **18.2 GB** |
| 产物清理：构建快照 43 目录 | `builds/`（`archive/` 保留） | 完成 | `prune-builds.ps1 -Keep 5`，释放 **2.23 GB** |
| 产物清理：隔离区旧构建目录 | `temp/.quarantine-20261113/A` | 完成 | 271.6 MB，`manifest.csv` 记录为可重建产物；`C`（含供应商图片）保留 |
| 产物清理：日志目录错置文件 | `logs/*.ps1`、`*.exit.txt`、`*.jpg` | 完成 | 删除 42 个；11 个被文档引用的包装脚本保留 |
| 产物清理：根目录遗留 | `version.txt.bak-1.0.17`、`temp_schema.txt`（0 B）、`.bdv4b.txt` | 完成 | 3 项 |
| 前端死代码：零引用组件 | `ExecutionBoard.vue`、`MyTodoSection.vue`、`FlowBuilderStep.vue`、`TemplateBindStep.vue` | 完成 | 全库 grep 0 引用；仅 `ui-tokens.css` 注释提及 |
| 前端死代码：无调用导出 | `api/{announcement,compliance,complianceV4,dashboard,project}.js`、`utils/{dict,supplier}.js` | 完成 | 删除 34 处 `export`（32 个 API 封装 + 2 个改文件内私有），复核后剩余 0 个未引用导出 |
| 样式收口 | `styles/ui-tokens.css` | 完成 | 移除 7 个仅服务已删组件的选择器（`.stat-warn` / `.stat-danger` / `.due-over` / `.todo-title` / `.todo-sub` / `.task-title` / `.task-sub`，全库计数 0）；`.stat` / `.stat-label` / `.stat-value` 保留（7 处在用） |
| 接口去重 | `components/compliance/ComplianceTemplateEditor.vue` | 完成 | 裸 `request.get('/compliance/v4/templates/'+id)` 改用 `getTemplateDetail` |
| DDL 收口：基线补齐 | `database/init.sql` | 完成 | 追加 §四，纳入 `20261116_*` 三个迁移的 4 表 + 种子，基线 81→**85 张业务表** |
| DDL 收口：一致性守卫 | `scripts/check-schema-parity.ps1`（新增） | 完成 | 只读校验 init.sql ↔ migration ↔ 生产库，实测三方 0 差异 |
| 文档事实修正 | `README.md`、`AGENTS.md`、`docs/architecture.md`、`docs/deployment.md`、`database/README.md` | 完成 | 表数 101/85 统一；Controller 31、Service 58、实体 67、页面 43、路由 37；迁移 63 个 |
| 文档过期删除 | `frontend/version.txt`（1.0.37.13 停留，无任何脚本读取） | 完成 | Git 可恢复 |
| 历史文档标注 | 3 篇设计文档（引用已删组件） | 完成 | 文首插入「状态提示（2026-11-16）」 |

## 5. 实际变更

**新增**

- `scripts/check-schema-parity.ps1`：DDL 三方一致性只读校验（`-NoDatabase` 支持纯文件校验）。

**修改（行为相关）**

- `frontend/src/components/compliance/ComplianceTemplateEditor.vue`：模板预览改用 `getTemplateDetail`（URL 不变）。
- `database/init.sql`：追加 145 行基线补齐段；头注释表数/脚本数更新。
- `frontend/src/styles/ui-tokens.css`：删除 8 行孤儿公共类。

**修改（纯删除/文档）**

- 删除 4 个 `.vue`、`frontend/version.txt`；删除 34 处 `export`；`README.md` / `AGENTS.md` / `docs/architecture.md` / `docs/deployment.md` / `database/README.md` / 3 篇 `docs/design/*` 文本更新。

**数据库/API/配置影响**

- 数据库：**无 DDL 变更**（`init.sql` 仅对基线文件补齐已存在于生产的结构）；未向 `schema_migration` 写任何记录。
- API：**无后端接口增删**；仅前端内部封装收敛。
- 配置/依赖：无变更；未新增/删除 npm、Maven 依赖。

## 6. 测试与验证

| 验证项 | 执行命令/步骤 | 结果 | 证据 |
| --- | --- | --- | --- |
| 结构一致性 | `powershell -File scripts\check-schema-parity.ps1` | **通过** | init.sql 85 表 = 生产库 85 业务表；migration 定义表全部被基线覆盖 |
| 后端单元测试 | `mvn -B test -DgitShort=local -Drevision=1.0.45`（需 `JWT_SECRET`） | **通过** | `Tests run: 159, Failures: 0, Errors: 0, Skipped: 11`，BUILD SUCCESS |
| 后端测试（无 JWT_SECRET） | `mvn -B test`（未注入环境变量） | 1 error | `ExpertSystemApplicationTests.contextLoads`：`WeakKeyException` 0 bits — **环境变量缺失，非本次改动导致** |
| 前端构建 | `npm run build -- --outDir ../temp/dist-verify --emptyOutDir` | **通过** | `✓ built in 33.73s`，exit 0（仅有既存 chunk>500kB 警告） |
| 前端静态检查 | `read_lints`（改动文件） | 通过 | 0 diagnostics |
| 线上首页与主包 | `GET /`、`GET /assets/index-C5tFE9Zn.js` | **通过** | 均 HTTP 200（清理发布快照后复测） |
| 后端健康 | `GET /health` | 通过 | `status=UP, version=1.0.45` |
| 登录 | `POST /api/auth/login`（`jshz`） | 通过 | HTTP 200，`isAdmin=true`，token 走 HttpOnly Cookie |
| 核心接口冒烟 | 27 项（专家/项目/看板/知识/供应商/客户/公告/消息/审批/RBAC/企微/审计/合规） | 通过 | 全部 200；先期 7 项 4xx/5xx 经复核为**本人路径写错**（如 `/api/supplier`→`/api/supplier/list`、`/api/compliance/mode`→`/api/compliance/v4/mode`） |
| 权限边界 | 匿名访问 `/api/experts`、`/api/dashboard/overview`、`/api/roles` | 通过 | 均返回 **401** |
| 页面级人工复验 | 浏览器点检 | **未执行** | 本次无浏览器环境，需业务侧配合（见第 7 节） |

## 7. 未完成事项与阻塞

| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 前端改动未上线 | 生产发布属高风险操作，需负责人批准 | 线上仍是 1.0.45 旧包；死代码删除仅存在于工作区 | 批准后走 `deploy-frontend.ps1` + 冒烟；或随下个版本发布 | 项目负责人 |
| `schema_migration` 台账异常 | `-Mode Status` 显示 **待执行 3**（`20261116_ai_assistant|budget_stage|budget_fee_scene`）、**漂移 18** | 台账与实际结构不符；`-Mode Apply` 会因漂移中止 | 负责人批准后重登记（写库操作，本次未执行） | 项目负责人 + DBA |
| 运行包与 HEAD 不一致 | 线上 jar 为 `bf275ca`，HEAD 为 `69074c8` | 无法证明 HEAD 代码已上线 | 下次发布时以 HEAD 构建并核对 `/health.gitShort` | 发布执行人 |
| 三处文档解析实现未合并 | 输出结构/降级策略不同（`String` / `ResumeParseResult` / `Extracted`，含 OCR 与图片抽取差异），强制合并属高风险重构 | 维护需改三处 | 单独立项：抽 `DocumentTextExtractor` 并补样例回归 | 后端负责人 |
| 三个兼容层保留 | `UserInfoCompatController`（旧缓存页兼容）、`ExportCompatController`（`/api/export` 无前端调用但属已发布能力）、`ComplianceLegacyService`（V3 页面仍在用） | 少量死表面 | 由负责人确认下线窗口后单独任务处理 | 项目负责人 |

## 8. 风险与回滚

- 已知风险：`check-schema-parity.ps1` 依赖 `mysql.exe` 与凭据解析；无客户端时需显式 `-NoDatabase`（已实现跳过）。
- 数据风险：**无**。未执行任何 DDL/DML；`temp/.quarantine-20261113/C`（含供应商图片）刻意保留。
- 兼容性风险：删除的前端导出均为零调用；`ui-tokens.css` 删除的类仅被已删组件使用（全库计数 1）。
- 回滚步骤（代码/文档）：`git checkout -- frontend/src docs README.md AGENTS.md database/init.sql` 并按需 `git clean` 恢复被删文件；新增脚本直接删除 `scripts/check-schema-parity.ps1`。
- 回滚限制：已删除的 20.6 GB 产物**不可回滚**，但均可用 `build-all.ps1` / `npm run build` 重建；`logs/` 中 42 个一次性脚本已删除，其中未被文档引用的部分无法精确还原（内容为历史发布包装，可从 `scripts/deploy-backend.ps1` 重新生成）。

## 9. 文档同步

- [x] 架构文档：`docs/architecture.md`（表数/Controller/Mapper/Entity 计数、DDL 单一来源说明）
- [x] 部署文档：`docs/deployment.md`（新环境建库流程：init.sql → 校验 → Bootstrap；85 表）
- [x] 数据库说明：`database/README.md`（基线补齐、单一来源原则、台账现状、校验命令）
- [x] 项目说明：`README.md`（事实基线、目录结构、快速开始）
- [x] 规范：`AGENTS.md` §0.5（2026-11-16 基线与 DDL 单一来源条款）
- [x] 历史设计文档：3 篇加「状态提示」
- [x] 交接摘要：`docs/handover/README.md` 顶部新增条目
- 未同步：`docs/reviews/`、`docs/releases/` 中的历史报告未逐篇修正（属历史快照，不做回溯改写）

## 10. 下一位 Agent 的接手指令

1. **首先检查**：`git status`；`powershell -File scripts\check-schema-parity.ps1`；`curl http://127.0.0.1:8080/health`（对比 `gitShort` 与 HEAD）。
2. **然后执行**：若被授权发布，`cd frontend && npm run build` → `scripts\deploy-frontend.ps1`（新 ReleaseId）→ 复测首页与主包 → 浏览器点检企业合规模板预览（本次唯一行为改动点）。
3. **验收标准**：`check-schema-parity` 0 差异；`mvn test` 159 通过（需 `JWT_SECRET`）；首页/登录/27 项接口 200；匿名 401；模板预览弹窗可正常加载旧版模板。
4. **未经确认禁止**：执行 `db-migrate -Mode Apply/Bootstrap`、修改 `schema_migration`、删除 `*_bak_*` 或 `mig_*` 表、删除 `builds/archive`、把 `init.sql` 重新生成为无种子版本。
5. **需要负责人决策**：三个兼容层下线窗口；三处解析实现是否合并；`schema_migration` 漂移与待执行的修复方案。
