-- ============================================================
-- 专家库系统 审批流三表迁移脚本（V1.002 增量）
-- 适用数据库：MySQL 8.x
-- 说明：补齐 expert_system 库审批流三表（approval_config /
--       expert_change_request / approval_record），
--       保证全新环境可建出全部 6 张表。
--       使用 CREATE TABLE IF NOT EXISTS，幂等可重复执行，不删除已有数据。
-- 依据：实际库 SHOW CREATE TABLE 导出（2026-10-22）+ 实体类字段映射
-- ============================================================

USE expert_system;

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
