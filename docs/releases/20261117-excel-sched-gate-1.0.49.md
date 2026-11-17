# 报价Excel表格预览与日程阶段推进校验 1.0.49 生产发布

2026-11-17 11:04 CST，主责 ZCode。状态为已发布。功能提交 `3ac2c90`，版本提交 `9343361`（`version.txt=1.0.49`）。

- 范围：①项目文件（含对客报价）xls/xlsx 预览改用知识星球表格方案——新端点 `GET /api/project/files/{fileName}/workbook` 复用同一 `ExcelPreviewService`，前端复用 `KnowledgeWorkbookPreview`（新增可选 fetcher prop），不再经 LibreOffice 转 PDF；②「日程上传」阶段推进校验收紧：须已上传当前日程且全部参会专家匹配到专家库（`project_speaker.expert_id` 非空），否则待办「上传日程」/「专家匹配入库（N 位库外专家）」并拦截推进。无数据库迁移。
- 发布前验收：单测 14/14（含 3 个新日程校验用例）、`npm run build` is-not-exported=0；影子 8081 + 浏览器实测 workbook 解析、待办展示、「去上传日程」跳转、报价 xlsx 预览为原生表格（视觉核验通过）。详见 [交接记录](../handover/tasks/20261117_excel_preview_schedule_gate.md)。
- 后端蓝绿 `-GracefulSwitch`，`/health` 为 `UP / 1.0.49 / 9343361`；前端 root 切至 `1.0.49-excel-sched-gate-20261117`，Nginx 主进程 PID 7696 保持。
- 核验：入口 `index-DtzvwLz3.js` 含 v1.0.49；SPA 三路由 200；匿名 API 401；旧入口兜底 200；当前 ProjectDetail chunk 含新代码；生产登录态实测 workbook 端点解析报价 Excel 正常、项目 54（日程阶段）待办返回 `['上传日程']`；`docs/deployment.md` 已登记发布行。
- 发布日志 `logs/20261117-excel-sched-gate-1.0.49-deploy.log`（UTF-16 混排需转码）。回滚：后端 `-RollbackJarPath` 指向 1.0.48 制品；前端用 `frontend/release-metadata/1.0.49-excel-sched-gate-20261117.rollback.conf.txt` 恢复 root 平滑重载；无数据库回滚。
- 遗留：日程上传为当前最末阶段，推进校验待新增后续阶段后在 UI 可见；「专家匹配入库（N）」UI 走查待真实未匹配数据；项目 61 仍待管理员删除。
