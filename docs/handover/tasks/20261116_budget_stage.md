# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261116-budget-stage
- 任务名称：项目管理新增「会议预算制作」阶段（线上简化报价单/线下六类费用明细+可配置收费标准）
- 主责 Agent：ZCode (GLM-5.3)
- 交接时间：2026-11-16
- 当前状态：已部署待人工验收（后端/前端 1.0.39，2026-11-16 11:40 上线，接口级验证通过，浏览器验收未做）
- 优先级：P1

## 2. 目标与验收条件
- 业务目标：项目流程在「日程上传」之前新增「会议预算制作」阶段；线下项目按 酒店费用（场地/住宿/用餐/茶歇）、交通费用（大交通按专家人数预估+地面交通车队专车/平台专车）、搭建费（非必需：签到背景板/LED/影音设备等）、物料费（设计费+公司收取串场500/场+沟通函及延展500/场+日程上浮5-10张+台卡上浮10-20张+展架每场4个易拉宝+条幅写真布+其他打印500+讲台花300-400+桌花视需求）、媒体费（非必需）、礼品费（客户确认后按200-300/份）、人员费（500元/人/天+交通住宿据实报销）填写；线上项目先做简化报价单（模板点选形式待定）；收费标准默认值管理员在「管理员设置→项目配置」维护。
- 本次范围：阶段种子+三张新表+后端接口+前端预算面板/收费标准页+文档。
- 不在本次范围：生产发布、线上模板点选（待定）、物料按参会人数自动联动计算、报价单导出/打印。
- 验收条件：
  - [ ] 迁移执行后 phase sort=98 位于日程上传之前，stepper 排序正确
  - [ ] OFFLINE 项目进入该阶段可见七节明细骨架（含收费标准预填），可增删行、保存草稿、确认报价，金额=数量×单价服务端计算
  - [ ] ONLINE 项目为简化报价单并显示"模板待定"提示
  - [ ] 已确认报价单不能再被草稿保存覆盖
  - [ ] 管理员项目配置页可修改收费标准，新项目打开预算单取新默认值（区间取低值）
  - [ ] 旧接口 PUT /api/project/{id}/budget（登记预算金额）不受影响

## 3. 项目基线
- 分支：codex/personal-dashboard-v11（main 停在 2026-11-03 旧代码，勿在 main 上继续）
- 当前提交号：9513801（1.0.39 生产运行版本）
- 工作区状态：本次提交后仅余本地不入库产物
- 前端 1.0.38.x / 后端 1.0.38.x / MySQL 26.7
- 运行环境：本机即生产（<LOCAL_PATH> 后端 + Nginx 前端
- 以上信息核实时间：2026-11-16

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据或说明 |
| --- | --- | --- | --- |
| 幂等迁移（阶段+三表+种子13项） | database/migration/20261116_budget_stage.sql | 已写，生产未执行 | 幂等写法沿用 20261109 模板 |
| 迁移执行脚本 | scripts/apply-migration-20261116-budget-stage.ps1 | 已写 | 用 common-credentials.ps1，含 DryRun 与三重校验 |
| 后端实体/Mapper×3/Service/Controller | backend com.jshz.expert | 完成 | mvn compile 通过 |
| 单测 | ProjectBudgetServiceTest（8 用例） | 通过 | mvn test 149/149 |
| 前端预算面板 | frontend/src/views/project/components/ProjectBudgetPanel.vue | 完成 | vite build 通过 |
| 详情页接入专用阶段视图 | ProjectDetail.vue（isFlowStage/isBudgetViewing） | 完成 | 同日程上传口径 |
| 收费标准管理页+路由恢复 | views/admin/BudgetFeeConfig.vue、router /admin/project/config | 完成 | — |
| API 封装 | api/project.js（getProjectBudget 等5个） | 完成 | — |

