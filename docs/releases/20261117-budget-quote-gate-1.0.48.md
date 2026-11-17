# 报价待办与估算预览修复 1.0.48 生产发布

2026-11-17 10:19 CST，主责 ZCode。状态为已发布。功能提交 `7e4a72d` 经 `8850d8b` 合入 main，版本提交 `cd18d7e`（`version.txt=1.0.48`）。

- 范围：会议预算制作阶段新增「生成报价文件」待办与推进校验（详情与推进接口共用 `missingRequiredTemplates`，须至少一份关联当前阶段且文件可读的报价版本）；估算变更预览弹窗三行独立成行修复。无数据库迁移、无权限/路由/配置变更。
- 发布前验收（影子 8081 + vite dev 5199，测试账号）：待办返回 `['生成报价文件']`；无报价推进被拒（业务错误文案正确）；生成报价 V1 后待办清空、推进放行进入日程上传；浏览器验证待办卡片「去处理报价 →」滚动定位对客报价区、推进按钮禁用态；估算预览弹窗 1280px 与 500px 窄屏均无文字重叠（视觉核验通过）。单测 `ProjectAccessRegressionTest` 3 用例通过、`npm run build` is-not-exported=0。
- 后端蓝绿：`deploy-backend.ps1 -GracefulSwitch`，10:19:21 起 `/health` 为 `UP / 1.0.48 / cd18d7e`。前端 graceful：root 由 `1.0.47-budget-ui-20261117` 切至 `1.0.48-budget-quote-gate-20261117`，Nginx 主进程 PID 7696 保持。
- 核验：新入口 `index-B2-AvcYd.js` 含 v1.0.48、`ProjectDetail-9Ae351r8.js` 200 且含新功能文案；旧入口 `index-7dgX--y3.js` 兜底 200；SPA 三路由 200；匿名 `/api/project/54` 401；`docs/deployment.md` 已登记发布行。
- 测试数据残留：项目 54 留报价 V1 及验收阶段变更动态；临时项目 61「验收-报价待办临时」删除接口 403（测试账号无删除权限）未清理，可由管理员删除。
- 发布日志 `logs/20261117-budget-gate-1.0.48-deploy.log`（UTF-16 混排，需转码阅读）。回滚：后端用 deploy-backend `-RollbackJarPath` 指向上一版本制品；前端用 `frontend/release-metadata/1.0.48-budget-quote-gate-20261117.rollback.conf.txt` 恢复 root 后平滑重载。无数据库回滚需求。

详细交接见 [报价待办交接](../handover/tasks/20261117_budget_quote_phase_gate.md)。
