# jshz-PMO 生产部署手册（Nginx 80 + 反向代理 8080）

> 适用环境：Windows 10（本机部署，动态 IP）
> 架构：浏览器 -> Nginx(:80, 托管 `frontend/dist`) -> 反代 `/api/` -> 后端 `127.0.0.1:8080`（NSSM 服务 `ExpertSystemBackend`）
> 配套文件：`scripts/nginx-jshz-pmo.conf`、`scripts/deploy-frontend.ps1`、`scripts/rollback-frontend.ps1`

---

## 1. 环境要求

| 组件 | 要求 | 说明 |
|---|---|---|
| Windows | 10 / Server 2016+ | 本机部署 |
| Nginx for Windows | `<LOCAL_PATH>` | 官方 nginx-1.24+ windows 版即可；未安装 |
| Node.js | 18+（含 npm） | 前端构建 `npm run build` 用 |
| 后端服务 | NSSM 服务 `ExpertSystemBackend` | 已托管 8080，无需改动 |
| 前端服务 | NSSM 服务 `ExpertSystemFrontend` | 阶段 B 完成后**停用**（dev 5173 不再需要） |

当前基线（2026-10-22 核实）：
- 后端：`ExpertSystemBackend`（NSSM）监听 8080，服务正常。
- 前端：`ExpertSystemFrontend`（NSSM）以 `cmd /c npm.cmd run dev` 跑 dev 5173。
- Nginx：**尚未安装**（全盘无 `nginx.exe`，服务列表无 nginx）。
- 前端代理：`vite.config.js` 中 `/api` -> `127.0.0.1:8080`（仅开发模式生效，生产由 Nginx 反代）。

---

## 2. 安装步骤（一次性）

### 2.1 安装并注册 Nginx

1. 下载 Nginx for Windows，解压到 `<LOCAL_PATH>`（保证 `<LOCAL_PATH>` 存在）。
2. 放置站点配置：
   - 将 `scripts/nginx-jshz-pmo.conf` 内容合入 `<LOCAL_PATH>` 的 `http { }` 块内；
   - 或拷贝到 `<LOCAL_PATH>`，并确认 `nginx.conf` 含 `include conf.d/*.conf;`。
3. 校验配置：
   ```powershell
   <LOCAL_PATH> -t
   ```
4. 注册为 Windows 服务（NSSM，方便开机自启）：
   ```powershell
   <LOCAL_PATH> install ExpertSystemNginx "<LOCAL_PATH>"
   <LOCAL_PATH> set ExpertSystemNginx AppParameters ""
   <LOCAL_PATH> set ExpertSystemNginx Start SERVICE_AUTO_START
   <LOCAL_PATH> start ExpertSystemNginx
   ```
   > 若 NSSM 未随 Nginx 提供，可从 nssm.cc 下载；也可跳过服务化，用命令行 `<LOCAL_PATH>` 手动启动。

### 2.2 停用旧前端 dev 服务（5173）

生产模式由 Nginx 托管静态产物，dev 服务不再需要：
```powershell
<LOCAL_PATH> stop ExpertSystemFrontend
<LOCAL_PATH> remove ExpertSystemFrontend confirm
```
> 停用前请先完成一次"首次发布"，并确认 80 端口访问正常。

### 2.3 后端服务确认

```powershell
sc.exe query ExpertSystemBackend   # 应显示 RUNNING
# 或
<LOCAL_PATH> status ExpertSystemBackend
```

---

## 3. 首次发布流程

1. 确认前置：Node 可用、后端服务运行、Nginx 已安装并加载站点配置。
2. 执行发布脚本：
   ```powershell
   powershell -ExecutionPolicy Bypass -File <LOCAL_PATH>
   ```
3. 脚本自动完成：备份旧 `dist` -> `npm run build` -> 校验 `dist/index.html` -> `nginx -s reload` -> 输出摘要。
4. 浏览器访问 `http://<本机IP>/` 验证（本机可用 `http://localhost/`）。

## 4. 增量发布流程（日常改版）

