# 结算资料限额、劳务拆项与成本单填表（1.0.148）

- 2026-11-25，主责 WorkBuddy。基线 main 未提交工作区，生产核实为 1.0.147 / 62b3c0e。四批改动合并为一个未发布版本 1.0.148。
- 现象：项目管理「结算资料上传」阶段，「高铁票据」细项一次选择 33 个文件（发票 + 行程单 + 小交通汇总表）后点击提交无反应。
- 根因：**文件数量硬限制**。前端 `ProjectSettlementMaterials.vue` 校验 `files.length > 20` 直接返回，提示写在页面顶部状态行；后端 `ProjectService.saveSettlementMaterials` 同样以 `uploads.size() > 20` 拒绝。

## 变更 1：多文件上传限额

1. 前端取消文件数量上限，上限改读 `GET /api/meta/limits`，编辑区显示「已选 N 个文件，合计 X MB」，超限用 `ElMessage` 提示。
2. 后端删除 20 个文件校验；单文件 `expert.upload.doc-max-bytes`，单批次 `expert.upload.settlement-batch-max-bytes`（500MB）；追加只校验合计。
3. `spring.servlet.multipart.max-request-size` 60MB→520MB；Nginx `client_max_body_size` 60m→520m + `client_body_timeout 300s`（已 `nginx -t`，随前端 graceful 发布重载生效）。

## 变更 2：doc-max-bytes 50MB → 100MB

`expert.upload.doc-max-bytes` = 104857600，同步 `spring.servlet.multipart.max-file-size` 100MB，并统一以下引用：`MetaController`、`CustomerAttachmentStorageService`、`ExpertDocumentService`、`KnowledgeDocStorageService`、`ScheduleParseService`、`ProjectService`（结算单文件）、`ProjectShowService`（串场 PPT 改为读配置，不再硬编码 50MB）、`frontend/src/utils/limits.js` 兜底值、知识文档与日程上传提示文案。

## 变更 3：ProjectDetail.vue 上传入口统一预检

新增 `precheckUpload(file, {label, accept})`：统一校验扩展名白名单与 `docMaxBytes`，命中即用 `ElMessage` 给出可见提示。接入本页全部上传入口：
- `uploadDoc`（会前财务附件、模板 FILE 附件、兜底阶段附件）
- `uploadShowFile`（串场 PPT，限 .ppt/.pptx）
- `onScheduleFile`（日程文件，替换原单独写死的 50MB 判断）

同时在会前财务附件与模板附件区补「单文件 ≤100MB」提示。

## 变更 4：劳务费用资料包拆分

`ProjectSettlementMaterials` 中原单一细项拆为：
- `U07` 劳务协议（组「劳务费用资料包」，必填，沿用原编码与既有文件）
- `U07B` 会议资料（同组，选填）

适用口数量变为 15/18/17（KEY/DIRECT_PAY/PLATFORM），通用必填仍为 11 项。归档 ZIP 路径变为 `劳务费用资料包/劳务协议/…` 与 `劳务费用资料包/会议资料/…`。无 DDL、无权限码变更。

## 变更 5：成本单制作（U08）改为填表生成 Excel

- 后端新增 `ProjectCostSheetWorkbook`：8 个费用分节（酒店/交通/媒体/搭建/物料/人员/劳务/转化费）、11 列（项目/明细/单价/单位/数量/应付金额/结算金额/退款金额/付款方式/供应商/备注），目录为唯一真源，前端按接口返回渲染。
- 新增接口 `GET|POST /api/project/{id}/cost-sheet/{phaseId}`：GET 返回目录 + 表头默认值（项目名/时间/客户/负责人取自项目档案）+ 上次保存值；POST 校验并生成 xlsx（POI，含 `SUM` 与乘法公式、fullCalcOnLoad），落项目资料库并登记为「会议成本单.xlsx」，属 U08 当前版本，历史批次保留。
- 校验：金额非负且 ≤1 亿，表头 ≤200 字，空表（无任何金额）拒绝。
- 表单 JSON 存 `project_stage_form` 的 `costSheet` 键，只覆盖该键，保留同阶段其它表单数据。
- 前端新增 `ProjectCostSheetForm.vue`，在 U08 卡片内渲染：可编辑明细表 + 分节小计 + 总计，提交后刷新批次。

### 与需求原文的偏差（需负责人确认）

需求要求「参考 Univer 的 Excel 能力」。本次**未引入 Univer SDK**，改为「结构化表单 + 服务端 POI 按公司模板生成 xlsx」：Univer 为在线电子表格引擎，引入需新增数 MB 前端依赖、离线资源与授权评估，与本项目内网离线部署、按模板出表的实际目标不匹配。当前实现保留表格化行内编辑、自动计算与合计，覆盖填报场景；若后续确需自由拖拽/跨表公式，再单独立项接入。

## 验证

- JDK 17 定向测试 `ProjectAccessRegressionTest` **20 项通过**（新增：33 个文件提交全部入库；超批次上限拒绝且提示不含文件数量口径；成本单空表拒绝 + 生成 xlsx 的 PK 头与应付/退款口径）。
- `npx vite build` 通过（Vite 大包体积警告为既有问题）。
- `application.yml` 经 SnakeYAML 解析确认 `doc-max-bytes=104857600`、`max-file-size=100MB`、`max-request-size=520MB`。
- `nginx -t` 通过（配置已改，重载随后端/前端发布执行）。
- 未验证：真实账号登录后的 33 文件提交、成本单生成文件版式人工核对、劳务拆项后的推进拦截实测。
