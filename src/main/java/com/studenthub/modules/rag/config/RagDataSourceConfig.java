package com.studenthub.modules.rag.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class RagDataSourceConfig {
    @Bean(name = "dataSource")
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "ragDataSource")
    @ConditionalOnProperty(prefix = "studenthub.rag", name = "enabled", havingValue = "true")
    public DataSource ragDataSource(RagProperties properties) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(properties.getJdbcUrl());
        ds.setUsername(properties.getUsername());
        ds.setPassword(properties.getPassword());
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(5000);
        ds.setInitializationFailTimeout(-1);
        return ds;
    }

    @Bean(name = "ragJdbcTemplate")
    @ConditionalOnProperty(prefix = "studenthub.rag", name = "enabled", havingValue = "true")
    public JdbcTemplate ragJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("ragDataSource") DataSource ragDataSource) {
        return new JdbcTemplate(ragDataSource);
    }
}
