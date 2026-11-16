# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261113_reuse_consolidation
- 任务名称：功能复用审查落地（R1~R5）+ AGENTS.md 第 19 节复用规范
- 主责 Agent：CodeBuddy（Auto）
- 交接时间：2026-11-13
- 当前状态：已发布上线 1.0.38.24（2026-11-13 蓝绿发布 + 前端快照发布；发布过程含一次 jar 误选事故已修复，见第 6a 节）
- 优先级：P1（R1 补方案 D 缺口）/ P2（其余）

## 2. 目标与验收条件
- 业务目标：落实同日复用审查报告的可执行项（#1~#5），消除安全敏感与高频复制逻辑的重复实现，并把复用规范沉淀为 AGENTS.md 第 19 节；#6（外部进程模式）/#7（列表页加载模式）按报告结论列为不动/暂缓。
- 验收条件：
  - [x] R1：存储服务通用实现收敛 + 6 处 MAX_SIZE 字面量接入 expert.upload 单一真源
  - [x] R2：safeName/extension 9 处收敛到 common.FileNames
  - [x] R3：前端跨页面字典收敛（客户关系方向 4 处 → utils/dict.js）
  - [x] R4：12 个脚本 $root 收敛到 scripts/common-paths.ps1（方案 C 落地）
  - [x] R5：venv 布局探测统一到 common.PpOcrRuntime
  - [x] AGENTS.md 第 19 节（识别标准/封装调用/命名接口/依赖管理/维护流程）
  - [x] mvn compile + npm run build + 15 脚本 AST 解析 + 残留 grep 全部通过

## 3. 项目基线
- 分支：codex/personal-dashboard-v11；发布提交链 a8e913f（R1~R5 主体）→ 96f35d1（测试适配）→ c330168（凭据库 Resolve-JwtSecret）
- 工作区：已发布上线（deploy-backend.ps1 护栏修复与文档同步随后提交）
- 运行环境：生产已运行 1.0.38.24（jar 归档 backend/releases/1.0.38.24-*，回滚基线 1.0.38.23 见 deployment.md 事故记录节）
- 核实时间：2026-11-13

## 4. 已完成工作
| 内容 | 文件/模块 | 状态 | 说明 |
| --- | --- | --- | --- |
| R1 公共校验/目录归一化 | common/UploadGuard.java（新）、FileNames.normalizeDir | 完成 | 三段校验统一，大小文案随限额动态生成 |
| R1 限额接入真源 | Photo/KnowledgeDoc/CustomerAttachment 构造器 @Value；ScheduleParse（doc）、ResumeParse（resume）@Value 字段 | 完成 | 删除 6 处 MAX_SIZE 字面量；修复方案 D"后端内部脱节"缺口 |
| R2 文件名清洗收敛 | common/FileNames.java（新）；3 个存储服务 + Schedule/Resume + KnowledgeDocumentService/TextExtractor + DocPreviewService | 完成 | 后两处保留一行私有委托（减小调用点改动，19.5 豁免条款） |
| R3 共享字典 | utils/dict.js（新）+ CustomerInfo/CustomerDetail/CustomerRelationGraph/CustomerRelationTypeManage | 完成 | 客户关系方向字典 4 处逐字重复收敛 |
| R4 路径单一真源 | scripts/common-paths.ps1（新，JSHZ_ROOT 可覆盖）+ build-paths.ps1 + 12 个脚本 | 完成 | 方案 C 落地；deploy-backend/frontend 常量区**未动**（生产关键脚本，改动需重新演练，单独决策） |
| R5 venv 探测统一 | common/PpOcrRuntime.java（新）+ PhotoMatteService + LocalTableOcr | 完成 | 仅统一布局常量与扫描逻辑；两消费方环境变量语义不同（PHOTOMATTE_* vs COMPLIANCE_PPOCR_ROOT），探测候选仍各自组装 |
| 规范文档 | AGENTS.md 第 19 节（557-604 行） | 完成 | 含 19.5 复制检测核对命令 |

## 5. 实际变更
- 新增 5：backend/src/main/java/com/jshz/expert/common/{FileNames,UploadGuard,PpOcrRuntime}.java、scripts/common-paths.ps1、frontend/src/utils/dict.js
- 修改 19：5 个存储服务、ScheduleParseService、ResumeParseService、KnowledgeDocumentService、KnowledgeDocTextExtractor、DocPreviewService、LocalTableOcr、PhotoMatteService（后端 11）+ 4 个客户页面 + scripts 12 个 + build-paths.ps1 + AGENTS.md
- 有意保留（豁免登记）：SupplierStorageService（UUID 命名/MIME 白名单/8MB 业务差异值 MAX_SINGLE_SIZE/resolve 返回 null 契约）、ProjectStorageService（_richtext 子目录与 real-path 校验）——与"时间戳+前缀"形态语义不同，不强行合并（19.1 边界）；前端日程/合规/企微/批量解析字典枚举域互异，不收口。

