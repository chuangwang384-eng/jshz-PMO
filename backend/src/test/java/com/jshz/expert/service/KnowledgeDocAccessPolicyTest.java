package com.jshz.expert.service;

import com.jshz.expert.entity.KnowledgeDocument;
import com.jshz.expert.security.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1-5：知识文档文件访问策略单元测试。
 * 纯函数测试，不启动 Spring、不连接数据库。
 */
class KnowledgeDocAccessPolicyTest {

    private KnowledgeDocument doc(int status) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(1L);
        doc.setStatus(status);
        return doc;
    }

    private AuthUser user(String... permissions) {
        return new AuthUser(1L, "fixture", false, false, Set.of(), Set.of(permissions));
    }

    @Test
    void onlineDocumentIsReadableByAnyLoggedInUser() {
        assertThat(KnowledgeDocumentService.canAccessFile(doc(1), user())).isTrue();
    }

    @Test
    void offlineDocumentIsHiddenFromNormalUser() {
        assertThat(KnowledgeDocumentService.canAccessFile(doc(0), user())).isFalse();
    }

    @Test
    void offlineDocumentIsStillReadableByMaintainer() {
        assertThat(KnowledgeDocumentService.canAccessFile(doc(0), user("knowledge:maintain"))).isTrue();
    }

    @Test
    void missingDocumentIsNeverReadable() {
        assertThat(KnowledgeDocumentService.canAccessFile(null, user("knowledge:maintain"))).isFalse();
    }
}
