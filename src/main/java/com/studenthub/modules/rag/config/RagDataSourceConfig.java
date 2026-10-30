package com.studenthub.modules.rag.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class RagDataSourceConfig {
    @Bean(name = "dataSource")
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    /**
     * RAG 数据源始终注册为可懒重建的包装器：应用即使未启用 RAG 也能启动，
     * 管理端启用/修改连接后由 {@link RagDataSourceHolder} 在首次访问时建池。
     */
    @Bean(name = "ragDataSource")
    public DataSource ragDataSource(RagDataSourceHolder holder) {
        return holder;
    }

    /**
     * 显式提供主库 JdbcTemplate 并标记 @Primary：自定义 ragJdbcTemplate 存在时，
     * Spring Boot 的 JdbcTemplate 自动配置会退让，若不显式声明，按类型注入会误拿到 RAG 模板。
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "ragJdbcTemplate")
    public JdbcTemplate ragJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("ragDataSource") DataSource ragDataSource) {
        return new JdbcTemplate(ragDataSource);
    }
}
