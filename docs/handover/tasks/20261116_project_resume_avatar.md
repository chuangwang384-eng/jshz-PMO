# 项目建档复用专家库简历头像

- 任务编号 `20261116-project-resume-avatar`，主责 Codex，2026-11-16，已完成待验收。
- 基线为 `codex/personal-dashboard-v11` 的 `1fa4cf2`，开工工作区已有未提交变更。线上后端健康检查为 `1.0.38.29` UP，Nginx 使用 `1.0.38.24-project-resume-parity-20261116`。

共用的 `BatchResumeDialog.vue` 将解析接口返回的照片候选用于项目单人和批量建档，复用专家库的预览、选择和取消选择。项目未选头像时继续提交 JSON；选中时由 `archiveManual` API 封装提交 `data` JSON 部件和 `photo` 图片文件。 `ProjectController` 按内容类型路由，`ScheduleParseService` 在校验项目操作权限及待回绑条件后建档，`ExpertService` 复用专家库头像存储，事务回滚清理新写入的头像。无数据库迁移、权限码、依赖或运行配置变更。

| 验证项 | 执行和结果 |
| --- | --- |
| 前端构建 | `npm run build -- --outDir ../builds/project-resume-avatar-build-20261116` 通过，Vite 提示部分 chunk 超过体积阈值 |
| 浏览器回归 | 对候选执行 `frontend/tests/project-resume-reuse-browser.mjs` 和 `frontend/tests/expert-batch-resume-browser.mjs`，Edge 模拟接口通过。旧线上快照在新增的头像候选断言失败。候选验证项目单人选择 PNG 后传送图片及原有字段，批量未选择时提交 JSON |
| 后端定向回归 | `mvn -q -DgitShort=1fa4cf2 -Dtest=ProjectResumeAvatarTest,ScheduleParseServiceTest,ResumeParseServiceTest test`，30 项通过。覆盖 multipart 与 JSON 兼容、服务权限拒绝、头像回滚清理，以及 `RESUME_NAME_TEST_PPTX` 指定的原始 PPTX 在模拟文字模型、无抠图的配置下返回姓名与 JPEG 候选 |

架构及接口说明见 [项目专家建档](../../architecture.md)。2026-11-16 09:24 完成生产后端蓝绿发布及前端快照切换，见 [生产发布记录](../../releases/20261116-project-resume-avatar.md)。真实简历建档的头像落盘及回绑待业务验收。回滚先恢复旧前端，再按需回滚后端；已建档数据使用现有业务流程处置。
