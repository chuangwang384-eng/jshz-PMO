# 企业合规 v4 生产发布记录

## 当前状态：已上线，生产技术验证通过，待业务体验复核

- 实际发布窗口：2026-11-03 21:41:58–21:44:11（北京时间）；业务代码 `c24add5`，ReleaseId `1.0.22-compliance-v4-c24add5-20261103`。
- 后端正式 JAR 已为验收候选 SHA256 `DDB6D4EB914747EEE322AD8465BA4EE6D0EC1F65A63ECC63AF6C2D58C1F0E301`，Java PID 18436（NSSM wrapper PID 39140），8081 已退出。
- Nginx 主进程发布前后均为 39504，未重启；新 root 为 `<LOCAL_PATH>`。index SHA256 `BB0F8B66AFAF1F72422660AD37A6B6E9F7B1678C4D23BA0A83EB0E4EC94B89B6`。
- 严格发布窗口探测：连接池/新建连接各 1815 次，均 0 失败；窗口 21:41:56–21:44:33，包含后端蓝绿、回切、前端切换及切换后验证。此前提权等待探测和第一次失败尝试不混入此数字。
- 生产健康/ready 均 200，匿名 v4/mode 为 401；登录页和 8 个新旧入口/合规资源均 200 且哈希匹配，浏览器无 pageerror/失败请求。
- 21:45 使用负责人提供的现有测试账号完成真实浏览器登录及 `/auth/me`、v4 模式/执行/待办、旧历史只读验收；均成功。没有切换其 V3 模式、修改密码或业务记录；仅正常登录/登出审计。临时明文凭据文件已清理。
- 新增 `compliance_user_mode` 含 JSON 历史快照列；生产两个 V4 环境开关已启用。mode 行数为 0，用户需要自行明确确认启用新版，旧待办保留。模板2/清单2/任务6/提醒2/旧事项29/旧进度5均保持发布前数量。
- 可提交脱敏证据见 [生产汇总](evidence/20261103-compliance-v4-production/production.json)、[连续探测](evidence/20261103-compliance-v4-production/continuity.json)、[只读账号验证](evidence/20261103-compliance-v4-production/authenticated-read-only.json)。未做真实业务写入、真实提醒发送或员工业务验收；这些不描述为生产验证通过。

## 提权阻塞的历史记录（21:09，后已解除）

- 主责：Codex；交接日期：2026-11-03；当前任务状态：已完成待业务验收；优先级 P1。

- 2026-11-03 用户已明确要求“生产部署上线”，继续沿用本聊天已批准的管理员提升、SYSTEM 平滑重载和前端目录切换范围。
- 业务代码候选：`c24add5`；发布 ID：`1.0.22-compliance-v4-c24add5-20261103`。前次隔离验收 36 项通过，见 [验收证据](evidence/20261103-compliance-v4/acceptance.json)。
- `Start-Process powershell.exe -Verb RunAs` 等待后返回“操作已被用户取消”；实际管理员脚本未启动，没有 production-release.log / release-state.json。
- 截至首次提权取消时未执行生产变更；之后已在同一桌面会话成功提升并按上述窗口完成发布。

## 已核实的发布前生产基线（历史快照，不是当前版本）

- 正式服务 `ExpertSystemBackend` Running；端口 8080 进程 20448；Nginx 服务 Running，主进程 39504；8081 无监听。
- Nginx 前端 root：`<LOCAL_PATH>`。
- NSSM 磁盘目标 JAR：`<LOCAL_PATH>`；SHA256 `3925BA7951CE118FE215B995C5B5D4A36F19A3BDF1936AED8A52E7587B71AEFA`。收尾核实该磁盘文件只有 605801 字节、无 Main-Class/BOOT-INF/lib，不能据其推定旧运行进程的加载产物，也不能用作回滚。新正式包已完整替换并实际启动，见下文回滚补正。
- 当前前端 index SHA256 `88E38CEA5478C9EBDDDCDD41C91CAC1412E7B1BCFD4D1EFA2A8C554275E4DF22`。
- 目标 MySQL：127.0.0.1:3306 / expert_system，26.7.0；已有七张 v4 表，字段/类型/可空性和唯一键均与验收结构匹配；缺 `compliance_user_mode`。
- 已有 2 模板、2 清单、6 任务、2 提醒（CANCELLED）；清单 1 DRAFT / 1 PAUSED；旧事项 29、个人进度 5。全部保留，不做批量搬移或自动切换用户。
- 首次没有找到用户名恰为“测试账号”的账号；负责人后来提供了现有专用账号，已完成只读验证。不伪造身份、不创建生产测试用户、不擅自切换员工模式。

## 备份、首次预检失败及脚本兼容修复

