package com.jshz.expert.controller;

import com.jshz.expert.common.*;
import com.jshz.expert.entity.*;
import com.jshz.expert.security.AuthUser;
import com.jshz.expert.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardFocusController {
    private final DashboardReadService reads;
    private final ApplicationService applications;
    private final NotificationService notifications;
    private final KnowledgeDocumentService documents;
    private final AnnouncementService announcements;
    @GetMapping("/focus/{kind}/{id}")
    public ApiResponse<Object> focus(@AuthenticationPrincipal AuthUser user,@PathVariable String kind,@PathVariable long id) {
        reads.requireUser(user);
        if(id<1) throw new BusinessException(400,"记录编号无效");
        switch(kind) {
            case "application", "approval" -> {
                String path="approval".equals(kind)?"/api/approval/requests":"/api/approval/my-applications";
                if(!reads.allowedPath(user,path)) throw new BusinessException(403,"记录不可用");
                return ApiResponse.ok(applications.dashboardFocus(id,"approval".equals(kind),user));
            }
            case "notification" -> {
                if(!reads.allowedPath(user,"/api/notifications")) throw new BusinessException(403,"记录不可用");
                Notification n=notifications.getById(id);
                if(n==null||!Objects.equals(n.getUserId(),user.getUserId())) throw new BusinessException(404,"记录不可用");
                return ApiResponse.ok(n);
            }
            case "announcement" -> {
                reads.requireEffectiveAnnouncement(user,id);
                return ApiResponse.ok(announcements.detail(user,id));
            }
            case "knowledge" -> {
                if(!reads.allowed(user,"knowledge")) throw new BusinessException(403,"记录不可用");
                KnowledgeDocument d=documents.getById(id);
                if(d==null||!Objects.equals(d.getStatus(),1)) throw new BusinessException(404,"记录不可用");
                Map<String,Object> item=new LinkedHashMap<>();
                item.put("id",d.getId());item.put("title",d.getTitle());item.put("category",d.getCategory());
                item.put("filePath",d.getFilePath());item.put("fileType",d.getFileType());item.put("fileName",d.getFileName());
                return ApiResponse.ok(item);
            }
            default -> throw new BusinessException(400,"定位类型无效");
        }
    }
    @GetMapping("/my-approvals")
    public ApiResponse<Object> actionable(@AuthenticationPrincipal AuthUser user,@RequestParam(defaultValue="1") int page) {
        reads.requireUser(user);
        if(!user.hasPermission("approval:audit") || !reads.allowedPath(user,"/api/approval/requests")) throw new BusinessException(403,"无权限");
        if(page<1||page>100000) throw new BusinessException(400,"分页无效");
        return ApiResponse.ok(reads.approvals(user,20,(page-1)*20).get("data"));
    }
    @GetMapping("/my-projects")
    public ApiResponse<Object> mine(@AuthenticationPrincipal AuthUser user,@RequestParam(defaultValue="1") int page) {
        reads.requireUser(user);
        if(!reads.allowed(user,"project")) throw new BusinessException(403,"无权限");
        if(page<1||page>100000) throw new BusinessException(400,"分页无效");
        return ApiResponse.ok(reads.projects(user,20,(page-1)*20).get("myProjects").get("data"));
    }
}
