# 2026-11-05 合规模板草稿生产发布

状态：PUBLISHED_WITH_WARNING，业务验收待复核；严格不停机门禁失败。

- 版本：1.0.24-compliance-template-20261105-r2；分支codex/personal-dashboard-v11，基线3e8bb1c加工作区改动（尚未提交）。制品哈希及备份见本机证据目录。
- 已授权管理员执行。任务根目录ACL和管理员令牌正常；CIM提供程序拒绝注册，原生Task Scheduler COM注册SYSTEM探针成功。重载改用COM，未放宽ACL/认证/防火墙。
- 平滑重载实测成功，Nginx master39504保持；后端8081影子→正式8080、前端不可变root完成并验证。新root为frontend/releases/1.0.24-compliance-template-20261105-r2。
- 备份及Verify：backup/backup_20261105_102130。回滚JAR保存在.build-compliance-template-production-20261105-r2/previous-expert-system.jar；前端兼容回滚目录为本次release加.rollback，配置位于frontend/release-metadata。回滚仍须使用平滑切换并验证，不删除业务表。
- ProgramData/Tesseract-OCR/tessdata已安装chi_sim及eng，NSSM配置TESSDATA_DIR；新正式服务已启动。无本次业务数据库迁移，无JWT轮换。生产OCR业务写流程未执行。
- 真实隔离数据库和Edge并发生成测试通过；生产登录、auth/me、工作台、搜索、项目只读接口、首页哈希和入口资源检查PASS。
- 连续性：pooled998次零失败，fresh996次2次ECONNRESET（10:22:14.946、10:23:29.142），因此release-state保留FAILED的严格门禁原始结果，不覆盖证据。切流后12秒稳态两路各108次零失败；不足以证明长期稳定或全程不停机。
- 本机证据：.build-compliance-template-production-20261105-r2/，包括发布日志、production-verification.json、continuity.json、steady/continuity.json。后续先排查新建连接重置，再做更长观察与业务人工复核；不要重复同一发布ID。
