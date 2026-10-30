package com.studenthub.modules.location.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 高德 Web 服务配置；管理端保存后热更新到本 Bean。 */
@Data
@ConfigurationProperties(prefix = "studenthub.amap")
public class AmapProperties {

    /** Web 服务 Key，仅后端使用，不下发客户端 */
    private String webServiceKey;

    private String baseUrl = "https://restapi.amap.com";

    private int connectTimeoutSeconds = 5;

    private int readTimeoutSeconds = 8;
}
