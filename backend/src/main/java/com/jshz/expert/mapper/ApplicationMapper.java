package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.Application;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通用申请 Mapper（application 表）。
 */
@Mapper
public interface ApplicationMapper extends BaseMapper<Application> {
}
