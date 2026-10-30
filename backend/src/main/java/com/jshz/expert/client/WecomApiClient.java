package com.jshz.expert.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.config.WecomProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业微信通讯录同步 API 客户端（纯服务端出站 HTTP，不需要域名/备案/入站端口）。
 *
 * <p>端点（官方文档，实施时以实测响应为准）：</p>
 * <ul>
 *   <li>GET /cgi-bin/gettoken?corpid=&amp;corpsecret= —— access_token，7200s 有效，进程内缓存并提前刷新</li>
 *   <li>POST /cgi-bin/user/list_id —— 成员 ID 列表（cursor 分页），仅通讯录同步 secret 可调用</li>
 *   <li>POST /cgi-bin/department/list_id —— 部门 ID 列表（cursor 分页，不可用时退回 department/list）</li>
 *   <li>GET /cgi-bin/user/get?userid= —— 成员详情（name/mobile/department/status）</li>
 * </ul>
 *
 * <p>日志纪律：任何日志不得出现 secret/access_token；手机号一律 138****1234 脱敏。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WecomApiClient {

    private static final String BASE_URL = "https://qyapi.weixin.qq.com/cgi-bin";
    private static final int ERRCODE_OK = 0;
    /** token 无效/过期 */
    private static final int ERR_TOKEN_INVALID = 40014;
    /** access_token 过期 */
    private static final int ERR_TOKEN_EXPIRED = 42001;

    private final WecomProperties wecomProperties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** token 缓存（volatile + 方法级 synchronized 防并发重复获取） */
    private volatile String cachedToken;
    private volatile long tokenExpireAtMillis;

    /** 企微部门信息（快照用） */
    public static class DeptInfo {
        public long deptId;
        public String name;
        public Long parentId;
        public int order;
    }

    /** 企微成员 ID 引用（含所属部门） */
    public static class MemberRef {
        public String userId;
        public List<Long> deptIds = new ArrayList<>();
    }

    /** 企微成员详情（user/get） */
    public static class MemberDetail {
        public String userId;
        public String name;
        public String mobile;
        public List<Long> departments = new ArrayList<>();
        public int status = 1; // 1 已激活 / 2 已禁用 / 4 未激活 / 5 退出企业
    }

    /**
     * 获取 access_token（缓存 + 提前 5 分钟刷新）。
     */
    public synchronized String getAccessToken() {
        long aheadMillis = wecomProperties.getTokenRefreshAheadSeconds() * 1000L;
        if (cachedToken != null && System.currentTimeMillis() + aheadMillis < tokenExpireAtMillis) {
            return cachedToken;
        }
        if (!wecomProperties.hasCredential()) {
            throw new BusinessException("企微凭证未配置：请通过环境变量注入 WECOM_CORP_ID / WECOM_CONTACT_SECRET");
        }
        Map<String, Object> resp = doGet("/gettoken",
                Map.of("corpid", wecomProperties.getCorpId(),
                        "corpsecret", wecomProperties.getContactSecret()),
                false);
        checkErrcode(resp, "/gettoken");
        String token = asText(resp, "access_token");
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("企微 access_token 获取失败：响应缺少 access_token");
        }
        long expiresIn = asLong(resp, "expires_in", 7200L);
        cachedToken = token;
        tokenExpireAtMillis = System.currentTimeMillis() + expiresIn * 1000L;
        log.info("企微 access_token 获取成功（expires_in={}s，已缓存）", expiresIn);
        return cachedToken;
    }

    /**
     * 拉取部门全量：优先 POST department/list_id（cursor 分页），
     * 若返回错误（该 secret 不可用）则退回 GET department/list。
     */
    public List<DeptInfo> listDepartments() {
        List<DeptInfo> result = new ArrayList<>();
        Map<String, Object> resp = postJson("/department/list_id", Map.of("cursor", ""));
        if (errcodeOf(resp) == ERRCODE_OK && resp.containsKey("department")) {
            collectDeptPage(resp, result);
            String nextCursor = asText(resp, "next_cursor");
            int guard = 0;
            while (StringUtils.hasText(nextCursor) && guard++ < 100) {
                resp = postJson("/department/list_id", Map.of("cursor", nextCursor));
                checkErrcode(resp, "/department/list_id");
                collectDeptPage(resp, result);
                nextCursor = asText(resp, "next_cursor");
            }
            log.info("企微部门拉取完成（list_id）：{} 个", result.size());
            return result;
        }
        // 退回旧接口 GET department/list
        Map<String, Object> legacy = getJson("/department/list", Map.of(), true);
        checkErrcode(legacy, "/department/list");
        JsonNode arr = legacy.get("department") instanceof JsonNode n ? n : objectMapper.valueToTree(legacy.get("department"));
        if (arr != null && arr.isArray()) {
            for (JsonNode node : arr) {
                DeptInfo d = new DeptInfo();
                d.deptId = node.path("id").asLong();
                d.name = node.path("name").asText();
                d.parentId = node.path("parentid").isNull() ? null : node.path("parentid").asLong();
                d.order = node.path("order").asInt(0);
                result.add(d);
            }
        }
        log.info("企微部门拉取完成（department/list 退回）：{} 个", result.size());
        return result;
    }

    private void collectDeptPage(Map<String, Object> resp, List<DeptInfo> result) {
        JsonNode arr = resp.get("department") instanceof JsonNode n ? n : objectMapper.valueToTree(resp.get("department"));
        if (arr != null && arr.isArray()) {
            for (JsonNode node : arr) {
                DeptInfo d = new DeptInfo();
                d.deptId = node.path("id").asLong();
                d.name = node.path("name").asText();
                d.parentId = node.path("parentid").isNull() ? null : node.path("parentid").asLong();
                d.order = node.path("order").asInt(0);
                result.add(d);
            }
        }
    }

    /**
     * 拉取成员 ID 全量（POST /user/list_id，cursor 分页，dept_user 展开为 MemberRef 列表）。
     */
    public List<MemberRef> listUserIds() {
        List<MemberRef> result = new ArrayList<>();
        String cursor = "";
        int guard = 0;
        do {
            Map<String, Object> resp = postJson("/user/list_id", Map.of("cursor", cursor));
            checkErrcode(resp, "/user/list_id");
            JsonNode arr = resp.get("dept_user") instanceof JsonNode n ? n : objectMapper.valueToTree(resp.get("dept_user"));
            if (arr != null && arr.isArray()) {
                for (JsonNode node : arr) {
                    long deptId = node.path("dept_id").asLong();
                    JsonNode userids = node.path("userid");
                    if (userids.isArray()) {
                        for (JsonNode uid : userids) {
                            MemberRef ref = new MemberRef();
                            ref.userId = uid.asText();
                            ref.deptIds.add(deptId);
                            result.add(ref);
                        }
                    }
                }
            }
            cursor = asText(resp, "next_cursor");
        } while (StringUtils.hasText(cursor) && guard++ < 200);
        log.info("企微成员 ID 拉取完成（list_id）：{} 个", result.size());
        return result;
    }

    /**
     * 获取成员详情（GET /user/get?userid=），批间节流由调用方/本方法控制。
     */
    public MemberDetail getUserDetail(String userId) {
        long interval = wecomProperties.getUserGetIntervalMs();
        if (interval > 0) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Map<String, Object> resp = getJson("/user/get", Map.of("userid", userId), true);
        checkErrcode(resp, "/user/get");
        MemberDetail d = new MemberDetail();
        d.userId = userId;
        d.name = asText(resp, "name");
        d.mobile = asText(resp, "mobile");
        d.status = (int) asLong(resp, "status", 1L);
        JsonNode arr = resp.get("department") instanceof JsonNode n ? n : objectMapper.valueToTree(resp.get("department"));
        if (arr != null && arr.isArray()) {
            for (JsonNode node : arr) {
                d.departments.add(node.asLong());
            }
        }
        log.debug("企微成员详情：userid={} name={} mobile={} depts={} status={}",
                userId, d.name, maskMobile(d.mobile), d.departments, d.status);
        return d;
    }

    // ---------------- 底层 HTTP + token 失效重试 ----------------

    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        return executeWithTokenRetry(() -> doPostJson(path, body));
    }

    private Map<String, Object> getJson(String path, Map<String, Object> params, boolean attachToken) {
        return executeWithTokenRetry(() -> doGetJson(path, params, attachToken));
    }

    private Map<String, Object> executeWithTokenRetry(java.util.function.Supplier<Map<String, Object>> action) {
        Map<String, Object> resp = action.get();
        int errcode = errcodeOf(resp);
        if (errcode == ERR_TOKEN_INVALID || errcode == ERR_TOKEN_EXPIRED) {
            log.warn("企微 token 失效（errcode={}），清缓存重试一次", errcode);
            cachedToken = null;
            resp = action.get();
        }
        return resp;
    }

    private Map<String, Object> doPostJson(String path, Map<String, Object> body) {
        try {
            String url = BASE_URL + path + "?access_token=" + URLEncoder.encode(getAccessToken(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return send(request);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("企微接口调用失败：" + path + "（" + e.getMessage() + "）");
        }
    }

    private Map<String, Object> doGet(String path, Map<String, Object> params, boolean attachToken) {
        try {
            StringBuilder sb = new StringBuilder(BASE_URL).append(path);
            sb.append("?");
            for (Map.Entry<String, Object> e : params.entrySet()) {
                sb.append(e.getKey()).append("=")
                        .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8)).append("&");
            }
            if (attachToken) {
                sb.append("access_token=").append(URLEncoder.encode(getAccessToken(), StandardCharsets.UTF_8));
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(sb.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            return send(request);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("企微接口调用失败：" + path + "（" + e.getMessage() + "）");
        }
    }

    private Map<String, Object> doGetJson(String path, Map<String, Object> params, boolean attachToken) {
        return doGet(path, params, attachToken);
    }

    private Map<String, Object> send(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        Map<String, Object> map = objectMapper.convertValue(root, Map.class);
        return map == null ? new LinkedHashMap<>() : map;
    }

    private void checkErrcode(Map<String, Object> resp, String api) {
        int errcode = errcodeOf(resp);
        if (errcode != ERRCODE_OK) {
            throw new BusinessException("企微接口错误 " + api + "：errcode=" + errcode
                    + " errmsg=" + asText(resp, "errmsg"));
        }
    }

    private int errcodeOf(Map<String, Object> resp) {
        Object v = resp == null ? null : resp.get("errcode");
        return v instanceof Number n ? n.intValue() : ERRCODE_OK;
    }

    private String asText(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private long asLong(Map<String, Object> map, String key, long def) {
        Object v = map == null ? null : map.get(key);
        return v instanceof Number n ? n.longValue() : def;
    }

    /** 手机号脱敏：138****1234（日志/报告统一使用） */
    public static String maskMobile(String mobile) {
        if (!StringUtils.hasText(mobile) || mobile.length() < 7) {
            return mobile == null ? "" : mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
