package com.jshz.expert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jshz.expert.entity.ComplianceProjectTodo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 项目合规待办 Mapper。 */
@Mapper
public interface ComplianceProjectTodoMapper extends BaseMapper<ComplianceProjectTodo> {

    @Select("""
            SELECT t.*, CASE WHEN d.id IS NULL THEN 1 ELSE 0 END AS source_deleted
            FROM compliance_project_todo t LEFT JOIN knowledge_document d ON d.id = t.document_id
            WHERE t.project_id = #{projectId}
            ORDER BY t.id DESC
            """)
    java.util.List<ComplianceProjectTodo> selectForProject(@Param("projectId") Long projectId);

    /** 推送事务内锁项目行（与来源撤下采用一致锁顺序，防跨文档并发首次绑定竞态）。 */
    @Select("SELECT id FROM project WHERE id = #{projectId} FOR UPDATE")
    Long lockProject(@Param("projectId") Long projectId);
}
