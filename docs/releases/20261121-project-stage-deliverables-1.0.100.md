# 项目阶段交付物与串场制作 1.0.100

- 2026-11-21 09:03（北京时间）完成生产前后端切换。功能提交 82fa04a 已快进合入本地 main。后端直连和代理均返回 UP / 1.0.100 / 82fa04a，Nginx 代理指向正式端口 8080。
- 前端生产快照为 1.0.100-stage-deliverables-20261121。首页、项目列表、项目 56 的串场编辑器入口及编辑器 JS 返回 200。三个页面入口与候选制品一致。匿名访问预算、阶段表单及串场日程接口返回 401。
- Java 17 的 Maven 定向构建运行 47 项回归测试，全部通过。前端 npm run build 通过。隔离 Edge 对串场待办、从日程生成茶歇页、编辑、排序、预览、草稿重开和归档后推进的验证通过。服务端生成的 HTML 在 Edge 断网模式验证了左右方向键播放。
- 制品目录为 builds/1.0.100-build-20261121_085804。已核对生产 JAR、生产前端入口和两项回滚制品的 SHA-256。沿用现有数据库和项目文件目录，无数据库迁移或业务配置改动。
- 本次发布未执行真实生产项目的报价确认、财务表写入、串场上传或发布。现有日程自动提取使用时段、表格和常见会议环节文字，复杂或低清晰度日程使用编辑器核对、补录。
- 技术证据见 logs/project-stage-deliverables-preflight-1.0.100.log 和 logs/project-stage-deliverables-apply-1.0.100.log。详细交接见 [任务记录](../handover/tasks/20261121_project_stage_deliverables_1.0.100.md)。

## 回滚

1. 后端以既有蓝绿脚本部署 backend/releases/1.0.100-20261121_090109/previous-expert-system.jar，目标版本 1.0.99。发布前备份与前次 1.0.99 制品一致。
2. 前端按 frontend/release-metadata/1.0.100-stage-deliverables-20261121.rollback.conf.txt 恢复 Nginx 配置，校验后优雅重载。回滚快照兼容新旧静态资源，其 index 已与旧版快照校验一致。
3. 核对 8080 直连、Nginx 代理、前端入口和匿名权限。应用回退无需数据库回退。
