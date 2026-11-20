# 1.0.95 发布记录：清理无类目收费项 + 预算骨架仅留酒店细项

- 日期：2026-11-21
- 提交：`a542a83`（main）
- 制品：`builds/1.0.95-build-20261120_145700/`
- 类型：后端蓝绿 + 前端 graceful + **数据清理迁移**（20261121_cleanup_no_catalog_fees.sql，Owner 授权的生产数据删除）

## 1. 需求（Owner）

1. 删除所有没有类目（catalog IS NULL）的收费项；已关联项目的明细行「连同明细行一起删除」（Owner 明确选择）。
2. 会议预算制作阶段默认细项除酒店外清空，只保留七大项：一、酒店费用（保留细项）、二、交通费用、三、搭建费、四、物料费、五、媒体费、六、礼品费、七、人员费。
3. 估算面板**保留**（Owner：酒店费用保留，估算面板仍对酒店住宿/茶歇按人数自动估量）。

## 2. 改动

| 模块 | 内容 |
| --- | --- |
| 数据迁移 20261121_cleanup_no_catalog_fees.sql | ① 删除引用无类目收费项的预算明细行；② 删除无类目收费项（19 条）；③ 重算各预算单 total_amount/cost_total；④ 已确认预算同步 project.budget_amount。可重跑 |
| init.sql | 移除原 18 条无类目收费项种子（MC_INTERLUDE/AGENDA_UP/STAFF_DAY/EST_* 等）；收费库仅留 171 条有类目模板项（STD_*）。估算参数由前端 budgetEstimate.js ESTIMATE_RULES 兜底常量提供 |
| 后端 ProjectBudgetService.skeleton() | 线下骨架仅酒店保留 4 细项（场地费/住宿费 HOTEL_ROOM/用餐费/茶歇费 HOTEL_TEA）；交通/搭建/物料/媒体/礼品/人员不再预填，仅留大项分节 |
| 前端 ProjectBudgetPanel.applySaved() | 空大项不再自动隐藏（removedSections 初始化为 []），七大项默认全部可见，用户按需「移除本节」或从收费库添加 |
| 测试 | offlineSkeletonPrefills… 改写为 offlineSkeletonKeepsOnlyHotelDetails（验证仅酒店 4 细项） |

## 3. 数据清理证据（实测，删除前已备份）

- 备份：`temp/backup_1095/fee_no_catalog.sql`（19 收费项）、`budget_items_ref.sql`（28 明细行，mysqldump --single-transaction）
- 删除前：无类目收费项 19、引用明细行 28、涉及 4 个项目
- 删除后：无类目收费项 **0**、收费库 **170** 条全有类目（线下 141/线上 29）、项目 57 合计 13056→**10020**（酒店 5420+交通 4600，物料/礼品/人员 3036 已删）
- check-schema-parity 全绿（表结构未变）
- 说明：orphan_refs=10 为 HOTEL_ROOM/HOTEL_TEA/TRAFFIC_MAIN/FLEET/PLATFORM 等**估算键**明细行——这些键本就不在收费库（估算面板专用），非本次清理对象，保留正常

## 4. 验证证据

| 项 | 结果 |
| --- | --- |
| 后端编译 / 预算测试 | 16/16 通过 |
| 前端构建 | 通过（is-not-exported 0） |
| 后端蓝绿 | 18 项 OK，/health=1.0.95/a542a83 UP |
| 前端 graceful（1.0.95-fee-cleanup-skeleton） | bundle v1.0.95，探针 200×3 |
| 线上验收 | 项目 57 七大项全部可见（含空的搭建/媒体/礼品）、估算面板保留；收费库线下 141 条全有类目、无「-」类目行 |

## 5. 影响与回滚

- **既有项目**： linked 到无类目收费项的明细行已删（酒店/交通估算键明细保留）；新预算骨架仅酒店细项。
- 回滚数据：用 temp/backup_1095/*.sql 可恢复被删收费项与明细行（恢复后需重算合计）。
- 回滚代码：前端 `1.0.95-fee-cleanup-skeleton.nginx-before.txt` 指针 + 后端蓝绿旧实例。
- 估算面板对其余大项不再自动估量（因默认细项已清空），仅酒店生效——符合 Owner 预期。
