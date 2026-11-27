# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261126-FINANCE-STAGE-P0
- 任务名称：会前财务资料上传阶段 P0 四项整改（归档件禁删与独立展示 / 存量项目可编辑 / 缺项目类型直达提示 / 阶段表单读取脱敏）
- 主责 Agent：AutoClaw（本会话）
- 交接时间：2026-11-26
- 当前状态：已完成待验收（**未发布**：后端 jar 与前端快照均未切换，生产仍为 1.0.158）
- 优先级：P0
- 前置输入：本会话《会前财务资料上传阶段 — 开发审查报告》（27 项发现：P0×4 / P1×8 / P2×9 / P3×6，附 8 条新需求建议）。本次只做 P0 四项，P1 及以下未实施。

## 2. 目标与验收条件
- 业务目标：修掉审查报告 P0 四项——归档件误删导致阶段推进被卡死、存量项目无法编辑、缺项目类型时无法推进且报错误导、非项目成员越权读取财务表单与归档件。
- 本次范围：仅「项目管理 → 会前财务资料上传」阶段相关的前后端文件、1 个新增回归测试、本记录与架构文档。审查范围外模块零改动。
- 不在本次范围：
  - 审查报告 P1（8 项）、P2（9 项）、P3（6 项）全部未动，包括「暂未配置」文案错位、前后端成员口径不一致、必填校验不定位、归档件与页面口径分裂、`project_finance_` 归档残留等；
  - 新需求建议 8 条未实施；
  - 数据库结构、权限码、环境变量、部署脚本零改动。
- 验收条件：
  - [x] P0-① 归档件禁止删除（后端硬拦截 + 前端独立展示、不给删除按钮）；
  - [x] P0-② 存量项目（`project_type` 为 NULL）可编辑保存其它字段，不再被项目类型必填拦截；已设类型项目不允许清空；
  - [x] P0-③ 缺项目类型时给出根因缺失项「选择项目类型」并提供直达「编辑项目」的入口；
  - [x] P0-④ 阶段表单读取对非真实项目参与人脱敏；归档件对非参与者不可见/不可下载；
  - [x] 新增后端回归测试 12 例，见红 6 失败 + 1 错误 → 见绿 12/12；
  - [x] 后端全量 404 例 0 失败；前端 `npm run build` 通过；
  - [ ] 生产环境验证（发布后另行验收）。

## 3. 项目基线
- 分支：main；起点提交 `8758976`（工作区起点干净）。
- 版本：`version.txt` revision=1.0.158 → 本次 bump 为 **1.0.159**（`frontend/package.json` 同步 1.0.159；pom `${revision}` 由构建流程从 version.txt 派生，未手改）。
- 生产实况（2026-11-26 17:42 只读核实）：`GET http://127.0.0.1:8080/health` → version=1.0.158、gitShort=ea37672、status=UP。本次未发布、未重启服务。
- 运行环境：本机生产 <LOCAL_PATH> 8080 / Nginx 前端）；前端构建产物在 `frontend/dist`（gitignore，未进版本库）。

## 4. 已完成工作（对照审查报告 P0 编号）

| 发现 | 内容 | 状态 | 证据 |
| --- | --- | --- | --- |
| P0-① | 归档件禁止删除：`deleteDoc` 对 `project_finance_` 前缀抛业务异常（提示回表单重新保存）；前端 `docsOfPhase` 排除该前缀，归档件拆为独立展示区（绿色左边框 + 「系统生成」标签 + 生成人/时间 + 预览链接，无删除按钮） | 完成 | 单测 `financeArchiveCannotBeDeletedFromStageMaterials`；`financeArchiveCannotBeDeletedFromStageMaterials`/`ordinaryStageAttachmentCanStillBeDeleted` |
| P0-② | 存量项目可编辑：`ProjectEdit` 记录原 `projectType`，新建仍必填、已设类型不允许清空、存量空类型不再阻断；payload 仅在有值时下发 `projectType` 键（后端「携带才校验」口径对齐） | 完成 | `ProjectEdit.vue:214-247`；后端 `ProjectService.update:479` 保持原值 |
| P0-③ | 缺项目类型直达：后端 `missingRequiredTemplates` 先判根因输出「选择项目类型」（与「保存阶段表单」互斥）；前端 `resolveLabel`/`resolveMissing` 增加该缺失项 → 直达「编辑项目」弹窗；表单空类型态新增「去编辑项目选择项目类型」按钮与后果说明 | 完成 | 单测 `missingProjectTypeIsReportedBeforeTheFinanceForm` / `financeFormIsRequiredOnceTheProjectTypeIsKnown` |
| P0-④ | 读取脱敏：`getStageForm` 对非真实项目参与人按 `isProjectMember` 判定脱敏（人名掩码为「姓+*」；赞助企业/客户工作单位/SFA 编号/记账部门整体隐去为 `***`）；`listDocs` 对非参与者不返回归档件；`requireArchivedFileReadable` 对非参与者下载/预览归档件返回 403 | 完成 | 单测 `stageFormIsMaskedForReadersWhoAreNotProjectParticipants` / `projectParticipantsStillSeeTheRawStageForm` / `financeArchiveIsHiddenFromReadersWhoAreNotProjectParticipants` / `financeArchiveIsVisibleToProjectParticipants` / `financeArchiveCannotBeDownloadedByReadersWhoAreNotProjectParticipants` / `financeArchiveRemainsDownloadableForProjectParticipants` / `maskHelperKeepsBusinessFieldsAndHidesSensitiveOnes` |
| 附带收口 | `project_finance_` 前缀字面量在 4 处散落 → 收敛为 `ProjectService.FINANCE_ARCHIVE_PREFIX` 单一真源（归档写入 / 必填判定 / 删除保护 / Controller 鉴权共用），符合 AGENTS.md §19.1「第 3 处复制即收口」 | 完成 | grep 无残留字面量（Controller 已改引常量） |

