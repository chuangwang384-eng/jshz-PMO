# 发布说明 v1.0.31（REVIEW-FIX-20261106）· 待发布

> **状态：待发布（未执行）**。负责人已批准上线，指定版本号 `1.0.31`、维护窗口「现网恢复与冒烟通过后立即」。
> 基线：分支 `codex/personal-dashboard-v11`，HEAD `b221ad4`；`version.txt` 已由 `1.0.30` 更新为 `1.0.31`。
> 本文件是发布准备材料，**不代表已部署、已上线或已业务验收**。完整审查与事件记录见 `../handover/tasks/20261106_deployment_preflight.md`。

---

## 1. 变更范围（仅后端，前端零改动）

| 编号 | 内容 | 影响 |
| --- | --- | --- |
| P1-2 | JWT 增加 `tv` claim 与 `sys_user.token_version` 比对；过滤器校验用户存在/启用/版本一致，查库异常 fail-closed；停用/删除/改密/改角色/企微离职/重新启用时自增版本并清缓存；改密后回写当前会话 Cookie | **全体存量 Token 失效，需重新登录一次** |
| P1-5 | 知识文档文件流入口统一 `requireDownloadable`：不存在 404、下架且非维护人 403 | 下架文档旧链接不再可下载 |
| P2-7 | 上传/更新改为流式落盘 + `PENDING` 异步解析；`id+version` 条件更新；新增解析状态字段与线程池 | 上传响应不再同步返回正文抽取结果 |
| P2-9 | 移除零引用 `spring-boot-starter-validation`；其余依赖告警判定为误报 | 无接口/数据影响 |
| 运维 | `scripts/backup.ps1` 修正 Drill 成功/失败退出码反转，逐表 COUNT 改为单条 `UNION ALL` | 恢复演练结论可信 |
| 配置 | `expert.auth.state-cache-seconds=60`、`expert.knowledge.parse.{core-pool-size,max-pool-size,queue-capacity}=2/4/100` | 均有默认值，生产可不改 |
| 文档 | 架构/部署/交接/施工/审查报告同步；docs 目录整理（不参与运行时） | — |

## 2. 数据库迁移（按顺序执行，均幂等）

1. `database/migration/20261106_add_user_token_version.sql` → `sys_user.token_version INT NOT NULL DEFAULT 0`
2. `database/migration/20261106_add_knowledge_parse_status.sql` → `knowledge_document.parse_status / parse_message / parse_time`

执行要求：**先备份**；使用 `mysql --default-character-set=utf8mb4`；含中文的脚本用**文件重定向**而非 PowerShell 管道；脚本只加列、不改旧数据，可重复执行。凭据由受控环境注入，禁止写入仓库或日志。

## 3. 上线影响（需提前通知）

- 升级前签发、不含 `tv` 的存量 Token 一律 401，**全体已登录用户需重新登录一次**（预期行为，非故障）。`JWT_SECRET` 必须保持不变。
- 停用/改密/改角色后：单实例下一次请求即失效；多实例最迟 60 秒（`expert.auth.state-cache-seconds`）。
- 知识文档上传返回 `parseStatus=PENDING`，随后异步变为 `RUNNING/SUCCESS/FAILED`；解析失败不影响上传结果，原因见 `parseMessage`。
- 线程池队列满采用 AbortPolicy：上传仍成功，文档被标记 `FAILED`，当前无重试接口。

## 4. 验证清单

发布前（见 preflight 第 5 节，共 10 项）：现网恢复与登录冒烟、负责人批准、备份、迁移、提交拆分、版本号、fat jar 校验、DryRun、冒烟、全员通知。

发布后冒烟（逐条记录结果，见 `docs/deployment.md` 第 465 行起）：

1. `SHOW COLUMNS FROM sys_user LIKE 'token_version'`、`SHOW COLUMNS FROM knowledge_document LIKE 'parse_%'` 有结果。
2. 旧 Cookie 访问 `/api/auth/me` → 401；重新登录 → 200。
3. 停用测试账号 → 其下一次请求 401；重新启用并登录后恢复。
4. 改密 → 当前会话保持登录（新 Cookie），其他会话旧 Cookie 401。
5. 上传 PDF/DOCX → `PENDING → SUCCESS`、`content` 非空；`enterprise-compliance` Excel 生成合规草稿。
6. 下架文档：普通账号文件链接业务码 403、维护人 200；删除后 404。

> 需真实业务账号的冒烟由业务负责人执行；Agent 可独立完成服务启动、`/health`、登录链路不再 500、静态页等只读验证。

## 5. 回滚

- 回退后端包；新增列可保留（旧代码忽略 `tv` / `parse_*`），无需删列。
- 回滚后旧代码不校验 `tv`，新 Token 仍可解析，不会造成登录中断。
- `PENDING/RUNNING` 解析记录旧代码不继续处理，可保留或人工置空。
- **风险**：回滚后下架文档旧链接会重新可下载（P1-5 回退）。
- P2-9 回滚 `pom.xml` 即恢复。

## 6. 未执行声明

截至本文件生成时：1.0.31 fat jar 已在隔离目录 `.build-1.0.31/backend` 构建并校验（**未部署**）、生产迁移未执行、DryRun 未跑、发布脚本未执行、6 项冒烟未执行、全员通知未发、现网服务尚未恢复。不停机蓝绿方案评估见 [上线执行卡](../handover/tasks/20261106_release_runbook_1.0.31.md) 第 0 节。任一环节未完成不得将本次发布描述为「已上线」。

---

## 附：12:15 执行状态更新

以下环节**已实际完成**：上线前备份（`backup/backup_20261106_120546`）、两个生产加列迁移（列与行数已验证）、1.0.31 fat jar 就位到服务加载路径（旧包已归档）、全员重登通知文案（`20261106-user-notice-1.0.31.md`，待业务账号发布）。

**仍未完成**：服务重启（需管理员）、发布后 6 项冒烟、系统公告发布、发布记录回填。运行中的 JVM 仍是损坏的 1.0.30，登录链路 500；生产库已领先于代码但兼容。不得将本次发布描述为「已上线」。
