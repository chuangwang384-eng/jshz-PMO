package com.jshz.expert.service;

import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.*;
import com.jshz.expert.mapper.*;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/** V4 resource policy. Snapshot ownership never bypasses source/project access. */
@Service
@RequiredArgsConstructor
public class ComplianceV4AccessService {
    private final KnowledgeDocumentMapper documents;
    private final ProjectMapper projects;
    private final ProjectService projectService;
    private final SysUserMapper users;
    private final PermissionService permissions;

    public AuthUser currentUser(Long id) {
        SysUser u = users.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId,id).last("FOR SHARE"));
        if (u == null || !Integer.valueOf(1).equals(u.getStatus())) return null;
        return new AuthUser(id, u.getUsername(), Boolean.TRUE.equals(u.getIsAdmin()), false,
                permissions.getUserRoles(id), permissions.getUserPermissions(id));
    }
    public KnowledgeDocument requireDocument(Long id, AuthUser user, boolean newUse) {
        KnowledgeDocument d = documents.selectOne(new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getId,id).last("FOR SHARE"));
        if (user == null || currentUser(user.getUserId()) == null || d == null || !"enterprise-compliance".equals(d.getCategory())
                || (newUse && !Integer.valueOf(1).equals(d.getStatus()))) {
            throw new BusinessException(404, "来源文档不可用");
        }
        return d;
    }
    public void requireDraftAuthor(Long documentId, AuthUser user) {
        user = user == null ? null : currentUser(user.getUserId());
        KnowledgeDocument d = requireDocument(documentId, user, true);
        if (!user.hasPermission("knowledge:maintain") && !Objects.equals(d.getUploadBy(), user.getUsername()))
            throw new BusinessException(403, "仅上传者或维护人可维护此文档规范");
    }
    public void requireProject(Long id, AuthUser user) {
        if (id == null) return;
        user = user == null ? null : currentUser(user.getUserId());
        if (user == null) throw new BusinessException(404, "关联项目不可用");
        Project p = projects.selectOne(new LambdaQueryWrapper<Project>().eq(Project::getId,id).last("FOR SHARE"));
        if (p == null || "DELETED".equals(p.getStatus())) throw new BusinessException(404, "关联项目不可用");
        projectService.requireProjectReadable(p, user);
    }
    public boolean canRead(ComplianceExecution e, AuthUser user) {
        if (e == null || user == null || !Objects.equals(e.getOwnerUserId(), user.getUserId())) return false;
        try { requireDocument(e.getSourceDocumentId(), user, false); requireProject(e.getProjectId(), user); return true; }
        catch (BusinessException denied) { return false; }
    }
    public boolean canRemind(ComplianceExecution e) {
        AuthUser u = currentUser(e.getOwnerUserId());
        return canRead(e, u) && (e.getProjectId() == null || "ACTIVE".equals(projects.selectById(e.getProjectId()).getStatus()));
    }
}
