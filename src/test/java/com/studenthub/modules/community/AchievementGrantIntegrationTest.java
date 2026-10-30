package com.studenthub.modules.community;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 成就 SYS09 集成测试（V71）：发放记录分页、手动发放、重复发放拦截、
 * 停用勋章拒绝发放、撤销后记录消失、无权限拒绝。
 */
class AchievementGrantIntegrationTest extends AdminApiTestSupport {

    @Test
    void grantListRevokeChain() throws Exception {
        String admin = login("admin");
        String requester = login("requester");
        long requesterId = currentUserId(requester);

        // 1) 建两枚测试勋章（一枚启用、一枚停用）
        long enabledBadgeId = createBadge(admin, "TEST_GRANT_ENABLED", true);
        createBadge(admin, "TEST_GRANT_DISABLED", false);
        assertTrue(enabledBadgeId > 0);

        // 2) 手动发放
        Map<String, Object> grantBody = new LinkedHashMap<>();
        grantBody.put("userId", requesterId);
        grantBody.put("badgeCode", "TEST_GRANT_ENABLED");
        grantBody.put("note", "集成测试手动发放");
        JsonNode granted = ok(postJson("/api/admin/achievements/grants", admin, grantBody));
        assertNotNull(granted.path("id").asLong());
        assertEquals("MANUAL", granted.path("grantSource").asText());
        assertEquals("TEST_GRANT_ENABLED", granted.path("badgeCode").asText());
        assertNotNull(granted.path("unlockedAt").asText());

        // 3) 重复发放被拒绝
        call(postRequest("/api/admin/achievements/grants", admin, grantBody), 400);

        // 4) 发放记录按用户 + 勋章筛选
        JsonNode page = ok(getJson("/api/admin/achievements/grants", admin,
                Map.of("userId", String.valueOf(requesterId), "badgeCode", "TEST_GRANT_ENABLED")));
        assertTrue(page.path("total").asInt() >= 1);
        assertEquals("MANUAL", page.path("records").get(0).path("grantSource").asText());

        // 5) 停用勋章不可发放
        Map<String, Object> disabledGrant = new LinkedHashMap<>(grantBody);
        disabledGrant.put("badgeCode", "TEST_GRANT_DISABLED");
        call(postRequest("/api/admin/achievements/grants", admin, disabledGrant), 400);

        // 6) 撤销后记录消失
        long grantId = granted.path("id").asLong();
        deleteJson("/api/admin/achievements/grants/" + grantId, admin);
        JsonNode after = ok(getJson("/api/admin/achievements/grants", admin,
                Map.of("userId", String.valueOf(requesterId), "badgeCode", "TEST_GRANT_ENABLED")));
        assertEquals(0, after.path("total").asInt());

        // 7) 无权限用户不可访问
        call(get("/api/admin/achievements/grants").header("Authorization", "Bearer " + requester), 403);
    }

    private long createBadge(String admin, String code, boolean enabled) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("badgeCode", code);
        body.put("badgeName", "测试勋章 " + code);
        body.put("category", "COMMUNITY");
        body.put("rarity", "COMMON");
        body.put("description", "集成测试用勋章");
        body.put("metricCode", "POST_COUNT");
        body.put("metricThreshold", 1);
        body.put("rewardExp", 0);
        body.put("sortOrder", 999);
        body.put("enabled", enabled);
        JsonNode created = ok(postJson("/api/admin/achievements/badges", admin, body));
        return created.path("id").asLong();
    }
}
