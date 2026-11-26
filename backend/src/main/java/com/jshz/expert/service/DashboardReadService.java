package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import java.time.*;
import java.util.*;

/** Read-only, minimal-field projections. No application bodies or expert sensitive fields. */
@Service
public class DashboardReadService {
    private final JdbcTemplate db;
    private final PermissionService permissions;
    private final GlobalSearchModules searchers;
    private final AntPathMatcher paths = new AntPathMatcher();
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final List<String> MODULES = List.of("knowledge","expert","project","customer","supplier","announcement");
    private static final Map<String,String> API = Map.of("knowledge","/api/knowledge/docs","expert","/api/experts","project","/api/project","customer","/api/customer","supplier","/api/supplier/list","announcement","/api/announcement");
    public static final Map<String,String> LIST = Map.of("knowledge","/knowledge-planet/meeting-execution","expert","/experts","project","/project","customer","/knowledge-planet/customer-info","supplier","/supplier","announcement","/profile/announcements");

    /** queryTimeoutSeconds<=0 时不设超时（隔离测试用）；生产经 expert.dashboard.query-timeout-seconds 注入。 */
    public DashboardReadService(JdbcTemplate jdbc, PermissionService permissions, GlobalSearchModules searchers,
                                @Value("${expert.dashboard.query-timeout-seconds:3}") int queryTimeoutSeconds) {
        this.db = Objects.requireNonNull(jdbc);
        if (queryTimeoutSeconds > 0) this.db.setQueryTimeout(queryTimeoutSeconds);
        this.permissions = permissions; this.searchers = searchers;
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
    public Map<String,Object> approvals(AuthUser user) {
        // Same OR-sign/transfer predicate as ApplicationService.checkAssignee, plus audit permission.
        if (!user.hasPermission("approval:audit")) throw new BusinessException(403,"无审批权限");
        String from="FROM application WHERE status=0 AND (approver_snapshot IS NULL OR approver_snapshot='' OR JSON_CONTAINS(approver_snapshot, JSON_OBJECT('username', ?)) OR current_assignee=? OR extra_approver=?) AND (current_assignee IS NULL OR TRIM(current_assignee)='' OR current_assignee=? OR extra_approver=?)";
        List<Object> args=List.of(user.getUsername(),user.getUsername(),user.getUsername(),user.getUsername(),user.getUsername());
        List<Map<String,Object>> items=rows("SELECT id,application_type title,applicant,create_time time "+from+" ORDER BY create_time,id LIMIT 3",args.toArray());
        items.forEach(m->{m.put("target",target("/profile/approval/manage","applicationId",m.get("id")));m.put("timeType","申请时间");});
        long total=count(from,args);return section(Map.of("total",total,"items",items),total==0);
    }
    /** 工作台「审批统计」面板（2026-11-25 审批管理需求 D）：待审/滞留分级/今日已处理/近30天平均耗时，口径与待办面板一致。 */
    public Map<String,Object> approvalStats(AuthUser user) {
        if (!user.hasPermission("approval:audit")) throw new BusinessException(403,"无审批权限");
        String scope="a.status=0 AND (a.approver_snapshot IS NULL OR a.approver_snapshot='' OR JSON_CONTAINS(a.approver_snapshot, JSON_OBJECT('username', ?)) OR a.current_assignee=? OR a.extra_approver=?) AND (a.current_assignee IS NULL OR TRIM(a.current_assignee)='' OR a.current_assignee=? OR a.extra_approver=?)";
        List<Object> scopeArgs=List.of(user.getUsername(),user.getUsername(),user.getUsername(),user.getUsername());
        Map<String,Object> pending=db.queryForMap("SELECT COUNT(*) pendingTotal,"+
            "COALESCE(SUM(TIMESTAMPDIFF(HOUR,COALESCE(r.last_submit,a.create_time),NOW())>=24),0) overdue24,"+
            "COALESCE(SUM(TIMESTAMPDIFF(HOUR,COALESCE(r.last_submit,a.create_time),NOW())>=72),0) overdue72 "+
            "FROM application a LEFT JOIN (SELECT application_id,MAX(create_time) last_submit FROM approval_record "+
            "WHERE action IN ('SUBMIT','RESUBMIT') GROUP BY application_id) r ON r.application_id=a.id WHERE "+scope,scopeArgs.toArray());
        LocalDate today=LocalDate.now(ZONE);
        LocalDateTime todayStart=today.atStartOfDay();
        LocalDateTime monthAgo=todayStart.minusDays(30);
        Map<String,Object> done=db.queryForMap("SELECT COUNT(*) todayDone,"+
            "COALESCE(ROUND(AVG(CASE WHEN a.approve_time>=? THEN TIMESTAMPDIFF(MINUTE,a.create_time,a.approve_time)/60 END),1),0) avgHours30 "+
            "FROM application a WHERE a.approve_by=? AND a.approve_time IS NOT NULL AND a.status IN (1,2,4) AND a.approve_time<? AND a.approve_time>=?",
            todayStart.plusDays(1), user.getUsername(), todayStart.plusDays(1), monthAgo);
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("pendingTotal",number(pending.get("pendingTotal")));
        data.put("overdue24",number(pending.get("overdue24")));
        data.put("overdue72",number(pending.get("overdue72")));
        data.put("todayDone",number(done.get("todayDone")));
        data.put("avgHours30",((Number)done.get("avgHours30")).doubleValue());
        return section(data,false);
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
    /** 工作台「我的项目」：阶段取 project_phase 字典；成员范围=负责人/创建人/协助人（兼容历史执行人）；到期风险只按 endDate。
     * 2026-11-23：风险计数与分页均下推 SQL，不再全量拉取到内存（原实现随项目数线性变慢）。 */
    public Map<String,Map<String,Object>> projects(AuthUser user) {
        LocalDate today=LocalDate.now(ZONE);
        Long uid=user.getUserId();
        java.sql.Date todaySql=java.sql.Date.valueOf(today);
        // 2026-11-09 项目流程重塑后：配置快照与阶段历史已停用，不再据此推断完成状态。
        // 成员口径与 ProjectService.isProjectMember 对齐，避免创建人/协助人在工作台看不到自己的项目。
        String scope="FROM project p LEFT JOIN sys_user u ON u.id=p.owner_id "+
            "LEFT JOIN project_phase ph ON ph.id=p.phase_id "+
            "LEFT JOIN supplier_city sc ON sc.id=p.city_id "+
            "WHERE p.status NOT IN ('DELETED','CANCELED','COMPLETED') AND (p.owner_id=? OR p.creator_id=? "+
            "OR EXISTS(SELECT 1 FROM project_assistant a2 WHERE a2.project_id=p.id AND a2.user_id=?) "+
            "OR EXISTS(SELECT 1 FROM project_executor e WHERE e.project_id=p.id AND e.user_id=?))";
        Object[] scopeArgs={uid,uid,uid,uid};
        // 与 DashboardRules.warning("INCOMPLETE",end,today) 同口径的 SQL 表达式；unknownCompletionCount 保留为 0（兼容既有前端字段）。
        String warningExpr="CASE WHEN p.end_date IS NULL THEN 'NONE' WHEN p.end_date<? THEN 'OVERDUE' "+
            "WHEN p.end_date=? THEN 'DUE_TODAY' WHEN p.end_date<=? + INTERVAL 7 DAY THEN 'DUE_SOON' ELSE 'NONE' END";
        Object[] warnArgs={todaySql,todaySql,todaySql};
        // 注意占位符顺序：SELECT 聚合里的 4 个日期参数在前，scope WHERE 的 4 个 uid 参数在后。
        Map<String,Object> counts= db.queryForMap("SELECT COUNT(*) total,"+
            "COALESCE(SUM(p.end_date IS NOT NULL AND p.end_date<?),0) overdueCount,"+
            "COALESCE(SUM(p.end_date=?),0) dueTodayCount,"+
            "COALESCE(SUM(p.end_date>? AND p.end_date<=? + INTERVAL 7 DAY),0) dueSoonCount "+scope,
            concat(new Object[]{todaySql,todaySql,todaySql,todaySql},scopeArgs));
        List<Map<String,Object>> rows=db.queryForList(
            "SELECT p.id,p.name,p.category,p.team,p.status,p.start_date startDate,p.end_date endDate,"+
            "p.meeting_start_date meetingStartDate,p.meeting_end_date meetingEndDate,"+
            "p.owner_id ownerId,p.creator_id creatorId,u.username ownerName,ph.name phaseName,sc.name cityName,"+
            "EXISTS(SELECT 1 FROM project_executor x WHERE x.project_id=p.id AND x.user_id=?) isExecutor,"+
            "EXISTS(SELECT 1 FROM project_assistant a WHERE a.project_id=p.id AND a.user_id=?) isAssistant,"+
            warningExpr+" warning "+scope+" "+
            // 风险等级 OVERDUE(0)→DUE_TODAY(1)→DUE_SOON(2)→其他(3)，endDate 空值最后，与原内存排序一致。
            "ORDER BY CASE WHEN p.end_date IS NOT NULL AND p.end_date<? THEN 0 WHEN p.end_date=? THEN 1 "+
            "WHEN p.end_date>? AND p.end_date<=? + INTERVAL 7 DAY THEN 2 ELSE 3 END,p.end_date IS NULL,p.end_date,p.id LIMIT 6",
            concat(concat(new Object[]{uid,uid},warnArgs),concat(scopeArgs,new Object[]{todaySql,todaySql,todaySql,todaySql})));
        Map<String,Long> risks=new LinkedHashMap<>();
        risks.put("overdueCount",number(counts.get("overdueCount")));
        risks.put("dueTodayCount",number(counts.get("dueTodayCount")));
        risks.put("dueSoonCount",number(counts.get("dueSoonCount")));
        risks.put("unknownCompletionCount",0L);
        boolean empty=number(counts.get("total"))==0;
        List<Map<String,Object>> items=new ArrayList<>();
        for(Map<String,Object> raw:rows) {
            Map<String,Object> item=new LinkedHashMap<>();
            for(String k:List.of("id","name","category","team","status","startDate","endDate","meetingStartDate","meetingEndDate")) item.put(k,raw.get(k));
            item.put("teamLabel",teamLabel(raw.get("team")));
            item.put("cityName",text(raw.get("cityName")));
            item.put("phaseName",raw.get("phaseName")==null?"未设置阶段":String.valueOf(raw.get("phaseName")));
            item.put("ownerName",text(raw.get("ownerName")));
            List<String> relations=new ArrayList<>();
            if(raw.get("ownerId")!=null && number(raw.get("ownerId"))==uid) relations.add("负责人");
            if(raw.get("creatorId")!=null && number(raw.get("creatorId"))==uid) relations.add("创建人");
            if(isTrue(raw.get("isAssistant"))) relations.add("协助人");
            if(isTrue(raw.get("isExecutor"))) relations.add("执行人");
            item.put("myRelations",relations);
            item.put("warning",String.valueOf(raw.get("warning")));
            item.put("completionState","INCOMPLETE");
            item.put("target",DashboardRules.target("/project/"+raw.get("id"),Map.of("from","dashboard")));
            items.add(item);
        }
        Map<String,Object> riskData=new LinkedHashMap<>(risks);
        return Map.of("projectRisk",section(riskData,empty),"myProjects",section(Map.of("total",number(counts.get("total")),"items",items),empty));
    }
    private static Object[] concat(Object[] a,Object[] b) {
        Object[] out=Arrays.copyOf(a,a.length+b.length);
        System.arraycopy(b,0,out,a.length,b.length);
        return out;
    }
    private static boolean isTrue(Object v) { return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue()==1; }
    private static String teamLabel(Object team) {
        if(team==null) return "";
        return switch(String.valueOf(team)){case "DEPT1"->"项目一部";case "DEPT2"->"项目二部";case "DEPT3"->"项目三部";default->"";};
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
        // 2026-11-09：项目阶段名由 GlobalSearchModules 的 project_phase 关联直接给出，
        // 不再读取已停用的 project_config_snapshot，避免搜索结果出现「配置异常」。
        List<Map<String,Object>> items=rows("SELECT t.id,"+title+" title,"+extra+" extra "+from+" ORDER BY CASE WHEN LOWER("+title+")=? THEN 0 WHEN LOWER("+title+") LIKE ? ESCAPE '!' THEN 1 WHEN LOWER("+title+") LIKE ? ESCAPE '!' THEN 2 ELSE 3 END,"+updated+" DESC,t.id DESC LIMIT ? OFFSET ?",selectArgs.toArray());
        items.forEach(m->{
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
