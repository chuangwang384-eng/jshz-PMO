# 个人工作台实施交接

> 最新发布状态（2026-11-04）：1.0.24已按用户批准上线，待业务验收；切流1次连接重置导致严格连续性门禁未通过，后续稳态复核正常。见[发布记录](../../releases/20261104-dashboard-production.md)。下文未发布描述保留为施工/隔离阶段事实。

## 1. 基本信息
- 任务编号：20261104_dashboard_implementation；任务：个人工作台V1.1.1实施；主责：Codex。
- 交接日期：2026-11-04（北京时间）；状态：已完成待验收；优先级：P1。
- 负责人授权：按V1.1文档实施，并明确选择“仅复用历史已完成阶段，其他类型暂待核实”。未授权生产发布。

## 2. 目标与范围
- 已实现：首屏个人待处理与风险、本人申请/审批分离、本人项目/收藏/公告/消息、固定入口、六类搜索、完整搜索列表、具体记录定位、处理后返回刷新、退回申请文本编辑重提。
- 完成判定：仅有效LEGACY_BASELINE快照内唯一历史“已完成”阶段；其余保守UNKNOWN；不新增完结字段或迁移，不以最后阶段猜测。
- 范围外：生产部署、合规待办聚合、拖拽、自定义入口、独立任务引擎、AI/OCR、外部统计。
- 验收：技术证据见专用说明与JSON；正式业务、代表性数据量及生产验收尚未完成。

## 3. 项目基线
- 基线提交：1060260；当前分支：codex/personal-dashboard-v11；未新增Git提交、未推送。
- 工作区：包含本轮代码/文档与上轮已批准文档修订，尚未提交。原有`.bdv4b.txt`、`tv.txt`未读取/修改；无他人变更被覆盖。
- 技术栈沿用现有Spring Boot/MyBatis-Plus、Vue/Vite/Element Plus；无应用依赖变更。
- 隔离验证：本机MySQL26.7.0，13319/dashboard_acceptance，datadir在`.build-dashboard-acceptance`；应用18085、前端localhost:5173。未读取或修改生产库，生产前端dist/index.html核实仍为14:09:44。
- 运行服务的最终清理状态见下方§10，不将隔离服务误认为生产部署。

## 4. 已完成工作与关键文件
| 文件/模块 | 实际作用 |
| --- | --- |
| backend/.../controller/DashboardController.java | 卡片聚合、当前可见入口、独立卡片请求 |
| backend/.../controller/GlobalSearchController.java | 六类预览与共规格完整分页 |
| backend/.../controller/DashboardFocusController.java | 授权单条定位、本人项目/审批完整列表 |
| backend/.../service/DashboardReadService.java | 最小字段只读投影、本人关系/审批/上架/生效过滤、批量阶段历史 |
| DashboardRules.java / DashboardTasks.java | 历史完成投影、日期边界；有界并行/错误隔离/超时与回收 |
| ModuleSearcher.java / GlobalSearchModules.java | 六模块注册查询规格，预览与完整列表一致 |
| ApplicationService.java / ApplicationController.java | 授权申请定位；可选文本补丁重提、原bodyless兼容、状态条件更新与事务 |
| frontend/src/views/dashboard/DashboardView.vue | 首屏、卡片、轮询、返回刷新、卡片版本隔离及el-main滚动恢复 |
| GlobalSearchBar.vue / GlobalModuleResults.vue / DashboardWorkList.vue | 搜索、键盘/输入法、完整结果及个人列表 |
| frontend/src/utils/dashboard.js / dashboardFocus.js / api/dashboard.js | 安全内站目标、请求版本、跨分页定位与取消 |
| App.vue / router/index.js / Login.vue / api/request.js | 路由承载、默认入口、合法深链保留，取消/卡片错误静默处理 |
| ExpertList / KnowledgeDocList / MyApproval / ApprovalManage / MessageCenter / AnnouncementDetail | 精确定位、既有处理/预览界面、退回编辑、成功打开已读 |
| 三个新增后端测试类、两份前端测试、scripts/test-dashboard.ps1、scripts/tests/dashboard-http-regression.py | 自动化回归与隔离护栏 |
| database/testing/dashboard_baseline.sql | 35张相关表的测试元数据，无业务数据、无生产迁移 |

数据库/迁移：无业务库结构或数据修复变更。API：新增6个GET端点、扩展原resubmit可选body。角色/权限码/CORS：无策略变更；新接口执行现有权限与资源范围。配置/依赖：无生产新增项。