## 5. 实际变更
- 新增文件：
  - `backend/src/test/java/com/jshz/expert/service/ProjectFinanceStageFormGuardTest.java`（12 例，Mockito 夹具，沿用 `ProjectAccessRegressionTest` 既有模式）；
  - 本记录 `docs/handover/tasks/20261126_finance_stage_p0.md`。
- 修改文件（5 个）：
  - `backend/src/main/java/com/jshz/expert/service/ProjectService.java`：
    - 新增常量 `FINANCE_ARCHIVE_PREFIX`（public）、`FINANCE_MASK_PERSON_FIELDS`、`FINANCE_MASK_HIDDEN_FIELDS`（package-private）；
    - 新增 `static void maskFinanceStageForm(Map)` 与 `private static String maskPersonName(String)`；
    - `getStageForm` 增加脱敏调用；`deleteDoc` 增加归档件删除拦截；`listDocs` 增加非参与者过滤；`requireArchivedFileReadable` 增加归档件 403；
    - `missingRequiredTemplates` 由 private 放宽为 package-private（便于测试，与既有 `archiveShowDocument` / `phaseNameOf` 口径一致），并改为「先根因后归档」互斥输出。
  - `backend/src/main/java/com/jshz/expert/controller/ProjectController.java`：`resolveReadableStoredFile` 的 `"project_finance_"` 字面量改引 `ProjectService.FINANCE_ARCHIVE_PREFIX`（行为不变，仅去重）。
  - `frontend/src/views/project/ProjectDetail.vue`：新增 `FINANCE_ARCHIVE_PREFIX` 与 `financeArchiveDoc` computed；`docsOfPhase` 排除归档件；财务阶段模板新增归档件独立展示区；`resolveLabel`/`resolveMissing` 增加「选择项目类型」；新增 `.fin-archive-*` scoped 样式。
  - `frontend/src/views/project/ProjectEdit.vue`：新增 `originalProjectType` / `isLegacyNoType`；保存校验分新建/存量/清空三态；payload 条件下发 `projectType`；存量空类型提示条；新增 `<style scoped>`。
  - `frontend/src/views/project/components/ProjectFinanceForm.vue`：空类型态新增后果说明与「去编辑项目选择项目类型」按钮（`emit('edit-project')`）；新增 `emit('edit-project')` 声明与两条 scoped 样式。
- 修改文件（版本/文档）：
  - `version.txt`（1.0.158 → 1.0.159）、`frontend/package.json`（version 同步）；
  - `docs/architecture.md`（新增「会前财务资料上传阶段 P0 整改（1.0.159）」小节）；
  - `docs/handover/README.md`（新增交接摘要条目）。
- 数据库/迁移：**无**。本次不新增表/列/索引，`database/init.sql` 与 `database/migration/` 未改，无需 db-migrate。
- API 契约变更：
  - `GET /api/project/{id}/stage-form`：**响应内容口径收紧**——非真实项目参与人读到的是脱敏字段（此前是原文）。字段名与结构不变，属向前兼容的最小必要访问收敛。
  - `GET /api/project/{id}/docs`：非真实项目参与人不再返回合规立项申请表条目。
  - `GET /api/project/{id}/files/{fileName}` 与 `/files/{fileName}/workbook`：非真实项目参与人对 `project_finance_` 前缀文件由 200 变 403「合规立项申请表仅项目成员可查看」。
  - `DELETE /api/project/{id}/docs/{docId}`：对 `project_finance_` 前缀文件由「软删成功」变业务异常「合规立项申请表由财务表单自动生成，请在表单中修改后重新保存」。
  - `PUT /api/project/{id}`：**新增「不带 `projectType` 键」这一合法请求形态**（存量项目编辑）。原本缺键即保持原值，后端未改，仅前端开始使用该能力。
  - 阶段推进的缺失项文案新增「选择项目类型」（原「保存阶段表单」仍保留，二者互斥）。
