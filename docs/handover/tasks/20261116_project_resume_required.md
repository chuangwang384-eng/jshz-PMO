# 项目建档必填项与联系方式

- 任务编号 `20261116-project-resume-required`，主责 Codex，2026-11-16，已完成待验收。
- 基线为 `codex/personal-dashboard-v11` 的 `1fa4cf2`，工作区已有未提交变更。线上后端 `1.0.38.30` UP，Nginx 指向 `1.0.38.30-project-resume-avatar-20261116`。

`BatchResumeDialog.vue` 及 `ProjectDetail.vue` 对项目单人、批量和手动建档标记并要求姓名、单位。解析或填写的联系方式「无」视为空值，其他非空值仍须为手机号或邮箱。选填职称、简介和头像允许留空。项目的姓名匹配与所选照片保存沿用原流程。

`ScheduleParseService` 对 JSON 和 multipart 建档使用同一必填项校验，兼容旧客户端传入的「无」。旧批量解析建档逐条拒绝缺少姓名或单位的输入，缺省职称、简介及联系方式存为空值。现有接口地址和请求结构保持兼容，无数据库迁移、权限调整和运行配置变更。已同步架构及交接索引。

后端执行 `mvn -q -DgitShort=1fa4cf2 -Dtest=ProjectResumeAvatarTest,ScheduleParseServiceTest test`，15 项通过，涵盖空单位拒绝、联系方式格式拒绝、接受「无」及选填字段保持为空。前端 `npm run build -- --outDir ../builds/project-resume-required-final-build-20261116` 和 `git diff --check` 通过。对候选执行 `frontend/tests/project-resume-reuse-browser.mjs` 和 `frontend/tests/expert-batch-resume-browser.mjs`，两组 Edge 模拟接口回归通过。新版验证单位缺失时禁用提交、补单位后可继续、无效号码有具体提示，以及填「无」时省略联系方式并仍可提交头像。旧线上快照在单位缺失提示断言失败。

生产于 2026-11-16 09:41 完成发布，后端 `1.0.38.31` 及同版前端快照通过线上检查。真实建档落库待验收。下一步用李宏原简历确认姓名、单位填妥后可以留空联系方式并提交，核对回绑和头像。发布与回滚路径见 [生产发布记录](../../releases/20261116-project-resume-required.md)。
