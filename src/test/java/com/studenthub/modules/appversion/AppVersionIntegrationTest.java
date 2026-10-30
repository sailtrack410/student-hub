package com.studenthub.modules.appversion;

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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * App 版本管理：创建草稿 → 原始流上传安装包 → 发布 → 公开检查更新/下载 → 新版本发布自动下线旧版本 → 强更规则。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppVersionIntegrationTest {

    /** 最小 ZIP 头 + 任意内容，满足服务端 PK\x03\x04 魔数校验 */
    private static final byte[] FAKE_APK = buildFakeApk();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publishCheckUpdateDownloadAndForceUpgrade() throws Exception {
        String admin = login("admin");

        long v5 = createVersion(admin, 5, "1.5.0", 0, false, null);
        mockMvc.perform(post("/api/admin/app-versions/" + v5 + "/package")
                        .param("fileName", "student-hub-1.5.0.apk")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(FAKE_APK)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPackage").value(true))
                .andExpect(jsonPath("$.data.packageFileName").value("student-hub-1.5.0.apk"))
                .andExpect(jsonPath("$.data.packageSha256").isNotEmpty());

        // 管理端下载包内容一致
        MvcResult adminDownload = mockMvc.perform(get("/api/admin/app-versions/" + v5 + "/download")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(FAKE_APK, adminDownload.getResponse().getContentAsByteArray());

        mockMvc.perform(post("/api/admin/app-versions/" + v5 + "/publish")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // 公开检查：低版本 → 有更新且不强制
        JsonNode check = publicCheck("ANDROID", 3);
        assertTrue(check.path("hasUpdate").asBoolean());
        assertFalse(check.path("forceUpdate").asBoolean());
        assertEquals(5, check.path("latest").path("versionCode").asInt());
        assertTrue(check.path("latest").path("downloadUrl").asText()
                .endsWith("/api/public/app-version/" + v5 + "/package"));

        // 公开下载：内容一致且下载量累加
        MvcResult download = mockMvc.perform(get("/api/public/app-version/" + v5 + "/package"))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(FAKE_APK, download.getResponse().getContentAsByteArray());
        JsonNode afterDownload = adminList(admin);
        long downloadCount = findVersion(afterDownload, v5).path("downloadCount").asLong();
        assertTrue(downloadCount >= 1, "下载后 downloadCount 应累加");

        // 发布 v6（外链、最低支持 6）→ v5 自动下线；current=5 时强制更新
        long v6 = createVersion(admin, 6, "1.6.0", 6, false, "https://example.com/student-hub-1.6.0.apk");
        mockMvc.perform(post("/api/admin/app-versions/" + v6 + "/publish")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        JsonNode offlineV5 = findVersion(adminList(admin), v5);
        assertEquals("OFFLINE", offlineV5.path("status").asText(), "发布新版本后旧版本应自动下线");

        JsonNode forced = publicCheck("ANDROID", 5);
        assertTrue(forced.path("forceUpdate").asBoolean(), "低于最低支持版本必须强制更新");
        assertEquals("https://example.com/student-hub-1.6.0.apk",
                forced.path("latest").path("downloadUrl").asText());

        // 已下线版本的公开包下载 404
        mockMvc.perform(get("/api/public/app-version/" + v5 + "/package"))
                .andExpect(status().isNotFound());

        // 未上传包/未填外链的草稿不可发布
        long draft = createVersion(admin, 7, "1.7.0", 0, false, null);
        mockMvc.perform(post("/api/admin/app-versions/" + draft + "/publish")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());

        // 已发布版本不可直接删除
        mockMvc.perform(delete("/api/admin/app-versions/" + v6)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());

        // 非法平台
        mockMvc.perform(get("/api/public/app-version/latest").param("platform", "WINDOWS"))
                .andExpect(status().isBadRequest());

        // 清理
        mockMvc.perform(post("/api/admin/app-versions/" + v6 + "/offline")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        for (long id : new long[]{v5, v6, draft}) {
            mockMvc.perform(delete("/api/admin/app-versions/" + id)
                            .header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void unknownRouteReturns404InsteadOf500() throws Exception {
        String admin = login("admin");
        mockMvc.perform(get("/api/admin/app-versions-not-exist")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_404"));
    }

    private long createVersion(String admin, int versionCode, String versionName, int minSupported,
                               boolean forceUpdate, String downloadUrl) throws Exception {
        String body = objectMapper.writeValueAsString(new VersionPayload(
                "ANDROID", "OFFICIAL", versionCode, versionName, minSupported, forceUpdate,
                "测试版本 " + versionName, downloadUrl));
        MvcResult result = mockMvc.perform(post("/api/admin/app-versions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private JsonNode publicCheck(String platform, int currentVersionCode) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/public/app-version/latest")
                        .param("platform", platform)
                        .param("versionCode", String.valueOf(currentVersionCode)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode adminList(String admin) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/app-versions")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode findVersion(JsonNode list, long id) {
        for (JsonNode node : list) {
            if (node.path("id").asLong() == id) {
                return node;
            }
        }
        assertNotNull(null, "版本不存在: " + id);
        return null;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"DevOnly123!\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private static byte[] buildFakeApk() {
        byte[] header = "PK\u0003\u0004".getBytes(StandardCharsets.ISO_8859_1);
        byte[] payload = "student-hub-badge-release-package".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[header.length + payload.length];
        System.arraycopy(header, 0, bytes, 0, header.length);
        System.arraycopy(payload, 0, bytes, header.length, payload.length);
        return bytes;
    }

    private record VersionPayload(String platform, String channel, int versionCode, String versionName,
                                  int minSupportedVersionCode, boolean forceUpdate, String updateLog,
                                  String downloadUrl) {
    }
}
