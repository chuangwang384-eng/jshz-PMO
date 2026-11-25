# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261125_my_applications_p2p3_detail_drawer
- 任务名称：「我的申请」第二轮审查 P2/P3 修复 + 申请详情抽屉（需求 C）+ 批量删除（参照消息中心）+ 发布上线
- 主责 Agent：ZCode
- 交接时间：2026-11-25
- 当前状态：1.0.140 已发布；**1.0.141 追加修订（同日，见文末）**
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：关闭 2026-11-25 「我的申请」只读审查列出的 6 项 P2 与 12 项 P3（不含 P1「退回修改单可撤回」，该项未获批准，仅修正删除接口的误导文案）；新增申请详情抽屉；参照消息中心补齐批量删除。
- 本次范围：`ApplicationService` / `ApplicationController` / `Application` 实体 / 新增 `ApplicationMyApplicationsHardeningTest`；`views/MyApproval.vue` / `components/ApprovalDiffDialog.vue` / `utils/approvalFields.js` / `api/approval.js`。
- 不在本次范围：审批管理页与审批流配置页业务逻辑、合同发起链路、专家库页面、数据库结构与权限码。
- 验收条件：
  - [x] 重提时临时照片失效不再静默成功（响应带 `droppedPhotoFields`，列表/重提弹窗标「照片失效」）
  - [x] 重提弹窗清空非必填字段 = 撤回该变更，不再把空串写入专家档案
  - [x] 移动端弹窗/抽屉按屏宽自适应
  - [x] 30 秒轮询静默刷新，不再闪加载遮罩；已打开的详情按 id 跟随最新数据
  - [x] 合同申请的待办/撤回通知文案不再显示为「专家信息修改申请」
  - [x] P3：关键词条件、排序兜底、提交接口类型校验、响应瘦身、死代码、字典收口、过期注释、深链参数、缺失 catch、样式
  - [x] 需求 C：详情抽屉（对比 + 进度时间线 + 行内操作）
  - [x] 批量删除：多选 + `DELETE /api/approval/requests/batch`，口径同单条删除
  - [ ] 登录后业务验收（测试账号名下无「已驳回/已撤回/退回修改」单据，批量删除执行与重提弹窗未能在浏览器实测）

## 3. 项目基线
- 分支：main；起点提交 9a96fbc（1.0.139）
- 工作区：起点存在他人未提交改动（`docs/deployment.md`、`docs/handover/tasks/20261124_organization_leaders.md`、`docs/releases/20261124-organization-leaders-1.0.137.md`、若干 `frontend/release-metadata/*`），本次未触碰、未提交
- 版本：1.0.140（`version.txt` 与 `frontend/package.json` 同步）
- 运行环境：本机 8080（NSSM `ExpertSystemBackend`）+ Nginx
- 以上信息核实时间：2026-11-25 08:25（`curl /health` version=1.0.139 gitShort=9a96fbc）

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据或说明 |
| --- | --- | --- | --- |
| P2-2 失效照片回传 | `ApplicationService.resubmit/fillView`、`Application` | 完成 | 单测 `resubmitReportsDroppedPhotoFields`、`fillViewMarksExpiredPhotosOnReturnedApplication` |
| P2-3 清空即撤回 | `ApplicationService.resubmit` | 完成 | 单测 `resubmitBlankTextFieldWithdrawsThatChange`、`resubmitBlankRequiredFieldStillRejected`、`resubmitBlankOnCreateRequestKeepsPreviousSemantics` |
| P2-4 移动端弹窗 | `MyApproval.vue`、`ApprovalDiffDialog.vue` | 完成 | 浏览器 760px 卡片视图实测；重提弹窗宽度仅代码核验 |
| P2-5 静默轮询 | `MyApproval.vue` | 完成 | 浏览器 36 秒观察：列表接口多次刷新、加载遮罩 0 次 |
| P2-6 通知文案 | `ApplicationService.notifyApprovalTodo/Withdrawn` | 完成 | 单测 `contractResubmitNotifiesWithContractTypeText`；合同发起处补通知**未做**（见第 7 节） |
| P3 各项 | 见第 5 节 | 完成 | 第 6 节 |
| 需求 C 详情抽屉 | `MyApproval.vue`、`ApprovalDiffDialog.vue`（新增 `variant`/`drawerSize`/`extra` 插槽） | 完成 | 浏览器实测截图 |
| 批量删除 | `ApplicationController.deleteBatch`、`ApplicationService.deleteApplications`、`MyApproval.vue` | 完成 | 单测 `batchDeleteRemovesOnlyOwnTerminalApplications`、`batchDeleteIsIdempotentOnDuplicatesAndRejectsOthersSilently` |

## 5. 实际变更
- 新增文件：`backend/src/test/.../ApplicationMyApplicationsHardeningTest.java`、本记录
- 修改文件：
  - 后端：`ApplicationService.java`、`ApplicationController.java`、`entity/Application.java`
  - 前端：`views/MyApproval.vue`（整体重写模板与脚本）、`components/ApprovalDiffDialog.vue`、`utils/approvalFields.js`、`api/approval.js`
  - 版本：`version.txt`、`frontend/package.json`
