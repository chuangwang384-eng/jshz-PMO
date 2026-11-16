# 2026-11-09 客户信息列表与详情抽屉改造发布（1.0.37.12，后端+前端）

状态：**COMPLETE**（后端蓝绿 + 前端 graceful，真机验证通过，无数据库变更）。

## 变更内容（需求/设计 v1.1，Owner 决策 B）
- **列表 7 列**：客户姓名（首字头像）/工作单位(240)/现任职务(180)/工作职级(120)/关联关系(160, 数量摘要)/最近更新(130, YYYY-MM-DD)/操作(100, 详情+更多下拉收纳编辑/删除)；工作职位/喜好/备注/创建人/创建时间移入抽屉。
- **详情抽屉**（el-drawer 520px）：头部（首字头像+姓名+单位·职务）、基本资料、关联人物（复用 centerGraph 接口：对方+关系类型）、元信息、打开完整档案入口；快速编辑（canManage）走既有 PUT 接口，保存全字段（避免 applyForm 置空缺失字段）。
- **后端**：`GET /api/customer` 列表项增加 `relationCount`（CustomerRelationService.countByCustomerIds 一次 GROUP 聚合，两端去重计数）；关系明细复用既有 `GET /customers/{id}/relations`。
- **数据建模核实结论（更正）**：customer_relation 实际有 **6 条真实关系**（含父子/母子/夫妻/明星与粉丝/同事/介绍关系），每客户 3 条，"3 条关联"数据正确；work_level 里的"妈妈/爸爸/儿子"是同一关系的手工冗余副本（Owner 决策 B：界面自行清理，本期不动数据）。

## 真机验证（测试账号）
- 列表 7 列渲染、头像首字、关系数量、日期格式、更多下拉（测试账号无 customer:manage 时编辑/删除隐藏，权限正确）。
- 抽屉：胡图图 → 基本资料 + 关联人物 3 条（胡英俊-上下级/张小丽-上下级/王创-业务伙伴）+ 元信息 + 打开完整档案。
- 回归：搜索/分页正常。

## 回滚
后端归档 jar；前端 release-metadata conf。


## 追加：抽屉关系网络中心模式（1.0.37.13，2026-11-09）

Owner 要求：抽屉点「查看关系网络」只展示当前客户的关系网络。
- 抽屉按钮改为带 `customerId`/`customerName` query 跳转；关系网络页读取 query 进入**中心模式**：调既有 `GET /api/customer/{id}/relations`（centerGraph），仅渲染该客户的关系圈，中心节点金色放大（centerGraph 已自带 isCenter 标记，零后端改动）。
- 工具行「关系网络」仍为全局模式，两入口并存。
- 真机实测：抽屉（胡图图）→ 「胡图图」的客户关系网络，中心金色节点 + 3 条关系（上下级×2/业务伙伴）带方向箭头；全局入口回归不受影响。


## 追加：列表新方案与删除修复（1.0.37.14，2026-11-09）

Owner 提供新布局示意图，并反馈两问题：① 删除功能失效；② 普通员工也能看到删除按钮。
- **列表重排**：客户姓名（头像）/「工作单位 / 职务」合并列（主行单位粗体+副行职级，空显示"未填写"）/关系列（蓝色人物图标+"N 条关联"链接，点击打开抽屉）/更新时间/操作=查看+···。
- **删除失效根因**：el-popconfirm 嵌在 el-dropdown-item 内的交互冲突（点击无响应）→ 改为 dropdown command + ElMessageBox.confirm 确认。
- **员工可见删除根因**：权限指令挂在 dropdown-item 上被菜单结构绕过 → 权限上移：`···` 菜单仅 canManage（或已删除视图）渲染，员工账号不再出现删除入口。
- **实测**（测试账号=员工）：··· 按钮数量 0（修复 ✓）、关系链接 4 个、新布局渲染正确；canManage 账号的编辑/删除/确认链路待 Owner 复核。


