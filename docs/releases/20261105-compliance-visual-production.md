# 2026-11-05 合规模板可视化维护生产发布（1.0.25-compliance-visual-20261105）

状态：**DEPLOYED_CONTINUITY_FAILED**（部署与验证全部完成；严格连续性门禁记录 1 次 ECONNRESET，不宣称全程严格不停机）。

- 发布 ID：1.0.25-compliance-visual-20261105；分支 codex/personal-dashboard-v11；代码基线 3e8bb1c + 提交 57f1afa（可视化维护特性）+ 1.0.25 版本号提交。
- 执行器：`scripts/release-compliance-visual.ps1`（本机首次入库）；stage：`.build-compliance-visual-production-20261105/`（隔离构建，backend jar 60,321,695B / 73 依赖 / version.properties=1.0.25）。
- 制品哈希：jar SHA256=D55A4179595666580B62B0AE8C0D2973ADA6DCD094AF5208BC00D22A8D47A90A；index SHA256=C404C3FBF144A9CA13B9F59FC52A471E55FE90934BDFD5342D791B37FA98C74C。
- 迁移前元数据门禁：expert_system 中四张新表（workspace/workspace_request/extraction/review）确认均不存在（11-03 的 node/option/version 为既有 V4 表，非冲突）；无原型残留结构。
- 备份：backup/backup_20261105_120324（Backup+Verify 通过）。
- 迁移：20261105_compliance_template_workspace.sql 执行成功；迁移后四表均存在且 0 行；既有 V4 表数据未动（node 40 / option 2 / version 5）。
- 后端蓝绿：COM 平滑重载成功，NSSM 运行新 jar（health=1.0.25 UP）；回滚 JAR=stage/previous-expert-system.jar（1.0.24，SHA256=6889DB42…E883，与运行中版本哈希一致）。
- 前端：root 切至 frontend/releases/1.0.25-compliance-visual-20261105，index 哈希与制品一致；兼容回滚目录与配置在 frontend/release-metadata。
- 认证只读校验：production-verification.json **PASS**（auth/me / dashboard.overview / search.global / project 列表；index/assets 哈希校验）；另实测登录→auth/me→project 200。
- 连续性（严格门禁）：pooled 872 次 **0 失败**；fresh 872 次 **1 次 read ECONNRESET**（12:04:06 /api/health，处于平滑重载排空窗口）。按发布评估要求如实记 FAILED，不覆盖证据；切流后业务接口复测全部正常。
- 回滚路径：previous-expert-system.jar（兼容旧库——四表保留不删，旧代码按元数据护栏走旧路径）+ 前端 root 回退（release-metadata/1.0.25-compliance-visual-20261105.rollback.conf.txt）。回滚须同样走平滑切换并验证。
- 遗留边界（继承评估文档）：提取为行候选须人工筛选核对；大文件同步 OCR 与故障恢复压测未做；Office 锚点页码有降级边界；业务负责人易用性复核待执行。✅已验收关闭（2026-11-19 Owner 批量授权）
