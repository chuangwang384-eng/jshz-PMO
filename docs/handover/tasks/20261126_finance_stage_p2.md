# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261126-FINANCE-STAGE-P2
- 任务名称：会前财务资料上传阶段 P2 九项整改（选项单一真源 / 文案副本收口 / 阶段名收敛 / 待办锚点 / 上传 accept / 显示名统一 / 金额空值 / 死代码确认 / 对齐守卫）
- 主责 Agent：AutoClaw（本会话）
- 交接时间：2026-11-26
- 当前状态：已完成待验收（**未发布**：后端 jar 与前端快照均未切换，生产仍为 1.0.158）
- 优先级：P2
- 前置输入：
  - 审查报告（本会话《会前财务资料上传阶段 — 开发审查报告》，P2 共 9 项）；
  - [P0 整改交接](20261126_finance_stage_p0.md)、[P1 整改交接](20261126_finance_stage_p1.md)（同批 1.0.159，一起发布）。

## 2. 目标与验收条件
- 业务目标：修掉审查报告 P2 九项——业务选项硬编码无字典来源、项目类型文案三份副本、阶段名硬编码散落、「去填写表单」待办无锚点、上传入口无 accept、归档件显示名两处不一致、金额字段空串初始化、死代码、缺自动化对齐守卫。
- 本次范围：仅「项目管理 → 会前财务资料上传」阶段相关前后端文件、2 个新增回归测试、本记录与架构文档。审查范围外模块零改动。
- 不在本次范围：
  - 审查报告 P3（6 项）未动；
  - 新需求建议 8 条未实施；
  - 数据库结构、权限码、环境变量、部署脚本零改动；
  - **模板两处口径不一致未改模板**（沿用 P1 批次的待负责人确认项，见第 7 节）。
- 验收条件：
  - [x] P2-⑬ 业务选项下沉到后端单一真源 + 只读接口，前端零副本；
  - [x] P2-⑭ 项目类型文案三份副本收口到 `utils/dict.js`；
  - [x] P2-⑮ 阶段名收敛为后端常量 + 前端常量，并加启动期字典存在性校验；
  - [x] P2-⑯ 「去填写表单」待办加锚点并精确定位；
  - [x] P2-⑰ 阶段附件上传补 accept 白名单；
  - [x] P2-⑱ 归档件显示名两处统一；
  - [x] P2-⑲ 数值字段 null 初始化 + 后端空值视为未填写；
  - [x] P2-⑳ 死代码确认（已于 P1 批次清理，本批复核）；
  - [x] P2-㉑ 新增模板 ↔ 后端 ↔ 生成件三方对齐守卫（11 例），漂移演练见红 2 例；
  - [x] 后端全量 428 例 0 失败；前端 `npm run build` 通过；
  - [ ] 生产环境验证（发布后另行验收）。

## 3. 项目基线
- 分支：main；起点提交 `8758976`（工作区起点干净）。
- 版本：`version.txt` revision=1.0.159（P0 批次已 bump，未发布）；本次 P2 属同一未发布版本，**不再重复 bump**。
- 生产实况（2026-11-26 只读核实）：`GET http://127.0.0.1:8080/health` → version=1.0.158、gitShort=ea37672、status=UP。未发布、未重启。
- 备份位置：`<LOCAL_PATH>`（改动前 8 个源文件逐份副本；`.P2FIXED.java` 为实施后快照，供漂移演练用）。

## 4. 已完成工作（对照审查报告 P2 编号）

