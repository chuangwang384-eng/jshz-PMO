package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.entity.Project;
import com.jshz.expert.entity.ProjectBudget;
import com.jshz.expert.entity.ProjectBudgetItem;
import com.jshz.expert.entity.ProjectBudgetQuote;
import com.jshz.expert.entity.ProjectDoc;
import com.jshz.expert.mapper.ProjectBudgetMapper;
import com.jshz.expert.mapper.ProjectBudgetQuoteMapper;
import com.jshz.expert.mapper.ProjectDocMapper;
import com.jshz.expert.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
    public record Confirmation(String customerName, LocalDateTime confirmedAt, String remark, Long evidenceDocId) {}
    public record Snapshot(String remark, String estimateInputs, List<ProjectBudgetItem> items) {}
    public record Download(Path path, String fileName) {}

    public List<ProjectBudgetQuote> list(Long projectId, AuthUser user) {
        projects.requireProjectReadable(projects.requireProject(projectId), user);
        return quotes.selectList(new LambdaQueryWrapper<ProjectBudgetQuote>()
                .eq(ProjectBudgetQuote::getProjectId, projectId)
                .orderByDesc(ProjectBudgetQuote::getVersionNo));
    }

    @Transactional
    public ProjectBudgetQuote generate(Long projectId, Generate request, AuthUser user) {
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
        doc.setFilePath(storage.saveBudgetQuote(workbook(project.getName(), version, exported, note)));
        doc.setUploadBy(user.getUsername());
        doc.setIsDeleted(0);
        docs.insert(doc);
        quote.setDocId(doc.getId());
        quotes.insert(quote);
        return quote;
    }

    public Download download(Long projectId, Long versionId, AuthUser user) {
        projects.requireProjectReadable(projects.requireProject(projectId), user);
        ProjectBudgetQuote quote = requireQuote(projectId, versionId, false);
        ProjectDoc doc = docs.selectById(quote.getDocId());
        Path path = doc == null || !projectId.equals(doc.getProjectId()) ? null : storage.resolveFile(doc.getFilePath());
        if (path == null) {
            throw new BusinessException(404, "报价文件不存在");
        }
        return new Download(path, doc.getFileName());
    }

    @Transactional
    public ProjectBudgetQuote confirm(Long projectId, Long versionId, Confirmation request, AuthUser user) {
        projects.requireOperable(projects.requireProject(projectId), user);
        String customer = bounded(request.customerName(), 100, "客户确认人");
        if (customer == null || request.confirmedAt() == null) {
            throw new BusinessException("请填写客户确认人和确认时间");
        }
        String remark = bounded(request.remark(), 1000, "确认备注");
        ProjectBudgetQuote quote = requireQuote(projectId, versionId, true);
        if (quote.getRecordedAt() != null) {
            if (customer.equals(quote.getCustomerName()) && request.confirmedAt().equals(quote.getCustomerConfirmedAt())
                    && Objects.equals(remark, quote.getCustomerRemark())
                    && Objects.equals(request.evidenceDocId(), quote.getEvidenceDocId())) {
                return quote;
            }
            throw new BusinessException("这个版本已登记客户确认");
        }
        if (request.evidenceDocId() != null) {
            ProjectDoc evidence = docs.selectById(request.evidenceDocId());
            if (evidence == null || !projectId.equals(evidence.getProjectId())
                    || !Integer.valueOf(0).equals(evidence.getIsDeleted())
                    || evidence.getFilePath() == null || evidence.getFilePath().startsWith(ProjectStorageService.QUOTE_FILE_PREFIX)
                    || evidence.getFilePath().startsWith("_richtext/")) {
                throw new BusinessException("请选择本项目有效的确认凭证");
            }
        }
        quote.setCustomerName(customer);
        quote.setCustomerConfirmedAt(request.confirmedAt());
        quote.setCustomerRemark(remark);
        quote.setEvidenceDocId(request.evidenceDocId());
        quote.setRecordedBy(user.getUserId());
        quote.setRecordedAt(LocalDateTime.now());
        quotes.updateById(quote);
        return quote;
    }

    @Transactional
    public Map<String, Object> restore(Long projectId, Long versionId, AuthUser user) {
        Project project = projects.requireProject(projectId);
        projects.requireOperable(project, user);
        requireBudgetPhase(project);
        ProjectBudget budget = budgetService.lockBudget(projectId);
        requireConfirmed(budget, "已有编辑中的草稿，请先处理当前草稿");
        Snapshot snapshot;
        try {
            snapshot = json.readValue(requireQuote(projectId, versionId, false).getSnapshot(), Snapshot.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("无法读取该报价版本");
        }
        budgets.update(null, new LambdaUpdateWrapper<ProjectBudget>()
                .eq(ProjectBudget::getId, budget.getId())
                .set(ProjectBudget::getStatus, "DRAFT")
                .set(ProjectBudget::getRemark, snapshot.remark()));
        List<Map<String, Object>> rows = json.convertValue(snapshot.items(), new TypeReference<>() {});
        return budgetService.save(projectId, rows, snapshot.remark(), false,
                snapshot.estimateInputs() == null ? "{}" : snapshot.estimateInputs(), user);
    }

    private void requireBudgetPhase(Project project) {
        if (!ProjectService.PHASE_BUDGET_MAKING.equals(projects.phaseNameOf(project))) {
            throw new BusinessException("请在" + ProjectService.PHASE_BUDGET_MAKING + "阶段执行此操作");
        }
    }

    /** 报价生成/恢复的前置状态校验：预算单存在且已内部确认（文案按场景区分）。 */
    private void requireConfirmed(ProjectBudget budget, String message) {
        if (budget == null || !"CONFIRMED".equals(budget.getStatus())) {
            throw new BusinessException(message);
        }
    }

    private ProjectBudgetQuote requireQuote(Long projectId, Long versionId, boolean lock) {
        LambdaQueryWrapper<ProjectBudgetQuote> query = new LambdaQueryWrapper<ProjectBudgetQuote>()
                .eq(ProjectBudgetQuote::getId, versionId)
                .eq(ProjectBudgetQuote::getProjectId, projectId);
        ProjectBudgetQuote quote = quotes.selectOne(lock ? query.last("FOR UPDATE") : query);
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

    static byte[] workbook(String projectName, int version, List<ProjectBudgetItem> items, String note) {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("会议报价");
            sheet.createRow(0).createCell(0).setCellValue(projectName + " · 会议报价 V" + version);
            var header = sheet.createRow(2);
            String[] columns = {"费用名称", "数量", "单位", "单价（元）", "金额（元）"};
            for (int col = 0; col < columns.length; col++) {
                header.createCell(col).setCellValue(columns[col]);
                sheet.setColumnWidth(col, (col == 0 ? 38 : 18) * 256);
            }
            var money = book.createCellStyle();
            money.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
            int index = 3;
            BigDecimal total = BigDecimal.ZERO;
            for (ProjectBudgetItem item : items) {
                var row = sheet.createRow(index++);
                row.createCell(0).setCellValue(item.getName());
                row.createCell(1).setCellValue(item.getQuantity().doubleValue());
                row.createCell(2).setCellValue(item.getUnit());
                row.createCell(3).setCellValue(item.getUnitPrice().doubleValue());
                row.getCell(3).setCellStyle(money);
                row.createCell(4).setCellValue(item.getAmount().doubleValue());
                row.getCell(4).setCellStyle(money);
                total = total.add(item.getAmount());
            }
            var footer = sheet.createRow(index++);
            footer.createCell(0).setCellValue("合计");
            footer.createCell(4).setCellValue(total.doubleValue());
            footer.getCell(4).setCellStyle(money);
            if (note != null) sheet.createRow(index).createCell(0).setCellValue("对客说明：" + note);
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("生成报价文件失败");
        }
    }
}
