# 项目阶段交付物与串场制作 1.0.100

- 任务编号 20261121-project-stage-deliverables，主责 Codex，2026-11-21 北京时间。
- 业务已确认。预算阶段以真实归档报价为推进依据。财务表保存时按项目类型用原模板生成合规立项 Excel，保留最新一份。串场阶段支持上传正式 PPT/PPTX 或从当前日程生成、编辑、保存草稿并归档离线 HTML，图片可替换主 K。
- 开发基线 main d042817，线上前后端原为 1.0.99，后端制品 4eba8fa。开发使用 codex/project-stage-deliverables 独立工作树，保留主工作区已有文档及发布脚本改动。
- 变更由 ProjectService 统一阶段待办及门禁，ProjectFinanceWorkbook 生成 Excel，ProjectShowService 提取日程、验证上传并生成离线网页。ProjectShowEditor 提供页面列表、属性编辑和全屏播放。源文件以现有项目文件权限下载。数据库和配置无需迁移。
- 模板包含各项目类型合并表头和隐藏数据校验字典，已移除原始模板示例输入。历史财务表单仍可读，在会前财务阶段重新保存即可归档 Excel。历史项目已进入后续阶段时继续按所在阶段处理。历史未归档报价需要执行报价确认以生成归档。
- 验收命令使用 Java 17 和 Maven 的 ProjectStageDeliverableTest、ProjectAccessRegressionTest、ProjectBudgetQuoteServiceTest、ProjectResumeAvatarTest、ScheduleParseServiceTest，另用 npm run build 验证前端。上线证据另见发布记录。
- 回滚沿用前后端已有制品，恢复 1.0.99。不涉及数据库回滚，已上传和已归档文件随项目保留。HTML 播放文件以下载交付，建议打开后用箭头键换页，使用正式项目执行一次上传及归档回归。