- 数据库/迁移：无
- API/接口契约：
  - 新增 `DELETE /api/approval/requests/batch`，body `{ids:[…]}`（≤100），返回实际删除条数；不可删条目（非本人/非终态/不存在）跳过
  - `POST /api/approval/requests/{id}/resubmit` 响应新增 `droppedPhotoFields`（被剔除的失效照片键）
  - 列表/详情响应新增 `expiredPhotoFields`（退回修改单里临时文件已丢失的照片键）；`bizData`、`approverSnapshot` 两个字段**不再随响应返回**（新增字段为增量，旧前端忽略）
  - 提交修改/新增/合同三个接口的 `changes`/`reason` 类型错误由 500 改为 400
- 页面/路由/权限：无路由与权限码变化；批量删除沿用「登录即可 + Service 本人校验」
- 配置/环境变量：无
- 依赖变更：无
- 状态字典联动（17.7）：本次未新增/修改任何状态值；前端字典副本新增 `CREATE_REQUIRED_FIELDS`、`NAME_PATTERN`（对应后端 `CREATE_REQUIRED_FIELDS`、`NAME_PATTERN`），移除零调用的 `applicationTypeText`

### P3 逐项落点
| # | 处理 | 位置 |
| --- | --- | --- |
| 7 | 删除 `like(applicant)` 条件；关键词 `%`/`_`/`/` 转义（`ESCAPE '/'`） | `ApplicationService.myApplications/escapeLike` |
| 8 | 排序追加 `orderByDesc(id)` | `ApplicationService.myApplications` |
| 9 | `typedChanges`/`typedReason` 类型校验 | `ApplicationController` |
| 10 | `bizData`、`approverSnapshot` 加 `@JsonIgnore`；进度记录批量查询保持不变（详情抽屉需要完整时间线） | `Application.java` |
| 11 | 删除 `extraKeys`、`PHOTO_KEYS` 别名、`applicationTypeText` | `MyApproval.vue`、`approvalFields.js` |
| 12 | 前端必填字段/姓名正则入 `approvalFields.js`；后端新增 `CONTRACT_FIELDS`、`MY_APPLICATIONS_PATH` 常量并合并两条重复判断 | `approvalFields.js`、`ApplicationService` |
| 13 | `oldData`/`currentAssignee` 过期注释更正 | `Application.java` |
| 14 | 深链打开后从 URL 移除 `applicationId`；筛选 watch 增加路由名判断 | `MyApproval.vue` |
| 15 | `confirmResubmit` 补 catch | `MyApproval.vue` |
| 16 | **未改**：核实 `uploadLimits` 自带与 yml 一致的默认值，「限额未加载时校验失效」不成立（审查结论有误，特此更正）；同字段重复上传的孤儿临时文件未处理（无临时文件删除端点，见第 7 节） |
| 17 | 敏感字段格式校验限定到本次改动键 | `ApplicationService.validateSensitiveFields(changes, touched)` |
| 18 | 去掉 `!important`；`.filter-bar` 挂 `ui-toolbar`；`ApprovalDiffDialog` 的 `4px` 圆角与令牌值（8px）不等，按「渲染不变」原则**保留** | `MyApproval.vue` |
| 19 | **未改**（低优先，仅多一次请求，结果由请求序号保证正确） | — |
| 附带 | 对比弹窗中退回修改单的临时照片此前被误标「已清理」，实际退回时保留，改为可预览 | `ApprovalDiffDialog.vue`（审批管理页同样受益） |

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 证据/备注 |
| --- | --- | --- | --- |
| 审批相关单测 | 隔离目录 `temp/p140/backend`：`mvn -o test -Dtest='Application*Test,DashboardResubmit*Test' -DargLine=-Xmx512m` | 通过 | 34 例：31 通过 / 3 跳过（`DashboardResubmitIntegrationTest` 需验收库环境变量）/ 0 失败；新增 12 例 |
| 后端全量单测 | 同目录 `mvn -o test` | 见发布记录 | 结果登记于发布记录 |
| 前端构建 | `npx vite build --outDir ../temp/p140/dist-check`（临时目录，未触碰线上 dist） | 通过 | 40.4s；`is-not-exported` 类告警 0 条 |
| 页面验证（浏览器，测试账号 测试，新前端连线上旧后端） | 列表、勾选列禁用态、详情抽屉、760px 卡片视图、36 秒静默轮询观察 | 通过 | 抽屉内含申请信息/对比/时间线；轮询期间加载遮罩 0 次 |
| 权限/安全验证 | 代码核验 + 单测：批量删除跳过非本人与非终态 | 通过（未做越权实测） | 建议负责人用普通账号直调接口复核 |
| 部署健康检查 | `release-full.ps1` 发布后自检 + 独立复核 | 见发布记录 | — |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| P1「退回修改单可撤回」 | 不在本次授权范围（仅按要求修正了删除接口的误导文案） | 退回单仍只能重提；审批流停用则滞留；合同单滞留会阻塞再次发起 | 待负责人批准后实现 4→3 撤回 | 项目负责人 |
| 合同发起提交不发待办通知 | 修复点在合同提交链路（项目模块），本次限定不动其他板块 | 合同审批人收不到新合同审批的待办通知 | 待负责人确认后补一行 `notifyApprovalTodo` | 项目负责人 |
| 同字段重复上传的孤儿临时文件 | 无「删除单个临时文件」端点；保留期清理任务 7 天内会回收 | 仅占磁盘，无功能影响 | 评估是否加清理端点 | 项目负责人 |
| 登录后业务验收 | 测试账号无目标状态单据 | 批量删除执行、重提弹窗、失效照片提示未实测 | 见第 10 节 | 项目负责人 |

