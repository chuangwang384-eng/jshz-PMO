# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261126-FINANCE-STAGE-P3
- 任务名称：会前财务资料上传阶段 P3 六项整改（Excel 原生日期 / 并发覆盖守卫 / 默认值同步 / 地点消歧 / 回滚清理 / 读取失败显式化）
- 主责 Agent：AutoClaw（本会话）
- 交接时间：2026-11-26
- 当前状态：已完成待验收（**未发布**：后端 jar 与前端快照均未切换，生产仍为 1.0.158）
- 优先级：P3
- 前置输入：
  - 审查报告（本会话《会前财务资料上传阶段 — 开发审查报告》，P3 共 6 项）；
  - [P0](20261126_finance_stage_p0.md) / [P1](20261126_finance_stage_p1.md) / [P2](20261126_finance_stage_p2.md) 整改交接（同批 1.0.159，一起发布）。

## 2. 目标与验收条件
- 业务目标：修掉审查报告 P3 六项——跨天会议日期写文本单元格、整包覆盖无并发保护、默认值不随项目信息同步、表单「地点」与项目属性同名不同义、归档回滚留孤儿文件、读取失败被静默吞掉。
- 本次范围：仅「项目管理 → 会前财务资料上传」阶段相关前后端文件、1 个新增回归测试、本记录与架构文档。审查范围外模块零改动。
- 不在本次范围：
  - 审查报告的「新需求建议」8 条未实施；
  - 数据库结构、权限码、环境变量、部署脚本零改动（**本次刻意不引入 DDL**，见第 8 节 P3-23 的方案取舍）；
  - 模板两处口径不一致未改模板（沿用 P1/P2 批次待负责人确认项）。
- 验收条件：
  - [x] P3-22 单日会议日期按 Excel 原生日期写入（可排序/筛选/计算），跨日保留区间文本；
  - [x] P3-23 表单并发覆盖守卫（版本令牌不一致 → 409 拒绝并提示刷新）；
  - [x] P3-24 未保存过的字段默认值随项目信息实时同步（已保存过的以财务口径为准）；
  - [x] P3-25 财务表单「地点」改名「会议地点（具体地址）」，与项目属性「地点（线上/线下）」消歧；
  - [x] P3-26 归档事务回滚时清理本次新生成的物理文件；
  - [x] P3-27 表单内容读取失败显式报错 + 禁止提交；
  - [x] 新增后端回归 9 例，见红 5 失败 → 见绿 9/9；
  - [x] 后端全量 437 例 0 失败；前端 `npm run build` 通过；
  - [ ] 生产环境验证（发布后另行验收）。

## 3. 项目基线
- 分支：main；起点提交 `8758976`。
- 版本：`version.txt` revision=1.0.159（P0 批次已 bump，未发布）；本次属同一未发布版本，**不再重复 bump**。
- 生产实况（2026-11-26 只读核实）：`GET http://127.0.0.1:8080/health` → version=1.0.158、gitShort=ea37672、status=UP。未发布、未重启。
- 备份位置：`<LOCAL_PATH>`（改动前 4 个源文件逐份副本 + 实施后快照 `.P3FIXED.java`，供见红演练用）。
- **工作区并行改动说明**：本批次期间工作区存在其他 Agent 的未提交改动（企微 API 客户端、操作日志切面、文件名清洗、IPUtils 防伪造等）。本记录只覆盖财务阶段相关文件；未触碰他人改动，也未代为提交。

## 4. 已完成工作（对照审查报告 P3 编号）

