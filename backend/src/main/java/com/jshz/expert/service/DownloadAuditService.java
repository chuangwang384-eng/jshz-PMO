package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jshz.expert.entity.DownloadLog;
import com.jshz.expert.mapper.DownloadLogMapper;
import com.jshz.expert.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 下载审计服务（正式版 V1.001，R5 下载留痕 + 下载次数）。
 * 用户操作日志改版：写入 download_log 的同时双写 operation_log（action_type=DOWNLOAD），
 * download_log 保留（下载次数统计等仍依赖），历史数据已由迁移脚本并入 operation_log。
 */
@Service
@RequiredArgsConstructor
public class DownloadAuditService extends ServiceImpl<DownloadLogMapper, DownloadLog> {

    private final OperationLogService operationLogService;

    /** 下载类型中文映射（操作日志描述用，与前端 downloadTypeMap 口径一致） */
    private static final Map<String, String> DOWNLOAD_TYPE_TEXT = Map.ofEntries(
            Map.entry("selected_xlsx", "选中导出Excel"),
            Map.entry("selected_zip", "选中导出ZIP"),
            Map.entry("full_xlsx", "全量Excel"),
            Map.entry("full_zip", "全量ZIP"),
            Map.entry("employee_zip", "员工ZIP"),
            Map.entry("md", "下载说明"),
            Map.entry("md_full", "下载说明（完整）"),
            Map.entry("template_xlsx", "导入模板"),
            Map.entry("knowledge_doc", "知识文档下载"),
            // 历史 CSV 类型兼容
            Map.entry("selected_csv", "选中导出CSV（历史）"),
            Map.entry("full_csv", "全量CSV（历史）"));

    private static String downloadTypeText(String downloadType) {
        return DOWNLOAD_TYPE_TEXT.getOrDefault(downloadType, downloadType);
    }

    /**
     * 记录一次下载行为。
     *
     * @param authUser     当前登录用户（可能为空）
     * @param expertId     专家 id（全量下载时为 null）
     * @param expertName   专家姓名
     * @param downloadType selected_xlsx / selected_zip / full_xlsx / full_zip / md / template_xlsx
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
        // 双写通用操作日志（下载/导出）
        operationLogService.record(
                log.getUserId(), log.getUsername(), OperationLogService.ACTION_DOWNLOAD,
                (expertName == null ? "全量数据" : "专家「" + expertName + "」") + "·" + downloadTypeText(downloadType),
                "EXPERT", expertId, request);
    }

    /**
     * 记录一次知识文档下载（C4）：download_type=knowledge_doc，doc_id 关联文档，
     * expert_name 复用为文档标题（下载留痕列表展示用）。
     */
    public void recordKnowledgeDoc(AuthUser authUser, Long docId, String docTitle,
                                   HttpServletRequest request) {
        DownloadLog log = new DownloadLog();
        log.setUserId(authUser != null ? authUser.getUserId() : null);
        log.setUsername(authUser != null ? authUser.getUsername() : null);
        log.setExpertId(null);
        log.setExpertName(docTitle);
        log.setDocId(docId);
        log.setDownloadType("knowledge_doc");
        log.setDownloadTime(LocalDateTime.now());
        log.setIp(clientIp(request));
        this.save(log);
        // 双写通用操作日志（知识文档下载）
        operationLogService.record(
                log.getUserId(), log.getUsername(), OperationLogService.ACTION_DOWNLOAD,
                "知识文档《" + docTitle + "》",
                "DOC", docId, request);
    }

    /**
     * 各知识文档下载次数（docId -> count，C4 列表展示用，单次聚合查询）。
     */
    public Map<Long, Long> countGroupByDoc() {
        List<Map<String, Object>> rows = baseMapper.countGroupByDoc();
        Map<Long, Long> result = new java.util.HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object docId = row.get("docId");
                Object cnt = row.get("cnt");
                if (docId != null && cnt != null) {
                    result.put(((Number) docId).longValue(), ((Number) cnt).longValue());
                }
            }
        }
        return result;
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
