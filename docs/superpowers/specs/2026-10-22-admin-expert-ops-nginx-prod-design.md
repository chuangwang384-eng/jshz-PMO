---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_73fa29d1cdc511f1ba6e525400de85a5
    ReservedCode1: 4e8wh6rlGoOiU5STaN6UJgr9uA4ibDvx16Q3Uc+BYsScb1Ie1181WtLzfu/Xg7mBxeaiYeULAMJlOs2mJfl5LmYy6X+NMdUO1amD3cSfWrBR9xYcsN9DaIg8j4cy3hEVAGuUcDqIVdtnhnd7nDQGgxchh1STmdzcQZWoQc5axk/7PA2ZCKFeF4adX7o=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_73fa29d1cdc511f1ba6e525400de85a5
    ReservedCode2: 4e8wh6rlGoOiU5STaN6UJgr9uA4ibDvx16Q3Uc+BYsScb1Ie1181WtLzfu/Xg7mBxeaiYeULAMJlOs2mJfl5LmYy6X+NMdUO1amD3cSfWrBR9xYcsN9DaIg8j4cy3hEVAGuUcDqIVdtnhnd7nDQGgxchh1STmdzcQZWoQc5axk/7PA2ZCKFeF4adX7o=
---

# jshz-PMO 改造设计：管理员专家库免审批操作 + Nginx 生产部署收口

- 日期：2026-10-22
- 状态：已批准（用户确认方案 A / 方案 A）
- 关联版本：v1.0.2 之后（拟 v1.1.0）

## 背景

- 当前管理员在专家库主页（`/company/experts`）只能只读查看详情，增删改需跳转 `/experts/manage`；员工在主页可直接新增/编辑（走审批流）。
- 期望：管理员在专家库主页的增改体验与普通账号一致（新增、编辑含照片），但**直写免审批**；并在详情弹窗内直接删除专家。
- 部署现状：前端以 Vite dev 5173 模式运行（NSSM 服务），生产建议 Nginx 托管 dist 并反代 `/api` → 8080。本机内网 IP 为动态（当前 <PRIVATE_IP>）。

## 任务 1：管理员专家库主页免审批增改删（纯前端改造，后端零改动）

### 方案

方案 A（已批准）：后端管理员直写链路已完整（`POST/PUT /api/experts` 支持文本+照片 multipart、`DELETE /api/experts/{id}` 物理删照片，均 ADMIN 权限），仅在专家库主页为管理员开通新增/编辑/删除入口。

### 设计

**前端 `frontend/src/views/ExpertList.vue`（核心改动）**

1. 管理员分支工具栏新增「新增专家」按钮
   - 复用 `ExpertManage.vue` 现有新增弹窗结构（3 个 `el-upload`：照片/身份证正面/身份证背面，`:auto-upload="false"`，accept 白名单）
   - 提交：multipart 直传 `POST /api/experts`（`buildFormData` 思路：FormData 中文字段 + 三类文件），立即生效，不走审批
2. 管理员详情弹窗改为可编辑
   - 与员工弹窗共用弹窗结构，通过模式区分（管理员 = 直写保存，员工 = 提交审批）
   - 文本字段（11 白名单字段）+ 3 张照片（可换图）
   - 保存：multipart 直传 `PUT /api/experts/{id}`；后端 `updateFromMultipart` 对非空新照片执行 `savePhoto` 并物理删除旧文件
3. 详情弹窗内加「删除专家」按钮
   - `ElMessageBox.confirm` 二次确认，文案明确"该专家的照片文件将一并物理删除"
   - 确认后调 `DELETE /api/experts/{id}`，成功刷新列表并关闭弹窗
4. 员工分支（v-else）保持现状不变（新增/编辑走审批流）

**后端**：零改动。验收时冒烟验证：
- 管理员 multipart 新增（含 3 照片）→ 专家出现、照片归档
- 管理员 multipart 修改（含换照片）→ 文本更新、新照片生效、旧照片被删
- 管理员删除 → 专家删除、照片文件物理删除
- 员工新增/编辑仍走审批（回归）

**文档同步（AGENTS.md §0 铁律）**
- `docs/architecture.md`：前端结构（ExpertList 管理员分支能力）、权限模型说明
- 交接文档：管理员主页操作说明
- `docs/deployment.md`：验收检查项补充

## 任务 2：Nginx 生产部署收口

### 方案

方案 A（已批准）：本机安装 Nginx for Windows，80 端口托管 `frontend/dist` + 反代 `/api` → `http://127.0.0.1:8080`；编写发布/回滚脚本与部署手册；HTTPS 仅输出规划（当前不启用）。

