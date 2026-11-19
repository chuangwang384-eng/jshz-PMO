# 项目日程与简历照片 1.0.69 发布

2026-11-19 08:43 CST 完成生产切换。主责 Codex。日程修复 `bd63533`、简历修复 `e019bc1` 和版本提交 `a133dad` 已进入本地 `main`。前后端制品位于 `builds/1.0.69-build-20261119_083749/`。

- 项目日程按单元格读取讲者、主持和讨论嘉宾，给候选保留时间和议题。简历按内容识别误标为 PPT 的 PPTX，保留有透明背景的证件照，并清理抠图中与主像分离的远处前景。数据库结构、接口契约、权限和应用配置沿用现状。
- JDK 17 在隔离工作树执行 `mvn -q -f backend/pom.xml -DgitShort=a133dad -Dtest=ScheduleParseServiceTest,ResumeParseServiceTest,PhotoMatteServiceTest,ProjectResumeAvatarTest,ProjectAccessRegressionTest test`。60 项通过，5 项可选测试跳过。前后端由 `scripts/build-all.ps1` 构建，JAR 可执行包结构与版本、提交号均通过检查。
- `scripts/backup.ps1` 完成备份和校验。目录为 `backup/schedule-resume-1.0.69-20261119/backup_20261119_083842/`，数据库导出、照片库和知识文档库均通过 SHA-256 与内容检查。无数据库迁移。
- 后端预演通过。8082 影子实例通过健康及业务探针，Nginx 平滑切流后回到 8080。前端切换至 `frontend/releases/1.0.69-schedule-resume-20261119/`，重载前后 Nginx 主进程 PID 为 7696。
- 生产直连和代理健康均返回 `UP / 1.0.69 / a133dad`。发布脚本末尾的文本比较在 Windows PowerShell 下误报首页内容不匹配，独立复核以响应字节 SHA-256 确认首页、项目管理、专家库和登录页均匹配发布快照。新旧 JS 入口返回 200，两个解析接口的匿名请求均返回 401，Nginx 和后端服务均为 Running。

回滚时，后端用 `backend/releases/1.0.69-20261119_084140/previous-expert-system.jar` 按蓝绿流程切回 `1.0.68`，该包的 SHA-256 已与原制品比对。前端使用 `frontend/release-metadata/1.0.69-schedule-resume-20261119.rollback.conf.txt` 平滑重载并复核路由及健康，无数据库补偿。构建、测试、备份、预演和核验日志位于 `logs/`，前缀为 `20261119-schedule-resume-1.0.69-`。

登录后的生产原件上传与选照待业务验收。先在指定项目复测日程候选的角色、时间与议题，再在专家库核对三份简历的首选照片。花枝和衣袖周围与主体相连的少量残边见 [简历原件交接](../handover/tasks/20261118_three_portrait_fixes.md)。日程原件复测见 [日程交接](../handover/tasks/20261119_schedule_ppt_role_cells.md)。
