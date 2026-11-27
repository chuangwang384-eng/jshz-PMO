# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261127-FINANCE-ARCHIVE-ENTRY
- 任务名称：合规立项申请表独立入口（在线预览 / 下载当前生效版）+ 服务端必填校验与中文字段错误定位
- 主责 Agent：AutoClaw（本会话）
- 交接时间：2026-11-27
- 当前状态：已完成待验收（随 1.0.159 一并发布）
- 优先级：P1（新需求 1 + 新需求 3）
- 前置输入：本会话审查报告「第二部分：新需求建议」第 1 条与第 3 条；同批 P0/P1/P2/P3 四批整改。

## 2. 目标与验收条件
- 业务目标：
  - **新需求 1**：合规立项申请表独立入口 —— 用户不必在通用附件列表里辨认哪一份是系统生成的申请表，可直接在线预览或下载**当前生效版**。
  - **新需求 3**：字段白名单 + **服务端必填校验** + 中文字段错误定位 —— 白名单与中文字段名已在 P1-⑨ 落地，本批补齐**服务端必填闸门**（含恒瑞条件必填），使服务端成为最终闸门而非仅靠前端校验。
- 本次范围：会前财务资料上传阶段相关前后端文件、1 个新增回归测试、本记录与架构文档。
- 不在本次范围：数据库结构、权限码、环境变量、部署脚本零改动；模板不修改。
- 验收条件：
  - [x] 独立入口：展示当前生效版（文件名/生成人/生成时间），提供「在线预览」「下载当前生效版」两个动作；
  - [x] 独立入口：非真实项目参与人拿不到文件路径（previewable=false），与归档件 403 口径一致；
  - [x] 服务端必填：缺失项一次性用中文字段名列出（含恒瑞条件必填），拒绝写入且不产生归档件；
  - [x] 白名单与中文字段名沿用 P1-⑨ 实现（同一套 `Field` 定义，前后端口径一致）；
  - [x] 新增后端回归 9 例，见红 3 失败 + 1 错误 → 见绿 9/9；
  - [x] 后端全量 446 例 0 失败；前端 `npm run build` 通过；
  - [ ] 生产环境验证（发布后另行验收）。

## 3. 项目基线
- 分支：main；起点提交 `8758976`（工作区起点干净）。
- 版本：`version.txt` revision=1.0.159（与 P0/P1/P2/P3 同批发布，未新增 bump）。
- 生产实况（2026-11-27 发布前只读核实）：`GET http://127.0.0.1:8080/health` → version=1.0.158、gitShort=ea37672、status=UP；Nginx root = `<LOCAL_PATH>`。
- 备份位置：`<LOCAL_PATH>`（改动前 6 个源文件逐份副本 + 实施后快照 `.FIXED.java`）。

## 4. 已完成工作

