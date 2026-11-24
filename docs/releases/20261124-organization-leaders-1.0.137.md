# 1.0.137 发布记录：组织负责人与总经理

## 内容

- 按公司简介重建 20 节点组织树（总经理唯一根 → 总经办 → 4 部门 → 14 下级），人事行政部改名综合部。
- 部门负责人（profile_department.leader_user_id）与总经理（根节点负责人，全系统唯一）。
- GENERAL_MANAGER 内置角色：代码内计算权限（全部权限码减 role:manage / audit:view / wecom:sync / storage:manage），只能经组织树根节点任命。
- 部门负责人可查看本部门及下级成员；账号停用自动撤销负责人身份。
- 详见 [任务交接](../handover/tasks/20261124_organization_leaders.md)。

## 数据库

- 迁移 `20261124_profile_organization_leaders.sql` 已于 2026-11-24 14:48 经 db-migrate Apply 登记执行。
  - 事故备注：首次 Apply 的 ALTER TABLE 被后端连接池空闲连接的元数据锁阻塞约 16 分钟（迁移新增外键同时引用 profile_department 与 sys_user）。清理空闲连接后立即完成，期间无请求受影响。后续含 FK 的迁移应在发布窗口内（服务停机或低峰）执行。
- 迁移前备份：`temp/backup/org-leaders-before-20261124_143034.sql`（profile_department / profile_membership / role / user_role）。

## 构建与测试

- 单元测试 20 项通过（-DargLine=-Xmx512m）；前端构建通过（is-not-exported 警告 0）。
- 隔离验证（临时 MySQL + 临时后端，58 项真实接口检查）全部通过：迁移幂等、新旧库 DDL 一致、总经理任命/顶替/停用撤销、权限包含与排除、成员范围与越权 403、角色锁定、审计文案。
- 正式制品：`builds/1.0.137-build-20261124_144910/expert-system.jar`（gitShort=8432d36）；commit 8432d36。

## 发布过程

- 14:24 Preflight 因迁移未应用而中止（预期拦截）。
- 14:48 迁移应用完成。
- 14:52 第一次 Publish 在蓝绿预检中止：影子端口 8081 被外部程序占用（E:\摸鱼\tomato-supermarket-v1.0.0.exe，端口编译期写死无法更改）。与负责人确认后，发布期间临时关闭该程序，发布完成后重新启动。
- 15:02 重新提权 Publish。

## 技术冒烟

- 15:09 发布完成：release-state.json status=SUCCESS，builds/1.0.137-build-20261124_150321。
- /health 直连与经 Nginx：version=1.0.137、gitShort=8432d36、buildTime=2026-11-24 15:04:24。
- Nginx root → frontend/releases/1.0.137-organization-leaders；主 bundle 含 v1.0.137；首页 200×3；经 Nginx API 200。

## 运行环境备注

- E:\摸鱼	omato-supermarket-v1.0.0.exe 编译期写死监听 127.0.0.1:8081（环境变量、命令行参数、旁挂配置文件均无法更改；二进制中无可安全补丁的端口常量）。8081 是后端蓝绿发布的影子端口，二者不能同时运行：**每次后端发布前需临时关闭该程序，发布完成后重新启动**。已与负责人确认此方案（2026-11-24）。

## 业务验收

- [ ] 超管「组织架构管理」看到 20 节点树。
- [ ] 任命总经理后，该账号重新登录获得业务与账号管理菜单，无角色管理/操作日志/企微同步/存储管理。
- [ ] 换任命后前任权限恢复。
- [ ] 部门负责人主页出现「部门成员」且只看到本部门及下级。

## 热修 1.0.137.1（2026-11-24 15:18）

- 业务验收中发现「部门加载失败」：浏览器日志显示 departments 请求从未发出——重写组件时遗漏 `getDepartments` 导入，调用抛错被 catch 吞掉（成员/账号下拉正常，后端接口实测 200 正常）。
- 修复 + graceful 切换：commit d7cfe41，Nginx root → releases/1.0.137.1-org-leaders-hotfix，首页 200×3，主 bundle 含 v1.0.137.1。后端保持 1.0.137。
- 教训：**catch 吞错的加载失败先核对浏览器是否真的发出了请求**；本地构建前的引用完整性检查无法发现"模块有导出但组件漏 import"。

## 回滚

- 前端：`release-full.ps1 -Mode Rollback -Yes`（依据 frontend/release-metadata/1.0.137-organization-leaders.nginx-before.txt）。
- 后端：backend/releases/<旧版本>-*/previous-expert-system.jar（蓝绿自带）。
- 数据库不回滚：加列 + 种子 + 新角色，旧包不读写。
