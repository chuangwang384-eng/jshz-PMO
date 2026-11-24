# 我的主页改密、组织架构及账号活动

## 范围与验收

- 任务日期 2026-11-24，主责 Codex，开发基线 main 584910e，开发分支 codex/profile-overview。
- 改密弹窗随登录状态及时显示。强制弹窗禁止 Esc、遮罩及右上角关闭，普通弹窗支持 Esc 和右上角关闭及再次打开，关闭或成功后清除输入。成功后沿用现有会话更新。密码最小长度由后端唯一校验。旧 /profile/change-password 路由转至有效入口。
- 手动组织树初始仅含参考图可见的五个顶级部门。无下级数据或账号归属回填。超管可新增、改名、移动、启停部门，并用账号 ID 区分同名账号，设定每人一个主部门与岗位。总经理通过岗位设置。拒绝同级同名、循环层级、停用有下级或成员的部门。
- 员工只看本人部门全路径、岗位及近 30 天成功账号事件。账号活动以本人稳定 ID 归属，早期只带用户名的登录或重置记录缺少可靠 ID，因此省略。列表只返回事件和时间。组织归属独立于 RBAC 和项目数据范围。

## 关键文件和接口

- ProfileCard.vue、ProfileOrganizationManager.vue 和 api/profileOverview.js 实现个人信息、账号活动、组织维护。
- ChangePasswordDialog.vue、App.vue、router/index.js 修复弹窗和旧链接。AuthService.java 统一后端长度。AuthController、UserController 和 UserService 调整审计关联及改密通知目的地。
- GET /api/profile/overview/organization、/activity?page= 均从当前认证账号获取本人 ID，不接受指定他人。活动与成员分页返回 total、items、pageSize。
- GET/POST /api/profile/overview/departments、PUT /departments/{id}、GET /members?keyword=&page=、PUT /members/{id} 均要求 SUPER_ADMIN，普通管理员和普通员工拒绝访问。组织写入记既有审计日志。
- database/migration/20261124_profile_manual_organization.sql 与 init.sql 新增 profile_department、profile_membership；逐人分配，部门停用采用状态字段。迁移重复执行保留既有部门修改。无共享认证拦截、依赖或环境变量变更。

## 验证

- JDK 17 下执行 mvn.cmd -f backend/pom.xml -Drevision=1.0.136 -DgitShort=584910e -Dtest=AuthServiceTest package，10 项测试通过，隔离 JAR 构建成功。
- 运行 npm.cmd run build -- --outDir <LOCAL_PATH> --emptyOutDir，构建通过。Edge 模拟接口检查了普通弹窗反复开关、输入清理、旧链接转发、登录后立即强制改密及强制窗禁用 Esc。
- 运行 temp/profile-overview-mysql.ps1，使用隔离 MySQL、隔离 JAR、合成超管、管理员和普通账号。新库初始化与旧库迁移重复执行后表结构一致。真实接口验证五个部门、超管维护权限、成员归属与总经理岗位、循环和停用保护、本人近 30 天活动、改密后 Cookie 仍可使用。
- 开发前生产基线 1.0.135 的 schema parity 只读通过。变更后隔离工作区 check-schema-parity.ps1 -NoDatabase 通过。

## 发布及回滚

1. 既有库先备份，再按迁移流程执行本次新增脚本并检查登记与基线对齐，新库从 init.sql 初始化。
2. 采用配套前后端 1.0.136，按正式脚本预检、蓝绿发布、健康检查与必要验收。生产账号归属按超管实际配置。
3. 应用回滚到 1.0.135 时保留组织表与已录入数据。当前登入改密流程保持页面层约束。
