# 客户信息四项需求交接

## 状态和基线

- 任务 20261124_customer_info_features，主责 Codex，2026-11-24，已完成待验收。
- 工作分支 codex/customer-info-fixes，基于 main 543d9b1，在上一轮客户信息修复的独立工作区继续开发。主工作区原有部署文档和发布元数据改动。
- 业务范围为客户列表、完整档案及关系网络。用户确认履历与基本资料独立，旧档案从空履历开始。

## 需求与验收

| 需求 | 操作与期望 |
| --- | --- |
| 新增疑似重复 | 在新增档案中填写与有效档案一致的姓名及单位，按保存时展示候选姓名、单位和现任职务。选择打开候选跳转已有档案，选择仍然新增才提交。单位为空时无重复提醒。历史数据保持原样，提醒属于人工确认。 |
| 列表筛选 | 输入单位关键字、选择关系类型和有无有效关系，按组合条件查看列表。分页总数与筛选一致。详情和关系网返回时保留筛选及页码，重置清空。两端有效才计入。关系类型筛选与无有效关系同时选择时结果为空。 |
| 关系网定位 | 在全局图点击按姓名或单位定位，用姓名或单位查询并选择结果。超过全局展示上限的有效客户仍可定位，进入该客户的一度关系圈。可切回全局或返回原列表。 |
| 任职履历 | 在完整档案维护单位、职务和起止月份，按开始月份倒序查看。开始空值显示未知，结束空值显示至今，结束早于开始时拒绝。普通登录账号只读，维护账号可增改删。删除单条履历需确认。编辑履历后基本资料保持原值。 |

## 接口和数据

- GET /api/customer 增加可选 workUnit、relationTypeId、hasRelations 参数。单位在基本档案的 workUnit 模糊匹配，其他参数与原搜索及删除状态取交集。
- GET /api/customer/duplicates 接收 name、workUnit，要求 customer:manage，按去掉首尾空白后的姓名及单位匹配有效档案，返回 ID、姓名、单位、现任职务候选。POST /api/customer 沿用既有的新增权限和业务校验。
- GET /api/customer/relations/search-targets?identityOnly=true 只以姓名或单位作为关键字字段，沿用有效客户分页搜索。省略参数时保留原搜索行为。
- GET /api/customer/{id}/employment 登录可读。POST /api/customer/{id}/employment、PUT 和 DELETE /api/customer/{id}/employment/{employmentId} 要求 customer:manage，写操作审计记录引用客户 ID 和履历 ID。访问已删除档案或跨客户修改履历时失败。
- 新表 customer_employment 使用客户外键，start_month 和 end_month 为可空的 YYYY-MM。无自动回填、无配置或依赖变更。权限码保持原有 customer:manage。
- 前端改动在 CustomerInfo、CustomerDetail、CustomerRelationGraph、CustomerSelectDialog、CustomerEmploymentHistory、customer API 及查询参数辅助函数。后端改动在 CustomerController、CustomerService、CustomerRelationService 及履历服务和数据层。

## 验证证据

- JDK 17 执行 mvn.cmd -f backend/pom.xml -Drevision=1.0.134 -DgitShort=543d9b1 -Dtest=CustomerFeaturesTest,CustomerInfoRegressionTest,StorageAdminServiceTest test。28 项通过，含本次 6 项，覆盖重复匹配、组合筛选、定位范围、履历数据校验与权限拒绝。
- npm.cmd run build -- --outDir <LOCAL_PATH> --emptyOutDir 通过。Vite 提示部分包超过产物体积提示阈值。
- 修改前在主工作区执行 scripts/check-schema-parity.ps1，生产与原基线一致。修改后在隔离工作区以 -NoDatabase 检查迁移已并入新库基线，通过。
- 使用本机 MySQL 独立数据目录及回环端口执行 temp/customer-features-mysql.ps1。新库初始化、旧库迁移连续执行两次、两者 SHOW CREATE TABLE 一致。合成数据验证重复匹配、组合关系筛选、姓名或单位搜索、月份可清空及履历不改变基本资料。
- Edge 使用 Playwright 加载构建产物和模拟 API。运行 temp/customer-features-edge.cjs，验证打开疑似重复档案与确认新增、列表组合参数及页码往返和重置、定位到图外客户、履历增改删与至今展示。
- git diff --check 通过。未执行真实账号连接 Spring Boot 与 MySQL 的端到端验收，未执行生产迁移或发布。

## 发布和回滚

1. 下一位接手者先核对任务分支和验收步骤。涉及生产部署时，使用当前版本重新做生产预检，并先取得发布授权。
2. 对既有库按项目迁移流程执行 database/migration/20261124_customer_employment.sql，确认 customer_employment 存在且登记成功，再切换配套前后端。新环境由 database/init.sql 一次建表。迁移在已存在 customer 表的 MySQL 8+ 运行，可重复执行。
3. 需要回滚时配套恢复前后端，保留新增表和业务数据。无需降表，已录入或已删除的履历不会随应用版本回滚恢复。
4. 文档已同步架构、部署及本交接的验收与接口说明。生产发布、登录账号端到端业务验收待执行。
