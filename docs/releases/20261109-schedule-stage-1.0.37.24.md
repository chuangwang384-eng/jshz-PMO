# 项目流程重塑第一阶段「日程上传」 — 发布记录 1.0.37.24

- 日期：2026-11-09
- 版本：`1.0.37.24`（gitShort=2f680ef）
- 类型：前后端 + 数据迁移（新表×2 + 阶段种子）
- 需求：[需求文档](../requirements/2026-11-09-项目流程重塑-日程上传-需求文档.md) / [设计文档](../design/2026-11-09-项目流程重塑-日程上传-设计文档.md)
- 交接：[任务交接记录](../handover/tasks/20261109_project_schedule_stage.md)

## 1. 发布内容

- 删除项目类型/执行人概念；新建项目固定落第一阶段「日程上传」（AGENDA_UPLOAD）。
- 权限口径：推进/取消/回退=负责人+创建人；资料/预算/合同/日程操作与敏感明文=项目成员（负责人/创建人/协助人，历史执行人兼容）。
- 类型→阶段配置体系全部 410 停用（表保留观察，下版删除）；新增 `/project/phase-options`、`/project/user-options` 字典接口；删除 `/project/executors`。
- 日程解析：规则打底（pptx/docx 表格逐单元格 + 多人同段=讨论嘉宾 + 页脚大会主席）→ Ollama 兜底 → 专家库精确匹配；AI 结果=候选，详情页人工确认；库外专家预填跳转建档；支持手动补录。
- 前端：新建/编辑表单瘦身、列表阶段列带日程状态、详情页「日程与参会专家」卡（上传/解析/候选确认/手动补录）、ExpertList 支持 presetName 预填。

## 2. 发布链路

`temp\deploy-13724.cmd`（管理员/UAC）顺序执行，任一步失败即停：

1. `scripts\apply-migration-20261109-schedule-stage.ps1` — 幂等 DDL + 阶段种子（先迁移后发布，旧代码不感知新表）
2. `scripts\release-backend.ps1` — 后端蓝绿 1.0.37.24（`builds\1.0.37.24-build-20261109_125421\expert-system.jar`）
3. `temp\run-deploy-frontend-13724.ps1` — 前端 graceful 切 root 到 `1.0.37.24-schedule-stage-20261109`

## 3. 构建

| 项 | 结果 |
| --- | --- |
| 前端 | `builds/1.0.37.24-build-20261109_125421/dist`，24.95s / 0 错误；入口 index-djnUjLgX.js 含 v1.0.37.24 |
| 后端 | 同目录 `expert-system.jar`，build-info 校验 version=1.0.37.24 gitShort=2f680ef，BOOT-INF/日志配置校验过 |
| 迁移 | MIGRATION_OK 2026-11-09T12:57:06：project_schedule 13 列 / project_speaker 12 列 / 日程上传阶段激活 / 旧 6 阶段停用 / 标记表在 |

## 4. 发布后核验（2026-11-09 12:59 实测回填）

| 项 | 方法 | 结果 |
| --- | --- | --- |
| 后端 | `curl /health` | ✅ version=1.0.37.24 UP，buildTime=2026-11-09 12:54:55，gitShort=2f680ef |
| root 指针 | nginx conf | ✅ `frontend/releases/1.0.37.24-schedule-stage-20261109` |
| 入口版本号 | 入口 chunk 内搜 | ✅ v1.0.37.24 命中 |
| 哈希 | 本地 vs HTTP sha256 | ✅ index.html=`b76826bf…`、index-djnUjLgX.js=`1e17566c…`、ProjectDetail-BIH9GrRl.js 一致 |
| 旧资产兜底 | 旧 chunk（union 副本） | ✅ ProjectList-Ca1JcDUG.js / ProjectList-6bM5U2gu.js 均 200 |
| 接口鉴权 | 无 token | ✅ /api/project、/api/project/1/schedule 均 401 |
| SPA 路由 | /、/project、/login | ✅ 均 200 |
| 数据 | information_schema | ✅ 两新表在；激活阶段=「日程上传」唯一 |

> 测试范围说明：自动化=ScheduleParseServiceTest 3/3（示例日程 9 人角色全对）+ 后端 93 测试 0 失败 + 前端 build 0 错误；**业务真机验收（建项目→传日程→解析→确认）待 Owner**，未执行不算通过。

## 5. 回滚

- 数据：`DROP TABLE project_schedule; DROP TABLE project_speaker;`（当前无业务数据）；阶段种子 `DELETE FROM project_phase WHERE name='日程上传';` + 旧阶段 `is_active=1`
- 后端：`backend/releases/` 上一版 jar 蓝绿回切（旧代码不感知新表，无需回滚 DDL）
- 前端：`scripts/rollback-frontend.ps1`，旧 release 完整保留

## 6. 已知口径 / 风险

1. 重名专家匹配取先入库者（候选可人工改绑）；同一人多角色取最高（主席>主持>讲者>讨论嘉宾）；页脚主席只升格致辞行。
2. 图片型日程依赖 Tesseract OCR，中文表格识别率有限——失败走手动补录，验收时如实记录识别率。
3. 旧类型/阶段/执行人表本版保留未删（Owner 决策：观察后下版删除）；`/config/*` 返回 410。