| 需求 | 内容 | 状态 | 证据 |
| --- | --- | --- | --- |
| 新需求 1-a（后端） | 新增 `GET /api/project/{id}/finance-archive?phaseId=`（`ProjectService.financeArchive`）：返回**当前生效的那一份**归档件（`project_finance_` 前缀 + 物理文件存在 + 取 id 最大者），字段 `{archived, previewable, fileName, filePath, uploadBy, createTime}`。非真实项目参与人只拿到 `{archived:false, previewable:false}`，**不返回 filePath**（与阶段表单脱敏、归档件下载/预览 403 同口径） | 完成 | 单测 `archiveEntryReturnsTheCurrentEffectiveArchiveForMembers`、`archiveEntryHidesTheFilePathFromNonParticipants`、`archiveEntryReportsNotArchivedWhenNoFileExists`、`archiveEntryRequiresAReadableProject` |
| 新需求 1-b（前端） | `ProjectFinanceForm.vue` 内新增「合规立项申请表」独立入口卡片：标题 + 「系统生成」标签 + 当前生效版文件名/生成人/生成时间 + 「在线预览」「下载当前生效版」两个按钮（`emit('preview'|'download')` → 复用 `ProjectDetail` 既有的 `openFilePreview` / `downloadProjectFile`）。保存成功后自动刷新入口。**原先 P0-① 在阶段资料区的归档卡片已移除**，全站只剩这一处入口（避免两处同名不同义） | 完成 | ProjectFinanceForm.vue 模板 + `loadArchive()`；`api/project.js` 新增 `getFinanceArchive` |
| 新需求 3（服务端必填） | `ProjectFinanceWorkbook.missingRequiredFields(projectType, values)` 返回缺失必填项的**中文名列表**（含「恒瑞」条件必填：分公司名称含恒瑞时，提供内容分节其余字段一并必填）；`saveStageForm` 在生成归档件**之前**校验，缺失即抛「请填写财务表单必填项：A、B、C」，**不写库、不产生归档件** | 完成 | 单测 `missingRequiredFieldsAreReportedWithChineseLabels`、`hengRuiCompanyMakesTheWholeSectionRequired`、`otherCompanyOnlyNeedsCompanyNameAndProvince`、`savingWithMissingRequiredFieldsIsRejectedByTheServer`、`savingWithAllRequiredFieldsProceeds` |
| 附带收口 | `projectFileDisplayName` 从 `ProjectDetail.vue` 私有函数上移到 `utils/format.js`（签名改为 `(doc, projectName)`），供阶段资料区与独立入口共用同一显示名规则（AGENTS §19.1「第 3 处复制即收口」） | 完成 | `utils/format.js` + 两个消费方 |

## 5. 实际变更
- 新增文件：
  - `backend/src/test/java/com/jshz/expert/service/ProjectFinanceArchiveEntryTest.java`（9 例）；
  - 本记录 `docs/handover/tasks/20261127_finance_archive_entry.md`。
- 修改文件（6 个源文件 + 文档）：
  - `backend/.../service/ProjectFinanceWorkbook.java`：新增 `missingRequiredFields`（服务端必填判定，与 `Field.isRequired` 共用条件必填口径）；
  - `backend/.../service/ProjectService.java`：新增 `financeArchive`（独立入口）；`saveStageForm` 增加服务端必填闸门；新增 `java.util.Comparator` 导入；
  - `backend/.../controller/ProjectController.java`：新增 `GET /{id}/finance-archive`；
  - `frontend/src/api/project.js`：新增 `getFinanceArchive`；
  - `frontend/src/utils/format.js`：新增 `projectFileDisplayName(doc, projectName)`；
  - `frontend/src/views/project/components/ProjectFinanceForm.vue`：独立入口卡片 + `archive` 状态 + `loadArchive()` + `preview`/`download` 事件；
  - `frontend/src/views/project/ProjectDetail.vue`：接入 `@preview`/`@download`；移除 P0-① 的归档卡片与 `financeArchiveDoc` 计算属性；改引 `utils/format` 的显示名函数。
- 数据库/迁移：**无**（本次仍无 DDL，1.0.159 保持「无迁移」形态）。
- API 契约变更（新增一个只读端点，向后兼容）：
  - `GET /api/project/{id}/finance-archive?phaseId=X` → `{archived:boolean, previewable:boolean, fileName?, filePath?, uploadBy?, createTime?}`。
  - 登录即可（沿用 `/api/project/phase-options` 同口径，不新增 permission 规则）。
  - `PUT /api/project/{id}/stage-form` 行为变更：必填缺失时**由服务端拒绝**（此前仅前端校验，服务端在生成阶段才间接失败）。
- 权限：无权限码/角色绑定改动。
- 配置/环境变量：无新增。

## 6. 测试与验证