| 发现 | 内容 | 状态 | 证据 |
| --- | --- | --- | --- |
| P3-22 | 跨天会议日期以拼接字符串写入 Excel | 完成（**部分修复，见下方「修复边界」**） | 单日会议日期写 Excel **原生日期**（模板日期列自带 `yyyy/m/d;@` 格式，写入后可排序/筛选/计算天数）；跨日会议保留区间文本 | 单测 `singleDayMeetingDateIsWrittenAsNativeExcelDate`（断言单元格类型 NUMERIC + `DateUtil.isCellDateFormatted` + 日期值）、`multiDayMeetingDateKeepsTheRangeText`、`keyTypeSheetWithoutDateFormatStillShowsADate`（重点类日期列无日期格式，自动补格式避免显示序列号） |
| P3-23 | 整包覆盖无版本号，并发下后写静默覆盖先写 | 完成 | 以「读取时的最后保存时间」（`project_stage_form.update_time`，秒级）为**版本令牌**：客户端保存时回传 `version`，与库中当前行不一致即抛 **409**「表单已被他人保存（最后保存时间：…），请刷新后在最新内容上重新填写」；前端保存成功后用响应里的新时间戳刷新令牌。**未携带 version 的旧客户端保持原行为**（不做校验，向后兼容） | 单测 `savingWithAStaleVersionIsRejected`（409 且不写库）、`savingWithTheCurrentVersionSucceeds`、`savingWithoutAVersionKeepsTheLegacyBehavior` |
| P3-24 | 部门/客户对接人默认值仅在首次加载带出 | 完成 | 记录「已保存过的字段」集合；**仅未保存过的字段**在项目信息变化时重新取项目默认值（部门 ← 团队、客户对接人 ← 客户姓名）；已保存过的字段以财务口径为准（沿用设计文档「允许保存财务口径」）。用户手动改过的字段不会被项目信息刷新覆盖（用「已套用默认值」比对判断） | 前端 `syncProjectDefaults` + `appliedDefaults`；保存后同步更新 `savedKeys` |
| P3-25 | 表单「地点」与项目主表 category 语义重复且不联动 | 完成（**改名消歧**） | 财务表单字段标签由「地点」改为「**会议地点（具体地址）**」（模板表头仍为「地点」，由对齐测试的别名表登记差异）；项目属性侧仍是「地点：线上/线下」，两处不再同名 | 单测 `locationFieldIsDisambiguatedFromTheProjectCategory`；对齐测试别名表新增一条 |
| P3-26 | 归档事务回滚时新文件成为磁盘孤儿 | 完成 | `archiveStageDocument` 在落盘新文件后登记 `afterCompletion` 钩子：**事务回滚即删除本次新文件**，提交则保留（文件已被新记录引用） | 单测 `archivingRollsBackAndCleansTheNewFile`（回滚 → `deleteFile` 被调用）、`archivingOnCommitKeepsTheNewFile`（提交 → 不删，且正常插入记录） |
| P3-27 | 前端表单读取失败被静默吞掉 | 完成 | 新增 `loadError` 状态：读取失败时页面显示「表单内容加载失败 + 重新加载」并**不渲染表单**（保存按钮不可见），`save()` 另有兜底拦截；不再出现「空表单 + 尚未填写」被误当成从未保存而覆盖 | 前端 `loadError` / 模板失败态 / `save()` 守卫 |

### 修复边界（P3-22 的诚实说明）
- 模板的「会议日期」列**只有一个单元格**，装不下跨日会议的两个原生日期。
- 因此：**单日** → 原生日期（可排序/筛选/计算）；**跨日** → 保留区间文本（`2026-11-21 至 2026-11-23`，ISO 前缀可正确字典序排序）。
- 完全修复需模板增加「开始日期/结束日期」两列 —— 属**模板结构变更**，未获授权故未做（见第 7 节）。
- 另修正：重点类 sheet 的日期列在模板中无日期格式（`numFmtId=0`），写原生日期会显示序列号 → 生成时自动为该单元格补 `yyyy/m/d` 格式（仅影响生成件，不改模板文件）。

## 5. 实际变更
- 新增文件：
  - `backend/src/test/java/com/jshz/expert/service/ProjectFinanceStageFormP3Test.java`（9 例，Mockito 夹具，沿用 P0/P1 批次既有模式）；
  - 本记录 `docs/handover/tasks/20261126_finance_stage_p3.md`。
- 修改文件（4 个源文件 + 1 个测试 + 文档）：
  - `backend/.../service/ProjectFinanceWorkbook.java`：`Field` 增 `excelDate` 标记（会议日期）；字段定义改用可读工厂方法（`text`/`requiredText`/`conditionalText`/`textarea`/`select`/`number`/`projectField`）；`generate` 对 `excelDate` 字段写原生日期；新增 `parseIsoDate`/`writeDateCell`；`location` 标签改名；
  - `backend/.../service/ProjectService.java`：新增 `assertStageFormVersionUnchanged`（P3-23 版本令牌守卫）与 `deleteGeneratedFileOnRollback`（P3-26 回滚清理）；`archiveStageDocument` 登记回滚清理钩子；
  - `frontend/src/views/project/components/ProjectFinanceForm.vue`：新增 `loadError` 读取失败态（含「重新加载」按钮、保存拦截）；`savedKeys`/`appliedDefaults` 与 `syncProjectDefaults`（P3-24）；保存请求携带 `version`（P3-23）；保存成功后刷新令牌与 `savedKeys`；
  - `backend/src/test/.../ProjectFinanceTemplateAlignmentTest.java`：表头别名表新增「会议地点（具体地址）→ 地点」；
  - 文档：`docs/architecture.md`（P3 整改小节）、`docs/handover/README.md`（摘要条目）、本记录。
