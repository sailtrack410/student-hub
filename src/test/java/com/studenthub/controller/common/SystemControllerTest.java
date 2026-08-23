package com.studenthub.controller.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsUp() throws Exception {
        mockMvc.perform(get("/api/common/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void apiDocumentationIsAvailableInTestEnvironment() throws Exception {
        mockMvc.perform(get("/doc.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.1"))
                .andExpect(jsonPath("$.info.title").value("Student Hub API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/common/auth/login']").exists())
                .andExpect(jsonPath("$.components.schemas.WechatLoginRequest.properties.code.description").exists())
                .andExpect(jsonPath("$.components.schemas.PasswordLoginRequest.properties.username.description").exists())
                .andExpect(jsonPath("$.components.schemas.LoginResponse.properties.accessToken.description").exists())
                .andExpect(jsonPath("$.components.schemas.CurrentUserResponse.properties.identityType.description").exists());

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk());
    }
}
