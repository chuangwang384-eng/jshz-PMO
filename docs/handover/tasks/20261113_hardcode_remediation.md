# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261113_hardcode_remediation
- 任务名称：硬编码治理四项改造（方案 B/D/A/E）
- 主责 Agent：CodeBuddy（Auto）
- 交接时间：2026-11-13
- 当前状态：已完成待验收
- 优先级：P1（方案 B）/ P2（D、A）/ P3（E）

## 2. 目标与验收条件
- 业务目标：按 2026-11-13 硬编码审查报告，将环境路径、上传限额、凭据解析、状态字典四类硬编码问题改造为可配置/单一真源，配置与业务逻辑解耦。
- 本次范围：
  - 方案 B（最高优先级）：application.yml 存储目录/LibreOffice 路径出口化、CORS 白名单出口化、PhotoMatte E 盘路径出口化、OllamaService 默认值收敛；
  - 方案 D：上传限额前后端单一真源（application.yml expert.upload + GET /api/meta/limits + 前端 utils/limits.js，替换 5 处字面量）；
  - 方案 A：凭据解析收敛到 scripts/common-credentials.ps1（9 个脚本）；
  - 方案 E：AGENTS.md 新增 17.7 状态字典联动约定（流程约束，无代码改动）。
- 不在本次范围：方案 C（44 个 ps1 的 $root 收敛，审查报告定级 P3 可延后，未获本次授权包含）；`logback-spring.xml` 的 LOG_DIR（原本已可覆盖）；Tesseract ProgramFiles 兜底（已环境变量优先）。
- 验收条件：
  - [x] 后端编译通过（mvn compile，按构建约定注入 -Drevision/-DgitShort）
  - [x] 前端构建通过（npm run build）
  - [x] 9 个改动脚本 PowerShell 语法解析通过
  - [x] 公共凭据库功能测试通过（bat 解析 + 环境变量优先级，临时目录假密码实测）
  - [x] 残留 grep 确认：后端 main 源码无裸路径（剩余均为 yml 默认值/注释）
  - [ ] 运行时验证（启动后端核对 CORS/limits 接口、存储目录注入覆盖）——需发布窗口，未执行

## 3. 项目基线
- 分支：codex/personal-dashboard-v11
- 当前提交号：31957bf（与本日样式治理任务共用基线，改动均未提交）
- 工作区状态：改动前含 20261113_style_dedup 未提交改动（25 文件）；本任务新增改动见第 5 节，两组改动无文件交集
- 运行环境：本仓库工作副本，未发布（version.txt 未动）
- 以上信息核实时间：2026-11-13

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 证据或说明 |
| --- | --- | --- | --- |
| B1 存储目录/预览缓存/LibreOffice 路径出口化 | backend/src/main/resources/application.yml | 完成 | STORAGE_ROOT（7 目录统一根）+ LIBREOFFICE_PATH，默认值=原值，行为不变 |
| B2 CORS 白名单出口化 | SecurityConfig.java + application.yml（security.cors.allowed-origins） | 完成 | CORS_ALLOWED_ORIGINS 环境变量注入，逗号分隔解析、去空容错 |
| B3 PhotoMatte E 盘路径出口化 + 根目录解析去重 | PhotoMatteService.java | 完成 | PHOTOMATTE_LEGACY_ROOT 可覆盖（默认 E:/AI助手开发/pp-ocrv5，行为不变）；resolveRoot()/legacyRoot() 单一来源 |
| B3 Ollama 默认值收敛 | OllamaService.java | 完成 | DEFAULT_MODEL/DEFAULT_TIMEOUT_SEC/DEFAULT_KEEP_ALIVE 常量 + baseUrl()/modelOrDefault()/resolveTimeoutSeconds() 辅助方法，消除 4+2+4+1 处重复 |
| D 后端限额单一真源 + 接口 | application.yml expert.upload + MetaController.java（新增） | 完成 | GET /api/meta/limits，登录即可访问（anyRequest authenticated） |
| D 前端单一真源与替换 | frontend/src/utils/limits.js（新增）+ ExpertList×3 + BatchResumeDialog×1 + ProjectDetail×1 | 完成 | 响应式兜底默认值 + ensureUploadLimits() 一次性拉取（silent，失败不阻断） |
| A 凭据解析收敛 | scripts/common-credentials.ps1（新增）+ backup.ps1 + db-migrate.ps1 + 7 个 apply-migration-*.ps1 | 完成 | 环境变量优先 → bat 运行时解析；bat 路径改为相对 scripts/ 上级推导 |
| E 状态字典联动约定 | AGENTS.md 17.7 | 完成 | 成对修改 + 交接记录联动检查，暂不做字典中心 |
| 文档同步 | docs/deployment.md | 完成 | 环境变量注入点章节新增方案 B/D/A 说明 |

