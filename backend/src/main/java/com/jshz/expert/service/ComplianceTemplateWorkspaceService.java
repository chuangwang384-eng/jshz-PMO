package com.jshz.expert.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Shared editing content; saving never mutates an employee execution or active template. */
@Service
@RequiredArgsConstructor
public class ComplianceTemplateWorkspaceService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ComplianceV4AccessService access;
    private final ComplianceRolloutService rollout;
    private final ComplianceV4Service templates;
    private final ComplianceWorkspaceSourceService sources;

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch(Exception e) { throw new BusinessException("模板内容格式无效"); }
    }
    private Map<String,Object> decode(Object value) {
        try { return json.readValue(value.toString(),new TypeReference<LinkedHashMap<String,Object>>(){}); }
        catch(Exception e) { throw new BusinessException("模板维护内容损坏，请联系管理员"); }
    }
    private void lock(long id, AuthUser user) {
        if(user==null) throw new BusinessException(401,"未登录");
        rollout.lockUser(user.getUserId());
        db.queryForList("SELECT id FROM knowledge_document WHERE id=? FOR UPDATE",id);
        access.requireDraftAuthor(id,user);
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> open(long id,AuthUser user) {
        lock(id,user);
        if(db.queryForObject("SELECT COUNT(*) FROM compliance_template_workspace WHERE document_id=?",Integer.class,id)==0) {
            var candidates=db.queryForList("SELECT id,state FROM compliance_template_version WHERE document_id=? AND state IN ('DRAFT','PUBLISHED') ORDER BY id DESC",id);
            Map<String,Object> content=new LinkedHashMap<>(Map.of("title","","groups",List.of(),"nodes",List.of()));
            Long active=null;
            if(candidates.size()==1) {
                long selected=((Number)candidates.get(0).get("id")).longValue();
                content=templates.templateDetail(selected,user);
                if("PUBLISHED".equals(candidates.get(0).get("state"))) active=selected;
            }
            db.update("INSERT INTO compliance_template_workspace(document_id,content,selection_resolved,active_template_version_id,updated_by) VALUES(?,?,?,?,?)",id,encode(content),candidates.size()>1?0:1,active,user.getUserId());
            if(active!=null)db.update("UPDATE compliance_template_workspace SET active_content_hash=? WHERE document_id=?",sources.hash(content),id);
        }
        var row=db.queryForMap("SELECT content,row_version,selection_resolved,active_template_version_id,active_content_hash,updated_at FROM compliance_template_workspace WHERE document_id=?",id);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("content",decode(row.get("content"))); result.put("rowVersion",row.get("row_version"));
        result.put("selectionResolved",((Number)row.get("selection_resolved")).intValue()==1);
        result.put("activeTemplateVersionId",row.get("active_template_version_id"));result.put("updatedAt",row.get("updated_at"));
        List<String> reviewedCodes=new ArrayList<>();
        Object contentNodes=decode(row.get("content")).get("nodes");
        if(contentNodes instanceof List<?> nodeRows)for(Object n:nodeRows){
            Map<String,Object> node=decode(encode(n));
            if(db.queryForObject("SELECT COUNT(*) FROM compliance_template_review WHERE document_id=? AND node_code=? AND content_hash=?",Integer.class,id,node.get("nodeCode"),sources.reviewHash(node))>0)reviewedCodes.add(node.get("nodeCode").toString());
        }
        result.put("reviewedNodeCodes",reviewedCodes);
        result.put("hasChanges",!sources.hash(decode(row.get("content"))).equals(row.get("active_content_hash")));
        return result;
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> save(long id,Map<String,Object> body,AuthUser user) {
        lock(id,user);
        if(!(body.get("content") instanceof Map<?,?> content) || !(body.get("expectedRowVersion") instanceof Number revision))
            throw new BusinessException("缺少模板内容或编辑序号");
        validate(id,content,user,false);
        String encoded=encode(content);
        if(encoded.length()>1000000) throw new BusinessException("模板内容过大");
        int updated=db.update("UPDATE compliance_template_workspace SET content=?,row_version=row_version+1,updated_by=? WHERE document_id=? AND row_version=? AND selection_resolved=1",encoded,user.getUserId(),id,revision.intValue());
        if(updated!=1) throw new BusinessException(409,"内容已被修改或尚未选择保留模板，请刷新对比；本地内容已保留");
        return open(id,user);
    }
    private void maintainer(AuthUser user) {
        AuthUser current=access.currentUser(user.getUserId());
        if(current==null || !current.hasPermission("knowledge:maintain")) throw new BusinessException(403,"需要维护人权限");
    }
    private int revision(Map<String,Object> body) {
        if(!(body.get("expectedRowVersion") instanceof Number n)) throw new BusinessException("缺少编辑序号");
        return n.intValue();
    }
    private Map<String,Object> previous(long id,Map<String,Object> body) {
        String key=Objects.toString(body.get("requestId"),"");
        if(!key.matches("[a-zA-Z0-9-]{16,64}")) throw new BusinessException("请求标识无效");
        var rows=db.queryForList("SELECT payload_hash,result FROM compliance_template_workspace_request WHERE document_id=? AND request_id=?",id,key);
        if(rows.isEmpty()) return null;
        if(!digest(body).equals(rows.get(0).get("payload_hash"))) throw new BusinessException(409,"重复请求内容不一致");
        return decode(rows.get(0).get("result"));
    }
    private String digest(Object value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private Map<String,Object> record(long id,Map<String,Object> body,Map<String,Object> result) {
        db.update("INSERT INTO compliance_template_workspace_request(document_id,request_id,payload_hash,result,actor_user_id) SELECT ?,?,?,?,updated_by FROM compliance_template_workspace WHERE document_id=?",id,body.get("requestId"),digest(body),encode(result),id);
        return result;
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public List<Map<String,Object>> candidates(long id,AuthUser user) {
        lock(id,user); maintainer(user);
        return db.queryForList("SELECT id,title,state,revision,update_time AS updated_at FROM compliance_template_version WHERE document_id=? AND state IN ('DRAFT','PUBLISHED') ORDER BY id DESC",id);
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> select(long id,Map<String,Object> body,AuthUser user) {
        lock(id,user); maintainer(user);
        Map<String,Object> existing=previous(id,body); if(existing!=null) return existing;
        Map<String,Object> workspace=open(id,user);
        if(Boolean.TRUE.equals(workspace.get("selectionResolved"))) throw new BusinessException(409,"已完成选择，请刷新");
        if(!(body.get("selectedId") instanceof Number selected)) throw new BusinessException("请选择保留内容");
        var found=db.queryForList("SELECT state FROM compliance_template_version WHERE id=? AND document_id=? AND state IN ('DRAFT','PUBLISHED')",selected.longValue(),id);
        if(found.isEmpty()) throw new BusinessException(404,"所选模板不可用");
        Map<String,Object> content=templates.templateDetail(selected.longValue(),user);
        Long active="PUBLISHED".equals(found.get(0).get("state"))?selected.longValue():null;
        int n=db.update("UPDATE compliance_template_workspace SET content=?,active_template_version_id=?,selection_resolved=1,row_version=row_version+1,updated_by=? WHERE document_id=? AND row_version=? AND selection_resolved=0",encode(content),active,user.getUserId(),id,revision(body));
        if(n!=1) throw new BusinessException(409,"内容已变化，请刷新");
        db.update("UPDATE compliance_template_workspace SET active_content_hash=? WHERE document_id=?",active==null?null:sources.hash(content),id);
        // Unselected immutable records remain readable by existing execution references.
        return record(id,body,open(id,user));
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> activate(long id,Map<String,Object> body,AuthUser user) {
        lock(id,user); maintainer(user);
        Map<String,Object> existing=previous(id,body); if(existing!=null) return existing;
        Map<String,Object> workspace=open(id,user);
        if(!Boolean.TRUE.equals(workspace.get("selectionResolved")) || ((Number)workspace.get("rowVersion")).intValue()!=revision(body)) throw new BusinessException(409,"内容已变化，请刷新对比");
        Map<String,Object> content=decode(encode(workspace.get("content")));
        String activationHash=sources.hash(content);
        validate(id,content,user,true);
        if(content.get("extractionId")!=null){var extraction=sources.extraction(id,content.get("extractionId").toString(),user);if(Boolean.TRUE.equals(extraction.get("incomplete")) && !Boolean.TRUE.equals(extraction.get("confirmed")))throw new BusinessException("请人工确认漏提取页及截断内容已补齐");}
        if(Objects.toString(content.get("title"),"").isBlank()) throw new BusinessException("请填写模板名称");
        if(!(content.get("nodes") instanceof List<?> nodes) || nodes.isEmpty()) throw new BusinessException("请添加事项");
        for(Object item:nodes) {
            if(!(item instanceof Map<?,?> node) || Objects.toString(node.get("title"),"").isBlank()) throw new BusinessException("请填写所有事项名称");
            String source=Objects.toString(node.get("sourceAnchor"),"");
            if(source.trim().startsWith("{")) {
                Map<String,Object> citation=decode(source);
                String hash=sources.reviewHash(decode(encode(node)));
                if(db.queryForObject("SELECT COUNT(*) FROM compliance_template_review WHERE document_id=? AND node_code=? AND content_hash=?",Integer.class,id,node.get("nodeCode"),hash)==0) throw new BusinessException("请逐项核对原文；修改事项后须重新核对");
            }
        }
        if(!Boolean.TRUE.equals(workspace.get("hasChanges")))return record(id,body,workspace);
        for(Object item:nodes) {
            @SuppressWarnings("unchecked") Map<String,Object> node=(Map<String,Object>)item;
            if(!List.of("SINGLE","MULTI").contains(Objects.toString(node.get("inputType"),""))) node.remove("options");
        }
        var created=templates.createDraft(id,null,Objects.toString(content.get("title"),""),user);
        long templateId=((Number)created.get("id")).longValue();
        content.put("rowVersion",created.get("rowVersion"));
        templates.patchTemplate(templateId,content,user);
        templates.publish(templateId,user);
        db.update("UPDATE compliance_template_workspace SET active_template_version_id=?,active_content_hash=?,row_version=row_version+1,updated_by=? WHERE document_id=?",templateId,activationHash,user.getUserId(),id);
        return record(id,body,open(id,user));
    }

    @SuppressWarnings("unchecked") private void validate(long id,Map<?,?> content,AuthUser user,boolean complete){
        if(!(content.get("nodes") instanceof List<?> nodes) || nodes.size()>200) throw new BusinessException("事项清单格式无效或超过200项");
        if(Objects.toString(content.get("title"),"").length()>200)throw new BusinessException("模板名称超过200字");
        Set<String> codes=new HashSet<>();
        if(content.get("groups") instanceof List<?> groups){Set<String> names=new HashSet<>();for(Object g:groups)if(!(g instanceof String name) || name.isBlank() || name.length()>32 || !names.add(name))throw new BusinessException("阶段名称须为1～32字且不可重复");}
        for(Object value:nodes){
            if(!(value instanceof Map<?,?> raw))throw new BusinessException("事项格式无效");
            var node=(Map<String,Object>)raw;
            String code=Objects.toString(node.get("nodeCode"),"");
            if(!code.matches("[a-zA-Z0-9-]{1,64}") || !codes.add(code))throw new BusinessException("事项标识重复或无效");
            if(!List.of("CHECK","TEXT","SINGLE","MULTI").contains(node.get("inputType")))throw new BusinessException("填写方式无效");
            if(Objects.toString(node.get("title"),"").length()>200 || Objects.toString(node.get("stage"),"").length()>32 || Objects.toString(node.get("sourceAnchor"),"").length()>500)throw new BusinessException("事项名称、阶段或引用超出长度限制");
            if(complete && Objects.toString(node.get("title"),"").isBlank())throw new BusinessException("请填写所有事项名称");
            if(complete && List.of("SINGLE","MULTI").contains(node.get("inputType"))){
                if(!(node.get("options") instanceof List<?> options) || options.isEmpty())throw new BusinessException("选择事项至少需要一个完整选项");
                Set<String> optionCodes=new HashSet<>();for(Object o:options){if(!(o instanceof Map<?,?> option) || Objects.toString(option.get("label"),"").isBlank() || !optionCodes.add(Objects.toString(option.get("optionCode"),"")) || Objects.toString(option.get("optionCode"),"").isBlank())throw new BusinessException("选项名称或标识不完整/重复");}
            }
            sources.verify(id,node,user);
        }
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    @SuppressWarnings("unchecked") public Map<String,Object> merge(long id,Map<String,Object> body,AuthUser user){
        lock(id,user);Map<String,Object> prior=previous(id,body);if(prior!=null)return prior;
        var w=open(id,user);if(((Number)w.get("rowVersion")).intValue()!=revision(body) || !Boolean.TRUE.equals(w.get("selectionResolved")))throw new BusinessException(409,"编辑内容已变化，请刷新");
        String extractionId=Objects.toString(body.get("extractionId"),"");sources.extraction(id,extractionId,user);
        var content=decode(encode(w.get("content")));var nodes=(List<Map<String,Object>>)content.get("nodes");
        if(!(body.get("actions") instanceof List<?> actions) || actions.size()>200)throw new BusinessException("请选择需要合并的候选");
        Set<String> usedCandidates=new HashSet<>(),usedTargets=new HashSet<>();
        for(Object a:actions){
            if(!(a instanceof Map<?,?> action))throw new BusinessException("合并动作无效");
            String candidateId=Objects.toString(action.get("candidateId"),"");var candidate=sources.candidate(id,extractionId,candidateId,user);
            String target=Objects.toString(action.get("targetCode"),"");if(!usedCandidates.add(candidateId) || (!target.isBlank() && !usedTargets.add(target)))throw new BusinessException("同一候选或替换目标不可重复选择");Map<String,Object> node=null;
            if(!target.isBlank()){node=nodes.stream().filter(n->target.equals(n.get("nodeCode"))).findFirst().orElseThrow(()->new BusinessException("替换目标不存在"));}
            else if(nodes.stream().anyMatch(n->candidateId.equals(n.get("sourceKey"))))continue;
            if(node==null){node=new LinkedHashMap<>(Map.of("nodeCode",UUID.randomUUID().toString(),"inputType","CHECK","required",0));nodes.add(node);}
            node.put("title",candidate.get("title"));node.put("instruction",candidate.get("acceptance"));node.put("acceptance",candidate.get("acceptance"));node.put("sourceAnchor",candidate.get("sourceAnchor"));node.put("sourceKey",candidateId);
        }
        content.put("extractionId",extractionId);validate(id,content,user,false);
        db.update("UPDATE compliance_template_workspace SET content=?,row_version=row_version+1,updated_by=? WHERE document_id=?",encode(content),user.getUserId(),id);
        return record(id,body,open(id,user));
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    @SuppressWarnings("unchecked") public Map<String,Object> review(long id,Map<String,Object> body,AuthUser user){
        lock(id,user);var w=open(id,user);if(((Number)w.get("rowVersion")).intValue()!=revision(body))throw new BusinessException(409,"编辑内容已变化，请刷新");
        var content=decode(encode(w.get("content")));var nodes=(List<Map<String,Object>>)content.get("nodes");
        String code=Objects.toString(body.get("nodeCode"),"");var node=nodes.stream().filter(n->code.equals(n.get("nodeCode"))).findFirst().orElseThrow(()->new BusinessException("事项不存在"));
        sources.verify(id,node,user);var source=sources.decode(Objects.toString(node.get("sourceAnchor"),""));source.put("needsReview",false);node.put("sourceAnchor",sources.encode(source));
        db.update("INSERT INTO compliance_template_review(document_id,node_code,content_hash,reviewed_by) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE content_hash=VALUES(content_hash),reviewed_by=VALUES(reviewed_by),reviewed_at=CURRENT_TIMESTAMP",id,code,sources.reviewHash(node),user.getUserId());
        db.update("UPDATE compliance_template_workspace SET content=?,row_version=row_version+1,updated_by=? WHERE document_id=?",encode(content),user.getUserId(),id);return open(id,user);
    }
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> confirmExtraction(long id,Map<String,Object> body,AuthUser user){
        lock(id,user);String extractionId=Objects.toString(body.get("extractionId"),"");sources.extraction(id,extractionId,user);
        if(!Boolean.TRUE.equals(body.get("confirmed")))throw new BusinessException("请确认已人工核对漏提取内容并补齐");
        db.update("UPDATE compliance_template_extraction SET confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP WHERE id=? AND document_id=?",user.getUserId(),extractionId,id);return Map.of("confirmed",true);
    }

}
