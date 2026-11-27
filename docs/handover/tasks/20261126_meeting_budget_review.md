# 会议预算制作阶段代码审查（2026-11-26）

> 审查方式：grep 检索 frontend/src 与 backend/src/main/java（关键词「会议预算 / meeting-budget / MeetingBudget / budget / 预算」），并读 docs/repo-map.md §3/§4 导航。只读审查，未修改任何文件，未运行构建/测试。所有发现均经第二轮独立复核（复核状态：verified；复核中的行号与影响面修正已并入下文）。重复报告的同一问题已合并为单条并注明。

## 第一部分：调查结果

### P1

#### 1. 线上项目全未点选即可「确认报价」，合计 0 覆盖 project.budget_amount 并归档空报价版本
- **位置**：frontend/src/views/project/components/ProjectBudgetPanel.vue:204（hasItems 仅校验 name）、:110（确认按钮 :disabled）；backend/src/main/java/com/jshz/expert/service/ProjectBudgetQuoteService.java:116-118（exporter 非空时跳过空明细校验）；backend/src/main/java/com/jshz/expert/service/ProjectBudgetService.java:117（setStatus）、:132-133（overrideBudgetAmount 覆盖调用）
- **类别**：边界问题（同一链路被三条独立发现报告，均已复核属实，本条为合并条目；接口层绕过前端直调的变体亦成立——PUT /meeting-budget confirm=true 无后端守卫）
- **影响范围**：线上项目（ONLINE 点选模式）骨架行 name 非空、quantity 全 0，hasItems 恒 true；save(confirm=true) 时 parseItem 对 name 空行跳过、parsed 为空仍落 CONFIRMED 并以 total=0 覆盖 budget_amount；archiveConfirmed 传 exporter 非空绕过「至少一条数量大于零」守卫，仍生成 V* 空报价 Excel 挂为阶段资料，missingRequiredTemplates 门禁（ProjectService.java:2603-2609）凭文件存在即放行。
- **用户场景**：用户刚打开预算阶段、未点选任何收费项就误点「确认报价」→ 提示成功、生成空报价文件并把项目财务登记金额清零；阶段门禁显示已满足，财务报表金额异常。脚本直调 API 同样可造成 0 元 CONFIRMED 单。
- **修复建议**：前端 hasItems/确认按钮增加 `amountOf(r)>0` 校验；后端把「至少一条数量大于零明细」校验对 exporter 路径同样生效（save 的 confirm 分支 parsed 为空或全零时抛 BusinessException，文案与前端对齐），archiveConfirmed 生成前校验 exported 非空。
- **优先级**：P1
- **复核状态**：verified。复核修正：QuoteService 守卫实际 L116-118（非 117-119）；覆盖调用在 L132-133（非 131），且金额覆盖发生在 save() 内部（Controller 先 save 后 archiveConfirmed），清零不依赖 archiveConfirmed 成败。

#### 2. 报价单交互生命周期前端入口整体孤儿化：客户确认与恢复快照功能在 UI 上不可达
- **位置**：frontend/src/views/project/components/ProjectBudgetQuotes.vue:1-177（无任何挂载点，仅 ProjectBudgetPanel.vue:149 与 utils/common.js:2 注释提及）；frontend/src/api/project.js:195-213（5 个报价单 API 封装仅被该死组件消费）；backend/src/main/java/com/jshz/expert/controller/ProjectBudgetQuoteController.java:21（类注解）/L28-58（list/generate/{versionId}/file/customer-confirmation/restore 五端点）
- **类别**：死代码 / 未启用代码（2026-11-21 面板移除「对客报价」区块所致；与 P2 第 1、3 条为同一事件的不同侧面，合并报告但分别保留修复决策点）
- **影响范围**：①project_budget 唯一的 CONFIRMED→DRAFT 恢复迁移入口（POST /quotes/{versionId}/restore，ProjectBudgetQuoteService.java:186-203）UI 不可达；②客户确认字段（customerName/customerConfirmedAt/recordedAt/evidenceDocId，ProjectBudgetQuote.java:26-31）无 UI 录入路径，将永久为 NULL；③后端 5 端点无 UI 消费方，接口契约与前端实际能力不一致。
- **用户场景**：误确认报价后想恢复上一版本快照或登记客户确认信息，页面已无任何入口；新接手 Agent 检索 api/project.js 会误以为这些接口可用。
- **修复建议**：与负责人二选一并同步文档（按 17.7 登记状态变更）：①「导出报价单即对客报价」为终局决策 → 删除 ProjectBudgetQuotes.vue、5 个 API 封装，后端下线手动 generate/customer-confirmation/restore 端点（archiveConfirmed 归档路径保留）；②恢复快照/客户确认仍是需求 → 恢复面板中的报价版本区块。
- **优先级**：P1
- **复核状态**：verified。复核修正：restore 位于 ProjectBudgetQuoteService.java:186-203（素材另一条误写 QuoteService.java:194-207，QuoteService 类不存在）；Controller 端点方法体 L28-58（非 21-55）；组件实际 1-177 行。

### P2

