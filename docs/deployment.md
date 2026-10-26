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

> 说明：`init.sql` 使用 `CREATE TABLE IF NOT EXISTS`（不删旧数据），已包含审批流三表与 RBAC 四表；历史环境若仅有三基础表，可执行 `database/migration/20261022_add_approval_tables.sql` 补建审批流三表（approval_config / expert_change_request / approval_record）；RBAC 升级环境执行 `database/migration/20261022_add_rbac_tables.sql` 补建 RBAC 四表（role / permission / role_permission / user_role，幂等可重复执行）；知识星球文档环境执行 `database/migration/20261023_add_knowledge_tables.sql` 补建 knowledge_document / knowledge_doc_version 两表并写入接口权限码 knowledge:manage（绑定 ADMIN，幂等可重复执行）；消息通知中心环境执行 `database/migration/20261023_add_notification_table.sql` 补建 notification 表（幂等可重复执行）。以实际库 **13 张表**为准。

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
- **JWT 密钥**：`application.yml` 改为 `${JWT_SECRET:}` 注入（未注入时后端启动失败属预期 fail-fast）。当前运行中的 jar 仍使用旧内置密钥；**新密钥将在下次后端发布时生效**，届时全体用户需重新登录。
- **NSSM 服务**：`ExpertSystemBackend` 的 `AppEnvironmentExtra` 由 `MYSQL_PASSWORD` 单项升级为 `MYSQL_PASSWORD` + `JWT_SECRET` 两项，由 `scripts/rotate-backend-credentials.ps1`（管理员运行，凭据从 start-backend.bat 读取）写入。
- **后续动作**：
  1. 管理员执行 `scripts/rotate-backend-credentials.ps1`（写服务环境变量 + 重启后端 + 冒烟）；
  2. 系统验证正常后，再以 `-DiscardOldPassword` 参数执行一次，弃用旧密码、结束过渡期；
  3. 下次后端发布前，在会话中注入 `MYSQL_PASSWORD` 与 `JWT_SECRET`（deploy-backend.ps1 预检会校验这两个变量）。
*（内容由AI生成，仅供参考）*

