# Agent 任务交接记录 — 项目板块审查修复 + 日历不显示根因修复

## 基本信息
- 任务编号：20261126-project-fixes ｜ 主责 Agent：ZCode ｜ 交接时间：2026-11-26
- 当前状态：已完成待验收（1.0.157 已上线）｜ 审查来源：同日项目管理板块开发审查

## 日历"不显示内容"双根因（用户报障，本次修复）
1. **前端渲染崩溃**：`ProjectCalendar.vue` / `ProjectDeptSchedule.vue` 模板误引用不存在的 `ym`（Demo 遗留变量，实际 props 为 year/month）→ 渲染函数 TypeError → 组件中断为注释占位、卡片空白。已改为 `{{ year }}/{{ month }}`。浏览器实测：36 格网格+月份标题正常渲染。
2. **后端 JDBC 绑定恒空**：`ProjectCalendarService.queryForUsers` 以 `db.query(sql, handler, to, from, userIds.toArray())` 混传标量+数组，varargs 打包成嵌套 `Object[]{to,from,Object[]{uid}}`；MySQL 驱动把内层数组当 Serializable blob 绑定到 `IN (?)` → 永不匹配 → meetings 恒 []（1.0.154 起即存在，此前冒烟只验了空态未验正例）。已改显式平铺参数数组单数组传入；新增回归断言 `queryArgsAreFlatNotNestedArray`。反编译线上 jar 字节码取证确认嵌套打包。

## 审查项落地
| 项 | 实现 |
| --- | --- |
| P1-1 路由复用 | ProjectDetail `projectId` 改 computed + `watch(immediate)` 重载、onMounted 去掉 fetchDetail；浏览器实测 router.push 66→65 数据正确切换 |
| P2-1 起止倒挂 | update() 落地后校验 endDate>=startDate（与 create 同口径）；ProjectEdit confirmSave 前端拦截 |
| P2-2 角色字典 | showDeck.js 增 ROLE_VALUE_OPTIONS（ROLE_ENUM_LABEL 派生）；ProjectDetail 删本地副本改 import；后端新增 SpeakerRole 枚举（权威，DDL 注释对应） |
| P2-3 status 非法 | pageProjects 非枚举且非数字 → BusinessException("状态筛选参数不合法") |
| P3-1/P3-2 导出 | **项目导出功能整体下线**（用户决策）：Controller /export 端点、Service buildExportWorkbook/collectProjects/projectTypeLabelOf/PROJECT_TYPE_LABELS、POI imports、前端按钮/handleExport/exportProjects 全删；permission 表 GET /api/project/export 规则保留（无端点匹配，无副作用） |
| P3-2 类型文案 | 收口 utils/dict.js PROJECT_TYPE_LABELS（统一"平台类项目"全称），List/Detail 引用；后端仅校验码集 PROJECT_TYPES，注释注明成对修改 |
| P3-3 LIKE 转义 | ParamUtils.escapeLike（\ % _）收口；pageProjects keyword 使用 |
| P3-4 缺失日期提示 | 日历接口 scope=me 增 missingDates（与我相关 ACTIVE 且日期缺失计数）；ProjectList 接线 ProjectCalendar 既有提示位 |

## 测试与验证
- 新增/扩展单测：ProjectCalendarServiceTest（+绑定回归）、ProjectMeetingDateTeamTest（+倒挂/status/escapeLike 3 例）全过
- 全量回归 393 例 0 失败；线上冒烟：日历正例 65/66 + missingDates=0、status 非法 400、导出 403（端点已删）、生产 UI chip 渲染核验通过
- 前端构建通过（is-not-exported=0）；浏览器实测：日历网格渲染、类型文案统一、导出按钮消失、路由复用重载
- 冒烟数据：生产暂留项目 65/66（日历冒烟-待删/路由复用冒烟-待删，owner=测试），**发布后已验证 chip 显示；DELETE 需 project:manage 测试账号无权，留待管理员删除（与项目 61/62/63 同口径）**

## 风险与回滚
- 行为变更：导出入口下线（不可逆功能移除，用户已决策）；列表类型 Tag 文案统一为全称
- 回滚：release-full Rollback；无迁移（本批零 DDL）

## 文档同步
architecture.md 项目小节、交接摘要、本记录、发布记录（发布后补）。
