package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Offline-only backfill, never invoked by normal application startup or an HTTP endpoint. */
@Service
@RequiredArgsConstructor
public class ProjectConfigurationMigration {
    private final JdbcTemplate db;
    private final ProjectConfigurationService config;

    public List<Map<String,Object>> legacyRequirements(long typeId,long phaseId,boolean runtime) {
        List<Map<String,Object>> own=bindings(typeId,phaseId);
        // Preserve the historical runtime bug for legacy projects; corrected new definitions
        // keep CUSTOM even when all its items are optional.
        if(own.isEmpty() || (runtime && own.stream().noneMatch(r->ProjectConfigurationService.number(r.get("required"))==1))) return bindings(0,phaseId);
        return own;
    }
    private List<Map<String,Object>> bindings(long type,long phase) {
        return db.queryForList("SELECT r.id,r.type_id typeId,r.phase_id phaseId,t.id templateId,t.name,t.template_type templateType,r.required,r.sort position FROM project_phase_template_rel r JOIN project_template t ON t.id=r.template_id WHERE r.type_id=? AND r.phase_id=? ORDER BY r.sort,r.id",type,phase);
    }
    private Map<String,Object> legacy(long type,String name,boolean runtime) {
        List<Map<String,Object>> phases=db.queryForList("SELECT p.id phaseId,p.name FROM project_type_phase_rel r JOIN project_phase p ON p.id=r.phase_id WHERE r.type_id=? ORDER BY r.sort,r.id",type);
        if(phases.isEmpty()) phases=db.queryForList("SELECT id phaseId,name FROM project_phase WHERE is_active=1 ORDER BY sort,id");
        if(phases.isEmpty()) throw new BusinessException("类型没有可冻结的流程："+type);
        for(int i=0;i<phases.size();i++) {
            Map<String,Object> p=phases.get(i);long phase=ProjectConfigurationService.number(p.get("phaseId"));
            boolean own=!bindings(type,phase).isEmpty();
            p.put("position",i+1); p.put("mode",runtime?"CUSTOM":own?"CUSTOM":"INHERIT");
            p.put("requirements",runtime?legacyRequirements(type,phase,true):own?bindings(type,phase):List.of());
        }
        return new LinkedHashMap<>(Map.of("typeId",type,"name",name,"schemaVersion",1,"phases",phases));
    }
    public Map<String,Object> preflight() {
        List<Map<String,Object>> anomalies=new ArrayList<>(), differences=new ArrayList<>(), orderDifferences=new ArrayList<>();
        for(Map<String,Object> p:db.queryForList("SELECT p.id,p.type_id,p.phase_id FROM project p LEFT JOIN project_config_snapshot s ON s.project_id=p.id WHERE s.project_id IS NULL")) {
            long project=ProjectConfigurationService.number(p.get("id"));
            if(p.get("type_id")==null || p.get("phase_id")==null) {anomalies.add(Map.of("projectId",project,"reason","缺类型或当前阶段"));continue;}
            long type=ProjectConfigurationService.number(p.get("type_id"));
            List<String> names=db.queryForList("SELECT name FROM project_type WHERE id=?",String.class,type);
            if(names.isEmpty()){anomalies.add(Map.of("projectId",project,"reason","类型缺失"));continue;}
            Map<String,Object> def=legacy(type,names.get(0),true);
            Set<Long> ids=new HashSet<>(); for(Map<String,Object> stage:ProjectConfigurationService.rows(def.get("phases"))) ids.add(ProjectConfigurationService.number(stage.get("phaseId")));
            Set<Long> used=new HashSet<>(db.queryForList("SELECT phase_id FROM project_doc WHERE project_id=? UNION SELECT from_phase_id FROM project_phase_history WHERE project_id=? UNION SELECT to_phase_id FROM project_phase_history WHERE project_id=?",Long.class,project,project,project));
            used.remove(null);used.add(ProjectConfigurationService.number(p.get("phase_id")));
            if(!ids.containsAll(used)) anomalies.add(Map.of("projectId",project,"reason","当前、历史或材料阶段不在有效流程"));
        }
        for(Map<String,Object> r:db.queryForList("SELECT r.type_id,r.phase_id FROM project_phase_template_rel r WHERE r.type_id<>0 AND NOT EXISTS(SELECT 1 FROM project_type_config_revision v WHERE v.type_id=r.type_id) GROUP BY r.type_id,r.phase_id HAVING MAX(r.required)=0")) differences.add(r);
        for(Map<String,Object> t:db.queryForList("SELECT t.id,t.name FROM project_type t WHERE NOT EXISTS(SELECT 1 FROM project_type_config_revision v WHERE v.type_id=t.id)")) {
            long type=ProjectConfigurationService.number(t.get("id"));
            List<Map<String,Object>> stages=ProjectConfigurationService.rows(legacy(type,t.get("name").toString(),false).get("phases"));
            Integer previous=null;
            for(Map<String,Object> stage:stages) {
                Integer sort=db.queryForObject("SELECT sort FROM project_phase WHERE id=?",Integer.class,stage.get("phaseId"));
                if(sort==null || (previous!=null && sort!=previous+1)) {
                    orderDifferences.add(Map.of("typeId",type,"reason","旧公共sort与显式顺序不相邻或不一致；快照将按类型流程推进"));break;
                }
                previous=sort;
            }
        }
        long dangling=db.queryForObject("SELECT COUNT(*) FROM project_phase_template_rel r LEFT JOIN project_template t ON t.id=r.template_id LEFT JOIN project_phase p ON p.id=r.phase_id WHERE t.id IS NULL OR p.id IS NULL",Long.class);
        long danglingFlow=db.queryForObject("SELECT COUNT(*) FROM project_type_phase_rel r LEFT JOIN project_type t ON t.id=r.type_id LEFT JOIN project_phase p ON p.id=r.phase_id WHERE t.id IS NULL OR p.id IS NULL",Long.class);
        if(dangling+danglingFlow>0) anomalies.add(Map.of("reason","配置有悬空引用","count",dangling+danglingFlow));
        long unsupported=db.queryForObject("SELECT COUNT(*) FROM project_phase_template_rel r JOIN project_template t ON t.id=r.template_id WHERE t.template_type NOT IN ('FILE','EXPERT','SUPPLIER','BUDGET','RICH_TEXT')",Long.class);
        if(unsupported>0)anomalies.add(Map.of("reason","配置引用未知资料类别","count",unsupported));
        return Map.of("anomalies",anomalies,"optionalFallbackDifferences",differences,"flowOrderDifferences",orderDifferences);
    }
    @Transactional
    public Map<String,Object> migrate(boolean reviewedDifferences) {
        config.lock();Map<String,Object> report=preflight();
        if(!((List<?>)report.get("anomalies")).isEmpty()) throw new BusinessException("迁移阻断：存在异常阶段或配置引用，请查看只读预检报告");
        if(!reviewedDifferences && (!((List<?>)report.get("optionalFallbackDifferences")).isEmpty() || !((List<?>)report.get("flowOrderDifferences")).isEmpty())) throw new BusinessException("迁移阻断：须复核旧全选填回退及流程顺序差异");
        for(Map<String,Object> t:db.queryForList("SELECT id,name FROM project_type ORDER BY sort,id")) {
            long id=ProjectConfigurationService.number(t.get("id"));
            if(db.queryForObject("SELECT COUNT(*) FROM project_type_config_revision WHERE type_id=?",Long.class,id)>0) continue;
            Map<String,Object> def=legacy(id,t.get("name").toString(),false);
            db.update("INSERT INTO project_type_config_revision(type_id,revision,schema_version,definition) VALUES (?,1,1,?)",id,config.encode(def));
            db.update("UPDATE project_type SET active_config_revision_id=LAST_INSERT_ID() WHERE id=?",id);
            // Materialize implicit global-stage membership before any future stage is created.
            if(db.queryForObject("SELECT COUNT(*) FROM project_type_phase_rel WHERE type_id=?",Long.class,id)==0)
                for(Map<String,Object> phase:ProjectConfigurationService.rows(def.get("phases"))) db.update("INSERT INTO project_type_phase_rel(type_id,phase_id,sort) VALUES (?,?,?)",id,phase.get("phaseId"),phase.get("position"));
        }
        for(Map<String,Object> p:db.queryForList("SELECT p.id,p.type_id,t.name FROM project p JOIN project_type t ON p.type_id=t.id LEFT JOIN project_config_snapshot s ON s.project_id=p.id WHERE s.project_id IS NULL")) {
            long type=ProjectConfigurationService.number(p.get("type_id"));
            Map<String,Object> def=legacy(type,p.get("name").toString(),true);
            String encoded=config.encode(def);
            db.update("INSERT INTO project_config_snapshot(project_id,source_revision_id,schema_version,effective_config,config_hash,origin) SELECT ?,active_config_revision_id,1,?,?,'LEGACY_BASELINE' FROM project_type WHERE id=?",p.get("id"),encoded,config.configHash(def),type);
        }
        config.compact();config.changed();return report;
    }
}
