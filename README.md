# 景帅会展内部管理库系统（Expert Library System）

> 📌 **文档同步铁律（开发者必读）**：任何代码、数据库结构、接口变更必须同步更新 `docs/` 对应文档与交接文档，检查清单见 [AGENTS.md](AGENTS.md) 第 0 节。当前事实基线（2026-11-06 核实）：后端 Controller **26** 个、Service **47** 个、`@TableName` 实体 **57** 个；前端页面 41 个、路由 35 条；数据库 **74** 张表（**68 张业务表 + 6 张历史备份表**，由 `information_schema` 实测，见下方「事实基线的核实方式」）。
>
> **事实基线的核实方式**（写文档前请重跑，勿把历史基线当永久事实）：
>
> ```bash
> # 表数量（业务表 / 备份表）
> mysql -uroot -p -N -B -e "SELECT COUNT(*) FROM information_schema.tables \
>   WHERE table_schema='expert_system' AND table_type='BASE TABLE';"
> # Controller / Service / 实体
> ls backend/src/main/java/com/jshz/expert/controller/*.java | wc -l
> ls backend/src/main/java/com/jshz/expert/service/*.java    | wc -l
> grep -rl "@TableName" backend/src/main/java/com/jshz/expert/entity/*.java | wc -l
> ```
>
> 历史偏差记录：本文档曾长期写「数据库 39 张表」，实际为 74 张（`docs/architecture.md` 另写 26 张、`docs/deployment.md` 写 13 张，三处互相矛盾）；`init.sql` 实际只建 **6 张表**，其余由 `database/migration/` 50 个增量脚本补齐。

## 项目定位

本系统为公司内部**办公管理系统**，用于专家信息的统一登记、维护、检索与调用。系统**依赖公司内网部署**，仅限内网环境访问，不与公网互通。

核心能力：

- 专家信息管理：专家档案（18 字段，含敏感身份信息）的登记、维护、检索与下载
- 数据安全：身份证号/银行卡号查看页脱敏显示；下载留痕审计（download_log 记录下载人/时间/类型/IP）
- 认证体系：账号=姓名登录、JWT（httpOnly cookie）、BCrypt、初始密码强制改密
- 权限管理：基于 RBAC 权限体系（role / permission / role_permission / user_role），sys_user.is_admin 保留为管理员名单兼容标记
- 审批流：员工修改/新增专家走审批链（卡片内编辑提交、新增专家申请），管理员审批通过后应用变更/创建专家并归档照片；审批流可配置开关与审批人角色

## 技术架构

前后端分离架构：

| 模块  | 技术栈                                      | 目录          |
| --- | ---------------------------------------- | ----------- |
| 后端  | Java 17 + Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + Spring Security + jjwt 0.11.5 | `backend/` |
| 前端  | Vue 3.4 + Vite 5 + Element Plus + Pinia + vue-router + axios | `frontend/` |
| 数据库 | MySQL 8.x（expert_system 库 **74 张表** = 68 业务表 + 6 历史备份表。按模块：合规 17 / 项目 16 / 供应商 7 / 客户 4 / 审批与申请 4 / 知识 3 / 企微 3 / 认证与权限 6 / 其他 8；完整字段说明见 `docs/architecture.md` §4） | `database/` |
| 照片存储 | 本地磁盘照片库（三类照片 + 静态资源映射） | `照片库/` |
| 文档  | 设计、架构、部署、验收报告 | `docs/`     |

## 目录结构

```
jshz-PMO/
├── README.md              # 项目说明
├── AGENTS.md              # 开发与 Agent 交接规范（文档同步铁律）
├── version.txt            # 版本号唯一真源（revision=x.y.z）
├── .gitignore             # Git 忽略规则（*.log / logs/ / temp/ 已忽略）
├── backend/               # 后端服务（Spring Boot 3.2.5 + MyBatis-Plus，26 个 Controller / 47 个 Service）
├── frontend/              # 前端应用（Vue 3.4 + Vite 5；线上目录是 releases/<ReleaseId>，dist 仅为构建输出，41 页面 / 35 路由）
├── database/              # 数据库脚本（init.sql 建 6 张基础表；migration/ 50 个增量脚本补齐至 74 表）
├── scripts/               # 构建、发布、回滚、备份、迁移脚本（PowerShell + Python）
├── docs/                  # 项目文档（设计/架构/部署/验收/交接）
├── builds/                # 构建产物根目录（见下方「产物与日志存放约定」）
│   └── archive/           # 历史构建快照归档
├── logs/                  # 唯一日志目录（应用日志 / 发布日志 / NSSM 重定向 / legacy 历史归档）
└── 照片库/                # 照片存储（avatar / idcard_front / idcard_back）
    └── 照片库_tmp/        # 审批流临时照片（审批通过归档至 照片库/，驳回/归档后清理）
```

