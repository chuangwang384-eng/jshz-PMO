# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261126-FINANCE-STAGE-P1
- 任务名称：会前财务资料上传阶段 P1 八项整改（专属空态 / 成员口径 / 主数据缺失引导 / 恒瑞口径 / 字段白名单 / 必填定位 / 快照语义 / 归档批量替换）
- 主责 Agent：AutoClaw（本会话）
- 交接时间：2026-11-26
- 当前状态：已完成待验收（**未发布**：后端 jar 与前端快照均未切换，生产仍为 1.0.158）
- 优先级：P1
- 前置输入：
  - 审查报告（本会话《会前财务资料上传阶段 — 开发审查报告》，P1 共 8 项）；
  - [P0 四项整改交接](20261126_finance_stage_p0.md)（同批次，1.0.159 内一并发布）。

## 2. 目标与验收条件
- 业务目标：修掉审查报告 P1 八项——阶段未开始文案误导、前后端成员口径不一致、主数据缺失无法修正、直付类「恒瑞」规则未落地、表单接口无字段白名单、必填校验不定位、归档件与页面口径未说明、归档替换命中不确定。
- 本次范围：仅「项目管理 → 会前财务资料上传」阶段相关前后端文件、1 个新增回归测试、本记录与架构文档。审查范围外模块零改动。
- 不在本次范围：
  - 审查报告 P2（9 项）、P3（6 项）全部未动；
  - 新需求建议 8 条未实施；
  - 数据库结构、权限码、环境变量、部署脚本零改动；
  - **模板两处口径不一致未改模板**（见第 7 节「模板待确认」），需负责人拍板。
- 验收条件：
  - [x] P1-⑤ 阶段未开始显示专属空态，不再出现「暂未配置」；
  - [x] P1-⑥ 前端成员口径改用后端 `detail.canOperate`，消除「可编辑但保存 403」；
  - [x] P1-⑦ 主数据字段缺失时给出直达「编辑项目」的入口；
  - [x] P1-⑧ 直付类「公司名称」可填「恒瑞」等实际提供方，并按「恒瑞需填写所有信息」动态必填；
  - [x] P1-⑨ 后端按项目类型字段白名单清洗，类型不符拒绝且只报中文字段名；
  - [x] P1-⑩ 必填校验改 el-form rules + scroll-to-error，逐项标红定位；
  - [x] P1-⑪ 归档件与表单 banner 均标注「保存时快照」语义；
  - [x] P1-⑫ 归档替换改批量软删同前缀旧件 + 提交后清理旧文件；
  - [x] 新增后端回归 9 例，见红 6 失败 + 1 错误 → 见绿 9/9；
  - [x] 后端全量 413 例 0 失败；前端 `npm run build` 通过；
  - [ ] 生产环境验证（发布后另行验收）。

## 3. 项目基线
- 分支：main；起点提交 `8758976`（工作区起点干净）。
- 版本：`version.txt` revision=1.0.159（P0 批次已 bump，未发布）；本次 P1 属同一未发布版本，**不再重复 bump**，`frontend/package.json` 同步 1.0.159。
- 生产实况（2026-11-26 只读核实）：`GET http://127.0.0.1:8080/health` → version=1.0.158、gitShort=ea37672、status=UP。本次未发布、未重启服务。
- 备份位置：`<LOCAL_PATH>`（改动前 4 个源文件逐份副本；`.P1FIXED.java` 为实施后快照，供见红演练）。

## 4. 已完成工作（对照审查报告 P1 编号）

