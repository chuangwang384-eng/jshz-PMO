---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_a7bc732fd1fd11f18068525400de85a5
    ReservedCode1: ri/LYHlPGmcj1qxl9oFhziVGgKX1/E6aRu/kGo84MTPQzUMcNUrdbyJvb6Mca+Q8CSlRfkIz9uyk/lluSIB9LEjABXElqn7Gv3wJ1NHhlTnjxNoaLtqpwN8yimPvwsuinluWezqZrQliydaKS59/DTS5a61yuFHiUDz5IPjVjHOHk++BVU/hNBzQH8s=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_a7bc732fd1fd11f18068525400de85a5
    ReservedCode2: ri/LYHlPGmcj1qxl9oFhziVGgKX1/E6aRu/kGo84MTPQzUMcNUrdbyJvb6Mca+Q8CSlRfkIz9uyk/lluSIB9LEjABXElqn7Gv3wJ1NHhlTnjxNoaLtqpwN8yimPvwsuinluWezqZrQliydaKS59/DTS5a61yuFHiUDz5IPjVjHOHk++BVU/hNBzQH8s=
---

---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_a3cde50cd0dd11f1814c525400cd780f
    ReservedCode1: Ify4TlCoLcIIhplv2IO/bfAOiHmDf56p/YpY70mCPKu/7pGtTDynCId/TkJJS+ahQDM5XHp4MOR7MCWBo851CgN1fkauereC18I79DAKg0fDWN13obNtJN10kAdqiaj8xlex4BPBTav1RKrdVRq3/O94KBzu6QxadWaCCma4Ne9ev74tcnLIJDL4pM8=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_a3cde50cd0dd11f1814c525400cd780f
    ReservedCode2: Ify4TlCoLcIIhplv2IO/bfAOiHmDf56p/YpY70mCPKu/7pGtTDynCId/TkJJS+ahQDM5XHp4MOR7MCWBo851CgN1fkauereC18I79DAKg0fDWN13obNtJN10kAdqiaj8xlex4BPBTav1RKrdVRq3/O94KBzu6QxadWaCCma4Ne9ev74tcnLIJDL4pM8=
---

---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_078bb7eccb8011f18078525400de85a5
    ReservedCode1: EJ4UAR3TTqMrcgjZqiqtE3SMpzL8bFR1hOtf0kebWhsMc+Oftnkv7tIRym5e0De7dv+lvDnJemQRrufJOQdumnUorSDeL87SLBY5ppXCDkk1iPbz4ywhX9skyicQxIhhDNLvh8d6GSkbxVDoTw9iKldAHXKdwPp0YKlu5y1PegoKRJfY85fH73KGJu4=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_078bb7eccb8011f18078525400de85a5
    ReservedCode2: EJ4UAR3TTqMrcgjZqiqtE3SMpzL8bFR1hOtf0kebWhsMc+Oftnkv7tIRym5e0De7dv+lvDnJemQRrufJOQdumnUorSDeL87SLBY5ppXCDkk1iPbz4ywhX9skyicQxIhhDNLvh8d6GSkbxVDoTw9iKldAHXKdwPp0YKlu5y1PegoKRJfY85fH73KGJu4=
---

# jshz-PMO 部署与发布运维手册

> 本系统依赖公司内网部署，以下为部署步骤与约束说明。

## 1. 前置要求

- JDK 17+
- Maven 3.8+
- Node.js 18+ / npm 9+
- MySQL 8.x+（内网实例；当前生产/测试实测 26.7.0）
- Nginx（生产环境反向代理，可选）

## 2. 数据库初始化与变更管理

### 2.1 新环境（从零建库）

```bash
mysql -h <内网MySQL地址> -u <账号> -p < database/init.sql
```

- `init.sql` 是**新环境唯一 DDL 来源**：2026-11-12 按生产库实际结构重建，2026-11-16 补齐 AI 助手与会议预算 4 表，现为 **85 张业务表**全量结构（列/索引/约束/字符集逐项与生产一致）+ 字典与权限种子（role 6 / permission 117+ / role_permission 53+ / supplier_city 349 等）+ 初始管理员 `admin`（密码 `111111`，部署后立即改密）。
- 语义：`CREATE TABLE IF NOT EXISTS`，**不含 DROP**、不含 `AUTO_INCREMENT` 计数器，可重复执行。
- 不含：业务数据、用户口令哈希（仅 admin）、历史备份表（`*_bak_*`）、迁移标记表（`mig_*`）。
- 新环境**不需要重放** `database/migration/` 的 63 个历史脚本；建库后先校验基线完整性，再登记版本台账：

```powershell
.\scripts\check-schema-parity.ps1 -NoDatabase   # 基线必须覆盖全部 migration，否则不得 Bootstrap
.\scripts\db-migrate.ps1 -Mode Bootstrap        # 把 init.sql 与 63 个历史脚本登记为已执行
```

### 2.2 已有环境（接管历史库）

```powershell
.\scripts\db-migrate.ps1 -Mode Bootstrap   # 一次性回填台账（按现有结构视为已执行）
.\scripts\db-migrate.ps1 -Mode Status      # 复核：待执行 0、漂移 0
```

### 2.3 日常变更（唯一正确姿势）

1. 新增 `database\migration\<yyyyMMdd>_<描述>.sql`，必须幂等（`IF NOT EXISTS` / `INSERT IGNORE` / 先判断后 ALTER）；
2. `.\scripts\db-migrate.ps1 -Mode Status` 查看待执行清单；
3. `.\scripts\db-migrate.ps1 -Mode Apply` 执行并登记（SHA256 + 时间 + 执行人，日志 `logs\db-migrate-*.log`）；
4. 已登记脚本若内容被改动 → 状态显示 **DRIFT**，`-Mode Apply` 会中止并要求人工确认，防止"改了脚本以为没执行"。

项目六阶段发布时，先应用 `20261118_project_lifecycle_stages.sql` 阶段字典迁移，再切换前端快照。发布前备份阶段字典及助手流程条目，并按 [六阶段迁移与验收交接](handover/tasks/20261118_project_lifecycle_stages.md) 核对现有项目进度及回滚条件。

### 2.4 版本表 `schema_migration`

| 列 | 说明 |
| --- | --- |
| `script` | 迁移文件名（唯一键），`init.sql` 作为基线存在一行 |
| `checksum` | 脚本文件 SHA256，用于漂移检测 |
| `status` | `APPLIED` 已执行 / `BASELINE` 基线 / `FAILED` 执行失败 |
| `applied_at` / `applied_by` | 执行时间与执行人（回填记录在 `note` 注明来源） |
| `note` | 回填说明、失败原因等 |

当前状态（2026-11-12）：`APPLIED` 60 + `BASELINE` 1，待执行 0、漂移 0。其中 7 条 `applied_at` 取自 `logs\migration-*.log` 的真实执行时刻，其余为回填（取脚本落盘时间，`note` 已标注）。

## 3. 后端部署

```bash
cd backend
mvn clean package -DskipTests
```

配置应用（通过环境变量注入，避免敏感信息入库）：

```bash
# Windows（PowerShell/CMD）注入数据库与 JWT 环境变量
set MYSQL_HOST=<内网数据库地址>
set MYSQL_PORT=3306
set MYSQL_DB=expert_system
set MYSQL_USER=<账号>
set MYSQL_PASSWORD=<密码>
set JWT_SECRET=<32字节以上随机密钥>
# 企微通讯录同步（1.007 起；未配置时同步功能不可用但登录不受影响，具体见「企微通讯录同步部署准备」）
set WECOM_CORP_ID=<企微企业 CorpID>
set WECOM_CONTACT_SECRET=<通讯录同步助手只读 secret>
set AUTH_LOGIN_MODE=COMPAT
```

**启动方式（Windows 唯一合法方式）**：使用项目根目录 `start-backend.bat`（内含 `set MYSQL_PASSWORD=<真实密码>`、`set JWT_SECRET=<真实密钥>` 与企微三变量占位（`WECOM_CORP_ID` / `WECOM_CONTACT_SECRET` / `AUTH_LOGIN_MODE=NAME`，2026-10-27 已加入注入点）后 `java -jar target\expert-system-1.001.jar`；真实值只存于该本地未入库文件与 NSSM 服务配置，禁止写入任何入库文件）。

> ⚠️ **禁止直接 `java -jar`**：`application.yml` 中 `password: ${MYSQL_PASSWORD:}` 未注入时为空，会报 `Access denied for user 'root'@'localhost'`；`start-backend.bat` 已注入密码，是唯一合法启动方式。

构建：

```bash
cd backend
mvn clean package -DskipTests
```

> ⚠️ 若旧 jar 被运行中 java 进程占用会打包失败，先停旧 java 进程再打包。

默认监听 `8080` 端口。后端已注册为 Windows 服务（NSSM，开机自启），服务启动效果等价于 `start-backend.bat`（注入 MYSQL_PASSWORD 后启动 jar）。

## 4. 前端部署

开发环境：

```bash
cd frontend
npm install
npm run dev          # 默认 http://localhost:5173
```

生产构建：

```bash
cd frontend
npm install
npm run build        # 产物在 dist/ 目录
```

将 `dist/` 静态文件部署到 Nginx，并配置反向代理：

