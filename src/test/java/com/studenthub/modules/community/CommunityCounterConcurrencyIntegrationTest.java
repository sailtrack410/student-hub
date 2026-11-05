package com.studenthub.modules.community;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.community.entity.CommunityCommentEntity;
import com.studenthub.modules.community.entity.CommunityPostLikeEntity;
import com.studenthub.modules.community.entity.CommunityCommentLikeEntity;
import com.studenthub.modules.community.mapper.CommunityCommentMapper;
import com.studenthub.modules.community.mapper.CommunityPostLikeMapper;
import com.studenthub.modules.community.mapper.CommunityCommentLikeMapper;
import com.studenthub.modules.community.mapper.CommunityPostMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityCounterConcurrencyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CommunityPostMapper posts;
    @Autowired CommunityCommentMapper comments;
    @Autowired CommunityPostLikeMapper likes;
    @Autowired CommunityCommentLikeMapper commentLikes;

    @Test
    void concurrentCommentsAndSameAccountLikesKeepCountersConsistent() throws Exception {
        String token = send(null, "/api/common/auth/login", Map.of(
                "username", "requester", "password", "DevOnly123!")).path("accessToken").asText();
        long postId = send(token, "/api/app/community/posts", Map.of(
                "sectionId", 6001, "content", "并发计数回归")).path("postId").asLong();
        List<Callable<Long>> actions = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int index = i;
            actions.add(() -> send(token, "/api/app/community/posts/" + postId + "/comments",
                    Map.of("content", "并发评论 " + index)).path("commentId").asLong());
        }
        actions.add(() -> { send(token, "/api/app/community/posts/" + postId + "/like", null); return 0L; });
        actions.add(() -> { send(token, "/api/app/community/posts/" + postId + "/like", null); return 0L; });
        List<Long> ids = concurrently(actions).stream().filter(id -> id != 0).toList();
        assertEquals(12, comments.selectCount(new LambdaQueryWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getPostId, postId).eq(CommunityCommentEntity::getStatus, "ENABLED")));
        assertEquals(12, posts.selectById(postId).getCommentCount());
        assertEquals(0, posts.selectById(postId).getLikeCount());
        assertEquals(0, likes.selectCount(new LambdaQueryWrapper<CommunityPostLikeEntity>()
                .eq(CommunityPostLikeEntity::getPostId, postId)));

        long commentId = ids.get(0);
        concurrently(List.of(
                () -> send(token, "/api/app/community/comments/" + commentId + "/like", null),
                () -> send(token, "/api/app/community/comments/" + commentId + "/like", null)));
        assertEquals(0, comments.selectById(commentId).getLikeCount());
        assertEquals(0, commentLikes.selectCount(new LambdaQueryWrapper<CommunityCommentLikeEntity>()
                .eq(CommunityCommentLikeEntity::getCommentId, commentId)));
        List<Integer> statuses = concurrently(List.of(
                () -> mvc.perform(delete("/api/app/community/comments/" + commentId)
                        .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus(),
                () -> mvc.perform(delete("/api/app/community/comments/" + commentId)
                        .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus()));
        assertTrue(statuses.contains(200));
        assertTrue(statuses.stream().allMatch(status -> status == 200 || status == 404));
        assertEquals(11, posts.selectById(postId).getCommentCount());
        assertEquals(11, comments.selectCount(new LambdaQueryWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getPostId, postId).eq(CommunityCommentEntity::getStatus, "ENABLED")));

        assertEquals(200, mvc.perform(delete("/api/app/community/posts/" + postId)
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus());
        assertEquals(0, posts.selectById(postId).getCommentCount());
        assertEquals(0, comments.selectCount(new LambdaQueryWrapper<CommunityCommentEntity>()
                .eq(CommunityCommentEntity::getPostId, postId).eq(CommunityCommentEntity::getStatus, "ENABLED")));
    }

    private JsonNode send(String token, String path, Object body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(200, response.getStatus(), response.getContentAsString());
        return json.readTree(response.getContentAsString()).path("data");
    }

    private <T> List<T> concurrently(List<Callable<T>> actions) throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        try {
            var futures = actions.stream().map(action -> executor.submit(() -> {
                start.await();
                return action.call();
            })).toList();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
