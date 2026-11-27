package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectBudget;
import com.jshz.expert.entity.ProjectBudgetItem;
import com.jshz.expert.entity.ProjectBudgetQuote;
import com.jshz.expert.entity.ProjectBudgetStatus;
import com.jshz.expert.entity.ProjectDoc;
import com.jshz.expert.mapper.ProjectBudgetMapper;
import com.jshz.expert.mapper.ProjectBudgetQuoteMapper;
import com.jshz.expert.mapper.ProjectDocMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectBudgetQuoteService {
    private final ProjectBudgetQuoteMapper quotes;
    private final ProjectBudgetMapper budgets;
    private final ProjectDocMapper docs;
    private final ProjectService projects;
    private final ProjectBudgetService budgetService;
    private final ProjectStorageService storage;
    private final ObjectMapper json;

    public record Generate(String requestKey, String clientNote) {}
    public record Snapshot(String remark, String estimateInputs, List<ProjectBudgetItem> items) {}
    public record Download(Path path, String fileName) {}

    public List<ProjectBudgetQuote> list(Long projectId, AuthUser user) {
        projects.requireProjectReadable(projects.requireProject(projectId), user);
        return quotes.selectList(new LambdaQueryWrapper<ProjectBudgetQuote>()
                .eq(ProjectBudgetQuote::getProjectId, projectId)
                .orderByDesc(ProjectBudgetQuote::getVersionNo));
    }

    /**
     * 确认后归档对客报价版本（存档路径，前端确认报价自动触发）。
     * 2026-11-26 审查整改：返回是否实际归档（非预算阶段返回 false，由调用方记录警告与操作日志）；
     * 手动 generate/customer-confirmation/restore 端点已随「导出报价单即对客报价」终局决策下线。
     */
    @Transactional
    public boolean archiveConfirmed(Long projectId, AuthUser user, ProjectBudgetExportService exporter) {
        Project project = projects.requireProject(projectId);
        projects.requireOperable(project, user);
        if (!ProjectService.PHASE_BUDGET_MAKING.equals(projects.phaseNameOf(project))) {
            log.warn("非预算阶段重确认，未归档新报价 project={} by={}", projectId,
                    user == null ? null : user.getUserId());
            return false;
        }
        ProjectBudget budget = budgetService.lockBudget(projectId);
        requireConfirmed(budget, "请先完成内部确认");
        var identity = json.createArrayNode()
                .add("confirmed-project-quote").add(project.getPhaseId()).add(project.getName())
                .add(project.getClientCompany()).add(project.getClientName())
                .add(java.util.Objects.toString(project.getMeetingStartDate(), ""))
                .add(java.util.Objects.toString(project.getMeetingEndDate(), ""))
                .add(budget.getRemark()).add(budget.getServiceRate()).add(budget.getTaxRate());
        for (ProjectBudgetItem item : budgetService.lockedItems(budget.getId())) {
            identity.addArray().add(item.getSection()).add(item.getName()).add(item.getQuantity())
                    .add(item.getUnit()).add(item.getUnitPrice()).add(item.getRemark());
        }
        String key = UUID.nameUUIDFromBytes(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        generate(projectId, new Generate(key, null), user, exporter);
        return true;
    }

    private ProjectBudgetQuote generate(Long projectId, Generate request, AuthUser user,
                                        ProjectBudgetExportService exporter) {
        if (exporter == null) {
            throw new BusinessException("报价导出服务不可用");
        }
        Project project = projects.requireProject(projectId);
        projects.requireOperable(project, user);
        String requestKey;
        try {
            requestKey = UUID.fromString(request.requestKey()).toString();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("报价生成请求标识无效");
        }
        String note = bounded(request.clientNote(), 1000, "对客说明");
        ProjectBudget budget = budgetService.lockBudget(projectId);
        ProjectBudgetQuote existing = quotes.selectOne(new LambdaQueryWrapper<ProjectBudgetQuote>()
                .eq(ProjectBudgetQuote::getProjectId, projectId)
                .eq(ProjectBudgetQuote::getRequestKey, requestKey)
                .last("FOR UPDATE"));
        if (existing != null) {
            return existing;
        }
        requireBudgetPhase(project);
        requireConfirmed(budget, "请先完成内部确认");
        List<ProjectBudgetItem> items = budgetService.lockedItems(budget.getId());
        List<ProjectBudgetItem> exported = items.stream().filter(i -> i.getQuantity().signum() > 0).toList();
        // P1-①（2026-11-26）：归档前必须有数量大于零的明细，空明细不再凭 exporter 非空放行
        if (exported.isEmpty()) {
            throw new BusinessException("报价至少需要一条数量大于零的明细");
        }
        ProjectBudgetQuote latest = quotes.selectOne(new LambdaQueryWrapper<ProjectBudgetQuote>()
                .eq(ProjectBudgetQuote::getProjectId, projectId)
                .orderByDesc(ProjectBudgetQuote::getVersionNo)
                .last("LIMIT 1 FOR UPDATE"));
        int version = latest == null ? 1 : latest.getVersionNo() + 1;

        ProjectBudgetQuote quote = new ProjectBudgetQuote();
        quote.setProjectId(projectId);
        quote.setVersionNo(version);
        quote.setRequestKey(requestKey);
        quote.setClientNote(note);
        quote.setCreatedBy(user.getUserId());
        quote.setCreateTime(LocalDateTime.now());
        quote.setSnapshot(snapshotJson(new Snapshot(budget.getRemark(), budget.getEstimateInputs(), items)));

        ProjectDoc doc = new ProjectDoc();
        doc.setProjectId(projectId);
        doc.setPhaseId(project.getPhaseId());
        doc.setFileName("会议报价-V" + version + ".xlsx");
        byte[] content = exporter.export(projectId, "QUOTE", user);
        doc.setFilePath(storage.saveBudgetQuote(content));
        doc.setUploadBy(user.getUsername());
        doc.setIsDeleted(0);
        docs.insert(doc);
        quote.setDocId(doc.getId());
        quotes.insert(quote);
        return quote;
    }

    public Download download(Long projectId, Long versionId, AuthUser user) {
        projects.requireProjectReadable(projects.requireProject(projectId), user);
        ProjectBudgetQuote quote = requireQuote(projectId, versionId);
        ProjectDoc doc = docs.selectById(quote.getDocId());
        Path path = doc == null || !projectId.equals(doc.getProjectId()) ? null : storage.resolveFile(doc.getFilePath());
        if (path == null) {
            throw new BusinessException(404, "报价文件不存在");
        }
        return new Download(path, doc.getFileName());
    }

    private void requireBudgetPhase(Project project) {
        if (!ProjectService.PHASE_BUDGET_MAKING.equals(projects.phaseNameOf(project))) {
            throw new BusinessException("请在" + ProjectService.PHASE_BUDGET_MAKING + "阶段执行此操作");
        }
    }

    /** 报价生成的前置状态校验：预算单存在且已内部确认。 */
    private void requireConfirmed(ProjectBudget budget, String message) {
        if (budget == null || !ProjectBudgetStatus.CONFIRMED.code().equals(budget.getStatus())) {
            throw new BusinessException(message);
        }
    }

    private ProjectBudgetQuote requireQuote(Long projectId, Long versionId) {
        LambdaQueryWrapper<ProjectBudgetQuote> query = new LambdaQueryWrapper<ProjectBudgetQuote>()
                .eq(ProjectBudgetQuote::getId, versionId)
                .eq(ProjectBudgetQuote::getProjectId, projectId);
        ProjectBudgetQuote quote = quotes.selectOne(query);
        if (quote == null) throw new BusinessException(404, "报价版本不存在");
        return quote;
    }

    private static String bounded(String text, int length, String label) {
        if (text == null || text.isBlank()) return null;
        String value = text.strip();
        if (value.length() > length) throw new BusinessException(label + "过长");
        return value;
    }

    private String snapshotJson(Snapshot snapshot) {
        try {
            return json.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BusinessException("保存报价版本失败");
        }
    }
}
