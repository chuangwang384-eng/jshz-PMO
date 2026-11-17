-- 20261117 估算规则并入收费标准配置（决策 A1/D1：负责人 2026-11-17 批准）
-- 背景：对客报价估算规则（茶歇 70%、机票 2000 等）原硬编码于前端 budgetEstimate.js 的 ESTIMATE_RULES 常量，
--       调整需改代码发版；并入管理员「收费标准配置」页（project_budget_fee_standard）后管理员可在线调价。
-- 说明：幂等（INSERT IGNORE），仅新增 5 行种子，无表结构变更；前端以 ESTIMATE_RULES 常量作接口缺失/未启用时的兜底，
--       兜底值与本种子一致。scene 默认 OFFLINE（估算输入均为线下会议概念；线上项目取不到时走兜底，行为不变）。
INSERT IGNORE INTO `project_budget_fee_standard` (`fee_key`,`name`,`default_value`,`unit`,`remark`) VALUES
('EST_TEA_RATIO',       '茶歇人数比例（估算）',   '70',   '%',     '对客报价估算：参会总人数 × 比例，向上取 5 的倍数'),
('EST_FLIGHT_FARE',     '机票估算单价',          '2000', '元/人',  '大交通选择飞机时的估算单价'),
('EST_PLATFORM_FIRST',  '平台专车单价-一线城市',  '200',  '元/人',  '地面交通选择平台专车，城市档位为一线城市时的估算单价'),
('EST_PLATFORM_OTHER',  '平台专车单价-其他城市',  '100',  '元/人',  '地面交通选择平台专车，城市档位为其他城市时的估算单价'),
('EST_HOTEL_ROOMS',     '住宿默认预留间数（估算）','2',   '间',     '无外埠人员时的住宿预留间数');
