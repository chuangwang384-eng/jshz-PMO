-- MySQL 8.0.19+。重塑启用的默认项目阶段，保留原预算和日程阶段 ID。
-- 可重跑。现有项目当前阶段、阶段历史及配置快照记录沿用原值。
START TRANSACTION;

INSERT INTO `project_phase` (`name`, `sort`, `is_active`) VALUES
    ('会议预算制作', 98, 1),
    ('会前财务资料上传', 99, 1),
    ('日程上传', 100, 1),
    ('串场制作', 101, 1),
    ('结算资料上传', 102, 1),
    ('会议费用结算及项目结项', 103, 1) AS expected
ON DUPLICATE KEY UPDATE `sort` = expected.`sort`, `is_active` = expected.`is_active`;

UPDATE `ai_assistant_entry`
SET `answer` = REPLACE(`answer`, '当前流程：会议预算制作 → 日程上传。',
    '当前流程：会议预算制作 → 会前财务资料上传 → 日程上传 → 串场制作 → 结算资料上传 → 会议费用结算及项目结项。')
WHERE `entry_key` = 'PHASE_ADVANCE'
    AND `answer` LIKE '%当前流程：会议预算制作 → 日程上传。%';

COMMIT;
