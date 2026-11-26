# 发布记录 — 公告板块修复与置顶排序 1.0.155

- 日期：2026-11-26 ｜ 任务：[交接记录](../handover/tasks/20261126_announcement_fixes.md) ｜ 状态：**已上线，待业务验收**

## 变更内容
公告板块审查修复 8 项（P2-1 时间前后校验、P2-2 员工列表统一生效口径、P2-3 已读统计在职口径、P2-4 幂等已读、P3-1 删除 /effective 死端点、P3-2 纯文本搜索、P3-3 生效口径收口+管理列表消 N+1、P3-4 开关按账号隔离、P3-5 页容量切换回首页）+ 需求四 置顶组内上移/下移（announcement.top_sort，新端点 POST /api/announcement/{id}/move，权限 announcement:manage）。

## 发布过程（实录）
| 时间 | 动作 | 结果 |
| --- | --- | --- |
| 12:03 | db-migrate Apply ×2（top_sort 列+回填守卫、move 权限行） | OK；回填验证：唯一置顶公告 id11 top_sort=1；权限行就位；parity 通过 |
| 12:04–12:19 | release-full Publish 1.0.155-announcement-fixes | SUCCESS：/health 1.0.155 gitShort=3f7de5e、root=1.0.155-announcement-fixes、首页 200×3、bundle 版本一致、发布后验证 5/5、错误数 0 |
| 12:20 | 线上冒烟（员工测试账号） | 列表仅 ONLINE 生效公告、无越口径条目、unread=0、read-stats 与 move 均 403（权限规则生效） |
| — | 管理路径（时间校验拒绝/置顶排序/统计口径）线上人工验收 | 未执行（Service 层 13 例单测覆盖；需管理员账号） |

## 回滚
- release-full Rollback（前端 root 回指 + previous jar）；top_sort 列旧代码不感知可保留；权限行无副作用。

## 验证
- 新增 AnnouncementServiceFixTest 13 例全过；全量回归 0 失败；前端构建通过（is-not-exported=0）。
- 未执行：浏览器人工回归（管理模式界面、置顶按钮、抽屉顺序核对）——待业务/管理员验收。
