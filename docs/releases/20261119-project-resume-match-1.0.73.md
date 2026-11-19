# 项目批量简历科室与匹配 1.0.73

2026-11-19 10:09 CST 完成生产切换，主责 Codex。功能提交 `6afcf5a`，运行构建提交 `4d51af1`，制品目录 `builds/1.0.73-build-20261119_100418/`。

- 项目「一键上传」新增科室，沿用专家库默认解析和文件名补姓名。参会专家可以搜索选择，姓名不一致时确认后按所选姓名保存。按姓名和单位查重，同名同单位可确认关联，原档案资料及证件照保留。复用现有接口、项目权限和存储，无数据库迁移。
- JDK 17 隔离 Maven 定向回归执行 `-Dtest=ProjectResumeAvatarTest,ScheduleSpeakerWorkflowTest,ScheduleSpeakerDiffTest,ScheduleParseServiceTest,ProjectAccessRegressionTest`，修正测试的 MyBatis 元数据初始化后，37 项通过。Node 验证默认解析、手选保留照片、科室保存参数及关联时不提交照片。前后端构建与发布预演通过。
- `backup/project-resume-match-1.0.73-20261119/backup_20261119_100259/` 中数据库、照片库及知识文档库均通过 SHA-256 和内容校验。8082 影子实例探针通过，生产已平滑切回 8080。前端快照为 `frontend/releases/1.0.73-project-resume-match-20261119/`，Nginx 主进程仍为 7696。
- 直连与代理健康为 `UP / 1.0.73 / 4d51af1`。项目详情与批量弹窗的线上 JS 与制品 SHA-256 一致，确认包含科室、搜索选择和关联确认。项目管理等入口返回 200，建档接口的匿名请求返回 401。日志前缀为 `20261119-project-resume-match-1.0.73-`。

后端回滚包 `backend/releases/1.0.73-20261119_100732/previous-expert-system.jar` 与 1.0.72 制品 SHA-256 一致。前端通过 [回滚配置](../../frontend/release-metadata/1.0.73-project-resume-match-20261119.rollback.conf.txt) 平滑切回。生产真实简历及档案关联待业务验收，步骤见 [任务交接](../handover/tasks/20261119_project_resume_department_match.md)。
