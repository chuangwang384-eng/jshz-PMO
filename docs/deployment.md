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

# 部署说明

> 本系统依赖公司内网部署，以下为部署步骤与约束说明。

## 1. 前置要求

- JDK 17+
- Maven 3.8+
- Node.js 18+ / npm 9+
- MySQL 8.x（内网实例）
- Nginx（生产环境反向代理，可选）

## 2. 数据库初始化

```bash
mysql -h <内网MySQL地址> -u <账号> -p < database/init.sql
```

执行后创建 `expert_system` 库及全部 **13 张表**（`expert_info` / `sys_user` / `download_log` / `approval_config` / `expert_change_request` / `approval_record` / `role` / `permission` / `role_permission` / `user_role` / `knowledge_document` / `knowledge_doc_version` / `notification`），并写入初始管理员数据。

> 说明：`init.sql` 使用 `CREATE TABLE IF NOT EXISTS`（不删旧数据），已包含审批流三表与 RBAC 四表；历史环境若仅有三基础表，可执行 `database/migration/20261022_add_approval_tables.sql` 补建审批流三表（approval_config / expert_change_request / approval_record）；RBAC 升级环境执行 `database/migration/20261022_add_rbac_tables.sql` 补建 RBAC 四表（role / permission / role_permission / user_role，幂等可重复执行）；知识星球文档环境执行 `database/migration/20261023_add_knowledge_tables.sql` 补建 knowledge_document / knowledge_doc_version 两表并写入接口权限码 knowledge:manage（绑定 ADMIN，幂等可重复执行）；消息通知中心环境执行 `database/migration/20261023_add_notification_table.sql` 补建 notification 表（幂等可重复执行）；审批模板环境执行 `database/migration/20261026_add_approval_template.sql` 补建 approval_template 表并给 expert_change_request 加 template_id 列、写入模板管理接口权限码与 3 个种子模板（幂等可重复执行）；知识星球增强（C1 内容检索/C3 收藏/C4 下载统计）环境执行 `database/migration/20261026_add_knowledge_c1c3c4.sql` 给 knowledge_document 加 content 列、download_log 加 doc_id 列并补建 knowledge_favorite 收藏表（幂等可重复执行）；B2 审批转办/加签 + 用户操作日志环境执行 `database/migration/20261026_add_b2_transfer_operation_log.sql` 给 expert_change_request 加 current_assignee / extra_approver 两列、新建 operation_log 操作日志表、把历史 download_log 迁移为 operation_log（action_type=DOWNLOAD）并写入转办/加签/可转办用户列表接口权限码（均幂等可重复执行）；「我的申请」改版环境执行 `database/migration/20261026_add_application_table.sql` 新建 application 通用申请主表、把 expert_change_request 全量迁移并入（保留原 id，旧表保留作历史备份）、给 approval_record 加 application_id 列并回填、修正存量通知跳转路径（幂等可重复执行，含 sys_user 与业务表 collation 差异的显式 COLLATE 处理）；供应商管理环境执行 `database/migration/20261026_add_supplier_module.sql` 新建供应商六表、预置 6 类型与 349 城市、写入 supplier:dict 权限码并绑定 ADMIN（幂等可重复执行）；审批「申请前后变化」对比环境执行 `database/migration/20261027_backfill_application_old_data.sql` 回填 application.biz_data.oldData 旧值快照（仅 EXPERT_MODIFY 且 status∈{0,2,3} 的存量单，无 DDL，幂等可重复执行；status=1 已通过单不回填，前端显示历史单提示）；企微通讯录同步与账号体系改造环境执行 `database/migration/20261026_add_wecom_contact_sync.sql` 新建 wecom_department / wecom_user_department / wecom_sync_log 三表、给 sys_user 加 mobile / position 列、建 uk_wecom_userid / uk_mobile 唯一索引、删除 uk_username 前备份为 wecom_migration_uk_username_backup、写入 wecom:sync 权限码（6 条路径绑 ADMIN，幂等可重复执行，已在本机测试库演练 3 遍通过）。以实际库 **26 张表**为准。

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

