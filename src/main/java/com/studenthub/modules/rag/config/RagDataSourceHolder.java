package com.studenthub.modules.rag.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * RAG 独立 PostgreSQL 数据源：按当前 {@link RagProperties} 懒创建连接池。
 *
 * <p>管理端保存 RAG 配置后调用 {@link #invalidate()} 关闭旧池，下一次访问按新配置重建，
 * 因此修改 JDBC URL、账号或密码不需要重启应用；RAG 停用时直接拒绝连接。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagDataSourceHolder implements DataSource {

    private final RagProperties properties;

    private volatile HikariDataSource pool;
    private volatile String fingerprint;

    /** 管理端配置变更后使旧连接池失效；连接会在下一次访问时重建。 */
    public synchronized void invalidate() {
        HikariDataSource current = this.pool;
        this.pool = null;
        this.fingerprint = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception exception) {
                log.warn("关闭旧 RAG 连接池失败: {}", exception.getMessage());
            }
        }
    }

    /** 当前配置是否具备连接条件（不代表数据库一定可达）。 */
    public boolean configured() {
        return properties.isEnabled()
                && properties.getJdbcUrl() != null && !properties.getJdbcUrl().isBlank();
    }

    /** 测试/管理端使用：当前配置目标（不含密码）。 */
    public String targetDescription() {
        return configured() ? properties.getJdbcUrl() + " (" + properties.getUsername() + ")" : "未配置";
    }

    @PreDestroy
    public void destroy() {
        invalidate();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return pool().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return pool().getConnection(username, password);
    }

    private HikariDataSource pool() throws SQLException {
        if (!properties.isEnabled()) {
            throw new SQLException("RAG 知识库未启用");
        }
        String currentFingerprint = fingerprintOf(properties);
        HikariDataSource current = this.pool;
        if (current != null && currentFingerprint.equals(this.fingerprint) && !current.isClosed()) {
            return current;
        }
        synchronized (this) {
            currentFingerprint = fingerprintOf(properties);
            current = this.pool;
            if (current != null && currentFingerprint.equals(this.fingerprint) && !current.isClosed()) {
                return current;
            }
            if (properties.getJdbcUrl() == null || properties.getJdbcUrl().isBlank()) {
                throw new SQLException("RAG JDBC 地址未配置");
            }
            HikariDataSource next = new HikariDataSource();
            next.setJdbcUrl(properties.getJdbcUrl());
            next.setUsername(properties.getUsername());
            next.setPassword(properties.getPassword());
            next.setMaximumPoolSize(5);
            next.setMinimumIdle(0);
            next.setConnectionTimeout(5000);
            next.setInitializationFailTimeout(-1);
            HikariDataSource previous = this.pool;
            this.pool = next;
            this.fingerprint = currentFingerprint;
            if (previous != null) {
                try {
                    previous.close();
                } catch (Exception exception) {
                    log.warn("关闭旧 RAG 连接池失败: {}", exception.getMessage());
                }
            }
            log.info("RAG 连接池已按最新配置重建, target={}", properties.getJdbcUrl());
            return next;
        }
    }

    private String fingerprintOf(RagProperties value) {
        return String.join("|",
                Objects.toString(value.getJdbcUrl(), ""),
                Objects.toString(value.getUsername(), ""),
                Objects.toString(value.getPassword(), ""));
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return pool().getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        pool().setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        pool().setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return pool().getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("RAG DataSource 不支持 parent logger");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return pool().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || pool().isWrapperFor(iface);
    }
}
