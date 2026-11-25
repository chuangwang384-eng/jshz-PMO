# 成本单 Excel 版式、已提交收起与待办精准跳转（1.0.149）

2026-11-25 负责人要求，三项改造合并发布。生产当前 1.0.148（`gitShort=c0a9042`，Nginx root `1.0.148-settlement-cost-sheet`）。

## 改动点

### 1. 成本单 Excel 版式美化（`ProjectCostSheetWorkbook`）

- 列宽重分配为 `{18,15,13,12,9,16,16,16,14,20,20}`，项目/明细可容纳 6 字费用名称，金额列可单行显示「应付金额（元）」表头。
- 表头信息区改为「标签格 + 合并值区」：项目名称/项目时间、客户名称/项目地点、客户负责人/项目负责人/项目协助人各占独立标签格，合并区内每格都套样式，修复原实现中中文标签挤在窄列、值区错位的问题。
- 分节标题浅黄底、小计浅灰底加粗、总计深灰底白字；明细金额右对齐、数量居中、零值显示「-」（仅数字格式，不改单元格数值）。
- 表尾说明合并 A–K 并自动换行，不再挤在 A 列竖排。
- 打印：A4 横向、页宽适应一页、表头行重复、打印区 A1:K<末行>、页边距收窄；冻结前 5 行。
- 公式不变且更稳：明细 `F=C×E`、`H=G−F`；分节 `SUM`；**总计由服务端静态值改为 `SUM(各分节小计单元格)`**，并保留 `fullCalcOnLoad`。数据值与金额口径未改。

### 2. 成本单已提交自动收起（`ProjectCostSheetForm.vue`）

- 新增 `submitted` 属性，由 `ProjectSettlementMaterials` 传入 `item.complete`。
- 已提交时默认收起填写详情，只显示「已提交」标签、合计摘要（应付/结算/退款）和「展开修改」；点击可展开继续编辑，重新生成成功后自动收起。
- 细项回到未提交（如删除当前批次文件）时自动展开，避免看不到待填内容。

### 3. 右侧待办「去处理」精准跳转（`ProjectDetail.vue` + `ProjectSettlementMaterials.vue`）

- 结算资料阶段的待办按细项名称/编码定位到 `settlement-slot-<code>` 卡片，`scrollIntoView` 居中并高亮 3 秒；可写上传类细项同时展开文件选择区并聚焦；成本单定位到在线填表区。
- 按钮文案区分「去上传」「去填写成本单」，替换原统一的「去处理」。
- 已在查看结算阶段时不再切换视图（平台补件场景当前阶段可能已推进）；组件未挂载或名称未命中时回退原「阶段资料」顶部定位。
- 组件 `load()` 复用进行中的请求，避免跳转与首屏加载并发时把刚打开的编辑器状态冲掉。

## 验证

- 后端 `ProjectAccessRegressionTest` **21 项通过**（新增 `costSheetWorkbookKeepsFormulasAndReadableLayout`：项目列 ≥16 字符宽、金额列 ≥15、合并区 ≥12、明细 `C7*E7` / `G7-F7` 公式、总计为 `SUM(...)` 公式）。`mvn test-compile` 全量测试代码编译通过。
- 样例成本单（含 8 分节、部分金额与空行）导出后用 LibreOffice 转 PDF 逐页目检：信息区无重叠、表头单行、金额/数量对齐、零值为「-」、总计完整显示、第 2 页重复表头、表尾说明独占整行。
- 前端 `vite build` 通过（仅既有大包体积警告）。
- `release-full.ps1 -Mode Preflight -Version 1.0.149 -Slug cost-sheet-ux` 通过：迁移一致、schema parity 通过、ReleaseId 可用；未改生产状态。

## 未验证

- 登录后真实项目：33 文件提交、成本单生成下载后的人工版式确认、已提交收起与展开、各细项「去处理」跳转（需业务账号与数据）。
- 生产切换本身（见下）。

## 发布

`release-full.ps1 -Mode Publish -Version 1.0.149 -Slug cost-sheet-ux -Yes -SkipVersionBump` 需管理员 PowerShell。当前会话非管理员且宿主安全策略禁止提权启动，待执行。

回滚：`-Mode Rollback -ReleaseId 1.0.149-cost-sheet-ux -IncludeBackend -Yes`；后端旧制品见 `backend/releases/1.0.148-*/previous-expert-system.jar`，前端旧 root 见 `frontend/release-metadata/1.0.149-cost-sheet-ux.nginx-before.txt`。本次无数据库迁移、无配置项变更、无权限码变更，回滚不涉及数据修复。
