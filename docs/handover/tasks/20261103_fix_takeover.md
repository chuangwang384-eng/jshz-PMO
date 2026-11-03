# 审查缺陷修复与崩溃Agent接手交接

## 1. 身份、目标与状态
- 任务编号：20261103_fix_takeover；主责：Codex；日期：2026-11-03；优先级P1。
- 状态：已完成待验收（实现与开发验证完成；未部署、未业务验收）。
- 范围：时间线资源授权、供应商引用一致性、富文本两项联合修复、合规提醒五项修复；接手项目配置图形化未提交改动。用户明确授权本次权限修复。
- 不做：生产发布/数据修复/历史文件批量清理、公共认证改造、无关重构。

## 2. 项目基线
- 起点：main / 8880324a2d358fa068c316feca314a802193bcbb，工作区有另一Agent留下的前端package/lock/ProjectConfig及store和9个组件，另有本轮前置技能与审查文档。
- 当前分支：codex/fix-review-and-config-takeover；未覆盖原改动，承接后针对性修复。
- 本机Node24.22.0/npm11.19.1；pom Java17/Spring Boot3.2.5；真实运行版本、数据库元数据和服务健康未核查。

## 3. 逐项结果
| 验收项 | 实现 | 开发证据 |
| --- | --- | --- |
| 时间线权限 | Controller传AuthUser，Service复用requireProjectReadable，详情调用同入口 | 非成员403、负责人/执行人/view-all通过测试 |
| 供应商一致性 | 被项目引用拒删；关联/删除锁供应商行；旧缺档行显示名称及费用且可移出 | 引用拒删与历史缺档回归测试 |
| 富文本删除 | 正确解析_richtext相对路径，拒绝越界；有效共享引用归零后提交清理 | 临时目录/共享引用测试 |
| 富文本覆盖 | 新保存UUID独立路径，每次记录拥有独立内容；事务失败清新文件 | 两次保存/删除互不影响测试 |
| 默认提醒漏发 | 从收藏者/责任人生成候选，无须先有个人行 | 首次收藏测试 |
| 取消收藏仍提醒 | 每次重查收藏关系，仅历史个人行不再构成资格；仍为责任人保留责任提醒 | 取消收藏测试 |
| 每天提醒 | 只精确提前N天及当天 | 连续日期测试 |
| 失败不重试 | 节点日志条件领取，最多3次发送；节点独立事务，事务失败亦有界重试 | 首失败后成功/始终失败/已发送重复节点测试 |
| 责任人重复通知 | 创建通知一次，编辑仅新增者 | A变A+B：A零、B一测试 |
| 前端崩溃接手 | 保留图表/表格及Pinia统一状态，修拖动清流程、未声明变量、异步绑定丢失、顺序回显、兜底来源/计数、部分失败重试；加载和保存时禁关闭 | 组件脚本行为测试与生产构建 |

## 4. 文件变更
- 后端：ProjectController、ProjectService、ProjectStorageService、SupplierService、ComplianceService、ComplianceDueScheduler、ComplianceReminderLog。
- 前端：承接package/lock、ProjectConfig、stores/projectConfig、project/components九组件；新增tests/project-config-regression.mjs。
- 测试：ProjectStorageRegressionTest、ProjectAccessRegressionTest、SupplierDeletionRegressionTest、ComplianceAssignmentRegressionTest、ComplianceReminderRegressionTest。
- 文档：architecture、deployment、接口测试说明、本记录、最新交接摘要。前置技能初始化和审查记录也保留。
- 数据库：无DDL迁移，无实际数据库操作。已有VARCHAR(16)容纳SENDING运行态，需部署前核实实际元数据。
- API：时间线URL不变，增加资源拒绝；供应商有引用时拒删；列表增加missing兼容字段；其他接口URL不变。
- 依赖：承接@vue-flow/core和@vue-flow/background；无新增后端依赖/配置/端口。

## 5. 验证记录
- 富文本首次回归：2项失败，分别复现固定文件覆盖与旧富文本路径无法解析；修复后通过。
- 中间调试：调度测试缺少MyBatis lambda元数据初始化、责任人测试遗漏progress mock曾失败，均修正fixture；未削弱断言。
- backend：mvn '-Dtest=*RegressionTest,AuthServiceTest' test，最终24 tests，0 failures/errors/skipped，BUILD SUCCESS。
- frontend：node tests/project-config-regression.mjs，通过阶段排序不丢节点、异步绑定、排序回显、回第二步保留编辑、拖拽结束、空流程兜底影响计数及来源。
- frontend：npm run build -- --outDir dist-review-fixes，通过2388模块，约47.71秒；存在大包警告。
- git diff --check通过，仅CRLF提示。
- 曾在根目录误运行npm/mvn，因无package/pom失败，随后在正确子目录重跑成功。
- 未执行：连接真实MySQL的事务/并发集成测试、真实浏览器端到端、全套Spring上下文测试、部署健康/业务验收。Mockito事务管理器不证明真实数据库原子性；需按接口测试说明补隔离验证。

## 6. 复审与风险
- code-review独立复审发现全批事务会回滚此前成功节点，已改每节点REQUIRES_NEW隔离；图形兜底空数组计数遗漏已补且测试通过。
- 通知及日志依赖同一MySQL事务数据源；任务硬崩溃后的跨日期补发不是本轮已验证能力。持续数据库不可用到3次事务失败只记录告警，需运维按范围评估补发，不无限重试。
- 旧数据可能仍有历史共享行和缺档供应商，兼容读取/移出但未自动清理；被历史覆盖的旧内容无法恢复。
- 新增类型多请求不是全局事务；保留已创建ID支持当前弹窗重试；关闭后可从已创建类型继续编辑。
- 图形化页面通过构建和组件脚本测试，真实浏览器交互待验收。大包优化不在本轮范围。

## 7. 下一步、回滚与禁止事项
1. 接手先检查分支、最新提交、git status，再读本记录及接口测试说明。
2. 在隔离MySQL及浏览器按 docs/20261103-审查修复接口与测试说明.md 执行权限、并发、事务和页面清单。
3. 验收通过后由负责人另行批准蓝绿/原子发布；本记录不构成发布授权。
4. 回滚代码及锁文件必须按本轮提交整体回退，先备份配置和项目资料库；不删除新UUID文件或还原生产数据库。回滚会重现旧缺陷。
5. 禁止擅自批量删历史记录/文件、重放生产调度或覆盖其他Agent后续修改。

## 8. 文档同步
架构、部署、接口/测试、交接摘要已同步；无DDL故不新增迁移；无生产发布记录。实际提交号以本任务提交后的git log为准。
