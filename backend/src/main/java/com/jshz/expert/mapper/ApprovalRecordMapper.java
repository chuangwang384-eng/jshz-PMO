package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ApprovalRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批流进度记录 Mapper。
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {
}
