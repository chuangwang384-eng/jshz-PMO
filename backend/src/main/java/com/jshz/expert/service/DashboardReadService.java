package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import java.sql.Date;
import java.time.*;
import java.util.*;

/** Read-only, minimal-field projections. No application bodies or expert sensitive fields. */
@Service
public class DashboardReadService {
    private final JdbcTemplate db;
    private final ProjectConfigurationService configuration;
    private final PermissionService permissions;
    private final GlobalSearchModules searchers;
    private final AntPathMatcher paths = new AntPathMatcher();
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final List<String> MODULES = List.of("knowledge","expert","project","customer","supplier","announcement");
    private static final Map<String,String> API = Map.of("knowledge","/api/knowledge/docs","expert","/api/experts","project","/api/project","customer","/api/customer","supplier","/api/supplier/list","announcement","/api/announcement");
    public static final Map<String,String> LIST = Map.of("knowledge","/knowledge-planet/meeting-execution","expert","/experts","project","/project","customer","/knowledge-planet/customer-info","supplier","/supplier","announcement","/profile/announcements");

    public DashboardReadService(JdbcTemplate jdbc, ProjectConfigurationService configuration, PermissionService permissions, GlobalSearchModules searchers) {
        this.db = new JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource()));
        this.db.setQueryTimeout(1);
        this.configuration = configuration; this.permissions = permissions; this.searchers = searchers;
    }
    public void requireUser(AuthUser user) {
        if (user == null || user.getUserId() == null) throw new BusinessException(401,"未登录");
    }
    public boolean allowedPath(AuthUser user, String path) {
        requireUser(user);
        for (PermissionService.PermissionRule rule : permissions.getPermissionRules())
            if ("GET".equals(rule.method()) && paths.match(rule.path(), path)) return user.hasPermission(rule.code());
        return true;
    }
    public boolean allowed(AuthUser user, String module) {
        return API.containsKey(module) && allowedPath(user, API.get(module));
    }
    private List<Map<String,Object>> rows(String sql, Object... args) { return db.queryForList(sql,args); }
    private long count(String from, List<Object> args) { return db.queryForObject("SELECT COUNT(*) " + from, Long.class,args.toArray()); }
    static long number(Object v) { return ((Number)v).longValue(); }
    private static Object text(Object value) { return value == null ? "" : value; }
    private static Map<String,Object> target(String path, String key, Object id) {
        return DashboardRules.target(path, Map.of(key, String.valueOf(id),"from","dashboard"));
    }
    public Map<String,Object> section(Map<String,Object> data, boolean empty) {
        return Map.of("status",empty?"empty":"success","data",data,"updatedAt",OffsetDateTime.now(ZONE).toString());
    }
    public Map<String,Object> favorites(AuthUser user) {
        String from="FROM knowledge_favorite f JOIN knowledge_document d ON d.id=f.doc_id WHERE f.user_id=? AND d.status=1";
        List<Map<String,Object>> items=rows("SELECT d.id docId,d.title,d.category,f.create_time favoriteTime "+from+" ORDER BY f.create_time DESC,f.id DESC LIMIT 8",user.getUserId());
        items.forEach(m->m.put("target",target(knowledgePath(m.get("category")),"docId",m.get("docId"))));
        long total=count(from,List.of(user.getUserId()));
        return section(Map.of("total",total,"items",items),total==0);
    }
    private static String knowledgePath(Object category) {
        String c=String.valueOf(category);
        return "/knowledge-planet/"+(List.of("meeting-execution","association-compliance","enterprise-compliance").contains(c)?c:"meeting-execution");
    }
    public Map<String,Object> applications(AuthUser user) {
        String mine="FROM application WHERE (applicant_id=? OR applicant=?)";
        List<Object> args=List.of(user.getUserId(),user.getUsername());
        List<Map<String,Object>> latest=rows("SELECT id,application_type title,status,create_time time "+mine+" ORDER BY create_time DESC,id DESC LIMIT 3",args.toArray());
        List<Map<String,Object>> returned=rows("SELECT id,application_type title,status,create_time time "+mine+" AND status=4 ORDER BY create_time,id LIMIT 3",args.toArray());
        for(List<Map<String,Object>> list:List.of(latest,returned)) list.forEach(m->{m.put("target",target("/profile/my-approvals","applicationId",m.get("id")));m.put("timeType","申请时间");});
        return section(Map.of("pendingOthersCount",count(mine+" AND status=0",args),"returnedCount",count(mine+" AND status=4",args),"latestItems",latest,"returnedItems",returned),latest.isEmpty());
    }
    public Map<String,Object> approvals(AuthUser user) { return approvals(user,3,0); }
    public Map<String,Object> approvals(AuthUser user,int limit,int offset) {
        // Same OR-sign/transfer predicate as ApplicationService.checkAssignee, plus audit permission.
        if (!user.hasPermission("approval:audit")) throw new BusinessException(403,"无审批权限");
        String from="FROM application WHERE status=0 AND (approver_snapshot IS NULL OR approver_snapshot='' OR JSON_CONTAINS(approver_snapshot, JSON_OBJECT('username', ?)) OR current_assignee=? OR extra_approver=?) AND (current_assignee IS NULL OR TRIM(current_assignee)='' OR current_assignee=? OR extra_approver=?)";
        List<Object> args=List.of(user.getUsername(),user.getUsername(),user.getUsername(),user.getUsername(),user.getUsername());
        List<Map<String,Object>> items=rows("SELECT id,application_type title,applicant,create_time time "+from+" ORDER BY create_time,id LIMIT "+limit+" OFFSET "+offset,args.toArray());
        items.forEach(m->{m.put("target",target("/profile/approval/manage","applicationId",m.get("id")));m.put("timeType","申请时间");});
        long total=count(from,args);return section(Map.of("total",total,"items",items),total==0);
    }
    public Map<String,Object> notifications(AuthUser user) {
        String from="FROM notification WHERE user_id=? AND is_read=0";
        List<Map<String,Object>> items=rows("SELECT id,title,create_time time "+from+" ORDER BY create_time DESC,id DESC LIMIT 3",user.getUserId());
        items.forEach(m->m.put("target",target("/profile/notifications","notificationId",m.get("id"))));
        long total=count(from,List.of(user.getUserId()));return section(Map.of("unreadCount",total,"items",items),total==0);
    }
    private String effective() { return "a.status='ONLINE' AND a.effective_time<=? AND (a.expire_time IS NULL OR a.expire_time>?)"; }
    public void requireEffectiveAnnouncement(AuthUser user,long id) {
        if(!allowed(user,"announcement") || !allowedPath(user,"/api/announcement/"+id)) throw new BusinessException(403,"记录不可用");
        LocalDateTime now=LocalDateTime.now(ZONE);
        if(count("FROM announcement a WHERE a.id=? AND "+effective(),List.of(id,now,now))!=1) throw new BusinessException(404,"记录不可用");
    }
    public Map<String,Object> announcements(AuthUser user) {
        LocalDateTime now=LocalDateTime.now(ZONE);
        String from="FROM announcement a WHERE "+effective();
        List<Map<String,Object>> items=rows("SELECT a.id,a.title,a.create_time publishTime,EXISTS(SELECT 1 FROM announcement_read r WHERE r.announcement_id=a.id AND r.user_id=?) AS `read` "+from+" ORDER BY a.is_top DESC,a.create_time DESC,a.id DESC LIMIT 3",user.getUserId(),now,now);
        items.forEach(m->m.put("target",DashboardRules.target("/profile/announcements/"+m.get("id"),Map.of("from","dashboard"))));
        long total=count(from,List.of(now,now));return section(Map.of("total",total,"items",items),total==0);
    }
    public Map<String,Map<String,Object>> projects(AuthUser user) { return projects(user,6,0); }
    public Map<String,Map<String,Object>> projects(AuthUser user,int limit,int offset) {
        LocalDate today=LocalDate.now(ZONE);
        List<Map<String,Object>> all=rows("SELECT p.id,p.name,p.owner_id ownerId,p.phase_id phaseId,p.status,p.end_date endDate,EXISTS(SELECT 1 FROM project_executor x WHERE x.project_id=p.id AND x.user_id=?) isExecutor,u.username ownerName,s.origin,s.schema_version schemaVersion,s.effective_config config,s.config_hash configHash FROM project p LEFT JOIN sys_user u ON u.id=p.owner_id LEFT JOIN project_config_snapshot s ON s.project_id=p.id WHERE p.status NOT IN ('DELETED','CANCELED') AND (p.owner_id=? OR EXISTS(SELECT 1 FROM project_executor e WHERE e.project_id=p.id AND e.user_id=?))",user.getUserId(),user.getUserId(),user.getUserId());
        // One batch for stage history; no per-card DB query.
        Map<Long,Map<String,Object>> histories=new HashMap<>();
        if(!all.isEmpty()) {
            String slots=String.join(",",Collections.nCopies(all.size(),"?"));
            List<Map<String,Object>> history=rows("SELECT h.project_id projectId,h.action_type actionType,h.from_phase_id fromPhaseId,h.to_phase_id toPhaseId,h.create_time time FROM project_phase_history h WHERE h.project_id IN ("+slots+") AND NOT EXISTS(SELECT 1 FROM project_phase_history n WHERE n.project_id=h.project_id AND (n.create_time>h.create_time OR (n.create_time=h.create_time AND n.id>h.id)))",all.stream().map(m->m.get("id")).toArray());
            history.forEach(h->histories.put(number(h.get("projectId")),h));
        }
        Map<String,Long> risks=new LinkedHashMap<>();
        for(String key:List.of("overdueCount","dueTodayCount","dueSoonCount","unknownCompletionCount")) risks.put(key,0L);
        List<Map<String,Object>> items=new ArrayList<>();
        for(Map<String,Object> raw:all) {
            String state="UNKNOWN", phase="配置异常";
            try {
                if(raw.get("schemaVersion")==null || number(raw.get("schemaVersion"))!=1) throw new IllegalArgumentException();
                Map<String,Object> config=configuration.decode(raw.get("config").toString());
                if(!configuration.configHash(config).equals(raw.get("configHash"))) throw new IllegalArgumentException();
                phase=configuration.phase(config,number(raw.get("phaseId"))).getName();
                state=DashboardRules.completion(String.valueOf(raw.get("origin")),config,number(raw.get("phaseId")));
                Map<String,Object> history=histories.get(number(raw.get("id")));
                if(history!=null) {
                    history.put("fromPhaseName",configuration.phase(config,number(history.get("fromPhaseId"))).getName());
                    history.put("toPhaseName",configuration.phase(config,number(history.get("toPhaseId"))).getName());
                }
            } catch(RuntimeException ex) { /* Fail closed: do not infer lifecycle from corrupt config. */ }
            if("COMPLETED".equals(state)) continue;
            LocalDate end=raw.get("endDate")==null?null:((Date)raw.get("endDate")).toLocalDate();
            String warning=DashboardRules.warning(state,end,today);
            String counter=switch(warning){case "OVERDUE"->"overdueCount";case "DUE_TODAY"->"dueTodayCount";case "DUE_SOON"->"dueSoonCount";case "UNKNOWN_COMPLETION"->"unknownCompletionCount";default->null;};
            if(counter!=null) risks.put(counter,risks.get(counter)+1);
            Map<String,Object> item=new LinkedHashMap<>();
            for(String k:List.of("id","name","status","endDate")) item.put(k,raw.get(k));
            item.put("ownerName",text(raw.get("ownerName")));
            List<String> relations=new ArrayList<>();
            if(raw.get("ownerId")!=null && number(raw.get("ownerId"))==user.getUserId()) relations.add("负责人");
            if(Boolean.TRUE.equals(raw.get("isExecutor")) || raw.get("isExecutor") instanceof Number n && n.intValue()==1) relations.add("执行人");
            item.put("myRelations",relations);
            item.put("phaseName",phase);item.put("completionState",state);item.put("warning",warning);item.put("lastPhaseChange",histories.get(number(raw.get("id"))));
            item.put("target",DashboardRules.target("/project/"+raw.get("id"),Map.of("from","dashboard")));items.add(item);
        }
        List<String> rank=List.of("OVERDUE","DUE_TODAY","DUE_SOON","NONE","UNKNOWN_COMPLETION");
        items.sort(Comparator.<Map<String,Object>>comparingInt(m->rank.indexOf(m.get("warning"))).thenComparing(m->m.get("endDate")==null?null:m.get("endDate").toString(),Comparator.nullsLast(Comparator.naturalOrder())).thenComparingLong(m->number(m.get("id"))));
        return Map.of("projectRisk",section(new LinkedHashMap<>(risks),items.isEmpty()),"myProjects",section(Map.of("total",items.size(),"items",items.stream().skip(offset).limit(limit).toList()),items.isEmpty()));
    }

    /** Query specification shared by the preview and full-list endpoint. SQL fragments are code-owned. */
    public Map<String,Object> search(AuthUser user,String module,String raw,int page,int size) {
        if(!allowed(user,module)) throw new BusinessException(403,"无权限");
        String word=DashboardRules.keyword(raw);
        ModuleSearcher.QuerySpec spec=searchers.query(module,user,LocalDateTime.now(ZONE));
        String title=spec.title(),extra=spec.extra(),table=spec.table(),condition=spec.condition(),updated=spec.updated();
        List<String> fields=spec.fields();List<Object> args=new ArrayList<>(spec.args());
        String pattern="%"+DashboardRules.literalLike(word.toLowerCase(Locale.ROOT))+"%";
        String predicates=String.join(" OR ",fields.stream().map(f->"LOWER("+f+") LIKE ? ESCAPE '!' ").toList());
        fields.forEach(f->args.add(pattern));
        String from="FROM "+table+" WHERE "+condition+" AND ("+predicates+")";
        long total=count(from,args);
        List<Object> selectArgs=new ArrayList<>(args);
        selectArgs.add(word.toLowerCase(Locale.ROOT));selectArgs.add(DashboardRules.literalLike(word.toLowerCase(Locale.ROOT))+"%");selectArgs.add(pattern);
        selectArgs.add(size);selectArgs.add((page-1)*size);
        String metadata="project".equals(module)?",t.phase_id phaseId,snap.effective_config config,snap.config_hash configHash,snap.schema_version schemaVersion":"";
        List<Map<String,Object>> items=rows("SELECT t.id,"+title+" title,"+extra+" extra "+metadata+" "+from+" ORDER BY CASE WHEN LOWER("+title+")=? THEN 0 WHEN LOWER("+title+") LIKE ? ESCAPE '!' THEN 1 WHEN LOWER("+title+") LIKE ? ESCAPE '!' THEN 2 ELSE 3 END,"+updated+" DESC,t.id DESC LIMIT ? OFFSET ?",selectArgs.toArray());
        items.forEach(m->{
            if("project".equals(module)) {
                try {
                    Map<String,Object> config=configuration.decode(m.get("config").toString());
                    if(number(m.get("schemaVersion"))!=1 || !configuration.configHash(config).equals(m.get("configHash"))) throw new IllegalArgumentException();
                    m.put("extra",configuration.phase(config,number(m.get("phaseId"))).getName());
                } catch(RuntimeException ex) { m.put("extra","配置异常"); }
                for(String field:List.of("config","configHash","schemaVersion","phaseId")) m.remove(field);
            }
            String path=LIST.get(module);Object id=m.get("id");
            Map<String,Object> dest=switch(module){
                case "knowledge"->target(knowledgePath(m.get("extra")),"docId",id);
                case "expert"->target(path,"expertId",id);
                case "customer"->DashboardRules.target("/knowledge-planet/customer-detail/"+id,Map.of("from","dashboard"));
                default->DashboardRules.target(path+"/"+id,Map.of("from","dashboard"));};
            m.put("target",dest);m.put("extra",text(m.get("extra")));
        });
        Map<String,Object> result=new LinkedHashMap<>();result.put("module",module);result.put("moduleName",Map.of("knowledge","知识文档","expert","专家","project","项目","customer","客户","supplier","供应商","announcement","公告").get(module));result.put("status",total==0?"empty":"success");result.put("total",total);result.put("items",items);result.put("hasMore",(long)page*size<total);
        result.put("allTarget",DashboardRules.target(LIST.get(module),Map.of("keyword",word,"searchMode","global")));return result;
    }
}
