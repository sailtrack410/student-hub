package com.studenthub.modules.screen;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.community.AdminApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * 公告 N01 集成测试（V71）：sys_notice（实际表 screen_notice）增加正文与封面后的
 * 创建/编辑/列表回显、正文长度校验、封面内容嗅探上传、App 公告封面下发。
 */
class AdminNoticeContentIntegrationTest extends AdminApiTestSupport {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Test
    void noticeContentAndCoverRoundTrip() throws Exception {
        String admin = login("admin");
        String student = login("requester");

        // 1) 创建带正文与封面的 App 公告
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("noticeType", "NOTICE");
        body.put("title", "集成测试公告：期中教学检查");
        body.put("summary", "公告摘要");
        body.put("content", "第一段正文。\n第二段正文，含换行。");
        body.put("coverImageUrl", "/api/common/assets/notices/itest-cover.png");
        body.put("channel", "APP");
        body.put("action", "PUBLISH");
        body.put("author", "教务处");
        body.put("sortOrder", 1);
        body.put("enabled", true);
        JsonNode created = ok(postJson("/api/admin/notices", admin, body));
        long noticeId = created.path("id").asLong();
        assertTrue(created.path("contentLength").asInt() >= 10);
        assertEqualsText(created.path("content").asText());
        assertTrue(created.path("coverImageUrl").asText().endsWith("itest-cover.png"));

        // 2) 编辑正文（覆盖更新）
        body.put("content", "更新后的正文，用于验证覆盖编辑。");
        JsonNode updated = ok(putJson("/api/admin/notices/" + noticeId, admin, body));
        assertTrue(updated.path("content").asText().startsWith("更新后的正文"));
        assertTrue(updated.path("contentLength").asInt() > 0);

        // 3) 列表回显正文与封面
        JsonNode list = ok(getJson("/api/admin/notices", admin,
                Map.of("channel", "APP", "publishStatus", "PUBLISHED")));
        JsonNode row = null;
        for (JsonNode item : list) {
            if (item.path("id").asLong() == noticeId) {
                row = item;
            }
        }
        assertNotNull(row, "公告应出现在列表");
        assertTrue(row.path("content").asText().contains("更新后的正文"));
        assertNotNull(row.path("coverImageUrl").asText());

        // 4) 正文超长（>20000）拒绝
        body.put("content", "超".repeat(20001));
        call(putRequest("/api/admin/notices/" + noticeId, admin, body), 400);

        // 5) 封面上传：PNG 魔数通过，非法内容拒绝
        MockMultipartFile png = new MockMultipartFile("file", "cover.png", MediaType.IMAGE_PNG_VALUE, PNG_BYTES);
        JsonNode uploaded = call(multipart("/api/admin/notices/cover").file(png)
                .header("Authorization", "Bearer " + admin), 200);
        String coverUrl = uploaded.path("data").asText();
        assertTrue(coverUrl.startsWith("/api/common/assets/notices/"), "封面应返回稳定公开地址: " + coverUrl);

        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes(StandardCharsets.UTF_8));
        call(multipart("/api/admin/notices/cover").file(fake)
                .header("Authorization", "Bearer " + admin), 400);

        // 6) App 公告列表下发封面字段
        body.put("content", "App 展示正文");
        body.put("coverImageUrl", coverUrl);
        ok(putJson("/api/admin/notices/" + noticeId, admin, body));
        JsonNode appList = ok(getJson("/api/app/announcements", student, Map.of("pageNum", "1", "pageSize", "50")));
        JsonNode appRow = null;
        for (JsonNode item : appList.path("records")) {
            if (item.path("id").asLong() == noticeId) {
                appRow = item;
            }
        }
        assertNotNull(appRow, "已发布公告应出现在 App 公告列表");
        assertTrue(appRow.path("coverImageUrl").asText().startsWith("/api/common/assets/notices/"));

        // 7) 非公告管理权限不可上传封面
        call(multipart("/api/admin/notices/cover").file(png)
                .header("Authorization", "Bearer " + student), 403);

        // 清理
        deleteJson("/api/admin/notices/" + noticeId, admin);
    }

    private void assertEqualsText(String content) {
        assertTrue(content.contains("第一段正文"));
        assertFalse(content.isBlank());
    }
}