- [ ] `database/init.sql` 在目标库执行成功，`SHOW TABLES` 确认 **13 张表**（含审批流三表 approval_config / expert_change_request / approval_record、RBAC 四表 role / permission / role_permission / user_role、知识星球两表 knowledge_document / knowledge_doc_version 与消息通知表 notification）
- [ ] 后端启动无异常，`GET /health` 返回 UP，`/api/expert/list` 可访问
- [ ] 审批流接口可用：`GET /api/approval/config`、`POST /api/approval/requests`（修改申请）、`POST /api/approval/create-request`（新增专家申请）
- [ ] 消息通知接口可用：`GET /api/notifications`、`GET /api/notifications/unread-count`、`POST /api/notifications/{id}/read`、`POST /api/notifications/read-all`
- [ ] 知识星球管理员永久删除可用：`DELETE /api/knowledge/docs/{id}/permanent`（删除主记录+版本历史+磁盘文件）
- [ ] 前端页面可正常加载并展示专家列表，顶栏铃铛可见、知识星球父页点击标题跳转/点击箭头展开子菜单
- [ ] （1.007 企微通讯录同步）`database/migration/20261026_add_wecom_contact_sync.sql` 已在目标库执行，`SHOW TABLES` 确认 26 张表；登录页文案随 `AUTH_LOGIN_MODE` 变化；管理页 DryRun 试算→实跑→组织架构树→未绑定清单→人工绑定链路可用；个人中心「通讯录」页展示本部门链路与成员
- [ ] 内网安全策略（IP 白名单 / 端口监听）已生效
*（内容由AI生成，仅供参考）*

## R-1 发布记录
- 2026-10-26 09:29:01 R-1 发布 1.001 成功（jar: expert-system-1.001.jar）
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

## R-1 发布记录
- 2026-10-26 11:26:53 R-1 发布 1.003 成功（jar: expert-system-1.003.jar）

## R-1 发布记录
- 2026-10-26 16:21:51 R-1 发布 1.004 成功（jar: expert-system-1.004.jar）

## 1.004 发布后修复记录（2026-10-26）
- 16:21:51 R-1 蓝绿发布 1.004 后健康检查全过，但实测新接口 404——**根因：NSSM `ExpertSystemBackend` 的 AppParameters 被此前发布改为直接指向 `expert-system-1.003.jar`**（偏离 deploy-backend.ps1 约定的固定名 `expert-system-1.001.jar`），导致阶段 9c 替换 1.001.jar 无效、服务重启后仍运行 1.003 旧代码（健康检查只探活、探不出版本错位）。
- 16:33 已用提权 wrapper（logs/deploy-1p004-fix-wrapper.ps1）把 AppParameters 改回 `-jar <LOCAL_PATH>`（该文件当时已含 1.004 构建内容）并重启服务，1.004 实际生效。回滚点：`backend/releases/1.004-20261026_162127`。
- **运维警告 1**：发布前必须核对 NSSM AppParameters 指向 `target\expert-system-1.001.jar`（固定名契约）；建议后续给 deploy-backend.ps1 增加版本探针（如 /health 返回构建版本比对）防止同类静默错位。
- **运维警告 2**：执行 database/migration 下含中文的 SQL 禁止经 PowerShell 管道（`$sql | mysql`）传参——PowerShell 5.1 管道默认 ASCII 编码，中文字面量会变 `?`(3F) 写入库（本次已用 database/migration/20261026_fix_b2_operation_log_utf8.sql 修复）。正确姿势：`Start-Process mysql -RedirectStandardInput 文件` 或 cmd 字节级重定向，并带 `--default-character-set=utf8mb4`。
- 前端 1.004 原子发布：16:22:31 完成（dist SHA256 自检通过；回滚点 `frontend/releases/1.004-20261026_162231`）。
- 本次 1.004 合并发布内容：知识星球格式显示优化（随工作区）+ B2 审批转办/加签（或签）+ 用户操作日志改版；双账号（jshz 超管 + 王创审批人）按验收清单逐项实测通过，详见 docs/20261026-handover-B2转办加签与用户操作日志.md。

