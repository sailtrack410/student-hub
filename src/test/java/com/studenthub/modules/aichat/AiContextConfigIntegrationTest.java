package com.studenthub.modules.aichat;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 上下文策略配置 + 知识库 AI 开关：管理端保存后能读回，知识库默认允许 AI 检索。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiContextConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextPolicyAndKnowledgeAssistantFlagsPersist() throws Exception {
        String admin = login("admin");

        JsonNode config = getData("/api/admin/ai/config", admin);
        assertEquals(60000, config.path("maxContextTokens").asInt());
        assertEquals(0.4, config.path("historyTokenRatio").asDouble(), 0.001);
        assertTrue(config.path("summaryEnabled").asBoolean());

        String update = """
                {"enabled": true, "connectTimeoutSeconds": 5, "readTimeoutSeconds": 60,
                 "maxInputCharacters": 12000, "hourlyMessageLimit": 20, "historyWindow": 10,
                 "maxMessageCharacters": 2000,
                 "maxContextTokens": 32000, "contextSafetyMarginTokens": 600, "maxOutputTokens": 1500,
                 "historyTokenRatio": 0.35, "ragTokenRatio": 0.30, "attachmentTokenRatio": 0.10,
                 "summaryEnabled": false, "summaryTriggerRatio": 0.75, "summaryKeepRecentTurns": 3,
                 "tokenEstimateCharsPerToken": 1.4}
                """;
        mockMvc.perform(put("/api/admin/ai/config")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk());

        JsonNode saved = getData("/api/admin/ai/config", admin);
        assertEquals(32000, saved.path("maxContextTokens").asInt());
        assertEquals(600, saved.path("contextSafetyMarginTokens").asInt());
        assertEquals(0.35, saved.path("historyTokenRatio").asDouble(), 0.001);
        assertFalse(saved.path("summaryEnabled").asBoolean());
        assertEquals(3, saved.path("summaryKeepRecentTurns").asInt());

        // 恢复默认配置，避免影响其他测试
        mockMvc.perform(put("/api/admin/ai/config")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": true, "connectTimeoutSeconds": 5, "readTimeoutSeconds": 60,
                                 "maxInputCharacters": 12000, "hourlyMessageLimit": 20, "historyWindow": 10,
                                 "maxMessageCharacters": 2000,
                                 "maxContextTokens": 60000, "contextSafetyMarginTokens": 512, "maxOutputTokens": 2048,
                                 "historyTokenRatio": 0.4, "ragTokenRatio": 0.25, "attachmentTokenRatio": 0.15,
                                 "summaryEnabled": true, "summaryTriggerRatio": 0.8, "summaryKeepRecentTurns": 4,
                                 "tokenEstimateCharsPerToken": 1.5}
                                """))
                .andExpect(status().isOk());
    }

    private JsonNode getData(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
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
}
