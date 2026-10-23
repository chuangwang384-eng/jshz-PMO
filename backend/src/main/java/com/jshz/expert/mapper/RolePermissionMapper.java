package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联 Mapper（RBAC 阶段1）。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