## 追加：关系列直达客户关系网络 + 列表查询状态入 URL（1.0.37.15，2026-11-09）

状态：**前端已发布**（graceful 切 root，master PID 7696 不变）；后端零改动。

Owner 要求：① 列表点「关系」列即跳转该客户自己的关系网络视图；② 顺手把列表查询状态同步进 URL，使从关系网络返回时能还原视图。

**前端改动（2 个文件，无接口/权限变更）**
- `CustomerInfo.vue`：关系列 `@click.stop` 由 `openDrawer(row)` 改为 `openRelationGraph(row)`，复用新抽出的 `goCustomerRelationGraph(customer)` → `/knowledge-planet/customer-relation-graph?customerId=..&customerName=..`；抽屉内「查看关系网络」共用同一入口。
- `CustomerInfo.vue`：列表初始化从 URL 读 `keyword/page/pageSize/deleted`（非法值回落默认）；`loadList()` 前 `syncRouteQuery()` 用 `router.replace` 写回（只 replace 不 push，默认值不写入），搜索/翻页/切已删除视图全部可还原。
- `CustomerRelationGraph.vue`：`centerMode` 改用 `centerId`（校验正整数，非法回落全局网络）；新增 `watch(centerId)` 重载图并退出连线模式；`goBack` 改为有历史时 `router.back()`，无历史才 push 列表。

**构建**：`VITE_DASHBOARD_LAYOUT_V2=true npx vite build`（**必须带开关**，避免 1.0.34.1「编辑布局」丢失事故复现），产物 `builds/1.0.37.15-build-20261109/dist`，34.77s / 0 错误；校验 `编辑布局` 在 `DashboardView-97Td3nn0.js` 内。

**发布**：`deploy-frontend-graceful.ps1 -StagedDist builds/1.0.37.15-build-20261109/dist -ReleaseId 1.0.37.15-customer-relation-jump-20261109 -Apply`（UAC 提权由 Owner 执行），日志 `logs/release-1.0.37.15-frontend.log`，输出 `Graceful reload signal accepted; master PID unchanged: 7696` + `Frontend switched and verified` + `DEPLOY_EXIT=0`。

**发布后校验（Agent 实测）**
- Nginx root → `frontend/releases/1.0.37.15-customer-relation-jump-20261109`；旧 root `1.0.37.14-customer-list-20261109` 保留。
- 服务端 `/` 的 index.html SHA256 = 新 release index.html（76357ee3…a16882）。
- `/`、`/knowledge-planet/customer-info`、`/knowledge-planet/customer-relation-graph` 均 200。
- 新主包 `index-DNS_i8yp.js` 内含 `1.0.37.15`；其引用的 `CustomerInfo-BR_-f0CH.js` 线上哈希 = 磁盘（754725…aac82）且含新文案「的关系网络」；`CustomerRelationGraph-C19LFHld.js` 含 `history.state` 且 200。
- 存量会话兜底：旧 hashed chunk（如 `CustomerInfo-DkQ8dFMW.js`）仍 200。
- 后端 `/health` 未变：1.0.37.12 UP（本次无后端发布）。

**未执行**：真实账号点选验收（无可用登录凭据），关系列跳转与返回还原的最终视觉确认待 Owner 真机过一遍。

**回滚**：配置备份 `frontend/release-metadata/1.0.37.15-customer-relation-jump-20261109.nginx-before.txt`；旧 release 目录完整保留；可用 `scripts/rollback-frontend.ps1`。


## 追加：「已删除视图」权限隔离（1.0.37.16，2026-11-09）

状态：**前端已发布**（graceful 切 root）；后端零改动。

Owner 反馈：无「知识星球维护人」角色（无 `customer:manage`）的账号仍能看到「已删除视图」开关。

