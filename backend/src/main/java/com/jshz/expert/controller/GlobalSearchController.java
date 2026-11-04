package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/search/global")
@RequiredArgsConstructor
public class GlobalSearchController {
    private final DashboardReadService reads;
    private final DashboardTasks tasks;
    @GetMapping
    public ApiResponse<Map<String,Object>> search(@AuthenticationPrincipal AuthUser user,@RequestParam String keyword,@RequestParam(required=false) String modules) {
        reads.requireUser(user);String word=DashboardRules.keyword(keyword);
        Map<String,Supplier<Map<String,Object>>> jobs=new LinkedHashMap<>();
        for(String module:DashboardRules.select(modules,DashboardReadService.MODULES)) if(reads.allowed(user,module)) jobs.put(module,()->reads.search(user,module,word,1,5));
        Map<String,Map<String,Object>> results=tasks.collect(jobs,2000);
        List<Map<String,Object>> groups=new ArrayList<>();
        results.forEach((module,result)->{result=new LinkedHashMap<>(result);result.put("module",module);if("error".equals(result.get("status"))){result.put("total",null);result.put("hasMore",null);result.put("items",List.of());}groups.add(result);});
        return ApiResponse.ok(Map.of("keyword",word,"groups",groups));
    }
    @GetMapping("/module/{module}")
    public ApiResponse<Map<String,Object>> list(@AuthenticationPrincipal AuthUser user,@PathVariable String module,@RequestParam String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        reads.requireUser(user);
        if(!DashboardReadService.MODULES.contains(module)||page<1||page>100000||size<1||size>100) throw new BusinessException(400,"分页或模块无效");
        return ApiResponse.ok(reads.search(user,module,keyword,page,size));
    }
}
