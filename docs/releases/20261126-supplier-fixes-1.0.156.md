# 发布记录 — 供应商板块修复与绑定概览 1.0.156

- 日期：2026-11-26 ｜ 任务：[交接记录](../handover/tasks/20261126_supplier_fixes.md) ｜ 状态：**已上线，待业务验收**

## 变更内容
审查修复 P2×3 + P3×6（评价超长业务报错、行价 FOR UPDATE 行锁、死类型清理迁移、死代码删除、bind 查库 4→2 次、图片限额 /api/meta/limits 真源化、取数 200 截断显式提示、列表视图页大小自适应、类型排序可编辑）+ 需求四 绑定概览（`GET /api/supplier/bindings/unbound` + 详情页对话框按城市分组显示未绑定对端酒店/打印店）。

## 发布过程（实录）
| 时间 | 动作 | 结果 |
| --- | --- | --- |
| 13:03 | db-migrate Apply（`20261126_cleanup_supplier_review_tmp.sql` 守卫式清理） | OK；类型表剩 6 个正规类型；parity 通过（无结构变更） |
| 13:05–13:16 | release-full Publish 1.0.156-supplier-fixes | SUCCESS：/health 1.0.156 gitShort=b1139ea、root=1.0.156-supplier-fixes、发布后验证 5/5 |
| 13:17 | 线上冒烟 | limits 新增 3 字段（20/8MB/100MB）；`/bindings/unbound` 返回未绑清单（上海市两家等）；超长评价业务码 400"评价内容不能超过 500 字" |
| 13:18 | 冒烟边界测试写入 1 条 500 字测试评价（供应商 71） | 已删除（DELETE id=56 AND username='测试' 守卫），supplier_review 剩 1 条真实数据 |

## 验证
- SupplierAuditFixTest 6 例全过；全量回归 389 例 0 失败；前端构建通过（is-not-exported=0）。
- 未执行：浏览器人工验收（类型排序编辑、绑定概览对话框、列表视图页大小随窗口变化）——待业务/管理员账号。

## 回滚
release-full Rollback（前端 root 回指 + previous jar）；`_review_tmp` 清理回滚 SQL 见迁移文件头。
