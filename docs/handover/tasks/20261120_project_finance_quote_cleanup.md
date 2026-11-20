# 20261120 会前财务、报价归档和旧区块清理交接

- 主责 Codex。2026-11-20。已独立归档 1.0.98 财务表单生产基线 e47bcdb；后端线上构建标识原为 ce9d71f。此次候选版本 1.0.99。
- 需求和验收口径见 [需求](../../requirements/2026-11-20-project-finance-quote-cleanup.md) 与 [设计](../../design/2026-11-20-project-finance-quote-cleanup.md)。
- 交付验证包含三类项目的财务联动与字典、旧卡片和入口清理、报价确认及幂等、既有预算阶段推进和权限。
- 本次使用现有项目阶段表、报价版本表和项目文件表。应用回滚到 1.0.98 前后端制品，保留归档数据。
- 接手先核对候选构建日志、测试证据和直连/代理健康，再核对线上前端快照。生产验收需以有项目操作权限的账户查看财务阶段和预算阶段。

## 验收证据

- 本地 main 代码提交 4eba8fa。后端 Maven 定向运行 ProjectBudgetServiceTest、ProjectBudgetQuoteServiceTest、ProjectAccessRegressionTest、ProjectStorageRegressionTest 和 ScheduleSpeakerWorkflowTest，34 项通过。首轮发现旧推进测试仍假设报价文件是门禁，本轮依据现有已确认预算规则修订断言。原有状态规则保持。
- 使用本地 Edge 和隔离 API 数据对照最终前端构建。三类字段口径、单日和多日、平台公司选项、直付合同选项、修改并保存部门及地点、确认报价后刷新项目文件及旧区块消失均通过。浏览器使用模拟 API，真实报价版本关联及去重由后端测试覆盖。
- 证据位于 logs/project-finance-quote-gate-20261120.log、logs/project-finance-edge-20261120.log、logs/project-finance-build-1.0.99-20261120.log、logs/project-finance-preflight-1.0.99-20261120.log。版本前后端制品位于 builds/1.0.99-build-20261120_172334。

## 发布过程

- 生产备份基线为 backup/backup_20261120_164820。正式发布按脚本蓝绿流程启动 8082、验证代理并归档原 1.0.98 JAR。正式端口 8080 安装 1.0.99 候选后健康检查通过。
- 首次从 8082 切回 8080 时，Nginx 重载任务仍显示运行中并超时。正式脚本恢复代理至健康 8082，前端暂保持 1.0.98，首轮发布日志为 logs/project-finance-deploy-1.0.99-20261120.log。
- 恢复入口 temp/restore-primary-1099.ps1 使用已核对的发布前 Nginx 配置，仅恢复两个 upstream 并通过官方优雅重载脚本验证，保留影子实例。确认代理回到 8080 后，使用 deploy-frontend-graceful.ps1 切换前端快照。

## 暂停点

- 合并及隔离验收已完成。生产后端的 8080 和 8082 均为 UP / 1.0.99 / 4eba8fa，代理通过 8082 正常响应，前端仍为 1.0.98-project-type-finance。当前恢复进程等待 Windows UAC。
- 下一步先完成已打开的管理员授权，读取 logs/project-finance-primary-reload-1.0.99.log 并检查代理指回 8080。保留影子进程至 Nginx 确认完全切换。随后以管理员权限执行前端发布，检查入口 hash、/project/56、匿名接口 401 和两条健康端点。
- 前端发布使用 scripts/deploy-frontend-graceful.ps1 -StagedDist <LOCAL_PATH> -ReleaseId 1.0.99-project-finance-quote-20261120 -Apply。
- 后端回滚包 backend/releases/1.0.99-20261120_173211/previous-expert-system.jar 应与 1.0.98 制品校验一致。没有新增迁移，历史记录保留。前端恢复前不要重复运行全栈发布脚本。
