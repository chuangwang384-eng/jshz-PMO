# jshz-PMO 实施计划：管理员专家库免审批操作 + Nginx 生产部署收口

- 日期：2026-10-22
- 上游设计：`docs/superpowers/specs/2026-10-22-admin-expert-ops-nginx-prod-design.md`（已批准）
- 目标版本：v1.1.0

## 阶段总览

| 阶段 | 内容 | 主要执行方 | 风险 |
|---|---|---|---|
| A | 任务1：ExpertList.vue 管理员免审批增改删 | file-agent | 低 |
| B | 任务2：Nginx 安装/配置/发布脚本/手册 | computer-agent + file-agent | 中（需确认） |
| C | 验收 + 文档同步 + Git 收口 + 发布说明 | file-agent / computer-agent | 低 |

## 阶段 A：任务1 前端改造（低风险，可先行）

1. 修改 `frontend/src/views/ExpertList.vue`：
   - 管理员分支工具栏新增「新增专家」按钮 → 复用 ExpertManage 新增弹窗结构（3 个 el-upload），multipart 直传 `POST /api/experts`
   - 管理员详情弹窗可编辑：文本 + 3 照片（换图），multipart 直传 `PUT /api/experts/{id}`
   - 管理员详情弹窗内「删除专家」按钮：二次确认（提示照片一并物理删除）→ `DELETE /api/experts/{id}`
   - 员工分支保持不变（审批流）
   - **强制要求**：template 中真实接线（新增按钮/编辑表单/删除按钮/事件绑定必须存在，不能只写 script 函数）；字段名与接口路径逐项核对现有 expert.js（严禁臆造英文字段或新路径）
2. 本地 `npm run build` 验证编译通过（构建产物先行确认，正式发布在阶段 B）
3. 冒烟验收（后端不动，用 dev 5173 或直接验证接口）：
   - 管理员 multipart 新增含照片 → 专家出现、照片归档
   - 管理员 multipart 修改含换照片 → 旧照片删除
   - 管理员删除 → 专家与照片物理删除
   - 员工新增/编辑仍走审批（回归）

## 阶段 B：任务2 Nginx 生产部署（安装前需用户确认）

1. **确认点 1**：安装 Nginx for Windows（解压 `<LOCAL_PATH>` + NSSM 注册 `ExpertSystemNginx` 服务）——用户确认后执行
2. **确认点 2**：停用 NSSM 服务 `ExpertSystemFrontend`（5173）——用户确认后执行
3. 编写 `<LOCAL_PATH>`：
   - `listen 80`；`server_name _`
   - `location /`：root `<LOCAL_PATH>` + SPA fallback
   - `location /api/`：proxy_pass `http://127.0.0.1:8080`，保留 Host、X-Real-IP、X-Forwarded-For
4. 编写脚本：
   - `scripts/deploy-frontend.ps1`：`npm run build` → 备份 `dist`（时间戳目录）→ 拷贝 → `nginx -s reload`
   - `scripts/rollback-frontend.ps1`：从最新备份恢复 → `nginx -s reload`
5. 编写 `docs/deployment-prod.md`：安装步骤、配置清单、发布/回滚流程、验收清单、动态 IP 说明、HTTPS 规划（OpenSSL 自签命令、443 server 块、客户端信任、cookie Secure 评估）
6. 执行首次发布：`deploy-frontend.ps1` → 验证 `http://<本机IP>` 登录、列表、照片、导出

## 阶段 C：验收、文档同步与 Git 收口

1. 全量验收（按设计文档验收标准逐项过）
2. 文档同步（AGENTS.md §0 铁律）：
   - `docs/architecture.md`：前端结构（ExpertList 管理员分支）、权限模型、部署架构（Nginx 生产模式）
   - `docs/deployment.md`：生产访问方式、验收项
   - 交接文档：管理员主页操作说明、生产部署章节
3. Git：
   - 独立分支 `feature/admin-expert-ops`、`feature/nginx-prod`（或经批准合并到 main 前先检查差异）
   - 合并 main → 打标签 `v1.1.0` → 发布说明（新增功能、改进、缺陷修复、破坏性变更、三线回滚方案）

## 验收清单（最终）

- [ ] 管理员主页可新增专家（含照片）免审批，无审批单产生
- [ ] 管理员详情可编辑文本+照片，旧照片清理
- [ ] 管理员详情内可删除专家（二次确认），照片物理删除
- [ ] 员工审批流回归通过
- [ ] `http://<本机IP>` 生产页面登录/列表/照片/导出正常；5173 服务已停
- [ ] 发布/回滚脚本可重复执行且可回退
- [ ] 文档同步完成、git 提交与 v1.1.0 标签就绪