```nginx
server {
    listen 80;
    server_name expert.internal;   # 内网域名

    location / {
        root /data/expert-system/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 5. 部署约束

- 所有服务仅监听内网地址，禁止暴露公网端口。
- 数据库连接信息使用环境变量注入，`.gitignore` 已排除本地敏感配置。
- 初始账号 admin / 111111，首次登录强制改密；员工账号初始密码 111111，同样强制改密。
- 前端页面通过内网域名访问，确保跨域与代理配置正确。

## 6. 验收检查项

- [ ] `database/init.sql` 在目标库执行成功，`SHOW TABLES` 确认 **85 张业务表**（含 `schema_migration`；不含 `*_bak_*` 与 `mig_*` 历史/标记表）
- [ ] `.\scripts\db-migrate.ps1 -Mode Bootstrap` 后 `-Mode Status` 显示「已登记 60 / 待执行 0 / 漂移 0」；新环境表结构应与生产一致（列 721、索引 313）
- [ ] 后端启动无异常，`GET /health` 返回 UP，`/api/expert/list` 可访问
- [ ] 审批流接口可用：`GET /api/approval/config`、`POST /api/approval/requests`（修改申请）、`POST /api/approval/create-request`（新增专家申请）
- [ ] 消息通知接口可用：`GET /api/notifications`、`GET /api/notifications/unread-count`、`POST /api/notifications/{id}/read`、`POST /api/notifications/read-all`
- [ ] 知识星球管理员永久删除可用：`DELETE /api/knowledge/docs/{id}/permanent`（删除主记录+版本历史+磁盘文件）
- [ ] 前端页面可正常加载并展示专家列表，顶栏铃铛可见、知识星球父页点击标题跳转/点击箭头展开子菜单
- [ ] （1.007 企微通讯录同步）`database/migration/20261026_add_wecom_contact_sync.sql` 已在目标库执行，`SHOW TABLES` 能查到 wecom_department / wecom_user_department / wecom_sync_log 三表与 wecom_migration_uk_username_backup（不做总数断言：脚本执行到哪一步取决于已跑到哪个增量，总数以最终 `information_schema` 实测为准）；登录页文案随 `AUTH_LOGIN_MODE` 变化；管理页 DryRun 试算→实跑→组织架构树→未绑定清单→人工绑定链路可用；个人中心「通讯录」页展示本部门链路与成员
- [ ] 内网安全策略（IP 白名单 / 端口监听）已生效
*（内容由AI生成，仅供参考）*

## 凭据轮换记录（2026-10-26）

> 安全整改：仓库内所有明文数据库密码与 JWT 密钥已清除（见 git 提交 20ef3bd），凭据一律环境变量注入，真实值只存于本地 `start-backend.bat`（gitignore 排除）与 NSSM 服务配置，禁止写入任何入库文件。

- **数据库密码**：已通过 MySQL 双密码机制完成第一階段轮换（`ALTER USER 'root'@'localhost' IDENTIFIED BY <新密码> RETAIN CURRENT PASSWORD`），新密码即日生效、旧密码保留过渡，运行中服务零中断。
- **JWT 密钥**：`application.yml` 改为 `${JWT_SECRET:}` 注入（未注入时后端启动失败属预期 fail-fast）。
- **NSSM 服务**：`ExpertSystemBackend` 的 `AppEnvironmentExtra` 由 `MYSQL_PASSWORD` 单项升级为 `MYSQL_PASSWORD` + `JWT_SECRET` 两项，由 `scripts/rotate-backend-credentials.ps1`（管理员运行，凭据从 start-backend.bat 读取）写入。
- **执行状态（2026-10-26 10:46）**：NSSM 环境变量切换与 1.002 后端发布已同批完成，新数据库密码与新 JWT 密钥均已生效（全体用户需重新登录，属预期）；冒烟验证通过（health / 登录 / 模板接口）。
- **剩余动作**：系统观察稳定后，以 `-DiscardOldPassword` 参数再执行一次 `rotate-backend-credentials.ps1`，弃用旧数据库密码、结束双密码过渡期。
*（内容由AI生成，仅供参考）*

## 凭据长期策略与免重登发布（2026-10-26 固化）

> **目标：日常发布/重启不影响任何已登录用户。** 已登录用户的 token 由 JWT 密钥签名，只要密钥不变，重启后端、蓝绿发布均不会使 token 失效。

- **JWT_SECRET 为长期固定凭据**：只存于本地 `start-backend.bat` 与 NSSM 服务环境（AppEnvironmentExtra），发布、重启、机器重启均不更换。`deploy-backend.ps1` 阶段 1 内置一致性护栏：会话注入的 `MYSQL_PASSWORD` / `JWT_SECRET` 必须与 NSSM 注册表值完全一致，不一致直接中止发布（DryRun 模式下同样执行该检查）。
- **发布前准备**（每次后端发布）：在会话中按 `start-backend.bat` 的值注入 `MYSQL_PASSWORD` 与 `JWT_SECRET`，再执行 `deploy-backend.ps1`。凭据一致 → 预检打印 `[OK] 凭据一致性...发布后用户无需重新登录`。
- **密钥轮换为例外操作**（仅在怀疑泄露时执行，需负责人批准并提前知会全员重新登录）：修改 `start-backend.bat` 中两个值 → 管理员执行 `rotate-backend-credentials.ps1`（写 NSSM + 重启 + 冒烟）。数据库密码另支持双密码零中断过渡，JWT 密钥轮换则会立即使所有 token 失效，属预期行为。
- 与发布无关的正常登录失效：JWT 有效期 8 小时（`expert.jwt.expire-hours`），到期需重新登录，属产品策略；如需调整有效期改配置并走正常发布。
*（内容由AI生成，仅供参考）*

## R-1 发布记录（后端蓝绿）
- 2026-10-26 09:29:01 R-1 发布 1.001 成功（jar: expert-system-1.001.jar）
- 2026-10-26 10:46:13 R-1 发布 1.002 成功（jar: expert-system-1.002.jar；R-B4 审批模板 + 凭据环境变量化生效）
*（内容由AI生成，仅供参考）*

## R-2 发布记录（前端原子）
- 2026-10-26 10:51:27 R-2 发布 1.002 成功（dist SHA256 自检通过；回滚点 `frontend/releases/1.002-20261026_105127`）
*（内容由AI生成，仅供参考）*

## 1.004 发布后修复记录（2026-10-26）
- 16:21:51 R-1 蓝绿发布 1.004 后健康检查全过，但实测新接口 404——**根因：NSSM `ExpertSystemBackend` 的 AppParameters 被此前发布改为直接指向 `expert-system-1.003.jar`**（偏离 deploy-backend.ps1 约定的固定名 `expert-system-1.001.jar`），导致阶段 9c 替换 1.001.jar 无效、服务重启后仍运行 1.003 旧代码（健康检查只探活、探不出版本错位）。
- 16:33 已用提权 wrapper（logs/deploy-1p004-fix-wrapper.ps1）把 AppParameters 改回 `-jar <LOCAL_PATH>`（该文件当时已含 1.004 构建内容）并重启服务，1.004 实际生效。回滚点：`backend/releases/1.004-20261026_162127`。
- **运维警告 1**：发布前必须核对 NSSM AppParameters 指向 `target\expert-system-1.001.jar`（固定名契约）；建议后续给 deploy-backend.ps1 增加版本探针（如 /health 返回构建版本比对）防止同类静默错位。
- **运维警告 2**：执行 database/migration 下含中文的 SQL 禁止经 PowerShell 管道（`$sql | mysql`）传参——PowerShell 5.1 管道默认 ASCII 编码，中文字面量会变 `?`(3F) 写入库（本次已用 database/migration/20261026_fix_b2_operation_log_utf8.sql 修复）。正确姿势：`Start-Process mysql -RedirectStandardInput 文件` 或 cmd 字节级重定向，并带 `--default-character-set=utf8mb4`。
- 前端 1.004 原子发布：16:22:31 完成（dist SHA256 自检通过；回滚点 `frontend/releases/1.004-20261026_162231`）。
- 本次 1.004 合并发布内容：知识星球格式显示优化（随工作区）+ B2 审批转办/加签（或签）+ 用户操作日志改版；双账号（jshz 超管 + 王创审批人）按验收清单逐项实测通过，详见 docs/handover/tasks/2026-10-26-B2审批转办加签与用户操作日志改版-交接记录.md。

## 备份与恢复演练体系（2026-10-26 建立）

- **一键备份**：`scripts/backup.ps1`（单脚本三模式 Backup/Verify/Drill）。备份产物 `backup\backup_时间戳\`：expert_system 全量 SQL（--single-transaction --set-gtid-purged=OFF）+ 照片库/知识文档库 zip（照片库_tmp 空则跳过）+ 每产物 .sha256 + manifest.txt；保留最近 5 份，超出移入回收站；日志 `logs\backup.log`。
- **凭据**：优先环境变量 MYSQL_PASSWORD，缺省从本地 start-backend.bat（gitignored）运行时解析，不落盘明文（解析实现已统一到 `scripts/common-credentials.ps1`，2026-11-13）。
- **自动计划任务**：`jshzPMOBackupWeekly` **每周日 02:00，SYSTEM 身份**，执行 `backup.ps1 -Mode Backup -Keep 5`（2026-11-12 重新注册，此前任务已丢失导致 6 天无备份）。注册/删除：`scripts\register-backup-schedule.ps1 [-DryRun|-Unregister]`（管理员）。
- **保留策略**：只处理 `backup_yyyyMMdd_HHmmss` 目录，超出 -Keep 的最旧者移入回收站；删除前判存在、删除后复核，已不存在的目录只记一行日志不再报错（2026-11-12 修复）。手工目录（如早期 `backup\frontend*`）不在策略范围，已归档到 `builds\archive\legacy-manual-backups-20261112\`。
- **校验**：`-Mode Verify` 对产物做 SHA256 复核 + zip 全量读取（CRC）校验。
- **恢复演练**：`-Mode Drill` 自动：校验→重建测试库 expert_system_restore_test（内置护栏仅操作该库）→导入→与生产库对比表数量与每表 COUNT(*) 精确行数→结论自动追加 `docs/guides/2026-10-26-备份与恢复-演练文档.md`（含手动灾难恢复步骤）。首次演练已通过：17 表 / 871 行完全一致（2026-10-26 19:19，耗时 42.8s）。

## 1.005 发布记录（2026-10-27 上午，四功能列车）
- 08:30 后端 1.005 蓝绿发布（我的申请+消息中心+供应商管理）；08:34 前端原子发布；两条迁移重跑（幂等，窗口数据零缺口核实：旧表31行全部并入、record 回填无缺口、23 表）。
- 部署后验收发现并修复两个缺陷（均已二次发布生效）：① 供应商 PUT 为全量语义导致画廊仅传 images 时报"类型不能为空"（951aa4f，改部分更新语义）；② 供应商详情路由复用导致绑定双向跳转数据不刷新（04c4bf1，加 route.params watch）。第二次后端蓝绿 09:0x、第二次前端原子发布均已自检通过。
- 回滚点：backend/releases/1.005-20261027_082855（首发）与 1.005-20261027_090xxx（二次）；frontend/releases/1.005-20261027_083403 与 1.005-20261027_090741。
- 浏览器双账号+张三验收通过（详见 docs/handover/tasks/2026-10-26-我的申请通用化改造与消息中心-交接记录.md §10）。

## 企微通讯录同步部署准备（2026-10-27，实施完成，发布执行留待负责人批准）

> 本文档只记录部署准备与注入点；**生产发布未执行**（需负责人批准后按 §11 部署步骤执行，与 1.005 同场 UAC 模式操作 NSSM）。

### 企微后台配置指引（负责人操作，约 10 分钟）
1. 登录[企微管理后台](https://work.weixin.qq.com/wework_admin) → 管理工具 → **通讯录同步助手**：开启，生成**只读** secret（勿申请写权限）。
2. 我的企业 → 企业信息：记录 **CorpID**。
3. 若企业开启「IP 可信/白名单」校验：把**公司网络出口公网 IP** 加入可信列表（本机当前在 192.168.110.x 网段，出口 IP 需在公司网络实测获取）。
4. 保持只读 secret，系统**不回写**企微。

### 环境变量注入点（代码侧已就绪）
- `start-backend.bat`（2026-10-27 已加占位）：`set WECOM_CORP_ID=`、`set WECOM_CONTACT_SECRET=`、`set AUTH_LOGIN_MODE=NAME`，真实值由负责人在部署时填写。
- NSSM 服务 `ExpertSystemBackend` 的 `AppEnvironmentExtra`：需在发布时（管理员/UAC）追加 `WECOM_CORP_ID` / `WECOM_CONTACT_SECRET` / `AUTH_LOGIN_MODE` 三项；可参考 `scripts/rotate-backend-credentials.ps1` 的写注册表方式新增 `scripts/set-wecom-env.ps1`（未创建）。
- `application.yml`：`expert.wecom.corp-id=${WECOM_CORP_ID:}`、`contact-secret=${WECOM_CONTACT_SECRET:}`、`sync-cron="0 30 2 * * ?"`、`sync-cron-enabled: false`（默认关闭，运维稳定后打开）、`login-mode=${AUTH_LOGIN_MODE:NAME}`。
- **fail-fast 约定**：`sync-cron-enabled=true` 且 corp-id/secret 为空 → 启动失败（对齐 JWT_SECRET 校验风格）。

#### 环境相关路径与 CORS 出口化（方案 B，2026-11-13）
- `STORAGE_ROOT`：全部存储目录统一根前缀（照片库/照片库_tmp/知识文档库/知识文档库_preview/客户附件库/供应商图库/项目资料库），默认 `<LOCAL_PATH>`；机器迁移/换盘只改此变量，无需改代码重编译。
- `LIBREOFFICE_PATH`：LibreOffice 可执行文件路径，默认 `<LOCAL_PATH> Files\LibreOffice\program\soffice.com`（Office 预览转换）。
- `CORS_ALLOWED_ORIGINS`：前端来源白名单（逗号分隔），默认 `http://<PRIVATE_IP>:5173,http://localhost:5173,http://<PRIVATE_IP>:5173`；配置项 `security.cors.allowed-origins`，消费点 `SecurityConfig`。
- `PHOTOMATTE_LEGACY_ROOT`：抠图历史 E 盘 venv 兜底根（默认 `E:/AI助手开发/pp-ocrv5`，置空即跳过该候选）；正式出口仍为 `PHOTOMATTE_PYTHON` / `PHOTOMATTE_ROOT`。
- `SR_UPSCALE_PYTHON`：低像素照片超分解释器（默认探测 `<LOCAL_PATH> Files/JSHZ/sr-upscale/venv/Scripts/python.exe`，不存在即整体跳过超分，2026-11-18）；worker 侧另有 `SR_UPSCALE_SCRIPT` / `SR_UPSCALE_MODELS` 覆盖口，缺省同根目录。
- 以上默认值均为本机当前值：不注入变量时运行行为与出口化前完全一致。
- **上传限额单一真源**（方案 D）：后端从 `application.yml expert.upload` 读取 photo/resume/doc-max-bytes，前端登录后经 `GET /api/meta/limits` 同步。调整时同步 `frontend/src/utils/limits.js` 中的离线兜底值。
- **专家简历限额**：`expert.upload.resume-max-bytes` 为 31457280 字节。2026-11-18 核对 `application.yml` 的 multipart 文件与请求上限分别为 50 MB 和 60 MB，运行中 Nginx 的 API 请求上限为 60m。发布先切后端限额，再切前端，无需调整代理或 multipart 配置。
- **凭据解析统一**（方案 A）：`backup.ps1`、`db-migrate.ps1`、7 个 apply-migration-*.ps1 的 MYSQL_PASSWORD 解析实现统一收敛到 `scripts/common-credentials.ps1`（环境变量优先 → start-backend.bat 运行时解析），其余脚本沿用会话注入约定不变。

