package com.studenthub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI studentHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Student Hub API")
                        .description("学生自助服务与校园事务智能助手接口文档")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque")));
    }

    @Bean
    public GroupedOpenApi commonApi() {
        return GroupedOpenApi.builder()
                .group("公共接口")
                .pathsToMatch("/api/common/**")
                .build();
    }

    @Bean
    public GroupedOpenApi appApi() {
        return GroupedOpenApi.builder()
                .group("小程序接口")
                .pathsToMatch("/api/app/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("管理端接口")
                .pathsToMatch("/api/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("公开接口")
                .pathsToMatch("/api/public/**")
                .build();
    }

    /** 开发调试分组：仅非 prod profile 注册（devtool 探针/同步端点不对生产环境暴露文档）。 */
    @Bean
    @Profile("!prod")
    public GroupedOpenApi devApi() {
        return GroupedOpenApi.builder()
                .group("开发调试接口")
                .pathsToMatch("/api/dev/**")
                .build();
    }
}
