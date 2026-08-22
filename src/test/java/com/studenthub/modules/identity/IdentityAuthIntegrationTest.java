package com.studenthub.modules.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/common/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    @Test
    void loginReturnsRolesPermissionsAndLogoutRevokesSession() throws Exception {
        String responseBody = mockMvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"DevOnly123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roles[0]").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.data.user.permissions").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        String token = response.path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/common/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));

        mockMvc.perform(post("/api/common/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/common/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidPasswordDoesNotRevealAccountDetails() throws Exception {
        mockMvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_01"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void requesterReceivesSelfScopedPermissionsWithoutDispatchAuthority() throws Exception {
        mockMvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"requester","password":"DevOnly123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.roles", hasItem("REQUESTER")))
                .andExpect(jsonPath("$.data.user.roleScopes[0].dataScope").value("SELF"))
                .andExpect(jsonPath("$.data.user.permissions", hasItem("service-request:create")))
                .andExpect(jsonPath("$.data.user.permissions", not(hasItem("work-order:dispatch"))));
    }

    @Test
    void wechatLoginCreatesNewUserAndReturnsSessionWithIsNewUserTrue() throws Exception {
        String responseBody = mockMvc.perform(post("/api/common/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-mock-code-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.user.identityType").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.authorizationStatus").value("BASIC"))
                .andExpect(jsonPath("$.data.user.username").value(org.hamcrest.Matchers.startsWith("wx_")))
                .andExpect(jsonPath("$.data.user.roles", hasItem("REQUESTER")))
                .andExpect(jsonPath("$.data.user.permissions", hasItem("service-request:create")))
                .andExpect(jsonPath("$.data.user.permissions", hasItem("service-request:view:self")))
                .andExpect(jsonPath("$.data.user.roleScopes[0].dataScope").value("SELF"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        String token = response.path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/common/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityType").value("STUDENT"))
                .andExpect(jsonPath("$.data.authorizationStatus").value("BASIC"));

        mockMvc.perform(post("/api/common/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/common/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wechatLoginReturnsExistingUserOnSecondCall() throws Exception {
        String firstBody = mockMvc.perform(post("/api/common/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-mock-code-repeat"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstResponse = objectMapper.readTree(firstBody);
        Long userId = firstResponse.path("data").path("user").path("userId").asLong();

        String secondBody = mockMvc.perform(post("/api/common/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-mock-code-repeat"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.user.userId").value(userId))
                .andExpect(jsonPath("$.data.user.roles", hasItem("REQUESTER")))
                .andExpect(jsonPath("$.data.user.permissions", hasItem("service-request:create")))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void wechatLoginRejectsInvalidCode() throws Exception {
        mockMvc.perform(post("/api/common/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"invalid-test-code"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_02"));
    }

    @Test
    void wechatLoginRejectsBlankCode() throws Exception {
        mockMvc.perform(post("/api/common/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
