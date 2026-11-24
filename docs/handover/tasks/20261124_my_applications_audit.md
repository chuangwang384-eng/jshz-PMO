# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261124-01
- 任务名称：「我的申请」模块代码审查（bug / 未启用功能 / 死代码 / 硬编码 / 可推进需求）
- 主责 Agent：AutoClaw
- 交接时间：2026-11-24
- 当前状态：已完成待验收（第二阶段：修复已实现并发布，业务验收待完成；实施记录见文末「二、实施记录」）
- 优先级：P1

## 2. 目标与范围
- 业务目标：聚焦「我的申请」模块做只读审查，给出修复建议；不改动任何其他模块。
- 本次范围：后端 `ApplicationController` / `ApplicationService` / `Application` / `ApplicationMapper` 链路，前端 `MyApproval.vue` / `api/approval.js` / `utils/format.js`，权限行（init.sql permission 表）。
- 不在本次范围：审批管理/审批流配置页面逻辑重构、生产发布、数据库变更。
- 验收条件：问题均有代码位置与证据、建议可执行、不夹带未验证结论。

## 3. 项目基线
- 分支：main；提交：940f505（1.0.137.3）。
- 工作区：有他人未提交改动（docs 与 frontend/release-metadata），本次未触碰。
- 核实时间：2026-11-24（git status / git log 实测）。

## 4-5. 审查发现与修复建议

### A. 疑似 bug（按优先级）

| # | 问题 | 位置 | 建议 |
| --- | --- | --- | --- |
| A1 | **修改申请提交后未通知审批人**：`submitRequest` 只 save+addRecord 即返回，未调用 `notifyApprovalTodo`；而 `submitCreateRequest`（L262）与 `resubmit`（L819）都通知。审批管理页无轮询，审批人只能手动刷新发现新的修改申请 | `ApplicationService.submitRequest`（约 L215-245） | 在 `addRecord(...)` 后补一行 `notifyApprovalTodo(app)`，行为与新增申请对齐；补一条 Service 测试 |
| A2 | **本人校验可能 NPE 且不认 applicantId**：`withdraw`/`resubmit` 用 `app.getApplicant().equals(user.getUsername())`，存量迁移行 applicant 可能为 null → 500；用户改名后无法撤回自己的申请（my-applications 列表却用 applicantId/applicant 双条件可见） | `withdraw`（约 L880）、`resubmit`（约 L770） | 改为与 my-applications/dashboardFocus 相同的双条件：`Objects.equals(app.getApplicantId(), user.getUserId()) \|\| Objects.equals(app.getApplicant(), user.getUsername())` |
| A3 | **oldData 不脱敏「联系方式」**：非管理员视图身份证号/银行卡号已前3后4，但手机号/邮箱明文出现在列表「变更摘要」与对比弹窗 | `maskedOldData` | 与负责人确认口径后补联系方式脱敏（或登记豁免理由） |
| A4 | **空 catch 吞异常**：`notifyApproverRole`/`notifyTransferred` 等多个通知 catch(Exception) 无日志，通知失败不可追溯 | ApplicationService 通知区各 catch | 统一改为 `log.warn(...)`（通知失败仍不影响主流程） |

### B. 未启用功能 / 死代码

| # | 项 | 证据 | 建议 |
| --- | --- | --- | --- |
| B1 | 旧端点 `GET /api/approval/my-requests` | 前端全库 0 调用（api/approval.js 未封装）；文档标注"兼容保留" | 与负责人确认后下线：先在后端加访问日志观测一段时间（migration 20261026 已上线 1 个月），确认无外部脚本调用再删 Controller 方法并同步 architecture.md |
| B2 | `resubmit(Long, AuthUser)` 两参重载 | 全库（含测试）无调用方 | 删除，保留四参版本 |
| B3 | `listRequests(Integer, ...)` 单状态重载 | Controller 只走 List 版 | 删除 |
| B4 | `getConfig()` 无参重载 | 全库无调用方（文档中提及均为历史版） | 删除 |
| B5 | `templateId` 注释残留 | `Application` 头注释与 `fillView` javadoc 提及，代码不读写；审批模板功能已于 2026-10-31 下线 | 清注释（低优先级，随下次改动顺手处理） |

### C. 硬编码检查
- 未发现盘符路径、内网 IP、密码/密钥类硬编码（符合 AGENTS.md 第 18 节）。
- `MANAGE_PATH`、通知 jump_path（`/profile/my-approvals?status=`）为前端路由常量，各一处定义，属可接受。
- 状态字典 0-4：后端 `statusText` 与前端 `utils/format.js approvalStatusText` 已成对同步，符合 17.7 展示层副本约定，无需改。