### 登录模式切换节奏（评审结论）
- 1.007 上线初始建议 `AUTH_LOGIN_MODE=COMPAT`（姓名/手机号双通道过渡，未绑定用户仍姓名登录）；
- 全员绑定率 100% 后再切 `MOBILE`（仅手机号登录、关闭自动注册；`is_admin=1` 管理员豁免不受影响）；
- 回滚档 `NAME`：一键改环境变量重启即恢复原状。

### 上线发布步骤（执行时）
1. 在目标库执行 `database/migration/20261026_add_wecom_contact_sync.sql`（幂等，先测试库演练——已在本机测试库演练 3 遍通过）；
2. 管理员/UAC：`start-backend.bat` 与 NSSM 服务环境追加 `WECOM_CORP_ID` / `WECOM_CONTACT_SECRET` / `AUTH_LOGIN_MODE=COMPAT`；
3. 后端 `mvn -DskipTests package` → `scripts/deploy-backend.ps1` 蓝绿发布；前端 `scripts/deploy-frontend.ps1` 原子发布；
4. 冒烟：登录页文案切换 → DryRun 同步 → 实跑 → 组织架构树 → 双通道登录 → 设计文档 §9 验收清单 ①-⑧。
*（内容由AI生成，仅供参考）*
*（内容由AI生成，仅供参考）*

## 故障处置：全员登录提示"登录已过期"（2026-10-31 12:35 已修复）

- **现象**：所有账号登录后立即提示"登录已过期，请重新登录"；`POST /api/auth/login` 返回 HTTP 401 且响应体为空（而非正常 `{code:401,...}` JSON）。
- **根因**：NSSM 服务运行中的 `backend/target/expert-system.jar` 被一次**在服务运行期间执行的后端构建**替换成了"瘦 jar"（仅 477KB、无 `BOOT-INF/lib`，即 repackage 未成功、被 jar 插件产物覆盖；原因是 Windows 下运行中的 jar 无法重命名 `.original`）。运行中的 JVM 之后按需加载类全部失败（`logs/expert-backend.err.log` 出现 `NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy`），登录链路抛错并走到 `/error` 分发（该路径需登录）→ Spring Security 返回空体 401 → 前端 401 分支统一提示"登录已过期"。
- **修复步骤**（实测有效）：
  1. 停止服务 `Stop-Service ExpertSystemBackend`（需管理员）；
  2. `cd backend && mvn clean package -DskipTests`，确认产物为 fat jar：`unzip -l target/expert-system.jar | grep -c BOOT-INF/lib` 应 > 0（本次 72 项，约 57.9MB）；
  3. `Start-Service ExpertSystemBackend`，`GET /health` 返回 `{"status":"UP","version":"1.0.10"}`；
  4. 验证登录：`POST /api/auth/login` 返回 200 + `Set-Cookie: expert_token=...`；带 cookie 的 `GET /api/auth/me`、`GET /api/project` 均 200。
- **预防**：① 不要在服务运行期间在 `backend/` 直接执行 `mvn package/clean`——先停服务，或用 `scripts/deploy-backend.ps1`（其阶段 9 会先停服务再替换 jar）；② 任何后端构建后先核对 `unzip -l target/expert-system.jar | grep -c BOOT-INF/lib`，为 0 说明是瘦 jar，禁止直接用于发布；③ `logs/expert-backend.err.log` 出现 `NoClassDefFoundError` 即高度怀疑运行中 jar 被替换。
- **影响面**：仅后端，前端 dist 与数据库无变更；JWT_SECRET 未变，用户无需重新登录（本次因接口恢复，重新登录后恢复正常）。

## 1.0.16 发布记录（2026-11-01，供应商权限放开改造）

- **版本**：1.0.16（供应商管理操作权限放开改造）
- **改造内容**：
  - `supplier:create` / `supplier:binding` 放全员：所有角色（ADMIN / APPROVER / EMPLOYEE / KNOWLEDGE_MAINTAINER / PROJECT_OWNER，SUPER_ADMIN 代码全权）可新增供应商、维护酒店-打印店关系（绑定/解绑）；
  - 删除/编辑/图片改名/行价/评价/跨城批量解绑维持仅管理员（`supplier:manage` 规则缩减为仅 ADMIN）；
  - `supplier:dict` 补绑 ADMIN；
  - 迁移 SQL：`database/migration/20261101_open_supplier_permission.sql`（规则改造式，幂等）。
- **蓝绿发布**：2026-11-01，绿（正式）8080 上线承载生产流量（`/health` 返回 `version=1.0.16`），蓝 8081 验证后清理。
- **归档路径**：旧 jar `backend/releases/1.0.16-20261101_082433/previous-expert-system.jar`；旧前端 `frontend/releases/1.0.16-20261101_082534`。
- **验收结论**：权限矩阵 7 接口×6 角色全部符合预期，需求代码审查四层通过（详见 output/1.0.16供应商权限放开改造审查报告.md）；冒烟验证通过（无 token 请求 `/api/supplier` 返回 401，非 404）。
*（内容由AI生成，仅供参考）*

## 2026-11-03 审查修复发布注意（尚未发布）
- 代码位于 codex/fix-review-and-config-takeover；按既有前端原子发布/后端蓝绿流程发布，需负责人另行批准。
- 无表结构迁移、无新增环境变量；提醒日志send_status已有VARCHAR(16)，新运行态SENDING无需DDL。发布前只读核对实际列类型与唯一键(user_id,item_id,reminder_type,scheduled_time)，本轮未连接实际库。
- 前端新增 @vue-flow/core、@vue-flow/background，依赖锁文件一并交付，按锁文件安装。隔离构建命令：cd frontend 后 npm run build -- --outDir dist-review-fixes，不覆盖运行dist。
- 后端通知与提醒日志必须保持同一事务数据源；没有配置外部消息发送。持久故障3次后FAILED不无限重试；不要批量修改历史日志来补发，需先审核范围。
- 旧富文本共享文件保留兼容；没有批量删行/删文件迁移。已被历史覆盖的旧内容无法由本修复恢复。
- 回滚前备份应用、配置及项目资料库；回滚代码会重新引入旧缺陷。新UUID富文本路径由旧readRichText仍可读取；不要回滚或清空数据。恢复演练需在隔离环境执行。
- 验证步骤和未验证项见 docs/handover/tasks/20261103_fix_takeover.md。

