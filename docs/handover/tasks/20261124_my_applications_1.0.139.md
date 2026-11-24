# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261124_my_applications_1.0.139
- 任务名称：「我的申请」板块审查问题全量修复（3×P1 / 6×P2 / 9×P3）+ R1 筛选检索 / R2 处理人与时效 / R5 移动端卡片 + 新增删除功能
- 主责 Agent：WorkBuddy（Claude）
- 交接时间：2026-11-24
- 当前状态：已完成待验收（发布状态见第 6 节与发布记录）
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：把 `docs/reviews/20261124-我的申请-代码审查.md` 中 18 项问题全部关闭，并按审查建议落地 R1、R2、R5 与删除能力，不触碰其它板块。
- 本次范围：`ApplicationController` / `ApplicationService` / `Application` / `ApplicationType`（只读）/ `ApplicationMapper`（只读）/ `ParamUtils` / `MybatisPlusConfig` / `PhotoStorageService` / `OperationLogService` / 新增 `ApprovalTempPhotoCleanTask` / `MyApproval.vue` / `ApprovalDiffDialog.vue` / `PhotoImage.vue`（仅 1 处色值）/ `api/approval.js` / `utils/format.js` / 新增 `utils/approvalFields.js` / `DownloadAudit.vue`（仅新增动作类型文案）/ `application.yml` / `database/init.sql`（仅注释）。
- 不在本次范围：`ApprovalManage.vue` 业务逻辑（仅共享组件被动受益）、审批流配置页、项目/知识星球/消息中心、CONTRACT 前端发起入口（结论见第 7 节）。
- 验收条件：
  - [x] Q1 `biz_data` 损坏时审批被阻断（不再「空审批通过」），并记 ERROR 日志
  - [x] Q2 分页参数双重钳制（page≥1、1≤size≤100）+ 框架 maxLimit=100 + 列表进度记录批量查询
  - [x] Q3 合同重提回写 `PENDING`/`approval_ref`，同一合同禁止两张在审单
  - [x] Q4/Q8/Q10 校验口径（姓名/单位、原因 ≤1000、文本 ≤5000、值必须为字符串）
  - [x] Q5 脱敏口径统一（oldData 与 changes 均明文，与 `ExpertService.toView` 一致）
  - [x] Q6/Q7 前端请求序号、失败态可重试、页码越界回退、size-change 重置页码、URL 白名单
  - [x] Q9 临时照片失效降级剔除 + 保留期清理任务
  - [x] Q11–Q18 日志、死代码、常量收口、CSS 令牌、`approval_ref` 清理、语义细节、文档同步
  - [x] R1 状态/类型/关键词/日期筛选 + URL 回存；R2 当前处理人 + 已等待；R5 ≤768px 卡片视图
  - [x] 删除：仅本人、仅已驳回/已撤回、二次确认、操作日志留痕、结果提示

## 3. 项目基线
- 分支：main
- 当前提交号：见本次 `fix(approval)` 提交与发布记录
- 工作区状态：提交前存在他人遗留的 `docs/deployment.md`（1.0.137.2/1.0.138 发布日志行）、`docs/handover/tasks/20261124_organization_leaders.md`、`docs/releases/20261124-organization-leaders-1.0.137.md` 未提交改动；本次未修改这三处内容，随本次文档提交一并入库（内容属实）
- 版本：`version.txt` 与 `frontend/package.json` 同步至 1.0.139
- 运行环境：本机 8080（NSSM `ExpertSystemBackend`）+ Nginx `<LOCAL_PATH>`
- 以上信息核实时间：2026-11-24 17:00 前后

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据或说明 |
| --- | --- | --- | --- |
| Q1 审批阻断 | `ApplicationService.readBiz/approveModify/approveContract` | 完成 | 单测 `approveModifyBlocksCorruptedBizData`/`approveModifyBlocksMissingChanges` |
| Q2 分页与 N+1 | `ApplicationController`、`ApplicationService`、`MybatisPlusConfig`、`ParamUtils.clamp` | 完成 | 列表 `fillViews` 一次 IN 查询；单测 `clampKeepsPageAndSizeWithinBounds` |
| Q3 合同重提与判重 | `ApplicationService.resubmit/syncContractByApproval/ensureNoOtherOpenContractApplication` | 完成 | `approval_ref` 在审指向本单、离开在审置空 |
| Q4/Q8/Q10 校验 | `checkChanges`/`validateModifyChanges`/`validateReason`/Controller | 完成 | 单测 `resubmitRejectsBlankName` |
| Q5 脱敏统一 | `fillView`（删除 `maskedOldData`/`isFullSensitive`） | 完成 | 见架构文档「脱敏口径统一」 |
| Q6/Q7 前端健壮性 | `MyApproval.vue` | 完成 | 请求序号 + 失败态 + 页码回退 + 筛选白名单 |
| Q9 临时照片 | `resubmit` 降级 + `PhotoStorageService.cleanTempPhotos` + `ApprovalTempPhotoCleanTask` | 完成 | 单测 `resubmitDropsMissingTempPhotoInsteadOfFailing` |
| Q11–Q18 | 见架构文档 1.0.139 段 | 完成 | 死代码/常量/令牌/注释/文档 |
| R1/R2/R5 | `myApplications` 筛选项、`fillTimeliness`、`MyApproval.vue` 卡片视图 | 完成 | 前端构建通过 |
| 删除功能 | `DELETE /api/approval/requests/{id}`、`deleteApplication`、前端入口 | 完成 | 4 条单测覆盖允许/拒绝/非本人 |

