# 企业合规模板维护 1.0.26 修复发布

2026-11-05 13:59 完成切流，严格连续性门禁状态为 DEPLOYED_CONTINUITY_FAILED。

- 发布 ID 1.0.26-compliance-visual-hotfix-20261105。隔离制品目录 .build-compliance-visual-production-20261105-hotfix。JAR SHA256 58F6481B256A8F439ABAA7A5B9B2371D530DCC1BBEB279B7DED361765023D967，正式 JAR 哈希一致。
- 备份 backup/backup_20261105_135757 已校验。四张现有表读取检查通过，未执行迁移。
- 正式健康接口返回 UP 和 1.0.26。登录态、工作台、搜索、项目列表和首页资源校验通过。截图对应文档的生产编辑流程尚未验收。
- 连续性探针复用连接 934 次零失败。新建连接 938 次，13:59:50 有一次 /api/health read ECONNRESET。
- 后端回滚包为制品目录 previous-expert-system.jar。前端上一入口为 1.0.25.2，兼容回滚配置由前端脚本保存在 frontend/release-metadata。证据见 release-state.json 和 production-verification.json。
