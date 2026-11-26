# 项目归档门槛与结项凭证

2026-11-26，主责 Codex。基线为 `main@47f23f5`，生产前端为 `1.0.151-settlement-stage-ui`，后端健康版本为 1.0.151。

当前已完成待业务验收。功能提交 `c1e3389` 已合入本地 `main`，正式前后端 1.0.152 发布状态为 `SUCCESS / verified`。

## 实现与规则

- 项目管理生命周期采用各阶段共用的大卡片。消除结算阶段专用紧凑样式，保留现有推进底栏。
- 结算阶段先检查适用必填资料，再检查现有资料包是否可用且对应各细项当前批次。旧 ZIP 按既有条目规则比较。增删、替换、修改不适用原因以及资料包不可用都会要求重新归档。推进和归档沿用项目行锁。
- 平台类须确认劳务费用结转凭证、会务执行费用到账凭证。直付类须确认会议整体费用到账凭证。全部确认后，在现有最后阶段显示「已结项」。重点类与未分类项目沿用既有页面。仅登记凭证，按用户确认不录入和校验金额。
- 结项标识由适用凭证的当前存储状态派生。保留现有项目状态、阶段编号与工作台统计口径。更换凭证在当前结项阶段可用，旧凭证软删并审计。

## 接口与存储

- `GET /api/project/{id}/closeout/{phaseId}` 返回适用事项、当前凭证、确认状态、可写状态和结项标识。读取沿用项目查看范围。
- `POST /api/project/{id}/closeout/{phaseId}/{item}` 接收 multipart `files`，沿用项目成员操作范围、现有上传校验与体积限制。仅允许当前活跃结项阶段的适用事项。新文件在事务内登记入 `project_doc`，复用项目文件预览和下载。无 DDL、迁移或权限码变更。
- 项目详情的 `missingRequiredTemplates` 在必填齐全而当前资料包不可用时返回「归档结算资料包」。前端据此显示推进原因和归档跳转。

## 验证与交付

- `mvn -f backend/pom.xml -DgitShort=47f23f5 -Drevision=1.0.152 -Dtest=ProjectAccessRegressionTest test`，22 项通过。覆盖归档前拒绝推进、归档后文件变化、现有 ZIP 继续有效、平台凭证确认、非成员和错误事项拒绝。
- `npm --prefix frontend run build -- --outDir <隔离目录>/dist-stage --emptyOutDir` 通过。隔离 Edge 的 `node frontend/tests/settlement-stage-browser.mjs dist-stage` 通过，覆盖大卡片、归档启用推进、删后拦截、凭证上传和结项标识。
- Windows PowerShell `release-full.ps1 -Mode Preflight -Version 1.0.152 -Slug project-closeout` 通过。无待执行迁移，schema parity 通过。生产发布使用前后端平滑切换，回滚保留发布前 1.0.151 后端和前端快照。登录业务验收需要获准项目与凭证。
- 同一隔离 Edge 用例对正式制品 `builds/1.0.152-build-20261126_090938/dist` 重跑通过。线上首页、入口包和项目详情包均返回 200 且与正式制品的 SHA-256 一致。后端直连与经 Nginx 均报告 1.0.152 UP、提交 `c1e3389`。匿名结项查询返回 401，Nginx master PID 8200 未变。见 `logs/release-full-20261126_090859.log`。
- 发布前备份位于 `backup/backup_20261126_090915`。前端回滚快照配置为 `frontend/release-metadata/1.0.152-project-closeout.rollback.conf.txt`，旧后端为 `backend/releases/1.0.152-20261126_091329/previous-expert-system.jar`。回滚须经批准，按部署文档恢复并重新验证。
- 业务验收可从已有结算项目检查归档与推进联动，再分别用平台类和直付类项目确认凭证和「已结项」。此次生产验证未写入业务资料。
