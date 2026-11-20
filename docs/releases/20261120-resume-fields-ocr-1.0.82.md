# 专家简历任职字段与图片 OCR 1.0.82 发布与回滚

- 发布时间为 2026-11-20 08:38（北京时间），本地 main 制品提交 f2adcbb。
- 仅后端蓝绿发布。Nginx 最终代理到 8080，页面快照为 1.0.81-budget-gate-20261119。数据库迁移不适用。
- 制品为 builds/1.0.82-build-20261120_083320/expert-system.jar，SHA-256 为 46A771CD43EE66EFA4C09214C9DB12F22CD634DF78A3EAB8E7425DC5E064603B。
- 构建和发布预演通过。正式发布日志 logs/20261120-resume-fields-ocr-1.0.82-deploy.log 记录 RESULT=PASS。直连 /health 和代理 /api/health 独立核验均为 UP、1.0.82、f2adcbb，正式服务为 Running。
- 线上 JAR 与候选 SHA-256 一致，归档回滚 JAR 与 1.0.81 制品 SHA-256 一致。站点首页、专家库和登录入口均返回 200，匿名解析接口返回 401。
- 四份原件在隔离后端通过解析回归。生产页面登录后的上传、预填和选照验收待执行。详见 [任务交接](../handover/tasks/20261120_resume_fields_ocr.md)。

## 回滚

以管理员 PowerShell 在仓库根执行。回滚 JAR SHA-256 为 02B44E3B9C288618A30D0DDE2468D77E6FCBA969734B586D0E6E60FB03DB875F。需按发布规范注入与 NSSM 一致的受控服务凭据。蓝绿回滚后核验直连和代理均为 UP / 1.0.81 / e3ba372。

```powershell
. .\scripts\common-credentials.ps1
$env:MYSQL_PASSWORD = Resolve-DbPassword
$env:JWT_SECRET = Resolve-JwtSecret
.\scripts\deploy-backend.ps1 -Version 1.0.81 -JarPath '<LOCAL_PATH>' -GracefulSwitch -BluePort 8082
```
