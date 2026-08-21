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
}
