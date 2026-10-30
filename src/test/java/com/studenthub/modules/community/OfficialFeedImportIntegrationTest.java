package com.studenthub.modules.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.modules.officialfeed.entity.OfficialFeedArticleEntity;
import com.studenthub.modules.officialfeed.mapper.OfficialFeedArticleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 官方通知 C08 集成测试（V71）：URL 域名校验 → 草稿预览 → 版权确认门槛 → 立即发布 →
 * 编辑/定时校验 → 失败重试不伪造成功。
 * 说明：真实抓取依赖学校官网外网，自动化只覆盖可离线复现的链路（草稿数据直接入库后走接口）。
 */
class OfficialFeedImportIntegrationTest extends AdminApiTestSupport {

    private static final long SOURCE_ID = 8001L;

    @Autowired
    private OfficialFeedArticleMapper articleMapper;

    @Test
    void importPreviewCopyrightPublishScheduleRetry() throws Exception {
        String admin = login("admin");

        // 1) 只允许学校官网域名
        call(postRequest("/api/admin/official-feed/articles/import-url", admin,
                Map.of("url", "https://evil.example.com/a.htm")), 400);
        call(postRequest("/api/admin/official-feed/articles/import-url", admin,
                Map.of("url", "ftp://www.mycc.edu.cn/a.htm")), 400);

        // 2) 草稿预览：图文块、字数、版权未确认
        long draftId = seedArticle("https://www.mycc.edu.cn/info/itest-draft.htm",
                OfficialFeedArticleEntity.STATUS_DRAFT, false,
                "[{\"type\":\"TEXT\",\"text\":\"第一段正文\",\"fileId\":null},"
                        + "{\"type\":\"TEXT\",\"text\":\"第二段正文\",\"fileId\":null}]");
        JsonNode preview = ok(getJson("/api/admin/official-feed/articles/" + draftId + "/preview", admin,
                Map.of()));
        assertEquals("DRAFT", preview.path("status").asText());
        assertTrue(preview.path("wordCount").asInt() >= 6);
        assertTrue(preview.path("blocks").size() >= 2);
        assertEquals("TEXT", preview.path("blocks").get(0).path("type").asText());
        assertTrue(preview.path("copyrightConfirmed").isBoolean());

        // 3) 未确认版权不允许发布
        call(postRequest("/api/admin/official-feed/articles/" + draftId + "/publish", admin, null), 400);

        // 4) 版权确认 → 立即发布
        JsonNode confirmed = ok(postJson("/api/admin/official-feed/articles/" + draftId + "/copyright", admin,
                Map.of("confirmed", true)));
        assertTrue(confirmed.path("copyrightConfirmed").asBoolean());
        JsonNode published = ok(postJson("/api/admin/official-feed/articles/" + draftId + "/publish",
                admin, null));
        assertEquals("PUBLISHED", published.path("status").asText());
        assertNotNull(published.path("postId").asLong());

        // 5) 重复发布 / 已发布编辑均被拒绝
        call(postRequest("/api/admin/official-feed/articles/" + draftId + "/publish", admin, null), 400);
        call(putRequest("/api/admin/official-feed/articles/" + draftId, admin,
                Map.of("title", "已发布改标题")), 400);

        // 6) 编辑草稿 + 定时发布校验
        long draft2 = seedArticle("https://www.mycc.edu.cn/info/itest-draft2.htm",
                OfficialFeedArticleEntity.STATUS_DRAFT, true,
                "[{\"type\":\"TEXT\",\"text\":\"待编辑正文\",\"fileId\":null}]");
        JsonNode edited = ok(putJson("/api/admin/official-feed/articles/" + draft2, admin,
                Map.of("title", "编辑后的标题", "plainText", "编辑后的正文内容")));
        assertEquals("编辑后的标题", edited.path("title").asText());
        assertTrue(edited.path("plainText").asText().contains("编辑后的正文"));

        call(postRequest("/api/admin/official-feed/articles/" + draft2 + "/schedule", admin,
                Map.of("scheduledAt", LocalDateTime.now().minusHours(1).toString())), 400);
        JsonNode scheduled = ok(postJson("/api/admin/official-feed/articles/" + draft2 + "/schedule",
                admin, Map.of("scheduledAt", LocalDateTime.now().plusHours(2).toString())));
        assertEquals("SCHEDULED", scheduled.path("status").asText());
        assertNotNull(scheduled.path("scheduledAt").asText());

        // 7) 失败重试：非白名单地址直接失败，保留 FAILED 与错误摘要，并累计重试次数
        long failedId = seedArticle("https://bad-host.example.com/a.htm",
                OfficialFeedArticleEntity.STATUS_FAILED, false,
                "[{\"type\":\"TEXT\",\"text\":\"失败草稿\",\"fileId\":null}]");
        JsonNode retried = ok(postJson("/api/admin/official-feed/articles/" + failedId + "/retry",
                admin, null));
        assertEquals("FAILED", retried.path("status").asText(), "重试失败不得伪造成功");
        assertTrue(retried.path("retryCount").asInt() >= 1);
        assertNotNull(retried.path("errorMessage").asText());

        // 8) 非管理员不可访问
        String requester = login("requester");
        call(get("/api/admin/official-feed/articles/" + draftId + "/preview")
                .header("Authorization", "Bearer " + requester), 403);
    }

    private long seedArticle(String url, String status, boolean copyrightConfirmed, String blocksJson) {
        OfficialFeedArticleEntity entity = new OfficialFeedArticleEntity();
        entity.setSourceId(SOURCE_ID);
        entity.setSourceUrl(url);
        entity.setTitle("集成测试官方通知 " + status);
        entity.setContentHash("itest-" + Math.abs(url.hashCode()));
        entity.setImageCount(0);
        entity.setStatus(status);
        entity.setFetchedAt(LocalDateTime.now());
        entity.setContentBlocks(blocksJson);
        entity.setPlainText("第一段正文\n第二段正文");
        entity.setImportMode(OfficialFeedArticleEntity.MODE_MANUAL_URL);
        entity.setCopyrightConfirmed(copyrightConfirmed);
        entity.setRetryCount(0);
        articleMapper.insert(entity);
        return entity.getId();
    }
}