- 权限：无权限码/角色绑定改动。行为变化只在既有权限位（`project:view-all` 全量查看豁免、项目成员口径）之内收敛。
- 配置/环境变量：无新增。
- 备份位置：`<LOCAL_PATH>`（改动前 5 个源文件的逐份副本；`backend__ProjectService.FIXED.java` 为实施后快照，用于见红演练）。目录在 `temp/` 下（gitignore），按 AGENTS.md §0.4「手工临时备份=temp/backup/」归置。

## 6. 测试与验证

| 验证项 | 执行命令/步骤 | 结果 | 备注 |
| --- | --- | --- | --- |
| 见红演练 | 把 `ProjectService.java` 临时回退为改动前行为（仅保留测试所需 API 表面），跑 `mvn -o "-Dtest=ProjectFinanceStageFormGuardTest" "-Dspring-boot.build-info.skip=true" test` | **12 例中 6 失败 + 1 错误**，失败点逐条对应 4 项 P0：归档件仍被删（抛 NPE 而非业务异常）、缺类型仍报「保存阶段表单」、表单未脱敏（读到「张三」原文）、归档件对非参与者仍可见（2 条而非 1 条）、归档件下载未拦截（未抛异常） | 失败原因均为待实现行为或原缺陷，非环境/构建问题；见红后恢复实施版 |
| 目标回归 | 同上命令（实施版） | **12/12 通过** | `mvn -q` 静默模式，Tests run: 12, Failures: 0, Errors: 0 |
| 后端全量回归 | `mvn -o "-Dspring-boot.build-info.skip=true" test`（JWT_SECRET 已设临时值） | **404 例，0 失败 0 错误，16 跳过**（跳过为既有 @Disabled），BUILD SUCCESS | 含 ProjectAccessRegressionTest 23/23、ProjectStageDeliverableTest 12/12 |
| 环境变量缺失下的既有失败 | 同上但不设 `JWT_SECRET` | `ExpertSystemApplicationTests.contextLoads` 报 `WeakKeyException`（key 0 bits） | **与本次改动无关**：JWT 密钥为空是既有 fail-fast 行为；补设临时 `JWT_SECRET` 后该用例 1/1 通过 |
| 前端构建 | `npm run build`（`<LOCAL_PATH> Files\nodejs\npm.cmd`） | 通过（built in 39.12s） | chunk 体积警告为既有状态；`npm.ps1` 被执行策略拦截，改用 `npm.cmd` |
| 编译 | 由 `mvn test` 的 test-compile 阶段覆盖 | 通过 | 无独立 compile 步骤 |
| schema 校验 | 未执行（本次无 DDL 变更） | 不适用 | init.sql 与 migration 未改，无需 parity 复跑 |
| 页面手工回归 | — | **未执行** | 需发布后以真实项目验收：归档件独立展示与禁删、存量项目编辑保存、缺类型直达、越权读取被拒 |
| 生产库 / 生产服务 | — | **未执行** | 无迁移需求；未发布、未重启 |

## 7. 未完成事项与阻塞

| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 发布 1.0.159 | 未获发布授权 | 4 项 P0 修复仅在代码库，生产仍为 1.0.158 | 走 release-full 流程（无迁移，可直接发布） | 负责人 |
| P0-④ 页面级越权实测 | 需发布后用非成员账号验证 | 单测已覆盖服务层判定，未做浏览器实测 | 发布后按第 6 节清单实测 | 负责人 |
| 审查报告 P1/P2/P3（23 项） | 本次只做 P0 | 见审查报告各条 | 另开任务按优先级推进；其中「阶段未开始显示暂未配置」「前后端成员口径不一致」「必填不定位」建议优先 | 负责人 |
| 新需求 8 条 | 未实施 | — | 见审查报告第二部分 | 负责人 |

