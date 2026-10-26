package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper（RBAC 阶段1）。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 按角色编码查询角色（如 EMPLOYEE）。role.code 唯一，查不到返回 null。
     */
    default Role selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, code));
    }
}