### 设计

**部署形态**
- 安装 Nginx for Windows（下载解压至 `<LOCAL_PATH>`），NSSM 注册为服务 `ExpertSystemNginx`（Automatic）开机自启
- `listen 80`，`server_name _`（接受任意 Host，适配动态内网 IP）
- `location /`：`root <LOCAL_PATH>` + SPA fallback `try_files $uri $uri/ /index.html;`
- `location /api/`：`proxy_pass http://127.0.0.1:8080;`，`proxy_set_header Host $host; X-Real-IP $remote_addr; X-Forwarded-For ...;`，Cookie 默认透传
- 同源部署（页面与 API 均 `http://<IP>:80`）后浏览器不触发 CORS；后端 CORS 白名单保留仅供 dev 模式（5173）使用
- 停用 NSSM 服务 `ExpertSystemFrontend`（5173 dev 双入口清理）；保留 `npm run dev` 手动开发能力

**产出物清单**
| 文件 | 说明 |
|---|---|
| `<LOCAL_PATH>` | 站点配置（80 端口、dist 托管、/api 反代） |
| `scripts/deploy-frontend.ps1` | `npm run build` → 备份旧 `dist`（`dist_backup_<时间戳>`）→ 拷贝新 `dist` → `nginx -s reload` |
| `scripts/rollback-frontend.ps1` | 从最新备份恢复 `dist` → `nginx -s reload` |
| `docs/deployment-prod.md` | 部署手册：安装、配置、首次发布/增量发布/回滚流程、验收清单、动态 IP 说明、HTTPS 规划章节 |

**HTTPS 规划章节（仅文档，不启用）**
- OpenSSL 自签证书生成命令（CA + 服务端证书，SAN 含 IP/主机名）
- Nginx `listen 443 ssl` server 块配置
- 客户端信任指引（导入 CA 到"受信任的根证书颁发机构"）
- Cookie 评估：启用 HTTPS 后需评估 `expert_token` 是否加 `Secure` 属性（后端响应头/SameSite 策略）

**发布/回滚流程**
- 首次发布：装 Nginx → 写配置 → `deploy-frontend.ps1` → 验证 `http://<IP>` 登录、`/api/experts`、照片展示
- 增量发布：跑 `deploy-frontend.ps1` 即可
- 回滚：`rollback-frontend.ps1`（后端不动，后端回滚见 `docs/releases/2026-10-22-v1.0.2-发布说明.md` 三线回滚方案）

**动态 IP 注意事项**
- 同事访问地址随 DHCP 变化；手册写明查看当前 IP 的命令（`ipconfig` / 内网工具），建议对主机做 DHCP 地址保留（可选）
- `server_name _` 保证任意 IP/主机名均可达

**文档同步（AGENTS.md §0 铁律）**
- `docs/architecture.md`：部署架构（Nginx 生产模式）
- `docs/deployment.md`：生产启动/访问方式更新、验收项
- 交接文档：生产部署章节

## 影响范围与风险

| 项 | 影响 | 等级 | 缓解 |
|---|---|---|---|
| 安装 Nginx + 注册 NSSM 服务 | 系统级变更 | 中 | 执行前用户确认；可卸载回退 |
| 停用 5173 NSSM 前端服务 | 开发习惯变化 | 低 | 保留 `npm run dev` 手动开发 |
| 管理员新增/编辑直写 | 数据不经审批直接入库 | 低 | 管理员为名单制可信账号，符合业务预期 |
| 删除物理删照片 | 不可逆 | 中 | 前端二次确认；照片库可在备份策略中覆盖 |

## 验收标准

1. 管理员登录 → 专家库主页可新增专家（含照片）→ 列表立即可见，无审批单产生
2. 管理员详情弹窗可编辑文本+照片 → 保存后生效，旧照片被清理
3. 管理员详情弹窗删除专家 → 二次确认后删除，照片文件消失
4. 员工新增/编辑仍走审批流（回归通过）
5. `http://<本机IP>` 可访问生产页面，登录、专家列表、照片、导出均正常；`http://<本机IP>:5173` 不再提供（服务停用）
6. 发布脚本可重复执行，回滚脚本可恢复上一个版本
7. 文档同步完成（architecture/deployment/交接文档/发布说明）

## Git 策略

- 按 AGENTS.md 每项需求独立分支：`feature/admin-expert-ops`、`feature/nginx-prod`
- 合并前检查差异与测试结果；合并后打标签（拟 `v1.1.0`）并写发布说明（含回滚方案）
*（内容由AI生成，仅供参考）*
