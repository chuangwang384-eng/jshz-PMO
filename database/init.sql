-- ============================================================
-- 专家库系统 数据库初始化脚本（正式版 V1.001）
-- 适用数据库：MySQL 8.x
-- 依据设计文档：docs/2026-10-20-专家库系统正式版开发-设计文档.md
-- 说明：使用 CREATE TABLE IF NOT EXISTS，不删除已有数据（消除 DROP 风险）；
--       列名英文、接口中文键由后端映射；密码为 BCrypt 哈希（统一初始密码 111111）
-- ============================================================

CREATE DATABASE IF NOT EXISTS expert_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE expert_system;

-- ------------------------------------------------------------
-- 1. 专家信息表（正式版 18 字段英文列名）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS expert_info (
  id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name               VARCHAR(50)  NOT NULL                COMMENT '姓名',
  unit               VARCHAR(100) DEFAULT NULL           COMMENT '单位',
  department         VARCHAR(100) DEFAULT NULL           COMMENT '科室',
  title              VARCHAR(50)  DEFAULT NULL           COMMENT '职称',
  research_direction VARCHAR(200) DEFAULT NULL           COMMENT '研究方向',
  expertise_area     VARCHAR(500) DEFAULT NULL           COMMENT '擅长领域',
  contact            VARCHAR(200) DEFAULT NULL           COMMENT '联系方式（demo 复合电话+邮箱可达71字符）',
  profile            TEXT                                COMMENT '简介',
  id_card            VARCHAR(18)  DEFAULT NULL           COMMENT '身份证号（查看脱敏，前3后4）',
  bank_card          VARCHAR(30)  DEFAULT NULL           COMMENT '银行卡号（查看脱敏，前3后4）',
  photo              VARCHAR(255) DEFAULT NULL           COMMENT '证件照文件名（相对照片库目录）',
  bank_branch        VARCHAR(100) DEFAULT NULL           COMMENT '银行卡开户行（R10 新增）',
  id_card_front      VARCHAR(255) DEFAULT NULL           COMMENT '身份证正面图片文件名（R10 新增，仅管理页维护、下载包含）',
  id_card_back       VARCHAR(255) DEFAULT NULL           COMMENT '身份证反面图片文件名（R10 新增，仅管理页维护、下载包含）',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  update_by          VARCHAR(50)  DEFAULT NULL           COMMENT '修改人id（登录用户名）',
  PRIMARY KEY (id),
  KEY idx_name (name),
  KEY idx_department (department),
  KEY idx_title (title)
) ENGINE=InnoDB COMMENT='专家信息表（正式版 18 字段）';

-- ------------------------------------------------------------
-- 2. 用户表（登录账号 = 员工姓名；is_admin 管理员名单，替代原 role 表 RBAC）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
  id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username             VARCHAR(50)  NOT NULL                COMMENT '登录账号 = 员工姓名',
  password             VARCHAR(100) NOT NULL                COMMENT 'BCrypt 加密密码（禁止明文）',
  is_admin             TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '0 普通员工 / 1 管理员（管理员名单）',
  wecom_userid         VARCHAR(64)  DEFAULT NULL            COMMENT '企微 userid（企微同步填充；登录标识，唯一索引 uk_wecom_userid）',
  must_change_password TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否需强制改密：1是 0否（设计文档 5.1 首次登录强制改密）',
  status               TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '1 启用 / 0 停用',
  create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='系统用户表';

-- ------------------------------------------------------------
-- 3. 下载审计表（R5 下载留痕 + 下载次数统计）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS download_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT       DEFAULT NULL            COMMENT '下载人 sys_user.id（可空）',
  username      VARCHAR(50)  DEFAULT NULL            COMMENT '下载人姓名',
  expert_id     BIGINT       DEFAULT NULL            COMMENT '专家 id（全量下载时为 NULL）',
  expert_name   VARCHAR(50)  DEFAULT NULL            COMMENT '专家姓名',
  download_type VARCHAR(30)  DEFAULT NULL            COMMENT 'selected_csv / selected_zip / full_csv / full_zip / md',
  download_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下载时间',
  ip            VARCHAR(45)  DEFAULT NULL            COMMENT '来源 IP',
  PRIMARY KEY (id),
  KEY idx_expert_id (expert_id),
  KEY idx_download_time (download_time),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB COMMENT='下载审计表';

