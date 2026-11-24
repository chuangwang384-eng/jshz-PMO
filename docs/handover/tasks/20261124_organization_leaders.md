# 组织树完善：部门负责人与总经理

## 范围与验收

- 任务日期 2026-11-24，主责 ZCode，开发基线 main 1e01d9a，目标版本 1.0.137。
- 组织树按公司简介重建为 20 个节点：总经理（唯一根）→ 总经办 → 项目部（项目一部/项目二部/项目三部/重点项目部）、综合部（行政/人力资源/法务）、设计部（策划/平面设计/3D设计/新媒体运营）、财务合规部（合规/会计/出纳）。原「人事行政部」改名「综合部」，保留原 id 与 seed_key。
- 每个节点可设一名负责人，可空缺；一人可负责多个部门；负责人不必是该部门成员。总经办不单独设负责人，始终显示总经理。
- 总经理：在根节点设负责人即任命，全系统一人，换人直接顶替。任命后清除本人部门归属与岗位，绑定 GENERAL_MANAGER 角色；可代理其他部门负责人；可维护组织树全部内容。
- 总经理权限为全部权限码减去超管专属码（role:manage、audit:view、wecom:sync、storage:manage），含账号管理；由代码计算，不写 role_permission。用户同时持有该角色时权限以其为准，不叠加其他角色。
- 部门负责人可查看本部门及全部下级的成员；其余账号拒绝。其他负责人权限仅预留，本次未实现。
- 岗位仍为自由文字。个人主页显示部门全路径与最近的负责人；树节点显示负责人姓名；成员列表带总经理/负责人标记。
- 验收条件：
  - [ ] 超管在我的主页「组织架构管理」看到 20 节点的树形结构与图一致。
  - [ ] 在「总经理」节点任命后，被任命人重新登录即拥有业务与账号管理菜单，但没有角色管理、用户操作日志、企微同步、存储管理。
  - [ ] 再任命另一人后，前任重新登录恢复原角色权限。
  - [ ] 部门负责人的主页出现「部门成员」按钮，只看到本部门及下级的成员。

## 关键文件和接口

- ProfileOverviewService.java、ProfileOverviewController.java：负责人、总经理任命、成员范围。
- PermissionService.java：GENERAL_MANAGER_CODE、GENERAL_MANAGER_EXCLUDED。RoleController.java：总经理角色与超管同样锁定绑定和状态。UserService.java：角色分配拒绝总经理角色且覆盖式分配保留现任总经理绑定；账号停用/删除自动撤销负责人身份。
- ProfileOrganizationManager.vue（树形表格、负责人选择、负责人只读模式）、ProfileCard.vue、RoleManage.vue、UserManage.vue（角色下拉不含总经理）。
- database/migration/20261124_profile_organization_leaders.sql 与 init.sql：profile_department.leader_user_id（外键 sys_user，删除置空）、20 节点种子、GENERAL_MANAGER 内置角色。迁移可重复执行。
- 接口变化（均在前缀 /api/profile/overview 下）：
  - GET /departments 返回 leaderUserId、leaderName、generalManagerNode、generalOfficeNode；超管与总经理可用。
  - POST /departments、PUT /departments/{id} 表单增加 leaderUserId；根节点表示任命/免去总经理；返回审计补充说明写入 operation_log。
  - GET /members 增加 departmentId（含下级）；超管与总经理看全部，负责人看本人负责范围，其余 403；行增加 departmentPath、generalManager、leaderOf。
  - GET /organization 增加 leaderName、generalManager、ledDepartments。
- 无新增表、环境变量、依赖。

## 验证

- `mvn -f backend/pom.xml -DargLine=-Xmx512m -Dtest=PermissionServiceGeneralManagerTest,ProfileOrganizationRulesTest test`：20 项通过。两组测试以替身数据库验证规则与落库语句。
- `npm run build -- --outDir temp/org-leaders-dist`：构建通过，`is not exported` 警告 0。
- `python temp/org_leaders_verify.py`：临时 MySQL 与临时后端（均在本机回环随机端口，结束即销毁）。新库 init.sql 与「1.0.136 基线 + 两次重复迁移」的 profile_department 结构一致，种子一致；58 项真实接口检查全部通过，覆盖任命、顶替、停用自动撤销、权限包含与排除、成员范围与越范围 403、角色锁定、审计文案。
- 未执行：浏览器页面人工验收；生产业务验收。

## 事故记录

- 本任务中在仓库根目录执行 `mvn -f backend/pom.xml package`，原地覆盖了生产后端正在使用的 backend/target/expert-system.jar（13:18–13:30）。已将 builds/1.0.136-build-20261124_102237/expert-system.jar 原样拷回，校验和一致，服务未重启，期间线上日志无报错。**任何指向 backend/pom.xml 的 package 都会覆盖线上 jar**，测试包必须在 temp 副本中构建。
- 单元测试替身曾把 ResultSet.next() 写成恒真，测试进程耗尽内存导致本机卡死。之后运行 Maven 均加 -Xmx 上限与 timeout。

## 发布及回滚

1. 发布前备份，执行 `db-migrate -Mode Apply` 应用本迁移（加法变更：一列、种子、一个角色）。
2. 按 release-full 发布 1.0.137 前后端。
3. 应用回滚到 1.0.136 时保留新列、种子与角色；旧包不读写这些内容。已任命的总经理会保留 GENERAL_MANAGER 角色绑定，而 1.0.136 不识别该角色，回滚后该账号仅剩其他角色权限，属预期。