## 5. 实际变更
- 新增文件：`backend/.../task/ApprovalTempPhotoCleanTask.java`、`backend/src/test/.../ApplicationApprovalGuardTest.java`、`frontend/src/utils/approvalFields.js`
- 修改文件：后端 `ApplicationController`、`ApplicationService`、`Application`、`ParamUtils`、`MybatisPlusConfig`、`PhotoStorageService`、`OperationLogService`、`application.yml`；前端 `MyApproval.vue`、`ApprovalDiffDialog.vue`、`PhotoImage.vue`、`api/approval.js`、`utils/format.js`、`views/DownloadAudit.vue`；文档 `docs/architecture.md`、`docs/deployment.md`、`database/init.sql`（注释）、`docs/reviews/20261117-...`（更正注记）、`docs/handover/README.md`
- 数据库/迁移：**无结构变更、无迁移脚本、无权限码变更**；仅 `database/init.sql` 两处列注释同步（parity 只比对表名，不影响一致性校验）
- API/接口契约：`GET /api/approval/my-applications` 增 `type`/`keyword`/`startDate`/`endDate`（向后兼容，旧参数不变）；新增 `DELETE /api/approval/requests/{id}`；`Application` 响应增 `currentAssigneeName`/`pendingHours`（新增字段，旧前端忽略）
- 页面/路由/权限：无路由与权限码变化（删除接口沿用「登录即可 + Service 本人校验」，与 withdraw/resubmit 一致）
- 配置/环境变量：`expert.photo.temp-retention-days`（`APPROVAL_TEMP_PHOTO_RETENTION_DAYS`，默认 7）、`expert.photo.temp-clean-cron`（`APPROVAL_TEMP_PHOTO_CLEAN_CRON`，默认 `0 10 3 * * ?`），默认值等于「不清理由此前也未清理」的行为基线，已登记 deployment.md
- 依赖变更：无

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 证据/备注 |
| --- | --- | --- | --- |
| 后端编译 | `mvn -o compile -DgitShort=devcheck -Drevision=1.0.139`（backend/） | 通过 | 无告警级错误 |
| 审批相关单测 | `mvn -o test -Dtest='ApplicationApprovalGuardTest,ApplicationResubmitTest,ApplicationConcurrencyGuardTest,ApplicationTransactionBoundaryTest'` | 通过 | 19 例全绿（新 9 + 旧 10） |
| 后端全量单测 | `mvn -o test`（308 例） | 通过（1 例环境门控） | 0 failure；`ExpertSystemApplicationTests.contextLoads` 因未注入 `JWT_SECRET` 报 WeakKey，注入后单跑通过（19.94s），属环境前置条件非本次改动 |
| 前端构建 | `cd frontend && npm run build` | 通过 | 54.3s，产物含 `MyApproval-*.js` |
| 接口/页面验证 | 登录后手工验证（发布记录第 6 节） | 见发布记录 | 无获准测试账号时记为待验收 |
| 部署健康检查 | `release-full.ps1` 发布后 5 项自检 | 见发布记录 | — |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| CONTRACT 前端发起入口 | 审查 Q12 结论为「后端就绪、前端未开放」 | 合同审批只能靠接口触发 | 需产品决策是否启用；启用后补项目管理结算合同页入口 | 产品负责人 |
| 登录后业务验收 | 无获准测试账号 | 页面交互未实测 | 由负责人用真实账号按发布记录清单逐条验收 | 项目负责人 |

## 8. 风险与回滚
- 已知问题：`my-applications` 的 `keyword` 使用 `JSON_EXTRACT` 无索引，个人数据量级（千级）可控；若单用户单据超万级需引入普通列或虚拟列。
- 数据风险：删除功能为物理删除（含进度记录），已限制为「本人 + 已驳回/已撤回」并写 `operation_log` 留痕；不可恢复。
- 兼容性风险：`oldData` 由脱敏改为明文，是可见性扩大（受众仍限本人与审批人）；`resubmit` 现在对「临时照片丢失」不再报错而是剔除该字段，行为更宽松。
- 回滚步骤：前端 `scripts/release-full.ps1 -Mode Rollback -Yes`；后端使用本次蓝绿自带的 `previous-expert-system.jar`。
- 回滚限制：无数据库变更，回滚无数据补偿需求。

## 9. 文档同步
- [x] 架构文档（`application`/`approval_config` 表说明、五态、templateId 下线、脱敏口径、1.0.139 变更段）
- [x] 部署文档（新增 2 个环境变量）
- [x] 接口文档（Controller javadoc 与 `api/approval.js` 注释）
- [x] 数据库注释（`database/init.sql`）
- [x] 测试说明（本文件第 6 节）
- [ ] 发布说明（发布完成后补 `docs/releases/20261124-my-applications-1.0.139.md`）
- [x] 最新交接摘要（`docs/handover/README.md` 新增首条）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`curl http://127.0.0.1:8080/health` 的 `version` 是否为 1.0.139；`git log -1` 与 `logs/release-state.json` 是否一致。
2. 然后执行：用具备审批权限的账号验收「我的申请」的筛选、当前处理人/已等待、移动端卡片（浏览器 375px）、删除（撤回一张单后再删）、退回单重提。
3. 验收标准：见第 2 节勾选项与发布记录「业务验收」清单。
4. 未经确认禁止：删除 status=0/1 的申请单（接口已拒绝，不得放宽）；修改脱敏口径；启用 CONTRACT 前端入口。
5. 需要负责人决策：CONTRACT 是否启用；`oldData` 明文口径是否接受（当前与专家详情一致）。
