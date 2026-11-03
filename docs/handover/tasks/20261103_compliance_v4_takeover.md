# 企业合规 v4 页面与编辑链路接手

## 1. 任务身份与范围
- 编号：20261103_compliance_v4_takeover；主责：Codex；日期：2026-11-03（北京时间）。
- 状态：页面与编辑链路已完成待验收；v4 整体不具备发布验收结论。
- 用户授权：补齐页面和编辑链路，再完成回归检验。本轮不执行生产发布、DDL、数据搬移或公共认证/权限规则变更。
- [需求](../../20261103-企业合规可复用清单-需求文档.md)：`<LOCAL_PATH>`。
- [技术设计](../../20261103-企业合规可复用清单-技术设计.md)：`<LOCAL_PATH>`。
- 原设计决策 D02–D08 的专项审批证据不在本聊天中；不能把另一个 Agent 已写代码当成这些规则已获业务批准。

## 2. 接手基线
- 分支：`codex/fix-review-and-config-takeover`；接手提交：`4ccc89d`。
- 前任已经提交 `ea2e091` 后端、`d199f52` 七表迁移、`7d8e716` 前端及三个后续修复。
- 接手时存在 `docs/deployment.md` 未提交修改及 `.bdv4b.txt`、`tv.txt` 未跟踪文件。均保留，本轮提交不包含它们。
- 栈：Java 17/Spring Boot 3.2.5/MyBatis-Plus；Vue 3/Vite 5。未重新核实生产数据库、运行进程或实际部署版本。
- 前任遗留四条 R-1 发布成功文字（17:07–17:19）不能作为上线验证证据。本轮未据此认定生产成功。

## 3. 复现与原因
- `EnterpriseCompliance.vue` 使用未定义 `v4Ready`、未导入 `ExecutionBoard`；重复旧维护板和文档库；旧收藏自动待办说明与 v4 冲突。
- `ExecutionBoard` 详情仅弹占位提示；项目选择调用人员接口；创建自动启动与草稿文案矛盾；完成记录重开没有刷新；等待使用错误字段且不带版本；重试会生成新幂等键。
- 模板维护接入旧 `MaintenanceBoard`，没有 v4 模板细项编辑。
- 新增服务层测试先复现超末页 `fromIndex > toIndex`、必填多选空集合可以完成；补充 MyBatis 元数据初始化后确认两项失败，再修复。
- 浏览器测试初次有测试夹具路径/Element Plus 隐藏 input 定位问题，已修正测试；不把测试脚本问题记作产品缺陷。

## 4. 实际变更
| 文件 | 作用 |
| --- | --- |
| `frontend/src/views/EnterpriseCompliance.vue` | 三页签接入真实 v4 组件，移除重复旧界面和无效旧弹窗 |
| `frontend/src/components/compliance/ComplianceTemplateEditor.vue` | 文档选择、草稿、复制修订、单/多选细项及要求/材料/标准/原文定位编辑、保存、维护人发布入口 |
| `frontend/src/components/compliance/ExecutionBoard.vue` | 个人答案/日期/优先级/备注编辑；版本参数；完成收纳/重开刷新；分页；等待设置/清除；草稿详情/显式开始/再次使用；失败保留输入与幂等键 |
| `backend/.../service/ComplianceV4Service.java` | 超末页安全分页、分页参数和状态校验、必填选择校验、任务更新原子版本条件、三天内排序边界 |
| `backend/.../service/ComplianceV4EditingRegressionTest.java` | 7 项 v4 编辑与边界回归 |
| `frontend/tests/compliance-v4-browser.mjs` | Edge 隔离 API 页面流程回归 |

没有新增依赖、迁移脚本、路由或公共权限策略；没有读取真实君实附件，所有测试均为自建示例。

## 5. 验收条件与真实证据
| 项目 | 结果 | 证据 |
| --- | --- | --- |
| 页面加载和任务详情 | 通过（API 夹具） | Edge 无 pageerror，真实 Vue 页面 |
| 结构化单选/私人备注保存回显 | 通过（API 夹具） | 断言 PATCH 答案、备注、rowVersion |
| 保存冲突保留输入 | 通过（API 夹具） | 注入业务 409 后弹窗不关闭，输入保持，再次保存成功 |
| 等待设置/清除 | 通过（API 夹具） | 等待摘要回显、PATCH 带版本、显式 null 清除 |
| 完成隐藏/记录查看/重开隐藏 | 通过（API 夹具） | 实际点击表格复选框和页签 |
| 创建草稿、显式开始、再次使用 | 通过（API 夹具） | 创建不自动 start；复用默认不带答案，独立幂等键及草稿 |
| 模板细项保存 | 通过（API 夹具） | 单选及候选项具体要求可保存，PATCH 包含规范细项且无个人 dueDate |
| 服务层正常/拒绝/边界 | 7 项通过 | 必填多选、陈旧版本、原子更新冲突、清空日期备注、超末页、非法分页、非本人拒绝 |
| 原有缺陷回归 | 15 项通过 | 项目读取 4、富文本 2、供应商 1、责任人通知 1、旧合规提醒 7 |
| 项目配置旧页面浏览器回归 | 通过（API 夹具） | 图形编辑/异步绑定/保存/表图切换，无 pageerror |
| 前端构建 | 通过 | `npm run build -- --outDir .build-compliance-v4-takeover`；保留已有大 chunk 警告 |
| 后端打包 | 独立目录通过 | 原 `backend/target` repackage 改名失败；保留原目录，复制当前 `pom.xml/src` 到 `.build-compliance-v4-backend-20261103-1845` 后执行 `mvn -q -DskipTests package`，退出 0，生成约 60 MB JAR；构建跳过测试，测试已由上列独立命令执行 |
| 真实隔离 MySQL / 真实后端 E2E / 生产验收 | 未执行 | 不能以 mock API 或 Mockito 代替事务/调度/部署验收 |