| 发现 | 内容 | 状态 | 证据 |
| --- | --- | --- | --- |
| P1-⑤ | 阶段未开始显示「暂未配置」 | 完成 | 前端改为 `isFinanceViewing` 分支内按 future 渲染专属空态「进入本阶段后可填写会前财务资料并生成合规立项申请表」；随之失效的 `PLACEHOLDER_PHASE_NAMES` / `isPlaceholderPhaseViewing` 及其 `v-else-if` 分支整体删除（原本已不可达的结项项一并清理） | ProjectDetail.vue 模板 + 脚本常量区 |
| P1-⑥ | 前端 `isMember` 与后端 `isProjectMember` 口径不一致 | 完成 | 前端 `canOperate` 改为直接取后端下发的 `detail.canOperate`（= `isProjectMember`：负责人/创建人/协助人/历史执行人/超管）；删除自算的 `isMember` 与随之无用的 `useAuthStore` 导入。**方向选择：前端向后端对齐**（不扩大权限，符合 AGENTS.md §14） | ProjectDetail.vue 1172-1182 |
| P1-⑦ | 会议日期为空时表单无法保存且无从修正 | 完成 | 主数据字段（`type: 'project'`）缺失时在字段下方显示「项目主数据未维护「X」」+「去编辑项目补充」按钮（复用 P0-3 已有的 `edit-project` 事件） | ProjectFinanceForm.vue 模板 + `isProjectFieldMissing` |
| P1-⑧ | 直付类「恒瑞需填写所有信息」规则完全未实现 | 完成 | 依 Excel 模板证据修正：①「公司名称」列在模板中**无下拉**（Sheet2 L 列无 dataValidation），原实现复用我方公司下拉导致「恒瑞」根本无法选择 → 改为自由文本；②新增 `requiredWhenHengRui` 标记，公司名称含「恒瑞」时事业部/产品线/记账部门/SFA 一并转必填，其余公司仍只要求公司名称+省区 | ProjectFinanceForm.vue SCHEMAS + `isHengRui` |
| P1-⑨ | 后端表单接口无字段白名单 | 完成 | 新增 `ProjectFinanceWorkbook.sanitize(projectType, raw)`：白名单 = 该类型 Excel 列清单，白名单外 key 丢弃、白名单内类型不符抛业务异常；错误文案改用中文字段名（新增 `LABELS` 映射，`labelOf`）；`saveStageForm` 落库前调用 | ProjectFinanceWorkbook + ProjectService.saveStageForm |
| P1-⑩ | 必填校验只有一次性提示 | 完成 | 改用 `el-form` `ref` + `:model="form"` + `:rules="rules"` + `scroll-to-error` + `:validate-on-rule-change="false"`，`el-form-item` 加 `:prop`；保存前 `formRef.validate()`，失败时逐项标红并滚动定位，同时保留一行汇总提示；新增 `syncProjectFields()` 把主数据实时值同步进表单模型，保证「显示值 = 校验值」 | ProjectFinanceForm.vue 模板 + `rules`/`save`/`syncProjectFields` |
| P1-⑪ | 归档件内容与页面显示可能不一致 | 完成 | 归档卡片与表单 banner 均标注「内容为保存时的快照：会议名称、会议日期等取当时的值」。**后端行为未改**：读路径按项目主数据重算、写路径归档时固化，是既定设计（设计文档 §2），本次只补齐语义说明 | ProjectDetail.vue 归档卡片 + ProjectFinanceForm.vue banner |
| P1-⑫ | 归档替换只命中 `findFirst()` | 完成 | `archiveStageDocument` 改为批量软删同前缀全部旧行 + 提交后清理旧物理文件（`deleteStoredFileAfterCommit`，无事务时立即清理，与 `deleteDoc` 同口径）；不再有「命中哪条不确定」与残留孤儿文件 | ProjectService.archiveStageDocument |

### 附带核实与修正（不改代码结论，只补证据）
- 解析 `backend/src/main/resources/templates/project-finance.xlsx` 的 `xl/worksheets/*.xml` 与 `xl/workbook.xml`，取得模板**权威口径**：
  - Sheet4 字典页：A1:A4=我方公司（直付）景帅/华秀/偌君/会信盈；B1:B4=费用类型；C1:C3=细分类型（平台）；D1:D2=细分类型（直付）有合同/无合同；
  - Sheet1 平台类 E3 我方公司下拉 = 河南景帅,郑州华秀,河南偌君,河南云展汇,**上海独家记亿**,北京际格；
  - Sheet2 直付类 D3 我方公司下拉同上 6 项；**L 列「公司名称」无 dataValidation**（自由文本）→ P1-⑧ 的直接依据；
  - Sheet2 L1 分组标题原文「提供内容（恒瑞需填写所有信息，其他公司仅需公司名称及省区）」→ P1-⑧ 动态必填的直接依据。
- 模板与前端两处不一致**未擅自改模板**，已列入第 7 节待负责人确认（见下）。

## 5. 实际变更
- 新增文件：
  - `backend/src/test/java/com/jshz/expert/service/ProjectFinanceStageFormP1Test.java`（9 例，Mockito 夹具）；
  - 本记录 `docs/handover/tasks/20261126_finance_stage_p1.md`。