## 6. 测试与验证
| 验证项 | 结果 | 备注 |
| --- | --- | --- |
| mvn compile | 通过 | 首次失败（PhotoMatteService 缺 PpOcrRuntime 导入）已修复 |
| npm run build | 通过 | ✓ 41.52s |
| 15 脚本 AST 解析 | 通过 | 含新增 common-paths 与 build-paths |
| 残留 grep | 通过 | `private String (safeName|extension|extensionOf)` 仅剩 3 处一行委托（豁免）；`$root='<LOCAL_PATH>'` 脚本 0 处；MAX_SIZE 字面量 0 处 |
| read_lints | 0 错误 | 覆盖全部新增/关键修改文件 |
| 运行时验证 | 通过 | 2026-11-13 发布后实测：/health=1.0.38.24 gitShort=96f35d1（8080 直连与经 Nginx 一致）；/api/meta/limits 未登录 401 拦截正确；前端 served index SHA256 与发布快照一致、assets 含 1.0.38.24 版本号 |

## 6a. 发布事故与修复（2026-11-13，如实披露）

- **事故**：首次蓝绿发布时发布包装脚本未传 -JarPath，deploy-backend.ps1 缺省按 LastWriteTime 选 jar；构建产物时间戳经 reproducible-builds 归一化后该判据失效，随机命中今晨 1.0.38.19 旧构建 → 生产 14:27~14:41 意外降级运行 1.0.38.19，且阶段 9e 只探活不核版本未拦截。
- **纠正**：发布后 /health 核验发现版本异常；修复护栏后显式指定 1.0.38.24 制品重新蓝绿发布，14:53 完成切回并实测版本正确。**事故与本次复用治理代码无关**（1.0.38.24 制品本身经完整构建与 testCompile 校验）。
- **护栏修复**（deploy-backend.ps1）：版本先行解析 + 候选 jar build.version 一致性校验 + 阶段 9e /health 版本比对（不符自动回滚）。详见 deployment.md「1.0.38.24 发布事故与修复记录」。
- **衍生修复**：阶段 1 预检要求 JWT_SECRET → 凭据库新增 Resolve-JwtSecret（c330168，bat 解析抽公共助手 Get-BatValue）；测试适配 PhotoStorageService 三参构造器（96f35d1，testCompile 此前未覆盖是流程盲区）。
- **备案**：① 阶段 10 的 8081 清空护栏会被 keepalive/浏览器直连卡住（两次均人工核验连接归属后收尾）；② 8081 存在发布前即有的无关 python 监听进程（PID 8340）待排查；③ 临时脚本含中文必须 UTF-8 带 BOM（本次补记发布表格行时再次踩坑，已当场修正记录行）。

## 7. 风险与回滚
- 已知问题：
  - PhotoStorageService 原安全名实现对 null 入参抛 NPE，FileNames.sanitize 为 null 安全返回空串——仅收紧异常路径，正常流不变（如实披露）；
  - 日程/简历超限文案从固定值改为随限额动态生成，当前值下渲染结果与原一致；
  - LocalTableOcr/PhotoMatte 探测仅改写为共享实现，探测顺序与判定不变。
- 回滚：`git checkout -- backend/src frontend/src scripts AGENTS.md docs`（未提交，注意会连带回滚第 18 节文档改动）。
- 未提交说明：用户未要求提交，改动留工作区待验收。

## 8. 文档同步
- [x] AGENTS.md 第 19 节（本任务主交付）
- [x] 交接记录（本文件）
- [x] handover/README.md 摘要
- [ ] deployment.md：不适用（无新环境变量；JSHZ_ROOT 属脚本演练用，随下次运维变更时登记）

## 9. 下一位 Agent 的接手指令
1. `git status` 应见本任务 ~24 文件未提交改动；`git log -1` = f597031。
2. 验证：`mvn compile`（带 -Drevision/-DgitShort）+ `npm run build` + 19.5 的复制检测 grep。
3. 未经确认禁止：把 Supplier/Project 存储服务并入 UploadGuard 形态（行为契约不同）；改动 deploy-backend.ps1 常量区；为收口而合并枚举域不同的前端字典。
4. 待负责人决策：#6/#7 观察项是否排期；本批次与第 18 节文档改动何时提交/发布。
