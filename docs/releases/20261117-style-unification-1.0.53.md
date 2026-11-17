# 发布记录：1.0.53 样式按全局规范统一第一批

| 项 | 内容 |
|---|---|
| 版本 | 1.0.53（version.txt 唯一真源） |
| 发布日期 | 2026-11-17 |
| 类型 | 纯前端快照发布（无后端/无迁移） |
| 决策来源 | A6：负责人 2026-11-17 批准进入「按全局规范统一」阶段 |

## 1. 变更内容
见 [交接记录](../handover/tasks/20261117_style_unification.md)：卡片 6 处容器挂 ui-card（圆角统一 10px/边框阴影统一）、供应商 type-pill/state-pill 挂 ui-badge、`.muted` 全局化（4 文件 #a8abb2→#909399）。Dashboard --c-* 与 Supplier #1f2329 受保护色系未动。

## 2. 发布过程
- 版本号 1.0.52→1.0.53（version.txt + package.json），`npm run build`（34.62s）
- `deploy-frontend-graceful.ps1 -Apply`：快照 `frontend/releases/1.0.53-style-unification-20261117`，graceful root 切换，旧 assets 并入保留
- 回滚点：`frontend/release-metadata/1.0.53-style-unification-20261117.rollback.conf.txt`（指回 1.0.52 release）

## 3. 发布后核验（2026-11-17 实测）
| 项 | 结果 |
|---|---|
| Nginx root | `frontend/releases/1.0.53-style-unification-20261117` ✓ |
| 线上入口 | `index-Ulo2ennJ.js`，含 `1.0.53` ✓（首轮抓取出现在切换完成前的旧入口，重抓已更新） |
| 样式 chunk 线上可达 | `ProjectList-K_2cuDFp.js`（含 ui-card 类）200 ✓ |
| SPA 路由 | /project、/expert、/supplier、/admin/ai-assistant 均 200 ✓ |
| 后端 | 未触碰，仍 1.0.52（/health UP） |

## 4. 待业务验收
- [ ] 抽验：批量建档卡片 / AI 助手管理页 / 专家库卡片视图 / 项目列表筛选区与表格卡 / 合规记事本 / 维护看板（卡片圆角 10px、统一浅边框与阴影）
- [ ] 抽验：审批表格/弹窗/我的审批灰字（#a8abb2→#909399 极轻微变化）
- [ ] 抽验：供应商列表类型胶囊、价格状态胶囊（形状不变、配色不变）
- [ ] Dashboard、登录页应无任何变化（受保护/有意保留）

## 5. 回滚
前端按 rollback.conf 指回 1.0.52 快照 + graceful reload；无数据/接口牵连。
