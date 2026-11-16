# 交接记录：docs 失效文档清理 + prune-all 散落文件告警（2026-11-13）

## 1. 基本信息
- 任务编号：20261113-docs-cleanup-prune-alert
- 主责 Agent：AI 助手（当前会话）
- 交接时间：2026-11-13
- 当前状态：已完成待验收
- 优先级：P2

## 2. 目标与验收条件
- 目标：清理 `docs/` 中「记录的功能已失效/过期、可能误导 agent」的文档；为 `scripts/prune-all.ps1` 增加 temp 根目录散落文件数量告警（阈值 160）。
- 验收条件：
  - [x] 删除项均有实证（当前技术栈/目录/端口/发布方式与文中陈述冲突，或指向已不存在目录）
  - [x] 删除后可回滚（git 历史保留），有审计记录
  - [x] `prune-all.ps1` 能统计 temp 根目录散文件数，超阈值输出 `[WARN]`，不影响退出码
  - [x] 两条路径实测（正常阈值不告警 / 低阈值触发告警）

## 3. 项目基线
- 分支：codex/personal-dashboard-v11；发布态 1.0.38.24（后端 8080，Nginx root 指向 `frontend/releases` 快照）
- 事实源：`version.txt`=1.0.38.24；`docs/deployment.md` 为唯一部署真源；数据库 MySQL `expert_system`（74 表）
- 核实时间：2026-11-13

## 4. 删除清单（7 项，均 `git rm`，历史可回溯）

| 文件 | 大小 | 删除理由 | 关键证据 |
| --- | --- | --- | --- |
| `docs/deployment-prod.md` | 11.3 KB | 整篇为 10-22 时点的旧上线手册，与现行发布方式冲突且以"生产部署手册"口吻陈述 | L15「Nginx(:80, 托管 `frontend/dist`)」；L33「Nginx：**尚未安装**」；L85「备份旧 dist → npm run build → nginx -s reload」。现状：Nginx 已装，root 指向 `frontend/releases/<ReleaseId>`（deployment.md） |
| `docs/guides/2026-10-20-内网数据查询接口-使用说明.md` | — | 整篇使用说明指向已删除的 Python Demo 服务（8000 端口 / SQLite），照做必失败 | L26「Demo（当前）http://<内网IP>:8000 由 demo/server.py 提供」；L29「`http://127.0.0.1:8000`」。实测仓库根 `demo/` 不存在 |
| `docs/design/2026-10-20-专家库系统SQLite升级与功能增强-设计文档.md` | 5.9 KB | 以「已批准，进入实施」陈述 SQLite 技术栈与 demo 库路径，与现行 MySQL 冲突 | L16「方案 B（底层升级 SQLite + Demo 增强）」；L30「数据库文件：demo\data\experts.db」 |
| `docs/design/2026-10-20-专家库系统UI精简-设计文档.md` | 5.4 KB | 「已批准，待实施」但改动目标文件是已删除的 Demo 静态页 | L17「涉及文件：demo\static\index.html、admin.html」；L26「后端 SQLite experts.db」 |
| `docs/design/2026-10-20-专家库照片显示修复与顶部吸附工具栏-设计文档.md` | 4 KB | 改动清单全部指向已删除的 demo\static 与 demo\uploads | L78「`<LOCAL_PATH>` 新增 .sticky-bar…」；L20「文件全部存在于 demo/uploads/」 |
| `docs/superpowers/plans/2026-10-22-admin-expert-ops-nginx-prod-plan.md` | 4.8 KB | 未勾选的任务式计划，直接指导 dist 覆盖式发布（已被快照发布取代），会误导 agent 执行 | L47「root `<LOCAL_PATH>`」；L52「编写 docs/deployment-prod.md…」 |
| `docs/superpowers/specs/2026-10-22-admin-expert-ops-nginx-prod-design.md` | 7.6 KB | 「状态：已批准」的方案 A 明文规定 80 端口托管 dist，与当前机制冲突 | L15「状态：已批准」；L61「80 端口托管 `frontend/dist` + 反代」 |

- 删除后 `docs/**/*.md` 剩余 **250** 个。
- 无 `.ps1`/`.json`/README/AGENTS 引用这些文件（仅历史交接记录 `docs/handover/tasks/20261106_docs_organization.md` 与 `.workbuddy/memory` 内有历史链接，属历史留档，不回改）。

