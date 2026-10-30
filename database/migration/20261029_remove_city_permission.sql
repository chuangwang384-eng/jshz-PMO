-- 供应商模块改造 v2.0「一、城市固定化」- 移除城市增删权限（幂等，可重复执行）
USE `expert_system`;
DELETE FROM `permission` WHERE `id` IN (62, 63) AND `path` IN ('/api/supplier/cities', '/api/supplier/cities/**');
