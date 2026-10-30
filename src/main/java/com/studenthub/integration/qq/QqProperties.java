package com.studenthub.integration.qq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studenthub.qq")
public class QqProperties {

    /**
     * mock=本地联调不访问 QQ；real=凭 accessToken 反查 graph.qq.com。
     * real 模式必须配置 clientId（QQ 互联 APP ID），用于校验 token 是否属于本应用。
     */
    private String mode = "mock";

    /** QQ 互联 APP ID（移动应用/网站应用详情页的 APP ID）。 */
    private String clientId;

    /**
     * QQ 互联 APP KEY。移动端 SDK 的 accessToken 反查流程不直接使用；
     * 预留给后续网站/桌面端 authorization_code 换 token 的扩展。
     */
    private String clientSecret;

    private String apiBaseUrl = "https://graph.qq.com";
    private Long defaultCampusId = 1L;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 10;
}
