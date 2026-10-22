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

执行后创建 `expert_system` 库及全部 **6 张表**（`expert_info` / `sys_user` / `download_log` / `approval_config` / `expert_change_request` / `approval_record`），并写入初始管理员数据。

> 说明：`init.sql` 使用 `CREATE TABLE IF NOT EXISTS`（不删旧数据），已包含审批流三表；历史环境若仅有三基础表，可执行 `database/migration/20261022_add_approval_tables.sql` 补建审批流三表（approval_config / expert_change_request / approval_record）。以实际库 **6 张表**为准。

## 3. 后端部署

```bash
cd backend
mvn clean package -DskipTests
```

配置应用（通过环境变量注入，避免敏感信息入库）：

```bash
# Windows（PowerShell/CMD）注入数据库环境变量
set MYSQL_HOST=<内网数据库地址>
set MYSQL_PORT=3306
set MYSQL_DB=expert_system
set MYSQL_USER=<账号>
set MYSQL_PASSWORD=<密码>
```

**启动方式（Windows 唯一合法方式）**：使用项目根目录 `start-backend.bat`（内含 `set MYSQL_PASSWORD=111111` 后 `java -jar target\expert-system-1.001.jar`）。

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

- [ ] `database/init.sql` 在目标库执行成功，`SHOW TABLES` 确认 **6 张表**（含 approval_config / expert_change_request / approval_record）
- [ ] 后端启动无异常，`GET /health` 返回 UP，`/api/expert/list` 可访问
- [ ] 审批流接口可用：`GET /api/approval/config`、`POST /api/approval/requests`（修改申请）、`POST /api/approval/create-request`（新增专家申请）
- [ ] 前端页面可正常加载并展示专家列表
- [ ] 内网安全策略（IP 白名单 / 端口监听）已生效
*（内容由AI生成，仅供参考）*
