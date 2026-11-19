# 项目简历选照与参会专家操作 1.0.72

2026-11-19 09:51 CST 完成生产切换，主责 Codex。两轮功能提交 `53c8cae` 和 `3cb9026` 均已进入本地 `main`。运行构建为 `3674562`，制品目录 `builds/1.0.72-build-20261119_094706/`。

- 项目一键上传复用专家库批量解析弹窗，保留最多 60 份，可按简历正文匹配待建档专家并选择证件照，提交后保存照片并回绑。参会候选支持单条保存。「还原为解析结果」已移除。「清空已保存专家」需二次确认，范围为当前项目已确认参会记录。
- JDK 17 隔离回归执行 `-Dtest=ProjectResumeAvatarTest,ScheduleSpeakerWorkflowTest,ScheduleSpeakerDiffTest,ScheduleParseServiceTest,ProjectAccessRegressionTest`，36 项通过。前后端构建、版本校验及发布预演通过。生产备份 `backup/project-speaker-1.0.72-20261119/backup_20261119_094708/` 的数据库导出、照片库和知识文档库通过 SHA-256 与内容校验。无数据库迁移。
- 8082 影子实例检查通过，正式流量切回 8080。Nginx 平滑切换前端至 `frontend/releases/1.0.72-project-speaker-20261119/`，主进程 PID 保持 7696。
- 后端直连及代理均返回 `UP / 1.0.72 / 3674562`。首页、项目管理、专家库和登录页与新快照一致。项目详情和证件照弹窗的线上 JS 与构建 SHA-256 一致，两项服务均为 Running。建档、日程差异与清空接口匿名请求返回 401。日志前缀为 `20261119-project-speaker-1.0.72-`。

后端回滚包 `backend/releases/1.0.72-20261119_094959/previous-expert-system.jar` 已与 1.0.71 制品核对。前端使用 [回滚配置](../../frontend/release-metadata/1.0.72-project-speaker-20261119.rollback.conf.txt) 平滑重载并复核健康和页面。已确认清空或已建档的数据需按操作日志和备份另行处理。

登录后的真实简历选照、单条保存与清空待业务验收。本次发布未操作项目 57 的参会名单。见 [简历验收](../handover/tasks/20261119_project_batch_resume_portrait.md) 和 [参会专家操作验收](../handover/tasks/20261119_project_speaker_row_actions.md)。
