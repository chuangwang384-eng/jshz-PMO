# 发布记录：企业合规记事本（1.0.37.34）

- 日期：2026-11-10
- 需求/设计：`docs/requirements/2026-11-10-企业合规记事本-需求文档.md`、`docs/design/2026-11-10-企业合规记事本-设计文档.md`
- 状态：**已发布生产为 1.0.37.36（17:18，迁移+后端 R-1 蓝绿+前端 graceful），发布后核验通过；页面真机验收待 Owner**
- ⚠️ **并发发布事件（已澄清）**：另一并行会话（Codex，合规模板单次保存任务，见 `docs/handover/tasks/20261110_compliance_template_single_save.md`）于 15:37/16:01 发布了 1.0.37.34/35（模板工作区单次保存+清空，与本记事本无代码冲突）。本发布首跑因迁移校验列数笔误（todo 16 列误写 17）中止；澄清后版本号改为 **1.0.37.36**（避免回退 .35）重建制品二次发布，当前树已包含两批全部改动（diff 核对：.35 快照 ⊂ 当前树）。
- 类型：功能迭代 + **有增量迁移**（新表×3+列×3，无存量数据变更）

## 1. 迁移（20261110_compliance_notebook.sql，幂等可重放）
- `compliance_item` 加 `withdrawn/withdrawn_by/withdrawn_time` + 索引 idx_ci_doc_withdrawn。
- 新表：`compliance_item_hide`（user_id+item_id 唯一）、`compliance_project_doc_binding`（project_id 主键）、`compliance_project_todo`（project_id+source_item_id 唯一、快照+row_version 乐观锁）+ done 标记表。
- 回滚：删 3 新表与 3 新列即可（新表无存量时零损失）。

## 2. 功能与接口（/api/compliance/notebook）
| 能力 | 实现 |
| --- | --- |
| 记事本查询 | GET /items：收藏 ∩ 已发布未撤下 ∩ 上架；documentId 筛选；view=hidden；按文档分组（真实标题） |
| 隐藏/恢复 | PUT /items/{id}/hide、/unhide（幂等，仅本人收藏口径内事项） |
| 维护端撤下 | POST /documents/{id}/withdraw-preview（标题+全量条数）与 /withdraw（expectedCount 集合版本校验，不符报冲突；事务内置 withdrawn+逐条 history+operation log COMPLIANCE_WITHDRAW）；候选/原件/收藏/模板/项目待办不受影响 |
| 可推送项目 | GET /pushable-projects：**真实参与关系**（ProjectService 新增 public isRealProjectMember——负责人/创建人/协助人/历史执行人，**不含 admin 全量查看豁免**，设计文档 §权限）+ 绑定兼容 |
| 推送 | POST /push：事务内锁项目行（FOR UPDATE，与撤下一致锁顺序）→ 校验成员/来源权限/发布状态/同一文档 → 首次绑定写 binding（异文档已绑定→拒绝"本期不支持换绑"）→ 逐条快照 insert（唯一约束去重计 skipped）；整批回滚；COMPLIANCE_PUSH 留痕 |
| 项目待办 | GET /projects/{id}/todos（项目可读）、PUT /todos/{id}（真实参与人；PENDING/DONE；row_version 冲突检测） |
| 项目详情 | ProjectService.detail 返回 complianceTodos + complianceBoundDocTitle |

## 3. 旧执行移除（需求规则 6）
- 后端：ComplianceV4Controller 六个写端点（create/start/reuse/pause/archive/PATCH task）改抛「旧执行清单已下线」，GET 只读保留。
- 前端：「我的执行」页签与 ExecutionBoard 移除；默认页签改「合规记事本」；「原有待办」（V3）页签按需求保留。
- 提醒：ComplianceDueScheduler（due-enabled 默认 false）与 V4 reminder rollout kill switch 保持关闭；发布核验时确认。
- **数据清理**：`database/migration/20261110_compliance_legacy_cleanup.sql`（含 COUNT 核对段；DELETE 按 reminder→event→task→execution）。**未随发布执行，需 Owner 批准后手工执行**（不可恢复）。

