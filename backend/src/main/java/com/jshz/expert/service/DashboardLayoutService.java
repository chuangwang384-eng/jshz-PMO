package com.jshz.expert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jshz.expert.common.BusinessException;
import com.jshz.expert.security.AuthUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardLayoutService {
    private static final Set<String> PUBLIC_CARDS = Set.of(
            "stat.myApplications", "stat.riskOverdue", "stat.riskDue", "panel.returned",
            "panel.myProjects", "panel.favorites", "panel.announcements", "panel.notifications", "page.notifications");
    private static final Set<String> APPROVAL_CARDS = Set.of("stat.myApprovals", "panel.myApprovals", "page.approvalManage");
    private static final Map<String, String> MODULE_CARDS = Map.of(
            "page.knowledge", "knowledge", "page.project", "project", "page.experts", "expert",
            "page.customer", "customer", "page.supplier", "supplier", "page.announcements", "announcement");
    private final DashboardReadService reads;
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public DashboardLayoutService(DashboardReadService reads, JdbcTemplate db, ObjectMapper json) {
        this.reads = reads;
        this.db = db;
        this.json = json;
    }

    public record Card(String i, int x, int y, int w, int h) {
        boolean overlaps(Card other) {
            return x < other.x + other.w && other.x < x + w && y < other.y + other.h && other.y < y + h;
        }
    }

    private record Stored(String legacy, String versioned) {}
    public record Snapshot(String revision, JsonNode items) {}

    public Snapshot load(AuthUser user) {
        reads.requireUser(user);
        return snapshot(user, read(user, false));
    }

    @Transactional
    public Snapshot save(AuthUser user, JsonNode body) {
        reads.requireUser(user);
        if (body == null || !body.path("revision").isTextual() || body.toString().length() > 20000) {
            throw invalid();
        }
        List<Card> cards = validate(user, body.path("items"));
        db.update("INSERT INTO user_dashboard_layout(user_id,layout) VALUES (?,JSON_ARRAY()) "
                + "ON DUPLICATE KEY UPDATE user_id=user_id", user.getUserId());
        Stored current = read(user, true);
        if (!revision(current).equals(body.path("revision").asText())) {
            throw new BusinessException(409, "布局已更新，请重新加载");
        }
        cards.sort(Comparator.comparingInt(Card::y).thenComparingInt(Card::x).thenComparing(Card::i));
        String legacy = json.valueToTree(cards.stream().map(card -> Map.of("i", card.i(), "w", card.w())).toList()).toString();
        String versioned = json.valueToTree(Map.of("schemaVersion", 2, "items", cards,
                "writeId", UUID.randomUUID().toString(), "legacyHash", legacyHash(legacy))).toString();
        db.update("UPDATE user_dashboard_layout SET layout=?, layout_v2=? WHERE user_id=?", legacy, versioned, user.getUserId());
        return snapshot(user, read(user, true));
    }

    private Stored read(AuthUser user, boolean lock) {
        String sql = "SELECT layout,layout_v2 FROM user_dashboard_layout WHERE user_id=?" + (lock ? " FOR UPDATE" : "");
        return db.query(sql, (rs, index) -> new Stored(rs.getString(1), rs.getString(2)), user.getUserId())
                .stream().findFirst().orElse(new Stored("[]", null));
    }

    private Snapshot snapshot(AuthUser user, Stored stored) {
        JsonNode legacy = parse(stored.legacy());
        JsonNode versioned = parse(stored.versioned());
        JsonNode source = versioned != null && versioned.path("legacyHash").asText().equals(legacyHash(stored.legacy()))
                ? versioned.path("items") : legacy != null && legacy.isArray() && !legacy.isEmpty() ? legacy : null;
        if (source == null || !source.isArray()) return new Snapshot(revision(stored), null);
        var visible = json.createArrayNode();
        for (JsonNode item : source) if (allowed(user, item.path("i").asText())) visible.add(item);
        return new Snapshot(revision(stored), visible);
    }

    private String revision(Stored stored) {
        return hash(legacyHash(stored.legacy()) + ":" + Objects.toString(stored.versioned(), ""));
    }

    private String legacyHash(String legacy) {
        JsonNode node = parse(legacy);
        if (node == null || !node.isArray()) return hash(Objects.toString(node, ""));
        var canonical = json.createArrayNode();
        for (JsonNode item : node) {
            var sorted = new TreeMap<String, JsonNode>();
            item.fields().forEachRemaining(field -> sorted.put(field.getKey(), field.getValue()));
            canonical.add(json.valueToTree(sorted));
        }
        return hash(canonical.toString());
    }

    private JsonNode parse(String value) {
        if (value == null) return null;
        try {
            return json.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored layout JSON", e);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Card> validate(AuthUser user, JsonNode items) {
        if (!items.isArray() || items.size() > 20) throw invalid();
        List<Card> cards = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : items) {
            String id = item.path("i").asText();
            if (!allowed(user, id)) throw new BusinessException(403, "存在不可用的卡片");
            Card card = new Card(id, integer(item, "x"), integer(item, "y"), integer(item, "w"), integer(item, "h"));
            int minHeight = id.startsWith("panel.") ? 4 : 2;
            if (card.x() < 0 || card.y() < 0 || card.w() < 3 || card.w() > 12 ||
                    card.h() < minHeight || card.h() > 24 || card.x() > 12 - card.w() ||
                    card.y() > 200 - card.h() || !seen.add(id) || cards.stream().anyMatch(card::overlaps)) {
                throw invalid();
            }
            cards.add(card);
        }
        return cards;
    }

    /** 卡片标识是否对当前用户可用（quick-access 等复用同一白名单）。 */
    public boolean allowed(AuthUser user, String id) {
        if (PUBLIC_CARDS.contains(id)) return true;
        if (APPROVAL_CARDS.contains(id)) return user.hasPermission("approval:audit");
        if (id.equals("page.myApplications")) return reads.allowedPath(user, "/api/approval/my-applications");
        if (id.equals("page.directory")) return reads.allowedPath(user, "/api/wecom/directory/mine");
        String module = MODULE_CARDS.get(id);
        return module != null && reads.allowed(user, module);
    }

    private static int integer(JsonNode item, String field) {
        JsonNode value = item.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw invalid();
        return value.intValue();
    }

    private static BusinessException invalid() {
        return new BusinessException(400, "布局格式、尺寸或位置无效");
    }
}