- 修改文件（4 个源文件 + 文档）：
  - `backend/src/main/java/com/jshz/expert/service/ProjectFinanceWorkbook.java`：新增 `LABELS`（字段中文名，后端权威副本）、`NUMERIC_FIELDS`、`sanitize`、`labelOf`、`layoutOf`；`generate` 与 `requireText` 的报错改用中文字段名；
  - `backend/src/main/java/com/jshz/expert/service/ProjectService.java`：`saveStageForm` 落库前调 `sanitize`；`archiveStageDocument` 改批量软删；新增 `deleteStoredFileAfterCommit`；
  - `frontend/src/views/project/ProjectDetail.vue`：财务阶段 future 专属空态；归档件卡片补「不可删除 + 快照」说明；成员口径改 `detail.canOperate`；删除占位阶段集合与相关死代码；
  - `frontend/src/views/project/components/ProjectFinanceForm.vue`：el-form rules 化；主数据缺失引导；「公司名称」改自由文本 + 恒瑞动态必填；banner 补快照说明；新增 `.fin-field` / `.fin-project-missing` 样式。
- 文档：`docs/architecture.md`（P1 整改小节）、`docs/handover/README.md`（摘要条目）、本记录。
- 数据库/迁移：**无**。无表/列/索引变更，`database/init.sql` 与 `database/migration/` 未改，无需 db-migrate。
- API 契约变更：
  - `PUT /api/project/{id}/stage-form`：**新增白名单与类型校验**。白名单外 key 被静默丢弃（不再落库）；白名单内字段类型不符返回业务异常，文案为中文字段名（如「省区」格式不正确）。未携带 `projectType` 或项目未设类型的项目，保存时按 P0-③ 返回「请先选择项目类型」。
  - `GET /api/project/{id}/stage-form`：响应结构不变（P0-④ 已收紧脱敏，本次无变化）。
  - 阶段归档件：每次保存新增一行 `project_doc`（旧行改为软删），`is_deleted=0` 的同前缀件恒为 1 份；`project_stage_form.form_data` 仍为一项目一阶段一份、整包覆盖。
- 权限：无权限码/角色绑定改动。P1-⑥ 只是把前端判定改为对齐后端既有口径，未扩大任何人的权限。
- 配置/环境变量：无新增。

## 6. 测试与验证

| 验证项 | 执行命令/步骤 | 结果 | 备注 |
| --- | --- | --- | --- |
| 模板口径取证 | 只读解析 `project-finance.xlsx` 的 `xl/workbook.xml` 与 `xl/worksheets/sheet*.xml`（解压到系统临时目录，未落仓库、未改模板） | 取得 4 个 sheet 名、全部表头、6 组 dataValidation、Sheet4 字典页全部取值 | 见第 4 节「附带核实与修正」 |
| 见红演练 | 把 `ProjectService.java` / `ProjectFinanceWorkbook.java` 临时回退为改动前行为（仅保留测试所需 API 表面：`sanitize` 退化为直通、`labelOf` 退化为回显 key），跑 `mvn -o "-Dtest=ProjectFinanceStageFormP1Test" test` | **9 例中 6 失败 + 1 错误**：白名单未生效（落库 JSON 里仍有 `__evil__`、`clientOwner`）、类型不符未拒绝（金额传字符串、省区传数字都未抛错）、未设项目类型未拒绝、归档替换未批量软删（旧实现在无事务上下文下直接抛 `IllegalState: Transaction synchronization is not active`，同时证明旧实现的提交后清理假设过强） | 失败原因均为待实现行为或原缺陷，非环境/构建问题 |
| 目标回归 | 同上命令（实施版） | **9/9 通过** | |
| 后端全量回归 | `mvn -o "-Dspring-boot.build-info.skip=true" test`（JWT_SECRET 已设临时值） | **413 例，0 失败 0 错误，16 跳过**，BUILD SUCCESS | 404（P0 批次）+ 9（P1 批次） |
| 前端构建 | `npm run build`（`<LOCAL_PATH> Files\nodejs\npm.cmd`） | 通过（built in 38.56s；改归档文案后复跑亦通过） | chunk 体积警告为既有状态 |
| Element Plus 校验契约核对 | 读 `element-plus/es/components/form/src/form-item.{mjs,vue_vue_type_script_setup_true_lang.mjs}` | 确认 `el-form-item` 的规则与取值链路：规则取 `formContext.rules[prop]`、取值取 `getProp(formContext.model, prop).value`、`required` prop 默认为 `undefined`（不会自动追加必填规则）、无「model 变化即校验」的 watcher（load() 回填不会误报红） | 据此补上了 `<el-form :model="form">`（缺它校验永远取不到值） |
| schema 校验 | 未执行（本次无 DDL 变更） | 不适用 | |
| 页面手工回归 | — | **未执行** | 需发布后验收（清单见第 10 节） |
| 生产库 / 生产服务 | — | **未执行** | 无迁移需求；未发布、未重启 |

