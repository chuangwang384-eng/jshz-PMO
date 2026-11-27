package com.jshz.expert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jshz.expert.common.KnowledgeParseStatus;
import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识文档解析任务崩溃恢复（2026-11-27 修复 RUNNING/PENDING 卡死）。
 *
 * <p>缺陷背景：解析任务只在上传/更新版本的 HTTP 请求里提交一次，应用重启后：</p>
 * <ul>
 *   <li>RUNNING 卡死：任务线程随进程消失，无任何写入者；
 *       且 {@code ComplianceService.checkUploadParsing} 对 RUNNING 永久 409，
 *       文档无法发起合规上传（死锁），只能重新上传新版本解套；</li>
 *   <li>PENDING 卡死：任务在提交前重启则永远无人触发。</li>
 * </ul>
 *
 * <p>恢复策略（应用就绪后执行一次）：</p>
 * <ol>
 *   <li>RUNNING → FAILED：消息注明"服务重启中断"，解除合规上传 409 死锁；</li>
 *   <li>PENDING（仅在架文档）→ 重新提交解析任务（复用 parseAsync 的 id+version 乐观校验），
 *       线程池拒绝时同步标 FAILED，与上传触发路径行为一致。</li>
 * </ol>
 *
 * <p>前提：本系统为单实例部署（NSSM 托管单进程）。多实例部署时本恢复会把其他实例
 * 正在执行的 RUNNING 误标为 FAILED（该实例任务完成时会按 id+version 重新写回 SUCCESS，
 * 状态短暂抖动但最终一致）；如演进为多实例，须改为按实例心跳/租约判定。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeParseRecoveryService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeParseTaskService parseTaskService;

    /**
     * 应用完全就绪（含线程池、数据源）后执行一轮恢复；执行体只做轻量 DB 操作，
     * PENDING 重触发经由 @Async 线程池立即返回，不阻塞启动完成信号。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        try {
            int recoveredRunning = recoverRunning();
            int retriggered = retriggerPending();
            if (recoveredRunning > 0 || retriggered > 0) {
                log.info("知识文档解析崩溃恢复完成：RUNNING置FAILED={}，PENDING重触发={}", recoveredRunning, retriggered);
            }
        } catch (Exception e) {
            // 恢复失败不阻断应用启动：与解析任务同口径降级，靠人工重新上传版本兜底
            log.warn("知识文档解析崩溃恢复执行失败（不影响启动）: err={}", e.getMessage());
        }
    }

    /**
     * 将全部 RUNNING 标记为 FAILED（启动时刻不存在本实例解析线程，见类注释单实例前提）。
     * 单条条件 UPDATE，无需事务包裹（经 this 自调用 @Transactional 也不会生效）。
     *
     * @return 恢复的文档数
     */
    public int recoverRunning() {
        return documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getParseStatus, KnowledgeParseStatus.RUNNING)
                .set(KnowledgeDocument::getParseStatus, KnowledgeParseStatus.FAILED)
                .set(KnowledgeDocument::getParseMessage, "服务重启导致解析中断，请重新上传版本重试")
                // 终态写入完成时间，与 updateStatus 的终态语义一致
                .set(KnowledgeDocument::getParseTime, LocalDateTime.now()));
    }

    /**
     * 重新触发在架且 PENDING 的文档解析（提交前重启导致任务丢失的场景）。
     *
     * @return 成功提交的文档数
     */
    public int retriggerPending() {
        List<KnowledgeDocument> pending = documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getParseStatus, KnowledgeParseStatus.PENDING)
                // 下架文档不再触发解析；重新上架/上传新版本时由正常链路处理
                .eq(KnowledgeDocument::getStatus, 1));
        int submitted = 0;
        for (KnowledgeDocument doc : pending) {
            if (submitQuietly(doc)) {
                submitted++;
            }
        }
        return submitted;
    }

    /**
     * 提交单个解析任务；线程池拒绝/提交异常时同步标 FAILED（口径与 KnowledgeController.triggerParse 一致）。
     *
     * @return 是否成功提交
     */
    private boolean submitQuietly(KnowledgeDocument doc) {
        try {
            // 跨 bean 调用经 Spring 代理，@Async("knowledgeParseExecutor") 正常生效（非 this 自调用）
            parseTaskService.parseAsync(doc.getId(), doc.getVersion(), doc.getFilePath(), doc.getFileName(),
                    doc.getFileType(), doc.getCategory(), null);
            return true;
        } catch (org.springframework.core.task.TaskRejectedException e) {
            log.warn("知识文档崩溃恢复：解析任务被拒绝 docId={}, version={}", doc.getId(), doc.getVersion());
            markFailedQuietly(doc, "解析任务排队已满，请稍后重新上传或更新");
            return false;
        } catch (Exception e) {
            log.warn("知识文档崩溃恢复：解析任务提交失败 docId={}, version={}, error={}",
                    doc.getId(), doc.getVersion(), e.getClass().getSimpleName());
            markFailedQuietly(doc, "解析任务提交失败，请稍后重新上传或更新");
            return false;
        }
    }

    /** 恢复路径的失败标记同样不允许反向影响启动/恢复主流程。 */
    private void markFailedQuietly(KnowledgeDocument doc, String message) {
        try {
            parseTaskService.markFailed(doc.getId(), doc.getVersion(), message);
        } catch (Exception e) {
            log.warn("知识文档崩溃恢复：失败状态写入异常 docId={}, error={}", doc.getId(), e.getClass().getSimpleName());
        }
    }
}
