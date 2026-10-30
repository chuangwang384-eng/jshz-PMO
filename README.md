---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_a21e74b7d0dd11f19fe7525400de85a5
    ReservedCode1: 4i5O7d8oB1yuecPArFaQjsPiGwGxGVGc1O8+zdLCDRrKY//cyjPza1G0Lp4NFjTqo4yOOCPSCKSP6DcaKd8yYyeeEEj5tj9pMeEhb54yiIQ+huPoeD+VULjxqDZ9D2+dtquisqQTGdQNUUcqpRX6lf5r3NGIEQzrvxT+s6R5WStuPczdRQhHCSgn/Ag=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_a21e74b7d0dd11f19fe7525400de85a5
    ReservedCode2: 4i5O7d8oB1yuecPArFaQjsPiGwGxGVGc1O8+zdLCDRrKY//cyjPza1G0Lp4NFjTqo4yOOCPSCKSP6DcaKd8yYyeeEEj5tj9pMeEhb54yiIQ+huPoeD+VULjxqDZ9D2+dtquisqQTGdQNUUcqpRX6lf5r3NGIEQzrvxT+s6R5WStuPczdRQhHCSgn/Ag=
---

\---  
AIGC:  
    Label: "1"  
    ContentProducer: 001191440300708461136T1XGW3  
    ProduceID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_0539750ccb8011f1acd7525400cd780f  
    ReservedCode1: DJkwL4VF0JDctvGVEOnFJXzKy6Nw6as+QBA2fsdKPBP8Nfe4gzoiP/+WN3p5Yd1pLPLmTi9Q17wsImcEG3M/Rj2rj7Ed/6tVSvg9Su+jQR5klGl0t4LGF8H+vXdcjFiLz9s0jnreiTj3+RRleIeIgGSkNi0nIweXZih9Dz/Yzy02FaSvLs+2pAqNVzI=  
    ContentPropagator: 001191440300708461136T1XGW3  
    PropagateID: d9cc1e9f35e2e4edfa52a2013dbcd9a5_0539750ccb8011f1acd7525400cd780f  
    ReservedCode2: DJkwL4VF0JDctvGVEOnFJXzKy6Nw6as+QBA2fsdKPBP8Nfe4gzoiP/+WN3p5Yd1pLPLmTi9Q17wsImcEG3M/Rj2rj7Ed/6tVSvg9Su+jQR5klGl0t4LGF8H+vXdcjFiLz9s0jnreiTj3+RRleIeIgGSkNi0nIweXZih9Dz/Yzy02FaSvLs+2pAqNVzI=  
\---

# 景帅会展内部管理库系统（Expert Library System）

> 📌 **文档同步铁律（开发者必读）**：任何代码、数据库结构、接口变更必须同步更新 `docs/` 对应文档与交接文档，检查清单见 [AGENTS.md](AGENTS.md) 第 0 节。当前事实基线：数据库 39 张表、后端 Controller 18 个。

## 项目定位

本系统为公司内部**办公管理系统**，用于专家信息的统一登记、维护、检索与调用。系统**依赖公司内网部署**，仅限内网环境访问，不与公网互通。

核心能力：

- 专家信息管理：专家档案（18 字段，含敏感身份信息）的登记、维护、检索与下载
- 数据安全：身份证号/银行卡号查看页脱敏显示；下载留痕审计（download_log 记录下载人/时间/类型/IP）
- 认证体系：账号=姓名登录、JWT（httpOnly cookie）、BCrypt、初始密码强制改密
- 权限管理：基于 RBAC 权限体系（role / permission / role_permission / user_role），sys_user.is_admin 保留为管理员名单兼容标记
- 审批流：员工修改/新增专家走审批链（卡片内编辑提交、新增专家申请），管理员审批通过后应用变更/创建专家并归档照片；审批流可配置开关与审批人角色

## 技术架构

前后端分离架构：

