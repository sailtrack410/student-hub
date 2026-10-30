package com.studenthub.modules.dict;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 字典全量目录集成测试：通用枚举字典来自 V88，成就/成长字典由 AchievementDictProvider 实时只读提供。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDictCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullCatalogIncludesAchievementReadonlyDictionaries() throws Exception {
        String token = login("admin");
        JsonNode types = ok(getJson("/api/admin/dicts/types", token));
        List<String> codes = new ArrayList<>();
        JsonNode badgeType = null;
        for (JsonNode type : types) {
            codes.add(type.path("typeCode").asText());
            if ("ACHIEVEMENT_BADGE".equals(type.path("typeCode").asText())) {
                badgeType = type;
            }
        }
        assertTrue(codes.size() >= 80, "应返回全量字典类型，实际 " + codes.size());
        assertTrue(codes.contains("COMMUNITY_REPORT_REASON"));
        assertTrue(codes.contains("WORK_ORDER_STATUS"));
        assertTrue(codes.contains("AUDIT_ACTION"));
        assertTrue(codes.contains("USER_LEVEL"));
        assertTrue(codes.contains("CLASS"));
        assertTrue(codes.contains("SERVICE_CATEGORY"));
        assertTrue(codes.contains("COMMUNITY_SECTION"));
        assertTrue(codes.contains("IDENTITY_REJECT_REASON"));
        assertTrue(codes.contains("USER_ROLE"));
        assertTrue(badgeType != null, "缺少勋章字典");
        assertEquals("ACHIEVEMENT", badgeType.path("source").asText());
        assertTrue(badgeType.path("readonly").asBoolean());
        assertTrue(badgeType.path("itemCount").asLong() >= 19, "至少应包含 19 枚内置勋章");

        JsonNode badgeItems = ok(getJson(
                "/api/admin/dicts/types/ACHIEVEMENT_BADGE/items?includeDisabled=true", token));
        assertEquals(badgeType.path("itemCount").asLong(), badgeItems.size(),
                "实时勋章数与字典类型 itemCount 应一致");
        boolean hasRarity = false;
        for (JsonNode item : badgeItems) {
            if (item.path("extra").asText().contains("\"rarity\"")) {
                hasRarity = true;
                break;
            }
        }
        assertTrue(hasRarity, "勋章项应带稀有度/解锁指标扩展信息");

        JsonNode serviceCategories = ok(getJson("/api/admin/dicts/types/SERVICE_CATEGORY/items", token));
        assertTrue(serviceCategories.size() >= 5, "服务分类应实时读取 service_category");
        assertTrue(serviceCategories.get(0).path("remark").asText().contains("服务目录"));

        JsonNode levelItems = ok(getJson("/api/admin/dicts/types/USER_LEVEL/items", token));
        assertEquals(9, levelItems.size());
        assertEquals("初来乍到", levelItems.get(0).path("itemLabel").asText());
        assertEquals("校园传奇", levelItems.get(8).path("itemLabel").asText());

        // 只读字典不允许在字典管理里新增项
        JsonNode rejected = call(post("/api/admin/dicts/items")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .header("X-Campus-Id", "1")
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "typeCode", "ACHIEVEMENT_BADGE",
                        "itemCode", "IT_SHOULD_NOT_EXIST",
                        "itemLabel", "不应写入",
                        "sortOrder", 1,
                        "status", "ENABLED"))), 400);
        assertFalse(rejected.path("success").asBoolean());
    }

    private String login(String username) throws Exception {
        JsonNode response = ok(postJson("/api/common/auth/login",
                Map.of("username", username, "password", "DevOnly123!")));
        return response.path("accessToken").asText();
    }

    private JsonNode ok(JsonNode result) {
        assertTrue(result.path("success").asBoolean(), "接口应成功: " + result);
        return result.path("data");
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return call(get(path).header("Authorization", "Bearer " + token)
                .header("X-Campus-Id", "1"), 200);
    }

    private JsonNode postJson(String path, Object body) throws Exception {
        return call(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)), 200);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(expectedStatus, result.getResponse().getStatus(), "响应体: " + body);
        return objectMapper.readTree(body);
    }
}
