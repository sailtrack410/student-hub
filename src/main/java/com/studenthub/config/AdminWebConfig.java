package com.studenthub.config;

import com.studenthub.modules.identity.campus.AdminCampusContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态 SPA 入口：/admin、/screen、/download 与对应斜杠结尾路径转发到 index.html；
 * 同时把 /api/admin/** 绑定到具体校区上下文。
 */
@Configuration
@RequiredArgsConstructor
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminCampusContextInterceptor adminCampusContextInterceptor;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/c/{slug:[A-Za-z0-9]+}").setViewName("forward:/share/index.html");
        registry.addViewController("/admin").setViewName("forward:/admin/index.html");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
        registry.addViewController("/screen").setViewName("forward:/screen/index.html");
        registry.addViewController("/screen/").setViewName("forward:/screen/index.html");
        registry.addViewController("/download").setViewName("forward:/download/index.html");
        registry.addViewController("/download/").setViewName("forward:/download/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminCampusContextInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}
