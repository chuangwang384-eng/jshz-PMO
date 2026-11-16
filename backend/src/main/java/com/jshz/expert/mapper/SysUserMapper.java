package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE")
    SysUser lockById(Long id);

    /**
     * 原子自增 Token 版本号（停用/删除/改密/改角色时调用）。
     * 使用数据库自增避免并发读改写丢失更新；旧代码忽略该列。
     */
    @org.apache.ibatis.annotations.Update("UPDATE sys_user SET token_version = token_version + 1 WHERE id = #{id}")
    int bumpTokenVersion(Long id);
}
