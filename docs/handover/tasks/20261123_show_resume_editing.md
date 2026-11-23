# 串场简历段落编辑与项目内打开

- 主责 Codex，2026-11-23。分支 codex/show-resume-editing，基线 main 127a2ba。
- 从项目详情点击 PPT 制作，在原页面打开全屏向导。关闭回到项目，归档后刷新项目文件。原独立路由仍可打开。
- 第三步保留各环节卡片，移除重复页面清单。第四步保留清单，可编辑当前页。
- 专家简介页的悬浮编辑工具可批量调整全部简历段落的字体和字号。可以删除单个文字框。按 Alt 拖动段落时靠近同行或同列的菱形标记会吸附。使用现有草稿、预览和归档接口。
- deck.slides[].layout 增加可选 hidden，旧数据缺省显示。通过隐藏保留段落及索引，避免其他段落的布局定位错位。无需数据库迁移或权限改动。
- 验收：项目详情 URL 保持不变，检查第三步、打开专家简介页，批量调字、拖动标记并删除一段，保存后重新预览与归档核对。

## 验证

- 前端 Vite 构建和 ProjectStageDeliverableTest 11 项通过。
- 隔离 Edge 使用服务端实际生成的 HTML，验证项目详情原位打开、第三步移除清单、批量字号、同列标记吸附、删除段落和保存内容。
- 本轮完成开发分支修改。未执行生产部署或真实项目写入。

## 合并与发布状态

- 功能提交 24e237b 已快进合入本地 main。
- 1.0.125-show-resume-editing 的生产只读预检通过，见 logs/show-125-preflight.log。当前会话无 Windows 管理员令牌，执行环境策略拦截管理员启动，正式发布未启动。
- 健康接口确认后端仍为 1.0.124，Nginx 前端仍指向 frontend/releases/1.0.124-show-inline-editor。
- 使用服务器管理员 PowerShell 运行标准发布命令：scripts/release-full.cmd -Mode Publish -Version 1.0.125 -Slug show-resume-editing -Yes。
