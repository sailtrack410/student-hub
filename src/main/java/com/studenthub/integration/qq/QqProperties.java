package com.studenthub.integration.qq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studenthub.qq")
public class QqProperties {

    /** mock=本地联调不访问 QQ；real=凭 accessToken 反查 graph.qq.com */
    private String mode = "mock";
    private String clientId;
    private String clientSecret;
    private String apiBaseUrl = "https://graph.qq.com";
    private Long defaultCampusId = 1L;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 10;
}
