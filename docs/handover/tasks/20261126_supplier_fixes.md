# Agent 任务交接记录 — 供应商板块审查修复 + 绑定概览

## 基本信息
- 任务编号：20261126-supplier-fixes ｜ 主责 Agent：ZCode ｜ 交接时间：2026-11-26
- 当前状态：已完成待验收（1.0.156 发布中）｜ 审查来源：同日供应商板块开发审查

## 变更清单（审查项 ↔ 实现）
| 项 | 实现 |
| --- | --- |
| P2-1 | `SupplierService.addReview` 超 500 字抛业务错误（与 varchar(500) 同口径），不再 500 |
| P2-2 | 新增 `lockSupplier`（FOR UPDATE）私有方法；`savePrices` 先删后插前锁供应商行；`deleteSupplier` 复用同入口 |
| P2-3 | 迁移 `20261126_cleanup_supplier_review_tmp.sql`（守卫式：仅 0 引用才删 `_review_tmp`），已执行并验证剩 6 正规类型 |
| P3-1 | 删除 `parseImages`/`writeJson` 死方法 + ObjectMapper 依赖 + 相关 import |
| P3-2 | `bind()` 复用已加载的 a/b，4 次查库减为 2 次（单测 verify times(2)） |
| P3-3 | `/api/meta/limits` 新增 supplierMaxImages/supplierMaxSingleBytes/supplierMaxTotalBytes（真源=SupplierImageService/StorageService 公共常量）；前端 limits.js 扩展兜底 + SupplierList/Detail 两处限额文案改动态；`SUPPLIER_LIST_FETCH_SIZE=200` 收口 utils/supplier.js 共享常量。压缩参数 1280/0.8 保持 imageCompress 前端默认（纯前端 canvas 行为，注释已说明） |
| P3-4 | 绑定候选/同城列表用共享常量取数；候选打满 200 时下拉下显式提示"仅显示前 N 家，请用筛选缩小范围"（SupplierList）；同城列表沿用既有"共 X 家仅加载前 N"文案 |
| P3-6 | `computeCols` 回退用面板容器宽度（panelEl），列表视图（默认视图）下页大小自适应生效 |
| P3-7 | 类型管理"排序"列改 el-input-number + 变更时行内"保存"（PUT /types/{id} name+sort，后端零改动）；fetchDicts 记录 $origSort 判定脏值 |
| 需求四 | 后端 `GET /api/supplier/bindings/unbound?cityId=`（登录即可，与模块口径一致）+ `unboundBindingPeers`（仅 HOTEL/PRINT、一次拉全 rel 统计、城市→类型→名称排序）；前端详情页绑定 Tab 新增"绑定概览"入口（supplier:manage）+ 对话框按城市分组显示未绑酒店/打印店 Tag，点击跳详情 |

## 明确不做
- P3-5（图片写操作 @OperationLog 留痕）未获指示未做，留待后续。

## 测试与验证
- 新增 `SupplierAuditFixTest` 7 例全过（P2-1×2/P2-2 锁顺序+FOR UPDATE 断言/P3-2 次数/需求四×3 含城市过滤与类型过滤）
- 全量回归/前端构建：见发布记录补记
- 迁移已执行并验证；parity 通过（无结构变更，纯数据清理迁移）
- 未执行：浏览器人工验收（排序编辑、绑定概览对话框、列表视图页大小）——待业务

## 风险与回滚
- 行为变更：评价超长报错文案化（原 500）；行价并发语义由"后写覆盖"变"串行化"（正确性修复）；limits 多 3 个字段（向后兼容）
- 回滚：release-full Rollback；死类型迁移回滚 SQL 见文件头注释