后端命令（PowerShell）：
```powershell
cd <LOCAL_PATH>
mvn -q '-Dtest=ComplianceV4EditingRegressionTest,ProjectAccessRegressionTest,ProjectStorageRegressionTest,SupplierDeletionRegressionTest,ComplianceAssignmentRegressionTest,ComplianceReminderRegressionTest' test
```
XML 证据在 `backend/target/surefire-reports/TEST-*.xml`，本次合计 22 项，0 失败/错误/跳过；警告中的提醒异常来自故意注入失败重试的测试。

浏览器复现（先在独立终端启动 Vite，仅监听本机）：
```powershell
cd <LOCAL_PATH>
npm run dev -- --host 127.0.0.1 --port 15173 --strictPort
# 另一个终端；PLAYWRIGHT_MODULE 可指向本机安装的 Playwright file:///.../index.mjs
node tests/compliance-v4-browser.mjs
node tests/project-config-browser.mjs
```
浏览器脚本拦截 `/api/`，不向生产发送业务请求。夹具不覆盖 Vite 的 `/src/api/` 模块路径。

## 6. API/数据/权限影响
- 继续使用已有 `/api/compliance/v4/*`，不改路径。任务 PATCH 的 `answer/dueDate/priority/progress/note/waiting/rowVersion` 为本轮界面实际使用字段。
- `dueDate/note/answer/waiting` 显式 null 清空；missing 不修改。更新以数据库 `id + row_version` 匹配，0 行返回业务 409，事务应回滚此前事件/提醒写入（真实 MySQL 尚未实测）。
- 列表 `page >= 1`、`1 <= size <= 200`；任务范围仅 ACTIVE/COMPLETED；超末页为空集合。
- 继承的七张新表及迁移文件仍需实际元数据核实；本轮未执行 SQL，不宣称七表已部署。

## 7. 未完成范围与发布阻断
以下是继承实现相对原全量设计的缺口，不能把本次页面验收当作全量验收：
1. **权限/来源可用性**：`ownedExecution/myTasks/templateDetail/createDraft` 尚未完整实现设计中的来源撤权、项目撤权复核、上传人对象权限；涉及安全规则，须专项批准后修复与越权集成验收。
2. **v4 提醒**：调度 `SENDING` 非原子认领，通知与 SENT 不在单节点事务中；多实例并发/失败恢复尚不能认为可靠。等待复查节点没有完整创建链路，个人提醒开关/提前天数缺少完整 PATCH/UI 支持。
3. **历史兼容**：新页签替换旧待办后，历史进度尚无完整只读入口/用户灰度互斥与迁移对账；旧调度仍存在，不能直接全员切换。
4. **模板与体验**：草稿暂靠版本编号恢复（页面显示编号），未提供完整草稿列表；规范分支、版本更新提示、个人事件历史、任务消息深链、暂停恢复和归档 UI 尚未闭环。文档/项目选择暂取前 100 条，规模化需搜索分页。
5. 七表真实迁移演练、端到端权限/并发/提醒/事务回滚、业务决策 D02–D08、生产版本与遗留 R-1 发布记录均待核实。

## 8. 风险与回滚
- 本轮没有发布，不改 Nginx、服务进程、生产配置或数据库。
- 构建产物仅在忽略目录中，不覆盖发布目录；本轮启动的 Vite 15173 在验证结束后停止。
- 回滚本轮专属代码/文档提交即可撤销页面与编辑修复；不能将其当成整体 v4 或生产数据回滚方案。
- 不删除七张表：已有业务数据时 DROP 会丢失数据，前任迁移文件的简单 DROP 注释不能直接用于生产。
- 对前任遗留修改保持原状；构建目录被现有 `.build-*/` 忽略规则覆盖，不提交。

## 9. 文档同步
- 本记录、最新交接摘要、架构说明、原设计交接补当前实施入口；需求/技术文档保留为设计规格并新增施工状态链接。
- 本轮 API 变化与测试说明集中在本记录第 5/6 节；没有 DB/部署方式变更。
- `docs/deployment.md` 的未提交 R-1 文字保留且未合入本轮提交，必须核实后再决定发布记录。

## 10. 下一位 Agent 第一项操作
1. `git status --short`、`git log -5 --oneline`，核对本记录与实际文件；保留上述前任遗留三项。
2. 先对第 7 节建立发布阻断清单，确认 D02–D08 与专项安全授权，然后在隔离 MySQL 完成权限/调度/旧数据兼容闭环。
3. 按上述命令重现页面测试；真实后端 E2E 必须另测，不用夹具结果替代。
4. 未经专项批准禁止执行生产 DDL、修权限策略、搬迁历史数据、重启或部署。
