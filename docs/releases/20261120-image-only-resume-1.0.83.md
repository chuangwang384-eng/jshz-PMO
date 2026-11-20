# 图片型 PPT 简历解析 1.0.83 发布与回滚

- 2026-11-20 08:54（北京时间），本地 main 制品提交 7f9d7f4。后端蓝绿发布，数据库迁移不适用。Nginx 最终代理到 8080，前端快照为 1.0.81-budget-gate-20261119。
- 制品 builds/1.0.83-build-20261120_085004/expert-system.jar，SHA-256 为 51DA53B34945A57D20F8A25658B2DE7937A37F7EDACFD2D4B8B2EB1507E79779。
- 发布日志 logs/20261120-image-only-resume-1.0.83-deploy.log 记录 RESULT=PASS。直连和代理独立核验均为 UP / 1.0.83 / 7f9d7f4，ExpertSystemBackend 为 Running。
- 线上 JAR 和构建包 SHA-256 一致，归档回滚 JAR 和 1.0.82 发布包 SHA-256 一致。专家库和登录入口返回 200，匿名简历解析接口返回 401。
- 定向回归 57 项通过，5 项按条件跳过，覆盖武振明 PPTX 和前次四份原件。生产登录后的原件解析和照片预览待验收。详见 [任务交接](../handover/tasks/20261120_image_only_resume_pptx.md)。

## 回滚

以管理员 PowerShell 在仓库根执行以下命令。回滚 JAR SHA-256 为 46A771CD43EE66EFA4C09214C9DB12F22CD634DF78A3EAB8E7425DC5E064603B。回滚后检查直连和代理均为 UP / 1.0.82 / f2adcbb。

```powershell
. .\scripts\common-credentials.ps1
$env:MYSQL_PASSWORD = Resolve-DbPassword
$env:JWT_SECRET = Resolve-JwtSecret
.\scripts\deploy-backend.ps1 -Version 1.0.82 -JarPath '<LOCAL_PATH>' -GracefulSwitch -BluePort 8082
```
