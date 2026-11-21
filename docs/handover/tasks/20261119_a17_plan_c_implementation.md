# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261119_a17_plan_c_implementation
- 任务名称：A17「硬编码治理方案 C 是否排期」决策流程落地（五阶段实施）
- 主责 Agent：CodeBuddy（Auto）
- 交接时间：2026-11-19
- 当前状态：进行中（阶段 1 完成）
- 优先级：P2

## 2. 目标与验收条件
- 业务目标：闭环 `docs/reviews/20261117-产品审查-01-文档痛点与挂起事项清单.md` A17 待决策项——按五阶段推进残余脚本收敛的评估、验证、决策、实施与归档。
- 本次范围：
  1. 盘点尚未改造的脚本，形成完整清单并标注优先级；
  2. 隔离环境补做「环境变量注入」验证（方案 B 遗留的 CORS/STORAGE_ROOT 覆盖演练）；
  3. 组织决策评审，明确各环节负责人并完成拍板确认；
  4. 动手改造脚本并逐项验证（全案最高风险环节，重点标注风险点与回滚措施）；
  5. 结案归档并更新相关文档。
- 不在本次范围：仓库外路径（`<LOCAL_PATH>` 等非仓库根子路径）的收敛；`*.bat`/`*.cmd`；后端/前端业务代码。
- 验收条件：
  - [ ] 阶段 1：清单经 grep 实测（非沿用旧基线），已收敛/未收敛分类准确
  - [ ] 阶段 2：影子实例注入验证有真实命令与输出记录
  - [ ] 阶段 3：决策结果留痕（批准/驳回/折中 + 范围）
  - [ ] 阶段 4：改动脚本 AST 解析通过；可安全实测项有真实触发记录；不可实测项如实标注「未执行」及原因
  - [ ] 阶段 5：A17 三处文档（审查清单/原交接记录/最新交接摘要）状态同步

## 3. 项目基线
- 分支：main；当前提交号：381bbaa（chore: 1.0.81 前端回滚元数据归档）
- 工作区状态：干净（git status 无未提交改动）
- 运行环境：生产后端 1.0.81（NSSM ExpertSystemBackend，8080）；核实时间 2026-11-19
- 核实方式：`git status` / `git log -1` 实测

## 4. 阶段 1：残余脚本盘点（2026-11-19 实测）

### 4.1 检索方法
- `rg "jshz-PMO" scripts -g "*.ps1"`（不分大小写）→ 22 文件命中；
- 再用赋值语句模式 `=\s*['"][^'"]*jshz-PMO` 区分「执行路径真字面量」与「注释/示例字符串」；
- 已收敛判定：dot-source `scripts/common-paths.ps1` 并以 `$root` 派生路径。

### 4.2 已收敛（无需改造，共 19）
| 类别 | 文件 |
| --- | --- |
| 单一真源（按设计豁免） | `common-paths.ps1`（`$root`，`JSHZ_ROOT` 可覆盖） |
| 真源派生 | `build-paths.ps1` |
| 已收敛（R4，12 个） | `db-migrate.ps1`、`build-all.ps1`、`register-backup-schedule.ps1`、`register-prune-schedule.ps1`、`release-backend.ps1`、`release-dashboard.ps1`、`release-compliance-visual.ps1`、`release-compliance-template.ps1`、9 个 `apply-migration-*.ps1`（去重后合计 12） |
| 无仓库根字面量 | `quarantine-final-delete.ps1`（`$PSScriptRoot` 派生）、`invoke-nginx-graceful.ps1`（仅临时任务名） |

### 4.3 未收敛清单（按优先级三档）

**P1-① 生产部署链（高风险，改动须正式批准 + 保留旧脚本副本 + 演练）**
| 文件 | 字面量位置 | 说明 |
| --- | --- | --- |
| `deploy-backend.ps1` | L57 `$BackendDir`、L59 `$ReleasesDir`、L60 `$DeployDoc`、L247 `$verFile` | 蓝绿发布主脚本；历史 jar 误选事故当事脚本，改动须重新演练 |
| `deploy-frontend.ps1` | L32 `$DistRoot`、L33 `$ReleasesRoot` | 参数默认值；支持临时目录演练 |
| `deploy-frontend-graceful.ps1` | L19 `$target`、L40 `$metadata` | 触发 Nginx graceful reload |
| `rollback-frontend.ps1` | L35/36 `$DistRoot`/`$ReleasesRoot`、L42 `$BackupRoot` | 回滚脚本，退路本身 |

