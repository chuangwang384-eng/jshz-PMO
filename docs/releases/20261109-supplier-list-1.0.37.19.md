# 1.0.37.19 供应商管理列表改造（列表/卡片双视图 + 筛选 + 排序）

日期 2026-11-09 · 状态：**已发布并校验** · 范围：`frontend/src/views/supplier/SupplierList.vue`、`SupplierController`、`SupplierService`

需求：[2026-11-09-供应商列表视图与筛选改造-需求文档](../requirements/2026-11-09-供应商列表视图与筛选改造-需求文档.md) · 设计：[2026-11-09-供应商列表视图与筛选改造-设计文档](../design/2026-11-09-供应商列表视图与筛选改造-设计文档.md)

## 1. 变更内容

Owner 给出交互原型，要求：可切换列表/卡片视图、搜索名称与联系人、按类型与城市筛选。

**前端（`SupplierList.vue` 整体重写，新增/类型管理弹窗逻辑原样保留）**
- 两行筛选区：第一行 `搜索供应商名称、联系人`（300px，400ms 防抖即搜）+ 类型 + 城市 + 重置 + 右侧「新增供应商 / 类型管理」；第二行 `仅看已评价`（左）+ `列表视图 / 卡片视图` 胶囊切换（右）。去掉了原「搜索」按钮。
- 汇总行：`共 N 家供应商` + 右侧排序下拉（按名称排序 / 按更新时间排序，无边框样式）。
- 列表视图行：类型图标（按 bizKey 映射，自定义类型回退通用图标）→ 名称 + 类型胶囊 → `省份 · 城市 · 联系人` → `★ 4.8（12条评价）`／`尚无评价` → 右侧「查看详情」。整行可点击，行间 1px `#eef1f5` 横线，无纵向边框无斑马纹，hover `#f7f9fc`。
- 卡片视图保留原卡片（封面/名称/类型/属地/星级），封面右上角曾加状态徽标（1.0.37.20 已移除）。
- 视图选择持久化到 `localStorage['supplier-view-mode']`（仅 `list`/`card`，非法值忽略）。
- 分页 12 → 10，页容量 10/20/50。

**后端（2 文件，向后兼容）**
- `SupplierController.list` 新增可选参数 `onlyReviewed`（默认 false）、`sort`（默认 `name`）。
- `SupplierService.pageSuppliers`：`onlyReviewed` 用 `wrapper.inSql(Supplier::getId, "SELECT supplier_id FROM supplier_review")`（子查询兼容 MyBatis-Plus 分页 count，走 `idx_review_supplier`）；排序 `name`→`name ASC, id ASC`、`updated`→`update_time DESC, id DESC`。

## 2. 构建

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 前端 | `VITE_DASHBOARD_LAYOUT_V2=true npx vite build` | 28.65s / 0 错误 → `builds/1.0.37.19-build-20261109/dist` |
| 后端 | `scripts\build-all.ps1 -BackendOnly` | 1m28s → `builds/1.0.37.19-build-20261109_100545/expert-system.jar`，`build.version=1.0.37.19`、`gitShort=43ae865`，含 `BOOT-INF/` |

注：本次构建带 `VITE_DASHBOARD_LAYOUT_V2=true`，`编辑布局` 在 `DashboardView-C_sXBp_l.js`（1.0.34.1 事故口径检查通过）。该前端包同时包含 1.0.37.18 的「查看关联客户」文案（1.0.37.18 未单独发布，已被本版本覆盖）。

## 3. 发布

一键脚本 `temp/deploy-13719.cmd`（自提权，UAC 一次跑完两步）：

1. `scripts\release-backend.ps1 -JarPath builds\1.0.37.19-build-20261109_100545\expert-system.jar -Version 1.0.37.19`（蓝绿不停机）
2. `scripts\deploy-frontend-graceful.ps1 -StagedDist builds\1.0.37.19-build-20261109\dist -ReleaseId 1.0.37.19-supplier-list-view-20261109 -Apply`

日志：`logs/deploy-backend.20261109_101004.log`、`logs/release-1.0.37.19-frontend.log`。刻意「先后端后前端」：新参数对旧前端向后兼容，中途失败不会白屏。

## 4. 发布后校验（Agent 实测，2026-11-09 10:15）

| 项 | 结果 |
| --- | --- |
| 后端 `/health` | `version=1.0.37.19`，`buildTime=2026-11-09 10:05:51`，`gitShort=43ae865`，`status=UP` |
| Nginx root | `frontend/releases/1.0.37.19-supplier-list-view-20261109`（旧 root 1.0.37.17 保留） |
| 服务端 index SHA256 | `261667a3…33a3656`，与 release `index.html` 一致 |
| 主包 | `assets/index-PSg2jkT7.js`，含 `1.0.37.19` |
| 供应商 chunk | `SupplierList-CnFmavxd.js` 线上哈希 = 磁盘 `49395c07…3592150`；含「列表视图/仅看已评价/合作中/待评估」 |
| SPA 路由 | `/`、`/supplier`、`/supplier/13`、`/project` 均 200 |
| 数据口径实测（只读 SQL） | `supplier` 共 **11** 条，其中有评价的仅 **1** 条 → 「仅看已评价」当前应只返回 1 条（测试打印店B） |
| 排序实测（只读 SQL） | `name ASC, id ASC` 首行为 `Bj`，其后中文按字面编码排序（非拼音，已在需求 §6 记为已知限制） |

## 5. 未执行

- **接口级带参验证**：`/api/supplier/list` 需登录（无凭据，无 token 调用返回 401），`onlyReviewed/sort` 的端到端返回待 Owner 在页面上验证（勾「仅看已评价」应只剩 1 条）。
- **页面视觉验收**：双视图切换、筛选、排序、跳详情，需 Owner 真机确认。

## 6. 回滚

- 前端：配置备份 `frontend/release-metadata/1.0.37.19-supplier-list-view-20261109.nginx-before.txt`，旧 release `1.0.37.17-customer-table-ui-20261109` 完整保留，可用 `scripts/rollback-frontend.ps1` 切回。
- 后端：蓝绿发布保留上一版本，按 `logs/deploy-backend.20261109_101004.log` 记录的切换步骤回切即可；本次仅新增可选参数，回滚风险极低。
