# 1.0.47 会议预算估算与对客报价发布

2026-11-17 09:06 CST，主责 Codex。状态为已发布待业务验收。开发分支已快进合入本地 main，发布提交 `b1070ee`，发布前线上为 `1.0.46 / 6370c0b`。

| 项目 | 结果 |
| --- | --- |
| 范围 | 线下估算草稿和管理员收费标准标识、线上数量录入、独立确认保留明细、对客 Excel 版本和确认记录 |
| 构建 | `scripts/build-all.ps1` 通过，候选 `builds/1.0.47-build-20261117_085955`，JAR 的版本和提交与候选一致 |
| 备份 | `backup/budget-1.0.47-20261117/backup_20261117_085955`，备份脚本 Verify 通过，另归档项目资料库并全量读取 ZIP、保存 SHA256 |
| 数据库 | 仅执行并登记 `20261117_budget_estimate_quote.sql`，确认估算 JSON 列和报价版本表，新基线及生产业务表清单一致。旧台账的 3 项待登记和 18 项漂移继续保留 |
| 后端 | 蓝绿发布 11 阶段通过，直连 8080 和经 Nginx 健康接口均为 `UP / 1.0.47 / b1070ee`，NSSM 为 Running |
| 前端 | Nginx root 切到 `frontend/releases/1.0.47-budget-quote-20261117`，线上入口哈希一致，master PID 7696 保持。新主包 `index-Dhp_3ZIB.js` 和旧主包 `index-BD4elGqA.js` 均为 200 |
| 鉴权 | 匿名预算、报价列表和报价下载接口均返回 401 |
| 业务验收 | 隔离 MySQL 已验证报价 V1/V2、阶段挂载、客户确认和另存草稿。生产登录浏览器连接超时，生产业务实单验收待执行 |

发布日志在 `logs/20261117-budget-1.0.47-deploy.log`。仓库另保留构建、备份和预演日志。架构及接口见 [估算与对客报价交接](../handover/tasks/20261117_budget_estimate_quote.md)。

## 回滚

1. 用 `frontend/release-metadata/1.0.47-budget-quote-20261117.rollback.conf.txt` 切到保留新旧哈希资产的旧入口，再做 Nginx graceful reload 和首页校验。
2. 用管理员 PowerShell 和生产同源凭据执行 `scripts/deploy-backend.ps1 -JarPath backend/releases/1.0.47-20261117_090446/previous-expert-system.jar -Version 1.0.46 -GracefulSwitch`，核查直连和 Nginx 返回 `1.0.46 / 6370c0b`。
3. 应用回滚保留增量 DDL、台账和已生成报价。生产数据恢复从本次备份独立评估。

下一步用授权账号打开测试项目，执行估算预览、内部确认、Excel 下载和客户确认。确认后回填业务验收结果。
