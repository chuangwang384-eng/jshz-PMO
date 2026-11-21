# 休眠代码清除排期计划（移交强模型施工，规划 Agent 不动码）

## 1. 基本信息
- 任务编号：20261119-dormant-cleanup-plan
- 任务性质：**纯规划**——本计划由 2026-11-19 会话产出，规划 Agent 不执行任何代码/表/前端改动
- 施工方：后续更强的智能体（按本文档逐阶段施工）
- 批准状态：排期与范围冻结 **待负责人批准**（批准前禁止进入任何实施阶段）
- 制定日期：2026-11-19
- 关联决策：审查清单 A19（已关闭，2026-11-19）关闭时登记的「半暗化休眠资产，保持现状不清理」→ 本计划即其有条件清理路径

## 2. 目标与验收条件
- 业务目标：清除已确认休眠的代码/表/前端资产，降低误读风险与维护面（决策载体已被 1.0.37.24 项目流程重塑删除）。
- 硬性前提：**只清"业务已死且运行时零依赖"的资产**；任何存在活跃引用的候选一律从范围剔除并回填本文档。
- 验收条件：
  - [ ] 全量引用核实报告完成，范围冻结并经 Owner 批准
  - [ ] 每阶段独立提交、独立发布窗口、可单独回滚
  - [ ] 删表前完成 RENAME 影子观察期（≥7 天）+ 全量备份
  - [ ] 施工后 217+ 后端测试全过、核心链路探针全过、schema 台账同步

## 3. 资产清单（候选，逐项须在 P0 核实后才可定案）

### S1 项目类型休眠链（A19 遗留，证据已核实）
| 资产 | 位置与证据 | 已知状态 |
| --- | --- | --- |
| `project_type` 表 | 种子：展会/会议/论坛 | 业务已死（新建项目 `ProjectService.create` 强制 `setTypeId(null)`，L402 注释「类型已删除概念，列保留停用」） |
| `project_type_phase_rel` / `project_phase_template_rel` 表 | 旧投影 | V3 `ProjectConfigurationService` 保存时仍**同步写入**（"Keep legacy read projections synchronized"，约 L189-194）——须先经 P2 切断写入路径才可清 |
| 实体/Mapper | `ProjectType`/`ProjectTypePhaseRel` + `ProjectTypeMapper`/`ProjectTypePhaseRelMapper` 等 | 随表处置 |
| `ProjectService` 休眠方法 | deleteType（约 L1385）/deletePhase 拦截（约 L1456）/类型流程查询（约 L1551）/阶段模板绑定（约 L1583-1617、L1687） | **待核实**调用链是否全为死端点 |
| `ProjectConfigurationService` 旧投影同步 | overview 的 types/phaseUsage 查询（约 L36-39）、`legacyCreate`（约 L212-217） | **待核实**哪些端点仍被前端调用 |
| 前端残留 | `TypeNode.vue`/`StepWizard.vue`/`FlowOverview.vue`/`ImpactPreview.vue`、`stores/projectConfig.js`、`utils/projectConfigRequestId.js`、`api/project.js` config 导出 | **待核实**：路由已改挂 `BudgetFeeConfig.vue`（router:183），但组件可能仍被懒加载引用 |

### S2 半暗化资产（本轮**不动**，仅观察登记）
- `project_config_snapshot` / `project_config_state` / `project_config_request` / `project_type_config_revision`：建项仍写入并作删除保护（审查底稿 04 登记"半暗化"）——**清除需先重构建项写入路径，单独排期，不入本期**。
- `DashboardRules.java`（LEGACY_BASELINE 分支读 config phases）：**待核实**是否仍被工作台引用；若死可并入 P1。

### S3 明确排除（禁止顺手清）
- **A4 三个兼容层**（`UserInfoCompatController`/`ExportCompatController`/`ComplianceLegacyService`）：有独立待决策项 A4，不入本期。
- **CONTRACT 合同审批流**（`project_contract` + ApplicationService CONTRACT 分支）：活跃流程，1.0.32 P1-1 验证项仍挂在上面。
- **`project_executor` 表**（执行人概念已删）：候选可并入 P3/P4，但须先核实无历史查询引用。
- **rotate/deploy/backup 等运维脚本**：与本计划无关，禁止纳入。

## 4. 分期排期（T = Owner 批准日）