**P2-② 定时任务/运维守护（中风险，改后须真实触发实测）**
| 文件 | 字面量位置 | 说明 |
| --- | --- | --- |
| `backup.ps1` | L40 `$BackupDir`、L49 `$ProjectRoot` | 每周日 02:00 SYSTEM 计划任务；改坏=备份静默断档（历史曾 6 天无备份） |
| `prune-logs.ps1` | L42 `$LogDir` | deploy-backend 阶段 11 自动调用 |
| `prune-temp.ps1` | L42 `$TempDir` | 月度计划任务 |
| `prune-builds.ps1` | L35 `$BuildsRoot` | 月度计划任务 |
| `prune-frontend-releases.ps1` | L50 `$FrontendDir` | 前端快照清理 |
| `verify-logging-rotation.ps1` | L203 `$logDir` | 日志轮转校验 |
| `rotate-backend-credentials.ps1` | L36 `$BatFile` | **禁真实触发**（会轮转生产凭据），仅语法/静态验证 |

**P3-③ 一次性/历史/测试工具（低风险，建议登记豁免或最小改造）**
| 文件 | 字面量位置 | 说明 |
| --- | --- | --- |
| `install-ppocr-runtime-20261111.ps1` | L12 `$log` | 一次性安装器，已完成使命 |
| `install-photomatte-20261111.ps1` | L12/49/50 | 一次性安装器 |
| `release-project-config-v3.ps1` | L18/97/143/146/175 | 历史专项发布脚本（已 dot-source 但未用 `$root`） |
| `scripts/tests/deployment-artifact-regression.ps1` | L2 `$KnownGood` | 测试工具，参数可覆盖 |

### 4.4 边界说明
- `<LOCAL_PATH>`（deploy/prune 系列各 1 处）是仓库外 Nginx 路径，不属于「仓库根单一真源」范畴，本次登记说明、不收敛。
- 数量口径：未收敛脚本 **12 个**（①4/②7/③4 含 tests 子目录；`install-*` 两脚本是否豁免由阶段 3 决策），赋值级字面量合计 **24 处**（不含注释与仓库外路径）。

### 阶段 1 验证结论
- [x] 清单经两轮 grep 交叉实测（2026-11-19），非沿用「44 个脚本」旧基线；
- [x] 已收敛 19 个 + 未收敛 12 个 + 真源 1 个，覆盖 `scripts/` 全部 `.ps1`；
- [ ] 待阶段 3 决策确认各档处置方式。

## 5. 阶段 2：隔离环境注入验证（已完成，2026-11-19）

### 5.1 验证方法
复用蓝绿发布同款影子机制（`deploy-backend.ps1` 阶段 2 的启动参数），在本机以**变体环境变量**启动独立影子实例，与生产 8080 完全隔离；不触碰生产服务/配置/数据（影子仅只读连接 127.0.0.1:3306，合规定时任务以启动参数关闭）。

- 变体注入值：`STORAGE_ROOT=temp\inject-verify-20261119\storage-root`（gitignored 目录）、`CORS_ALLOWED_ORIGINS=http://<PRIVATE_IP>:5173`（非生产来源）
- 凭据：经 `scripts/common-credentials.ps1` 运行时解析注入子进程环境（不落盘、不打印）
- 端口：8082（启动前实测空闲）

### 5.2 过程异常（如实记录）
- **第一次启动失败**：影子进程因缺 `JWT_SECRET` 抛 `WeakKeyException`（生产该值由 NSSM AppEnvironmentExtra 注入，本会话未继承）。补 `$env:JWT_SECRET = Resolve-JwtSecret` 后重试成功。失败本身反证了「注入覆盖」机制的真实存在。