与首次发布完全一致，直接再次执行 `deploy-frontend.ps1`：
- 每次发布前自动把当前 `dist` 备份到 `<LOCAL_PATH><yyyyMMdd_HHmmss>`；
- Nginx 已加载长缓存策略，`index.html` 不缓存，改版后刷新即可生效；
- 若仅改动后端（8080），前端无需发布。

## 5. 回滚流程

前端发布后发现问题，回滚到最近一次发布前的版本：
```powershell
powershell -ExecutionPolicy Bypass -File <LOCAL_PATH>
```
脚本行为：
1. 取 `<LOCAL_PATH>` 下**最新** `dist_backup_*` 目录；
2. 当前异常 `dist` 先存档为 `dist_broken_<时间戳>`（保留现场便于排查）；
3. 将备份内容恢复为 `frontend\dist`；
4. `nginx -s reload` 并输出摘要。

> 若需要回滚到更早版本：先手动把目标 `dist_backup_<时间戳>` 内容覆盖到 `frontend\dist`，再执行 `<LOCAL_PATH> -s reload`。

---

## 6. 验收清单

| # | 验收项 | 方法 | 预期 |
|---|---|---|---|
| 1 | 站点可访问 | 浏览器访问 `http://<本机IP>/` | 登录页正常，控制台无报错 |
| 2 | 登录 | 员工/管理员账号登录 | 成功进入主页，`expert_token` cookie 写入 |
| 3 | 专家列表 | 主页查看专家列表 | 数据来自 8080（经 Nginx 反代），刷新前端路由不 404 |
| 4 | 照片 | 查看/上传专家照片 | 图片可显示；上传不超 20m 限制 |
| 5 | 导出 | 执行导出操作（ZIP/报表） | 长耗时请求不中断（反代已放宽 read 超时） |
| 6 | 管理员增改删 | 管理员免审批新增/编辑（含照片）/详情弹窗删除 | 与阶段 A 验收一致，均走 `/api/experts/**` |
| 7 | 静态资源缓存 | DevTools 观察 `assets/*-hash.*` 响应头 | `Cache-Control: public, max-age=2592000, immutable` |

---

## 7. 动态 IP 说明

- 本机 IP 变化时，**无需**修改 Nginx 配置：`server_name _` 通配任何 Host，`listen 80` 监听本机所有网卡。
- 获取当前地址：
  ```powershell
  ipconfig
  ```
  取 IPv4 地址（如 `<PRIVATE_IP>`），局域网内其他机器访问 `http://<PRIVATE_IP>/`。
- 建议在路由器为服务器做 **DHCP 地址保留**（按 MAC 固定 IP），避免重启后地址漂移导致同事访问失效。
- 若需要固定对外地址且不想用 IP，可在路由器设置端口映射/域名解析；Nginx 侧只需把 `server_name _` 改为实际域名（可选）。

---

## 8. HTTPS 规划

> 现状：HTTP 80 明文传输；生产使用建议尽快启用 HTTPS，至少对登录/上传场景加密。

### 8.1 自签证书生成（OpenSSL，含 SAN）

Windows 可复用 Git 自带 OpenSSL（`<LOCAL_PATH> Files\Git\usr\bin\openssl.exe`）或自行安装。先生成目录并生成证书：

```powershell
New-Item -ItemType Directory -Path <LOCAL_PATH> -Force | Out-Null

& '<LOCAL_PATH> Files\Git\usr\bin\openssl.exe' req -x509 -newkey rsa:2048 -sha256 -days 3650 -nodes `
  -keyout <LOCAL_PATH> -out <LOCAL_PATH> `
  -subj "/CN=jshz-pmo" `
  -addext "subjectAltName=IP:<PRIVATE_IP>,DNS:localhost,DNS:jshz-pmo"