| 发现 | 内容 | 状态 | 证据 |
| --- | --- | --- | --- |
| P2-⑬ | 业务选项硬编码 4 组，无字典来源 | 完成 | 选项下沉到 `ProjectFinanceWorkbook.OPTIONS`（与模板 Sheet4 字典页、各 sheet 列下拉一致），经 `GET /api/project/finance-options` 下发；**更彻底**：整个表单结构（分区/字段/中文名/值类型/必填/下拉）由后端 `finance-schema` 下发，前端 `SCHEMAS` 常量整块删除（四组选项 + 字段表都不再存在）；读回历史数据由后端 `normalizeStoredSelects` 归一化（旧简称转现选项、失效选项置空），前端不再维护别名映射 | 前端 grep 无 `SCHEMAS` / `OUR_COMPANY_OPTIONS` / `PLATFORM_COMPANIES` / `SUB_TYPE_OPTIONS` / `EXPENSE_TYPE_OPTIONS` 残留 |
| P2-⑭ | 项目类型文案三份副本 | 完成 | 删除 `ProjectFinanceForm.vue` 的 `TYPE_LABELS` 与 `ProjectEdit.vue` 的 3 个内联 `el-option` 及硬编码 placeholder/警告文案，统一引用 `utils/dict.js` 的 `PROJECT_TYPE_LABELS` | ProjectFinanceForm.vue:93、ProjectEdit.vue:12-15/222 |
| P2-⑮ | 阶段名硬编码散落 6 处 | 完成 | 新增 `com.jshz.expert.common.ProjectPhaseNames`（后端唯一真源，含 6 个默认阶段名 + LIFECYCLE 顺序）；后端 `ProjectService` / `ProjectCloseout` / `ProjectSettlementMaterials` / `ProjectShowService` 全部改引常量；前端新增 `utils/dict.js` 的 `PROJECT_PHASE_NAMES`，`ProjectDetail.vue` 的 5 处阶段名判定改引常量；新增 `ProjectPhaseDictionaryCheck`（ApplicationReadyEvent）核对字典是否仍含这些阶段名，缺失打 ERROR | 后端全库 grep 仅 `ProjectPhaseNames` 自身含字面量；前端 grep 仅 dict.js 副本 |
| P2-⑯ | 「去填写表单」待办无锚点 | 完成 | 表单根元素加稳定锚点 `id="project-finance-form"`；`resolveMissing` 新增「保存阶段表单」分支 → 切到当前阶段后滚动定位到锚点（原先只能滚到「阶段资料」卡片顶部，保存按钮在视口外） | ProjectDetail.vue resolveMissing |
| P2-⑰ | 阶段附件上传入口未设置 accept | 完成 | 财务阶段 `el-upload` 补 accept 白名单（与页内 `PHASE_DOC_ACCEPT` 同口径的扩展名列表），非法类型在文件选择阶段即被过滤 | ProjectDetail.vue 财务分支 el-upload |
| P2-⑱ | 归档件在两处显示名不一致 | 完成 | 归档卡片改用 `projectFileDisplayName(financeArchiveDoc)`，与右侧项目文件栏同口径（原先归档卡片显示原始名、侧栏显示带项目名前缀名） | ProjectDetail.vue 归档卡片 |
| P2-⑲ | 金额字段以空字符串初始化 | 完成 | 前端 `blankForm()` 对数值字段初始化 `null`；`load()` 对数值字段做有限数校验，非数字一律归为 `null`；后端 `sanitize` 把 `null`/空白串视为「未填写」不落库，`generate` 对非数值金额跳过写入（保留模板空白单元格，不再写成文本单元格，Excel SUM 不再跳过） | `ProjectFinanceTemplateAlignmentTest.blankAmountIsNotWrittenAsTextIntoTheNumericColumn` |
| P2-⑳ | 死代码：`PLACEHOLDER_PHASE_NAMES` 永不可达 | 完成（**已于 P1 批次清理**） | P1-⑤ 移除「会前财务资料上传」占位项后，整个 `PLACEHOLDER_PHASE_NAMES` 集合与 `isPlaceholderPhaseViewing` 计算属性连同其 `v-else-if` 分支已删除，本批复核确认无残留（grep 仅剩一行说明性注释） | ProjectDetail.vue:1170 注释 |
| P2-㉑ | 缺自动化对齐守卫 | 完成 | 新增 `ProjectFinanceTemplateAlignmentTest`（11 例）：模板 sheet 名 ↔ 表头文案与字段顺序 ↔ 列数 ↔ 下拉取值（对 Sheet4 字典页逐格比对）↔ 必填口径 ↔ 生成件实际列位 ↔ 空金额不写成文本 ↔ 历史别名可解析。**漂移演练**：人为调换 PLATFORM 两个字段顺序 → 见红 2 例（表头错位 + 生成件写错列），恢复后 11/11 通过 | 见第 6 节 |

