package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限点 Mapper（RBAC 阶段1）。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
