package com.studenthub.modules.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.privacy.PhoneMasker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 管理端校区上下文 / 全量写操作审计 / 手机号脱敏 / App 功能开关集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminArchitectureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void campusContextAuditAndFeatureFlags() throws Exception {
        String admin = login("admin");
        String requester = login("requester");

        // 1. 上下文：平台管理员、当前校区、可切换校区
        JsonNode context = ok(getJson("/api/admin/context", admin, Map.of(), null));
        assertTrue(context.path("platformAdmin").asBoolean());
        assertEquals(1L, context.path("campusId").asLong());
        assertTrue(context.path("campuses").size() >= 1);

        // 2. 平台管理员创建校区后可切换；用户列表按校区隔离
        JsonNode created = ok(postJson("/api/admin/campuses", admin,
                Map.of("campusCode", "TEST" + System.nanoTime() % 100000, "campusName", "集成测试校区")));
        long newCampusId = created.path("campusId").asLong();
        assertTrue(newCampusId > 0);

        JsonNode switched = ok(getJson("/api/admin/context", admin, Map.of(), newCampusId));
        assertEquals(newCampusId, switched.path("campusId").asLong());
        assertEquals("集成测试校区", switched.path("campusName").asText());
        JsonNode emptyUsers = ok(getJson("/api/admin/users", admin, Map.of("pageSize", "10"), newCampusId));
        assertEquals(0, emptyUsers.path("total").asInt());
        JsonNode mainUsers = ok(getJson("/api/admin/users", admin, Map.of("pageSize", "10"), 1L));
        assertTrue(mainUsers.path("total").asInt() >= 4);
        assertEquals(0, ok(getJson("/api/admin/service-requests", admin,
                Map.of("pageSize", "10"), newCampusId)).path("total").asInt());
        assertEquals(0, ok(getJson("/api/admin/work-orders", admin,
                Map.of("pageSize", "10"), newCampusId)).path("total").asInt());
        assertEquals(0, ok(getJson("/api/admin/homework", admin,
                Map.of("pageSize", "10"), newCampusId)).path("total").asInt());
        assertEquals(0, ok(getJson("/api/admin/official-feed/sources", admin,
                Map.of(), newCampusId)).size());

        // 3. 非平台管理员不能切到其他校区
        JsonNode forbidden = call(get("/api/admin/context")
                .header("Authorization", "Bearer " + requester)
                .header("X-Campus-Id", String.valueOf(newCampusId)), 403);
        assertEquals("AUTH_403", forbidden.path("code").asText());
        JsonNode own = ok(getJson("/api/admin/context", requester, Map.of(), null));
        assertEquals(1L, own.path("campusId").asLong());
        assertFalse(own.path("platformAdmin").asBoolean());

        // 4. 跨校区写操作被拒绝（目标用户不属于当前校区）
        JsonNode crossCampusWrite = call(post("/api/admin/users/1003/status")
                .header("Authorization", "Bearer " + admin)
                .header("X-Campus-Id", String.valueOf(newCampusId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("status", "DISABLED"))), 404);
        assertEquals("COMMON_404", crossCampusWrite.path("code").asText());

        // 5. 写操作自动进入审计（无需 Controller 手工埋点）
        ok(postJson("/api/admin/users/1004/status", admin, Map.of("status", "DISABLED"), 1L));
        JsonNode auditPage = ok(getJson("/api/admin/audit-logs", admin,
                Map.of("pageSize", "50", "action", "ADMIN_UPDATE_USER_STATUS"), 1L));
        assertTrue(auditPage.path("total").asInt() >= 1);
        JsonNode auditRow = auditPage.path("records").get(0);
        assertEquals("USERS", auditRow.path("resourceType").asText());
        assertEquals("1004", auditRow.path("resourceId").asText());
        assertTrue(auditRow.path("afterJson").asText().contains("DISABLED"));
        // 清理：恢复用户状态，避免影响其他测试
        ok(postJson("/api/admin/users/1004/status", admin, Map.of("status", "ENABLED"), 1L));

        // 6. 手机号脱敏工具
        assertEquals("138****0000", PhoneMasker.mask("13800000000"));
        assertEquals("12****56", PhoneMasker.mask("123456"));
        assertNull(PhoneMasker.mask(null));

        // 7. App 读取有效开关（匿名可读；登录后带校区）
        JsonNode anonymous = ok(getJson("/api/common/features", null, Map.of(), null));
        assertTrue(anonymous.path("flags").has("feature.community_ads"));
        assertTrue(anonymous.path("flags").path("feature.community_ads").asBoolean());
        JsonNode loggedIn = ok(getJson("/api/common/features", admin, Map.of(), 1L));
        assertEquals(1L, loggedIn.path("campusId").asLong());
    }

    private String login(String username) throws Exception {
        JsonNode response = ok(postJson("/api/common/auth/login", null,
                Map.of("username", username, "password", "DevOnly123!")));
        return response.path("accessToken").asText();
    }

    private JsonNode ok(JsonNode result) {
        assertTrue(result.path("success").asBoolean(), "接口应成功: " + result);
        return result.path("data");
    }

    private JsonNode getJson(String path, String token, Map<String, String> params, Long campusId) throws Exception {
        MockHttpServletRequestBuilder builder = get(path);
        params.forEach(builder::param);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (campusId != null) builder.header("X-Campus-Id", String.valueOf(campusId));
        return call(builder, 200);
    }

    private JsonNode postJson(String path, String token, Object body) throws Exception {
        return postJson(path, token, body, null);
    }

    private JsonNode postJson(String path, String token, Object body, Long campusId) throws Exception {
        MockHttpServletRequestBuilder builder = post(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) builder.content(objectMapper.writeValueAsBytes(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (campusId != null) builder.header("X-Campus-Id", String.valueOf(campusId));
        return call(builder, 200);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(expectedStatus, result.getResponse().getStatus(), "响应体: " + body);
        return objectMapper.readTree(body);
    }
}