### 附带核实与修正
- **P2-⑬ 的取证**：本批开始前已解析模板 `xl/workbook.xml` / `xl/worksheets/sheet*.xml`，取得 Sheet4 字典页与 3 个 sheet 的全部下拉与表头。据此确认：
  - 平台类我方公司下拉为 6 项全称（与原前端一致）；
  - 直付类细分类型/费用类型/我方公司分别取 Sheet4!D / B / A（与原前端一致）；
  - 「公司名称」列在模板中**无下拉**（P1-⑧ 已据此改为自由文本）。
- **模板两处口径不一致**（沿用 P1 批次待确认项，本次**未擅自改模板**，改为在代码中显式登记）：
  - 平台类我方公司第 5 项：模板 Sheet1!E3 为「上海独家**记亿**」，本定义取「上海独家**记忆**」（需求文档口径）；
  - 直付类我方公司：模板 Sheet2!D3 数据格为 6 项全称，同列 D1:D2/D4+ 与 Sheet4!A 为 4 项简称（模板自相矛盾），本定义取 4 项简称（与需求文档一致）。
  - 两者登记在 `ProjectFinanceWorkbook.KNOWN_TEMPLATE_DEVIATIONS`，并由对齐测试显式断言「差异数量恰为 2」，使**新漂移不会被旧差异掩盖**。
- **修正**：P1 批次的 `clearedFieldsKeepTheirNullValue` 用例断言「null 值保留落库」，与 P2-⑲ 的新口径（null 视为未填写、不落库）冲突；已改写为 `clearedFieldsAreDroppedRatherThanStoredAsNull`，并在方法注释中标注变更原因。

## 5. 实际变更
- 新增文件：
  - `backend/src/main/java/com/jshz/expert/common/ProjectPhaseNames.java`（阶段名唯一真源）；
  - `backend/src/main/java/com/jshz/expert/service/ProjectPhaseDictionaryCheck.java`（启动期字典校验）；
  - `backend/src/test/java/com/jshz/expert/service/ProjectFinanceTemplateAlignmentTest.java`（11 例对齐守卫）；
  - `backend/src/test/java/com/jshz/expert/service/ProjectPhaseDictionaryCheckTest.java`（4 例）；
  - 本记录 `docs/handover/tasks/20261126_finance_stage_p2.md`。
- 修改文件（7 个源文件 + 文档）：
  - `backend/.../service/ProjectFinanceWorkbook.java`：**重写**——分区/字段定义（`Section`/`Field`/`Schema`）、`OPTIONS`、`LEGACY_ALIASES`（按项目类型+组限定）、`schemaPayload`、`optionsOf`、`normalizeStoredSelects`、`resolveLegacyAlias`、`sanitize`（空值视为未填写）、`generate`（列位由定义派生、空金额不写文本）、`KNOWN_TEMPLATE_DEVIATIONS`；
  - `backend/.../service/ProjectService.java`：新增 `financeSchema`/`financeOptions` 服务方法；`getStageForm` 增加下拉归一化；阶段名判定改引常量；
  - `backend/.../controller/ProjectController.java`：新增 `GET /api/project/finance-schema`、`GET /api/project/finance-options`；
  - `backend/.../service/ProjectCloseout.java`、`ProjectSettlementMaterials.java`、`ProjectShowService.java`：阶段名改引常量；
  - `frontend/src/api/project.js`：新增 `getFinanceSchema`、`getFinanceOptions`；
  - `frontend/src/utils/dict.js`：新增 `PROJECT_PHASE_NAMES`；`PROJECT_TYPE_LABELS` 注释更新为「四处在引用」；
  - `frontend/src/views/project/components/ProjectFinanceForm.vue`：**改为 schema 驱动**——删除 `SCHEMAS` 与四组选项常量，新增 `loadSchema`/`schemaError` 失败态与重试；历史简称映射移交后端；
  - `frontend/src/views/project/ProjectDetail.vue`：阶段名改引 `PROJECT_PHASE_NAMES`；财务阶段 el-upload 补 accept；归档卡片显示名统一；新增「保存阶段表单」待办锚点定位；
  - `frontend/src/views/project/ProjectEdit.vue`：项目类型文案改引 `PROJECT_TYPE_LABELS`。
