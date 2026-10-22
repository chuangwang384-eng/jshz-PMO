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

> 📌 **文档同步铁律（开发者必读）**：任何代码、数据库结构、接口变更必须同步更新 `docs/` 对应文档与交接文档，检查清单见 [AGENTS.md](AGENTS.md) 第 0 节。当前事实基线：数据库 6 张表、后端 Controller 8 个。

## 项目定位

本系统为公司内部**办公管理系统**，用于专家信息的统一登记、维护、检索与调用。系统**依赖公司内网部署**，仅限内网环境访问，不与公网互通。

核心能力：

- 专家信息管理：专家档案（18 字段，含敏感身份信息）的登记、维护、检索与下载
- 数据安全：身份证号/银行卡号查看页脱敏显示；下载留痕审计（download_log 记录下载人/时间/类型/IP）
- 认证体系：账号=姓名登录、JWT（httpOnly cookie）、BCrypt、初始密码强制改密
- 权限管理：基于 sys_user.is_admin 管理员名单区分管理员与普通员工（无 role 表 RBAC）
- 审批流：员工修改/新增专家走审批链（卡片内编辑提交、新增专家申请），管理员审批通过后应用变更/创建专家并归档照片；审批流可配置开关与审批人角色

## 技术架构

前后端分离架构：

| 模块  | 技术栈                                      | 目录          |
| --- | ---------------------------------------- | ----------- |
| 后端  | Java 17 + Spring Boot 3.2.5 + MyBatis-Plus 3.5.7 + Spring Security + jjwt 0.11.5 | `backend/` |
| 前端  | Vue 3.4 + Vite 5 + Element Plus + Pinia + vue-router + axios | `frontend/` |
| 数据库 | MySQL 8.x（expert_system 库：expert_info / sys_user / download_log / approval_config / expert_change_request / approval_record） | `database/` |
| 照片存储 | 本地磁盘照片库（三类照片 + 静态资源映射） | `照片库/` |
| 文档  | 设计、架构、部署、验收报告 | `docs/`     |

## 目录结构

```
jshz-PMO/
├── README.md              # 项目说明
├── .gitignore             # Git 忽略规则
├── backend/               # 后端服务（Spring Boot 3.2.5，V1.001，含审批流三表服务）
├── frontend/              # 前端应用（Vue 3.4 + Vite 5）
├── database/              # 数据库脚本（init.sql：6 表结构，无 role 表；migration/ 补建迁移）
├── scripts/               # 迁移脚本（SQLite → MySQL）
├── docs/                  # 项目文档（设计/架构/部署/验收）
└── 照片库/                # 照片存储（avatar / idcard_front / idcard_back）
    └── 照片库_tmp/        # 审批流临时照片（审批通过归档至 照片库/，驳回/归档后清理）
```

## 快速开始（概要）

1. 初始化数据库：创建 `expert_system` 库并执行 `database/init.sql`（专家信息 / 用户 / 下载审计 / 审批流三表）
2. 启动后端：配置数据库环境变量后 `cd backend && mvn spring-boot:run`（默认端口 8080）
3. 启动前端：`cd frontend && npm install && npm run dev`（默认端口 5173）

数据库环境变量（以 `backend/src/main/resources/application.yml` 为准）：`MYSQL_HOST / MYSQL_PORT / MYSQL_DB / MYSQL_USER / MYSQL_PASSWORD`。

详细步骤见 [docs/deployment.md](docs/deployment.md)。

## 部署约束

- **内网部署**：所有服务仅监听内网地址，禁止暴露公网端口
- **数据库**：使用内网 MySQL 8 实例（`expert_system` 库），连接信息通过 `MYSQL_*` 环境变量注入，禁止硬编码提交
- **认证**：JWT 存 httpOnly cookie（SameSite=Lax、无 Secure，适配内网 http），登出清 cookie；CORS 仅允许 `<PRIVATE_IP>:5173` / `localhost:5173`
- **网络隔离**：前端通过内网域名或 Nginx 反代访问后端接口  