- 新备份 `<LOCAL_PATH>`：SQL 430981 字节、照片 ZIP 23653672 字节、知识文档 ZIP 3728145 字节；manifest 和三个 SHA256 均已生成。
- `backup.ps1 -Mode Verify -From ...` 的全部 SHA256/压缩内容校验通过；未执行本轮全库恢复演练。该旧脚本使用 RESULT=1 表示成功（退出码非通用约定），本轮以实际 manifest/内容校验结果核实，不仅依赖退出码。
- 重试前刷新完整备份：`<LOCAL_PATH>`，三份产物 SHA256/压缩内容校验均通过。最终环境/旧配置/JAR 备份在 `.build-compliance-v4-production-20261103/production-backup-20261103214159/`；敏感 NSSM 环境 XML 仅 SYSTEM/Administrators 可读，不入库。
- 21:32 第一次管理员尝试已完成增量 DDL/配置开关，但在后端第一阶段健康预检失败，未启动影子或切流。SQL 重复执行保护有效，原有行数保持不变。
- 复现：PowerShell 7 与 curl GET 原后端健康为200；Windows PowerShell 5.1 的 HTTP/1.1 读取报“连接被意外关闭”，HTTP/1.0 为200。不能将该客户端失败当成生产服务中断。
- 新增 `scripts/deployment-http.ps1` 仅用于本机部署 GET，固定 HTTP/1.0 / 关闭 KeepAlive、保留状态码和原始字节；后端/前端脚本共用。未改业务 HTTP 协议、TLS、允许来源或鉴权。真实 WinPS5.1 回归 health200/匿名401/前端字节200 通过；修复后严格蓝绿发布实际完成。
- 收尾元数据检查发现 WinPS5.1 原生管道默认编码将新表/JSON列的中文 COMMENT 写为问号；已按迁移脚本原文修正注释，字段类型、约束及数据未改。该修正独立双路各129次零失败探测，不并入发布窗口1815次。执行器及隔离迁移脚本已显式使用UTF-8，WinPS5原生Unicode管道测试通过；数据库注释中文已回读核实。
- 发布脚本补充影子参数 `--expert.compliance.v4-reminder-enabled=false`，与旧调度共同关闭；新增可选 `-DisableComplianceSchedulersOnRollback`，旧二进制回退前通过 NSSM 参数关闭两套调度。实际发布未进入回退分支。
- 候选 JAR SHA256 `DDB6D4EB914747EEE322AD8465BA4EE6D0EC1F65A63ECC63AF6C2D58C1F0E301`；前端 index SHA256 `BB0F8B66AFAF1F72422660AD37A6B6E9F7B1678C4D23BA0A83EB0E4EC94B89B6`。
- 原始准备文件在忽略目录 `.build-compliance-v4-production-20261103/`，不含 Git 交付的生产数据副本；SQL/ZIP 备份只保留在本机 backup。

## 本次执行器与操作顺序（审计留存，禁止同 ReleaseId 重复执行）

本次在管理员 PowerShell 中执行下述包装器，自动管理连续探测并调用后端/前端发布及页面检查；现已执行完成。后续发布须使用新的 ReleaseId、重新核查目标和审批，不得直接重复运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File <LOCAL_PATH>
```

执行器将：核对候选哈希/连续探测（超过5秒未更新即拒绝）→备份当前 JAR、Nginx 与受限 ACL 的 NSSM 环境→两份增量迁移→保留原凭据并增加两个 V4 开关→关闭调度的 8081 影子→SYSTEM 平滑切流→正式 8080 新 JAR→切回/排空→不可变前端 root→核对主进程/健康/哈希。配置凭据从已有 NSSM 环境加载，不放入参数或聊天。

最初等待提权时仅做发布前探测，各4836次零失败，不是切流验证；最终切流证据为本记录开头的各1815次。所有本次探测进程均已停止。

本地 probe/runner 是受控运行产物，不在 Git 中；重新生成或接手时必须先阅读 [部署说明](../deployment-compliance-v4.md)，审查脚本、确认源提交/哈希及实际目标。没有管理员令牌时不得尝试绕过 Windows 权限。

## 回滚产物补正与发布护栏

- 发布前漏查旧磁盘 JAR 的可启动性，收尾才发现归档 `previous-expert-system.jar` 无启动清单/依赖；保留作证据，**禁止用它回滚**。此次新包启动成功，未执行旧包自动回退，未造成已观测的可用性失败。
- 已在 `backend/releases/compliance-v4-c24add5-20261103_214307/` 额外保存 `current-expert-system.jar`（本次已启动的完整包，SHA256 DDB6...0E301）及 `known-good-review-fixes.jar`（15:37已验证的完整基线，SHA256 `6F62438EB5D2C7B13466F044A0709825F7F0D1B31280EA327D9AAF0FC400E278`）。两者均含 JarLauncher 及72个依赖。普通回退优先保留当前兼容后端、关闭V4开关/回退前端；旧基线必须另行批准并关闭两套调度、核对用户模式和写入兼容。
- 新增 `deployment-artifact.ps1`，在后端服务变更前验证候选与回滚源为完整可启动 Boot 包；可通过 `-RollbackJarPath` 显式指定经批准的基线。无启动清单/缺依赖包拒绝发布。真实坏包/缺文件拒绝测试、有效包接受及完整 DryRun 均通过，未为了测试再切一次生产。
- 正式服务固定使用 `backend/target/expert-system.jar`；禁止在该目录运行 `mvn package/clean` 覆盖或清理正式包。所有后续构建必须放在独立目录，再以不可变候选发布。

## 下一步与回滚边界

1. 负责人复核真实业务体验；员工需要明确点击启用新版，旧记录只读保留，不批量替员工确认。
2. 技术只读上线检查均已完成；实际生产创建/发布模板/开始清单/发送提醒等业务写入未执行，不能混同为员工验收通过。
3. 后续 Agent 先检查实际服务、配置、哈希和用户模式，不能照搬此时点 PID 或直接重跑同 ReleaseId。
4. 不删除八张表/模式标记/历史快照；回退旧二进制必须关闭两套调度。具体限制见 [部署与回滚](../deployment-compliance-v4.md)。
