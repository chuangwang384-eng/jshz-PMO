# 专家简历四原件修复与上传限额 1.0.57 发布

2026-11-18 10:05 CST 完成生产前后端切换，主责 ZCode（接手 Codex 未完成发布）。工作区提交为 `main` 的 `4c844fe`（Codex 已提交，版本号 1.0.57 已 bump），本次仅执行发布与核验，无代码改动。发布范围：单文件限额统一 30 MiB（31457280 字节）、文件名兜底补姓名、PPTX 负数形状 ID 备用读取、补肩增强。数据库迁移、API 契约、权限均无变更。

## 构建及上线验证

- 候选 `builds/1.0.57-build-20261118_095829/`（builtAt 10:00:23，gitShort=4c844fe，frontend+backend 全量），晚于提交 09:58 且工作区干净，与仓库一致。前端 bundle `index-CjSXmzZ_.js` 含 1.0.57 标记。发布前 `release-backend.ps1 -DryRun` 预检全绿，凭据一致性护栏通过（JWT 未变，用户无需重登）。
- 后端 `release-backend.ps1`（提权包装 `temp/release-1057-wrapper.ps1`）蓝绿发布成功，退出码 0。直连与经 Nginx 的 `/api/health` 均返回 `UP / 1.0.57 / 4c844fe`（2026-11-18 10:05 实测）。`docs/deployment.md` R-1 表已自动记录。
- 前端 `deploy-frontend-graceful.ps1 -Apply`（提权包装 `temp/release-fe-1057-wrapper.ps1`）切换 Nginx root 至 `frontend/releases/1.0.57-resume-limits-20261118`，退出码 0。首页 200，线上 HTML 引用 `assets/index-CjSXmzZ_.js` 且该文件含 1.0.57 标记。
- 匿名 `GET /api/meta/limits` 返回 401（需登录）；登录后限额核验与四原件上传验收待业务执行。

## 回滚

- 后端回滚包 `backend/releases/1.0.57-*/previous-expert-system.jar`（1.0.55），用正式蓝绿脚本按版本参数发布。
- 前端回滚配置 `frontend/release-metadata/1.0.57-resume-limits-20261118.rollback.conf.txt`，平滑重载 Nginx。
- 业务回归步骤见 [四原件交接](../handover/tasks/20261118_resume_four_samples.md)。

## 发布日志

- `logs/deploy-backend.20261118_100313.log`（DryRun）、`logs/deploy-backend.20261118_100338.log`（正式）
