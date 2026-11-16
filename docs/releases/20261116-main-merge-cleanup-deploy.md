# 发布记录：main 分支合并 + 冗余清理/DDL 收口上线（2026-11-16）

## 1. 概要

| 项 | 内容 |
| --- | --- |
| 发布时间 | 2026-11-16 16:15（后端）/ 16:18（前端） |
| 分支 | `main`（合并自 `codex/personal-dashboard-v11`） |
| 合并提交 | `c962ea2`（merge-20261116） |
| 发布提交 | `fc173ce`（含发布回滚元数据入库）；其后 `0962de6` 起仅有文档/元数据提交，代码树未变 |
| 版本 | 1.0.45（版本号不变；区分依据为 `/health.gitShort`） |
| 后端制品 | `builds/1.0.45-build-20261116_160759/expert-system.jar` |
| 前端发布点 | `frontend/releases/1.0.45-cleanup-20261116` |
| 发布执行 | `scripts/deploy-backend.ps1 -GracefulSwitch` + `scripts/deploy-frontend-graceful.ps1 -Apply`（管理员权限） |
| 发布日志 | `logs/deploy-20261116-backend.log`、`logs/deploy-20261116-frontend.log` |

## 2. 本次变更内容

见 `docs/handover/tasks/20261116_redundancy_cleanup.md`，摘要：

- 前端：删除 4 个零引用组件、34 处无调用 `export`、7 个孤儿全局样式选择器；`ComplianceTemplateEditor` 模板预览改用 `getTemplateDetail`（URL 不变，唯一行为接触点）。
- 数据库：`init.sql` 基线补齐 4 表至 85 张业务表；新增 `scripts/check-schema-parity.ps1` 守卫。**本次未执行任何 DDL/DML**。
- 文档：表数/计数事实统一；3 篇历史设计文档加删除提示；删除过期 `frontend/version.txt`。
- 后端 Java 代码：**零改动**（本次发布为同一源码重新构建，行为与上一版一致）。

## 3. 发布过程关键节点（后端 11 阶段）

| 阶段 | 结果 |
| --- | --- |
| 1/11 预检（新 jar + 绿服务健康 + 凭据一致性） | OK |
| 2~3/11 影子启动 8081 + 健康/业务探测 | OK |
| 4~7/11 备份 Nginx 配置 → 切流 8081 → `nginx -t` → GracefulSwitch | OK（master PID 不变） |
| 8/11 经 Nginx 蓝验证 | OK |
| 9/11 稳态化：停 NSSM → 替换 jar → 启 NSSM → 8080 健康 | OK（旧进程已退出、制品解锁） |
| 10/11 切回 8080 正式端口 | OK |
| 11/11 清理 + 归档（保留 5 个归档版本，轮转 NSSM 日志） | OK，清理 1 个旧版本目录 |

前端：创建不可变发布目录 → 合并旧 root 的 hash 资产（防旧客户端 404）→ 写 Nginx 配置备份与 `.rollback` 目录 → 切 root → graceful reload → **服务端 index.html SHA256 与新发布目录一致**校验通过。

## 4. 发布后验证（经 Nginx，端口 80）

| 验证项 | 结果 |
| --- | --- |
| `GET /` | 200，引用主包 `/assets/index-uND0exbZ.js` |
| 新主包 / 样式（1143 KB / CSS） | 200 / 200 |
| `GET /api/health` | 200，`status=UP`，`version=1.0.45`，`gitShort=fc173ce` |
| `POST /api/auth/login`（管理员） | 200，`isAdmin=true` |
| 13 个核心接口（me/专家/项目/看板/知识/供应商/客户/公告/消息/审批/角色/审计/合规/企微） | 全部 200 |
| 匿名访问 `/api/experts`、`/api/roles` | 401（鉴权链正常） |
| 旧发布包 hash 资产（`index-0oR6vjTL.js`） | 200（平滑过渡，旧标签页不白屏） |
| NSSM 服务 `ExpertSystemBackend` | Running / Automatic |

失败项合计 **0**。

## 5. 回滚方案

| 层 | 回滚点 | 步骤 |
| --- | --- | --- |
| 前端 | `frontend/releases/1.0.45-ai-chat-20261116`（旧 root 保留）；配置备份 `frontend/release-metadata/1.0.45-cleanup-20261116.nginx-before.txt`，回滚配置 `1.0.45-cleanup-20261116.rollback.conf.txt` | 将 `<LOCAL_PATH>` 的 `root` 改回旧目录 → `scripts/invoke-nginx-graceful.ps1`（需管理员）→ 验证 `GET /` 200 且 hash 匹配 |
| 后端 | `backend/releases/1.0.45-20261116_161555`（上一版 jar，buildTime 14:33，gitShort bf275ca） | `scripts/deploy-backend.ps1 -JarPath <旧 jar> -Version 1.0.45 -GracefulSwitch`（需管理员，同样走蓝绿） |
| 代码 | 提交 `c962ea2` / `fc173ce` | `git revert` 或回到 `287257b`；注意本次无数据库结构变更，回滚无需数据补偿 |

## 6. 遗留与风险

1. **未推送到 origin**：本次仅本地 `main` 合并与发布。`git fetch` 实测 `origin/main=56c31ed` 是本地 `main` 的**祖先**（本地领先 42、落后 0），推送为快进安全操作，但未获推送授权，故未执行；授权后 `git push origin main` 即可。
2. `schema_migration` 台账仍为「待执行 3 / 漂移 18」，与本次发布无关，需批准后单独处理。
3. 版本号未递增（1.0.45 存在两个构建）：以 `/health.gitShort` 区分，`fc173ce` 为本次。若后续需要可读版本，可在下次发布时升 1.0.46。
4. 建议业务侧点检：企业合规模板预览弹窗（本次唯一行为接触点）、项目配置向导、知识星球文档预览。
