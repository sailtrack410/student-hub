package com.studenthub.modules.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.modules.identity.security.LoginCaptchaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 security.* 配置真正进入运行时：验证码、密码策略、会话 TTL、CORS、设备上限。
 * 这些测试会临时改 sys_config，@AfterEach 恢复，避免污染同一 H2 上下文中的其他测试。
 */
@SpringBootTest(properties = "studenthub.mail.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRuntimeIntegrationTest {

    private static final String KEY_PASSWORD_MIN = "security.password_min_length";
    private static final String KEY_PASSWORD_COMPLEX = "security.password_require_complex";
    private static final String KEY_CAPTCHA = "security.login_captcha_enabled";
    private static final String KEY_MAX_DEVICES = "security.max_devices_per_user";
    private static final String KEY_SESSION_TTL = "security.session_ttl_hours";
    private static final String KEY_CORS = "security.cors_allowed_origins";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private LoginCaptchaService captchaService;

    @AfterEach
    void restoreConfig() {
        setConfig(KEY_PASSWORD_MIN, "8");
        setConfig(KEY_PASSWORD_COMPLEX, "true");
        setConfig(KEY_CAPTCHA, "false");
        setConfig(KEY_MAX_DEVICES, "5");
        setConfig(KEY_SESSION_TTL, "8");
        setConfig(KEY_CORS, "");
    }

    @Test
    void captchaSwitchControlsPasswordLogin() throws Exception {
        setConfig(KEY_CAPTCHA, "true");

        MvcResult captchaResult = mockMvc.perform(get("/api/common/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.captchaId").isNotEmpty())
                .andExpect(jsonPath("$.data.image").isNotEmpty())
                .andReturn();
        String image = objectMapper.readTree(captchaResult.getResponse().getContentAsString())
                .path("data").path("image").asText();
        assertTrue(image.startsWith("data:image/svg+xml;base64,"));

        // 未携带验证码：验证码校验先于密码校验
        mockMvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"DevOnly123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_04"));

        LoginCaptchaService.CaptchaChallenge challenge = captchaService.issue();
        mockMvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin",
                                "password", "DevOnly123!",
                                "captchaId", challenge.captchaId(),
                                "captchaCode", challenge.answer()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // 一次性：同一验证码再次使用必须失败
        mockMvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin",
                                "password", "DevOnly123!",
                                "captchaId", challenge.captchaId(),
                                "captchaCode", challenge.answer()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_04"));
    }

    @Test
    void passwordPolicyIsReadAtRuntime() throws Exception {
        setConfig(KEY_PASSWORD_MIN, "12");
        setConfig(KEY_PASSWORD_COMPLEX, "true");
        String shortEmail = uniqueEmail("short");
        String shortCode = emailCode(shortEmail);
        mockMvc.perform(post("/api/common/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", "s" + System.nanoTime() % 1_000_000_000L,
                                "email", shortEmail,
                                "password", "Abcdef12",
                                "code", shortCode))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码长度需为 12-128 位"));

        String goodEmail = uniqueEmail("good");
        String goodCode = emailCode(goodEmail);
        mockMvc.perform(post("/api/common/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "accountId", "g" + System.nanoTime() % 1_000_000_000L,
                                "email", goodEmail,
                                "password", "Abcdef123456",
                                "code", goodCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void sessionTtlIsReadFromConfig() throws Exception {
        setConfig(KEY_SESSION_TTL, "1");
        String token = login("admin");
        assertNotNull(token);
        MvcResult result = mockMvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"DevOnly123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String expiresAt = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("expiresAt").asText();
        Duration remaining = Duration.between(Instant.now(), Instant.parse(expiresAt));
        assertTrue(remaining.toMinutes() >= 55 && remaining.toMinutes() <= 65,
                "1 小时 TTL 的会话剩余时间应接近 1 小时，实际=" + remaining);
    }

    @Test
    void corsWhitelistIsReadAtRuntime() throws Exception {
        setConfig(KEY_CORS, "https://allowed.example");
        mockMvc.perform(options("/api/common/system/health")
                        .header(HttpHeaders.ORIGIN, "https://allowed.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://allowed.example"));

        mockMvc.perform(options("/api/common/system/health")
                        .header(HttpHeaders.ORIGIN, "https://denied.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deviceLimitEvictsOldestSessionOnNewLogin() throws Exception {
        setConfig(KEY_MAX_DEVICES, "1");
        String tokenA = loginWithUserAgent("Device-A");
        String tokenB = loginWithUserAgent("Device-B");

        mockMvc.perform(get("/api/common/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/common/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "DevOnly123!"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String loginWithUserAgent(String userAgent) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/common/auth/login")
                        .header(HttpHeaders.USER_AGENT, userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin", "password", "DevOnly123!"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        assertTrue(token != null && !token.isBlank());
        return token;
    }

    private String emailCode(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/common/auth/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "purpose", "REGISTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devCode").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("devCode").asText();
    }

    private String uniqueEmail(String prefix) {
        return prefix + System.nanoTime() + "@example.com";
    }

    private void setConfig(String key, String value) {
        int updated = jdbcTemplate.update("UPDATE sys_config SET config_value = ? WHERE config_key = ? AND campus_id = 0",
                value, key);
        assertEquals(1, updated, "测试需要命中 sys_config 种子行: " + key);
    }
}