## 快速开始（概要）

1. 初始化数据库：创建 `expert_system` 库 → 执行 `database/init.sql`（建 6 张基础表：expert_info / sys_user / download_log / approval_config / expert_change_request / approval_record，并写入 admin 与示例数据）→ 按文件名顺序执行 `database/migration/` 全部 50 个增量脚本，最终 **74 张表**（68 业务表 + 6 历史备份表）
2. 启动后端：配置数据库环境变量后 `cd backend && mvn spring-boot:run`（默认端口 8080）
3. 启动前端：`cd frontend && npm install && npm run dev`（默认端口 5173）

数据库环境变量（以 `backend/src/main/resources/application.yml` 为准）：`MYSQL_HOST / MYSQL_PORT / MYSQL_DB / MYSQL_USER / MYSQL_PASSWORD`。

详细步骤见 [docs/deployment.md](docs/deployment.md)。

## 部署约束

- **内网部署**：所有服务仅监听内网地址，禁止暴露公网端口
- **数据库**：使用内网 MySQL 8 实例（`expert_system` 库），连接信息通过 `MYSQL_*` 环境变量注入，禁止硬编码提交
- **认证**：JWT 存 httpOnly cookie（SameSite=Lax、无 Secure，适配内网 http），登出清 cookie；CORS 仅允许 `<PRIVATE_IP>:5173` / `localhost:5173`
- **网络隔离**：前端通过内网域名或 Nginx 反代访问后端接口  

## 不停机部署方案（发布运维规范）

> **每次代码/配置更新上线，必须按本方案（R-1 后端蓝绿 / R-2 前端原子发布）执行，禁止直接覆盖运行文件；严格不停机发布使用 `deploy-backend.ps1 -GracefulSwitch` 和 `deploy-frontend-graceful.ps1`，禁止普通用户直接发送 reload。** 完整设计与流程图见 [docs/design/2026-10-24-发布运维体系与侧边栏菜单偏移修复-设计文档.md](docs/design/2026-10-24-发布运维体系与侧边栏菜单偏移修复-设计文档.md)。

### R-1 后端蓝绿不停机发布

脚本：`scripts/deploy-backend.ps1`，按设计文档 §4.1 实现 11 阶段蓝绿发布：

```
预检 → 影子启动 8081 → 健康+业务探测 → 备份 Nginx conf → 改 proxy_pass → nginx -t
→ Restart-Service ExpertSystemNginx → 蓝验证 → 稳态化 NSSM → 切回 8080 → 清理+归档
```

- 端口约定：`8080`=生产（绿），`8081`=影子（蓝）
- **严格不停机切流使用 `-GracefulSwitch`**：管理员通过 SYSTEM 一次性任务平滑重载、验证主进程不变并等待旧 worker 排空；旧版默认 `Restart-Service` 会有入口间隙，不能用于不停机验收。详见 `docs/releases/20261103-review-fixes-approval.md`
- 阶段 9 先停 NSSM 服务再替换 `target/*.jar`（释放进程锁；不先停会替换失败）
- 归档：`backend/releases/`，默认保留 5 个版本（`-KeepVersions` 可调）

```powershell
.\scripts\deploy-backend.ps1 -DryRun                                        # 预演（不改配置/不动服务）
.\scripts\release-backend.ps1 -DryRun                                       # 推荐入口：预演（自动校验版本/凭据）
.\scripts\release-backend.ps1                                               # 推荐入口：正式蓝绿发布（需管理员）
.\scripts\deploy-backend.ps1 -JarPath <LOCAL_PATH>
```

