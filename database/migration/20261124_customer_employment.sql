-- MySQL 8+，在已有 customer 的库创建独立任职履历。可重复执行，无历史档案回填。
CREATE TABLE IF NOT EXISTS `customer_employment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_id` bigint NOT NULL,
  `work_unit` varchar(255) NOT NULL,
  `position` varchar(255) NOT NULL,
  `start_month` char(7) DEFAULT NULL COMMENT 'YYYY-MM，空表示未知',
  `end_month` char(7) DEFAULT NULL COMMENT 'YYYY-MM，空表示至今',
  PRIMARY KEY (`id`),
  KEY `idx_customer_employment_order` (`customer_id`,`start_month`,`id`),
  CONSTRAINT `fk_customer_employment_customer` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户任职履历';