## 8. 风险与回滚
- 已知问题：
  - P0-④ 会让**仅持 `project:view-all` 的账号**在该阶段表单里看到掩码人名与 `***` 占位，而非原文。这是本次有意的收敛；但若业务上认为这类账号应看到完整负责人姓名，需负责人拍板后调整 `FINANCE_MASK_*` 集合（改一处即可，见 `ProjectService` 常量区）。
  - P0-① 拦截的是**通过阶段资料删除入口**删除归档件；若有人直连数据库把 `is_deleted` 置 1，归档件同样会消失（`validArchive` 依赖 `is_deleted=0`），此时需重新保存一次表单重建。
  - P0-② 允许存量项目不选类型保存后，该项目在会前财务资料上传阶段仍**无法保存表单、无法推进**——这是 P0-③ 的提示所覆盖的既有业务约束，不是本次放宽引入的新卡点。
- 数据风险：低。无 DDL；`project_stage_form` / `project_doc` 数据结构与写入口径未变。唯一新增写入是「编辑项目时不再下发 `projectType` 键」，后端按「携带才校验」保持原值，存量 `project_type` 仍为 NULL（不自动填充、不猜默认值）。
- 兼容风险：
  - 旧前端 + 新后端：删除归档件会被拒（提示回表单保存）、缺类型时推进提示改为「选择项目类型」——均为更严格的提示，不阻断旧路径；
  - 新前端 + 旧后端：旧后端 `update` 同样支持缺键（本次未改后端该逻辑），但旧后端不会输出「选择项目类型」缺失项，新前端的该入口不会触发（旧后端仍输出「保存阶段表单」）；
  - 新前端 + 旧后端：旧后端不拦截归档件删除、不脱敏 → 需**前后端同时发布**才能拿到完整 P0 收益。
- 回滚步骤：
  - 代码：`git revert` 本次提交即可（5 个源文件 + 1 个测试文件 + 3 个文档/版本文件，无数据迁移需回滚）；
  - 手工兜底：从 `<LOCAL_PATH>` 逐份还原 `backend__ProjectService.java`、`backend__ProjectController.java`、`frontend__ProjectDetail.vue`、`frontend__ProjectEdit.vue`、`frontend__ProjectFinanceForm.vue`（该目录为改动前原始副本）；
  - 版本：`version.txt` 与 `frontend/package.json` 同步改回 1.0.158；
  - 无 DDL / 权限 / 配置回滚项。

## 9. 文档同步
- [x] 架构文档（`docs/architecture.md` 新增「会前财务资料上传阶段 P0 整改（1.0.159）」小节）
- [x] 交接记录（本文件）
- [x] 最新交接摘要（`docs/handover/README.md` 已加条目）
- [x] 测试说明（本记录第 6 节；新增自动化回归 12 例）
- [ ] 部署文档（本次无迁移/环境变量/部署方式变更，`docs/deployment.md` 无需改；发布记录随发布任务生成）
- [ ] 接口文档（仓库无独立接口文档文件，接口契约变化以本记录第 5 节「API 契约变更」+ 架构文档为准）
- [ ] 数据库迁移 / 数据字典（不适用：无 DDL 变更）

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`（应只有本次 5 个源文件 + 1 个测试 + 3 个文档/版本文件）；`git diff` 复核 `ProjectService` 的 4 处行为改动；`mvn -o "-Dtest=ProjectFinanceStageFormGuardTest" test` 应 12/12；`npm run build` 应通过。
2. 然后执行（获发布授权后）：走 `scripts/release-full.ps1 -Mode Publish -Version 1.0.159`（**本次无迁移，可直接发布**），健康检查 `/health` 应显示 1.0.159。
3. 发布后手工回归（第 6 节清单）：
   - 用真实项目保存财务表单 → 阶段资料区出现独立「合规立项申请表」卡片（带「系统生成」标签、无删除按钮），普通附件列表不再混入它；
   - 尝试删除该归档件 → 后端返回「合规立项申请表由财务表单自动生成…」且阶段推进不被卡死；
   - 打开一个 `project_type` 为空的存量项目 → 「编辑项目」可保存会议名称/负责人等字段；补选类型保存后生效；
   - 缺类型项目进入本阶段 → 出现「去编辑项目选择项目类型」按钮；待办「去选择项目类型」直达编辑弹窗；
   - 用非项目成员但持 `project:view-all` 的账号打开该阶段 → 表单人名为掩码、`sfaNo`/`bookDept` 为 `***`、项目文件列表无归档件、下载归档件返回 403。
4. 未经确认禁止：执行生产库迁移之外的任何 DDL；放宽 `FINANCE_MASK_*` 脱敏集合（需负责人拍板）；恢复归档件可删除的行为。
5. 需要负责人决策：P0-④ 的脱敏字段范围是否需要覆盖/放宽；审查报告 P1/P2/P3（23 项）与新需求 8 条的推进排期。