## 严格不停机发布新模式（2026-11-03，待审批）
历史NSSM默认发布会重启Nginx，不能作为严格不停机路径。本轮新增 deploy-backend.ps1 -GracefulSwitch：管理员通过invoke-nginx-graceful.ps1创建SYSTEM一次性任务发送reload，验证master PID不变、新worker出现并等待旧worker全部退出，再检查旧后端连接排空。影子实例仅回环监听，禁用定时任务。
前端使用deploy-frontend-graceful.ps1不可变release/root平滑切换，保留新旧hash资产并准备兼容回滚目录。旧目录重命名方案不作为本次不停机路径。
生产SYSTEM权限及慢上传排空尚未实测，需负责人批准后先做身份预检，失败终止而非重启Nginx。操作、风险、回滚与验收证据见 [发布审批单](releases/20261103-review-fixes-approval.md)。无环境变量或数据库结构迁移，新增前端release-metadata存储配置回滚元数据。

## 2026-11-03 15:37 严格不停机模式已执行
负责人已批准。SYSTEM平滑重载、后端8080/8081蓝绿、前端不可变root切换完成；运行root已为 frontend/releases/1.0.22-review-fixes-20261103，旧dist不是当前入口。后续勿用旧默认dist脚本认定上线。
Nginx主进程4364未重启，后端正式PID33864；最终双路各10377请求零失败。初始预检另有1次连接异常、编码和任务状态误判，均已如实记录。详情及备份/回滚位置见 [生产发布记录](releases/20261103-review-fixes-production.md)。SYSTEM重载现已实测，真实账号业务验收尚待负责人。

## 企业合规 v4 设计阶段历史记录
初始方案和设计基线见 [技术设计](design/2026-11-03-企业合规可复用清单-设计文档.md)。后续已有候选实现与隔离验收，当前状态以本文件末尾“企业合规 v4 候选”及关联部署说明为准；本文件不授权执行生产 DDL 或发布。

## 企业合规 v4 候选：隔离验收与后续审批

历史隔离阶段状态：2026-11-03 首期资源权限、提醒事务与用户切换修复完成真实隔离验收时，尚未生产迁移/发布；后续已上线，见下方21:44记录。部署前需核实目标八表及生产实际版本；新增两个默认关闭的 V4 开关，个人模式/历史快照在回滚时必须保留。现有 CORS 来源不变，仅补 PATCH 方法。详见 [部署/API/权限及回滚说明](deployment/2026-11-03-企业合规V4隔离验收灰度切换与回滚-部署说明.md) 和 [最新交接](handover/tasks/20261103_compliance_v4_release_blockers.md)。本段不为此前 R-1 发布文字提供生产验证背书。

## 企业合规 v4 生产更新（2026-11-03 21:44）

已按用户指令完成后端蓝绿与前端不可变root切换，业务代码c24add5；Nginx主进程39504不变，最终发布探测两路各1815次零失败。正式8080 Java PID18436，前端root为frontend/releases/1.0.22-compliance-v4-c24add5-20261103；两个V4开关true，用户仍自行确认模式切换，原有清单/任务/旧进度保留。测试账号真实登录及只读接口、页面和新旧资源验证通过，未进行生产业务写入或提醒发送测试。

旧磁盘归档小JAR不可启动，禁止用作回滚；已额外保存完整当前包/已验证基线，部署脚本增加可启动回滚校验及WinPS HTTP兼容探测。完整证据、备份和回滚限制见 [最终生产记录](releases/20261103-compliance-v4-production.md)。业务体验待负责人复核，历史R-1文字不替代本次实测。

## 项目配置 v3 待发布增量（2026-11-04，PC-20261104）

本地施工新增四张配置表和 project_type 的版本指针/唯一排序约束，尚未执行生产迁移或切流。完整迁移、数据盘点、兼容窗口、接口和回退限制见 [2026-11-04-项目配置V3接口数据字典与发布步骤-部署说明.md](deployment/2026-11-04-项目配置V3接口数据字典与发布步骤-部署说明.md)。迁移文件为 database/migration/20261104_project_config_snapshots.sql 与 20261104_project_config_finalize.sql，中间必须运行受控离线预检/回填；异常和旧规则差异需要复核。

新后端依赖存量快照完整性；旧后端不支持 NONE 和固定流程，不能无条件回滚旧包。生产迁移/发布须另行批准。所有 Maven 验证在 .build-project-config-acceptance/backend 内完成，禁止清理或重打生产服务正在使用的 backend/target。

## 个人工作台 V1.1.1（本地实施，未发布）

本次新增工作台、全局搜索及原页面精确定位；业务库无需新迁移，现有生产端口/认证/CORS与角色权限规则保持。发布前须先核实目标库的项目配置快照与应用兼容性，不能自动运行其他项目迁移。正式上线仍须独立批准并使用现有不停机发布流程。

隔离验证使用环回MySQL13319/dashboard_acceptance、应用18085、现有允许来源localhost:5173，均非生产。`scripts/test-dashboard.ps1`具有数据库身份护栏，后端制品与前端build均输出`.build-dashboard-acceptance`；需设置测试专用DASHBOARD_ACCEPTANCE_DB与DASHBOARD_DB_PASSWORD，不得复用生产凭证。详细API、测试命令、结果、风险和回滚见[工作台API/部署/测试说明](deployment/2026-11-04-个人工作台V1.1.1接口与隔离验证-部署说明.md)。

## R-1 发布记录
- 2026-11-04 15:45:37 R-1 发布 1.0.24-dashboard-20261104 成功（jar: expert-system.jar）

### 2026-11-04 工作台1.0.24生产结果

已按用户批准完成蓝绿后端与不可变前端root发布，源码1980a66；master39504不变、健康/授权只读业务/372资源核验通过。切流原探针1次连接重置，严格连续性门禁失败；单独稳态3034请求零失败，最终PUBLISHED_WITH_WARNING，待业务验收。无本轮迁移或密钥轮换。完整证据、备份和旧后端补丁兼容回滚限制见[生产发布记录](releases/20261104-dashboard-production.md)。

## 2026-11-05 企业合规模板草稿隔离验收
同一受控进程新建隔离库、生成并传递临时凭据、运行数据库及真实Edge流程通过；生产构建已准备。本轮未执行生产发布：当前终端非管理员，LocalSystem后端未配置中文OCR模型路径。接口、复现步骤和局限见[合规部署说明](deployment/2026-11-03-企业合规V4隔离验收灰度切换与回滚-部署说明.md)及[任务交接](handover/tasks/20261105_compliance_template_generation.md)。

平滑重载2026-11-05诊断：管理员注册SYSTEM任务被Windows返回0x80070005拒绝访问；原超时为后续查询的次生错误。脚本已补显式TaskPath与注册/启动/查询失败立即停止。仍需恢复系统任务注册权限后实测；不得将脚本修正认作重载成功。

## R-1 发布记录
- 2026-11-05 10:23:18 R-1 发布 1.0.24-compliance-template-20261105-r2 成功（jar: expert-system.jar）

2026-11-05最终状态：合规模板草稿已蓝绿上线，PUBLISHED_WITH_WARNING。Nginx平滑重载改为Task Scheduler原生COM，未放宽权限；登录态业务只读检查通过，但fresh连续性探针2次连接重置，严格不停机未通过。见[发布记录](releases/20261105-compliance-template-production.md)。

2026-11-05可视化维护阶段三最终结果：核心链路真实MySQL和Edge验收通过，含员工新建后模板更新不改变原任务；生产四表尚不存在。发布评估为可进入业务复核和发布准备，本次不执行生产迁移/切流。详见releases/20261105-compliance-visual-release-assessment.md。

## 2026-11-06 审查问题修复（P1-2/P1-5/P2-7/P2-9，本地实施，未发布）

> 代码已在当前工作区完成并通过后端测试；**尚未执行数据库迁移、未发布、未做接口实测**。发布需负责人另行批准。

### 数据库迁移（按顺序执行，均幂等）

1. `database/migration/20261106_add_user_token_version.sql`：`sys_user.token_version INT NOT NULL DEFAULT 0`。
2. `database/migration/20261106_add_knowledge_parse_status.sql`：`knowledge_document.parse_status/parse_message/parse_time`。

执行要求：先备份；使用 `mysql --default-character-set=utf8mb4`，含中文的脚本用文件重定向而非 PowerShell 管道（见本文件 2026-10-26 运维警告 2）。脚本只加列/不改旧数据，可重复执行。

### 上线影响

- **所有已登录用户需重新登录一次**：新过滤器要求 Token 携带 `tv` claim，升级前签发的存量 Token 无该 claim，一律按未认证处理（401）。这是 P1-2 的预期行为，不是故障。
- 停用/删除/改密/改角色后：单实例下一次请求即失效；多实例最迟 `expert.auth.state-cache-seconds`（默认 60 秒）生效。
- 知识文档上传/更新接口不再同步抽取正文：请求返回时 `parseStatus=PENDING`，随后异步更新为 `RUNNING/SUCCESS/FAILED`；解析失败不影响上传结果，失败原因见 `parseMessage`。
- 线程池队列满时采用 AbortPolicy：上传仍成功，文档被同步标记 `FAILED`（“解析任务排队已满”），可重新上传或后续人工重试（当前未提供重试接口）。

### 新增配置（均有默认值，可不改）

```yaml
expert:
  auth:
    state-cache-seconds: 60          # 登录态用户状态缓存 TTL
  knowledge:
    parse:
      core-pool-size: 2
      max-pool-size: 4
      queue-capacity: 100
```

### 发布后冒烟

1. 迁移后 `SHOW COLUMNS FROM sys_user LIKE 'token_version'`、`SHOW COLUMNS FROM knowledge_document LIKE 'parse_%'` 均有结果。
2. 旧 Cookie 访问 `/api/auth/me` 返回 401；重新登录后 200，且响应 Cookie 可继续访问。
3. 管理员停用测试账号 → 该账号下一次请求 401；重新启用并登录后恢复。
4. 修改密码后当前会话收到新 Cookie 且保持登录，其他会话旧 Cookie 401。
5. 上传 PDF/DOCX 后列表 `parseStatus` 由 PENDING → SUCCESS，`content` 非空；上传 `enterprise-compliance` Excel 后生成合规草稿。
6. 下架文档：普通账号直接访问 `/api/knowledge/docs/files/{fileName}` 返回业务码 403（HTTP 200，项目统一 body.code 约定），`knowledge:maintain` 账号正常返回文件；删除后返回业务码 404。

