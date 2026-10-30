package com.studenthub.modules.systemconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.integration.wechat.WechatProperties;
import com.studenthub.modules.community.AdminApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 管理端 QQ/微信/邮箱配置：密钥加密落库、读取不回显、模式热更新。 */
class AdminAuthConfigIntegrationTest extends AdminApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private WechatProperties wechatProperties;

    @Test
    void secretIsEncryptedStoredAndNeverReturnedByApi() throws Exception {
        String admin = login("admin");
        String key = "mail.password";
        String plaintext = "integration-test-smtp-password";
        try {
            ok(call(post("/api/admin/system/config")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + admin)
                    .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                            Map.of("key", key, "value", plaintext, "campusId", 0))))), 200));

            String stored = jdbcTemplate.queryForObject(
                    "select config_value from sys_config where config_key = ? and campus_id = 0",
                    String.class, key);
            assertTrue(stored != null && stored.startsWith("enc:v1:"), "密钥应加密落库");
            assertFalse(stored.contains(plaintext), "数据库中不能出现明文");

            JsonNode items = ok(getJson("/api/admin/system/config", admin, Map.of()));
            JsonNode item = find(items, key);
            assertTrue(item.path("secret").asBoolean());
            assertTrue(item.path("configured").asBoolean());
            assertEquals("DATABASE", item.path("source").asText());
            assertTrue(item.path("value").isNull(), "密钥值不能通过 API 回显");
        } finally {
            ok(call(post("/api/admin/system/config")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + admin)
                    .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                            Map.of("key", key, "value", "", "campusId", 0))))), 200));
        }
        String cleared = jdbcTemplate.queryForObject(
                "select config_value from sys_config where config_key = ? and campus_id = 0",
                String.class, key);
        assertEquals("", cleared, "管理员显式清空后不应回退环境变量");
    }

    @Test
    void secretUpdateReloadsRuntimeWithoutExposing() throws Exception {
        String admin = login("admin");
        String key = "wechat.app_secret";
        try {
            ok(call(post("/api/admin/system/config")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + admin)
                    .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                            Map.of("key", key, "value", "test-rotated-secret", "campusId", 0))))), 200));
            assertEquals("test-rotated-secret", wechatProperties.getAppSecret(),
                    "解密后的密钥应热更新到运行时属性");

            JsonNode items = ok(getJson("/api/admin/system/config", admin, Map.of()));
            assertTrue(find(items, key).path("value").isNull(), "密钥值不能通过 API 回显");
        } finally {
            ok(call(post("/api/admin/system/config")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + admin)
                    .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                            Map.of("key", key, "value", "", "campusId", 0))))), 200));
        }
        assertEquals("", wechatProperties.getAppSecret());
    }

    @Test
    void modeChangeTakesEffectWithoutRestart() throws Exception {
        String admin = login("admin");
        String original = wechatProperties.getMode();
        try {
            ok(call(post("/api/admin/system/config")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + admin)
                    .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                            Map.of("key", "wechat.mode", "value", "real", "campusId", 0))))), 200));
            assertEquals("real", wechatProperties.getMode(), "保存后运行时配置应立即更新");
        } finally {
            ok(call(post("/api/admin/system/config")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + admin)
                    .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                            Map.of("key", "wechat.mode", "value", original, "campusId", 0))))), 200));
        }
        assertEquals(original, wechatProperties.getMode());
    }

    @Test
    void rejectsUnknownModeValue() throws Exception {
        String admin = login("admin");
        call(post("/api/admin/system/config")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + admin)
                .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                        Map.of("key", "qq.mode", "value", "production", "campusId", 0))))), 400);
    }

    private JsonNode find(JsonNode items, String key) {
        for (JsonNode item : items) {
            if (key.equals(item.path("key").asText())) {
                return item;
            }
        }
        assertNull(null, "未找到配置项: " + key);
        return null;
    }
}
