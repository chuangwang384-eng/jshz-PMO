# 发布说明 v1.0.2（jshz-PMO 专家库系统）

> 本文档为 v1.0.2 版本历史快照，其中表数 / Controller 数为发布时状态（6 表 / 8 Controller）；RBAC 升级后当前基线为 **10 表 / 10 Controller**（见 AGENTS.md §0.3）。

- 发布日期：2026-10-22
- 标签：`v1.0.2`
- 基线：V1.001（正式版一期）+ 审批流三需求 + 风险点修复 + 登录跨域修复 + 文档同步
- 系统：景帅会展内部管理系统 · 专家库子系统（内网部署）

---

## 一、新增功能（New Features）

1. **需求3 · 照片审批流**：员工更换/上传照片走审批（照片预上传 `照片库_tmp` 的 `TMP:` 通道），管理员审批通过后 `moveTempPhoto` 归档至正式 `照片库/`，驳回/归档后清理临时文件。
2. **需求4 · 新增专家审批**：非管理员可通过「新增专家」提交申请（11 文本字段 + 3 类照片），新增独立接口 `POST /api/approval/create-request`；管理员审批通过后自动创建专家、归档照片并回填 `expert_id`。`expert_change_request` 新增 `request_type`（0=修改 / 1=新增）。
3. **需求5 · 修改专家审批**：非管理员卡片内编辑 11 白名单字段，关闭检测 `dirty/photoChanged` 引导提交修改申请，管理员审批后应用变更。
4. **审批流配置**：`approval_config`（flow_key=EXPERT_MODIFY、enabled、approver_role=ADMIN）+ 前端审批管理/配置页。

## 二、改进（Improvements）

1. 审批流接口支持**多状态筛选**：`GET /api/approval/requests?statuses=1,2` 一次拉取已通过+已驳回，统一分页；`ApprovalManage.vue` 由多请求改单请求。
2. 前端审批列表新增「申请类型」标签列（requestTypeText：修改/新增专家）。
3. 后端 Controller 达 **8 个**（新增 ApprovalController），Service 达 7 个，Mapper/Entity 各 6 个。
4. 数据库脚本补齐：`init.sql` 现含全部 **6 张表**（v1.0.2 当时状态；RBAC 升级后当时 **10 张表**、知识星球升级后当时 **12 张表**、通知中心升级后当前为 **13 张表**，含 role / permission / role_permission / user_role、knowledge_document / knowledge_doc_version 与 notification）；新增幂等迁移脚本 `database/migration/20261022_add_approval_tables.sql`。
5. 文档同步：`docs/deployment.md`、`docs/architecture.md` 以代码/实际库为事实源统一修正（6 表、8 Controller、启动方式、验收项）；AGENTS.md 新增「§0 文档同步铁律」维护机制。

## 三、缺陷修复（Bug Fixes）

1. **风险点1 · 敏感字段校验未覆盖提交主通道**：`submitRequest` 未调用 `validateSensitiveFields`，非法身份证（17 位）/非法联系方式（12345）/非法银行卡（1234）可绕过校验；已补三通道（修改提交、新增提交、审批应用变更）统一调用，接口实测拦截生效。
2. **风险点2 · 多状态分页异常**：`GET /requests` 单状态查询与多状态统一分页歧义，已通过 `statuses` 参数修复，实测 total 正确。
3. **风险点3 · 前端展开报错**：`ApprovalManage.vue` `handleExpand` 未判空 `expertId`，已补 `expertId != null` 判断。
4. **风险点4 · 前端状态请求碎片化**：`fetchDone` 多次请求改为 `statuses='1,2'` 单请求。
5. 后端 JWT 修复：cookie 读取与 httpOnly cookie 登录态兼容（header 优先）。
6. **登录跨域修复**：`SecurityConfig.setAllowedOrigins` 新增 `http://<PRIVATE_IP>:5173`（内网本机 IP）白名单，修复该 Origin 登录 403 CORS 拒绝；`application.yml` JDBC URL 追加 `&allowPublicKeyRetrieval=true`，修复间歇性 500 `Public Key Retrieval is not allowed`。实测 <PRIVATE_IP> 与 localhost 两个 Origin 登录均 200、ACAO 正确回显、写入 httpOnly + SameSite=Lax cookie，`/api/auth/me` 验证登录态通过。

## 四、破坏性变更（Breaking Changes）

1. `expert_change_request` 表新增列 `request_type TINYINT`（默认 0）；存量环境需执行迁移脚本 `database/migration/20261022_add_approval_tables.sql`（幂等，`CREATE TABLE IF NOT EXISTS` / 补列）。
2. `GET /api/approval/requests` 查询参数改为 `statuses`（逗号分隔多状态），旧的单值 `status` 语义不再保证；前端已同步更新。
3. `start-backend.bat`（含数据库密码）已加入 `.gitignore` **不再入库**；克隆仓库后需按 `start-backend.example.bat` 模板复制并填写真实密码。
4. 照片/备份/日志目录（`照片库/`、`照片库_tmp/`、`backup/`、`备份文件夾/`、`logs/`）已全部 .gitignore，**不入库**。

## 五、回滚方案（Rollback）

### 5.1 代码回滚

- 目标版本：v1.0.2 之前的最后发布版本（V1.001 基线）。
- 命令（在 `<LOCAL_PATH>` 下执行）：

```bash
# 查看当前与历史版本
git tag -l
# 回退到上一版本标签（如有 v1.0.1 等；否则回退到初始主干提交）
git checkout v1.0.1        # 或 git reset --hard <上一版本commit>
# 若需保留当前版本快照再回滚（推荐）
git branch backup-v1.0.2
git reset --hard v1.0.1
```

### 5.2 数据库回滚

- 审批流三表与 `request_type` 列：执行反向 SQL 删除审批相关表/列（**操作前必须全库备份**）：

```sql
-- 备份（命令执行前）：
-- mysqldump -uroot -p111111 expert_system > expert_system_backup_YYYYMMDD.sql
ALTER TABLE expert_change_request DROP COLUMN request_type; -- 仅当需要彻底回退
DROP TABLE IF EXISTS approval_record;
DROP TABLE IF EXISTS expert_change_request;
DROP TABLE IF EXISTS approval_config;
```

- ⚠️ 数据库回滚为**不可逆操作**，必须先执行 `mysqldump` 全库备份，确认备份文件可读后再操作；回滚后重启后端验证 `/health`。

### 5.3 构建产物回滚

- 后端：旧 jar 为 `backend/target/expert-system-1.001.jar`（构建产物不入库，保留本地备份）；回滚时替换为备份 jar 并重启服务。
- 前端：旧 dist 由 `npm run build` 重新生成（源码随 git 回退），或使用本地备份的 `frontend/dist/`。

### 5.4 备份要求（上线前必做）

1. 数据库：`mysqldump -uroot -p111111 expert_system > expert_system_backup_YYYYMMDD.sql`
2. 照片库：整目录复制 `照片库/`、`照片库_tmp/` 到备份介质（照片不入库，回滚需手工还原）。
3. 后端 jar：复制 `backend/target/expert-system-1.001.jar` 为 `expert-system-1.001.jar.bak`。
4. 记录当前后端服务（NSSM）与前端进程状态，回滚后按 `start-backend.example.bat` 模板恢复启动。

---

*本文档由 Git 收口任务生成，版本号与全局目标一致（v1.0.2）。*
