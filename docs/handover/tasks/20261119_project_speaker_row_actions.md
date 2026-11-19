# 项目候选单条保存与清空已保存专家

主责 Codex，日期 2026-11-19，基线 `main@53c8cae`，开工工作区干净。状态为已完成待验收。

项目日程移除「还原为解析结果」，候选操作列新增「保存」。单条保存仅消费对应候选，其余候选及未提交编辑保留；整批保存和放弃候选入口继续可用。参会专家表头新增「清空已保存专家」，用户确认后清空当前项目全部正式参会记录（含手动补录），保留解析候选、专家库档案和项目专家名单关联；同时清理针对旧名单的待确认差异并记录操作日志。

修改项目详情、API 封装、项目控制器和日程解析服务。现有保存接口支持可选 `candidateId`，清空走 `DELETE /api/project/{id}/speakers/confirmed`，沿用项目可操作权限，无数据库、权限配置或部署配置迁移。

JDK 17 隔离 Maven 回归执行 `-Dtest=ScheduleSpeakerWorkflowTest,ScheduleSpeakerDiffTest,ScheduleParseServiceTest,ProjectAccessRegressionTest`，32 项通过。前端隔离构建、Node 操作模拟及 `git diff --check` 通过。Node 模拟验证单条提交携带候选 ID、保留其他草稿，取消清空不发请求。日志前缀为 `20261119-project-speaker-row-actions-`。

已随 1.0.72 上线，见 [生产发布与回滚](../../releases/20261119-project-speaker-1.0.72.md)。登录测试项目验收时，先修改两个候选，点其中一个「保存」并确认另一条编辑仍保留。再取消一次清空确认，随后确认清空并核对候选和专家库档案。用户已经确认清空的参会记录可从备份另行恢复。
