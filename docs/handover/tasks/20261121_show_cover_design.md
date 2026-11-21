# 串场封面设计与公司共享模板 1.0.122

- 主责 Codex，2026-11-21。基线 main 1e7488b，生产基线 1.0.121。
- 项目 > 串场制作 > PPT 制作 > 模板与封面。选内置主题并可上传 16:9 主视觉，添加自定义字段，拖动或用方向键调整位置。「预览正式封面」使用归档同款渲染。「另存为共享模板」保存名称、主题、背景和字段定义及坐标，项目文字只写入项目草稿。
- 支持启用项目可读权限的成员选用。维护接口复用服务端项目可操作校验，编辑者可新建、更新、下架模板。包含项目审计事件。
- API：GET /api/project/{id}/show/templates（摘要）、GET /{id}/show/templates/{templateId}（版式）、PUT /{id}/show/templates/{templateId}（以 UUID 创建或覆盖）、DELETE /{id}/show/templates/{templateId}（下架）。除首条外路径均相对于 /api/project。PUT 请求包含 name、theme、background、fields。fields 仅存 key、label、x、y；服务端清空 value。
- 专家照片定位弹窗聚合处理状态。中心透明 PNG/WebP 统一叠加过场与专家简介页头像，素材随项目草稿和归档保存。
- 第三步卡片含每位专家会议角色，可在同一环节内拖拽或用按钮调整播放顺序，右上角删除页面。过场页姓名、职称分行。页面编排随草稿保存。
- 沿用项目资料存储目录，模板文件位于 _show_templates。无需数据库迁移。backup.ps1 在目录含模板时增加 show-templates.zip，恢复时从批准的备份解压到同一目录。发布回滚应用不删除模板文件。
- 业务复验：建带字段与主视觉的共享模板，在另一个可读项目选用并填写项目文字；拖动封面并预览；上传透明头像框；在同一环节调整及删除卡片、保存后重新打开并归档。

## 验证与发布

- 前端 Vite 构建通过；隔离 Edge 验证封面字段拖动与坐标、环节内卡片拖拽和删除、透明头像框上传及单页预览参数。备份脚本 PowerShell 语法检查通过。
- 隔离 Maven 定向运行 ProjectStageDeliverableTest、ProjectResumeAvatarTest、ProjectShowTemplateServiceTest，共 14 项通过。模板测试覆盖跨项目读取、剥离项目文字与无操作权限拒绝。
- 功能提交 0538348 已快进合入本地 main。生产发布 1.0.122-show-cover-design，退出码 0。健康检查、Nginx 根目录、首页连续三次 200 和哈希、主 bundle 版本均通过。共享模板接口匿名请求 401。
- 备份 backup/backup_20261121_170626；发布日志 logs/release-full-20261121_170609.log；旧前端 frontend/releases/1.0.121-show-wizard-page-preview；旧后端 backend/releases/1.0.122-20261121_170928/previous-expert-system.jar。
- 生产尚未用真实项目与有权限账号创建共享模板或归档演示。
