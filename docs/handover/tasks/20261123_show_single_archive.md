# 串场滚轮缩放、唯一成品与项目侧栏

## 任务与基线

- 任务 20261123-show-single-archive，主责 Codex，2026-11-23。开发分支 `codex/show-single-archive` 基于本地 `main` 的 `dede169`，生产前后端基线为 1.0.127。
- 已确认 PPT、PPTX、HTML 共用一份有效串场成品。历史定点清理范围为「君实线下会议」，保留最新资料。图片手动缩放不设业务百分比上下限。
- 验收覆盖固定框内滚轮缩放、跨格式替换、旧文件可恢复、侧栏图 3 卡片去除和图 4 卡片置顶。

## 实际改动

- `CropAvatar.vue` 用裁剪区滚轮及放大、缩小按钮调节图片。固定框、拖动、键盘方向键及自动人脸加肩部留量沿用原逻辑。
- `ProjectDetail.vue` 移除右侧项目成员展示卡，将合规要求注意事项移至右栏顶部，继续支持查看和处理推送事项。
- 串场 PPT 上传与 HTML 归档共用 `ProjectService.archiveShowDocument`。串场请求按项目行加锁，落盘后软删除同项目同阶段的旧成品并登记新文件，通过同一事务提交。新文件的事务回滚清理由存储服务负责。
- 接口路径、参数、权限及配置沿用，无数据库结构迁移。架构文档同步说明。

## 定点清理

- 只读查询确认要清理项目 57、阶段 12。该组有有效资料 93、94、95、96，按创建时间排序最新为 96。全库查询发现一组重复的有效串场资料。本轮仅针对确认的一个项目。
- 发布备份完成后再执行定点清理。事务按项目加锁，预检项目名、阶段、四条活动记录及最新 ID。只对 93–95 写入软删除与审计标记，其他项目、其他类型文件及原物理文件均排除。执行前数据在 `logs/show-128-cleanup-before.log`。
- 必要时参照发布备份及本次审计标记恢复指定记录。恢复前重新检查当前唯一成品，避免造成重复。

## 验证与发布

- `npm run build -- --outDir <LOCAL_PATH>` 通过。首次编译发现已移除头像样式的残余闭合括号，修正后通过。
- `node temp/show-single-edge.cjs <LOCAL_PATH>` 通过。模拟带合规推送的项目，验证该卡置顶、项目成员卡移除、照片滚轮缩放可小于固定框、缩放按钮工作。
- JDK 17 执行 `mvn -q -DgitShort=<基线短提交> -Dtest=ProjectStageDeliverableTest,ProjectShowTemplateServiceTest,ProjectAccessRegressionTest test`，23 项通过。单测环境先初始化 MyBatis Lambda 字段元数据。
- `scripts/release-full.cmd -Mode Preflight -Version 1.0.128 -Slug show-single-archive` 通过。
- 功能提交 `9afb0be` 已快进合入本地 `main`。1.0.128 全栈发布成功，线上后端健康和前端主包版本均实测。
- 生产定点清理已提交，3 条旧成品记录软删除。资料 96 仍是唯一有效串场文件，另有两份其他有效项目资料。四份原文件 SHA-256 均未变化，全库重复组复查为 0。证据见 `logs/show-128-cleanup-apply.log` 和 `logs/show-128-file-hashes-checked.log`。
- 真实项目登录后的 PPT 与 HTML 交替归档待业务验收。回滚与备份路径见 [1.0.128 发布记录](../../releases/20261123-show-single-archive-1.0.128.md)。
