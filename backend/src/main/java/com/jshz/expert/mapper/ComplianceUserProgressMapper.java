package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceUserProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 个人事项进度/提醒/备注（仅本人可见） Mapper。
 */
@Mapper
public interface ComplianceUserProgressMapper extends BaseMapper<ComplianceUserProgress> {
}
