# 仓库地图（Repo Map）

> 面向新接手的 Agent/开发者的快速导航。数量类基线**均于 2026-11-17 实测**（AGENTS.md §0.5：易变信息必须现查，本文基线过期时以现查为准）。
> 规则约束见根目录 [AGENTS.md](../AGENTS.md)（文档同步铁律/样式令牌/硬编码禁令/复用规范等 19 节）；Agent 约定见 [docs/agents/](agents/)。

## 1. 这是什么系统

公司内部管理系统（景帅会展 PMO）：专家档案库、项目全流程（预算→报价→日程→合规→执行）、供应商、知识库、合规记事本、AI 问答/助手、企微集成。**本机即生产服务器**（Nginx + NSSM 后端 + MySQL），发布必须走脚本三件套，禁止手工改生产。

- 技术栈：Spring Boot 3.2（Java 17 + MyBatis-Plus）· Vue 3 + Element Plus + Vite · MySQL · 本地 Ollama(qwen2.5:3b) / PP-OCRv5 / rembg
- 版本唯一真源：`version.txt`（当前 1.0.49，须与 `frontend/package.json` 一致）
- 端口：后端 8080（NSSM `ExpertSystemBackend`）、前端 Nginx 80、Ollama 11434（NSSM `ExpertSystemOllama`，有看门狗）

## 2. 目录总览

| 路径 | 内容 | 说明 |
| --- | --- | --- |
| `backend/src/main/java/com/jshz/expert/` | 后端唯一包根 | Controller 32 / Service 59 / Entity+Mapper 68 |
| `frontend/src/` | Vue 前端 | views 45 页 / components 20 / api 19 模块 |
| `database/init.sql` | 新环境唯一 DDL 来源（85 业务表） | `migration/*.sql`(64) 仅既有库增量，新环境不重放 |
| `scripts/` | 构建/部署/迁移/巡检（48 个 ps1） | 公共库：common-paths / common-credentials / build-paths |
| `docs/` | architecture / deployment / handover / releases / decisions / agents | 文档同步铁律见 AGENTS.md §0 |
| `builds/`、`frontend/releases/` | 构建产物与前端发布快照 | 切换靠 Nginx root 指针，禁止原地替换 dist |
| `logs/`、`temp/` | 日志与临时文件（gitignored） | 禁止散落（AGENTS.md §0.4） |
| 根目录中文目录（项目资料库/照片库等） | 业务文件存储区 | 路径经 `STORAGE_ROOT` 出口化 |

## 3. 后端模块地图（按业务域分组）

| 业务域 | Controller（代表） | 关联 Service / 表 |
| --- | --- | --- |
| 认证与权限 | AuthController、UserController、RoleController、PermissionController | security/（JWT 过滤器 + DB 权限）、wecom 通讯录登录 |
| 项目流程（核心） | ProjectController（含 GET /api/project/calendar 项目日历）、ProjectBudgetController、ProjectBudgetQuoteController、ProjectConfigurationController | ProjectService（阶段推进/待办校验 `missingRequiredTemplates`）、ProjectCalendarService（日历/部门排期）、ProjectTeamSyncService（team 派生）、project / project_phase / project_doc / project_schedule / project_speaker / project_budget* |
| 专家库 | ExpertController | Expert*、简历解析 + rembg 抠图 + Ollama 语义抽取 |
| 供应商 | SupplierController | supplier_*（飞书风格 UI 为受保护色系） |
| 知识库/星球 | KnowledgeController、CustomerController | knowledge_*、客户信息、Excel 预览（ExcelPreviewService，被项目文件预览复用） |
| 合规 | ComplianceItemController、ComplianceNotebookController、ComplianceV4Controller | compliance_*、PP-OCRv5/LLM 解析链 |
| 工作台/搜索 | DashboardController、DashboardFocusController、GlobalSearchController | `--c-*` 专属色系边界（AGENTS.md §17.2） |
| AI 助手/公告/审批/消息 | AiAssistantController、AnnouncementController、ApplicationController、NotificationController、AuditController | ai_assistant_* 等 |
| 运维接口 | HealthController、MetaController、StorageAdminController、UploadController、ExportCompatController | 上传限额单一真源 `expert.upload` + `GET /api/meta/limits` |

横切层：`common/`（FileNames/UploadGuard/PpOcrRuntime/BusinessException——复用规范 §19 唯一登记处）、`config/SecurityConfig`（CORS 白名单出口 `security.cors.allowed-origins`）、`scheduler|task|aspect|client`。

## 4. 前端模块地图

| 路径 | 内容 |
| --- | --- |
| `views/project/` | 项目全流程：ProjectList（列表/日历切换，ProjectCalendar 月历 + ProjectDeptSchedule 部门排期，utils/projectCalendar.js）/ ProjectDetail（阶段视图大页）/ ProjectEdit / ProjectConfig + components/（预算估算、报价、流程图、StepWizard、budgetEstimate.js 及其 node --test） |
| `views/expert*`、`views/supplier/`、`views/admin/`、`views/dashboard/` | 专家三视图改造版、供应商、管理页、工作台可调布局 |
| `api/`（19 模块） | 与后端 Controller 一一对应；request.js 统一 baseURL/拦截 |
| `components/KnowledgeWorkbookPreview.vue` | Excel 表格预览共用组件（fetcher 可注入，知识星球与项目文件共用） |
| `utils/` | 共享纯函数/字典唯一处（dict/format/limits/supplier/dashboard*，§19.2 现役清单） |
| `styles/ui-tokens.css` | **全局样式唯一入口**（--ui-* 令牌与 ui- 公共类，§17.6 清单） |
| `stores/`（Pinia） | auth（会话）、projectConfig |

## 5. 关键链路（改动的常见入口）

1. **项目阶段推进**：`ProjectService.changePhase` → `missingRequiredTemplates`（预算=报价文件已生成；日程=上传日程+专家全匹配）→ `project_phase_history`。前端 ProjectDetail 待办卡 `resolveLabel/resolveMissing` **成对修改**（§17.7）。
2. **文件预览**：xls/xlsx → `GET /project/files/{name}/workbook` + KnowledgeWorkbookPreview；PDF/图片/Office → DocPreviewService（LibreOffice）+ pdf.js。
3. **发布三件套**：`build-all.ps1` → `deploy-backend.ps1 -GracefulSwitch` → `deploy-frontend-graceful.ps1 -Apply`；无迁移可拆开。回滚靠 release-metadata 的 rollback.conf。
4. **AI 本地链**：Ollama 11434（`OllamaHealthWatch` 计划任务每 5 分钟探测，60s 超时自动重启）；OCR=E 盘 pp-ocrv5 venv 优先。

## 6. 新 Agent 最小路径

1. 读 AGENTS.md + 本文件 + `docs/handover/README.md`（最新交接）
2. `git log --oneline -5` + `curl 127.0.0.1:8080/health` 核对基线
3. 改动前跑 `scripts/check-schema-parity.ps1`（涉及 DB 时）
4. 完成后按 AGENTS.md §11 写交接记录、§13 汇报