### 迁移演练结果（2026-11-06，仅测试库）

已在隔离 MySQL `127.0.0.1:13319`（datadir 含 `.build-dashboard-acceptance`）的专用库 `expert_system_migration_test` 完成演练：首轮按顺序执行两个脚本成功；存量样本行数与正文不变，新增列默认值正确；第二轮重复执行成功且无重复列错误；`token_version` 自增与 `parse_*` 写入可正常回读。测试实例为 MySQL 26.7.0。

### 生产备份与恢复演练结果（2026-11-06）

生产库实测为 MySQL 26.7.0（`127.0.0.1:3306/expert_system`）。已用 `backup\backup_20261106_101945` 对 `expert_system_restore_test` 完成 Drill：表集合 74/74、独立逐表 `COUNT(*)` 均为 2714 行且差异为 0、全库 74 表 `CHECKSUM TABLE` 全部一致、目标表列结构一致。生产迁移仍未执行，须另行批准并选择低峰窗口。

> 首次 Drill 自动日志曾显示 3797 行；`backup.ps1` 已修复成功退出码并改为单条 `UNION ALL` 汇总，2026-11-06 复跑输出 2714 行、退出码 0，与独立逐表 `COUNT(*)` 及 `CHECKSUM TABLE` 一致。

### 回滚

- 代码回滚到发布前版本即可；新增列可保留（旧代码忽略），无需删列。
- 回滚后旧代码不再校验 `tv`，新 Token 仍可被旧代码解析（多余 claim 被忽略），不会造成登录中断。
- 已处于 `PENDING/RUNNING` 的解析记录不会被旧代码继续处理；可保留展示或人工置空，不影响文件下载。
- P2-9 仅移除未被引用的 `spring-boot-starter-validation` 声明，无接口/数据影响；回滚 `pom.xml` 即恢复。

### 工作台二维布局

迁移顺序、构建开关、隔离验证、静态制品回退和 API 契约见 [工作台二维布局接口与部署说明](deployment/2026-11-06-工作台二维布局-接口部署与验收.md)。

> 2026-11-09 现状补充（1.0.37.28）：布局编辑已固定启用，前端构建**不再需要** `VITE_DASHBOARD_LAYOUT_V2=true`（历史上漏带该开关会导致功能整块消失，见 1.0.34.1.1 editfix）。旧版布局回退统一由后端 `/api/dashboard/layout/v2` 的 `legacyHash` 校验处理，前端不再读取 `/api/dashboard/layout`。本次「工作台与实际功能对齐」**无数据库迁移**，发布链路为 `temp\deploy-13728.cmd`（后端蓝绿 → 前端 graceful，ReleaseId `1.0.37.28-dashboard-align-20261109`）；验证结果与发布后核验清单见 [发布记录](releases/20261109-dashboard-align-1.0.37.28.md)。1.0.37.28 已发布并核验通过；发布后发现快捷访问候选 13 项与后端 `MAX_ITEMS=10` 冲突（保存布局 400），已出纯前端补丁 1.0.37.28.1（仅前端 graceful，后端不动），详见同文档 §8。

## R-1 发布记录

> 2026-11-06 起发布记录统一以表格行追加（实现 `scripts/release-record.ps1`），以下为历史记录汇总。

| 时间 | 版本 | 结果 | 制品 |
| --- | --- | --- | --- |
| 2026-10-26 09:29:01 | 1.001 | 成功 | expert-system-1.001.jar |
| 2026-10-26 11:26:53 | 1.003 | 成功 | expert-system-1.003.jar |
| 2026-10-26 16:21:51 | 1.004 | 成功 | expert-system-1.004.jar |
| 2026-10-27 08:32:33 | 1.005 | 成功 | expert-system-1.005.jar |
| 2026-10-27 08:59:29 | 1.005 | 成功 | expert-system-1.005.jar |
| 2026-10-27 14:55:12 | 1.006 | 成功 | expert-system-1.006.jar |
| 2026-10-27 20:40:35 | 1.007 | 成功 | expert-system-1.007.jar |
| 2026-10-29 08:33:53 | 1.009 | 成功 | expert-system-1.009.jar |
| 2026-10-29 17:15:33 | 1.009 | 成功 | expert-system-1.009-fix.jar |
| 2026-10-29 19:21:57 | 1.009 | 成功 | expert-system-1.009.jar |
| 2026-10-30 09:08:48 | 1.010 | 成功 | expert-system-1.010.jar |
| 2026-10-30 12:31:26 | 1.010 | 成功 | expert-system-1.010.jar |
| 2026-10-30 14:23:57 | 1.011 | 成功 | expert-system-1.011.jar |
| 2026-10-30 20:34:24 | 1.0.10 | 成功 | expert-system.jar |
| 2026-10-30 21:35:34 | 1.0.10 | 成功 | expert-system.jar |
| 2026-10-30 23:01:20 | 1.0.10 | 成功 | expert-system.jar |
| 2026-10-31 08:34:48 | 1.0.10 | 成功 | expert-system.jar |
| 2026-10-31 09:09:15 | 1.011 | 成功 | expert-system.jar |
| 2026-10-31 09:49:53 | 1.012 | 成功 | expert-system.jar |
| 2026-10-31 18:33:30 | 1.0.13 | 成功 | expert-system.jar |
| 2026-10-31 21:21:58 | 1.0.14 | 成功 | expert-system.jar |
| 2026-10-31 23:07:32 | 1.0.15 | 成功 | expert-system.jar |
| 2026-11-01 08:24:53 | 1.0.16 | 成功 | expert-system.jar |
| 2026-11-01 16:01:48 | 1.0.17 | 成功 | expert-system.jar |
| 2026-11-02 11:26:00 | 1.011 | 成功 | expert-system.jar |
| 2026-11-02 14:21:10 | 1.012 | 成功 | expert-system.jar |
| 2026-11-02 16:16:15 | 1.012 | 成功 | expert-system.jar |
| 2026-11-02 16:18:50 | 1.012 | 成功 | expert-system.jar |
| 2026-11-02 22:12:43 | 1.0.21 | 成功 | expert-system-1.0.21-new.jar |
| 2026-11-03 00:37:34 | 1.0.22 | 成功 | expert-system.jar |
| 2026-11-03 08:34:06 | 1.022 | 成功 | expert-system.jar |
| 2026-11-03 09:13:52 | 1.022 | 成功 | expert-system.jar |
| 2026-11-03 09:21:05 | 1.022 | 成功 | expert-system.jar |
| 2026-11-03 09:36:14 | 1.022 | 成功 | expert-system.jar |
| 2026-11-03 09:39:26 | 1.022 | 成功 | expert-system.jar |
| 2026-11-03 15:36:44 | 1.0.22-review-fixes | 成功 | expert-system.jar |
| 2026-11-03 17:07:38 | 1.022 | 成功 | expert-system.jar |
| 2026-11-03 17:11:48 | 1.022 | 成功 | expert-system.jar |
| 2026-11-03 17:15:55 | 1.022 | 成功 | expert-system.jar |
| 2026-11-03 17:19:23 | 1.022 | 成功 | expert-system.jar |
| 2026-11-03 21:43:52 | compliance-v4-c24add5 | 成功 | expert-system.jar |
| 2026-11-04 11:31:21 | 1.0.23 | 成功 | expert-system.jar |
| 2026-11-05 12:04:57 | 1.0.25-compliance-visual-20261105 | 成功 | expert-system.jar |
| 2026-11-05 13:59:41 | 1.0.26-compliance-visual-hotfix-20261105 | 成功 | expert-system.jar |
| 2026-11-05 14:24:55 | 1.0.27-compliance-visual-hotfix-20261105 | 成功 | expert-system.jar |
| 2026-11-05 15:33:25 | 1.0.28-compliance-visual-hotfix-20261105 | 成功 | expert-system.jar |
| 2026-11-05 16:25:28 | 1.0.29-compliance-visual-hotfix-20261105 | 成功 | expert-system.jar |
| 2026-11-06 08:37:36 | 1.0.30-compliance-visual-hotfix-20261105 | 成功 | expert-system.jar |
| 2026-11-06 13:47:48 | 1.0.31-dashboard-layout-20261106 | 成功 | candidate.jar |
| 2026-11-06 15:24:03 | 1.0.32 | 成功 | expert-system.jar |
| 2026-11-06 16:32:40 | 1.0.33 | 成功 | expert-system.jar |
| 2026-11-06 16:44:43 | 1.0.34 | 成功 | expert-system.jar |
| 2026-11-07 09:32:58 | 1.0.35 | 成功 | expert-system.jar |
| 2026-11-07 10:52:48 | 1.0.36 | 成功 | expert-system.jar |
| 2026-11-07 12:10:43 | 1.0.37 | 成功 | expert-system.jar |
| 2026-11-07 12:31:42 | 1.0.37.1 | 成功 | expert-system.jar |
| 2026-11-07 13:31:39 | 1.0.37.2 | 成功 | expert-system.jar |
| 2026-11-07 14:44:46 | 1.0.37.3 | 成功 | expert-system.jar |
| 2026-11-07 15:14:35 | 1.0.37.4 | 成功 | expert-system.jar |
| 2026-11-07 15:33:00 | 1.0.37.5 | 成功 | expert-system.jar |
| 2026-11-07 15:53:10 | 1.0.37.6 | 成功 | expert-system.jar |
| 2026-11-07 16:00:45 | 1.0.37.7 | 成功 | expert-system.jar |
| 2026-11-07 16:28:46 | 1.0.37.8 | 成功 | expert-system.jar |
| 2026-11-07 16:38:49 | 1.0.37.9 | 成功 | expert-system.jar |
| 2026-11-07 16:53:08 | 1.0.37.10 | 成功 | expert-system.jar |
| 2026-11-07 17:08:02 | 1.0.37.11 | 成功 | expert-system.jar |
| 2026-11-09 08:34:18 | 1.0.37.12 | 成功 | expert-system.jar |
| 2026-11-09 10:11:12 | 1.0.37.19 | 成功 | expert-system.jar |
| 2026-11-09 10:42:21 | 1.0.37.21 | 成功 | expert-system.jar |
| 2026-11-09 10:56:22 | 1.0.37.22 | 成功 | expert-system.jar |
| 2026-11-09 11:31:36 | 1.0.37.23 | 成功 | expert-system.jar |
| 2026-11-09 12:58:11 | 1.0.37.24 | 成功 | expert-system.jar |
| 2026-11-09 14:18:52 | 1.0.37.25 | 成功 | expert-system.jar |
| 2026-11-09 14:43:44 | 1.0.37.25 | 成功 | expert-system.jar |
| 2026-11-09 15:01:16 | 1.0.37.26 | 成功 | expert-system.jar |
| 2026-11-09 16:00:01 | 1.0.37.27 | 成功 | expert-system.jar |
| 2026-11-09 17:21:31 | 1.0.37.28 | 成功 | expert-system.jar |
| 2026-11-10 08:59:45 | 1.0.37.29 | 成功 | expert-system.jar |
| 2026-11-10 09:43:00 | 1.0.37.30 | 成功 | expert-system.jar |
| 2026-11-10 11:19:09 | 1.0.37.31 | 成功 | expert-system.jar |
| 2026-11-10 12:07:53 | 1.0.37.32 | 成功 | expert-system.jar |
| 2026-11-10 13:15:10 | 1.0.37.33 | 成功 | expert-system.jar |
| 2026-11-10 15:37:11 | 1.0.37.34 | 成功 | expert-system.jar |
| 2026-11-10 16:01:36 | 1.0.37.35 | 成功 | expert-system.jar |
| 2026-11-10 17:17:07 | 1.0.37.36 | 成功 | expert-system.jar |
| 2026-11-10 17:27:11 | 1.0.37.36.1 | 成功 | expert-system.jar |
| 2026-11-10 18:01:09 | 1.0.37.37 | 成功 | expert-system.jar |
| 2026-11-10 18:40:39 | 1.0.37.38 | 成功 | expert-system.jar |
| 2026-11-10 19:10:08 | 1.0.37.39 | 成功 | expert-system.jar |
| 2026-11-11 10:19:33 | 1.0.38.1 | 成功 | expert-system.jar |
| 2026-11-11 10:34:52 | 1.0.38.2 | 成功 | expert-system.jar |
| 2026-11-11 11:18:09 | 1.0.38.3 | 成功 | expert-system.jar |
| 2026-11-11 11:44:12 | 1.0.38.4 | 成功 | expert-system.jar |
| 2026-11-11 12:09:04 | 1.0.38.5 | 成功 | expert-system.jar |
| 2026-11-11 13:03:06 | 1.0.38.6 | 成功 | expert-system.jar |
| 2026-11-11 15:20:38 | 1.0.38.7 | 成功 | expert-system.jar |
| 2026-11-12 08:24:45 | 1.0.38.8 | 成功 | expert-system.jar |
| 2026-11-12 09:39:30 | 1.0.38.9 | 成功 | expert-system.jar |
| 2026-11-12 10:00:22 | 1.0.38.10 | 成功 | expert-system.jar |
| 2026-11-12 10:18:14 | 1.0.38.11 | 成功 | expert-system.jar |
| 2026-11-12 11:39:42 | 1.0.38.12 | 成功 | expert-system.jar |
| 2026-11-12 12:29:02 | 1.0.38.13 | 成功 | expert-system.jar |
| 2026-11-12 13:36:00 | 1.0.38.14 | 成功 | expert-system.jar |
| 2026-11-12 14:42:39 | 1.0.38.15 | 成功 | expert-system.jar |
| 2026-11-12 15:44:03 | 1.0.38.16 | 成功 | expert-system.jar |
| 2026-11-12 16:05:03 | 1.0.38.17 | 成功 | expert-system.jar |
| 2026-11-12 16:16:14 | 1.0.38.18 | 成功 | expert-system.jar |
| 2026-11-13 09:40:18 | 1.0.38.19 | 成功 | expert-system.jar |
| 2026-11-13 13:44:46 | 1.0.38.23 | 成功 | expert-system.jar |
| 2026-11-13 14:53:11 | 1.0.38.24 | 成功 | expert-system.jar |
| 2026-11-13 15:53:04 | 1.0.38.25 | 成功 | expert-system.jar |
| 2026-11-13 16:05:48 | 1.0.38.26 | 成功 | expert-system.jar |
| 2026-11-13 16:50:33 | 1.0.38.27 | 成功 | expert-system.jar |
| 2026-11-13 17:07:10 | 1.0.38.28 | 成功 | expert-system.jar |
| 2026-11-13 17:19:17 | 1.0.38.29 | 成功 | expert-system.jar |
| 2026-11-16 09:24:03 | 1.0.38.30 | 成功 | expert-system.jar |
| 2026-11-16 09:40:35 | 1.0.38.31 | 成功 | expert-system.jar |
| 2026-11-16 11:39:23 | 1.0.39 | 成功 | expert-system.jar |
| 2026-11-16 12:17:12 | 1.0.40 | 成功 | expert-system.jar |
| 2026-11-16 12:42:19 | 1.0.41 | 成功 | expert-system.jar |
| 2026-11-16 13:54:53 | 1.0.42 | 成功 | expert-system.jar |
| 2026-11-16 14:12:54 | 1.0.43 | 成功 | expert-system.jar |
| 2026-11-16 14:24:50 | 1.0.44 | 成功 | expert-system.jar |
| 2026-11-16 14:47:55 | 1.0.45 | 成功 | expert-system.jar |
| 2026-11-16 16:16:21 | 1.0.45 | 成功 | expert-system.jar |
| 2026-11-16 16:27:58 | 1.0.46 | 成功 | expert-system.jar |
| 2026-11-17 09:05:27 | 1.0.47 | 成功 | expert-system.jar |
| 2026-11-17 10:18:53 | 1.0.48 | 成功 | expert-system.jar |
| 2026-11-17 10:58:20 | 1.0.49 | 成功 | expert-system.jar |
| 2026-11-17 12:53:11 | 1.0.50 | 成功 | expert-system.jar |
| 2026-11-17 14:32:05 | 1.0.51 | 成功 | expert-system.jar |
| 2026-11-17 15:00:31 | 1.0.52 | 成功 | expert-system.jar |
| 2026-11-17 17:28:36 | 1.0.54 | 成功 | expert-system.jar |
| 2026-11-18 08:55:35 | 1.0.55 | 成功 | expert-system.jar |
| 2026-11-18 10:05:04 | 1.0.57 | 成功 | expert-system.jar |
| 2026-11-18 10:45:03 | 1.0.58 | 成功 | expert-system.jar |
| 2026-11-18 12:20:52 | 1.0.59 | 成功 | expert-system.jar |
| 2026-11-18 12:51:45 | 1.0.60 | 成功 | expert-system.jar |
| 2026-11-18 13:51:24 | 1.0.61 | 成功 | expert-system.jar |
| 2026-11-18 14:23:02 | 1.0.62 | 成功 | expert-system.jar |
| 2026-11-18 14:50:16 | 1.0.63 | 成功 | expert-system.jar |
| 2026-11-18 15:18:39 | 1.0.64 | 成功 | expert-system.jar |
| 2026-11-18 15:38:31 | 1.0.65 | 成功 | expert-system.jar |
| 2026-11-18 16:13:31 | 1.0.67 | 成功 | expert-system.jar |
| 2026-11-18 17:01:07 | 1.0.68 | 成功 | expert-system.jar |

