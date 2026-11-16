# Agent 任务交接记录

## 1. 基本信息
- 任务编号：20261111-root-cleanup-batch1
- 任务名称：根目录/散落残留清理第一档（零风险）
- 主责 Agent：CodeBuddy
- 交接时间：2026-11-11 16:45
- 当前状态：已完成待验收
- 优先级：P2

## 2. 目标与验收条件
- 业务目标：清除已失效/未引用的临时残留，恢复 AGENTS.md 0.4 目录约定（日志归 `logs/`、临时文件归 `temp/`、构建产物归 `builds/`）。
- 本次范围：仅第一档（可再生或纯日志/临时文件）。
- 不在本次范围：`backend/target-1.0.22/`、`temp/` 历史发布脚本与旧 jar、`builds/archive/`、`temp/` 内业务文档、ProjectConfig 死链、`frontend/releases`/`backend/releases` 保留策略。
- 验收条件：
  - [x] backend/frontend 根目录不再有散落 `*.log`、`$err`、`$log`、`smoke_*.json`。
  - [x] 生产运行文件 `backend/target/expert-system.jar` 未被触碰，`/health` 仍 200。
  - [x] 删除项均无脚本/代码引用，或为 gitignore 覆盖的本机产物。

## 3. 项目基线
- 分支：master；HEAD：a417c51；工作区脏（含他人未提交改动，本任务未触碰）
- 生产后端：NSSM `ExpertSystemBackend`，运行 `backend\target\expert-system.jar`（1.0.38.7）
- 核实时间：2026-11-11 16:40

## 4. 实际删除（共 47 项）
| 类别 | 明细 | 数量 | 依据 |
| --- | --- | --- | --- |
| 安全敏感 | `temp/cookies.txt`（含有效期至 2026-11-12 的 `expert_token` JWT） | 1 | 凭据残留；建议作废该测试账号 token |
| 根目录临时 | `tv.txt`（内容"2"） | 1 | 2026-11-06 报告标记临时残留；本次确认曾被 2f680ef 误提交，删除后 `git status` 显示 `D tv.txt`，需随下次提交落库 |
| 文档备份 | `docs/deployment.md.bak-20261106_160221`、`..._160331` | 2 | `*.bak-*` 不入库；`deployment.md` 为唯一事实源 |
| backend 散落日志 | `app.*.log`、`backend*.log`×12、`mvn*.log`×5、`spring-boot-run*.log` | 24 | 违反 0.4；`scripts/` 无引用；`.gitignore` 覆盖 |
| backend 冒烟输出 | `smoke_1/fix/full/pwd.json` | 4 | 脚本已改用环境变量；`.gitignore` 覆盖 |
| frontend 散落日志 | `build_out.*`、`build*.log`、`fe_build.log`、`fe-build-final.log`、`install-vueflow.log`、`npm*.log`×5、`$err`、`$log` | 14 | 构建/dev server 重定向日志，无引用 |
| 测试产物 | `backend/target/surefire-reports/` 内容（91 dumpstream + xml/txt/lst） | 171 文件（目录保留） | `mvn test` 可再生；未触碰同目录外 jar |

## 5. 未删除（刻意保留）
- `backend/target/expert-system.jar`：NSSM 运行中，删即停产。
- `backend/logs/`、`logs/`、`logs/legacy/`：由 `prune-logs.ps1` 按策略管理，不手工清。
- `temp/` 其余历史脚本/旧 jar/业务文档、`builds/`：属第二档或需负责人确认归属。

## 6. 验证
| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| backend 根目录残留计数 | `Get-ChildItem backend -File \| ? {Name -match '\.log$\|smoke_'} \| measure` | 0 |
| frontend 根目录残留计数 | 同上匹配 `\.log$\|^\$\|build_out` | 0 |
| 生产 jar 存在 | `Test-Path backend\target\expert-system.jar` | True |
| 服务健康 | `Invoke-WebRequest http://127.0.0.1:8080/health` | 200 |
| git 污染 | `git status --porcelain` | 仅 `D tv.txt`（预期） |

## 7. 风险与回滚
- 删除项均为可再生/无引用产物，无业务数据、无源码；回滚无必要。
- 唯一需注意：`tv.txt` 删除需随下次提交生效，否则他人 `git status` 仍显示删除待提交；请勿 `git checkout --` 恢复。
- `temp/cookies.txt` 中的 JWT 在 2026-11-12 到期，如需立即失效可按后端 token 版本机制（`tv` 声明）处理。

## 8. 文档同步
- [x] `docs/handover/README.md` 新增本条摘要
- [x] 本任务记录
- [ ] 无架构/部署/接口变更，其余文档不适用

## 9. 下一位 Agent 的第一步
1. 提交时把 `tv.txt` 删除一并纳入（避免遗留），不要恢复该文件。
2. 需要继续清理时从第二档开始：`backend/target-1.0.22/` **已于 2026-11-12 17:00 删除**（删前核对注册表 `HKLM\SYSTEM\CurrentControlSet\Services\ExpertSystemBackend\Parameters`：`AppParameters=-jar <LOCAL_PATH>`、`AppDirectory=<LOCAL_PATH>`，确认服务未指向该目录；57.3 MB，删后服务 Running、`/health`=UP 1.0.38.18）；`temp/` 内业务文档（`dinghuixin.doc` 等）须 Owner 确认归属后再动。
3. 未经批准禁止：删除 `backend/target/expert-system.jar`、`*releases*`、`builds/archive/.build-project-config-production-20261104/`（含发布脚本依赖的 dpapi 口令）。
