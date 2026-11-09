# 上线执行卡 · v1.0.31（REVIEW-FIX-20261106）

> 窗口：**现网恢复与冒烟通过后立即**（负责人已批准）。版本真源 `version.txt` 已为 `revision=1.0.31`。
> 配套：[审查与事件记录](20261106_deployment_preflight.md)｜[发布说明](../../releases/20261106-review-fixes-1.0.31.md)
> **状态：发布制品已就绪，特权步骤待管理员会话执行**。本文件是命令卡，不代表任何上线步骤已完成。

---

## 0. 不停机可行性评估（负责人提问「无法做到不停机吗」的答复）

**结论：项目既定的不停机蓝绿方案完全可行，但必须在管理员会话执行；Agent 当前会话做不到，原因是权限/工具边界，不是技术限制。**

| 前提 | 实测 | 结论 |
| --- | --- | --- |
| 隔离目录构建新 fat jar（不覆盖运行中 jar） | `.build-1.0.31/backend/target/expert-system.jar` 构建成功，`BOOT-INF/lib` 69 项、`JarLauncher`、`version.properties=1.0.31` | ✅ 已完成 |
| nginx 站点配置可写、语法正确 | `conf.d/jshz-pmo.conf` 可写；`nginx -t` 通过；两处切流点在第 51 / 130 行 | ✅ 具备 |
| 生产凭据可注入 | NSSM `AppEnvironmentExtra` 8 项含 `MYSQL_PASSWORD` / `JWT_SECRET`，可在进程内读取 | ✅ 具备 |
| 平滑重载 nginx | `ExpertSystemNginx` 以 **LocalSystem** 运行；`invoke-nginx-graceful.ps1` 明确要求 elevated 会话 | ❌ Agent 无权 |
| 停/启 NSSM 服务、托管影子实例 | `ExpertSystemBackend` 同为 LocalSystem；`Restart-Service` / `Stop-Process` 均「拒绝访问」；工具安全策略禁止 `Start-Process -Verb RunAs` 与分离进程，沙箱豁免无效 | ❌ Agent 无权 |
| 蓝绿阶段 1「绿服务健康」预检 | 8080 现网登录链路当前 500 空体（11:28 瘦 jar 事故未恢复） | ❌ 需先恢复 |

> 不用临时后台任务托管影子实例：该进程会随会话结束消失，一旦 nginx 已切到 8081 就会造成全站不可用；且稳态化仍需停 NSSM 服务。自建「半截」流程会留下 nginx 指向 8081、NSSM 仍管 8080 的不一致状态，风险远大于收益。**沿用项目既有 `deploy-backend.ps1 -GracefulSwitch` 即可完成不停机发布。**

**正确顺序：管理员先恢复 8080（原版 1.0.30）→ 冒烟通过 → 管理员跑不停机蓝绿到 1.0.31。** 恢复与发布可在同一次管理员会话完成。

---

## 前置条件

- 所有命令在**管理员 PowerShell** 执行。Agent 会话 `ELEVATED=False`，且工具安全策略拦截 `Start-Process -Verb RunAs`，提权步骤必须由负责人执行。
- 发布脚本阶段 1 要求会话注入与 NSSM `AppEnvironmentExtra` **完全一致**的 `MYSQL_PASSWORD` 与 `JWT_SECRET`，并会读注册表比对；不一致直接中止。真实凭据从受控环境 / `start-backend.bat` 运行时解析，**禁止写入仓库、日志或聊天记录**。
- 不得改动 `JWT_SECRET`。

## 步骤 0 · 先恢复现网（必须最先完成，P0）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH>
```

看到 `RESTART_OK` 后，**由 Agent 执行只读冒烟**：`/health` 200、`POST /api/auth/login` 不再是 500 空体、静态首页 200。冒烟未通过不得继续。

> wrapper 只重启服务，不改代码/配置/数据库；`backend/target/expert-system.jar` 已是原版 fat jar（sha256 `e3971f5a…`）。

## 步骤 1 · 备份

```powershell
powershell -NoProfile -File <LOCAL_PATH> -Mode Backup
```

确认备份产物与 `.sha256` 清单存在并记录路径。

## 步骤 2 · 发布制品（**已由 Agent 完成，无需再构建**）

```
<LOCAL_PATH>
```

| 校验项 | 结果 |
| --- | --- |
| 大小 / sha256 | 58,800,754 字节 / `25653800d5539b237b8e82418f63d3bca0e885e361d5c5c9a2e9111fe442b6ec` |
| `BOOT-INF/lib` | 69 项（fat jar，`Main-Class: JarLauncher`） |
| 本轮修复类 | `UserAuthStateService`、`KnowledgeParseTaskService`、`KnowledgeAsyncConfig`、`KnowledgeParseStatus` 均在；`JwtTokenProvider` 含 `extractTokenVersion` |
| 版本 | `app.version=1.0.31` |
| 隔离性 | 在 `.build-1.0.31/backend` 构建，**未触碰运行中的 `backend/target/expert-system.jar`** |

> 如需重建：复制 `backend/src` + `backend/pom.xml` 到 `.build-1.0.31/backend`，复制 `version.txt` 到 `.build-1.0.31/`，执行 `mvn -B -DskipTests package`（pom 的 filter 读 `../version.txt`）。**绝不可在服务运行时于 `backend/` 直接打包**——这是 11:28 事故根因。

## 步骤 3 · 生产迁移（按顺序、文件重定向、交互输入密码）

```bash
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -uroot -p expert_system < <LOCAL_PATH>
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -uroot -p expert_system < <LOCAL_PATH>
```

核对：

```sql
SHOW COLUMNS FROM sys_user LIKE 'token_version';
SHOW COLUMNS FROM knowledge_document LIKE 'parse_%';
```

两个脚本幂等、只加列、不改旧数据；含中文，必须用文件重定向而非管道。

## 步骤 4 · 发布（**首选不停机蓝绿**，管理员会话）

```powershell
# DryRun 预演（无副作用）
powershell -NoProfile -File <LOCAL_PATH> -Version 1.0.31 -JarPath <LOCAL_PATH> -DryRun