### 1.0.38.24 发布事故与修复记录（2026-11-13）

- **事故**：14:27 蓝绿发布中，deploy-backend.ps1 缺省 jar 选择按 LastWriteTime 在 builds\ 下取"最新"，但构建产物时间戳经 reproducible-builds 归一化（全部 1980-02-01），同时间戳下随机命中今晨 1.0.38.19 旧构建目录，**生产在 14:27~14:41 期间意外运行 1.0.38.19（自 1.0.38.23 降级）**；阶段 9e 健康检查只探活不核版本，未能拦截。
- **发现与纠正**：发布后核验 /health 发现 version=1.0.38.19；修复脚本护栏后以 -JarPath 显式指定 1.0.38.24 制品重新蓝绿发布，14:53 完成切回，实测 8080 直连与经 Nginx 均为 1.0.38.24（gitShort=96f35d1），JWT/MYSQL 未变更免重登。
- **护栏修复（deploy-backend.ps1）**：① 期望版本先行解析（version.txt 唯一真源），候选 jar 必须通过 build.version 一致性校验，无匹配即 fail-fast 并列出候选版本清单；② 显式 -JarPath 亦强制版本核对；③ 阶段 9e 健康检查增加 /health 版本比对，不符触发既有自动回滚。
- **已知行为（暂不改，留观察）**：阶段 10 的 8081 清空护栏会被 Nginx upstream keepalive 或本地浏览器直连连接卡住而中止（脚本保留影子进程待人工确认）；本次两次发布均人工核验连接归属（chrome/python 直连，非生产流量）后终止影子进程完成收尾。后续可评估更精细的"真实业务流量"判定。
- **回滚基线**：1.0.38.23 jar = `backend/releases/1.0.38.24-20261113_142742/previous-expert-system.jar`（已核验 build.version=1.0.38.23）；注意 `releases/1.0.38.24-20261113_1428xx`（第二次运行归档）里的 previous jar 是事故期误选的 1.0.38.19 包，**禁止作为回滚源**。
- **备案**：8081 端口存在发布前即有的无关 python 监听进程（PID 8340，12:34 启动），与后端影子端口共存，待排查归属。

## 构建与发布脚本约定（2026-11-06 改造）

本节固定 7 项改造的落地方式，后续任何人改脚本前先读这一节，改完必须按「验证方式」逐条过一遍。

### 一、改动范围总览

| # | 改造项 | 涉及文件 | 类型 |
|---|--------|----------|------|
| 1 | 日志统一 UTF-8 | `backend/src/main/resources/logback-spring.xml`（新建）、`scripts/logging.ps1`（新建）、`scripts/deploy-backend.ps1`、`scripts/release-backend.ps1` | 配置 + 脚本 |
| 2 | 发布记录表格化 | `scripts/release-record.ps1`（新建）、`scripts/deploy-backend.ps1`（阶段 11）、`docs/deployment.md` | 脚本 + 文档 |
| 3 | 版本号单一来源 | `version.txt`、`backend/pom.xml`、`backend/src/main/resources/version.properties`、`scripts/build-paths.ps1`（新建）、`scripts/build-all.ps1`（重写） | 配置 + 脚本 |
| 4 | `/health` 增 `buildTime`/`gitShort` | `backend/pom.xml`（spring-boot build-info goal）、`backend/src/main/java/com/jshz/expert/controller/HealthController.java`、`HealthControllerTest.java`（新建） | 代码 |
| 5 | 日志轮转 | `logback-spring.xml`（应用层）、`scripts/configure-backend-logging.ps1`（新建，NSSM 层） | 配置 + 脚本 |
| 6 | 停服后轮询确认进程退出 | `scripts/deploy-backend.ps1`（`Wait-BackendProcessExit`、阶段 9a） | 脚本 |
| 7 | build 目录统一归档 | `scripts/build-paths.ps1`、`scripts/build-all.ps1`、`scripts/deploy-backend.ps1`、`scripts/release-backend.ps1`、`builds/README.md`（新建） | 脚本 + 目录约定 |

