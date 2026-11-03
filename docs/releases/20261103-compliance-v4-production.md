# 企业合规 v4 生产发布记录

## 当前状态：生产发布被 Windows 管理员提升阻塞

- 主责：Codex；交接日期：2026-11-03；任务状态：已阻塞；优先级 P1。

- 2026-11-03 用户已明确要求“生产部署上线”，继续沿用本聊天已批准的管理员提升、SYSTEM 平滑重载和前端目录切换范围。
- 业务代码候选：`c24add5`；发布 ID：`1.0.22-compliance-v4-c24add5-20261103`。前次隔离验收 36 项通过，见 [验收证据](evidence/20261103-compliance-v4/acceptance.json)。
- `Start-Process powershell.exe -Verb RunAs` 等待后返回“操作已被用户取消”；实际管理员脚本未启动，没有 production-release.log / release-state.json。
- **尚未执行生产 DDL、服务环境开关修改、后端发布或 Nginx 切流。不能据本记录认定已上线。**

## 已核实生产基线（本次只读）

- 正式服务 `ExpertSystemBackend` Running；端口 8080 进程 20448；Nginx 服务 Running，主进程 39504；8081 无监听。
- Nginx 前端 root：`<LOCAL_PATH>`。
- NSSM 正式 JAR：`<LOCAL_PATH>`；SHA256 `3925BA7951CE118FE215B995C5B5D4A36F19A3BDF1936AED8A52E7587B71AEFA`。
- 当前前端 index SHA256 `88E38CEA5478C9EBDDDCDD41C91CAC1412E7B1BCFD4D1EFA2A8C554275E4DF22`。
- 目标 MySQL：127.0.0.1:3306 / expert_system，26.7.0；已有七张 v4 表，字段/类型/可空性和唯一键均与验收结构匹配；缺 `compliance_user_mode`。
- 已有 2 模板、2 清单、6 任务、2 提醒（CANCELLED）；清单 1 DRAFT / 1 PAUSED；旧事项 29、个人进度 5。全部保留，不做批量搬移或自动切换用户。
- 没有找到用户名恰为“测试账号”的启用账号。登录态只读验收已向负责人询问可用测试账号或由其上线后复核；不伪造身份、不创建生产测试用户、不擅自切换员工模式。

## 备份与准备证据

- 新备份 `<LOCAL_PATH>`：SQL 430981 字节、照片 ZIP 23653672 字节、知识文档 ZIP 3728145 字节；manifest 和三个 SHA256 均已生成。
- `backup.ps1 -Mode Verify -From ...` 的全部 SHA256/压缩内容校验通过；未执行本轮全库恢复演练。该旧脚本使用 RESULT=1 表示成功（退出码非通用约定），本轮以实际 manifest/内容校验结果核实，不仅依赖退出码。
- 后端严格 `-GracefulSwitch` DryRun 和前端只读 plan 通过；没有实际切流。
- 发布脚本补充影子参数 `--expert.compliance.v4-reminder-enabled=false`，与旧调度共同关闭；新增可选 `-DisableComplianceSchedulersOnRollback`，旧二进制回退前通过 NSSM 参数关闭两套合规调度，防止恢复旧提醒。AST 及 DryRun 已通过，真实发布尚未执行。
- 候选 JAR SHA256 `DDB6D4EB914747EEE322AD8465BA4EE6D0EC1F65A63ECC63AF6C2D58C1F0E301`；前端 index SHA256 `BB0F8B66AFAF1F72422660AD37A6B6E9F7B1678C4D23BA0A83EB0E4EC94B89B6`。
- 原始准备文件在忽略目录 `.build-compliance-v4-production-20261103/`，不含 Git 交付的生产数据副本；SQL/ZIP 备份只保留在本机 backup。

## 已准备的执行器与操作顺序

在**管理员 PowerShell** 中执行本机已准备包装器（不能直接照搬旧 PID）。它会自动归档旧探测、启动新的连续探测窗口、拒绝过期/失败探测，调用发布执行器及只读页面检查，并停止本任务的探测：

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File <LOCAL_PATH>
```

执行器将：核对候选哈希/连续探测（超过5秒未更新即拒绝）→备份当前 JAR、Nginx 与受限 ACL 的 NSSM 环境→两份增量迁移→保留原凭据并增加两个 V4 开关→关闭调度的 8081 影子→SYSTEM 平滑切流→正式 8080 新 JAR→切回/排空→不可变前端 root→核对主进程/健康/哈希。配置凭据从已有 NSSM 环境加载，不放入参数或聊天。

当前窗口仅完成发布前探测：连接池与新建连接各 4836 次，均零失败；因管理员提升未成功，**这不是发布过程不停机的验证**。探测已停止，避免后台长期占用；包装器和执行器的 AST 均通过，尚未实际执行。

本地 probe/runner 是受控运行产物，不在 Git 中；重新生成或接手时必须先阅读 [部署说明](../deployment-compliance-v4.md)，审查脚本、确认源提交/哈希及实际目标。没有管理员令牌时不得尝试绕过 Windows 权限。

## 下一步与回滚边界

1. 处理 Windows UAC / 管理员令牌问题后，再重新核对备份时点和实时业务变更，启动新的零失败探测窗口并执行发布。
2. 上线后运行已准备的 `production-check.mjs`，验证新 index、健康/ready、匿名 401、登录页和新旧资源哈希；登录态验收按负责人提供的现有测试方式或其人工复核完成。
3. 记录每阶段真实结果、Nginx 主进程、后端 PID、前后哈希、探测数量/失败及业务验收状态；更新本记录后才可标记“已上线”。
4. 不删除八张表/模式标记/历史快照；回退旧二进制必须关闭两套调度。具体限制见 [部署与回滚](../deployment-compliance-v4.md)。
