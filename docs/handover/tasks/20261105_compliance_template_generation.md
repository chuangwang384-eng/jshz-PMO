# Agent任务交接：企业合规文件生成V4模板草稿

- 任务编号：20261105_compliance_template_generation；主责：Codex；交接：2026-11-05；状态：进行中，隔离技术验收通过，生产尚未发布。
- 基线：codex/personal-dashboard-v11 / 3e8bb1c；工作区有本任务与此前任务未提交内容，未覆盖或重置。
- 目标：结构化提取、扫描页提示、本地OCR、原文引用与V4待确认草稿；由人工核对并发布。

## 实际改动
- KnowledgeDocTextExtractor：保留页/段/表格行锚点，短文本PDF页尝试本机Tesseract简体中文+英文OCR，失败保留漏提取提示。
- ComplianceV4Controller / complianceV4.js / ComplianceTemplateEditor.vue：已上架企业合规文档生成草稿接口和页面引用；上传者/维护人可生成，维护人发布。复用现有source_anchor列，无本功能生产迁移。
- scripts/run-compliance-v4-e2e.ps1：同一控制进程新建隔离datadir、生成临时数据库/测试账号/JWT凭据，经子进程环境传递；结束清理候选服务和环境凭据。
- scripts/test-compliance-v4.ps1及ComplianceV4AcceptanceIT：补齐项目配置快照迁移、有效项目快照夹具和独立浏览器夹具恢复。
- frontend/tests/compliance-v4-live.mjs：真实PDF上传、生成草稿、原文引用及重复生成复用；保留登录、项目关联、私人数据、跨用户拒绝、完成/再次使用/历史测试。

## 验证证据
- 实际执行 scripts/run-compliance-v4-e2e.ps1，最终退出0；迁移重复执行、七组Maven数据库/服务测试、浏览器夹具恢复均通过。
- 真实Edge → Vite5173 → 候选Spring18086 → 隔离MySQL13316/acceptance_v4，无API拦截。上传基金会真实PDF后页面显示页码和摘录，所有候选sourceAnchor含锚点/摘录；重复生成仅一个草稿。
- 最终候选包：.build-compliance-v4-acceptance/candidate-20261105-094409/backend/target/expert-system.jar。
- npm run build -- --outDir <LOCAL_PATH> 成功；有现存大chunk告警。组合命令后续报告路径错误不影响已完成构建，已从仓库根目录重查报告及制品。
- git diff --check通过（仅换行风格警告）；结束后13316/18086/5173无监听。截图：.build-compliance-v4-acceptance/evidence/live-browser.png。
- 前期失败：Vite启动方式、项目快照缺失/迁移缺失、测试选择器歧义均修复并完整重跑通过。后续浏览器夹具测试会覆盖同名IT的Surefire报告，不能把最终单项报告当作全部测试计数。

## 发布前置与剩余风险
- 当前终端非管理员，既定GracefulSwitch和前端Apply脚本要求管理员；本轮未执行生产切流。
- 正式后端为LocalSystem，未配置TESSDATA_DIR；中文模型仅在交互用户LocalAppData。服务默认查找路径没有模型，须配置服务可读的明确路径并验证，不能将交互用户OCR成功视为服务OCR成功。
- 常规重复生成通过；并发生成仍有竞争窗口，暂无数据库唯一约束。更新文档版本绑定、扫描漏提取异常提示的完整端到端覆盖未完成；解析质量仍须人工核对，不能认作业务验收。
- 回滚：保持当前生产入口；上线前保存当前完整可启动JAR、Nginx配置及前端root，使用既有蓝绿/不可变前端root回退，不删除业务数据。

## 文档与下一步
- 已同步architecture.md、deployment-compliance-v4.md、deployment.md和最新交接摘要。
- 下一位先核实分支/差异、生产服务环境和目标元数据；补并发生成护栏及失败场景，再在管理员环境配置服务OCR模型，复验后使用deploy-backend.ps1 -GracefulSwitch及deploy-frontend-graceful.ps1发布，记录切流连续性与回滚基线。
- 用户此前已授权验收通过后蓝绿发布；不要重复索要相同授权。不得绕过管理员要求、改认证权限或运行生产破坏性SQL。

## 2026-11-05 并发护栏与生产发布尝试
- generate-draft增加READ_COMMITTED事务，按用户→文档顺序锁行，复用检查和创建/填充草稿原子提交；真实数据库与Edge重跑退出0，新增并发生成返回同一草稿断言通过。锁覆盖OCR过程，长文件可能等待。
- 管理员执行器已运行：备份并Verify通过，备份位于backup/backup_20261105_101009；中英文模型复制到<LOCAL_PATH> TESSDATA_DIR已配置，list-langs确认chi_sim/eng。正式服务尚未完成重启切换，不能认作服务OCR已验收。
- 发布失败：SYSTEM计划任务Nginx平滑重载查询ObjectNotFound并超时；release-state.json为FAILED。后端8080仍原PID8376，候选8081 PID30288保留且调度关闭；前端回退配置root指向本次.rollback目录，proxy_pass均8080。首页、代理健康、8080健康均200；探针已观察两路各超过1260请求零失败。未完成认证发布后验证，未宣称上线。
- 发布编排已补后端脚本退出码门禁，防止后端失败后继续前端切流。后续须先诊断SYSTEM任务注册/查询失败并核实运行中的Nginx配置；不要重跑已有发布ID，不要停候选或改入口直到确认运行流量。证据：.build-compliance-template-production-20261105/。

## 2026-11-05 平滑重载故障诊断
管理员重试捕获明确根因：Register-ScheduledTask返回HRESULT 0x80070005 PermissionDenied。此前ObjectNotFound是注册失败后继续查询引起的次生错误。invoke-nginx-graceful.ps1已显式TaskPath根路径、使用注册返回对象启动/查询，并在注册/启动/查询处强制ErrorAction Stop；新复验已正确停在注册拒绝，尚未成功重载。未改任务计划程序ACL、认证、安全策略或改用重启Nginx。待系统管理员恢复该身份注册SYSTEM任务的权限后，须先原配置平滑重载验证，再用全新发布ID继续蓝绿。证据reload-diagnostic.log；本次无进一步切流。

2026-11-05根因复核与修复：管理员令牌、任务根目录ACL正常；同身份使用Task Scheduler原生COM接口注册SYSTEM探针任务成功并清理。故障局限于ScheduledTasks/CIM调用路径，无需修改ACL或系统安全策略。invoke-nginx-graceful.ps1改为原生COM注册/运行/查询/清理，实测重载成功，Nginx master39504保持，旧worker排空；随后以新发布ID 1.0.24-compliance-template-20261105-r2继续蓝绿，独立证据目录.build-compliance-template-production-20261105-r2。

## 最终生产状态（2026-11-05 10:24）
新版本已切流，PUBLISHED_WITH_WARNING；并非未部署。平滑重载已修复为原生COM任务接口，无需改ACL。生产登录态/只读接口/首页资源通过；切流fresh探针2次ECONNRESET，严格不停机门禁失败；后续12秒稳态两路各108次零失败。保留新健康版本，业务验收待复核，详见docs/releases/20261105-compliance-template-production.md。原始FAILED门禁状态不覆盖。代码未提交；并发锁覆盖OCR可能造成长等待，生产OCR业务端到端未执行。
