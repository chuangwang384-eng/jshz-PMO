# Standards 轴审查报告

- 任务：20261103_code_review；主责子 Agent：standards_review；日期：2026-11-03。
- 基线：提交 8880324a2d358fa068c316feca314a802193bcbb，独立只读快照 <LOCAL_PATH>
- 范围：本次用户要求全项目核查，按全量快照检查，偏离 code-review 默认的差异审查范围。未审查共享目录未提交代码。
- 方法：阅读 AGENTS.md、README、architecture/deployment；rg 引用检索、Get-Content 调用链阅读。未运行测试、构建或服务；未连接数据库、生产 API 或外部业务服务。以下为静态证据，不代表已实测复现；实际数据库约束和部署配置未验证。

## 1. [P1] 项目时间线绕过行级读取权限
- 位置：backend/src/main/java/com/jshz/expert/controller/ProjectController.java:116；backend/src/main/java/com/jshz/expert/service/ProjectService.java:456。
- 证据：Controller 不接收 AuthUser，直接调用 timeline(id)；Service 直接查询 target_type=PROJECT/target_id，无 requireProjectReadable。其他读取接口先调用 requireProjectReadable（1601），检查 project:view-all 或负责人/执行人。
- 触发：非项目负责人/执行人且无全量查看权限的已登录用户请求 GET /api/project/{他人项目ID}/timeline。
- 影响：读取他人项目操作日志、项目名称、操作人、关联专家/供应商等上下文。数据库规则即使限制普通权限，也不能取代该接口缺失的项目级授权。
- 标准：AGENTS.md §4.10/§5.1/§5.2，资源权限应由后端校验；docs/2026-10-28-项目权限变更设计.md:10 约定普通员工只见本人负责/执行项目。
- 建议验证：隔离测试中验证非成员403、成员200、全量查看用户200。

## 2. [P2] 删除供应商后项目名单、预算与移出逻辑不一致
- 位置：service/SupplierService.java:283；service/ProjectService.java:593、649、782（目录均 backend/src/main/java/com/jshz/expert/）。
- 证据：deleteSupplier 物理删除供应商及行价/评价/酒店打印关联，未检查/处理 project_supplier；listSuppliers 遇不存在供应商直接跳过；budgetOf 从 project_supplier 累计费用；removeSupplier 删除关联后用 supplierMapper.selectById(supplierId).getName() 写日志，可空指针并使事务回滚。
- 触发：某供应商已关联项目并有费用，再删除供应商。仓库建表迁移 20261027_add_project_module.sql 的关联表无外键；实际库是否另有约束/触发器未核查。
- 影响：名单消失、预算残留；移出失败。需业务决定禁止删除被引用供应商、软删保留历史，或以快照保存项目历史，不能擅自级联丢弃业务费用。
- 标准：AGENTS.md §3.2、§4.4、§7.2（跨模块查询/数据一致性及失败恢复）。

## 3. [P2] 富文本资料删除未删除其真实文件
- 位置：backend/src/main/java/com/jshz/expert/service/ProjectService.java:719；ProjectStorageService.java:99、114。
- 证据：deleteDoc 对 _richtext/ 路径调用 deleteFile；deleteFile 统一调用 resolveFile；resolveFile 取 getFileName 后拼到存储根目录，丢弃 _richtext 子目录；实际 saveRichText 在 _richtext 下写入。因此常规情况下解析不到文件，静默不删。
- 触发：删除已存在的阶段富文本资料。
- 影响：软删成功但原内容文件残留，违反该方法“富文本同时清理物理文件”的实现契约。该内容是否允许物理清理还须结合重复记录策略确认（见下一项）。
- 建议：区分附件与富文本的安全路径解析，同时测试正常删除、越界路径和共享文件引用。

## 4. [P2] 富文本反复保存产生多条记录共用同一文件
- 位置：backend/src/main/java/com/jshz/expert/service/ProjectService.java:742-751；ProjectStorageService.java:66-70。
- 证据：每次 saveRichText 固定写 proj{projectId}_p{phaseId}_t{templateId}.json，并无条件 insert 新 project_doc；listDocs:659-671 返回全部未删记录且按该路径读取。
- 触发：同一项目/阶段/模板保存两次以上。
- 影响：数据库重复增长；旧记录读取到最新内容，并非历史版本。若修复第3项后直接删除其中一条，将删除其他未删记录共用文件，造成现有内容丢失。两项必须一起设计修复。
- 建议：明确该表记录代表当前内容还是版本历史；当前内容使用唯一键/upsert，历史则每版独立路径。不得未经审核直接清理旧行。

## 无效代码与架构启发式
- 未确认可安全删除的业务代码。ProjectEdit.vue 已由 ProjectList/ProjectDetail 引用，无独立路由并不等于无效。
- 会议执行与协会合规页面通过 KnowledgeDocList 复用，有效；不能按薄包装直接判 Middle Man 并删除。
- AuthController 的企微 OAuth 是明确标识的二期占位，属于未实现能力，应结合计划决定保留；不能当成已实现功能。
- 未将 Fowler smells 当硬性缺陷；优先报告可触发的行为问题。

## 后续与交接
- 状态：静态审查已完成待主责整合；4项发现，最严重为时间线资源越权风险。
- 实际变更：仅本报告。无业务/数据库/API/配置变更，无迁移，无部署。回滚只涉及移除本报告，不应撤销共享目录其他工作。
- 下一位第一步：在固定快照核对上述路径，使用隔离测试 fixture 验证时间线权限与供应商删除链路；富文本两项联合评估。
- 未经负责人批准禁止修改认证/权限、删除生产数据或发布。本文不构成上线验收结论。