## 7. 未完成事项与阻塞

| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| **模板口径不一致 ①**：Sheet1/Sheet2 我方公司下拉为「上海独家**记亿**」，前端选项与需求文档为「上海独家**记忆**」 | 业务名称以哪一方为准无法从代码判定 | 归档件里若存「上海独家记忆」，与模板下拉不一致，Excel 打开该单元格可能提示无效值 | 需负责人确认公司正式名称后**同时**改模板与前端（模板改动涉及 `database/` 外的资源文件，需走发布流程） | 负责人 |
| **模板口径不一致 ②**：Sheet2 直付类「我方公司」D3 用 6 项全称下拉，而同列 D1:D2/D4+ 与 Sheet4!A1:A4 是 4 项简称（景帅/华秀/偌君/会信盈） | 模板自身冲突，无法从代码判定哪套为准 | 前端直付类「我方公司」按 4 项简称实现（与交接文档与需求文档一致），与 D3 下拉不一致 | 需负责人确认直付类我方公司取值口径，并修正模板使其内部自洽 | 负责人 |
| 模板一致性守护（列序 / 下拉 / 字典） | 本次仅取证，未加自动化断言 | 后续改模板仍可能与前端列序/选项漂移（审查报告新需求 7） | 另开任务加单测断言「模板表头 ↔ LAYOUTS ↔ 前端 SCHEMAS」三方一致 | 负责人 |
| 审查报告 P2（9 项）、P3（6 项） | 本次只做 P1 | 见审查报告各条 | 另开任务按优先级推进 | 负责人 |
| P1-⑥ 的连带项 | 详情页「编辑项目」按钮条件仍用后端 `canManage || canEditBasic`，而 `update` 接口只允许项目成员 | 持 `project:manage` 但非项目成员的账号仍会看到「编辑项目」并在保存时被 403（**不在本次 P1-⑥ 报告范围内**，属同类相邻问题） | 需负责人决定：收窄 `canEditBasic` 还是放开 `update` 权限 | 负责人 |
| 发布 1.0.159 | 未获发布授权 | P0+P1 共 12 项修复仅在代码库 | 走 release-full 流程（无迁移，可直接发布） | 负责人 |

## 8. 风险与回滚
- 已知问题：
  - **P1-⑨ 白名单是「丢弃」而非「拒绝」**：客户端传白名单外 key 会被静默忽略（不报错）。好处是对旧前端/第三方脚本更宽容，代价是「传错 key」不会立刻提示。若需改成显式拒绝，只改 `sanitize` 一处。
  - **P1-⑨ 类型校验是「拒绝」**：白名单内字段类型不符直接报错。若历史数据里存在数值型字符串（例如 `province: "123"` 仍是字符串，OK）会被拒；实测历史 JSON 均由前端 el-input/el-select 产生，文本字段恒为字符串、数值字段恒为 number，未发现会被误拒的历史数据（但未做全库扫描）。
  - **P1-⑩ 校验改为阻断式**：原先「建议必填」只弹提示、不阻断保存；改后必填未满足时**点保存不会发请求**。若历史数据存在必填缺失（如某项目从未填过「我方公司」），用户重新保存时会被拦住，必须补齐——这是审查报告明确要求的收紧。
  - **P1-⑫ 归档件 doc 行会新增**：每次保存新增一行 `project_doc`（旧行软删），`is_deleted=1` 的历史行会缓慢累积（文件已清理，数据行保留留痕）。当前无归档行清理机制（既有行为如此，非本次引入）。
