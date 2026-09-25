package com.studenthub.integration.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studenthub.wechat")
public class WechatProperties {

    private String mode = "real";
    private String appId;
    private String appSecret;
    private String apiBaseUrl;
    private Long defaultCampusId = 1L;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 10;
}