- 文档：`docs/architecture.md`（P2 整改小节）、`docs/handover/README.md`（摘要条目）、本记录。
- 数据库/迁移：**无**。无表/列/索引变更，无需 db-migrate。
- API 契约变更（新增两个只读端点，**向后兼容**）：
  - `GET /api/project/finance-schema?projectType=PLATFORM` → `{projectType, sections:[{key,title,note,fields:[{key,label,valueType,required,requiredWhenCompanyIs,unit,options}]}]}`；
  - `GET /api/project/finance-options?projectType=PLATFORM` → `{组名: [选项...]}`。
  - 两者均**登录即可**（沿用 `/api/project/phase-options` 同口径：不新增 permission 规则，DbPermissionFilter 无匹配规则即放行；`/api/project/export` 那种敏感导出的 `project:manage` 规则不适用于本端点——表单结构不含业务数据）。
  - `GET/PUT /api/project/{id}/stage-form` 行为不变（结构下发不影响既有契约）。
- 权限：无权限码/角色绑定改动。
- 配置/环境变量：无新增。
- 前端兼容性：旧前端 + 新后端 → 旧前端自带 SCHEMAS，仍可正常渲染（不影响）；**新前端 + 旧后端 → `finance-schema` 404 → 前端显示「表单结构加载失败 + 重新加载」而非空白表单**（已做失败态处理，不静默空白）。**因此发布顺序必须是先切后端、再切前端**（见第 10 节）。

## 6. 测试与验证

| 验证项 | 执行命令/步骤 | 结果 | 备注 |
| --- | --- | --- | --- |
| 漂移演练（见红） | 在 `ProjectFinanceWorkbook` 的 PLATFORM「会议信息」分区**人为调换** `platform` 与 `ourCompany` 两个字段的顺序，跑 `mvn -o "-Dtest=ProjectFinanceTemplateAlignmentTest" test` | **11 例中 2 失败**：`templateHeadersMatchTheBackendFieldOrder`（PLATFORM 第 4 列表头「依托平台」与字段「ourCompany/我方公司」不一致）+ `generatedWorkbookWritesEachFieldToItsOwnColumn`（我方公司写到了第 4 列而非第 5 列） | 证明守卫能同时捕获「表头错位」与「生成件写错列」两类漂移——这正是 P2-㉑ 要防的后果 |
| 目标回归 | 恢复实现后同一命令 | **11/11 通过** | |
| 阶段常量与启动校验单测 | `mvn -o "-Dtest=ProjectPhaseDictionaryCheckTest" test` | **4/4 通过** | 含「字典缺阶段不抛异常」「字典读失败不阻断启动」 |
| 后端全量回归 | `mvn -o "-Dspring-boot.build-info.skip=true" test`（JWT_SECRET 已设临时值） | **428 例，0 失败 0 错误，16 跳过**，BUILD SUCCESS | 424（P0+P1）+ 11 + 4（P2 新增）；其中 1 例为 P1 批次因 P2-⑲ 口径变更而改写的断言 |
| 前端构建 | `npm run build`（`<LOCAL_PATH> Files\nodejs\npm.cmd`） | 通过（built in 54.75s） | 期间因删除 SCHEMAS 后残留重复声明（`typeLabel`/`sections`/`isHengRui`/`isFieldRequired` 各两份）导致一次构建失败，已清理重复块后通过 |
| 副本残留 grep | 前端 grep `SCHEMAS`/`TYPE_LABELS`/`OUR_COMPANY_OPTIONS`/`PLATFORM_COMPANIES`/`SUB_TYPE_OPTIONS`/`EXPENSE_TYPE_OPTIONS`/`requiredWhenHengRui` | 财务表单内**零残留**；其余 `TYPE_LABELS` 命中属消息中心/通知类型的**不同枚举域**（AGENTS 19.1 明确不得合并），保持原样 | 有意保留 |
| 阶段名残留 grep | 后端全库 + 前端全量 | 后端仅 `ProjectPhaseNames` 自身含字面量；前端仅 `utils/dict.js` 副本 + 3 处**其它阶段**的既有字面量（`ProjectList.vue` 日程上传、`ShowWizard.vue`/`ProjectShowEditor.vue` 串场制作、`router/index.js` 串场制作标题） | 属**其它阶段**，不在本次审查范围，未动 |
| schema 校验 | 未执行（本次无 DDL 变更） | 不适用 | |
| 页面手工回归 | — | **未执行** | 需发布后验收（清单见第 10 节） |
| 生产库 / 生产服务 | — | **未执行** | 无迁移需求；未发布、未重启 |