#### 3. 对客报价生成/客户确认/恢复快照前端死代码（与 P1-2 合并决策）
- **位置**：frontend/src/views/project/components/ProjectBudgetQuotes.vue:71-72、103、131、136、150、164；frontend/src/api/project.js:195-213
- **类别**：未启用代码
- **影响范围**：死组件 + 5 个 API 封装（listBudgetQuotes/generateBudgetQuote/downloadBudgetQuote/recordBudgetQuoteConfirmation/restoreBudgetQuote）失去全部活跃消费方，与后端端点持续漂移。
- **用户场景**：无直接感知；开发者按注释外观念会误以为报价确认/恢复流程仍在 UI 中。
- **修复建议**：同 P1-2 的二选一决策；若保留后端端点须在 AGENTS/交接记录注明仅存档用途。
- **优先级**：P2
- **复核状态**：verified（与 P1-2 复核同一证据链）。

#### 4. 汇总链服务费/增值税费率 0.06 前端硬编码，未消费后端 serviceRate/taxRate（三条发现合并）
- **位置**：frontend/src/views/project/components/ProjectBudgetPanel.vue:241、243（serviceFee/taxFee 字面量 0.06），:116、118（模板硬编码「服务费(6%)」「增值税(6%)」文案）；后端落库 ProjectBudgetService.java:115-116（null 时补默认 0.06）；导出侧消费 ProjectBudgetExportService.java:318-328（读 budget.getServiceRate()/getTaxRate()）；数据库列 database/migration/20261120_budget_fee_library.sql（service_rate/tax_rate DECIMAL(5,4) DEFAULT 0.0600，可更新）
- **类别**：硬编码（违反 AGENTS.md 18.1 §4「前后端共享数值以后端为唯一真源」；三组独立发现均 verified，本条合并）
- **影响范围**：overview 接口已返回 budget 实体但前端从未读取费率字段（grep 全前端 src 中 serviceRate|taxRate 零匹配）；库中费率一旦调整（或按单定制），页面含税总价与导出 Excel（后端按库中费率计算）金额分叉。
- **用户场景**：管理员将费率调为 8%：面板按 6% 展示含税总价，导出 Excel 按 8% 计算，对客报价两处数字对不上，财务对账口径漂移。
- **修复建议**：改为 `Number(budget.value?.serviceRate ?? 0.06)` / taxRate 同理（覆盖 null），删除 0.06 字面量与固定 6% 文案；或经既有 limits.js 模式由 meta 接口下发。
- **优先级**：P2
- **复核状态**：verified。复核修正：后端默认值落库在 L115-116 且为 null 时条件赋值（非无条件）；导出侧费率读取在 ExportService L318-319（一条素材误写 268-272，另一条误写 L110-111，均以复核为准）。

#### 5. 后端允许空明细确认：save/confirm 无明细校验 + mapList 静默丢弃畸形行叠加（P1-1 的 API 层侧面）
- **位置**：backend/src/main/java/com/jshz/expert/service/ProjectBudgetService.java:104-116（save 循环无空明细校验）、L316-322（parseItem name 空返回 null）；ProjectBudgetQuoteService.java:105-106（exporter==null 才拦截）；ProjectBudgetController.java:125-131（mapList 对非 Map 元素 filter 静默丢弃，非 List 返回空 List）
- **类别**：边界问题
- **影响范围**：绕过前端 disabled 校验（直调 API 或并发），items 全空行/畸形格式仍返回 200「报价单已确认」；0 元报价满足阶段门禁。mapList 静默丢弃与同方法内 estimateInputs 严格抛错（L56「估算参数不正确」）口径不一致。
- **用户场景**：脚本以 `items:["场地费"]` 或 `items:[{name:""}]` confirm=true 调 PUT，返回成功且状态 CONFIRMED、budget_amount=0。
- **修复建议**：save/confirm 在 parsed 为空或无数量>0 明细时拒绝 confirm（草稿可允许空）；archiveConfirmed 生成前校验 exported 非空；mapList 对非 Map 元素抛「明细格式不正确」与 estimateInputs 口径对齐。
- **优先级**：P2
- **复核状态**：verified。复核修正：mapList 实际 L125-131；「0 元 CONFIRMED 单」下游行为经 P1-1 复核链路证实成立。

