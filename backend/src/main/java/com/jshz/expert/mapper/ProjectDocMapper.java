package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ProjectDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ProjectDoc Mapper。
 */
@Mapper
public interface ProjectDocMapper extends BaseMapper<ProjectDoc> {
    @Select("""
            SELECT d.file_path
            FROM project_budget_quote q
            JOIN project_doc d ON d.id = q.doc_id AND d.project_id = q.project_id
            WHERE q.project_id = #{projectId} AND d.phase_id = #{phaseId} AND d.is_deleted = 0
            ORDER BY q.version_no DESC
            """)
    List<String> listBudgetQuotePaths(@Param("projectId") Long projectId, @Param("phaseId") Long phaseId);
}