```

> `-addext` 中的 SAN 必须包含实际访问 IP/域名；IP 变化后需重新生成或添加新 IP（OpenSSL 1.1.1+ 支持 `-addext`，Git 自带版本满足）。

### 8.2 Nginx 443 server 块（启用时）

将 `scripts/nginx-jshz-pmo.conf` 中 HTTPS 预留块取消注释，或按以下完整配置替换：

```nginx
server {
    listen 443 ssl;
    server_name _;

    ssl_certificate     <LOCAL_PATH>
    ssl_certificate_key <LOCAL_PATH>
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    charset utf-8;
    root <LOCAL_PATH>
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_connect_timeout 60s;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
        client_max_body_size 20m;
    }

    gzip on;
    gzip_min_length 1k;
    gzip_comp_level 6;
    gzip_types text/plain text/css application/json application/javascript text/javascript application/xml image/svg+xml;
    gzip_vary on;
}

# HTTP 80 跳转 HTTPS（可选，启用后更安全）
server {
    listen 80;
    server_name _;
    return 301 https://$host$request_uri;
}
```

启用后执行：
```powershell
<LOCAL_PATH> -t
<LOCAL_PATH> -s reload
```

### 8.3 客户端导入自签 CA 指引（浏览器信任）

1. 把 `<LOCAL_PATH>` 拷贝到各客户端电脑；
2. 双击 `server.crt` -> 「安装证书」->「本地计算机」->「将所有证书都放入下列存储」-> 浏览选择「**受信任的根证书颁发机构**」-> 完成；
3. 访问 `https://<本机IP>/`，浏览器不再告警；
4. 手机/其他设备首次访问需同样导入，或选择「高级 -> 继续前往」临时放行。

### 8.4 expert_token cookie Secure 评估

- 现状：后端签发 `expert_token`（HttpOnly cookie，SameSite=Lax，**未设置 Secure**），HTTP 明文传输时 cookie 明文可见，存在被截获风险。
- 启用 HTTPS 后评估建议：
  1. **后端增加 Secure 标记**：在 JWT 认证过滤器/`SecurityConfig` 中构造 `ResponseCookie` 时追加 `.secure(true)`，并保持 `httpOnly(true)`、`sameSite("Lax")`，使 cookie 仅经 HTTPS 传输；
  2. **关闭 HTTP 明文访问**：启用 8.2 的 80 -> 443 跳转，避免用户走明文通道；
  3. **验证**：HTTPS 下登录后，DevTools Application/Cookies 中 `expert_token` 的 `Secure` 列应为勾选状态，且 `http://` 访问不再能读取该 cookie；
  4. 后端改完需重启 `ExpertSystemBackend` 服务，并在发布手册中记录 cookie 策略变更。

> 说明：若短期无法启用 HTTPS，至少应限制本机/内网使用范围，避免在不可信网络使用该系统。

---

## 9. 常见问题（FAQ）

| 现象 | 排查 |
|---|---|
| `nginx.exe -t` 报错 | 检查 `<LOCAL_PATH>` 存在；检查是否重复定义 `server`/`listen`；检查引号与分号 |
| 访问 80 端口无响应 | 确认 Nginx 已启动（任务管理器/`netstat -ano | findstr :80`）；确认防火墙放行 80 |
| 页面能开但 `/api` 报 502 | 后端 `ExpertSystemBackend` 未运行；`proxy_pass` 目标是否 8080 |
| 刷新子路由 404 | `location /` 缺少 `try_files $uri $uri/ /index.html;` |
| 上传大文件失败 | `client_max_body_size` 是否足够（模板 20m）；后端 multipart 限制 |
| 发布后页面还是旧的 | `index.html` 是否被缓存（应 no-cache）；`nginx -s reload` 后强刷（Ctrl+F5） |

---

## 10. 相关文件清单

| 文件 | 用途 |
|---|---|
| `scripts/nginx-jshz-pmo.conf` | Nginx 站点配置模板（80 + /api 反代 + HTTPS 预留） |
| `scripts/deploy-frontend.ps1` | 发布脚本（备份 + build + reload） |
| `scripts/rollback-frontend.ps1` | 回滚脚本（恢复最近备份 + reload） |
| `docs/deployment-prod.md` | 本手册 |
| `docs/superpowers/plans/2026-10-22-admin-expert-ops-nginx-prod-plan.md` | 阶段计划文档 |
