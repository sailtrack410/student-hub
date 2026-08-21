package com.studenthub.integration.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "studenthub.ai")
public class AiProperties {

    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String model;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 60;
    private int maxInputCharacters = 12000;
}
