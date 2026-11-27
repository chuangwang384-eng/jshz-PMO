# 1.0.158 发布记录：会议预算制作阶段审查整改 + 收费标准库权限收紧

- 发布时间：2026-11-26 16:17–16:26（release-full-20261126_161715.log，SUCCESS/verified）
- 版本/提交：1.0.158 / ea37672（gitShort 实测一致）
- 前端 ReleaseId：1.0.158-project-type-finance（Nginx root 已切换，index.html 主 bundle 含 v1.0.158）
- 数据库迁移（**先迁移后切换**，db-migrate-20261126_161531.log，均已执行并登记）：
  - 20261126_budget_optimistic_lock.sql：project_budget.version 乐观锁列（已连库核实列存在）
  - 20261126_budget_fee_permission.sql：重建 GET /api/project/config/budget-fee-standards → project:manage 规则（已连库核实 PUT/DELETE/GET 三行齐备；config/phases、config/types 字典读取未受影响）
- 变更范围：会议预算阶段 6 项需求收口（确认守卫双层、乐观锁+409、费率/估算参数/类目/状态单一真源、报价死代码删除与三端点下线、ONLINE 持久化 estimate_inputs、parseItem 上限）；完整清单见 [整改交接](../handover/tasks/20261126_meeting_budget_remediation.md)。
- 发布验证（日志证据）：
  - 发布前 parity 通过（92 表）；后端蓝绿 8081 影子健康（10 次×6s 循环）；
  - 切流后 /api/health=200 且与 8081 响应直连一致；8080 新版健康，build 版本=1.0.158；
  - 发布后 /api/health=200，/api/expert/list=401（鉴权在线）；
  - 独立实测（本会话）：/health version=1.0.158 gitShort=ea37672 buildTime 16:19；Nginx root=1.0.158-project-type-finance；前端 bundle 含 v1.0.158；version 列与 GET 权限行连库核实存在。
- 备份与回滚：backup_20261126_161731；回滚走 release-full Rollback（旧 root 1.0.157-project-fixes，旧后端 1.0.157）；数据库回滚 DROP COLUMN version + 删除新建 GET 权限行（可逆，见整改交接第 8 节）。
- 待验收（业务侧）：空点选确认被拒、并发保存 409 提示、退回草稿/移节/0 单价三个弹窗、库弹窗截断提示、导出下载正常。
