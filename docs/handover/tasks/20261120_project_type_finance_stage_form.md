# 项目类型 + 会前财务资料上传差异化表单

- 任务编号 20261120-project-type-finance-stage-form，主责 WorkBuddy（栈师），日期 2026-11-20，状态：**开发与数据库变更已完成，应用未发布（后端 jar 与前端快照未切换）**。
- 需求（Owner 2026-11-20）：
  1. 编辑项目弹窗中原「会议类型」改名为「地点」（字段仍为 `category` ONLINE/OFFLINE，值与接口不变）；新增「项目类型」必填下拉：平台类项目 / 直付类项目 / 重点类项目。
  2. 会前财务资料上传阶段按项目类型展示差异化表单，字段结构参考《2026年合规立项申请表8.17.xlsx》三个业务 sheet。
  3. 美化该阶段前端 UI。
- 三项按顺序执行，均已实现并通过前端构建、后端编译、schema parity 校验。

## 数据库（已执行）

- `database/migration/20261120_project_type_stage_form.sql`（幂等，已用 db-migrate -Mode Apply 应用并登记）：
  - `project` 新列 `project_type VARCHAR(20)`（PLATFORM 平台类 / DIRECT_PAY 直付类 / KEY 重点类），新索引 `idx_project_ptype`。
  - 新表 `project_stage_form`（project_id + phase_id 唯一，`form_data JSON` 整包覆盖保存，不做历史版本）。
  - `database/init.sql` 基线已同步（project 列/索引 + 新表），`check-schema-parity.ps1` 复跑全绿（88 业务表 / 库总 104）。
- 回滚：`ALTER TABLE project DROP COLUMN project_type;`（先删索引）+ `DROP TABLE project_stage_form;`（删表前确认无业务数据）。

## 后端

- `Project.java` 新增 `projectType` 字段；`ProjectService.create/update` 校验必填 ∈ {PLATFORM, DIRECT_PAY, KEY}（编辑时携带才校验，存量空值可补填）；`toMap` 输出 `projectType`（列表/详情均返回）；导出 Excel 新增「项目类型」列、「会议类型」表头改「地点」。错误文案「会议类型必须为…」改「地点必须为线上(ONLINE)或线下(OFFLINE)」。
- 新增实体 `ProjectStageForm` + `ProjectStageFormMapper`；`ProjectService.getStageForm / saveStageForm`（保存走 `requireOperable` 与阶段资料上传同口径，`validatePhaseForProject` 校验阶段归属，写操作日志）；Controller 新增 `GET/PUT /api/project/{id}/stage-form`。

## 前端

- `ProjectEdit.vue`：布局改为「项目类型 + 地点 / 团队 + 会议城市」两行；`projectType` 必填校验并随保存提交。
- `ProjectList.vue`：筛选下拉改名「地点」；行内元信息加项目类型徽标。
- `ProjectDetail.vue`：概览头部与「项目属性」卡展示项目类型；「会前财务资料上传」阶段从占位空态改为专用视图（挂 `ProjectFinanceForm`）+ 附件补充上传区。
- 新组件 `frontend/src/views/project/components/ProjectFinanceForm.vue`：按 `detail.projectType` 分支渲染三套表单（分节卡片配色沿用申请表分组口径：项目类型蓝紫 / 会议信息绿 / 企业·提供内容黄 / 备注灰），保存前校验建议必填（细分类型、我方公司、会议日期、会议/项目名称；直付类另要求公司名称、省区，与申请表粉红标注一致）。
- `api/project.js` 新增 `getStageForm / saveStageForm`。

## 表单字段与 Excel 的对应（form_data JSON key）

- 平台类：subType / platform / ourCompany / ourOwner / dept / meetingDate / clientContact / location / meetingName / sponsorAmount / sponsorCompany / productLine / province / remark。
- 直付类：subType / ourCompany / expenseType / ourOwner / dept / meetingDate / clientContact / location / meetingName / companyName / businessUnit / productLine / province / bookDept / sfaNo / remark。
- 重点类：meetingDate / clientContact / location / meetingName / clientOwner / clientWorkUnit / remark。
- 字典：我方公司=景帅/华秀/偌君/会信盈；细分类型=单会立项/系列会立项/系列单场立项；费用类型=会议费/设计费/物料费/其他（沿用申请表 Sheet4）。

