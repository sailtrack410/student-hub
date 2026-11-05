package com.studenthub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import com.studenthub.modules.identity.security.SecurityRuntimeConfig;
import com.studenthub.modules.identity.security.SessionAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final SessionAuthenticationFilter sessionAuthenticationFilter;
    private final SecurityRuntimeConfig securityRuntimeConfig;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 允许管理台 iframe 嵌入数据大屏（同源）
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // SSE 异步完成后的 ASYNC/ERROR 分派会再次过授权过滤器，
                        // 此时异步线程的 SecurityContext 不可用，须按分派类型放行
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC,
                                jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/common/system/health",
                                "/api/common/features",
                                "/api/common/features/**",
                                "/api/common/auth/login",
                                "/api/common/auth/captcha",
                                "/api/common/auth/wechat-login",
                                "/api/common/auth/qq-login",
                                "/api/common/auth/dev-wechat-login",
                                "/api/common/auth/email-code",
                                "/api/common/auth/register",
                                "/api/common/auth/password/reset",
                                "/api/common/files/view/**",
                                "/api/common/assets/**",
                                "/api/public/screen/**",
                                "/api/public/knowledge-bases/**",
                                "/api/public/app-version/**",
                                "/ws/**",
                                "/admin",
                                "/admin/**",
                                "/screen",
                                "/screen/**",
                                "/c/**",
                                "/share/**",
                                "/.well-known/assetlinks.json",
                                "/download",
                                "/download/**",
                                "/actuator/health",
                                "/api",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/configuration/**",
                                "/doc.html",
                                "/webjars/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api/swagger-ui.html",
                                "/api/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // 不缓存 CorsConfiguration：配置中心修改 security.cors_allowed_origins 后下一次请求立即生效。
        return (HttpServletRequest request) -> {
            List<String> origins = securityRuntimeConfig.corsAllowedOrigins();
            if (origins.isEmpty()) {
                return null;
            }
            CorsConfiguration configuration = new CorsConfiguration();
            if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
                configuration.setAllowedOriginPatterns(origins);
            } else {
                configuration.setAllowedOrigins(origins);
            }
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                    "X-Request-Id", "X-Campus-Id", "X-Client-Type"));
            configuration.setExposedHeaders(List.of("X-Request-Id"));
            configuration.setAllowCredentials(true);
            configuration.setMaxAge(3600L);
            return configuration;
        };
    }

    private void writeSecurityError(HttpServletResponse response, ErrorCode errorCode) throws java.io.IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.failed(errorCode.getCode(), errorCode.getMessage()));
    }
}