| 模块  | 技术栈                                      | 目录          |
| --- | ---------------------------------------- | ----------- |
| 后端  | Java 17 + Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + Spring Security + jjwt 0.11.5 | `backend/` |
| 前端  | Vue 3.4 + Vite 5 + Element Plus + Pinia + vue-router + axios | `frontend/` |
| 数据库 | MySQL 8.x（expert_system 库 39 张表：expert_info / sys_user / download_log / expert_change_request / approval_config / approval_record / approval_template / role / permission / role_permission / user_role / knowledge_document / knowledge_doc_version / knowledge_favorite / notification / application / operation_log / announcement / announcement_read / project / project_type / project_template / project_phase / project_phase_template_rel / project_contract / project_doc / project_executor / project_expert / project_supplier / supplier / supplier_type / supplier_city / supplier_price_item / supplier_review / supplier_hotel_print_rel / wecom_department / wecom_user_department / wecom_sync_log / wecom_migration_uk_username_backup） | `database/` |
| 照片存储 | 本地磁盘照片库（三类照片 + 静态资源映射） | `照片库/` |
| 文档  | 设计、架构、部署、验收报告 | `docs/`     |

## 目录结构

```
jshz-PMO/
├── README.md              # 项目说明
├── .gitignore             # Git 忽略规则
├── backend/               # 后端服务（Spring Boot 3.2.5，V1.001，含审批流三表服务）
├── frontend/              # 前端应用（Vue 3.4 + Vite 5）
├── database/              # 数据库脚本（init.sql 基础 6 表；migration/ 增量迁移补齐至 39 表）
├── scripts/               # 迁移脚本（SQLite → MySQL）
├── docs/                  # 项目文档（设计/架构/部署/验收）
└── 照片库/                # 照片存储（avatar / idcard_front / idcard_back）
    └── 照片库_tmp/        # 审批流临时照片（审批通过归档至 照片库/，驳回/归档后清理）
```

## 快速开始（概要）

1. 初始化数据库：创建 `expert_system` 库并执行 `database/init.sql`，再依次执行 `database/migration/` 全部增量脚本（合计 39 张表：专家信息 / 用户 / 下载审计 / 审批流 / 审批模板 / RBAC / 知识星球 / 收藏 / 通知 / 专家申请 / 审计操作日志 / 公告 / 项目 / 供应商 / 企微通讯录）
2. 启动后端：配置数据库环境变量后 `cd backend && mvn spring-boot:run`（默认端口 8080）
3. 启动前端：`cd frontend && npm install && npm run dev`（默认端口 5173）

数据库环境变量（以 `backend/src/main/resources/application.yml` 为准）：`MYSQL_HOST / MYSQL_PORT / MYSQL_DB / MYSQL_USER / MYSQL_PASSWORD`。

详细步骤见 [docs/deployment.md](docs/deployment.md)。

## 部署约束

- **内网部署**：所有服务仅监听内网地址，禁止暴露公网端口
- **数据库**：使用内网 MySQL 8 实例（`expert_system` 库），连接信息通过 `MYSQL_*` 环境变量注入，禁止硬编码提交
- **认证**：JWT 存 httpOnly cookie（SameSite=Lax、无 Secure，适配内网 http），登出清 cookie；CORS 仅允许 `<PRIVATE_IP>:5173` / `localhost:5173`
- **网络隔离**：前端通过内网域名或 Nginx 反代访问后端接口  

## 不停机部署方案（发布运维规范）

> **每次代码/配置更新上线，必须按本方案（R-1 后端蓝绿 / R-2 前端原子发布）执行，禁止直接覆盖运行文件、禁止 `nginx -s reload`。** 完整设计与流程图见 [docs/2026-10-24-发布运维体系与UI偏移修复-design.md](docs/2026-10-24-发布运维体系与UI偏移修复-design.md)。

### R-1 后端蓝绿不停机发布