### 二、各改动的实现要点

**1) 日志统一 UTF-8**

- 文件日志由 logback 强制编码：`APP_FILE` appender 显式 `<charset>UTF-8</charset>`，不受系统代码页影响。
- 控制台 appender 保留默认（不写 charset），因为 Windows 控制台仍是 GBK，硬写 UTF-8 会在命令行里显示成乱码；如需统一，先在 NSSM 侧加 `-Dsun.stdout.encoding=UTF-8`（见第 5 项）。
- 脚本侧统一出口：所有发布/构建脚本 dot-source `scripts/logging.ps1`，用 `Write-Utf8Text` / `Add-Utf8Line` / `Add-Utf8Text` 写文件，全部 `New-Object System.Text.UTF8Encoding($false)`——**不写 BOM**，避免 `docs/*.md` 被下游工具误读。
- 各脚本开头调用 `Enable-Utf8Console`，把 `[Console]::OutputEncoding` 设为 UTF-8。

**2) 发布记录改为表格行**

- `docs/deployment.md` 不再追加「## R-1 发布记录」段落的自由文本，改为向**一张表**追加一行：`| 时间 | 版本 | 结果 | 制品 |`。
- `scripts/release-record.ps1` 提供 `Add-ReleaseTableRow`：从文档末尾向上找第一个「带表格的最后一个章节」，把新行插在该表最后一行之后；表不存在则整段补建（标题 + 表头 + 分隔行）。
- 历史遗留的 39 个重复 `## R-1 发布记录` 标题已由 `scripts/normalize-release-log.py` 合并为单表（50 条记录）。含正文的 3 个同名标题按规则保留，未被误合并。

**3) 版本号单一来源**

- 唯一权威：`<LOCAL_PATH>`（一行纯文本，如 `1.0.32`）。
- `backend/pom.xml` 改为 `<version>${revision}</version>`，properties 中 `revision` 缺省值为哨兵 `0.0.0-UNSET`。构建必须显式传 `-Drevision=<version.txt>`，漏传会得到一个版本号为 `0.0.0-UNSET` 的包，肉眼可辨。
- `version.properties` 用 `${revision}` 占位，由 pom 的 `<filters>` 过滤注入（原有机制保留）。
- `scripts/build-paths.ps1` 的 `Get-ProjectRevision` 读 `version.txt`，读不到或为空直接 throw（fail-fast），不做猜测性回退。

**4) `/health` 新增 `buildTime` 与 `gitShort`**

- `spring-boot-maven-plugin` 增 `build-info` execution，绑到 `generate-resources`，产出 `META-INF/build-info.properties`；`<time>${maven.build.timestamp}</time>` 显式指定（不写会回落到 `project.build.outputTimestamp` 缺省值，得到 `1980-02-01T00:00:00Z`），`gitShort` 经 `additionalProperties` 写入。
- `HealthController` 构造注入 `ObjectProvider<BuildProperties>`（本地 IDE 直跑无 build-info 时不至于启动失败），`/health` 返回五字段：`status / time / version / buildTime / gitShort`。
- 取值全部经 `sanitize()`：null、空串、含 `${` 的未替换占位符一律返回 `unknown`，绝不把半成品占位符暴露到接口上。
- `buildTime` 按 `Asia/Shanghai` 格式化 `yyyy-MM-dd HH:mm:ss`。

**5) 日志轮转**

- 应用层（兜底，不依赖服务配置）：`CONSOLE` + `APP_FILE`，`SizeAndTimeBasedRollingPolicy`，`maxFileSize=20MB`、`maxHistory=30`、`totalSizeCap=2GB`、`cleanHistoryOnStart=true`，日志目录 `LOG_DIR`（默认 `<LOCAL_PATH>`，可用系统属性覆盖）。
- 服务层：`scripts/configure-backend-logging.ps1` 用 `nssm set` 幂等写入 `AppRotateFiles=1`、`AppRotateOnline=1`、`AppRotateBytes=10485760`，并在 `AppParameters` 的 `-jar` 之前插入 `-Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8`（往 NSSM 重定向的 stdout 写 UTF-8，避免中文日志在重定向文件里变成 `?`）。脚本不触碰 `AppEnvironmentExtra`，数据库密码等环境变量不受影响；重复执行不会重复插入 `-D` 参数。

**6) 停服后轮询确认进程真正退出**

- 旧版 `deploy-backend.ps1` 只等 `Stop-Service` 返回就换包，遇到 NSSM 报了 Stopped 但 java 还在退出中（正在写日志、刷盘）会拿到「文件被占用」，甚至出现新旧 jar 半混状态。
- 新版阶段 9a 流程：`Stop-Service` → 校验 `Status -eq 'Stopped'` → `Wait-BackendProcessExit` 轮询。
- 轮询判据三重：8080 端口无监听（`Get-PortListenerPids`）、目标 jar 可独占打开（`Test-FileExclusivelyOpenable`）、残留 java 进程（命令行含目标 jar 路径）已消失。默认最长 90 秒，每 500ms 一次。
- 超时直接 `throw`，**一份文件都不替换**，宁可发布失败也不留半套制品；成功时打一行「旧进程已退出且制品已解锁（轮询 N 次）」，便于事后核对。

**7) build 目录统一归档**

- 统一根：`<LOCAL_PATH>`。历史产物一律进 `builds\archive\`；每次构建在 `builds\<版本>-build-<yyyyMMdd_HHmmss>\` 下产出，不在仓库根或 `backend\target` 周边留垃圾。
- `scripts/build-paths.ps1` 集中定义 `$BuildsRoot` / `$BuildsArchive`，并提供 `New-BuildStage`、`Assert-UnderBuilds`（越界写入直接 throw）。
- **构建必须隔离目录**：`mvn clean` 在 `backend\target` 会因生产进程占用 `expert-system.jar` 而失败，因此 `build-all.ps1` 把后端源码复制到 `builds\<stage>\backend` 再 `mvn clean package`，彻底避开占用。
- 候选包查找只遍历 `builds\` 一级子目录并排除 `archive`，避免误把历史包当最新候选（该 bug 已实际出现过：`-Recurse` 递归进 `archive` 捡到旧 jar）。
- 已归档：27 个历史目录共约 11 GB；空壳目录 `.build-1.0.22-v4-20261103_165002` 已清理，根目录 `.build-*` 已清零。目录约定与清理策略见 `builds/README.md`。

### 三、构建与发布命令

```text
# 构建（前端 + 后端，产物落 builds\<版本>-build-<时间戳>\）
powershell -ExecutionPolicy Bypass -File <LOCAL_PATH>

# 预演发布（不动机器，只打印将执行的步骤）
powershell -ExecutionPolicy Bypass -File <LOCAL_PATH> -DryRun

