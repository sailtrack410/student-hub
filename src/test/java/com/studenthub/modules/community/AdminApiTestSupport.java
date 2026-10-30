package com.studenthub.modules.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 管理端集成测试公共基类：H2（MODE=MySQL）+ Flyway + MockMvc，登录走开发种子账号。
 * 供 community / screen / audit 等模块的接口级测试复用，避免每个测试重复拼接请求。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AdminApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    protected String login(String username) throws Exception {
        JsonNode response = ok(postJson("/api/common/auth/login", null,
                Map.of("username", username, "password", "DevOnly123!")));
        return response.path("accessToken").asText();
    }

    protected JsonNode ok(JsonNode result) {
        assertTrue(result.path("success").asBoolean(), "接口应成功: " + result);
        return result.path("data");
    }

    protected JsonNode getJson(String path, String token, Map<String, String> params) throws Exception {
        return call(getRequest(path, token, params), 200);
    }

    protected MockHttpServletRequestBuilder getRequest(String path, String token, Map<String, String> params) {
        MockHttpServletRequestBuilder builder = get(path);
        params.forEach(builder::param);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    protected JsonNode postJson(String path, String token, Object body) throws Exception {
        return call(postRequest(path, token, body), 200);
    }

    protected JsonNode putJson(String path, String token, Object body) throws Exception {
        return call(putRequest(path, token, body), 200);
    }

    protected JsonNode deleteJson(String path, String token) throws Exception {
        return call(delete(path).header("Authorization", "Bearer " + token), 200);
    }

    protected MockHttpServletRequestBuilder postRequest(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = post(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    protected MockHttpServletRequestBuilder putRequest(String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = put(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    protected JsonNode call(MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(builder)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().is(expectedStatus))
                .andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNotNull(content);
        assertFalse(content.isBlank(), "响应体不应为空, status=" + expectedStatus);
        return objectMapper.readTree(content);
    }

    protected byte[] raw(MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(builder)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().is(expectedStatus))
                .andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    /** 取当前登录用户 ID（用于成就发放等需要用户 ID 的接口） */
    protected long currentUserId(String token) throws Exception {
        JsonNode me = ok(getJson("/api/common/auth/me", token, Map.of()));
        if (me.hasNonNull("userId")) {
            return me.path("userId").asLong();
        }
        return me.path("id").asLong();
    }
}