## 5. 验证证据
- 实际运行`scripts/test-dashboard.ps1`：17项后端测试及隔离打包通过；当时7项前端测试及隔离build通过。
- 后续补卡片并行测试：`node --test frontend/tests/dashboard.test.mjs frontend/tests/dashboard-search.test.mjs`，最终8项通过。最终前端构建输出`.build-dashboard-acceptance/frontend-delivery-latest`，通过。
- `python scripts/tests/dashboard-http-regression.py`：虚构双账号，未登录/跨账号/伪造userId/无权限审批/参数边界/六类完整与预览一致检查通过。
- 真实浏览器（IAB、1366×768）：默认工作台、登录恢复具体申请、键盘专家定位、知识查看全部→预览、实际审批后待审1→0、修改姓名/新增单位后重提、退回1→0、消息已读、公告已读通过；无API mock。
- 同一申请实际已保存修改且权威专家记录尚未改动（必须审批后应用），HTTP复核通过。
- 单模块表锁故障：供应商搜索约1071ms、收藏约1032ms返回对应模块错误；其他模块成功；搜索错误数量为null，不伪装无结果。
- 快照哈希异常→UNKNOWN、不告警；回退历史名称来自快照，通过；测试临时改动均恢复/精确清理。
- 压力共4000请求：两接口1/20并发各1000，模块错误0；20并发P95聚合216.4ms/搜索180.1ms。仅极小虚构数据集、环回网络，不作生产性能结论。
- `git diff --check`、本地文档链接/代码制品源一致性及证据JSON检查通过。
- 原始日志/制品：本地忽略目录`.build-dashboard-acceptance/`，不可误当已提交交付；可提交脱敏证据见`docs/testing/evidence/dashboard-v11/`。

验证中失败及解决：反斜杠fixture改参数化；已持久化浏览器fixture与空表假设冲突，改事务隔离且保留断言；SFC卸载hook补局部document桩；32队列20并发出现11个繁忙错误后调128并复测零错误；JAR运行锁导致一次打包失败，仅停隔离进程重打通过；15185来源CORS被拒绝，改用既有允许localhost:5173；首次默认前端build在transform阶段终止，改隔离outDir。详情见专用说明。

## 6. 未完成与验证边界
- 已批准的其他类型UNKNOWN保守范围仍保留，后续完成语义须负责人另行确定，不擅自补完结字段。
- 代表性正文/生产数据量、真实内网网络、冷启动压力、不少于5名员工可用性、Chrome/Edge最近两个版本矩阵未执行；均待验收，不宣称通过。
- 浏览器主链路后补卡片请求隔离、nextTick滚动恢复及已登录访问login时原目标优先；已做前端自动化/构建，滚动恢复未单独浏览器复测。
- 生产数据库是否已具备有效项目配置快照未核实，发布前必须核实，不可自动运行其他任务迁移。

## 7. 风险与回滚
- 新查询规格必须随原模块可见性变更同步，避免权限规则漂移；已有跨账号与行级回归防护。
- 小数据压力达标不代表大规模LIKE/COUNT成本已解决；无搜索索引或缓存变更。
- 业务回滚恢复匹配前后端制品与旧默认入口，保留原模块路由；不删除申请、审计、原始资料。无本功能业务迁移需回滚。
- 本地撤销只针对本轮代码，不重置工作区或覆盖之前批准的需求修订；相应更新文档实现状态。

## 8. 文档同步
- 已更新：原需求/设计V1.1.1、architecture.md、deployment.md、deployment-dashboard-v11.md（API/权限/测试/回滚）、证据README/JSON、上轮记录的后续状态、本记录及最新摘要。
- 数据字典/业务迁移/生产发布记录：无本轮实际变更，不伪造发布结论。测试schema在专用说明中明确为测试用途。

## 9. 下一位Agent的第一步
1. 读取AGENTS.md、本记录、需求/设计V1.1.1；运行git status与git log确认基线及未提交文件，不覆盖`.bdv4b.txt`/`tv.txt`。
2. 对照专用说明复核技术证据与未执行验收项；在独立环回MySQL13319、datadir含`.build-dashboard-acceptance`环境设置测试专用变量运行`scripts/test-dashboard.ps1`。打包前先确认本次隔离JAR已停止，避免Windows锁文件。
3. 请负责人按实际业务数据验收首屏、申请审批与项目UNKNOWN展示，再另行核实生产快照/制品/备份前置条件并申请发布。
4. 禁止直接生产发布/迁移、变更项目完结语义/认证/权限、删除原始数据、外发公司资料或提交私有测试凭证。

## 10. 测试环境清理
- 已停止本次隔离应用18085、前端5173和MySQL13319；环回连接核实三个端口均无监听。未停止或重启生产服务。
- 私有制品、数据库目录及凭证仍保留于Git忽略的`.build-dashboard-acceptance/`，未执行递归删除或移动，不得提交私有文件。
