-- MySQL 8+. Requires sys_user. Reapplying preserves any administrator edits.
CREATE TABLE IF NOT EXISTS `profile_department` (
  id bigint NOT NULL AUTO_INCREMENT,
  seed_key varchar(40) DEFAULT NULL,
  parent_id bigint DEFAULT NULL,
  name varchar(100) NOT NULL,
  active tinyint NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_profile_department_seed (seed_key),
  KEY idx_profile_department_parent (parent_id),
  CONSTRAINT fk_profile_department_parent FOREIGN KEY (parent_id) REFERENCES profile_department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `profile_membership` (
  user_id bigint NOT NULL,
  department_id bigint NOT NULL,
  position varchar(100) DEFAULT NULL,
  PRIMARY KEY (user_id),
  KEY idx_profile_membership_department (department_id),
  CONSTRAINT fk_profile_membership_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_profile_membership_department FOREIGN KEY (department_id) REFERENCES profile_department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO profile_department (seed_key,name) VALUES
('GENERAL_OFFICE','总经办'),('PROJECT','项目部'),('DESIGN','设计部'),
('FINANCE_COMPLIANCE','财务合规部'),('HR_ADMIN','人事行政部');
