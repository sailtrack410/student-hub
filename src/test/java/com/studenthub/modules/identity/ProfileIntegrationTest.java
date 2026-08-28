package com.studenthub.modules.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人中心：昵称修改与身份申报，会话内用户数据需同步刷新。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void updateIdentityAndProfileRefreshesSession() throws Exception {
        String token = login("requester");

        // 身份申报为学生 → 教师，/me 立即生效
        mockMvc.perform(post("/api/app/profile/identity")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"TEACHER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityType").value("TEACHER"));

        mockMvc.perform(get("/api/common/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityType").value("TEACHER"));

        // 仅允许学生/教师自申报
        mockMvc.perform(post("/api/app/profile/identity")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        // 昵称修改同样刷新会话
        mockMvc.perform(post("/api/app/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"小张同学\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("小张同学"));

        mockMvc.perform(get("/api/common/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("小张同学"));

        // 未登录不可调用
        mockMvc.perform(post("/api/app/profile/identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityType\":\"TEACHER\"}"))
                .andExpect(status().isUnauthorized());
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