## 7. 未完成事项与阻塞

| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 模板口径不一致 ①（上海独家**记亿** vs **记忆**） | 业务名称以哪一方为准无法从代码判定 | 归档件若存「记忆」，与模板下拉不一致，Excel 打开该单元格可能提示无效值 | 需负责人确认公司正式名称后**同时**改模板与 `OPTIONS`（并同步移除 `KNOWN_TEMPLATE_DEVIATIONS` 第 1 条） | 负责人 |
| 模板口径不一致 ②（直付类我方公司 6 项全称 vs 4 项简称） | 模板自身冲突（数据格 D3 与同列其它区域指向不同字典） | 本定义取 4 项简称（与需求文档一致）；若模板口径应为 6 项则需改 `OPTIONS` | 需负责人拍板 | 负责人 |
| 模板一致性守护常态化 | 本次已加单测，但缺「新增/修改表单字段」的**流程约束**（开发者若绕过测试仍可能漂移） | 后续新增字段若不同步改测试，测试会失败（属期望行为） | 建议在开发流程文档中写明「改表单字段必须同步改 `ProjectFinanceWorkbook` 定义并跑对齐测试」 | 负责人 |
| 其它阶段的阶段名字面量 | 不在本次审查范围（`ProjectList.vue` / `ShowWizard.vue` / `ProjectShowEditor.vue` / `router/index.js`） | 这些阶段若改名，同样会静默失效 | 另开任务把前端阶段名统一到 `utils/dict.js` 的 `PROJECT_PHASE_NAMES` | 负责人 |
| 审查报告 P3（6 项） | 本次只做 P2 | 见审查报告各条 | 另开任务 | 负责人 |
| 发布 1.0.159 | 未获发布授权 | P0+P1+P2 共 21 项修复仅在代码库 | 走 release-full 流程（无迁移） | 负责人 |

## 8. 风险与回滚
- 已知问题：
  - **P2-⑬ 的结构下发有强后端依赖**：新前端必须搭配新后端（旧后端无 `finance-schema` → 页面显示加载失败态）。已做显式失败态与「重新加载」按钮，不静默空白；但**发布顺序必须是先切后端、再切前端**（`release-full.ps1` 默认后端先切，需确认）。
  - **P2-㉑ 的差异登记是「当前现状」而非「已确认正确」**：`KNOWN_TEMPLATE_DEVIATIONS` 记录的是模板与定义不一致的**事实**，不代表定义正确；两条均待负责人确认。
  - **后端归一化会改变旧数据的读回表现**：平台类我方公司若历史存了简称，现读回自动转成全称；不在选项集内的旧值会被**置空**（提示用户重选），这是有意行为（原先前端同样会置空，但口径分散在前端）。
  - **P2-⑮ 的启动期校验只打 ERROR 不阻断启动**：阶段字典被改名时应用照常启动，但相关阶段业务会静默失效——校验的价值在于让改名变成**可观测**故障，需依赖日志监控发现。若要求硬阻断需负责人确认（会直接影响可用性）。
- 数据风险：中。①结构下发不改变 `form_data` 的 key 集合（白名单仍按字段定义）；②`normalizeStoredSelects` 只影响**读回展示**，不修改库中已存值；③`sanitize` 把 null/空串视为未填写——表单为整包覆盖保存，不落 null 与不落 key 等价，已存值不受影响。
- 兼容风险：旧前端 + 新后端 → 正常（旧前端自带 SCHEMAS，新端点不影响）；新前端 + 旧后端 → 表单显示加载失败态（需先切后端）。
- 回滚步骤：
  - 代码：`git revert` 本次提交（7 个源文件 + 2 个新增测试 + 2 个新增后端类 + 2 个文档文件，无数据迁移需回滚）；
  - 手工兜底：从 `<LOCAL_PATH>` 还原 `backend__ProjectFinanceWorkbook.java`、`backend__ProjectService.java`、`backend__ProjectController.java`、`frontend__api__project.js`、`frontend__utils__dict.js`、`frontend__ProjectFinanceForm.vue`、`frontend__ProjectEdit.vue`、`frontend__ProjectDetail.vue`；
  - 版本：`version.txt` / `frontend/package.json` 保持 1.0.159；
  - 无 DDL / 权限 / 配置回滚项。

