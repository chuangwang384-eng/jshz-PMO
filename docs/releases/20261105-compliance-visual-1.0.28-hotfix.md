# 2026-11-05 合规 OCR 分组/收藏筛选/旧版页签移除生产发布（1.0.28-compliance-visual-hotfix-20261105）

状态：**COMPLETE**（部署、验证、严格连续性门禁全部通过）。

- 发布 ID：1.0.28-compliance-visual-hotfix-20261105；分支 codex/personal-dashboard-v11；代码=提交（OCR 行列分组+收藏筛选+旧版页签移除+执行器 Hotfix 模式），version.txt=1.0.28。
- 执行器：`scripts/release-compliance-visual.ps1 -Apply -Hotfix -HotfixVersion 1.0.28`（Hotfix 模式：跳迁移、校验四表可查询；Codex 本轮扩展，含 DEPLOYED_CONTINUITY_FAILED 状态保留改进）。
- 制品哈希：jar SHA256=AD2B6A0A…29FA3（60,331,783B / 73 依赖 / version.properties=1.0.28）；index SHA256=2C5AC552…C306F。
- 无数据库迁移；四表均在线（extraction/review/workspace/workspace_request 各 0 行）；无 OCR 配置变更。
- 备份：backup/backup_20261105_153153；回滚 JAR=stage/previous-expert-system.jar（1.0.27 fat jar）。
- 后端蓝绿 COM 平滑重载成功；前端 root：1.0.27-hotfix → 1.0.28-compliance-visual-hotfix-20261105。
- 认证只读校验 production-verification.json **PASS**（4 检查+index 哈希一致）；另实测登录→auth/me 200、`GET /api/knowledge/docs?favoritesOnly=true` 200 返回收藏文档（本轮新特性生效）。
- 连续性严格门禁：**pooled 876 次 0 失败、fresh 870 次 0 失败**——首次全程零失败（此前 r2/1.0.25 各有 1~2 次 ECONNRESET，本次未复现）。
- 发布中断记录：首次 UAC 提权被取消（"操作已被用户取消"），无任何生产变更，重试成功。
- 隔离验收（发布前已由 Codex 完成）：V4_VISUAL_EDITOR=true run-compliance-v4-e2e.ps1 退出 0（独立 MySQL 13316/acceptance_v4 + 真实 Edge）；真实基金会 PDF 第 2 页表格分组提取、页内引用持久化、收藏/仅看收藏/关键词组合过滤、取消收藏刷新、员工新版页签无"旧版只读历史"。
- 回滚：previous-expert-system.jar + frontend/release-metadata 回滚 conf；本轮无迁移、无数据回滚需求。
- 已知边界（继承交接）：OCR 比较符号/汉字可能误识别需人工核对；浏览器分页按钮未单独测试；真实数据库验收未执行（隔离库验收通过）。