#### 6. 收费标准读取端点全员可读：GET /api/project/config/budget-fee-standards 无权限拦截
- **位置**：backend/src/main/java/com/jshz/expert/controller/ProjectBudgetController.java:101-105（listFeeStandards 无权限参数；:25 javadoc 注释失实）；backend/src/main/java/com/jshz/expert/service/ProjectBudgetService.java:185-192（无 user/角色校验）；database/migration/20261029_fix_project_page_read_permissions.sql:11-13（删除了 GET /api/project/config/** → project:manage 规则）；database/init.sql:1428-1431（仅 PUT/DELETE 规则）
- **类别**：边界问题（权限）
- **影响范围**：登录用户即可拉到全量收费标准（含 cost_price 成本价与 min_profit 最低利润，ProjectBudgetFeeStandard.java:45/48）；Controller 注释声称「project:manage」与实际不符。20261029 迁移本意为放开 config/phases、config/types 字典，副作用是收费库 GET 一并放开。
- **用户场景**：任意普通登录用户浏览器直请求该端点，返回全部模板项的完整成本价与利润底线，公司对客定价策略泄露给全员。
- **修复建议**：为该 GET 路径重建 project:manage 规则（可按 path 收窄到 /api/project/config/budget-fee-standards，参考迁移文末回滚段），并更正 Controller 注释；预算面板所需 feeStandards 已由 /meeting-budget overview 按项目返回，不受影响。
- **优先级**：P2
- **复核状态**：verified（「171 条」条数未连库核验，不影响结论；迁移 DELETE 语句在 11-13 行）。

#### 7. 成本单导出权限与写入级操作未对齐
- **位置**：backend/src/main/java/com/jshz/expert/service/ProjectBudgetExportService.java:78-81（export 仅 requireProjectReadable；docType=COST 分支 78-128 行无第二次权限校验）；对照 ProjectBudgetQuoteService.java:64、85（generate/restore 为 requireOperable）
- **类别**：边界问题（权限）
- **影响范围**：成本单价/成本合计/毛利属内部敏感数据，可被项目只读成员导出。**复核修正**：requireOperable 同样放行协助人（project_assistant），且协助人经 ProjectBudgetService.java:50 的 requireProjectReadable 读取接口本已能看到成本单价，故实际敏感数据暴露增量小于原发现所述——本条按「导出与写入权限形式不一致 + view-all 管理员可导出边界」定性；简单换成 requireOperable 不能实现「成本仅负责人/管理员可见」。
- **用户场景**：协助人打开预算面板点「导出成本单 Excel」，获得全部费用项成本单价与毛利参考（业务口径上成本数据只应负责人/管理员可见——若该口径成立）。
- **修复建议**：与负责人确认成本数据可见口径；若需收紧，新增专用财务权限校验（docType=COST 分支），而非简单替换 requireOperable。
- **优先级**：P2
- **复核状态**：verified（代码事实成立；影响面按复核修正如上，原 userScenario 高估）。

#### 8. 线上项目「需要主视觉设计」勾选状态不持久化
- **位置**：backend/src/main/java/com/jshz/expert/service/ProjectBudgetService.java:94-96（仅 OFFLINE 写 estimate_inputs，ONLINE 静默丢弃）；frontend/src/views/project/components/ProjectBudgetPanel.vue:218-222（needsMainVisual computed）、:340（加载 JSON.parse）、:356（保存提交 estimateInputs）
- **类别**：缺陷
- **影响范围**：线上项目取消勾选后保存，刷新/他人打开回到默认 true，设计类费用项重新可点选且可能重新计入。
- **用户场景**：用户取消「需要主视觉设计」并保存草稿，次日打开发现复选框又勾上了，误以为操作未生效。
- **修复建议**：ONLINE 也持久化 estimateInputs（去掉 category 条件），或为 ONLINE 单独保存 needsMainVisual 字段；若有意不存则前端不应将其放进 estimateInputs。
- **优先级**：P2
- **复核状态**：verified。复核修正：判断在 L94-96（非 91-93）；全库 grep 确认无其他 ONLINE 持久化路径。

#### 9. save(false) 将已 CONFIRMED 预算单静默降级为 DRAFT
- **位置**：backend/src/main/java/com/jshz/expert/service/ProjectBudgetService.java:117（setStatus 一刀切）、:91-92（2026-11-21 移除拒写守卫注释）、:131-133（overrideBudgetAmount 仅 confirm 分支）；frontend/src/views/project/components/ProjectBudgetPanel.vue:109（保存草稿按钮）、:343-372（save 函数无禁用/弹窗）、:104（已确认徽标）
- **类别**：缺陷
- **影响范围**：已归档报价文件仍在（门禁照常通过）但当前明细与其脱钩；budget_amount 停留旧确认值与草稿金额不一致；徽标凭空消失；无 operationLog 审计（仅 log.info），状态迁移不可追溯。
- **用户场景**：负责人已确认报价后只想微调备注点「保存草稿」：状态无声退回 DRAFT、徽标消失，不知道触发了回退，也不知道 budget_amount 与明细已不同步。
- **修复建议**：对已 CONFIRMED 单执行 save(false) 时前端弹确认框，或后端返回 409 要求显式 demote 参数；降级后写入操作日志。
- **优先级**：P2
- **复核状态**：verified。复核修正：前端按钮在 L109（非 262-268）；覆盖分支在 L131-133。

#### 10. 并发编辑 last-write-wins，无乐观锁/冲突检测
- **位置**：backend/src/main/java/com/jshz/expert/service/ProjectBudgetService.java:86-125（lockBudget FOR UPDATE 仅串行化写事务，删旧插新无版本比对）、:110-119；backend/src/main/java/com/jshz/expert/entity/ProjectBudget.java（无 @Version，仅 updateTime L54）；database/init.sql:2150-2166（project_budget 无 version 列）
- **类别**：缺陷
- **影响范围**：两个成员同时编辑，后提交者整体覆盖前者明细、备注、状态，甚至把刚确认的 CONFIRMED 覆回 DRAFT，双方均无感知。
- **用户场景**：A 先点「确认报价」，B 随后点「保存草稿」——B 的旧明细覆盖 A 的确认版，A 的确认结果不翼而飞且无人知道。
- **修复建议**：project_budget 加 version 乐观锁（前端携带加载时 update_time，服务端比对不一致返回 409），或保存前重读比对；至少对「覆盖 CONFIRMED 状态」的写加防回退校验。
- **优先级**：P2
- **复核状态**：verified（「前端加载后不刷新即提交」行为本轮未核验，属未验证项，不影响后端无冲突检测的核心结论）。

#### 11. 「移除本节」无确认弹窗，立即物理删除整节明细
- **位置**：frontend/src/views/project/components/ProjectBudgetPanel.vue:308-310（removeSection）、:312-313（restoreSection 只重建空节）、:26（danger 小字按钮，与「+ 空行」相邻）
- **类别**：边界问题
- **影响范围**：仅线下预算编辑模式（isOnline 下按钮不显示，v-if="!editingLocked && !isOnline"）；未保存的整节数据一键消失且无撤销。
- **用户场景**：用户在「礼品费」节填了 5 行，手滑点「移除本节」→ 5 行全消失；点「+ 礼品费」恢复的只是空节标题，数据回不来只能重填。
- **修复建议**：removeSection 前加 ElMessageBox.confirm（提示影响行数）；或改为标记隐藏、保存时剔除、恢复时还原；按钮虽有 danger 样式，仍建议与添加类按钮拉开距离/加图标。
- **优先级**：P2
- **复核状态**：verified（全文件无 ElMessageBox/confirm 调用；danger 样式与 isOnline 限定为复核补充）。

#### 12. 线上点选行单价为 0 时可无告警确认保存
- **位置**：frontend/src/views/project/components/ProjectBudgetPanel.vue:206-216（togglePick 置 unitPrice=null 仅注释说明）、:351（save 静默落 0）、:343-376（save 函数，唯一前置拦截 L359-361 仅查 name）、:110（确认按钮不校验 unitPrice）
- **类别**：边界问题
- **影响范围**：管理员漏配默认单价的收费项，勾选后无必填校验/视觉标记，确认后以 0 元进入对客报价单 Excel 与 budget_amount，毛利为负无提示（underMinProfit 仅覆盖 minProfit>0 且有 itemKey 场景）。
- **用户场景**：用户勾选后没注意单价输入框是空的，点确认 → 该项 0 元进报价单，毛利等于负成本却无提示。
- **修复建议**：保存/确认前对 quantity>0 且 unitPrice==null/0 的行给出警告（列名+行名），确认后放行；勾选时对该行加明显高亮/红点。
- **优先级**：P2
- **复核状态**：verified。复核修正：save 位置 L343-376（素材写 ~355）。

#### 13. 估算参数键混入「收费标准库添加」弹窗（历史缺陷，已被 20261121 迁移闭环）
- **位置**：backend/src/main/java/com/jshz/expert/service/ProjectBudgetService.java:302-308（activeFeeStandards 无键前缀过滤）；frontend/src/views/project/components/ProjectBudgetPanel.vue:271-277（libraryRows 无 EST_ 排除）、:279-293（pickFromLibrary 把 defaultValue 当单价）；database/migration/20261117_estimate_rules_fee_standards.sql:6-11
- **类别**：缺陷（历史）
- **影响范围**：**复核修正：原发现描述的「EST_* 参数当费用项加入预算」在当前代码与基线下已不成立**——20261121_cleanup_no_catalog_fees.sql:7-15 删除了全部 catalog IS NULL 的 18 条行并重算受影响预算合计，init.sql:2228-2231 确认新基线不再种子。残留两点：a) 代码层防御仍未加（管理员手工新建无类目 EST_* 键仍会进弹窗，保存校验 L200-217 只查格式）；b) 20261117 迁移 INSERT IGNORE 幂等，在已跑 20261121 的库上单独重放会重新插入。生产库是否已执行 20261121 未连库核验。
- **用户场景**：历史场景（已闭环）；残留场景为管理员重建无类目参数键。
- **修复建议**：按复核降级为低优先级防御性改进——activeFeeStandards/libraryRows 过滤 EST_ 前缀；管理页对估算参数键单独分组标注。
- **优先级**：P2（素材原定级；复核建议降级为防御性改进）
- **复核状态**：verified（机制历史属实，当前缺陷已过时，未连库实测）。

### P3

#### 14. estimateState JSON.parse 无容错，脏数据锁死整个预算面板（两条重复发现合并）
- **位置**：frontend/src/views/project/components/ProjectBudgetPanel.vue:339-340（applySaved 内 JSON.parse）、:315-327（load catch 置 error）、:191（editingLocked = props.readonly || !!error.value）
- **类别**：边界问题
- **影响范围**：estimate_inputs 被污染为非法 JSON（历史 bug/手工修库）时，面板只读锁定、红色错误条「预算单加载失败」，明细无法编辑，须后端清字段恢复。
- **用户场景**：用户打开预算阶段只见错误条，全部费用明细不可编辑。
- **修复建议**：JSON.parse 包 try/catch，失败降级 `{}` 并 ElMessage 提示「估算参数解析失败，已忽略」，明细照常展示。
- **优先级**：P3
- **复核状态**：verified（parse 在 L339/340，两处素材行号各有一行偏差，以复核为准）。

#### 15. 收费标准库弹窗 libraryRows 静默截断前 200 条
- **位置**：frontend/src/views/project/components/ProjectBudgetPanel.vue:271-277（slice(0,200)）、:133（el-table max-height=420，无总数展示）
- **类别**：边界问题
- **影响范围**：**复核修正**：类目/关键词过滤发生在 slice 之前，排名 201+ 的项**用关键词能搜到**，原 userScenario「搜索不到」不成立；真实影响是无关键词浏览时第 201 条起不可见且用户不知被截断（注释称模板库现 171 条，管理页可继续新增）。
- **用户场景**：库扩到 230 条后，用户无关键词翻看弹窗，新加项永远不出现，误判为未配置而手填。
- **修复建议**：显示总数与截断提示（「共 N 条，已显示前 200 条」），或去掉 slice 由虚拟滚动承担。
- **优先级**：P3
- **复核状态**：verified（用户场景按复核修正）。

#### 16. doExport 内联 blob 下载逻辑复制 + CATALOGS 类目字典两处逐字复制（两条发现合并）
- **位置**：frontend/src/views/project/components/ProjectBudgetPanel.vue:248-257（手写 createObjectURL，未 append body、立即 revoke，Safari 旧版兼容性弱）、:266（CATALOGS）；frontend/src/views/admin/BudgetFeeConfig.vue:158（CATALOGS 同字面量）；frontend/src/utils/common.js:5-13（downloadBlob 收口版，文件头注明 2026-11-17 收口名单中本就含 ProjectBudgetPanel——属被点名却漏改的唯一残留）
- **类别**：死代码 / 硬编码（违反 19.1「禁止复制本地副本」；复核另发现 CustomerDetail.vue:436、ExpertList.vue:1033、ShowWizard.vue:495 同类内联下载，超出本素材范围，记为建议）
- **影响范围**：Safari 旧版可能不触发下载；下载逻辑统一修复时此处被漏；类目键（ELEC/PRINT/ONLINE/BUILD/STAFF/SPECIAL）改动需改两处，漏改则面板显示原始键名。
- **用户场景**：旧 Safari 点「导出报价单 Excel」无反应；管理员新增类目 GIFT 只改了管理页，预算弹窗里显示原始键名。
- **修复建议**：doExport 改用 `downloadBlob(resp.data, (docType==='COST'?'会议成本单':'会议报价单')+'.xlsx')`；CATALOGS 抽到 utils/dict.js 供两页共享。
- **优先级**：P3
- **复核状态**：verified。

#### 17. ProjectBudgetPanel 未使用的 props（canRecord、documents）与未调用的 emit（generated）
- **位置**：frontend/src/views/project/components/ProjectBudgetPanel.vue:163-167（声明）、:370（唯一 emit 是 'saved'）；ProjectDetail.vue:142（仅传 :project-id/:readonly）
- **类别**：死代码（报价区块移除后的残留接口）
- **影响范围**：无用户感知；后续开发者传 documents 期待生效却无效，监听 @generated 永远收不到。
- **修复建议**：删除这两个 props 与 'generated' emit 声明。
- **优先级**：P3
- **复核状态**：verified。

#### 18. 后端 confirm 端点无前端消费方
- **位置**：backend/src/main/java/com/jshz/expert/controller/ProjectBudgetController.java:69-77（POST /meeting-budget/confirm）；frontend/src/api/project.js:185-198（无 confirm 封装）；ProjectBudgetPanel.vue:157（import 行）
- **类别**：未启用代码
- **影响范围**：前端唯一确认入口是 save(confirm=true)；confirm() 幂等端点无 UI 触达（区别于死组件：其逻辑被 archiveConfirmed 复用，非完全无用）。
- **用户场景**：无感知；接口文档若登记独立「确认报价」端点会与实际 UI 不一致，测试用例可能针对不存在的入口编写。
- **修复建议**：补前端封装与入口（已确认单重新触发同步的幂等场景），或在接口文档/交接记录注明该端点当前仅幂等修复用途、无 UI 入口。
- **优先级**：P3
- **复核状态**：verified。复核修正：Controller L69-77（非 74-81）；api/project.js L185-198；Panel import 在 L157（非 152）。

#### 19. ESTIMATE_RULES 兜底常量成为事实上的唯一估算参数真源
- **位置**：frontend/src/views/project/components/budgetEstimate.js:36-42（常量）、:34-35（注释自认须与种子一致）、:57-64（静默回退）
- **类别**：硬编码
- **影响范围**：**复核修正（漂移方向反转）**：init.sql:2228-2230 已于 2026-11-21 移除 EST_* 种子，生产库中这 5 条种子很可能已被 20261121 清理删除——常量不再是「接口缺失兜底」而是事实唯一默认源，管理员在线调价入口已消失（行被删）。素材原 userScenario（管理员改库后接口降级回落旧常量）已不成立。
- **用户场景**：估算静默按常量计算，无任何「按内置默认值估算」提示；费率调整无线上入口。
- **修复建议**：将兜底常量确立为唯一真源并同步 init.sql/清理事宜（记录决策），basis 文案标注「按内置默认值估算」；或对 migration 20261117 种子做读值比对（**原 fix 中「对 init.sql 种子比对」不可行——init.sql 已无 EST_* 种子**）。一致性测试可扩展 budgetEstimate.test.js 既有 fallback 用例。
- **优先级**：P3
- **复核状态**：verified（方向修正如上；生产库行是否已删未连库验证）。

#### 20. watch(projectId, load) 与 :key="projectId" 双重加载触发，watch 不可达
- **位置**：frontend/src/views/project/ProjectDetail.vue:142（:key 强制重建）；ProjectBudgetPanel.vue:378-379（onMounted + watch）
- **类别**：死代码（冗余防御）
- **影响范围**：无用户感知；两套加载触发并存，改动时易引发双重请求。
- **修复建议**：二选一：保留 :key 删 watch（推荐，状态随项目切换整体重置更安全）。
- **优先级**：P3
- **复核状态**：verified（watch 实际 L378-379，素材写 372-373）。

#### 21. 状态字面量散落，违反 17.7 成对约定
- **位置**：后端 6 处（复核数，素材写 4 处）：ProjectBudgetService.java:58、117、149、152；ProjectBudgetQuoteService.java:200、215；前端 1 处：ProjectBudgetPanel.vue:104（`budget?.status === 'CONFIRMED'` 裸比较）；database/init.sql:2153（status VARCHAR(20) 无 CHECK 约束）。另有测试 2 文件 8 处。
- **类别**：硬编码（违反 AGENTS.md 17.7「权威定义在后端、前端为展示层副本」）
- **影响范围**：无 ProjectBudgetStatus 枚举、前端无 statusMap；任一侧改值无法静态发现另一侧遗漏。
- **用户场景**：后续新增状态（如已作废）漏改 QuoteService.requireConfirmed 或前端徽标，出现死分支且编译期无法发现。
- **修复建议**：后端建 ProjectBudgetStatus 枚举统一引用；前端 statusMap 成对登记；可选在迁移中补 CHECK（MySQL 8.0.16+）。
- **优先级**：P3
- **复核状态**：verified（后端实为 6 处含遗漏的 L149；QuoteService 行号修正为 200/215；前端为 L104）。

#### 22. 项目大类切换后预算保存死锁，违规行不可见
- **位置**：backend/src/main/java/com/jshz/expert/service/ProjectBudgetService.java:328-332（分节×category 校验）、:93（save 每次重快照）、:289-293（categoryOf）；ProjectBudgetPanel.vue:193-199（isOnline/activeSections 过滤 ONLINE_QUOTE）、:344-354（全量提交不清理异类分节行）
- **类别**：边界问题
- **影响范围**：项目从 ONLINE 改 OFFLINE 后，旧 ONLINE_QUOTE 行不渲染但随 items 提交，保存必抛「线下项目不允许线上报价分节」；且 overview 返回旧 category，前端仍渲染线上 UI——无论哪种视图保存都被拒且不可自愈。
- **用户场景**：管理员把线上项目改为线下，负责人打开预算面板点保存即报错，刷新重试同样报错，只能求助开发。
- **修复建议**：save 时按新 category 重映射或丢弃不兼容分节（迁移语义需负责人拍板），或报错文案指明具体费用项；前端保存前过滤异类分节行。
- **优先级**：P3
- **复核状态**：verified（校验在 L328-332，素材写 351-357；activeSections 在 L197-199）。

#### 23. 阶段推进后 confirm 的「确认」与「归档」不一致
- **位置**：ProjectBudgetController.java:59-60（save confirm=true → archiveConfirmed）；ProjectBudgetQuoteService.java:52（非预算阶段静默 return，无日志无异常）；ProjectBudgetService.java:74-132（save 无阶段校验）
- **类别**：边界问题
- **影响范围**：预算阶段之后经 API 重确认：状态升级、budget_amount 覆盖生效，但新报价不归档、无提示；门禁仍凭旧报价放行，客户可见报价与现行预算脱钩。generate/restore 有 requireBudgetPhase 保护，唯独 save 路径无，不对称。
- **用户场景**：项目已到日程上传阶段，负责人接口重确认调整后预算：金额更新，资料里客户可见的仍是旧版报价，无人被告知不一致。
- **修复建议**：非预算阶段 confirm 时记录操作日志/警告「本次确认未归档新报价」；或归档条件放宽为内容指纹变化即归档（负责人决策）。
- **优先级**：P3
- **复核状态**：verified（门禁 missingRequiredTemplates 实现未逐行核对，属素材推断，与已核实行为逻辑自洽）。

#### 24. estimateInputs 长度上限 1000 仅后端抛错，前端无预检
- **位置**：ProjectBudgetController.java:59-62（超 1000 抛「估算参数过长」）；ProjectBudgetPanel.vue:343-376（save 无长度预检）、:356（直接序列化整个 estimateState 含 basis）；ProjectBudgetEstimate.vue:104-110（basis 按 itemKey 持久累积）
- **类别**：边界问题
- **影响范围**：多次预览产生多条长中文 basis 后保存被拒，报错不含超限字数，用户无引导。
- **用户场景**：用户多次调整估算参数点保存草稿，收到「估算参数过长」，不知道哪里超了。
- **修复建议**：保存前前端检查 JSON.stringify(estimateState).length 并提示；或后端按 basis 条目截断/放宽上限，超限返回具体字数。
- **优先级**：P3
- **复核状态**：verified（Controller 校验在 L60-62，素材写 62-65）。

#### 25. mapList 静默丢弃非 Map 元素（校验口径不一致）
- 详见 P2 第 5 条（合并），本条保留索引：ProjectBudgetController.java:125-131，P3→并入 P2 定级。复核状态：verified。

#### 26. parseItem 数量/单价无上限校验
- **位置**：ProjectBudgetService.java:316 起（parseItem），数量/单价非负校验 ~L341-344，之后直接 setScale(2)（L346-349）；DDL：init.sql:2195-2200（quantity DECIMAL(10,2)，amount/cost_price/cost_amount DECIMAL(14,2)）
- **类别**：边界问题
- **影响范围**：极端输入（quantity 1e12 等）落库 MySQL 报 Out of range，用户收到 500 通用「保存失败」而非业务提示；数量超 2 位小数按 DECIMAL(10,2) 截断/严格模式报错；cost_price/cost_amount 同样仅有非负校验。
- **用户场景**：粘贴错误输入极大数量，保存时后端数据库溢出报错，前端只显示通用失败。
- **修复建议**：parseItem 增加上限（quantity ≤ 1e6、price ≤ 1e8）与数量小数位 ≤2 校验，超限抛业务异常；一并覆盖 cost_price。
- **优先级**：P3
- **复核状态**：verified（依据 DDL 与代码静态推断，未连库复现溢出）。

#### 27. restore() 冗余写 + requireConfirmed 文案语义错误
- **位置**：ProjectBudgetQuoteService.java:186-206（restore：L199-203 先 budgets.update 置 DRAFT/写 remark，L206 budgetService.save(false) 再次覆盖）、:191（requireConfirmed 调用点）、:209-213（budget==null 与非 CONFIRMED 抛同一文案「已有编辑中的草稿，请先处理当前草稿」）
- **类别**：缺陷（休眠路径，无前端入口）
- **影响范围**：同一行两次写；budget 从未保存过时提示「已有草稿」误导。
- **用户场景**：预算单不存在时调 restore，被「已有编辑中的草稿」误导。
- **修复建议**：删除冗余 update（save(false) 已完成置位）；budget==null 用独立文案「请先保存并确认预算单」。
- **优先级**：P3
- **复核状态**：verified。

#### 28. materialQuantityItem 死代码 + 无参 activeFeeStandards() 未调用（两条发现合并）
- **位置**：ProjectBudgetService.java:428-435（materialQuantityItem，全仓库无调用点）、:297（无参 activeFeeStandards() 无外部调用方）
- **类别**：死代码
- **影响范围**：无运行时影响；误导后续维护者以为物料数量规则仍在骨架路径生效。**复核修正**：「与 feeItem 一同构成骨架遗留」不成立——feeItem（:445）仍被 :394 在 ONLINE_QUOTE 预填路径活跃调用，仅 materialQuantityItem 一处遗留。素材另一条写位置 455-462 有误（那是 estimateItem 的一部分）。
- **用户场景**：不直接影响用户；后续 Agent 误判骨架行为，做错误改动。
- **修复建议**：删除两方法，`mvn compile` 验证（本次未执行编译）。
- **优先级**：P3
- **复核状态**：verified。

### 未确认条目说明

所有素材发现均标记 verified 并通过复核；无「未确认」条目。需注意的保留限定：
- P1-2 / P2-3 的「后端端点线上可达」为代码层判断，运行时状态未实测；
- P2-13 的生产库是否已执行 20261121 迁移、P3-19 的 EST_* 行是否已删，均未连库核验（只读审查、无凭据）；
- P2-6 的「171 条模板项」条数未连库核验；
- P2-10 的前端「加载后不刷新即提交」行为未核验；
- P3-26 的数据库溢出为静态推断，未复现。

## 第二部分：新需求建议

1. **预算状态机收敛与客户确认入口重建**
   - 价值：解决 P1-2/P2-3/P2-9 的根因——CONFIRMED 生命周期（确认、降级、恢复、客户确认）在 UI 上有半数迁移不可达且无审计。重建统一入口后状态可追溯，误操作可恢复。
   - 实现边界：需负责人先拍板「导出即对客报价」是否终局（决定删除还是恢复 ProjectBudgetQuotes 入口）；确认/降级操作加 operationLog 审计。
   - 改动范围：frontend/src/views/project/components/ProjectBudgetPanel.vue（或恢复 ProjectBudgetQuotes.vue）、frontend/src/api/project.js:195-213、backend ProjectBudgetQuoteController.java、ProjectBudgetQuoteService.java、新增 OperationLog 写入。

2. **预算单乐观锁与防回退校验**
   - 价值：消除 P2-10 并发静默覆盖与 CONFIRMED 被并发草稿覆回的风险，多人协作安全。
   - 实现边界：project_budget 加 version 列（migration + init.sql 双写，过 check-schema-parity）；前端提交携带加载时 update_time；覆盖 CONFIRMED 需显式参数。涉及数据库变更，走迁移审批流程。
   - 改动范围：database/migration/（新脚本）+ init.sql、ProjectBudget.java、ProjectBudgetService.java:86-125、ProjectBudgetPanel.vue save。

3. **费率与估算参数单一真源化**
   - 价值：解决 P2-4/P3-19 的口径漂移：费率、估算参数各定一处真源（后端字段 / 前端常量+决策记录），页面与导出永远一致。
   - 实现边界：前端改读 budget.serviceRate/taxRate（改动小）；ESTIMATE_RULES 真源归属需负责人决策（常量 vs 恢复收费标准库 EST_* 行）。
   - 改动范围：ProjectBudgetPanel.vue:241-243/116/118、budgetEstimate.js:34-42、budgetEstimate.test.js（fallback 测试扩展）、（可选）database/migration/。

4. **预算确认守卫（前后端双层）**
   - 价值：封死 P1-1/P2-5/P2-12 三条 0 元/空明细/0 单价确认路径，保护财务登记金额与对客报价文件。
   - 实现边界：后端 save 的 confirm 分支与 archiveConfirmed 前置校验；前端确认按钮与 0 单价行警告；mapList 口径对齐。
   - 改动范围：ProjectBudgetService.java（save/parseItem/mapList 调用处）、ProjectBudgetQuoteService.java:105-118、ProjectBudgetController.java:125-131、ProjectBudgetPanel.vue:110/204/206-216/343-376。

5. **收费标准库权限收紧与类目字典收口**
   - 价值：封 P2-6 的全员可读成本价漏洞；消除 P3-16 的 CATALOGS 双写漂移。
   - 实现边界：GET 规则按 path 收窄重建（复用 20261029 迁移回滚参考），注意不得影响 config/phases、config/types 字典的 EMPLOYEE 读取（20261029 的原始目的）；CATALOGS 抽 utils/dict.js。
   - 改动范围：database/migration/（新脚本）+ init.sql 权限段、ProjectBudgetController.java:25 注释、ProjectBudgetPanel.vue:266、BudgetFeeConfig.vue:158、frontend/src/utils/dict.js（新增）。

6. **状态枚举成对登记（17.7 合规）**
   - 价值：消除 P3-21 的 7 处字面量漂移风险，新增状态可静态发现遗漏。
   - 实现边界：后端枚举 + 前端 statusMap，一次提交成对修改；可选 CHECK 约束需评估 MySQL 版本。
   - 改动范围：backend 新增 ProjectBudgetStatus 枚举、ProjectBudgetService.java:58/117/149/152、ProjectBudgetQuoteService.java:200/215、ProjectBudgetPanel.vue:104。

## 附录：审查范围文件清单与状态流转说明

### A1. 审查范围文件清单（31 个，全部只读）

前端（9）：
- frontend/src/api/project.js（L183-231 预算段 + 报价单 5 函数 L195-213）
- frontend/src/router/index.js（/project/:id L165-167；/admin/project/config L202-205）
- frontend/src/views/project/ProjectDetail.vue（预算面板挂载 L140-142）
- frontend/src/views/project/components/ProjectBudgetPanel.vue
- frontend/src/views/project/components/ProjectBudgetEstimate.vue
- frontend/src/views/project/components/budgetEstimate.js（含 node --test 测试，本次未运行）
- frontend/src/views/project/components/budgetEstimate.test.js
- frontend/src/views/project/components/ProjectBudgetQuotes.vue（死组件）
- frontend/src/views/admin/BudgetFeeConfig.vue、frontend/src/utils/common.js

后端（15）：
- backend/src/main/java/com/jshz/expert/controller/ProjectBudgetController.java、ProjectBudgetQuoteController.java
- backend/src/main/java/com/jshz/expert/service/ProjectBudgetService.java、ProjectBudgetQuoteService.java、ProjectBudgetExportService.java、ProjectService.java（仅 PHASE_BUDGET_MAKING 相关）
- backend/src/main/java/com/jshz/expert/entity/ProjectBudget.java、ProjectBudgetItem.java、ProjectBudgetQuote.java、ProjectBudgetFeeStandard.java
- backend/src/main/java/com/jshz/expert/mapper/ProjectBudgetMapper.java、ProjectBudgetItemMapper.java、ProjectBudgetQuoteMapper.java、ProjectBudgetFeeStandardMapper.java、ProjectDocMapper.java

数据库（7）：
- database/init.sql（L2150-2260 相关段、权限段 1428-1431、EST_* 注释 2228-2231）
- database/migration/20261116_budget_stage.sql、20261116_budget_fee_scene.sql、20261117_budget_estimate_quote.sql、20261117_estimate_rules_fee_standards.sql、20261120_budget_fee_library.sql、20261121_cleanup_no_catalog_fees.sql

### A2. 状态流转说明（本次核实）

**预算单 project_budget.status**（VARCHAR(20)，无枚举/CHECK，仅两值）：
- `save(confirm=true)`：任意 → CONFIRMED（同事务内 overrideBudgetAmount 覆盖 project.budget_amount，并经 Controller 调 archiveConfirmed 归档报价——仅预算阶段；非预算阶段归档静默跳过）
- `save(confirm=false)`：任意 → DRAFT（**无守卫**：已确认单被静默降级，见 P2-9；budget_amount 不回退）
- `confirm()`：DRAFT → CONFIRMED 幂等（端点无 UI 入口，P3-18）
- `restore()`：CONFIRMED → DRAFT（重放快照；**UI 不可达**，P1-2）
- 2026-11-21 已移除 CONFIRMED 拒写守卫（ProjectBudgetService.java:91-92 注释）。

**报价单 project_budget_quote**（无显式 status 字段，以时间戳表达）：
- 生成：versionNo 递增 + snapshot 快照 + doc_id（archiveConfirmed 归档路径在线；手动 generate 路径 UI 已死）
- 客户确认：customerConfirmedAt/customerName/customerRemark/evidenceDocId（**UI 不可达**，字段将永久 NULL）
- 恢复：restore 用快照覆盖预算明细（UI 不可达）

**阶段门禁**：ProjectService.PHASE_BUDGET_MAKING="会议预算制作"（ProjectService.java:96-97）；missingRequiredTemplates 要求已生成报价单文件（QUOTE_FILE_PREFIX，L2603-2608，经 ProjectDocMapper.listBudgetQuotePaths 内联 @Select），只查文件存在不校验金额/明细——空报价文件即满足门禁（P1-1）。

**同名概念边界**：供应商报价、登记预算（budget_amount 登记入口）中仅项目管理会议预算流程部分纳入；DownloadAudit.vue 的 REGISTER_BUDGET 文案、settlementPresentation.js 的 /预算单/ 正则未列入。

### A3. 本报告的证据与验证声明

- 所有发现与复核均来自素材（任务文件 .zwork/runs/a82a5c8b/task-flow-writer-报告撰写-0e3c3ce64a.md 中的 JSON findings 及其 confirmNote），本报告为整理与合并，未新增任何发现。
- 复核中的行号修正已并入正文各条「复核状态」；重复发现的合并已在条目中注明。
- 本次未运行任何构建、测试或数据库查询（只读审查）；P3-28 的 `mvn compile` 验证建议属未执行项。
