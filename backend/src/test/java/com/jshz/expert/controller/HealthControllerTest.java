package com.jshz.expert.controller;

import com.jshz.expert.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * /health 字段契约单测（不启动 Spring 上下文）。
 *
 * <p>锁定三件事：
 * 1. buildTime / gitShort 由 build-info 注入后能正确回显（时间按 Asia/Shanghai 格式化）；
 * 2. build-info 缺失时两项返回 unknown，且不影响 status/version；
 * 3. 未替换的 Maven 占位符（${revision}）不得原样暴露给调用方。</p>
 */
class HealthControllerTest {

    @SuppressWarnings("unchecked")
    private HealthController controllerWith(BuildProperties buildProperties) {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(buildProperties);
        HealthController controller = new HealthController(provider);
        ReflectionTestUtils.setField(controller, "appVersion", "1.0.32");
        return controller;
    }

    private BuildProperties buildProperties() {
        Properties props = new Properties();
        props.setProperty("version", "1.0.32");
        props.setProperty("artifact", "expert-system");
        props.setProperty("name", "expert-system");
        props.setProperty("group", "com.jshz");
        // 2026-11-06T07:58:22Z == 北京时间 15:58:22
        props.setProperty("time", Instant.parse("2026-11-06T07:58:22Z").toString());
        props.setProperty("gitShort", "b221ad4");
        return new BuildProperties(props);
    }

    @Test
    void healthExposesBuildTimeAndGitShort() {
        ApiResponse<Map<String, Object>> response = controllerWith(buildProperties()).health();

        assertNotNull(response);
        Map<String, Object> data = response.getData();
        assertEquals("UP", data.get("status"));
        assertEquals("1.0.32", data.get("version"));
        assertEquals("2026-11-06 15:58:22", data.get("buildTime"));
        assertEquals("b221ad4", data.get("gitShort"));
    }

    @Test
    void healthFallsBackToUnknownWhenBuildInfoMissing() {
        ApiResponse<Map<String, Object>> response = controllerWith(null).health();

        Map<String, Object> data = response.getData();
        assertEquals("UP", data.get("status"));
        assertEquals("unknown", data.get("buildTime"));
        assertEquals("unknown", data.get("gitShort"));
    }

    @Test
    void unresolvedMavenPlaceholderIsNotExposed() {
        Properties props = new Properties();
        props.setProperty("version", "${revision}");
        props.setProperty("time", Instant.parse("2026-11-06T07:58:22Z").toString());
        props.setProperty("gitShort", "${gitShort}");

        Map<String, Object> data = controllerWith(new BuildProperties(props)).health().getData();

        assertTrue(!String.valueOf(data.get("gitShort")).contains("${"));
        assertEquals("unknown", data.get("gitShort"));
    }
}
