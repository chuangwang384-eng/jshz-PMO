package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper（RBAC 阶段1）。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