# 正式发布（管理员窗口；.cmd 只做提权自检后转 PowerShell）
<LOCAL_PATH>
```

`release-backend.cmd` 必须是**纯 ASCII + CRLF**。写成 UTF-8 无 BOM + LF 时，`cmd.exe` 按 GBK 解析会把汉字和行尾 CR/LF 一起吞掉，多行拼成一条命令，报 `'満鍙戝彂甯冿?.0.32锛?echo' 不是内部或外部命令` 这类乱码错（已实际踩过）。

### 四、验证方式

| # | 改造项 | 验证命令 / 判据 | 本次结果 |
|---|--------|------------------|----------|
| 1 | 日志 UTF-8 | `iconv -f UTF-8 -t UTF-8 logs/deploy-backend.*.log` 无报错；`head -c 3` 不为 `ef bb bf`（无 BOM） | 通过。三层证据：构建期断言 + 运行中 jar 探针 + 落盘判定，CONSOLE 与 APP_FILE 均 UTF-8 |
| 2 | 发布记录表格 | `powershell -File scripts/tests/test-release-record.ps1`（3 用例 10 断言） | 10/10 PASS |
| 3 | 版本单一来源 | 构建日志出现 `[OK] build-info 校验：version=1.0.32`；漏传 `-Drevision` 时包内版本应为 `0.0.0-UNSET` | 通过（1.0.32 / b221ad4） |
| 4 | `/health` 新字段 | `curl --noproxy '*' http://127.0.0.1:8080/health`，`data` 含 `buildTime`、`gitShort`；单测 `-Dtest=HealthControllerTest` | 3/3 PASS，实测 `buildTime=2026-11-06 15:58:22`、`gitShort=b221ad4` |
| 5 | 日志轮转 | 服务启动后 `logs/expert-app.log` 自动生成且 UTF-8 合法；`nssm get <服务> AppRotateBytes` 返回 `10485760` | 通过（两层均生效）：NSSM 已把 11.36MB 的 out.log 轮转为 `expert-backend.out-20261106T083210.791.log`；应用层 `expert-app.log` UTF-8 |
| 6 | 停服轮询 | 发布日志出现「旧进程已退出且制品已解锁（轮询 N 次）」；人为占住 jar 时应超时 throw 且不替换文件 | 已实测：1.0.33、1.0.34 两次蓝绿发布均打印「轮询 1 次」 |
| 7 | 目录归档 | `ls -d <LOCAL_PATH>` 无输出；`builds\` 下仅新版构建目录 + `archive\` | 通过（27 目录 11 GB 迁入） |

### 五、执行与验证结果（2026-11-06 16:45 收口）

- **NSSM 侧参数已由负责人执行 `scripts/configure-backend-logging.ps1` 落盘**：`AppRotateFiles=1` / `AppRotateOnline=1` / `AppRotateBytes=10485760`（均 DWord），`AppParameters` 已插入 `-Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8`。
- **1.0.33**（16:32）与 **1.0.34**（16:44）两次蓝绿发布已上线，`/health` 分别返回对应 `version`、`buildTime`、`gitShort`；阶段 9a 轮询两次均一次通过。
- 应用层 logback 轮转、NSSM 轮转、发布日志 UTF-8 均已在生产验证。

### 六、日志保留策略（2026-11-06 落地）

| 日志 | 产生者 | 保留 | 执行者 |
| --- | --- | --- | --- |
| `expert-app.log` / `expert-app.<日期>.<序号>.log.gz` | logback | 20MB/文件、30 天、总量 2GB | logback 自管 |
| `expert-backend.out-<UTC>.log` / `.err-<UTC>.log` | NSSM 轮转（**每次服务启动必轮转一次**，运行中超 10MB 也轮转） | 各保留最近 **10** 个 | `scripts/prune-logs.ps1` |
| `deploy-backend.<yyyyMMdd_HHmmss>.log` | `release-backend.ps1` | 保留最近 **20** 个 | 同上 |
| `expert-backend.out.log` / `.err.log` | NSSM 当前重定向 | 永不删除 | — |

- 自动执行：`deploy-backend.ps1` 阶段 11 调用 `prune-logs.ps1`（DryRun 时只打印）。
- 手动：`.\scripts\prune-logs.ps1 [-DryRun] [-KeepNssm 10] [-KeepDeploy 20]`。
- NSSM 轮转产物命名规则为 `原名-UTC时间戳.扩展名`（如 `expert-backend.out-20261106T083210.791.log`，`08:32:10Z` = 北京时间 16:32:10），**不是** `.log.<时间戳>`。

### 七、前端发布的真实路径（与 README R-2 一致）

- 线上 `Nginx root` 指向 `frontend/releases/<ReleaseId>` 快照目录，发布靠改 root + graceful reload，**不是**替换 `frontend/dist`。
- 发布：`scripts\deploy-frontend-graceful.ps1 -StagedDist <构建目录> -ReleaseId <标签> -Apply`（管理员）；先不带 `-Apply` 出计划。
- 回滚：改回 `frontend/release-metadata/<ReleaseId>.rollback.conf.txt` 记录的 root，`nginx -t` + graceful reload。
- `scripts/deploy-frontend.ps1` 已加护栏：root 不指向 `frontend\dist` 时直接 `[ABORT]` 退出码 2。

### 八、前端目录保留策略（2026-11-06 落地，2026-11-12 调整默认保留 5）

`frontend/` 下历史目录分三类，由 `scripts/prune-frontend-releases.ps1` 统一清理：

| 类型 | 命名 | 说明 | 策略 |
| --- | --- | --- | --- |
| 正式快照 | `releases/<ReleaseId>` | 有 `release-metadata/<ReleaseId>.nginx-before.txt` 的才是真快照 | 保留最近 `-KeepReleases`（默认 **5**）个 |
| 回滚副本 | `releases/<ReleaseId>.rollback` | 每次发布复制的上一版完整副本，内容与上一版快照相同 | 仅保留当前 release 对应的那一个 |
| 失效流程遗留 | `dist_old.*` / `dist-new.*` / `dist-green` / `dist-review-fixes` / `.build-*` | 旧 dist 替换流程产物，从未被 Nginx 服务 | 全部删除 |

四重保护：Nginx 当前 root、当前 root 的 `.rollback` 回滚目标、最近 N 个正式快照、路径必须落在 `frontend/` 下——任一命中即不删。

```powershell
.\scripts\prune-frontend-releases.ps1 -DryRun   # 预演，列出将删目录与可释放空间
.\scripts\prune-frontend-releases.ps1           # 执行
```

2026-11-06 实测：清理 136 个目录（删除 75 + 61），`releases/` 由 89 个 / 2.8 GB 降至 4 个 / 409 MB，`frontend/` 总占用由约 3.4 GB 降至 727 MB（剩余主要为 `node_modules` 311 MB），释放约 2.5 GB。

### 八之二、统一空间保留策略与月度计划任务（2026-11-12 落地）

历史上 `frontend/releases`、`builds`、`temp` 只有前两者有手动脚本、且从未定时执行，实测累积：
`frontend/releases` 136 个目录 / **31.2 GB**、`builds`（不含 archive）**16.5 GB**、`temp` 273 项 / 571 MB。

统一入口 `scripts\prune-all.ps1`（一次执行四类清理）：

| 目录 | 脚本 | 保留规则 | 关键护栏 |
| --- | --- | --- | --- |
| `frontend\releases` | `prune-frontend-releases.ps1` | 最近 **5** 个正式快照 + 当前 root 的 `.rollback` | Nginx 当前 root、回滚目标、路径必须在 `frontend\` 下 |
| `builds\` | `prune-builds.ps1`（新增） | 最近 **5** 个构建目录 | `archive\` 永不删除（含被 release 脚本引用的 `smoke-password.dpapi`），路径必须在 `builds\` 下 |
| `temp\` | `prune-temp.ps1`（新增） | 目录/jar/script/log/other 各类保留最近 **5** 个 | 2 天内修改的跳过；文件名出现在 `docs\`/`scripts\` 文本中的跳过；路径必须在 `temp\` 下 |
| `logs\` | `prune-logs.ps1` | 见上节 | 活动日志永不删除 |

计划任务：`jshzPMOPruneMonthly`（**每月 1 日 03:30，SYSTEM**，与周日 02:00 备份任务错开），执行
`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\prune-all.ps1 -Keep 5`，日志 `logs\prune-<时间戳>.log`。

**temp 根目录散落文件告警（2026-11-13 新增）**：`prune-all.ps1` 在临时产物清理后统计 `temp\` **根目录散文件**数量
（不含子目录），超过阈值（`-ScatterThreshold`，默认 **160**）即输出 `[WARN]` 并列出类型分布与最近修改的 10 个文件；
**只告警不删除**（删除属高风险须人工批准），不影响退出码。`-SkipScatterCheck` 可跳过该检查。
背景：`temp\` 曾堆积 160+ 个一次性脚本/日志（2026-11-13 已清理并审计，见
`docs/handover/tasks/20261113_temp_cleanup_audit.md`）。

```powershell
.\scripts\prune-all.ps1 -DryRun            # 全量预演（不删任何文件）
.\scripts\prune-all.ps1                    # 立即执行一次
.\scripts\register-prune-schedule.ps1 -DryRun      # 查看任务定义
.\scripts\register-prune-schedule.ps1              # 注册/更新（管理员）
.\scripts\register-prune-schedule.ps1 -Unregister  # 删除任务（管理员）
```

2026-11-12 预演结果：前端快照将删 130 个目录 ≈ **29.1 GB**；builds 将删 102 个目录 ≈ **15.9 GB**；
temp 将删 99 项 ≈ 169 MB（27 项因被引用跳过）。

### 九、1.0.34 前端 root 切换记录（2026-11-06 17:03）

- 旧 root：`frontend/releases/1.0.31-dashboard-layout-ui-fix-20261106`（界面显示 v1.0.31）
- 新 root：`frontend/releases/1.0.34-20261106_1705`（界面显示 v1.0.34）
- 回滚目标：`frontend/releases/1.0.34-20261106_1705.rollback`（内容 = 1.0.31-ui-fix，索引页 SHA256 一致）
- Nginx master PID **39504 未变**（graceful reload 只换 worker）
- 验证：远端 `GET /` 的 `index.html` SHA256 = `247924033f3c5f9de84636a643e6627cace0b5e9ee05b90c3d46474fc11ba197`，与本地快照一致；主包 `assets/index-xoNP1tM1.js` 内 `Moe="v1.0.34"`；`/`、主包、CSS、旧 chunk（`AnnouncementDetail-2ZueWjIJ.js`）均 200；`/api/health` 200

### 本地 AI 模型目录迁移（2026-11-13，待切换）

迁移候选以 <LOCAL_PATH> 为模型权重根目录。Ollama 通过 OLLAMA_MODELS 读取 ollama/models。后端通过 COMPLIANCE_PPOCR_MODELS、U2NET_HOME、TESSDATA_DIR 分别指定 PP-OCRv5、抠图和语言模型目录。程序与解释器保留现有安装位置。切换、验证和回滚见 [迁移交接](handover/tasks/20261113_ai_model_migration.md)。

### Ollama 服务健康看门狗（2026-11-17 启用）

`scripts/ollama-health-watch.ps1` 由计划任务 `OllamaHealthWatch` 每 5 分钟以 SYSTEM 运行：对 `http://127.0.0.1:11434/api/generate`（qwen2.5:3b，num_predict=1）做真实推理探测，**60 秒无响应或失败即自动重启 `ExpertSystemOllama` 服务**（NSSM 只在进程退出时重启，GPU/模型加载假死不会自愈）；重启后最长等待 180 秒回连并做一次预热推理。健康时静默；事件写 `logs/ollama-watch.log`（自动裁剪保留 300 行）。停用方式：`schtasks /Change /TN OllamaHealthWatch /DISABLE`。模型名可用环境变量 `OLLAMA_MODEL` 覆盖（默认 qwen2.5:3b）。

### 会议预算估算与对客报价（2026-11-17，1.0.47 已发布）

新环境由 `database/init.sql` 初始化。既有环境先查看迁移台账、备份目标库和项目资料库，经授权执行 `database/migration/20261117_budget_estimate_quote.sql`，核实新增列和版本表后再切换前后端。脚本可重试，旧预算无需数据回填。部署沿用现有项目资料目录与 Apache POI 依赖。验收涵盖估算保存、内部确认、版本下载、阶段资料挂载和客户确认。回滚应用制品时保留新增列、版本表和已生成的文件。验收及接口细节见 [估算与对客报价交接](handover/tasks/20261117_budget_estimate_quote.md)。

本机生产迁移、蓝绿切换和前端不可变发布记录见 [1.0.47 发布](releases/20261117-budget-estimate-quote-1.0.47.md)。

### 专家简历 MODNet 抠图

抠图 worker 优先使用 `PHOTOMATTE_MODNET_PATH` 指定的本地 ONNX 权重。默认路径为 `%ProgramFiles%/JSHZ/portrait-matting/modnet.onnx`，变量置空可禁用 MODNet。人像使用原图 RGB 与 MODNet 输出的软 alpha，并沿用透明边距裁剪。模型缺失或加载失败时使用现有 U²-Net 链路。生产部署预置权重并校验 SHA-256，运行时由现有 ONNX Runtime 在本机执行。对照与验证入口见 [抠图评估](handover/tasks/20261118_portrait_matting_modnet_evaluation.md)。

MODNet 试点由 `PHOTOMATTE_MODNET_ENABLED=true|false` 控制。变量未配置时，worker 启动时读取权重同目录默认位置的 `modnet.enabled` 标记。生产可通过移除该标记并重新启动 worker 关闭试点。启用后，MODNet 推理超时或返回空掩码时，解析将返回原图 JPEG 候选。权重缺失或初始化失败时使用 U²-Net。
