-- MySQL 8.0。会议预算制作阶段审查整改（2026-11-26，docs/handover/tasks/20261126_meeting_budget_review.md）：
-- project_budget 增加乐观锁 version 列——前端保存时携带加载时版本号，服务端比对不一致返回 409，
-- 消除并发 last-write-wins（含 CONFIRMED 被并发草稿覆回）。
-- 可重跑：列已存在时 ALTER 报 Duplicate column，需人工忽略；执行前先跑 check-schema-parity 确认基线无漂移。
ALTER TABLE `project_budget`
  ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（2026-11-26：每次保存/确认+1，前端提交携带加载时版本，不一致返回409）'
  AFTER `estimate_inputs`;