脚本：`scripts/deploy-backend.ps1`，按设计文档 §4.1 实现 11 阶段蓝绿发布：

```
预检 → 影子启动 8081 → 健康+业务探测 → 备份 Nginx conf → 改 proxy_pass → nginx -t
→ Restart-Service ExpertSystemNginx → 蓝验证 → 稳态化 NSSM → 切回 8080 → 清理+归档
```

- 端口约定：`8080`=生产（绿），`8081`=影子（蓝）
- **Nginx 切流一律 `Restart-Service ExpertSystemNginx`**（NSSM 托管约束，禁止 `nginx -s reload`）
- 阶段 9 先停 NSSM 服务再替换 `target/*.jar`（释放进程锁；不先停会替换失败）
- 归档：`backend/releases/`，默认保留 5 个版本（`-KeepVersions` 可调）

```powershell
.\scripts\deploy-backend.ps1 -DryRun                                        # 预演（不改配置/不动服务）
.\scripts\deploy-backend.ps1 -JarPath <LOCAL_PATH>
```

参数：`-JarPath`（新 jar 绝对路径，缺省取 target 下最新）、`-Version`（归档目录名，缺省从 jar 名推导）、`-DryRun`、`-KeepVersions`。

> **凭据策略（免全员重登）**：发布前在会话按 `start-backend.bat` 值注入 `MYSQL_PASSWORD` 与 `JWT_SECRET`；脚本阶段 1 强制校验会话与 NSSM 服务环境一致，不一致中止。`JWT_SECRET` 为长期固定凭据，发布/重启不更换——已登录用户 token 跨发布保持有效；轮换仅限疑似泄露场景且需全员重登（详见 `docs/deployment.md`「凭据长期策略与免重登发布」）。

### R-2 前端原子发布与回滚

- **发布**：`scripts/deploy-frontend.ps1` —— 构建输出到临时目录 `dist-new.<时间戳>`（不直接覆盖 `dist`）→ 校验 `index.html` 与 `assets/` → 备份旧 `dist` 至 `frontend/releases/<版本>-<时间戳>/` → 原子替换（失败自动恢复）→ 自检首页 SHA256。
- **回滚**：`scripts/rollback-frontend.ps1` —— 恢复 `frontend/releases/` 下最新版本（`-Version` 指定版本）；当前损坏 `dist` 存档为 `backup/frontend/dist_broken.<时间戳>`；兼容旧 `backup/frontend/dist_backup_*` 兜底。
- **关键约束**：`dist` 即取即用，发布/回滚后**无需重启 Nginx**（禁止 `nginx -s reload`）。

```powershell
.\scripts\deploy-frontend.ps1 -DryRun                                       # 预演
.\scripts\deploy-frontend.ps1                                               # 原子发布
.\scripts\rollback-frontend.ps1 [-Version 1.0.5]                            # 回滚
```

### 发布流程总纲（每次更新必读）

| 环节 | 动作 | 回滚 |
| --- | --- | --- |
| 后端更新 | `mvn package` 构建 → `deploy-backend.ps1` 蓝绿发布（先 `-DryRun` 预演） | 设计文档 §4.1.4 回滚预案 / `deployment.md` |
| 前端更新 | `deploy-frontend.ps1` 原子发布（自动构建 + 备份 + 替换 + 自检） | `rollback-frontend.ps1` |
| 数据库变更 | 遵循 R-3 向后兼容迁移规范（新增列/状态需兼容旧版本，禁止破坏性变更） | 迁移脚本保留现场 |
| 文档同步 | 按 [AGENTS.md](AGENTS.md) 第 0 节同步 `docs/` 对应文档与交接文档 | — |

R-3 数据库向后兼容迁移规范、R-4 一键发布脚本化、R-5 版本保留与回滚预案详见设计文档 §4.3~§4.5。
*（内容由AI生成，仅供参考）*