## 8. 风险与回滚
- 已知问题：`bizData`/`approverSnapshot` 不再返回——仓库内前端零引用，但若有外部脚本读取这两个字段会取到空。
- 数据风险：批量删除为物理删除（含进度记录），口径与单条删除一致并写 `operation_log`，不可恢复。
- 兼容性风险：清空字段的语义变化（空串 → 撤回变更）仅对带旧值快照的修改申请生效；审批管理页对比弹窗对退回单现在会显示临时照片缩略图。
- 回滚步骤：前端 `scripts/release-full.ps1 -Mode Rollback -Yes`；后端使用本次蓝绿自带的 `previous-expert-system.jar`。
- 回滚限制：无数据库变更，回滚无数据补偿需求。

## 9. 文档同步
- [x] 架构文档（`docs/architecture.md` 新增 1.0.140 段）
- [x] 部署文档：无部署/环境变量变化，不需要
- [x] 接口文档：仓库无独立接口文档，接口说明以 Controller javadoc 与 `api/approval.js` 注释为准（已同步）
- [x] 数据库迁移/数据字典：无变更
- [x] 测试说明（本文件第 6 节）
- [x] 发布记录 `docs/releases/20261125-my-applications-1.0.140.md`
- [x] 最新交接摘要 `docs/handover/README.md`

## 10. 下一位 Agent 的接手指令
1. 首先检查：`curl http://127.0.0.1:8080/health` 的 version 是否为 1.0.140；`logs/release-state.json` 的 status。
2. 然后执行（需具备目标状态单据的账号）：
   - 提交一张修改申请 → 撤回 → 列表勾选该行 → 批量删除，确认提示「已删除 1 张」且行消失；
   - 待审批单的勾选框应为禁用；直接调用 `DELETE /api/approval/requests/batch` 传他人单据 id，应返回 0 且对方数据不变；
   - 退回修改单：重提弹窗清空一个非必填字段并提交，审批通过后专家该字段保持原值；
   - 窄屏（≤768px）打开详情与重新编辑弹窗，不应溢出。
3. 验收标准：第 2 节勾选项。
4. 未经确认禁止：放宽批量/单条删除的状态限制（待审批、已通过、退回修改不可删）；恢复 `bizData`/`approverSnapshot` 的返回；启用 P1 退回单撤回。
5. 需要负责人决策：P1 退回单撤回；合同发起通知；孤儿临时文件清理端点。


---

# 追加修订：1.0.141（2026-11-25 当日，负责人反馈）

## 反馈与处理
- 负责人实测「删除功能无法使用」。排查（Nginx access log 无 DELETE 请求）确认根因：删除口径原为「仅已驳回/已撤回」，负责人名下 14 张可删单据虽存在，但其主要单据均为「已通过」——界面上没有删除入口，属**设计口径不符预期**而非功能缺陷。
- 负责人决策：**「已通过」也可删除**。已实现（单条 + 批量同口径）：后端 `deleteApplication`/`deleteApplications` 状态条件放宽为 {1,2,3}，专家档案不受影响，变更留痕由 operation_log 保留；前端 `canDelete` 同步、删除确认文案更新；`GET /approval/my-applications` 无变化。
- 「变更摘要」列按负责人要求去掉（桌面表格列 + 移动端卡片行，同一信息在详情抽屉仍完整可见）；`changeSummary` 不再被本页引用，已移除 import。
- 验证：审批相关单测 35 例全绿（Guard 10 例：新增 `deleteAllowsApprovedForOwner`，`deleteRejectsNonTerminalOrApproved` 改为仅待审批/退回修改拒绝；Hardening 12 例：批量删除混合批次期望 2→3）；本次单测已设 `LOG_DIR` 隔离，生产日志无污染。前端构建通过。浏览器实测（测试账号，新前端连线上旧后端）：变更摘要列已消失、已通过行勾选框可用且出现「删除」按钮、确认弹窗文案正确、请求发出后被旧后端拒绝并正确提示（旧口径预期行为）。
- 版本：1.0.141；发布记录 `docs/releases/20261125-my-applications-1.0.141.md`。
