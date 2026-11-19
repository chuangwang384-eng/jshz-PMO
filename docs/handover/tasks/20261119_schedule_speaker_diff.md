# 日程参会差异与简历一键建档

- 任务编号 20261119-schedule-speaker-diff，主责 Codex，基线 `main@810729c`，初始工作区干净。开发分支 `codex/schedule-speaker-diff`，JDK 17 与隔离构建目录验证。
- 新日程保留手动补录，按出场过滤已保存的同场同角色。角色变更可从弹窗更新原记录。第二场仍为新增候选。旧记录与新出场无法唯一对齐时，弹窗并列显示减少的出场和同一专家的新增候选，分别由用户确认。移除只涉及当前项目的参会记录。
- 当前待办有候选时定位到保存入口。有已确认的待建档专家且候选已处理后，选取简历即逐份自动解析、建档和回绑。仅接受正文识别的姓名与待建档专家匹配且单位齐全的简历。存在同名档案、无法匹配或解析失败时逐份返回原因。手动建档与人工核对弹窗仍可从相应参会行进入。
- 代码涉及 `ScheduleSpeakerDiff`、`ScheduleParseService`、`ProjectController`、`ProjectService` 的日程阶段校验，以及项目详情组件和 API 封装。新增差异审批 API、解析批次标识和校验，复用已有 JSON 字段、参会记录表及项目权限。无需 DDL、迁移或新增配置。

验证通过。JDK 17 在隔离副本执行 `mvn -q -f backend/pom.xml -Dspring-boot.build-info.skip=true -Dtest=ScheduleSpeakerWorkflowTest,ScheduleSpeakerDiffTest,ScheduleParseServiceTest,ProjectAccessRegressionTest,ProjectStorageRegressionTest,ProjectResumeAvatarTest test`，36 项通过，0 失败、0 跳过。`npm --prefix frontend run build -- --outDir <LOCAL_PATH>` 通过，保留已有大包提示。`git diff --check` 通过。隔离数据库及登录页面端到端未执行。

验收步骤为在隔离测试项目先保存含多场和手动补录的日程，再上传含不变、换角色、新增及减少场次的版本。核对候选、差异弹窗、确认和保留两个分支，验证移除之后专家档案仍可查。保存新增候选后从「一键上传」选择匹配、不匹配、同名冲突和损坏的简历，检查结果及回绑。

发布时需要前后端同期切换。回滚方式是撤销本任务提交并部署前一制品。已由用户确认的参会记录变更和自动创建的专家档案不会随代码回滚，必要时按操作日志和备份处理。任务仅合并本地 `main`，生产发布与业务验收待单独执行。
