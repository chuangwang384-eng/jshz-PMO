package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardReadService reads;
    private final DashboardTasks tasks;
    private final JdbcTemplate db;
    private static final List<String> SECTIONS=List.of("myApplications","myApprovals","projectRisk","myProjects","favorites","notifications","announcements");
    private static final int LAYOUT_MAX_CHARS=20000;

    /** 当前用户工作台布局（未保存过返回 null，前端使用默认布局）。 */
    @GetMapping("/layout")
    public ApiResponse<Map<String,Object>> layout(@AuthenticationPrincipal AuthUser user) {
        reads.requireUser(user);
        List<String> rows=db.queryForList("SELECT layout FROM user_dashboard_layout WHERE user_id=?",String.class,user.getUserId());
        return ApiResponse.ok(Map.of("layout",rows.isEmpty()?"":rows.get(0)));
    }

    /** 保存当前用户工作台布局。layout 为栅格数组 JSON 文本（MySQL JSON 列自动校验），超限拒绝；登录即可、仅作用本人。 */
    @PutMapping("/layout")
    public ApiResponse<Void> saveLayout(@AuthenticationPrincipal AuthUser user,@RequestBody Map<String,Object> body) {
        reads.requireUser(user);
        Object layout=body==null?null:body.get("layout");
        if(!(layout instanceof String text) || text.isBlank()) throw new com.jshz.expert.common.BusinessException(400,"缺少布局内容");
        if(text.length()>LAYOUT_MAX_CHARS) throw new com.jshz.expert.common.BusinessException(400,"布局内容超出大小限制");
        try { new com.fasterxml.jackson.databind.ObjectMapper().readTree(text); } catch(Exception e) { throw new com.jshz.expert.common.BusinessException(400,"布局不是合法的 JSON"); }
        db.update("INSERT INTO user_dashboard_layout(user_id,layout) VALUES (?,?) ON DUPLICATE KEY UPDATE layout=VALUES(layout)",user.getUserId(),text);
        return ApiResponse.ok(null);
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String,Object>> overview(@AuthenticationPrincipal AuthUser user,@RequestParam(required=false) String sections) {
        reads.requireUser(user);
        List<String> selected=DashboardRules.select(sections,SECTIONS);
        Map<String,Supplier<Map<String,Object>>> jobs=new LinkedHashMap<>();
        if(selected.contains("myApplications") && reads.allowedPath(user,"/api/approval/my-applications")) jobs.put("myApplications",()->reads.applications(user));
        if(selected.contains("myApprovals") && user.hasPermission("approval:audit") && reads.allowedPath(user,"/api/approval/requests")) jobs.put("myApprovals",()->reads.approvals(user));
        if((selected.contains("myProjects")||selected.contains("projectRisk")) && reads.allowed(user,"project")) jobs.put("projects",()->new LinkedHashMap<>(reads.projects(user)));
        if(selected.contains("favorites")&&reads.allowed(user,"knowledge")&&reads.allowedPath(user,"/api/knowledge/favorites")) jobs.put("favorites",()->reads.favorites(user));
        if(selected.contains("notifications")&&reads.allowedPath(user,"/api/notifications")) jobs.put("notifications",()->reads.notifications(user));
        if(selected.contains("announcements")&&reads.allowed(user,"announcement")) jobs.put("announcements",()->reads.announcements(user));
        Map<String,Map<String,Object>> results=tasks.collect(jobs,1500);
        Map<String,Object> output=new LinkedHashMap<>(results);
        Map<String,Object> projects=results.get("projects");output.remove("projects");
        if(projects!=null) for(String key:List.of("projectRisk","myProjects")) if(selected.contains(key)) output.put(key,projects.getOrDefault(key,projects));
        List<String> capabilities=new ArrayList<>();
        for(String module:DashboardReadService.MODULES) if(reads.allowed(user,module)) capabilities.add(module);
        if(reads.allowedPath(user,"/api/approval/my-applications")) capabilities.add("myApplications");
        if(reads.allowedPath(user,"/api/wecom/directory/mine")) capabilities.add("directory");
        return ApiResponse.ok(Map.of("capabilities",capabilities,"generatedAt",OffsetDateTime.now(DashboardReadService.ZONE).toString(),"businessDate",LocalDate.now(DashboardReadService.ZONE).toString(),"sections",output));
    }
}
