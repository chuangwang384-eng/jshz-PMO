package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.*;
import lombok.RequiredArgsConstructor;
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
    private static final List<String> SECTIONS=List.of("myApplications","myApprovals","projectRisk","myProjects","favorites","notifications","announcements");

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