- 数据库/迁移：**无**。P3-23 刻意采用「更新时间令牌」而非新增 `version` 列（理由见第 8 节），因此**不引入 DDL、不改变 1.0.159 的发布形态**（无需迁移、无先后顺序约束）。
- API 契约变更：
  - `PUT /api/project/{id}/stage-form`：**新增可选字段 `version`**（字符串，= 客户端读取到的 `updateTime`）。携带且与库中不一致 → 409 + 中文提示；不携带 → 旧行为不变。响应结构不变。
  - `GET /api/project/{id}/stage-form`：**结构不变**（`updateTime` 本就返回，可直接作为版本令牌）。
- 权限：无权限码/角色绑定改动。
- 配置/环境变量：无新增。

## 6. 测试与验证

| 验证项 | 执行命令/步骤 | 结果 | 备注 |
| --- | --- | --- | --- |
| 见红演练 | 把 `ProjectFinanceWorkbook`（原生日期分支、location 标签）与 `ProjectService`（版本守卫、回滚清理）临时改为改动前行为，跑 `mvn -o "-Dtest=ProjectFinanceStageFormP3Test" test` | **9 例中 5 失败**：日期写成 STRING 而非原生日期（含重点类）、location 标签未消歧、过期 version 未被拒（未抛异常）、回滚未清理新文件 | 失败原因均为待实现行为或原缺陷，非环境/构建问题；另 4 例（旧客户端不带 version、提交保留文件等）仍通过，符合预期 |
| 目标回归 | 恢复实现后同一命令 | **9/9 通过** | |
| 后端全量回归 | `mvn -o "-Dspring-boot.build-info.skip=true" test`（JWT_SECRET 已设临时值） | **437 例，0 失败 0 错误，16 跳过**，BUILD SUCCESS | 428（P0+P1+P2）+ 9（P3） |
| 前端构建 | `npm run build`（`<LOCAL_PATH> Files\nodejs\npm.cmd`） | 通过（built in 41.01s；改 P3-24 保存后同步后复跑亦通过） | |
| 模板日期列格式取证 | 只读解析 `project-finance.xlsx` 的 `xl/styles.xml` 与各 `sheet*.xml` | 平台类 H3 / 直付类 H3 = `s=63/69` → `numFmtId=164`（`yyyy/m/d;@`，**日期格式**）；重点类 C3 = `s=2` → `numFmtId=0`（无日期格式） | 该取证直接决定了 P3-22 的写法（原生日期 + 重点类补格式） |
| 残留 grep | 前端 `loadError`/`savedKeys`/`version` 接线；后端 `excelDate`/`assertStageFormVersionUnchanged`/`deleteGeneratedFileOnRollback` | 均已接线并被测试覆盖 | |
| 页面手工回归 | — | **未执行** | 需发布后验收（清单见第 10 节） |
| 生产库 / 生产服务 | — | **未执行** | 无迁移需求；未发布、未重启 |

## 7. 未完成事项与阻塞

| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 跨日会议日期在 Excel 中仍为文本 | 模板「会议日期」列只有一格，装不下两个原生日期；改模板结构需授权 | 跨日会议行在 Excel 里仍不能参与日期计算（可排序，ISO 前缀） | 需负责人决定是否给模板增加「开始日期/结束日期」两列（属模板结构变更） | 负责人 |
| 模板口径不一致 ①/②（沿用 P1/P2 批次） | 见 P1 交接记录 | 同前 | 同 P1 交接记录 | 负责人 |
| P3-23 版本令牌为秒级时间戳 | `project_stage_form.update_time` 为 `DATETIME`（秒级）；新增 `version` 列需 DDL 与发布迁移 | 理论上「同一秒内的两次保存」无法区分（人手编辑表单不可能同秒完成）；实务无影响 | 若要求严格，可后续按 `project_budget.version` 的先例加列（届时需迁移 + 先迁移后切后端） | 负责人 |
| 审查报告「新需求建议」8 条 | 本次只做 P3 | 见审查报告 | 另开任务 | 负责人 |
| 发布 1.0.159 | 未获发布授权 | P0+P1+P2+P3 共 27 项修复仅在代码库 | 走 release-full 流程（无迁移） | 负责人 |

