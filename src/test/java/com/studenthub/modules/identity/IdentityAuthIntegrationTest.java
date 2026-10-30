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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "studenthub.mail.enabled=false")
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
    void devWechatLoginReturnsSameFixedUser() throws Exception {
        String first = mockMvc.perform(post("/api/common/auth/dev-wechat-login"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String second = mockMvc.perform(post("/api/common/auth/dev-wechat-login"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstResponse = objectMapper.readTree(first);
        JsonNode secondResponse = objectMapper.readTree(second);
        org.junit.jupiter.api.Assertions.assertEquals(
                firstResponse.path("data").path("user").path("userId").asText(),
                secondResponse.path("data").path("user").path("userId").asText());
        org.junit.jupiter.api.Assertions.assertFalse(secondResponse.path("data").path("isNewUser").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals("调试同学",
                secondResponse.path("data").path("user").path("displayName").asText());
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
                .andExpect(jsonPath("$.data.user.username").value(org.hamcrest.Matchers.startsWith("wechat_")))
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

    @Test
    void qqLoginCreatesUserAndReusesItOnSecondCall() throws Exception {
        String token = "qq-test-access-token-" + System.currentTimeMillis();

        String firstBody = mockMvc.perform(post("/api/common/auth/qq-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("accessToken", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.user.identityType").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.authorizationStatus").value("BASIC"))
                .andExpect(jsonPath("$.data.user.username",
                        org.hamcrest.Matchers.startsWith("qq_")))
                .andExpect(jsonPath("$.data.user.displayName",
                        org.hamcrest.Matchers.startsWith("QQ用户")))
                .andExpect(jsonPath("$.data.user.roles", hasItem("REQUESTER")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode first = objectMapper.readTree(firstBody);
        String userId = first.path("data").path("user").path("userId").asText();
        String sessionToken = first.path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/common/auth/me")
                        .header("Authorization", "Bearer " + sessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identityType").value("STUDENT"));

        mockMvc.perform(post("/api/common/auth/qq-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("accessToken", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.user.userId").value(userId));
    }

    @Test
    void registerUsesDefaultNicknameAndDefaultAvatarFallback() throws Exception {
        String token = registerAccount();
        mockMvc.perform(get("/api/common/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName",
                        org.hamcrest.Matchers.matchesPattern("用户\\d{6}")))
                .andExpect(jsonPath("$.data.avatarPath").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void bindingQqFillsDefaultNicknameButKeepsCustomNickname() throws Exception {
        // 默认昵称账号：绑定 QQ 后使用 QQ 昵称
        String defaultToken = registerAccount();
        mockMvc.perform(post("/api/app/profile/qq")
                        .header("Authorization", "Bearer " + defaultToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "accessToken", "qq-bind-default-" + System.nanoTime()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName",
                        org.hamcrest.Matchers.startsWith("QQ用户")));

        // 自定义昵称账号：绑定 QQ 后昵称保持不变，且 credentials 显示已绑定
        String customToken = registerAccount();
        mockMvc.perform(post("/api/app/profile")
                        .header("Authorization", "Bearer " + customToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"自定义昵称"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("自定义昵称"));

        mockMvc.perform(post("/api/app/profile/qq")
                        .header("Authorization", "Bearer " + customToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "accessToken", "qq-bind-custom-" + System.nanoTime()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("自定义昵称"));

        mockMvc.perform(get("/api/app/profile/credentials")
                        .header("Authorization", "Bearer " + customToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qqBound").value(true));
    }

    /** 走邮箱验证码注册，返回登录 token；测试 profile 下 devCode 直接返回。 */
    private String registerAccount() throws Exception {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000_000L);
        String accountId = "t" + suffix;
        String email = accountId + "@example.com";
        String codeBody = mockMvc.perform(post("/api/common/auth/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "purpose", "REGISTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devCode").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String code = objectMapper.readTree(codeBody).path("data").path("devCode").asText();

        String registerBody = mockMvc.perform(post("/api/common/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "accountId", accountId,
                                "email", email,
                                "password", "DevOnly123!",
                                "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.user.displayName",
                        org.hamcrest.Matchers.matchesPattern("用户\\d{6}")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(registerBody);
        assertFalse(response.path("data").path("user").path("displayName").asText().isEmpty());
        assertTrue(response.path("data").path("user").path("avatarPath").isNull());
        String token = response.path("data").path("accessToken").asText();
        assertNotNull(token);
        return token;
    }

    @Test
    void qqLoginRejectsOpenIdMismatch() throws Exception {
        mockMvc.perform(post("/api/common/auth/qq-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accessToken":"qq-mismatch-token","openId":"forged-open-id"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_03"));
    }

    @Test
    void qqLoginRejectsBlankAccessToken() throws Exception {
        mockMvc.perform(post("/api/common/auth/qq-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accessToken":""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