### 5.3 验证结果（真实命令输出）
| # | 验证项 | 预期 | 实测 | 结论 |
| --- | --- | --- | --- | --- |
| 1 | 影子 `/health`（8082） | 200 UP | `version=1.0.81 buildTime=2026-11-19 16:07:04 gitShort=e3ba372`（与生产 jar 同版本） | 通过 |
| 2 | STORAGE_ROOT 绑定 | 变体根下出现存储目录 | 实测出现：供应商图库、客户附件库、照片库、照片库_tmp、知识文档库(+_preview)、项目资料库(+_richtext)——与 application.yml 七目录定义一致（构造器启动即建目录，物理铁证） | **通过** |
| 3 | CORS 变体 Origin 预检 | 放行 | `OPTIONS Origin=http://<PRIVATE_IP>:5173` → `HTTP/1.1 200` + `Access-Control-Allow-Origin: http://<PRIVATE_IP>:5173` | **通过** |
| 4 | CORS 默认生产 Origin（阴性对照） | 拒绝 | `OPTIONS Origin=http://<PRIVATE_IP>:5173` → `HTTP/1.1 403 Invalid CORS request`，无放行头 | **通过**（证明注入是**替换**而非合并，白名单语义正确） |
| 5 | `/api/meta/limits` 匿名访问 | 401 | 实测 401 | 通过 |

