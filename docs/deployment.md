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

> 说明：`init.sql` 使用 `CREATE TABLE IF NOT EXISTS`（不删旧数据），已包含审批流三表与 RBAC 四表；历史环境若仅有三基础表，可执行 `database/migration/20261022_add_approval_tables.sql` 补建审批流三表（approval_config / expert_change_request / approval_record）；RBAC 升级环境执行 `database/migration/20261022_add_rbac_tables.sql` 补建 RBAC 四表（role / permission / role_permission / user_role，幂等可重复执行）；知识星球文档环境执行 `database/migration/20261023_add_knowledge_tables.sql` 补建 knowledge_document / knowledge_doc_version 两表并写入接口权限码 knowledge:manage（绑定 ADMIN，幂等可重复执行）；消息通知中心环境执行 `database/migration/20261023_add_notification_table.sql` 补建 notification 表（幂等可重复执行）；审批模板环境执行 `database/migration/20261026_add_approval_template.sql` 补建 approval_template 表并给 expert_change_request 加 template_id 列、写入模板管理接口权限码与 3 个种子模板（幂等可重复执行）；知识星球增强（C1 内容检索/C3 收藏/C4 下载统计）环境执行 `database/migration/20261026_add_knowledge_c1c3c4.sql` 给 knowledge_document 加 content 列、download_log 加 doc_id 列并补建 knowledge_favorite 收藏表（幂等可重复执行）；B2 审批转办/加签 + 用户操作日志环境执行 `database/migration/20261026_add_b2_transfer_operation_log.sql` 给 expert_change_request 加 current_assignee / extra_approver 两列、新建 operation_log 操作日志表、把历史 download_log 迁移为 operation_log（action_type=DOWNLOAD）并写入转办/加签/可转办用户列表接口权限码（均幂等可重复执行）；「我的申请」改版环境执行 `database/migration/20261026_add_application_table.sql` 新建 application 通用申请主表、把 expert_change_request 全量迁移并入（保留原 id，旧表保留作历史备份）、给 approval_record 加 application_id 列并回填、修正存量通知跳转路径（幂等可重复执行，含 sys_user 与业务表 collation 差异的显式 COLLATE 处理）。以实际库 **17 张表**为准。

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
```

**启动方式（Windows 唯一合法方式）**：使用项目根目录 `start-backend.bat`（内含 `set MYSQL_PASSWORD=<真实密码>` 与 `set JWT_SECRET=<真实密钥>` 后 `java -jar target\expert-system-1.001.jar`；真实值只存于该本地未入库文件与 NSSM 服务配置，禁止写入任何入库文件）。

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
