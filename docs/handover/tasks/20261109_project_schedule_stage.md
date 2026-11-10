# Agent 任务交接记录：项目流程重塑第一阶段「日程上传」

## 1. 基本信息
- 任务编号：20261109-project-schedule-stage
- 任务名称：删除项目类型/执行人概念 + 第一阶段日程上传与参会专家解析
- 主责 Agent：ZCode（GLM）
- 交接时间：2026-11-09
- 当前状态：已完成待验收（**已发布 1.0.37.24**：迁移+蓝绿+前端全链完成，发布后核验全过，见 [发布记录](../../releases/20261109-schedule-stage-1.0.37.24.md)；业务真机验收待 Owner）
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：见 [需求文档](../../requirements/2026-11-09-项目流程重塑-日程上传-需求文档.md)
- 本次范围：概念删除（类型/执行人/新建选阶段）、日程上传+解析（主席/主持/讲者/讨论嘉宾）、人工确认+专家库精确匹配、权限口径改造、旧配置停用（410）
- 不在本次范围：后续阶段定义、旧表物理删除、图片日程识别率优化
- 验收条件：
  - [x] 规则解析单测通过（示例日程 9 人角色全对）
  - [x] 后端 mvn test 93 通过 0 失败（9 跳过，与改动前口径一致）
  - [x] 前端 production build 0 错误（27.3s）
  - [ ] Owner 真机验收：新建项目→上传日程→解析→确认→库外专家去建档
  - [ ] 生产迁移执行 + 蓝绿发布（未做，需 Owner 批准）