| 验证项 | 执行命令/步骤 | 结果 | 备注 |
| --- | --- | --- | --- |
| 见红演练 | 把 `financeArchive` 置为「改动前无独立入口」占位、并移除 `saveStageForm` 的服务端必填闸门，跑 `mvn -o "-Dtest=ProjectFinanceArchiveEntryTest" test` | **3 失败 + 1 错误**：归档件信息取不到（archived 恒 false）、非成员仍被判可预览、无独立入口时不报权限错、另有 1 处严格桩残留 | 失败原因均为待实现行为或原缺陷 |
| 目标回归 | 恢复实现后同一命令 | **9/9 通过** | |
| 后端全量回归 | `mvn -o "-Dspring-boot.build-info.skip=true" test`（JWT_SECRET 已设临时值） | **446 例，0 失败 0 错误，16 跳过**，BUILD SUCCESS | 437（P0~P3）+ 9（本批） |
| 前端构建 | `npm run build` | 通过（built in 2m 14s） | |
| 模板一致性 | 本批未改模板，模板相关守卫 `ProjectFinanceTemplateAlignmentTest` 仍全绿 | 通过 | |
| 页面手工回归 | — | **未执行** | 需发布后验收（清单见第 10 节） |

## 7. 未完成事项与阻塞

| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 模板口径不一致 ①/②、跨日日期两列（沿用 P1/P2/P3 批次） | 见对应交接记录 | 同前 | 同前 | 负责人 |
| 发布 1.0.159 | 本批次已获授权执行 | — | 见第 8/9 节 | 负责人 |
| 审查报告其余新需求 2/4/5/6/7/8 | 本次只做新需求 1 与 3 | — | 另开任务 | 负责人 |

## 8. 风险与回滚
- 已知问题：
  - **服务端必填闸门会拒绝历史缺项数据**：若某个项目此前已保存过缺必填项的表单（P1-⑨ 之前服务端宽松存储），现在重新保存会被拦，需补齐字段。错误信息已一次性列出全部缺失项，用户可一次补完。
  - **独立入口的「当前生效版」以物理文件存在为准**：若归档件文件被外部删除，入口显示「尚未生成」（与必填判定一致），此时重新保存表单即可重建。
  - **非成员看不到入口详情**：仅返回 `{archived:false, previewable:false}`，前端显示「仅项目成员可查看」；这是 P0-④ 的既定口径，非本批新增。
- 数据风险：低。服务端必填只拦截写入，不修改既有数据；独立入口为纯只读端点。
- 兼容风险：旧前端 + 新后端 → 旧前端不带 `version`（P3-⑳ 已兼容）、不使用新端点，行为不变；新前端 + 旧后端 → 独立入口取不到数据（显示「尚未生成」），且新前端不发送 `version` → 与旧行为一致。
- 回滚步骤：`git revert` 本次提交（6 个源文件 + 1 个测试 + 文档，无数据迁移需回滚）；手工兜底从 `temp/backup/20261127-finance-archive-entry/` 逐份还原。

## 9. 文档同步
- [x] 架构文档（`docs/architecture.md` 新增「合规立项申请表独立入口与服务端必填校验（1.0.159）」小节）
- [x] 交接记录（本文件）
- [x] 最新交接摘要（`docs/handover/README.md`）
- [x] 测试说明（本记录第 6 节）
- [ ] 部署文档（无迁移/环境变量变更；发布记录随发布任务生成）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`；`mvn -o "-Dtest=ProjectFinanceArchiveEntryTest" test` 应 9/9；`npm run build` 应通过。
2. 发布后手工回归：
   - 进入会前财务资料上传阶段 → 出现「合规立项申请表」独立入口，含「在线预览」「下载当前生效版」；
   - 在线预览能打开表格分页预览；下载得到的是**当前生效**那一份；
   - 保存表单后入口信息刷新为最新生成人/时间；
   - 通用附件清单里不再出现该申请表（仍只有这一处入口）；
   - 服务端必填：直调接口提交缺「我方公司」的表单 → 返回「请填写财务表单必填项：我方公司」；
   - 直付类公司名称填「恒瑞」时，缺事业部/产品线/记账部门/SFA 的提交被拒，错误列出这四项。
3. 未经确认禁止：修改模板；放宽服务端必填闸门；让非成员拿到归档件 filePath。