## 备份与恢复演练体系（2026-10-26 建立）

- **一键备份**：`scripts/backup.ps1`（单脚本三模式 Backup/Verify/Drill）。备份产物 `backup\backup_时间戳\`：expert_system 全量 SQL（--single-transaction --set-gtid-purged=OFF）+ 照片库/知识文档库 zip（照片库_tmp 空则跳过）+ 每产物 .sha256 + manifest.txt；保留最近 5 份，超出移入回收站；日志 `logs\backup.log`。
- **凭据**：优先环境变量 MYSQL_PASSWORD，缺省从本地 start-backend.bat（gitignored）运行时解析，不落盘明文。
- **自动计划任务**：`jshzPMOBackupWeekly` 每周日 02:00 以当前用户运行 Backup 模式（已注册，NextRun 见 `Get-ScheduledTaskInfo`）。
- **校验**：`-Mode Verify` 对产物做 SHA256 复核 + zip 全量读取（CRC）校验。
- **恢复演练**：`-Mode Drill` 自动：校验→重建测试库 expert_system_restore_test（内置护栏仅操作该库）→导入→与生产库对比表数量与每表 COUNT(*) 精确行数→结论自动追加 `docs/恢复演练文档.md`（含手动灾难恢复步骤）。首次演练已通过：17 表 / 871 行完全一致（2026-10-26 19:19，耗时 42.8s）。

## R-1 发布记录
- 2026-10-27 08:32:33 R-1 发布 1.005 成功（jar: expert-system-1.005.jar）

## R-1 发布记录
- 2026-10-27 08:59:29 R-1 发布 1.005 成功（jar: expert-system-1.005.jar）

## 1.005 发布记录（2026-10-27 上午，四功能列车）
- 08:30 后端 1.005 蓝绿发布（我的申请+消息中心+供应商管理）；08:34 前端原子发布；两条迁移重跑（幂等，窗口数据零缺口核实：旧表31行全部并入、record 回填无缺口、23 表）。
- 部署后验收发现并修复两个缺陷（均已二次发布生效）：① 供应商 PUT 为全量语义导致画廊仅传 images 时报"类型不能为空"（951aa4f，改部分更新语义）；② 供应商详情路由复用导致绑定双向跳转数据不刷新（04c4bf1，加 route.params watch）。第二次后端蓝绿 09:0x、第二次前端原子发布均已自检通过。
- 回滚点：backend/releases/1.005-20261027_082855（首发）与 1.005-20261027_090xxx（二次）；frontend/releases/1.005-20261027_083403 与 1.005-20261027_090741。
- 浏览器双账号+张三验收通过（详见 docs/20261026-handover-我的申请通用化改造.md §10）。

## R-1 发布记录
- 2026-10-27 14:55:12 R-1 发布 1.006 成功（jar: expert-system-1.006.jar）

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

## R-1 发布记录
- 2026-10-27 20:40:35 R-1 发布 1.007 成功（jar: expert-system-1.007.jar）

## R-1 发布记录
- 2026-10-29 08:33:53 R-1 发布 1.009 成功（jar: expert-system-1.009.jar）

## R-1 发布记录
- 2026-10-29 17:15:33 R-1 发布 1.009 成功（jar: expert-system-1.009-fix.jar）

## R-1 发布记录
- 2026-10-29 19:21:57 R-1 发布 1.009 成功（jar: expert-system-1.009.jar）

## R-1 发布记录
- 2026-10-30 09:08:48 R-1 发布 1.010 成功（jar: expert-system-1.010.jar）

## R-1 发布记录
- 2026-10-30 12:31:26 R-1 发布 1.010 成功（jar: expert-system-1.010.jar）

## R-1 发布记录
- 2026-10-30 14:23:57 R-1 发布 1.011 成功（jar: expert-system-1.011.jar）