## 5. 保留但含过时陈述的文件（历史留档，未改内容）

以下文件按"历史记录"性质保留，但含与现状冲突的陈述，后续阅读需以 `docs/deployment.md` 为准：

| 文件 | 过时陈述 |
| --- | --- |
| `docs/reviews/2026-11-06-全项目代码审查与目录清理排查报告.md` | 「frontend/dist 为线上站点根」（11-03 起已改快照 root）；提及已不存在的 `demo/` |
| `docs/reviews/2026-10-26-jshz-PMO项目技术摸底-调研报告.md` | 「nginx 配置指向 frontend/dist」 |
| `docs/design/2026-11-02-审批流配置板块改造-施工蓝图.md` | 「实际生效 … 80 → root frontend/dist」 |
| `docs/requirements/2026-10-20-专家库系统正式版部署-需求文档.md` | 「当前 Demo 版仅依赖 Python 3.11 标准库」 |
| `docs/superpowers/specs/2026-10-26-knowledge-planet-format-display-design.md` | 「npm run build 后重启 Nginx 生效（覆盖 dist）」 |
| `docs/design/2026-10-20-专家库系统正式版开发-设计文档.md` | Demo 期描述，但含「SQLite → MySQL 迁移」正确记录，保留 |

## 6. prune-all.ps1 变更

- 新增参数：`-ScatterThreshold`（默认 **160**）、`-SkipScatterCheck`。
- 新增 `Get-TempScatter` 函数：统计 `temp\` **根目录散文件**（不含子目录）数量、扩展名分布、最近修改 10 个文件。
- 在临时产物清理后执行检查：超阈值输出 `[WARN]` + 类型分布 + 最近修改清单，写入 `logs\prune-<时间戳>.log`；**只告警不删除**，退出码不变；末尾汇总 `[WARN] … 不影响退出码`。
- 编码：保持 UTF-8 **带 BOM**（EF BB BF，PS 5.1 中文必需），语法解析通过。

## 7. 验证（真实执行）

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 语法/BOM | `Parser::ParseFile` + 读首 3 字节 | SYNTAX OK；BOM=239,187,191 ✓ |
| 正常阈值（160） | `prune-all.ps1 -DryRun -SkipLogs` | `散落文件数: 9（阈值 160）; 类型分布: .jpg=3,.png=3,.docx=2,.doc=1` → `[OK] 未超阈值`；退出码 0 ✓ |
| 告警路径（阈值 5） | `prune-all.ps1 -DryRun -SkipLogs -ScatterThreshold 5` | `[WARN] temp\ 根目录散落文件 9 个，超过阈值 5…` + 最近修改清单 + 末尾告警汇总；退出码 0 ✓ |
| 文档删除 | `git rm` 7 文件 | 全部成功，md 剩余 250 ✓ |
| 引用排查 | 检索 6 个文件名（排除 temp/builds/releases） | 生产脚本与 README/AGENTS 零引用 ✓ |

## 8. 风险与回滚

- 文档删除：`git revert <本次提交>` 或 `git checkout <commit> -- <path>` 可完全恢复。
- 告警为只读统计，不改变任何清理行为；如需关闭用 `-SkipScatterCheck`。
- 已知：`docs/handover/tasks/20261106_docs_organization.md` 内保留指向已删文档的历史链接（历史留档，不追改）。

## 9. 文档同步

- [x] `docs/deployment.md`（八之二节补充散落文件告警说明）
- [x] 本交接记录
- [x] `docs/handover/README.md` 摘要
- 未涉及：数据库/接口/权限/前端页面变更（无）

## 10. 下一位 Agent 的接手指令

1. 首先检查：`git log -1`、`docs/deployment.md` 八之二节、`scripts/prune-all.ps1 -DryRun`。
2. 然后执行：月度清理前确认 temp 散落文件告警是否触发，触发则按 AGENTS.md 0.4 归类到 `temp\archive\` 或走批准清理流程。
3. 验收标准：`prune-all.ps1` 输出散落文件数与阈值判断；docs 中不再存在指向 `demo/` 或 dist 发布方式的"当前指引"。
4. 未经确认禁止：批量删除 docs 下历史记录（release/reviews/testing evidence）。