- 数据风险：中。①白名单会丢弃白名单外 key——若未来新增字段但忘记同步后端 LAYOUTS，该字段将被丢弃（前端已加字段、后端未加 → 静默丢数据）。**新增字段必须成对改后端 LAYOUTS 与前端 SCHEMAS**；②必填阻断式校验会让历史缺项数据无法保存（需补齐）。
- 兼容风险：旧前端 + 新后端 → 旧前端传的字段若不在白名单内会被丢弃（当前两套字段集合完全一致，无实际影响）；新前端 + 旧后端 → 无白名单校验，且新前端的 el-form 规则仍会阻断保存（前端侧独立生效），P1-⑨ 的服务端收紧需前后端同时发布才完整。
- 回滚步骤：
  - 代码：`git revert` 本次提交（4 个源文件 + 1 个测试文件 + 2 个文档文件，无数据迁移需回滚）；
  - 手工兜底：从 `<LOCAL_PATH>` 还原 `backend__ProjectService.java`、`backend__ProjectFinanceWorkbook.java`、`frontend__ProjectDetail.vue`、`frontend__ProjectFinanceForm.vue`；
  - 版本：`version.txt` / `frontend/package.json` 保持 1.0.159（与 P0 批次同版本发布，如需拆分发布则改回 1.0.158 并另行 bump）；
  - 无 DDL / 权限 / 配置回滚项。

## 9. 文档同步
- [x] 架构文档（`docs/architecture.md` 新增「会前财务资料上传阶段 P1 整改（1.0.159）」小节）
- [x] 交接记录（本文件；P0 批次见 [20261126_finance_stage_p0.md](20261126_finance_stage_p0.md)）
- [x] 最新交接摘要（`docs/handover/README.md` 已加条目）
- [x] 测试说明（本记录第 6 节；新增自动化回归 9 例）
- [x] 字段中文字典成对登记（AGENTS.md §17.7）：后端 `ProjectFinanceWorkbook.LABELS` ↔ 前端 `ProjectFinanceForm.vue SCHEMAS[].label`，本记录第 5 节即为两侧文件成对清单
- [ ] 部署文档（本次无迁移/环境变量/部署方式变更，`docs/deployment.md` 无需改；发布记录随发布任务生成）
- [ ] 接口文档（仓库无独立接口文档文件，接口契约变化以本记录第 5 节 + 架构文档为准）
- [ ] 数据库迁移 / 数据字典（不适用：无 DDL 变更）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`（应只有 P0+P1 两批改动）；`mvn -o "-Dtest=ProjectFinanceStageFormGuardTest+ProjectFinanceStageFormP1Test" test` 应 12/12 + 9/9；`npm run build` 应通过。
2. 然后执行（获发布授权后）：`scripts/release-full.ps1 -Mode Publish -Version 1.0.159`（**P0+P1 同版本，无迁移，可直接发布**），健康检查 `/health` 应显示 1.0.159。
3. 发布后手工回归清单：
   - 财务阶段未开始时点步骤条 → 显示「进入本阶段后可填写会前财务资料并生成合规立项申请表」，**不再出现「暂未配置」**；
   - 用仅持 `project:manage`、非项目成员的账号打开该阶段 → 表单应为**只读**（改用后端 canOperate 口径），不再出现「填完保存 403」；
   - 项目未维护会议日期 → 字段下方出现「项目主数据未维护「会议日期」」+「去编辑项目补充」按钮，点击直达编辑弹窗；
   - 直付类「公司名称」可自由输入「恒瑞」→ 事业部/产品线/记账部门/SFA 立刻变必填（带 *）；改为其它公司名则恢复只必填公司名称+省区；
   - 保存必填缺失项 → 页面滚动定位到第一个红色字段并逐项提示（不是只弹一行汇总）；
   - 直接调 `PUT /api/project/{id}/stage-form` 传 `{"province":123}` → 报「「省区」格式不正确」（不暴露 province）；传 `{"__evil__":"x"}` → 静默丢弃，不落库；
   - 连续保存两次表单 → 项目文件里《合规立项申请表》**始终只有 1 份**，旧文件不残留。
4. 未经确认禁止：修改 `templates/project-finance.xlsx`（模板两处口径不一致待负责人拍板）；改 `FINANCE_MASK_*` 脱敏集合；放宽白名单为「显式拒绝」或「宽松忽略」之外的第三种策略。
5. 需要负责人决策：模板「上海独家记亿 vs 独家记忆」；直付类我方公司 4 项简称 vs 6 项全称；`canEditBasic` 与 `update` 权限口径统一方向；审查报告 P2/P3（15 项）与新需求 8 条的排期。
