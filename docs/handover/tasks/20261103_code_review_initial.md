# 项目代码审查：首轮核查记录

- 任务编号：20261103_code_review_initial
- 主责：Codex；日期：2026-11-03；状态：进行中（首轮静态核查完成，全项目审查未完成）。
- 目标：检查无效代码与模块联动缺口；不修改业务代码、不发布、不操作数据库。
- 基线：main，8880324；共享工作区不干净。既有修改为 frontend/package.json、package-lock.json、src/views/project/ProjectConfig.vue；未跟踪 src/stores/、src/views/project/components/。均未改动。
- 技术：pom 为 Spring Boot 3.2.5 / Java 17；前端 Vue 3/Vite。实际服务版本、数据库结构、部署状态未验证。

## 已核查与初步发现
1. 供应商删除缺少项目关联处理：SupplierService.deleteSupplier（283 起）删除供应商及价格/评价/酒店打印关系；未处理 project_supplier。ProjectService.listSuppliers（587 起）跳过已不存在供应商，budgetOf（777 起）仍直接累计关联费用；removeSupplier（638 起）直接解引用 supplierMapper.selectById 的结果。可能造成项目名单消失但预算保留，以及移出时报空指针。迁移 20261027_add_project_module.sql 的 project_supplier 未声明外键；实际数据库及其他触发器未核实。需隔离测试确认，勿直接删除生产数据。
2. 联动已有实现：项目关联专家、供应商、合同、预算和操作日志；ProjectEdit 被列表与详情作为弹窗使用，不能因无独立路由判定无效。会议执行与协会合规页面共用 KnowledgeDocList，属于有效复用。
3. 企微 OAuth 明确为占位（二期、等备案），见 AuthController 139/150 附近；属于尚未实现能力，不应直接删除。
4. 待评估需求：项目与客户关联（Project 实体未见 customerId）；项目变更通知（ProjectService 未搜到 Notification 调用）。仅为待确认需求，不代表全系统无其他实现。
5. README 表数/Controller 数的当前基线未核实，不应作为本轮事实。测试目录仅搜到 AuthServiceTest 与 ExpertSystemApplicationTests；未发现项目/供应商对应测试文件。

## 验证证据与限制
- 已执行 git status --short、git branch --show-current、git log -1 --oneline、rg --files、rg -n 引用检索和 Get-Content 静态阅读。
- 已阅读仓库 AGENTS、README、最新交接摘要及架构/部署文档开头。全量架构与业务需求仍待阅读。
- 数据库、接口、页面回归、单元测试、构建、部署健康检查：未执行。不得将首轮结果当作完整审计。
- 默认 .codex/.sandbox/sandbox.log 不存在；.sandbox-bin Owner 为当前用户，权限继承关闭。当前命令已可运行，未变更 ACL；历史沙箱故障根因尚未证实。

## 下一步及验收
1. 固定 8880324 的独立快照后扩大审查，排除另一 Agent 进行中的项目配置改造；不要切换共享工作区分支。
2. 检查供应商删除全链路与实际测试库约束，用测试数据复现名单/预算/移出行为。
3. 补查 API 调用、动态组件、后端反射和计划任务，再输出无效代码候选与模块联动矩阵。
4. 验收：每个问题有文件位置、触发条件、影响和验证状态；业务需求与缺陷分开列示。
- 风险：共享文件可能变化，本次仅首轮静态观察。
- 回滚：无业务变更，仅新增本记录及摘要链接；无需迁移。
- 文档：新增本记录，摘要追加链接；架构/部署/API 未修改，因为未实施功能变更。
- 禁止：未经批准删除代码、修改认证/权限、生产数据操作或部署。