### D. 可推进的新需求（候选，均需负责人排期）
1. **退回修改后无法更换照片**：resubmit 前端弹窗明示"照片保留原申请内容"，后端 resubmit patch 显式拒绝 PHOTO_FIELDS；而 status=4 不能撤回重提。申请人被退回后没有任何途径换照片 → 建议支持 resubmit 重传照片（复用 photo-upload + TMP: 语义，改动集中 ApplicationController/Service/MyApproval.vue）。
2. **A1 通知缺失修复**（即上表 A1，本质是体验缺陷修复）。
3. 我的申请列表 CONTRACT 类型行的"查看对比"复用专家字段 diff 弹窗，合同单只有身份证号/银行卡号两项，展示是否合适需产品确认。
4. 30s 轮询兜底可升级为通知/长连接驱动（低优先级，现实现已防重入且不重置分页）。

## 6. 测试与验证
| 验证项 | 命令/步骤 | 结果 |
| --- | --- | --- |
| 模块定位 | rg 检索「我的申请」前后端命中 | 通过（范围见第 2 节） |
| 死代码调用面 | rg 全库检索 my-requests / resubmit( / listRequests( / getConfig( | 通过（结论见 B 表） |
| 通知缺失对照 | rg notifyApprovalTodo 命中 262/819，submitRequest 无 | 通过 |
| 硬编码扫描 | 人工通读 Service/Controller/Vue + permission 行 | 无违规项 |
| 编译/构建 | 未执行（本次未改代码，无需构建） | 不适用 |

## 8. 风险
- 本次纯只读审查，无代码/数据库/配置变更，无回滚需求。
- A1/A2 若后续修复，属行为变更（补通知、改校验口径），建议单独提交并跑 `mvn compile` + 现有 `ApplicationConcurrencyGuardTest`。

## 9. 文档同步
- 本记录即交付物；architecture.md 中 my-requests"兼容保留"表述与现状一致，暂不改（若下线 B1 再同步）。

## 10. 下一位 Agent 的接手指令
1. 先与负责人确认 A1/A2 是否批准修复（各为 1-2 行改动+测试）。
2. B2-B4 删除重载前重跑调用面 grep；B1 下线前先加访问日志观测。
3. D1 若立项，按 jshz-agentguifan 技能第 3 步走迁移/API/页面逐层联通（本次无 DB 变更需求）。
4. 未经确认禁止：删除 my-requests 端点、修改权限路径行、发布生产。

## 附：技能安装说明
- 用户要求安装 E:/王创/项目skills/jshz-agentguifan（2026-11-24 新版，含 release-verification）。安装工具拒绝覆盖同名旧版（skill_install_conflict）。新版内容已读入并在本次任务遵循。
- 建议：负责人在 Skill 设置中删除旧版 `jshz-agentguifan` 后，把 E 盘新版复制进工作区（temp/ 已 gitignore）重新安装；或将 E 盘新版改以同名覆盖由用户在设置端更新。

---

# 二、实施记录（2026-11-24，负责人指令：修复 bug/死代码 + 照片重传需求 + 两项确认 + 发布）

## 目标与范围
- 修复第一轮审查的 A1-A4（bug）与 B1-B5（死代码/未启用/过期注释）。
- 需求：退回修改（status=4）后重新提交可更换照片（前后端打通）。
- 确认项：① 合同申请复用对比弹窗的展示；② 「我的申请」列表刷新的驱动机制。
- 不在范围：数据库结构、权限行、环境变量、审批管理页逻辑。

## 实际变更（后端）
- `ApplicationService.submitRequest`：补 `notifyApprovalTodo`（A1）。
- `ApplicationService.withdraw` / `resubmit`：本人判定改用新增私有方法 `isApplicant`（applicant_id 或 applicant 任一匹配，A2）。
- `ApplicationService.maskedOldData`：脱敏字段增加「联系方式」（A3）。
- `ApplicationService`：5 处通知空 catch、快照解析空 catch 补 `log.warn`；`readBiz` 解析失败补 `log.error`（A4）。
- `ApplicationService.resubmit(id, patch, reason, user)`：放开 PHOTO_FIELDS（值须 TMP: 且临时文件存在，沿用 checkChanges）；被替换的旧临时照片名收集后在事务提交后 `deleteTempPhoto`；CONTRACT 仍仅身份证号/银行卡号；非 TMP 值仍被拒。
- 删除死代码：`resubmit(Long, AuthUser)` 两参重载、`listRequests(Integer,...)` 单状态重载、`getConfig()` 无参重载、`ApplicationController.myRequests`（`GET /api/approval/my-requests`）。
- 注释清理：`Application` 实体与 `fillView` javadoc 去掉已下线的 `templateId`；类/方法注释同步下线说明。
- 测试：新增 `ApplicationResubmitTest`（4 例：照片 patch 接受并清理旧临时照片、合同拒照片、非本人拒绝、非 TMP 值拒绝）；`ApplicationTransactionBoundaryTest` 去掉 resubmit/2 条目；`DashboardResubmitIntegrationTest` 两参调用改四参。

## 实际变更（前端）
- `MyApproval.vue` 重新编辑弹窗：非合同申请显示 照片/身份证正面/身份证反面 三行，各带「重新上传」（el-upload + `uploadApprovalPhoto`，大小限制取 `utils/limits` 真源）；提交时把本次重传字段（`TMP:文件名`）并入 changes；提示文案同步。
- `MyApproval.vue` 刷新驱动修正：删除页面自建 30s 定时器，改为 `watch(notificationStore.lastPollAt)` 跟随全局通知轮询（`store/notification.js` 新增 `lastPollAt` 状态，poll 末尾更新）。
- `ApprovalDiffDialog.vue`：`showLegacyTip` 排除 `applicationType === 'CONTRACT'`（合同单无旧值快照且 requestType=0，此前被误判「历史数据」弹黄色提示）；删除不存在的 `returnBy/returnTime` 字段引用（退回单审批人/时间本就落在 approveBy/approveTime）。

## 确认项结论
1. **合同申请对比弹窗**：可用但有一处错误提示（已修）。修正后合同单显示：头部专家/申请人/类型、申请原因、身份证号/银行卡号两行「变更前 - → 变更后 新值」；审批人侧（管理员）看原文，申请人侧看本人提交值，均未扩大可见范围。变更前为空属预期（合同单不产生专家旧值快照）。
2. **轮询驱动**：原注释「通知驱动为主，轮询补位」与实现不符——全局通知 store 与页面各有一个 30s 定时器并行，且新通知到达不会触发页面刷新。已改为单一驱动源（全局 store 轮询 → 页面跟随刷新），刷新频率不变，少一个定时器；真正的服务端推送（SSE/WebSocket）不在本次范围。

## 验证
| 验证项 | 命令/步骤 | 结果 |
| --- | --- | --- |
| 后端单测（隔离目录 temp/test-backend-20261124，JDK 17 / Maven 3.10） | `mvn -f <stage>/backend/pom.xml test -Dtest=Application*Test,DashboardResubmit*Test` | 通过：10 项通过，3 项跳过（`DashboardResubmitIntegrationTest` 需 `DASHBOARD_ACCEPTANCE_DB` 验收库，本机未启用） |
| 前端构建 | `cd frontend && npm run build` | 通过（41.97s，仅有既有 chunk 体积告警） |
| 调用面复核 | `rg "resubmit\(|listRequests\(|getConfig\(\)|my-requests"` main+test | 无残留调用（仅注释中的下线说明） |
| 硬编码复查 | 人工检查本次 diff | 无盘符/IP/密钥/魔法数值新增；照片大小限制取既有真源 |
| 发布预检/发布/冒烟 | 见 `docs/releases/20261124-my-applications-1.0.138.md` | 见发布记录 |

## 风险与回滚
- `GET /api/approval/my-requests` 下线：前端零调用；若存在未知外部脚本调用会得到 404。回滚即恢复旧 jar。
- 重新提交弹窗上传照片后取消：已上传的临时照片会残留在临时目录（与专家新增/编辑弹窗既有行为一致，当前无照片临时目录清理任务，属既有遗留，未在本次扩大范围处理）。
- 页面刷新依赖全局通知轮询（`MessageDrawerBell` 挂载后启动）；若铃铛组件未挂载则页面只做首屏加载与操作后刷新。
- 回滚：`release-full.ps1 -Mode Rollback -Yes`（前端）；后端用蓝绿自带的 `previous-expert-system.jar`。本次无数据库/配置变更，回滚无数据兼容风险。

## 文档同步
- [x] `docs/architecture.md`（Controller 说明 + 2026-11-24 修复条目）
- [x] 最新交接摘要 `docs/handover/README.md`
- [x] 发布记录 `docs/releases/20261124-my-applications-1.0.138.md`
- [ ] `docs/deployment.md`：无部署/环境变量变化，不需要
- [ ] 接口文档：仓库无独立接口文档文件，接口说明以 Controller javadoc 为准（已同步）

## 下一位 Agent 的接手指引
1. 业务验收（需负责人/测试账号）：提交修改申请后审批人铃铛出现待办；退回一张带照片的申请 → 重新编辑弹窗重传照片 → 提交 → 审批通过后专家照片为新图；合同申请「查看对比」不再出现「历史数据」提示。
2. 如需在验收库跑集成测试：设置 `DASHBOARD_ACCEPTANCE_DB=dashboard_acceptance` 与 `DASHBOARD_DB_PASSWORD` 后运行 `DashboardResubmitIntegrationTest`。
3. 未经确认禁止：恢复 my-requests 端点、改动权限行、调整通知类型常量。