## 3. 项目基线
- 分支：codex/personal-dashboard-v11
- 当前提交号：本次功能提交（见 git log「feat: 项目流程重塑第一阶段」）
- 工作区状态：本项目文件已提交；**工作区仍有大量 1.0.37.15→.23 已发布未提交代码与 docs 重组改动，非本次任务范围，勿误动**
- 运行环境：生产仍在 1.0.37.23（本功能未部署）

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据 |
| --- | --- | --- | --- |
| 迁移脚本（幂等） | database/migration/20261109_project_schedule_stage.sql | 已写未执行 | 新表 project_schedule/project_speaker + AGENDA_UPLOAD 阶段种子 + 旧 6 阶段停用 + 标记表 |
| 实体/Mapper | ProjectSchedule/ProjectSpeaker + 2 Mapper | 完成 | 编译通过 |
| 解析服务 | service/ScheduleParseService.java | 完成 | 规则打底→Ollama 兜底→合并→专家库精确匹配；候选 confirmed=0 |
| LLM 抽取 | OllamaService.extractScheduleSpeakers | 完成 | role 受限枚举，失败返回 null 降级 |
| 权限改造 | ProjectService | 完成 | isProjectMember（负责人/创建人/协助人+历史执行人兼容）、isOwnerOrCreator（推进/取消/回退）、行级过滤含 creator/assistant |
| 配置停用 | ProjectController/ProjectConfigurationController | 完成 | 全部 /config/* 与 /{id}/configuration 返回 410 |
| 字典接口 | /project/phase-options、/project/user-options | 完成 | 替代已删 config/phases 与 /executors |
| 前端 | ProjectEdit（删类型/阶段/执行人）、ProjectList（删筛选/配置入口、阶段列带日程状态）、ProjectDetail（日程与参会专家卡：上传/解析/候选确认/手动补录/去建档）、api/project.js、ExpertList（presetName 预填）、MaintenanceBoard、router（删配置路由） | 完成 | build 通过 |
| 单测 | ScheduleParseServiceTest（示例日程 fixture 3 用例） | 3/3 通过 | target/surefire-reports |

## 5. 实际变更
- 新增：见上表；修改：ComplianceV4AcceptanceIT（pageProjects 签名同步）、ProjectAccessRegressionTest（+assistant mock）
- 数据库：迁移脚本**未执行**；API 契约：/config/*→410、create 不再收 typeId/executorIds、列表返回含 scheduleStatus/speakerConfirmedCnt
- 删除接口：GET /project/executors

## 6. 测试与验证
| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 单测（规则解析） | mvn test -Dtest=ScheduleParseServiceTest | 3/3 通过 |
| 全量后端测试 | mvn test（需 shell 注入 JWT_SECRET，缺省会因 WeakKeyException 失败 contextLoads，与本改动无关） | 93 通过 0 失败 9 跳过 |
| 前端构建 | npx vite build --mode production | 0 错误 27.3s |
| 真机/接口验证 | 未执行（未部署） | 未执行 |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 下一步 | 责任方 |
| --- | --- | --- | --- |
| 迁移执行+发布 | 需 Owner 批准（高风险操作） | 参照 deploy-13723 模式编排：迁移→后端蓝绿→前端 graceful | Owner 批准后 Agent 执行 |
| 真机验收 | 未部署 | 用测试/111111 建项目传示例日程验证 | Owner |
| 图片日程识别率 | Tesseract 中文表格有限 | 验收时如实记录，失败走手动补录 | Agent+Owner |

## 8. 风险与回滚
- 旧快照项目（1.0.37.23 前创建的测试项目已清零，实际无存量）走 snapshotOrNull 兼容分支，理论上无存量影响
- 回滚：代码回切上一版 jar/release；新表 drop 即可（无数据）；迁移幂等可重放
- 已知口径：重名专家匹配取先入库者（候选可人工改绑）；同一人多角色取最高角色；页脚主席只升格致辞行

## 9. 文档同步
- [x] 需求/设计文档（前次提交 47ce6ac）
- [x] 本交接记录
- [x] 交接摘要（README 已更新）
- [ ] 发布记录（发布时新建）

## 10. 下一位 Agent 的接手指令
1. 首先检查：git log 确认本功能提交；`git status` 注意勿动工作区其他未提交文件
2. 然后执行：获 Owner 批准后编排发布（迁移脚本→release-backend.ps1→run-deploy-frontend）
3. 验收标准：/health 新版本；新表在库；新建项目→上传 `10.23-日程.pptx`→解析 9 人角色正确→确认保存
4. 未经确认禁止：生产迁移、发布、删除停用表
5. 需要负责人决策：第二阶段定义；旧表（project_type 等）物理删除时机

---

## 追加：2026-11-10 附件删除 + 解析泛用性强化（1.0.37.29，未发布）

- Owner 反馈：附件不可删、解析操作逻辑不清晰、三门峡新日程解析准确率低。
- 取证根因与全部变更、验证证据见 [发布记录](../../releases/20261110-schedule-doc-delete-1.0.37.29.md)（§2 取证 / §3 变更 / §4 实测：三门峡真实文件候选 23 人全量抓齐）。
- 新接口：`DELETE /api/project/{id}/schedule/docs/{docId}`（ScheduleParseService.deleteScheduleDoc，成员级 requireOperable）。
- 口径变更（Owner 已确认）：上传=替换（不做历史版本）；删生效日程附件=联动硬删日程记录与未确认候选（confirmed=1 保留）；文件仅在全库无引用时物理删。
- 已于 2026-11-10 09:00 发布上线（`temp\deploy-13729.cmd`：后端蓝绿+前端 graceful，无迁移）；发布后核验通过（/health 直连+经 Nginx 均 1.0.37.29 UP、root 已切且 master PID 未变、index 哈希一致、schedule 接口 401、登录冒烟过）；页面真机验收待 Owner。

---

## 追加：2026-11-10 解析泛用性第二轮强化（1.0.37.30，已发布）

- Owner 复测曲径通幽日程识别率差；取证四份真实文件（曲径通幽/三门峡/邯郸卫星会/线上肾病）暴露 7 类形态问题与修复，及详情页「阶段资料」区移到「参会专家」之前，见 [发布记录](../../releases/20261110-schedule-parse-generalize-1.0.37.30.md)（§1 取证表 / §2 变更 / §3 实测 12/23/4/7 人全对 / §4 发布核验）。
- 于 09:43 发布上线（temp\deploy-13730.cmd，无迁移）；页面真机验收待 Owner（重新解析曲径通幽日程应为 12 人：主席1+主持2+讲者2+讨论6+总结1，其中主持行来自表内「主持：X 教授 单位」）。
