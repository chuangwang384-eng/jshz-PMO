package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.DownloadLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 下载审计 Mapper。
 */
@Mapper
public interface DownloadLogMapper extends BaseMapper<DownloadLog> {

    /**
     * 各专家下载次数统计（R5：专家列表返回下载总次数）。
     */
    @Select("SELECT expert_id AS expertId, COUNT(*) AS cnt FROM download_log " +
            "WHERE expert_id IS NOT NULL GROUP BY expert_id")
    List<Map<String, Object>> countGroupByExpert();

    /**
     * 单个专家下载次数。
     */
    @Select("SELECT COUNT(*) FROM download_log WHERE expert_id = #{expertId}")
    Long countByExpertId(Long expertId);

    /**
     * 各知识文档下载次数统计（C4：download_type=knowledge_doc 按 doc_id 聚合）。
     */
    @Select("SELECT doc_id AS docId, COUNT(*) AS cnt FROM download_log " +
            "WHERE download_type = 'knowledge_doc' AND doc_id IS NOT NULL GROUP BY doc_id")
    List<Map<String, Object>> countGroupByDoc();
}
