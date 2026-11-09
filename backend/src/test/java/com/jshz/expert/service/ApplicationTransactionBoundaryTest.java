package com.jshz.expert.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H1 回归测试：审批流写入口必须具备事务边界（@Transactional + rollbackFor=Exception），
 * 且照片归档拆成「事务内只命名、事务提交后才落盘」两段，避免文件 IO 占用数据库事务。
 */
class ApplicationTransactionBoundaryTest {

    /** 审批流写入口：方法名 + 参数个数（重载方法逐一校验） */
    private record Entry(String name, int paramCount) {
    }

    private static final List<Entry> WRITE_ENTRIES = List.of(
            new Entry("updateConfig", 3),
            new Entry("submitRequest", 4),
            new Entry("submitCreateRequest", 3),
            new Entry("submitContractApproval", 5),
            new Entry("approve", 3),
            new Entry("approveModify", 3),
            new Entry("reject", 3),
            new Entry("returnRequest", 3),
            new Entry("resubmit", 2),
            new Entry("resubmit", 4),
            new Entry("withdraw", 2),
            new Entry("transfer", 3),
            new Entry("addApprover", 4),
            new Entry("saveConfigUsers", 2));

    @Test
    void approvalWriteEntriesDeclareRollbackTransactional() {
        for (Entry entry : WRITE_ENTRIES) {
            Method annotated = null;
            for (Method m : ApplicationService.class.getDeclaredMethods()) {
                if (m.getName().equals(entry.name()) && m.getParameterCount() == entry.paramCount()
                        && m.getAnnotation(Transactional.class) != null) {
                    annotated = m;
                    break;
                }
            }
            assertNotNull(annotated, entry.name() + "/" + entry.paramCount()
                    + " 缺少 @Transactional，审批流写入口事务边界未覆盖");
            Transactional tx = annotated.getAnnotation(Transactional.class);
            assertTrue(List.of(tx.rollbackFor()).contains(Exception.class),
                    entry.name() + " 未声明 rollbackFor = Exception.class，受检异常不会回滚");
        }
    }

    @Test
    void buildArchivedNameOnlyNamesAndArchiveMovesFile(@TempDir Path dir) throws Exception {
        Path storage = dir.resolve("storage");
        Path temp = dir.resolve("temp");
        PhotoStorageService service = new PhotoStorageService(storage.toString(), temp.toString());
        Files.writeString(temp.resolve("t1.png"), "x");

        String archived = service.buildArchivedName("t1.png", 12L, "photo");
        assertTrue(archived.startsWith("avatar_12_"), "归档名应遵循 avatar_<id>_<时间戳> 规则，实际=" + archived);
        assertTrue(Files.exists(temp.resolve("t1.png")), "事务内命名阶段不应移动临时文件");
        assertFalse(Files.exists(storage.resolve(archived)), "事务内命名阶段不应生成正式文件");

        service.archiveTempPhoto("t1.png", archived);
        assertTrue(Files.exists(storage.resolve(archived)), "提交后归档应生成正式文件");
        assertFalse(Files.exists(temp.resolve("t1.png")), "提交后归档应删除临时文件");
    }
}
