package com.studenthub.integration.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * 管理端保存邮箱配置后，把新配置应用到已经创建的 JavaMailSender（热更新，无需重启）。
 * 同时提供管理端“测试连通”使用的真实 SMTP 连接测试；返回值不含密码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailRuntimeRefresher {

    private final MailProperties properties;
    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;

    public void refresh() {
        JavaMailSender sender = javaMailSenderProvider.getIfAvailable();
        if (sender instanceof JavaMailSenderImpl impl) {
            MailConfig.apply(impl, properties);
            log.info("邮箱 SMTP 配置已热更新, enabled={}, host={}, port={}",
                    properties.isEnabled(), properties.getHost(), properties.getPort());
        }
    }

    /** 真实 SMTP 连接测试；只返回可达性、耗时和经过截断的异常摘要。 */
    public ProbeResult testConnection() {
        if (!StringUtils.hasText(properties.getHost())) {
            return new ProbeResult(false, 0, "未配置 SMTP 服务器地址");
        }
        long start = System.currentTimeMillis();
        JavaMailSenderImpl sender = MailConfig.build(properties);
        try {
            sender.testConnection();
            return new ProbeResult(true, System.currentTimeMillis() - start,
                    "SMTP 连接成功（" + properties.getHost() + ":" + properties.getPort() + "）");
        } catch (Exception exception) {
            String detail = safeMessage(exception);
            log.warn("SMTP 连接测试失败, host={}, port={}, error={}",
                    properties.getHost(), properties.getPort(), detail);
            return new ProbeResult(false, System.currentTimeMillis() - start, detail);
        }
    }

    private String safeMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null
                && (current instanceof org.springframework.mail.MailException
                || current instanceof jakarta.mail.MessagingException)) {
            current = current.getCause();
        }
        String type = current.getClass().getSimpleName();
        if (current instanceof SocketTimeoutException) {
            type = "连接/读取超时";
        } else if (current instanceof ConnectException) {
            type = "连接被拒绝或网络不可达";
        }
        String message = current.getMessage();
        if (!StringUtils.hasText(message)) {
            return type;
        }
        String cleaned = message.replaceAll("[\\r\\n]+", " ").trim();
        if (StringUtils.hasText(properties.getPassword())) {
            cleaned = cleaned.replace(properties.getPassword(), "******");
        }
        if (cleaned.length() > 160) {
            cleaned = cleaned.substring(0, 160) + "…";
        }
        return type + ": " + cleaned;
    }

    public record ProbeResult(boolean reachable, long latencyMs, String detail) {
    }
}