## 5. 实际变更
- 新增文件：
  - backend/src/main/java/com/jshz/expert/controller/MetaController.java
  - frontend/src/utils/limits.js
  - scripts/common-credentials.ps1（UTF-8 带 BOM，Windows PowerShell 5.1 兼容）
- 修改文件：
  - backend/src/main/resources/application.yml（STORAGE_ROOT/LIBREOFFICE_PATH/CORS/upload 三块）
  - backend/src/main/java/com/jshz/expert/config/SecurityConfig.java
  - backend/src/main/java/com/jshz/expert/service/PhotoMatteService.java
  - backend/src/main/java/com/jshz/expert/service/OllamaService.java
  - frontend/src/views/ExpertList.vue、components/BatchResumeDialog.vue、views/project/ProjectDetail.vue
  - scripts/backup.ps1、scripts/db-migrate.ps1、scripts/apply-migration-20261109-{project-rebuild,price-quote-date,supplier-review-open,schedule-stage}.ps1、scripts/apply-migration-20261110-compliance-notebook.ps1、scripts/apply-migration-20261112-{storage-orphan-scopes,storage-manage-permission}.ps1
  - docs/deployment.md、AGENTS.md（17.7）
- 删除文件：无（temp/ 三个临时脚本用后即删）
- 数据库/迁移：无
- API/接口契约：新增 GET /api/meta/limits（登录即可，返回 {photoMaxBytes,resumeMaxBytes,docMaxBytes}）；无接口修改/删除
- 页面/路由/权限：前端无新路由；后端无新权限码（/api/meta/** 落在"其余接口登录即可"）
- 配置/环境变量：新增可覆盖变量 STORAGE_ROOT、LIBREOFFICE_PATH、CORS_ALLOWED_ORIGINS、PHOTOMATTE_LEGACY_ROOT（全部有原值默认，不注入则行为与改前一致）
- 依赖变更：无

## 6. 测试与验证
| 验证项 | 执行命令/步骤 | 结果 | 证据/备注 |
| --- | --- | --- | --- |
| 后端编译 | mvn -q -DskipTests -Drevision=<version.txt> -DgitShort=<git short> compile | 通过 | EXIT=0；裸 mvn 会因缺 -DgitShort 在 build-info 失败，属既有约定（pom L22/L187），非本次引起 |
| 前端构建 | cd frontend && npm run build | 通过 | ✓ built in 30.61s（chunk 告警为既有问题） |
| 脚本语法 | PowerShell AST Parser::ParseFile 检查 10 个脚本 | 通过 | 9 OK；common-credentials.ps1 首次 FAIL（写文件无 BOM，PS5.1 按 ANSI 误读中文）→ 补 BOM 后 OK |
| 凭据库功能测试 | 临时目录 + 假密码 bat：dot-source → Use-DbPassword | 通过 | BAT-PARSE OK（bat 值解析含引号剥离）+ ENV-PRIORITY OK（环境变量优先）；测试目录已删除 |
| 残留路径 grep | backend/src/main 检索 <LOCAL_PATH> IP | 通过 | 剩余命中均为 yml 默认值、Java 注释、logback 既有可覆盖项 |
| IDE 诊断 | read_lints（后端 4 文件 + limits.js） | 通过 | 0 错误 |
| 运行时验证 | 启动后端核对 CORS/limits/存储覆盖 | 未执行 | 需重启生产服务（高风险操作，须负责人批准）；随下次发布窗口验证 |
| 单元测试 | 未新增（MetaController 建议后续补 1 个 MockMvc 用例） | 未执行 | 纯配置透传接口，风险低 |

## 7. 未完成事项与阻塞
| 事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 运行时验证（B/D 实际生效） | 需重启生产后端（高风险，须批准） | 出口化变量与 limits 接口未经真实进程核验 | 随下次后端发布窗口：注入验证 STORAGE_ROOT/CORS_ALLOWED_ORIGINS + curl /api/meta/limits | 项目负责人 |
| 方案 C（44 脚本 $root 收敛） | 审查报告定级 P3 且用户本次未点名（"可延后"层级按 E 处理） | 脚本仍各自硬编码 <LOCAL_PATH> | 后续运维任务顺带实施 | 后续任务 |
| MetaController 单测 | 优先级低 | 无 | 随下次后端测试补充 | 后续任务 |
| 前端 limits 拉取时机 | ensureUploadLimits 在 3 个组件 setup 调用；若用户未进入这三个页面则使用兜底默认值（与 yml 当前一致） | 后端改限额且前端兜底未刷新时预校验可能用旧值（仅提示口径，后端强校验不受影响） | 可选：移至路由守卫登录后统一拉取 | 后续任务 |

## 8. 风险与回滚
- 已知问题：
  - OllamaService 曾出现 replace_all 波及辅助方法自身的临时递归（baseUrl 调用自身），已当场发现并修复，编译验证通过；
  - common-credentials.ps1 必须保持 UTF-8 **带 BOM** 编码，否则 Windows PowerShell 5.1 按 ANSI 解析中文注释会报语法错误（已实测踩坑并修复）；
  - 7 个 apply-migration 脚本中 $startBat 变量保留未删（不再被引用，无害）；backup.ps1 同（$StartBat）。
- 数据风险：无（无数据库操作；凭据行为不变——解析优先级与原脚本一致）。
- 兼容性风险：
  - 不注入新环境变量时全部行为与改前一致（默认值=原值，已逐一核对）；
  - /api/meta/limits 为新增登录接口，不进 permission 表 → 登录即可，与现有 RBAC 约定一致；
  - 9 个脚本失败路径从"返回空/null"变为"抛异常带明确信息"（backup/db-migrate 调用处的空值守卫成为冗余但无害）。
- 回滚步骤：`git checkout -- backend/src frontend/src scripts docs AGENTS.md`（改动未提交，单命令回滚；注意会连带回滚同日样式治理改动，如需分离请按文件清单选择性 checkout）。
- 回滚限制：无数据库/接口契约牵连（meta/limits 为纯新增）。

## 9. 文档同步
- [x] 部署文档（deployment.md：环境变量注入点 + 备份凭据小节）
- [x] AGENTS.md（17.7 状态字典联动约定）
- [x] 最新交接摘要（docs/handover/README.md）
- [x] 任务交接记录（本文件）
- [ ] 架构文档（未更新：新增接口属前端元信息，量级小，待验收后统一补记）
- 未完成项及原因：架构文档待验收后随批次更新。

## 10. 下一位 Agent 的接手指令
1. 首先检查：`git status`（应见本任务 + 20261113_style_dedup 两组未提交改动）、`git log -1`（31957bf）。
2. 然后执行：`cd backend && mvn -q -DskipTests "-Drevision=<version.txt revision>" "-DgitShort=<git rev-parse --short HEAD>" compile`；`cd frontend && npm run build`。
3. 验收标准：两组任务文件清单与本记录第 5 节一致；运行时验收待发布窗口（注入 STORAGE_ROOT 变体目录启动 + curl /api/meta/limits + 登录后前端上传预校验提示）。
4. 未经确认禁止：不得在生产直接重启后端做运行时验证（高风险，须批准）；不得把 start-backend.bat 的真实密码写入任何入库文件或日志；不得为"统一"删除 PhotoMatte 的 PHOTOMATTE_LEGACY_ROOT 默认值（E 盘路径是当前运行时实测可用兜底，删除前须确认生产实际走 <LOCAL_PATH> Files\JSHZ\pp-ocrv5）。
5. 需要负责人决策：方案 C 是否排期；运行时验证的发布窗口。
