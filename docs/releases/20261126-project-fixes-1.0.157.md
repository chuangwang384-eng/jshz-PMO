# 发布记录 — 项目板块修复与日历根因修复 1.0.157

- 日期：2026-11-26 ｜ 任务：[交接记录](../handover/tasks/20261126_project_fixes.md) ｜ 状态：**已上线，待业务验收**

## 变更内容
日历"不显示内容"双根因修复（组件模板 ym 崩溃 + JDBC varargs 嵌套数组绑定恒空）；详情路由复用修复；编辑起止倒挂校验；角色/类型字典收口 + SpeakerRole 枚举；status 非法 400；LIKE 转义收口；missingDates 提示接线；**项目导出功能整体下线**（用户决策）。零迁移。

## 发布过程（实录）
| 时间 | 动作 | 结果 |
| --- | --- | --- |
| 14:13 | release-full Publish 首发 | **中止于蓝绿第 8 步 drain 守卫**："Port 8080 still has active requests"——根因是本次诊断用的本地预览代理(5173→8080)+浏览器页轮询持续打生产后端，drain 等不到空闲；脚本保留生产进程未切 NSSM |
| 14:25 | 恢复：停预览服务/浏览器置 about:blank；nginx conf 回滚备份(20261126_141717)+graceful reload；清 8081 遗留影子 PID 32196 | /api/health 回 1.0.156，8081 无监听 |
| 14:30–14:39 | release-full Publish 重发 | SUCCESS：/health 1.0.157 gitShort=060792e、root=1.0.157-project-fixes、发布后验证 5/5 |
| 14:40 | 线上冒烟（测试账号） | 日历正例返回项目 65/66（11-20/11-21）+ missingDates=0；status=abc → 400"状态筛选参数不合法"；/project/export → 403（权限规则拦截，端点已删） |
| 14:42 | 生产 UI 核验 | v1.0.157、月历 36 格、两条"负"角色 chip 正常渲染（报障端到端修复确认） |

## 遗留与回滚
- **冒烟项目 65/66（日历冒烟-待删/路由复用冒烟-待删，owner=测试）留待管理员删除**：DELETE /api/project/{id} 需 project:manage，测试账号无权；与既往项目 61/62/63 同口径。删除后日历即空。
- 回滚：release-full Rollback（前端 root 回指 + previous jar）；零 DDL 无数据回滚需求。
- 教训登记：发布期间禁止本地代理/浏览器轮询打生产后端（drain 守卫会中止）；JdbcTemplate varargs 禁止混传标量与数组。

## 验证
- 全量回归 393 例 0 失败；前端构建 is-not-exported=0；新增绑定回归/倒挂/status/escapeLike 单测全过。
- 未执行：部门排期视图人工验收（需部门负责人账号）、暗色/窄屏目视。
