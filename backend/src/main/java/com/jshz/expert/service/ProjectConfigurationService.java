package com.jshz.expert.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.ProjectPhase;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** PC-20261104: one transaction boundary for configuration, ranks and immutable project rules. */
@Service
@RequiredArgsConstructor
public class ProjectConfigurationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public void requireManager(AuthUser user) {
        if (user == null || (!user.isAdmin() && !user.hasPermission("project:manage")))
            throw new BusinessException(403, "需要项目配置管理权限");
    }

    public long lock() {
        return jdbc.queryForObject("SELECT epoch FROM project_config_state WHERE id=1 FOR UPDATE", Long.class);
    }
    public void changed() { jdbc.update("UPDATE project_config_state SET epoch=epoch+1 WHERE id=1"); }
    @Transactional(readOnly=true)
    public Map<String,Object> context(AuthUser user) {
        requireManager(user);
        Map<String,Object> common=new LinkedHashMap<>();
        for(Long id:jdbc.queryForList("SELECT id FROM project_phase WHERE is_active=1 ORDER BY sort,id",Long.class)) common.put(id.toString(),common(id));
        return Map.of("expectedEpoch",jdbc.queryForObject("SELECT epoch FROM project_config_state WHERE id=1",Long.class),"common",common,
                "types",jdbc.queryForList("SELECT id,name,sort FROM project_type ORDER BY sort,id"),
                "phaseUsage",jdbc.queryForList("SELECT p.id phaseId,(SELECT COUNT(*) FROM project_type_phase_rel r WHERE r.phase_id=p.id) typeCount,(SELECT COUNT(*) FROM project_config_snapshot s WHERE JSON_CONTAINS(JSON_EXTRACT(s.effective_config,'$.phases[*].phaseId'),CAST(p.id AS JSON))) projectCount FROM project_phase p"));
    }
    private void expect(Map<String,Object> body, long epoch) {
        if (number(body.get("expectedEpoch")) != epoch) throw new BusinessException(409, "配置已被他人修改，请刷新后重试；当前草稿已保留");
    }
    static long number(Object value) {
        try { return Long.parseLong(String.valueOf(value)); }
        catch (RuntimeException e) { throw new BusinessException("无效的整数参数"); }
    }
    private String name(Object value) {
        String v = value == null ? "" : value.toString().trim();
        if (v.isEmpty() || v.length()>50) throw new BusinessException("名称须为1～50个字符");
        return v;
    }
    private String templateType(Object value) {
        String type=String.valueOf(value);
        if(!List.of("FILE","EXPERT","SUPPLIER","BUDGET","RICH_TEXT").contains(type)) throw new BusinessException("不支持的资料要求类别，请先维护配置");
        return type;
    }
    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new BusinessException("配置序列化失败"); }
    }
    public Map<String,Object> decode(String value) {
        try { return json.readValue(value, new TypeReference<LinkedHashMap<String,Object>>(){}); }
        catch (Exception e) { throw new BusinessException("项目配置快照损坏，请联系管理员"); }
    }
    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> rows(Object value) {
        if (!(value instanceof List<?> list)) throw new BusinessException("阶段或材料清单格式错误");
        for (Object item : list) if (!(item instanceof Map<?,?>)) throw new BusinessException("清单格式错误");
        return (List<Map<String,Object>>) value;
    }
    public Map<String,Object> definition(long typeId) {
        List<Map<String,Object>> found=jdbc.queryForList("SELECT r.definition,r.schema_version FROM project_type_config_revision r JOIN project_type t ON t.active_config_revision_id=r.id AND t.id=r.type_id WHERE t.id=?", typeId);
        if (found.isEmpty()) throw new BusinessException("类型尚未完成配置迁移，暂不可使用");
        if(number(found.get(0).get("schema_version"))!=1) throw new BusinessException("不支持的类型配置版本，请联系管理员");
        return decode(found.get(0).get("definition").toString());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> edit(long typeId, AuthUser user) {
        requireManager(user);
        Map<String,Object> result=new LinkedHashMap<>(definition(typeId));
        List<Map<String,Object>> types=jdbc.queryForList("SELECT name,sort FROM project_type WHERE id=?",typeId);
        if(types.isEmpty()) throw new BusinessException("类型不存在");
        result.put("name",types.get(0).get("name")); result.put("position",types.get(0).get("sort"));
        result.put("expectedEpoch",jdbc.queryForObject("SELECT epoch FROM project_config_state WHERE id=1",Long.class));
        result.put("effective",effective(result));
        return result;
    }
    public List<Map<String,Object>> common(long phaseId) {
        return jdbc.queryForList("SELECT r.id,r.type_id typeId,r.phase_id phaseId,t.id templateId,t.name,t.template_type templateType,r.required,r.sort position FROM project_phase_template_rel r JOIN project_template t ON t.id=r.template_id WHERE r.type_id=0 AND r.phase_id=? ORDER BY r.sort,r.id",phaseId);
    }
    public Map<String,Object> effective(Map<String,Object> definition) {
        List<Map<String,Object>> phases=new ArrayList<>();
        for(Map<String,Object> original:rows(definition.get("phases"))) {
            Map<String,Object> phase=new LinkedHashMap<>(original);
            if(phase.get("phaseId")!=null) {
                List<String> names=jdbc.queryForList("SELECT name FROM project_phase WHERE id=?",String.class,phase.get("phaseId"));
                if(names.isEmpty()) throw new BusinessException("配置引用的阶段不存在");
                phase.put("name",names.get(0));
            }
            String mode=String.valueOf(phase.get("mode"));
            List<Map<String,Object>> requirements=switch(mode) {
                case "INHERIT" -> phase.get("phaseId")==null ? List.of() : common(number(phase.get("phaseId")));
                case "CUSTOM" -> rows(phase.get("requirements"));
                case "NONE" -> List.of();
                default -> throw new BusinessException("请选择阶段要求模式");
            };
            List<Map<String,Object>> resolved=new ArrayList<>();
            for(Map<String,Object> requirement:requirements) {
                List<Map<String,Object>> templates=jdbc.queryForList("SELECT name,template_type FROM project_template WHERE id=?",requirement.get("templateId"));
                if(templates.isEmpty()) throw new BusinessException("配置引用的材料要求不存在");
                Map<String,Object> r=new LinkedHashMap<>(requirement);r.put("name",templates.get(0).get("name"));r.put("templateType",templateType(templates.get(0).get("template_type")));resolved.add(r);
                Object scope="INHERIT".equals(mode)?0L:definition.get("typeId");
                List<Long> relationIds=scope==null||phase.get("phaseId")==null?List.of():jdbc.queryForList("SELECT id FROM project_phase_template_rel WHERE type_id=? AND phase_id=? AND template_id=?",Long.class,scope,phase.get("phaseId"),r.get("templateId"));
                r.put("id",relationIds.isEmpty()?null:relationIds.get(0));r.put("typeId",scope);r.put("phaseId",phase.get("phaseId"));
            }
            phase.put("requirements",resolved); phases.add(phase);
        }
        Map<String,Object> result=new LinkedHashMap<>(definition); result.put("phases",phases); return result;
    }
    private Map<String,Object> normalize(Map<String,Object> input, boolean persist) {
        List<Map<String,Object>> phases=rows(input.get("phases"));
        if(phases.isEmpty() || phases.size()>100) throw new BusinessException("流程必须包含1～100个阶段");
        List<Map<String,Object>> normalized=new ArrayList<>(); Set<String> keys=new HashSet<>();
        for(Map<String,Object> p:phases) {
            Long id=p.get("phaseId")==null?null:number(p.get("phaseId"));
            String phaseName;
            if(id==null) {
                phaseName=name(p.get("name"));
                if(jdbc.queryForObject("SELECT COUNT(*) FROM project_phase WHERE name=?",Long.class,phaseName)>0)
                    throw new BusinessException("阶段「"+phaseName+"」已存在，请选择已有阶段；停用阶段请先维护");
                if(persist) {
                    jdbc.update("INSERT INTO project_phase(name,sort,is_active) VALUES (?,(SELECT n FROM (SELECT COALESCE(MAX(sort),0)+1 n FROM project_phase) x),1)",phaseName);
                    id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
                }
            } else {
                List<Map<String,Object>> existing=jdbc.queryForList("SELECT name,is_active FROM project_phase WHERE id=?",id);
                if(existing.isEmpty() || number(existing.get(0).get("is_active"))!=1) throw new BusinessException("阶段不存在或已停用，请重新选择");
                phaseName=existing.get(0).get("name").toString();
            }
            // Use names too: two new stages must not survive preview as duplicate names.
            if(!keys.add(phaseName.toLowerCase(Locale.ROOT))) throw new BusinessException("同一流程不可重复添加阶段");
            String mode=String.valueOf(p.get("mode"));
            if(!List.of("INHERIT","CUSTOM","NONE").contains(mode)) throw new BusinessException("请确认每个阶段的要求模式");
            List<Map<String,Object>> requirements=new ArrayList<>(); Set<Long> templates=new HashSet<>();
            for(Map<String,Object> r:rows(p.getOrDefault("requirements",List.of()))) {
                long templateId=number(r.get("templateId"));
                if(!templates.add(templateId)) throw new BusinessException("阶段材料不可重复");
                List<Map<String,Object>> t=jdbc.queryForList("SELECT name,template_type FROM project_template WHERE id=?",templateId);
                if(t.isEmpty()) throw new BusinessException("资料要求模板不存在");
                long required=number(r.get("required"));
                if(required!=0 && required!=1) throw new BusinessException("必填标记无效");
                requirements.add(new LinkedHashMap<>(Map.of("templateId",templateId,"name",t.get(0).get("name"),"templateType",templateType(t.get(0).get("template_type")),"required",required,"position",requirements.size()+1)));
            }
            if(mode.equals("CUSTOM") && requirements.isEmpty()) throw new BusinessException("单独配置至少选择一项；无材料请选择无需提交材料");
            if(!mode.equals("CUSTOM") && !requirements.isEmpty()) throw new BusinessException("通用或无材料模式不可携带隐藏的专属要求");
            Map<String,Object> phase=new LinkedHashMap<>(); phase.put("phaseId",id); phase.put("name",phaseName);
            phase.put("position",normalized.size()+1); phase.put("mode",mode); phase.put("requirements",requirements);
            normalized.add(phase);
        }
        return new LinkedHashMap<>(Map.of("schemaVersion",1,"name",name(input.get("name")),"phases",normalized));
    }
    @Transactional
    public Map<String,Object> preview(Map<String,Object> input,AuthUser user) {
        requireManager(user); expect(input,lock()); return effective(normalize(input,false));
    }
    @Transactional
    public Map<String,Object> save(Long typeId,Map<String,Object> input,AuthUser user) {
        requireManager(user); long epoch=lock();
        String requestId=String.valueOf(input.getOrDefault("requestId",""));
        if(!requestId.matches("[a-zA-Z0-9-]{16,64}")) throw new BusinessException("保存请求标识无效");
        String payload=encode(canonical(input)), digest=hash(String.valueOf(typeId)+":"+payload);
        List<Map<String,Object>> previous=jdbc.queryForList("SELECT payload_hash,result FROM project_config_request WHERE request_id=?",requestId);
        if(!previous.isEmpty()) {
            if(!digest.equals(previous.get(0).get("payload_hash"))) throw new BusinessException(409,"请求标识已使用，请重新保存");
            return decode(previous.get(0).get("result").toString());
        }
        expect(input,epoch);
        Map<String,Object> def=normalize(input,true); String typeName=def.get("name").toString();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM project_type WHERE name=? AND id<>?",Long.class,typeName,typeId==null?-1L:typeId)>0) throw new BusinessException("类型名称已存在");
        if(typeId==null) {
            jdbc.update("INSERT INTO project_type(name,sort) VALUES (?,(SELECT n FROM (SELECT COALESCE(MAX(sort),0)+1 n FROM project_type) x))",typeName);
            typeId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        } else if(jdbc.update("UPDATE project_type SET name=? WHERE id=?",typeName,typeId)!=1) throw new BusinessException("类型不存在");
        def.put("typeId",typeId);
        long revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM project_type_config_revision WHERE type_id=?",Long.class,typeId);
        jdbc.update("INSERT INTO project_type_config_revision(type_id,revision,schema_version,definition,created_by) VALUES (?,?,1,?,?)",typeId,revision,encode(def),user.getUserId());
        jdbc.update("UPDATE project_type SET active_config_revision_id=LAST_INSERT_ID() WHERE id=?",typeId);
        // Keep legacy read projections synchronized. Their write endpoints are intentionally retired.
        jdbc.update("DELETE FROM project_type_phase_rel WHERE type_id=?",typeId);
        jdbc.update("DELETE FROM project_phase_template_rel WHERE type_id=?",typeId);
        for(Map<String,Object> p:rows(def.get("phases"))) {
            jdbc.update("INSERT INTO project_type_phase_rel(type_id,phase_id,sort) VALUES (?,?,?)",typeId,p.get("phaseId"),p.get("position"));
            for(Map<String,Object> r:rows(p.get("requirements"))) jdbc.update("INSERT INTO project_phase_template_rel(type_id,phase_id,template_id,required,sort) VALUES (?,?,?,?,?)",typeId,p.get("phaseId"),r.get("templateId"),r.get("required"),r.get("position"));
        }
        move(typeId,Math.toIntExact(number(input.get("position")))); changed();
        Map<String,Object> result=new LinkedHashMap<>(Map.of("id",typeId,"revision",revision,"expectedEpoch",epoch+1));
        audit(user,"保存项目类型配置「"+typeName+"」，版本 "+revision+"；已有项目不变");
        jdbc.update("INSERT INTO project_config_request(request_id,payload_hash,result) VALUES (?,?,?)",requestId,digest,encode(result));
        return result;
    }
    private Map<String,Object> legacyInput(long typeId,long epoch) {
        Map<String,Object> input=new LinkedHashMap<>(definition(typeId));
        input.put("expectedEpoch",epoch);input.put("requestId",UUID.randomUUID().toString());
        input.put("position",jdbc.queryForObject("SELECT sort FROM project_type WHERE id=?",Integer.class,typeId));
        return input;
    }
    /** Legacy requests become complete, individually atomic published definitions. */
    @Transactional
    public Map<String,Object> legacyCreate(String name,Integer position,AuthUser user) {
        requireManager(user);long epoch=lock();
        List<Map<String,Object>> phases=new ArrayList<>();
        for(Long id:jdbc.queryForList("SELECT id FROM project_phase WHERE is_active=1 ORDER BY sort,id",Long.class))
            phases.add(new LinkedHashMap<>(Map.of("phaseId",id,"mode","INHERIT","requirements",List.of())));
        long last=jdbc.queryForObject("SELECT COUNT(*)+1 FROM project_type",Long.class);
        Map<String,Object> input=new LinkedHashMap<>();input.put("name",name);input.put("position",position==null||position==0?last:position);
        input.put("phases",phases);input.put("expectedEpoch",epoch);input.put("requestId",UUID.randomUUID().toString());
        return save(null,input,user);
    }
    @Transactional
    public Map<String,Object> legacyUpdate(long typeId,String name,Integer position,AuthUser user) {
        requireManager(user);long epoch=lock();Map<String,Object> input=legacyInput(typeId,epoch);
        if(name!=null&&!name.isBlank())input.put("name",name);
        if(position!=null)input.put("position",position==0?1:position);
        return save(typeId,input,user);
    }
    @Transactional
    public void legacyPhases(long typeId,List<Long> ids,AuthUser user) {
        requireManager(user);long epoch=lock();
        if(ids==null||ids.isEmpty())throw new BusinessException("空流程不再代表全部阶段，请在新版向导中明确选择阶段");
        Map<String,Object> input=legacyInput(typeId,epoch);List<Map<String,Object>> current=rows(input.get("phases")),phases=new ArrayList<>();
        for(Long id:ids) {
            if(id==null)throw new BusinessException("阶段不能为空");
            Map<String,Object> existing=current.stream().filter(p->number(p.get("phaseId"))==id).findFirst().orElse(null);
            phases.add(existing==null?new LinkedHashMap<>(Map.of("phaseId",id,"mode","INHERIT","requirements",List.of())):existing);
        }
        input.put("phases",phases);save(typeId,input,user);
    }
    @Transactional
    public void legacyBindings(long typeId,long phaseId,List<Map<String,Object>> binds,AuthUser user) {
        requireManager(user);long epoch=lock();
        if(binds==null||binds.isEmpty())throw new BusinessException("空材料绑定含义不明确，请在新版向导选择沿用通用或无需材料");
        Map<String,Object> input=legacyInput(typeId,epoch);
        Map<String,Object> phase=rows(input.get("phases")).stream().filter(p->number(p.get("phaseId"))==phaseId).findFirst().orElseThrow(()->new BusinessException("阶段不属于此类型流程"));
        List<Map<String,Object>> sorted=new ArrayList<>(binds);
        sorted=sorted.stream().map(r->{Map<String,Object> copy=new LinkedHashMap<>(r);if(copy.get("required")==null)copy.put("required",0);return copy;}).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        sorted.sort(Comparator.comparingLong(r->r.get("sort")==null?0:number(r.get("sort"))));
        phase.put("mode","CUSTOM");phase.put("requirements",sorted);save(typeId,input,user);
    }
    public void move(long id,int position) {
        List<Long> ids=new ArrayList<>(jdbc.queryForList("SELECT id FROM project_type ORDER BY sort,id",Long.class));
        if(!ids.remove(id) || position<1 || position>ids.size()+1) throw new BusinessException("展示位置超出范围");
        ids.add(position-1,id); rank(ids);
    }
    public void compact() { rank(jdbc.queryForList("SELECT id FROM project_type ORDER BY sort,id",Long.class)); }
    private void rank(List<Long> ids) {
        long max=jdbc.queryForObject("SELECT COALESCE(MAX(sort),0) FROM project_type",Long.class);
        if(max+ids.size()+1>Integer.MAX_VALUE) throw new BusinessException("排序值超出安全范围");
        for(int i=0;i<ids.size();i++) jdbc.update("UPDATE project_type SET sort=? WHERE id=?",max+i+1,ids.get(i));
        for(int i=0;i<ids.size();i++) jdbc.update("UPDATE project_type SET sort=? WHERE id=?",i+1,ids.get(i));
    }
    @Transactional
    public void order(Map<String,Object> input,AuthUser user) {
        requireManager(user); expect(input,lock());
        if(!(input.get("ids") instanceof List<?> submitted)) throw new BusinessException("排序清单无效");
        List<Long> ids=submitted.stream().map(ProjectConfigurationService::number).toList();
        List<Long> all=jdbc.queryForList("SELECT id FROM project_type",Long.class);
        if(ids.size()!=all.size() || new HashSet<>(ids).size()!=all.size() || !new HashSet<>(ids).equals(new HashSet<>(all))) throw new BusinessException("排序必须包含全部类型且不可重复");
        rank(ids); changed(); audit(user,"调整项目类型展示顺序");
    }
    private void audit(AuthUser user,String content) {
        jdbc.update("INSERT INTO operation_log(user_id,username,action_type,action_desc,target_type) VALUES (?,?,?,?,?)",user.getUserId(),user.getUsername(),"UPDATE_PROJECT_CONFIG",content,"PROJECT");
    }
    public Map<String,Object> snapshot(long projectId) {
        List<Map<String,Object>> values=jdbc.queryForList("SELECT effective_config,schema_version,config_hash FROM project_config_snapshot WHERE project_id=?",projectId);
        if(values.isEmpty()) throw new BusinessException("项目缺少配置快照，请先完成迁移");
        if(number(values.get(0).get("schema_version"))!=1) throw new BusinessException("不支持的项目配置快照版本，请联系管理员");
        Map<String,Object> result=decode(values.get(0).get("effective_config").toString());
        if(!configHash(result).equals(values.get(0).get("config_hash"))) throw new BusinessException("项目配置快照校验失败，请联系管理员");
        return result;
    }
    public Map<String,Object> creation(long typeId) { lock(); return effective(definition(typeId)); }
    public void freeze(long projectId,long typeId,Map<String,Object> effective) {
        jdbc.update("INSERT INTO project_config_snapshot(project_id,source_revision_id,schema_version,effective_config,config_hash,origin) SELECT ?,active_config_revision_id,1,?,?, 'CREATED' FROM project_type WHERE id=?",projectId,encode(effective),configHash(effective),typeId);
    }
    public ProjectPhase phase(Map<String,Object> config,Long phaseId) {
        for(Map<String,Object> p:rows(config.get("phases"))) if(Objects.equals(number(p.get("phaseId")),phaseId)) {
            ProjectPhase phase=new ProjectPhase(); phase.setId(phaseId);phase.setName(p.get("name").toString());phase.setSort(Math.toIntExact(number(p.get("position"))));phase.setIsActive(1);return phase;
        }
        throw new BusinessException("阶段不属于此项目的固定流程");
    }
    public List<Map<String,Object>> display(Map<String,Object> config) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> p:rows(config.get("phases"))) {
            Map<String,Object> item=new LinkedHashMap<>(p);item.put("id",p.get("phaseId")); item.put("sort",p.get("position"));item.put("isActive",1);
            List<Map<String,Object>> templates=new ArrayList<>();
            for(Map<String,Object> r:rows(p.get("requirements"))) { Map<String,Object> t=new LinkedHashMap<>(r); t.put("phaseId",p.get("phaseId")); t.put("templateName",r.get("name"));t.put("sort",r.get("position"));t.put("fallback","INHERIT".equals(p.get("mode"))); templates.add(t); }
            item.put("templates",templates); result.add(item);
        }
        return result;
    }
    public List<Map<String,Object>> requirements(long projectId,long phaseId) {
        Map<String,Object> config=snapshot(projectId); phase(config,phaseId);
        return rows(rows(config.get("phases")).stream().filter(p->number(p.get("phaseId"))==phaseId).findFirst().orElseThrow().get("requirements"));
    }
    public void protectReference(String field,long id) {
        if(!List.of("phaseId","templateId").contains(field)) throw new IllegalArgumentException();
        String path="phaseId".equals(field)?"$.phases[*].phaseId":"$.phases[*].requirements[*].templateId";
        if(jdbc.queryForObject("SELECT COUNT(*) FROM project_config_snapshot WHERE JSON_CONTAINS(JSON_EXTRACT(effective_config,?),CAST(? AS JSON))",Long.class,path,String.valueOf(id))>0) throw new BusinessException("已有项目快照引用，不能删除；请保留历史配置");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM project_type_config_revision WHERE JSON_CONTAINS(JSON_EXTRACT(definition,?),CAST(? AS JSON))",Long.class,path,String.valueOf(id))>0) throw new BusinessException("历史配置版本引用，不能删除");
    }
    private String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private Object canonical(Object value) {
        if(value instanceof Map<?,?> map) {
            Map<String,Object> sorted=new TreeMap<>();
            map.forEach((k,v)->sorted.put(k.toString(),canonical(v))); return sorted;
        }
        if(value instanceof List<?> list) return list.stream().map(this::canonical).toList();
        return value;
    }
    public String configHash(Map<String,Object> value) { return hash(encode(canonical(value))); }
}
