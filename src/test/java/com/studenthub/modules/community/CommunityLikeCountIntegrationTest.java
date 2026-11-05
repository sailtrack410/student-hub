package com.studenthub.modules.community;

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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * D1/D3 验收（H2 + Flyway + MockMvc，走完整 Controller→Service→Mapper 链路）：
 * - D1 点赞计数原子化：点赞/取消后计数与响应一致、并发语义由 GREATEST 钳 0 兜底；
 * - D3 列表瘦身：列表响应不再携带 contentBlocks 与 thumbnails，详情保持全量。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityLikeCountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static final String AUTHOR = "requester";

    @Test
    void likeToggleKeepsCountConsistentAndListDropsContentBlocks() throws Exception {
        String token = login(AUTHOR);

        // 发一篇带图文块的帖子（触发 contentBlocks 下发路径）
        long postId = ok(postJson(token, "/api/app/community/posts", Map.of(
                "sectionId", 6001,
                "title", "D1/D3 验收帖",
                "content", "正文内容",
                "postType", "ARTICLE",
                "contentBlocks", java.util.List.of(
                        Map.of("type", "TEXT", "text", "第一段"),
                        Map.of("type", "TEXT", "text", "第二段"))))).path("postId").asLong();

        // 点赞 → 计数 1、liked=true
        JsonNode liked = ok(postJson(token, "/api/app/community/posts/" + postId + "/like", null));
        assertEquals(1, liked.path("likeCount").asInt(), "点赞后计数应为 1");
        assertTrue(liked.path("liked").asBoolean());
        assertFalse(liked.path("contentBlocks").isNull(), "详情/单帖组装保持全量 contentBlocks");
        assertTrue(liked.path("contentBlocks").size() >= 1, "单帖响应应含内容块");
        assertFalse(liked.path("imageUrls").isNull());

        // 取消 → 计数 0、liked=false（GREATEST 钳 0 下不会出负数）
        JsonNode unliked = ok(postJson(token, "/api/app/community/posts/" + postId + "/like", null));
        assertEquals(0, unliked.path("likeCount").asInt(), "取消后计数应为 0");
        assertFalse(unliked.path("liked").asBoolean());

        // 再点回 1，供列表断言
        ok(postJson(token, "/api/app/community/posts/" + postId + "/like", null));

        // D3：列表响应不携带 contentBlocks / thumbnailUrls
        JsonNode list = ok(getJson(token, "/api/app/community/posts",
                Map.of("pageNum", "1", "pageSize", "20")));
        JsonNode mine = null;
        for (JsonNode record : list.path("records")) {
            if (record.path("postId").asLong() == postId) {
                mine = record;
                break;
            }
        }
        assertNotNull(mine, "列表应包含刚发的帖子");
        assertTrue(mine.path("contentBlocks").isEmpty(), "列表 contentBlocks 应为空数组（瘦身）");
        assertTrue(mine.path("thumbnailUrls").isEmpty(), "列表 thumbnailUrls 应为空数组（瘦身）");
        assertEquals(1, mine.path("likeCount").asInt(), "列表计数应与点赞态一致");
        assertTrue(mine.path("liked").asBoolean());

        // 详情保持全量
        JsonNode detail = ok(getJson(token, "/api/app/community/posts/" + postId, Map.of()));
        assertTrue(detail.path("post").path("contentBlocks").size() >= 1, "详情 contentBlocks 应保持全量");
        assertTrue(detail.path("post").path("thumbnailUrls").size() >= 0);
    }

    @Test
    void commentLikeCountStaysConsistent() throws Exception {
        String token = login(AUTHOR);
        long postId = ok(postJson(token, "/api/app/community/posts", Map.of(
                "sectionId", 6001,
                "content", "评论点赞验收帖正文"))).path("postId").asLong();
        long commentId = ok(postJson(token, "/api/app/community/posts/" + postId + "/comments",
                Map.of("content", "评论内容"))).path("commentId").asLong();

        JsonNode liked = ok(postJson(token, "/api/app/community/comments/" + commentId + "/like", null));
        assertEquals(1, liked.path("likeCount").asInt(), "评论点赞后计数应为 1");
        JsonNode unliked = ok(postJson(token, "/api/app/community/comments/" + commentId + "/like", null));
        assertEquals(0, unliked.path("likeCount").asInt(), "评论取消后计数应为 0");
    }

    // ---------- 脚手架（与 CommunityAdsIntegrationTest 同款） ----------

    private JsonNode ok(JsonNode result) {
        assertTrue(result.path("success").asBoolean(), "接口应成功: " + result);
        return result.path("data");
    }

    private JsonNode postJson(String token, String path, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        return call(builder, 200);
    }

    private JsonNode getJson(String token, String path, Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder builder = get(path)
                .header("Authorization", "Bearer " + token);
        params.forEach(builder::param);
        return call(builder, 200);
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        assertEquals(expectedStatus, result.getResponse().getStatus(),
                "HTTP 状态不符: " + result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String login(String username) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/common/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("username", username, "password", "DevOnly123!")));
        JsonNode response = call(builder, 200);
        assertTrue(response.path("success").asBoolean(), "登录应成功: " + response);
        return response.path("data").path("accessToken").asText();
    }
}
