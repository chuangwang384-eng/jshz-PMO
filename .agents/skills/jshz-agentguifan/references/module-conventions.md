# PMO 模块参照与检查项

以下路径相对 PMO 仓库根目录。参考实现基线为 `388a01e`。开工时确认路径和模式仍适用。客户与供应商模块的业务授权仅适用于各自模块。

| 要找的模式 | 当前仓库参照 |
| --- | --- |
| 客户档案、关系类型与公共字典 | `backend/src/main/java/com/jshz/expert/controller/CustomerController.java`、同 Java 根下的 `service/CustomerService.java`、`dto/CustomerForm.java`、`entity/Customer.java`、`mapper/CustomerMapper.java` |
| 多表业务和删除回归 | 同 Java 根下的 `controller/SupplierController.java`、`service/SupplierService.java`，以及 `backend/src/test/java/com/jshz/expert/service/SupplierDeletionRegressionTest.java` |
| Vue API、列表与详情 | `frontend/src/api/customer.js`、`frontend/src/api/request.js`、`frontend/src/views/CustomerInfo.vue`、`frontend/src/views/CustomerDetail.vue`、`frontend/src/views/supplier/SupplierList.vue` |
| 权限声明与执行 | `database/migration/20261029_add_customer_module.sql`、`database/migration/20261102_customer_permission_consolidation.sql`、同 Java 根下的 `security/DbPermissionFilter.java` 和 `service/PermissionService.java`，以及 `frontend/src/router/index.js`、`frontend/src/store/auth.js`、`frontend/src/directives/permission.js` |

## 迁移与数据

- 从当前表定义、迁移记录和调用方核对列类型、默认值、唯一性、索引及历史数据影响。结构改动先在 `database/migration/` 写增量，再同步 `database/init.sql` 的新库定义与所需初始数据。新库用初始化基线，既有库用增量迁移。已登记的迁移以校验和追踪，后续调整使用新脚本。
- 用仓库 `scripts/db-migrate.ps1` 和 `scripts/check-schema-parity.ps1` 的当前说明确认模式、目标和权限。结构基线校验按仓库要求执行。测试迁移使用隔离库，核实旧数据保留及预期回填，并记录重放策略。
- 主子表操作沿用同类模块的事务和删除语义。按需要核对更新、归档、恢复和关联引用。

## 后端

- 在现有包结构中按改动加 Controller、Service、DTO、Entity 和 Mapper。按同类接口沿用请求校验、异常语义、`ApiResponse` 与分页契约。更改共享契约前查调用方。
- 验证实际入口、分页或筛选、非法参数及受影响的既有行为。状态、范围或角色约束落实到对应后端入口和服务。

## 页面和权限

- 用现有请求封装、Vue 和 Element Plus 组件及样式变量。页面入口按路由与布局规则接线，核对列表、详情、编辑或新增中实际受影响的状态。
- 从动作反查 HTTP 方法、路径、后端拦截与数据范围、权限定义和角色绑定、前端路由及按钮。每项按业务需求确定角色和范围。页面可见、按钮可用、接口允许是三项独立验收。
- 检查直接访问路由与直接调用接口的结果。对照有权和无权账号，数据受范围限制时再检查越范围访问。种子和增量权限数据只按需求授权，沿用本模块已有的读写边界。
