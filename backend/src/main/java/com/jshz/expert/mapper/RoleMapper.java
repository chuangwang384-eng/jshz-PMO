package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.Role;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    /**
     * 按角色编码集合批量查询角色（单次 IN 查询，供角色名称动态映射等场景使用）。
     * code 集合为空时不做任何查询，直接返回空列表。
     */
    default List<Role> selectByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapper<Role>().in(Role::getCode, codes));
    }
}
