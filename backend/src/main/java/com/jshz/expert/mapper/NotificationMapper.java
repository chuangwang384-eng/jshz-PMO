package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通用消息通知 Mapper。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