参数：`-JarPath`（候选包绝对路径，缺省取 `builds\` 下最新，**不递归 `archive`**）、`-Version`（缺省读 `version.txt`，且必须与包内 `build-info.version` 一致，否则拒绝发布）、`-DryRun`、`-GracefulSwitch`（严格不停机，必须）、`-KeepVersions`。

> **发布入口约定（2026-11-06）**：生产发布统一走 `scripts\release-backend.ps1`（管理员会话）或 `scripts\release-backend.cmd`（右键以管理员运行）。它负责从 `start-backend.bat` 注入 `MYSQL_PASSWORD` / `JWT_SECRET`、校验候选包版本与 `version.txt` 一致、把输出写入 UTF-8 日志 `logs\deploy-backend.<时间戳>.log`，再调用 `deploy-backend.ps1`。`release-backend.cmd` 必须是**纯 ASCII + CRLF**，写成 UTF-8 会被 `cmd.exe` 按 GBK 解析成乱码命令。

> **凭据策略（免全员重登）**：发布前在会话按 `start-backend.bat` 值注入 `MYSQL_PASSWORD` 与 `JWT_SECRET`；脚本阶段 1 强制校验会话与 NSSM 服务环境一致，不一致中止。`JWT_SECRET` 为长期固定凭据，发布/重启不更换——已登录用户 token 跨发布保持有效；轮换仅限疑似泄露场景且需全员重登（详见 `docs/deployment.md`「凭据长期策略与免重登发布」）。

### R-2 前端发布与回滚（快照目录 + Nginx root 指针切换）

> **2026-11-06 事实核对修正**：此前本文写的「`dist` 原子替换 + 无需 reload」与线上实际不符。线上已改为 `Nginx root` 指向 `frontend/releases/<发布标签>` 快照目录，靠改 root + graceful reload 切换；**替换 `frontend/dist` 对线上没有任何影响**（`deploy-frontend.ps1` 曾因此空转，其第 5 步自检会失败）。

- **发布**：`scripts/deploy-frontend-graceful.ps1` —— 以已构建目录作 `-StagedDist` → 复制到 `frontend/releases/<ReleaseId>`（目录必须不存在）→ 把旧 root 的 `assets/` 一并并入新目录（保证已打开页面仍能取到旧 chunk）→ 写入 `frontend/release-metadata/<ReleaseId>.nginx-before.txt` 与 `.rollback.conf.txt` → `nginx -t` → graceful reload（主进程不变）→ 校验远端 `index.html` 哈希。
- **回滚**：把 Nginx root 改回 `release-metadata/<ReleaseId>.rollback.conf.txt` 记录的旧目录，`nginx -t` + graceful reload。
- **前置**：需管理员会话（改 `<LOCAL_PATH>` 并重载 Nginx）。
- **`deploy-frontend.ps1` 已降级为历史脚本**：它只做 `dist` 替换，仅当 root 指回 `frontend\dist` 时才有效。脚本内已加护栏，root 不匹配时直接 `[ABORT]` 退出码 2，防止再次空转。`rollback-frontend.ps1` 同属该历史体系，恢复 `dist` 用，不作用于快照目录。
- **体量提示**：每个快照会并入旧 assets 而逐次增大（当前线上目录 1060 文件 / 106 MB）。**2026-11-06 已按策略清理**：`frontend/releases/` 由 89 个目录 / 2.8 GB 降到 4 个 / 409 MB（当前 root + 回滚副本 + 上两版），清理脚本 `scripts/prune-frontend-releases.ps1`（`-DryRun` 先看清单）。
- **`rollback-frontend.ps1` 同样已加护栏**：它的回滚方式是"恢复快照到 dist"，而 dist 不被服务；root 不是 dist 时直接 `[ABORT]` 退出码 2，并把正确的 root 回滚路径打出来。

```powershell
# 1) 构建到 staging（不改线上）
.\scripts\build-all.ps1
# 2) 演练（只打印计划，不写生产）
.\scripts\deploy-frontend-graceful.ps1 -StagedDist <LOCAL_PATH> -ReleaseId 1.0.34-20261106_1705
# 3) 正式切换（管理员）
.\scripts\deploy-frontend-graceful.ps1 -StagedDist <LOCAL_PATH> -ReleaseId 1.0.34-20261106_1705 -Apply
```

### 发布流程总纲（每次更新必读）

| 环节 | 动作 | 回滚 |
| --- | --- | --- |
| 后端更新 | `scripts\build-all.ps1 -BackendOnly` 构建 → `scripts\release-backend.ps1`（先 `-DryRun` 预演，管理员执行） | 设计文档 §4.1.4 回滚预案 / `deployment.md` |
| 前端更新 | `scripts\deploy-frontend-graceful.ps1 -StagedDist <构建目录> -ReleaseId <标签> -Apply`（快照目录 + root 指针切换，管理员） | 改回 `release-metadata\<ReleaseId>.rollback.conf.txt` 记录的 root，`nginx -t` + graceful reload |
| 数据库变更 | 遵循 R-3 向后兼容迁移规范（新增列/状态需兼容旧版本，禁止破坏性变更） | 迁移脚本保留现场 |
| 文档同步 | 按 [AGENTS.md](AGENTS.md) 第 0 节同步 `docs/` 对应文档与交接文档 | — |

R-3 数据库向后兼容迁移规范、R-4 一键发布脚本化、R-5 版本保留与回滚预案详见设计文档 §4.3~§4.5。

---

## 产物与日志存放约定

> 与 [AGENTS.md](AGENTS.md) §0.4 一致：**仓库根目录禁止出现 `*.log`、`dist_old.*`、`dist-new.*`、临时构建目录**。所有产物与日志都有固定归属地，散落即视为缺陷。

### 构建产物：`builds/`

| 路径 | 内容 | 说明 |
| --- | --- | --- |
| `builds/<版本>-build-<时间戳>/` | 单次构建产物：`backend/`（隔离构建目录）、`expert-system.jar`（发布用副本）、`dist/`（全量构建时）、`build-info.txt` | `build-all.ps1` 生成；`-BackendOnly` 时不产出 `dist/` |
| `builds/archive/` | 历史构建快照归档 | 2026-11-06 把根目录与旧 `.build-*` 共 27 个目录约 11 GB 迁入 |

- 候选包查找只遍历 `builds/` **一级子目录并排除 `archive/`**，绝不递归历史归档（曾因此误取旧 jar）。
- **禁止在 `backend/` 原地 `mvn clean package`**：生产进程占用 `backend/target/expert-system.jar`，`clean` 会失败；构建一律在 `builds/<stage>/backend` 隔离目录进行。

### 日志：`logs/`（唯一日志目录）

| 文件 | 产生者 | 保留策略 |
| --- | --- | --- |
| `logs/expert-app.log`、`logs/expert-app.<日期>.<序号>.log.gz` | logback（`logback-spring.xml`，UTF-8） | 单文件 20MB 切分、保留 30 天、总量上限 2GB、启动时清理；**由 logback 自管** |
| `logs/expert-backend.out.log`、`.err.log` | NSSM 重定向 stdout/stderr（UTF-8） | 当前活动文件，永不删除 |
| `logs/expert-backend.out-<UTC时间戳>.log`、`.err-<UTC时间戳>.log` | NSSM 轮转产物（**每次服务启动必轮转一次**，运行中超过 10MB 也轮转） | 由 `scripts/prune-logs.ps1` 各保留最近 **10** 个 |
| `logs/deploy-backend.<yyyyMMdd_HHmmss>.log` | `release-backend.ps1` 每次发布一个（UTF-8） | 由 `scripts/prune-logs.ps1` 保留最近 **20** 个 |
| `logs/legacy/` | 2026-11-06 前散落在仓库根目录的 7 个历史发布日志 | 仅归档，不再新增 |

- 保留策略**自动执行**：`deploy-backend.ps1` 阶段 11 会调用 `prune-logs.ps1`。
- 手动执行：`.\scripts\prune-logs.ps1`；预演不删除：`.\scripts\prune-logs.ps1 -DryRun`；调参：`-KeepNssm 10 -KeepDeploy 20`。

### 前端快照：`frontend/releases/`

线上静态目录是 `releases/<ReleaseId>`（Nginx `root` 指向它），**不是** `frontend/dist`；`dist` 只是构建输出，发布脚本已加护栏防止误以为换 `dist` 就上线。

| 路径 | 内容 | 保留策略 |
| --- | --- | --- |
| `frontend/releases/<ReleaseId>/` | 正式快照，发布时会把上一版 assets 合并进来（旧 chunk 供已打开页面使用） | 保留最近 **3** 个（含当前 root） |
| `frontend/releases/<ReleaseId>.rollback/` | 上一版完整副本，`release-metadata/<ReleaseId>.rollback.conf.txt` 指向它 | 只保留**当前** release 的那一个 |
| `frontend/release-metadata/` | 每次发布的 nginx-before / rollback 配置备份 | 不清理（审计与回滚依据，体积可忽略） |
| `frontend/dist_old.*` / `dist-new.*` / `dist-green` / `dist-review-fixes` / `.build-*` | 旧 dist 替换流程遗留，从未被 Nginx 服务 | 全部删除（`prune-frontend-releases.ps1`） |

- 清理脚本：`.\scripts\prune-frontend-releases.ps1 -DryRun` 预演，去掉 `-DryRun` 执行；`-KeepReleases N` 调保留数。
- 2026-11-06 实测：136 个目录 → 释放约 2.5 GB，`releases/` 89 个 / 2.8 GB 降至 4 个 / 409 MB，线上首页哈希与本地快照一致、旧 chunk 仍 200。
- `*.log`、`logs/`、`temp/` 已由 `.gitignore` 覆盖，不会进版本库；但**覆盖不等于可以散落**，根目录出现日志仍需立即归位到 `logs/`。

## 版本升级三步走（版本号自动同步）

> 自 v1.0.10 起，项目版本号改为**单一真源**：项目根目录 `version.txt`（properties 格式，如 `revision=1.0.10`）。
> 前端界面、后端 `/health` 接口、发布归档命名全部由它驱动，不再手工改代码中的硬编码版本号。

升级版本只需三步：

1. **改版本**：编辑 `<LOCAL_PATH>`，把 `revision=` 改为新版本号（如 `revision=1.0.11`），提交。
2. **构建**：执行 `.\scripts\build-all.ps1`（前端 + 后端，产物落 `builds\<版本>-build-<时间戳>\`）；纯后端发布加 `-BackendOnly`，不重建 `frontend\dist`，避免动到线上正在服务的静态资源。
   - 前端：`vite.config.js` 构建期读取 `version.txt` 注入 `VITE_APP_VERSION`，登录页/侧边栏统一显示"正式版 v1.0.10"（前缀见 `frontend/.env` 的 `VITE_APP_VERSION_PREFIX`）。
   - 后端：`pom.xml` 的 `<version>` 为 `${revision}`（缺省哨兵值 `0.0.0-UNSET`，漏传 `-Drevision` 会构建出可肉眼识别的错版本包）；`backend\src\main\resources\version.properties` 仍由 `maven-resources-plugin` 的 `<filters>`（指向根目录 `version.txt`）过滤生成。`spring-boot-maven-plugin` 的 `build-info` 目标额外产出 `buildTime` / `gitShort`，`/health` 返回 `status / time / version / buildTime / gitShort` 五个字段。
3. **发布**：后端 `.\scripts\release-backend.ps1`（蓝绿不停机，需管理员会话）；前端 `.\scripts\deploy-frontend.ps1`（构建到临时目录后原子替换 `dist`，无需 reload Nginx）。两者归档目录名均自动取自 `version.txt`。

**版本号一致性要求**：`version.txt` 是唯一驱动源，以下三处必须与它同步，否则视为缺陷——

| 位置 | 当前值 | 说明 | 校验方式 |
| --- | --- | --- | --- |
| `version.txt` | `revision=1.0.34` | **唯一真源**，改版本只改这一行 | — |
| `frontend/package.json` 的 `version` | `1.0.34` | 工程元数据，不参与构建注入，但必须同步避免误读 | 人工比对 / 发布前检查 |
| `backend/pom.xml` 的实际版本 | `${revision}` 动态注入 | 不再是硬编码，无需手工维护；包内实际值见 `build-info.properties` | `release-backend.ps1` 强制校验包内版本 == `version.txt` |

界面展示（登录页/侧边栏「正式版 vX」）与 `/health` 的 `version` 都来自构建期的 `version.txt`，因此**改了 `version.txt` 必须重新构建前端**，否则界面仍显示旧版本号。

*（内容由AI生成，仅供参考）*
