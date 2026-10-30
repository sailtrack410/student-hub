package com.studenthub.integration.mail;

import jakarta.mail.Session;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 使用 Spring Mail 的 JavaMailSender，但配置项统一收敛到 {@code studenthub.mail.*}，
 * 避免再引入一套 {@code spring.mail.*} 配置。
 *
 * <p>Bean 始终创建：管理端可以把 {@code mail.enabled} 从 false 改为 true 并热更新，
 * 发送器在每次发送前通过 {@link MailProperties} 判断是否已启用/配置完整。</p>
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(MailProperties properties) {
        return build(properties);
    }

    /** 按当前配置构建一个独立的 JavaMailSender（也用于管理端 SMTP 连通性测试）。 */
    public static JavaMailSenderImpl build(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        apply(sender, properties);
        return sender;
    }

    /** 把当前配置应用到已存在的 sender（管理端保存后热更新，不重启）。 */
    public static void apply(JavaMailSenderImpl sender, MailProperties properties) {
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setUsername(properties.getUsername());
        sender.setPassword(properties.getPassword());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties javaMail = sender.getJavaMailProperties();
        javaMail.setProperty("mail.transport.protocol", "smtp");
        javaMail.setProperty("mail.smtp.auth", String.valueOf(StringUtils.hasText(properties.getUsername())));
        javaMail.setProperty("mail.smtp.ssl.enable", String.valueOf(properties.isSslEnabled()));
        javaMail.setProperty("mail.smtp.starttls.enable", String.valueOf(properties.isStarttlsEnabled()));
        javaMail.setProperty("mail.smtp.connectiontimeout", String.valueOf(properties.getConnectTimeoutMs()));
        javaMail.setProperty("mail.smtp.timeout", String.valueOf(properties.getReadTimeoutMs()));
        javaMail.setProperty("mail.smtp.writetimeout", String.valueOf(properties.getWriteTimeoutMs()));

        // JavaMailSenderImpl 会缓存首次创建的 Session；管理端热更新后必须替换，
        // 否则最近一次发信过的进程仍会继续使用旧主机/端口/凭证。
        sender.setSession(Session.getInstance(javaMail));
    }
}