## 5. 实际变更
- 新增文件：上表所列 11 个（含迁移/脚本/文档）
- 修改文件：ProjectDetail.vue、api/project.js、router/index.js、docs/architecture.md、docs/handover/README.md
- 删除文件：无
- 数据库/迁移：20261116_budget_stage.sql（**生产未执行**）
- API/接口契约：新增 GET/PUT /api/project/{id}/meeting-budget、POST /{id}/meeting-budget/confirm、GET/PUT /api/project/config/budget-fee-standards；旧 PUT /{id}/budget 不变（曾因路径冲突改名，见 §8）
- 页面/路由/权限：恢复路由 /admin/project/config（project:manage）承载收费标准；项目详情预算阶段只读口径=非项目成员或非当前阶段
- 配置/环境变量：无
- 依赖变更：无

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 证据/备注 |
| --- | --- | --- | --- |
| 单元测试 | mvn test（注入 JWT_SECRET，backend/ 内） | 通过 | Tests run: 149, Failures: 0, Errors: 0, Skipped: 11（skip 为既有） |
| 编译 | mvn compile -Dspring-boot.build-info.skip=true | 通过 | build-info 需 git 信息，本地加 skip |
| 前端构建 | npm run build | 通过 | is-not-exported 警告=0 |
| 迁移幂等 | 未在库执行（生产库勿直接跑） | 未执行 | 发布时用执行脚本，可重复执行 |
| 接口/页面验证 | 未执行 | 未执行 | 需按发布流程起服务后验证 |
| 权限/安全验证 | 未执行 | 未执行 | 写操作走 DbPermissionFilter project:manage（数据驱动，未实测） |
| 部署健康检查 | 不适用 | 不适用 | 未发布 |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 生产发布（迁移+后端+前端） | 发布需负责人批准 | 功能未上线 | 按 §10 发布序列执行 | 负责人+Agent |
| 浏览器人工验收 | 未部署 | 验收条件未闭环 | 发布后按 §2 逐条验 | 负责人 |
| 线上模板点选 | 形式待定 | 线上报价为简化表单 | 负责人定模板形式后开发 | 负责人 |
| 物料按参会人数自动联动 | 第一版仅默认值预填 | 需手动改数量 | 视业务反馈追加 | 待定 |

## 8. 风险与回滚
- 已知问题：无（测试全绿）
- 数据风险：迁移仅 CREATE TABLE IF NOT EXISTS/INSERT IGNORE/插入一行 phase，不改既有表数据；回滚=DROP 三表+DELETE phase 行（见 SQL 头注释）
- 兼容性风险：新端点曾与旧 PUT /{id}/budget 冲突（Ambiguous mapping），已改 /meeting-budget，旧前端/旧调用不受影响；后端先于前端发布时新路由 404 无影响
- 回滚步骤：代码回滚到本次提交前；数据库按 SQL 头回滚预案（新表尚无生产数据时可安全 DROP）
- 回滚限制：若预算单已填数据，回滚前需备份 project_budget/project_budget_item

## 9. 文档同步
- [x] 架构文档（docs/architecture.md 新增小节）
- [ ] 部署文档（发布时补发布记录至 docs/releases/）
- [x] 接口文档（架构文档小节内含端点清单）
- [x] 数据库迁移/数据字典（迁移 SQL 即字典）
- [x] 测试说明（本记录 §6）
- [ ] 发布说明（发布时）
- [x] 最新交接摘要（README.md 已加链接）
- 未完成项及原因：发布类文档待实际发布时补

## 10. 下一位 Agent 的接手指令
1. 首先检查：git log 确认本次提交在 codex/personal-dashboard-v11；`"<LOCAL_PATH> Files\MySQL\MySQL Server 26.7\bin\mysql.exe"` 查 project_phase 是否已有「会议预算制作」（应有则迁移已跑）。
2. 然后执行（经负责人批准后）：管理员 PowerShell 跑 scripts\apply-migration-20261116-budget-stage.ps1（先 -DryRun）→ 按既有蓝绿发布脚本发后端（mvn package 禁止在 backend/ 内跑，用发布脚本）→ 发布前端。
3. 验收标准：§2 六条逐条验证；测试账号 测试/111111。
4. 未经确认禁止：直接对生产库手工执行 SQL（必须走执行脚本）；跳过 DryRun；未批准即发布。
5. 需要负责人决策：线上模板点选形式；礼品/物料默认值是否调整；发布窗口。
