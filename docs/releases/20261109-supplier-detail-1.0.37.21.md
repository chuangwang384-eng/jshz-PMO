# 供应商详情页改版发布记录（1.0.37.21）

- **版本**：1.0.37.21（迁移 + 后端 + 前端同版本）
- **日期**：2026-11-09
- **类型**：DB 迁移（新增可空列）+ 后端接口字段扩展 + 前端页面重构

## 1. 改动清单

| 层 | 文件 | 改动 |
|----|------|------|
| DB | `database/migration/20261109_supplier_price_quote_date.sql` | `supplier_price_item` 新增 `quote_date`、`valid_until`（均可空，幂等） |
| 后端 | `entity/SupplierPriceItem.java` | 增 `quoteDate` / `validUntil`（`yyyy-MM-dd`） |
| 后端 | `service/SupplierService.java` | `savePrices` 解析两个日期字段，校验有效期不早于报价日期 |
| 前端 | `views/supplier/SupplierDetail.vue` | 全量重写（Tab + 分组档案 + 4:3 图库 + 价格表 + 评价统计 + 同城侧栏） |
| 前端 | `views/supplier/SupplierList.vue` | 查询状态与 URL 双向同步（返回可还原） |

权限码、接口路径、鉴权规则零改动。

## 2. 构建产物

| 项 | 路径 | 校验 |
|----|------|------|
| 前端 | `builds/1.0.37.21-build-20261109/dist` | 33.49s，0 错误；主包 `index-SvrTtIZu.js` 含 `1.0.37.21`；`SupplierDetail-DRWl5_DD.js` 含「基础资料/联系信息/档案信息/图片管理/历史价格/服务评价/同城供应商/复制电话/长期有效/已过期/新增评价/重命名图片/已排除当前档案」与权限码 `supplier:manage`/`supplier:image`，且「图片命名」为 0；`编辑布局` 仍在 `DashboardView-BW0xIeSw.js`；客户页「查看关联客户」仍在 |
| 后端 | `builds/1.0.37.21-build-20261109_102951/expert-system.jar` | 1m13s；`build-info version=1.0.37.21 gitShort=43ae865`；含 `BOOT-INF/` |

## 3. 发布方式

一键（管理员）：双击 `<LOCAL_PATH>`，UAC 点「是」。脚本按 **迁移 → 后端 → 前端** 顺序执行，任一步失败即中止后续步骤：

1. `scripts/apply-migration-20261109-price-quote-date.ps1`：从 `start-backend.bat` 注入 `MYSQL_PASSWORD`（不打印），执行幂等 SQL，校验 `quote_date`/`valid_until` 两列存在；日志 `logs\migration-20261109-price-quote-date.log`
2. `scripts/release-backend.ps1 -JarPath … -Version 1.0.37.21`：蓝绿切换
3. `temp/run-deploy-frontend-13721.ps1`：graceful 切 root 到 `1.0.37.21-supplier-detail-tabs-20261109`

顺序理由：新 jar 会 SELECT 新列，**迁移必须先于后端**；迁移只加可空列，旧 jar 不受影响。

## 4. 发布后校验清单

| 项 | 命令/方法 | 期望 |
|----|-----------|------|
| 迁移 | `information_schema.COLUMNS` 查询 | `quote_date`、`valid_until` 存在，类型 `date`，`IS_NULLABLE=YES` |
| 后端 | `curl /health` | `version=1.0.37.21`，`status=UP` |
| 前端 root | `grep root nginx conf` | `1.0.37.21-supplier-detail-tabs-20261109` |
| index 哈希 | 服务端 index vs release index | 一致 |
| 新 chunk | `SupplierDetail-*.js` 线上哈希 vs 磁盘 | 一致，且含新文案 |
| 旧 chunk 兜底 | 旧 hashed 资产 | 200（存量会话不白屏） |
| SPA 路由 | `/`、`/supplier`、`/supplier/13` | 200 |
| 价格接口 | 编辑一条报价，填报价日期+有效期保存 | 回显日期，过期项显示「已过期」 |

## 5. 回滚

- 前端：`scripts/rollback-frontend.ps1`，旧 release 目录（`1.0.37.20-supplier-list-no-status-20261109`）完整保留
- 后端：上一版 jar 仍在 `builds/`，`scripts/release-backend.ps1` 指定旧 jar 回切
- DB：如需回滚列（一般不必，旧代码忽略新列）
  ```sql
  ALTER TABLE `supplier_price_item` DROP COLUMN `quote_date`, DROP COLUMN `valid_until`;
  ```

## 6. 已知限制

- 同一服务项目的多次报价仍是**覆盖**（全量替换语义），不是追加；要形成价格曲线需另建历史表，本期未做。
- 同城侧栏一次最多加载 200 家；超出时底部提示「共 N 家，仅加载前 200 家」。
- 「按名称排序」受 MySQL 排序规则影响，中文按字面编码而非拼音（沿用上一版）。

## 7. 发布结果（2026-11-09 10:41–10:42，Owner 执行 `temp\deploy-13721.cmd`）

三步全部成功，`[OK] ALL DONE: migration + backend + frontend are 1.0.37.21`：

| 项 | 结果 |
| --- | --- |
| 迁移 | `MIGRATION_OK 2026-11-09T10:41:16`；执行前列数 0 → 执行后 2；直查 information_schema 确认 `quote_date`/`valid_until` 均为 `date NULL` |
| 后端 | 蓝绿完成：8081 影子健康→切流→NSSM 换 jar→8080 健康，`/health` = `1.0.37.21`（直连与经 Nginx 一致），旧 jar 归档 `backend/releases/1.0.37.21-20261109_104156/` |
| 前端 | root → `1.0.37.21-supplier-detail-tabs-20261109`，master PID 7696 不变，`DEPLOY_EXIT=0` |
| index 哈希 | 服务端 = release：`84cef053…4aeaaf` |
| 新 chunk | 主包 `index-SvrTtIZu.js` 含 `1.0.37.21`；其引用 `SupplierDetail-DRWl5_DD.js` 线上哈希 = 磁盘（`8ae14bbb…348bab`），含「基础资料/联系信息/图片管理/历史价格/服务评价/同城供应商/新增评价」，「图片命名」为 0（已按设计移除） |
| 旧 chunk 兜底 | 1.0.37.20 的 `SupplierList-CWqWHsCk.js` 等 hashed 资产仍 200 |
| SPA 路由 | `/`、`/supplier`、`/supplier/13`、`/knowledge-planet/customer-info` 全 200 |

待 Owner 真机验收：图片 Tab 4:3 缩略图与悬停操作、历史价格日期回显与「已过期」标记、评价 Tab 默认统计+列表、同城侧栏切换后筛选保留、返回列表还原筛选与页码。