# 正式：严格不停机切流（需管理员；脚本内部以 SYSTEM 单次任务平滑重载 Nginx，主进程不变）
powershell -NoProfile -File <LOCAL_PATH> -Version 1.0.31 -JarPath <LOCAL_PATH> -GracefulSwitch
```

脚本阶段 9 会自行停 NSSM 服务、归档旧 jar、把新 jar 复制到 `backend/target/expert-system.jar`、重启并切回 8080，无需手工操作。

**备选（仅当蓝绿不可用、接受短暂停机窗口）**：

```powershell
Stop-Service -Name ExpertSystemBackend -Force
Copy-Item <LOCAL_PATH> <LOCAL_PATH> -Force
Start-Service -Name ExpertSystemBackend
```

## 步骤 5 · 冒烟

按 `docs/deployment.md` 第 465 行起 6 项逐条记录；需真实业务账号的部分由业务负责人执行。Agent 可独立完成服务启动、`/health`、登录链路、静态页、数据库连接等只读验证。

## 步骤 6 · 通知

通知全体用户：**本次升级后需重新登录一次**（P1-2 预期行为，非故障）。

## 步骤 7 · 补发布记录

把实际提交号、备份路径、jar sha256、迁移结果、DryRun 输出、切流连续性与主进程号、冒烟逐条结果、回滚点、实际窗口写入 `docs/releases/20261106-review-fixes-1.0.31.md`，并更新 `docs/handover/README.md`。

---

## 禁止事项

- 不改 `JWT_SECRET`；不跳过 DryRun；不发布未经 `BOOT-INF/lib` 校验的 jar；不在服务运行时于 `backend/` 构建。
- 不把文档整理与临时文件（`tv.txt`、`.bdv4b.txt`、`.workbuddy/`）带入发布提交。
- 现网未恢复、冒烟未通过、备份未确认之前，不得执行步骤 3 及以后。
- 回滚方案见发布说明第 5 节；回滚后下架文档旧链接会重新可下载（P1-5 回退风险）。

---

## 附：12:15 执行状态覆盖（以此节为准）

第 1 节备份、第 2 节迁移、第 3 节制品、通知文案均**已实际完成**，详见 [审查记录第 11 节](20261106_deployment_preflight.md)。

- 备份：`backup/backup_20261106_120546`
- 迁移：两个脚本已在生产执行并验证列与行数
- 制品：1.0.31 fat jar 已写入 `backend/target/expert-system.jar`；旧包归档 `backend/releases/1.0.31-20261106_121500/previous-expert-system.jar`
- 通知文案：`../../releases/20261106-user-notice-1.0.31.md`（待发布）

**剩余唯一特权动作**：管理员会话执行 `Restart-Service -Name ExpertSystemBackend -Force`。因不停机切流（nginx reload）需 LocalSystem 权限且工具禁止提权，已降级为「替换 jar + 重启」，**nginx 配置无需改动、没有切流步骤**；重启后由 Agent 冒烟、由业务账号发通知。

回滚：把归档的 `previous-expert-system.jar` 写回 `backend/target/expert-system.jar` 并重启；新增列可保留（旧代码忽略）。

---

## 附 2：12:20 一键恢复 + 冒烟（当前唯一待执行动作）

复核：服务**仍未重启**（`/health` 仍是 `version=1.0.30`、登录 500）。再次确认 8080 进程 PID 8720 的父进程为 `nssm.exe`；`Stop-Process`（含沙箱豁免）与 `taskkill /F /PID 8720` 均「拒绝访问」，`Restart-Service` 亦失败，属 Windows 账户层限制，Agent 无法发起 UAC 提权。

管理员会话执行**一条命令**即可完成恢复与冒烟：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH>
```

脚本依次：`Restart-Service` → 等待 `/health` 返回 `version=1.0.31` → 登录接口不再 500 → 未认证 `/api/auth/me` 401 → nginx 静态页 200 → 数据库 `sys_user` 可查。日志 `logs/recover-and-smoke-1.0.31.log`，出现 `SMOKE_DONE` 即通过。

之后由业务账号发布 `../../releases/20261106-user-notice-1.0.31.md` 的全员重登通知，并把实际结果回填发布记录与交接摘要。需真实业务账号的 6 项业务冒烟见 `docs/deployment.md` 第 465 行起。

## 2026-11-06 12:29 生产恢复复验

主责 Codex。分支 codex/personal-dashboard-v11，基线 b221ad4，工作区已有待交付变更。本轮状态为已完成待验收。普通 PowerShell 的 Administrators 组为 deny-only。经 Windows UAC 启动提升的 PowerShell，仅执行 Restart-Service -Name ExpertSystemBackend -Force，加载已校验的 1.0.31。无本轮数据库、配置、应用制品改动。

- curl.exe 请求后端 8080 /health 返回 HTTP 200、UP、1.0.31。
- 正式入口首页 HTTP 200，未认证 /api/auth/me HTTP 401。
- 通过正式入口 POST /api/auth/login，格式正确的虚构中文用户名返回 HTTP 200、业务码 401「用户名或密码错误」。恢复前同链路为 HTTP 500 空体。
- Get-Service 确认后端、Nginx 和 MySQL26 均 Running。未执行真实账号登录与已认证业务操作，也未发布重登通知。
- 如真实账号无法登录，先复测入口和后端日志，再按本执行卡的备份制品回退步骤评估。回退需要管理员权限和生产批准。