## 8. 风险与回滚
- 已知问题：
  - **P3-23 的令牌是「最后保存时间」而非递增版本号**：秒级精度；同一秒内的连续保存无法区分（已在第 7 节登记）。若同一用户连续快速保存两次且两次内容不同，第二次可能不被拦（内容仍会正确保存，只是失去了「冲突提示」）。
  - **P3-22 造成同一列出现两种类型**：单日行为原生日期、跨日行为文本，Excel 的筛选下拉会同时出现两类值。这是单列模板的固有限制，已在第 4 节说明。
  - **P3-26 的回滚清理依赖事务同步器**：若归档在**无事务上下文**中被调用（当前仅 `saveStageForm` 调用，始终带事务），不会有回滚语义，文件不清理（与既有行为一致，不会误删已被引用的文件）。
  - **P3-25 的改名只改表单标签**：归档件（Excel）的表头仍是模板原文「地点」，两者不同名是**有意为之**（模板未授权变更）；对齐测试以别名表登记该差异。
- 数据风险：低。①P3-23 只增加写入前的校验，不改数据结构；被拒的写入不落库；②P3-24 只改前端内存中的默认值，不改已存数据；③P3-26 只在事务回滚时删除「本次新生成、尚未被任何记录引用」的文件。
- 兼容风险：
  - 旧前端 + 新后端 → 不带 `version`，不触发并发校验，行为与现在一致；
  - 新前端 + 旧后端 → 新前端会带 `version` 字段，旧后端忽略未知字段（`body.get("version")` 未被读取）→ 行为等同旧版（无并发保护），不报错。
- 回滚步骤：
  - 代码：`git revert` 本次提交（4 个源文件 + 1 个测试 + 2 个文档文件，无数据迁移需回滚）；
  - 手工兜底：从 `<LOCAL_PATH>` 还原 `backend__ProjectFinanceWorkbook.java`、`backend__ProjectService.java`、`backend__ProjectFinanceTemplateAlignmentTest.java`、`frontend__ProjectFinanceForm.vue`；
  - 版本：`version.txt` / `frontend/package.json` 保持 1.0.159；
  - 无 DDL / 权限 / 配置回滚项。

## 9. 文档同步
- [x] 架构文档（`docs/architecture.md` 新增「会前财务资料上传阶段 P3 整改（1.0.159）」小节）
- [x] 交接记录（本文件；P0/P1/P2 批次见同目录对应记录）
- [x] 最新交接摘要（`docs/handover/README.md` 已加条目）
- [x] 测试说明（本记录第 6 节；新增自动化回归 9 例）
- [ ] 部署文档（本次无迁移/环境变量/部署方式变更，`docs/deployment.md` 无需改；发布记录随发布任务生成）
- [ ] 接口文档（仓库无独立接口文档文件，`version` 字段的契约以本记录第 5 节 + 架构文档为准）
- [ ] 数据库迁移 / 数据字典（不适用：无 DDL 变更）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`（应只有 P0+P1+P2+P3 四批改动 + 他人并行改动，**勿代提交他人文件**）；`mvn -o "-Dtest=ProjectFinanceStageFormP3Test" test` 应 9/9；`npm run build` 应通过。
2. 然后执行（获发布授权后）：`scripts/release-full.ps1 -Mode Publish -Version 1.0.159`（**无迁移，可直接发布**）；仍需确认**后端先于前端**切换（P2-⑬ 新前端依赖 `finance-schema` 端点）。
3. 发布后手工回归清单（在前三批清单基础上追加）：
   - 平台类项目保存表单 → 下载的《合规立项申请表.xlsx》：「会议日期」单元格**双击显示真日期**（非左对齐文本），按该列可排序；
   - 跨日会议项目保存 → 日期单元格显示「起始 至 结束」区间文本（信息不丢）；
   - 两个浏览器同时打开同一项目表单：A 保存后，B 再保存 → B 得到「表单已被他人保存…」提示且**内容未被覆盖**；
   - 项目归属部门调整后，未保存过财务表单的项目 → 表单「会议归属部门」自动跟随新部门；已保存过的保持财务口径不变；
   - 财务表单「会议地点（具体地址）」与详情页项目属性「地点：线上/线下」不再同名；
   - 断网/弱网下打开已保存过的表单 → 显示「表单内容加载失败 + 重新加载」，**无保存按钮**（不会误覆盖）。
4. 未经确认禁止：修改 `templates/project-finance.xlsx`（模板结构与两处口径不一致待负责人拍板）；给 `project_stage_form` 加 `version` 列（需 DDL，须先迁移后切后端并经负责人批准）；放宽 `assertStageFormVersionUnchanged` 的令牌口径。
5. 需要负责人决策：跨日会议日期是否给模板加「开始/结束日期」两列；模板「上海独家记亿 vs 独家记忆」；直付类我方公司 4 项简称 vs 6 项全称；P3-23 是否改用递增 `version` 列；审查报告新需求 8 条的排期。
