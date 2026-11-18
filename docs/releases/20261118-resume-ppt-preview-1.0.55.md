# 专家简历 PPT 解析与照片预览 1.0.55 发布

2026-11-18 08:56 CST 完成生产前后端切换，主责 Codex。工作区文件提交在 `main` 的 `d4b2581`，版本及构建提交为 `de6b319`。发布范围包括旧版 PPT 与部分 PPTX 的解析兼容、单独及批量上传图片放大，以及纯色底证件照肩部恢复。数据库迁移不适用。

## 构建及上线验证

- `scripts/build-all.ps1` 通过，候选位于 `builds/1.0.55-build-20261118_085138/`。发布前简历解析回归 22 项通过、1 项可选测试跳过，真实样本解析和 Edge 隔离图片预览检查通过。
- 后端使用 `deploy-backend.ps1 -GracefulSwitch` 蓝绿发布，经 8081 影子实例验证后切回 8080。直连及经 Nginx 的健康接口均返回 `UP / 1.0.55 / de6b319`。后端和 Nginx 服务均为 Running。
- 前端使用 `deploy-frontend-graceful.ps1 -Apply` 切换到 `frontend/releases/1.0.55-resume-ppt-preview-20261118/`。Nginx master PID 保持 7696。首页、专家库、登录页均返回 200，HTML 与前端快照完全一致。新入口 `index-BiLWpxuO.js` 和旧入口 `index-h4_-vUbT.js` 均返回 200。
- 匿名 `POST /api/experts/resume/parse` 返回 401。生产登录后的样本上传、选照及保存仍待业务验收。下一步用两份原件分别走单独和批量上传，核对候选数量和大图预览。
- 构建、预演、发布及核验日志均在 `logs/`，文件名前缀为 `20261118-resume-1.0.55-`。

## 回滚

- 后端的 1.0.54 回滚包为 `backend/releases/1.0.55-20261118_085452/previous-expert-system.jar`，SHA-256 与已知 1.0.54 制品一致。使用正式蓝绿脚本按版本参数 `1.0.54` 发布该包。
- 前端回滚配置见 [Nginx 回滚配置](../../frontend/release-metadata/1.0.55-resume-ppt-preview-20261118.rollback.conf.txt)，使用保留新旧哈希资源的回滚快照并平滑重载 Nginx。
- 业务回归步骤见 [PPT 与预览交接](../handover/tasks/20261118_resume_ppt_preview.md)及[肩部恢复交接](../handover/tasks/20261118_resume_portrait_shoulders.md)。
