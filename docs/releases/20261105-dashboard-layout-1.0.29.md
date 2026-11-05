# 2026-11-05 工作台可调布局生产发布（1.0.29-compliance-visual-hotfix-20261105）

状态：**COMPLETE**（部署、验证、严格连续性门禁全部通过）。

- 功能：工作台布局用户可调——「编辑布局」模式下基于 grid-layout-plus（vue-grid-layout 7.4k star 的 Vue3 官方社区延续版）拖拽/缩放卡片；卡片目录含全部既有数据卡（4 统计+6 面板）与全部可访问页面卡（10 个，按 capabilities/权限自动过滤）；备选卡片区点击添加、卡片 × 移除、恢复默认；布局=可序列化 [{i,x,y,w,h}] 数组，按用户持久化（新表 user_dashboard_layout，user_id PK + layout JSON），下次进入即保存的布局。权限口径：待我审批类卡片按 approval:audit、页面卡按 overview capabilities 过滤，与后端一致。
- 后端：DashboardController 新增 GET/PUT /api/dashboard/layout（登录即可、仅作用本人；PUT 校验 JSON 合法性与 20KB 上限）；迁移 `20261105_user_dashboard_layout.sql`（发布前经提权脚本执行并验证：3 列 0 行）。
- 发布：执行器 Hotfix 模式（无合规迁移）——备份 backup_20261105_161956 附近（见 stage release-state.json）、蓝绿 COM 平滑重载、前端 root→releases/1.0.29-compliance-visual-hotfix-20261105。
- 制品哈希：jar SHA256=DE978151…AD79A（1.0.29，73 依赖）；index SHA256=17425B29…36D42。
- 回滚：previous-expert-system.jar（1.0.28）+ 前端 release-metadata 回滚 conf；user_dashboard_layout 表保留无害（旧代码不读）。
- 验证：production-verification.json PASS；连续性 pooled 1034 / fresh 1032，**双双 0 失败**；health=1.0.29 UP。布局 API 实测：未保存→空；PUT 保存→GET 返回一致；非法 JSON→body code=400（"布局不是合法的 JSON"）；测试数据已清理（user_dashboard_layout 清空）。
- 遗留：编辑模式为 12 列栅格（普通视图按布局顺序+列宽响应式重排，与编辑视图绝对定位存在合理差异）；卡片高度在普通视图为自适应。
