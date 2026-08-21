package com.studenthub.modules.identity.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "studenthub.security")
public class SecurityProperties {

    private String sessionStore = "memory";
    private Duration sessionTtl = Duration.ofHours(8);
    private boolean passwordLoginEnabled;
    private List<String> allowedOrigins = new ArrayList<>();
}