-- ------------------------------------------------------------
-- 初始数据（种子）
-- 密码统一为 111111 的 BCrypt 哈希：$2a$10$FECPAy5iIj1rMBwiGKRc0uxg5b8kIlO4lSnHLowNxtdMrJyAo0brK
-- ------------------------------------------------------------
-- 初始管理员（登录账号 admin，密码 111111；部署后请立即修改）
INSERT IGNORE INTO sys_user (username, password, is_admin, must_change_password, status) VALUES
  ('admin', '$2a$10$FECPAy5iIj1rMBwiGKRc0uxg5b8kIlO4lSnHLowNxtdMrJyAo0brK', 1, 0, 1);

-- 示例普通员工（登录账号 = 姓名，密码 111111，首次登录强制改密）
INSERT IGNORE INTO sys_user (username, password, is_admin, must_change_password, status) VALUES
  ('张三', '$2a$10$FECPAy5iIj1rMBwiGKRc0uxg5b8kIlO4lSnHLowNxtdMrJyAo0brK', 0, 1, 1);

-- 示例专家数据（与迁移数据共存的演示数据）
INSERT IGNORE INTO expert_info (name, unit, department, title, research_direction, expertise_area, contact, profile, id_card, bank_card, bank_branch) VALUES
  ('张三', '信息研究院', '人工智能实验室', '教授', '机器学习与计算机视觉', '图像识别、目标检测', '13800000001', '长期从事人工智能与机器学习研究。', '110101199001011234', '6222020212345678901', '中国工商银行'),
  ('李四', '信息技术中心', '网络安全部', '高级工程师', '网络安全攻防', '渗透测试、安全管理', '13800000002', '网络安全攻防与渗透测试方向。', '110101199002022345', '6222020212345678902', '中国建设银行');

-- ------------------------------------------------------------
-- 4. 审批流配置表（需求3/5：审批流开关与审批人角色）
--    初始化行由后端 getConfig() 在首次访问时自动创建
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_config (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  flow_key      VARCHAR(50)  NOT NULL                COMMENT '流程标识',
  flow_name     VARCHAR(100) NOT NULL DEFAULT '专家信息修改审批' COMMENT '流程名称',
  enabled       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用审批流（1启用/0停用）',
  approver_role VARCHAR(50)  NOT NULL DEFAULT 'ADMIN' COMMENT '审批人角色',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_key (flow_key)
) ENGINE=InnoDB COMMENT='审批流配置';

-- ------------------------------------------------------------
-- 5. 专家信息变更审批单表（需求4/5：员工提交的修改/新增专家申请）
--    status: 0 待审批 / 1 已通过 / 2 已驳回
--    request_type: 0=修改申请 / 1=新增专家申请
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS expert_change_request (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  expert_id       BIGINT       DEFAULT NULL            COMMENT '专家ID（新增申请提交时为空，审批通过后回填）',
  expert_name     VARCHAR(100) NOT NULL                COMMENT '专家姓名快照',
  applicant       VARCHAR(100) NOT NULL                COMMENT '申请人用户名',
  change_data     TEXT         NOT NULL                COMMENT '变更内容JSON（字段中文键->新值）',
  reason          VARCHAR(500) DEFAULT NULL            COMMENT '申请原因',
  status          TINYINT      NOT NULL DEFAULT 0      COMMENT '0待审批/1已通过/2已驳回',
  request_type    TINYINT      NOT NULL DEFAULT 0      COMMENT '申请类型：0=修改申请，1=新增专家申请',
  approve_by      VARCHAR(100) DEFAULT NULL            COMMENT '审批人用户名',
  approve_comment VARCHAR(500) DEFAULT NULL            COMMENT '审批意见',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  approve_time    DATETIME     DEFAULT NULL            COMMENT '审批时间',
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (id),
  KEY idx_expert_id (expert_id),
  KEY idx_applicant (applicant),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='专家信息变更审批单';

-- ------------------------------------------------------------
-- 6. 审批流进度记录表（需求3：审批进度时间线）
--    action: SUBMIT / APPROVE / REJECT
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_record (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  request_id  BIGINT       NOT NULL                COMMENT '审批单ID',
  node_name   VARCHAR(100) NOT NULL                COMMENT '节点名称',
  action      VARCHAR(20)  NOT NULL                COMMENT '动作：SUBMIT/APPROVE/REJECT',
  operator    VARCHAR(100) NOT NULL                COMMENT '操作人',
  comment     VARCHAR(500) DEFAULT NULL            COMMENT '说明/意见',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (id),
  KEY idx_request_id (request_id)
) ENGINE=InnoDB COMMENT='审批流进度记录';