| 阶段 | 窗口 | 内容 | 门槛 |
| --- | --- | --- | --- |
| **P0 核实冻结** | T+0 ~ T+3 天 | LSP/grep 全量引用核实 → 《引用核实报告》（逐资产：活跃引用清单/死端点判定）；影子实例全量回归（后端 217+ 测试）；范围冻结清单交 Owner 批准 | 报告 + 批准缺一不可 |
| **P1 代码摘除** | T+4 ~ T+7 天 | 仅编译级删除：实体/Mapper/Service 死方法/前端死组件/死路由导入；不改任何运行行为与接口契约 | 单主题提交；蓝绿发布窗口；测试全过 |
| **P2 投影写入切断** | T+8 ~ T+14 天 | 移除 `ProjectConfigurationService` 对 `project_type_phase_rel`/`project_phase_template_rel` 的同步 INSERT/DELETE 与 overview 旧查询 | 独立发布窗口；建项/详情/阶段推进探针全过 |
| **P3 表影子观察** | P2 后 ≥7 天 | `RENAME TABLE project_type → project_type_retired_YYYYMMDD`（及 S1 其余表）；生产日志观察 7 天无 `Unknown column`/`Table doesn't exist` | **DROP 前必须完成本观察期** |
| **P4 物理删除** | 观察期通过后 | 正式 DROP 影子表；`schema_migration` 台账补记；架构文档/审查底稿同步 | Owner 二次批准；备份在保留期内 |

> ⚠️ **P4 前置阻塞（A2）**：`schema_migration` 台账漂移 18 项 + 待登记 3 项，`db-migrate -Mode Apply` 当前会因漂移中止（审查清单 A2）。P4 的 DROP 迁移要么先解决 A2 重登记，要么采用手工幂等 SQL + 台账补记双轨。**A2 不解决，P4 不得执行。**

## 5. 施工方验收标准（DoD，逐阶段）
1. 编译 + 全量测试 0 失败；影子实例 `/health` UP 且版本号与制品一致；
2. 核心链路探针：登录/审批提交与通过/建项/阶段推进/预算确认/日程上传，全部 200 且无 ERROR 日志；
3. 无 `Table doesn't exist`/`Unknown column` 类运行日志；
4. 每阶段独立 git 提交（建议信息：`refactor(project): 休眠代码清除 P{n}（范围见 20261119_dormant_code_cleanup_plan.md）`）；
5. 本文档逐阶段回填实测结果（含命令与异常）。

## 6. 风险与回滚
| 风险 | 防控 |
| --- | --- |
| 摘除破坏隐藏调用链 | P0 引用核实 + 影子全量回归；发现活跃引用即剔除 |
| 删表丢历史数据 | DROP 前强制 RENAME 观察期 ≥7 天；备份保留策略覆盖全窗口 |
| 台账漂移导致迁移失控 | A2 解决或双轨补记，否则 P4 冻结 |
| 与在途需求冲突（预算阶段/日程门槛仍在迭代） | P1/P2 发布窗口须避开当周已排期发布；冲突时顺延 |

- 回滚步骤：代码=对应阶段 `git revert` 单主题提交；表=观察期内 `RENAME` 回原名（秒回）；兜底=全量备份还原。
- 回滚限制：P4 DROP 完成后只能靠备份还原，故 P4 需 Owner 二次批准。

## 7. 下一位施工 Agent 的接手指令
1. 首先检查：`git log -1`、本文件 §4 P0 状态、Owner 是否已批准（批准记录回填至 §8）。
2. 然后执行：按 §3 清单逐资产核实引用（LSP references 优先于文本搜索），产出《引用核实报告》并回填 §3 表格"已知状态"列。
3. 验收标准：§5 DoD 逐阶段全过；任何偏离须停下上报，不得自行放宽门槛。
4. 未经确认禁止：未获 Owner 批准进入 P1；跳过 P3 观察期直接 DROP；顺手触碰 §3 S3 排除项；在 deploy/rotate 脚本窗口外做任何发布动作。
5. 需要负责人决策：① 排期批准与 T 基准日；② P0 冻结范围确认；③ P4 DROP 的二次批准。

## 8. 决策与执行留痕（施工 Agent 逐项回填）
| 时点 | 决策/阶段 | 内容 | 记录人 |
| --- | --- | --- | --- |
| 2026-11-19 | 计划制定 | 本文档创建（纯规划，零代码改动） | 规划 Agent |
| （待填） | Owner 批准 | 排期与 T 基准日 | |
| （待填） | P0 | 引用核实报告结论 | |
| （待填） | P1~P4 | 逐阶段执行与验证结果 | |