## 9. 文档同步
- [x] 架构文档（`docs/architecture.md` 新增「会前财务资料上传阶段 P2 整改（1.0.159）」小节）
- [x] 交接记录（本文件；P0/P1 批次见同目录对应记录）
- [x] 最新交接摘要（`docs/handover/README.md` 已加条目）
- [x] 测试说明（本记录第 6 节；新增自动化回归 15 例：11 对齐守卫 + 4 启动校验）
- [x] 字典成对登记（AGENTS.md §17.7）：
  - 后端 `com.jshz.expert.common.ProjectPhaseNames` ↔ 前端 `utils/dict.js PROJECT_PHASE_NAMES`；
  - 后端 `ProjectFinanceWorkbook.Field.label` ↔ 前端不再有副本（标签直接来自接口），对齐测试断言其与模板表头一致；
  - 后端 `ProjectFinanceWorkbook.OPTIONS` ↔ 前端不再有副本（选项直接来自接口），对齐测试断言其与模板 Sheet4 一致。
- [ ] 部署文档（本次无迁移/环境变量/部署方式变更，`docs/deployment.md` 无需改；**但发布顺序约束须写入发布记录**：先切后端再切前端）
- [ ] 接口文档（仓库无独立接口文档文件，新增两个端点的契约以本记录第 5 节 + 架构文档为准）
- [ ] 数据库迁移 / 数据字典（不适用：无 DDL 变更）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`（应只有 P0+P1+P2 三批改动）；`mvn -o "-Dtest=ProjectFinanceTemplateAlignmentTest+ProjectPhaseDictionaryCheckTest" test` 应 11/11 + 4/4；`npm run build` 应通过。
2. 然后执行（获发布授权后）：`scripts/release-full.ps1 -Mode Publish -Version 1.0.159`（**无迁移，可直接发布**），**确认后端先于前端切换**（新前端依赖新后端的 `finance-schema`）；健康检查 `/health` 应显示 1.0.159。
3. 发布后手工回归清单：
   - 打开平台类 / 直付类 / 重点类项目进入本阶段 → 表单分区、字段、下拉与改动前一致（尤其「我方公司」「细分类型」「费用类型」的下拉项）；
   - 直付类「公司名称」可填「恒瑞」→ 事业部/产品线/记账部门/SFA 立刻变必填；填其它公司名则恢复只必填公司名称+省区；
   - 平台类项目若历史存过我方公司简称「景帅」→ 读回自动显示「河南景帅」；
   - 赞助金额不填直接保存 → 成功，且下载的 Excel 金额列是**空白单元格**（不是文本、不是 0）；
   - 待办「去填写表单」→ 页面直接滚到表单（不是卡片顶部）；
   - 阶段附件上传 → 文件选择器已按扩展名过滤；
   - 侧栏项目文件中的合规立项申请表名称与阶段区归档卡片显示一致；
   - 故意改阶段字典中的「会前财务资料上传」名称 → 重启后日志出现「阶段字典与代码常量不一致」ERROR（**仅在隔离库验证，勿动生产字典**）。
4. 未经确认禁止：修改 `templates/project-finance.xlsx`（两处口径不一致待负责人拍板）；修改 `KNOWN_TEMPLATE_DEVIATIONS` 登记来「让测试变绿」；放宽 `sanitize` 的空值口径。
5. 需要负责人决策：模板「上海独家记亿 vs 独家记忆」；直付类我方公司 4 项简称 vs 6 项全称；启动期阶段字典校验是否改为硬阻断；其余阶段的阶段名收口排期；审查报告 P3（6 项）与新需求 8 条的排期。
