package com.jshz.expert.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStorageRegressionTest {
    @TempDir Path dir;
    @Test void savesHaveIndependentContentAndDeleteOnlyTheirOwnFile() {
        ProjectStorageService service = new ProjectStorageService(dir.toString());
        service.ensureDir();
        String first = service.saveRichText(1L, 2L, 3L, "first");
        String second = service.saveRichText(1L, 2L, 3L, "second");
        assertNotEquals(first, second);
        assertEquals("first", service.readRichText(first));
        service.deleteFile(first);
        assertNull(service.readRichText(first));
        assertEquals("second", service.readRichText(second));
    }
    @Test void rejectsTraversalAndPreservesLegacyRichTextPaths() throws Exception {
        ProjectStorageService service = new ProjectStorageService(dir.toString());
        service.ensureDir();
        Files.writeString(dir.resolve("_richtext/legacy.json"), "legacy");
        assertNotNull(service.resolveFile("_richtext/legacy.json"));
        assertNull(service.resolveFile("../_richtext/legacy.json"));
        assertNull(service.resolveFile(dir.resolve("_richtext/legacy.json").toString()));
    }
}
