package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ApprovalConfigUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批流具体审批人配置 Mapper（approval_config_user 表）。
 */
@Mapper
public interface ApprovalConfigUserMapper extends BaseMapper<ApprovalConfigUser> {
}
