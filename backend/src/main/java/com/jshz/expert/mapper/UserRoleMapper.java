package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper（RBAC 阶段1）。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
