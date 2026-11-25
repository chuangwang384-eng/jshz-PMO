package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.NotificationMute;
import org.apache.ibatis.annotations.Mapper;

/** 消息免打扰偏好 Mapper（需求 D）。禁止复制本地副本；查询一律带 user_id 条件。 */
@Mapper
public interface NotificationMuteMapper extends BaseMapper<NotificationMute> {
}