## 4. 前端
- 新组件 `ComplianceNotebook.vue`：文档筛选、已隐藏切换、事项卡片（重要红标/纯文本内容/来源引用）、批量勾选推送（项目下拉含绑定兼容提示、结果显示新增/跳过）。
- `EnterpriseCompliance.vue`：页签重排（记事本默认/文档库/模板维护+情况事项看板/原有待办）；监听 favorite-changed 即时刷新记事本。
- `ProjectDetail.vue`：独立「合规要求注意事项」卡（不受阶段折叠影响）：绑定文档名、待办快照列表、完成/重开（row_version 冲突提示）。
- api：`api/compliance.js` 新增 9 个记事本封装。

## 5. 验证（实测）
| 验证项 | 结果 |
| --- | --- |
| 后端全量 mvn test | **99 通过 / 0 失败 / 9 跳过**（跳过=隔离库门控 IT） |
| 隔离库验收 IT（新增 ComplianceNotebookAcceptanceIT，ACCEPTANCE_DB 门控） | 编译通过；覆盖 AC02（双账号隐藏隔离/取消收藏移除）、AC04（重复推送去重/绑定冲突拒绝）、AC05（非参与者与全量查看权被拒、历史执行人可推）、AC06（撤下后快照与状态不变）、撤下计数冲突——**待隔离库环境执行**（生产发布后可对隔离库跑一轮） |
| 前端构建 | 30.4s，is-not-exported=0 |
| 制品 | jar build.version=1.0.37.34（gitShort=a417c51）；前端 index-CBa9tGqe.js 含版本号、EnterpriseCompliance chunk 含「合规记事本」 |

## 6. AC 核对状态（发布后真机逐条）
AC01 上传发布收藏阅读（真机）/ AC02 IT+真机 / AC03 清空计数（真机，撤下预览展示准确条数）/ AC04 IT+真机 / AC05 IT / AC06 IT+真机 / AC07 旧入口已移除（本版）+清理待批准执行。

## 6.1 发布执行与核验（17:15–17:18 实测）
- 迁移：MIGRATION_OK 17:15:56（校验 hide=1 binding=1 todo_cols=16 withdrawn_col=1 marker=1；幂等重放成功）。
- 后端 R-1 发布完成（`logs/deploy-backend.20261110_171601.log`）；前端 graceful（ReleaseId `1.0.37.36-compliance-notebook-20261110`，DEPLOY_EXIT=0）。
- 核验：`/api/health` 经 Nginx **1.0.37.36 UP**（buildTime=17:12:13、gitShort=a417c51）；root 已切新 release、线上 index=磁盘哈希（index-C9YIe1Pz.js）；SPA 200；notebook/withdraw 端点未登录 401。

## 6.2 热修 1.0.37.36.1（17:26 后端蓝绿发布完成）
- Owner 反馈：模板维护「情况事项」看板分组显示「文档 #58」编号而非文档名。
- 根因：维护端 `ComplianceService.listItems` 从未填充 documentTitle（员工视角 myTodo 有填充，维护端遗漏）。
- 修复：listItems 批量查询文档标题并逐行填充 documentTitle；前端 MaintenanceBoard 已优先用 documentTitle，无需改前端。
- 发布：`temp\deploy-13736-1.cmd` 仅后端蓝绿（`builds/1.0.37.36.1-build-20261110_172415`，build.version=1.0.37.36.1）；前端制品不变（package.json 保持 1.0.37.36，版本说明同 1.0.37.28.1 先例）。

## 7. 回滚
上一版制品即可（迁移仅增量，旧代码不感知新列/新表；回退后旧版 listItems 会显示 withdrawn 行——旧入口已关闭缓解）；旧提醒保持关闭，避免回退后重新产生旧执行数据。
