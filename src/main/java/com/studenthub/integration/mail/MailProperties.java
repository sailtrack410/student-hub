package com.studenthub.integration.mail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 校园邮箱验证码发信配置。所有真实凭证通过环境变量注入，禁止写入仓库。
 *
 * <pre>
 * MAIL_ENABLED=true
 * MAIL_HOST=smtp.example.com
 * MAIL_PORT=465
 * MAIL_USERNAME=no-reply@example.com
 * MAIL_PASSWORD=******
 * MAIL_FROM=no-reply@example.com
 * MAIL_FROM_NAME=绵城一码通
 * MAIL_SSL_ENABLED=true
 * MAIL_STARTTLS_ENABLED=false
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "studenthub.mail")
public class MailProperties {

    /** false 时生产环境发送验证码会明确返回「邮箱服务未配置」，开发/测试环境可直接返回 devCode。 */
    private boolean enabled = false;

    private String host;
    private int port = 465;
    private String username;
    private String password;

    /** 发件人地址；留空时回退到 username。 */
    private String from;

    /** 发件人显示名。 */
    private String fromName = "绵城一码通";

    /** 465 端口通常开启 SSL；587 端口通常关闭 SSL 并开启 STARTTLS。 */
    private boolean sslEnabled = true;
    private boolean starttlsEnabled = false;

    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 5000;
    private int writeTimeoutMs = 5000;

    /** 开发/测试环境是否在响应中返回验证码；生产环境即使误配 true 也不会返回。 */
    private boolean devReturnCode = true;
}
