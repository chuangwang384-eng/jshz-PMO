# 客户信息 1.0.135 发布交接

- 任务日期 2026-11-24，主责 Codex。合并已完成，生产发布待管理员执行。
- 客户信息修复及新增四项需求随 `584910e` 快进合入本地 main。版本源、前端元数据和锁文件均为 1.0.135。主工作区已有的 1.0.134 文档修改及发布元数据保留在工作区。
- 发布目标 1.0.135-customer-info。生产 8080 和经 Nginx 健康检查实测均为 1.0.134，提交 543d9b1，前端根为 frontend/releases/1.0.134-compliance-shared-assets。
- db-migrate -Mode Status 显示 71 项已登记、1 项待执行、0 项漂移。待执行脚本仅 20261124_customer_employment.sql。默认发布预检在待迁移检查处中止。
- 自动审批拒绝 Start-Process -Verb RunAs，理由为 blocked by policy。当前终端无管理员令牌。1.0.135 的生产备份、迁移和切流未启动。

## 验证

- 源码提交 584910e 使用 JDK 17 和 Maven 复跑 CustomerFeaturesTest、CustomerInfoRegressionTest、StorageAdminServiceTest，28 项通过。日志 logs/customer-1.0.135-test.log。
- 原功能验收包括前端构建、隔离 MySQL 新库和升级验证、Edge 模拟接口验收。见 [功能交接](../handover/tasks/20261124_customer_info_features.md)。
- 正式 build-all 构建通过。制品目录 builds/1.0.135-build-20261124_093058，后端 build-info 为 1.0.135 / 584910e，客户 Controller、履历 Service 和 Mapper 均在包内。JAR SHA-256 为 96E3B36E0361E7F9FCDF1CE519940D3AE10B7E775BDEFABF0E04CAB6FBF1FA8E。使用正式前端 dist 的 Edge 模拟接口验收通过。

## 继续发布

1. 管理员 PowerShell 运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File <LOCAL_PATH>`。入口先核对提交、生产健康和待迁移清单，再执行全量备份、迁移、配套构建和前后端平滑发布。
2. 核对 orchestration 记录中的 CUSTOMER_BACKUP、logs/release-state.json 及对应发布日志。若迁移已完成而发布中止，先核对生产实际版本与指针、备份及迁移登记，按发布流程从安全阶段继续。发布后从 Nginx 和 8080 核对 1.0.135，检查线上首页和主包、客户 API 的登录要求，并用获准账号完成客户业务验收。
3. 应用回滚时按正式全栈回滚流程还原 1.0.134 前后端，保留新增履历表和已录入数据。
## 上线与验收更新（2026-11-24）

- 项目负责人从管理员 PowerShell 完成 1.0.135-customer-info 发布。发布状态 SUCCESS，完成时间 09:41:44。迁移前全量备份为 backup/backup_20261124_093502。
- 实测后端直连和 Nginx 均为 1.0.135 / 584910e；前端发布根为 frontend/releases/1.0.135-customer-info，首页与入口脚本 HTTP 200。匿名客户 API 返回 401。
- 履历迁移已登记，72 项已登记、0 项待执行、0 项漂移，生产库与 init.sql 业务表清单一致。
- 用正式发布 JAR 与隔离 MySQL 通过 10 项真实接口验收。用正式前端制品与模拟接口通过页面验收，客户页面脚本与线上发布目录 SHA-256 相同。生产登录账号业务复验未执行，验收未写入生产档案。
- 证据目录 E:/王创/验收证据/20261124-客户信息-1.0.135，入口文件 00-验收报告.md。