## 验证

- 前端 `npm run build` 通过（产物 temp/finance-form-20261120/dist）。
- 后端 `mvn compile -DskipTests -Dspring-boot.build-info.skip=true` 通过。
- `check-schema-parity.ps1` 全绿；information_schema 实测新列/新表存在。
- 未执行：浏览器端到端联调（依赖后端 jar 与前端快照发布后）、自动化测试（本仓库无对应测试文件，回归靠构建+接口冒烟）。

## 发布与回滚（待执行）

1. 迁移已应用，发布时无需再动库；按惯例构建新 jar（`deploy-backend.ps1`）+ 前端快照（`frontend/releases/<ReleaseId>/` + Nginx root 指针）后健康检查。
2. 存量项目 `project_type` 为 NULL：列表/详情显示 "-"，编辑保存时补选即可；后端对存量空值放行展示、仅保存时校验。
3. 回滚：前端切回上一快照；代码回退即恢复旧行为；数据库回滚见上文回滚 SQL（建议保留数据仅回代码）。

## 风险与备注

- 表单为「建议必填」前端校验，后端宽松存储（不阻断历史数据）；如需强制，后续在 `saveStageForm` 加服务端校验。
- Excel 中「地点」列（线上/线下）在表单中独立于项目主表 `category`，便于财务表单留档；未做联动回写。

## 一键发布脚本（2026-11-20 追加交付）

新增 `scripts/release-full.ps1`（编排层）+ `scripts/release-full.cmd`（Bypass 执行策略入口），
把既有子脚本串成 `Preflight / Publish / Rollback` 三模式；用法见 `docs/deployment.md` §4.1。
本次发布命令（管理员 PowerShell，需显式 -Yes 批准）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Mode Preflight -Version 1.0.98
powershell -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Mode Publish -Version 1.0.98 -Slug project-type-finance -Yes
powershell -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH> -Mode Rollback -Yes
```

- 版本口径：线上后端 `/health`=1.0.95、Nginx root=前端 1.0.97-fee-detail-edit、`version.txt`=1.0.97，
  本次含前后端改动建议 bump 到 **1.0.98**（脚本自动同步两处版本文件，不提交 git）。
- 迁移状态实测：69 个脚本全部登记、0 待执行、0 漂移；parity 全绿（88 业务表）。
- 验证记录：PowerShell 语法解析 0 错误；`-Mode Preflight -Version 1.0.98` 实跑 EXIT=0（6 项检查全过）；
  `Publish`（无 -Yes）与 `Rollback`（临时 state、无 -Yes）计划分支实跑 EXIT=0 且未改生产；
  `.cmd` 入口参数转发 EXIT=0；版本替换 / state JSON 往返 / GBK 解码兜底独立验证通过；
  主日志 `logs/release-full-*.log` 中文 UTF-8 正常。
- **未实跑**：Publish / Rollback 的执行分支（需管理员权限与正式环境发布批准），不得视为已部署或已验收。
- 已知显示差异：从 Git Bash 调用 `.cmd` 时中文输出乱码（控制台代码页差异）；Windows PowerShell 控制台正常，
  日志始终 UTF-8。脚本文件带 UTF-8 BOM（PowerShell 5.1 无 BOM 会按 GBK 解析中文）。

## 线上基线归档

2026-11-20 复核生产直连为 UP / 1.0.98 / ce9d71f，Nginx root 指向 1.0.98-project-type-finance。线上后端 JAR 与 builds/1.0.98-build-20261120_164842/expert-system.jar 的 SHA-256 相同，五份变更后端源码和前端入口 index 与构建快照一致。按负责人批准独立补归档本次源码和迁移。此归档未执行数据迁移或重新发布。
