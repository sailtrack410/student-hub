package com.studenthub.modules.systemconfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.community.AdminApiTestSupport;
import com.studenthub.modules.file.config.FileProperties;
import com.studenthub.modules.jwauth.config.JwProperties;
import com.studenthub.modules.location.config.AmapProperties;
import com.studenthub.modules.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 管理端 JW / 高德 / RAG / 文件存储配置：种子行、热更新、敏感值加密与集成状态。 */
class AdminPlatformConfigIntegrationTest extends AdminApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwProperties jwProperties;
    @Autowired
    private AmapProperties amapProperties;
    @Autowired
    private RagProperties ragProperties;
    @Autowired
    private FileProperties fileProperties;

    @Test
    void platformGroupsExposeManagedConfigAndTestableIntegrations() throws Exception {
        String admin = login("admin");
        JsonNode items = ok(getJson("/api/admin/system/config", admin, Map.of()));
        assertTrue(has(items, "jw.sso_base_url"));
        assertTrue(has(items, "jw.encrypt_key"));
        assertTrue(has(items, "amap.web_service_key"));
        assertTrue(has(items, "rag.jdbc_url"));
        assertTrue(has(items, "rag.password"));

        JsonNode integrations = ok(getJson("/api/admin/system/integrations", admin, Map.of()));
        for (String code : List.of("jw", "amap", "rag")) {
            JsonNode integration = find(integrations, code);
            assertTrue(integration.path("editable").asBoolean(), code + " 应可在管理端编辑");
        }
    }

    @Test
    void jwAndAmapAndRagValuesReloadWithoutRestart() throws Exception {
        String admin = login("admin");
        int originalReadTimeout = jwProperties.getReadTimeoutSeconds();
        String originalAmapBase = amapProperties.getBaseUrl();
        int originalRrfK = ragProperties.getRrfK();
        try {
            save(admin, "jw.read_timeout_seconds", "23");
            assertEquals(23, jwProperties.getReadTimeoutSeconds(), "教务超时应热更新");

            save(admin, "amap.base_url", "https://restapi.amap.com");
            assertEquals("https://restapi.amap.com", amapProperties.getBaseUrl(), "高德地址应热更新");

            save(admin, "rag.rrf_k", "77");
            assertEquals(77, ragProperties.getRrfK(), "RAG RRF 参数应热更新");
        } finally {
            save(admin, "jw.read_timeout_seconds", String.valueOf(originalReadTimeout));
            save(admin, "amap.base_url", originalAmapBase);
            save(admin, "rag.rrf_k", String.valueOf(originalRrfK));
        }
    }

    @Test
    void platformSecretsAreEncryptedAndNeverReturned() throws Exception {
        String admin = login("admin");
        String key = "jw.encrypt_key";
        String plaintext = "integration-test-jw-session-key";
        String original = jwProperties.getEncryptKey();
        try {
            save(admin, key, plaintext);
            String stored = jdbcTemplate.queryForObject(
                    "select config_value from sys_config where config_key = ? and campus_id = 0",
                    String.class, key);
            assertTrue(stored != null && stored.startsWith("enc:v1:"), "教务会话密钥应加密落库");
            assertFalse(stored.contains(plaintext), "数据库中不能出现明文");
            JsonNode item = find(ok(getJson("/api/admin/system/config", admin, Map.of())), key);
            assertTrue(item.path("secret").asBoolean());
            assertTrue(item.path("value").isNull(), "密钥值不能通过 API 回显");
        } finally {
            save(admin, key, original);
        }
    }

    @Test
    void rejectsMalformedPlatformValues() throws Exception {
        String admin = login("admin");
        call(post("/api/admin/system/config")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + admin)
                .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                        Map.of("key", "rag.jdbc_url", "value", "http://not-postgres", "campusId", 0))))), 400);
        call(post("/api/admin/system/config")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + admin)
                .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                        Map.of("key", "rag.lexical_min_similarity", "value", "9", "campusId", 0))))), 400);
        call(post("/api/admin/system/config")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + admin)
                .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                        Map.of("key", "jw.encrypt_key", "value", "", "campusId", 0))))), 400);
    }

    @Test
    void fileGroupExposesLocalMinioAndOssConfiguration() throws Exception {
        String admin = login("admin");
        JsonNode items = ok(getJson("/api/admin/system/config", admin, Map.of()));
        for (String key : List.of("file.storage", "file.storage_dir", "file.sign_secret",
                "file.minio_endpoint", "file.minio_access_key", "file.minio_secret_key",
                "file.oss_endpoint", "file.oss_public_endpoint", "file.oss_bucket",
                "file.oss_access_key_id", "file.oss_access_key_secret")) {
            assertTrue(has(items, key), "文件存储配置应包含 " + key);
            assertTrue(find(items, key).path("group").asText().equals("file"));
        }
        JsonNode storage = find(ok(getJson("/api/admin/system/integrations", admin, Map.of())), "storage");
        assertEquals("local", storage.path("mode").asText());
        assertFalse(storage.path("editable").asBoolean(), "存储由文件存储页管理，集成卡片不直接编辑");
    }

    @Test
    void fileStorageValuesAndSecretsReloadWithoutRestart() throws Exception {
        String admin = login("admin");
        int originalMax = fileProperties.getMaxFileSizeMb();
        int originalTtl = fileProperties.getViewTtlMinutes();
        String originalSecret = fileProperties.getSignSecret();
        try {
            save(admin, "file.max_size_mb", "9");
            save(admin, "file.view_ttl_minutes", "41");
            save(admin, "file.sign_secret", "integration-file-sign-secret-123");
            assertEquals(9, fileProperties.getMaxFileSizeMb(), "单文件上限应热更新");
            assertEquals(41, fileProperties.getViewTtlMinutes(), "签名有效期应热更新");
            assertEquals("integration-file-sign-secret-123", fileProperties.getSignSecret(), "签名密钥应热更新");

            String stored = jdbcTemplate.queryForObject(
                    "select config_value from sys_config where config_key = ? and campus_id = 0",
                    String.class, "file.sign_secret");
            assertTrue(stored != null && stored.startsWith("enc:v1:"), "文件签名密钥应加密落库");
            assertFalse(stored.contains("integration-file-sign-secret-123"), "数据库中不能出现明文");
            JsonNode item = find(ok(getJson("/api/admin/system/config", admin, Map.of())), "file.sign_secret");
            assertTrue(item.path("secret").asBoolean());
            assertTrue(item.path("value").isNull(), "密钥值不能通过 API 回显");
        } finally {
            save(admin, "file.max_size_mb", String.valueOf(originalMax));
            save(admin, "file.view_ttl_minutes", String.valueOf(originalTtl));
            save(admin, "file.sign_secret", originalSecret);
        }
    }

    @Test
    void fileSettingsApiSavesAndRejectsIncompleteObjectStorageSwitch() throws Exception {
        String admin = login("admin");
        int originalMax = fileProperties.getMaxFileSizeMb();
        int originalTtl = fileProperties.getViewTtlMinutes();
        String originalDir = fileProperties.getStorageDir();
        try {
            JsonNode saved = ok(postJson("/api/admin/files/settings", admin, Map.of(
                    "maxSizeMb", 7, "viewTtlMinutes", 12, "storage", "local",
                    "storageDir", originalDir)));
            assertEquals("local", saved.path("storage").asText());
            assertEquals(7, saved.path("maxSizeMb").asInt());
            assertEquals(12, saved.path("viewTtlMinutes").asInt());
            assertEquals(7, fileProperties.getMaxFileSizeMb());
            assertEquals(12, fileProperties.getViewTtlMinutes());

            // 没有任何 OSS 凭据时不允许直接切换到 oss。
            call(post("/api/admin/files/settings")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + admin)
                    .content(objectMapper.writeValueAsString(Map.of("storage", "oss"))), 400);
        } finally {
            ok(postJson("/api/admin/files/settings", admin, Map.of(
                    "maxSizeMb", originalMax, "viewTtlMinutes", originalTtl, "storage", "local",
                    "storageDir", originalDir)));
        }
    }

    @Test
    void fileStorageTestReportsTargetTypeAndDoesNotSwitchActiveStorage() throws Exception {
        String admin = login("admin");
        JsonNode local = ok(call(post("/api/admin/files/settings/test")
                .param("storage", "local")
                .header("Authorization", "Bearer " + admin), 200));
        assertEquals("local", local.path("storage").asText());
        assertEquals("本地目录", local.path("label").asText());
        assertTrue(local.path("active").asBoolean(), "local 是测试环境当前生效存储");
        assertTrue(local.path("reachable").asBoolean(), "本地测试目录应可写");
        assertTrue(local.path("target").asText().contains("test-files"));

        JsonNode oss = ok(call(post("/api/admin/files/settings/test")
                .param("storage", "oss")
                .header("Authorization", "Bearer " + admin), 200));
        assertEquals("oss", oss.path("storage").asText());
        assertEquals("阿里云 OSS", oss.path("label").asText());
        assertFalse(oss.path("active").asBoolean());
        assertFalse(oss.path("reachable").asBoolean());
        assertTrue(oss.path("detail").asText().contains("未配置"));
        assertEquals("local", fileProperties.normalizedStorage(), "单独测试 OSS 不能改变当前生效存储");

        JsonNode generic = ok(call(post("/api/admin/system/integrations/storage/test")
                .header("Authorization", "Bearer " + admin), 200));
        assertTrue(generic.path("detail").asText().contains("当前生效存储 本地目录"),
                "通用集成测试也必须标明实际测试的存储类型");

        call(post("/api/admin/files/settings/test")
                .param("storage", "ftp")
                .header("Authorization", "Bearer " + admin), 400);
    }

    @Test
    void rejectsInvalidFileStorageValues() throws Exception {
        String admin = login("admin");
        for (Map<String, Object> item : List.of(
                Map.<String, Object>of("key", "file.storage", "value", "ftp", "campusId", 0),
                Map.<String, Object>of("key", "file.max_size_mb", "value", "0", "campusId", 0),
                Map.<String, Object>of("key", "file.oss_bucket", "value", "bad bucket", "campusId", 0),
                Map.<String, Object>of("key", "file.oss_endpoint", "value", "ftp://not-oss", "campusId", 0))) {
            call(post("/api/admin/system/config")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + admin)
                    .content(objectMapper.writeValueAsString(Map.of("items", List.of(item)))), 400);
        }
    }

    private void save(String admin, String key, String value) throws Exception {
        ok(call(post("/api/admin/system/config")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + admin)
                .content(objectMapper.writeValueAsString(Map.of("items", List.of(
                        Map.of("key", key, "value", value, "campusId", 0))))), 200));
    }

    private boolean has(JsonNode items, String key) {
        for (JsonNode item : items) {
            if (key.equals(item.path("key").asText()) || key.equals(item.path("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode find(JsonNode items, String key) {
        for (JsonNode item : items) {
            if (key.equals(item.path("key").asText()) || key.equals(item.path("code").asText())) {
                return item;
            }
        }
        throw new AssertionError("未找到: " + key);
    }
}
