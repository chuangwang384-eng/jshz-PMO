# 企业合规要求页面优化交接

2026-11-11 · 主责 Codex · 待验收

- 截图对应合规记事本。增加页面标题和独立筛选区，事项按文档卡片展示。标题、内容与来源信息有清楚的视觉层次，窄屏筛选可换行，正文保留换行。
- 文档选择在本次返回的完整分组上过滤，解决选择一个文档后其他选项消失。勾选时突出当前事项，跨文档勾选沿用同一文档推送约束。推送栏可取消选择，空状态可进入规范文档库。
- 改动位于 frontend/src/views/EnterpriseCompliance.vue 和 frontend/src/components/compliance/ComplianceNotebook.vue。无数据库、接口和权限变更。工作区原有改动随任务开始时保留。
- 验收步骤：收藏两份包含已发布事项的文档，依次切换两份文档、清除筛选、查看已隐藏、勾选及取消选择。检查跨文档不可同时勾选、推送正常，以及窄屏没有横向遮挡。浏览器验收和生产部署待执行。
- 如需回退，仅撤回本次两个前端文件的改动并重建前端。

验证记录：npm --prefix frontend run build 在独立构建输出目录通过，git diff --check 通过。日志 logs/enterprise-compliance-ui-build-20261111.log。

## 浏览器验收与生产发布

- 隔离 Edge 使用真实后端、MySQL 与待发布前端，切换两份收藏文档、隐藏和恢复、限制跨文档勾选、取消选择及推送到隔离项目均通过。430px 视口下记事本和工具栏无横向溢出。首轮测试定位到 Element Plus 隐藏输入导致超时，改为点击可见开关后完整复跑通过。日志 logs/compliance-ui-edge-retest-20261111.log。
- 生产前端发布目录为 frontend/releases/1.0.38.7-compliance-ui-20261111。平滑切换退出 0。合规页面入口和页面脚本在线上返回且哈希匹配。经 Nginx 后端健康为 1.0.38.7 UP。仅发布前端。
- 回退配置备份为 frontend/release-metadata/1.0.38.7-compliance-ui-20261111.nginx-before.txt，旧快照为 frontend/releases/1.0.38.7-resume-matte-20261111。

## 浏览器批注调整

- 专家列表卡片和表格的头像区域阻止点击事件传到卡片及行，沿用 PhotoImage 的大图预览。点击头像以外的卡片和行仍进入专家详情。
- 项目详情把合规注意事项放到右栏「当前待办」下方，项目各阶段共用。每条显示标题、正文、来源、状态与有权限的完成或重开操作。
- 合规记事本勾选事项后在右侧悬浮推送面板。宽屏为面板留出空间，窄屏面板靠右上。
- 改动文件为 frontend/src/views/ExpertList.vue、frontend/src/views/project/ProjectDetail.vue 与 frontend/src/components/compliance/ComplianceNotebook.vue。无需数据库、权限和 API 变更。
- 验收依次点击有头像的专家卡片、头像及列表行。进入有已推送事项的项目，切换查看阶段并检查右栏，验证完成及重开。记事本勾选后滚动页面，核对悬浮面板可操作且取消选择后收起。回退仅需撤回本次前端改动。

本次批注调整的前端构建和 git diff --check 已通过。构建日志 logs/browser-comments-ui-build-20261111.log。浏览器验收与生产发布待执行。

### 浏览器批注发布结果

- 隔离 Edge 使用独立 MySQL 和待发布制品完成验收。专家卡片及列表照片只打开大图，卡片正文仍可打开专家详情。项目切换第二阶段后，已推送事项仍在「当前待办」下方并可完成及重开。记事本右侧悬浮、推送与 430px 窄屏通过。首轮阶段用例因夹具只有一条旧快照阶段而中断，调整隔离夹具后复跑退出 0。日志 logs/browser-comments-edge-retest-20261111.log。
- 生产前端平滑切换至 frontend/releases/1.0.38.7-browser-comments-20261111，发布退出 0。三个 SPA 路由 HTTP 200，入口和三组页面脚本、样式哈希与候选一致。经 Nginx 后端健康为 1.0.38.7 UP。仅发布前端，无迁移。
- 上一版为 frontend/releases/1.0.38.7-compliance-ui-20261111，Nginx 配置备份为 frontend/release-metadata/1.0.38.7-browser-comments-20261111.nginx-before.txt。生产交互未写入真实业务数据。

### 模板维护页签样式优化

- 搜索及新增独立为工具栏，解析、候选及已发布分为三个区块。折叠标题展示文档名称及条数。操作支持窄屏换行。
- 改动位于 MaintenanceBoard.vue 和 EnterpriseCompliance.vue，隔离验收脚本定位随布局更新。无接口、数据库或权限改动。
- 浏览器复验先选文档并解析，展开候选和已发布分组，确认计数、编辑保存及撤下清空流程。回退本次前端布局和对应测试定位即可。

### 模板维护浏览器复验与发布

- 首轮隔离 Edge 在 430px 检出横向溢出。调整网格列最小宽度、窄屏按钮换行和折叠内容局部滚动后重新构建。复跑通过 430px 断言和真实 PDF 上传、主动解析、候选计数、编辑发布、清空重解析、永久删除及项目快照保留。日志 logs/compliance-maintenance-ui-edge-retest-20261111.log。
- 生产仅前端平滑切换至 frontend/releases/1.0.38.7-maintenance-ui-20261111，提权脚本退出 0。合规路由 HTTP 200，入口及企业合规 JS/CSS 哈希匹配，经 Nginx 后端健康为 1.0.38.7 UP。无数据库迁移，业务写入验证均在隔离环境完成。
- 回退使用 frontend/releases/1.0.38.7-browser-comments-20261111 和 frontend/release-metadata/1.0.38.7-maintenance-ui-20261111.nginx-before.txt。

- 2026-11-12 模板维护移除事项搜索框、按钮及其页面筛选状态，新增事项移至已发布事项标题栏。现有分组分页加载、解析及新增入口继续使用。无 API 或数据库变更，回退仅需撤回 MaintenanceBoard.vue 对应布局改动。

- 2026-11-12 前端构建和 git diff --check 通过。日志 logs/maintenance-no-search-build-20261112.log。浏览器复验及生产部署未执行。

### 2026-11-12 搜索移除验收与发布

- 真实 Edge 使用待发布前端、隔离 MySQL 及生产同版 1.0.38.8 后端复验。检查搜索框与按钮消失、已发布区新增保存、文档组计数增加、430px 无横向溢出，退出 0。日志 logs/maintenance-no-search-edge-1388-20261112.log。
- 生产前端平滑切换至 frontend/releases/1.0.38.8-maintenance-no-search-20261112。合规路由 HTTP 200，入口与企业合规脚本、样式哈希匹配，后端为 1.0.38.8 UP。无需数据库迁移。
- 回退快照 frontend/releases/1.0.38.8-maintenance-search-20261112，Nginx 备份 frontend/release-metadata/1.0.38.8-maintenance-no-search-20261112.nginx-before.txt。