### 5.4 清理与生产影响
- 影子进程 PID 26116 已停止，8082 监听确认消失；
- 生产 8080 全程未动，事后复测 `/health` 200 UP 1.0.81；
- 变体目录 `temp\inject-verify-20261119\` 已删除。

### 阶段 2 验证结论
- [x] `hardcode_remediation.md` 第 76/85 行遗留的「CORS/存储目录注入覆盖演练」**已闭环**：两个环境变量均可真实覆盖默认值，语义为替换；
- [x] 方案 B 的环境变量出口经运行时实证可用，为阶段 4 的脚本改造提供了「换机场景仅设 JSHZ_ROOT 即可」的兜底依据；
- [x] 未执行项：无（本阶段计划内容全部实测）。

## 6. 阶段 3：决策评审（已完成，2026-11-19）

### 6.1 责任人与分工
| 环节 | 责任人 | 状态 |
| --- | --- | --- |
| 评估材料（清单+验证） | 主责 Agent | 已交付（本文档 §4/§5） |
| 排期拍板 | 项目负责人（Owner） | **已拍板**（见 6.2） |
| 脚本改造实施与实测 | 主责 Agent | 阶段 4 执行 |
| ① 类真实演练窗口批准与执行 | 项目负责人批准；管理员/发布窗口执行 | **挂起待窗口** |
| 结案归档复核 | 主责 Agent | 阶段 5 执行 |

### 6.2 决策结果（2026-11-19，经评审问答确认留痕）
1. **排期范围：批准「推荐方案」**——② 定时任务类 7 个立即改造+实测；① 部署链 4 个改造后随下一次后端蓝绿发布窗口演练；③ 历史/一次性 4 个登记豁免。
2. **演练窗口**：① 类改造后仅做 AST 解析+静态核验，**真实演练挂起待窗口**，窗口须项目负责人批准后执行（自举风险控制：旧脚本副本以未提交前 git 版本为回滚点）。
3. **③ 类处置**：登记豁免不动，注明已完成使命，git 历史保留。

### 6.3 决策依据（供复核）
- 阶段 2 实证注入覆盖真实可用 → 换机场景已有 `JSHZ_ROOT`/环境变量兜底，收敛收益=消除人工核对点与减少换机风险敞口；
- ② 类改造收益实、风险可控（可安全实测）；① 类收益同但有自举风险，故改造与演练分离。

## 7. 阶段 4：脚本改造与验证（已完成，2026-11-19）

### 7.1 实际变更（11 个脚本，+58/-28 行，未提交）
统一改造模式：dot-source `scripts/common-paths.ps1`（`$root`，`JSHZ_ROOT` 可覆盖）+ 参数缺省移至脚本体内按 `$root` 推导（`IsNullOrWhiteSpace` 守卫）；显式传参行为不变，`JSHZ_ROOT` 未设置时解析结果与原字面量逐字节一致。

- 修改文件（② 类 7 个）：`backup.ps1`（$BackupDir/$ProjectRoot）、`prune-logs.ps1`、`prune-temp.ps1`、`prune-builds.ps1`、`prune-frontend-releases.ps1`（$FrontendDir）、`verify-logging-rotation.ps1`（$logDir）、`rotate-backend-credentials.ps1`（$BatFile）
- 修改文件（① 类 4 个）：`deploy-backend.ps1`（$BackendDir/$ReleasesDir/$DeployDoc/L247 $verFile）、`deploy-frontend.ps1`（$DistRoot/$ReleasesRoot）、`deploy-frontend-graceful.ps1`（$target/$metadata）、`rollback-frontend.ps1`（$DistRoot/$ReleasesRoot/$BackupRoot）
- 有意保留（决策范围外）：各脚本中 `<LOCAL_PATH>` 等仓库外路径（5 处）；③ 类豁免脚本（install×2、release-project-config-v3、tests/deployment-artifact-regression）
- 新增文件：无（复用既有 common-paths.ps1）；文档：本记录 + 阶段 5 列出的三处同步
- 数据库/迁移：无；API/页面/权限：无；依赖：无

### 7.2 风险点与回滚措施（高风险环节专项）
| 风险点 | 防控措施 | 落实情况 |
| --- | --- | --- |
| 备份脚本改坏→备份静默断档 | 默认值推导与原值逐字节一致；改后立即 `-Mode Verify` 只读实测 | Verify 通过（见 7.3） |
| 发布脚本自举风险（用被改的脚本验证自己） | 改造与演练分离：本次仅 DryRun/plan-only；真实演练挂起待窗口；旧脚本副本=未提交前 git 版本 | DryRun 全过；演练待批准窗口 |
| 回滚退路 | 全部改动未提交，单命令回滚：`git checkout -- scripts/`；不涉数据库/接口 | 工作区可随时回滚 |
| 凭据泄露 | rotate-credentials 禁真实触发；DryRun 亦未执行（#requires 管理员） | 未触发，静态验证通过 |
| 误删文件 | prune 系列仅 DryRun 触发，未实际删除；删除逻辑代码零改动 | DryRun 通过 |
| 中文脚本编码 | 全部 11 文件 AST 解析 0 错误 + UTF-8 BOM 校验通过（PS 5.1 约束） | 11/11 OK |

### 7.3 测试与验证（真实执行记录）
| 验证项 | 命令/方式 | 结果 |
| --- | --- | --- |
| AST 语法解析 | `Parser::ParseFile` 逐一解析 11 个改动脚本 | **通过 11/11，0 错误** |
| UTF-8 BOM 校验 | 读前 3 字节 EF BB BF | **通过 11/11** |
| 残留字面量 grep | `=\s*['"][^'"]*jshz-PMO` 全 scripts | **②①类仓库根赋值字面量清零**；残留 10 处均为决策保留（5 处仓库外 nginx + 5 处 ③ 豁免） |
| prune-logs 触发 | `-DryRun`（真实执行，路径解析 `<LOCAL_PATH>` 正确） | 通过 |
| prune-temp/builds/frontend-releases 触发 | `-DryRun`（各自目录解析正确，保留策略逻辑完好） | 通过（真实删除未执行——删除逻辑零改动，随月度计划任务正常运行） |
| verify-logging-rotation 触发 | 无 `-Restart`（只读全量检查） | 通过：轮转参数/编码/健康全 OK，`$logDir` 解析正确 |
| backup 触发 | `-Mode Verify`（只读） | **通过**：默认 `$BackupDir` 经 `$root` 解析定位最新备份 `backup_20261112_170431`，4 产物 SHA256/内容校验一致 |
| rollback-frontend 触发 | `-DryRun` | 通过：路径解析正确；Nginx root 护栏照常生效（护栏未被改坏的证据） |
| deploy-frontend 触发 | `-DryRun` | 通过：DistRoot/ReleasesRoot 解析正确，版本 1.0.81 读出 |
| deploy-frontend-graceful 触发 | plan-only（临时 staged 目录，无 `-Apply`，未触 Nginx） | 通过：目标快照路径经 `$root` 推导正确，"Plan only: no production writes" |
| deploy-backend 触发 | `-DryRun`（含凭据会话注入） | **通过：11 阶段全过、零副作用**；$verFile 解析出期望版本 1.0.81、$BackendDir 工作目录、$ReleasesDir 归档路径均正确 |
| rotate-backend-credentials | AST 通过；真实触发被脚本 `#requires` 管理员声明拦截 | **未执行**（需管理员会话；本改动仅路径推导，静态核验足够；后续凭据轮换时自然验证） |
| ① 类真实演练 | 需蓝绿发布窗口 | **挂起待窗口**（阶段 3 决策：窗口须负责人批准后执行） |

## 8. 阶段 5：结案归档（已完成，2026-11-19）

- [x] 本任务记录（本文档）五阶段齐全、可追溯；
- [x] `20261113_hardcode_remediation.md` 第 113 行待决策项标注闭环；
- [x] `20261117-产品审查-01` A17 行标注处置结果；
- [x] `docs/handover/README.md` 新增最新交接摘要条目；
- [ ] `docs/deployment.md`：脚本路径推导机制无运维操作变化（默认值不变），无需更新部署步骤——判定为不适用；
- [ ] 架构文档：无后端/前端变更，不适用。

## 9. 当前状态与未完成事项
- **当前状态：已完成待验收**（代码未提交；① 类真实演练挂起待窗口）。
| 未完成事项 | 原因 | 影响 | 下一步 | 责任方 |
| --- | --- | --- | --- | --- |
| 11 个脚本改动未提交入库 | 提交须随验收 | 工作区带 11 个 M 文件 | 验收后按单主题提交（建议信息：`refactor(scripts): 方案C二期残余脚本收敛到common-paths单一真源`） | 负责人验收后 Agent 提交 |
| ① 类部署链真实演练 | 挂起待发布窗口（阶段 3 决策） | 无——改动经 11 阶段 DryRun 验证，默认行为与改前一致 | 下次后端蓝绿发布窗口批准后执行；该窗口即演练 | 负责人批准；发布执行人 |
| rotate-credentials 真实触发 | 脚本 `#requires` 管理员 | 无——仅路径推导改动，静态核验足够 | 下次凭据轮换时自然验证 | 管理员会话 |
| prune 系列真实删除 | 本次仅 DryRun 触发 | 无——删除逻辑零改动 | 月度计划任务 `jshzPMOPruneMonthly` 自然运行即验证 | 既有计划任务 |

## 10. 风险与回滚
- 已知问题：无（全部验证通过）。
- 数据风险：无（无数据库操作）。
- 兼容性风险：`JSHZ_ROOT` 未设置时所有脚本行为与改前逐字节一致；显式传参行为不变；计划任务命令行未变（脚本绝对路径不变）。
- 回滚步骤：`git checkout -- scripts/`（单命令，回滚全部 11 个脚本；本任务文档为新增文件可保留）。
- 回滚限制：无数据库/接口/契约牵连。

## 11. 下一位 Agent 的接手指令
1. 首先检查：`git status`（应见 11 个 scripts M + 本文档 untracked）、`git log -1`（381bbaa）。
2. 然后执行：抽查任一脚本 diff 确认改造模式（`git diff scripts/backup.ps1`）；下一次后端蓝绿发布窗口时，发布即完成 ① 类真实演练（观察 deploy-backend 各阶段路径输出）。
3. 验收标准：窗口演练全过后提交入库；`docs/handover/README.md` 对应摘要条目更新为「已提交」。
4. 未经确认禁止：不得在生产直接执行 deploy/rotate 脚本（须窗口批准）；不得为 ③ 类豁免脚本「顺手」改造或删除。
5. 需要负责人决策：① 类演练窗口的批准时间；改动提交入库的时点。