- **后端本来就正确**：`GET /api/customer?deleted=1` 走 `requireManage`（`customer:manage` = ADMIN / 知识星球维护人），删除/恢复接口同样校验；**漏的是前端没隐藏入口**，无权限账号点了会弹 403。
- **前端改动（`CustomerInfo.vue`）**：① 开关加 `v-if="canManage"`，无权限完全不渲染；② `showDeleted` 初值改为 `canManage && URL.deleted==='1'`，手工拼 `?deleted=1` 也被忽略，不会发出 403 请求；③ `syncRouteQuery` / `loadList` / `handleSwitchDeleted` 三处补 `canManage` 防状态与 URL 绕过；④ 操作列 `···` 条件 `canManage || showDeleted` 收紧为 `canManage`。
- **构建**：`VITE_DASHBOARD_LAYOUT_V2=true`，产物 `builds/1.0.37.16-build-20261109/dist`，29.94s / 0 错误；校验含 `1.0.37.16`、「已删除视图」、`customer:manage`，`编辑布局` 仍在。
- **发布**：`-ReleaseId 1.0.37.16-customer-deleted-perm-20261109`，日志 `logs/release-1.0.37.16-frontend.log`，`DEPLOY_EXIT=0`；发布后校验 root 指针、index 哈希、新 chunk 均通过。
- **未执行**：无权限账号真机确认开关消失（需 Owner 用该账号 `Ctrl+F5` 复核）。


## 追加：列表视觉改造（1.0.37.17，2026-11-09）

状态：**前端已发布**（graceful 切 root，master PID 7696 不变）；后端零改动。

Owner 反馈四点：单位/职务列过宽、行高约 80px 偏大、第四行出现灰色斑马纹易误判为选中、`N 条关联` 无图标无提示不知可点。

- **列宽按比例重分配**：`min-width` 取 200 / 380 / 170 / 150 / 100（约 20% / 38% / 17% / 15% / 10%），去掉左右 `fixed`（固定列会把宽度钉死、破坏等比分配，1140px 下 5 列不需要横向锁定）。
- **行高 80 → 60px**：保留单位/职务两行结构，单位行高 20 + 职务 16 + 间距 2 = 38，单元格上下 padding 11px → 60px；关系链接按钮 padding 收到 `0 4px`，避免 10% 宽的操作列（查看 + ···）换行撑高行。
- **去斑马纹 / 去纵向边框**：移除 `stripe`、`border`；行背景统一 `#fff`，横向分隔线 `#eef1f5`，表头 `#fafbfc`，hover `#f4f7fc`，选中行（点开抽屉那行）才用 `--el-color-primary-light-8`，抽屉关闭自动取消选中。第四行偏灰的根因即 `stripe`。
- **关系列**：`Connection` 图标 + 「N 条关联」蓝色链接 + `el-tooltip`（200ms 延迟，初始文案「查看关联人员」），点击仍直达该客户关系网络；单位/职务超长单行省略并加原生 `title` 看全称。
- **构建**：`VITE_DASHBOARD_LAYOUT_V2=true`，产物 `builds/1.0.37.17-build-20261109/dist`，31s / 0 错误。
- **发布**：`-ReleaseId 1.0.37.17-customer-table-ui-20261109`，日志 `logs/release-1.0.37.17-frontend.log`，`Graceful reload signal accepted; master PID unchanged: 7696` + `Frontend switched and verified` + `DEPLOY_EXIT=0`。
- **发布后校验（Agent 实测）**：root → `1.0.37.17-customer-table-ui-20261109`（旧 root 1.0.37.16 保留）；服务端 index SHA256 = release index（`0a2f5cac…e5c1b24`）；主包 `index-BrnxhVBL.js` 含 `1.0.37.17`；`CustomerInfo-n7iSf9ZC.js` 线上哈希 = 磁盘（`6f891246…a4e8bff`）；`/`、`/knowledge-planet/customer-info`、`/knowledge-planet/customer-relation-graph` 均 200；后端 `/health` 仍 1.0.37.12 UP。
- **未执行**：视觉验收（行高 60px、操作列不换行、仅选中行高亮）依赖真实账号，Owner 真机过一遍。

