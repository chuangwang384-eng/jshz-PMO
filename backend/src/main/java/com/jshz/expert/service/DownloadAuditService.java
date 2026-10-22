package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.entity.DownloadLog;
import com.jshz.expert.mapper.DownloadLogMapper;
import com.jshz.expert.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 下载审计服务（正式版 V1.001，R5 下载留痕 + 下载次数）。
 */
@Service
public class DownloadAuditService extends ServiceImpl<DownloadLogMapper, DownloadLog> {

    /**
     * 记录一次下载行为。
     *
     * @param authUser     当前登录用户（可能为空）
     * @param expertId     专家 id（全量下载时为 null）
     * @param expertName   专家姓名
     * @param downloadType selected_csv / selected_zip / full_csv / full_zip / md
     * @param request      HttpServletRequest（取 IP）
     */
    public void record(AuthUser authUser, Long expertId, String expertName, String downloadType,
                       HttpServletRequest request) {
        DownloadLog log = new DownloadLog();
        log.setUserId(authUser != null ? authUser.getUserId() : null);
        log.setUsername(authUser != null ? authUser.getUsername() : null);
        log.setExpertId(expertId);
        log.setExpertName(expertName);
        log.setDownloadType(downloadType);
        log.setDownloadTime(LocalDateTime.now());
        log.setIp(clientIp(request));
        this.save(log);
    }

    /**
     * 各专家下载次数统计（expertId -> count）。
     */
    public Map<Long, Long> countGroupByExpert() {
        List<Map<String, Object>> rows = baseMapper.countGroupByExpert();
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object expertId = row.get("expertId");
                Object cnt = row.get("cnt");
                if (expertId != null && cnt != null) {
                    result.put(((Number) expertId).longValue(), ((Number) cnt).longValue());
                }
            }
        }
        return result;
    }

    /**
     * 单个专家下载次数。
     */
    public Long countByExpertId(Long expertId) {
        Long cnt = baseMapper.countByExpertId(expertId);
        return cnt == null ? 0L : cnt;
    }

    /**
     * 分页查询下载审计记录（管理员）。
     */
    public Page<DownloadLog> pageQuery(Long expertId, String username, int page, int size) {
        LambdaQueryWrapper<DownloadLog> wrapper = new LambdaQueryWrapper<>();
        if (expertId != null) {
            wrapper.eq(DownloadLog::getExpertId, expertId);
        }
        if (StringUtils.hasText(username)) {
            wrapper.like(DownloadLog::getUsername, username);
        }
        wrapper.orderByDesc(DownloadLog::getDownloadTime);
        return this.page(new Page<>(page, size), wrapper);
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
